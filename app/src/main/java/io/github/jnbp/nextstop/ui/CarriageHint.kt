package io.github.jnbp.nextstop.ui

import android.content.Context
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.hypot
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.BoardingEstimate
import io.github.jnbp.nextstop.data.BoardingGeometry
import io.github.jnbp.nextstop.data.ChangePosition
import io.github.jnbp.nextstop.data.ChangePositions
import io.github.jnbp.nextstop.data.ChangeQuery
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.Leg
import io.github.jnbp.nextstop.data.OsmPlatform
import io.github.jnbp.nextstop.data.OsmStation
import io.github.jnbp.nextstop.data.OsmStations
import io.github.jnbp.nextstop.data.PlatformChange
import io.github.jnbp.nextstop.data.SAME_SPOT_METERS
import io.github.jnbp.nextstop.data.Stopover
import io.github.jnbp.nextstop.data.normalStationId
import io.github.jnbp.nextstop.data.samePlatformByNumbers
import io.github.jnbp.nextstop.data.samePlatformRecordAllowed
import io.github.jnbp.nextstop.data.stationNameKey
import io.github.jnbp.nextstop.data.stopSpotMeters
import io.github.jnbp.nextstop.data.stopSpotNeed
import io.github.jnbp.nextstop.ui.theme.AppColors

// The dataset only knows trains. Buses, trams and ferries are left out; a ride whose provider
// names no product is asked all the same, as the dataset only holds lines of trains.
private val ROAD_PRODUCTS = setOf("bus", "coach", "tram", "ferry")

/** Whether boarding hints are shown at all; follows the setting and is set from it at start. */
val boardingHintsOn = mutableStateOf(true)

/**
 * A change between two trains as the dataset of change positions asks for it: the station
 * and line the traveller arrives with and the station before, the station and line to
 * change to and the station after. [arriving] and [next] are the two rides, also with a walk
 * between them. [arrivingStops] and [nextStops] are the stops of both rides from boarding to
 * getting off, by default those the journey brought; while they are unknown the stations
 * before and after are guessed. [arrivingBeyond] is the stop the arriving vehicle calls at
 * after the change and [nextBefore] the one the departing vehicle comes from, where the runs
 * of the vehicles are known: they settle the direction when the stops in between are not.
 * Null for buses and trams.
 */
fun changeQuery(
    arriving: Leg,
    next: Leg,
    arrivingStops: List<Stopover> = arriving.stopovers,
    nextStops: List<Stopover> = next.stopovers,
    arrivingBeyond: Stopover? = null,
    nextBefore: Stopover? = null,
): ChangeQuery? {
    if (arriving.walking || next.walking) return null
    if (arriving.product in ROAD_PRODUCTS || next.product in ROAD_PRODUCTS) return null
    val fromLine = arriving.line?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val toLine = next.line?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    // The stops of a ride run from where the traveller boards to where they get off.
    val fromStop = arriving.toPlace ?: arrivingStops.lastOrNull()?.place
    val toStop = next.fromPlace ?: nextStops.firstOrNull()?.place
    val fromStation = fromStop?.id ?: return null
    val toStation = toStop?.id ?: return null
    val fromName = fromStop.name.ifEmpty { arriving.to }
    val toName = toStop.name.ifEmpty { next.from }
    // The stop right before the change: the last one of the ride that is not the change
    // station itself (a provider may list it twice, for two platforms). With stops in between
    // it is known; with only the two ends (a ride of one stop, or one whose stops are not
    // loaded) it is the boarding stop, which the dataset then confirms as a neighbour or
    // drops. The same for the stop after.
    val before = arrivingStops.dropLast(1).lastOrNull { !sameStop(it, fromStation, fromName) }
    val after = nextStops.drop(1).firstOrNull { !sameStop(it, toStation, toName) }
    val previousCertain = arrivingStops.size >= 3 && before?.place != null && !sameStop(before, arriving.fromPlace?.id, arriving.from)
    val nextCertain = nextStops.size >= 3 && after?.place != null && !sameStop(after, next.toPlace?.id, next.to)
    val previousStop = before?.place ?: arriving.fromPlace
    val nextStop = after?.place ?: next.toPlace
    return ChangeQuery(
        fromStation = fromStation,
        fromLine = fromLine,
        previousStation = previousStop?.id,
        toStation = toStation,
        toLine = toLine,
        nextStation = nextStop?.id,
        // A ride of truly one stop names its boarding stop as the one before: that is certain too.
        previousCertain = previousCertain || (arrivingStops.size == 2 && arrivingStops === arriving.stopovers),
        nextCertain = nextCertain || (nextStops.size == 2 && nextStops === next.stopovers),
        fromName = fromName,
        previousName = previousStop?.name ?: before?.name ?: arriving.from,
        toName = toName,
        nextName = nextStop?.name ?: after?.name ?: next.to,
        previousExcluded = arrivingBeyond?.place?.id,
        previousExcludedName = arrivingBeyond?.name,
        nextExcluded = nextBefore?.place?.id,
        nextExcludedName = nextBefore?.name,
    )
}

