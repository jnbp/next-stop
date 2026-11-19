package io.github.jnbp.nextstop.wear.complication

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.datasource.ComplicationDataTimeline
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingTimelineComplicationDataSourceService
import io.github.jnbp.nextstop.wear.R
import io.github.jnbp.nextstop.wear.data.WatchStore

/*
 * The services the watch faces bind to, one per complication (each has its entry in the
 * manifest with its label, icon, types and update period). They differ only in what they
 * show (ComplicationSources) and in their preview for the picker of the watch face. The
 * previews use made-up times and well-known lines and stations, never live data.
 */

/** The common part: loads the stored state, builds the slides and answers with a timeline for the type asked for. */
abstract class TransitComplicationService : SuspendingTimelineComplicationDataSourceService() {
    /** The slides to show now. */
    protected abstract suspend fun slides(): Slides

    /** What the picker of the watch face shows. */
    protected abstract fun preview(): Slide

    // Fixed texts: the picker and the editor of a face draw the preview at a time of their own.
    override fun getPreviewData(type: ComplicationType): ComplicationData? = preview().toData(this, type, fixed = true)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationDataTimeline? {
        WatchStore.load(this)
        val shown = slides()
        return timelineOf(this, shown.slides, shown.starts, request.complicationType, expired = shown.expired)
    }

    /** A preview of a departure of [line] towards [towards] leaving in a few minutes. */
    protected fun departurePreview(line: String, towards: String, stop: String): Slide {
        val time = System.currentTimeMillis() + PREVIEW_MINUTES * 60_000L
        return Slide(
            text = COUNTDOWN,
            title = line,
            longText = "$line ${getString(R.string.towards_arrow, towards)} · $COUNTDOWN",
            longTitle = stop,
            description = "$line $towards",
            countdownTo = time,
            ringWindow = RING_WINDOW_MILLIS,
        )
    }

    private companion object {
        // The previews count down from this many minutes.
        const val PREVIEW_MINUTES = 4L
    }
}

/** The next departures at the favourite stop. */
class FavoriteStopComplicationService : TransitComplicationService() {
    override suspend fun slides(): Slides = ComplicationSources.favorite(this)

    override fun preview(): Slide = departurePreview("S41", "Ring", "Hauptbahnhof")
}

/** The next departures at the nearest stop. */
class NearbyComplicationService : TransitComplicationService() {
    override suspend fun slides(): Slides = ComplicationSources.nearby(this)

    override fun preview(): Slide = departurePreview("M10", "Warschauer Str.", "Hauptbahnhof")
}

/** The trip companion: its ring of progress and the next step. */
class CompanionComplicationService : TransitComplicationService() {
    override suspend fun slides(): Slides = ComplicationSources.companion(this)

    // Waiting at the stop for the U2: the icon in place of the line (the app's, the state's where chosen, #253), the line in the long slot.
    override fun preview(): Slide = Slide(
        text = COUNTDOWN,
        longText = getString(R.string.step_board, "U2", "Alexanderplatz") + " · " + COUNTDOWN,
        longTitle = "Pankow",
        description = getString(R.string.r6cstate_wait, "U2"),
        countdownTo = System.currentTimeMillis() + 6 * 60_000L,
        progress = 0.2f,
        icon = tripIcons(WatchStore.stateIcons.value, tripGlyphIcon(tripGlyph(TripState.Wait, "subway")), R.drawable.ic_launcher_monochrome).complication,
    )
}

/** The next connection home. */
class HomeComplicationService : TransitComplicationService() {
    override suspend fun slides(): Slides = ComplicationSources.home(this)

    override fun preview(): Slide = Slide(
        text = COUNTDOWN,
        title = "U2›S41",
        longText = getString(R.string.complication_home_long, "U2 › S41", COUNTDOWN, "18:40"),
        longTitle = getString(R.string.complication_home_label),
        description = getString(R.string.complication_home_label),
        countdownTo = System.currentTimeMillis() + 7 * 60_000L,
        icon = R.drawable.ic_home,
    )
}

/** The disruptions at the favourite (else the nearest) stop. */
class DisruptionsComplicationService : TransitComplicationService() {
    override suspend fun slides(): Slides = ComplicationSources.disruptions(this)

    override fun preview(): Slide = Slide(
        text = "1",
        title = "Hbf",
        longText = getString(R.string.complication_disruptions_label),
        longTitle = "Hauptbahnhof",
        description = getString(R.string.complication_disruptions_label),
        icon = R.drawable.ic_warning,
    )
}

/** An icon on the watch face that opens the route search. */
class QuickStartComplicationService : TransitComplicationService() {
    override suspend fun slides(): Slides = ComplicationSources.quickStart(this)

    override fun preview(): Slide = ComplicationSources.quickStart(this).slides.first().copy(tap = null)
}
