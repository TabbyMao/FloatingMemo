package com.example.floatingmemo

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Settings screen: grants the "display over other apps" permission and
 * starts / stops the floating memo.
 */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var permissionButton: Button
    private lateinit var toggleButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (24 * resources.displayMetrics.density).toInt()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad, pad, pad)
        }
        status = TextView(this).apply {
            textSize = 16f
            setPadding(0, 0, 0, pad)
        }
        permissionButton = Button(this).apply {
            text = "1. Allow display over other apps"
            setOnClickListener {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        }
        toggleButton = Button(this).apply {
            setOnClickListener {
                val service = Intent(this@MainActivity, FloatingMemoService::class.java)
                if (FloatingMemoService.running) {
                    stopService(service)
                    FloatingMemoService.running = false
                } else {
                    startForegroundService(service)
                    FloatingMemoService.running = true
                }
                refresh()
            }
        }
        box.addView(status)
        box.addView(permissionButton)
        box.addView(toggleButton)
        setContentView(box)

        // Android 13+ needs permission to show the "running" notification.
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val allowed = Settings.canDrawOverlays(this)
        permissionButton.isEnabled = !allowed
        toggleButton.isEnabled = allowed
        toggleButton.text =
            if (FloatingMemoService.running) "Stop floating memo" else "2. Start floating memo"
        status.text = when {
            !allowed -> "Permission needed: tap button 1 and turn the switch on, then come back."
            FloatingMemoService.running -> "Floating memo is on. Press Home and look for the thin blue line on the left edge."
            else -> "Ready. Tap button 2 to show the floating memo."
        }
    }
}
