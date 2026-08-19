package com.example.quickgestures.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.quickgestures.utils.AppLockManager

@Composable
fun InternalPinUnlockScreen(
    lockManager: AppLockManager,
    onUnlocked: () -> Unit,
    onCancelled: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("أدخل رمز PIN", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it; error = false },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            isError = error,
            modifier = Modifier.fillMaxWidth()
        )

        if (error) {
            Text("رمز خاطئ", color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(16.dp))
        Row {
            TextButton(onClick = onCancelled) { Text("إلغاء") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                if (lockManager.verifyInternalPin(pin)) onUnlocked() else error = true
            }) { Text("فتح") }
        }
    }
}
