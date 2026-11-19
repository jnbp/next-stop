package io.github.jnbp.nextstop.wear.data

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The crown zoom of an opened map whose picture is a fixed framing (the map of a change): a
 * factor from [MIN] (the framing as it opens) to [MAX], in [STEPS_PER_DOUBLING] notches per
 * doubling, so each notch of the crown is one tick of the haptics.
 */
object MapZoom {
    const val MIN = 1.0
    const val MAX = 4.0
    const val STEPS_PER_DOUBLING = 4

    // Crown scroll pixels per notch (a turn of a usual crown or bezel gives several notches).
    const val PIXELS_PER_STEP = 48f

    // Above this factor the phone is asked for a sharper picture of the zoomed view.
    const val SHARPER_ABOVE = 2.0

    /** The factor of notch [step] (0: the framing), within [MIN]..[MAX]. */
    fun factorOf(step: Int): Double = 2.0.pow(step.toDouble() / STEPS_PER_DOUBLING).coerceIn(MIN, MAX)

    /** The highest notch: [MAX]. */
    val maxStep: Int get() = (ln(MAX) / ln(2.0) * STEPS_PER_DOUBLING).roundToInt()

    /** The notch after [step] moved by [delta] notches (crown up: in), kept within 0..[maxStep]. */
    fun stepBy(step: Int, delta: Int): Int = (step + delta).coerceIn(0, maxStep)

    /**
     * The whole notches in [pixels] of crown scrolling gathered so far, and what is left over
     * for the next event (so slow turns add up and a fast one gives several notches).
     */
    fun notches(pixels: Float): Pair<Int, Float> {
        val whole = (pixels / PIXELS_PER_STEP).toInt()
        return whole to (pixels - whole * PIXELS_PER_STEP)
    }

    /**
     * The scale to ask the phone for a picture at, relative to the framing: the framing's own
     * up to [SHARPER_ABOVE] (the picture is stretched, still fine), above it the zoom rounded to
     * half a doubling, so the crown does not ask for a new picture on every notch.
     */
    fun requestFactor(factor: Double): Double {
        if (factor <= SHARPER_ABOVE) return 1.0
        val halves = (ln(factor) / ln(2.0) * 2).roundToInt()
        return 2.0.pow(halves / 2.0).coerceIn(SHARPER_ABOVE, MAX)
    }

    /**
     * The pan ([x], [y] in world units from the framing's middle) kept so the zoomed view stays
     * inside the framing: a view of [width] x [height] pixels at [pixelsPerUnit] for the framing,
     * zoomed by [factor]. At [MIN] the pan is always zero.
     */
    fun clampPan(x: Double, y: Double, factor: Double, width: Int, height: Int, pixelsPerUnit: Double): Pair<Double, Double> {
        if (pixelsPerUnit <= 0.0 || factor <= MIN) return 0.0 to 0.0
        val reachX = max(0.0, width / 2.0 / pixelsPerUnit * (1 - 1 / factor))
        val reachY = max(0.0, height / 2.0 / pixelsPerUnit * (1 - 1 / factor))
        return min(reachX, max(-reachX, x)) to min(reachY, max(-reachY, y))
    }
}
