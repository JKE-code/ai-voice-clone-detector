package com.kitsumed.shizucallrecorder.ml

import com.kitsumed.shizucallrecorder.ml.dsp.PcmRingBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmRingBufferTest {

    @Test
    fun testPcmRingBuffer_accumulatesAndEmitsOnHop() {
        // Window capacity = 10 samples, Hop = 4 samples
        val ringBuffer = PcmRingBuffer(windowCapacity = 10, hopSamples = 4)
        var emitCount = 0
        var lastEmittedWindow: FloatArray? = null

        // Initial state
        assertFalse(ringBuffer.isFull())

        // Feed 8 samples (not enough for 10)
        ringBuffer.append(floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f)) {
            emitCount++
            lastEmittedWindow = it
        }
        assertEquals(0, emitCount)
        assertFalse(ringBuffer.isFull())

        // Feed 4 more samples (total 12 samples >= 10 capacity, hop of 4 reached)
        ringBuffer.append(floatArrayOf(9f, 10f, 11f, 12f)) {
            emitCount++
            lastEmittedWindow = it
        }

        assertEquals(1, emitCount)
        assertTrue(ringBuffer.isFull())
        // First emitted full window at sample 10 has [1f .. 10f]
        assertEquals(10, lastEmittedWindow?.size)
        assertEquals(1f, lastEmittedWindow!![0], 0.001f)
        assertEquals(10f, lastEmittedWindow!![9], 0.001f)
    }

    @Test
    fun testPcmRingBuffer_resetClearsState() {
        val ringBuffer = PcmRingBuffer(windowCapacity = 6, hopSamples = 2)
        ringBuffer.append(floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f)) {}
        assertTrue(ringBuffer.isFull())

        ringBuffer.reset()
        assertFalse(ringBuffer.isFull())
    }
}
