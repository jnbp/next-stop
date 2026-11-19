package io.github.jnbp.nextstop.wear.data

/*
 * #227b: how one clock time with its delay is shown on the watch, as the phone's setting
 * "Verspätungen" says (synced as "delayDisplay" with the places, see SyncedPlaces). Twin of the
 * phone's data/ShownTime.kt with epoch milliseconds instead of OffsetDateTime: change both
 * together and keep their tests equal. The colours follow [timeTone]: green = live and on time
 * (or early), red = live and late, plain = no live data.
 */

/**
 * How a time with a delay is shown (the phone's DelayDisplay, by the same names).
 * - [RealtimeAndDelay]: the expected time (delay included) with "+2" beside it (default).
 * - [PlannedAndDelay]: the timetable time with "+2" beside it.
 * - [RealtimeOnly]: the expected time alone, red when late and green when on time.
 * - [StruckPlanned]: the timetable time struck through beside the expected time.
 */
enum class DelayDisplay {
    RealtimeAndDelay, PlannedAndDelay, RealtimeOnly, StruckPlanned;

    companion object {
        /** The setting by its name as the phone writes it; the default for anything else (an older phone app). */
        fun parse(name: String?): DelayDisplay = entries.firstOrNull { it.name == name } ?: RealtimeAndDelay
    }
}

/**
 * One time as shown: the [clock] time (epoch ms) in the colour of [tone], the timetable time
 * [struck] through before it (null for none) and the delay [delay] beside it ("+2"; null for none).
 */
data class ShownTime(
    val clock: Long,
    val tone: TimeTone,
    val struck: Long?,
    val delay: Long?,
)

/**
 * How [display] shows the time [actual] (delay included) planned for [planned], [realtime]
 * whether it is live, [delayMinutes] its delay: the timetable time with PlannedAndDelay (plain,
 * the "+2" beside it carries the colour), else the expected time, green or red when live.
 * [estimated] (#277): the time is the app's own estimate from the position ("ca."), not the
 * provider's: that time alone, blue, nothing struck and no delay. The flag is only set where
 * the estimate stands in place of the live time, so a live time without it stays green or red.
 */
fun shownTime(
    display: DelayDisplay,
    actual: Long,
    planned: Long?,
    realtime: Boolean,
    delayMinutes: Long,
    estimated: Boolean = false,
): ShownTime {
    if (estimated) return ShownTime(actual, TimeTone.Estimated, null, null)
    val showsPlanned = display == DelayDisplay.PlannedAndDelay
    val changed = realtime && delayMinutes != 0L
    return ShownTime(
        clock = if (showsPlanned && planned != null) planned else actual,
        tone = shownTone(display, realtime, delayMinutes),
        struck = planned?.takeIf { display == DelayDisplay.StruckPlanned && changed },
        delay = delayMinutes.takeIf { shownDelayStyle(display) && delayTone(realtime, delayMinutes) != null },
    )
}

/**
 * The colour of a shown time: blue for the app's estimate from the position ([estimated], #277),
 * plain as timetable time (PlannedAndDelay) or without live data, else green or red.
 */
fun shownTone(display: DelayDisplay, realtime: Boolean, delayMinutes: Long, estimated: Boolean = false): TimeTone = when {
    estimated -> TimeTone.Estimated
    display == DelayDisplay.PlannedAndDelay -> TimeTone.Scheduled
    else -> timeTone(realtime, delayMinutes)
}

/** Whether a "+2" stands beside a time with [display]. */
fun shownDelayStyle(display: DelayDisplay): Boolean =
    display == DelayDisplay.RealtimeAndDelay || display == DelayDisplay.PlannedAndDelay

/**
 * The time as plain text, where nothing can be coloured or struck through (a complication):
 * the clock time and, as the setting says, the delay ("08:37 +1") written by [delayText].
 */
fun shownTimeText(shown: ShownTime, clockText: (Long) -> String, delayText: (Long) -> String): String =
    clockText(shown.clock) + (shown.delay?.let { " " + delayText(it) } ?: "")

/**
 * The time a departure board counts its minutes to (the phone's DepartureRow): the timetable
 * time with PlannedAndDelay (when known), else the expected one.
 */
fun countedTime(display: DelayDisplay, time: Long?, plannedTime: Long?): Long? =
    if (display == DelayDisplay.PlannedAndDelay && plannedTime != null) plannedTime else time ?: plannedTime
