package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime

/*
 * #227: how one clock time with its delay is shown, as the setting "Verspätungen" says, in one
 * place for every view (departures, detail, Live-Fahrt, chart, pickers, notification). The
 * views only draw what this decides; the colours follow the marks of DepartureTones:
 * green = live and on time (or early), red = live and late, plain = no live data.
 */

/**
 * How a time with a delay is shown everywhere in the app.
 * - [RealtimeAndDelay]: the expected time (delay included) with "+2" beside it (default).
 * - [PlannedAndDelay]: the timetable time with "+2" beside it.
 * - [RealtimeOnly]: the expected time alone, red when late and green when on time.
 * - [StruckPlanned]: the timetable time struck through beside the expected time (as in "Takt").
 */
enum class DelayDisplay { RealtimeAndDelay, PlannedAndDelay, RealtimeOnly, StruckPlanned }

/**
 * One time as shown: the [clock] time in the colour of [tone], the timetable time [struck]
 * through before it (null for none) and the delay [delay] beside it ("+2"; null for none).
 */
data class ShownTime(
    val clock: OffsetDateTime,
    val tone: TimeTone,
    val struck: OffsetDateTime?,
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
    actual: OffsetDateTime,
    planned: OffsetDateTime?,
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
 * The time as plain text, where nothing can be coloured or struck through (the title of a
 * Live Update): the clock time and, as the setting says, the delay ("08:37 +1") written by [delayText].
 */
fun shownTimeText(shown: ShownTime, clockText: (OffsetDateTime) -> String, delayText: (Long) -> String): String =
    clockText(shown.clock) + (shown.delay?.let { " " + delayText(it) } ?: "")
