package io.github.jnbp.nextstop.ui

import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.sqrt

/*
 * Small chevrons along a way that has a direction: the course of a line, the rides of a
 * connection, the ride the companion follows. They repeat every ARROW_SPACING_DP along the way
 * on the screen, point the way it runs and sit inside the line in an ink that stands out from
 * its colour (white on dark lines, near black on light ones). The network of the overview has
 * no direction and no chevrons. Pure logic, no Android: the way is given as places on the
 * screen, and the work makes nothing, so it can run in every frame of a moving map.
 */

/** The distance between two chevrons along a way on the screen, in dp. */
const val ARROW_SPACING_DP = 96f

/**
 * Walks along the way through [places] (x and y in turn, pixels; [count] points) as far as
 * [reach] (in points, 0 to count - 1, a part of the next stretch after the whole ones: the way
 * as far as it has drawn itself) and calls [each] with the place of a chevron and the
 * direction of the way there (x and y of a unit vector) every [spacing] pixels, the first half a
 * spacing after the start, moved on by [phase] pixels (0 to one spacing: the chevrons flow
 * along the way as it grows). Chevrons farther than [margin] outside the screen ([width] by
 * [height]) are not reported; stretches wholly outside are skipped in one step. Returns how many
 * were reported.
 */
inline fun walkArrows(
    places: FloatArray,
    count: Int,
    reach: Float,
    spacing: Float,
    width: Float,
    height: Float,
    margin: Float,
    phase: Float = 0f,
    each: (x: Float, y: Float, dx: Float, dy: Float) -> Unit,
): Int {
    if (count < 2 || spacing <= 0f || reach <= 0f) return 0
    val end = reach.coerceAtMost((count - 1).toFloat())
    var next = arrowStart(spacing, phase)
    var walked = 0f
    var reported = 0
    var index = 0
    while (index < count - 1 && index < end) {
        val x0 = places[index * 2]
        val y0 = places[index * 2 + 1]
        var x1 = places[index * 2 + 2]
        var y1 = places[index * 2 + 3]
        val part = end - index
        if (part < 1f) {
            x1 = x0 + (x1 - x0) * part
            y1 = y0 + (y1 - y0) * part
        }
        index++
        val dx = x1 - x0
        val dy = y1 - y0
        val length = sqrt(dx * dx + dy * dy)
        if (length <= 0f) continue
        val stretchEnd = walked + length
        val outside = (x0 < -margin && x1 < -margin) || (x0 > width + margin && x1 > width + margin) ||
            (y0 < -margin && y1 < -margin) || (y0 > height + margin && y1 > height + margin)
        if (outside) {
            // None of its chevrons can show: the next one after this stretch, in one step.
            if (next <= stretchEnd) next += ceil((stretchEnd - next) / spacing + 1e-4f) * spacing
            walked = stretchEnd
            continue
        }
        val ux = dx / length
        val uy = dy / length
        while (next <= stretchEnd) {
            val along = next - walked
            val x = x0 + ux * along
            val y = y0 + uy * along
            if (x >= -margin && x <= width + margin && y >= -margin && y <= height + margin) {
                each(x, y, ux, uy)
                reported++
            }
            next += spacing
        }
        walked = stretchEnd
    }
    return reported
}

/**
 * Where the first chevron of a way lies, in pixels from its start: half a [spacing], moved on
 * by [phase] and wrapped into the first spacing, so the row of chevrons runs on without a jump.
 */
fun arrowStart(spacing: Float, phase: Float): Float {
    if (spacing <= 0f) return 0f
    val shifted = (spacing / 2f + phase) % spacing
    return if (shifted < 0f) shifted + spacing else shifted
}

/** How fast the chevrons flow along a way, in dp per second: a calm drift, not a race. */
const val ARROW_FLOW_DP_PER_SECOND = 14f

// The flow is moved on this often, in ms (about 30 times a second): smooth at its slow pace,
// and only half the frames of a 60 Hz screen, a quarter of a 120 Hz one.
const val ARROW_FLOW_FRAME_MILLIS = 33L

/**
 * How far the chevrons have flowed at [millis] (any running clock), in pixels within one
 * [spacing], at [speed] pixels per second: the phase for [walkArrows].
 */
fun arrowFlowPhase(millis: Long, speed: Float, spacing: Float): Float {
    if (spacing <= 0f || speed <= 0f) return 0f
    val period = (spacing / speed * 1000f).toLong().coerceAtLeast(1L)
    return (millis % period) / period.toFloat() * spacing
}

// The flow pauses this long (ms of uptime) after the last camera move or pick, and fades its
// pace out and in again over this long (ms of the animation clock) instead of stopping or starting at once.
const val ARROW_FLOW_REST_MILLIS = 8_000L
const val ARROW_FLOW_FADE_MILLIS = 600L

