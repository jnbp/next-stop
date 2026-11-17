package io.github.jnbp.nextstop.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.data.FootPaths
import io.github.jnbp.nextstop.data.FootWay
import io.github.jnbp.nextstop.data.Leg
import io.github.jnbp.nextstop.data.OsmStations
import io.github.jnbp.nextstop.data.SAME_SPOT_METERS
import io.github.jnbp.nextstop.data.WalkTargetKind
import io.github.jnbp.nextstop.data.routeMeters
import io.github.jnbp.nextstop.data.plainStopName
import io.github.jnbp.nextstop.data.walkDetails
import io.github.jnbp.nextstop.data.shownWalkMinutes
import io.github.jnbp.nextstop.data.walkMinutesFor
import io.github.jnbp.nextstop.data.stopSpotMeters
import io.github.jnbp.nextstop.data.ChangeMapEnds
import io.github.jnbp.nextstop.data.OsmPlatform
import io.github.jnbp.nextstop.data.changeMapEnds
import io.github.jnbp.nextstop.data.changeMovesPlace
import io.github.jnbp.nextstop.data.ChangeMapKind
import io.github.jnbp.nextstop.data.changeMapKind
import io.github.jnbp.nextstop.ui.theme.AppColors

/*
 * The small maps of walks (see WalkMiniMap) as they stand in the journey detail and in the
 * companion: folded away behind a visible "Show map" button, unless the setting "Always
 * unfold walk mini maps" is on. A folded map takes no picture of the map.
 */

/** The setting whether the small maps of walks start unfolded; off by default. */
object WalkMapSetting {
    var open by mutableStateOf(false)
        private set
    private var loaded = false

    /** The stored setting, read once per process. */
    fun load(context: Context): Boolean {
        if (!loaded) {
            loaded = true
            open = AppPrefs(context).walkMapsOpen
        }
        return open
    }

    fun update(context: Context, value: Boolean) {
        loaded = true
        open = value
        AppPrefs(context).walkMapsOpen = value
    }
}

/** Whether a small map starts unfolded, by the setting; each map keeps its own state after that. */
@Composable
fun rememberWalkMapOpen(): androidx.compose.runtime.MutableState<Boolean> {
    val context = LocalContext.current
    return remember { mutableStateOf(WalkMapSetting.load(context)) }
}

/**
 * The button that unfolds or folds a small map: a map icon, "Show map" or "Hide map" and
 * a chevron that turns; its width follows the label softly.
 */
@Composable
fun MapFoldButton(
    open: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    ground: Color = AppColors.Chip,
    ink: Color = AppColors.Ink,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(ground)
            .clickable(onClick = onToggle)
            .defaultMinSize(minHeight = 40.dp)
            .animateContentSize(Motion.settle())
            .padding(start = 10.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        GlyphIcon(Glyph.Map, color = ink, size = 16.dp)
        Text(
            text = stringResource(if (open) R.string.walk_map_hide else R.string.walk_map_show),
            color = ink,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            maxLines = 1,
        )
        Chevron(open = open, color = ink)
    }
}

/** [content] unfolded while [open], folding in and out softly. */
@Composable
fun MapFold(open: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = open,
        enter = expandVertically(Motion.settle()) + fadeIn(Motion.fadeIn()),
        exit = shrinkVertically(Motion.settle()) + fadeOut(Motion.fadeOut()),
    ) {
        content()
    }
}

/**
 * A small map with its own fold button above it, for places without a walk card. The button
 * stands [indent] in from the start, in line with the text it belongs to; the map takes the
 * full width.
 */
@Composable
fun FoldedMap(modifier: Modifier = Modifier, indent: Dp = 0.dp, map: @Composable () -> Unit) {
    var open by rememberWalkMapOpen()
    Column(modifier = modifier.fillMaxWidth()) {
        MapFoldButton(open = open, onToggle = { open = !open }, modifier = Modifier.padding(start = indent))
        MapFold(open = open) {
            Box(modifier = Modifier.padding(top = 8.dp)) { map() }
        }
    }
}

