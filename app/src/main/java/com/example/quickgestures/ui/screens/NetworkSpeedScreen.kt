package com.example.quickgestures.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.quickgestures.data.AppPreferences
import com.example.quickgestures.data.NetworkSpeedDisplayMode

@Composable
fun NetworkSpeedScreen(prefs: AppPreferences) {
    var mode by remember { mutableStateOf(prefs.networkSpeedDisplayMode) }

    val options = listOf(
        NetworkSpeedDisplayMode.DOWNLOAD_ONLY to "داونلود فقط",
        NetworkSpeedDisplayMode.UPLOAD_ONLY to "أبلود فقط",
        NetworkSpeedDisplayMode.BOTH to "الاثنين معاً",
        NetworkSpeedDisplayMode.DISABLED to "إلغاء"
    )

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("مراقب سرعة الإنترنت", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        options.forEach { (value, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                RadioButton(
                    selected = mode == value,
                    onClick = {
                        mode = value
                        prefs.networkSpeedDisplayMode = value
                    }
                )
                Text(label)
            }
        }
    }
}

private typealias Alignment = androidx.compose.ui.Alignment
