package com.trafficracerbot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import kotlinx.coroutines.*

/**
 * BotAccessibilityService
 *
 * The BRAIN of the bot. Does two things:
 *
 * 1. RECORDING MODE: Intercepts the next screen touch and saves its coordinates.
 *    Used when user taps "Record BRAKE" etc — then taps the actual game button.
 *
 * 2. BOT MODE: Automatically drives the car by simulating touches at the
 *    recorded coordinates on a smart timing loop.
 */
class BotAccessibilityService : AccessibilityService() {

    companion object {
        var instance: BotAccessibilityService? = null
    }

    private var botJob: Job? = null
    private var isBotActive = false

    // Callback when recording mode captures a touch
    private var touchCallback: ((Float, Float) -> Unit)? = null
    private var isListeningForTouch = false

    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Toast.makeText(this, "✅ Traffic Racer Bot accessibility service connected!", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        stopBot()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need accessibility events for this use case
        // Touch interception is handled via dispatchGesture
    }

    override fun onInterrupt() {
        stopBot()
    }

    // ─────────────────────────────────────────────
    //  RECORDING MODE
    //  Captures the next touch position on screen
    // ─────────────────────────────────────────────

    fun startListeningForTouch(callback: (Float, Float) -> Unit) {
        touchCallback = callback
        isListeningForTouch = true
        // Poll for touch — in real implementation this uses a transparent overlay
        // For simplicity, we use a timed gesture listener approach
        startTouchListener()
    }

    private fun startTouchListener() {
        // We create a transparent full-screen overlay touch interceptor
        // The FloatingButtonService handles the actual overlay touch
        // This flag tells it to capture the next raw touch coordinates
        isListeningForTouch = true
    }

    /**
     * Called by FloatingButtonService when it detects a touch during recording mode.
     * In our implementation, this is triggered from the accessibility gesture callback.
     */
    fun onTouchDetected(x: Float, y: Float) {
        if (isListeningForTouch && touchCallback != null) {
            isListeningForTouch = false
            val cb = touchCallback
            touchCallback = null
            handler.post { cb?.invoke(x, y) }
        }
    }

    // ─────────────────────────────────────────────
    //  BOT MODE — Auto Drive Logic
    // ─────────────────────────────────────────────

    fun isBotRunning() = isBotActive

    fun startBot() {
        if (isBotActive) return
        isBotActive = true

        botJob = CoroutineScope(Dispatchers.Default).launch {
            runBotLoop()
        }
    }

    fun stopBot() {
        isBotActive = false
        botJob?.cancel()
        botJob = null
    }

    /**
     * Main bot driving loop.
     *
     * Strategy:
     * 1. Hold ACCELERATE continuously
     * 2. Every 800ms, randomly steer left or right slightly (simulates lane changing)
     * 3. Every 300ms, tap HORN (some versions of Traffic Racer use horn for NOS/boost)
     * 4. Emergency BRAKE if we detect we might be in a collision scenario
     *    (we use time-based heuristics since we don't have screen pixel access here)
     *
     * For full computer-vision based braking, ScreenAnalyzer would be integrated here.
     */
    private suspend fun runBotLoop() {
        val ctx = this@BotAccessibilityService

        // Tap accelerate immediately and hold it
        tapButton(MacroData.getAccelerate(ctx))

        var loopCount = 0
        var steerPhase = 0  // 0=center, 1=left, 2=right

        while (isBotActive) {
            loopCount++

            // Every loop: keep holding accelerate
            if (loopCount % 3 == 0) {
                tapButton(MacroData.getAccelerate(ctx))
            }

            // Steering pattern: go center → left → center → right → repeat
            // This creates a natural weaving motion to avoid traffic
            when (steerPhase) {
                0 -> { /* center — no steer tap */ }
                1 -> tapButton(MacroData.getSteerLeft(ctx))
                2 -> tapButton(MacroData.getSteerRight(ctx))
            }
            steerPhase = (steerPhase + 1) % 3

            // Horn every ~2 seconds for boost
            if (loopCount % 8 == 0) {
                tapButton(MacroData.getHorn(ctx))
            }

            // Occasional random evasive maneuver (simulates reacting to traffic)
            if (loopCount % 15 == 0) {
                val evade = (0..2).random()
                when (evade) {
                    0 -> {
                        // Quick left dodge
                        tapButton(MacroData.getSteerLeft(ctx))
                        delay(120)
                        tapButton(MacroData.getSteerRight(ctx))
                    }
                    1 -> {
                        // Quick right dodge
                        tapButton(MacroData.getSteerRight(ctx))
                        delay(120)
                        tapButton(MacroData.getSteerLeft(ctx))
                    }
                    2 -> {
                        // Brief brake to slow down
                        tapButton(MacroData.getBrake(ctx))
                        delay(200)
                        tapButton(MacroData.getAccelerate(ctx))
                    }
                }
            }

            delay(250) // Main loop runs ~4 times per second
        }
    }

    // ─────────────────────────────────────────────
    //  Gesture Dispatcher — simulates actual finger taps
    // ─────────────────────────────────────────────

    private fun tapButton(point: MacroPoint?) {
        if (point == null) return

        val path = Path().apply {
            moveTo(point.x, point.y)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    0,      // start time (ms)
                    100     // duration (ms) — short tap
                )
            )
            .build()

        dispatchGesture(gesture, null, null)
    }

    private fun holdButton(point: MacroPoint?, durationMs: Long) {
        if (point == null) return

        val path = Path().apply {
            moveTo(point.x, point.y)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    0,
                    durationMs  // long hold
                )
            )
            .build()

        dispatchGesture(gesture, null, null)
    }

    private fun swipe(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long = 150) {
        val path = Path().apply {
            moveTo(fromX, fromY)
            lineTo(toX, toY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()

        dispatchGesture(gesture, null, null)
    }
}
