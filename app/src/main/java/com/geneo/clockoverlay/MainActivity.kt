package com.geneo.clockoverlay

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.geneo.clockoverlay.databinding.ActivityMainBinding

/**
 * Setup screen. The overlay clock itself is drawn by ClockOverlayService, not by this
 * Activity. This screen exists only because Android requires a one-time, user-driven
 * grant of the "draw over other apps" permission -- that grant cannot be requested
 * silently or from a boot receiver. Once granted, it persists across reboots and the
 * BootReceiver starts the clock automatically with no further taps needed.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnGrantOverlay.setOnClickListener { requestOverlayPermission() }
        binding.btnIgnoreBattery.setOnClickListener { requestIgnoreBatteryOptimizations() }
        binding.btnStartNow.setOnClickListener { startClock() }
        binding.btnStopNow.setOnClickListener { stopClock() }
        binding.btnEditTimetable.setOnClickListener {
            startActivity(Intent(this, ScheduleEditorActivity::class.java))
        }
        binding.btnTestPopup.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, R.string.status_overlay_needed, Toast.LENGTH_LONG).show()
                requestOverlayPermission()
                return@setOnClickListener
            }
            ClockOverlayService.testPopup(this)
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatusText()
    }

    private fun updateStatusText() {
        binding.tvOverlayStatus.text = if (Settings.canDrawOverlays(this))
            getString(R.string.status_overlay_granted) else getString(R.string.status_overlay_needed)
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } else {
            Toast.makeText(this, R.string.status_overlay_granted, Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            try {
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    private fun startClock() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.status_overlay_needed, Toast.LENGTH_LONG).show()
            requestOverlayPermission()
            return
        }
        ClockOverlayService.start(this)
        Toast.makeText(this, R.string.clock_started, Toast.LENGTH_SHORT).show()
    }

    private fun stopClock() {
        ClockOverlayService.stop(this)
        Toast.makeText(this, R.string.clock_stopped, Toast.LENGTH_SHORT).show()
    }
}
