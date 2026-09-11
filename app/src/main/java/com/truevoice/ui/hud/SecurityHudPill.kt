/*
 * True Voice: Real-Time On-Device AI Voice Clone & Scam Defense
 * True Voice In-Call Floating Security HUD (Compose Component)
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.truevoice.ml.RiskAssessment
import com.truevoice.ml.RiskLevel
import com.truevoice.ui.theme.RiskColors

/**
 * Floating Pill & Expanded Card Security HUD.
 *
 * Glassmorphism Material 3 capsule that renders on top of active calls:
 * - 🟢 SAFE: "Voice Verified • Human"
 * - 🟡 CAUTION: Pulsing amber "Analyzing acoustic markers..."
 * - 🔴 CLONE_ALERT: Expanded alert card "⚠️ AI Cloned Voice Detected (95% Synthetic)"
 * - 🟠 FINANCIAL_COERCION: "⚠️ Financial Coercion Detected"
 */
@Composable
fun SecurityHudPill(
    assessment: RiskAssessment,
    onDragY: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDismiss: () -> Unit,
    onOpenForensics: (() -> Unit)? = null
) {
    var isExpanded by remember { mutableStateOf(assessment.level == RiskLevel.CLONE_ALERT) }

    // Pulsing animation for warning states
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (assessment.level == RiskLevel.CLONE_ALERT || assessment.level == RiskLevel.FINANCIAL_COERCION) 1.15f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hudPulse"
    )

    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    val currentLevel = assessment.level
    val borderColor by animateColorAsState(
        targetValue = RiskColors.getBorderColorForRisk(currentLevel),
        animationSpec = tween(400),
        label = "hudBorder"
    )
    val containerColor by animateColorAsState(
        targetValue = RiskColors.getContainerColorForRisk(currentLevel),
        animationSpec = tween(400),
        label = "hudContainer"
    )

    Surface(
        modifier = Modifier
            .widthIn(min = 180.dp, max = 340.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDragY(dragAmount.y)
                    }
                )
            }
            .clip(RoundedCornerShape(24.dp))
            .border(width = 1.5.dp, color = borderColor, shape = RoundedCornerShape(24.dp))
            .clickable { isExpanded = !isExpanded },
        color = RiskColors.GlassBackground,
        shadowElevation = 10.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Header: Status Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                // Pulsing Status Dot
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .scale(if (currentLevel == RiskLevel.CLONE_ALERT || currentLevel == RiskLevel.CAUTION) pulseScale else 1.0f)
                        .background(color = borderColor.copy(alpha = dotAlpha), shape = CircleShape)
                )

                Spacer(modifier = Modifier.width(10.dp))

                // Status Title
                Column(modifier = Modifier.weight(1f)) {
                    val title = when (currentLevel) {
                        RiskLevel.SAFE -> "Voice Verified"
                        RiskLevel.CAUTION -> "Analyzing Voice..."
                        RiskLevel.CLONE_ALERT -> "CRITICAL: AI Clone"
                        RiskLevel.FINANCIAL_COERCION -> "Extortion Alert"
                        RiskLevel.INCONCLUSIVE -> "True Voice Shield"
                    }
                    val subtitle = when (currentLevel) {
                        RiskLevel.SAFE -> "100% Genuine Human"
                        RiskLevel.CAUTION -> "Checking synthetic markers"
                        RiskLevel.CLONE_ALERT -> "Synthetic Voice Attack"
                        RiskLevel.FINANCIAL_COERCION -> "Urgent Financial Pressure"
                        RiskLevel.INCONCLUSIVE -> "Listening to audio stream..."
                    }

                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = borderColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp
                        )
                    )
                }

                // Confidence badge or close
                Box(
                    modifier = Modifier
                        .background(color = containerColor, shape = RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    val scoreText = when (currentLevel) {
                        RiskLevel.CLONE_ALERT -> "${(assessment.smoothedScore * 100).toInt()}% CLONE"
                        RiskLevel.SAFE -> "SAFE"
                        RiskLevel.FINANCIAL_COERCION -> "SCAM"
                        RiskLevel.CAUTION -> "SCAN"
                        RiskLevel.INCONCLUSIVE -> "READY"
                    }
                    Text(
                        text = scoreText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = RiskColors.getTextColorForRisk(currentLevel),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp
                        )
                    )
                }
            }

            // Expandable Forensic Details (Expanded on CLONE_ALERT or User Tap)
            AnimatedVisibility(
                visible = isExpanded || currentLevel == RiskLevel.CLONE_ALERT,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .background(color = RiskColors.CardBackground, shape = RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    if (currentLevel == RiskLevel.CLONE_ALERT) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Alert",
                                tint = RiskColors.CloneAlertRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Synthetic vocoder patterns & flat pitch detected across ${assessment.consecutiveAlertWindows} windows.",
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
                                contentDescription = "Scam",
                                tint = RiskColors.CoercionOrange,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Coercion words detected: ${triggers.take(3).joinToString(", ")}",
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
                                text = "Downlink isolated • Zero disk footprint • DPDP compliant",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }

                    if (onOpenForensics != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenForensics() }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "Open Forensic Timeline ➜",
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
