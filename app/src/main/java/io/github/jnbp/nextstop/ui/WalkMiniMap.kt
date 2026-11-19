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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
 * position or entrance it leads to. A tap opens the walk on the full map; in the calm
 * companion it opens larger in place, a larger picture to move and zoom with the fingers (#114).
 */

// The size of the picture in dp; it is cropped to the width of the card.
private const val MINI_WIDTH_DP = 360
private const val MINI_HEIGHT_DP = 140

// The height of a small map opened in place (the calm companion, see LocalMiniMapInPlace).
private const val TALL_HEIGHT_DP = 300

/**
 * True where a tap on a small map of a walk opens it larger in place instead of showing the walk
 * on the big map: the calm companion, which keeps its view (#61). A second tap makes it small again.
 */
val LocalMiniMapInPlace = staticCompositionLocalOf { false }

// The picture is given up after this long (no network, no style).
private const val SNAPSHOT_TIMEOUT_MILLIS = 15_000L

// A picture that failed is asked once more after this long (MapLibre gives up a still picture
// at the first tile, glyph or sprite that does not load, so a second try usually succeeds).
private const val SNAPSHOT_RETRY_MILLIS = 1_500L

// The picture fades in over this long.
private const val PICTURE_FADE_MILLIS = 260

// The fingers rest this long on an opened small map before the part in view is drawn sharp (#190).
private const val SHARP_SETTLE_MILLIS = 350L

// The sharp part of an opened small map is drawn at no closer zoom than this (#190).
private const val SHARP_MAX_ZOOM = 20.0

// The zoom of the picture stays within these bounds; a long walk is shown whole (#114).
private const val MIN_ZOOM = 11.0
private const val MAX_ZOOM = 18.0

// The picture is as wide as its card, within these bounds (dp).
private const val MIN_PICTURE_WIDTH_DP = 200
private const val MAX_PICTURE_WIDTH_DP = 600

// The room the walk keeps from the edges of its picture (dp): more on top, where the pins of
// stop positions stand above their point (#114).
private const val FRAME_SIDE_DP = 28.0
private const val FRAME_TOP_DP = 24.0
private const val FRAME_PIN_TOP_DP = 46.0
private const val FRAME_BOTTOM_DP = 22.0

// An opened small map (#114): its picture reaches this many times the box on each side, to
// move around in; zoomed out it shows all of it, in up to this much.
private const val TALL_SPAN = 1.5f
private const val OPENED_MIN_SCALE = 1f / TALL_SPAN
private const val OPENED_MAX_SCALE = 3f

// MapLibre draws the world 512 logical pixels wide at zoom 0.
private const val WORLD_TILE = 512.0

/** The last pictures, by walk and look, so scrolling the timeline does not ask for them again. */
private object MiniMapCache {
    val pictures = LruCache<String, Bitmap>(6)

    /** The one opened picture (#114), larger: kept apart, so it does not push the small ones out. */
    val opened = LruCache<String, Bitmap>(1)
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
 * Where the walk ends at the destination of the trip ([destination]), that end is the round
 * badge of the big map (#181b), smaller (see MINI_BADGE_RADIUS_DP), instead of its own mark.
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
    destination: Boolean = false,
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
    // The destination: the badge in the colours of the big map's, as the map looks now.
    val flag = if (destination) {
        val look = rememberFlagLook()
        MiniFlag(look.fill.toArgb(), look.ink.toArgb(), look.glyph.toArgb(), look.halo?.toArgb())
    } else {
        null
    }
    val ends = EndMarks(targetLabel, accent, targetLines, targetIsPosition && flag == null, flag)
    // Opened larger in place by a tap (#61): a taller picture of its own, the small one meanwhile.
    val inPlace = LocalMiniMapInPlace.current
    var tall by remember { mutableStateOf(false) }
    val opened = inPlace && tall
    val heightDp = if (opened) TALL_HEIGHT_DP else MINI_HEIGHT_DP
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
    // The picture as wide as its card, so nothing of the walk is cropped away at its sides (#114).
    val widthDp = if (maxWidth.value.isFinite()) maxWidth.value.roundToInt().coerceIn(MIN_PICTURE_WIDTH_DP, MAX_PICTURE_WIDTH_DP) else MINI_WIDTH_DP
    // The walk framed in the room of the box, its pins clear of the edges (#114). Opened, the
    // picture reaches beyond the box on every side, to move around in (see panMiniView).
    val pins = marks.color != null || ends.position
    val frame = remember(points, widthDp, heightDp, pins) {
        miniMapFrame(
            points,
            widthDp.toDouble(),
            heightDp.toDouble(),
            side = FRAME_SIDE_DP,
            top = if (pins) FRAME_PIN_TOP_DP else FRAME_TOP_DP,
            bottom = FRAME_BOTTOM_DP,
            minZoom = MIN_ZOOM,
            maxZoom = MAX_ZOOM,
        )
    }
    val pictureWidth = if (opened) (widthDp * TALL_SPAN).roundToInt() else widthDp
    val pictureHeight = if (opened) (heightDp * TALL_SPAN).roundToInt() else heightDp
    val key = remember(points, styleUrl, walkColor, marks, ends, details, brokenLifts, pictureWidth, pictureHeight, frame) {
        points.joinToString(";") { String.format(Locale.ROOT, "%.5f,%.5f", it.first, it.second) } +
            "|" + styleUrl + "|" + walkColor + "|" + marks + "|" + ends +
            "|" + details.hashCode() + "|" + brokenLifts.hashCode() + "|" + pictureWidth + "x" + pictureHeight + "|" + frame
    }
    val cache = if (opened) MiniMapCache.opened else MiniMapCache.pictures
    // The last picture shown, and whether it was an opened one, stand in while the next one is made.
    val lastPicture = remember { arrayOf<Bitmap?>(null) }
    val lastOpened = remember { booleanArrayOf(false) }
    var picture by remember(key) { mutableStateOf<Bitmap?>(cache.get(key) ?: lastPicture[0]) }
    var pictureOpened by remember(key) { mutableStateOf(if (cache.get(key) != null) opened else lastOpened[0]) }
    var fresh by remember(key) { mutableStateOf(cache.get(key) != null) }
    var failed by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) {
        if (fresh) return@LaunchedEffect
        val made = snapshotWalk(context, styleUrl, points, density, walkColor, marks, ends, details, brokenLifts, frame, pictureWidth, pictureHeight)
        if (made != null) {
            cache.put(key, made)
            picture = made
            pictureOpened = opened
            fresh = true
        } else {
            failed = true
        }
    }
    picture?.let {
        lastPicture[0] = it
        lastOpened[0] = pictureOpened
    }
    val boxHeight by animateDpAsState(
        heightDp.dp,
        if (animate) spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow) else snap(),
        label = "walkMapHeight",
    )
    val shownAlpha by animateFloatAsState(
        targetValue = if (picture != null) 1f else 0f,
        animationSpec = if (animate) tween(PICTURE_FADE_MILLIS) else tween(0),
        label = "walkPicture",
    )
    // How the opened picture is looked at: moved with one finger, zoomed with two (#114); back
    // to its middle each time it is opened. No live map: the one picture, moved and scaled.
    var view by remember(opened) { mutableStateOf(MiniView()) }
    val viewSize = remember { floatArrayOf(0f, 0f) }
    val pixels = LocalDensity.current
    // #190: zoomed in with the fingers, the one picture was only scaled up and went soft. Once the
    // fingers have rested a moment, the part in view is drawn anew at the zoom it is seen at, the
    // size of the box (see miniSharpRequest), and laid over the picture where it belongs, so it
    // moves and scales with it. The one before stays until the new one is there; one per map.
    var sharp by remember(key, opened) { mutableStateOf<Pair<MiniSharp, Bitmap>?>(null) }
    LaunchedEffect(key, opened, pictureOpened, view) {
        if (!opened || !pictureOpened || picture == null) return@LaunchedEffect
        delay(SHARP_SETTLE_MILLIS)
        val ask = miniSharpRequest(frame, pictureWidth, pictureHeight, view, viewSize[0], viewSize[1], density, SHARP_MAX_ZOOM)
            ?: return@LaunchedEffect
        if (sharp?.first == ask) return@LaunchedEffect
        val made = snapshotWalk(context, styleUrl, points, density, walkColor, marks, ends, details, brokenLifts, ask.frame, ask.widthDp, ask.heightDp)
        if (made != null) sharp = ask to made
    }
    // A walk keeps its box when the picture fails: it still opens the walk on the full map.
    val loading = picture == null && !failed
    val inPlaceLabel = stringResource(if (tall) R.string.r5c_map_smaller else R.string.r5c_map_bigger)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(boxHeight)
            .clip(RoundedCornerShape(18.dp))
            .onSizeChanged {
                viewSize[0] = it.width.toFloat()
                viewSize[1] = it.height.toFloat()
            }
            .then(if (loading && animate) Modifier.shimmer() else Modifier.background(AppColors.Chip))
            .then(
                if (opened) {
                    Modifier.pointerInput(pictureWidth, pictureHeight) {
                        val contentWidth = pictureWidth * pixels.density
                        val contentHeight = pictureHeight * pixels.density
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var moving = false
                            var travelled = 0f
                            do {
                                val event = awaitPointerEvent()
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val several = event.changes.count { it.pressed } > 1
                                travelled += pan.getDistance()
                                if (!moving && (several || travelled > viewConfiguration.touchSlop)) moving = true
                                if (moving) {
                                    val before = view
                                    view = panMiniView(
                                        before, zoom, pan.x, pan.y,
                                        viewSize[0], viewSize[1], contentWidth, contentHeight,
                                        minScale = OPENED_MIN_SCALE, maxScale = OPENED_MAX_SCALE,
                                    )
                                    // What the picture could not take (at its edge) is left to the page's scroll.
                                    if (several || view != before) event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                } else {
                    Modifier
                },
            )
            .then(
                if (inPlace) {
                    Modifier.clickable(onClickLabel = inPlaceLabel) { tall = !tall }
                } else {
                    Modifier.clickable(onClick = onClick)
                },
            ),
    ) {
        picture?.let { shown ->
            if (pictureOpened && opened) {
                // The opened picture at its own size, its middle in the middle of the box, moved and
                // scaled; the sharper part in view over it (#190), in the same layer, so both move as one.
                Box(
                    modifier = Modifier
                        .requiredSize(pictureWidth.dp, pictureHeight.dp)
                        .graphicsLayer {
                            alpha = shownAlpha
                            scaleX = view.scale
                            scaleY = view.scale
                            translationX = view.x
                            translationY = view.y
                        },
                ) {
                    Image(
                        bitmap = shown.asImageBitmap(),
                        contentDescription = stringResource(R.string.walk_minimap),
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize(),
                    )
                    sharp?.let { (at, bitmap) ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                .offset(at.left.dp, at.top.dp)
                                .requiredSize(at.width.dp, at.height.dp),
                        )
                    }
                }
            } else {
                Image(
                    bitmap = shown.asImageBitmap(),
                    contentDescription = stringResource(R.string.walk_minimap),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(shownAlpha),
                )
            }
        }
        // A way the foot router had none for is drawn straight: said so, not pretended.
        if (straight) StraightLineNote(modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
    }
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
private data class EndMarks(val label: String?, val color: Int, val lines: List<OsmLine>, val position: Boolean, val flag: MiniFlag? = null)

/** The colours (ARGB) of the destination badge on a small map: see FlagLook. */
private data class MiniFlag(val fill: Int, val ink: Int, val glyph: Int, val halo: Int? = null)

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
    frame: MiniFrame,
    widthDp: Int,
    heightDp: Int,
): Bitmap? {
    // The camera is worked out before (see miniMapFrame), so every point can be put on the picture by the same sums.
    val center = LatLng(miniLatitudeOf(frame.centerY), miniLongitudeOf(frame.centerX))
    val options = MapSnapshotter.Options(widthDp, heightDp)
        .withStyleBuilder(Style.Builder().fromUri(styleUrl))
        .withCameraPosition(CameraPosition.Builder().target(center).zoom(frame.zoom).build())
        .withPixelRatio(density)
        .withLogo(false)
    return takeSnapshot(context, options, SNAPSHOT_TIMEOUT_MILLIS) { base ->
        drawWalk(base, points, frame, widthDp, heightDp, density, walkColor, start, end, details, brokenLifts)
    }
}

/** [base] with the walk drawn on it: a dotted way, a ring where it starts, a marked target. */
private fun drawWalk(
    base: Bitmap,
    points: List<Pair<Double, Double>>,
    frame: MiniFrame,
    widthDp: Int,
    heightDp: Int,
    density: Float,
    walkColor: Int,
    start: StartMark,
    end: EndMarks,
    details: WalkDetails = WalkDetails.EMPTY,
    brokenLifts: List<Pair<Double, Double>> = emptyList(),
): Bitmap {
    val bitmap = base.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = AndroidCanvas(bitmap)
    // Logical pixels of the camera to pixels of this picture. Measured on the picture's own
    // size: a taller picture put its walk too high before, by half the difference (#114).
    val scale = bitmap.width / widthDp.toDouble()
    fun pixel(point: Pair<Double, Double>): Pair<Float, Float> {
        val x = frame.x(point.second, widthDp.toDouble())
        val y = frame.y(point.first, heightDp.toDouble())
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
    // Where the walk ends at the destination's badge, the signs near it give way (#181c).
    drawWalkOver(canvas, details, brokenLifts, density, pins, badge = end.flag?.let { points.last() }, badgeRadiusDp = MINI_BADGE_RADIUS_DP) { pixel(it) }
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
    // The destination of the trip: the badge of the big map, smaller (#181b).
    if (end.flag != null) {
        val (endX, endY) = pixel(points.last())
        drawMiniFlag(canvas, endX, endY, density, end.flag)
    } else if (!end.position) {
        // A target that is no stop position: a round mark in the accent colour with its letter, or a dot.
        val (endX, endY) = pixel(points.last())
        mark(endX, endY, end.label?.takeIf { it.isNotBlank() }, end.color)
    }
    return bitmap
}

/**
 * The destination badge (#181b) on [x], [y]: as drawDestinationFlag draws it on the big map,
 * with a radius of MINI_BADGE_RADIUS_DP. Drawn once into the still picture, like the rest.
 */
private fun drawMiniFlag(canvas: AndroidCanvas, x: Float, y: Float, density: Float, flag: MiniFlag) {
    val radius = MINI_BADGE_RADIUS_DP * density
    val outline = badgeOutlineDp(MINI_BADGE_RADIUS_DP) * density
    // On a dark map a thin light line outside the dark outline, no thick light ring.
    flag.halo?.let { halo ->
        val width = BADGE_DARK_HALO_DP * density
        canvas.drawCircle(
            x,
            y,
            radius + outline / 2f + width / 2f,
            AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                style = AndroidPaint.Style.STROKE
                strokeWidth = width
                color = halo
            },
        )
    }
    canvas.drawCircle(x, y, radius, AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { color = flag.fill })
    canvas.drawCircle(
        x,
        y,
        radius,
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            style = AndroidPaint.Style.STROKE
            strokeWidth = outline
            color = flag.ink
        },
    )
    val g = flagGlyph(radius)
    val glyph = AndroidPath().apply {
        moveTo(x + g[0], y + g[1])
        for (i in 2 until g.size step 2) lineTo(x + g[i], y + g[i + 1])
        close()
    }
    canvas.drawPath(glyph, AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { color = flag.glyph })
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
