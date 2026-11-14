package io.github.jnbp.nextstop.wear.data

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sinh

/**
 * Web Mercator as MapLibre uses it: the world as a square of 1 x 1 "units", x east from
 * the antimeridian, y south from the top (about 85° north). At zoom z the world is
 * [TILE_SIZE] * 2^z logical pixels wide, each of them pixelRatio pixels of the picture.
 * The watch draws its own signs in the same projection as the map pictures of the phone.
 */
object WebMercator {
    const val TILE_SIZE = 512.0
    const val EARTH_CIRCUMFERENCE = 40_075_016.686
    private const val MAX_LATITUDE = 85.05112878

    fun x(longitude: Double): Double = (longitude + 180.0) / 360.0

    fun y(latitude: Double): Double {
        val s = sin(Math.toRadians(latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE)))
        return 0.5 - ln((1 + s) / (1 - s)) / (4 * PI)
    }

    fun longitude(x: Double): Double = x * 360.0 - 180.0

    fun latitude(y: Double): Double = Math.toDegrees(atan(sinh(PI * (1 - 2 * y))))

    /** Metres on the ground per unit of the world at [latitude]. */
    fun metresPerUnit(latitude: Double): Double = EARTH_CIRCUMFERENCE * cos(Math.toRadians(latitude))

    /** Pixels of a picture per unit of the world at [zoom] with [pixelRatio]. */
    fun pixelsPerUnit(zoom: Double, pixelRatio: Double): Double = TILE_SIZE * pixelRatio * 2.0.pow(zoom)

    /** The zoom at which a picture with [pixelRatio] has [pixelsPerUnit] pixels per unit. */
    fun zoomFor(pixelsPerUnit: Double, pixelRatio: Double): Double = log2(pixelsPerUnit / (TILE_SIZE * pixelRatio))
}

/**
 * A map picture to ask the phone for: [width] x [height] pixels around ([latitude],
 * [longitude]) at MapLibre's [zoom], drawn with [pixelRatio] (the density of the watch, so
 * labels have their usual size), in the dark style when [dark]. The phone's camera sees
 * width / pixelRatio x height / pixelRatio logical pixels; that is the area the picture shows
 * ([unitsWide] x [unitsHigh] world units), whatever the pixel size of the bitmap that comes back.
 */
data class MapImageRequest(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double,
    val width: Int,
    val height: Int,
    val pixelRatio: Double,
    val dark: Boolean,
) {
    val x: Double get() = WebMercator.x(longitude)
    val y: Double get() = WebMercator.y(latitude)
    val pixelsPerUnit: Double get() = WebMercator.pixelsPerUnit(zoom, pixelRatio)

    /** The world units the picture spans across and down. */
    val unitsWide: Double get() = width / pixelsPerUnit
    val unitsHigh: Double get() = height / pixelsPerUnit

    /** The area this request shows, as a received picture of it would. */
    val frame: MapFrame get() = MapFrame(x, y, unitsWide, unitsHigh, zoom, dark)

    /** True when a picture of this request covers all of [view] at about its scale (see [MapFrame.covers]). */
    fun covers(view: MapImageRequest): Boolean = frame.covers(view)

    /** The picture to ask for to show [this] view: a margin around it for small moves, at most [MAX_SIDE] pixels a side. */
    fun withMargin(): MapImageRequest = copy(
        width = min((width * MARGIN).toInt(), MAX_SIDE),
        height = min((height * MARGIN).toInt(), MAX_SIDE),
    )

    /**
     * A quick small picture of the same area, shown until the full one is there: at most
     * [QUICK_SIDE] pixels a side, at a lower zoom by the same factor (a quarter of the tiles
     * for half the size), the same ratio. Null when this one is small already.
     */
    fun quick(): MapImageRequest? {
        val longest = max(width, height)
        if (longest <= QUICK_SIDE * 1.25) return null
        val factor = longest.toDouble() / QUICK_SIDE
        return copy(
            zoom = zoom - log2(factor),
            width = (width / factor).roundToInt().coerceAtLeast(1),
            height = (height / factor).roundToInt().coerceAtLeast(1),
        )
    }

    companion object {
        // A picture this far off in zoom (a factor of about 1.15) is still sharp enough.
        const val ZOOM_SLACK = 0.2
        const val MARGIN = 1.25
        const val MAX_SIDE = 640
        const val QUICK_SIDE = 240

        // The zooms MapLibre draws well; a view beyond them is shown from the nearest.
        const val MIN_ZOOM = 1.0
        const val MAX_ZOOM = 19.5

        /**
         * The picture of a view with its centre at the world point ([x], [y]), [pixelsPerUnit]
         * screen pixels per unit and [width] x [height] pixels, drawn with [pixelRatio].
         */
        fun ofView(x: Double, y: Double, pixelsPerUnit: Double, width: Int, height: Int, pixelRatio: Double, dark: Boolean): MapImageRequest =
            MapImageRequest(
                latitude = WebMercator.latitude(y),
                longitude = WebMercator.longitude(x),
                zoom = WebMercator.zoomFor(pixelsPerUnit, pixelRatio).coerceIn(MIN_ZOOM, MAX_ZOOM),
                width = width,
                height = height,
                pixelRatio = pixelRatio,
                dark = dark,
            )
    }
}

