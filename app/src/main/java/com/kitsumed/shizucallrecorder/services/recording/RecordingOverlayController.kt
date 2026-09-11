/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 * Copyright (C) 2026-present kitsumed (Med)
 * This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 * The full license text is available in the LICENSE file at the root of this project.
 * This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.services.recording

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.kitsumed.shizucallrecorder.data.AppPreferences
import com.kitsumed.shizucallrecorder.system.permissions.PermissionChecks
import com.kitsumed.shizucallrecorder.ui.common.RecordingOverlay
import com.kitsumed.shizucallrecorder.ui.theme.ShizuCallRecorderTheme
import com.truevoice.ml.RiskAssessment
import com.truevoice.ml.RiskLevel
import com.truevoice.ui.hud.SecurityHudPill
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import android.provider.Settings
import androidx.core.net.toUri
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.kitsumed.shizucallrecorder.utils.AppLogger
import com.truevoice.ml.AuthenticityLabel
import com.truevoice.ml.AuthenticityResult
import kotlin.math.max
import kotlin.math.min

/**
 * Controller that hosts a ComposeView inside WindowManager for True Voice in-call floating security HUD.
 */
class RecordingOverlayController(private val context: Context) {

    companion object {
        @Volatile
        private var instance: RecordingOverlayController? = null

        fun getInstance(): RecordingOverlayController? = instance

        fun isOverlayShowing(): Boolean = instance?.composeView != null

        fun hideTestOverlay() {
            instance?.hideOverlay()
        }

        fun triggerTestOverlay(context: Context) {
            val controller = instance ?: RecordingOverlayController(context.applicationContext).also {
                instance = it
            }
            controller.startTestOverlay()
        }
    }

    init {
        instance = this
    }

