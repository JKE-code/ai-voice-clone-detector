/*
 * True Voice: Real-Time On-Device AI Voice Clone & Scam Defense
 * Audio Resampling, Channel Downmixing & Normalization
 */

package com.truevoice.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Result of resampling a PCM audio chunk.
 */
data class ResampledAudio(
    val samples: FloatArray,
    val rmsEnergy: Float
)

/**
 * Converts raw 16-bit PCM (e.g. 48kHz stereo) into 16kHz mono normalized float PCM [-1.0, 1.0].
 * High-performance, zero unnecessary allocations.
 */
class AudioResampler {

    /**
     * Processes a ByteBuffer containing 16-bit signed PCM audio.
     *
     * @param byteBuffer Decoded PCM byte buffer (little-endian 16-bit)
     * @param inputSampleRate Source sample rate (e.g. 48000)
     * @param inputChannels Source channels (1 for mono, 2 for stereo)
     * @param targetSampleRate Destination sample rate (e.g. 16000)
     */
    fun process(
        byteBuffer: ByteBuffer,
        inputSampleRate: Int = 48000,
        inputChannels: Int = 2,
        targetSampleRate: Int = 16000,
        downlinkChannelOnly: Boolean = true
    ): ResampledAudio {
        val shortBuffer = byteBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val totalShorts = shortBuffer.remaining()

        if (totalShorts <= 0) {
            return ResampledAudio(FloatArray(0), 0f)
        }

        // Step 1: In stereo mode, isolate Channel 0 (Downlink / Far-end caller)
        // to prevent local microphone bleed from corrupting AI clone analysis.
        val monoCount = totalShorts / inputChannels
        val monoFloats = FloatArray(monoCount)

        if (inputChannels == 2) {
            for (i in 0 until monoCount) {
                val left = shortBuffer.get(i * 2) / 32768.0f
                val right = shortBuffer.get(i * 2 + 1) / 32768.0f
                monoFloats[i] = if (downlinkChannelOnly) left else (left + right) * 0.5f
            }
        } else {
            for (i in 0 until monoCount) {
                monoFloats[i] = shortBuffer.get(i) / 32768.0f
            }
        }

        // Step 2: Resample to targetSampleRate
        val resampled: FloatArray
        if (inputSampleRate == targetSampleRate) {
            resampled = monoFloats
        } else if (inputSampleRate == 48000 && targetSampleRate == 16000) {
            // Optimized 3:1 integer decimation with 3-tap moving average (anti-aliasing)
            val outputCount = monoCount / 3
            resampled = FloatArray(outputCount)
            for (i in 0 until outputCount) {
                val idx = i * 3
                resampled[i] = (monoFloats[idx] + monoFloats[idx + 1] + monoFloats[idx + 2]) / 3.0f
            }
        } else {
            // General linear interpolation
            val ratio = inputSampleRate.toDouble() / targetSampleRate.toDouble()
            val outputCount = (monoCount / ratio).toInt()
            resampled = FloatArray(outputCount)
            for (i in 0 until outputCount) {
                val srcIdx = i * ratio
                val srcIdxFloor = srcIdx.toInt()
                val frac = (srcIdx - srcIdxFloor).toFloat()
                val s0 = monoFloats[srcIdxFloor]
                val s1 = if (srcIdxFloor + 1 < monoCount) monoFloats[srcIdxFloor + 1] else s0
                resampled[i] = s0 + frac * (s1 - s0)
            }
        }

        // Step 3: Compute RMS Energy
        var sumSquares = 0.0
        for (sample in resampled) {
            sumSquares += sample * sample
        }
        val rms = if (resampled.isNotEmpty()) sqrt(sumSquares / resampled.size).toFloat() else 0f

        return ResampledAudio(resampled, rms)
    }
}
