package com.focusguard.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focusguard.app.data.FocusGuardPrefs
import com.focusguard.app.ui.InterventionActivity

class FocusGuardAccessibilityService : AccessibilityService() {

    private val blockedPackages = setOf(
        "com.zhiliaoapp.musically"
    )

    private val restrictedKeywords = listOf(
        "porn", "xvideos", "xnxx", "pornhub", "onlyfans", "hentai", "chaturbate"
    )

    private var lastTriggerMs = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 120
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !FocusGuardPrefs.isProtectionActive(this)) return
        val pkg = event.packageName?.toString() ?: return

        if (blockedPackages.contains(pkg)) {
            launchIntervention("Restricted App Launch: $pkg")
            return
        }

        if (pkg.contains("chrome") || pkg.contains("browser") || pkg.contains("firefox")) {
            val root = rootInActiveWindow ?: return
            inspectNode(root, 0)
        }
    }

    private fun inspectNode(node: AccessibilityNodeInfo, depth: Int) {
        if (depth > 10) return
        val text = node.text?.toString()?.lowercase()
        if (!text.isNullOrBlank()) {
            for (kw in restrictedKeywords) {
                if (text.contains(kw)) {
                    launchIntervention("Browser Content Trigger: $kw")
                    return
                }
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            inspectNode(child, depth + 1)
        }
    }

    private fun launchIntervention(reason: String) {
        val now = System.currentTimeMillis()
        if (now - lastTriggerMs < 3500L) return
        lastTriggerMs = now

        FocusGuardPrefs.incrementBlockedCount(this)
        val intent = Intent(this, InterventionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_TRIGGER_REASON", reason)
        }
        startActivity(intent)
    }

    override fun onInterrupt() {}
}
