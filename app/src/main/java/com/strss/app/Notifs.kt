package com.strss.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object Notifs {
    fun init(c: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("limits", "App timers", NotificationManager.IMPORTANCE_HIGH).apply { enableVibration(true) })
        nm.createNotificationChannel(NotificationChannel("bed", "Bed schedule", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel("focus", "Focus sessions", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel("service", "Monitoring", NotificationManager.IMPORTANCE_MIN))
    }

    fun post(c: Context, id: Int, channel: String, title: String, text: String) {
        val pi = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(c, channel)
            .setSmallIcon(R.drawable.ic_stat).setContentTitle(title).setContentText(text)
            .setContentIntent(pi).setAutoCancel(true).build()
        try { NotificationManagerCompat.from(c).notify(id, n) } catch (_: SecurityException) { }
    }
}
