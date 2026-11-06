package com.strss.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** 2x2 glass widget (28dp rounded) showing the Daily Stress Index and screen time. */
class StrssWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) = refresh(c)

    companion object {
        fun refresh(c: Context) {
            val m = AppWidgetManager.getInstance(c)
            val ids = m.getAppWidgetIds(ComponentName(c, StrssWidget::class.java))
            if (ids.isEmpty()) return
            val snap = try { Usage.snapshot(c) } catch (e: Exception) { null }
            val rv = RemoteViews(c.packageName, R.layout.widget_strss)
            val ok = snap != null && snap.access
            rv.setTextViewText(R.id.w_level, if (ok) snap!!.stress.toString() else "--")
            rv.setTextViewText(R.id.w_time, if (ok) "Screen " + dur(snap!!.totalMs) else "Allow usage access")
            rv.setTextViewText(R.id.w_insight, if (ok) Insight.short(snap!!.stress) else "Open Strss to begin.")
            rv.setOnClickPendingIntent(
                R.id.w_root,
                PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            )
            ids.forEach { m.updateAppWidget(it, rv) }
        }

        private fun dur(ms: Long): String {
            val m = ms / 60000
            return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
        }
    }
}
