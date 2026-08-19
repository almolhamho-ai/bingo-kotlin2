package com.example.quickgestures.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.quickgestures.data.AppPreferences
import com.example.quickgestures.data.GestureActionCatalog
import com.example.quickgestures.services.edge.EdgeGestureShape

@Composable
fun EdgeGestureScreen(prefs: AppPreferences) {
    var mapping by remember { mutableStateOf(prefs.edgeGestureActionMapping) }

    val shapes = listOf(
        EdgeGestureShape.STRAIGHT_LINE to "خط مستقيم",
        EdgeGestureShape.L_CORNER to "زاوية L",
        EdgeGestureShape.HALF_CIRCLE to "نص دائرة"
    )

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("إيماءات الحافة", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        shapes.forEach { (shape, label) ->
            var expanded by remember { mutableStateOf(false) }
            val selectedActionId = mapping[shape.name]
            val selectedLabel = GestureActionCatalog.byId(selectedActionId ?: "")?.displayLabel ?: "بدون ربط"

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Text(label)
                Box {
                    TextButton(onClick = { expanded = true }) { Text(selectedLabel) }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        GestureActionCatalog.all.forEach { action ->
                            DropdownMenuItem(
                                text = { Text(action.displayLabel) },
                                onClick = {
                                    mapping = mapping + (shape.name to action.id)
                                    prefs.edgeGestureActionMapping = mapping
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
