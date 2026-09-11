package com.kitsumed.shizucallrecorder.ml

import com.kitsumed.shizucallrecorder.ml.model.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemporalRiskEngineTest {

    @Test
    fun testSafeVoiceEvaluation() {
        val engine = TemporalRiskEngine(
            smoothingAlpha = 0.35f,
            cloneAlertThreshold = 0.65f,
            safeThreshold = 0.35f
        )

        val assessment = engine.update(
            acousticSpoofScore = 0.10f,
            indicCoercionScore = 0.05f,
            speechRatio = 0.80f,
            latencyMs = 25L
        )

        assertEquals(RiskLevel.SAFE, assessment.riskLevel)
        assertTrue(assessment.smoothedScore < 0.35f)
        assertEquals(0, assessment.consecutiveSuspiciousWindows)
    }

    @Test
    fun testSilenceGuardrail_yieldsInconclusiveOrHolds() {
        val engine = TemporalRiskEngine()

        val assessment = engine.update(
            acousticSpoofScore = 0.99f,
            indicCoercionScore = 0.99f,
            speechRatio = 0.05f, // Silence/noise
            latencyMs = 10L
        )

        assertEquals(RiskLevel.INCONCLUSIVE, assessment.riskLevel)
        assertEquals(0, assessment.totalWindowsAnalyzed)
    }

    @Test
    fun testDualAnalysis_compoundSyntheticAndTeluguIntentTriggersAlert() {
        val engine = TemporalRiskEngine(
            smoothingAlpha = 0.50f,
            cloneAlertThreshold = 0.65f,
            safeThreshold = 0.35f,
            requiredConsecutiveAlerts = 2
        )

        // Telugu phrase intent: "naaku tonderga paisalu pampu" -> Coercion score = 1.0
        val intent = IndicIntentEngine.analyzeTranscript("naaku tonderga paisalu pampu hospital lo unna")
        assertEquals(1.0f, intent.coercionScore, 0.01f)

        // Window 1: High acoustic spoof score + high Telugu intent
        val assessment1 = engine.update(
            acousticSpoofScore = 0.85f,
            indicCoercionScore = intent.coercionScore,
            detectedCategories = intent.matchedCategories,
            speechRatio = 0.90f,
            latencyMs = 30L
        )
        // Compounded high score
        assertTrue(assessment1.smoothedScore >= 0.90f)
        assertTrue(assessment1.consecutiveSuspiciousWindows == 1)

        // Window 2: Another suspicious window -> triggers SUSPICIOUS_VOICE_CLONE immediately
        val assessment2 = engine.update(
            acousticSpoofScore = 0.90f,
            indicCoercionScore = intent.coercionScore,
            detectedCategories = intent.matchedCategories,
            speechRatio = 0.85f,
            latencyMs = 28L
        )
        assertEquals(RiskLevel.SUSPICIOUS_VOICE_CLONE, assessment2.riskLevel)
        assertEquals(2, assessment2.consecutiveSuspiciousWindows)
    }
}
