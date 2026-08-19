package com.example.quickgestures.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.example.quickgestures.ui.AppLockActivity
import com.example.quickgestures.utils.AppLockManager

/**
 * ينفّذ الأزرار العامة (رجوع/هوم) المطلوبة من ActionExecutor، ويراقب أي تطبيق يُفتح
 * لإنفاذ قفل التطبيقات إذا كان محددًا بقائمة AppLockManager.lockedPackages.
 */
class AccessibilityShortcutService : AccessibilityService() {

    private lateinit var lockManager: AppLockManager
    private var lastUnlockedPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        lockManager = AppLockManager(applicationContext)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (packageName == this.packageName) return

        if (lockManager.isPackageLocked(packageName) && packageName != lastUnlockedPackage) {
            val intent = Intent(this, AppLockActivity::class.java).apply {
                putExtra(AppLockActivity.EXTRA_TARGET_PACKAGE, packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }
    }

    fun performBack() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun performHome() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun performRecents() = performGlobalAction(GLOBAL_ACTION_RECENTS)

    override fun onInterrupt() = Unit
}
