package io.github.jnbp.nextstop.wear.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer

// One run of the little train across its track, in milliseconds, as on the phone (Delight.kt there).
private const val TRAIN_RUN_MILLIS = 2_400

// Gentle at both ends, like a train pulling out and braking in: cubic-bezier(.45,.05,.55,.95).
private val TRAIN_EASING = CubicBezierEasing(0.45f, 0.05f, 0.55f, 0.95f)

// Dp per unit of the drawing (a car is 30 x 18 units): smaller than on the phone, for the round watch.
private const val TRAIN_SCALE = 0.45f

/**
 * The phone's loader on the watch: three plain rounded cars gliding over a dashed track from
 * the left to the right, eased in and out, again and again, fading in at the left edge and
 * out at the right one. No wheels, no couplings, nothing bobs. Standing in the middle when
 * the system has animations switched off.
 */
@Composable
fun TrainLoader(modifier: Modifier = Modifier, width: Dp = 64.dp, color: Color = MaterialTheme.colorScheme.primary) {
    // The system's setting and the app's own "Animations" (see rememberWatchAnimationsOn).
    val on = rememberWatchAnimationsOn()
    val transition = rememberInfiniteTransition(label = "train")
    val run by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(TRAIN_RUN_MILLIS, easing = TRAIN_EASING),
            RepeatMode.Restart,
            // The first run starts as if it had been going for a while: the train is already in
            // sight, so even a quick search shows it rather than an empty track.
            initialStartOffset = StartOffset((TRAIN_RUN_MILLIS * 0.4f).toInt(), StartOffsetType.FastForward),
        ),
        label = "trainRun",
    )
    Canvas(
        modifier = modifier
            .width(width)
            .height(18.dp)
            // A layer of its own, so the fade at both edges takes away only the train and the track.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        val unit = TRAIN_SCALE.dp.toPx()
        val trainWidth = TRAIN_LENGTH_UNITS * unit
        val left = if (on) -trainWidth + run * (size.width + trainWidth) else (size.width - trainWidth) / 2
        drawMinimalTrain(left, unit, color, TRACK_GREY)
    }
}

/** A loading state of a list on the watch: the little train and a short centred note below it. */
@Composable
fun LoadingNote(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TrainLoader()
        Note(text)
    }
}

// The train of the loader in units (the mockup's pixels): three cars of 30 x 18 with gaps of 3.
private const val CAR_WIDTH_UNITS = 30f
private const val CAR_HEIGHT_UNITS = 18f
private const val CAR_GAP_UNITS = 3f
private const val CAR_CORNER_UNITS = 6f
private const val NOSE_CORNER_UNITS = 12f
private const val TRAIN_LENGTH_UNITS = CAR_WIDTH_UNITS * 3 + CAR_GAP_UNITS * 2

// The track: 4 units tall, dashes of 10 with gaps of 6, its top this far below the cars.
private const val TRACK_HEIGHT_UNITS = 4f
private const val TRACK_DASH_UNITS = 10f
private const val TRACK_GAP_UNITS = 6f
private const val TRACK_CLEARANCE_UNITS = 3f

// Share of the width over which the train and track fade in at the left and out at the right.
private const val EDGE_FADE = 0.18f

// The grey of the dashed track.
private val TRACK_GREY = Color(0xFFB9C2CF)

/**
 * The loader's picture: the dashed track along the bottom, the train with its left edge at
 * [left] above it ([unit] pixels per unit), the last car's top front corner rounder; then both
 * ends faded out. The layer must be drawn offscreen for the fade (CompositingStrategy.Offscreen).
 */
private fun DrawScope.drawMinimalTrain(left: Float, unit: Float, train: Color, track: Color) {
    val trackHeight = TRACK_HEIGHT_UNITS * unit
    val trackMiddle = size.height - trackHeight / 2
    drawLine(
        color = track,
        start = Offset(trackHeight / 2, trackMiddle),
        end = Offset(size.width - trackHeight / 2, trackMiddle),
        strokeWidth = trackHeight,
        cap = StrokeCap.Round,
        // A round cap adds half the height at each end of a dash: drawn that much shorter, it is 10 long.
        pathEffect = PathEffect.dashPathEffect(
            floatArrayOf((TRACK_DASH_UNITS - TRACK_HEIGHT_UNITS) * unit, (TRACK_GAP_UNITS + TRACK_HEIGHT_UNITS) * unit),
        ),
    )
    val car = Size(CAR_WIDTH_UNITS * unit, CAR_HEIGHT_UNITS * unit)
    val top = size.height - trackHeight - TRACK_CLEARANCE_UNITS * unit - car.height
    val corner = CornerRadius(CAR_CORNER_UNITS * unit)
    for (index in 0 until 3) {
        val x = left + index * (car.width + CAR_GAP_UNITS * unit)
        if (index < 2) {
            drawRoundRect(train, topLeft = Offset(x, top), size = car, cornerRadius = corner)
        } else {
            val front = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(Offset(x, top), car),
                        topLeft = corner,
                        topRight = CornerRadius(NOSE_CORNER_UNITS * unit),
                        bottomRight = corner,
                        bottomLeft = corner,
                    ),
                )
            }
            drawPath(front, train)
        }
    }
    // Soft ends: transparent at the very edges, whole from EDGE_FADE of the width inwards.
    drawRect(
        brush = Brush.horizontalGradient(
            0f to Color.Transparent,
            EDGE_FADE to Color.Black,
            1f - EDGE_FADE to Color.Black,
            1f to Color.Transparent,
        ),
        blendMode = BlendMode.DstIn,
    )
}
