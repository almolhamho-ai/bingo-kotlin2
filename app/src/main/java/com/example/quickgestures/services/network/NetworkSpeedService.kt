package com.example.quickgestures.services.network

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.net.TrafficStats
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import com.example.quickgestures.data.AppPreferences
import com.example.quickgestures.data.NetworkSpeedDisplayMode

/**
 * مؤشر عائم شفاف يرتسم فوق منطقة شريط الحالة (بدون Root ما فيه طريقة لحقن أيقونة فعلية
 * داخل شريط الحالة النظامي، فالحل هو Overlay بنفس المنطقة بصريًا).
 */
class NetworkSpeedService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: AppPreferences

    private var lastRxBytes = TrafficStats.getTotalRxBytes()
    private var lastTxBytes = TrafficStats.getTotalTxBytes()
    private var lastTimestamp = System.currentTimeMillis()

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateSpeed()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = AppPreferences(applicationContext)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupOverlay()
        handler.post(updateRunnable)
    }

    private fun setupOverlay() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 220 // بجانب أيقونات البطارية/الواي فاي/الساعة تقريبياً
            y = 6
        }

        val textView = TextView(this).apply {
            textSize = 10f
            setTextColor(0xFFFFFFFF.toInt())
        }
        overlayView = textView
        windowManager.addView(textView, params)
    }

    private fun updateSpeed() {
        val mode = prefs.networkSpeedDisplayMode
        if (mode == NetworkSpeedDisplayMode.DISABLED) {
            overlayView?.text = ""
            return
        }

        val now = System.currentTimeMillis()
        val elapsedSec = ((now - lastTimestamp).coerceAtLeast(1)) / 1000.0
        val rxNow = TrafficStats.getTotalRxBytes()
        val txNow = TrafficStats.getTotalTxBytes()

        val downloadKbps = ((rxNow - lastRxBytes) / elapsedSec / 1024).toInt()
        val uploadKbps = ((txNow - lastTxBytes) / elapsedSec / 1024).toInt()

        lastRxBytes = rxNow
        lastTxBytes = txNow
        lastTimestamp = now

        overlayView?.text = when (mode) {
            NetworkSpeedDisplayMode.DOWNLOAD_ONLY -> "↓${downloadKbps}KB/s"
            NetworkSpeedDisplayMode.UPLOAD_ONLY -> "↑${uploadKbps}KB/s"
            NetworkSpeedDisplayMode.BOTH -> "↓${downloadKbps} ↑${uploadKbps}KB/s"
            NetworkSpeedDisplayMode.DISABLED -> ""
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateRunnable)
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
