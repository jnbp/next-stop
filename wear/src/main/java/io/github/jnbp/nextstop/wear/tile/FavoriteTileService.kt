package io.github.jnbp.nextstop.wear.tile

import android.content.Context
import android.util.Log
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
import io.github.jnbp.nextstop.wear.data.Journey
import io.github.jnbp.nextstop.wear.data.LOG_TAG
import io.github.jnbp.nextstop.wear.data.WatchStore
import io.github.jnbp.nextstop.wear.data.currentPosition
import io.github.jnbp.nextstop.wear.data.minutesUntil
import io.github.jnbp.nextstop.wear.ui.MainActivity
import io.github.jnbp.nextstop.wear.ui.OpenRequest
import io.github.jnbp.nextstop.wear.ui.clock

/**
 * The tile "To a favourite" ("Nach Hause"): the next connection from where the watch (or
 * the phone) is to the destination chosen in the app (or the favourite marked as home).
 * The button opens all connections; without a destination it opens the page to choose one.
 */
class FavoriteTileService : Material3TileService() {
    private val client = BvgClient()

    override suspend fun MaterialScope.tileResponse(requestParams: RequestBuilders.TileRequest): TileBuilders.Tile {
        val service = this@FavoriteTileService
        WatchStore.load(service)
        val target = WatchStore.tileTarget(service)
        val title: String
        val lines: List<String>
        val button: String
        val request: OpenRequest
        if (target == null) {
            title = service.getString(R.string.favorite_tile_label)
            lines = listOf(service.getString(R.string.favorite_tile_empty))
            button = service.getString(R.string.choose)
            request = OpenRequest.TileTarget
        } else {
            title = target.name
            button = service.getString(R.string.open)
            request = OpenRequest.Results(target)
            val position = currentPosition(service, waitForFix = false)
            lines = if (position == null) {
                listOf(service.getString(R.string.no_position_fix))
            } else {
                val journey = try {
                    client.journeysFrom(
                        position.latitude,
                        position.longitude,
                        service.getString(R.string.my_position),
                        target,
                        results = 1,
                        comfort = WatchStore.comfort.value,
                    )
                        .firstOrNull()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.d(LOG_TAG, "favourite tile: journeys failed: $e")
                    null
                }
                if (journey == null) listOf(service.getString(R.string.load_failed)) else journeyLines(service, journey)
            }
        }

        val clickable = ModifiersBuilders.Clickable.Builder()
            .setId("open")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(service.packageName)
                            .setClassName(MainActivity::class.java.name)
                            .apply {
                                OpenRequest.extras(request).forEach { (key, value) ->
                                    addKeyToExtraMapping(key, ActionBuilders.AndroidStringExtra.Builder().setValue(value).build())
                                }
                            }
                            .build(),
                    )
                    .build(),
            )
            .build()
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
            bottomSlot = { textEdgeButton(onClick = clickable, labelContent = { text(button.layoutString) }) },
        )
        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(FRESHNESS_MILLIS)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            .build()
    }

    companion object {
        private const val RESOURCES_VERSION = "1"

        // A journey search is heavier than departures: every two minutes while shown.
        private const val FRESHNESS_MILLIS = 120_000L

        /** Asks the system to draw the tile again, after its destination changed. */
        fun requestUpdate(context: Context) {
            try {
                TileService.getUpdater(context).requestUpdate(FavoriteTileService::class.java)
            } catch (e: Exception) {
                // No tile host: nothing to update.
            }
        }
    }
}

/** The next connection in short lines: lines and minutes, the times, the changes. */
private fun journeyLines(context: Context, journey: Journey): List<String> {
    val minutes = minutesUntil(journey.departure)
    val rides = journey.rides.mapNotNull { it.line }.take(3).joinToString(" › ")
    val head = listOf(rides.ifEmpty { "🚶" }, "$minutes'").joinToString(" · ")
    val times = "${clock(journey.departure)}–${clock(journey.arrival)}"
    val changes = if (journey.transfers == 0) context.getString(R.string.direct) else context.getString(R.string.changes, journey.transfers)
    return listOf(head, times, changes)
}
