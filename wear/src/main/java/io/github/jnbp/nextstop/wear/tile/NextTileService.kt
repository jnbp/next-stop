package io.github.jnbp.nextstop.wear.tile

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.Material3TileService
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import kotlin.coroutines.cancellation.CancellationException
import io.github.jnbp.nextstop.wear.R
import io.github.jnbp.nextstop.wear.data.DepartureCache
import io.github.jnbp.nextstop.wear.data.isBayProduct
import io.github.jnbp.nextstop.wear.data.plausibleWatchPlatform
import io.github.jnbp.nextstop.wear.data.Departure
import io.github.jnbp.nextstop.wear.data.StepKind
import io.github.jnbp.nextstop.wear.data.TransitRouter
import io.github.jnbp.nextstop.wear.data.TripSnapshot
import io.github.jnbp.nextstop.wear.data.WatchStore
import io.github.jnbp.nextstop.wear.data.getOffSoon
import io.github.jnbp.nextstop.wear.data.minutesUntil
import io.github.jnbp.nextstop.wear.data.shownPlaceName
import io.github.jnbp.nextstop.wear.data.watchShowsClock
import io.github.jnbp.nextstop.wear.ui.clock
import io.github.jnbp.nextstop.wear.ui.spanText
import io.github.jnbp.nextstop.wear.ui.MainActivity
import io.github.jnbp.nextstop.wear.ui.ageText
import io.github.jnbp.nextstop.wear.complication.countdownLabel
import io.github.jnbp.nextstop.wear.complication.countdownMinutes
import io.github.jnbp.nextstop.wear.complication.timelineDepartures

/**
 * The tile "Next departure": the next step of the followed trip (from the phone or the
 * watch) when there is one, else the next three departures of the first favourite stop
 * the phone sent. A tap opens the app. Drawn again every minute while it is shown.
 */
class NextTileService : Material3TileService() {
    override suspend fun MaterialScope.tileResponse(requestParams: RequestBuilders.TileRequest): TileBuilders.Tile {
        val service = this@NextTileService
        WatchStore.load(service)
        val trip = WatchStore.trip.value
        val now = System.currentTimeMillis()
        val open = ModifiersBuilders.Clickable.Builder()
            .setId("open")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(service.packageName)
                            .setClassName(MainActivity::class.java.name)
                            .build(),
                    )
                    .build(),
            )
            .build()

        val title: String
        val lines: List<String>
        // A trip of the phone that stopped writing is not shown frozen (as in the complications).
        if (trip.active && !trip.stale(now)) {
            title = service.getString(R.string.your_trip)
            lines = tripLines(service, trip)
        } else {
            val stop = WatchStore.tileStop()
            if (stop == null) {
                title = service.getString(R.string.tile_label)
                lines = listOf(service.getString(R.string.tile_empty))
            } else {
                title = shownPlaceName(stop)
                val departures = try {
                    // As the complications and the app: BVG for a stop of the VBB, else the phone or Transitous.
                    TransitRouter.departures(service, stop, results = 6).also { DepartureCache.put(service, stop.id, it) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                // Offline: the departures loaded last, with their age.
                val cached = if (departures == null) DepartureCache.get(service, stop.id) else null
                // Not cancelled and not gone: the departures kept offline may have left long ago.
                val shown = (departures ?: cached?.departures)?.let { timelineDepartures(it, now, max = 3) }
                lines = when {
                    shown == null -> listOf(service.getString(R.string.load_failed))
                    shown.isEmpty() -> listOf(service.getString(R.string.no_departures))
                    cached != null -> shown.map { departureLine(service, it, now) } + ageText(service, cached.timeMillis)
                    else -> shown.map { departureLine(service, it, now) }
                }
            }
        }

        // The whole content opens the app too (#270), not only the button at the edge.
        val column = LayoutElementBuilders.Column.Builder()
            .setWidth(DimensionBuilders.expand())
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(open).build())
        lines.forEachIndexed { index, line ->
            column.addContent(
                text(
                    text = line.layoutString,
                    typography = if (index == 0) Typography.TITLE_MEDIUM else Typography.BODY_MEDIUM,
                    maxLines = 2,
                ),
            )
        }
        val layout = primaryLayout(
            titleSlot = { text(title.layoutString) },
            mainSlot = { column.build() },
            bottomSlot = {
                textEdgeButton(onClick = open, labelContent = { text(service.getString(R.string.open).layoutString) })
            },
        )
        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(FRESHNESS_MILLIS)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            .build()
    }

    companion object {
        private const val RESOURCES_VERSION = "1"
        private const val FRESHNESS_MILLIS = 60_000L

        /** Asks the system to draw the tile again, after the phone or the watch changed the trip. */
        fun requestUpdate(context: Context) {
            try {
                TileService.getUpdater(context).requestUpdate(NextTileService::class.java)
            } catch (e: Exception) {
                // No tile host (an emulator without tiles): nothing to update.
            }
        }
    }
}

/** "M10 12' Warschauer Str.": the minutes as the app shows them ("Jetzt", the clock time from an hour on). */
private fun departureLine(context: Context, departure: Departure, now: Long): String {
    val time = departure.time ?: now
    val minutes = countdownLabel(
        countdownMinutes(time, now),
        clock(time),
        context.getString(R.string.now),
        context.getString(R.string.minutes_short),
    )
    return "${departure.line} $minutes ${departure.direction}"
}

/** The trip in a few short lines: minutes and line, what to do, and the hint or the stops left. */
internal fun tripLines(context: Context, trip: TripSnapshot): List<String> {
    val minutes = minutesUntil(trip.time)
    // From an hour on the clock time instead of the minutes (#148b, as on the phone).
    val count = if (watchShowsClock(trip.time - System.currentTimeMillis())) clock(trip.time) else "$minutes'"
    val head = listOfNotNull(trip.line?.takeIf { (trip.kind == StepKind.Board && !trip.leave) || trip.kind == StepKind.GetOff }, count)
        .joinToString(" · ")
    val step = when (trip.kind) {
        StepKind.Board -> if (trip.leave) context.getString(R.string.step_leave, clock(trip.time)) else context.getString(R.string.step_board, trip.line ?: "", trip.place)
        StepKind.GetOff -> context.getString(R.string.step_get_off, trip.place)
        StepKind.WalkToEnd -> context.getString(if (trip.walk?.cycling == true) R.string.step_cycle else R.string.step_walk, trip.place)
        StepKind.Arrived -> context.getString(R.string.arrived)
    }
    val extra = when {
        trip.kind == StepKind.GetOff && trip.stopsLeft == 1 && !getOffSoon(trip.time, System.currentTimeMillis()) ->
            context.getString(
                // A change says so, as on the trip screen.
                if (trip.change) R.string.change_in_at else R.string.get_off_in_at,
                spanText(context, minutesUntil(trip.time)),
                clock(trip.time),
            )
        trip.kind == StepKind.GetOff && trip.stopsLeft == 1 -> context.getString(R.string.next_stop_get_off)
        trip.kind == StepKind.GetOff && trip.stopsLeft > 1 -> context.getString(R.string.stops_left, trip.stopsLeft)
        else -> trip.hint ?: plausibleWatchPlatform(trip.platform, trip.product)?.let { (shown, numbered) ->
            when {
                !numbered -> shown
                isBayProduct(trip.product) -> context.getString(R.string.platform_bay, shown)
                else -> context.getString(R.string.platform, shown)
            }
        }
    }
    return listOfNotNull(head, step, extra)
}
