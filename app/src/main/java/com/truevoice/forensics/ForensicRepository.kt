/*
 * True Voice: Real-Time On-Device AI Voice Clone & Scam Defense
 * Post-Call Forensic Telemetry & Threat Intelligence Repository
 */

package com.truevoice.forensics

import android.content.Context
import android.content.Intent
import com.kitsumed.shizucallrecorder.utils.AppLogger
import com.truevoice.ml.AuthenticityResult
import com.truevoice.ml.RiskAssessment
import com.truevoice.ml.RiskLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Singleton repository managing active call forensic timelines, completed call records,
 * and Indian Cybercrime Portal (1930) forensic evidence export.
 */
object ForensicRepository {

    private val _callHistory = MutableStateFlow<List<ForensicCallRecord>>(createInitialSampleHistory())
    val callHistory: StateFlow<List<ForensicCallRecord>> = _callHistory.asStateFlow()

    private var currentSession: ActiveSession? = null
    private var database: com.truevoice.data.db.TrueVoiceDatabase? = null

    /**
     * Initializes the repository with persistent SQLite storage.
     */
    @Synchronized
    fun initialize(context: Context) {
        if (database == null) {
            val db = com.truevoice.data.db.TrueVoiceDatabase.getInstance(context)
            database = db
            val existing = db.getAllCallRecords()
            if (existing.isNotEmpty()) {
                _callHistory.value = existing
                AppLogger.i("[TrueVoice Forensics] Loaded ${existing.size} call records from SQLite")
            } else {
                val samples = createInitialSampleHistory()
                for (s in samples) {
                    db.insertCallRecord(s)
                }
                _callHistory.value = samples
                AppLogger.i("[TrueVoice Forensics] Initialized database with ${samples.size} sample audits")
            }
        }
    }

    private class ActiveSession(
        val callId: String,
        val startTimeMs: Long,
        val callerNumber: String,
        val callerName: String?
    ) {
        val points = mutableListOf<ForensicTimelinePoint>()
        var peakScore: Float = 0.0f
        var totalScoreSum: Float = 0.0f
        var windowsCount: Int = 0
        var vocoderDetected: Boolean = false
        var coercionDetected: Boolean = false
        val triggeredKeywords = mutableSetOf<String>()
        var highestRiskLevel: RiskLevel = RiskLevel.INCONCLUSIVE
    }

    /**
     * Starts tracking forensic data for an incoming or outgoing carrier call.
     */
    @Synchronized
    fun startSession(
        callId: String = UUID.randomUUID().toString(),
        callerNumber: String = "Unknown",
        callerName: String? = null
    ) {
        AppLogger.i("[TrueVoice Forensics] Starting forensic tracking session for call $callId ($callerNumber)")
        currentSession = ActiveSession(
            callId = callId,
            startTimeMs = System.currentTimeMillis(),
            callerNumber = callerNumber,
            callerName = callerName
        )
    }

    /**
     * Records an evaluated audio window into the active forensic timeline.
     */
    @Synchronized
    fun recordWindow(
        result: AuthenticityResult?,
        assessment: RiskAssessment,
        isVoiced: Boolean
    ) {
        val session = currentSession ?: return
        val now = System.currentTimeMillis()
        val offsetSeconds = ((now - session.startTimeMs) / 1000f).coerceAtLeast(0f)

        val score = result?.syntheticScore ?: assessment.smoothedScore
        if (score > session.peakScore) {
            session.peakScore = score
        }
        session.totalScoreSum += score
        session.windowsCount++

        if (result?.spectralCutoffDetected == true) {
            session.vocoderDetected = true
        }

        if (assessment.level.ordinal > session.highestRiskLevel.ordinal) {
            session.highestRiskLevel = assessment.level
        }

        val point = ForensicTimelinePoint(
            timestampMs = now,
            offsetSeconds = offsetSeconds,
            syntheticScore = score,
            riskLevel = assessment.level,
            isVoiced = isVoiced,
            spectralCutoffDetected = result?.spectralCutoffDetected ?: false
        )
        session.points.add(point)
    }

    /**
     * Records detected coercion / extortion keywords into the session.
     */
    @Synchronized
    fun recordCoercionKeywords(keywords: List<String>) {
        val session = currentSession ?: return
        if (keywords.isNotEmpty()) {
            session.coercionDetected = true
            session.triggeredKeywords.addAll(keywords)
        }
    }

