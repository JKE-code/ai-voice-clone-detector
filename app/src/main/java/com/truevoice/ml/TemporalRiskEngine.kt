/*
 * True Voice: Real-Time On-Device AI Voice Clone & Scam Defense
 * Temporal Risk Decision Engine
 */

package com.truevoice.ml

import com.kitsumed.shizucallrecorder.utils.AppLogger
import java.util.ArrayDeque

/**
 * Operational Risk Level emitted to the UI/Overlay.
 */
enum class RiskLevel {
    SAFE,                // Genuine human caller confirmed
    CAUTION,             // Transient suspicion or marginal vocoder markers
    CLONE_ALERT,         // Confirmed AI-generated / cloned voice attack
    FINANCIAL_COERCION,  // Human caller but severe financial coercion / extortion detected
    INCONCLUSIVE         // Insufficient speech samples or low confidence
}

/**
 * Live Risk Assessment snapshot emitted to listeners and UI StateFlows.
 */
data class RiskAssessment(
    val level: RiskLevel,
    val smoothedScore: Float,
    val confidence: Float,
    val consecutiveAlertWindows: Int,
    val totalEvaluatedWindows: Int,
    val latestResult: AuthenticityResult?,
    val conversationalRisk: ConversationalRiskResult? = null
)

/**
 * Deterministic Temporal Risk Engine.
 *
 * Prevents false positives caused by single-frame anomalies (e.g. sudden cough, line crackle,
 * or background television) by requiring temporal persistence across multiple overlapping windows.
 *
 * Rules:
 * 1. Rolling window buffer of size [historyCapacity] (default 8 windows).
 * 2. Exponential or linear recency weighting over past scores.
 * 3. Persistence Rule: Requires at least [minConsecutiveAlerts] (default 3) consecutive
 *    positive windows above threshold to escalate to [RiskLevel.CLONE_ALERT].
 * 4. Inconclusive Guard: Returns [RiskLevel.INCONCLUSIVE] if fewer than [minWindowsRequired]
 *    windows are evaluated.
 */
class TemporalRiskEngine(
    private val historyCapacity: Int = 8,
    private val minWindowsRequired: Int = 2,
    private val minConsecutiveAlerts: Int = 3,
    private val cloneAlertThreshold: Float = 0.65f,
    private val cautionThreshold: Float = 0.40f
) {

    private val windowHistory = ArrayDeque<AuthenticityResult>(historyCapacity)
    private var consecutiveCloneCount = 0
    private var totalEvaluations = 0

    @Synchronized
    fun processResult(result: AuthenticityResult): RiskAssessment {
        totalEvaluations++

        // Ignore completely silent/insufficient windows from advancing consecutive streaks
        if (result.label == AuthenticityLabel.INSUFFICIENT_AUDIO) {
            return currentAssessment(latest = result)
        }

        if (windowHistory.size >= historyCapacity) {
            windowHistory.removeFirst()
        }
        windowHistory.addLast(result)

        // Track consecutive clone alerts
        if (result.syntheticScore >= cloneAlertThreshold) {
            consecutiveCloneCount++
        } else {
            consecutiveCloneCount = 0
        }

        return currentAssessment(latest = result)
    }

    private var latestConversationalRisk: ConversationalRiskResult? = null

    /**
     * Updates the conversational coercion risk from transcribed text.
     */
    @Synchronized
    fun processTranscriptRisk(risk: ConversationalRiskResult): RiskAssessment {
        latestConversationalRisk = risk
        return currentAssessment(latest = windowHistory.peekLast())
    }

    @Synchronized
    fun reset() {
        windowHistory.clear()
        consecutiveCloneCount = 0
        totalEvaluations = 0
        latestConversationalRisk = null
        AppLogger.d("[TrueVoice Risk] TemporalRiskEngine reset")
    }

    private fun currentAssessment(latest: AuthenticityResult?): RiskAssessment {
        if (windowHistory.size < minWindowsRequired) {
            return RiskAssessment(
                level = RiskLevel.INCONCLUSIVE,
                smoothedScore = latest?.syntheticScore ?: 0.0f,
                confidence = 0.3f,
                consecutiveAlertWindows = consecutiveCloneCount,
                totalEvaluatedWindows = totalEvaluations,
                latestResult = latest
            )
        }

        // Compute linearly weighted average (recent windows have higher weight)
        var totalWeight = 0.0f
        var weightedScoreSum = 0.0f
        var confidenceSum = 0.0f

        var weight = 1.0f
        for (item in windowHistory) {
            weightedScoreSum += (item.syntheticScore * weight)
            confidenceSum += item.confidence
            totalWeight += weight
            weight += 0.5f // Linearly increase weight for recent frames
        }

        val smoothedScore = (weightedScoreSum / totalWeight).coerceIn(0.0f, 1.0f)
        val avgConfidence = (confidenceSum / windowHistory.size).coerceIn(0.0f, 1.0f)

        var riskLevel = when {
            consecutiveCloneCount >= minConsecutiveAlerts || (smoothedScore >= cloneAlertThreshold && consecutiveCloneCount >= 2) -> {
                RiskLevel.CLONE_ALERT
            }
            smoothedScore >= cautionThreshold || consecutiveCloneCount > 0 -> {
                RiskLevel.CAUTION
            }
            else -> {
                RiskLevel.SAFE
            }
        }

        // If voice is genuine human but financial extortion / urgency coercion is detected, elevate to FINANCIAL_COERCION
        val convRisk = latestConversationalRisk
        if (riskLevel != RiskLevel.CLONE_ALERT && convRisk != null && convRisk.riskScore >= 0.55f) {
            riskLevel = RiskLevel.FINANCIAL_COERCION
        }

        return RiskAssessment(
            level = riskLevel,
            smoothedScore = smoothedScore,
            confidence = avgConfidence,
            consecutiveAlertWindows = consecutiveCloneCount,
            totalEvaluatedWindows = totalEvaluations,
            latestResult = latest,
            conversationalRisk = convRisk
        )
    }
}
