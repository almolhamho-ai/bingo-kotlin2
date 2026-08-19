package com.example.quickgestures.utils

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.bluetooth.BluetoothAdapter
import com.example.quickgestures.data.ActionCategory
import com.example.quickgestures.data.GestureAction
import com.example.quickgestures.services.recording.QuickRecorderService

/** منفّذ مركزي لكل الإجراءات المتاحة بالكتالوج، يُستدعى من الكرة، إيماءات الحافة، والروتينات. */
class ActionExecutor(private val context: Context) {

    fun execute(action: GestureAction) {
        when (action.id) {
            "flashlight_toggle" -> toggleFlashlight()
            "screenshot" -> requestSystemAction("screenshot")
            "back" -> requestSystemAction("back")
            "home" -> requestSystemAction("home")
            "recents" -> requestSystemAction("recents")
            "volume_mute" -> toggleMute()
            "media_play_pause" -> sendMediaKey()
            "wifi_toggle" -> toggleWifi()
            "bt_toggle" -> toggleBluetooth()
            "dnd_toggle" -> requestSystemAction("dnd")
            "start_recording" -> startTransparentRecording()
            "open_app" -> { /* يحتاج اختيار تطبيق محدد من واجهة الإعدادات */ }
            else -> { /* إجراء غير معروف - تجاهل بأمان */ }
        }
    }

    private fun toggleFlashlight() {
        // التنفيذ الفعلي عبر CameraManager.setTorchMode بمكان مركزي بالتطبيق (خدمة أو مدير مخصص)
        val intent = Intent("com.example.quickgestures.ACTION_TOGGLE_FLASHLIGHT")
        context.sendBroadcast(intent)
    }

    private fun toggleMute() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (current > 0) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        } else {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, max / 2, 0)
        }
    }

    private fun sendMediaKey() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.dispatchMediaKeyEvent(
            android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        )
    }

    private fun toggleWifi() {
        // بدءاً من أندرويد 10 لازم تفتح لوحة الإعدادات السريعة، ما في صلاحية تبديل مباشر لتطبيق عادي
        context.startActivity(Intent(android.provider.Settings.Panel.ACTION_WIFI).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun toggleBluetooth() {
        context.startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun requestSystemAction(action: String) {
        val intent = Intent("com.example.quickgestures.ACTION_SYSTEM_$action")
        context.sendBroadcast(intent)
    }

    private fun startTransparentRecording() {
        val intent = Intent(context, QuickRecorderService::class.java)
        context.startForegroundService(intent)
    }
}
