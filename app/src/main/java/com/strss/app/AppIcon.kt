package com.strss.app

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** Switches the launcher icon between the default one and the shoe-box one. */
object AppIcon {
    private fun box(c: Context) = ComponentName(c, "com.strss.app.BoxIcon")
    private fun def(c: Context) = ComponentName(c, "com.strss.app.DefaultIcon")

    fun isBox(c: Context): Boolean =
        c.packageManager.getComponentEnabledSetting(box(c)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    fun set(c: Context, useBox: Boolean) {
        val pm = c.packageManager
        // enable the new entry first so the app never has zero launcher entries
        pm.setComponentEnabledSetting(
            if (useBox) box(c) else def(c), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        pm.setComponentEnabledSetting(
            if (useBox) def(c) else box(c), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
    }
}
