package com.strss.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

object Bed {
    private fun pi(c: Context, code: Int) = PendingIntent.getBroadcast(
        c, code, Intent(c, BedReceiver::class.java).putExtra("code", code),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun next(min: Int): Long {
        val m = ((min % 1440) + 1440) % 1440
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, m / 60); set(Calendar.MINUTE, m % 60); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    fun schedule(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pi(c, 1)); am.cancel(pi(c, 2))
        val s = Store(c)
        if (!s.bedEnabled) return
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next(s.bedMin - s.windDown), pi(c, 1))
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next(s.bedMin), pi(c, 2))
    }
}

class BedReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Notifs.init(c)
        val s = Store(c)
        if (i.getIntExtra("code", 0) == 1)
            Notifs.post(c, 200, "bed", "Wind down", "Bedtime in ${s.windDown} min. Start putting your phone away.")
        else
            Notifs.post(c, 201, "bed", "Time for bed", "Rest well. Your body recovers while you sleep.")
        Haptics.double(c)
        Bed.schedule(c)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Bed.schedule(c)
        LimitService.start(c)
    }
}
