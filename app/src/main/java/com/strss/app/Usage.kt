package com.strss.app

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import java.util.Calendar

data class AppUse(val pkg: String, val label: String, val ms: Long)

data class Snap(
    val access: Boolean,
    val totalMs: Long,
    val apps: List<AppUse>,
    val hourly: LongArray,
    val nightMs: Long,
    val overruns: Int,
    val stress: Int
) {
    val calm: Int get() = (100 - stress).coerceIn(0, 100)
}

object Insight {
    fun text(s: Int) = when {
        s < 25 -> "Your stress levels are low. Keep up the balanced use of your device."
        s < 50 -> "Your stress levels are steady. A short screen break could help."
        s < 75 -> "Screen use is high today. Try a breathing session and put the phone down earlier tonight."
        else -> "Screen load is very high. Take a break and wind down before bed."
    }

    fun short(s: Int) = when {
        s < 25 -> "Low stress. Keep it balanced."
        s < 50 -> "Steady. Try a short screen break."
        s < 75 -> "High use today. Time to breathe."
        else -> "Very high. Take a break now."
    }
}

object Usage {
    fun hasAccess(c: Context): Boolean {
        val ao = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29)
            ao.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        else @Suppress("DEPRECATION")
        ao.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun dayStart(daysAgo: Int = 0): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, -daysAgo)
    }.timeInMillis

    private fun usm(c: Context) = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    private fun launchable(c: Context): Set<String> {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return c.packageManager.queryIntentActivities(i, 0).map { it.activityInfo.packageName }.toSet()
    }

    private fun label(pm: PackageManager, pkg: String) = try {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { pkg }

    fun installed(c: Context): List<Pair<String, String>> {
        val pm = c.packageManager
        return launchable(c).filter { it != c.packageName }.map { it to label(pm, it) }
            .sortedBy { it.second.lowercase() }
    }

    fun apps(c: Context, from: Long, to: Long): List<AppUse> {
        val ok = launchable(c)
        val pm = c.packageManager
        return usm(c).queryAndAggregateUsageStats(from, to).values
            .filter { it.totalTimeInForeground > 0 && it.packageName in ok && it.packageName != c.packageName }
            .map { AppUse(it.packageName, label(pm, it.packageName), it.totalTimeInForeground) }
            .sortedByDescending { it.ms }
    }

    /** Foreground time per hour of today, built from usage events. */
    fun hourly(c: Context): LongArray {
        val start = dayStart()
        val now = System.currentTimeMillis()
        val ok = launchable(c)
        val arr = LongArray(24)
        val open = HashMap<String, Long>()
        val ev = usm(c).queryEvents(start, now)
        val e = UsageEvents.Event()
        fun add(s: Long, en: Long) {
            var cur = s
            while (cur < en) {
                val h = ((cur - start) / 3_600_000L).toInt().coerceIn(0, 23)
                val seg = minOf(en, start + (h + 1) * 3_600_000L) - cur
                if (seg <= 0) break
                arr[h] += seg
                cur += seg
            }
        }
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            if (e.packageName !in ok || e.packageName == c.packageName) continue
            when (e.eventType) {
                1 -> open[e.packageName] = e.timeStamp
                2 -> open.remove(e.packageName)?.let { add(it, e.timeStamp) }
            }
        }
        open.values.forEach { add(it, now) }
        return arr
    }

    /** Package currently in the foreground and when it came forward, or null. */
    fun foreground(c: Context): Pair<String, Long>? {
        val ev = usm(c).queryEvents(dayStart(), System.currentTimeMillis())
        val e = UsageEvents.Event()
        var pkg: String? = null; var type = 0; var ts = 0L
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            if (e.eventType == 1 || e.eventType == 2) { pkg = e.packageName; type = e.eventType; ts = e.timeStamp }
        }
        return if (type == 1 && pkg != null) pkg to ts else null
    }

    fun weekly(c: Context): List<Long> {
        val now = System.currentTimeMillis()
        return (6 downTo 0).map { d ->
            val from = dayStart(d)
            val to = if (d == 0) now else from + 86_400_000L
            apps(c, from, to).sumOf { it.ms }
        }
    }

    private fun inBed(h: Int, bed: Int, wake: Int): Boolean {
        val b = bed / 60; val w = wake / 60
        return if (b > w) h >= b || h < w else h in b until w
    }

    fun snapshot(c: Context): Snap {
        if (!hasAccess(c)) return Snap(false, 0, emptyList(), LongArray(24), 0, 0, 0)
        val store = Store(c)
        val list = apps(c, dayStart(), System.currentTimeMillis())
        val total = list.sumOf { it.ms }
        val hourly = hourly(c)
        val night = (0..23).filter { inBed(it, store.bedMin, store.wakeMin) }.sumOf { hourly[it] }
        val used = list.associate { it.pkg to it.ms }
        val over = store.limits().count { (p, m) -> (used[p] ?: 0L) >= m * 60_000L }
        // Estimate (not a medical measure): screen hours, night-time use and exceeded timers
        val s = (total / 3_600_000.0 * 9).coerceAtMost(60.0) +
                (night / 60_000.0 / 4).coerceAtMost(25.0) +
                (over * 5).coerceAtMost(15)
        return Snap(true, total, list, hourly, night, over, s.toInt().coerceIn(0, 100))
    }
}