/** True when [stop] is the station [id] (or, without a VBB number in it, the one named [name]). */
private fun sameStop(stop: Stopover, id: String?, name: String?): Boolean {
    val own = stop.place?.id?.let { normalStationId(it) }
    val other = id?.let { normalStationId(it) }
    if (own != null && other != null && own.length == 9 && own.startsWith("900") && other.length == 9 && other.startsWith("900")) {
        return own == other
    }
    val key = stationNameKey(stop.place?.name ?: stop.name)
    return key.isNotEmpty() && name != null && key == stationNameKey(name)
}

/** Where to be in [arriving] for the change to [next] by the dataset alone, outside of Compose; null while it is not loaded. */
fun changePositionOf(arriving: Leg, next: Leg): ChangePosition? =
    changeQuery(arriving, next)?.let { ChangePositions.find(it) }?.checkedFor(arriving, next)

/**
 * This record with "same platform" kept only where it may be believed for the change from
 * [arriving] to [next] (see [samePlatformRecordAllowed]): one station, and one family of
 * transport, or S-Bahn and U-Bahn where the dataset names a cross-platform interchange.
 */
fun ChangePosition.checkedFor(arriving: Leg, next: Leg): ChangePosition =
    if (samePlatform && !samePlatformRecordAllowed(arriving, next)) copy(samePlatform = false) else this

/**
 * Where to board [arriving] for the change to [next]: the record of the dataset where it has
 * one, else an estimate from the map (see [geometryPosition]). Outside of Compose; the
 * arguments are those of [changeQuery]. Null for staying on board and where neither knows.
 */
fun boardingPositionOf(
    arriving: Leg,
    next: Leg,
    arrivingStops: List<Stopover> = arriving.stopovers,
    nextStops: List<Stopover> = next.stopovers,
    arrivingBeyond: Stopover? = null,
    nextBefore: Stopover? = null,
): ChangePosition? {
    if (arriving.walking || next.walking) return null
    if (arriving.tripId != null && arriving.tripId == next.tripId) return null
    val query = changeQuery(arriving, next, arrivingStops, nextStops, arrivingBeyond, nextBefore)
    if (query != null) {
        ChangePositions.find(query)?.let { record ->
            Log.d(LOG_TAG, "boarding ${arriving.line} -> ${next.line}: dataset record")
            return record.checkedFor(arriving, next)
        }
    }
    val estimate = geometryPosition(arriving, next, arrivingStops, nextStops)
    Log.d(
        LOG_TAG,
        "boarding ${arriving.line} -> ${next.line}: " +
            (if (query == null) "no dataset query" else "no dataset record") +
            ", geometry ${if (estimate == null) "none" else "part ${estimate.part}"}",
    )
    return estimate
}

/**
 * Where to board [arriving] by the map: the direction the train runs in at the change (from
 * its way, else the stop before) and where the departing ride leaves from, ahead or behind.
 * Only for trains; marked as estimated. Null where a position is missing or tells nothing.
 */