// A longer step of the clock between two rounds (a pause of the loop) moves the flow no further than this (ms).
private const val ARROW_FLOW_MAX_STEP_MILLIS = 100L

/**
 * The running flow of the chevrons: its [phase] (pixels within one [spacing]) moves on at
 * [speed] pixels per second times [pace], which fades to 0 once nothing happened for
 * [ARROW_FLOW_REST_MILLIS] and back to 1 at the next camera move or pick ([active]): the
 * chevrons slow down and come to rest, never jump. [resting] tells the loop it may stop.
 */
class ArrowFlowRun(private val spacing: Float, private val speed: Float) {
    /** How far the chevrons have flowed, within one spacing (pixels). */
    var phase = 0f
        private set

    /** The share of the full pace now, 0 to 1. */
    var pace = 0f
        private set

    private var activeAt = Long.MIN_VALUE
    private var lastClock = Long.MIN_VALUE

    /** Something happened at [nowMillis] (uptime): the camera moved, an option was picked, the flow was shown again. */
    fun active(nowMillis: Long) {
        activeAt = nowMillis
    }

    /** True when the flow has come to rest at [nowMillis] and stays there until the next [active]. */
    fun resting(nowMillis: Long): Boolean = pace <= 0f && !wanted(nowMillis)

    /** The loop paused: the next [step] goes on from there, without catching up. */
    fun pause() {
        lastClock = Long.MIN_VALUE
    }

    private fun wanted(nowMillis: Long) = activeAt != Long.MIN_VALUE && nowMillis - activeAt < ARROW_FLOW_REST_MILLIS

    /** One round at [nowMillis] (uptime) and [clockMillis] (the animation clock): the new [phase]. */
    fun step(nowMillis: Long, clockMillis: Long): Float {
        val dt = if (lastClock == Long.MIN_VALUE) 0L else (clockMillis - lastClock).coerceIn(0L, ARROW_FLOW_MAX_STEP_MILLIS)
        lastClock = clockMillis
        if (spacing <= 0f || speed <= 0f) return phase
        val before = pace
        val change = dt.toFloat() / ARROW_FLOW_FADE_MILLIS
        pace = if (wanted(nowMillis)) minOf(1f, pace + change) else maxOf(0f, pace - change)
        // The mean pace over the step: a smooth slope, no jump at either end of the fade.
        val moved = speed * (before + pace) / 2f * dt / 1000f
        phase = (phase + moved) % spacing
        if (phase < 0f) phase += spacing
        return phase
    }
}

/**
 * Whether any part of the way through [places] (x and y in turn, [count] points) may lie in the
 * room [left]..[right] × [top]..[bottom]: a point in it, or a stretch whose bounds reach into it.
 * False where the cards cover all of the way: its chevrons need not flow.
 */
fun wayInRoom(places: FloatArray, count: Int, left: Float, top: Float, right: Float, bottom: Float): Boolean {
    if (count <= 0 || right <= left || bottom <= top) return false
    var x0 = places[0]
    var y0 = places[1]
    if (count == 1) return x0 in left..right && y0 in top..bottom
    for (point in 1 until count) {
        val x1 = places[point * 2]
        val y1 = places[point * 2 + 1]
        if (minOf(x0, x1) <= right && maxOf(x0, x1) >= left && minOf(y0, y1) <= bottom && maxOf(y0, y1) >= top) return true
        x0 = x1
        y0 = y1
    }
    return false
}

// Inks of the chevrons: on a light line near black, on a dark one white; a little see-through.
const val ARROW_DARK_INK = 0xD91B1F27.toInt()
const val ARROW_LIGHT_INK = 0xF2FFFFFF.toInt()

// Above this relative luminance a line counts as light (the yellow of the U2, the light green of the S-Bahn).
private const val LIGHT_LINE_LUMINANCE = 0.42

/** The ink of the chevrons on a line of the colour [argb]: the one that stands out from it. */
fun arrowInkArgb(argb: Int): Int = if (relativeLuminance(argb) > LIGHT_LINE_LUMINANCE) ARROW_DARK_INK else ARROW_LIGHT_INK

/** The relative luminance (WCAG) of the colour [argb], 0 for black to 1 for white. */
fun relativeLuminance(argb: Int): Double {
    fun channel(value: Int): Double {
        val c = value / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel((argb shr 16) and 0xFF) + 0.7152 * channel((argb shr 8) and 0xFF) + 0.0722 * channel(argb and 0xFF)
}

/**
 * How strongly the chevrons of a way show while it draws itself ([reveal], 0 to 1): not at all
 * during the first part, then fading in with the end of the line, whole once it is drawn.
 */
fun arrowAppear(reveal: Float): Float {
    val share = ((reveal - 0.55f) / 0.45f).coerceIn(0f, 1f)
    return share * share * (3f - 2f * share)
}
