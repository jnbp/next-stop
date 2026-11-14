package io.github.jnbp.nextstop.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import android.graphics.Typeface
import android.util.LruCache
import androidx.compose.ui.graphics.toArgb
import kotlin.math.max
import io.github.jnbp.nextstop.data.OsmLine
import io.github.jnbp.nextstop.data.PassageKind
import io.github.jnbp.nextstop.data.WalkDetails

/*
 * The marks of a walk at a station, drawn the same way wherever a walk is shown: on the small
 * map of the walk (WalkMiniMap, a still picture), over the big map while the walk is in focus
 * (WalkDetailsOverlay) and on the map pictures of the watch (WatchMapImages). One drawing for
 * all, so they cannot drift apart:
 *
 * - under the way: the other stop positions of the stops at its ends, small pale dots with
 *   "Pos. 3" ([drawWalkUnder]);
 * - over the way: the entrances and exits it passes (blue signs with their letters), lifts and
 *   escalators (grey signs), and the stop positions it starts or ends at as pins: a dot in the
 *   colour of the line and above it the pill "Pos. 4" with the line chips, as on the main map,
 *   ringed in that colour ([drawWalkOver]).
 *
 * Plain android.graphics on a canvas with a function that puts a point (latitude, longitude)
 * on it, in pixels; [density] is pixels per dp.
 */

/** A stop position a walk starts or ends at: the pin with [pos] ("4") and its [lines], in [color] (ARGB). */
internal data class PositionPin(
    val point: Pair<Double, Double>,
    val pos: String?,
    val lines: List<OsmLine>,
    val color: Int,
)

/** The pills "Pos. 4" with their line chips, made once per look (they are drawn on every frame of the big map). */
private object PillCache {
    val pills = LruCache<String, Bitmap>(24)

    fun pill(pos: String?, lines: List<OsmLine>, density: Float, at: Pair<Double, Double>?): Bitmap {
        val key = pos + "|" + lines.joinToString(",") { it.name + "/" + it.product } + "|" + density + "|" +
            (at?.let { "%.2f,%.2f".format(java.util.Locale.ROOT, it.first, it.second) } ?: "")
        pills.get(key)?.let { return it }
        return stopPositionBitmap(pos, lines, density, at).also { pills.put(key, it) }
    }
}

/** The colour of the pin of a stop position with [lines] (the first of them), else [fallback]. */
internal fun pinColor(lines: List<OsmLine>, at: Pair<Double, Double>?, fallback: Int): Int =
    lines.firstOrNull()?.let { lineColor(it.name, it.product, at).toArgb() } ?: fallback

/**
 * What lies under the way of a walk with [details]: its other stop positions, small pale dots
 * with "Pos. 3" above them (not those at [skip], drawn as pins).
 */
internal fun drawWalkUnder(
    canvas: AndroidCanvas,
    details: WalkDetails,
    density: Float,
    skip: List<Pair<Double, Double>> = emptyList(),
    pixel: (Pair<Double, Double>) -> Pair<Float, Float>,
) {
    if (details.positions.isEmpty()) return
    val dot = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { color = 0x997A808A.toInt() }
    val rim = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { color = 0xB3FFFFFF.toInt() }
    val halo = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = 0xB3FFFFFF.toInt()
        textSize = 9f * density
        typeface = Typeface.DEFAULT_BOLD
        textAlign = AndroidPaint.Align.CENTER
        style = AndroidPaint.Style.STROKE
        strokeWidth = 2.5f * density
        strokeJoin = AndroidPaint.Join.ROUND
    }
    val ink = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = 0xCC4B515B.toInt()
        textSize = 9f * density
        typeface = Typeface.DEFAULT_BOLD
        textAlign = AndroidPaint.Align.CENTER
    }
    for (position in details.positions) {
        if (position == details.target || position == details.start) continue
        if (skip.any { it == position.position }) continue
        val (x, y) = pixel(position.position)
        if (x < -40f || y < -40f || x > canvas.width + 40f || y > canvas.height + 40f) continue
        canvas.drawCircle(x, y, 4f * density, rim)
        canvas.drawCircle(x, y, 3f * density, dot)
        position.pos?.let { pos ->
            val text = "Pos. $pos"
            canvas.drawText(text, x, y - 6f * density, halo)
            canvas.drawText(text, x, y - 6f * density, ink)
        }
    }
}

