package com.focusguard.app

import android.app.Application
import com.focusguard.app.service.BackgroundAndDnsManager

class FocusGuardApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        BackgroundAndDnsManager.scheduleWatchdog(this)
    }
}
