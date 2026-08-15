package com.geneo.clockoverlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Foreground service that draws a small floating clock above every other app on the
 * board (TYPE_APPLICATION_OVERLAY), lets the user drag it anywhere on screen, updates
 * once a minute (hour:minute display, no seconds), and re-colors itself so it stays
 * readable against whatever is behind it. Also announces period changes with a
 * popup + bell sound, based on the editable schedule from Prefs.
 */
class ClockOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private lateinit var params: WindowManager.LayoutParams

    private val handler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    // ---- period-end popup ----
    private var popupView: View? = null
    private var lastCheckedMinute = -1
    private var activeMediaPlayer: MediaPlayer? = null

    private val tickRunnable = object : Runnable {
        override fun run() {
            updateClockText()
            checkPeriodTransition()
            val millisIntoMinute = System.currentTimeMillis() % 60_000L
            val delayToNextMinute = 60_000L - millisIntoMinute
            handler.postDelayed(this, delayToNextMinute)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundNotification()
        addOverlayView()
        handler.post(tickRunnable)
        Prefs.setEnabled(this, true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TEST_POPUP) {
            showPeriodPopup(buildMessage("Period 1"))
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(tickRunnable)
        overlayView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
        popupView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        popupView = null
        stopBellSound()
        Prefs.setEnabled(this, false)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyColor()
    }

    // ---------- overlay setup ----------

    private fun addOverlayView() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        if (overlayView != null) return

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.overlay_clock, null)
        overlayView = view

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = Prefs.getX(this, 24)
        params.y = Prefs.getY(this, 24)

        windowManager.addView(view, params)
        makeDraggable(view)
        applyColor()
        updateClockText()
    }

    private fun makeDraggable(view: View) {
        var initialX = 0
        var initialY = 0
        var touchStartX = 0f
        var touchStartY = 0f
        var moved = false

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchStartX).toInt()
                    val dy = (event.rawY - touchStartY).toInt()
                    if (abs(dx) > 4 || abs(dy) > 4) moved = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(v, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    Prefs.savePosition(this, params.x, params.y)
                    if (!moved) v.performClick()
                    true
                }
                else -> false
            }
        }
    }

    // ---------- clock + color ----------

    private fun updateClockText() {
        val tv = overlayView?.findViewById<TextView>(R.id.tvClock) ?: return
        tv.text = timeFormat.format(Date())
    }

    private fun applyColor() {
        val root = overlayView ?: return
        val tv = root.findViewById<TextView>(R.id.tvClock) ?: return
        val pill = root.findViewById<View>(R.id.overlayRoot)

        tv.setTextColor(Color.BLACK)
        pill?.background?.setTint(Color.parseColor("#B3FFFFFF"))
    }

    // ---------- period-end popup (editable schedule from Prefs) ----------

    /** Runs once a minute. If the clock's minute just crossed a schedule slot's end
     *  time, shows a popup + 2-second alarm sound announcing the next slot. */
    private fun checkPeriodTransition() {
        val cal = Calendar.getInstance()
        val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        if (nowMinutes == lastCheckedMinute) return
        lastCheckedMinute = nowMinutes

        val schedule = Prefs.getSchedule(this)
        val endedIndex = schedule.indexOfFirst { it.endMinutes == nowMinutes }
        if (endedIndex == -1) return

        val next = schedule.getOrNull(endedIndex + 1) ?: return // last slot ended, nothing after
        showPeriodPopup(buildMessage(next.label))
    }

    /** "Period N" slots get the exact requested phrasing with just the number; any
     *  other label (Diary Checking, Extra Class, or whatever the label is edited
     *  to) is announced by name instead. */
    private fun buildMessage(nextLabel: String): String {
        val periodNumber = Regex("""^Period (\d+)$""").find(nextLabel)?.groupValues?.get(1)
        return if (periodNumber != null) {
            "This period is over, its $periodNumber period now"
        } else {
            "This period is over, its $nextLabel now"
        }
    }

    private fun showPeriodPopup(message: String) {
        popupView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.overlay_period_popup, null)
        popupView = view

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val popupParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        popupParams.gravity = Gravity.CENTER

        // Set the real text BEFORE adding the window -- this is what actually
        // determines how big WRAP_CONTENT measures the window. Setting it after
        // addView() left the window sized to the XML placeholder text.
        view.findViewById<TextView>(R.id.tvPopupMessage).text = message

        windowManager.addView(view, popupParams)

        view.findViewById<Button>(R.id.btnPopupOk).setOnClickListener {
            popupView?.let { v -> try { windowManager.removeView(v) } catch (_: Exception) {} }
            popupView = null
            stopBellSound()
        }

        playBellSound()
    }

    /** Plays a synthesized bell-ring sound (bundled as res/raw/bell.wav) once. */
    private fun playBellSound() {
        try {
            stopBellSound()
            val mp = MediaPlayer.create(this, R.raw.bell)
            activeMediaPlayer = mp
            mp?.setOnCompletionListener {
                it.release()
                if (activeMediaPlayer == it) activeMediaPlayer = null
            }
            mp?.start()
        } catch (_: Exception) {
            // Playback failed on this device -- the popup still shows.
        }
    }

    private fun stopBellSound() {
        activeMediaPlayer?.let {
            try { if (it.isPlaying) it.stop() } catch (_: Exception) {}
            try { it.release() } catch (_: Exception) {}
        }
        activeMediaPlayer = null
    }

    // ---------- foreground notification ----------

    private fun startForegroundNotification() {
        val channelId = "geneo_clock_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Board Clock", NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Keeps the floating clock running"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Geneo Clock is active")
            .setContentText("Floating clock is running on top of all apps")
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1001, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(1001, notification)
        }
    }

    companion object {
        const val ACTION_TEST_POPUP = "com.geneo.clockoverlay.ACTION_TEST_POPUP"

        fun start(context: Context) {
            val intent = Intent(context, ClockOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ClockOverlayService::class.java))
        }

        fun testPopup(context: Context) {
            val intent = Intent(context, ClockOverlayService::class.java)
            intent.action = ACTION_TEST_POPUP
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
