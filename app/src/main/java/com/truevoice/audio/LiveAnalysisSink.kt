/*
 * True Voice: Real-Time On-Device AI Voice Clone & Scam Defense
 * Live In-Memory Audio Analysis Sink (Sink 2)
 */

package com.truevoice.audio

import android.content.Context
import com.kitsumed.shizucallrecorder.integrations.scrcpy.ScrcpyAudioCodec
import com.kitsumed.shizucallrecorder.integrations.scrcpy.ScrcpyClient
import com.kitsumed.shizucallrecorder.utils.AppLogger
import com.truevoice.ml.AuthenticityResult
import com.truevoice.ml.RiskAssessment
import com.truevoice.ml.RiskLevel
import com.truevoice.ml.SileroVadEngine
import com.truevoice.ml.TemporalRiskEngine
import com.truevoice.ml.VoiceAuthenticityEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.Closeable
import java.nio.ByteBuffer

/**
 * Sink 2: Decoupled in-memory live audio analysis sink.
 * Receives raw [ScrcpyClient.AudioPacket] instances from the capture pipeline,
 * decodes them in RAM via [OpusPcmDecoder], resamples to 16kHz mono via [AudioResampler],
 * and buffers the latest audio into [PcmRingBuffer].
 *
 * Phase 2 AI Intelligence:
 * Periodically gates audio frames through [SileroVadEngine] for speech activity,
 * forwards voiced 3.0s windows to [VoiceAuthenticityEngine] for synthetic clone detection,
 * and passes results to [TemporalRiskEngine] to emit a smoothed [RiskAssessment] via [riskFlow].
 */