/**
 * The area a received map picture shows: its centre ([x], [y]) and its extent ([unitsWide] x
 * [unitsHigh]) in Web Mercator units, its MapLibre [zoom] and style. It is what the phone's
 * camera saw (the phone sends it back with the picture), not derived from the bitmap's pixel
 * size: a bitmap of another size (another density, rounding, a phone that scaled it) is
 * stretched over the same area and so never lands beside its place.
 */
data class MapFrame(
    val x: Double,
    val y: Double,
    val unitsWide: Double,
    val unitsHigh: Double,
    val zoom: Double,
    val dark: Boolean,
) {
    /**
     * True when this picture covers all of [view] (a picture of the screen as it is now) at
     * about its scale, so no new one is needed.
     */
    fun covers(view: MapImageRequest): Boolean {
        if (dark != view.dark || abs(zoom - view.zoom) > MapImageRequest.ZOOM_SLACK) return false
        return abs(view.x - x) + view.unitsWide / 2 <= unitsWide / 2 * (1 + EDGE_SLACK) &&
            abs(view.y - y) + view.unitsHigh / 2 <= unitsHigh / 2 * (1 + EDGE_SLACK)
    }

    /** The share (0..1) of [view] this picture shows, whatever its zoom. */
    fun overlap(view: MapImageRequest): Double {
        val left = max(x - unitsWide / 2, view.x - view.unitsWide / 2)
        val right = min(x + unitsWide / 2, view.x + view.unitsWide / 2)
        val top = max(y - unitsHigh / 2, view.y - view.unitsHigh / 2)
        val bottom = min(y + unitsHigh / 2, view.y + view.unitsHigh / 2)
        if (right <= left || bottom <= top) return 0.0
        return (right - left) * (bottom - top) / (view.unitsWide * view.unitsHigh)
    }

    /**
     * Where the picture goes on a screen of [screenWidth] x [screenHeight] pixels whose middle
     * shows the world point ([viewX], [viewY]) at [pixelsPerUnit] screen pixels per unit.
     */
    fun placement(viewX: Double, viewY: Double, pixelsPerUnit: Double, screenWidth: Double, screenHeight: Double): MapPlacement {
        val width = unitsWide * pixelsPerUnit
        val height = unitsHigh * pixelsPerUnit
        return MapPlacement(
            left = screenWidth / 2 + (x - viewX) * pixelsPerUnit - width / 2,
            top = screenHeight / 2 + (y - viewY) * pixelsPerUnit - height / 2,
            width = width,
            height = height,
        )
    }

    companion object {
        private const val EDGE_SLACK = 0.001

        /**
         * The area the phone drew: a camera at ([latitude], [longitude]) and [zoom] over
         * [logicalWidth] x [logicalHeight] logical pixels (its size before the pixel ratio).
         */
        fun drawn(latitude: Double, longitude: Double, zoom: Double, logicalWidth: Double, logicalHeight: Double, dark: Boolean): MapFrame {
            val perUnit = WebMercator.pixelsPerUnit(zoom, 1.0)
            return MapFrame(WebMercator.x(longitude), WebMercator.y(latitude), logicalWidth / perUnit, logicalHeight / perUnit, zoom, dark)
        }
    }
}

/** Where a map picture is drawn on the screen, in screen pixels. */
data class MapPlacement(val left: Double, val top: Double, val width: Double, val height: Double)
