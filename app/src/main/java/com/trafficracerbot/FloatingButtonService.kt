package com.trafficracerbot

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.*
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import android.os.Handler
import android.os.Looper

/**
 * FloatingButtonService
 * Shows a 🏎️ button that floats over ALL apps including Traffic Racer.
 * When tapped → shows the macro recording popup.
 */
class FloatingButtonService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var floatingBtn: TextView

    private var isRecording = false
    private var currentRecordingType = ""

    companion object {
        var instance: FloatingButtonService? = null
        const val CHANNEL_ID = "TrafficBotChannel"
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        startForeground(1, buildNotification())

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setupFloatingButton()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        if (::floatingView.isInitialized) {
            try { windowManager.removeView(floatingView) } catch (_: Exception) {}
        }
    }

    // ─────────────────────────────────────────────
    //  Floating Button Setup
    // ─────────────────────────────────────────────

    private fun setupFloatingButton() {
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.floating_button, null)
        floatingBtn = floatingView.findViewById(R.id.floatingBtn)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 16
            y = 300
        }

        // Drag to move the button around
        var initialX = 0; var initialY = 0
        var initialTouchX = 0f; var initialTouchY = 0f
        var isDragging = false

        floatingView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                        isDragging = true
                        params.x = initialX - dx
                        params.y = initialY + dy
                        windowManager.updateViewLayout(floatingView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        // It's a tap — show the popup
                        showBotMenu()
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(floatingView, params)
    }

    // ─────────────────────────────────────────────
    //  Main Bot Menu Popup
    // ─────────────────────────────────────────────

    private fun showBotMenu() {
        val botService = BotAccessibilityService.instance
        val isRunning = botService?.isBotRunning() == true

        // Build menu on main thread via handler (since we're in a service)
        Handler(Looper.getMainLooper()).post {
            val options = arrayOf(
                "🎮 ${if (isRunning) "STOP BOT" else "START BOT"}",
                "━━━━━━━━━━━━━━",
                "⚡ Record ACCELERATE button",
                "🛑 Record BRAKE button",
                "📯 Record HORN button",
                "⬅️ Record STEER LEFT button",
                "➡️ Record STEER RIGHT button",
                "━━━━━━━━━━━━━━",
                "🗑️ Clear all recordings",
                "❌ Close floating button"
            )

            val dialog = android.app.AlertDialog.Builder(
                this, android.R.style.Theme_DeviceDefault_Dialog_Alert
            )
                .setTitle("🏎️ Traffic Racer Bot")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> toggleBot()
                        2 -> startRecording("ACCELERATE")
                        3 -> startRecording("BRAKE")
                        4 -> startRecording("HORN")
                        5 -> startRecording("STEER_LEFT")
                        6 -> startRecording("STEER_RIGHT")
                        8 -> confirmClearAll()
                        9 -> stopSelf()
                    }
                }
                .create()

            // Must set window type for overlay
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            dialog.show()
        }
    }

    // ─────────────────────────────────────────────
    //  Macro Recording
    // ─────────────────────────────────────────────

    private fun startRecording(type: String) {
        currentRecordingType = type
        isRecording = true

        // Update floating button to show recording mode
        floatingBtn.text = "👆"
        floatingBtn.setBackgroundResource(R.drawable.circle_bg_recording)

        val label = when (type) {
            "ACCELERATE" -> "⚡ ACCELERATE"
            "BRAKE"      -> "🛑 BRAKE"
            "HORN"       -> "📯 HORN"
            "STEER_LEFT" -> "⬅️ STEER LEFT"
            "STEER_RIGHT"-> "➡️ STEER RIGHT"
            else -> type
        }

        // Show toast instruction
        Toast.makeText(
            this,
            "👆 Now TAP the $label button in Traffic Racer!\n(Floating button is watching...)",
            Toast.LENGTH_LONG
        ).show()

        // Tell accessibility service to listen for next touch
        BotAccessibilityService.instance?.startListeningForTouch { x, y ->
            saveRecording(type, x, y)
        }
    }

    fun saveRecording(type: String, x: Float, y: Float) {
        isRecording = false
        floatingBtn.text = "🏎️"
        floatingBtn.setBackgroundResource(R.drawable.circle_bg)

        when (type) {
            "ACCELERATE"  -> MacroData.saveAccelerate(this, x, y)
            "BRAKE"       -> MacroData.saveBrake(this, x, y)
            "HORN"        -> MacroData.saveHorn(this, x, y)
            "STEER_LEFT"  -> MacroData.saveSteerLeft(this, x, y)
            "STEER_RIGHT" -> MacroData.saveSteerRight(this, x, y)
        }

        val label = type.replace("_", " ")
        Toast.makeText(
            this,
            "✅ $label recorded at (${"%.0f".format(x)}, ${"%.0f".format(y)})",
            Toast.LENGTH_SHORT
        ).show()
    }

    // ─────────────────────────────────────────────
    //  Bot Control
    // ─────────────────────────────────────────────

    private fun toggleBot() {
        val botService = BotAccessibilityService.instance

        if (botService == null) {
            Toast.makeText(this, "⚠️ Accessibility service not running!\nGo to Settings → Accessibility → Enable Traffic Racer Bot", Toast.LENGTH_LONG).show()
            return
        }

        if (botService.isBotRunning()) {
            botService.stopBot()
            floatingBtn.text = "🏎️"
            Toast.makeText(this, "⏹️ Bot STOPPED", Toast.LENGTH_SHORT).show()
        } else {
            if (!MacroData.allRecorded(this)) {
                Toast.makeText(this, "⚠️ Please record all 5 buttons first!\nTap 🏎️ → Record each button", Toast.LENGTH_LONG).show()
                return
            }
            botService.startBot()
            floatingBtn.text = "🤖"
            Toast.makeText(this, "▶️ Bot STARTED! Open Traffic Racer now!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmClearAll() {
        Handler(Looper.getMainLooper()).post {
            val dialog = android.app.AlertDialog.Builder(
                this, android.R.style.Theme_DeviceDefault_Dialog_Alert
            )
                .setTitle("🗑️ Clear All Recordings?")
                .setMessage("This will delete all recorded button positions. You'll need to record them again.")
                .setPositiveButton("Yes, Clear") { _, _ ->
                    MacroData.clearAll(this)
                    Toast.makeText(this, "✅ All recordings cleared!", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .create()
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            dialog.show()
        }
    }

    // ─────────────────────────────────────────────
    //  Notification (required for foreground service)
    // ─────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Traffic Bot",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Traffic Racer Bot is running" }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("🏎️ Traffic Racer Bot")
            .setContentText("Floating button is active. Tap it to start.")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi)
            .build()
    }
}
