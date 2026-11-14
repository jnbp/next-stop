package io.github.jnbp.nextstop.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan
import kotlinx.coroutines.delay
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.Disruption
import io.github.jnbp.nextstop.data.spotMeters
import io.github.jnbp.nextstop.ui.theme.AppColors
import io.github.jnbp.nextstop.ui.theme.Appearance
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter

/*
 * The map of "your world" at the top of "My routes": a still picture of the map in the app's
 * own style (MapSnapshotter, as the small maps of walks; no live map view, nothing to scroll),
 * with the user's position, the favourite and saved places as pins (the travel time from the
 * place cards beside them, a warning mark where a notice touches them), the notices of the
 * cards as warning markers at their stops, and the saved routes as faint dashed lines. The
 * pins are drawn by Compose over the picture, so each can be tapped; a tap on the map itself
 * opens the Map tab on the same area.
 */

/** A place as a pin: [minutes] the travel time from its card, [affected] a notice touches it. */
data class WaysMapPlace(
    val key: String,
    val glyph: Glyph,
    val name: String,
    val position: Pair<Double, Double>,
    val minutes: Int?,
    val affected: Boolean,
    val onClick: () -> Unit,
)

/** A notice ([id], its headline) as a marker at one of its stops. */
data class WaysMapNotice(val id: String, val headline: String, val position: Pair<Double, Double>)

/**
 * The ground within which notices count for a place or a spot of a route (its start, its end,
 * a change): a faint circle of [radiusMeters] around [position], in the colour of warnings
 * where a notice there touches it ([affected]).
 */
data class WaysMapArea(val position: Pair<Double, Double>, val radiusMeters: Int, val affected: Boolean)

/** A saved route from one end to the other, as a faint dashed line. */
data class WaysMapRoute(val from: Pair<Double, Double>, val to: Pair<Double, Double>)

/** Where the picture looks: its centre in Web Mercator (0..1) and the zoom of MapLibre. */
data class WaysCamera(val centerX: Double, val centerY: Double, val zoom: Double) {
    /** Where [position] (latitude, longitude) lies on a picture [widthDp] × [heightDp], in dp from its top left. */
    fun place(position: Pair<Double, Double>, widthDp: Double, heightDp: Double): Pair<Double, Double> {
        val world = WAYS_WORLD_TILE * Math.pow(2.0, zoom)
        return ((waysMercatorX(position.second) - centerX) * world + widthDp / 2.0) to
            ((waysMercatorY(position.first) - centerY) * world + heightDp / 2.0)
    }

    /** How long [meters] on the ground at [latitude] are on the picture, in dp. */
    fun length(meters: Double, latitude: Double): Double {
        val world = WAYS_WORLD_TILE * Math.pow(2.0, zoom)
        return meters / (WAYS_EARTH_METERS * Math.cos(Math.toRadians(latitude))) * world
    }
}

/**
 * The points that hold [area] for the camera: its circle's northern, southern, eastern and
 * western edge, so a fitted picture shows the whole circle.
 */
fun areaBounds(area: WaysMapArea): List<Pair<Double, Double>> {
    val (latitude, longitude) = area.position
    val north = area.radiusMeters / WAYS_METERS_PER_DEGREE
    val east = area.radiusMeters / (WAYS_METERS_PER_DEGREE * Math.cos(Math.toRadians(latitude)).coerceAtLeast(0.01))
    return listOf(latitude + north to longitude, latitude - north to longitude, latitude to longitude + east, latitude to longitude - east)
}

/**
 * The camera that shows all of [points] on a picture [widthDp] × [heightDp] with [padDp] to
 * spare on each side: one point close up, several as close as they fit, never closer than
 * [MAX_FIT_ZOOM] nor further than [MIN_FIT_ZOOM] (the whole city). Null without points.
 */
fun waysCamera(points: List<Pair<Double, Double>>, widthDp: Double, heightDp: Double, padDp: Double): WaysCamera? {
    if (points.isEmpty()) return null
    val xs = points.map { waysMercatorX(it.second) }
    val ys = points.map { waysMercatorY(it.first) }
    val centerX = (xs.min() + xs.max()) / 2.0
    val centerY = (ys.min() + ys.max()) / 2.0
    val spanX = xs.max() - xs.min()
    val spanY = ys.max() - ys.min()
    if (spanX < 1e-9 && spanY < 1e-9) return WaysCamera(centerX, centerY, ONE_POINT_ZOOM)
    val fit = min(
        (widthDp - 2 * padDp).coerceAtLeast(1.0) / (max(spanX, 1e-9) * WAYS_WORLD_TILE),
        (heightDp - 2 * padDp).coerceAtLeast(1.0) / (max(spanY, 1e-9) * WAYS_WORLD_TILE),
    )
    return WaysCamera(centerX, centerY, log2(fit).coerceIn(MIN_FIT_ZOOM, MAX_FIT_ZOOM))
}

