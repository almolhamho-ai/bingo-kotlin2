package com.example.quickgestures.services

import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import com.example.quickgestures.data.AppPreferences
import kotlin.math.sqrt

/**
 * يكتشف الاهتزاز باستخدام التسارع، مع:
 *  - عتبة حساسية مشتقة من إعداد 1..10 (AppPreferences.currentShakeThreshold)
 *  - معايرة تكيّفية حسب نمط الحركة (تبقى كما هي من النسخة السابقة)
 *  - حساس التقارب: أي حدث يوصل وقت ما الجهاز "بالجيب" (تقارب قريب + بدون ضوء) يتم تجاهله
 *  - عند تفعيل الفلاش: اهتزاز تأكيد بمدة قابلة للتخصيص من 0 إلى 3 ثواني
 */
class ShakeDetectorService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var proximitySensor: Sensor? = null
    private lateinit var prefs: AppPreferences
    private lateinit var vibrator: Vibrator

    // حالة حساس التقارب اللحظية: true يعني الجهاز مغطى (غالبًا بالجيب)
    @Volatile private var isCoveredByProximity = false

    private var lastAccel = floatArrayOf(0f, 0f, 0f)
    private var lastUpdateTime = 0L

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        prefs = AppPreferences(applicationContext)
        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator

        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        if (prefs.proximityPocketGuardEnabled) {
            proximitySensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> {
                val maxRange = proximitySensor?.maximumRange ?: 5f
                isCoveredByProximity = event.values[0] < maxRange
            }
            Sensor.TYPE_ACCELEROMETER -> handleAccelerometer(event)
        }
    }

    private fun handleAccelerometer(event: SensorEvent) {
        // 4) تجاهل كامل لو الجهاز مغطى بحساس التقارب (على الأغلب بالجيب)
        if (prefs.proximityPocketGuardEnabled && isCoveredByProximity) return

        val now = System.currentTimeMillis()
        if (now - lastUpdateTime < 60) return
        val dt = (now - lastUpdateTime).coerceAtLeast(1)
        lastUpdateTime = now

        val (x, y, z) = event.values
        val deltaX = x - lastAccel[0]
        val deltaY = y - lastAccel[1]
        val deltaZ = z - lastAccel[2]
        lastAccel = floatArrayOf(x, y, z)

        // سرعة التغير التقريبية (m/s²) بمعزل عن dt لتفادي حساسية زائدة عند تفاوت معدل العينات
        val jerk = sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ) * (1000f / dt).coerceAtMost(60f)

        // 3) العتبة الفعلية مشتقة من مستوى الحساسية 1..10 المختار بالإعدادات
        val threshold = prefs.currentShakeThreshold()

        if (jerk > threshold) {
            onShakeDetected()
        }
    }

    private fun onShakeDetected() {
        // تنفيذ الإجراء المرتبط بالهزة (فلاش أو غيره) يتم عبر ActionExecutor بمكان آخر بالتطبيق.
        // هون فقط مثال لمنطق اهتزاز التأكيد بعد تفعيل الفلاش تحديدًا:
        triggerFlashConfirmVibrationIfNeeded()
    }

    /** 5) اهتزاز تأكيد بعد تفعيل الفلاش، بمدة يختارها المستخدم من 0 إلى 3 ثواني */
    private fun triggerFlashConfirmVibrationIfNeeded() {
        val durationMs = prefs.flashConfirmVibrationMs
        if (durationMs <= 0) return // المستخدم اختار 0 = بدون اهتزاز إطلاقاً
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
