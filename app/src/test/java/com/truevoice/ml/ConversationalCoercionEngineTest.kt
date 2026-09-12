package com.truevoice.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationalCoercionEngineTest {

    private val engine = ConversationalCoercionEngine()

    @Test
    fun testEnglishScamExtortion() {
        val result = engine.analyzeTranscript("I am police officer from cyber cell, transfer money to UPI immediately or arrest warrant will be issued")
        assertTrue("Expected high risk score for English police extortion", result.riskScore >= 0.70f)
        assertTrue(result.categoriesDetected.contains(CoercionCategory.FINANCIAL_URGENCY))
        assertTrue(result.categoriesDetected.contains(CoercionCategory.AUTHORITY_PRESSURE))
        assertTrue(result.triggeredKeywords.contains("upi"))
        assertTrue(result.triggeredKeywords.contains("police"))
    }

    @Test
    fun testHindiScamExtortion() {
        val result = engine.analyzeTranscript("maa jaldi pese bhej ye number pe mujhe police thane me hu aur arrest kar liya")
        assertTrue("Expected high risk score for Hindi extortion", result.riskScore >= 0.70f)
        assertTrue(result.categoriesDetected.contains(CoercionCategory.FINANCIAL_URGENCY))
        assertTrue(result.categoriesDetected.contains(CoercionCategory.AUTHORITY_PRESSURE))
        assertTrue(result.triggeredKeywords.contains("pese bhej"))
        assertTrue(result.triggeredKeywords.contains("thane me hu"))
    }

    @Test
    fun testTeluguScamExtortion() {
        val result = engine.analyzeTranscript("naaku tonderga paisalu pampu hospital lo unna accident ayyindi call cut cheyoddu")
        assertTrue("Expected high risk score for Telugu extortion", result.riskScore >= 0.70f)
        assertTrue(result.categoriesDetected.contains(CoercionCategory.FINANCIAL_URGENCY))
        assertTrue(result.categoriesDetected.contains(CoercionCategory.FAMILY_EMERGENCY))
        assertTrue(result.triggeredKeywords.contains("paisalu pampu"))
        assertTrue(result.triggeredKeywords.contains("hospital lo unna"))
        assertTrue(result.triggeredKeywords.contains("accident ayyindi"))
    }

    @Test
    fun testBenignConversations_remainSafe() {
        val resultEnglish = engine.analyzeTranscript("Hey let us catch up for lunch tomorrow around noon")
        assertEquals(0.0f, resultEnglish.riskScore, 0.01f)
        assertTrue(resultEnglish.categoriesDetected.isEmpty())

        val resultHindi = engine.analyzeTranscript("kya haal hai bhai kal sham ko milte hai ghar pe")
        assertEquals(0.0f, resultHindi.riskScore, 0.01f)
        assertTrue(resultHindi.categoriesDetected.isEmpty())

        val resultTelugu = engine.analyzeTranscript("ela unnaru repu office ki veltunnava dinner tinnava")
        assertEquals(0.0f, resultTelugu.riskScore, 0.01f)
        assertTrue(resultTelugu.categoriesDetected.isEmpty())
    }
}
