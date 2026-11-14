package io.github.jnbp.nextstop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.ui.theme.AppColors

/*
 * The quiet mark of the app on the plain ground of the start page when its map is switched
 * off: the shape of the launcher icon (an arrow into the stop circle) in one soft tone just
 * apart from the ground, with "Next Stop" below it. Behind everything, never focused.
 */

/** The tone of the mark: a little darker than the plain ground in light, a little lighter in dark. */
internal fun stopMarkTone(): Color =
    if (AppColors.palette.dark) lerp(AppColors.PlainGround, Color.White, 0.08f) else lerp(AppColors.PlainGround, Color.Black, 0.09f)

/**
 * The arrow into the stop circle of the launcher icon (ic_launcher_foreground), [width] wide
 * and half as tall, its middle at [center], in one [color].
 */
internal fun DrawScope.drawStopMark(center: Offset, width: Float, color: Color) {
    // The icon in its own units: the arrow from x 27 to 53, the circle around (74, 54) with
    // radius 10; with the strokes it spans x 21 to 89.
    val scale = width / 68f
    fun point(x: Float, y: Float) = Offset(center.x + (x - 55f) * scale, center.y + (y - 54f) * scale)
    val stroke = 8f * scale
    drawLine(color, point(27f, 54f), point(48f, 54f), strokeWidth = stroke, cap = StrokeCap.Round)
    val head = Path().apply {
        val a = point(41f, 42f)
        val b = point(53f, 54f)
        val c = point(41f, 66f)
        moveTo(a.x, a.y)
        lineTo(b.x, b.y)
        lineTo(c.x, c.y)
    }
    drawPath(head, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawCircle(color, radius = 10f * scale, center = point(74f, 54f), style = Stroke(width = stroke))
}

/**
 * The mark centred in the free part of the screen: below [top] (pixels: the status bar and the
 * chips) and above the sheet, whose top edge [coverTop] gives (pixels; NaN while unknown). It
 * steps aside when the sheet leaves too little room. Fades in slowly, unless animations are off.
 */
@Composable
fun StartLogoMark(coverTop: () -> Float, top: Float, modifier: Modifier = Modifier) {
    val animate = rememberAnimationsOn()
    val fade = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(animate) {
        if (animate) fade.animateTo(1f, tween(1_200)) else fade.snapTo(1f)
    }
    val tone = stopMarkTone()
    Box(modifier = modifier.fillMaxSize().clearAndSetSemantics { }) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .graphicsLayer { alpha = fade.value }
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        val height = constraints.maxHeight.toFloat()
                        val cover = coverTop().let { if (it.isNaN() || it <= 0f || it > height) height else it }
                        val free = cover - top
                        if (free < placeable.height + 32.dp.toPx()) {
                            placeable.place(-10_000, -10_000)
                        } else {
                            placeable.place(
                                (constraints.maxWidth - placeable.width) / 2,
                                (top + (free - placeable.height) / 2).roundToInt(),
                            )
                        }
                    }
                },
        ) {
            Canvas(modifier = Modifier.size(width = 112.dp, height = 56.dp)) {
                drawStopMark(center, size.width, tone)
            }
            Text(
                text = stringResource(R.string.app_name),
                color = tone,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
            )
        }
    }
}
