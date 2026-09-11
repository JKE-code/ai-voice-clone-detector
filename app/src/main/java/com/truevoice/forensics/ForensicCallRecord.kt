/*
 * True Voice: Real-Time On-Device AI Voice Clone & Scam Defense
 * Forensic Call Telemetry & Timeline Models
 */

package com.truevoice.forensics

import com.truevoice.ml.RiskLevel

/**
 * Individual second-by-second snapshot along the in-call forensic timeline.
 */
data class ForensicTimelinePoint(
    val timestampMs: Long,
    val offsetSeconds: Float,
    val syntheticScore: Float,
    val riskLevel: RiskLevel,
    val isVoiced: Boolean,
    val spectralCutoffDetected: Boolean = false,
    val triggeredKeyword: String? = null
)

/**
 * Comprehensive forensic audit record for a completed phone call.
 *
 * DPDP & Telecommunications Act Compliant:
 * Stores strictly acoustic forensic parameters, frequency characteristics,
 * risk scores, and temporal markers. Zero raw audio or audio recordings
 * are stored on disk.
 */
data class ForensicCallRecord(
    val callId: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val durationSeconds: Int,
    val callerNumber: String,
    val callerName: String? = null,
    val finalVerdict: RiskLevel,
    val peakSyntheticScore: Float,
    val averageSyntheticScore: Float,
    val totalWindowsAnalyzed: Int,
    val vocoderCutoffDetected: Boolean,
    val coercionDetected: Boolean,
    val triggeredKeywords: List<String> = emptyList(),
    val timeline: List<ForensicTimelinePoint> = emptyList()
)
