package io.github.jnbp.nextstop.wear.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import android.util.Log
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import io.github.jnbp.nextstop.wear.R
import io.github.jnbp.nextstop.wear.data.BvgClient
import io.github.jnbp.nextstop.wear.data.LOG_TAG
import io.github.jnbp.nextstop.wear.data.MapImageRequest
import io.github.jnbp.nextstop.wear.data.Place
import io.github.jnbp.nextstop.wear.data.Position
import io.github.jnbp.nextstop.wear.data.RoundScreen
import io.github.jnbp.nextstop.wear.data.WatchStore
import io.github.jnbp.nextstop.wear.data.WebMercator
import io.github.jnbp.nextstop.wear.data.currentPosition
import io.github.jnbp.nextstop.wear.data.hasLocationPermission
import io.github.jnbp.nextstop.wear.data.locationGrants
import io.github.jnbp.nextstop.wear.data.nearbyStopsWithFallback
import io.github.jnbp.nextstop.wear.data.phonePosition

private val mapClient = BvgClient()

/**
 * A stop of the map with where it lies from the centre of the load, in metres east and north
 * (for distances), and in Web Mercator units (for drawing, as the map picture of the phone).
 */
private class MapStop(val place: Place, val east: Double, val north: Double, val x: Double, val y: Double)

/** Metres east and north of ([latitude0], [longitude0]): flat, good enough for the surroundings. */
private fun eastOf(latitude0: Double, longitude0: Double, longitude: Double): Double =
    (longitude - longitude0) * METRES_PER_DEGREE * cos(Math.toRadians(latitude0))

private fun northOf(latitude0: Double, latitude: Double): Double = (latitude - latitude0) * METRES_PER_DEGREE

private fun mapStopsOf(centre: Position, places: List<Place>): List<MapStop> = places.mapNotNull { place ->
    val latitude = place.latitude ?: return@mapNotNull null
    val longitude = place.longitude ?: return@mapNotNull null
    MapStop(
        place,
        eastOf(centre.latitude, centre.longitude, longitude),
        northOf(centre.latitude, latitude),
        WebMercator.x(longitude),
        WebMercator.y(latitude),
    )
}

/**
 * The map of the surroundings, north up: the stops nearby (from the BVG nearby request,
 * Transitous when BVG fails) as "H" signs and the user as a blue dot, with a scale bar. Under
 * them the map picture the phone draws for the view (see [rememberMapImage]); without the
 * phone a sketch on black. The crown
 * and a pinch zoom, a drag moves it, a double tap centres it on the user again. A tap on
 * a stop shows its name, a second tap (or the name below) opens its departures ([onStop]).
 * The position is asked for again every [OWN_REFRESH_MILLIS]; once the user has moved far
 * from where the stops were loaded, they are loaded again around the new position.
 */
