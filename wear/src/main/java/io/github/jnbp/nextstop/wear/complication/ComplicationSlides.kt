package io.github.jnbp.nextstop.wear.complication

import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountDownTimeReference
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import androidx.wear.watchface.complications.datasource.ComplicationDataTimeline
import androidx.wear.watchface.complications.datasource.TimeInterval
import androidx.wear.watchface.complications.datasource.TimelineEntry
import io.github.jnbp.nextstop.wear.R
import io.github.jnbp.nextstop.wear.data.WatchStore
import io.github.jnbp.nextstop.wear.ui.clock
import java.time.Instant
import java.util.concurrent.TimeUnit

/*
 * What one complication shows, once for every type a watch face may ask for: a Slide holds the
 * texts (short and long), an optional countdown, the progress for a ring, the icon and the tap
 * action, and toData turns it into the ComplicationData of the requested type. A list of
 * slides with their start times becomes a timeline (timelineOf), so the face moves on to the
 * next departure by itself.
 *
 * A countdown in a long slot is a TimeDifferenceComplicationText: the watch face counts it
 * down itself ("12 Min."). A short slot and a ring have no room for that unit (in German the
 * face cut "12 Min." down to "Min."), so there the countdown is the app's own short form
 * ("12'", "0'" for now, the clock time an hour ahead) in a timeline of one entry per minute
 * (countdownSegments): the face moves along it by itself, without asking the app again. In a
 * text the countdown stands at COUNTDOWN ("^1", the placeholder of the library).
 */

/** The placeholder of the countdown in [Slide.text] and [Slide.longText]. */
const val COUNTDOWN = "^1"

/** What a complication shows at one time; see the comment at the top of this file. */
data class Slide(
    /** The main text of a short slot; may hold [COUNTDOWN]. Up to [SHORT_LABEL_MAX] characters besides it. */
    val text: String,
    /** The small title above or below it in a short slot (a line); null for none. */
    val title: String? = null,
    /** The text of a long slot; may hold [COUNTDOWN]. */
    val longText: String = text,
    /** The title of a long slot; null for none. */
    val longTitle: String? = title,
    /** What a screen reader says. */
    val description: String,
    /** The time the countdown runs to (epoch ms); null without one. */
    val countdownTo: Long? = null,
    /** The value of a ring (RANGED_VALUE), 0..1; null where the slide has none (then 0). */
    val progress: Float? = null,
    /** Where set, the ring fills towards [countdownTo] over this time (ms, see departureRingValue) instead of [progress]. */
    val ringWindow: Long? = null,
    /**
     * A monochrome icon (drawable resource): the image of an icon slot; beside the text of a
     * short slot or a ring in place of [title] (see textSlotExtras), in a long slot with it.
     */
    val icon: Int? = null,
    /** What a tap opens. */
    val tap: PendingIntent? = null,
)

/** [text] of a long slot as complication text: with the system's countdown to [countdownTo] in words where it holds [COUNTDOWN]. */
private fun longComplicationText(text: String, countdownTo: Long?): ComplicationText {
    if (countdownTo == null || !text.contains(COUNTDOWN)) return PlainComplicationText.Builder(text).build()
    return TimeDifferenceComplicationText.Builder(
        TimeDifferenceStyle.SHORT_WORDS_SINGLE_UNIT,
        CountDownTimeReference(Instant.ofEpochMilli(countdownTo)),
    )
        .setText(text)
        .setDisplayAsNow(true)
        .setMinimumTimeUnit(TimeUnit.MINUTES)
        .build()
}

private fun plain(text: String): ComplicationText = PlainComplicationText.Builder(text).build()

/** True when the short text counts down: it holds [COUNTDOWN] and the slide has a time to count to. */
private val Slide.countsDown: Boolean get() = countdownTo != null && text.contains(COUNTDOWN)

/**
 * The countdown in the app's short form at [at] (epoch ms, see countdownLabel): [minutes] of
 * the stretch it is shown in where given ([known]), else as counted at [at].
 */