fun geometryPosition(
    arriving: Leg,
    next: Leg,
    arrivingStops: List<Stopover> = arriving.stopovers,
    nextStops: List<Stopover> = next.stopovers,
): ChangePosition? {
    if (arriving.walking || next.walking || arriving.product in ROAD_PRODUCTS) return null
    // The platform from OpenStreetMap first: the source's positions are often those of the
    // whole station, for the U-Bahn and the trams in front of it alike (one stop for all).
    val byPlatform = try {
        platformPosition(arriving, next, arrivingStops)
    } catch (e: RuntimeException) {
        Log.w(LOG_TAG, "platform geometry", e)
        null
    }
    if (byPlatform != null) return estimatedPosition(byPlatform.part)
    // The stop positions the source gives, which for many sources are those of the platform.
    val change = arriving.toPlace?.position ?: arrivingStops.lastOrNull()?.place?.position ?: arriving.path.lastOrNull()
    val departure = next.fromPlace?.position ?: nextStops.firstOrNull()?.place?.position ?: next.path.firstOrNull()
    if (change == null || departure == null) {
        Log.d(LOG_TAG, "geometry: no position (change ${change != null}, departure ${departure != null})")
        return null
    }
    // The direction of travel: along the way of the ride, else from the stop before, else from boarding.
    val fromPath = BoardingGeometry.tangentPoint(arriving.path, change)
    val fromStop = arrivingStops.dropLast(1).asReversed().firstNotNullOfOrNull { stop ->
        stop.place?.position?.takeIf { BoardingGeometry.distance(it, change) >= BoardingGeometry.MIN_BASE }
    }
    val previous = fromPath ?: fromStop ?: arriving.fromPlace?.position
    if (previous == null) {
        Log.d(LOG_TAG, "geometry: no direction of travel")
        return null
    }
    val source = when (previous) {
        fromPath -> "path"
        fromStop -> "previous stop"
        else -> "boarding stop"
    }
    val decision = BoardingGeometry.estimate(previous, change, departure, arriving.product)
    Log.d(LOG_TAG, "geometry by $source: ${decision.reason}")
    val estimate = decision.estimate ?: return null
    return estimatedPosition(estimate.part)
}

/** An estimated position in the third [part] of the train (0 rear, 1 middle, 2 front). */
private fun estimatedPosition(part: Int): ChangePosition = ChangePosition(
    fromPosition = when (part) {
        0 -> 0.15f
        2 -> 0.85f
        else -> 0.5f
    },
    samePlatform = false,
    estimated = true,
)

/**
 * Where to board [arriving] by the platform it arrives at, from OpenStreetMap: the direction
 * the train runs along it (from its way, else the stop before, else where it was boarded)
 * and where the change leaves the platform (see [platformExit]). Of several platforms the
 * line may use, the one where its way ends; without a way, all of them must agree. Null where
 * the platform, the direction or where the next ride leaves from is not known.
 */
private fun platformPosition(arriving: Leg, next: Leg, arrivingStops: List<Stopover>): BoardingEstimate? {
    val (station, platforms) = OsmStations.alightingPlatforms(arriving) ?: return null
    val exit = platformExit(arriving, next, station, platforms) ?: return null
    val pathEnd = arriving.path.lastOrNull()
    val candidates = if (pathEnd != null && platforms.size > 1) {
        listOfNotNull(platforms.minByOrNull { BoardingGeometry.distance(PlatformChange.middle(it), pathEnd) })
    } else {
        platforms
    }
    val estimates = candidates.map { platform -> platformEstimateOf(arriving, arrivingStops, platform, exit) ?: return null }
    val first = estimates.firstOrNull() ?: return null
    return if (estimates.all { it.part == first.part }) first else null
}

/** [platformPosition] for one [platform] the change leaves towards [exit]. */
private fun platformEstimateOf(
    arriving: Leg,
    arrivingStops: List<Stopover>,
    platform: OsmPlatform,
    exit: Pair<Double, Double>,
): BoardingEstimate? {
    val middle = PlatformChange.middle(platform)
    val fromPath = BoardingGeometry.tangentPoint(arriving.path, middle)
    val fromStop = arrivingStops.dropLast(1).asReversed().firstNotNullOfOrNull { stop ->
        stop.place?.position?.takeIf { BoardingGeometry.distance(it, middle) >= BoardingGeometry.MIN_BASE }
    }
    val previous = fromPath ?: fromStop ?: arriving.fromPlace?.position ?: return null
    val source = when (previous) {
        fromPath -> "path"
        fromStop -> "previous stop"
        else -> "boarding stop"
    }
    val decision = BoardingGeometry.platformEstimate(platform.a, platform.b, previous, exit, arriving.product)
    Log.d(LOG_TAG, "platform geometry by $source: ${decision.reason}")
    return decision.estimate
}

