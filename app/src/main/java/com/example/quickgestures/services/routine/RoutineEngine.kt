package com.example.quickgestures.services.routine

import com.example.quickgestures.data.ActionStep
import com.example.quickgestures.data.CompareOp
import com.example.quickgestures.data.GestureActionCatalog
import com.example.quickgestures.data.Routine
import com.example.quickgestures.data.RoutineCondition
import com.example.quickgestures.utils.ActionExecutor
import java.util.Calendar

/**
 * عند تحقق مُشغّل الروتين: يمر على كل ActionStep على حدة، ويفحص شروطه الخاصة فقط.
 * هيك ممكن يكون بروتين واحد أكثر من إجراء، كل إجراء إله شروط مختلفة تمامًا،
 * وممكن ينفذ إجراء أو أكثر بنفس الوقت إذا تحققت شروط كل واحد منهم.
 */
class RoutineEngine(
    private val actionExecutor: ActionExecutor,
    private val liveStateProvider: LiveStateProvider
) {

    fun onTriggerFired(routine: Routine) {
        if (!routine.enabled) return

        val stepsToRun = routine.actionSteps
            .sortedBy { it.order }
            .filter { step -> step.conditionsSatisfied(::evaluateCondition) }

        stepsToRun.forEach { step ->
            GestureActionCatalog.byId(step.actionId)?.let { action ->
                actionExecutor.execute(action)
            }
        }
    }

    private fun evaluateCondition(condition: RoutineCondition): Boolean = when (condition) {
        is RoutineCondition.TimeRange -> {
            val nowMinutes = currentMinuteOfDay()
            if (condition.startMinuteOfDay <= condition.endMinuteOfDay) {
                nowMinutes in condition.startMinuteOfDay..condition.endMinuteOfDay
            } else {
                // مدى يعبر منتصف الليل
                nowMinutes >= condition.startMinuteOfDay || nowMinutes <= condition.endMinuteOfDay
            }
        }
        is RoutineCondition.WifiState -> liveStateProvider.isWifiConnected() == condition.connected
        is RoutineCondition.BatteryLevel -> {
            val current = liveStateProvider.currentBatteryPercent()
            when (condition.op) {
                CompareOp.LESS_THAN -> current < condition.percent
                CompareOp.GREATER_THAN -> current > condition.percent
                CompareOp.EQUALS -> current == condition.percent
            }
        }
        is RoutineCondition.DayOfWeek -> {
            val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
            today in condition.days
        }
    }

    private fun currentMinuteOfDay(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }
}

/** مصدر قراءة الحالة اللحظية للجهاز (واي فاي/بطارية)، يُمرَّر كتبعية لسهولة الاختبار */
interface LiveStateProvider {
    fun isWifiConnected(): Boolean
    fun currentBatteryPercent(): Int
}
