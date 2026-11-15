package com.focusguard.app.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.work.*
import com.focusguard.app.data.FocusGuardPrefs
import java.util.concurrent.TimeUnit

object BackgroundAndDnsManager {

    const val RECOMMENDED_FAMILY_DNS = "family.adguard-dns.com"

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    @SuppressLint("BatteryLife")
    fun requestBatteryOptimizationExemption(context: Context) {
        if (!isIgnoringBatteryOptimizations(context)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun openSystemPrivateDnsSettings(context: Context) {
        val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openAlwaysOnVpnSettings(context: Context) {
        val intent = Intent(Settings.ACTION_VPN_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun getActivePrivateDnsHostname(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork ?: return null
        val linkProps = cm.getLinkProperties(activeNetwork) ?: return null
        return if (linkProps.isPrivateDnsActive) linkProps.privateDnsServerName else null
    }

    fun isFamilyPrivateDnsActive(context: Context): Boolean {
        val host = getActivePrivateDnsHostname(context)?.lowercase() ?: return false
        return host.contains("family.adguard-dns.com") ||
            host.contains("adult-filter-dns.cleanbrowsing.org") ||
            host.contains("family.cloudflare-dns.com")
    }

    fun startVpnIfPermitted(context: Context): Boolean {
        if (VpnService.prepare(context) == null) {
            val vpnIntent = Intent(context, FocusGuardVpnService::class.java)
            ContextCompat.startForegroundService(context, vpnIntent)
            return true
        }
        return false
    }

    fun scheduleWatchdog(context: Context) {
        val workRequest = PeriodicWorkRequestBuilder<ProtectionWatchdogWorker>(
            15, TimeUnit.MINUTES
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "FocusGuardProtectionWatchdog",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }
}

class ProtectionWatchdogWorker(
    appContext: Context,
    params: WorkerParameters
) : Worker(appContext, params) {

    override fun doWork(): Result {
        if (FocusGuardPrefs.isProtectionActive(applicationContext)) {
            BackgroundAndDnsManager.startVpnIfPermitted(applicationContext)
        }
        return Result.success()
    }
}
