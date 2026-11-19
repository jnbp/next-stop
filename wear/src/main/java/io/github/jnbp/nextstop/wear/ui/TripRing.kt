package io.github.jnbp.nextstop.wear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import kotlin.math.cos
import kotlin.math.sin

/*
 * The progress of a ride to the stop to get off, drawn once and used twice: along the round
 * edge of the screen on the ride page of a trip (a straight bar on a square watch) and as a
 * small ring on the trip card of the start page. The way runs clockwise over RING_SWEEP
 * degrees from RING_START, leaving the top free for the time of the watch; each stop is a dot
 * on it, the stop to get off a ring at its end, the train a white dot at the head of the
 * coloured part. In ambient mode only thin outlines in white are drawn, nothing filled.
 */

/** Where the way of the ring starts (degrees, clockwise from three o'clock, as drawArc counts): just right of the time at the top. */
const val RING_START = -60f

/** How far it runs: around to just left of the time. */
const val RING_SWEEP = 300f

// Up to this many stops are dotted; a longer ride shows only its end. The small ring of the card has room for fewer.
internal const val RING_MAX_STOP_TICKS = 24
private const val MINI_RING_MAX_TICKS = 8

/**
 * Draws the way of a ride with [stops] stops (boarding and getting off included; fewer than 3:
 * no dots between) of which [fraction] (0..1) lies behind, in [color] on [track]; the dots of
 * the stops are [ground] on the part behind and [tick] ahead. [round] draws it as an arc of the
 * circle that fits inside [inset], else as a line [inset] above the foot of the area.
 */
fun DrawScope.drawRideWay(
    fraction: Float,
    stops: Int,
    color: Color,
    track: Color,
    ground: Color,
    tick: Color,
    stroke: Float,
    inset: Float,
    round: Boolean,
    ambient: Boolean,
) {
    val done = fraction.coerceIn(0f, 1f)
    val width = if (ambient) stroke / 3f else stroke
    val ink = if (ambient) Color.White else color
    val topLeft = Offset(inset, inset)
    val arcSize = Size(size.width - 2 * inset, size.height - 2 * inset)
    val lineY = size.height - inset
    // The point of the way at share [t] of its length.
    fun pointAt(t: Float): Offset = if (round) {
        val angle = Math.toRadians((RING_START + RING_SWEEP * t).toDouble())
        Offset(
            (size.width / 2 + arcSize.width / 2 * cos(angle)).toFloat(),
            (size.height / 2 + arcSize.height / 2 * sin(angle)).toFloat(),
        )
    } else {
        Offset(inset + (size.width - 2 * inset) * t, lineY)
    }
    if (!ambient) {
        if (round) {
            drawArc(track, RING_START, RING_SWEEP, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(width, cap = StrokeCap.Round))
        } else {
            drawLine(track, pointAt(0f), pointAt(1f), strokeWidth = width, cap = StrokeCap.Round)
        }
    }
    if (done > 0f) {
        if (round) {
            drawArc(ink, RING_START, RING_SWEEP * done, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(width, cap = StrokeCap.Round))
        } else {
            drawLine(ink, pointAt(0f), pointAt(done), strokeWidth = width, cap = StrokeCap.Round)
        }
    }
    // The stops between: dots, dark on the part behind, light on the part ahead.
    if (!ambient && stops in 3..RING_MAX_STOP_TICKS) {
        for (k in 1 until stops - 1) {
            val t = k.toFloat() / (stops - 1)
            drawCircle(if (t <= done) ground.copy(alpha = 0.6f) else tick, radius = stroke * 0.2f, center = pointAt(t))
        }
    }
    // The stop to get off: a ring at the end.
    val end = pointAt(1f)
    if (ambient) {
        drawCircle(Color.White, radius = stroke * 0.8f, center = end, style = Stroke(stroke / 4f))
    } else {
        drawCircle(Color.White, radius = stroke * 0.9f, center = end)
        drawCircle(color, radius = stroke * 0.55f, center = end)
    }
    // The train at the head of the way.
    val head = pointAt(done)
    if (ambient) {
        drawCircle(Color.White, radius = stroke * 0.5f, center = head, style = Stroke(stroke / 4f))
    } else {
        drawCircle(Color.White, radius = stroke * 0.65f, center = head)
        drawCircle(color, radius = stroke * 0.35f, center = head)
    }
}

/**
 * The small ring of the trip card: the way of the ride in [color] with [stopsLeft] in the middle
 * (nothing there when unknown). The card's text says the same; the ring itself is decoration.
 */
@Composable
fun RideRing(fraction: Float, stops: Int, stopsLeft: Int, color: Color, modifier: Modifier = Modifier, diameter: Dp = 34.dp) {
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val ground = MaterialTheme.colorScheme.background
    val tick = MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(diameter)) {
            val stroke = 3.5.dp.toPx()
            drawRideWay(fraction, if (stops > MINI_RING_MAX_TICKS) 0 else stops, color, track, ground, tick, stroke, inset = stroke, round = true, ambient = false)
        }
        if (stopsLeft > 0) {
            Text("$stopsLeft", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}
