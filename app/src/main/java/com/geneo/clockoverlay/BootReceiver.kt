package com.geneo.clockoverlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Fires when the Geneo board finishes booting. If the overlay permission has already been
 * granted once (see MainActivity), the clock is relaunched with zero taps required.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            if (Settings.canDrawOverlays(context)) {
                ClockOverlayService.start(context)
            }
            // If permission was never granted, nothing happens silently here --
            // Android will not allow a background receiver to request it. The user
            // grants it once from MainActivity and every boot after that is automatic.
        }
    }
}
