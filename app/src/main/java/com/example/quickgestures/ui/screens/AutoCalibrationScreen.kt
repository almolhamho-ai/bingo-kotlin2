package com.example.quickgestures.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.quickgestures.data.AppPreferences

@Composable
fun AutoCalibrationScreen(prefs: AppPreferences) {
    var sensitivity by remember { mutableIntStateOf(prefs.shakeSensitivityLevel) }
    var proximityGuard by remember { mutableStateOf(prefs.proximityPocketGuardEnabled) }
    var flashVibrationMs by remember { mutableIntStateOf(prefs.flashConfirmVibrationMs) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 3) حساسية الاهتزاز: 1 (صعب) → 10 (سهل)، أعداد طبيعية فقط
        Column {
            Text("حساسية الاهتزاز: $sensitivity / 10", style = MaterialTheme.typography.titleMedium)
            Text(
                "كل ما اقتربت من 1 صار تفعيل الهزة أصعب، وكل ما اقتربت من 10 صار أسهل.",
                style = MaterialTheme.typography.bodySmall
            )
            Slider(
                value = sensitivity.toFloat(),
                onValueChange = { sensitivity = it.toInt() },
                onValueChangeFinished = { prefs.shakeSensitivityLevel = sensitivity },
                valueRange = AppPreferences.SENSITIVITY_MIN.toFloat()..AppPreferences.SENSITIVITY_MAX.toFloat(),
                steps = (AppPreferences.SENSITIVITY_MAX - AppPreferences.SENSITIVITY_MIN) - 1 // يقفل القيم على أعداد صحيحة
            )
        }

        // 4) حماية الجيب عبر حساس التقارب
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.weight(1f)) {
                Text("تجاهل الهزة بالجيب", style = MaterialTheme.typography.titleMedium)
                Text(
                    "يستخدم حساس التقارب لمعرفة إذا الجهاز مغطى (بالجيب) ويمنع التفعيل الخاطئ.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = proximityGuard,
                onCheckedChange = {
                    proximityGuard = it
                    prefs.proximityPocketGuardEnabled = it
                }
            )
        }

        // 5) زمن اهتزاز التأكيد بعد تفعيل الفلاش: 0..3 ثواني
        Column {
            val seconds = flashVibrationMs / 1000f
            Text("زمن اهتزاز التأكيد بعد الفلاش: ${"%.1f".format(seconds)} ثانية", style = MaterialTheme.typography.titleMedium)
            Text(
                if (flashVibrationMs == 0) "بدون اهتزاز" else "يهتز الجهاز لهاد المدة كتأكيد بعد تفعيل الفلاش.",
                style = MaterialTheme.typography.bodySmall
            )
            Slider(
                value = flashVibrationMs.toFloat(),
                onValueChange = { flashVibrationMs = it.toInt() },
                onValueChangeFinished = { prefs.flashConfirmVibrationMs = flashVibrationMs },
                valueRange = 0f..3000f,
                steps = 11 // خطوات كل 250ms تقريباً
            )
        }
    }
}
