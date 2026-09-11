package com.kitsumed.shizucallrecorder.ml

import java.util.regex.Pattern

/**
 * Result of Multilingual Indic Intent Analysis.
 */
data class IndicIntentAssessment(
    val coercionScore: Float,            // 0.0 to 1.0
    val matchedCategories: List<String>, // ["FINANCIAL_DEMAND", "AUTHORITY_IMPERSONATION", ...]
    val matchedPatternsCount: Int,
    val urgencyLevel: String             // "CRITICAL", "HIGH", "MODERATE", "LOW"
)

/**
 * On-device multilingual Indic intent and coercion detection engine.
 * Covers major Indian languages (Hindi, Telugu, Tamil, Kannada, Marathi, Bengali, Indian English)
 * in both native speech transcripts and Romanized (Hinglish/Tenglish/etc.) text.
 */
object IndicIntentEngine {

    private data class CategoryConfig(
        val weight: Float,
        val patterns: List<Pattern>
    )

    private val categories = mapOf(
        "FINANCIAL_DEMAND" to CategoryConfig(
            weight = 0.95f,
            patterns = listOf(
                // English
                Pattern.compile("\\b(send|transfer|pay|give)\\s+(money|cash|funds|amount|rupees)\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\b(upi|gpay|phonepe|paytm|bhim|qr code)\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\b(share|send|tell)\\s+(otp|pin|cvv|password|code)\\b", Pattern.CASE_INSENSITIVE),
                // Hindi / Hinglish
                Pattern.compile("\\b(paise|pese|rupaye|rakam)\\s+(bhej|transfer|dal|dalo|de|do)\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\b(otp|pin)\\s+(bata|batao|de|bhejo)\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\b(account|khate)\\s+me(in)?\\s+(dal|bhej)\\b", Pattern.CASE_INSENSITIVE),
                // Telugu / Tenglish
                Pattern.compile("\\b(paisalu|dabbulu|money)\\s+(pampu|pampinchu|ivvu|veyyi|vei)\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\b(otp|pin)\\s+(cheppu|pampu|ivvu)\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\b(account|khata)\\s+lo\\s+(vei|veyyi|pampinchu)\\b", Pattern.CASE_INSENSITIVE),
                // Tamil / Tanglish
                Pattern.compile("\\b(panam|kaasu|rupees)\\s+(anupu|anupunga|kudu|thanga)\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\b(otp|pin)\\s+(sollu|anupu)\\b", Pattern.CASE_INSENSITIVE),
                // Kannada
                Pattern.compile("\\b(dhana|hana|dundu)\\s+(kalsi|kodi|haki)\\b", Pattern.CASE_INSENSITIVE),
                // Marathi
                Pattern.compile("\\b(paise|rupaye)\\s+(pathav|dya|transfer kara)\\b", Pattern.CASE_INSENSITIVE),
                // Bengali
                Pattern.compile("\\b(taka|poisa)\\s+(pathao|dao|transfer koro)\\b", Pattern.CASE_INSENSITIVE)
            )
        ),
        "AUTHORITY_IMPERSONATION" to CategoryConfig(
            weight = 0.90f,
            patterns = listOf(
                // English
                Pattern.compile("\\b(police|cbi|ed|customs|cyber\\s+crime|inspector|dsp|court|arrest|warrant|jail)\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\b(arrested|in\\s+custody|detained|fir\\s+registered)\\b", Pattern.CASE_INSENSITIVE),
                // Hindi / Hinglish
                Pattern.compile("\\b(police|thane|thaana|chowki)\\s+(me(in)?\\s+hu|pakad\\s+liya|arrest\\s+kar\\s+liya)\\b", Pattern.CASE_INSENSITIVE),
                // Telugu / Tenglish
                Pattern.compile("\\b(police|station)\\s+(lo\\s+unna|pattu\\s*kunnaru|arrest\\s+chesaru)\\b", Pattern.CASE_INSENSITIVE),
                // Tamil / Tanglish
                Pattern.compile("\\b(police|station)\\s+(la\\s+irukken|pidichitanga|arrest\\s+pannitanga)\\b", Pattern.CASE_INSENSITIVE)
            )
        ),
        "EMOTIONAL_EXTORTION" to CategoryConfig(
            weight = 0.85f,
            patterns = listOf(
                // English
                Pattern.compile("\\b(accident|hospital|icu|emergency|injured|kidnapped|save\\s+me|help\\s+me)\\b", Pattern.CASE_INSENSITIVE),
                // Hindi / Hinglish
                Pattern.compile("\\b(accident|hadsa)\\s+(ho\\s+gaya|hua)\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\b(hospital|aspatal)\\s+me(in)?\\s+hu\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\b(mujhe|meri)\\s+(madad|bachao|bacha\\s+lo)\\b", Pattern.CASE_INSENSITIVE),
                // Telugu / Tenglish
                Pattern.compile("\\b(accident|gundepotu)\\s+(ayyindi|jarigindi)\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\b(hospital|dawakana)\\s+lo\\s+unna\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\b(naaku|nannu)\\s+(help|kapadandi|madath)\\b", Pattern.CASE_INSENSITIVE),
                // Tamil / Tanglish
                Pattern.compile("\\b(accident)\\s+(aayiduchu|aachu)\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\b(hospital|aaspattiri)\\s+la\\s+irukken\\b", Pattern.CASE_INSENSITIVE)
            )
        ),
        "URGENCY_PRESSURE" to CategoryConfig(
            weight = 0.70f,
            patterns = listOf(
                // English
                Pattern.compile("\\b(immediately|right\\s+now|urgent|urgently|don't\\s+hang\\s+up)\\b", Pattern.CASE_INSENSITIVE),
                // Hindi / Hinglish
                Pattern.compile("\\b(jaldi|turant|abhi\\s+ke\\s+abhi|phone\\s+mat\\s+katna)\\b", Pattern.CASE_INSENSITIVE),
                // Telugu / Tenglish
                Pattern.compile("\\b(tonderga|ventane|ippude|call\\s+cut\\s+cheyoddu)\\b", Pattern.CASE_INSENSITIVE),
                // Tamil / Tanglish
                Pattern.compile("\\b(seekiram|udaney|ippovey|phone\\s+vekkatha)\\b", Pattern.CASE_INSENSITIVE)
            )
        )
    )

