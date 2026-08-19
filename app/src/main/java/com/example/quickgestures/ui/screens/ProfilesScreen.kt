package com.example.quickgestures.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun ProfilesScreen(onExport: () -> String, onImport: (String) -> Unit) {
    val context = LocalContext.current
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(onExport().toByteArray()) }
            statusMessage = "تم تصدير البروفايل بنجاح"
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                onImport(it.readText())
            }
            statusMessage = "تم استيراد البروفايل بنجاح"
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("البروفايلات", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("تصدير كل إعدادات التطبيق (الكرة، الروتينات، القفل...) كملف JSON واحد لمشاركته.")
        Spacer(Modifier.height(16.dp))

        Button(onClick = { exportLauncher.launch("quick_touch_profile.json") }) {
            Text("تصدير الإعدادات")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
            Text("استيراد إعدادات")
        }

        statusMessage?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
