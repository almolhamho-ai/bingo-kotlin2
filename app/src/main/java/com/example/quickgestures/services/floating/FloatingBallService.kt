package com.example.quickgestures.services.floating

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import com.example.quickgestures.data.AppPreferences
import com.example.quickgestures.data.GestureActionCatalog
import com.example.quickgestures.ui.components.QuickBallOverlayView
import com.example.quickgestures.utils.ActionExecutor

/**
 * تشغل الكرة العائمة على مستوى النظام (خيار "تعمل برا التطبيق كمان").
 * تستخدم نفس QuickBallOverlayView (Compose) المستخدم داخل التطبيق لضمان تطابق السلوك تماماً.
 */
class FloatingBallService : Service() {

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private lateinit var prefs: AppPreferences
    private lateinit var actionExecutor: ActionExecutor

    private var ballX = 0
    private var ballY = 400

    override fun onCreate() {
        super.onCreate()
        prefs = AppPreferences(applicationContext)
        actionExecutor = ActionExecutor(applicationContext)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        addOverlay()
    }

    private fun addOverlay() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = ballX
            y = ballY
        }

        val view = ComposeView(this).apply {
            setContent {
                QuickBallOverlayView(
                    config = prefs.quickBallRadialConfig,
                    actionsCatalog = GestureActionCatalog::byId,
                    isEdgeOnLeft = ballX < 100,
                    onActionTapped = { action -> actionExecutor.execute(action) },
                    onLongPressMove = { dx, dy ->
                        ballX += dx.toInt()
                        ballY += dy.toInt()
                        params.x = ballX
                        params.y = ballY
                        windowManager.updateViewLayout(this, params)
                    }
                )
            }
        }

        composeView = view
        windowManager.addView(view, params)
    }

    override fun onDestroy() {
        composeView?.let { runCatching { windowManager.removeView(it) } }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