/**
 * Where the change from [arriving], left at [platforms] of [station], to [next] leaves the
 * platform: for a bus, tram or a train at another station, the exit of the station nearest
 * to where [next] leaves from (its stop position from OpenStreetMap, else the source's
 * position where it is not that of the whole station), else that place itself; for a train
 * at another platform of the same station, the point of the platform closest to that one.
 * Null where that is not known, for the same platform, and for platforms side by side (the
 * way between them, by stairs at either end or across a bridge, is not known).
 */
private fun platformExit(
    arriving: Leg,
    next: Leg,
    station: OsmStation,
    platforms: List<OsmPlatform>,
): Pair<Double, Double>? {
    val arrival = arriving.toPlace?.position
    // A position of the source counts only where it is not the one of the whole station.
    fun distinct(point: Pair<Double, Double>?): Pair<Double, Double>? =
        point?.takeIf { arrival == null || BoardingGeometry.distance(it, arrival) >= BoardingGeometry.MIN_GAP }
    val target: Pair<Double, Double>
    if (next.product in ROAD_PRODUCTS) {
        target = next.boardingSpot?.position ?: OsmStations.rideSpot(next, boarding = true)?.position
            ?: distinct(next.fromPlace?.position) ?: distinct(next.path.firstOrNull()) ?: return null
    } else {
        val to = OsmStations.platformEnds(next, boarding = true)
        if (to != null && to.station === station) {
            if (to.platforms.any { it in platforms }) return null
            if (to.platforms.all { other -> platforms.all { sideBySide(it, other) } }) return null
            return PlatformChange.closestPoints(platforms, to.platforms)?.first
        }
        target = to?.platforms?.firstOrNull()?.let { PlatformChange.middle(it) } ?: distinct(next.fromPlace?.position) ?: return null
    }
    return OsmStations.exitToward(station, platforms, target, arriving.product)?.position ?: target
}

/** True when the platforms [a] and [b] run within 30 degrees of each other. */
private fun sideBySide(a: OsmPlatform, b: OsmPlatform): Boolean {
    val (eastA, northA) = BoardingGeometry.offset(a.a, a.b)
    val (eastB, northB) = BoardingGeometry.offset(b.a, b.b)
    val lengths = hypot(eastA, northA) * hypot(eastB, northB)
    if (lengths <= 0.0) return true
    return abs(eastA * eastB + northA * northB) / lengths > SIDE_BY_SIDE_COS
}

// The cosine of 30 degrees.
private const val SIDE_BY_SIDE_COS = 0.866

/**
 * True when the map estimate for boarding [arriving] towards [next] wants the stops of the
 * ride: a train whose way is not known, so the stop before the change gives the direction.
 */
fun needsStopsForGeometry(arriving: Leg, next: Leg): Boolean =
    !arriving.walking && !next.walking && arriving.product !in ROAD_PRODUCTS &&
        arriving.path.size < 2 && (arriving.tripId == null || arriving.tripId != next.tripId)

/** The ride after [leg] in [journey], walks left out; null when [leg] is the last ride or not part of it. */
fun rideAfter(journey: Journey, leg: Leg): Leg? {
    val index = journey.legs.indexOfFirst { it === leg }.takeIf { it >= 0 } ?: journey.legs.indexOf(leg)
    if (index < 0) return null
    return journey.legs.drop(index + 1).firstOrNull { !it.walking }
}

/** "Board in the middle" and the like, outside of Compose; softer for an estimate. */
fun carriageText(context: Context, position: ChangePosition): String = context.getString(carriageTextId(position))

