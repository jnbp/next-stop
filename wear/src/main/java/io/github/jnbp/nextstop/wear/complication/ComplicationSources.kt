package io.github.jnbp.nextstop.wear.complication

import android.content.Context
import android.util.Log
import kotlinx.coroutines.TimeoutCancellationException
import kotlin.coroutines.cancellation.CancellationException
import io.github.jnbp.nextstop.wear.R
import io.github.jnbp.nextstop.wear.data.BvgClient
import io.github.jnbp.nextstop.wear.data.Departure
import io.github.jnbp.nextstop.wear.data.DepartureCache
import io.github.jnbp.nextstop.wear.data.Journey
import io.github.jnbp.nextstop.wear.data.LOG_TAG
import io.github.jnbp.nextstop.wear.data.Place
import io.github.jnbp.nextstop.wear.data.StepKind
import io.github.jnbp.nextstop.wear.data.TransitRouter
import io.github.jnbp.nextstop.wear.data.TripSnapshot
import io.github.jnbp.nextstop.wear.data.WatchStore
import io.github.jnbp.nextstop.wear.data.shownTime
import io.github.jnbp.nextstop.wear.data.shownTimeText
import io.github.jnbp.nextstop.wear.data.currentPosition
import io.github.jnbp.nextstop.wear.data.shownPlaceName
import io.github.jnbp.nextstop.wear.data.stopNameParts
import io.github.jnbp.nextstop.wear.data.withHardTimeout
import io.github.jnbp.nextstop.wear.data.withoutCancelledVariants
import io.github.jnbp.nextstop.wear.tile.tripLines
import io.github.jnbp.nextstop.wear.ui.OpenRequest
import io.github.jnbp.nextstop.wear.ui.clock

/*
 * What each complication shows, as slides (see ComplicationSlides.kt). The loads are the ones
 * of the app (TransitRouter, BvgClient, the departures kept in DepartureCache when offline),
 * each given at most LOAD_TIMEOUT_MILLIS: a complication that hangs keeps the watch awake.
 */

/**
 * The slides of a complication with the time each starts (see slideStarts), and [expired]: what
 * it shows once the times of the last slide have run out and no new data came (#235); null
 * where the last slide may stay (see timelineOf).
 */
class Slides(val slides: List<Slide>, val starts: List<Long>, val expired: Slide? = null) {
    /** True when the slides count down to times (only those have an [expired] slide). */
    val counting: Boolean get() = expired != null

    companion object {
        /** One slide, shown until the next update. */
        fun one(slide: Slide) = Slides(listOf(slide), listOf(Long.MIN_VALUE))
    }
}

/** [block] with at most [LOAD_TIMEOUT_MILLIS]; null (logged as [what]) when it fails or takes longer. */
internal suspend fun <T> loadOrNull(what: String, block: suspend () -> T): T? = try {
    withHardTimeout(LOAD_TIMEOUT_MILLIS) { block() }
} catch (e: TimeoutCancellationException) {
    Log.d(LOG_TAG, "complication: $what timed out")
    null
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Log.d(LOG_TAG, "complication: $what failed: $e")
    null
}

object ComplicationSources {
    private val client = BvgClient()

    /**
     * Automatic: the companion while a trip runs, else the departures of the nearest stop (the
     * favourite stop without a position), else (#235: the nearest has none to show) those of
     * the favourite stop where it has any; else the state of the nearest with its icon.
     */
    suspend fun auto(context: Context, now: Long = System.currentTimeMillis()): Slides {
        val trip = WatchStore.trip.value
        if (autoMode(trip.active, trip.kind == StepKind.Arrived, trip.stale(now)) == AutoMode.Trip) {
            return Slides.one(tripSlide(context, trip, ComplicationKind.Auto))
        }
        val favorite = WatchStore.tileStop()
        val stop = NearestStop.find(context) ?: favorite
            ?: return Slides.one(noPositionSlide(context, ComplicationKind.Auto))
        val nearest = departures(context, stop, ComplicationKind.Auto)
        if (!autoTriesFavorite(nearest.counting, stop.id, favorite?.id) || favorite == null) return nearest
        val other = departures(context, favorite, ComplicationKind.Auto)
        return if (other.counting) other else nearest
    }

