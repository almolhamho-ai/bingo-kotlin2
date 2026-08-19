package com.example.quickgestures.data

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * مُشغّل الروتين: الحدث الذي يبدأ فحص الشروط.
 */
@Serializable
sealed class RoutineTrigger {
    @Serializable data class Shake(val placeholder: Boolean = true) : RoutineTrigger()
    @Serializable data class EdgeGesture(val shape: String) : RoutineTrigger()
    @Serializable data class AppOpened(val packageName: String) : RoutineTrigger()

    fun shortLabel(): String = when (this) {
        is Shake -> "هزة"
        is EdgeGesture -> "إيماءة حافة"
        is AppOpened -> "فتح تطبيق"
    }
}

/**
 * شرط واحد. كل إجراء (ActionStep) يحمل قائمة شروطه الخاصة به،
 * ويسمح باختيار أكثر من شرط بنفس الوقت (تُقيَّم كلها بمنطق AND).
 */
@Serializable
sealed class RoutineCondition {
    @Serializable data class TimeRange(val startMinuteOfDay: Int, val endMinuteOfDay: Int) : RoutineCondition()
    @Serializable data class WifiState(val connected: Boolean) : RoutineCondition()
    @Serializable data class BatteryLevel(val op: CompareOp, val percent: Int) : RoutineCondition()
    @Serializable data class DayOfWeek(val days: Set<Int>) : RoutineCondition() // 1=الأحد..7=السبت

    fun shortLabel(): String = when (this) {
        is TimeRange -> "الوقت"
        is WifiState -> "الواي فاي"
        is BatteryLevel -> "نسبة البطارية"
        is DayOfWeek -> "أيام الأسبوع"
    }
}

@Serializable
enum class CompareOp { LESS_THAN, GREATER_THAN, EQUALS }

/**
 * خطوة إجراء داخل الروتين: إجراء واحد + شروطه الخاصة (اختيار متعدد، AND).
 * إذا كانت قائمة الشروط فاضية، الإجراء ينفذ دايمًا عند تحقق المُشغّل.
 */
@Serializable
data class ActionStep(
    val id: String = UUID.randomUUID().toString(),
    val actionId: String,
    val conditions: List<RoutineCondition> = emptyList(),
    val order: Int = 0
) {
    fun conditionsSatisfied(evaluator: (RoutineCondition) -> Boolean): Boolean =
        conditions.all(evaluator)
}

/**
 * الروتين الكامل: مُشغّل واحد + عدة خطوات إجراء، كل خطوة بشروطها المستقلة.
 * إذا ترك المستخدم الاسم فاضي، يتم توليد اسم تلقائي عند الحفظ.
 */
@Serializable
data class Routine(
    val id: String = UUID.randomUUID().toString(),
    val userGivenName: String? = null,
    val trigger: RoutineTrigger,
    val actionSteps: List<ActionStep>,
    val enabled: Boolean = true
) {
    /** الاسم الفعلي المعروض: اسم المستخدم إذا وُجد، وإلا اسم مولّد تلقائياً */
    fun resolvedName(actionLookup: (String) -> GestureAction?): String {
        if (!userGivenName.isNullOrBlank()) return userGivenName

        val triggerPart = trigger.shortLabel()
        val actionNames = actionSteps
            .sortedBy { it.order }
            .mapNotNull { actionLookup(it.actionId)?.displayLabel }

        return when {
            actionNames.isEmpty() -> "روتين ($triggerPart)"
            actionNames.size == 1 -> "$triggerPart ← ${actionNames.first()}"
            else -> "$triggerPart ← ${actionNames.take(2).joinToString(" + ")}" +
                    if (actionNames.size > 2) " (+${actionNames.size - 2})" else ""
        }
    }
}
