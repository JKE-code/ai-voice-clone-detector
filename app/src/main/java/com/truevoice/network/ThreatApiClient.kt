/*
 * True Voice: Real-Time On-Device AI Voice Clone & Scam Defense
 * Enterprise Threat Intelligence API Client
 */

package com.truevoice.network

import com.kitsumed.shizucallrecorder.utils.AppLogger
import com.truevoice.forensics.ForensicCallRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class CallerReputation(
    val phoneNumber: String,
    val reputationStatus: String, // "SAFE", "SUSPICIOUS", "AI_CLONE_ATTACK", "CONFIRMED_FRAUD"
    val riskScore: Float,
    val totalThreatReports: Int,
    val latestThreatCategory: String?,
    val recommendedAction: String // "ALLOW", "WARN_USER", "BLOCK_TRANSACTION"
)

object ThreatApiClient {

    // Default API endpoint: 10.0.2.2 for emulator, localhost with adb reverse for real devices
    @Volatile
    var apiBaseUrl: String = "http://127.0.0.1:8000"

    /**
     * Queries telecom threat intelligence grid for an incoming caller's reputation.
     */
    suspend fun checkReputation(phoneNumber: String): CallerReputation? = withContext(Dispatchers.IO) {
        try {
            val encodedPhone = URLEncoder.encode(phoneNumber, "UTF-8")
            val url = URL("$apiBaseUrl/api/v1/reputation?phone=$encodedPhone")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3000
                readTimeout = 3000
                setRequestProperty("Accept", "application/json")
            }

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                val json = JSONObject(response)
                val cat = if (json.has("latest_threat_category") && !json.isNull("latest_threat_category")) {
                    json.getString("latest_threat_category")
                } else null

                return@withContext CallerReputation(
                    phoneNumber = json.optString("phone_number", phoneNumber),
                    reputationStatus = json.optString("reputation_status", "SAFE"),
                    riskScore = json.optDouble("risk_score", 0.05).toFloat(),
                    totalThreatReports = json.optInt("total_threat_reports", 0),
                    latestThreatCategory = cat,
                    recommendedAction = json.optString("recommended_action", "ALLOW")
                )
            } else {
                AppLogger.w("[ThreatApiClient] Reputation check returned HTTP ${conn.responseCode}")
            }
        } catch (e: Exception) {
            AppLogger.d("[ThreatApiClient] Threat API offline or unreachable: ${e.message}")
        }
        return@withContext null
    }

    /**
     * Submits an on-device forensic audit report to the enterprise community defense grid.
     */
    suspend fun submitThreatReport(record: ForensicCallRecord): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$apiBaseUrl/api/v1/report")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 4000
                readTimeout = 4000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            val payload = JSONObject().apply {
                put("call_id", record.callId)
                put("caller_number", record.callerNumber)
                put("peak_synthetic_score", record.peakSyntheticScore.toDouble())
                put("highest_risk_level", record.finalVerdict.name)
                put("vocoder_detected", record.vocoderCutoffDetected)
                put("coercion_detected", record.coercionDetected)
                put("triggered_keywords", JSONArray(record.triggeredKeywords))
                put("reported_by", "TrueVoice-Android-App")
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val success = conn.responseCode in 200..299
            if (success) {
                AppLogger.i("[ThreatApiClient] Successfully synced threat report for ${record.callerNumber} to server")
            } else {
                AppLogger.w("[ThreatApiClient] Threat report submission failed with HTTP ${conn.responseCode}")
            }
            return@withContext success
        } catch (e: Exception) {
            AppLogger.d("[ThreatApiClient] Could not sync threat report (API server offline): ${e.message}")
            return@withContext false
        }
    }
}
