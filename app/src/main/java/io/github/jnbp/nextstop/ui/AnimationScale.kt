package io.github.jnbp.nextstop.ui

/*
 * The system's "Animator duration scale" (developer options) as plain arithmetic, without
 * Android: 1 is normal, 2 twice as long, 0.5 half as long, 0 no animations at all. Compose's
 * own animations (animate*, Animatable, transitions) follow it by themselves; these helpers are
 * for the app's own loops and for the map camera (see SystemAnimation).
 */

/** The longest scale the app takes from the system; the developer options go up to 10. */
const val MAX_ANIMATOR_SCALE = 20f

/** A scale as read from the system made safe: 1 for nonsense (negative, not a number), at most [MAX_ANIMATOR_SCALE]. */
fun sanitizeAnimatorScale(raw: Float): Float = when {
    raw.isNaN() || raw < 0f -> 1f
    else -> minOf(raw, MAX_ANIMATOR_SCALE)
}

/** A duration of [millis] at the [scale] of the system: 0 without animations, else at least 1 ms for any wait. */
fun scaledMillis(millis: Long, scale: Float): Long {
    if (scale <= 0f || millis <= 0L) return 0L
    return (millis * scale.toDouble() + 0.5).toLong().coerceAtLeast(1L)
}

/** As [scaledMillis], for durations given as Int (the map camera, tweens). */
fun scaledMillis(millis: Int, scale: Float): Int = scaledMillis(millis.toLong(), scale).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

/** A time constant or duration [millis] as a Double at [scale]: 0 without animations. */
fun scaledMillis(millis: Double, scale: Float): Double = if (scale <= 0f || millis <= 0.0) 0.0 else millis * scale

/** A time far beyond the end of any animation: what has played of one when animations are off. */
const val ANIMATION_DONE_MILLIS = Long.MAX_VALUE / 4

/**
 * How much of an animation has played after [elapsedMillis] of real time at [scale]: the
 * elapsed time divided by the scale, so a duration counted against it lasts duration x scale.
 * With animations off ([scale] 0) every animation has played to its end at once.
 */
fun playedMillis(elapsedMillis: Long, scale: Float): Long {
    if (scale <= 0f) return ANIMATION_DONE_MILLIS
    if (elapsedMillis <= 0L) return elapsedMillis
    return (elapsedMillis / scale.toDouble()).toLong()
}

/**
 * The share of the way an exponential follow (time constant [tauMillis]) covers in
 * [elapsedMillis] at [scale]: 1 - e^(-t / (tau x scale)); all of it at once without animations.
 */
fun followShare(elapsedMillis: Long, tauMillis: Double, scale: Float): Double {
    val tau = scaledMillis(tauMillis, scale)
    if (tau <= 0.0) return 1.0
    return 1.0 - kotlin.math.exp(-elapsedMillis.coerceAtLeast(0L) / tau)
}

/**
 * A clock for endless loops (flowing arrows, a pulse) that runs at 1 / scale of real time, so
 * one turn of a loop takes its duration x scale. It stays continuous when the scale changes,
 * and stands still while it is 0. No objects are made per call. Not thread-safe: use it from
 * one thread (the main thread).
 */
class AnimationClock {
    private var baseReal = Long.MIN_VALUE
    private var baseTime = 0L
    private var baseScale = 1f

    /** The time of the clock at the real time [realMillis] with the system's [scale]. */
    fun at(realMillis: Long, scale: Float): Long {
        if (baseReal == Long.MIN_VALUE) {
            baseReal = realMillis
            baseTime = realMillis
            baseScale = scale
        } else if (scale != baseScale) {
            baseTime = run(realMillis, baseScale)
            baseReal = realMillis
            baseScale = scale
        }
        return run(realMillis, scale)
    }

    private fun run(realMillis: Long, scale: Float): Long =
        if (scale <= 0f) baseTime else baseTime + ((realMillis - baseReal) / scale.toDouble()).toLong()
}
