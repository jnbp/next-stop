package io.github.jnbp.nextstop.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.DashPathEffect
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.withLock
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.OsmLine
import io.github.jnbp.nextstop.data.WalkDetails
import io.github.jnbp.nextstop.ui.theme.AppColors
import io.github.jnbp.nextstop.ui.theme.Appearance
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshot
import org.maplibre.android.snapshotter.MapSnapshotter

/*
 * A small map of a walk for the timeline of the companion: a still picture of the map
 * (MapSnapshotter, no live map view) with the way on foot, where it starts and the stop
 * position or entrance it leads to. A tap opens the walk on the full map.
 */

// The size of the picture in dp; it is cropped to the width of the card.
private const val MINI_WIDTH_DP = 360
private const val MINI_HEIGHT_DP = 140

// The picture is given up after this long (no network, no style).
private const val SNAPSHOT_TIMEOUT_MILLIS = 15_000L

// A picture that failed is asked once more after this long (MapLibre gives up a still picture
// at the first tile, glyph or sprite that does not load, so a second try usually succeeds).
private const val SNAPSHOT_RETRY_MILLIS = 1_500L

// The picture fades in over this long.
private const val PICTURE_FADE_MILLIS = 260

// The zoom of the picture stays within these bounds.
private const val MIN_ZOOM = 13.0
private const val MAX_ZOOM = 18.0

// MapLibre draws the world 512 logical pixels wide at zoom 0.
private const val WORLD_TILE = 512.0

/** The last pictures, by walk and look, so scrolling the timeline does not ask for them again. */
private object MiniMapCache {
    val pictures = LruCache<String, Bitmap>(6)
}

/**
 * The walk along [path] (latitude, longitude, from its start to its end) as a small map with
 * rounded corners. [target] is the stop position or entrance it leads to, marked with
 * [targetLabel] ("3" for Pos. 3, "U" for an entrance); null marks the end of the path. Shows
 * nothing for a path with fewer than two points. [onClick] opens the walk on the full map.
 * For a change between two stop positions of buses or trams the start is marked like the
 * target: with [startLabel] (its number) where it has one, and each end in the colour of
 * its line ([startColor], [targetColor], as ARGB); without them the start is a ringed dot
 * and the target in the accent colour. Where [targetIsPosition] the target (and a start with
 * [startColor]) is a stop position, drawn as its pin "Pos. 4" with the line chips as on the big
 * map; the station details along the way are those of [details] (see WalkMarks).
 */
@Composable
fun WalkMiniMap(
    path: List<Pair<Double, Double>>,
    target: Pair<Double, Double>?,
    targetLabel: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    startLabel: String? = null,
    startColor: Int? = null,
    targetColor: Int? = null,
    startLines: List<OsmLine> = emptyList(),
    targetLines: List<OsmLine> = emptyList(),
    straight: Boolean = false,
    details: WalkDetails = WalkDetails.EMPTY,
    brokenLifts: List<Pair<Double, Double>> = emptyList(),
    targetIsPosition: Boolean = false,
) {
    val points = if (target != null && path.lastOrNull() != target) path + target else path
    if (points.size < 2) return
    val context = LocalContext.current
    val animate = rememberAnimationsOn()
    val density = LocalDensity.current.density
    val styleUrl = mapStyleUrl(Appearance.mapStyle, AppColors.palette.dark)
    val walkColor = AppColors.Ink.toArgb()
    // A stop position without a colour given: the colour of its first line, as on the big map.
    val accent = targetColor
        ?: if (targetIsPosition) pinColor(targetLines, points.last(), AppColors.Blue.toArgb()) else AppColors.Blue.toArgb()
    val marks = StartMark(startLabel, startColor, startLines)
    val ends = EndMarks(targetLabel, accent, targetLines, targetIsPosition)
    val key = remember(points, styleUrl, walkColor, marks, ends, details, brokenLifts) {
        points.joinToString(";") { String.format(Locale.ROOT, "%.5f,%.5f", it.first, it.second) } +
            "|" + styleUrl + "|" + walkColor + "|" + marks + "|" + ends +
            "|" + details.hashCode() + "|" + brokenLifts.hashCode()
    }
    var picture by remember(key) { mutableStateOf<Bitmap?>(MiniMapCache.pictures.get(key)) }
    var failed by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) {
        if (picture != null) return@LaunchedEffect
        val made = snapshotWalk(context, styleUrl, points, density, walkColor, marks, ends, details, brokenLifts)
        if (made != null) {
            MiniMapCache.pictures.put(key, made)
            picture = made
        } else {
            failed = true
        }
    }
    val shownAlpha by animateFloatAsState(
        targetValue = if (picture != null) 1f else 0f,
        animationSpec = if (animate) tween(PICTURE_FADE_MILLIS) else tween(0),
        label = "walkPicture",
    )
    // A walk keeps its box when the picture fails: it still opens the walk on the full map.
    val loading = picture == null && !failed
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(MINI_HEIGHT_DP.dp)
            .clip(RoundedCornerShape(18.dp))
            .then(if (loading && animate) Modifier.shimmer() else Modifier.background(AppColors.Chip))
            .clickable(onClick = onClick),
    ) {
        picture?.let { shown ->
            Image(
                bitmap = shown.asImageBitmap(),
                contentDescription = stringResource(R.string.walk_minimap),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(shownAlpha),
            )
        }
        // A way the foot router had none for is drawn straight: said so, not pretended.
        if (straight) StraightLineNote(modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
    }
}

