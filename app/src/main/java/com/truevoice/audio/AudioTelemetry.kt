/*
 * True Voice: Real-Time On-Device AI Voice Clone & Scam Defense
 * Audio Telemetry & Health Monitoring
 */

package com.truevoice.audio

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Diagnostics and real-time telemetry for the In-Memory Audio Pipeline (Sink 2).
 */
data class AudioPipelineSnapshot(
    val isDecoderActive: Boolean,
    val codecName: String,
    val inputSampleRate: Int,
    val inputChannels: Int,
    val outputSampleRate: Int,
    val totalPacketsReceived: Long,
    val packetsPerSecond: Int,
    val totalPcmSamplesDecoded: Long,
    val bufferDurationSeconds: Float,
    val currentRmsEnergy: Float,
    val lastError: String?
)

class AudioTelemetry {
    val isDecoderActive = AtomicBoolean(false)
    val totalPacketsReceived = AtomicLong(0L)
    val packetsInCurrentSecond = AtomicInteger(0)
    val packetsPerSecond = AtomicInteger(0)
    val totalPcmSamplesDecoded = AtomicLong(0L)
    
    @Volatile
    var codecName: String = "UNKNOWN"
    
    @Volatile
    var inputSampleRate: Int = 48000
    
    @Volatile
    var inputChannels: Int = 2
    
    @Volatile
    var outputSampleRate: Int = 16000
    
    @Volatile
    var bufferDurationSeconds: Float = 0f
    
    @Volatile
    var currentRmsEnergy: Float = 0f
    
    @Volatile
    var lastError: String? = null
    
    private var lastSecondTimestampNanos = System.nanoTime()

    fun onPacketReceived() {
        totalPacketsReceived.incrementAndGet()
        val count = packetsInCurrentSecond.incrementAndGet()
        
        val now = System.nanoTime()
        val elapsed = now - lastSecondTimestampNanos
        if (elapsed >= 1_000_000_000L) { // 1 second
            packetsPerSecond.set((count * 1_000_000_000L / elapsed).toInt())
            packetsInCurrentSecond.set(0)
            lastSecondTimestampNanos = now
        }
    }

    fun onSamplesDecoded(sampleCount: Int, rms: Float, currentBufferSeconds: Float) {
        totalPcmSamplesDecoded.addAndGet(sampleCount.toLong())
        currentRmsEnergy = rms
        bufferDurationSeconds = currentBufferSeconds
    }

    fun getSnapshot(): AudioPipelineSnapshot {
        return AudioPipelineSnapshot(
            isDecoderActive = isDecoderActive.get(),
            codecName = codecName,
            inputSampleRate = inputSampleRate,
            inputChannels = inputChannels,
            outputSampleRate = outputSampleRate,
            totalPacketsReceived = totalPacketsReceived.get(),
            packetsPerSecond = packetsPerSecond.get(),
            totalPcmSamplesDecoded = totalPcmSamplesDecoded.get(),
            bufferDurationSeconds = bufferDurationSeconds,
            currentRmsEnergy = currentRmsEnergy,
            lastError = lastError
        )
    }

    fun reset() {
        isDecoderActive.set(false)
        totalPacketsReceived.set(0L)
        packetsInCurrentSecond.set(0)
        packetsPerSecond.set(0)
        totalPcmSamplesDecoded.set(0L)
        bufferDurationSeconds = 0f
        currentRmsEnergy = 0f
        lastError = null
        lastSecondTimestampNanos = System.nanoTime()
    }
}
