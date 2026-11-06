package com.strss.app

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun dayKey(ms: Long): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(ms))

class Store(c: Context) {
    private val p = c.getSharedPreferences("strss", Context.MODE_PRIVATE)

    fun setLimit(pkg: String, minutes: Int) {
        p.edit().apply { if (minutes <= 0) remove("limit_$pkg") else putInt("limit_$pkg", minutes) }.apply()
    }

    fun limits(): Map<String, Int> =
        p.all.filterKeys { it.startsWith("limit_") }
            .map { it.key.removePrefix("limit_") to (it.value as Int) }.toMap()

    var bedEnabled: Boolean
        get() = p.getBoolean("bed_on", true)
        set(v) { p.edit().putBoolean("bed_on", v).apply() }
    var bedMin: Int
        get() = p.getInt("bed", 23 * 60)
        set(v) { p.edit().putInt("bed", v).apply() }
    var wakeMin: Int
        get() = p.getInt("wake", 7 * 60)
        set(v) { p.edit().putInt("wake", v).apply() }
    var windDown: Int
        get() = p.getInt("wind", 30)
        set(v) { p.edit().putInt("wind", v).apply() }
    var premium: Boolean
        get() = p.getBoolean("premium", true)
        set(v) { p.edit().putBoolean("premium", v).apply() }

    // ---------- Focus / flip sessions ----------
    var goalMin: Int
        get() = p.getInt("goal_min", 25)
        set(v) { p.edit().putInt("goal_min", v).apply() }
    var breakMin: Int
        get() = p.getInt("break_min", 5)
        set(v) { p.edit().putInt("break_min", v).apply() }
    var streakMin: Int
        get() = p.getInt("streak_min", 10)
        set(v) { p.edit().putInt("streak_min", v).apply() }

    fun addFocus(startMs: Long, endMs: Long) {
        val dur = endMs - startMs
        val k = "fd_" + dayKey(endMs)
        val sess = (p.getString("f_sessions", "") ?: "").split(";").filter { it.isNotBlank() }.takeLast(59) + "$startMs,$endMs"
        p.edit()
            .putLong(k, p.getLong(k, 0L) + dur)
            .putLong("f_total", p.getLong("f_total", 0L) + dur)
            .putString("f_sessions", sess.joinToString(";"))
            .apply()
    }

    fun focusDays(): Map<String, Long> =
        p.all.filterKeys { it.startsWith("fd_") }.map { it.key.removePrefix("fd_") to (it.value as Long) }.toMap()

    fun focusTotal(): Long = p.getLong("f_total", 0L)

    fun sessions(): List<Pair<Long, Long>> =
        (p.getString("f_sessions", "") ?: "").split(";").filter { it.contains(",") }
            .mapNotNull { val a = it.split(","); val s = a[0].toLongOrNull(); val e = a[1].toLongOrNull(); if (s != null && e != null) s to e else null }
            .reversed()

    /** Consecutive days (ending today, or yesterday if today isn't done yet) with at least streakMin of focus. */
    fun streak(): Int {
        val days = focusDays()
        val need = streakMin * 60_000L
        val cal = Calendar.getInstance()
        fun ok() = (days[dayKey(cal.timeInMillis)] ?: 0L) >= need
        if (!ok()) cal.add(Calendar.DAY_OF_YEAR, -1)
        var n = 0
        while (n < 5000 && ok()) { n++; cal.add(Calendar.DAY_OF_YEAR, -1) }
        return n
    }
}