/**
 * How the start of a walk is marked: with a [label] in a pill, in [color] (ARGB); both null for
 * a ringed dot. With a [color] it is a stop position: its pin ("Pos. 3" and [lines], see drawPositionPin).
 */
private data class StartMark(val label: String?, val color: Int?, val lines: List<OsmLine> = emptyList())

/**
 * How the target of a walk is marked: [label] ("4", or the letter of an entrance) in [color]
 * (ARGB) with its [lines]; where [position] is true it is a stop position, drawn as its pin
 * "Pos. 4" with the line chips, else a round mark with the label.
 */
private data class EndMarks(val label: String?, val color: Int, val lines: List<OsmLine>, val position: Boolean)

/** The picture of the walk, or null when the map could not be drawn in time. Call on the main thread. */
private suspend fun snapshotWalk(
    context: Context,
    styleUrl: String,
    points: List<Pair<Double, Double>>,
    density: Float,
    walkColor: Int,
    start: StartMark,
    end: EndMarks,
    details: WalkDetails,
    brokenLifts: List<Pair<Double, Double>>,
): Bitmap? {
    val width = MINI_WIDTH_DP.toDouble()
    val height = MINI_HEIGHT_DP.toDouble()
    // The camera is worked out here, so every point can be put on the picture by the same sums.
    val xs = points.map { mercatorX(it.second) }
    val ys = points.map { mercatorY(it.first) }
    val centerX = (xs.min() + xs.max()) / 2.0
    val centerY = (ys.min() + ys.max()) / 2.0
    val spanX = max(xs.max() - xs.min(), 1e-9)
    val spanY = max(ys.max() - ys.min(), 1e-9)
    // A margin around the way, so its ends and their marks stay inside.
    val fit = min((width - 2 * 36.0) / (spanX * WORLD_TILE), (height - 2 * 26.0) / (spanY * WORLD_TILE))
    val zoom = log2(fit).coerceIn(MIN_ZOOM, MAX_ZOOM)
    val center = LatLng(latitudeOf(centerY), longitudeOf(centerX))
    val options = MapSnapshotter.Options(MINI_WIDTH_DP, MINI_HEIGHT_DP)
        .withStyleBuilder(Style.Builder().fromUri(styleUrl))
        .withCameraPosition(CameraPosition.Builder().target(center).zoom(zoom).build())
        .withPixelRatio(density)
        .withLogo(false)
    return takeSnapshot(context, options, SNAPSHOT_TIMEOUT_MILLIS) { base ->
        drawWalk(base, points, centerX, centerY, zoom, density, walkColor, start, end, details, brokenLifts)
    }
}