/** True when [walk] has a way to draw as a small map: two points at least. */
fun hasWalkMap(walk: Leg): Boolean {
    val path = FootPaths.pathFor(walk)
    val target = walk.walkTarget?.position
    val points = if (target != null && path.lastOrNull() != target) path.size + 1 else path.size
    return points >= 2
}

/**
 * True when the change from [arriving] to [next] is a walk between two stop positions of
 * buses or trams without a walk leg of its own, far enough apart to show (see [SpotWalkMap]).
 */
fun hasSpotWalkMap(arriving: Leg, next: Leg): Boolean {
    if (arriving.walking || next.walking) return false
    val meters = stopSpotMeters(arriving, next) ?: return false
    return meters > SAME_SPOT_METERS
}

/**
 * The ends of the change from [arriving] to [next] for its small map (see [changeMapEnds]),
 * with the platform of a rail ride from OpenStreetMap where its way and stop position are not known.
 */
fun changeEndsOf(arriving: Leg, next: Leg): ChangeMapEnds? = changeMapEnds(
    arriving,
    next,
    alightPlatform = OsmStations.ridePlatforms(arriving, boarding = false).firstOrNull()?.let(::platformMiddle),
    boardPlatform = OsmStations.ridePlatforms(next, boarding = true).firstOrNull()?.let(::platformMiddle),
)

private fun platformMiddle(platform: OsmPlatform): Pair<Double, Double> =
    (platform.a.first + platform.b.first) / 2 to (platform.a.second + platform.b.second) / 2

/**
 * True when the change from [arriving] to [next], without a walk leg of its own, gets the small
 * map of [ChangeWalkMap]: the next ride is boarded elsewhere (see [changeMovesPlace]), as from a
 * bus stop in the street to a platform of the same name, and no map of two stop positions of
 * buses or trams ([hasSpotWalkMap]) shows it already.
 */
fun hasChangeMap(arriving: Leg, next: Leg): Boolean {
    if (arriving.walking || next.walking || hasSpotWalkMap(arriving, next)) return false
    if (OsmStations.samePlatform(arriving, next)) return false
    return changeMovesPlace(arriving, next, changeEndsOf(arriving, next))
}

/**
 * The small map of the change from [arriving] to [next] with the walk leg [walk] between them
 * (null for none, [walkMinutes] long), by [changeMapKind]: the walk's own, else the two stop
 * positions, else the change as known. Null where it shows none.
 */
fun changeWalkMapKind(arriving: Leg, next: Leg, walk: Leg?, walkMinutes: Long): ChangeMapKind = changeMapKind(
    arriving,
    next,
    changeEndsOf(arriving, next),
    walkMinutes = walkMinutes,
    walkHasMap = walk != null && hasWalkMap(walk),
    samePlatform = OsmStations.samePlatform(arriving, next),
)

/** The small map of [kind] for the change from [arriving] to [next] with the walk leg [walk] (see changeWalkMapKind). */
@Composable
fun ChangeMapOf(kind: ChangeMapKind, arriving: Leg, next: Leg, walk: Leg?, onFocusWalk: (Leg) -> Unit) {
    when (kind) {
        ChangeMapKind.Walk -> if (walk != null) WalkMap(walk, onFocusWalk)
        ChangeMapKind.Spots -> SpotWalkMap(arriving, next, onFocusWalk)
        ChangeMapKind.Change -> ChangeWalkMap(arriving, next, onFocusWalk)
        ChangeMapKind.None -> Unit
    }
}

/**
 * The small map of a change without a walk of its own between any two rides (see
 * [hasChangeMap]): from where [arriving] is left to where [next] is boarded, by the stop
 * positions, platforms or stops known (see [changeEndsOf]), along the real way on foot once it
 * is known, the straight line until then. A tap shows it on the full map.
 */