/**
 * The hint for before boarding, with the line it leads to: "Board at the front – short way to
 * the S2". Outside of Compose. On the same platform, where to board does not matter: the
 * change itself says so (see [changeText]). [nextProduct] names a bus or tram as such.
 */
fun boardingText(context: Context, position: ChangePosition, nextLine: String?, nextProduct: String? = null): String? {
    if (position.samePlatform) return null
    val line = nextLine?.trim().orEmpty()
    if (line.isEmpty()) return carriageText(context, position)
    val way = when (nextProduct) {
        "bus" -> R.string.carriage_short_way_bus
        "tram" -> R.string.carriage_short_way_tram
        else -> R.string.carriage_short_way
    }
    return context.getString(way, carriageText(context, position), line)
}

/** "Same platform – change right here to the S85", outside of Compose. */
fun samePlatformText(context: Context, nextLine: String?): String {
    val line = nextLine?.trim().orEmpty()
    return if (line.isEmpty()) {
        context.getString(R.string.carriage_same_platform_change)
    } else {
        context.getString(R.string.carriage_same_platform_to, line)
    }
}

/**
 * What to know at the change itself from [arriving] to [next], outside of Compose: only the
 * same platform (by the platform numbers or the dataset). Where to board was said before.
 * Null otherwise, and for staying on board.
 */
fun changeText(context: Context, arriving: Leg, next: Leg, walkMinutes: Long = 0): String? {
    if (arriving.tripId != null && arriving.tripId == next.tripId) return null
    val line = shortLine(next.line)
    if (walkMinutes <= 0 && samePlatformByNumbers(arriving, next)) return samePlatformText(context, line)
    val position = changePositionOf(arriving, next) ?: return null
    return if (position.samePlatform) samePlatformText(context, line) else null
}

private fun carriageTextId(position: ChangePosition): Int = when {
    position.samePlatform -> R.string.carriage_same_platform
    position.estimated && position.part == 0 -> R.string.carriage_rear_estimated
    position.estimated && position.part == 2 -> R.string.carriage_front_estimated
    position.estimated -> R.string.carriage_middle_estimated
    position.part == 0 -> R.string.carriage_rear
    position.part == 2 -> R.string.carriage_front
    else -> R.string.carriage_middle
}

/** Loads the dataset of change positions for a composable; true once it can be asked. */
@Composable
private fun changePositionsReady(): Boolean {
    val context = LocalContext.current
    var ready by remember { mutableStateOf(ChangePositions.loaded) }
    LaunchedEffect(Unit) {
        if (!ready) {
            ChangePositions.load(context)
            ready = true
        }
    }
    return ready
}

/**
 * Where to board [arriving] for a short change to [next], shown where the traveller boards
 * it: a drawn train with the zone to board in the colour of the line, and "Board in the
 * middle – short way to the S46". [arrivingBeyond] and [nextBefore] are the stops the two
 * vehicles call at after and before the change, where their runs are known (see
 * [changeQuery]). [onBlue] draws it for the blue banner of a trip. The dataset decides where
 * it has a record, else the map (see [boardingPositionOf]); an estimate is shown a little
 * quieter. Shows nothing where neither knows or the change is on the same platform (the
 * change says so), for a bus or tram to get off, and while the setting is off.
 */
@Composable
fun CarriageHint(
    arriving: Leg,
    next: Leg,
    modifier: Modifier = Modifier,
    arrivingStops: List<Stopover> = arriving.stopovers,
    nextStops: List<Stopover> = next.stopovers,
    arrivingBeyond: Stopover? = null,
    nextBefore: Stopover? = null,
    onBlue: Boolean = false,
) {
    if (!boardingHintsOn.value) return
    if (arriving.walking || next.walking || arriving.product in ROAD_PRODUCTS) return
    if (arriving.tripId != null && arriving.tripId == next.tripId) return
    if (samePlatformByNumbers(arriving, next)) return
    // The record of the dataset wins over the map: wait for it before deciding.
    if (!changePositionsReady()) return
    val position = remember(arriving, next, arrivingStops, nextStops, arrivingBeyond, nextBefore) {
        boardingPositionOf(arriving, next, arrivingStops, nextStops, arrivingBeyond, nextBefore)
    } ?: return
    if (position.samePlatform) return
    val context = LocalContext.current
    val text = boardingText(context, position, shortLine(next.line), next.product) ?: return
    HintRow(arriving = arriving, part = position.part, estimated = position.estimated, text = text, onBlue = onBlue, modifier = modifier)
}

