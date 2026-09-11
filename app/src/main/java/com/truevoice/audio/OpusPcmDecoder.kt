/*
 * True Voice: Real-Time On-Device AI Voice Clone & Scam Defense
 * In-Memory MediaCodec Audio Decoder for Opus and AAC
 */

package com.truevoice.audio

import android.media.MediaCodec
import android.media.MediaFormat
import com.kitsumed.shizucallrecorder.utils.AppLogger
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * High-speed, in-memory decoder using Android's native [MediaCodec].
 * Decodes compressed audio packets (Opus or AAC) directly to 16-bit PCM.
 *
 * Zero disk I/O.
 */
class OpusPcmDecoder(
    private val onPcmDecoded: (pcmBuffer: ByteBuffer, sampleRate: Int, channels: Int) -> Unit
) : Closeable {

    private var codec: MediaCodec? = null
    private var isInitialized = false
    private var outputSampleRate = 48000
    private var outputChannels = 2
    private val bufferInfo = MediaCodec.BufferInfo()

    /**
     * Initializes the decoder for the specified MIME type.
     *
     * @param mimeType E.g. [MediaFormat.MIMETYPE_AUDIO_OPUS] or "audio/mp4a-latm"
     * @param sampleRate Input sample rate (typically 48000)
     * @param channelCount Channel count (typically 2)
     * @param configData Optional CSD-0 data from scrcpy config packet
     */
    fun initialize(
        mimeType: String = MediaFormat.MIMETYPE_AUDIO_OPUS,
        sampleRate: Int = 48000,
        channelCount: Int = 2,
        configData: ByteArray? = null
    ) {
        if (isInitialized) {
            release()
        }

        try {
            AppLogger.d("Initializing in-memory audio decoder: mime=$mimeType, sr=$sampleRate, ch=$channelCount")
            val format = MediaFormat.createAudioFormat(mimeType, sampleRate, channelCount)

            if (mimeType == MediaFormat.MIMETYPE_AUDIO_OPUS) {
                configureOpusFormat(format, sampleRate, channelCount, configData)
            } else if (configData != null && configData.isNotEmpty()) {
                format.setByteBuffer("csd-0", ByteBuffer.wrap(configData))
            }

            val decoder = MediaCodec.createDecoderByType(mimeType)
            decoder.configure(format, null, null, 0)
            decoder.start()

            codec = decoder
            outputSampleRate = sampleRate
            outputChannels = channelCount
            isInitialized = true
            AppLogger.i("In-memory audio decoder started successfully for $mimeType")
        } catch (e: Exception) {
            AppLogger.e("Failed to initialize in-memory audio decoder: ${e.message}")
            release()
            throw e
        }
    }

    /**
     * Feeds a single compressed audio packet into the decoder and drains available decoded PCM.
     */
    fun decodePacket(data: ByteArray, ptsUs: Long) {
        val decoder = codec ?: return
        if (!isInitialized || data.isEmpty()) return

        try {
            // 1. Enqueue input buffer
            val inputIndex = decoder.dequeueInputBuffer(10_000L) // 10ms timeout
            if (inputIndex >= 0) {
                val inputBuffer = decoder.getInputBuffer(inputIndex)
                if (inputBuffer != null) {
                    inputBuffer.clear()
                    inputBuffer.put(data)
                    decoder.queueInputBuffer(inputIndex, 0, data.size, ptsUs, 0)
                }
            } else {
                AppLogger.w("Decoder input buffer not available (timeout)")
            }

            // 2. Drain output buffers
            drainOutputBuffers(decoder)
        } catch (e: Exception) {
            AppLogger.w("Exception during in-memory audio decode: ${e.message}")
        }
    }

    private fun drainOutputBuffers(decoder: MediaCodec) {
        while (true) {
            val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 0L)
            when {
                outputIndex >= 0 -> {
                    val outputBuffer = decoder.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                        // Deliver decoded 16-bit PCM
                        onPcmDecoded(outputBuffer, outputSampleRate, outputChannels)
                    }
                    decoder.releaseOutputBuffer(outputIndex, false)
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val newFormat = decoder.outputFormat
                    outputSampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE, outputSampleRate)
                    outputChannels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT, outputChannels)
                    AppLogger.d("Decoder output format changed: rate=$outputSampleRate channels=$outputChannels")
                }
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    break // No more output available right now
                }
                else -> break
            }
        }
    }

    /**
     * Constructs required CSD buffers for Android's Opus decoder if not provided.
     */
    private fun configureOpusFormat(
        format: MediaFormat,
        sampleRate: Int,
        channelCount: Int,
        configData: ByteArray?
    ) {
        val csd0 = if (configData != null && configData.isNotEmpty()) {
            ByteBuffer.wrap(configData)
        } else {
            // Standard 19-byte OpusHead for 48kHz stereo/mono
            val header = ByteBuffer.allocate(19).order(ByteOrder.nativeOrder())
            header.put("OpusHead".toByteArray(Charsets.US_ASCII)) // Magic bytes
            header.put(1.toByte())                                 // Version
            header.put(channelCount.toByte())                      // Channel count
            header.putShort(3840.toShort())                        // Pre-skip (default 3840 samples)
            header.putInt(sampleRate)                              // Original sample rate
            header.putShort(0.toShort())                           // Output gain
            header.put(0.toByte())                                 // Channel mapping family
            header.flip()
            header
        }
        format.setByteBuffer("csd-0", csd0)

        // CSD-1: Pre-skip in nanoseconds (3840 samples @ 48kHz = 80,000,000 ns)
        val csd1 = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder())
        csd1.putLong(80_000_000L)
        csd1.flip()
        format.setByteBuffer("csd-1", csd1)

        // CSD-2: Seek pre-roll in nanoseconds (80,000,000 ns)
        val csd2 = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder())
        csd2.putLong(80_000_000L)
        csd2.flip()
        format.setByteBuffer("csd-2", csd2)
    }

    override fun close() {
        release()
    }

    fun release() {
        try {
            codec?.stop()
        } catch (_: Exception) {}
        try {
            codec?.release()
        } catch (_: Exception) {}
        codec = null
        isInitialized = false
    }
}
