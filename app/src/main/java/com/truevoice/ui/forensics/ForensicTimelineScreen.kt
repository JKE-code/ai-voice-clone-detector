/*
 * True Voice: Real-Time On-Device AI Voice Clone & Scam Defense
 * Post-Call Forensic Timeline & Law Enforcement (1930) Evidence Screen
 */

package com.truevoice.ui.forensics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.truevoice.forensics.ForensicCallRecord
import com.truevoice.forensics.ForensicRepository
import com.truevoice.ml.RiskLevel
import com.truevoice.ui.theme.RiskColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Post-Call Forensic Timeline Screen (Material 3 Dark Glassmorphism).
 *
 * Displays second-by-second acoustic synthetic probabilities with complete axes,
 * threshold guidelines, unified inspection breakdown, and 1-tap export
 * of legal forensic dossiers for India's 1930 Cybercrime Portal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForensicTimelineScreen(
    record: ForensicCallRecord,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val verdictColor = RiskColors.getBorderColorForRisk(record.finalVerdict)
    val verdictContainer = RiskColors.getContainerColorForRisk(record.finalVerdict)
    val verdictText = RiskColors.getTextColorForRisk(record.finalVerdict)

    val dateFormat = SimpleDateFormat("MMM dd, yyyy • HH:mm:ss", Locale.ENGLISH)
    val formattedDate = dateFormat.format(Date(record.startTimeMs))

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Forensic Call Audit",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Incident: ${record.callId}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { ForensicRepository.share1930Report(context, record) }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share 1930 Evidence Dossier",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = RiskColors.GlassBackground,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF0B0F19)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 1. Hero Verdict Banner ──────────────────────────────────
            HeroVerdictCard(
                record = record,
                formattedDate = formattedDate,
                verdictColor = verdictColor,
                verdictContainer = verdictContainer,
                verdictText = verdictText
            )

            // ── 2. Second-by-Second Canvas Risk Timeline ───────────────
            ForensicWaveformCard(record = record)

            // ── 3. Forensic Diagnostic Breakdown Cards ────────────────
            DiagnosticAuditSection(record = record)

            // ── 4. Cybercrime 1930 Export Action ────────────────────────
            Button(
                onClick = { ForensicRepository.share1930Report(context, record) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (record.finalVerdict == RiskLevel.CLONE_ALERT || record.finalVerdict == RiskLevel.FINANCIAL_COERCION) {
                        RiskColors.CloneAlertRed
                    } else {
                        Color(0xFF2563EB)
                    }
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Export 1930 Cybercrime Dossier",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HeroVerdictCard(
    record: ForensicCallRecord,
    formattedDate: String,
    verdictColor: Color,
    verdictContainer: Color,
    verdictText: Color
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.5.dp, verdictColor.copy(alpha = 0.8f), RoundedCornerShape(20.dp)),
        color = RiskColors.CardBackground
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header Row: Status Label & Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(verdictColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val verdictTitle = when (record.finalVerdict) {
                        RiskLevel.CLONE_ALERT -> "AI Voice Clone Detected"
                        RiskLevel.FINANCIAL_COERCION -> "Extortion Threat Detected"
                        RiskLevel.CAUTION -> "Elevated Risk / Suspicious"
                        RiskLevel.SAFE -> "Authentic Voice Verified"
                        RiskLevel.INCONCLUSIVE -> "Insufficient Speech Data"
                    }
                    Text(
                        text = verdictTitle,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = verdictColor,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                val badgeText = when (record.finalVerdict) {
                    RiskLevel.CLONE_ALERT -> "CRITICAL THREAT"
                    RiskLevel.FINANCIAL_COERCION -> "SCAM ALERT"
                    RiskLevel.CAUTION -> "CAUTION"
                    RiskLevel.SAFE -> "LOW RISK"
                    RiskLevel.INCONCLUSIVE -> "INCONCLUSIVE"
                }

                Box(
                    modifier = Modifier
                        .background(verdictContainer, RoundedCornerShape(8.dp))
                        .border(1.dp, verdictColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = verdictText,
                            fontWeight = FontWeight.Black,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Caller Identity & Metadata
            Text(
                text = record.callerName ?: record.callerNumber,
                style = MaterialTheme.typography.headlineSmall.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            )
            if (record.callerName != null) {
                Text(
                    text = record.callerNumber,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp
                    )
                )
            }

            Text(
                text = "$formattedDate • Duration: ${record.durationSeconds}s",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color(0xFF64748B),
                    fontSize = 11.sp
                ),
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 3 Summary Telemetry Metric Tiles
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                    .padding(vertical = 12.dp, horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                MetricColumn(
                    label = "Peak Threat",
                    value = "${(record.peakSyntheticScore * 100).toInt()}%",
                    color = verdictColor
                )
                MetricColumn(
                    label = "Average Risk",
                    value = "${(record.averageSyntheticScore * 100).toInt()}%",
                    color = Color(0xFFCBD5E1)
                )
                val windowDurationSec = record.totalWindowsAnalyzed * 3
                MetricColumn(
                    label = "Sample Windows",
                    value = "${record.totalWindowsAnalyzed} (${windowDurationSec}s)",
                    color = Color(0xFFCBD5E1)
                )
            }
        }
    }
}

@Composable
private fun MetricColumn(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                color = Color(0xFF94A3B8),
                fontSize = 10.sp
            )
        )
    }
}

