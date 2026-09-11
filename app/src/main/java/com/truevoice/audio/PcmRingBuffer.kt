/*
 * True Voice: Real-Time On-Device AI Voice Clone & Scam Defense
 * Circular In-Memory PCM Ring Buffer
 */

package com.truevoice.audio

import kotlin.math.min

/**
 * Thread-safe in-memory circular audio buffer holding normalized float PCM samples.
 * Zero disk allocations. Used for sliding-window feeding to VAD and anti-spoofing models.
 *
 * @param sampleRate Target audio sample rate (e.g. 16000 Hz)
 * @param maxDurationSeconds Maximum history duration to preserve in RAM (e.g. 3.0 seconds)
 */
class PcmRingBuffer(
    val sampleRate: Int = 16000,
    val maxDurationSeconds: Float = 3.0f
) {
    val capacity: Int = (sampleRate * maxDurationSeconds).toInt()
    private val buffer: FloatArray = FloatArray(capacity)
    
    private val lock = Any()
    private var writeHead: Int = 0
    private var totalSamplesWritten: Long = 0L

    /**
     * Appends new float PCM samples into the circular buffer.
     */
    fun write(samples: FloatArray, offset: Int = 0, length: Int = samples.size) {
        if (length <= 0) return
        synchronized(lock) {
            for (i in 0 until length) {
                buffer[writeHead] = samples[offset + i]
                writeHead = (writeHead + 1) % capacity
            }
            totalSamplesWritten += length
        }
    }

    /**
     * Returns the duration of valid audio currently held in the buffer (up to maxDurationSeconds).
     */
    fun getAvailableDurationSeconds(): Float {
        synchronized(lock) {
            val validSamples = min(totalSamplesWritten, capacity.toLong()).toInt()
            return validSamples.toFloat() / sampleRate
        }
    }

    /**
     * Extracts the most recent [requestedSeconds] of audio from the buffer as a contiguous FloatArray.
     * If the buffer has fewer samples than requested, it returns whatever is currently available.
     */
    fun getLatestWindow(requestedSeconds: Float): FloatArray {
        synchronized(lock) {
            val requestedSamples = (requestedSeconds * sampleRate).toInt()
            val validSamples = min(totalSamplesWritten, capacity.toLong()).toInt()
            val samplesToRead = min(requestedSamples, validSamples)

            if (samplesToRead <= 0) {
                return FloatArray(0)
            }

            val result = FloatArray(samplesToRead)
            var readIndex = (writeHead - samplesToRead + capacity) % capacity

            for (i in 0 until samplesToRead) {
                result[i] = buffer[readIndex]
                readIndex = (readIndex + 1) % capacity
            }

            return result
        }
    }

    /**
     * Resets the buffer pointer and sample count.
     */
    fun clear() {
        synchronized(lock) {
            writeHead = 0
            totalSamplesWritten = 0L
            buffer.fill(0f)
        }
    }
}
