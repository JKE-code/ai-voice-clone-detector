/*
 * True Voice: Real-Time On-Device AI Voice Clone & Scam Defense
 * Post-Call Forensic Timeline Activity
 */

package com.truevoice.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.kitsumed.shizucallrecorder.ui.theme.ShizuCallRecorderTheme
import com.truevoice.forensics.ForensicRepository
import com.truevoice.ui.forensics.ForensicTimelineScreen
import com.truevoice.ui.history.CallHistoryScreen

/**
 * Standalone Activity hosting the Post-Call Forensic Timeline and Call History dashboard.
 */
class ForensicTimelineActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CALL_ID = "com.truevoice.EXTRA_CALL_ID"

        fun createIntent(context: Context, callId: String? = null): Intent {
            return Intent(context, ForensicTimelineActivity::class.java).apply {
                if (callId != null) {
                    putExtra(EXTRA_CALL_ID, callId)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetCallId = intent.getStringExtra(EXTRA_CALL_ID)

        setContent {
            ShizuCallRecorderTheme(darkTheme = true, dynamicColor = false) {
                val history by ForensicRepository.callHistory.collectAsState()
                val targetRecord = history.find { it.callId == targetCallId } ?: history.firstOrNull()

                if (targetCallId != null && targetRecord != null) {
                    ForensicTimelineScreen(
                        record = targetRecord,
                        onBack = { finish() }
                    )
                } else {
                    CallHistoryScreen(
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}
