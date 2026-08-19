package com.example.quickgestures.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.quickgestures.utils.AppLockManager

/**
 * لازم تمديد FragmentActivity وليس ComponentActivity لأن BiometricPrompt يتطلب ذلك.
 */
class AppLockActivity : FragmentActivity() {

    private lateinit var lockManager: AppLockManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lockManager = AppLockManager(applicationContext)

        val targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE) ?: ""

        when (lockManager.lockMethod) {
            AppLockManager.LockMethod.DEVICE_BIOMETRIC -> showBiometricPrompt(targetPackage)
            AppLockManager.LockMethod.INTERNAL_PIN -> setContent {
                InternalPinUnlockScreen(
                    lockManager = lockManager,
                    onUnlocked = { finishUnlocked() },
                    onCancelled = { finish() }
                )
            }
        }
    }

    private fun showBiometricPrompt(targetPackage: String) {
        val biometricManager = BiometricManager.from(this)
        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            finish()
            return
        }

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    finishUnlocked()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    finish()
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("فتح القفل")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        prompt.authenticate(promptInfo)
    }

    private fun finishUnlocked() {
        setResult(RESULT_OK)
        finish()
    }

    companion object {
        const val EXTRA_TARGET_PACKAGE = "extra_target_package"
    }
}
