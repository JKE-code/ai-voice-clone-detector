package com.kitsumed.shizucallrecorder.ml

import android.content.Context
import com.kitsumed.shizucallrecorder.ml.dsp.AudioResampler
import com.kitsumed.shizucallrecorder.ml.dsp.PcmRingBuffer
import com.kitsumed.shizucallrecorder.ml.model.RiskLevel
import com.kitsumed.shizucallrecorder.ml.model.VoiceRiskAssessment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Master coordinator orchestrating:
 * 1. Track 1: Live Telephony Downlink Audio -> DSP Resampler -> Silero VAD -> Acoustic ONNX Classifier
 * 2. Track 2: Live Transcribed Speech -> Multilingual Indic Intent & Coercion Engine (Hindi, Telugu, Tamil, etc.)
 * 3. Fusion: TemporalRiskEngine -> StateFlow<VoiceRiskAssessment>
 */
class VoiceCloneDetectionCoordinator(
    private val context: Context,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) : AutoCloseable {

    private val sessionManager = OrtSessionManager(context)
    private val vadDetector = SileroVadDetector(sessionManager)
    private val classifier = VoiceCloneClassifier(sessionManager)
    private val riskEngine = TemporalRiskEngine()
    private val ringBuffer = PcmRingBuffer(windowCapacity = 48000, hopSamples = 8000)

    private val _riskAssessmentFlow = MutableStateFlow(VoiceRiskAssessment())
    val riskAssessmentFlow: StateFlow<VoiceRiskAssessment> = _riskAssessmentFlow.asStateFlow()

    @Volatile
    private var isSessionActive = false

    @Volatile
    private var latestIndicTranscript: String = ""

    /**
     * Starts a new detection session for an active phone call.
     */
    fun startSession() {
        isSessionActive = true
        latestIndicTranscript = ""
        ringBuffer.reset()
        vadDetector.resetState()
        riskEngine.reset()
        _riskAssessmentFlow.value = VoiceRiskAssessment(riskLevel = RiskLevel.INCONCLUSIVE)
    }

    /**
     * Feeds raw 48kHz stereo 16-bit PCM bytes received from scrcpy downlink stream.
     */
    fun feed48kStereoPcm(pcmBytes: ByteArray, offset: Int = 0, length: Int = pcmBytes.size) {
        if (!isSessionActive || length <= 0) return

        val floatSamples = AudioResampler.pcm48kStereoTo16kMonoFloat(pcmBytes, offset, length)
        if (floatSamples.isEmpty()) return

        ringBuffer.append(floatSamples) { windowSnapshot ->
            coroutineScope.launch {
                processWindow(windowSnapshot)
            }
        }
    }

    /**
     * Feeds real-time transcribed text (Hindi, Telugu, Tamil, Marathi, English, etc.)
     * from speech recognizer / keyword spotter into Track 2.
     */
    fun updateLiveTranscript(transcriptChunk: String) {
        if (!isSessionActive || transcriptChunk.isBlank()) return
        latestIndicTranscript = "$latestIndicTranscript $transcriptChunk".trim()

        // Re-evaluate immediate risk if new trigger words arrive
        val intentAssessment = IndicIntentEngine.analyzeTranscript(latestIndicTranscript)
        if (intentAssessment.matchedCategories.isNotEmpty()) {
            val current = _riskAssessmentFlow.value
            val updated = riskEngine.update(
                acousticSpoofScore = current.acousticSpoofScore,
                indicCoercionScore = intentAssessment.coercionScore,
                detectedCategories = intentAssessment.matchedCategories,
                speechRatio = current.speechConfidence,
                latencyMs = current.inferenceLatencyMs
            )
            _riskAssessmentFlow.value = updated
        }
    }

    private fun processWindow(windowSamples: FloatArray) {
        if (!isSessionActive) return

        // Step 1: Voice Activity Detection (VAD) Speech Ratio
        val speechRatio = vadDetector.computeSpeechRatio(windowSamples)

        // Step 2: Track 1 — Acoustic Anti-Spoofing Classifier
        val classification = if (speechRatio >= 0.15f) {
            classifier.classifyWindow(windowSamples)
        } else {
            ClassificationResult(spoofProbability = 0.0f, inferenceTimeMs = 1L, isSuccess = true)
        }

        // Step 3: Track 2 — Multilingual Indic Intent Analysis
        val intentResult = IndicIntentEngine.analyzeTranscript(latestIndicTranscript)

        // Step 4: Hybrid Fusion in Temporal Risk Engine
        val assessment = riskEngine.update(
            acousticSpoofScore = classification.spoofProbability,
            indicCoercionScore = intentResult.coercionScore,
            detectedCategories = intentResult.matchedCategories,
            speechRatio = speechRatio,
            latencyMs = classification.inferenceTimeMs
        )

        _riskAssessmentFlow.value = assessment
    }

    /**
     * Stops the active detection session and cleans up state.
     */
    fun stopSession() {
        isSessionActive = false
        latestIndicTranscript = ""
        ringBuffer.reset()
        vadDetector.resetState()
        riskEngine.reset()
    }

    override fun close() {
        stopSession()
        vadDetector.close()
        classifier.close()
        sessionManager.close()
    }
}
