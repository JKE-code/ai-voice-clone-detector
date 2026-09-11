/*
 * True Voice: Real-Time On-Device AI Voice Clone & Scam Defense
 * UPI & Banking App Foreground Protection Monitor
 */

package com.truevoice.ui

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.kitsumed.shizucallrecorder.utils.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Monitors the foreground app package to protect Indian UPI and Banking apps.
 *
 * Problem Statement:
 * Indian banking apps (Google Pay, PhonePe, Paytm, BHIM, YONO SBI, etc.) employ aggressive anti-tamper
 * SDKs that detect active TYPE_APPLICATION_OVERLAY windows to prevent tapjacking. If an overlay is visible,
 * the banking app terminates immediately with the error: "Delete interfering app".
 *
 * Solution:
 * This monitor continuously detects when any blacklisted financial/banking application comes to the foreground.
 * The instant a banking app is focused, [onSensitiveAppFocused] is invoked to immediately retract/hide the HUD.
 * Once the user returns to the call or home screen, [onSensitiveAppDismissed] restores the HUD.
 */
class ForegroundAppMonitor(
    private val context: Context,
    private val onSensitiveAppFocused: (String) -> Unit,
    private val onSensitiveAppDismissed: () -> Unit
) {

    companion object {
        // High-Risk Indian UPI & Banking App Package Names
        val SENSITIVE_BANKING_PACKAGES = setOf(
            "com.google.android.apps.nbu.paisa.user", // Google Pay
            "com.phonepe.app",                       // PhonePe
            "net.one97.paytm",                       // Paytm
            "in.org.npci.upiapp",                    // BHIM UPI
            "com.sbi.lotusintouch",                  // YONO SBI
            "com.msf.kbank.mobile",                  // Kotak 811
            "com.icicibank.mobile",                  // iMobile Pay
            "com.snapwork.hdfc",                     // HDFC Bank Mobile
            "com.axis.mobile",                       // Axis Mobile
            "com.cred.android",                      // CRED
            "com.mobikwik_new",                      // MobiKwik
            "com.amazon.mShop.android.shopping"      // Amazon Pay
        )
    }

    private var monitorJob: Job? = null
    private var isSensitiveAppActive = false
    private val usageStatsManager by lazy {
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    }

    /**
     * Checks whether the user has granted PACKAGE_USAGE_STATS permission.
     */
    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Starts continuous foreground app polling during active calls.
     */
    fun startMonitoring(scope: CoroutineScope) {
        stopMonitoring()
        if (!hasUsageStatsPermission()) {
            AppLogger.d("[TrueVoice Monitor] PACKAGE_USAGE_STATS not granted. UPI auto-retraction standby.")
            return
        }

        monitorJob = scope.launch(Dispatchers.Default) {
            AppLogger.i("[TrueVoice Monitor] ForegroundAppMonitor active")
            while (isActive) {
                checkForegroundApp()
                delay(400) // Poll every 400ms during active call
            }
        }
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        if (isSensitiveAppActive) {
            isSensitiveAppActive = false
            onSensitiveAppDismissed()
        }
    }

    private fun checkForegroundApp() {
        val mgr = usageStatsManager ?: return
        val time = System.currentTimeMillis()
        val events = mgr.queryEvents(time - 1500, time) ?: return
        val event = UsageEvents.Event()

        var latestPackage: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                latestPackage = event.packageName
            }
        }

        if (latestPackage != null) {
            val isSensitive = SENSITIVE_BANKING_PACKAGES.contains(latestPackage)
            if (isSensitive && !isSensitiveAppActive) {
                isSensitiveAppActive = true
                AppLogger.w("[TrueVoice Monitor] Sensitive UPI App focused: $latestPackage. Auto-retracting HUD!")
                onSensitiveAppFocused(latestPackage)
            } else if (!isSensitive && isSensitiveAppActive) {
                isSensitiveAppActive = false
                AppLogger.i("[TrueVoice Monitor] Returned from sensitive app. Restoring HUD.")
                onSensitiveAppDismissed()
            }
        }
    }
}
