package com.example.quickgestures.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.quickgestures.utils.AppLockManager

data class InstalledAppInfo(val packageName: String, val label: String)

@Composable
fun AppLockScreen(lockManager: AppLockManager, installedApps: List<InstalledAppInfo>) {
    var lockedPackages by remember { mutableStateOf(lockManager.lockedPackages) }
    var lockMethod by remember { mutableStateOf(lockManager.lockMethod) }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("قفل التطبيقات", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("التسجيلات الصوتية مقفولة دائماً بغض النظر عن هالإعداد.", style = MaterialTheme.typography.bodySmall)

        Spacer(Modifier.height(16.dp))
        Text("طريقة القفل", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = lockMethod == AppLockManager.LockMethod.DEVICE_BIOMETRIC,
                onClick = {
                    lockMethod = AppLockManager.LockMethod.DEVICE_BIOMETRIC
                    lockManager.lockMethod = lockMethod
                }
            )
            Text("قفل الجهاز (بصمة/نمط/رقم)")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = lockMethod == AppLockManager.LockMethod.INTERNAL_PIN,
                onClick = {
                    lockMethod = AppLockManager.LockMethod.INTERNAL_PIN
                    lockManager.lockMethod = lockMethod
                }
            )
            Text("رمز PIN داخلي مخصص")
        }

        Spacer(Modifier.height(16.dp))
        Text("التطبيقات المقفولة", style = MaterialTheme.typography.titleMedium)

        LazyColumn {
            items(installedApps) { app ->
                val checked = app.packageName in lockedPackages
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { isChecked ->
                            lockedPackages = if (isChecked) {
                                lockedPackages + app.packageName
                            } else {
                                lockedPackages - app.packageName
                            }
                            lockManager.lockedPackages = lockedPackages
                        }
                    )
                    Text(app.label)
                }
            }
        }
    }
}