private fun countdownAt(context: Context, target: Long, at: Long, known: Boolean, minutes: Long?, slot: Boolean): String {
    val shown = if (known) minutes else countdownMinutes(target, at)
    val pattern = context.getString(R.string.minutes_short)
    // A short slot or a ring: "0'" for now, which fits beside the line (#235).
    if (slot) return slotCountdownLabel(shown, clock(target), pattern)
    return countdownLabel(shown, clock(target), context.getString(R.string.now), pattern)
}

/** The short text at [at]: its countdown as the app's short form (see [countdownAt]). */
private fun Slide.shortText(context: Context, at: Long, known: Boolean, minutes: Long?): ComplicationText {
    val target = countdownTo
    if (target == null || !countsDown) return plain(text)
    return plain(text.replace(COUNTDOWN, countdownAt(context, target, at, known, minutes, slot = true)))
}

/**
 * The long text: the system's countdown in words, or for a preview ([fixed]) the minutes at
 * [at] as plain text. A preview is drawn later, at a time of the watch face's choosing (the
 * editor of a face draws its own preview time), when a countdown has long run out ("Jetzt").
 */
private fun Slide.longSlotText(context: Context, at: Long, fixed: Boolean): ComplicationText {
    val target = countdownTo
    if (!fixed || target == null || !longText.contains(COUNTDOWN)) return longComplicationText(longText, target)
    return plain(longText.replace(COUNTDOWN, countdownAt(context, target, at, known = false, minutes = null, slot = false)))
}

/** The value of the ring at [at]: towards the countdown where the slide has a [Slide.ringWindow], else its progress. */
private fun Slide.ringValue(at: Long): Float {
    val target = countdownTo
    val window = ringWindow
    val value = if (target != null && window != null) departureRingValue(target, at, window) else progress ?: 0f
    return value.coerceIn(0f, 1f)
}

/**
 * [slide] as the data of [type] at [at] (epoch ms); null for a type this kind of slide cannot
 * fill. [segment]: the stretch of the countdown this data is shown in (short slot and ring).
 * [fixed]: a preview, whose texts must not change with the time it is drawn at.
 */
fun Slide.toData(
    context: Context,
    type: ComplicationType,
    at: Long = System.currentTimeMillis(),
    segment: CountdownSegment? = null,
    fixed: Boolean = false,
): ComplicationData? {
    fun imageOf(resource: Int?) = resource?.let { MonochromaticImage.Builder(Icon.createWithResource(context, it)).build() }
    // The app's icon by default, the slide's own (home, warning, state) only with the option (#282).
    val shownIcon = complicationIcon(WatchStore.stateIcons.value, icon, R.drawable.ic_launcher_monochrome)
    val image = imageOf(shownIcon)
    val spoken = plain(description)
    val short = shortText(context, at, segment != null, segment?.minutes)
    // A short slot and a ring: an icon or a title, never both (see textSlotExtras).
    val (slotIcon, slotTitle) = textSlotExtras(shownIcon, title, R.drawable.ic_launcher_monochrome)
    // A long slot has room for both, but not for the app's own icon beside a title.
    val longIcon = shownIcon.takeUnless { it == R.drawable.ic_launcher_monochrome && longTitle != null }
    return when (type) {
        ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(short, spoken)
            .apply {
                slotTitle?.let { setTitle(plain(it)) }
                imageOf(slotIcon)?.let { setMonochromaticImage(it) }
                tap?.let { setTapAction(it) }
            }
            .build()
        ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(longSlotText(context, at, fixed), spoken)
            .apply {
                longTitle?.let { setTitle(plain(it)) }
                imageOf(longIcon)?.let { setMonochromaticImage(it) }
                tap?.let { setTapAction(it) }
            }
            .build()
        ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(ringValue(at), 0f, 1f, spoken)
            .apply {
                setText(short)
                slotTitle?.let { setTitle(plain(it)) }
                imageOf(slotIcon)?.let { setMonochromaticImage(it) }
                tap?.let { setTapAction(it) }
            }
            .build()
        ComplicationType.MONOCHROMATIC_IMAGE -> image?.let {
            MonochromaticImageComplicationData.Builder(it, spoken).apply { tap?.let { action -> setTapAction(action) } }.build()
        }
        ComplicationType.SMALL_IMAGE -> shownIcon?.let {
            val small = SmallImage.Builder(Icon.createWithResource(context, it), SmallImageType.ICON).build()
            SmallImageComplicationData.Builder(small, spoken).apply { tap?.let { action -> setTapAction(action) } }.build()
        }
        else -> null
    }
}