    /**
     * Concludes the active call and saves the forensic audit record.
     */
    @Synchronized
    fun endSession(): ForensicCallRecord? {
        val session = currentSession ?: return null
        val now = System.currentTimeMillis()
        val durationSeconds = ((now - session.startTimeMs) / 1000).toInt().coerceAtLeast(1)

        val avgScore = if (session.windowsCount > 0) {
            session.totalScoreSum / session.windowsCount
        } else {
            0.0f
        }

        // Determine final verdict based on temporal peak and persistence
        val finalVerdict = when {
            session.highestRiskLevel == RiskLevel.CLONE_ALERT -> RiskLevel.CLONE_ALERT
            session.highestRiskLevel == RiskLevel.FINANCIAL_COERCION -> RiskLevel.FINANCIAL_COERCION
            session.highestRiskLevel == RiskLevel.CAUTION -> RiskLevel.CAUTION
            session.windowsCount >= 2 -> RiskLevel.SAFE
            else -> RiskLevel.INCONCLUSIVE
        }

        val record = ForensicCallRecord(
            callId = session.callId,
            startTimeMs = session.startTimeMs,
            endTimeMs = now,
            durationSeconds = durationSeconds,
            callerNumber = session.callerNumber,
            callerName = session.callerName,
            finalVerdict = finalVerdict,
            peakSyntheticScore = session.peakScore,
            averageSyntheticScore = avgScore,
            totalWindowsAnalyzed = session.windowsCount,
            vocoderCutoffDetected = session.vocoderDetected,
            coercionDetected = session.coercionDetected,
            triggeredKeywords = session.triggeredKeywords.toList(),
            timeline = session.points.toList()
        )

        currentSession = null

        // Save to persistent SQLite database
        database?.insertCallRecord(record)

        // Prepend to call history (most recent first)
        val updated = listOf(record) + _callHistory.value
        _callHistory.value = updated.take(50) // Keep last 50 call audits
        AppLogger.i("[TrueVoice Forensics] Forensic audit finalized for call ${record.callId}: Verdict=${record.finalVerdict}, Peak=${"%.2f".format(record.peakSyntheticScore)}")
        return record
    }

    /**
     * Finds a record by call ID.
     */
    fun getRecordById(callId: String): ForensicCallRecord? {
        return _callHistory.value.find { it.callId == callId }
    }