/**
 * What lies over the way of a walk with [details]: the entrances, lifts and escalators it passes
 * (a lift out of order, [brokenLifts] within 15 m, in red with a cross), then the [pins] of the
 * stop positions it starts or ends at; each pill on the side away from the other pin.
 */
internal fun drawWalkOver(
    canvas: AndroidCanvas,
    details: WalkDetails,
    brokenLifts: List<Pair<Double, Double>>,
    density: Float,
    pins: List<PositionPin> = emptyList(),
    pixel: (Pair<Double, Double>) -> Pair<Float, Float>,
) {
    drawPassageSigns(canvas, details, brokenLifts, density, pixel)
    val at = pins.map { pixel(it.point) }
    pins.forEachIndexed { index, pin ->
        val (x, y) = at[index]
        val otherY = at.filterIndexed { i, _ -> i != index }.firstOrNull()?.second
        drawPositionPin(canvas, x, y, pin, density, up = otherY == null || otherY >= y)
    }
}

/**
 * The pin of a stop position at [x], [y]: a dot in its colour with a white rim, and above it
 * ([up]; below where there is no room or not [up]) the pill "Pos. 4" with the line chips,
 * ringed in the colour of the pin.
 */
internal fun drawPositionPin(canvas: AndroidCanvas, x: Float, y: Float, pin: PositionPin, density: Float, up: Boolean = true) {
    val fill = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    val ground = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { color = pin.color }
    canvas.drawCircle(x, y, 7.5f * density, fill)
    canvas.drawCircle(x, y, 5.5f * density, ground)
    if (pin.pos == null && pin.lines.isEmpty()) return
    val pill = PillCache.pill(pin.pos, pin.lines, density, pin.point)
    val gap = 9f * density
    val maxLeft = (canvas.width - pill.width).toFloat().coerceAtLeast(0f)
    val left = (x - pill.width / 2f).coerceIn(0f, maxLeft)
    val above = y - gap - pill.height
    val below = y + gap
    val top = when {
        up && above >= 0f -> above
        !up && below + pill.height <= canvas.height -> below
        above >= 0f -> above
        else -> below
    }
    // A ring in the colour of the line marks the pill as the one the walk is about.
    val ring = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = pin.color
        style = AndroidPaint.Style.STROKE
        strokeWidth = 2f * density
    }
    val box = RectF(left - 1.5f * density, top - 1.5f * density, left + pill.width + 1.5f * density, top + pill.height + 1.5f * density)
    val halo = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    canvas.drawRoundRect(RectF(box.left - 1.5f * density, box.top - 1.5f * density, box.right + 1.5f * density, box.bottom + 1.5f * density), box.height() / 2f + 1.5f * density, box.height() / 2f + 1.5f * density, halo)
    canvas.drawBitmap(pill, left, top, null)
    canvas.drawRoundRect(box, box.height() / 2f, box.height() / 2f, ring)
}

/**
 * The entrances, lifts and escalators of [details] on the way: an entrance as a small blue
 * sign with its letter (or an arrow), a lift as the lift sign, an escalator as a slanted step
 * line; a lift out of order ([brokenLifts], within 15 m) in red with a cross.
 */
private fun drawPassageSigns(
    canvas: AndroidCanvas,
    details: WalkDetails,
    brokenLifts: List<Pair<Double, Double>>,
    density: Float,
    pixel: (Pair<Double, Double>) -> Pair<Float, Float>,
) {
    if (details.passages.isEmpty()) return
    for (passage in details.passages) {
        val (x, y) = pixel(passage.position)
        when (passage.kind) {
            PassageKind.Entrance -> drawEntranceSign(canvas, x, y, passage.label, passage.throughBuilding, density)
            PassageKind.Lift, PassageKind.Escalator -> {
                val broken = passage.kind == PassageKind.Lift && brokenLifts.any { liftMeters(it, passage.position) <= 15.0 }
                drawLiftSign(canvas, x, y, passage.kind == PassageKind.Escalator, broken, density)
            }
        }
    }
}

