package com.example.quickgestures.services.edge

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.example.quickgestures.data.AppPreferences
import com.example.quickgestures.data.GestureActionCatalog
import com.example.quickgestures.utils.ActionExecutor
import kotlin.math.abs

enum class EdgeGestureShape { STRAIGHT_LINE, L_CORNER, HALF_CIRCLE }

/** يضيف شريط لمس شفاف رفيع عند حافتي الشاشة، ويصنّف شكل السحبة تلقائياً. */
class EdgeGestureService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: AppPreferences
    private lateinit var actionExecutor: ActionExecutor
    private val pathPoints = mutableListOf<Pair<Float, Float>>()

    override fun onCreate() {
        super.onCreate()
        prefs = AppPreferences(applicationContext)
        actionExecutor = ActionExecutor(applicationContext)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        addEdgeStrip(Gravity.START)
        addEdgeStrip(Gravity.END)
    }

    private fun addEdgeStrip(gravity: Int) {
        val params = WindowManager.LayoutParams(
            24,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { this.gravity = gravity or Gravity.CENTER_VERTICAL }

        val strip = object : View(this) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> pathPoints.clear().also { pathPoints.add(event.rawX to event.rawY) }
                    MotionEvent.ACTION_MOVE -> pathPoints.add(event.rawX to event.rawY)
                    MotionEvent.ACTION_UP -> {
                        pathPoints.add(event.rawX to event.rawY)
                        val shape = classifyShape(pathPoints)
                        onGestureClassified(shape)
                    }
                }
                return true
            }
        }
        windowManager.addView(strip, params)
    }

    private fun classifyShape(points: List<Pair<Float, Float>>): EdgeGestureShape {
        if (points.size < 3) return EdgeGestureShape.STRAIGHT_LINE

        val start = points.first()
        val end = points.last()
        val totalDx = abs(end.first - start.first)
        val totalDy = abs(end.second - start.second)

        // فحص وجود نقطة انعطاف حادة بمنتصف المسار = زاوية L
        val mid = points[points.size / 2]
        val turnDx = abs(mid.first - start.first)
        val turnDy = abs(mid.second - start.second)
        val hasSharpTurn = (turnDx > 40 && totalDy > 40) || (turnDy > 40 && totalDx > 40)

        // فحص عودة نقطة النهاية قريبة من نقطة البداية = نص دائرة
        val closesLoop = abs(end.first - start.first) < 60 && abs(end.second - start.second) < 60 && points.size > 8

        return when {
            closesLoop -> EdgeGestureShape.HALF_CIRCLE
            hasSharpTurn -> EdgeGestureShape.L_CORNER
            else -> EdgeGestureShape.STRAIGHT_LINE
        }
    }

    private fun onGestureClassified(shape: EdgeGestureShape) {
        val actionId = prefs.edgeGestureActionMapping[shape.name] ?: return
        GestureActionCatalog.byId(actionId)?.let { actionExecutor.execute(it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
