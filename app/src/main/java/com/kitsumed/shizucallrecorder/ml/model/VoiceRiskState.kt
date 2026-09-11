package com.kitsumed.shizucallrecorder.ml.model

/**
 * Represents the real-time AI Voice Clone & Impersonation risk state during a phone call.
 */
enum class RiskLevel {
    /** High confidence genuine human speech. */
    SAFE,

    /** Slight spectral irregularities, weak synthetic signature, or moderate urgency words. */
    CAUTION,

    /** High confidence AI-generated / cloned voice or severe coercion attack detected. */
    SUSPICIOUS_VOICE_CLONE,

    /** Silence, background noise, or insufficient speech duration to classify accurately. */
    INCONCLUSIVE
}

/**
 * Data packet emitted in real time by [com.kitsumed.shizucallrecorder.ml.TemporalRiskEngine].
 *
 * @property riskLevel The smoothed risk category for UI display.
 * @property smoothedScore The temporal hybrid moving average score in range [0.0 .. 1.0].
 * @property acousticSpoofScore The raw acoustic synthetic score from the ONNX classifier [0.0 .. 1.0].
 * @property indicCoercionScore The Multilingual Indic intent coercion score [0.0 .. 1.0].
 * @property detectedScamCategories Categories matched across Indian languages (e.g. FINANCIAL_DEMAND, AUTHORITY_IMPERSONATION).
 * @property speechConfidence Confidence score from Silero VAD (0.0 = silence/noise, 1.0 = clear speech).
 * @property consecutiveSuspiciousWindows Number of back-to-back high-risk windows observed.
 * @property totalWindowsAnalyzed Total count of speech windows processed in current call.
 * @property inferenceLatencyMs Execution latency of the last ML inference in milliseconds.
 */
data class VoiceRiskAssessment(
    val riskLevel: RiskLevel = RiskLevel.INCONCLUSIVE,
    val smoothedScore: Float = 0.0f,
    val acousticSpoofScore: Float = 0.0f,
    val indicCoercionScore: Float = 0.0f,
    val detectedScamCategories: List<String> = emptyList(),
    val speechConfidence: Float = 0.0f,
    val consecutiveSuspiciousWindows: Int = 0,
    val totalWindowsAnalyzed: Int = 0,
    val inferenceLatencyMs: Long = 0L,
    val timestampMs: Long = System.currentTimeMillis()
)