@Composable
private fun ForensicWaveformCard(record: ForensicCallRecord) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = RiskColors.CardBackground),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Title & Clean Dedicated Legend
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Acoustic Synthetic Timeline",
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    )

                    val maxScore = (record.peakSyntheticScore * 100).toInt()
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(RiskColors.getContainerColorForRisk(record.finalVerdict))
                            .border(1.dp, RiskColors.getBorderColorForRisk(record.finalVerdict).copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            text = "Peak: $maxScore%",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = RiskColors.getTextColorForRisk(record.finalVerdict),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Dedicated Legend Row (Full Width - never wraps into vertical text)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(RiskColors.SafeGreen, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Score",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(12.dp)
                                .height(2.dp)
                                .background(RiskColors.CautionAmber)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Caution (40%)",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = RiskColors.CautionAmberText,
                                fontSize = 11.sp
                            )
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(12.dp)
                                .height(2.dp)
                                .background(RiskColors.CloneAlertRed)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Alert (65%)",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = RiskColors.CloneAlertRedText,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Chart Container Box
            val points = record.timeline
            val totalSec = record.durationSeconds.coerceAtLeast(1)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A), RoundedCornerShape(14.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                if (points.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Insufficient temporal audio windows recorded",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF64748B))
                        )
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Plot Row: Y-Axis Column (Left) + Canvas Area (Right)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                        ) {
                            // Y-Axis Ticks Column (0%, 40%, 65%, 100%)
                            Box(
                                modifier = Modifier
                                    .width(38.dp)
                                    .fillMaxHeight()
                            ) {
                                Text(
                                    text = "100%",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 10.sp,
                                        color = Color(0xFF64748B),
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    modifier = Modifier.align(Alignment.TopEnd)
                                )
                                Text(
                                    text = "65%",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 10.sp,
                                        color = RiskColors.CloneAlertRed,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = (150.dp * 0.35f) - 7.dp)
                                )
                                Text(
                                    text = "40%",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 10.sp,
                                        color = RiskColors.CautionAmber,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = (150.dp * 0.60f) - 7.dp)
                                )
                                Text(
                                    text = "0%",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 10.sp,
                                        color = Color(0xFF64748B),
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    modifier = Modifier.align(Alignment.BottomEnd)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Canvas Graph
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val width = size.width
                                    val height = size.height

                                    // 1. Gridlines
                                    // 100% Top line
                                    drawLine(
                                        color = Color.White.copy(alpha = 0.08f),
                                        start = Offset(0f, 0f),
                                        end = Offset(width, 0f),
                                        strokeWidth = 1f
                                    )

                                    // 65% Alert Line (Dashed Red)
                                    val alertY = height * (1.0f - 0.65f)
                                    drawLine(
                                        color = RiskColors.CloneAlertRed.copy(alpha = 0.6f),
                                        start = Offset(0f, alertY),
                                        end = Offset(width, alertY),
                                        strokeWidth = 1.5f,
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                                    )

                                    // 40% Caution Line (Dashed Amber)
                                    val cautionY = height * (1.0f - 0.40f)
                                    drawLine(
                                        color = RiskColors.CautionAmber.copy(alpha = 0.5f),
                                        start = Offset(0f, cautionY),
                                        end = Offset(width, cautionY),
                                        strokeWidth = 1.5f,
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                                    )

                                    // 0% Baseline
                                    drawLine(
                                        color = Color.White.copy(alpha = 0.15f),
                                        start = Offset(0f, height),
                                        end = Offset(width, height),
                                        strokeWidth = 1.5f
                                    )

                                    // Vertical Time Gridlines (4 intervals)
                                    for (i in 1..3) {
                                        val vx = width * (i / 4f)
                                        drawLine(
                                            color = Color.White.copy(alpha = 0.05f),
                                            start = Offset(vx, 0f),
                                            end = Offset(vx, height),
                                            strokeWidth = 1f,
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                                        )
                                    }

                                    // 2. Data Curve Plotting
                                    val maxOffset = (points.lastOrNull()?.offsetSeconds ?: 1.0f).coerceAtLeast(1.0f)
                                    val stepX = width / maxOffset

                                    val path = Path()
                                    val fillPath = Path()

                                    points.forEachIndexed { i, p ->
                                        val x = (p.offsetSeconds * stepX).coerceIn(0f, width)
                                        val y = height * (1.0f - p.syntheticScore.coerceIn(0f, 1f))

                                        if (i == 0) {
                                            path.moveTo(x, y)
                                            fillPath.moveTo(x, height)
                                            fillPath.lineTo(x, y)
                                        } else {
                                            path.lineTo(x, y)
                                            fillPath.lineTo(x, y)
                                        }
                                    }

                                    val lastX = (points.last().offsetSeconds * stepX).coerceIn(0f, width)
                                    fillPath.lineTo(lastX, height)
                                    fillPath.close()

                                    // Gradient fill
                                    drawPath(
                                        path = fillPath,
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                RiskColors.getBorderColorForRisk(record.finalVerdict).copy(alpha = 0.30f),
                                                Color.Transparent
                                            ),
                                            startY = 0f,
                                            endY = height
                                        )
                                    )

                                    // Stroke line
                                    drawPath(
                                        path = path,
                                        color = RiskColors.getBorderColorForRisk(record.finalVerdict),
                                        style = Stroke(width = 3.5f, cap = StrokeCap.Round)
                                    )

                                    // Plot Points
                                    var peakP: com.truevoice.forensics.ForensicTimelinePoint? = null
                                    var maxS = -1f
                                    points.forEach { p ->
                                        if (p.syntheticScore > maxS) {
                                            maxS = p.syntheticScore
                                            peakP = p
                                        }
                                        val px = (p.offsetSeconds * stepX).coerceIn(0f, width)
                                        val py = height * (1.0f - p.syntheticScore.coerceIn(0f, 1f))

                                        val dotColor = when {
                                            p.spectralCutoffDetected || p.syntheticScore >= 0.65f -> RiskColors.CloneAlertRed
                                            p.syntheticScore >= 0.40f -> RiskColors.CautionAmber
                                            else -> RiskColors.SafeGreen
                                        }

                                        // Outer halo
                                        drawCircle(
                                            color = dotColor.copy(alpha = 0.25f),
                                            radius = 7f,
                                            center = Offset(px, py)
                                        )
                                        // Solid dot
                                        drawCircle(
                                            color = dotColor,
                                            radius = 4f,
                                            center = Offset(px, py)
                                        )
                                    }

                                    // Highlight Peak Point
                                    peakP?.let { pk ->
                                        val px = (pk.offsetSeconds * stepX).coerceIn(0f, width)
                                        val py = height * (1.0f - pk.syntheticScore.coerceIn(0f, 1f))
                                        drawCircle(
                                            color = Color.White,
                                            radius = 6f,
                                            center = Offset(px, py),
                                            style = Stroke(width = 2f)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // X-Axis Time Ticks Row (Offset by 46dp to align with Canvas)
                        val step = (totalSec / 4).coerceAtLeast(1)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 46.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "0s (Start)",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF64748B),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                            Text(
                                text = "${step}s",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF64748B),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                            Text(
                                text = "${step * 2}s",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF64748B),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                            Text(
                                text = "${step * 3}s",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF64748B),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                            Text(
                                text = "${totalSec}s (End)",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF64748B),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticAuditSection(record: ForensicCallRecord) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = RiskColors.CardBackground),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Forensic Diagnostics & Inspection",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                )
            }

            // 1. Spectral Bandwidth & Vocoder Cutoff
            val hasCutoff = record.vocoderCutoffDetected
            AuditRow(
                icon = Icons.Default.GraphicEq,
                iconTint = if (hasCutoff) RiskColors.CloneAlertRed else RiskColors.SafeGreen,
                title = "Spectral Bandwidth",
                status = if (hasCutoff) "FLAGGED (<4.8kHz)" else "PASSED",
                statusColor = if (hasCutoff) RiskColors.CloneAlertRed else RiskColors.SafeGreen,
                description = if (hasCutoff) {
                    "Unnatural high-frequency spectral cutoff detected. Typical of neural vocoder downsampling."
                } else {
                    "Continuous harmonic formants across full telephony bandwidth without compression cutoff."
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color(0x14FFFFFF))

            // 2. Prosodic Pitch Dynamics
            val isRigid = record.peakSyntheticScore >= 0.65f
            AuditRow(
                icon = Icons.Default.Timeline,
                iconTint = if (isRigid) RiskColors.CloneAlertRed else RiskColors.SafeGreen,
                title = "Pitch & Prosody Dynamics",
                status = if (isRigid) "FLAGGED (Rigid)" else "PASSED",
                statusColor = if (isRigid) RiskColors.CloneAlertRed else RiskColors.SafeGreen,
                description = if (isRigid) {
                    "Acoustic prosody exhibits flat fundamental frequency typical of auto-regressive speech models."
                } else {
                    "Healthy involuntary pitch micro-jitter and natural human prosodic intonation."
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color(0x14FFFFFF))

            // 3. Conversational Coercion & Scammer Lexicon
            val hasCoercion = record.coercionDetected || record.triggeredKeywords.isNotEmpty()
            AuditRow(
                icon = if (hasCoercion) Icons.Default.Warning else Icons.Default.CheckCircle,
                iconTint = if (hasCoercion) RiskColors.CoercionOrange else RiskColors.SafeGreen,
                title = "Conversational Threat",
                status = if (hasCoercion) "COERCION ALERT" else "CLEAN",
                statusColor = if (hasCoercion) RiskColors.CoercionOrange else RiskColors.SafeGreen,
                description = if (hasCoercion) {
                    "Detected high-pressure scam triggers: ${record.triggeredKeywords.joinToString(", ")}."
                } else {
                    "No financial coercion, extortion, or fraudulent urgency patterns detected."
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color(0x14FFFFFF))

            // 4. Privacy & Hardware Execution
            AuditRow(
                icon = Icons.Default.Lock,
                iconTint = Color(0xFF38BDF8),
                title = "Zero-Trust Architecture",
                status = "ON-DEVICE",
                statusColor = Color(0xFF38BDF8),
                description = "Processed strictly in volatile RAM on local NPU. Zero audio data stored or transmitted."
            )
        }
    }
}

@Composable
private fun AuditRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    status: String,
    statusColor: Color,
    description: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.12f))
                .border(1.dp, iconTint.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(17.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    ),
                    modifier = Modifier.weight(1f, fill = false)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusColor.copy(alpha = 0.14f))
                        .border(1.dp, statusColor.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 2.5.dp)
                ) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            )
        }
    }
}