    /** The favourite stop's next departures; a hint to add one when there is none. */
    suspend fun favorite(context: Context): Slides {
        val stop = WatchStore.tileStop() ?: return Slides.one(
            Slide(
                text = DASH,
                longText = context.getString(R.string.tile_empty),
                description = context.getString(R.string.tile_empty),
                // Not a lone dash: the icon tells which slot waits for a stop.
                icon = R.drawable.ic_place,
                tap = Complications.tap(context, ComplicationKind.Favorite, null),
            ),
        )
        return departures(context, stop, ComplicationKind.Favorite)
    }

    /** The nearest stop's next departures. */
    suspend fun nearby(context: Context): Slides {
        val stop = NearestStop.find(context) ?: return Slides.one(noPositionSlide(context, ComplicationKind.Nearby))
        return departures(context, stop, ComplicationKind.Nearby)
    }

    /** The running trip with its progress; "no trip" without one. */
    fun companion(context: Context, now: Long = System.currentTimeMillis()): Slides {
        val trip = WatchStore.trip.value
        if (autoMode(trip.active, trip.kind == StepKind.Arrived, trip.stale(now)) == AutoMode.Trip) {
            return Slides.one(tripSlide(context, trip, ComplicationKind.Trip))
        }
        val none = context.getString(R.string.complication_no_trip)
        return Slides.one(
            Slide(
                text = DASH,
                longText = none,
                description = none,
                progress = 0f,
                icon = R.drawable.ic_launcher_monochrome,
                tap = Complications.tap(context, ComplicationKind.Trip, null),
            ),
        )
    }

    /** The next connections from where the user is to home (the destination of the tile "To a favourite"). */
    suspend fun home(context: Context, now: Long = System.currentTimeMillis()): Slides {
        val target = WatchStore.tileTarget(context)
        if (target == null) {
            val choose = context.getString(R.string.favorite_tile_empty)
            return Slides.one(Slide(text = DASH, longText = choose, description = choose, icon = R.drawable.ic_home, tap = Complications.tap(context, ComplicationKind.Home, OpenRequest.TileTarget)))
        }
        val tap = Complications.tap(context, ComplicationKind.Home, OpenRequest.Results(target))
        val name = shortLabel(shownPlaceName(target), LONG_TITLE_MAX)
        val position = currentPosition(context, waitForFix = false)
            ?: return Slides.one(messageSlide(context.getString(R.string.no_position_fix), name, R.drawable.ic_home, tap))
        val journeys = loadOrNull("journeys home") {
            client.journeysFrom(
                position.latitude,
                position.longitude,
                context.getString(R.string.my_position),
                target,
                results = HOME_JOURNEYS,
                comfort = WatchStore.comfort.value,
            )
        }
            // A cancelled way to a train another connection rides is no slide of its own (#233).
            ?.let { withoutCancelledVariants(it) }
            ?.filter { it.departure >= now - GONE_GRACE_MILLIS }
            ?.sortedBy { it.departure }
            .orEmpty()
        if (journeys.isEmpty()) return Slides.one(messageSlide(context.getString(R.string.complication_failed), name, R.drawable.ic_home, tap))
        val slides = journeys.map { journeySlide(context, it, name, tap) }
        return Slides(slides, slideStarts(journeys.map { it.departure }), expiredSlide(context, name, R.drawable.ic_home, tap))
    }