    /**
     * Analyzes live transcribed speech for coercion patterns across Indian languages.
     */
    fun analyzeTranscript(transcript: String?): IndicIntentAssessment {
        if (transcript.isNullOrBlank()) {
            return IndicIntentAssessment(0.0f, emptyList(), 0, "LOW")
        }

        val matchedCategories = mutableListOf<String>()
        val categoryScores = mutableListOf<Float>()
        var matchCount = 0

        for ((catName, config) in categories) {
            var catMatched = false
            for (pattern in config.patterns) {
                val matcher = pattern.matcher(transcript)
                if (matcher.find()) {
                    catMatched = true
                    matchCount++
                }
            }
            if (catMatched) {
                matchedCategories.add(catName)
                categoryScores.add(config.weight)
            }
        }

        val coercionScore = if (categoryScores.isEmpty()) {
            0.05f
        } else {
            val baseScore = categoryScores.maxOrNull() ?: 0.5f
            val compoundBoost = 0.15f * (categoryScores.size - 1)
            minOf(1.0f, baseScore + compoundBoost)
        }

        val urgency = when {
            coercionScore >= 0.85f -> "CRITICAL"
            coercionScore >= 0.65f -> "HIGH"
            coercionScore >= 0.35f -> "MODERATE"
            else -> "LOW"
        }

        return IndicIntentAssessment(
            coercionScore = coercionScore,
            matchedCategories = matchedCategories,
            matchedPatternsCount = matchCount,
            urgencyLevel = urgency
        )
    }
}
