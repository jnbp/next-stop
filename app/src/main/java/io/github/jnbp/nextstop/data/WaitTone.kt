package io.github.jnbp.nextstop.data

/**
 * How a change is coloured where it is shown (choice tiles, change rows, the chart): by the
 * time it leaves, not only by its [Tightness]. A change with lots of room is not "the best
 * one" when that room is a long wait on the platform.
 *
 * - [Sprint]: cannot be made by the walk rule (Tightness.VeryRisky), magenta.
 * - [Tight]: knapp (Tightness.Risky), red.
 * - [Comfortable]: enough room and no long wait, green.
 * - [Neutral]: in between (little room, or a wait that is getting long), grey.
 * - [LongWait]: a long wait on the platform, a calm sand with a clock.
 *
 * Only for showing: the transfer level filter (TransferComfort) keeps rating by [Tightness].
 */
enum class WaitTone { Sprint, Tight, Comfortable, Neutral, LongWait }

// The thresholds of [waitTone], all in minutes, in one place.
/** A change is comfortable from this many minutes to spare (and at least as many as it needs). */
const val TONE_MIN_SPARE = 2L

/** A comfortable change leaves at most this much from arrival to departure ... */
const val TONE_COMFORT_MAX_GAP = 12L

/** ... or at most this much more than the change needs (for long walks). */
const val TONE_COMFORT_MAX_OVER_NEED = 8L

/** Beyond this many minutes standing around (beyond what the change needs) the wait is long. */
const val TONE_LONG_WAIT = 15L

/**
 * The [WaitTone] of a change of [tightness] with [minutes] from the live arrival to the live
 * departure and [need] minutes needed for it (walk included; see [changeNeed]):
 *
 * - sprint and knapp as [tightness] says (it may come from the journey's own rating);
 * - long wait where more than [TONE_LONG_WAIT] minutes are left beyond the need;
 * - comfortable where the spare is at least the need (and at least [TONE_MIN_SPARE]) and the
 *   whole change takes no more than [TONE_COMFORT_MAX_GAP] minutes, or no more than
 *   [TONE_COMFORT_MAX_OVER_NEED] beyond its need;
 * - neutral otherwise.
 */
fun waitTone(tightness: Tightness, minutes: Long, need: Long): WaitTone {
    if (tightness == Tightness.VeryRisky) return WaitTone.Sprint
    if (tightness == Tightness.Risky) return WaitTone.Tight
    val spare = minutes - need
    if (spare > TONE_LONG_WAIT) return WaitTone.LongWait
    val enoughRoom = spare >= maxOf(need, TONE_MIN_SPARE)
    val shortWait = minutes <= maxOf(TONE_COMFORT_MAX_GAP, need + TONE_COMFORT_MAX_OVER_NEED)
    return if (enoughRoom && shortWait) WaitTone.Comfortable else WaitTone.Neutral
}

/** The [WaitTone] of this change time. */
val ChangeTime.tone: WaitTone get() = waitTone(tightness, minutes, need)
