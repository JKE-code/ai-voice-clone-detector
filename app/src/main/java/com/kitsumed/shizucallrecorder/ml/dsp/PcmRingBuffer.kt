package com.kitsumed.shizucallrecorder.ml.dsp

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Thread-safe concurrent circular buffer designed for real-time audio sliding windows.
 *
 * @param windowCapacity Total samples required for one complete ML inference window (e.g. 48,000 @ 16kHz = 3.0s).
 * @param hopSamples Number of new samples required before triggering the next inference snapshot (e.g. 8,000 @ 16kHz = 0.5s).
 */
class PcmRingBuffer(
    val windowCapacity: Int = 48000,
    val hopSamples: Int = 8000
) {
    private val buffer = FloatArray(windowCapacity)
    private val lock = ReentrantLock()
    private var writeHead = 0
    private var totalSamplesWritten = 0L
    private var samplesSinceLastHop = 0

    /**
     * Appends new audio samples into the ring buffer.
     *
     * @param samples Array of normalized Float32 audio samples.
     * @param onWindowReady Callback invoked with a snapshot of the latest [windowCapacity] samples
     *                       whenever [hopSamples] new samples have accumulated and the buffer is full.
     */
    fun append(samples: FloatArray, onWindowReady: (FloatArray) -> Unit) {
        if (samples.isEmpty()) return

        var snapshotToEmit: FloatArray? = null

        lock.withLock {
            for (sample in samples) {
                buffer[writeHead] = sample
                writeHead = (writeHead + 1) % windowCapacity
                totalSamplesWritten++
                samplesSinceLastHop++

                if (totalSamplesWritten >= windowCapacity && samplesSinceLastHop >= hopSamples) {
                    samplesSinceLastHop = 0
                    val snapshot = FloatArray(windowCapacity)
                    // Linearize the circular buffer (oldest sample at index 0, newest at index capacity-1)
                    val firstPartLength = windowCapacity - writeHead
                    System.arraycopy(buffer, writeHead, snapshot, 0, firstPartLength)
                    System.arraycopy(buffer, 0, snapshot, firstPartLength, writeHead)
                    snapshotToEmit = snapshot
                }
            }
        }

        snapshotToEmit?.let { onWindowReady(it) }
    }

    /**
     * Returns whether the buffer has accumulated at least one full window of audio.
     */
    fun isFull(): Boolean = lock.withLock { totalSamplesWritten >= windowCapacity }

    /**
     * Resets the ring buffer state (e.g., when a call ends or starts).
     */
    fun reset() {
        lock.withLock {
            writeHead = 0
            totalSamplesWritten = 0L
            samplesSinceLastHop = 0
            buffer.fill(0.0f)
        }
    }
}
