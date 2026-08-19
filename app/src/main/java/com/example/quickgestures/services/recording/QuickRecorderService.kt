package com.example.quickgestures.services.recording

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.quickgestures.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * تسجيل صوتي شفاف بالكامل عن قصد — قرار نهائي وثابت بالمشروع:
 * إشعار دائم لا يمكن إخفاؤه طول فترة التسجيل، ما في تسجيل خفي بالتطبيق إطلاقاً.
 */
class QuickRecorderService : Service() {

    private var recorder: MediaRecorder? = null
    private val channelId = "quick_touch_recording_channel"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildPersistentNotification())
        startRecording()
        return START_STICKY
    }

    private fun startRecording() {
        val outputDir = File(filesDir, "secure_recordings").apply { mkdirs() }
        val fileName = "REC_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.m4a"
        val outputFile = File(outputDir, fileName)

        recorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44_100)
            setOutputFile(outputFile.absolutePath)
            prepare()
            start()
        }
    }

    private fun buildPersistentNotification() =
        NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.recording_service_channel))
            .setContentText("جاري التسجيل الآن")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true) // إشعار دائم لا يمكن إخفاؤه — شرط أساسي وغير قابل للتفاوض
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.recording_service_channel),
                NotificationManager.IMPORTANCE_HIGH
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        recorder?.apply {
            runCatching { stop() }
            release()
        }
        recorder = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 4201
    }
}
