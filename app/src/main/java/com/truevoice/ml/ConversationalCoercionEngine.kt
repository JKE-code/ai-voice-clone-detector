/*
 * True Voice: Real-Time On-Device AI Voice Clone & Scam Defense
 * Conversational Coercion & Urgency NLP Engine
 */

package com.truevoice.ml

import com.kitsumed.shizucallrecorder.utils.AppLogger
import java.util.Locale

/**
 * Result of conversational coercion / scam pattern heuristic analysis.
 */
data class ConversationalRiskResult(
    val riskScore: Float, // 0.0 (benign) to 1.0 (severe extortion/coercion)
    val triggeredKeywords: List<String>,
    val categoriesDetected: Set<CoercionCategory>,
    val confidence: Float
)

enum class CoercionCategory {
    FINANCIAL_URGENCY,   // OTP, UPI, Immediate transfer, Google Pay, PhonePe
    AUTHORITY_PRESSURE,  // Police, Arrest, Cyber crime, CBI, ED, Court warrant
    FAMILY_EMERGENCY,    // Accident, Hospital, ICU, Kidnap, Ransom, In danger
    SECRECY_COERCION     // Don't hang up, Don't tell anyone, Keep this private
}

/**
 * Lightweight, Deterministic On-Device NLP / Pattern Heuristic Engine.
 *
 * Evaluates transcribed or keyword-matched text snippets from live call downlink
 * to identify real-world Indian cyber-scam coercion tactics (supporting English,
 * Hindi-transliterated / Hinglish keywords).
 */
class ConversationalCoercionEngine {

    companion object {
        // Indian Cyber-Scam Keyword Lexicon (English + Hinglish Transliteration)
        private val FINANCIAL_KEYWORDS = setOf(
            "otp", "pin", "cvv", "upi", "gpay", "google pay", "phonepe", "paytm",
            "immediate transfer", "send money", "bank account", "transfer now",
            "account blocked", "kyc expired", "paisa bhejo", "turant bhejo", "paise transfer"
        )

        private val AUTHORITY_KEYWORDS = setOf(
            "police", "arrest", "cbi", "ed", "crime branch", "cyber cell",
            "fir registered", "court warrant", "customs", "parcel seized",
            "drugs found", "police station", "thana", "hiraasat", "giraftaar"
        )

        private val EMERGENCY_KEYWORDS = setOf(
            "accident", "hospital", "icu", "emergency", "operation", "admitted",
            "injured", "blood required", "kidnapped", "life in danger",
            "jaan khatre mein", "bachao"
        )

        private val SECRECY_KEYWORDS = setOf(
            "don't tell anyone", "do not hang up", "stay on line", "keep call connected",
            "call mat kaatna", "kisi ko mat batana", "secret rakho"
        )
    }

    /**
     * Evaluates a text transcript snippet for coercion indicators.
     */
    fun analyzeTranscript(text: String): ConversationalRiskResult {
        if (text.isBlank()) {
            return ConversationalRiskResult(
                riskScore = 0.0f,
                triggeredKeywords = emptyList(),
                categoriesDetected = emptySet(),
                confidence = 0.5f
            )
        }

        val normalized = text.lowercase(Locale.ROOT)
        val triggered = mutableListOf<String>()
        val categories = mutableSetOf<CoercionCategory>()

        var totalWeight = 0.0f

        // Check Financial keywords (Weight 0.35 each category match)
        val matchedFinancial = FINANCIAL_KEYWORDS.filter { normalized.contains(it) }
        if (matchedFinancial.isNotEmpty()) {
            triggered.addAll(matchedFinancial)
            categories.add(CoercionCategory.FINANCIAL_URGENCY)
            totalWeight += 0.35f + (matchedFinancial.size - 1) * 0.05f
        }

        // Check Authority keywords (Weight 0.30)
        val matchedAuthority = AUTHORITY_KEYWORDS.filter { normalized.contains(it) }
        if (matchedAuthority.isNotEmpty()) {
            triggered.addAll(matchedAuthority)
            categories.add(CoercionCategory.AUTHORITY_PRESSURE)
            totalWeight += 0.30f + (matchedAuthority.size - 1) * 0.05f
        }

        // Check Emergency keywords (Weight 0.30)
        val matchedEmergency = EMERGENCY_KEYWORDS.filter { normalized.contains(it) }
        if (matchedEmergency.isNotEmpty()) {
            triggered.addAll(matchedEmergency)
            categories.add(CoercionCategory.FAMILY_EMERGENCY)
            totalWeight += 0.30f + (matchedEmergency.size - 1) * 0.05f
        }

        // Check Secrecy keywords (Weight 0.20)
        val matchedSecrecy = SECRECY_KEYWORDS.filter { normalized.contains(it) }
        if (matchedSecrecy.isNotEmpty()) {
            triggered.addAll(matchedSecrecy)
            categories.add(CoercionCategory.SECRECY_COERCION)
            totalWeight += 0.20f
        }

        // Compound bonus: if authority/emergency is combined with financial urgency (the classic extortion pattern)
        if (categories.contains(CoercionCategory.FINANCIAL_URGENCY) &&
            (categories.contains(CoercionCategory.AUTHORITY_PRESSURE) || categories.contains(CoercionCategory.FAMILY_EMERGENCY))
        ) {
            totalWeight += 0.25f
        }

        val score = totalWeight.coerceIn(0.0f, 1.0f)
        val confidence = if (triggered.isNotEmpty()) 0.85f else 0.50f

        AppLogger.d("[TrueVoice NLP] Evaluated text (${triggered.size} triggers): score=${"%.2f".format(score)}, categories=$categories")

        return ConversationalRiskResult(
            riskScore = score,
            triggeredKeywords = triggered,
            categoriesDetected = categories,
            confidence = confidence
        )
    }
}