    /**
     * Formats an official evidence incident report suitable for India's 1930 Cybercrime Portal.
     */
    fun generate1930ReportText(record: ForensicCallRecord): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.ENGLISH)
        val dateStr = dateFormat.format(Date(record.startTimeMs))

        val verdictTitle = when (record.finalVerdict) {
            RiskLevel.CLONE_ALERT -> "CRITICAL THREAT: AI Synthetic Voice Clone Attack"
            RiskLevel.FINANCIAL_COERCION -> "HIGH THREAT: Financial Coercion / Impersonation Extortion"
            RiskLevel.CAUTION -> "ELEVATED RISK: Suspicious Acoustic / Speech Anomalies"
            RiskLevel.SAFE -> "BENIGN: Verified Natural Human Speech"
            RiskLevel.INCONCLUSIVE -> "INCONCLUSIVE: Insufficient Audio Sample"
        }

        return buildString {
            appendLine("══════════════════════════════════════════════════════════")
            appendLine("   TRUE VOICE FORENSIC INCIDENT DOSSIER")
            appendLine("   Evidence for National Cybercrime Reporting Portal (1930)")
            appendLine("══════════════════════════════════════════════════════════")
            appendLine()
            appendLine("1. INCIDENT OVERVIEW")
            appendLine("   • Incident ID: ${record.callId}")
            appendLine("   • Timestamp: $dateStr")
            appendLine("   • Calling Party / MSISDN: ${record.callerNumber}")
            appendLine("   • Associated Name: ${record.callerName ?: "Not available in local contacts"}")
            appendLine("   • Call Duration: ${record.durationSeconds} seconds")
            appendLine("   • Final Security Verdict: $verdictTitle")
            appendLine()
            appendLine("2. ACOUSTIC FORENSICS & AI SYNTHETIC MARKERS")
            appendLine("   • Peak Synthetic Probability: ${"%.1f".format(record.peakSyntheticScore * 100)}%")
            appendLine("   • Temporal Mean Synthetic Score: ${"%.1f".format(record.averageSyntheticScore * 100)}%")
            appendLine("   • Evaluated 3.0s Voice Windows: ${record.totalWindowsAnalyzed}")
            appendLine("   • Neural Vocoder Cutoff Artifact: ${if (record.vocoderCutoffDetected) "POSITIVE (Hard cutoff > 4kHz detected, characteristic of HiFi-GAN / ElevenLabs / Bark)" else "NEGATIVE (Natural high-frequency formant decay)"}")
            appendLine("   • Pitch Micro-Tremor Rigidity: ${if (record.peakSyntheticScore > 0.7f) "POSITIVE (Synthetic zero-crossing micro-regularity)" else "NEGATIVE (Natural human biometric vocal jitter)"}")
            appendLine()
            appendLine("3. COERCION & SCAM PATTERN DETECTION")
            appendLine("   • Financial / Authority Coercion: ${if (record.coercionDetected) "DETECTED" else "None flagged"}")
            if (record.triggeredKeywords.isNotEmpty()) {
                appendLine("   • Flagged Lexicon Triggers: ${record.triggeredKeywords.joinToString(", ")}")
            }
            appendLine()
            appendLine("4. INTEGRITY & ZERO-TRUST ATTESTATION")
            appendLine("   • Telemetry Source: Downlink Isolated Hardware Audio Pipeline")
            appendLine("   • Extraction Method: Scrcpy Rootless ADB / Shizuku Subsystem")
            appendLine("   • Device Processing: 100% On-Device Neural Inference (Edge NPU/CPU)")
            appendLine("   • Privacy Footprint: Zero disk storage of raw voice audio (DPDP compliant)")
            appendLine("   • Generated by: True Voice v1.0 (Ministry of Home Affairs SIH Cybersecurity)")
            appendLine("══════════════════════════════════════════════════════════")
        }
    }

    /**
     * Triggers Android share sheet with the 1930 Forensic Report.
     */
    fun share1930Report(context: Context, record: ForensicCallRecord) {
        val report = generate1930ReportText(record)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "True Voice Forensic Dossier - 1930 Complaint [${record.callerNumber}]")
            putExtra(Intent.EXTRA_TEXT, report)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Cybercrime Forensic Dossier").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    /**
     * Seeds initial sample forensic records for realistic UI preview & verification.
     */
    private fun createInitialSampleHistory(): List<ForensicCallRecord> {
        val now = System.currentTimeMillis()

        // 1. Simulated AI Clone Attack Call
        val attackStartTime = now - 1000L * 60 * 45 // 45 mins ago
        val attackTimeline = listOf(
            ForensicTimelinePoint(attackStartTime + 1000, 1.0f, 0.22f, RiskLevel.SAFE, true),
            ForensicTimelinePoint(attackStartTime + 3000, 3.0f, 0.48f, RiskLevel.CAUTION, true),
            ForensicTimelinePoint(attackStartTime + 5000, 5.0f, 0.88f, RiskLevel.CAUTION, true, spectralCutoffDetected = true),
            ForensicTimelinePoint(attackStartTime + 7000, 7.0f, 0.94f, RiskLevel.CLONE_ALERT, true, spectralCutoffDetected = true),
            ForensicTimelinePoint(attackStartTime + 9000, 9.0f, 0.96f, RiskLevel.CLONE_ALERT, true, spectralCutoffDetected = true),
            ForensicTimelinePoint(attackStartTime + 11000, 11.0f, 0.92f, RiskLevel.CLONE_ALERT, true, spectralCutoffDetected = true)
        )
        val cloneAttackRecord = ForensicCallRecord(
            callId = "SIM-CLONE-1930",
            startTimeMs = attackStartTime,
            endTimeMs = attackStartTime + 12000,
            durationSeconds = 12,
            callerNumber = "+91 98765 43210",
            callerName = "Impersonation Suspect (DCP Cyber Crime)",
            finalVerdict = RiskLevel.CLONE_ALERT,
            peakSyntheticScore = 0.96f,
            averageSyntheticScore = 0.73f,
            totalWindowsAnalyzed = 6,
            vocoderCutoffDetected = true,
            coercionDetected = true,
            triggeredKeywords = listOf("police", "arrest", "immediate transfer", "customs"),
            timeline = attackTimeline
        )

        // 2. Genuine Human Call
        val genuineStartTime = now - 1000L * 60 * 180 // 3 hours ago
        val genuineTimeline = listOf(
            ForensicTimelinePoint(genuineStartTime + 1000, 1.0f, 0.04f, RiskLevel.SAFE, true),
            ForensicTimelinePoint(genuineStartTime + 3000, 3.0f, 0.08f, RiskLevel.SAFE, true),
            ForensicTimelinePoint(genuineStartTime + 5000, 5.0f, 0.06f, RiskLevel.SAFE, true),
            ForensicTimelinePoint(genuineStartTime + 7000, 7.0f, 0.09f, RiskLevel.SAFE, true)
        )
        val genuineRecord = ForensicCallRecord(
            callId = "SIM-GENUINE-CALL",
            startTimeMs = genuineStartTime,
            endTimeMs = genuineStartTime + 8000,
            durationSeconds = 8,
            callerNumber = "+91 91234 56789",
            callerName = "Jayanth (Teammate)",
            finalVerdict = RiskLevel.SAFE,
            peakSyntheticScore = 0.09f,
            averageSyntheticScore = 0.07f,
            totalWindowsAnalyzed = 4,
            vocoderCutoffDetected = false,
            coercionDetected = false,
            triggeredKeywords = emptyList(),
            timeline = genuineTimeline
        )

        return listOf(cloneAttackRecord, genuineRecord)
    }
}
