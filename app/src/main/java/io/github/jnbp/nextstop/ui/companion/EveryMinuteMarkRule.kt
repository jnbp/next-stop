package io.github.jnbp.nextstop.ui.companion

/*
 * #259: while "Every minute counts" is on for the trip followed, the top bar of the new
 * Live-Fahrt views (calm and with the map) carries a small lightning bolt. As the mode comes on
 * (switched on during the trip, or the page opened with it on) the bolt pops in with its word
 * ("Every minute") for a moment, then stays as the bolt alone while the mode is on.
 */

/** What the mark at the top of the Live-Fahrt shows. */
enum class EveryMinuteMarkLook { Hidden, Word, Bolt }

/** How long the bolt keeps its word after the mode came on, in milliseconds. */
const val EVERY_MINUTE_WORD_MILLIS = 3_000L

/**
 * The mark with the mode [on] since [onSinceMillis] (a clock of the device, 0 before it came
 * on) at [nowMillis] of the same clock: hidden while off, the bolt with its word for
 * [EVERY_MINUTE_WORD_MILLIS] after it came on, then the bolt alone.
 */
fun everyMinuteMarkLook(on: Boolean, onSinceMillis: Long, nowMillis: Long): EveryMinuteMarkLook = when {
    !on -> EveryMinuteMarkLook.Hidden
    onSinceMillis <= 0L -> EveryMinuteMarkLook.Word
    nowMillis - onSinceMillis < EVERY_MINUTE_WORD_MILLIS -> EveryMinuteMarkLook.Word
    else -> EveryMinuteMarkLook.Bolt
}