    /** The disruptions at the favourite stop, else at the nearest one: the count, and the first notice in a long slot. */
    suspend fun disruptions(context: Context): Slides {
        val kind = ComplicationKind.Disruptions
        val stop = WatchStore.tileStop() ?: NearestStop.find(context) ?: return Slides.one(
            Slide(
                text = DASH,
                longText = context.getString(R.string.tile_empty),
                description = context.getString(R.string.tile_empty),
                icon = R.drawable.ic_warning,
                tap = Complications.tap(context, kind, null),
            ),
        )
        val tap = Complications.tap(context, kind, OpenRequest.Departures(stop))
        val name = stopNameParts(stop.name).title
        // Only a fresh load: the departures kept offline carry no notices.
        val departures = loadOrNull("disruptions of ${stop.id}") { TransitRouter.departures(context, stop, results = DISRUPTION_DEPARTURES) }
            ?: return Slides.one(messageSlide(context.getString(R.string.complication_failed), shortLabel(name, LONG_TITLE_MAX), R.drawable.ic_warning, tap))
        val summary = disruptionSummary(departures)
        val long = when {
            summary.clear -> context.getString(R.string.complication_no_disruptions)
            summary.notices.isNotEmpty() -> shortLabel(summary.notices.first(), NOTICE_MAX) +
                (summary.count - 1).let { more -> if (more > 0) " (+$more)" else "" }
            else -> context.resources.getQuantityString(R.plurals.complication_cancelled, summary.cancelled, summary.cancelled)
        }
        return Slides.one(
            Slide(
                text = summary.count.toString(),
                // A stop name in a short slot is compact, never cut (#252).
                title = slotStopName(name).ifEmpty { null },
                longText = long,
                longTitle = shortLabel(name, LONG_TITLE_MAX),
                description = "$name: $long",
                icon = if (summary.clear) R.drawable.ic_check else R.drawable.ic_warning,
                tap = tap,
            ),
        )
    }

    /** The quick start: an icon (and "Route") that opens the route search. */
    fun quickStart(context: Context): Slides {
        val label = context.getString(R.string.complication_quick_long)
        return Slides.one(
            Slide(
                text = shortLabel(context.getString(R.string.route_search)),
                longText = label,
                description = label,
                icon = R.drawable.ic_route,
                tap = Complications.tap(context, ComplicationKind.QuickStart, OpenRequest.Search),
            ),
        )
    }

    /**
     * The next departures of [stop] as a timeline: those the app kept moments ago (keptIsFresh),
     * else loaded now (kept in DepartureCache), else the departures kept from earlier that have
     * not left yet; "no departures" when none is left (see emptyBoard). Only a timeline with
     * departures has an [Slides.expired] slide.
     */
    private suspend fun departures(context: Context, stop: Place, kind: ComplicationKind, now: Long = System.currentTimeMillis()): Slides {
        val tap = Complications.tap(context, kind, OpenRequest.Departures(stop))
        // What the app loaded moments ago for this stop is shown as it is: the same board, no request (#235).
        val kept = DepartureCache.get(context, stop.id)?.takeIf { keptIsFresh(it.timeMillis, now) }?.departures
        val loaded = kept ?: loadOrNull("departures of ${stop.id}") { TransitRouter.departures(context, stop, results = STOP_DEPARTURES) }
        if (loaded != null && kept == null) DepartureCache.put(context, stop.id, loaded)
        val all = loaded ?: DepartureCache.get(context, stop.id)?.departures.orEmpty()
        val shown = timelineDepartures(all, now)
        val name = stopNameParts(stop.name).title
        if (shown.isEmpty()) {
            // A failed load is never "no departures" (#235); the slot shows the stop with an icon of the state, not a lone dash.
            val state = emptyBoard(loaded != null)
            val none = context.getString(if (state == EmptyBoard.Failed) R.string.complication_failed else R.string.no_departures)
            val icon = if (state == EmptyBoard.Failed) R.drawable.ic_refresh else R.drawable.ic_schedule
            return Slides.one(messageSlide(none, shortLabel(name, LONG_TITLE_MAX), icon, tap, shortText = slotStopName(name)))
        }
        val starts = slideStarts(shown.map { it.time ?: now })
        val slides = shown.map { departure -> departureSlide(context, name, departure, now, tap) }
        return Slides(slides, starts, expiredSlide(context, name, R.drawable.ic_refresh, tap))
    }

