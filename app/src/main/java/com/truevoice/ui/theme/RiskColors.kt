/*
 * True Voice: Real-Time On-Device AI Voice Clone & Scam Defense
 * True Voice Material 3 Security Color Tokens & Risk Themes
 */

package com.truevoice.ui.theme

import androidx.compose.ui.graphics.Color
import com.truevoice.ml.RiskLevel

object RiskColors {
    // 🟢 SAFE: Genuine Human Voice
    val SafeGreen = Color(0xFF10B981)
    val SafeGreenContainer = Color(0xFF064E3B)
    val SafeGreenText = Color(0xFFD1FAE5)

    // 🟡 CAUTION: Analyzing / Transient Suspicion
    val CautionAmber = Color(0xFFF59E0B)
    val CautionAmberContainer = Color(0xFF78350F)
    val CautionAmberText = Color(0xFFFEF3C7)

    // 🔴 CLONE ALERT: Confirmed Deepfake Voice Attack
    val CloneAlertRed = Color(0xFFEF4444)
    val CloneAlertRedContainer = Color(0xFF7F1D1D)
    val CloneAlertRedText = Color(0xFFFEE2E2)

    // 🟠 FINANCIAL COERCION: Real Voice but Extortion / Scammer Call
    val CoercionOrange = Color(0xFFF97316)
    val CoercionOrangeContainer = Color(0xFF7C2D12)
    val CoercionOrangeText = Color(0xFFFFEDD5)

    // ⚪ INCONCLUSIVE: Silent / Initial Analysis
    val InconclusiveGray = Color(0xFF6B7280)
    val InconclusiveContainer = Color(0xFF1F2937)
    val InconclusiveText = Color(0xFFF3F4F6)

    // Dark Glassmorphism Surface & Borders
    val GlassBackground = Color(0xE60F172A) // 90% opacity deep slate
    val GlassBorder = Color(0x33FFFFFF)     // 20% white subtle border
    val CardBackground = Color(0xFF1E293B)  // Slate 800

    fun getBorderColorForRisk(level: RiskLevel): Color = when (level) {
        RiskLevel.SAFE -> SafeGreen
        RiskLevel.CAUTION -> CautionAmber
        RiskLevel.CLONE_ALERT -> CloneAlertRed
        RiskLevel.FINANCIAL_COERCION -> CoercionOrange
        RiskLevel.INCONCLUSIVE -> InconclusiveGray
    }

    fun getContainerColorForRisk(level: RiskLevel): Color = when (level) {
        RiskLevel.SAFE -> SafeGreenContainer
        RiskLevel.CAUTION -> CautionAmberContainer
        RiskLevel.CLONE_ALERT -> CloneAlertRedContainer
        RiskLevel.FINANCIAL_COERCION -> CoercionOrangeContainer
        RiskLevel.INCONCLUSIVE -> InconclusiveContainer
    }

    fun getTextColorForRisk(level: RiskLevel): Color = when (level) {
        RiskLevel.SAFE -> SafeGreenText
        RiskLevel.CAUTION -> CautionAmberText
        RiskLevel.CLONE_ALERT -> CloneAlertRedText
        RiskLevel.FINANCIAL_COERCION -> CoercionOrangeText
        RiskLevel.INCONCLUSIVE -> InconclusiveText
    }
}
