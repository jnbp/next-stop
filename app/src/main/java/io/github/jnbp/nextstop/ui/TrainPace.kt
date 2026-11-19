package io.github.jnbp.nextstop.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * The pace of the loading train (TrainLoader in Delight.kt) as plain arithmetic, without
 * Android: it never stops. It comes in from the left at speed, slows down gently towards the
 * middle of the track and speeds up again as it leaves on the right.
 */

/**
 * One run of the little train, in milliseconds: it comes in at speed, slows down through the
 * middle without stopping and speeds up as it leaves (see [trainPass]). 3.4 s felt a little
 * slow; the same calm profile now runs in 2.7 s.
 */
const val TRAIN_RUN_MILLIS = 2_700

/** Where the train of [TrainLoader] is, as the left edge of a train [trainWidth] wide on a track [trackWidth] wide. */
fun trainOffset(progress: Float, trackWidth: Float, trainWidth: Float): Float =
    -trainWidth + progress.coerceIn(0f, 1f) * (trackWidth + trainWidth)

/**
 * The train's speed in the middle of the track, as a share of its speed at both ends. At 0.14
 * it all but stood in the middle and shot off at the ends, a stop-and-go that made the train
 * seem to hold (#261); at 0.45 it glides through, still a little calmer by the platform.
 */
const val TRAIN_SLOWEST_SHARE = 0.45f

/**
 * The share of a run (around its middle) over which the train slows down and speeds up again.
 * Over 0.42 of the 2.7 s run the braking into the middle and the start out of it were too
 * abrupt (#124): it now eases in and out over 0.7 of the run, with the same slowest speed.
 */
const val TRAIN_SLOW_WIDTH = 0.7f

/**
 * The speed of the train at [time] of one run (0..1), relative to its speed at the ends:
 * v(t) = 1 - (1 - [TRAIN_SLOWEST_SHARE]) * bell(t), the bell a raised cosine
 * [TRAIN_SLOW_WIDTH] wide around the middle of the run (1 there, 0 at its edges and beyond).
 */
fun trainSpeed(time: Float): Float {
    val half = TRAIN_SLOW_WIDTH / 2f
    val d = (time - 0.5f) / half
    if (abs(d) > 1f) return 1f
    val bell = 0.5f * (1f + cos(PI.toFloat() * d))
    return 1f - (1f - TRAIN_SLOWEST_SHARE) * bell
}

// The way the speed above has gone up to [t], not yet normalised (the closed form of its integral).
private fun trainWayRaw(t: Double): Double {
    val half = TRAIN_SLOW_WIDTH / 2.0
    val calm = 1.0 - TRAIN_SLOWEST_SHARE
    // The integral of the bell from the start of the run to t.
    val bell = when {
        t <= 0.5 - half -> 0.0
        t >= 0.5 + half -> half
        else -> 0.5 * (t - (0.5 - half)) + 0.5 * half / PI * sin(PI * (t - 0.5) / half)
    }
    return t - calm * bell
}

// Points of the table of [trainPass] over one run.
private const val TRAIN_TABLE_STEPS = 256

// The way at TRAIN_TABLE_STEPS + 1 evenly spaced times of a run, 0 at the start, 1 at the end;
// worked out once, so a frame only looks up and interpolates.
private val TRAIN_TABLE = FloatArray(TRAIN_TABLE_STEPS + 1).also { table ->
    val total = trainWayRaw(1.0)
    for (i in 0..TRAIN_TABLE_STEPS) table[i] = (trainWayRaw(i.toDouble() / TRAIN_TABLE_STEPS) / total).toFloat()
    table[TRAIN_TABLE_STEPS] = 1f
}

/**
 * The little train passing its platform, as the share of the way it has gone at [time] of one
 * run (both 0..1; see [trainOffset], where 0 is just out of sight on the left, 0.5 the train in
 * the middle and 1 just out of sight on the right): the normalised way of [trainSpeed]. It
 * never stands still; it is slowest in the middle of the track, at half the run.
 */
fun trainPass(time: Float): Float {
    val t = time.coerceIn(0f, 1f) * TRAIN_TABLE_STEPS
    val index = t.toInt().coerceAtMost(TRAIN_TABLE_STEPS - 1)
    val part = t - index
    return TRAIN_TABLE[index] + (TRAIN_TABLE[index + 1] - TRAIN_TABLE[index]) * part
}
