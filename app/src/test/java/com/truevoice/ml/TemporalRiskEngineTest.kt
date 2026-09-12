package com.truevoice.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TemporalRiskEngineTest {

    private lateinit var riskEngine: TemporalRiskEngine
    private lateinit var coercionEngine: ConversationalCoercionEngine

    @Before
    fun setUp() {
        riskEngine = TemporalRiskEngine(
            historyCapacity = 8,
            minWindowsRequired = 2,
            minConsecutiveAlerts = 3,
            cloneAlertThreshold = 0.65f,
            cautionThreshold = 0.40f
        )
        coercionEngine = ConversationalCoercionEngine()
    }

    @Test
    fun testInitialWindows_areInconclusive() {
        val result1 = AuthenticityResult(
            syntheticScore = 0.95f,
            confidence = 0.90f,
            label = AuthenticityLabel.SYNTHETIC_CLONE,
            inferenceTimeMs = 3L
        )
        val assessment = riskEngine.processResult(result1)
        assertEquals("Single window must be inconclusive", RiskLevel.INCONCLUSIVE, assessment.level)
    }

    @Test
    fun testAiVoiceClone_triggersCloneAlert() {
        val cloneResult = AuthenticityResult(
            syntheticScore = 0.92f,
            confidence = 0.95f,
            label = AuthenticityLabel.SYNTHETIC_CLONE,
            inferenceTimeMs = 2L
        )

        // Process 3 consecutive windows of high synthetic score
        riskEngine.processResult(cloneResult)
        val assessment2 = riskEngine.processResult(cloneResult)
        assertEquals("2 windows above threshold triggers CLONE_ALERT", RiskLevel.CLONE_ALERT, assessment2.level)

        val assessment3 = riskEngine.processResult(cloneResult)
        assertEquals(RiskLevel.CLONE_ALERT, assessment3.level)
        assertTrue(assessment3.smoothedScore >= 0.85f)
        assertEquals(3, assessment3.consecutiveAlertWindows)
    }

    @Test
    fun testHumanSafeConversation_evaluatesToSafe() {
        val humanResult = AuthenticityResult(
            syntheticScore = 0.05f,
            confidence = 0.92f,
            label = AuthenticityLabel.GENUINE,
            inferenceTimeMs = 2L
        )

        riskEngine.processResult(humanResult)
        val assessment = riskEngine.processResult(humanResult)
        assertEquals(RiskLevel.SAFE, assessment.level)
        assertTrue(assessment.smoothedScore < 0.20f)

        // Add benign transcript
        val benignTranscript = coercionEngine.analyzeTranscript("Hey how are you, let us meet tomorrow for coffee")
        val finalAssessment = riskEngine.processTranscriptRisk(benignTranscript)
        assertEquals(RiskLevel.SAFE, finalAssessment.level)
    }

    @Test
    fun testHumanSpamExtortion_triggersFinancialCoercion() {
        val humanResult = AuthenticityResult(
            syntheticScore = 0.08f,
            confidence = 0.90f,
            label = AuthenticityLabel.GENUINE,
            inferenceTimeMs = 2L
        )

        // Voice is 100% human
        riskEngine.processResult(humanResult)
        val voiceAssessment = riskEngine.processResult(humanResult)
        assertEquals("Acoustically genuine voice is initially safe", RiskLevel.SAFE, voiceAssessment.level)

        // Feed Trilingual Coercion Transcript (Hindi/English police scam)
        val extortionRisk = coercionEngine.analyzeTranscript(
            "police thane me hu arrest kar liya send money immediately to UPI do not hang up"
        )
        val finalAssessment = riskEngine.processTranscriptRisk(extortionRisk)

        // Should escalate to FINANCIAL_COERCION even though voice is human!
        assertEquals("Human scammer must escalate to FINANCIAL_COERCION", RiskLevel.FINANCIAL_COERCION, finalAssessment.level)
        assertTrue("Extortion score must be high", extortionRisk.riskScore >= 0.70f)
    }

    @Test
    fun testTeluguHumanSpamExtortion_triggersFinancialCoercion() {
        val humanResult = AuthenticityResult(
            syntheticScore = 0.04f,
            confidence = 0.91f,
            label = AuthenticityLabel.GENUINE,
            inferenceTimeMs = 2L
        )

        // Voice is human
        riskEngine.processResult(humanResult)
        riskEngine.processResult(humanResult)

        // Telugu extortion transcript
        val teluguScam = coercionEngine.analyzeTranscript(
            "naaku tonderga paisalu pampu hospital lo unna accident ayyindi"
        )
        val assessment = riskEngine.processTranscriptRisk(teluguScam)

        assertEquals(RiskLevel.FINANCIAL_COERCION, assessment.level)
        assertTrue(teluguScam.categoriesDetected.contains(CoercionCategory.FINANCIAL_URGENCY))
        assertTrue(teluguScam.categoriesDetected.contains(CoercionCategory.FAMILY_EMERGENCY))
    }
}