class LiveAnalysisSink(
    private val appContext: Context? = null
) : Closeable {

    val ringBuffer = PcmRingBuffer(sampleRate = 16000, maxDurationSeconds = 3.0f)
    val telemetry = AudioTelemetry()
    private val resampler = AudioResampler()

    // Phase 2 AI Engines
    private var vadEngine: SileroVadEngine? = null
    private var authenticityEngine: VoiceAuthenticityEngine? = null
    private val coercionEngine = com.truevoice.ml.ConversationalCoercionEngine()
    private val riskEngine = TemporalRiskEngine()

    private val _riskFlow = MutableStateFlow(
        RiskAssessment(
            level = RiskLevel.INCONCLUSIVE,
            smoothedScore = 0.0f,
            confidence = 0.0f,
            consecutiveAlertWindows = 0,
            totalEvaluatedWindows = 0,
            latestResult = null
        )
    )
    val riskFlow: StateFlow<RiskAssessment> = _riskFlow.asStateFlow()

    /**
     * Feeds speech-to-text transcript snippets into the Conversational Risk Engine.
     */
    fun analyzeTranscript(text: String): RiskAssessment {
        val result = coercionEngine.analyzeTranscript(text)
        val updated = riskEngine.processTranscriptRisk(result)
        telemetry.latestRiskAssessment = updated
        _riskFlow.value = updated
        com.truevoice.forensics.ForensicRepository.recordCoercionKeywords(result.triggeredKeywords)
        return updated
    }

    private var decoder: OpusPcmDecoder? = null
    private var isRunning = false
    private var lastLogNanos = 0L
    private var lastInferenceNanos = 0L

    private var mlScope: CoroutineScope? = null

    /**
     * Initializes the in-memory decoder and AI engines for the call.
     */
    fun initialize(
        codec: ScrcpyAudioCodec,
        sampleRate: Int = 48000,
        channels: Int = 2,
        configData: ByteArray? = null,
        context: Context? = appContext,
        callerNumber: String = "Unknown",
        callerName: String? = null
    ) {
        release()

        telemetry.reset()
        telemetry.codecName = codec.cliKey
        telemetry.inputSampleRate = sampleRate
        telemetry.inputChannels = channels
        telemetry.outputSampleRate = ringBuffer.sampleRate
        riskEngine.reset()

        com.truevoice.forensics.ForensicRepository.startSession(
            callerNumber = callerNumber,
            callerName = callerName
        )

        val activeContext = context ?: appContext
        if (activeContext != null) {
            try {
                vadEngine = SileroVadEngine(activeContext.applicationContext).apply { initialize() }
                authenticityEngine = VoiceAuthenticityEngine(activeContext.applicationContext).apply { initialize() }
            } catch (e: Exception) {
                AppLogger.w("[TrueVoice ML] Error initializing AI engines: ${e.message}")
            }
        }

        mlScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
            AppLogger.i("[TrueVoice] LiveAnalysisSink initialized: ${codec.cliKey} $sampleRate Hz, $channels ch -> 16kHz mono AI pipeline")
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
            if (decoder == null) {
                initialize(currentCodec, configData = packet.data)
            }
            return
        }

        decoder?.decodePacket(packet.data, packet.pts)
    }

    private fun handleDecodedPcm(pcmBuffer: ByteBuffer, srcRate: Int, srcChannels: Int) {
        try {
            val resampled = resampler.process(
                byteBuffer = pcmBuffer,
                inputSampleRate = srcRate,
                inputChannels = srcChannels,
                targetSampleRate = ringBuffer.sampleRate
            )

            if (resampled.samples.isNotEmpty()) {
                ringBuffer.write(resampled.samples)

                val availableSeconds = ringBuffer.getAvailableDurationSeconds()
                telemetry.onSamplesDecoded(resampled.samples.size, resampled.rmsEnergy, availableSeconds)

                // Trigger ML inference cadence: once every ~1.5s (overlapping 3s window)
                val now = System.nanoTime()
                if (now - lastInferenceNanos >= 1_500_000_000L && availableSeconds >= 1.0f) {
                    lastInferenceNanos = now
                    triggerAiInference(now)
                }

                // Periodic telemetry logging (every ~2 seconds)
                if (now - lastLogNanos >= 2_000_000_000L) {
                    lastLogNanos = now
                    val snapshot = telemetry.getSnapshot()
                    val currentRisk = _riskFlow.value
                    AppLogger.i(
                        "[TrueVoice Telemetry] Live PCM: Active=${snapshot.isDecoderActive} | " +
                        "Rate=${snapshot.packetsPerSecond} pkt/s | " +
                        "Buffer=${"%.2f".format(snapshot.bufferDurationSeconds)}s / 3.0s | " +
                        "RMS=${"%.4f".format(snapshot.currentRmsEnergy)} | " +
                        "Risk=${currentRisk.level} (${"%.2f".format(currentRisk.smoothedScore)})"
                    )
                }
            }
        } catch (e: Exception) {
            telemetry.lastError = e.message
            AppLogger.w("[TrueVoice] Error processing decoded PCM: ${e.message}")
        }
    }

    /**
     * Executes Phase 2 AI intelligence asynchronously on Dispatchers.Default.
     */
    private fun triggerAiInference(timestampNanos: Long) {
        val scope = mlScope ?: return
        val window = ringBuffer.getSnapshot()
        if (window.isEmpty()) return

        scope.launch {
            try {
                // 1. Silero VAD gate
                val vad = vadEngine
                val isSpeech = if (vad != null) {
                    // Check last 512 samples for active speech
                    val testChunk = if (window.size >= SileroVadEngine.WINDOW_SIZE_SAMPLES) {
                        window.copyOfRange(window.size - SileroVadEngine.WINDOW_SIZE_SAMPLES, window.size)
                    } else {
                        window
                    }
                    vad.isSpeechPresent(testChunk)
                } else {
                    // Energy fallback: RMS > 0.015 indicates voice presence
                    val rms = telemetry.currentRmsEnergy
                    rms > 0.015f
                }

                if (!isSpeech) {
                    // Voice is silent/paused: skip heavy anti-spoofing to preserve CPU
                    return@launch
                }

                // 2. Voice Authenticity & Anti-Spoofing Analysis
                val authEngine = authenticityEngine
                val authResult = if (authEngine != null) {
                    authEngine.evaluateWindow(window)
                } else {
                    // Fallback to minimal acoustic evaluation
                    VoiceAuthenticityEngine(appContext ?: return@launch).use { it.evaluateWindow(window) }
                }

                // 3. Temporal Risk Fusion
                val assessment = riskEngine.processResult(authResult)
                telemetry.latestRiskAssessment = assessment
                _riskFlow.value = assessment
                com.truevoice.forensics.ForensicRepository.recordWindow(authResult, assessment, isSpeech)

                AppLogger.d(
                    "[TrueVoice AI] label=${authResult.label} score=${"%.2f".format(authResult.syntheticScore)} " +
                    "conf=${"%.2f".format(authResult.confidence)} trend=${"%+.3f".format(authResult.temporalTrend)} " +
                    "frames=[${authResult.subFrameScores.joinToString { "%.2f".format(it) }}] " +
                    "time=${authResult.inferenceTimeMs}ms → Risk=${assessment.level} (smoothed=${"%.2f".format(assessment.smoothedScore)})"
                )
            } catch (e: Exception) {
                AppLogger.w("[TrueVoice AI] Error during inference cycle: ${e.message}")
            }
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
        if (!isRunning) return
        isRunning = false
        decoder?.release()
        decoder = null
        telemetry.isDecoderActive.set(false)
        mlScope?.cancel()
        mlScope = null
        vadEngine?.close()
        vadEngine = null
        authenticityEngine?.close()
        authenticityEngine = null
        com.truevoice.forensics.ForensicRepository.endSession()
        AppLogger.d("[TrueVoice] LiveAnalysisSink stopped")
    }

    override fun close() {
        release()
    }

    fun release() {
        stop()
        ringBuffer.clear()
        telemetry.reset()
        riskEngine.reset()
    }
}
