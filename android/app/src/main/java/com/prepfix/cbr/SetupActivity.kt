package com.prepfix.cbr

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** What must be switched on for the lock to work. */
object Setup {
    fun overlayOk(c: Context) = Settings.canDrawOverlays(c)

    fun usageOk(c: Context): Boolean {
        val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun notifyOk(c: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun batteryOk(c: Context): Boolean =
        (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(c.packageName)

    /** Code, overlay and usage access are required. Notifications and battery are strongly advised. */
    fun complete(c: Context): Boolean = Prefs(c).hasCode() && overlayOk(c) && usageOk(c)
}

class SetupActivity : Activity() {
    private lateinit var codeStatus: TextView
    private lateinit var overlayStatus: TextView
    private lateinit var usageStatus: TextView
    private lateinit var notifyStatus: TextView
    private lateinit var batteryStatus: TextView
    private lateinit var startBtn: Button
    private lateinit var codeBox: EditText

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(parent: LinearLayout, text: String, bold: Boolean = false, size: Float = 15f): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(Color.parseColor("#17212F"))
        if (bold) t.setTypeface(t.typeface, android.graphics.Typeface.BOLD)
        t.setPadding(0, dp(6), 0, dp(2))
        parent.addView(t)
        return t
    }

    private fun button(parent: LinearLayout, text: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = text
        b.isAllCaps = false
        b.setOnClickListener { onClick() }
        parent.addView(b)
        return b
    }

    private fun status(parent: LinearLayout): TextView {
        val t = TextView(this)
        t.textSize = 14f
        t.setPadding(0, 0, 0, dp(10))
        parent.addView(t)
        return t
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(22), dp(40), dp(22), dp(30))
        col.setBackgroundColor(Color.WHITE)
        scroll.addView(col)
        setContentView(scroll)

        label(col, "Set up the CBR Toolkit lock", true, 22f)
        label(col, "Every morning from the lock time (4:00 AM unless you change it in Settings) this phone stays locked until you finish your CBR: read a chapter, then write a Fact, an Insight and a Deed (3 sentences each). While locked, other apps are covered by CBR Toolkit. Phone calls still work.")
        label(col, "If you set this up after 4:00 AM and have not finished today's reading, the phone locks straight away. Use the emergency code if you must.")

        label(col, "1. Choose an emergency code", true)
        label(col, "4 to 8 numbers. Give it to a pastor or someone you trust. It unlocks the phone for 30 minutes.", false, 13f)
        codeBox = EditText(this)
        codeBox.hint = "For example 4821"
        codeBox.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        codeBox.filters = arrayOf(android.text.InputFilter.LengthFilter(8))
        col.addView(codeBox)
        button(col, "Save code") {
            val c = codeBox.text.toString().trim()
            if (Regex("^\\d{4,8}$").matches(c)) {
                Prefs(this).code = c
                codeBox.setText("")
                Toast.makeText(this, "Code saved", Toast.LENGTH_SHORT).show()
                refresh()
            } else {
                Toast.makeText(this, "Please type 4 to 8 numbers only", Toast.LENGTH_SHORT).show()
            }
        }
        codeStatus = status(col)

        label(col, "2. Allow display over other apps", true)
        label(col, "Lets CBR Toolkit come back to the front when you open another app while locked.", false, 13f)
        button(col, "Open this setting") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        overlayStatus = status(col)

        label(col, "3. Allow usage access", true)
        label(col, "Lets CBR Toolkit see which app is on screen. Find CBR Toolkit in the list and switch it on.", false, 13f)
        button(col, "Open this setting") { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        usageStatus = status(col)

        label(col, "4. Allow notifications (advised)", true)
        button(col, "Allow") {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        notifyStatus = status(col)

        label(col, "5. Do not limit battery use (advised)", true)
        label(col, "Stops the phone from switching the lock off to save power.", false, 13f)
        button(col, "Open this setting") {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
        batteryStatus = status(col)

        startBtn = Button(this)
        startBtn.text = "Start CBR Toolkit"
        startBtn.isAllCaps = false
        startBtn.setOnClickListener {
            if (Setup.complete(this)) {
                LockService.start(this)
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }
        col.addView(startBtn)
        (startBtn.layoutParams as LinearLayout.LayoutParams).gravity = Gravity.CENTER_HORIZONTAL
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    private fun mark(t: TextView, ok: Boolean, good: String, bad: String) {
        t.text = if (ok) "✓ $good" else "✗ $bad"
        t.setTextColor(Color.parseColor(if (ok) "#1B7A3E" else "#B3261E"))
    }

    private fun refresh() {
        mark(codeStatus, Prefs(this).hasCode(), "Code saved", "No code yet")
        mark(overlayStatus, Setup.overlayOk(this), "Allowed", "Not allowed yet")
        mark(usageStatus, Setup.usageOk(this), "Allowed", "Not allowed yet")
        mark(notifyStatus, Setup.notifyOk(this), "Allowed", "Not allowed (the lock still works)")
        mark(batteryStatus, Setup.batteryOk(this), "Not limited", "Limited (the lock may stop on some phones)")
        startBtn.isEnabled = Setup.complete(this)
        startBtn.alpha = if (startBtn.isEnabled) 1f else 0.4f
        startBtn.visibility = View.VISIBLE
    }
}
