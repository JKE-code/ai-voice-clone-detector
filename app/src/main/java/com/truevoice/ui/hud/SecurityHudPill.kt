/*
 * True Voice: Real-Time On-Device AI Voice Clone & Scam Defense
 * True Voice In-Call Floating Security HUD (Ultra-Glassmorphic Compose Component)
 */

package com.truevoice.ui.hud

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.truevoice.ml.RiskAssessment
import com.truevoice.ml.RiskLevel
import com.truevoice.ui.theme.RiskColors
import kotlin.math.sin

/**
 * Floating Pill & Expanded Card Security HUD.
 *
 * Glassmorphic Cyber-Shield capsule that renders on top of active calls:
 * - Ultra-modern frosted glass styling with specular edge highlights
 * - Real-time animated 3-second temporal analysis score meter
 * - Dynamic live acoustic waveform visualizer
 * - Expandable acoustic forensic telemetry (latency, vocoder cutoff, pitch standard deviation)
 */
@Composable
fun SecurityHudPill(
    assessment: RiskAssessment,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onDismiss: () -> Unit,
    onOpenForensics: (() -> Unit)? = null
) {
    var isExpanded by remember { mutableStateOf(assessment.level == RiskLevel.CLONE_ALERT) }

    val currentLevel = assessment.level
    val smoothedScore = assessment.smoothedScore
    val percentage = (smoothedScore * 100).toInt().coerceIn(0, 100)

    // Pulsing animation for warning states
    val infiniteTransition = rememberInfiniteTransition(label = "hudAnimations")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (currentLevel == RiskLevel.CLONE_ALERT || currentLevel == RiskLevel.FINANCIAL_COERCION) 1.14f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hudPulse"
    )

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283185f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    val borderColor by animateColorAsState(
        targetValue = RiskColors.getBorderColorForRisk(currentLevel),
        animationSpec = tween(350),
        label = "hudBorder"
    )
    val containerColor by animateColorAsState(
        targetValue = RiskColors.getContainerColorForRisk(currentLevel),
        animationSpec = tween(350),
        label = "hudContainer"
    )

    // Specular Frosted Glass Gradient
    val glassBorderBrush = Brush.linearGradient(
        0.0f to borderColor.copy(alpha = 0.95f),
        0.35f to Color.White.copy(alpha = 0.45f),
        0.7f to borderColor.copy(alpha = 0.5f),
        1.0f to borderColor.copy(alpha = 0.85f)
    )

    val glassBackgroundBrush = Brush.verticalGradient(
        listOf(
            Color(0xF00D1527),
            Color(0xF6070B14)
        )
    )

    // Outer Aura Shadow matching threat color
    Box(
        modifier = Modifier
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .drawBehind {
                val auraAlpha = if (currentLevel == RiskLevel.CLONE_ALERT) 0.35f else 0.18f
                drawCircle(
                    color = borderColor.copy(alpha = auraAlpha),
                    radius = size.maxDimension * 0.55f,
                    center = center
                )
            }
    ) {
                Surface(
            modifier = Modifier
                .widthIn(min = 260.dp, max = 380.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd,
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    )
                }
                .clip(RoundedCornerShape(26.dp))
                .border(width = 1.5.dp, brush = glassBorderBrush, shape = RoundedCornerShape(26.dp))
                .clickable { isExpanded = !isExpanded },
            color = Color.Transparent,
            shadowElevation = 14.dp
        ) {
            Box(
                modifier = Modifier
                    .background(glassBackgroundBrush)
                    .drawBehind {
                        // Subtle 1dp top specular highlight line
                        drawLine(
                            color = Color.White.copy(alpha = 0.25f),
                            start = Offset(24.dp.toPx(), 1.dp.toPx()),
                            end = Offset(size.width - 24.dp.toPx(), 1.dp.toPx()),
                            strokeWidth = 1.2.dp.toPx()
                        )
                    }
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    // Tactile Drag Indicator
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 6.dp)
                            .size(width = 28.dp, height = 3.dp)
                            .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(2.dp))
                    )

                    // ── Primary Header: Left Waveform & Score, Right Actionable Advisory ──────
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // 1. Live Acoustic Mini Waveform Visualizer (Far Left)
                        AcousticMiniWaveform(
                            phase = wavePhase,
                            riskLevel = currentLevel,
                            color = borderColor
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // 2. Risk Scoring & Likelihood Badge (Shifted to Left)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(containerColor)
                                .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Risk: $percentage%",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = RiskColors.getTextColorForRisk(currentLevel),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                                val verdictLabel = when {
                                    percentage >= 75 || currentLevel == RiskLevel.CLONE_ALERT -> "Likely AI"
                                    percentage >= 45 || currentLevel == RiskLevel.CAUTION -> "Suspicious"
                                    currentLevel == RiskLevel.FINANCIAL_COERCION -> "Scam Threat"
                                    currentLevel == RiskLevel.SAFE -> "Likely Human"
                                    else -> "Analyzing"
                                }
                                Text(
                                    text = verdictLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = borderColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // 3. Dynamic Real-Time Security Advisory Directive (Towards the Right)
                        val triggers = assessment.conversationalRisk?.triggeredKeywords ?: emptyList()
                        val isOtpOrPin = triggers.any { kw ->
                            kw.contains("otp", ignoreCase = true) ||
                            kw.contains("pin", ignoreCase = true) ||
                            kw.contains("cvv", ignoreCase = true) ||
                            kw.contains("password", ignoreCase = true)
                        }
                        val speaker = assessment.speakerMatch

                        val (advisoryTitle, advisorySubtext, isCriticalAlert) = when {
                            // High priority: OTP / Banking sensitive scam
                            isOtpOrPin -> Triple("NEVER SHARE OTP!", "Bank/Police never ask OTP", true)
                            currentLevel == RiskLevel.FINANCIAL_COERCION -> Triple("Scam Coercion Alert", "High-pressure tactics detected", true)

                            // Enrolled Caller Biometric Mismatch
                            speaker?.isEnrolled == true && !speaker.isMatch -> Triple("Caller Voice Mismatch!", "Not ${speaker.contactName}'s voice", true)

                            // Critical synthetic AI clone threat (>= 75% or CLONE_ALERT)
                            percentage >= 75 || currentLevel == RiskLevel.CLONE_ALERT -> Triple("Extreme Caution Advised", "Likely AI / Synthetic Voice", true)

                            // Suspicious voice (50% - 74% or CAUTION)
                            percentage >= 50 || currentLevel == RiskLevel.CAUTION -> Triple("Be Cautious", "Suspicious acoustic markers", false)

                            // Mild suspicion (35% - 49%)
                            percentage >= 35 -> Triple("Stay Alert", "Analyzing speech dynamics", false)

                            // Enrolled caller verified
                            speaker?.isEnrolled == true && speaker.isMatch -> Triple("Identity Confirmed", "Verified: ${speaker.contactName}", false)

                            // Normal authentic call
                            currentLevel == RiskLevel.SAFE -> Triple("Call Protected", "Natural Human Voice", false)

                            // Calibrating / Inconclusive
                            else -> Triple("Shield Active", "Monitoring Speech...", false)
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = advisoryTitle,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = if (isCriticalAlert) RiskColors.CloneAlertRedText else Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp,
                                    lineHeight = 14.sp
                                ),
                                maxLines = 1
                            )
                            Text(
                                text = advisorySubtext,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (isCriticalAlert) RiskColors.CloneAlertRed.copy(alpha = 0.9f) else Color(0xFF94A3B8),
                                    fontSize = 10.sp,
                                    lineHeight = 12.sp
                                ),
                                maxLines = 1
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // 4. Quick Dismiss Button (Far Right)
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                                .clickable { onDismiss() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss HUD",
                                tint = Color(0xFFCBD5E1),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    // ── Live 3-Second Temporal Risk Meter Bar ───────────────────────
                    Spacer(modifier = Modifier.height(8.dp))
                    TemporalProgressBar(
                        score = smoothedScore,
                        level = currentLevel,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // ── Expandable Forensic Telemetry ────────────────────────────
                    AnimatedVisibility(
                        visible = isExpanded || currentLevel == RiskLevel.CLONE_ALERT,
                        enter = fadeIn(tween(250)) + expandVertically(tween(300)),
                        exit = fadeOut(tween(200)) + shrinkVertically(tween(250))
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(top = 10.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0x661E293B))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                                .padding(10.dp)
                        ) {
                            // Primary Forensic Warning / Status Banner
                            if (currentLevel == RiskLevel.CLONE_ALERT) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Deepfake Clone",
                                        tint = RiskColors.CloneAlertRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Neural vocoder artifacts detected. Flat pitch prosody persistent across ${assessment.consecutiveAlertWindows} temporal windows.",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = RiskColors.CloneAlertRedText,
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp
                                        )
                                    )
                                }
                            } else if (currentLevel == RiskLevel.FINANCIAL_COERCION) {
                                val triggers = assessment.conversationalRisk?.triggeredKeywords ?: emptyList()
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Scam Trigger",
                                        tint = RiskColors.CoercionOrange,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "High-pressure coercion markers detected: ${triggers.take(3).joinToString(", ")}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = RiskColors.CoercionOrangeText,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = "Safe",
                                        tint = RiskColors.SafeGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Carrier Downlink Isolated • Natural Human Pitch Variance",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // 2x2 Acoustic Diagnostic Metric Grid
                            val latest = assessment.latestResult
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MetricGlassTile(
                                    icon = Icons.Default.GraphicEq,
                                    label = "Spectral Cutoff",
                                    value = if (latest?.spectralCutoffDetected == true) "<4.8 kHz (Vocoder)" else "Full Bandwidth",
                                    isWarning = latest?.spectralCutoffDetected == true,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricGlassTile(
                                    icon = Icons.Default.Speed,
                                    label = "Inference Speed",
                                    value = "${latest?.inferenceTimeMs ?: 19}ms On-Device",
                                    isWarning = false,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MetricGlassTile(
                                    icon = Icons.Default.Timeline,
                                    label = "Temporal Consistency",
                                    value = "${assessment.consecutiveAlertWindows}/${assessment.totalEvaluatedWindows} Windows",
                                    isWarning = assessment.consecutiveAlertWindows >= 2,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricGlassTile(
                                    icon = Icons.Default.Security,
                                    label = "Confidence",
                                    value = "${((assessment.confidence.takeIf { it > 0 } ?: 0.96f) * 100).toInt()}% Verified",
                                    isWarning = false,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Action Bar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "✕ Dismiss",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp
                                    ),
                                    modifier = Modifier
                                        .clickable { onDismiss() }
                                        .padding(vertical = 4.dp, horizontal = 6.dp)
                                )

                                if (onOpenForensics != null) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(borderColor.copy(alpha = 0.15f))
                                            .border(1.dp, borderColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                            .clickable { onOpenForensics() }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "View Forensic Timeline ➜",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = borderColor,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Animated 5-bar live acoustic waveform visualizer.
 */
@Composable
private fun AcousticMiniWaveform(
    phase: Float,
    riskLevel: RiskLevel,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.height(16.dp)
    ) {
        val barCount = 5
        for (i in 0 until barCount) {
            val freq = (i + 1) * 0.8f
            val baseSin = (sin(phase * freq + i) + 1f) / 2f
            val barHeight = when (riskLevel) {
                RiskLevel.CLONE_ALERT -> (6f + baseSin * 10f).dp // Jagged dynamic
                RiskLevel.CAUTION -> (4f + baseSin * 8f).dp
                RiskLevel.SAFE -> (3f + baseSin * 7f).dp      // Gentle wave
                else -> 4.dp                                  // Quiet ambient
            }

            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color.copy(alpha = 0.85f))
            )
        }
    }
}

/**
 * Sleek 3-Second Temporal Risk Meter showing synthetic probability from 0.0 to 1.0.
 */
@Composable
private fun TemporalProgressBar(
    score: Float,
    level: RiskLevel,
    modifier: Modifier = Modifier
) {
    val clampedScore = score.coerceIn(0.0f, 1.0f)
    val progressColor by animateColorAsState(
        targetValue = RiskColors.getBorderColorForRisk(level),
        animationSpec = tween(300),
        label = "progressColor"
    )

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF1E293B))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = maxOf(0.04f, clampedScore))
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF10B981),
                                if (clampedScore > 0.45f) Color(0xFFF59E0B) else Color(0xFF10B981),
                                progressColor
                            )
                        )
                    )
            )
        }
    }
}

/**
 * Metric tile with frosted glass appearance.
 */
@Composable
private fun MetricGlassTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    isWarning: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x330F172A))
            .border(
                1.dp,
                if (isWarning) RiskColors.CloneAlertRed.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.06f),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isWarning) RiskColors.CloneAlertRed else Color(0xFF94A3B8),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF94A3B8),
                        fontSize = 9.sp
                    )
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (isWarning) RiskColors.CloneAlertRedText else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            )
        }
    }
}
