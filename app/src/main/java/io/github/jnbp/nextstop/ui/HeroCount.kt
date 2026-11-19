package io.github.jnbp.nextstop.ui

/*
 * #148: the big lime counter of the companion. From an hour on a countdown ("ca. 2:59 h") is
 * hard to read; the clock time of the step says more ("an 15:17"). Below an hour the minutes
 * count down ("45′"). Around the boundary a minute of hysteresis keeps it from flipping back
 * and forth while the live times move by a minute.
 */

/** From this many minutes ahead the counter shows the clock time of the step. */
const val HERO_CLOCK_FROM_MINUTES = 60L

/**
 * Whether the counter shows the clock time with [minutes] to go, [showingClock] what it shows
 * now: the clock from [HERO_CLOCK_FROM_MINUTES] on; once shown, it stays down to one minute less.
 */
fun heroShowsClock(minutes: Long, showingClock: Boolean): Boolean =
    minutes >= if (showingClock) HERO_CLOCK_FROM_MINUTES - 1 else HERO_CLOCK_FROM_MINUTES

/** Whether the clock time of a step of [kind] is an arrival ("an 15:17") rather than a departure ("um 15:17"). */
fun heroClockIsArrival(kind: StepKind): Boolean = kind != StepKind.Board

/**
 * Where the parts of the minute count sit in the lime pill (#182), in pixels: [width] of the
 * whole, and the x of the small [lead] ("ca."), the [number] and its [unit] (the prime).
 */
data class HeroCountPlacement(val width: Int, val lead: Int, val number: Int, val unit: Int)

/**
 * The minute count placed so its digits are the middle of the pill: the prime hangs off to the
 * right and "ca." off to the left, each balanced by as much room on the other side, so the
 * pill's own centring puts the digits in its centre. The number takes at least [slotWidth]
 * (the width of two digits), so the pill keeps its width from 10′ to 9′; a shorter number sits
 * in the middle of that slot with the prime right after its last digit, never after the slot.
 */
fun heroCountPlacement(leadWidth: Int, numberWidth: Int, unitWidth: Int, slotWidth: Int): HeroCountPlacement {
    val side = maxOf(leadWidth, unitWidth, 0)
    val slot = maxOf(numberWidth, slotWidth)
    val number = side + (slot - numberWidth) / 2
    return HeroCountPlacement(width = slot + 2 * side, lead = number - leadWidth, number = number, unit = number + numberWidth)
}