/** [base] with the walk drawn on it: a dotted way, a ring where it starts, a marked target. */
private fun drawWalk(
    base: Bitmap,
    points: List<Pair<Double, Double>>,
    centerX: Double,
    centerY: Double,
    zoom: Double,
    density: Float,
    walkColor: Int,
    start: StartMark,
    end: EndMarks,
    details: WalkDetails = WalkDetails.EMPTY,
    brokenLifts: List<Pair<Double, Double>> = emptyList(),
): Bitmap {
    val bitmap = base.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = AndroidCanvas(bitmap)
    // Logical pixels of the camera to pixels of this picture.
    val scale = bitmap.width / MINI_WIDTH_DP.toDouble()
    val world = WORLD_TILE * Math.pow(2.0, zoom)
    fun pixel(point: Pair<Double, Double>): Pair<Float, Float> {
        val x = (mercatorX(point.second) - centerX) * world + MINI_WIDTH_DP / 2.0
        val y = (mercatorY(point.first) - centerY) * world + MINI_HEIGHT_DP / 2.0
        return (x * scale).toFloat() to (y * scale).toFloat()
    }
    val way = AndroidPath()
    points.forEachIndexed { index, point ->
        val (x, y) = pixel(point)
        if (index == 0) way.moveTo(x, y) else way.lineTo(x, y)
    }
    val casing = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.STROKE
        strokeWidth = 7f * density
        strokeCap = AndroidPaint.Cap.ROUND
        strokeJoin = AndroidPaint.Join.ROUND
        color = android.graphics.Color.WHITE
        alpha = 200
    }
    val dots = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.STROKE
        strokeWidth = 4f * density
        strokeCap = AndroidPaint.Cap.ROUND
        strokeJoin = AndroidPaint.Join.ROUND
        color = walkColor
        pathEffect = DashPathEffect(floatArrayOf(0.1f, 7f * density), 0f)
    }
    // The stop positions the walk starts or ends at, as pins (the same as on the big map).
    val pins = listOfNotNull(
        start.color?.let { color -> PositionPin(points.first(), start.label?.takeIf { it.isNotBlank() }, start.lines, color) },
        if (end.position) PositionPin(points.last(), end.label?.takeIf { it.isNotBlank() }, end.lines, end.color) else null,
    )
    // The other stop positions of the stops at the ends, faint: where the walk does not lead.
    drawWalkUnder(canvas, details, density, skip = pins.map { it.point }) { pixel(it) }
    canvas.drawPath(way, casing)
    canvas.drawPath(way, dots)
    // The entrances, lifts and escalators the way passes, on it; the pins of its stop positions.
    drawWalkOver(canvas, details, brokenLifts, density, pins) { pixel(it) }
    // Where the walk starts: a white dot with a ring.
    val (startX, startY) = pixel(points.first())
    val fill = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    val ring = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.STROKE
        strokeWidth = 3f * density
        color = walkColor
    }
    // A pill in [groundColor] with [label] at a point, or a dot where there is no label.
    fun mark(x: Float, y: Float, label: String?, groundColor: Int) {
        val ground = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { color = groundColor }
        if (label == null) {
            canvas.drawCircle(x, y, 9f * density, fill)
            canvas.drawCircle(x, y, 7f * density, ground)
            return
        }
        val ink = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = if (lightGround(groundColor)) 0xFF16130A.toInt() else android.graphics.Color.WHITE
            textSize = 12f * density
            typeface = Typeface.DEFAULT_BOLD
            textAlign = AndroidPaint.Align.CENTER
        }
        val textWidth = ink.measureText(label)
        val halfWidth = max(textWidth / 2f + 7f * density, 11f * density)
        val halfHeight = 11f * density
        val box = RectF(x - halfWidth, y - halfHeight, x + halfWidth, y + halfHeight)
        val outline = RectF(box.left - 2f * density, box.top - 2f * density, box.right + 2f * density, box.bottom + 2f * density)
        canvas.drawRoundRect(outline, halfHeight + 2f * density, halfHeight + 2f * density, fill)
        canvas.drawRoundRect(box, halfHeight, halfHeight, ground)
        val baseline = y - (ink.descent() + ink.ascent()) / 2f
        canvas.drawText(label, x, baseline, ink)
    }
    // Where the walk starts, unless at a stop position (its pin): a white dot with a ring, or
    // the exit it leaves by with its letter.
    if (start.color == null) {
        if (start.label == null) {
            canvas.drawCircle(startX, startY, 6f * density, fill)
            canvas.drawCircle(startX, startY, 6f * density, ring)
        } else {
            mark(startX, startY, start.label.takeIf { it.isNotBlank() }, walkColor)
        }
    }
    // A target that is no stop position: a round mark in the accent colour with its letter, or a dot.
    if (!end.position) {
        val (endX, endY) = pixel(points.last())
        mark(endX, endY, end.label?.takeIf { it.isNotBlank() }, end.color)
    }
    return bitmap
}

