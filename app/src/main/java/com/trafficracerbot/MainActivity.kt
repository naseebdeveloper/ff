package com.trafficracerbot

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var btnOverlay: Button
    private lateinit var btnAccessibility: Button
    private lateinit var btnLaunch: Button

    private lateinit var statusOverlay: TextView
    private lateinit var statusAccessibility: TextView
    private lateinit var statusAccel: TextView
    private lateinit var statusBrake: TextView
    private lateinit var statusHorn: TextView
    private lateinit var statusSteer: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupButtons()
        // Show welcome dialog on first launch
        showWelcomeDialog()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatuses()
        updateMacroStatuses()
    }

    private fun bindViews() {
        btnOverlay       = findViewById(R.id.btnOverlay)
        btnAccessibility = findViewById(R.id.btnAccessibility)
        btnLaunch        = findViewById(R.id.btnLaunch)

        statusOverlay       = findViewById(R.id.statusOverlay)
        statusAccessibility = findViewById(R.id.statusAccessibility)
        statusAccel         = findViewById(R.id.statusAccel)
        statusBrake         = findViewById(R.id.statusBrake)
        statusHorn          = findViewById(R.id.statusHorn)
        statusSteer         = findViewById(R.id.statusSteer)
    }

    private fun setupButtons() {
        btnOverlay.setOnClickListener {
            showOverlayPermissionDialog()
        }

        btnAccessibility.setOnClickListener {
            showAccessibilityPermissionDialog()
        }

        btnLaunch.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "⚠️ Please grant Display Over Apps permission first!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (!isAccessibilityEnabled()) {
                Toast.makeText(this, "⚠️ Please enable Accessibility Service first!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            // Start the floating button service
            startService(Intent(this, FloatingButtonService::class.java))
            Toast.makeText(this, "🏎️ Bot launched! Look for the floating button on your screen.", Toast.LENGTH_LONG).show()
            // Minimize app so user can see the floating button
            moveTaskToBack(true)
        }
    }

    // ─────────────────────────────────────────────
    //  Permission dialogs with clear explanations
    // ─────────────────────────────────────────────

    private fun showWelcomeDialog() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("welcomed", false)) return
        prefs.edit().putBoolean("welcomed", true).apply()

        AlertDialog.Builder(this)
            .setTitle("👋 Welcome to Traffic Racer Bot!")
            .setMessage(
                "This app will auto-play Traffic Racer for you!\n\n" +
                "Here's how it works:\n\n" +
                "1️⃣  Grant 2 permissions (steps below)\n\n" +
                "2️⃣  Launch the floating 🏎️ button\n\n" +
                "3️⃣  Open Traffic Racer\n\n" +
                "4️⃣  Tap the floating button → record each game button\n\n" +
                "5️⃣  Tap START and watch the bot drive!\n\n" +
                "Takes less than 2 minutes to set up 🚀"
            )
            .setPositiveButton("Let's Go! 🔥") { d, _ -> d.dismiss() }
            .setCancelable(false)
            .show()
    }

    private fun showOverlayPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("📱 Display Over Apps")
            .setMessage(
                "This permission lets the small 🏎️ bot button float on top of Traffic Racer.\n\n" +
                "Without it, the button would disappear when you switch to the game.\n\n" +
                "On the next screen:\n" +
                "• Find 'Traffic Racer Bot' in the list\n" +
                "• Toggle it ON\n" +
                "• Come back here"
            )
            .setPositiveButton("Open Settings ➜") { _, _ ->
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
            .setNegativeButton("Not Now") { d, _ -> d.dismiss() }
            .show()
    }

    private fun showAccessibilityPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("♿ Accessibility Service")
            .setMessage(
                "This is the most important permission!\n\n" +
                "It allows the bot to:\n" +
                "✅ Simulate finger taps on your screen\n" +
                "✅ Hold the accelerate button\n" +
                "✅ Tap brake when danger detected\n" +
                "✅ Steer left and right automatically\n\n" +
                "On the next screen:\n" +
                "• Scroll down to find 'Traffic Racer Bot'\n" +
                "• Tap it → Toggle ON\n" +
                "• Tap ALLOW on the popup\n" +
                "• Come back here"
            )
            .setPositiveButton("Open Settings ➜") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Not Now") { d, _ -> d.dismiss() }
            .show()
    }

    // ─────────────────────────────────────────────
    //  Status Updates
    // ─────────────────────────────────────────────

    private fun updatePermissionStatuses() {
        val overlayOk = Settings.canDrawOverlays(this)
        val accessOk = isAccessibilityEnabled()

        statusOverlay.text = if (overlayOk) "✅" else "⭕"
        statusAccessibility.text = if (accessOk) "✅" else "⭕"

        btnOverlay.text = if (overlayOk) "✅ GRANTED" else "GRANT PERMISSION"
        btnOverlay.backgroundTintList = if (overlayOk)
            android.content.res.ColorStateList.valueOf(0xFF388E3C.toInt())
        else
            android.content.res.ColorStateList.valueOf(0xFFFF1744.toInt())

        btnAccessibility.text = if (accessOk) "✅ GRANTED" else "GRANT PERMISSION"
        btnAccessibility.backgroundTintList = if (accessOk)
            android.content.res.ColorStateList.valueOf(0xFF388E3C.toInt())
        else
            android.content.res.ColorStateList.valueOf(0xFFFF6D00.toInt())
    }

    private fun updateMacroStatuses() {
        val accel = MacroData.getAccelerate(this)
        val brake = MacroData.getBrake(this)
        val horn  = MacroData.getHorn(this)
        val sl    = MacroData.getSteerLeft(this)
        val sr    = MacroData.getSteerRight(this)

        statusAccel.text = if (accel != null) "⚡ Accel: ✅ Set" else "⚡ Accelerate: ❌ Not set"
        statusBrake.text = if (brake != null) "🛑 Brake: ✅ Set" else "🛑 Brake: ❌ Not set"
        statusHorn.text  = if (horn  != null) "📯 Horn: ✅ Set"  else "📯 Horn: ❌ Not set"
        statusSteer.text = if (sl != null && sr != null) "↔️ Steer: ✅ Set" else "↔️ Steer: ❌ Not set"
    }

    private fun isAccessibilityEnabled(): Boolean {
        return try {
            val enabled = Settings.Secure.getInt(
                contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
            if (enabled == 1) {
                val services = Settings.Secure.getString(
                    contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return false
                services.contains(packageName, ignoreCase = true)
            } else false
        } catch (e: Exception) {
            false
        }
    }
}
