package com.kitsumed.shizucallrecorder.ml

import com.kitsumed.shizucallrecorder.ml.model.RiskLevel
import com.kitsumed.shizucallrecorder.ml.model.VoiceRiskAssessment

/**
 * Fuses Acoustic Voice Clone Scoring (Track 1) with Multilingual Indic Coercion Intent (Track 2)
 * using exponential temporal smoothing and confidence guardrails.
 *
 * @param smoothingAlpha Weight given to the newest audio window (default: 0.35f).
 * @param cloneAlertThreshold Score threshold to qualify as critical alert (default: 0.65f).
 * @param safeThreshold Score threshold to qualify as definitely safe (default: 0.35f).
 * @param requiredConsecutiveAlerts Consecutive high-risk windows needed for escalation.
 */
class TemporalRiskEngine(
    private val smoothingAlpha: Float = 0.35f,
    private val cloneAlertThreshold: Float = 0.65f,
    private val safeThreshold: Float = 0.35f,
    private val requiredConsecutiveAlerts: Int = 2
) {
    private var currentSmoothedScore = 0.0f
    private var consecutiveSuspiciousCount = 0
    private var totalWindowsCount = 0
    private var isFirstWindow = true

    /**
     * Updates the hybrid risk assessment with acoustic features + Indic intent transcript.
     *
     * @param acousticSpoofScore Raw spoof probability output by [VoiceCloneClassifier].
     * @param indicCoercionScore Coercion score from [IndicIntentEngine].
     * @param detectedCategories List of matched scam categories.
     * @param speechRatio VAD active speech ratio.
     * @param latencyMs Inference execution time in milliseconds.
     */
    @Synchronized
    fun update(
        acousticSpoofScore: Float,
        indicCoercionScore: Float = 0.05f,
        detectedCategories: List<String> = emptyList(),
        speechRatio: Float = 1.0f,
        latencyMs: Long = 0L
    ): VoiceRiskAssessment {
        // Guardrail: If window was mostly silence/noise, retain previous state or return inconclusive
        if (speechRatio < 0.20f) {
            return VoiceRiskAssessment(
                riskLevel = if (totalWindowsCount == 0) RiskLevel.INCONCLUSIVE else currentLevel(),
                smoothedScore = currentSmoothedScore,
                acousticSpoofScore = acousticSpoofScore,
                indicCoercionScore = indicCoercionScore,
                detectedScamCategories = detectedCategories,
                speechConfidence = speechRatio,
                consecutiveSuspiciousWindows = consecutiveSuspiciousCount,
                totalWindowsAnalyzed = totalWindowsCount,
                inferenceLatencyMs = latencyMs
            )
        }

        totalWindowsCount++

        // Dual-Analysis Hybrid Fusion:
        // Weight: 65% Acoustic Voice Physics + 35% Indic Coercion Intent
        val rawHybridScore = if (acousticSpoofScore >= 0.70f && indicCoercionScore >= 0.70f) {
            // Compound multiplier if BOTH synthetic voice and coercion words are present
            0.98f
        } else {
            (0.65f * acousticSpoofScore) + (0.35f * indicCoercionScore)
        }

        // Exponential Moving Average (EMA)
        if (isFirstWindow) {
            currentSmoothedScore = rawHybridScore
            isFirstWindow = false
        } else {
            currentSmoothedScore = (smoothingAlpha * rawHybridScore) + ((1.0f - smoothingAlpha) * currentSmoothedScore)
        }

        // Track consecutive alerts
        if (rawHybridScore >= cloneAlertThreshold) {
            consecutiveSuspiciousCount++
        } else {
            consecutiveSuspiciousCount = maxOf(0, consecutiveSuspiciousCount - 1)
        }

        val level = currentLevel()

        return VoiceRiskAssessment(
            riskLevel = level,
            smoothedScore = currentSmoothedScore,
            acousticSpoofScore = acousticSpoofScore,
            indicCoercionScore = indicCoercionScore,
            detectedScamCategories = detectedCategories,
            speechConfidence = speechRatio,
            consecutiveSuspiciousWindows = consecutiveSuspiciousCount,
            totalWindowsAnalyzed = totalWindowsCount,
            inferenceLatencyMs = latencyMs
        )
    }

    private fun currentLevel(): RiskLevel {
        return when {
            consecutiveSuspiciousCount >= requiredConsecutiveAlerts || currentSmoothedScore >= cloneAlertThreshold -> {
                RiskLevel.SUSPICIOUS_VOICE_CLONE
            }
            currentSmoothedScore >= safeThreshold -> {
                RiskLevel.CAUTION
            }
            else -> {
                RiskLevel.SAFE
            }
        }
    }

    /**
     * Resets the temporal smoothing state (at start/end of call).
     */
    @Synchronized
    fun reset() {
        currentSmoothedScore = 0.0f
        consecutiveSuspiciousCount = 0
        totalWindowsCount = 0
        isFirstWindow = true
    }
}
