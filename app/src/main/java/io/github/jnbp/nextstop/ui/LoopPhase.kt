package io.github.jnbp.nextstop.ui

/*
 * The phase of an endless loop (a sketch that keeps moving) that runs only while it is wanted
 * and goes on from where it stood: pure logic, no Android.
 */

/** [phase] (0 to 1) moved on by [deltaNanos] of a loop [loopMillis] long, wrapped into 0 until 1. */
fun advanceLoop(phase: Float, deltaNanos: Long, loopMillis: Long): Float {
    if (loopMillis <= 0L || deltaNanos <= 0L) return phase
    val step = (deltaNanos / 1_000_000.0 / loopMillis).toFloat()
    val next = (phase + step) % 1f
    return if (next < 0f) next + 1f else next
}
