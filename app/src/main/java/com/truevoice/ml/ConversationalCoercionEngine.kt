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
        // Trilingual Indian Cyber-Scam Keyword Lexicon (English, Hindi/Hinglish, Telugu/Tenglish)
        private val FINANCIAL_KEYWORDS = setOf(
            // English
            "otp", "pin", "cvv", "upi", "gpay", "google pay", "phonepe", "paytm", "bhim", "qr code",
            "immediate transfer", "send money", "bank account", "transfer now", "account blocked", "kyc expired",
            // Hindi / Hinglish
            "paisa bhejo", "paise bhejo", "pese bhej", "rupaye bhej", "rupaye transfer", "khate me dal",
            "khate mein dal", "otp bata", "otp batao", "rakam transfer", "paise do", "turant bhejo", "paise transfer",
            // Telugu / Tenglish
            "paisalu pampu", "dabbulu pampu", "money pampu", "pampinchu", "otp cheppu", "khata lo vei",
            "khata lo veyyi", "khata lo pampinchu", "account lo pampu", "dabbulu ivvu", "paisalu ivvu", "pin cheppu"
        )

        private val AUTHORITY_KEYWORDS = setOf(
            // English
            "police", "arrest", "cbi", "ed", "crime branch", "cyber cell", "fir registered", "court warrant",
            "customs", "parcel seized", "drugs found", "police station", "inspector", "dsp", "court", "jail", "detained", "custody",
            // Hindi / Hinglish
            "thana", "thane me hu", "thaana", "hiraasat", "giraftaar", "case darj", "police pakad liya", "arrest kar liya", "chowki",
            // Telugu / Tenglish
            "police station lo unna", "station lo unna", "pattu kunnaru", "arrest chesaru", "case kattali", "jail nunchi", "station ki ravali"
        )

        private val EMERGENCY_KEYWORDS = setOf(
            // English
            "accident", "hospital", "icu", "emergency", "operation", "admitted", "injured", "blood required",
            "kidnapped", "life in danger", "save me", "help me",
            // Hindi / Hinglish
            "hadsa ho gaya", "hadsa hua", "jaan khatre mein", "bachao", "bacha lo", "aspatal me hu", "aspatal mein hu",
            "madad karo", "madat chaiye", "meri madad",
            // Telugu / Tenglish
            "accident ayyindi", "gundepotu", "jarigindi", "hospital lo unna", "dawakana lo unna", "kapadandi",
            "sahayam cheyandi", "naaku help", "nannu kapadandi"
        )

        private val SECRECY_KEYWORDS = setOf(
            // English
            "don't tell anyone", "do not hang up", "stay on line", "keep call connected", "immediately", "right now", "urgent", "urgently",
            // Hindi / Hinglish
            "call mat kaatna", "phone mat katna", "kisi ko mat batana", "secret rakho", "turant", "jaldi", "abhi ke abhi",
            // Telugu / Tenglish
            "call cut cheyoddu", "phone pettoddu", "evariki cheppoddu", "tonderga", "ventane", "ippude"
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