/**
 * The [slides] as a timeline for [type] at [now]: the first slide from [now], each further one
 * from its start ([starts], epoch ms, see slideStarts) until the next one starts; the last
 * stays for [LAST_SLIDE_MILLIS] (by then the system has asked again). In a short slot and a
 * ring a slide that counts down is split into one entry per minute (countdownSegments), at
 * most [COUNTDOWN_SEGMENTS_MAX] in all. Null when no slide can fill [type].
 *
 * With an [expired] slide (#235) the last countdown ends once its run has gone (lastSlideEnd)
 * and [expired] follows; it is also the data the face shows outside every entry, so the face
 * never falls back to the first departure, long gone, nor keeps a "now" for hours.
 */
fun timelineOf(
    context: Context,
    slides: List<Slide>,
    starts: List<Long>,
    type: ComplicationType,
    now: Long = System.currentTimeMillis(),
    expired: Slide? = null,
): ComplicationDataTimeline? {
    val split = type == ComplicationType.SHORT_TEXT || type == ComplicationType.RANGED_VALUE
    val pieces = ArrayList<Pair<TimeInterval, ComplicationData>>()
    for (index in slides.indices) {
        val slide = slides[index]
        val start = if (index == 0) now else starts.getOrNull(index) ?: continue
        val target = slide.countdownTo
        val end = starts.getOrNull(index + 1)?.takeIf { index + 1 < slides.size }
            ?: lastSlideEnd(start, target?.takeIf { slide.countsDown || slide.longText.contains(COUNTDOWN) }, expired != null)
        if (end <= start) continue
        if (split && target != null && slide.countsDown) {
            // Room for the slides still to come: at least one entry each.
            val budget = (COUNTDOWN_SEGMENTS_MAX - pieces.size - (slides.size - index - 1)).coerceAtLeast(1)
            for (segment in countdownSegments(start, end, target, budget)) {
                val data = slide.toData(context, type, segment.start, segment) ?: break
                pieces.add(TimeInterval(Instant.ofEpochMilli(segment.start), Instant.ofEpochMilli(segment.end)) to data)
            }
        } else {
            val data = slide.toData(context, type, start) ?: continue
            pieces.add(TimeInterval(Instant.ofEpochMilli(start), Instant.ofEpochMilli(end)) to data)
        }
    }
    val after = expired?.toData(context, type)
    if (after != null) {
        // From where the last slide ends: the times shown have run out until new data comes.
        val from = pieces.lastOrNull()?.first?.end?.toEpochMilli() ?: now
        // The entry needs a data object of its own: the same instance as the default data and as
        // an entry makes the wire data contain itself, and turning it into a bundle recurses
        // until the stack overflows (crash, 19.11.2026).
        val afterEntry = expired?.toData(context, type) ?: after
        val entries = pieces.map { (interval, data) -> TimelineEntry(interval, data) } +
            TimelineEntry(TimeInterval(Instant.ofEpochMilli(from), Instant.ofEpochMilli(from + LAST_SLIDE_MILLIS)), afterEntry)
        return ComplicationDataTimeline(after, entries)
    }
    val first = pieces.firstOrNull()?.second ?: return null
    val entries = pieces.drop(1).map { (interval, data) -> TimelineEntry(interval, data) }
    return ComplicationDataTimeline(first, entries)
}