@Composable
fun ChangeWalkMap(arriving: Leg, next: Leg, onFocusWalk: (Leg) -> Unit) {
    val ends = remember(arriving, next) { changeEndsOf(arriving, next) } ?: return
    val start = ends.start
    val end = ends.end
    val from = arriving.alightingSpot
    val to = next.boardingSpot
    // Drawn again once the real way on foot came in.
    val footVersion by FootPaths.version.collectAsState()
    val path = remember(start, end, footVersion) { FootPaths.wayBetween(start, end) }
    val straight = remember(start, end, footVersion) { FootPaths.statusBetween(start, end) == FootWay.Straight }
    LaunchedEffect(start, end) { FootPaths.wantWay(start, end) }
    val walk = remember(arriving, next, path) { spotWalk(arriving, next, path) }
    val details = remember(path, end, from, to) {
        val points = if (path.lastOrNull() != end) path + end else path
        walkDetails(points, targetPos = to?.label, startPos = from?.label)
    }
    val broken = rememberBrokenLifts(details)
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        WalkMiniMap(
            path = path,
            target = end,
            targetLabel = to?.label,
            onClick = { onFocusWalk(walk) },
            startLabel = from?.label,
            startColor = lineColor(arriving).toArgb(),
            targetColor = lineColor(next).toArgb(),
            startLines = remember(from) { from?.let { OsmStations.linesAt(it) }.orEmpty() },
            targetLines = remember(to) { to?.let { OsmStations.linesAt(it) }.orEmpty() },
            straight = straight,
            details = details,
            brokenLifts = broken,
            targetIsPosition = to != null,
        )
        val summary = remember(details, broken, path) {
            walkSummaryText(context, details, to?.label, walkMinutesFor(routeMeters(path)), broken)
        }
        WalkSummaryLine(summary, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
    }
}

/** The small map of [walk] (see [WalkMiniMap]); a tap shows the walk on the full map. */
@Composable
fun WalkMap(walk: Leg, onFocusWalk: (Leg) -> Unit) {
    // Drawn again once the real way on foot came in.
    val footVersion by FootPaths.version.collectAsState()
    val path = remember(walk, footVersion) { FootPaths.pathFor(walk) }
    val straight = remember(walk, footVersion) { FootPaths.status(walk) == FootWay.Straight }
    // A small map in view asks for its way at once, whatever page it is on.
    LaunchedEffect(walk) { FootPaths.wantWalk(walk) }
    val target = walk.walkTarget
    // What OSM knows along the way: the other stop positions, entrances, lifts, escalators.
    val details = remember(path, target, walk.walkExit) {
        val points = target?.position?.let { if (path.lastOrNull() != it) path + it else path } ?: path
        walkDetails(
            points,
            targetPos = target?.takeIf { it.kind == WalkTargetKind.StopPosition }?.label,
            entrance = target?.takeIf { it.kind == WalkTargetKind.Entrance },
            exit = walk.walkExit,
        )
    }
    val broken = rememberBrokenLifts(details)
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        WalkMiniMap(
            path = path,
            target = target?.position,
            targetLabel = when {
                target == null -> null
                target.kind == WalkTargetKind.StopPosition -> target.label
                else -> target.label?.takeIf { it.length <= 3 } ?: stringResource(R.string.walk_minimap_entrance)
            },
            onClick = { onFocusWalk(walk) },
            // The exit of the station the walk leaves by, with its letter where it is a short one,
            // where the way starts there (a way through it from the platform shows it on the way).
            startLabel = walk.walkExit?.takeIf { it.position == path.firstOrNull() }?.label?.takeIf { it.length <= 3 },
            targetLines = remember(target) { target?.let { OsmStations.linesAt(it) }.orEmpty() },
            straight = straight,
            details = details,
            brokenLifts = broken,
            targetIsPosition = target?.kind == WalkTargetKind.StopPosition,
        )
        val summary = remember(details, broken, path, walk) {
            walkSummaryText(
                context,
                details,
                target?.takeIf { it.kind == WalkTargetKind.StopPosition }?.label,
                // As the card of the walk says: planned, but never fewer than the drawn way takes.
                if (walk.cycling) null else shownWalkMinutes(walk),
                broken,
                targetName = walk.to.plainStopName(),
            )
        }
        WalkSummaryLine(summary, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
    }
}

