package io.github.jnbp.nextstop.wear.data

/*
 * #148b: the watch shows the countdown of a step as the phone does (heroShowsClock there): from
 * an hour on the clock time ("an 15:17" / "um 15:17"), below it the minutes ("45′"), with a
 * minute of hysteresis so it does not flip while the live times move by a minute.
 */

/** From this many minutes ahead the countdown shows the clock time. */
const val WATCH_CLOCK_FROM_MINUTES = 60L

/** The minutes until a time [leftMillis] ahead: the full minutes left, as the phone counts them (see [minutesUntil]). */
fun watchMinutesLeft(leftMillis: Long): Long = (leftMillis / 60_000).coerceAtLeast(0)

/**
 * Whether the countdown shows the clock time with [leftMillis] to go, [showingClock] what it
 * shows now: from [WATCH_CLOCK_FROM_MINUTES] on; once shown, down to one minute less.
 */
fun watchShowsClock(leftMillis: Long, showingClock: Boolean = false): Boolean =
    watchMinutesLeft(leftMillis) >= if (showingClock) WATCH_CLOCK_FROM_MINUTES - 1 else WATCH_CLOCK_FROM_MINUTES
