package com.strss.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/** Watches the foreground app. When its daily timer is used up it nudges with a notification + haptics. */
class LimitService : Service() {
    private val h = Handler(Looper.getMainLooper())
    private val lastNag = HashMap<String, Long>()
    private var n = 0
    private val loop = object : Runnable {
        override fun run() { tickCheck(); h.postDelayed(this, 15_000) }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifs.init(this)
        val note = NotificationCompat.Builder(this, "service").setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Strss is tracking your app timers").setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, note)
        h.removeCallbacks(loop); h.post(loop)
        return START_STICKY
    }

    private fun tickCheck() {
        if (!Usage.hasAccess(this)) return
        if (++n % 8 == 0) StrssWidget.refresh(this)
        val limits = Store(this).limits()
        if (limits.isEmpty()) return
        val now = System.currentTimeMillis()
        val used = Usage.apps(this, Usage.dayStart(), now).associate { it.pkg to it }
        val fg = Usage.foreground(this) ?: return
        val min = limits[fg.first] ?: return
        val ms = (used[fg.first]?.ms ?: 0L) + (now - fg.second)
        if (ms >= min * 60_000L && now - (lastNag[fg.first] ?: 0L) > 5 * 60_000L) {
            lastNag[fg.first] = now
            Haptics.heavy(this)
            val label = used[fg.first]?.label ?: fg.first
            Notifs.post(this, 100 + (fg.first.hashCode() and 0xff), "limits",
                "Time limit reached", "You've used $label for ${min} min today. Take a break.")
        }
    }

    override fun onDestroy() { h.removeCallbacks(loop); super.onDestroy() }

    companion object {
        fun start(c: Context) {
            if (!Usage.hasAccess(c) || Store(c).limits().isEmpty()) return
            try { ContextCompat.startForegroundService(c, Intent(c, LimitService::class.java)) } catch (_: Exception) { }
        }
    }
}