@Composable
fun MapScreen(onStop: (Place) -> Unit) {
    val context = LocalContext.current
    // Where the stops were loaded around (the origin of the map), the latest position, the stops.
    var centre by remember { mutableStateOf<Position?>(null) }
    var own by remember { mutableStateOf<Position?>(null) }
    var stops by remember { mutableStateOf<List<MapStop>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var phase by remember { mutableStateOf(MapPhase.Position) }
    var problem by remember { mutableStateOf<Problem?>(null) }
    var round by remember { mutableIntStateOf(0) }
    val grants by locationGrants.collectAsState()
    val phone by WatchStore.phone.collectAsState()
    val scope = rememberCoroutineScope()
    val request = rememberLocationRequest()
    // Where stops were loaded (as the map is moved too), so the same ground is not asked again.
    val areas = remember { mutableListOf<LoadedArea>() }
    // Two clear steps, each with a time limit: the position ("Suche Standort…", the watch's
    // own fix, else the phone's last location through the Data Layer), then the stops
    // ("Lade Haltestellen…", BVG, else Transitous). Each ends in a result or an error with retry.
    LaunchedEffect(round, grants) {
        loading = true
        phase = MapPhase.Position
        problem = null
        try {
            val position = withTimeoutOrNull(POSITION_TIMEOUT_MILLIS) { currentPosition(context) }
                ?: phonePosition(context)
            if (position == null) {
                Log.d(LOG_TAG, "map: no position from the watch or the phone")
                problem = Problem.NoPosition
                return@LaunchedEffect
            }
            own = position
            phase = MapPhase.Stops
            val found = mapStopsOf(position, nearbyStopsWithFallback(mapClient, position.latitude, position.longitude, MAP_RESULTS))
            Log.d(LOG_TAG, "map: ${found.size} stops")
            centre = position
            stops = found
            areas += LoadedArea.of(position.latitude, position.longitude, found, 0.0)
            if (found.isEmpty()) problem = Problem.NoStops
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(LOG_TAG, "map: load failed: $e")
            problem = problemOf(e)
        } finally {
            loading = false
        }
    }
    // The user's position while the map is open; the stops again once far from the centre.
    val shown = centre != null && stops.isNotEmpty()
    LaunchedEffect(shown) {
        if (!shown) return@LaunchedEffect
        while (true) {
            delay(OWN_REFRESH_MILLIS)
            val fresh = try {
                currentPosition(context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } ?: continue
            own = fresh
            val from = centre ?: continue
            val east = eastOf(from.latitude, from.longitude, fresh.longitude)
            val north = northOf(from.latitude, fresh.latitude)
            if (sqrt(east * east + north * north) > RELOAD_METRES) {
                try {
                    val places = nearbyStopsWithFallback(mapClient, fresh.latitude, fresh.longitude, MAP_RESULTS)
                    if (places.isNotEmpty()) {
                        // Around the new position, the stops known so far kept.
                        val found = mapStopsOf(fresh, places)
                        areas += LoadedArea.of(fresh.latitude, fresh.longitude, found, 0.0)
                        centre = fresh
                        stops = mergeStops(found, mapStopsOf(fresh, stops.map { it.place }))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.d(LOG_TAG, "map: reload failed: $e")
                }
            }
        }
    }
    val origin = centre
    if (origin == null || stops.isEmpty()) {
        val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
        ScreenScaffold(scrollState = listState) { contentPadding ->
            ScalingLazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
                item { ListHeader { Text(stringResource(R.string.map)) } }
                if (loading) {
                    item {
                        LoadingNote(stringResource(if (phase == MapPhase.Position) R.string.map_finding_position else R.string.map_loading_stops))
                    }
                }
                problem?.let { current ->
                    if (current == Problem.NoPosition) {
                        noPositionItems(
                            permitted = hasLocationPermission(context),
                            request = request,
                            phone = phone,
                            onRetry = { round++ },
                            onReconnect = {
                                scope.launch {
                                    WatchStore.refresh(context)
                                    round++
                                }
                            },
                        )
                    } else {
                        problemItems(current, onRetry = { round++ })
                    }
                }
            }
        }
        return
    }
    // The map moved or zoomed out to ground without known stops: the stops there too (BVG,
    // else Transitous), added to those known. One load at a time; the newest view wins.
    var areaLoad by remember { mutableStateOf<Job?>(null) }
    StopsMap(
        origin = origin,
        own = own,
        stops = stops,
        onStop = onStop,
        onViewRested = { latitude, longitude, span ->
            if (areas.none { it.covers(latitude, longitude, span) }) {
                areaLoad?.cancel()
                areaLoad = scope.launch {
                    val results = (span / METRES_PER_RESULT).roundToInt().coerceIn(MAP_RESULTS, MAX_MAP_RESULTS)
                    try {
                        val places = nearbyStopsWithFallback(mapClient, latitude, longitude, results)
                        val from = centre ?: return@launch
                        val found = mapStopsOf(from, places)
                        areas += LoadedArea.of(latitude, longitude, found, span)
                        val merged = mergeStops(stops, found)
                        Log.d(LOG_TAG, "map: ${found.size} stops around the moved view, ${merged.size - stops.size} new")
                        if (merged.size != stops.size) stops = merged
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.d(LOG_TAG, "map: stops around the moved view failed: $e")
                    }
                }
            }
        },
    )
}

/**
 * Where stops were loaded: around ([latitude], [longitude]) up to [reach] metres (the farthest
 * stop found, at least the view's radius then), so a view inside it needs no load.
 */
private class LoadedArea(val latitude: Double, val longitude: Double, val reach: Double) {
    /** True when a view around ([latitude], [longitude]) reaching [span] metres lies within this area. */
    fun covers(latitude: Double, longitude: Double, span: Double): Boolean {
        val east = eastOf(this.latitude, this.longitude, longitude)
        val north = northOf(this.latitude, latitude)
        return sqrt(east * east + north * north) + span * VIEW_SHARE <= reach
    }

    companion object {
        fun of(latitude: Double, longitude: Double, found: List<MapStop>, span: Double): LoadedArea {
            val farthest = found.maxOfOrNull { stop ->
                val east = eastOf(latitude, longitude, stop.place.longitude ?: longitude)
                val north = northOf(latitude, stop.place.latitude ?: latitude)
                sqrt(east * east + north * north)
            } ?: 0.0
            return LoadedArea(latitude, longitude, maxOf(farthest, span * VIEW_SHARE + 1, MIN_AREA_METRES))
        }

        // The part of the view's radius that must lie inside: the corners may show ground without stops.
        private const val VIEW_SHARE = 0.6
        private const val MIN_AREA_METRES = 150.0
    }
}

/** [known] and the new ones of [found] (by id), at most [MAX_MAP_STOPS]. */
private fun mergeStops(known: List<MapStop>, found: List<MapStop>): List<MapStop> {
    val ids = known.mapTo(HashSet()) { it.place.id }
    return (known + found.filter { it.place.id !in ids }).take(MAX_MAP_STOPS)
}

/** The step the map is loading: the position first, then the stops around it. */
private enum class MapPhase { Position, Stops }

/** The drawn map of [stops] around [origin] with the user at [own]; see [MapScreen]. */
@Composable
private fun StopsMap(
    origin: Position,
    own: Position?,
    stops: List<MapStop>,
    onStop: (Place) -> Unit,
    onViewRested: (latitude: Double, longitude: Double, span: Double) -> Unit,
) {
    // The metres from the middle of the screen to its edge, and the point in the middle (Web Mercator units).
    var span by remember(origin) { mutableDoubleStateOf(fitSpan(origin, own, stops)) }
    var viewX by remember(origin) { mutableDoubleStateOf(WebMercator.x((own ?: origin).longitude)) }
    var viewY by remember(origin) { mutableDoubleStateOf(WebMercator.y((own ?: origin).latitude)) }
    var selected by remember { mutableStateOf<String?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val stopsState = rememberUpdatedState(stops)
    val ownState = rememberUpdatedState(own)
    val onStopState = rememberUpdatedState(onStop)
    val onViewRestedState = rememberUpdatedState(onViewRested)
    // Once the view rests after a drag or a zoom: the stops of the new ground (see MapScreen).
    LaunchedEffect(origin) {
        snapshotFlow { Triple(viewX, viewY, span) }.collectLatest { (x, y, metres) ->
            delay(STOPS_DEBOUNCE_MILLIS)
            onViewRestedState.value(WebMercator.latitude(y), WebMercator.longitude(x), metres)
        }
    }
    val density = LocalDensity.current
    val hitRadius = with(density) { HIT_RADIUS.toPx() }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            focus.requestFocus()
        } catch (e: IllegalStateException) {
            // Not attached yet: the crown then does nothing, the touch gestures still work.
        }
    }
    val textMeasurer = rememberTextMeasurer()
    val signGreen = Color(0xFF00854A)
    val signYellow = Color(0xFFF6D500)
    val chosen = stops.firstOrNull { it.place.id == selected }
    // The hint at the foot: shown for a few seconds while no stop is chosen.
    var hintShown by remember { mutableStateOf(true) }
    LaunchedEffect(chosen == null) {
        hintShown = true
        if (chosen == null) {
            delay(HINT_MILLIS)
            hintShown = false
        }
    }
    // The map picture of the phone for the view as it is now, in the style of the setting.
    val dark = rememberMapDark()
    val pixelRatio = density.density.toDouble().coerceIn(1.0, 3.0)
    val view = if (canvasSize.width > 0 && canvasSize.height > 0) {
        MapImageRequest.ofView(
            viewX,
            viewY,
            pixelsPerUnit(span, viewY, canvasSize.width, canvasSize.height),
            canvasSize.width,
            canvasSize.height,
            pixelRatio,
            dark = dark,
        )
    } else {
        null
    }
    val mapImage = rememberMapImage(MAP_IMAGE_SLOT, view)
    val image = mapImage.image
    // On the light map dark ink; on the dark map and on black (the sketch) the ink of the theme.
    val lightMap = image != null && !image.frame.dark
    val ink = if (lightMap) MAP_INK else MaterialTheme.colorScheme.onSurface
    val attribution = stringResource(R.string.map_attribution)
    // The hint at the foot, whole inside the round screen: raised a little where needed, else smaller.
    val hint = stringResource(R.string.map_hint)
    val hintStyle = MaterialTheme.typography.labelSmall
    val round = LocalConfiguration.current.isScreenRound
    val hintFit = remember(hint, hintStyle, canvasSize, round) {
        fitHint(textMeasurer, hint, hintStyle, min(canvasSize.width, canvasSize.height).toFloat(), round, density)
    }
    ScreenScaffold { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasSize = it }
                    .onRotaryScrollEvent { event ->
                        span = (span * exp(event.verticalScrollPixels / ROTARY_PIXELS_PER_E)).coerceIn(MIN_SPAN, MAX_SPAN)
                        true
                    }
                    .focusRequester(focus)
                    .focusable()
                    .pointerInput(origin) {
                        // Drag moves, pinch zooms. A drag from the left edge is left to the
                        // swipe back of the watch, so the map can be closed as every page.
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (down.position.x < size.width * EDGE_FRACTION) return@awaitEachGesture
                            var moving = false
                            var zoomSum = 1f
                            var panSum = Offset.Zero
                            do {
                                val event = awaitPointerEvent()
                                if (event.changes.any { it.isConsumed }) break
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                if (!moving) {
                                    zoomSum *= zoom
                                    panSum += pan
                                    val zoomMotion = abs(1 - zoomSum) * event.calculateCentroidSize(useCurrent = false)
                                    moving = zoomMotion > viewConfiguration.touchSlop || panSum.getDistance() > viewConfiguration.touchSlop
                                }
                                if (moving) {
                                    val perUnit = pixelsPerUnit(span, viewY, size.width, size.height)
                                    viewX -= pan.x / perUnit
                                    viewY -= pan.y / perUnit
                                    if (zoom > 0f) span = (span / zoom).coerceIn(MIN_SPAN, MAX_SPAN)
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .pointerInput(origin) {
                        detectTapGestures(
                            onDoubleTap = {
                                ownState.value?.let { position ->
                                    viewX = WebMercator.x(position.longitude)
                                    viewY = WebMercator.y(position.latitude)
                                }
                            },
                            onTap = { tap ->
                                val perUnit = pixelsPerUnit(span, viewY, size.width, size.height)
                                fun distance(stop: MapStop): Double {
                                    val dx = size.width / 2.0 + (stop.x - viewX) * perUnit - tap.x
                                    val dy = size.height / 2.0 + (stop.y - viewY) * perUnit - tap.y
                                    return sqrt(dx * dx + dy * dy)
                                }
                                val hit = stopsState.value.minByOrNull { distance(it) }?.takeIf { distance(it) <= hitRadius }
                                when {
                                    hit == null -> selected = null
                                    hit.place.id == selected -> onStopState.value(hit.place)
                                    else -> selected = hit.place.id
                                }
                            },
                        )
                    },
            ) {
                val metresPerPixel = span / (min(size.width, size.height) / 2.0)
                val perUnit = pixelsPerUnit(span, viewY, size.width.roundToInt(), size.height.roundToInt())
                fun project(x: Double, y: Double) = Offset(
                    (size.width / 2.0 + (x - viewX) * perUnit).toFloat(),
                    (size.height / 2.0 + (y - viewY) * perUnit).toFloat(),
                )
                // The map of the phone below everything, in the same projection.
                image?.let { drawMapImage(it, viewX, viewY, perUnit) }
                // A faint ring around the user at the length of the scale bar, for a feel of distance.
                val bar = scaleBarMetres(span)
                val ownAt = own?.let { project(WebMercator.x(it.longitude), WebMercator.y(it.latitude)) }
                ownAt?.let {
                    drawCircle(ink.copy(alpha = 0.15f), radius = (bar / metresPerPixel).toFloat(), center = it, style = Stroke(1.dp.toPx()))
                }
                // The stops: "H" signs, the chosen one larger and ringed white.
                val sign = textMeasurer.measure("H", TextStyle(color = signGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                stops.forEach { stop ->
                    val at = project(stop.x, stop.y)
                    if (at.x < -20f || at.y < -20f || at.x > size.width + 20f || at.y > size.height + 20f) return@forEach
                    val on = stop.place.id == selected
                    val radius = (if (on) 10.dp else 8.dp).toPx()
                    if (on) drawCircle(Color.White, radius = radius + 2.dp.toPx(), center = at)
                    drawCircle(signYellow, radius = radius, center = at)
                    drawCircle(signGreen, radius = radius, center = at, style = Stroke(1.5.dp.toPx()))
                    drawText(sign, topLeft = Offset(at.x - sign.size.width / 2f, at.y - sign.size.height / 2f))
                }
                // The user.
                ownAt?.let {
                    drawCircle(Color.White, radius = 7.dp.toPx(), center = it)
                    drawCircle(if (lightMap) OWN_BLUE_ON_MAP else OWN_BLUE, radius = 5.dp.toPx(), center = it)
                }
                // North up: a small arrow at the top, the scale bar below it; both below the
                // time of the watch at the top edge, so they do not run into it.
                val top = NORTH_TOP.toPx()
                val arrow = Path().apply {
                    moveTo(size.width / 2, top - 7.dp.toPx())
                    lineTo(size.width / 2 - 5.dp.toPx(), top + 4.dp.toPx())
                    lineTo(size.width / 2 + 5.dp.toPx(), top + 4.dp.toPx())
                    close()
                }
                drawPath(arrow, ink)
                val barPixels = (bar / metresPerPixel).toFloat()
                val barY = top + 16.dp.toPx()
                val left = size.width / 2 - barPixels / 2
                drawLine(ink, Offset(left, barY), Offset(left + barPixels, barY), strokeWidth = 2.dp.toPx())
                drawLine(ink, Offset(left, barY - 3.dp.toPx()), Offset(left, barY + 3.dp.toPx()), strokeWidth = 2.dp.toPx())
                drawLine(ink, Offset(left + barPixels, barY - 3.dp.toPx()), Offset(left + barPixels, barY + 3.dp.toPx()), strokeWidth = 2.dp.toPx())
                val label = textMeasurer.measure(distanceLabel(bar), TextStyle(color = ink, fontSize = 10.sp))
                drawText(label, topLeft = Offset(size.width / 2 - label.size.width / 2f, barY + 3.dp.toPx()))
                // The map data is OpenStreetMap's (tiles of OpenFreeMap): named on the map.
                image?.let { drawMapAttribution(textMeasurer.measureAttribution(attribution, dark = it.frame.dark), dark = it.frame.dark) }
            }
            // The chosen stop by name with its distance: a tap opens its departures.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (chosen == null && hintShown) hintFit.bottom else FOOT_BOTTOM, start = 24.dp, end = 24.dp),
            ) {
                // While the phone has sent no picture: how it is coming along, not a silent sketch.
                if (image == null) MapImageStatusLine(mapImage.status, modifier = Modifier.padding(bottom = 4.dp), onRetry = mapImage.retry)
                if (chosen != null) {
                    val distance = own?.let { position ->
                        val east = chosen.east - eastOf(origin.latitude, origin.longitude, position.longitude)
                        val north = chosen.north - northOf(origin.latitude, position.latitude)
                        sqrt(east * east + north * north).roundToInt()
                    }
                    SmallAction(
                        text = chosen.place.name + (distance?.let { " · " + distanceLabel(it.toDouble()) } ?: ""),
                        onClick = { onStop(chosen.place) },
                        icon = R.drawable.ic_schedule,
                        primary = true,
                    )
                } else {
                    // A short hint, narrow enough for the bottom of a round screen, that fades
                    // out after a few seconds; it shows again when a chosen stop is let go.
                    AnimatedVisibility(visible = hintShown, enter = fadeIn(), exit = fadeOut()) {
                        Text(
                            hint,
                            style = hintStyle.copy(fontSize = hintFit.fontSize),
                            color = if (image != null) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            // On the map the hint keeps a dark ground, so it stays readable.
                            modifier = Modifier
                                .then(
                                    if (image != null) {
                                        Modifier.background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                                    } else {
                                        Modifier
                                    },
                                )
                                .padding(horizontal = HINT_PAD, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

/** The size of the hint's text and how far above the lowest point of the screen its pill sits. */
private class HintFit(val fontSize: TextUnit, val bottom: Dp)

/**
 * Fits [text] in [style] as a pill at the foot of a [diameter] pixels wide screen: on a
 * [round] one the pill's lower edge (its narrowest place on the circle) must hold it with
 * [HINT_MARGIN] to the edge. It sits at [FOOT_BOTTOM]; raised up to [MAX_HINT_BOTTOM] where
 * it does not fit there, and its font made smaller (to [MIN_HINT_SIZE]) where not even that suffices.
 */
private fun fitHint(measurer: TextMeasurer, text: String, style: TextStyle, diameter: Float, round: Boolean, density: Density): HintFit {
    val base = if (style.fontSize.isSpecified) style.fontSize else 12.sp
    if (!round || diameter <= 0f) return HintFit(base, FOOT_BOTTOM)
    return with(density) {
        val extra = 2 * (HINT_PAD + HINT_MARGIN).toPx()
        val textWidth = measurer.measure(text, style.copy(fontSize = base), softWrap = false, maxLines = 1).size.width.toFloat()
        val needed = RoundScreen.bottomFor(diameter, textWidth + extra)
        if (needed <= MAX_HINT_BOTTOM.toPx()) {
            HintFit(base, maxOf(FOOT_BOTTOM, needed.toDp()))
        } else {
            val room = RoundScreen.chord(diameter, MAX_HINT_BOTTOM.toPx()) - extra
            val scaled = (base.value * (room / textWidth)).coerceIn(MIN_HINT_SIZE.value, base.value)
            HintFit(scaled.sp, MAX_HINT_BOTTOM)
        }
    }
}

/** Screen pixels per Web Mercator unit when [span] metres reach from the middle of a [width] x [height] screen to its edge. */
private fun pixelsPerUnit(span: Double, viewY: Double, width: Int, height: Int): Double {
    val metresPerPixel = span / (min(width, height).coerceAtLeast(1) / 2.0)
    return WebMercator.metresPerUnit(WebMercator.latitude(viewY)) / metresPerPixel
}

/** The span that shows the user and the nearest stops: the farthest of the nearest few, with a margin. */
private fun fitSpan(origin: Position, own: Position?, stops: List<MapStop>): Double {
    val ownEast = own?.let { eastOf(origin.latitude, origin.longitude, it.longitude) } ?: 0.0
    val ownNorth = own?.let { northOf(origin.latitude, it.latitude) } ?: 0.0
    val distances = stops.map { sqrt((it.east - ownEast) * (it.east - ownEast) + (it.north - ownNorth) * (it.north - ownNorth)) }.sorted()
    val reach = distances.getOrNull(FIT_STOPS - 1) ?: distances.lastOrNull() ?: DEFAULT_SPAN
    return (reach * 1.3).coerceIn(MIN_SPAN, MAX_SPAN)
}

/** A round length for the scale bar: about a third of the radius of the screen. */
private fun scaleBarMetres(span: Double): Double {
    val wanted = span / 2.5
    return SCALE_STEPS.lastOrNull { it <= wanted } ?: SCALE_STEPS.first()
}

/** "250 m", "1.5 km". */
private fun distanceLabel(metres: Double): String = when {
    metres < 1000 -> "${metres.roundToInt()} m"
    metres % 1000 == 0.0 -> "${(metres / 1000).roundToInt()} km"
    else -> "%.1f km".format(metres / 1000)
}

// The longest wait for the watch's own position before the phone is asked.
private const val POSITION_TIMEOUT_MILLIS = 12_000L

// The stops asked for around the position; loaded again once the user is this far from where they were.
private const val MAP_RESULTS = 20
private const val RELOAD_METRES = 400.0
private const val OWN_REFRESH_MILLIS = 20_000L

// The metres from the middle to the edge of the screen: the range of the zoom, the start without stops.
private const val MIN_SPAN = 60.0
private const val MAX_SPAN = 3_000.0
private const val DEFAULT_SPAN = 400.0
private const val FIT_STOPS = 5

// A turn of the crown by this many pixels zooms by the factor e.
private const val ROTARY_PIXELS_PER_E = 300f

private val HIT_RADIUS = 22.dp

// Drags that start in this part of the width at the left edge go back, not move the map.
private const val EDGE_FRACTION = 0.15f
private val SCALE_STEPS = listOf(10.0, 20.0, 50.0, 100.0, 200.0, 500.0, 1_000.0, 2_000.0)
private const val METRES_PER_DEGREE = 111_320.0
private val OWN_BLUE = Color(0xFF4FC3F7)

// On the light map of the phone: a deeper blue for the user, dark grey for arrow and scale.
private val OWN_BLUE_ON_MAP = Color(0xFF1E88E5)
private val MAP_INK = Color(0xFF37474F)

// The hint at the foot of the map fades out after this long.
private const val HINT_MILLIS = 4_000L

// The foot of the map: the chosen stop's chip and the hint, above the lowest point of the screen.
private val FOOT_BOTTOM = 14.dp
private val MAX_HINT_BOTTOM = 26.dp
private val HINT_PAD = 8.dp
private val HINT_MARGIN = 4.dp
private val MIN_HINT_SIZE = 9.sp

// Stops are loaded for a moved view once it rested this long; more of them for a wide view.
private const val STOPS_DEBOUNCE_MILLIS = 700L
private const val METRES_PER_RESULT = 40.0
private const val MAX_MAP_RESULTS = 50
private const val MAX_MAP_STOPS = 300

// The slot of the map pictures of this page (see MapImages).
private const val MAP_IMAGE_SLOT = "map"

// The top of the north arrow's base line: below the time text of the watch.
private val NORTH_TOP = 44.dp
