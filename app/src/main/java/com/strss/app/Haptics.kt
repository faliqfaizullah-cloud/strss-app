package com.strss.app

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

/** Haptic feedback used on every tap, toggle, timer change and breathing phase. */
object Haptics {
    private fun play(c: Context, predefined: Int, ms: Long, amp: Int) {
        val v = c.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (!v.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= 29) v.vibrate(VibrationEffect.createPredefined(predefined))
            else v.vibrate(VibrationEffect.createOneShot(ms, amp))
        } catch (_: Exception) {
        }
    }

    fun tick(c: Context) = play(c, VibrationEffect.EFFECT_TICK, 8, 60)
    fun click(c: Context) = play(c, VibrationEffect.EFFECT_CLICK, 15, 120)
    fun heavy(c: Context) = play(c, VibrationEffect.EFFECT_HEAVY_CLICK, 30, 220)
    fun double(c: Context) = play(c, VibrationEffect.EFFECT_DOUBLE_CLICK, 40, 180)
}
