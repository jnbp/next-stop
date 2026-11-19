package io.github.jnbp.nextstop.ui

import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/*
 * The framing of the small still maps of walks (WalkMiniMap) and the pan and zoom of one opened
 * larger in the calm companion (#114). Pure sums, no Android: Web Mercator from 0 to 1 over the
 * world, and MapLibre's world 512 logical pixels wide at zoom 0.
 */

private const val MINI_WORLD_TILE = 512.0

/** The x of [longitude] in Web Mercator, 0 to 1 over the world. */
fun miniMercatorX(longitude: Double): Double = (longitude + 180.0) / 360.0

/** The y of [latitude] in Web Mercator, 0 (north) to 1 (south). */
fun miniMercatorY(latitude: Double): Double {
    val phi = Math.toRadians(latitude.coerceIn(-85.0, 85.0))
    return (1.0 - ln(tan(phi) + 1.0 / Math.cos(phi)) / PI) / 2.0
}

/** The longitude of the Web Mercator [x]. */
fun miniLongitudeOf(x: Double): Double = x * 360.0 - 180.0

/** The latitude of the Web Mercator [y]. */
fun miniLatitudeOf(y: Double): Double = Math.toDegrees(Math.atan(Math.sinh(PI * (1.0 - 2.0 * y))))

/**
 * The camera of a small map: its middle ([centerX], [centerY] in Web Mercator) and [zoom]. Where
 * [x] and [y] put a point in logical pixels of a picture [width] by [height].
 */
data class MiniFrame(val centerX: Double, val centerY: Double, val zoom: Double) {
    private val world: Double get() = MINI_WORLD_TILE * Math.pow(2.0, zoom)

    fun x(longitude: Double, width: Double): Double = (miniMercatorX(longitude) - centerX) * world + width / 2.0

    fun y(latitude: Double, height: Double): Double = (miniMercatorY(latitude) - centerY) * world + height / 2.0
}

/**
 * The camera that shows all [points] (latitude, longitude) in a picture [width] by [height]
 * (logical pixels), [side] clear of its left and right edges, [top] of its top and [bottom] of
 * its bottom: the marks on the ends stand above their point (the pins of stop positions), so
 * the top keeps more room (#114). The middle moves by half the difference of [top] and
 * [bottom], so the points sit in the middle of the room left. The zoom stays within
 * [minZoom] and [maxZoom].
 */
fun miniMapFrame(
    points: List<Pair<Double, Double>>,
    width: Double,
    height: Double,
    side: Double,
    top: Double,
    bottom: Double,
    minZoom: Double,
    maxZoom: Double,
): MiniFrame {
    val xs = points.map { miniMercatorX(it.second) }
    val ys = points.map { miniMercatorY(it.first) }
    val middleX = (xs.min() + xs.max()) / 2.0
    val middleY = (ys.min() + ys.max()) / 2.0
    val spanX = max(xs.max() - xs.min(), 1e-12)
    val spanY = max(ys.max() - ys.min(), 1e-12)
    val roomX = max(width - 2 * side, 1.0)
    val roomY = max(height - top - bottom, 1.0)
    val fit = min(roomX / (spanX * MINI_WORLD_TILE), roomY / (spanY * MINI_WORLD_TILE))
    val zoom = log2(fit).coerceIn(minZoom, maxZoom)
    val world = MINI_WORLD_TILE * Math.pow(2.0, zoom)
    // The room left is (top - bottom) / 2 below the picture's middle: the camera goes up by that.
    val shiftY = (top - bottom) / 2.0 / world
    return MiniFrame(middleX, middleY - shiftY, zoom)
}

/**
 * How an opened small map is looked at (#114): its [scale] and how far its picture is moved
 * from the middle ([x], [y], in pixels).
 */
data class MiniView(val scale: Float = 1f, val x: Float = 0f, val y: Float = 0f)

/**
 * [view] zoomed by [zoom] and moved by [panX], [panY] for a picture [contentWidth] by
 * [contentHeight] seen through a window [viewWidth] by [viewHeight] (pixels): the scale stays
 * within [minScale] and [maxScale], and the picture always covers the window.
 */
fun panMiniView(
    view: MiniView,
    zoom: Float,
    panX: Float,
    panY: Float,
    viewWidth: Float,
    viewHeight: Float,
    contentWidth: Float,
    contentHeight: Float,
    minScale: Float,
    maxScale: Float,
): MiniView {
    val scale = (view.scale * zoom).coerceIn(minScale, maxScale)
    val reachX = ((contentWidth * scale - viewWidth) / 2f).coerceAtLeast(0f)
    val reachY = ((contentHeight * scale - viewHeight) / 2f).coerceAtLeast(0f)
    return MiniView(scale, (view.x + panX).coerceIn(-reachX, reachX), (view.y + panY).coerceIn(-reachY, reachY))
}

/**
 * A sharper picture of the part of an opened small map in view (#190): drawn by the camera
 * [frame] at [widthDp] by [heightDp] (the size of the window), it covers [width] by [height] of
 * the larger picture, from [left], [top] (all in its logical pixels), and lies over it there.
 */
data class MiniSharp(
    val frame: MiniFrame,
    val widthDp: Int,
    val heightDp: Int,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

/**
 * What to draw anew of an opened small map zoomed in to [view] (#190): the picture of the camera
 * [frame], [pictureWidthDp] by [pictureHeightDp], is only scaled up by the fingers and goes soft;
 * the part of it in the window ([viewWidthPx] by [viewHeightPx], [density] pixels to a logical
 * one) is drawn again at the zoom it is seen at, at most [maxZoom], so it is sharp at the size of
 * the window and no larger. Null where the picture is not enlarged by at least [fromScale].
 */
fun miniSharpRequest(
    frame: MiniFrame,
    pictureWidthDp: Int,
    pictureHeightDp: Int,
    view: MiniView,
    viewWidthPx: Float,
    viewHeightPx: Float,
    density: Float,
    maxZoom: Double,
    fromScale: Float = MINI_SHARP_FROM_SCALE,
): MiniSharp? {
    if (view.scale < fromScale || viewWidthPx <= 0f || viewHeightPx <= 0f || density <= 0f) return null
    val widthDp = Math.ceil(viewWidthPx / density.toDouble()).toInt().coerceAtLeast(1)
    val heightDp = Math.ceil(viewHeightPx / density.toDouble()).toInt().coerceAtLeast(1)
    val zoom = min(frame.zoom + log2(view.scale.toDouble()), max(maxZoom, frame.zoom))
    val factor = Math.pow(2.0, zoom - frame.zoom)
    // The middle of the window in the picture, from the picture's middle (logical pixels): the
    // picture is scaled about its middle, then moved by x, y (see WalkMiniMap).
    val dx = -view.x / view.scale / density
    val dy = -view.y / view.scale / density
    val world = MINI_WORLD_TILE * Math.pow(2.0, frame.zoom)
    val width = (widthDp / factor).toFloat()
    val height = (heightDp / factor).toFloat()
    return MiniSharp(
        frame = MiniFrame(frame.centerX + dx / world, frame.centerY + dy / world, zoom),
        widthDp = widthDp,
        heightDp = heightDp,
        left = pictureWidthDp / 2f + dx - width / 2f,
        top = pictureHeightDp / 2f + dy - height / 2f,
        width = width,
        height = height,
    )
}

/** Below this enlargement the picture of an opened small map is sharp enough as it is (#190). */
const val MINI_SHARP_FROM_SCALE = 1.15f
