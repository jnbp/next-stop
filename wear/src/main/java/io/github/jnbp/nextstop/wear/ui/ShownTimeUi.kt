package io.github.jnbp.nextstop.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import io.github.jnbp.nextstop.wear.data.DelayDisplay
import io.github.jnbp.nextstop.wear.data.Journey
import io.github.jnbp.nextstop.wear.data.TimeTone
import io.github.jnbp.nextstop.wear.data.WatchStore
import io.github.jnbp.nextstop.wear.data.shownTime

/*
 * #227b: the watch's times with a delay as the phone's setting "Verspätungen" says (see
 * ShownTime.kt), drawn like the phone's appendDelayedTime: the timetable time struck through,
 * the clock time green (live, on time), red (live, late) or plain (no live data), a small "+2".
 */

/** The phone's setting how delays are shown, as synced with the places; the default before any sync. */
@Composable
fun rememberDelayDisplay(): DelayDisplay = WatchStore.places.collectAsState().value.phoneDelayDisplay

/**
 * Appends the time [actual] (delay included, epoch ms) with its [delayMinutes] as [display] says;
 * [realtime] whether it is live, [plain] the colour without live data, [muted] the struck time's.
 * An [estimated] time (the phone's estimate from the position, #277) is blue with [about] ("ca.").
 */
fun AnnotatedString.Builder.appendShownTime(
    display: DelayDisplay,
    actual: Long,
    delayMinutes: Long,
    realtime: Boolean,
    plain: Color,
    muted: Color,
    estimated: Boolean = false,
    about: String = "",
) {
    val shown = shownTime(display, actual, actual - delayMinutes * 60_000L, realtime, delayMinutes, estimated)
    if (shown.tone == TimeTone.Estimated && about.isNotEmpty()) append(about + " ")
    shown.struck?.let { struck ->
        withStyle(SpanStyle(color = muted, textDecoration = TextDecoration.LineThrough, fontSize = 0.85.em)) { append(clock(struck)) }
        append(" ")
    }
    val tone = when (shown.tone) {
        TimeTone.Scheduled -> plain
        TimeTone.Late -> Brand.Late
        TimeTone.OnTime -> Brand.Early
        // The blue of the own position, light enough to read on black.
        TimeTone.Estimated -> Brand.BlueOnDark
    }
    withStyle(SpanStyle(color = tone)) { append(clock(shown.clock)) }
    shown.delay?.let { delay ->
        withStyle(SpanStyle(color = if (delay > 0) Brand.Late else Brand.Early, fontWeight = FontWeight.Bold, fontSize = 0.8.em)) {
            append(" " + "%+d".format(delay))
        }
    }
}

/**
 * [template] with each [times] put in for its marker ("\u0000", "\u0001", …) as [display] says
 * (see appendShownTime); [times]: actual (epoch ms), delay (min) and whether live; [estimated]
 * with [about] ("ca.") for times the phone estimated from the position (#277).
 */
fun shownTimesText(
    template: String,
    display: DelayDisplay,
    times: List<Triple<Long, Long, Boolean>>,
    plain: Color,
    muted: Color,
    estimated: Boolean = false,
    about: String = "",
): AnnotatedString = buildAnnotatedString {
    template.forEach { char ->
        val time = times.getOrNull(char.code)
        if (char.code < times.size && time != null) {
            appendShownTime(display, time.first, time.second, time.third, plain, muted, estimated, about)
        } else {
            append(char)
        }
    }
}

/** The departure ("\u0000") and arrival ("\u0001") of [journey] for [shownTimesText], as the phone's journeySpanText. */
fun journeyTimes(journey: Journey): List<Triple<Long, Long, Boolean>> {
    val first = journey.legs.first()
    val last = journey.legs.last()
    return listOf(
        Triple(first.departure, first.departureDelayMinutes.toLong(), first.realtime),
        Triple(last.arrival, last.arrivalDelayMinutes.toLong(), last.realtime),
    )
}
