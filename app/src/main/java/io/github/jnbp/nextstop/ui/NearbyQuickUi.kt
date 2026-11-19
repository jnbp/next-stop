package io.github.jnbp.nextstop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.Departure
import io.github.jnbp.nextstop.data.PlaceKind
import io.github.jnbp.nextstop.data.QUICK_STOPS_METERS
import io.github.jnbp.nextstop.data.QuickStop
import io.github.jnbp.nextstop.data.QuickStopCandidate
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.StopLine
import io.github.jnbp.nextstop.data.TransitSource
import io.github.jnbp.nextstop.data.WALK_DETOUR
import io.github.jnbp.nextstop.data.nearbyQuickStops
import io.github.jnbp.nextstop.data.shortStopName
import io.github.jnbp.nextstop.ui.theme.AppColors
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * "Von wo?": up to four stops near the user right under "Aktueller Standort" (#173), each
 * for a line no nearer one offers (see nearbyQuickStops). The stops come from those the app
 * already knows around the position (the map's nearby stops), else from one nearby request,
 * kept for that spot; the lines of a stop from the nearby departures where loaded, else asked
 * once per stop and kept for the process. Worked out once when the panel opens, not per key.
 */
internal object NearbyQuickCache {
    // The stops asked for around a spot of about 100 m.
    private var spot: Pair<Int, Int>? = null
    private var stops: List<Stop> = emptyList()
    private val lines = HashMap<String, List<StopLine>>()

    private fun spotOf(position: Pair<Double, Double>) = (position.first * 1000).toInt() to (position.second * 1000).toInt()

    /**
     * The quick picks for [position]: [known] stops (the map's nearby ones) where they are near
     * it, else those of one request (kept per spot); lines from [departures] (the nearby board),
     * else from the stop itself (kept). Null where nothing could be had.
     */
    suspend fun load(
        source: TransitSource,
        position: Pair<Double, Double>,
        known: List<Stop>,
        departures: List<Departure>,
    ): List<QuickStop> {
        val here = spotOf(position)
        val near = known.filter { it.kind == PlaceKind.Stop && it.position != null }
            .takeIf { list -> list.any { quickMeters(position, it.position!!) <= QUICK_STOPS_METERS } }
        val around = near ?: if (spot == here && stops.isNotEmpty()) {
            stops
        } else {
            source.nearbyStops(position.first, position.second, NEARBY_ASKED).filter { it.kind == PlaceKind.Stop }.also {
                spot = here
                stops = it
            }
        }
        val fromBoard = departures.filter { it.stop != null }.groupBy { it.stop!!.id }
            .mapValues { (_, list) -> list.map { StopLine(it.line, it.product) } }
        val candidates = around.mapNotNull { stop ->
            val at = stop.position ?: return@mapNotNull null
            val meters = quickMeters(position, at)
            // Not worth asking for the lines of a stop beyond the walk (see nearbyQuickStops).
            if (meters * WALK_DETOUR > QUICK_STOPS_METERS) return@mapNotNull null
            val stopLines = fromBoard[stop.id] ?: lines[stop.id] ?: try {
                source.stopLines(stop.id).also { lines[stop.id] = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            QuickStopCandidate(stop.copy(distanceMeters = meters), meters, stopLines)
        }
        return nearbyQuickStops(candidates)
    }

    // Stops asked for when none are known around the position.
    private const val NEARBY_ASKED = 10
}

/** Metres between two points as the crow flies. */
private fun quickMeters(from: Pair<Double, Double>, to: Pair<Double, Double>): Int {
    val lat1 = Math.toRadians(from.first)
    val lat2 = Math.toRadians(to.first)
    val dLat = lat2 - lat1
    val dLon = Math.toRadians(to.second - from.second)
    val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
    return (2 * 6_371_000.0 * asin(sqrt(a))).toInt()
}

// The badges of the lines that make a quick pick worth showing; more are counted.
private const val QUICK_BADGES = 5

/** One quick pick: the stop's name, "3′ zu Fuß" and the badges of the lines it adds. A tap takes it as the start. */
@Composable
internal fun NearbyQuickRow(quick: QuickStop, shape: Shape, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.Card)
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 52.dp)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PlaceSymbols(quick.stop)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = quick.stop.name.shortStopName(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.r6n_quick_walk, quick.walkMinutes.toInt()),
                color = AppColors.Muted,
                fontSize = 13.sp,
                maxLines = 1,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            quick.newLines.take(QUICK_BADGES).forEach { line -> LineBadge(line.line, line.product, minWidth = 30.dp) }
            val more = quick.newLines.size - QUICK_BADGES
            if (more > 0) {
                Text(text = stringResource(R.string.r6n_quick_more, more), color = AppColors.Muted, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
        }
    }
}
