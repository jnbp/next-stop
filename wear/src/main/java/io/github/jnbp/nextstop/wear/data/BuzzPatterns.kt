package io.github.jnbp.nextstop.wear.data

/*
 * The vibrations of a trip on the wrist: one pattern per kind of event, to be told apart
 * without looking (see docs/watch-vibrations.md). Short (each at most about a second) and
 * only once per event. Pure, so the mapping is tested; [playBuzz] plays a pattern.
 */

/** The events of a trip the wrist feels. */
enum class TripBuzz {
    /** The trip starts, or the next ride was boarded: two short taps. */
    Board,

    /** Get off at the next stop (the end of the last ride): one long, one short. */
    GetOffNext,

    /** Change at the next stop: three short taps. */
    ChangeNow,

    /** Something went wrong (platform changed, ride missed or cancelled, change broken): long, short, long. */
    Urgent,

    /** Arrived at the destination: one soft pulse. */
    Arrival,
}

/**
 * A waveform: [timings] in milliseconds, pauses and pulses in turn from a pause of 0; the
 * strength of each in [amplitudes] (0 for the pauses, 1..255 for the pulses).
 */
class BuzzPattern(val timings: LongArray, val amplitudes: IntArray) {
    /** How long the whole pattern lasts, in milliseconds. */
    val millis: Long get() = timings.sum()
}

private val BOARD = BuzzPattern(longArrayOf(0, 70, 110, 70), intArrayOf(0, 200, 0, 200))
private val GET_OFF_NEXT = BuzzPattern(longArrayOf(0, 450, 150, 90), intArrayOf(0, 255, 0, 255))
private val CHANGE_NOW = BuzzPattern(longArrayOf(0, 70, 100, 70, 100, 70), intArrayOf(0, 230, 0, 230, 0, 230))
private val URGENT = BuzzPattern(longArrayOf(0, 320, 110, 90, 110, 320), intArrayOf(0, 255, 0, 255, 0, 255))
private val ARRIVAL = BuzzPattern(longArrayOf(0, 200), intArrayOf(0, 110))

/** The pattern of [event]. */
fun buzzPattern(event: TripBuzz): BuzzPattern = when (event) {
    TripBuzz.Board -> BOARD
    TripBuzz.GetOffNext -> GET_OFF_NEXT
    TripBuzz.ChangeNow -> CHANGE_NOW
    TripBuzz.Urgent -> URGENT
    TripBuzz.Arrival -> ARRIVAL
}

/** The event of the alert before getting off: a [change] there, else the end of the last ride. */
fun alertBuzz(change: Boolean): TripBuzz = if (change) TripBuzz.ChangeNow else TripBuzz.GetOffNext

/**
 * True when [after] brings bad news about the same trip as [before], to be felt at once: the
 * ride was missed, the phone offers a rescue for a broken change, the platform of the same
 * step changed, or another ride of the plan was cancelled. False for a first state, another
 * trip, or a trip that ended.
 */
fun urgentChange(before: TripSnapshot?, after: TripSnapshot): Boolean {
    if (before == null || !before.active || !after.active) return false
    if (before.tripKey != after.tripKey) return false
    if (after.missed && !before.missed) return true
    if (after.rescue != null && before.rescue?.key != after.rescue.key) return true
    val sameStep = before.kind == after.kind && before.planIndex == after.planIndex && before.line == after.line && before.place == after.place
    val platformBefore = before.platform?.trim()?.takeIf { it.isNotEmpty() }
    val platformAfter = after.platform?.trim()?.takeIf { it.isNotEmpty() }
    if (sameStep && after.kind == StepKind.Board && platformBefore != null && platformAfter != null && platformBefore != platformAfter) return true
    if (before.plan.size == after.plan.size && after.plan.indices.any { after.plan[it].cancelled && !before.plan[it].cancelled }) return true
    return false
}
