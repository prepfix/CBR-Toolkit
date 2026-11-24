package com.prepfix.cbr

import android.content.Context
import java.util.Calendar
import java.util.Locale

/** Small settings store shared by the reading page, the setup screen and the lock service. */
class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("cbr", Context.MODE_PRIVATE)

    var lockTime: String
        get() = sp.getString("lock", "04:00") ?: "04:00"
        set(v) { sp.edit().putString("lock", v).apply() }

    /** Dates (yyyy-MM-dd) on which the reading was finished and the phone unlocked. */
    var finished: Set<String>
        get() = HashSet(sp.getStringSet("finished", emptySet()) ?: emptySet())
        set(v) { sp.edit().putStringSet("finished", HashSet(v)).apply() }

    var bypassUntil: Long
        get() = sp.getLong("bypass", 0L)
        set(v) { sp.edit().putLong("bypass", v).apply() }

    var code: String
        get() = sp.getString("code", "") ?: ""
        set(v) { sp.edit().putString("code", v).apply() }

    fun hasCode(): Boolean = Regex("^\\d{4,8}$").matches(code)
}

/** Same rules as the PC app (lockLogic.js): locked from the lock time until today's reading is finished. */
object LockLogic {
    fun ymd(c: Calendar): String = String.format(
        Locale.US, "%04d-%02d-%02d",
        c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)
    )

    fun lockMinutes(t: String): Int {
        val m = Regex("^(\\d{1,2}):(\\d{2})$").find(t) ?: return 4 * 60
        return minOf(23, m.groupValues[1].toInt()) * 60 + minOf(59, m.groupValues[2].toInt())
    }

    fun shouldLock(lockTime: String, finished: Set<String>, now: Calendar, bypassUntil: Long): Boolean {
        if (bypassUntil > 0 && now.timeInMillis < bypassUntil) return false
        val mins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        if (mins < lockMinutes(lockTime)) return false
        if (finished.contains(ymd(now))) return false
        return true
    }

    fun isLockedNow(ctx: Context): Boolean {
        val p = Prefs(ctx)
        return shouldLock(p.lockTime, p.finished, Calendar.getInstance(), p.bypassUntil)
    }
}
