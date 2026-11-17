package io.github.jnbp.nextstop.wear.data

/*
 * How a departure's minutes, clock time and delay are marked, the same on the phone and the
 * watch. Twin of the phone's data/DepartureTones.kt: change both together and
 * keep their tests equal.
 *
 * - Minutes: a cancelled run says so (red); one that has left is muted; one leaving within
 *   SOON_MINUTES ("now" included) is highlighted in lime; later ones are plain. The minutes do
 *   not say whether a run is late: the delay beside them does.
 * - Clock time: without live data muted (timetable), late red, on time or early green.
 * - Delay ("+4", "-1"): only with live data and when not zero; red when late, green when early.
 */

/** How the minutes until a departure are marked (see [minutesTone]). */
enum class MinutesTone { Cancelled, Gone, Soon, Plain }

/** Up to this many minutes ahead ("now" included) a departure is highlighted. */
const val SOON_MINUTES = 3L

/** The mark of [minutes] until a departure (null: no time known), [cancelled] or not. */
fun minutesTone(minutes: Long?, cancelled: Boolean): MinutesTone = when {
    cancelled -> MinutesTone.Cancelled
    minutes == null -> MinutesTone.Plain
    minutes < 0 -> MinutesTone.Gone
    minutes <= SOON_MINUTES -> MinutesTone.Soon
    else -> MinutesTone.Plain
}

/** How a clock time is marked (see [timeTone]). */
enum class TimeTone { Scheduled, Late, OnTime }

/** The mark of a clock time: [realtime] whether the provider sent live data, [delayMinutes] its delay. */
fun timeTone(realtime: Boolean, delayMinutes: Long): TimeTone = when {
    !realtime -> TimeTone.Scheduled
    delayMinutes > 0 -> TimeTone.Late
    else -> TimeTone.OnTime
}

/** How a delay beside a time is marked (see [delayTone]). */
enum class DelayTone { Late, Early }

/** The mark of the delay "+4" / "-1"; null where none is shown (no live data, or on time). */
fun delayTone(realtime: Boolean, delayMinutes: Long): DelayTone? = when {
    !realtime || delayMinutes == 0L -> null
    delayMinutes > 0 -> DelayTone.Late
    else -> DelayTone.Early
}