/** True for a light colour (ARGB), which takes dark ink. */
private fun lightGround(color: Int): Boolean {
    val r = (color shr 16 and 0xFF) / 255.0
    val g = (color shr 8 and 0xFF) / 255.0
    val b = (color and 0xFF) / 255.0
    return 0.2126 * r + 0.7152 * g + 0.0722 * b > 0.6
}

/**
 * The still picture of [options], passed through [draw] (null drops it), or null when MapLibre
 * reports an error or nothing comes within [timeoutMillis]. A failed picture is reported
 * through the error handler only (MapLibre gives up a still picture at the first resource
 * that does not load: a tile, a glyph range, the sprite); without one the call waited for the
 * whole timeout. Tried a second time after a short pause. One picture at a time in the app
 * (see [SnapshotGate]). Call on the main thread.
 */
internal suspend fun takeSnapshot(
    context: Context,
    options: MapSnapshotter.Options,
    timeoutMillis: Long,
    draw: (Bitmap) -> Bitmap?,
): Bitmap? {
    repeat(2) { attempt ->
        if (attempt > 0) delay(SNAPSHOT_RETRY_MILLIS)
        // One picture at a time in the app (see SnapshotGate); the wait does not count against the time limit.
        SnapshotGate.lock.withLock { snapshotOnce(context, options, timeoutMillis, draw) }?.let { return it }
    }
    return null
}

private suspend fun snapshotOnce(
    context: Context,
    options: MapSnapshotter.Options,
    timeoutMillis: Long,
    draw: (Bitmap) -> Bitmap?,
): Bitmap? {
    val handler = Handler(Looper.getMainLooper())
    return withTimeoutOrNull(timeoutMillis) {
        suspendCancellableCoroutine<Bitmap?> { continuation ->
            // Held by the cancel handler: MapLibre keeps only a weak reference to it.
            val snapshotter = MapSnapshotter(context, options)
            continuation.invokeOnCancellation { handler.post { snapshotter.cancel() } }
            snapshotter.start(
                object : MapSnapshotter.SnapshotReadyCallback {
                    override fun onSnapshotReady(snapshot: MapSnapshot) {
                        val drawn = try {
                            draw(snapshot.bitmap)
                        } catch (e: RuntimeException) {
                            null
                        }
                        if (continuation.isActive) continuation.resume(drawn)
                    }
                },
                MapSnapshotter.ErrorHandler { _ ->
                    if (continuation.isActive) continuation.resume(null)
                },
            )
        }
    }
}

/** True for a place a map can be drawn at: real numbers, on the map, not the empty 0,0. */
fun plausibleSpot(spot: Pair<Double, Double>): Boolean =
    spot.first.isFinite() && spot.second.isFinite() &&
        spot.first in -85.0..85.0 && spot.second in -180.0..180.0 &&
        !(spot.first == 0.0 && spot.second == 0.0)

// Web Mercator, 0 to 1 over the world.
private fun mercatorX(longitude: Double): Double = (longitude + 180.0) / 360.0

private fun mercatorY(latitude: Double): Double {
    val phi = Math.toRadians(latitude.coerceIn(-85.0, 85.0))
    return (1.0 - ln(tan(phi) + 1.0 / Math.cos(phi)) / PI) / 2.0
}

private fun longitudeOf(x: Double): Double = x * 360.0 - 180.0

private fun latitudeOf(y: Double): Double = Math.toDegrees(Math.atan(Math.sinh(PI * (1.0 - 2.0 * y))))

/**
 * A small still map of [spots] (latitude, longitude), each a dot in the warning colour: the
 * place a disruption notice is about. One spot is shown close up, several together. Shows
 * nothing without a plausible spot (see plausibleSpot), and nothing once the picture could
 * not be made: no empty box. While it loads the box shimmers; the picture fades in.
 * A tap runs [onClick] (the place on the full map).
 */
@Composable
fun SpotsMiniMap(
    spots: List<Pair<Double, Double>>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val placed = remember(spots) { spots.filter(::plausibleSpot).distinct() }
    if (placed.isEmpty()) return
    SpotsMiniMapOf(placed, onClick, modifier, description)
}