/**
 * What to know at the change from [arriving] to [next] itself, with [walkMinutes] on foot
 * between them: "Same platform – change right here to the S85" where both platform numbers
 * at the station agree or the dataset says so; between two buses or trams whose stop
 * positions OpenStreetMap knows, "Same stop" or how far the other stop position is (see
 * [stopSpotMeters]). Where to board was said at the ride before; nothing else is shown here.
 * Nothing for staying on board and while the setting is off.
 */
@Composable
fun ChangeHint(
    arriving: Leg,
    next: Leg,
    modifier: Modifier = Modifier,
    walkMinutes: Long = 0,
    arrivingStops: List<Stopover> = arriving.stopovers,
    nextStops: List<Stopover> = next.stopovers,
    arrivingBeyond: Stopover? = null,
    nextBefore: Stopover? = null,
    onBlue: Boolean = false,
) {
    if (!boardingHintsOn.value) return
    if (arriving.tripId != null && arriving.tripId == next.tripId) return
    val context = LocalContext.current
    val line = shortLine(next.line)
    // Between two stop positions of buses or trams: the same stop, or the way to the other one.
    val spotMeters = if (walkMinutes <= 0 && stopSpotNeed(arriving, next) != null) stopSpotMeters(arriving, next) else null
    if (spotMeters != null) {
        val text = if (spotMeters <= SAME_SPOT_METERS) {
            if (line.isNullOrBlank()) context.getString(R.string.change_same_stop) else context.getString(R.string.change_same_stop_to, line)
        } else {
            // Rounded to 10 m: the positions are not more precise than that.
            val meters = (((spotMeters + 5.0) / 10.0).toInt() * 10).coerceAtLeast(10)
            val number = next.boardingSpot?.label
            if (number == null) context.getString(R.string.change_stop_walk, meters) else context.getString(R.string.change_stop_walk_pos, meters, number)
        }
        HintRow(arriving = arriving, part = null, estimated = false, text = text, onBlue = onBlue, modifier = modifier, glyph = spotMeters <= SAME_SPOT_METERS)
        return
    }
    if (walkMinutes <= 0 && samePlatformByNumbers(arriving, next)) {
        HintRow(arriving = arriving, part = null, estimated = false, text = samePlatformText(context, line), onBlue = onBlue, modifier = modifier)
        return
    }
    val query = remember(arriving, next, arrivingStops, nextStops, arrivingBeyond, nextBefore) {
        changeQuery(arriving, next, arrivingStops, nextStops, arrivingBeyond, nextBefore)
    } ?: return
    if (!changePositionsReady()) return
    val position = remember(query) { ChangePositions.find(query)?.checkedFor(arriving, next) } ?: return
    if (position.samePlatform) {
        HintRow(arriving = arriving, part = null, estimated = false, text = samePlatformText(context, line), onBlue = onBlue, modifier = modifier)
    }
}

/**
 * One hint: the drawn train (none for [part] null, the same platform) and its text beside it.
 * An [estimated] hint is a little quieter: a paler ground and zone and a lighter text.
 */
