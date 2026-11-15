package com.focusguard.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focusguard.app.data.FocusGuardPrefs
import com.focusguard.app.service.BackgroundAndDnsManager

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            if (FocusGuardPrefs.isProtectionActive(context)) {
                BackgroundAndDnsManager.scheduleWatchdog(context)
                BackgroundAndDnsManager.startVpnIfPermitted(context)
            }
        }
    }
}