    private var isTestMode = false
    private var testDismissJob: kotlinx.coroutines.Job? = null
    private val overlayContext: Context by lazy {
        // Get a display context on Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
            context.createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        } else {
            context
        }
    }

    private val windowManager by lazy { overlayContext.getSystemService(WindowManager::class.java) }
    private val appPreferences = AppPreferences(context)

    private var composeView: ComposeView? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var lifecycleOwner: ComposeWindowLifecycleOwner? = null

    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val testRiskFlow = MutableStateFlow(
        RiskAssessment(
            level = RiskLevel.SAFE,
            smoothedScore = 0.04f,
            confidence = 0.99f,
            consecutiveAlertWindows = 0,
            totalEvaluatedWindows = 1,
            latestResult = AuthenticityResult(
                syntheticScore = 0.04f,
                confidence = 0.99f,
                label = AuthenticityLabel.GENUINE,
                inferenceTimeMs = 17,
                spectralCutoffDetected = false
            )
        )
    )

    /**
     * Displays the recording overlay and True Voice security HUD with the current state.
     */
    fun showOverlay(state: RecordingServiceState) {
        if (!state.isRecordingActive) {
            hideOverlay()
            return
        }

        val hudEnabled = appPreferences.isSecurityHudEnabled()
        val legacyOverlay = appPreferences.isOverlayEnabled()

        if (!hudEnabled && !legacyOverlay) {
            AppLogger.d("[TrueVoice Overlay] Overlay disabled in preferences")
            hideOverlay()
            return
        }

        if (!PermissionChecks.hasOverlayPermission(context)) {
            AppLogger.w("[TrueVoice Overlay] Overlay not shown: 'Appear on top' permission is NOT granted!")
            notifyMissingOverlayPermission()
            hideOverlay()
            return
        }

        if (composeView == null) {
            initOverlayView()
        }

        val activeEngine = (state as? RecordingServiceState.Active)?.engine

        composeView?.setContent {
            val darkTheme = when (appPreferences.getThemeMode()) {
                AppPreferences.ThemeMode.LIGHT -> false
                AppPreferences.ThemeMode.DARK   -> true
                AppPreferences.ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            val dynamicColor = appPreferences.isDynamicColorEnabled()
            val isVisible = remember { MutableTransitionState(false).apply { targetState = true } }

            // Observe live Phase 2 AI risk assessment
            val liveRiskAssessment = activeEngine?.liveAnalysisSink?.riskFlow?.collectAsState()?.value
                ?: remember {
                    RiskAssessment(
                        level = RiskLevel.INCONCLUSIVE,
                        smoothedScore = 0.0f,
                        confidence = 0.0f,
                        consecutiveAlertWindows = 0,
                        totalEvaluatedWindows = 0,
                        latestResult = null
                    )
                }

            ShizuCallRecorderTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
                AnimatedVisibility(
                    visibleState = isVisible,
                    enter = slideInHorizontally(
                        animationSpec = tween(durationMillis = 400),
                        initialOffsetX = { fullWidth -> fullWidth }
                    ) + fadeIn(animationSpec = tween(durationMillis = 400))
                ) {
                    androidx.compose.foundation.layout.Column(
                        horizontalAlignment = androidx.compose.ui.Alignment.End
                    ) {
                        // True Voice Real-Time Security HUD Pill
                        if (hudEnabled) {
                            SecurityHudPill(
                                assessment = liveRiskAssessment,
                                onDrag = { deltaX, deltaY -> updateOverlayPosition(deltaX, deltaY) },
                                onDragEnd = { saveOverlayPosition() },
                                onDismiss = { hideOverlay() },
                                onOpenForensics = {
                                    val intent = Intent(context, com.truevoice.ui.ForensicTimelineActivity::class.java).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }
                            )
                        }

                        // Compact call recording action controls
                        if (legacyOverlay) {
                            RecordingOverlay(
                                isRecordingActive = state.isRecordingActive,
                                isRecordingPaused = state.isRecordingPaused,
                                onActionClick = { sendServiceAction(state) },
                                onDrag = { deltaX, deltaY -> updateOverlayPosition(deltaX, deltaY) },
                                onDragEnd = { saveOverlayPosition() }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun initOverlayView() {
        val savedX = appPreferences.getOverlayXPosition()
        val savedY = appPreferences.getOverlayYPosition()

        val screenWidth = windowManager.currentWindowMetrics.bounds.width()
        val screenHeight = windowManager.currentWindowMetrics.bounds.height()
        val density = overlayContext.resources.displayMetrics.density

        val defaultWidthPx = (240 * density).toInt()
        val defaultHeightPx = (80 * density).toInt()

        val safeX = if (savedX == -1) {
            // Default to right side with 12dp margin
            max(10, screenWidth - defaultWidthPx - (12 * density).toInt())
        } else {
            savedX.coerceIn(10, max(10, screenWidth - 100))
        }

        val safeY = if (savedY == -1) {
            // Default to upper-middle (180dp down)
            (180 * density).toInt().coerceIn(60, max(60, screenHeight - defaultHeightPx))
        } else {
            savedY.coerceIn(60, max(60, screenHeight - 100))
        }

        windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = safeX
            y = safeY
        }

        // Attach the required artificial lifecycle to the ComposeView
        composeView = ComposeView(overlayContext).also { view ->
            view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            lifecycleOwner = ComposeWindowLifecycleOwner().apply {
                attachToView(view)
            }
        }

        windowManager.addView(composeView, windowParams)
    }

    private fun updateOverlayPosition(deltaX: Float, deltaY: Float) {
        val params = windowParams ?: return
        val view = composeView ?: return

        val screenWidth = windowManager.currentWindowMetrics.bounds.width()
        val screenHeight = windowManager.currentWindowMetrics.bounds.height()

        val density = overlayContext.resources.displayMetrics.density
        val viewWidth = view.width.takeIf { it > 0 } ?: (240 * density).toInt()
        val viewHeight = view.height.takeIf { it > 0 } ?: (80 * density).toInt()

        // Clamp within visible screen boundaries (10px margin on sides, 60px on top/bottom)
        val newX = (params.x + deltaX.toInt()).coerceIn(10, max(10, screenWidth - viewWidth - 10))
        val newY = (params.y + deltaY.toInt()).coerceIn(60, max(60, screenHeight - viewHeight - 60))

        params.x = newX
        params.y = newY
        windowManager.updateViewLayout(view, params)
    }

    private fun saveOverlayPosition() {
        windowParams?.let {
            appPreferences.setOverlayXPosition(it.x)
            appPreferences.setOverlayYPosition(it.y)
        }
    }

    /**
     * Hides the overlay and cleans up resources.
     */
    fun hideOverlay() {
        testDismissJob?.cancel()
        testDismissJob = null
        isTestMode = false
        composeView?.let { view ->
            runCatching { windowManager.removeView(view) }

            // Clean up the lifecycle to prevent memory leaks
            lifecycleOwner?.destroy()
            lifecycleOwner = null

            composeView = null
            windowParams = null
        }
    }

    private fun notifyMissingOverlayPermission() {
        try {
            val notificationManager = context.getSystemService(android.app.NotificationManager::class.java) ?: return
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:${context.packageName}".toUri()
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                context,
                911,
                intent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )
            val channelId = "truevoice_hud_permission"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    "True Voice Security Alerts",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                )
                notificationManager.createNotificationChannel(channel)
            }
            val notif = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(com.kitsumed.shizucallrecorder.R.mipmap.ic_launcher)
                .setContentTitle("True Voice: Live Call HUD Permission Required")
                .setContentText("Tap to grant 'Appear on top' to see real-time AI clone alerts over calls.")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .build()
            notificationManager.notify(9110, notif)
        } catch (e: Exception) {
            AppLogger.w("[TrueVoice Overlay] Error posting permission notification: ${e.message}")
        }
    }

    fun startTestOverlay() {
        if (!PermissionChecks.hasOverlayPermission(context)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:${context.packageName}".toUri()
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return
        }

        // Toggle behavior: if already showing test preview, dismiss it immediately
        if (isTestMode && composeView != null) {
            hideOverlay()
            return
        }

        isTestMode = true
        if (composeView == null) {
            initOverlayView()
        }

        testDismissJob?.cancel()
        testDismissJob = controllerScope.launch {
            val sequence = listOf(
                RiskAssessment(
                    level = RiskLevel.SAFE,
                    smoothedScore = 0.04f,
                    confidence = 0.99f,
                    consecutiveAlertWindows = 0,
                    totalEvaluatedWindows = 1,
                    latestResult = AuthenticityResult(0.04f, 0.99f, AuthenticityLabel.GENUINE, 16, false)
                ),
                RiskAssessment(
                    level = RiskLevel.SAFE,
                    smoothedScore = 0.12f,
                    confidence = 0.97f,
                    consecutiveAlertWindows = 0,
                    totalEvaluatedWindows = 2,
                    latestResult = AuthenticityResult(0.14f, 0.97f, AuthenticityLabel.GENUINE, 18, false)
                ),
                RiskAssessment(
                    level = RiskLevel.CAUTION,
                    smoothedScore = 0.58f,
                    confidence = 0.92f,
                    consecutiveAlertWindows = 1,
                    totalEvaluatedWindows = 3,
                    latestResult = AuthenticityResult(0.65f, 0.92f, AuthenticityLabel.SUSPICIOUS, 21, true)
                ),
                RiskAssessment(
                    level = RiskLevel.CLONE_ALERT,
                    smoothedScore = 0.94f,
                    confidence = 0.98f,
                    consecutiveAlertWindows = 3,
                    totalEvaluatedWindows = 4,
                    latestResult = AuthenticityResult(0.96f, 0.98f, AuthenticityLabel.SYNTHETIC_CLONE, 20, true)
                ),
                RiskAssessment(
                    level = RiskLevel.CLONE_ALERT,
                    smoothedScore = 0.98f,
                    confidence = 0.99f,
                    consecutiveAlertWindows = 4,
                    totalEvaluatedWindows = 5,
                    latestResult = AuthenticityResult(0.99f, 0.99f, AuthenticityLabel.SYNTHETIC_CLONE, 19, true)
                )
            )

            for (step in sequence) {
                testRiskFlow.value = step
                kotlinx.coroutines.delay(2600)
            }
            kotlinx.coroutines.delay(6000)
            hideOverlay()
        }

        composeView?.setContent {
            val liveRisk by testRiskFlow.collectAsState()
            ShizuCallRecorderTheme(darkTheme = true, dynamicColor = false) {
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = Alignment.End
                ) {
                    SecurityHudPill(
                        assessment = liveRisk,
                        onDrag = { deltaX, deltaY -> updateOverlayPosition(deltaX, deltaY) },
                        onDragEnd = { saveOverlayPosition() },
                        onDismiss = {
                            hideOverlay()
                        },
                        onOpenForensics = {
                            hideOverlay()
                            val intent = Intent(context, com.truevoice.ui.ForensicTimelineActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }
    }

    private fun sendServiceAction(state: RecordingServiceState) {
        val intentAction = when {
            !state.isRecordingActive -> RecordingForegroundService.ACTION_MANUAL_START
            state.isRecordingPaused -> RecordingForegroundService.ACTION_RESUME_RECORDING
            else -> RecordingForegroundService.ACTION_PAUSE_RECORDING
        }
        context.startService(Intent(context, RecordingForegroundService::class.java).apply { action = intentAction })
    }

    /**
     * A fake LifecycleOwner/ViewModelStoreOwner/SavedStateRegistryOwner for ComposeView when used in a raw WindowManager context.
     * This is required to prevent crashes when using ComposeView in a raw WindowManager context.
     */
    private class ComposeWindowLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)
        private val store = ViewModelStore()

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
        override val viewModelStore: ViewModelStore get() = store

        init {
            savedStateRegistryController.performRestore(Bundle())
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }

        fun attachToView(view: View) {
            view.setViewTreeLifecycleOwner(this)
            view.setViewTreeViewModelStoreOwner(this)
            view.setViewTreeSavedStateRegistryOwner(this)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        fun destroy() {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            store.clear()
        }
    }
}