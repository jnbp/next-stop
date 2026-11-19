package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.SLOWER_WALK_FACTOR
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.jnbp.nextstop.data.Accessibility
import io.github.jnbp.nextstop.ui.theme.AppColors

/*
 * Small schematic sketches of the setup steps "Accessibility" and "Disruption check", in the
 * manner of the others (see SetupWizard): drawn, decorative, in the colours of the app.
 */

// The lift sign of the map (liftBitmap), kept per density.
private val liftImages = HashMap<Float, ImageBitmap>()

private fun DrawScope.liftImage(): ImageBitmap = liftImages.getOrPut(density) { liftBitmap(density, escalator = false).asImageBitmap() }

/** A flight of stairs: three steps rising to the right, standing on [bottom] around [centerX]. */
private fun DrawScope.stairs(centerX: Float, bottom: Float, color: Color) {
    val step = 5.dp.toPx()
    val path = Path().apply {
        moveTo(centerX - step * 1.5f, bottom)
        lineTo(centerX - step * 1.5f, bottom - step)
        lineTo(centerX - step * 0.5f, bottom - step)
        lineTo(centerX - step * 0.5f, bottom - step * 2)
        lineTo(centerX + step * 0.5f, bottom - step * 2)
        lineTo(centerX + step * 0.5f, bottom - step * 3)
        lineTo(centerX + step * 1.5f, bottom - step * 3)
    }
    drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
}

/**
 * The step-free choice: a ride with two stations, each reached by stairs or by a lift (the
 * lift sign of the map). Any: stairs at both; partly: a lift at one; fully: lifts at both.
 */
internal fun DrawScope.accessibilitySketch(level: Accessibility, reveal: Float) {
    val margin = 10.dp.toPx()
    val y = size.height * 0.4f
    val line = SketchLines[1]
    drawLine(line, Offset(margin, y), Offset(size.width - margin, y), strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
    val stations = listOf(size.width * 0.28f, size.width * 0.72f)
    val lifts = when (level) {
        Accessibility.None -> 0
        Accessibility.Partial -> 1
        Accessibility.Complete -> 2
    }
    val street = size.height - 8.dp.toPx()
    stations.forEachIndexed { index, x ->
        drawCircle(Color.White, radius = 5.dp.toPx(), center = Offset(x, y))
        drawCircle(line, radius = 5.dp.toPx(), center = Offset(x, y), style = Stroke(width = 2.dp.toPx()))
        if (index < lifts) {
            val image = liftImage()
            val alpha = reveal.coerceIn(0f, 1f)
            val lift = (1f - alpha) * 6.dp.toPx()
            drawImage(image, topLeft = Offset(x - image.width / 2f, street - image.height + lift), alpha = alpha)
        } else {
            stairs(x, street, AppColors.Ink.copy(alpha = 0.7f))
        }
    }
}

/**
 * "Slower on the move": the walk of a change as a dotted way to the next ride; with the
 * setting it counts [factor] times as long (1.3, or an own factor, #198) and the way grows.
 */
@Composable
internal fun SlowerSketch(on: Boolean, height: Dp = 40.dp, factor: Double = SLOWER_WALK_FACTOR) {
    val animate = rememberAnimationsOn()
    val stretch by animateFloatAsState(
        if (on) factor.toFloat() else 1f,
        if (animate) spring<Float>(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow) else snap<Float>(),
        label = "slowerWalk",
    )
    val first = SketchLines[0]
    val second = SketchLines[2]
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.Chip),
    ) {
        val margin = 14.dp.toPx()
        val y = size.height / 2
        val bar = 6.dp.toPx()
        val walk = size.width * 0.3f * stretch
        val firstEnd = margin + size.width * 0.18f
        val secondStart = (firstEnd + walk).coerceAtMost(size.width - margin - bar)
        drawLine(first, Offset(margin, y), Offset(firstEnd, y), strokeWidth = bar, cap = StrokeCap.Round)
        drawLine(
            color = AppColors.Muted,
            start = Offset(firstEnd + 6.dp.toPx(), y),
            end = Offset(secondStart - 6.dp.toPx(), y),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 5.dp.toPx())),
        )
        drawLine(second, Offset(secondStart, y), Offset(size.width - margin, y), strokeWidth = bar, cap = StrokeCap.Round)
    }
}

/** A small bell, as on a saved route (see the bell in the favourites). */
@Composable
internal fun SmallBell(color: Color = AppColors.Blue, edge: Dp = 20.dp) {
    Canvas(modifier = Modifier.size(edge)) {
        val w = size.width
        val bell = Path().apply {
            moveTo(w * 0.5f, w * 0.14f)
            cubicTo(w * 0.28f, w * 0.14f, w * 0.24f, w * 0.34f, w * 0.24f, w * 0.5f)
            lineTo(w * 0.24f, w * 0.66f)
            lineTo(w * 0.14f, w * 0.78f)
            lineTo(w * 0.86f, w * 0.78f)
            lineTo(w * 0.76f, w * 0.66f)
            lineTo(w * 0.76f, w * 0.5f)
            cubicTo(w * 0.76f, w * 0.34f, w * 0.72f, w * 0.14f, w * 0.5f, w * 0.14f)
            close()
        }
        drawPath(bell, color)
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(w * 0.4f, w * 0.76f),
            size = Size(w * 0.2f, w * 0.2f),
        )
    }
}