/**
 * The small map of a change between two stop positions of buses or trams without a walk of
 * its own: where [arriving] stops, where [next] leaves (each in the colour of its line, with
 * its "Pos." number) and the way on foot between them. A tap shows it on the full map.
 */
@Composable
fun SpotWalkMap(arriving: Leg, next: Leg, onFocusWalk: (Leg) -> Unit) {
    val from = arriving.alightingSpot ?: return
    val to = next.boardingSpot ?: return
    val start = arriving.path.lastOrNull() ?: from.position
    val end = next.path.firstOrNull() ?: to.position
    // Drawn again once the real way on foot came in.
    val footVersion by FootPaths.version.collectAsState()
    val path = remember(start, end, footVersion) { FootPaths.wayBetween(start, end) }
    val straight = remember(start, end, footVersion) { FootPaths.statusBetween(start, end) == FootWay.Straight }
    LaunchedEffect(start, end) { FootPaths.wantWay(start, end) }
    val walk = remember(arriving, next, path) { spotWalk(arriving, next, path) }
    val details = remember(path, end, from, to) {
        val points = if (path.lastOrNull() != end) path + end else path
        walkDetails(points, targetPos = to.label, startPos = from.label)
    }
    val broken = rememberBrokenLifts(details)
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        WalkMiniMap(
            path = path,
            target = end,
            targetLabel = to.label,
            onClick = { onFocusWalk(walk) },
            startLabel = from.label,
            startColor = lineColor(arriving).toArgb(),
            targetColor = lineColor(next).toArgb(),
            startLines = remember(from) { OsmStations.linesAt(from) },
            targetLines = remember(to) { OsmStations.linesAt(to) },
            straight = straight,
            details = details,
            brokenLifts = broken,
            targetIsPosition = true,
        )
        val summary = remember(details, broken, path) {
            walkSummaryText(context, details, to.label, walkMinutesFor(routeMeters(path)), broken)
        }
        WalkSummaryLine(summary, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
    }
}

/**
 * True when [ride], a bus or tram ride, starts at a known stop position of a stop with at least
 * three of them in OSM: worth a small map of the stop with the position to go to.
 */
fun hasBoardingPositionMap(ride: Leg): Boolean {
    if (ride.walking || (ride.product != "bus" && ride.product != "tram")) return false
    val spot = ride.boardingSpot ?: return false
    val anchor = ride.fromPlace?.position ?: spot.position
    return OsmStations.areasOf(ride.fromPlace?.id, ride.fromPlace?.name ?: ride.from, anchor).sumOf { it.positions.size } >= 3
}

/**
 * The small map of the stop where [ride] is boarded, for a stop of many stop positions (see
 * [hasBoardingPositionMap]): all its positions faint, the one of the ride highlighted in the
 * colour of its line with its number and lines, and the way there on foot from [from] (where
 * the previous ride ended, the user, else the stop's own point). A tap shows that way on the
 * full map. Nothing where the position is not known.
 */
