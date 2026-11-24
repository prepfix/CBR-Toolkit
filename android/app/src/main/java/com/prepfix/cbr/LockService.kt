package com.prepfix.cbr

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * While the phone is locked (see LockLogic), checks once a second which app is on screen.
 * Any app other than CBR Toolkit, the phone/call screens and the keyboard is covered by CBR Toolkit again.
 */
class LockService : Service() {
    companion object {
        private const val CHANNEL = "cbr_guard"
        private val ALWAYS_ALLOWED = setOf(
            "android", "com.android.systemui", "com.android.phone", "com.android.server.telecom",
            "com.android.incallui", "com.android.dialer", "com.google.android.dialer",
            "com.samsung.android.incallui", "com.samsung.android.dialer"
        )

        fun start(ctx: Context) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, LockService::class.java))
            } catch (_: Exception) {
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastPkg = ""
    private val loop = object : Runnable {
        override fun run() {
            try {
                tick()
            } catch (_: Exception) {
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, 1, notification(), type)
        handler.removeCallbacks(loop)
        handler.post(loop)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        scheduleRestart()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(loop)
        hideOverlay()
        scheduleRestart()
        super.onDestroy()
    }

    private fun scheduleRestart() {
        try {
            val pi = PendingIntent.getForegroundService(
                this, 7, Intent(this, LockService::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
            )
            (getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                .set(AlarmManager.RTC, System.currentTimeMillis() + 1500, pi)
        } catch (_: Exception) {
        }
    }

    private var overlay: View? = null

    private fun tick() {
        if (!LockLogic.isLockedNow(this)) { hideOverlay(); return }
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) return
        val pkg = foregroundPackage() ?: return
        if (pkg == packageName || allowed(pkg)) { hideOverlay(); return }
        // Another app is on screen while locked: cover it, then bring CBR Toolkit back to the front.
        showOverlay()
        launchMain()
    }

    private fun launchMain() {
        val i = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        try {
            startActivity(i)
        } catch (_: Exception) {
        }
    }

    /** A full-screen cover that also works if the phone refuses to start the app from the background. */
    private fun showOverlay() {
        if (overlay != null || !Settings.canDrawOverlays(this)) return
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val root = LinearLayout(this)
            root.orientation = LinearLayout.VERTICAL
            root.gravity = Gravity.CENTER
            root.setPadding(60, 60, 60, 60)
            root.setBackgroundColor(Color.parseColor("#F20A2A6B"))
            val title = TextView(this)
            title.text = "🔒 Your phone is locked"
            title.textSize = 26f
            title.setTextColor(Color.WHITE)
            title.gravity = Gravity.CENTER
            val body = TextView(this)
            body.text = "Finish today's reading to unlock: read a chapter, then write a Fact, an Insight and a Deed."
            body.textSize = 16f
            body.setTextColor(Color.parseColor("#DCE6FF"))
            body.gravity = Gravity.CENTER
            body.setPadding(0, 24, 0, 36)
            val btn = Button(this)
            btn.text = "Open CBR Toolkit"
            btn.isAllCaps = false
            btn.setOnClickListener { launchMain() }
            root.addView(title)
            root.addView(body)
            root.addView(btn)
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            wm.addView(root, lp)
            overlay = root
        } catch (_: Exception) {
            overlay = null
        }
    }

    private fun hideOverlay() {
        val v = overlay ?: return
        try {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v)
        } catch (_: Exception) {
        }
        overlay = null
    }

    /** The app that most recently came to the front (needs "Usage access"). */
    private fun foregroundPackage(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val events = usm.queryEvents(end - 10_000, end)
        val e = UsageEvents.Event()
        var pkg: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == 1) pkg = e.packageName // 1 = app moved to the foreground
        }
        if (pkg != null) lastPkg = pkg
        return if (lastPkg.isEmpty()) null else lastPkg
    }

    private fun allowed(pkg: String): Boolean {
        if (pkg in ALWAYS_ALLOWED) return true
        try {
            val tm = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            if (pkg == tm.defaultDialerPackage) return true
        } catch (_: Exception) {
        }
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            if (imm.enabledInputMethodList.any { it.packageName == pkg }) return true
        } catch (_: Exception) {
        }
        return false
    }

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "CBR Toolkit lock", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("CBR Toolkit")
            .setContentText("Protecting your Bible reading time")
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }
}
