package com.example.quickgestures.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.quickgestures.data.AppPreferences
import com.example.quickgestures.data.GestureActionCatalog
import com.example.quickgestures.data.QuickBallRadialConfig

@Composable
fun QuickBallConfigScreen(prefs: AppPreferences) {
    var config by remember { mutableStateOf(prefs.quickBallRadialConfig) }

    fun update(newConfig: QuickBallRadialConfig) {
        config = newConfig
        prefs.quickBallRadialConfig = newConfig
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("اختر الاختصارات اللي بدك تظهر بالكرة الدائرية", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "بتترتب كفقاعات صغيرة حول دائرة مركزية بنفس المقاس، وإذا زاد العدد عن ${config.itemsPerRing} بتقدر تدوّرها بسحبة على المركز.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(GestureActionCatalog.all) { action ->
                val selected = action.id in config.selectedActionIds
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { checked ->
                            val newIds = if (checked) {
                                config.selectedActionIds + action.id
                            } else {
                                config.selectedActionIds - action.id
                            }
                            update(config.copy(selectedActionIds = newIds))
                        }
                    )
                    Text(action.displayLabel)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("عدد الاختصارات بالحلقة الواحدة قبل التدوير: ${config.itemsPerRing}")
        Slider(
            value = config.itemsPerRing.toFloat(),
            onValueChange = { update(config.copy(itemsPerRing = it.toInt())) },
            valueRange = 3f..10f,
            steps = 6
        )
    }
}

// استيراد Alignment هنا لتفادي تضارب أسماء مع Compose الأساسي
private typealias Alignment = androidx.compose.ui.Alignment
