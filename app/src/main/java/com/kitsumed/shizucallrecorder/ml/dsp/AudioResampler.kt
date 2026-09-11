package com.kitsumed.shizucallrecorder.ml.dsp

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * High-performance DSP utility for downmixing stereo to mono,
 * converting 16-bit signed PCM to normalized [-1.0f .. 1.0f] float,
 * and downsampling from 48kHz to 16kHz (3:1 integer decimation with low-pass filter).
 */
object AudioResampler {

    /**
     * Converts 48kHz 16-bit Stereo PCM byte buffer into 16kHz Mono FloatArray.
     * Decimation factor = 3 (48000 / 16000 = 3).
     *
     * @param pcmBytes Raw 16-bit little-endian PCM bytes (4 bytes per stereo frame: 2 bytes L + 2 bytes R).
     * @param offset Byte offset in buffer.
     * @param length Number of bytes to process.
     * @return Normalized 16kHz mono FloatArray.
     */
    fun pcm48kStereoTo16kMonoFloat(
        pcmBytes: ByteArray,
        offset: Int = 0,
        length: Int = pcmBytes.size
    ): FloatArray {
        val totalStereoFrames = length / 4 // 2 bytes Left + 2 bytes Right
        val outputFrames = totalStereoFrames / 3
        if (outputFrames <= 0) return FloatArray(0)

        val output = FloatArray(outputFrames)
        var outIdx = 0

        // Process every 3rd stereo frame (decimation factor = 3)
        // With simple 3-tap moving average box filter to prevent aliasing
        var byteIdx = offset
        while (outIdx < outputFrames && (byteIdx + 11) < (offset + length)) {
            // Frame 0
            val l0 = (pcmBytes[byteIdx].toInt() and 0xFF) or (pcmBytes[byteIdx + 1].toInt() shl 8)
            val r0 = (pcmBytes[byteIdx + 2].toInt() and 0xFF) or (pcmBytes[byteIdx + 3].toInt() shl 8)
            val mono0 = (l0.toShort() + r0.toShort()) / 2.0f

            // Frame 1
            val l1 = (pcmBytes[byteIdx + 4].toInt() and 0xFF) or (pcmBytes[byteIdx + 5].toInt() shl 8)
            val r1 = (pcmBytes[byteIdx + 6].toInt() and 0xFF) or (pcmBytes[byteIdx + 7].toInt() shl 8)
            val mono1 = (l1.toShort() + r1.toShort()) / 2.0f

            // Frame 2
            val l2 = (pcmBytes[byteIdx + 8].toInt() and 0xFF) or (pcmBytes[byteIdx + 9].toInt() shl 8)
            val r2 = (pcmBytes[byteIdx + 10].toInt() and 0xFF) or (pcmBytes[byteIdx + 11].toInt() shl 8)
            val mono2 = (l2.toShort() + r2.toShort()) / 2.0f

            // Anti-aliasing average across the 3 frames
            val filteredMono = (mono0 + mono1 + mono2) / 3.0f

            // Normalize 16-bit integer range [-32768, 32767] to [-1.0, 1.0]
            output[outIdx++] = (filteredMono / 32768.0f).coerceIn(-1.0f, 1.0f)
            byteIdx += 12 // Advance 3 stereo frames (3 * 4 bytes = 12 bytes)
        }

        return output
    }

    /**
     * Converts 16kHz 16-bit Mono PCM byte buffer directly into normalized FloatArray.
     */
    fun pcm16kMonoToFloat(pcmBytes: ByteArray, offset: Int = 0, length: Int = pcmBytes.size): FloatArray {
        val numSamples = length / 2
        val output = FloatArray(numSamples)
        var byteIdx = offset
        for (i in 0 until numSamples) {
            val sample = (pcmBytes[byteIdx].toInt() and 0xFF) or (pcmBytes[byteIdx + 1].toInt() shl 8)
            output[i] = (sample.toShort() / 32768.0f).coerceIn(-1.0f, 1.0f)
            byteIdx += 2
        }
        return output
    }
}
