package com.example.quickgestures.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RecordingSettingsScreen() {
    var ballTrigger by remember { mutableStateOf(true) }
    var shakeTrigger by remember { mutableStateOf(false) }
    var edgeTrigger by remember { mutableStateOf(false) }
    var notificationTrigger by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("التسجيل الصوتي الشفاف", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "إشعار دائم لا يمكن إخفاؤه يظهر طول فترة التسجيل. جودة عالية: AAC 128kbps / 44.1kHz. " +
                "الملفات محفوظة بمجلد محمي دائماً بقفل التطبيق.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(16.dp))
        Text("طرق التفعيل", style = MaterialTheme.typography.titleMedium)

        ToggleRow("من الكرة العائمة", ballTrigger) { ballTrigger = it }
        ToggleRow("بالهزة", shakeTrigger) { shakeTrigger = it }
        ToggleRow("بإيماءة الحافة", edgeTrigger) { edgeTrigger = it }
        ToggleRow("زر بالإشعار", notificationTrigger) { notificationTrigger = it }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