/**
 * The markers of [items] on the map: each notice at its stops that have a position, each
 * stop once per notice. A notice found all over the city (more than [MAX_NOTICE_SPOTS]
 * stops, not a station's) has no one place and gets none, as in its details; but its stops
 * within one of [areas] (where it counts for a place or a route) are marked all the same.
 */
fun noticeSpots(items: List<Disruption>, areas: List<WaysMapArea> = emptyList()): List<WaysMapNotice> = items.flatMap { item ->
    val placed = item.stops.mapNotNull { it.position }.distinct()
    val shown = if (item.station || placed.size <= MAX_NOTICE_SPOTS) placed.take(MAX_NOTICE_SPOTS) else emptyList()
    val inAreas = placed.filter { at -> areas.any { spotMeters(it.position, at) <= it.radiusMeters } }.take(MAX_NOTICE_SPOTS)
    (shown + inAreas).distinct().map { WaysMapNotice(item.id, item.summary.ifEmpty { item.text }, it) }
}

/**
 * The map header of "My routes", [WAYS_MAP_HEIGHT] high with rounded corners (see the top of this
 * file). [onNotice] gets the id of a tapped notice; [onMap] the points the picture shows, for
 * the Map tab. Without places it centres on [here] and asks to save places.
 */
@Composable
fun WaysMapHeader(
    here: Pair<Double, Double>?,
    places: List<WaysMapPlace>,
    notices: List<WaysMapNotice>,
    routes: List<WaysMapRoute>,
    onNotice: (String) -> Unit,
    onMap: (List<Pair<Double, Double>>) -> Unit,
    modifier: Modifier = Modifier,
    areas: List<WaysMapArea> = emptyList(),
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val animate = rememberAnimationsOn()
    val styleUrl = mapStyleUrl(Appearance.mapStyle, AppColors.palette.dark)
    val description = stringResource(R.string.ways_map_description)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(WAYS_MAP_HEIGHT.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(AppColors.Chip)
            .border(1.dp, AppColors.GlassBorder, RoundedCornerShape(28.dp)),
    ) {
        val widthDp = maxWidth.value.toInt().coerceAtLeast(1)
        // The circles of the radius are held whole as well.
        val fitPoints = places.map { it.position } + notices.map { it.position } + listOfNotNull(here) +
            areas.flatMap { areaBounds(it) }
        val shownPoints = fitPoints.ifEmpty { listOf(BERLIN_MIDDLE) }
        val camera = remember(shownPoints, widthDp) {
            waysCamera(shownPoints, widthDp.toDouble(), WAYS_MAP_HEIGHT.toDouble(), MAP_PAD)
                ?: WaysCamera(waysMercatorX(BERLIN_MIDDLE.second), waysMercatorY(BERLIN_MIDDLE.first), MIN_FIT_ZOOM)
        }
        val key = String.format(Locale.ROOT, "%s|%d|%.6f|%.6f|%.2f", styleUrl, widthDp, camera.centerX, camera.centerY, camera.zoom)
        var picture by remember(key) { mutableStateOf<Bitmap?>(WaysMapCache.pictures.get(key)) }
        LaunchedEffect(key) {
            if (picture != null) return@LaunchedEffect
            val made = snapshotBase(context, styleUrl, widthDp, WAYS_MAP_HEIGHT, camera, density)
            if (made != null) {
                WaysMapCache.pictures.put(key, made)
                picture = made
            }
        }
        // The picture settles in as a camera would: from a little closer, fading in.
        val settle = remember(key) { Animatable(if (animate && picture == null) 0f else 1f) }
        LaunchedEffect(picture != null, key) {
            if (picture != null && settle.value < 1f) settle.animateTo(1f, tween(MAP_SETTLE_MILLIS))
        }
        val width = widthDp.toDouble()
        val height = WAYS_MAP_HEIGHT.toDouble()
        fun inside(at: Pair<Double, Double>): Boolean = at.first in -8.0..(width + 8.0) && at.second in -8.0..(height + 8.0)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = description }
                .clickable(role = Role.Button) { onMap(shownPoints) },
        ) {
            picture?.let { shown ->
                Image(
                    bitmap = shown.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = settle.value
                            val scale = 1f + (1f - settle.value) * 0.06f
                            scaleX = scale
                            scaleY = scale
                        },
                )
            }
        }
        // The saved routes, faint and dashed; under them the circles of the radius, as faint.
        val routeInk = AppColors.Ink.copy(alpha = 0.35f)
        val areaInk = AppColors.Ink.copy(alpha = 0.22f)
        val areaWarning = AppColors.Warning
        Canvas(modifier = Modifier.fillMaxSize()) {
            areas.forEach { area ->
                val (x, y) = camera.place(area.position, width, height)
                val radiusPx = (camera.length(area.radiusMeters.toDouble(), area.position.first) * density).toFloat()
                val center = Offset((x * density).toFloat(), (y * density).toFloat())
                val ink = if (area.affected) areaWarning else areaInk
                drawCircle(color = ink.copy(alpha = if (area.affected) 0.10f else 0.05f), radius = radiusPx, center = center)
                drawCircle(
                    color = ink.copy(alpha = if (area.affected) 0.55f else 0.30f),
                    radius = radiusPx,
                    center = center,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
            val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
            routes.forEach { route ->
                val (ax, ay) = camera.place(route.from, width, height)
                val (bx, by) = camera.place(route.to, width, height)
                drawLine(
                    color = routeInk,
                    start = Offset((ax * density).toFloat(), (ay * density).toFloat()),
                    end = Offset((bx * density).toFloat(), (by * density).toFloat()),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = dash,
                )
            }
        }
        val ready = picture != null
        // Where the user is: a dot with a white rim.
        here?.let { camera.place(it, width, height) }?.takeIf { inside(it) }?.let { (x, y) ->
            Box(
                modifier = Modifier
                    .offset((x - 8).dp, (y - 8).dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(androidx.compose.ui.graphics.Color.White)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(AppColors.Blue),
            )
        }
        notices.forEachIndexed { index, notice ->
            val at = camera.place(notice.position, width, height)
            if (!inside(at)) return@forEachIndexed
            MapAppear(ready = ready, animate = animate, order = index + places.size) { appear ->
                Box(
                    modifier = Modifier
                        .offset((at.first - NOTICE_EDGE / 2).dp, (at.second - NOTICE_EDGE / 2).dp)
                        .size(NOTICE_EDGE.dp)
                        .graphicsLayer {
                            scaleX = appear
                            scaleY = appear
                            alpha = appear
                        }
                        .clip(CircleShape)
                        .background(AppColors.WarningContainer)
                        .border(1.5.dp, AppColors.Warning, CircleShape)
                        .semantics { contentDescription = notice.headline }
                        .clickable(role = Role.Button) { onNotice(notice.id) },
                    contentAlignment = Alignment.Center,
                ) {
                    WarningMark(size = 14.dp)
                }
            }
        }
        places.forEachIndexed { index, place ->
            val at = camera.place(place.position, width, height)
            if (!inside(at)) return@forEachIndexed
            MapAppear(ready = ready, animate = animate, order = index) { appear ->
                Box(
                    modifier = Modifier
                        .offset((at.first - PIN_EDGE / 2).dp, (at.second - PIN_EDGE / 2).dp)
                        .graphicsLayer {
                            scaleX = appear
                            scaleY = appear
                            alpha = appear
                        },
                ) {
                    Box(
                        modifier = Modifier
                            .size(PIN_EDGE.dp)
                            .clip(CircleShape)
                            .background(AppColors.Blue)
                            .border(2.dp, if (place.affected) AppColors.Warning else androidx.compose.ui.graphics.Color.White, CircleShape)
                            .semantics { contentDescription = place.name }
                            .clickable(role = Role.Button, onClick = place.onClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        GlyphIcon(place.glyph, color = AppColors.OnBlue, size = 17.dp)
                    }
                    // A notice touches the place: the mark of its framed card below.
                    if (place.affected) {
                        Box(
                            modifier = Modifier
                                .offset((PIN_EDGE - 10).dp, (-4).dp)
                                .size(15.dp)
                                .clip(CircleShape)
                                .background(AppColors.WarningContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            WarningMark(size = 11.dp)
                        }
                    }
                }
                place.minutes?.let { minutes ->
                    // The travel time from its card, on a small ground so it reads on any street.
                    Box(
                        modifier = Modifier
                            .offset((at.first - LABEL_WIDTH / 2).dp, (at.second + PIN_EDGE / 2 + 2).dp)
                            .width(LABEL_WIDTH.dp)
                            .graphicsLayer { alpha = appear },
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Text(
                            text = stringResource(R.string.ways_map_minutes, minutes),
                            color = AppColors.Ink,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            maxLines = 1,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(AppColors.Card)
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }
        if (places.isEmpty()) {
            Text(
                text = stringResource(R.string.ways_map_hint),
                color = AppColors.Ink,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                maxLines = 2,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppColors.Card)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * A marker that comes in once the picture is there: grows and fades in a little after the one
 * before it ([order]); at once with animations off. [content] gets how far it is in (0..1).
 */
@Composable
private fun MapAppear(ready: Boolean, animate: Boolean, order: Int, content: @Composable (Float) -> Unit) {
    val appear = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(ready) {
        if (!ready || appear.value >= 1f) return@LaunchedEffect
        delay(MAP_SETTLE_MILLIS / 2L + order.coerceAtMost(8) * PIN_STAGGER_MILLIS)
        appear.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow))
    }
    if (!ready && animate) return
    content(appear.value)
}

/** The last pictures, by look, size and camera; the page asks again only when one of them changes. */
private object WaysMapCache {
    val pictures = LruCache<String, Bitmap>(3)
}

/** The map alone at [camera], [widthDp] × [heightDp], or null when it could not be drawn in time. Call on the main thread. */
private suspend fun snapshotBase(
    context: Context,
    styleUrl: String,
    widthDp: Int,
    heightDp: Int,
    camera: WaysCamera,
    density: Float,
): Bitmap? {
    val center = LatLng(waysLatitudeOf(camera.centerY), waysLongitudeOf(camera.centerX))
    val options = MapSnapshotter.Options(widthDp, heightDp)
        .withStyleBuilder(Style.Builder().fromUri(styleUrl))
        .withCameraPosition(CameraPosition.Builder().target(center).zoom(camera.zoom).build())
        .withPixelRatio(density)
        .withLogo(false)
    // The same way as the small maps of walks: errors end the wait, one more try.
    return takeSnapshot(context, options, WAYS_SNAPSHOT_MILLIS) { it }
}

private fun waysMercatorX(longitude: Double): Double = (longitude + 180.0) / 360.0

private fun waysMercatorY(latitude: Double): Double {
    val phi = Math.toRadians(latitude.coerceIn(-85.0, 85.0))
    return (1.0 - ln(tan(phi) + 1.0 / Math.cos(phi)) / PI) / 2.0
}

private fun waysLongitudeOf(x: Double): Double = x * 360.0 - 180.0

private fun waysLatitudeOf(y: Double): Double = Math.toDegrees(Math.atan(Math.sinh(PI * (1.0 - 2.0 * y))))

/** The height of the map header in dp. */
const val WAYS_MAP_HEIGHT = 200

// The room kept around the pins when the camera fits them, in dp.
private const val MAP_PAD = 34.0

// One place alone is shown close up; several never closer than this nor further than the city.
private const val ONE_POINT_ZOOM = 14.0
private const val MAX_FIT_ZOOM = 15.0
private const val MIN_FIT_ZOOM = 9.5

// MapLibre draws the world 512 logical pixels wide at zoom 0.
private const val WAYS_WORLD_TILE = 512.0

// The picture is given up after this long (no network, no style).
private const val WAYS_SNAPSHOT_MILLIS = 15_000L

// How the picture settles in, and how far apart the pins come.
private const val MAP_SETTLE_MILLIS = 420
private const val PIN_STAGGER_MILLIS = 60L

// The sizes of the pins, the markers of notices and the labels of travel times, in dp.
private const val PIN_EDGE = 30.0
private const val NOTICE_EDGE = 26.0
private const val LABEL_WIDTH = 44.0

// A notice found at more stops than this has no one place on the map.
private const val MAX_NOTICE_SPOTS = 3

// The length of the equator, and of a degree of latitude, in metres.
private const val WAYS_EARTH_METERS = 40_075_016.686
private const val WAYS_METERS_PER_DEGREE = 111_320.0

// Where the map looks without any place or position: the middle of Berlin.
private val BERLIN_MIDDLE = 52.52 to 13.405
