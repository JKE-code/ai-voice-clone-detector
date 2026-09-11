/*
 * True Voice: Real-Time On-Device AI Voice Clone & Scam Defense
 * Call Forensics & Threat Intelligence History Dashboard
 */

package com.truevoice.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.truevoice.forensics.ForensicCallRecord
import com.truevoice.forensics.ForensicRepository
import com.truevoice.ml.RiskLevel
import com.truevoice.ui.forensics.ForensicTimelineScreen
import com.truevoice.ui.theme.RiskColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class HistoryFilter {
    ALL,
    THREATS_ONLY,
    VERIFIED_HUMAN
}

/**
 * True Voice Call History & Threat Intelligence Screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallHistoryScreen(
    onBack: () -> Unit
) {
    val history by ForensicRepository.callHistory.collectAsState()
    var selectedRecord by remember { mutableStateOf<ForensicCallRecord?>(null) }
    var currentFilter by remember { mutableStateOf(HistoryFilter.ALL) }

    // If a call record is selected, show its deep Forensic Timeline
    if (selectedRecord != null) {
        ForensicTimelineScreen(
            record = selectedRecord!!,
            onBack = { selectedRecord = null }
        )
        return
    }

    val threatsCount = history.count { it.finalVerdict == RiskLevel.CLONE_ALERT || it.finalVerdict == RiskLevel.FINANCIAL_COERCION }
    val filteredList = when (currentFilter) {
        HistoryFilter.ALL -> history
        HistoryFilter.THREATS_ONLY -> history.filter { it.finalVerdict == RiskLevel.CLONE_ALERT || it.finalVerdict == RiskLevel.FINANCIAL_COERCION }
        HistoryFilter.VERIFIED_HUMAN -> history.filter { it.finalVerdict == RiskLevel.SAFE }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "True Voice Call Shield",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Real-Time AI Forensic History",
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = RiskColors.GlassBackground,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF0B0F19)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Shield Overview Banner ────────────────────────────────
            item {
                ShieldOverviewBanner(
                    totalCalls = history.size,
                    threatsBlocked = threatsCount
                )
            }

            // ── Filter Chips ──────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = currentFilter == HistoryFilter.ALL,
                        onClick = { currentFilter = HistoryFilter.ALL },
                        label = { Text("All Calls (${history.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF334155),
                            selectedLabelColor = Color.White
                        )
                    )
                    FilterChip(
                        selected = currentFilter == HistoryFilter.THREATS_ONLY,
                        onClick = { currentFilter = HistoryFilter.THREATS_ONLY },
                        label = { Text("Threats ($threatsCount)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RiskColors.CloneAlertRedContainer,
                            selectedLabelColor = RiskColors.CloneAlertRedText
                        )
                    )
                    FilterChip(
                        selected = currentFilter == HistoryFilter.VERIFIED_HUMAN,
                        onClick = { currentFilter = HistoryFilter.VERIFIED_HUMAN },
                        label = { Text("Verified") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RiskColors.SafeGreenContainer,
                            selectedLabelColor = RiskColors.SafeGreenText
                        )
                    )
                }
            }

            // ── Call History Items ────────────────────────────────────
            if (filteredList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No calls match current filter",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF64748B))
                        )
                    }
                }
            } else {
                items(filteredList, key = { it.callId }) { record ->
                    CallHistoryCard(
                        record = record,
                        onClick = { selectedRecord = record }
                    )
                }
            }
        }
    }
}

@Composable
private fun ShieldOverviewBanner(totalCalls: Int, threatsBlocked: Int) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, Color(0x3338BDF8), RoundedCornerShape(20.dp)),
        color = RiskColors.CardBackground
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF0284C7).copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Neural Shield: Active",
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "Zero-Trust Universal Analysis",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .background(RiskColors.SafeGreenContainer, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "PROTECTED",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = RiskColors.SafeGreenText,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                    .padding(vertical = 10.dp, horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                BannerStat(label = "Calls Screened", value = "$totalCalls", color = Color.White)
                BannerStat(label = "Threats Caught", value = "$threatsBlocked", color = if (threatsBlocked > 0) RiskColors.CloneAlertRed else Color(0xFF94A3B8))
                BannerStat(label = "UPI Protection", value = "AUTO-RETRACT", color = Color(0xFF38BDF8))
            }
        }
    }
}

@Composable
private fun BannerStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
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
private fun CallHistoryCard(
    record: ForensicCallRecord,
    onClick: () -> Unit
) {
    val verdictColor = RiskColors.getBorderColorForRisk(record.finalVerdict)
    val verdictContainer = RiskColors.getContainerColorForRisk(record.finalVerdict)
    val verdictText = RiskColors.getTextColorForRisk(record.finalVerdict)

    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.ENGLISH)
    val dateStr = dateFormat.format(Date(record.startTimeMs))

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RiskColors.CardBackground),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                // Risk Indicator Dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(verdictColor, CircleShape)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = record.callerName ?: record.callerNumber,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    )
                    Text(
                        text = "$dateStr • ${record.durationSeconds}s",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    )

                    if (record.coercionDetected) {
                        Text(
                            text = "⚠️ Extortion Keywords Flagged",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = RiskColors.CoercionOrange,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(verdictContainer, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    val badgeLabel = when (record.finalVerdict) {
                        RiskLevel.CLONE_ALERT -> "CLONE ${(record.peakSyntheticScore * 100).toInt()}%"
                        RiskLevel.FINANCIAL_COERCION -> "COERCION"
                        RiskLevel.CAUTION -> "CAUTION"
                        RiskLevel.SAFE -> "SAFE"
                        RiskLevel.INCONCLUSIVE -> "LOW AUDIO"
                    }
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = verdictText,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Inspect",
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
