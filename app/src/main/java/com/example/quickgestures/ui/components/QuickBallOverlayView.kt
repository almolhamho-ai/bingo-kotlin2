package com.example.quickgestures.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.quickgestures.data.GestureAction
import com.example.quickgestures.data.QuickBallRadialConfig
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

/**
 * الحالة الأساسية (مغلقة): نص دائرة صغير وخفيف، نصفه برا حدود الشاشة عند الحافة.
 * عند الضغط: ينبثق (Scale + Fade) لقائمة دائرية — دائرة مركزية بنفس مكانها،
 * وحولها فقاعات الاختصارات موزعة على شكل دائرة كاملة بنفس المقاس، بزاوية متساوية بينهم.
 *
 * سحبة أفقية على الدائرة المركزية أثناء الفتح = تدوير كل الحلقة (لعرض اختصارات إضافية
 * إذا كان عدد الاختصارات المختارة أكبر من itemsPerRing).
 */
@Composable
fun QuickBallOverlayView(
    config: QuickBallRadialConfig,
    actionsCatalog: (String) -> GestureAction?,
    isEdgeOnLeft: Boolean,
    onActionTapped: (GestureAction) -> Unit,
    onLongPressMove: (dx: Float, dy: Float) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var rotation by remember { mutableFloatStateOf(config.rotationOffsetDegrees) }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    // أنيميشن سلس للانبثاق: منحنى overshoot خفيف يعطي إحساس احترافي "spring"
    val expandProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "quickBallExpand"
    )

    val density = LocalDensity.current
    val centerSizeDp = config.centerBubbleSizeDp.dp
    val satelliteSizeDp = config.satelliteBubbleSizeDp.dp
    val collapsedSizeDp = config.collapsedSizeDp.dp

    // نصف قطر توزيع الحلقة يتحسب من مقاس الفقاعات حتى ما تتلامس
    val ringRadiusDp = (centerSizeDp.value * 1.9f).dp

    val visibleActions = remember(config.selectedActionIds, config.itemsPerRing, rotation) {
        config.selectedActionIds.mapNotNull(actionsCatalog).take(
            maxOf(config.itemsPerRing, config.selectedActionIds.size)
        )
    }

    Box(
        modifier = Modifier
            .size(if (expanded) ringRadiusDp * 2 + satelliteSizeDp else collapsedSizeDp),
        contentAlignment = Alignment.Center
    ) {
        // فقاعات الاختصارات حول المركز — تظهر فقط بالحالة المفتوحة، بأنيميشن تدرّجي
        if (expandProgress > 0.01f) {
            val angleStep = 360f / maxOf(visibleActions.size, 1)
            visibleActions.forEachIndexed { index, action ->
                val angleDeg = rotation + angleStep * index - 90f
                val angleRad = angleDeg * PI.toFloat() / 180f
                val radiusPx = with(density) { ringRadiusDp.toPx() } * expandProgress
                val offsetX = cos(angleRad) * radiusPx
                val offsetY = sin(angleRad) * radiusPx

                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = offsetX
                            translationY = offsetY
                            scaleX = expandProgress
                            scaleY = expandProgress
                            alpha = expandProgress
                        }
                        .size(satelliteSizeDp)
                        .clip(CircleShape)
                        .pointerInput(action.id) {
                            detectTapGestures(onTap = {
                                onActionTapped(action)
                                expanded = false
                            })
                        },
                    contentAlignment = Alignment.Center
                ) {
                    ActionBubbleContent(action)
                }
            }
        }

        // الدائرة المركزية — بنفس مكانها بالحالتين، هي يلي بتفتح/تسكر وبتستقبل سحبة التدوير
        Box(
            modifier = Modifier
                .size(if (expanded) centerSizeDp else collapsedSizeDp)
                .graphicsLayer {
                    // بالحالة المغلقة نص الدائرة برا الشاشة (نصف قطرها خارج الحافة)
                    translationX = if (!expanded) {
                        if (isEdgeOnLeft) -collapsedSizeDp.toPx() / 2f else collapsedSizeDp.toPx() / 2f
                    } else 0f
                }
                .clip(CircleShape)
                .pointerInput(expanded) {
                    detectTapGestures(onTap = {
                        if (!expanded && visibleActions.isNotEmpty()) {
                            expanded = true
                        } else if (expanded) {
                            // نقرة على المركز بالحالة المفتوحة تنفذ آخر إجراء ظاهر بالمنتصف (اختياري)
                            expanded = false
                        }
                    })
                }
                .pointerInput(expanded) {
                    detectDragGestures(
                        onDragStart = { dragAccumulator = 0f },
                        onDragEnd = {
                            if (expanded) rotation = normalizeAngle(rotation)
                        }
                    ) { change, dragAmount: Offset ->
                        change.consume()
                        if (expanded) {
                            // سحب أفقي = تدوير الحلقة كاملة
                            dragAccumulator += dragAmount.x
                            rotation += dragAmount.x * 0.6f
                        } else {
                            // سحبة طويلة بالحالة المغلقة = تحريك موقع الكرة (كما بالنسخة القديمة)
                            onLongPressMove(dragAmount.x, dragAmount.y)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        )
    }
}

@Composable
private fun ActionBubbleContent(action: GestureAction) {
    Box(
        modifier = Modifier
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // إذا لم يضع المستخدم أيقونة مخصصة، الحرف الأول من الاسم (يبقى كما هو بانتظار الأيقونات المخصصة)
        Text(text = action.displayLabel.take(1))
    }
}

private fun normalizeAngle(angle: Float): Float {
    var a = angle % 360f
    if (a < 0f) a += 360f
    return a
}
