package com.example.quickgestures.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * المخزن المركزي لكل إعدادات التطبيق.
 * تم تحديثه ليشمل:
 *  - حساسية الاهتزاز كرقم طبيعي من 1 إلى 10 (1 = أصعب تفعيل، 10 = أسهل تفعيل)
 *  - زمن اهتزاز التأكيد بعد تفعيل الفلاش (0 إلى 3 ثواني)
 *  - إعدادات الكرة العائمة كقائمة دائرية (Radial) بدل القائمة الخطية القديمة
 */
class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("quick_touch_prefs", Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ---------------------------------------------------------------------
    // 3) حساسية الاهتزاز: 1 (صعب) .. 10 (سهل) — رقم طبيعي فقط، لا كسور
    // ---------------------------------------------------------------------
    companion object {
        const val SENSITIVITY_MIN = 1
        const val SENSITIVITY_MAX = 10
        const val SENSITIVITY_DEFAULT = 5

        // حدود التسارع الفعلية (m/s²) المرتبطة بكل مستوى حساسية.
        // القيمة الأعلى = عتبة أعلى = صعوبة أكبر بالتفعيل (لمستوى 1).
        // القيمة الأدنى = عتبة أدنى = سهولة أكبر بالتفعيل (لمستوى 10).
        private const val THRESHOLD_HARDEST = 22f   // عند حساسية = 1
        private const val THRESHOLD_EASIEST = 8f    // عند حساسية = 10

        fun sensitivityToThreshold(level: Int): Float {
            val clamped = level.coerceIn(SENSITIVITY_MIN, SENSITIVITY_MAX)
            // تدرج خطي معكوس: كل ما زاد الرقم قلّت العتبة (أسهل)
            val fraction = (clamped - SENSITIVITY_MIN).toFloat() / (SENSITIVITY_MAX - SENSITIVITY_MIN)
            return THRESHOLD_HARDEST - (THRESHOLD_HARDEST - THRESHOLD_EASIEST) * fraction
        }
    }

    var shakeSensitivityLevel: Int
        get() = prefs.getInt("shake_sensitivity_level", SENSITIVITY_DEFAULT)
            .coerceIn(SENSITIVITY_MIN, SENSITIVITY_MAX)
        set(value) = prefs.edit()
            .putInt("shake_sensitivity_level", value.coerceIn(SENSITIVITY_MIN, SENSITIVITY_MAX))
            .apply()

    /** العتبة الفعلية الجاهزة للاستخدام مباشرة داخل ShakeDetectorService */
    fun currentShakeThreshold(): Float = sensitivityToThreshold(shakeSensitivityLevel)

    // ---------------------------------------------------------------------
    // 5) زمن اهتزاز التأكيد بعد تفعيل الفلاش: 0..3000 ملي ثانية (خطوة 250ms)
    // ---------------------------------------------------------------------
    var flashConfirmVibrationMs: Int
        get() = prefs.getInt("flash_confirm_vibration_ms", 300).coerceIn(0, 3000)
        set(value) = prefs.edit()
            .putInt("flash_confirm_vibration_ms", value.coerceIn(0, 3000))
            .apply()

    // ---------------------------------------------------------------------
    // 4) استخدام حساس التقارب لاكتشاف وضعية "بالجيب"
    // ---------------------------------------------------------------------
    var proximityPocketGuardEnabled: Boolean
        get() = prefs.getBoolean("proximity_pocket_guard", true)
        set(value) = prefs.edit().putBoolean("proximity_pocket_guard", value).apply()

    // ---------------------------------------------------------------------
    // إيماءات الحافة: ربط كل شكل (STRAIGHT_LINE / L_CORNER / HALF_CIRCLE) بإجراء
    // ---------------------------------------------------------------------
    var edgeGestureActionMapping: Map<String, String>
        get() {
            val raw = prefs.getString("edge_gesture_mapping", null) ?: return emptyMap()
            return try { json.decodeFromString(raw) } catch (e: Exception) { emptyMap() }
        }
        set(value) = prefs.edit()
            .putString("edge_gesture_mapping", json.encodeToString(value))
            .apply()

    // ---------------------------------------------------------------------
    // مراقب سرعة الإنترنت
    // ---------------------------------------------------------------------
    var networkSpeedDisplayMode: NetworkSpeedDisplayMode
        get() = try {
            NetworkSpeedDisplayMode.valueOf(
                prefs.getString("network_speed_mode", NetworkSpeedDisplayMode.DISABLED.name)!!
            )
        } catch (e: Exception) {
            NetworkSpeedDisplayMode.DISABLED
        }
        set(value) = prefs.edit().putString("network_speed_mode", value.name).apply()

    // ---------------------------------------------------------------------
    // 2) إعدادات الكرة الدائرية (Radial Quick Ball)
    // ---------------------------------------------------------------------
    var quickBallRadialConfig: QuickBallRadialConfig
        get() {
            val raw = prefs.getString("quick_ball_radial_config", null)
                ?: return QuickBallRadialConfig.default()
            return try {
                json.decodeFromString(raw)
            } catch (e: Exception) {
                QuickBallRadialConfig.default()
            }
        }
        set(value) = prefs.edit()
            .putString("quick_ball_radial_config", json.encodeToString(value))
            .apply()
}

/**
 * إعدادات القائمة الدائرية للكرة العائمة.
 * itemsPerRing: كم اختصار يظهر بحلقة واحدة حول المركز قبل ما يحتاج تدوير.
 * rotationOffsetDegrees: زاوية التدوير الحالية المختارة من المستخدم.
 */
@Serializable
data class QuickBallRadialConfig(
    val selectedActionIds: List<String>,
    val itemsPerRing: Int = 6,
    val rotationOffsetDegrees: Float = 0f,
    val collapsedSizeDp: Int = 28,      // حجم نص الدائرة الصغير على الحافة قبل الفتح
    val centerBubbleSizeDp: Int = 56,   // حجم الدائرة المركزية
    val satelliteBubbleSizeDp: Int = 56 // نفس مقاس الدائرة المركزية (طلب المستخدم: بنفس المقاس)
) {
    companion object {
        fun default() = QuickBallRadialConfig(
            selectedActionIds = emptyList(),
            itemsPerRing = 6,
            rotationOffsetDegrees = 0f
        )
    }
}