    /**
     * What a timeline shows once its last departure has gone and no new data came (#235): no
     * time at all, only [name] (the stop or the destination, compact: slotStopName, #252) beside
     * [icon], or the icon alone where the name has no compact form, and in a long slot that a
     * tap shows the current times; never a run that has left long ago.
     */
    private fun expiredSlide(context: Context, name: String, icon: Int, tap: android.app.PendingIntent): Slide {
        val hint = context.getString(R.string.r7wear_tap_for_current)
        return Slide(
            text = slotStopName(name),
            longText = hint,
            longTitle = shortLabel(name, LONG_TITLE_MAX).ifEmpty { null },
            description = listOf(name, hint).filter { it.isNotBlank() }.joinToString(": "),
            progress = 0f,
            icon = icon,
            tap = tap,
        )
    }

    private fun departureSlide(context: Context, stopName: String, departure: Departure, now: Long, tap: android.app.PendingIntent): Slide {
        val time = departure.time ?: now
        val towards = context.getString(R.string.towards_arrow, departure.direction)
        val delay = departure.delayMinutes?.takeIf { it > 0 }?.let { " +$it" }.orEmpty()
        return Slide(
            text = COUNTDOWN,
            title = shortLabel(departure.line),
            longText = "${departure.line} $towards · $COUNTDOWN$delay",
            longTitle = shortLabel(stopName, LONG_TITLE_MAX),
            // The clock time as the phone's delay setting says (#227b); the countdown keeps its "+2".
            description = "${departure.line} $towards, ${departureClockText(departure, time)}, $stopName",
            countdownTo = time,
            ringWindow = RING_WINDOW_MILLIS,
            tap = tap,
        )
    }

    private fun journeySlide(context: Context, journey: Journey, targetName: String, tap: android.app.PendingIntent): Slide {
        val lines = journey.rides.mapNotNull { it.line }
        val onFoot = context.getString(R.string.r6comp_on_foot)
        val chain = lineChain(lines).ifEmpty { onFoot }
        val long = context.getString(R.string.complication_home_long, lineChain(lines, max = LONG_CHAIN_LINES).ifEmpty { onFoot }, COUNTDOWN, clock(journey.arrival))
        return Slide(
            text = COUNTDOWN,
            title = shortLabel(chain),
            longText = long,
            longTitle = targetName,
            description = "$targetName: " + long.replace(COUNTDOWN, clock(journey.departure)),
            countdownTo = journey.departure,
            icon = R.drawable.ic_home,
            tap = tap,
        )
    }

    /**
     * The trip as one slide: what to do next with a countdown, the progress for a ring. In a
     * short slot and a ring an icon stands in place of the line, which stays in the long slot:
     * the app's icon, or with the option (WatchStore.stateIcons, #253) the icon of the state
     * (waiting, riding, getting off, changing, on foot).
     */
    private fun tripSlide(context: Context, trip: TripSnapshot, kind: ComplicationKind): Slide {
        val state = tripState(trip)
        val lines = tripLines(context, trip)
        val step = lines.getOrNull(1) ?: trip.destination
        // Riding, the step names only the stop to get off at: the line goes in front of it.
        val line = trip.line?.takeIf { trip.kind == StepKind.GetOff }
        val minutes = countdownMinutes(trip.time, System.currentTimeMillis())
        val spoken = listOfNotNull(
            tripStateLabel(context, state, trip.line) +
                (minutes?.let { ", " + context.resources.getQuantityString(R.plurals.r6cstate_minutes_left, it.toInt(), it.toInt()) } ?: ""),
            step,
            lines.getOrNull(2),
        ).joinToString(". ")
        return Slide(
            text = COUNTDOWN,
            title = null,
            longText = listOfNotNull(line, step, COUNTDOWN).joinToString(" · "),
            longTitle = shortLabel(trip.destination, LONG_TITLE_MAX).ifEmpty { null },
            description = spoken,
            countdownTo = trip.time,
            progress = trip.progress,
            // The app's icon by default, the state's where chosen (#253, #282; the ongoing activity keeps the app's).
            icon = tripIcons(WatchStore.stateIcons.value, tripGlyphIcon(tripGlyph(state, trip.product)), R.drawable.ic_launcher_monochrome).complication,
            tap = Complications.tap(context, kind, OpenRequest.Trip),
        )
    }

