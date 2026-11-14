package io.github.jnbp.nextstop.ui

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.OffsetDateTime
import kotlin.math.sqrt
import kotlin.math.cos
import kotlin.math.roundToInt
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.Stopover
import io.github.jnbp.nextstop.data.TripCourse
import io.github.jnbp.nextstop.data.plainStopName
import io.github.jnbp.nextstop.data.plausible
import io.github.jnbp.nextstop.data.ridePlace
import io.github.jnbp.nextstop.data.townOf
import io.github.jnbp.nextstop.data.townedStopName
import io.github.jnbp.nextstop.ui.theme.AppColors
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

/**
 * The trip whose course is shown, with what is known of it before it is loaded.
 * [fromStop] is the name of the stop it was opened from, as the provider spells it.
 * [line], [product] and the colours ([color], [textColor], [at]) are those of the ride,
 * departure or vehicle it was opened from: they name the course, not what the provider's
 * trip calls itself (a run that begins as one line and goes on as another, a night line
 * that becomes a day line, names only its first line).
 */
data class CourseRequest(
    val tripId: String,
    val line: String?,
    val product: String,
    val direction: String?,
    val fromStop: String?,
    val color: Int? = null,
    val textColor: Int? = null,
    val at: Pair<Double, Double>? = null,
)

/**
 * [course] as the ride of [request] names it (see [CourseRequest]): its line, product and the
 * provider's colours where the request knows them; the trip's own where it does not.
 */
fun courseAsRequested(course: TripCourse, request: CourseRequest?): TripCourse {
    if (request == null || request.tripId != course.tripId) return course
    return course.copy(
        line = request.line?.takeIf { it.isNotBlank() && it != "?" } ?: course.line,
        product = request.product.takeIf { it.isNotEmpty() } ?: course.product,
        providerColor = request.color ?: course.providerColor,
        providerTextColor = if (request.color != null) request.textColor else course.providerTextColor,
    )
}

// Every stop of a course takes one row of this height.
private val COURSE_ROW = 40.dp

/**
 * The course of one line: its name on top, the map with its way free in the middle, and
 * below a sheet with all its stops and their times, with the vehicle on its way between
 * them. The sheet can be put away below its handle. A tap on a stop shows it on the map, its ⋮ (or a long press) opens the menu of the stop.
 */
