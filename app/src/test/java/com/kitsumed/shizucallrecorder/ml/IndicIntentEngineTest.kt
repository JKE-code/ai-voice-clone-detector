package com.kitsumed.shizucallrecorder.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IndicIntentEngineTest {

    @Test
    fun testHindiScamIntent() {
        val result = IndicIntentEngine.analyzeTranscript("maa jaldi pese bhej ye number ko mujhe madat chaiye")
        assertTrue(result.coercionScore >= 0.85f)
        assertEquals("CRITICAL", result.urgencyLevel)
        assertTrue(result.matchedCategories.contains("FINANCIAL_DEMAND"))
        assertTrue(result.matchedCategories.contains("URGENCY_PRESSURE"))
    }

    @Test
    fun testTeluguScamIntent() {
        val result = IndicIntentEngine.analyzeTranscript("naaku tonderga paisalu pampu hospital lo unna")
        assertTrue(result.coercionScore >= 0.85f)
        assertEquals("CRITICAL", result.urgencyLevel)
        assertTrue(result.matchedCategories.contains("FINANCIAL_DEMAND"))
        assertTrue(result.matchedCategories.contains("EMOTIONAL_EXTORTION"))
    }

    @Test
    fun testTamilScamIntent() {
        val result = IndicIntentEngine.analyzeTranscript("police station la irukken seekiram panam anupu")
        assertTrue(result.coercionScore >= 0.85f)
        assertEquals("CRITICAL", result.urgencyLevel)
        assertTrue(result.matchedCategories.contains("AUTHORITY_IMPERSONATION"))
    }

    @Test
    fun testSafeConversation() {
        val result = IndicIntentEngine.analyzeTranscript("kya haal hai kal milte hai dinner pe")
        assertEquals(0.05f, result.coercionScore, 0.01f)
        assertEquals("LOW", result.urgencyLevel)
        assertTrue(result.matchedCategories.isEmpty())
    }
}