/** An entrance sign at [x], [y]: blue (grey for an access [throughBuilding]) with its [label] where short, else an arrow. */
internal fun drawEntranceSign(canvas: AndroidCanvas, x: Float, y: Float, label: String?, throughBuilding: Boolean, density: Float, ground: Int? = null) {
    val white = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    val ink = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 10f * density
        typeface = Typeface.DEFAULT_BOLD
        textAlign = AndroidPaint.Align.CENTER
    }
    val half = 8f * density
    val corner = 3f * density
    val text = label?.takeIf { it.length <= 3 } ?: "↗"
    val halfWidth = max(half, ink.measureText(text) / 2f + 4f * density)
    val box = RectF(x - halfWidth, y - half, x + halfWidth, y + half)
    canvas.drawRoundRect(RectF(box.left - 1.5f * density, box.top - 1.5f * density, box.right + 1.5f * density, box.bottom + 1.5f * density), corner + 1.5f * density, corner + 1.5f * density, white)
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = ground ?: if (throughBuilding) 0xFF5F6670.toInt() else 0xFF0664AB.toInt()
    }
    canvas.drawRoundRect(box, corner, corner, paint)
    canvas.drawText(text, x, y - (ink.descent() + ink.ascent()) / 2f, ink)
}

/** A lift sign at [x], [y] (triangles up and down), an escalator sign ([escalator], a step line); [broken] in red with a cross. */
internal fun drawLiftSign(canvas: AndroidCanvas, x: Float, y: Float, escalator: Boolean, broken: Boolean, density: Float) {
    val white = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    val stroke = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = AndroidPaint.Style.STROKE
        strokeWidth = 1.6f * density
        strokeCap = AndroidPaint.Cap.ROUND
        strokeJoin = AndroidPaint.Join.ROUND
    }
    val half = 8f * density
    val corner = 3f * density
    val box = RectF(x - half, y - half, x + half, y + half)
    canvas.drawRoundRect(RectF(box.left - 1.5f * density, box.top - 1.5f * density, box.right + 1.5f * density, box.bottom + 1.5f * density), corner + 1.5f * density, corner + 1.5f * density, white)
    val ground = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { color = if (broken) 0xFFC62828.toInt() else 0xFF5A6072.toInt() }
    canvas.drawRoundRect(box, corner, corner, ground)
    val unit = half / 7.5f
    when {
        escalator -> {
            val path = AndroidPath().apply {
                moveTo(box.left + 3 * unit, box.bottom - 4 * unit)
                lineTo(box.left + 6 * unit, box.bottom - 4 * unit)
                lineTo(box.right - 6 * unit, box.top + 4 * unit)
                lineTo(box.right - 3 * unit, box.top + 4 * unit)
            }
            canvas.drawPath(path, stroke)
        }
        broken -> {
            canvas.drawLine(box.left + 4 * unit, box.top + 4 * unit, box.right - 4 * unit, box.bottom - 4 * unit, stroke)
            canvas.drawLine(box.right - 4 * unit, box.top + 4 * unit, box.left + 4 * unit, box.bottom - 4 * unit, stroke)
        }
        else -> {
            val upPath = AndroidPath().apply {
                moveTo(x, box.top + 2.5f * unit)
                lineTo(x + 3.5f * unit, box.top + 6.5f * unit)
                lineTo(x - 3.5f * unit, box.top + 6.5f * unit)
                close()
            }
            val downPath = AndroidPath().apply {
                moveTo(x, box.bottom - 2.5f * unit)
                lineTo(x + 3.5f * unit, box.bottom - 6.5f * unit)
                lineTo(x - 3.5f * unit, box.bottom - 6.5f * unit)
                close()
            }
            canvas.drawPath(upPath, white)
            canvas.drawPath(downPath, white)
        }
    }
}

private fun liftMeters(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
    val north = (b.first - a.first) * 111_320.0
    val east = (b.second - a.second) * 111_320.0 * Math.cos(Math.toRadians((a.first + b.first) / 2))
    return Math.sqrt(north * north + east * east)
}