@Composable
private fun SpotsMiniMapOf(
    spots: List<Pair<Double, Double>>,
    onClick: () -> Unit,
    modifier: Modifier,
    description: String?,
) {
    val context = LocalContext.current
    val animate = rememberAnimationsOn()
    val density = LocalDensity.current.density
    val styleUrl = mapStyleUrl(Appearance.mapStyle, AppColors.palette.dark)
    val accent = AppColors.Warning.toArgb()
    val key = remember(spots, styleUrl, accent) {
        "spots|" + spots.joinToString(";") { String.format(Locale.ROOT, "%.5f,%.5f", it.first, it.second) } +
            "|" + styleUrl + "|" + accent
    }
    var picture by remember(key) { mutableStateOf<Bitmap?>(MiniMapCache.pictures.get(key)) }
    var failed by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) {
        if (picture != null) return@LaunchedEffect
        val made = snapshotSpots(context, styleUrl, spots, density, accent)
        if (made != null) {
            MiniMapCache.pictures.put(key, made)
            picture = made
        } else {
            failed = true
        }
    }
    // No picture to show: no box either.
    if (failed && picture == null) return
    val shownAlpha by animateFloatAsState(
        targetValue = if (picture != null) 1f else 0f,
        animationSpec = if (animate) tween(PICTURE_FADE_MILLIS) else tween(0),
        label = "spotsPicture",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(MINI_HEIGHT_DP.dp)
            .clip(RoundedCornerShape(18.dp))
            .then(if (picture == null && animate) Modifier.shimmer() else Modifier.background(AppColors.Chip))
            .clickable(onClick = onClick),
    ) {
        picture?.let { shown ->
            Image(
                bitmap = shown.asImageBitmap(),
                contentDescription = description,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(shownAlpha),
            )
        }
    }
}

// The zoom of a map of one spot: the stop and the streets around it.
private const val SPOT_ZOOM = 16.0

/** The picture of [spots] with a dot on each, or null when the map could not be drawn in time. Call on the main thread. */
private suspend fun snapshotSpots(
    context: Context,
    styleUrl: String,
    spots: List<Pair<Double, Double>>,
    density: Float,
    accent: Int,
): Bitmap? {
    val width = MINI_WIDTH_DP.toDouble()
    val height = MINI_HEIGHT_DP.toDouble()
    val xs = spots.map { mercatorX(it.second) }
    val ys = spots.map { mercatorY(it.first) }
    val centerX = (xs.min() + xs.max()) / 2.0
    val centerY = (ys.min() + ys.max()) / 2.0
    val zoom = if (spots.size == 1) {
        SPOT_ZOOM
    } else {
        val spanX = max(xs.max() - xs.min(), 1e-9)
        val spanY = max(ys.max() - ys.min(), 1e-9)
        val fit = min((width - 2 * 30.0) / (spanX * WORLD_TILE), (height - 2 * 24.0) / (spanY * WORLD_TILE))
        log2(fit).coerceIn(MIN_ZOOM - 2.0, SPOT_ZOOM)
    }
    val center = LatLng(latitudeOf(centerY), longitudeOf(centerX))
    val options = MapSnapshotter.Options(MINI_WIDTH_DP, MINI_HEIGHT_DP)
        .withStyleBuilder(Style.Builder().fromUri(styleUrl))
        .withCameraPosition(CameraPosition.Builder().target(center).zoom(zoom).build())
        .withPixelRatio(density)
        .withLogo(false)
    return takeSnapshot(context, options, SNAPSHOT_TIMEOUT_MILLIS) { base ->
        drawSpots(base, spots, centerX, centerY, zoom, density, accent)
    }
}

/** [base] with a dot in [accent] and a white rim on each of [spots]. */
private fun drawSpots(
    base: Bitmap,
    spots: List<Pair<Double, Double>>,
    centerX: Double,
    centerY: Double,
    zoom: Double,
    density: Float,
    accent: Int,
): Bitmap {
    val bitmap = base.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = AndroidCanvas(bitmap)
    val scale = bitmap.width / MINI_WIDTH_DP.toDouble()
    val world = WORLD_TILE * Math.pow(2.0, zoom)
    val rim = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    val dot = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { color = accent }
    spots.forEach { spot ->
        val x = ((mercatorX(spot.second) - centerX) * world + MINI_WIDTH_DP / 2.0) * scale
        val y = ((mercatorY(spot.first) - centerY) * world + MINI_HEIGHT_DP / 2.0) * scale
        canvas.drawCircle(x.toFloat(), y.toFloat(), 10f * density, rim)
        canvas.drawCircle(x.toFloat(), y.toFloat(), 7.5f * density, dot)
    }
    return bitmap
}