@Composable
private fun HintRow(arriving: Leg, part: Int?, estimated: Boolean, text: String, onBlue: Boolean, modifier: Modifier, glyph: Boolean = true) {
    val line = if (arriving.cancelled) AppColors.Rail else lineColor(arriving)
    val ink = if (onBlue) AppColors.OnBlue else AppColors.Ink
    val ground = when {
        onBlue -> AppColors.OnBlue.copy(alpha = if (estimated) 0.10f else 0.16f)
        else -> line.copy(alpha = if (estimated) 0.06f else 0.10f)
    }
    Row(
        modifier = modifier
            .clearAndSetSemantics { contentDescription = text }
            .clip(RoundedCornerShape(12.dp))
            .background(ground)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (part != null) {
            TrainGraphic(
                part = part,
                segments = segmentsOf(arriving.product),
                line = if (onBlue) AppColors.OnBlue else line,
                body = if (onBlue) AppColors.OnBlue.copy(alpha = 0.14f) else AppColors.Card,
                tint = if (onBlue) Color.Transparent else line.copy(alpha = 0.22f),
                zoneAlpha = if (estimated) 0.55f else 1f,
            )
        } else if (glyph) {
            SamePlatformGlyph(color = if (onBlue) AppColors.OnBlue else line, quiet = if (onBlue) AppColors.OnBlue.copy(alpha = 0.7f) else AppColors.Muted)
        }
        Text(
            text = text,
            color = if (estimated) ink.copy(alpha = 0.85f) else ink,
            fontWeight = if (estimated) FontWeight.SemiBold else FontWeight.Bold,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** How many segments the train of [product] is drawn with: an S-Bahn as four, other trains as three. */
private fun segmentsOf(product: String): Int = when (product) {
    "suburban" -> 4
    else -> 3
}

/**
 * A minimal train running to the right: one capsule with a softly rounded rear and a fully
 * rounded front, split by thin gaps into [segments]. The segments of the third at [part]
 * (0 rear, 1 middle, 2 front) are filled in [line], the others in a light [tint] of it over
 * [body]. [zoneAlpha] below 1 pales the whole train for an estimate.
 */
@Composable
private fun TrainGraphic(
    part: Int,
    segments: Int,
    line: Color,
    body: Color,
    tint: Color,
    zoneAlpha: Float,
) {
    Canvas(modifier = Modifier.size(width = 64.dp, height = 16.dp)) {
        val top = 3.dp.toPx()
        val bottom = size.height - 3.dp.toPx()
        val height = bottom - top
        val gap = 1.5.dp.toPx()
        val segment = (size.width - gap * (segments - 1)) / segments
        // The segments of the third to board: those whose middle lies in it.
        val picked = (0 until segments).filter { index ->
            val middle = (index + 0.5f) / segments
            when (part) {
                0 -> middle < 1f / 3f
                2 -> middle > 2f / 3f
                else -> middle in (1f / 3f)..(2f / 3f)
            }
        }.ifEmpty { listOf(if (part == 0) 0 else if (part == 2) segments - 1 else segments / 2) }.toSet()
        val rear = CornerRadius(3.dp.toPx())
        val front = CornerRadius(height / 2f)
        val shape = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(0f, top, size.width, bottom),
                    topLeft = rear,
                    topRight = front,
                    bottomRight = front,
                    bottomLeft = rear,
                ),
            )
        }
        clipPath(shape) {
            for (index in 0 until segments) {
                val left = index * (segment + gap)
                val inZone = index in picked
                if (inZone) {
                    drawRect(line.copy(alpha = zoneAlpha), topLeft = Offset(left, top), size = Size(segment, height))
                } else {
                    drawRect(body.copy(alpha = body.alpha * zoneAlpha), topLeft = Offset(left, top), size = Size(segment, height))
                    drawRect(tint.copy(alpha = tint.alpha * zoneAlpha), topLeft = Offset(left, top), size = Size(segment, height))
                }
            }
        }
    }
}

/** Two trains on either side of one platform: the sign for a change across the same platform. */
@Composable
private fun SamePlatformGlyph(color: Color, quiet: Color) {
    Canvas(modifier = Modifier.size(width = 30.dp, height = 20.dp)) {
        val pill = 5.dp.toPx()
        val radius = CornerRadius(pill / 2f)
        drawRoundRect(color = color.copy(alpha = 0.35f), topLeft = Offset(1.dp.toPx(), 2.dp.toPx()), size = Size(size.width - 2.dp.toPx(), pill), cornerRadius = radius)
        drawRoundRect(color = color, topLeft = Offset(1.dp.toPx(), size.height - 2.dp.toPx() - pill), size = Size(size.width - 2.dp.toPx(), pill), cornerRadius = radius)
        val mid = size.height / 2f
        drawLine(quiet, Offset(2.dp.toPx(), mid), Offset(size.width - 2.dp.toPx(), mid), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
    }
}

private const val LOG_TAG = "ChangePos"
