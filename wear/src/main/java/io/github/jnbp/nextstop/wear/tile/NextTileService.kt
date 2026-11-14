package io.github.jnbp.nextstop.wear.tile

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
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
import io.github.jnbp.nextstop.wear.data.BvgClient
import io.github.jnbp.nextstop.wear.data.DepartureCache
import io.github.jnbp.nextstop.wear.data.Departure
import io.github.jnbp.nextstop.wear.data.StepKind
import io.github.jnbp.nextstop.wear.data.TripSnapshot
import io.github.jnbp.nextstop.wear.data.WatchStore
import io.github.jnbp.nextstop.wear.data.minutesUntil
import io.github.jnbp.nextstop.wear.ui.MainActivity
import io.github.jnbp.nextstop.wear.ui.ageText

/**
 * The tile "Next departure": the next step of the followed trip (from the phone or the
 * watch) when there is one, else the next three departures of the first favourite stop
 * the phone sent. A tap opens the app. Drawn again every minute while it is shown.
 */
class NextTileService : Material3TileService() {
    private val client = BvgClient()

    override suspend fun MaterialScope.tileResponse(requestParams: RequestBuilders.TileRequest): TileBuilders.Tile {
        val service = this@NextTileService
        WatchStore.load(service)
        val trip = WatchStore.trip.value
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
        if (trip.active) {
            title = service.getString(R.string.your_trip)
            lines = tripLines(service, trip)
        } else {
            val stop = WatchStore.tileStop()
            if (stop == null) {
                title = service.getString(R.string.tile_label)
                lines = listOf(service.getString(R.string.tile_empty))
            } else {
                title = stop.name
                val departures = try {
                    client.departures(stop.id, results = 6).also { DepartureCache.put(service, stop.id, it) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                // Offline: the departures loaded last, with their age.
                val cached = if (departures == null) DepartureCache.get(service, stop.id) else null
                val shown = (departures ?: cached?.departures)?.filter { !it.cancelled }?.take(3)
                lines = when {
                    shown == null -> listOf(service.getString(R.string.load_failed))
                    shown.isEmpty() -> listOf(service.getString(R.string.no_departures))
                    cached != null -> shown.map { departureLine(it) } + ageText(service, cached.timeMillis)
                    else -> shown.map { departureLine(it) }
                }
            }
        }

        val column = LayoutElementBuilders.Column.Builder()
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

private fun departureLine(departure: Departure): String {
    val minutes = departure.time?.let { minutesUntil(it) } ?: 0
    return "${departure.line} ${minutes}' ${departure.direction}"
}

/** The trip in a few short lines: minutes and line, what to do, and the hint or the stops left. */
internal fun tripLines(context: Context, trip: TripSnapshot): List<String> {
    val minutes = minutesUntil(trip.time)
    val head = listOfNotNull(trip.line?.takeIf { trip.kind == StepKind.Board || trip.kind == StepKind.GetOff }, "$minutes'")
        .joinToString(" · ")
    val step = when (trip.kind) {
        StepKind.Board -> context.getString(R.string.step_board, trip.line ?: "", trip.place)
        StepKind.GetOff -> context.getString(R.string.step_get_off, trip.place)
        StepKind.WalkToEnd -> context.getString(R.string.step_walk, trip.place)
        StepKind.Arrived -> context.getString(R.string.arrived)
    }
    val extra = when {
        trip.kind == StepKind.GetOff && trip.stopsLeft == 1 -> context.getString(R.string.next_stop_get_off)
        trip.kind == StepKind.GetOff && trip.stopsLeft > 1 -> context.getString(R.string.stops_left, trip.stopsLeft)
        else -> trip.hint ?: trip.platform?.let { context.getString(R.string.platform, it) }
    }
    return listOfNotNull(head, step, extra)
}
