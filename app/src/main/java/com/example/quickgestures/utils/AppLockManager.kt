package com.example.quickgestures.utils

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

/**
 * منطق القفل الموحّد: قائمة الحزم المقفولة + نوع القفل (بصمة الجهاز أو PIN داخلي).
 * التسجيلات الصوتية مقفولة دايماً بغض النظر عن هالإعداد (قرار ثابت بالمشروع).
 */
class AppLockManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_lock_prefs", Context.MODE_PRIVATE)

    enum class LockMethod { DEVICE_BIOMETRIC, INTERNAL_PIN }

    var lockMethod: LockMethod
        get() = LockMethod.valueOf(prefs.getString("lock_method", LockMethod.DEVICE_BIOMETRIC.name)!!)
        set(value) = prefs.edit().putString("lock_method", value.name).apply()

    var lockedPackages: Set<String>
        get() = prefs.getStringSet("locked_packages", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("locked_packages", value).apply()

    fun isPackageLocked(packageName: String): Boolean =
        packageName in lockedPackages || packageName == "com.example.quickgestures"

    fun setInternalPin(pin: String) {
        prefs.edit().putString("pin_hash", sha256(pin)).apply()
    }

    fun verifyInternalPin(pin: String): Boolean {
        val stored = prefs.getString("pin_hash", null) ?: return false
        return stored == sha256(pin)
    }

    fun hasInternalPinSet(): Boolean = prefs.contains("pin_hash")

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