@Composable
fun CourseScreen(
    request: CourseRequest,
    course: TripCourse?,
    failed: Boolean,
    now: OffsetDateTime,
    onBack: () -> Unit,
    onShowStop: (Stop) -> Unit,
    onStopAction: (Stop, StopAction) -> Unit,
    liveProgress: ((Long) -> Float?)? = null,
) {
    var sheetAway by remember { mutableStateOf(false) }
    // The times as shown: an implausible prognosis of the provider gives way to an estimate.
    val course = remember(course) { course?.plausible() }
    // The ride it was opened from names it (see CourseRequest), the trip only where that says nothing.
    val line = request.line?.takeIf { it.isNotBlank() && it != "?" } ?: course?.line
    val product = request.product.takeIf { it.isNotEmpty() } ?: course?.product.orEmpty()
    val direction = course?.direction ?: request.direction
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // The list never takes more than this, so the line on the map stays in view above it.
        val listHeight = maxHeight * 0.4f
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 12.dp, bottom = LocalNavInset.current),
        ) {
            GlassSurface(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    // The map frames the course between this header and the list below.
                    .coversMapTop(),
                shape = RoundedCornerShape(30.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RoundButton(
                        glyph = Glyph.Back,
                        description = stringResource(R.string.back),
                        onClick = onBack,
                        solid = true,
                    )
                    LineBadge(
                        line,
                        product,
                        at = request.at ?: course?.ridePlace,
                        provided = request.color ?: course?.providerColor,
                        providedText = if (request.color != null) request.textColor else course?.providerTextColor,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.line_course),
                            color = AppColors.Muted,
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                        // The direction fades in once the course says it.
                        FadeSwap(target = if (direction == null) "" else stringResource(R.string.direction, direction.plainStopName())) { shownDirection ->
                            Text(
                                text = shownDirection,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            CollapsibleBottom(
                collapsed = sheetAway,
                onCollapsed = { sheetAway = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .coversMapBottom(),
            ) {
                GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp)) {
                    // Loading, a note or the stops: a change between them cross-fades.
                    val phase = when {
                        course == null && !failed -> COURSE_LOADING
                        course == null || course.stops.isEmpty() -> COURSE_FAILED
                        else -> COURSE_SHOWN
                    }
                    FadeSwap(target = phase) { shownPhase ->
                    val shownCourse = course
                    when {
                        shownPhase == COURSE_LOADING -> LoadingRow(
                            text = stringResource(R.string.course_loading),
                            modifier = Modifier.padding(18.dp),
                        )

                        shownPhase == COURSE_FAILED || shownCourse == null -> Text(
                            text = stringResource(R.string.course_failed),
                            color = AppColors.DelayRed,
                            modifier = Modifier.padding(18.dp),
                        )

                        else -> CourseStops(
                            course = shownCourse,
                            fromStop = request.fromStop,
                            now = now,
                            maxHeight = listHeight,
                            onShowStop = onShowStop,
                            onStopAction = onStopAction,
                            liveProgress = liveProgress,
                        )
                    }
                    }
                }
            }
        }
    }
}

// What the sheet of a course shows (see CourseScreen).
private const val COURSE_LOADING = 0
private const val COURSE_FAILED = 1
private const val COURSE_SHOWN = 2

/** All stops of a course under each other, with the list opened at the stop the course was asked from. */
@Composable
private fun CourseStops(
    course: TripCourse,
    fromStop: String?,
    now: OffsetDateTime,
    maxHeight: Dp,
    onShowStop: (Stop) -> Unit,
    onStopAction: (Stop, StopAction) -> Unit,
    liveProgress: ((Long) -> Float?)?,
) {
    val color = lineColor(course)
    val stops = course.stops
    // The first stop the vehicle has not left yet; everything before it lies behind.
    val ahead = stops.indexOfFirst { (it.departure ?: it.arrival)?.isAfter(now) == true }
    val focus = stops.indexOfFirst { it.name == fromStop }
    val scroll = rememberScrollState()
    val rowPixels = with(LocalDensity.current) { COURSE_ROW.toPx() }
    // Once per trip the list moves to the stop that matters, with one stop before it in view.
    LaunchedEffect(course.tripId) {
        val target = if (focus >= 0) focus else ahead
        if (target > 1) scroll.scrollTo(((target - 1) * rowPixels).roundToInt())
    }
    // The town of the course (of the stop it was opened from, else of its first stop that names
    // one): stops in another town say theirs ("Kirchstr. · Zagelsdorf").
    val town = remember(stops, fromStop) { townOf(fromStop) ?: stops.firstNotNullOfOrNull { townOf(it.name) } }
    val clock = remember(stops) { StopClock(stops) }
    val uncertain = remember(stops) { stops.any { it.estimated } }
    val beat = rememberBeat()
    // New live times move the vehicle; it glides there instead of jumping (see Glide).
    val glide = remember { Glide(1) }
    val place = remember { DoubleArray(1) }
    Column {
        // Above the list, out of its scrolling: where the app estimated times, it says so.
        if (uncertain) {
            Text(
                text = stringResource(R.string.prognosis_uncertain),
                color = AppColors.Muted,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
            )
        }
        Box(
            modifier = Modifier
                .heightIn(max = maxHeight)
                .fadeEdges(scroll, horizontal = false)
                .verticalScroll(scroll)
                .padding(horizontal = 8.dp, vertical = 10.dp),
        ) {
            Column {
                stops.forEachIndexed { index, stop ->
                    CourseRow(
                        stop = stop,
                        color = color,
                        first = index == 0,
                        last = index == stops.lastIndex,
                        passed = ahead < 0 || index < ahead,
                        strong = index == focus,
                        town = town,
                        onShowStop = onShowStop,
                        onStopAction = onStopAction,
                    )
                }
            }
            // The vehicle on the rail, between the stops it is between right now. It is placed
            // by the clock at every beat, so it moves evenly from row to row.
            val shape = RoundedCornerShape(7.dp)
            Box(
                modifier = Modifier
                    .offset {
                        beat.intValue
                        val millis = System.currentTimeMillis()
                        // The live position where there is one, the same as on the map; else the timetable.
                        val live = liveProgress?.invoke(SystemClock.uptimeMillis())
                        if (live != null || clock.active(millis)) {
                            place[0] = (live ?: clock.progress(millis)).toDouble()
                            glide.follow(place, millis)
                            IntOffset(
                                (COURSE_RAIL_CENTRE - COURSE_VEHICLE_WIDTH / 2).roundToPx(),
                                (rowPixels * (place[0].toFloat() + 0.5f)).roundToInt() - 7.dp.roundToPx(),
                            )
                        } else {
                            glide.reset()
                            // Not on the road: far outside of the list.
                            IntOffset(-10_000, -10_000)
                        }
                    }
                    .size(width = COURSE_VEHICLE_WIDTH, height = 14.dp)
                    .clip(shape)
                    .background(AppColors.Lime)
                    .border(2.dp, AppColors.OnLime, shape),
            )
        }
    }
}

// Where the rail of a course runs, from the left edge of its rows: their padding, the time and the gap behind it.
private val COURSE_RAIL_CENTRE = 8.dp + 64.dp + 10.dp + 9.dp
private val COURSE_VEHICLE_WIDTH = 22.dp

/**
 * One stop of a course: its time, its place on the rail of the line and its name. Stops
 * the vehicle has [passed] are grey; the stop the course was opened from is [strong].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CourseRow(
    stop: Stopover,
    color: Color,
    first: Boolean,
    last: Boolean,
    passed: Boolean,
    strong: Boolean,
    town: String?,
    onShowStop: (Stop) -> Unit,
    onStopAction: (Stop, StopAction) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val place = stop.place
    // The time to leave counts; the last stop only has the time to arrive.
    val time = stop.departure ?: stop.arrival
    val planned = if (stop.departure != null) stop.plannedDeparture else stop.plannedArrival
    val delayMinutes = stop.delaySeconds?.let { it / 60 }
    // Expected or timetable time, with or without "+2", as the delay setting says (see DelayDisplay).
    val display = LocalDelayDisplay.current
    val rail = if (passed) AppColors.Rail else color
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(COURSE_ROW)
                .clip(RoundedCornerShape(12.dp))
                .then(
                    if (place == null) {
                        Modifier
                    } else {
                        Modifier.combinedClickable(
                            onClickLabel = stringResource(R.string.show_on_map),
                            onLongClickLabel = stringResource(R.string.stop_menu),
                            onLongClick = { menuOpen = true },
                            onClick = { onShowStop(place) },
                        )
                    },
                )
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.width(64.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            ) {
                // With the delay next to it; live times are green or red, timetable times dark.
                // A time the app estimated is grey with "ca." and without a delay: it is a guess.
                if (stop.estimated && !passed) {
                    Text(
                        text = stringResource(R.string.estimated_prefix),
                        color = AppColors.Muted,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                } else if (delayMinutes != null && delayMinutes != 0 && !passed && display.showsDelay) {
                    Text(
                        text = stringResource(R.string.delay_short, delayMinutes),
                        color = if (delayMinutes > 0) AppColors.DelayRed else AppColors.Early,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
                Text(
                    text = time?.let { (if (stop.estimated) it else display.clockOf(it, planned)).clock() } ?: "",
                    color = when {
                        passed || stop.estimated -> AppColors.Muted
                        else -> display.timeColor(delayMinutes != null, (delayMinutes ?: 0).toLong(), AppColors.Ink)
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                )
            }
            Canvas(
                modifier = Modifier
                    .width(18.dp)
                    .fillMaxHeight(),
            ) {
                val middle = Offset(size.width / 2f, size.height / 2f)
                val top = if (first) middle.y else 0f
                val bottom = if (last) middle.y else size.height
                drawLine(rail, Offset(middle.x, top), Offset(middle.x, bottom), strokeWidth = 6.dp.toPx())
                if (strong) {
                    drawCircle(AppColors.Card, radius = 9.dp.toPx(), center = middle)
                    drawCircle(AppColors.Ink, radius = 6.5.dp.toPx(), center = middle, style = Stroke(5.dp.toPx()))
                } else {
                    drawCircle(AppColors.Card, radius = 6.dp.toPx(), center = middle)
                    drawCircle(rail, radius = 4.5.dp.toPx(), center = middle, style = Stroke(3.dp.toPx()))
                }
            }
            Text(
                text = townedStopName(stop.name, town),
                color = if (passed) AppColors.Muted else AppColors.Ink,
                fontWeight = if (strong) FontWeight.ExtraBold else FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // The menu of the stop in plain sight; the long press on the row is a shortcut.
            if (place != null) MoreDots(label = stringResource(R.string.stop_menu), onClick = { menuOpen = true }, size = 32.dp)
        }
        if (place != null) {
            StopMenu(
                open = menuOpen,
                onDismiss = { menuOpen = false },
                onAction = { action -> onStopAction(place, action) },
            )
        }
    }
}

/**
 * The stops of a course on the map: a ring on the line for each, and a name at both ends
 * and at the stop the course was opened from.
 */
@Composable
fun CourseMarkers(map: MapLibreMap?, cameraTick: () -> Int, course: TripCourse, fromStop: String?) {
    if (map == null) return
    val color = lineColor(course)
    // Each ring sits on the line: a stop is put on the nearest point of the way, where that is near.
    val placed = remember(course.stops, course.path) {
        course.stops.mapNotNull { stop -> stop.place?.position?.let { stop to snapToPath(it, course.path) } }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Read here, so the rings are redrawn whenever the map moves.
        cameraTick()
        placed.forEach { (_, position) ->
            val point = map.projection.toScreenLocation(LatLng(position.first, position.second))
            val spot = Offset(point.x, point.y)
            drawCircle(AppColors.Card, radius = 5.5.dp.toPx(), center = spot)
            drawCircle(color, radius = 4.dp.toPx(), center = spot, style = Stroke(2.5.dp.toPx()))
        }
    }
    val named = placed.filterIndexed { index, (stop, _) ->
        index == 0 || index == placed.lastIndex || stop.name == fromStop
    }
    Box(modifier = Modifier.fillMaxSize()) {
        named.forEach { (stop, position) ->
            val strong = stop.name == fromStop
            Text(
                text = stop.name.plainStopName(),
                color = if (strong) AppColors.OnInk else AppColors.Ink,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier
                    // Read in the layout step, so moving the map costs no recomposition.
                    .offset {
                        cameraTick()
                        val point = map.projection.toScreenLocation(LatLng(position.first, position.second))
                        IntOffset(point.x.roundToInt(), point.y.roundToInt())
                    }
                    // The name stands to the right of its ring.
                    .graphicsLayer {
                        translationX = 9.dp.toPx()
                        translationY = -size.height / 2f
                    }
                    .shadow(3.dp, RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (strong) AppColors.Ink else AppColors.Card)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

// A stop farther than this from the way of its trip keeps its own position on the map.
private const val SNAP_METRES = 150.0

/** [position] moved onto the nearest point of [path], when that lies within [SNAP_METRES]; else as it is. */
internal fun snapToPath(position: Pair<Double, Double>, path: List<Pair<Double, Double>>): Pair<Double, Double> {
    if (path.size < 2) return position
    val scale = cos(Math.toRadians(position.first))
    var best = Double.MAX_VALUE
    var bestPoint = position
    for (index in 0 until path.size - 1) {
        val ax = (path[index].second - position.second) * scale
        val ay = path[index].first - position.first
        val dx = (path[index + 1].second - position.second) * scale - ax
        val dy = path[index + 1].first - position.first - ay
        val length = dx * dx + dy * dy
        val share = if (length <= 0.0) 0.0 else (-(ax * dx + ay * dy) / length).coerceIn(0.0, 1.0)
        val px = ax + dx * share
        val py = ay + dy * share
        val distance = px * px + py * py
        if (distance < best) {
            best = distance
            bestPoint = (position.first + py) to (position.second + px / scale)
        }
    }
    return if (sqrt(best) * 111_320.0 <= SNAP_METRES) bestPoint else position
}