@Composable
fun BoardingPositionMap(ride: Leg, from: Pair<Double, Double>?, onFocusWalk: (Leg) -> Unit) {
    val spot = ride.boardingSpot ?: return
    val start = from ?: ride.fromPlace?.position ?: return
    val end = spot.position
    val footVersion by FootPaths.version.collectAsState()
    val path = remember(start, end, footVersion) { FootPaths.wayBetween(start, end) }
    val straight = remember(start, end, footVersion) { FootPaths.statusBetween(start, end) == FootWay.Straight }
    LaunchedEffect(start, end) { FootPaths.wantWay(start, end) }
    val walk = remember(ride, path) { boardingWalk(ride, path) }
    val details = remember(path, end, spot) {
        val points = if (path.lastOrNull() != end) path + end else path
        walkDetails(points, targetPos = spot.label)
    }
    val broken = rememberBrokenLifts(details)
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        WalkMiniMap(
            path = path,
            target = end,
            targetLabel = spot.label,
            onClick = { onFocusWalk(walk) },
            targetColor = lineColor(ride).toArgb(),
            targetLines = remember(spot) { OsmStations.linesAt(spot) },
            straight = straight,
            details = details,
            brokenLifts = broken,
            targetIsPosition = true,
        )
        val summary = remember(details, broken, path) {
            walkSummaryText(context, details, spot.label, walkMinutesFor(routeMeters(path)), broken)
        }
        WalkSummaryLine(summary, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
    }
}

/** The way on foot to the stop position of [ride] along [path], as a walk to show on the map; no leg of the journey. */
internal fun boardingWalk(ride: Leg, path: List<Pair<Double, Double>>): Leg = Leg(
    line = null,
    product = "",
    walking = true,
    from = ride.from,
    to = ride.from,
    departure = ride.departure,
    plannedDeparture = null,
    arrival = ride.departure,
    plannedArrival = null,
    departurePlatform = null,
    arrivalPlatform = null,
    direction = null,
    cancelled = false,
    path = path,
    fromPlace = ride.fromPlace,
    toPlace = ride.fromPlace,
    walkTarget = ride.boardingSpot,
)

/** The way on foot between two rides along [path], as a walk to show on the map; it is no leg of the journey. */
private fun spotWalk(arriving: Leg, next: Leg, path: List<Pair<Double, Double>>): Leg = Leg(
    line = null,
    product = "",
    walking = true,
    from = arriving.to,
    to = next.from,
    departure = arriving.arrival,
    plannedDeparture = null,
    arrival = next.departure,
    plannedArrival = null,
    departurePlatform = null,
    arrivalPlatform = null,
    direction = null,
    cancelled = false,
    path = path,
    fromPlace = arriving.toPlace,
    toPlace = next.fromPlace,
)

/**
 * The small note on a walk drawn as the straight line because no real way on foot is known
 * for it ("Luftlinie"), so the line is not taken for the way to go.
 */
@Composable
fun StraightLineNote(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.walk_straight_line),
        color = AppColors.Muted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.Chip)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * "Zu Pos. 9 gehen · ca. 2 min" for [ride] before it is boarded, at a stop of many stop
 * positions (see [hasBoardingPositionMap]): from where the ride before it in [journey] ends,
 * else from the stop's own point, by the way on foot where known. Nothing otherwise.
 */
@Composable
fun BoardingPositionHint(ride: Leg, journey: io.github.jnbp.nextstop.data.Journey?, color: Color, modifier: Modifier = Modifier) {
    if (!hasBoardingPositionMap(ride)) return
    val spot = ride.boardingSpot ?: return
    val label = spot.label ?: return
    val legs = journey?.legs.orEmpty()
    val previous = legs.indexOfFirst { it === ride }.takeIf { it > 0 }?.let { legs[it - 1] }
    val from = previous?.takeIf { !it.walking }?.let { it.path.lastOrNull() ?: it.toPlace?.position } ?: ride.fromPlace?.position
    val footVersion by FootPaths.version.collectAsState()
    // The way on foot is asked for here as well: the trip map draws it to the position.
    LaunchedEffect(from, spot) { from?.let { FootPaths.wantWay(it, spot.position) } }
    val minutes = remember(from, spot, footVersion) {
        from?.let { walkMinutesFor(routeMeters(FootPaths.wayBetween(it, spot.position))) } ?: 0L
    }
    Text(
        text = if (minutes > 0) {
            stringResource(R.string.go_to_pos_minutes, label, minutes.toInt())
        } else {
            stringResource(R.string.go_to_pos, label)
        },
        color = color,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        maxLines = 1,
        modifier = modifier,
    )
}