    private fun noPositionSlide(context: Context, kind: ComplicationKind): Slide =
        messageSlide(context.getString(R.string.no_position_fix), null, R.drawable.ic_place, Complications.tap(context, kind, OpenRequest.Nearby))

    /**
     * A slide that only says [message] (in a long slot; [shortText] beside the icon in a short
     * one, "" for the icon alone, see slotStopName; a dash where none is given).
     */
    private fun messageSlide(message: String, longTitle: String?, icon: Int?, tap: android.app.PendingIntent, shortText: String? = null): Slide =
        Slide(
            text = shortText?.takeIf { it.isNotEmpty() || icon != null } ?: DASH,
            title = null,
            longText = message,
            longTitle = longTitle,
            description = listOfNotNull(longTitle, message).joinToString(": "),
            progress = 0f,
            icon = icon,
            tap = tap,
        )

    private const val DASH = "–"

    // Departures loaded for a timeline (some are cancelled or gone), and for the disruptions (more runs, more notices).
    private const val STOP_DEPARTURES = 8
    private const val DISRUPTION_DEPARTURES = 12

    // Connections home in one timeline: the next and the two after it.
    private const val HOME_JOURNEYS = 3

    // A long slot has room for this much of a title, a notice and of lines.
    private const val LONG_TITLE_MAX = 24
    private const val NOTICE_MAX = 60
    private const val LONG_CHAIN_LINES = 3
}

// The most a complication waits for the network (ms) before it shows what it kept.
private const val LOAD_TIMEOUT_MILLIS = 15_000L

/** The drawable of [glyph] (monochrome, tinted by the watch face). */
internal fun tripGlyphIcon(glyph: TripGlyph): Int = when (glyph) {
    TripGlyph.Hourglass -> R.drawable.ic_state_wait
    TripGlyph.Bus -> R.drawable.ic_state_bus
    TripGlyph.Tram -> R.drawable.ic_state_tram
    TripGlyph.Train -> R.drawable.ic_state_train
    TripGlyph.Exit -> R.drawable.ic_state_get_off
    TripGlyph.Change -> R.drawable.ic_state_change
    TripGlyph.Walk -> R.drawable.ic_walk
    TripGlyph.Bike -> R.drawable.ic_state_bike
    TripGlyph.Check -> R.drawable.ic_check
}

/** What a screen reader says for [state] ("Warten auf U3"); [line] where known. */
internal fun tripStateLabel(context: Context, state: TripState, line: String?): String = when (state) {
    TripState.Wait -> line?.let { context.getString(R.string.r6cstate_wait, it) } ?: context.getString(R.string.r6cstate_wait_plain)
    TripState.Ride -> line?.let { context.getString(R.string.r6cstate_ride, it) } ?: context.getString(R.string.r6cstate_ride_plain)
    TripState.GetOff -> context.getString(R.string.r6cstate_get_off)
    TripState.Change -> context.getString(R.string.r6cstate_change)
    TripState.Walk -> context.getString(R.string.r6cstate_walk)
    TripState.Cycle -> context.getString(R.string.r6cstate_cycle)
    TripState.Arrived -> context.getString(R.string.r6cstate_arrived)
}

/** "12:09 +2" of a departure as the phone's delay setting says (#227b), in plain text. */
private fun departureClockText(departure: Departure, time: Long): String {
    val delay = (departure.delayMinutes ?: 0).toLong()
    val shown = shownTime(WatchStore.places.value.phoneDelayDisplay, time, departure.plannedTime ?: time - delay * 60_000L, departure.delayMinutes != null, delay)
    return shownTimeText(shown, ::clock) { "%+d".format(it) }
}
