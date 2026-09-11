/*
 * True Voice: Real-Time On-Device AI Voice Clone & Scam Defense
 * Live In-Memory Audio Analysis Sink (Sink 2)
 */

package com.truevoice.audio

import com.kitsumed.shizucallrecorder.integrations.scrcpy.ScrcpyAudioCodec
import com.kitsumed.shizucallrecorder.integrations.scrcpy.ScrcpyClient
import com.kitsumed.shizucallrecorder.utils.AppLogger
import java.io.Closeable
import java.nio.ByteBuffer

/**
 * Sink 2: Decoupled in-memory live audio analysis sink.
 * Receives raw [ScrcpyClient.AudioPacket] instances from the capture pipeline,
 * decodes them in RAM via [OpusPcmDecoder], resamples to 16kHz mono via [AudioResampler],
 * and buffers the latest audio into [PcmRingBuffer] for the AI models.
 */
class LiveAnalysisSink : Closeable {

    val ringBuffer = PcmRingBuffer(sampleRate = 16000, maxDurationSeconds = 3.0f)
    val telemetry = AudioTelemetry()
    private val resampler = AudioResampler()
    
    private var decoder: OpusPcmDecoder? = null
    private var isRunning = false
    private var lastLogNanos = 0L

    /**
     * Initializes the in-memory decoder for the stream format.
     */
    fun initialize(
        codec: ScrcpyAudioCodec,
        sampleRate: Int = 48000,
        channels: Int = 2,
        configData: ByteArray? = null
    ) {
        release()

        telemetry.reset()
        telemetry.codecName = codec.cliKey
        telemetry.inputSampleRate = sampleRate
        telemetry.inputChannels = channels
        telemetry.outputSampleRate = ringBuffer.sampleRate

        try {
            decoder = OpusPcmDecoder { pcmBuffer: ByteBuffer, srcRate: Int, srcChannels: Int ->
                handleDecodedPcm(pcmBuffer, srcRate, srcChannels)
            }
            decoder?.initialize(
                mimeType = codec.mimeType,
                sampleRate = sampleRate,
                channelCount = channels,
                configData = configData
            )
            telemetry.isDecoderActive.set(true)
            isRunning = true
            AppLogger.i("[TrueVoice] LiveAnalysisSink initialized: ${codec.cliKey} $sampleRate Hz, $channels ch -> 16kHz mono ring buffer")
        } catch (e: Exception) {
            telemetry.lastError = e.message
            AppLogger.e("[TrueVoice] LiveAnalysisSink failed to initialize: ${e.message}")
        }
    }

    /**
     * Enqueues an audio packet directly from [ScrcpyClient].
     */
    fun enqueuePacket(packet: ScrcpyClient.AudioPacket, currentCodec: ScrcpyAudioCodec) {
        if (!isRunning) return
        telemetry.onPacketReceived()

        if (packet.isConfigPacket) {
            AppLogger.d("[TrueVoice] Config packet received in LiveAnalysisSink (${packet.data.size} bytes)")
            // If decoder not started or needs CSD, initialize with it
            if (decoder == null) {
                initialize(currentCodec, configData = packet.data)
            }
            return
        }

        // Decode audio frame in memory
        decoder?.decodePacket(packet.data, packet.pts)
    }

    private fun handleDecodedPcm(pcmBuffer: ByteBuffer, srcRate: Int, srcChannels: Int) {
        try {
            // Downmix to mono and resample to 16kHz float [-1.0, 1.0]
            val resampled = resampler.process(
                byteBuffer = pcmBuffer,
                inputSampleRate = srcRate,
                inputChannels = srcChannels,
                targetSampleRate = ringBuffer.sampleRate
            )

            if (resampled.samples.isNotEmpty()) {
                // Write into circular buffer
                ringBuffer.write(resampled.samples)

                val availableSeconds = ringBuffer.getAvailableDurationSeconds()
                telemetry.onSamplesDecoded(resampled.samples.size, resampled.rmsEnergy, availableSeconds)

                // Periodic telemetry logging (every ~2 seconds)
                val now = System.nanoTime()
                if (now - lastLogNanos >= 2_000_000_000L) {
                    lastLogNanos = now
                    val snapshot = telemetry.getSnapshot()
                    AppLogger.i(
                        "[TrueVoice Telemetry] Live PCM: Active=${snapshot.isDecoderActive} | " +
                        "Rate=${snapshot.packetsPerSecond} pkt/s | " +
                        "Buffer=${"%.2f".format(snapshot.bufferDurationSeconds)}s / 3.0s | " +
                        "RMS=${"%.4f".format(snapshot.currentRmsEnergy)} | " +
                        "Decoded=${snapshot.totalPcmSamplesDecoded} samples"
                    )
                }
            }
        } catch (e: Exception) {
            telemetry.lastError = e.message
            AppLogger.w("[TrueVoice] Error processing decoded PCM: ${e.message}")
        }
    }

    /**
     * Retrieves the latest audio window from the ring buffer for AI inference.
     */
    fun getLatestWindow(durationSeconds: Float): FloatArray {
        return ringBuffer.getLatestWindow(durationSeconds)
    }

    fun getTelemetrySnapshot(): AudioPipelineSnapshot {
        return telemetry.getSnapshot()
    }

    fun stop() {
        isRunning = false
        decoder?.release()
        decoder = null
        telemetry.isDecoderActive.set(false)
        AppLogger.d("[TrueVoice] LiveAnalysisSink stopped")
    }

    override fun close() {
        release()
    }

    fun release() {
        stop()
        ringBuffer.clear()
        telemetry.reset()
    }
}
