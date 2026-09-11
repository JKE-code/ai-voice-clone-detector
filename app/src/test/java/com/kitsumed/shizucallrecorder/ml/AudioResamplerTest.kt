package com.kitsumed.shizucallrecorder.ml

import com.kitsumed.shizucallrecorder.ml.dsp.AudioResampler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioResamplerTest {

    @Test
    fun testPcm48kStereoTo16kMonoFloat_decimationAndNormalization() {
        // 12 bytes = 3 stereo frames @ 48kHz -> should produce exactly 1 mono sample @ 16kHz
        // Frame 0: L=16384, R=16384 -> avg=16384
        // Frame 1: L=16384, R=16384 -> avg=16384
        // Frame 2: L=16384, R=16384 -> avg=16384
        // Box filter avg = 16384 / 32768.0 = 0.5f
        val bytes = ByteArray(12)
        val shortVal: Short = 16384
        val byte0 = (shortVal.toInt() and 0xFF).toByte()
        val byte1 = ((shortVal.toInt() shr 8) and 0xFF).toByte()

        for (i in 0 until 12 step 2) {
            bytes[i] = byte0
            bytes[i + 1] = byte1
        }

        val result = AudioResampler.pcm48kStereoTo16kMonoFloat(bytes)
        assertEquals(1, result.size)
        assertEquals(0.5f, result[0], 0.01f)
    }

    @Test
    fun testPcm48kStereoTo16kMonoFloat_emptyInput() {
        val emptyResult = AudioResampler.pcm48kStereoTo16kMonoFloat(ByteArray(0))
        assertEquals(0, emptyResult.size)
    }

    @Test
    fun testPcm16kMonoToFloat() {
        // 2 samples: 0 and -32768
        val bytes = ByteArray(4)
        bytes[0] = 0
        bytes[1] = 0
        // -32768 in 16-bit little-endian is 0x00 0x80
        bytes[2] = 0x00
        bytes[3] = 0x80.toByte()

        val result = AudioResampler.pcm16kMonoToFloat(bytes)
        assertEquals(2, result.size)
        assertEquals(0.0f, result[0], 0.001f)
        assertEquals(-1.0f, result[1], 0.001f)
    }
}
