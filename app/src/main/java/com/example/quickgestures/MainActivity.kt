package com.example.quickgestures

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.quickgestures.data.AppPreferences
import com.example.quickgestures.ui.screens.*
import com.example.quickgestures.utils.AppLockManager

class MainActivity : ComponentActivity() {

    private lateinit var prefs: AppPreferences
    private lateinit var lockManager: AppLockManager

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* النتيجة تُقرأ لاحقاً عبر ContextCompat.checkSelfPermission عند الحاجة */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = AppPreferences(applicationContext)
        lockManager = AppLockManager(applicationContext)

        requestRuntimePermissions()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    QuickTouchNavHost(navController, prefs, lockManager)
                }
            }
        }
    }

    private fun requestRuntimePermissions() {
        val neededPermissions = mutableListOf<String>()

        neededPermissions += Manifest.permission.RECORD_AUDIO
        neededPermissions += Manifest.permission.CAMERA

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            neededPermissions += Manifest.permission.POST_NOTIFICATIONS
        }

        val toRequest = neededPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (toRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(toRequest.toTypedArray())
        }

        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                android.content.Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }
}

@Composable
private fun QuickTouchNavHost(
    navController: NavHostController,
    prefs: AppPreferences,
    lockManager: AppLockManager
) {
    NavHost(navController = navController, startDestination = "home") {
        composable("home") { HomeScreen(navController) }
        composable("quick_ball") { QuickBallConfigScreen(prefs) }
        composable("edge_gestures") { EdgeGestureScreen(prefs) }
        composable("routines") {
            var routines by remember { mutableStateOf(listOf<com.example.quickgestures.data.Routine>()) }
            RoutinesScreen(
                routines = routines,
                onSave = { saved ->
                    routines = if (routines.any { it.id == saved.id }) {
                        routines.map { if (it.id == saved.id) saved else it }
                    } else {
                        routines + saved
                    }
                },
                onDelete = { toDelete -> routines = routines.filterNot { it.id == toDelete.id } }
            )
        }
        composable("recording") { RecordingSettingsScreen() }
        composable("calibration") { AutoCalibrationScreen(prefs) }
        composable("profiles") { ProfilesScreen(onExport = { "{}" }, onImport = {}) }
        composable("app_lock") { AppLockScreen(lockManager, installedApps = emptyList()) }
        composable("network_speed") { NetworkSpeedScreen(prefs) }
    }
}

@Composable
private fun HomeScreen(navController: NavHostController) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Quick Touch", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        val items = listOf(
            "quick_ball" to "الكرة العائمة",
            "edge_gestures" to "إيماءات الحافة",
            "routines" to "الروتينات",
            "recording" to "التسجيل الصوتي",
            "calibration" to "المعايرة والحساسية",
            "profiles" to "البروفايلات",
            "app_lock" to "قفل التطبيقات",
            "network_speed" to "مراقب سرعة الإنترنت"
        )

        items.forEach { (route, label) ->
            OutlinedButton(
                onClick = { navController.navigate(route) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) { Text(label) }
        }
    }
}
