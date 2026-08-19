package com.example.quickgestures.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.quickgestures.data.*

/**
 * قائمة الروتينات + محرر روتين واحد.
 * كل خطوة إجراء (ActionStep) عندها شروطها المستقلة، وممكن تختار أكثر من شرط لنفس الإجراء،
 * وممكن يكون بروتين واحد أكثر من إجراء بشروط مختلفة كليًا.
 * إذا تُرك اسم الروتين فاضي، بينولّد اسم تلقائي من المُشغّل + الإجراءات (Routine.resolvedName).
 */
@Composable
fun RoutinesScreen(
    routines: List<Routine>,
    onSave: (Routine) -> Unit,
    onDelete: (Routine) -> Unit
) {
    var editing by remember { mutableStateOf<Routine?>(null) }

    if (editing != null) {
        RoutineEditor(
            routine = editing!!,
            onCancel = { editing = null },
            onSave = { saved -> onSave(saved); editing = null }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("الروتينات", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = {
                editing = Routine(trigger = RoutineTrigger.Shake(), actionSteps = emptyList())
            }) { Text("روتين جديد") }
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn {
            items(routines) { routine ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(routine.resolvedName(GestureActionCatalog::byId))
                            Text(
                                "${routine.actionSteps.size} إجراء",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(checked = routine.enabled, onCheckedChange = {
                            onSave(routine.copy(enabled = it))
                        })
                        TextButton(onClick = { editing = routine }) { Text("تعديل") }
                        TextButton(onClick = { onDelete(routine) }) { Text("حذف") }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutineEditor(
    routine: Routine,
    onCancel: () -> Unit,
    onSave: (Routine) -> Unit
) {
    var name by remember { mutableStateOf(routine.userGivenName ?: "") }
    var steps by remember { mutableStateOf(routine.actionSteps) }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("اسم الروتين (اختياري — إذا تركته فاضي رح يتسمى تلقائي)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))
        Text("الإجراءات", style = MaterialTheme.typography.titleMedium)

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(steps) { step ->
                ActionStepEditor(
                    step = step,
                    onChange = { updated ->
                        steps = steps.map { if (it.id == updated.id) updated else it }
                    },
                    onRemove = { steps = steps.filterNot { it.id == step.id } }
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = {
                steps = steps + ActionStep(
                    actionId = GestureActionCatalog.all.first().id,
                    order = steps.size
                )
            }) { Text("+ إضافة إجراء") }

            Row {
                TextButton(onClick = onCancel) { Text("إلغاء") }
                Button(onClick = {
                    onSave(
                        routine.copy(
                            userGivenName = name.ifBlank { null },
                            actionSteps = steps
                        )
                    )
                }) { Text("حفظ") }
            }
        }
    }
}

@Composable
private fun ActionStepEditor(
    step: ActionStep,
    onChange: (ActionStep) -> Unit,
    onRemove: () -> Unit
) {
    var actionMenuExpanded by remember { mutableStateOf(false) }
    val selectedAction = GestureActionCatalog.byId(step.actionId)

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    TextButton(onClick = { actionMenuExpanded = true }) {
                        Text(selectedAction?.displayLabel ?: "اختر إجراء")
                    }
                    DropdownMenu(expanded = actionMenuExpanded, onDismissRequest = { actionMenuExpanded = false }) {
                        GestureActionCatalog.all.forEach { action ->
                            DropdownMenuItem(
                                text = { Text(action.displayLabel) },
                                onClick = {
                                    onChange(step.copy(actionId = action.id))
                                    actionMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                TextButton(onClick = onRemove) { Text("حذف الإجراء") }
            }

            Text(
                "شروط هذا الإجراء (اختر أكثر من شرط إذا بدك — لازم تتحقق كلها):",
                style = MaterialTheme.typography.bodySmall
            )

            ConditionMultiSelect(
                selected = step.conditions,
                onChange = { onChange(step.copy(conditions = it)) }
            )
        }
    }
}

/** اختيار متعدد لأنواع الشروط المتاحة لهذا الإجراء تحديداً */
@Composable
private fun ConditionMultiSelect(
    selected: List<RoutineCondition>,
    onChange: (List<RoutineCondition>) -> Unit
) {
    val hasWifi = selected.any { it is RoutineCondition.WifiState }
    val hasBattery = selected.any { it is RoutineCondition.BatteryLevel }
    val hasTime = selected.any { it is RoutineCondition.TimeRange }

    Column {
        FilterChipRow(
            label = "الواي فاي متصل",
            checked = hasWifi,
            onCheckedChange = { checked ->
                onChange(
                    if (checked) selected + RoutineCondition.WifiState(true)
                    else selected.filterNot { it is RoutineCondition.WifiState }
                )
            }
        )
        FilterChipRow(
            label = "البطارية أقل من 30%",
            checked = hasBattery,
            onCheckedChange = { checked ->
                onChange(
                    if (checked) selected + RoutineCondition.BatteryLevel(CompareOp.LESS_THAN, 30)
                    else selected.filterNot { it is RoutineCondition.BatteryLevel }
                )
            }
        )
        FilterChipRow(
            label = "بين الساعة 8 مساءً و 7 صباحًا",
            checked = hasTime,
            onCheckedChange = { checked ->
                onChange(
                    if (checked) selected + RoutineCondition.TimeRange(20 * 60, 7 * 60)
                    else selected.filterNot { it is RoutineCondition.TimeRange }
                )
            }
        )
    }
}

@Composable
private fun FilterChipRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label)
    }
}
