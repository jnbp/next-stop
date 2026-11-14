package io.github.jnbp.nextstop.wear

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tan
import io.github.jnbp.nextstop.data.OsmStations
import io.github.jnbp.nextstop.data.shortStopName
import io.github.jnbp.nextstop.ui.DetailKind
import io.github.jnbp.nextstop.ui.DetailSymbol
import io.github.jnbp.nextstop.ui.PositionPin
import io.github.jnbp.nextstop.ui.detailMinZoom
import io.github.jnbp.nextstop.ui.drawEntranceSign
import io.github.jnbp.nextstop.ui.drawLiftSign
import io.github.jnbp.nextstop.ui.drawPositionPin
import io.github.jnbp.nextstop.ui.mergeNearSymbols

/*
 * The station details on the map pictures of the watch (WatchMapImages), as the phone's map
 * shows them at the same zoom: the station signs, the entrances and exits (signs with their
 * letters, from zoom 16), lifts (17) and escalators (18), and the stop positions of buses and
 * trams with their "Pos." pills (and their lines from zoom 17). Drawn onto the snapshot with
 * the same signs as the maps of a walk on the phone (WalkMarks), from the offline OSM data.
 * Not tied to a walk: a picture shows everything in its area, so the watch can keep and reuse
 * it for any page that shows that area (the walk and change pages draw their way on top).
 */
internal object WatchMapDetails {
    // From these zooms on: the station signs, the stop positions as dots, with their pills, with their lines.
    private const val STATION_SIGN_ZOOM = 15.0
    private const val POSITION_DOT_ZOOM = 15.5
    private const val POSITION_PILL_ZOOM = 16.5
    private const val POSITION_LINES_ZOOM = 17.0

    /** Below this zoom nothing is drawn (the picture would only get busy). */
    const val MIN_ZOOM = STATION_SIGN_ZOOM

    /**
     * [base] with the details drawn on it, for a picture of the camera at [latitude],
     * [longitude], [zoom] with [ratio] pixels per logical pixel; [base] itself where there is
     * nothing to draw (zoomed out, no OSM data loaded).
     */
    fun drawOnto(base: Bitmap, latitude: Double, longitude: Double, zoom: Double, ratio: Float): Bitmap {
        if (zoom < MIN_ZOOM || !OsmStations.loaded) return base
        val width = base.width
        val height = base.height
        // Pixels of the picture per unit of Web Mercator (0 to 1 over the world).
        val world = WORLD_TILE * 2.0.pow(zoom) * ratio
        val centerX = mercatorX(longitude)
        val centerY = mercatorY(latitude)
        fun pixel(point: Pair<Double, Double>): Pair<Float, Float> =
            ((mercatorX(point.second) - centerX) * world + width / 2.0).toFloat() to
                ((mercatorY(point.first) - centerY) * world + height / 2.0).toFloat()
        val margin = 24f * ratio
        fun inView(at: Pair<Float, Float>): Boolean =
            at.first >= -margin && at.second >= -margin && at.first <= width + margin && at.second <= height + margin
        // Metres from the centre to a corner: what to look up.
        val metersPerPixel = 2 * PI * EARTH_RADIUS * cos(Math.toRadians(latitude)) / world
        val reach = sqrt((width / 2.0) * (width / 2.0) + (height / 2.0) * (height / 2.0)) * metersPerPixel + 60.0
        val center = latitude to longitude
        val stations = OsmStations.stationsNear(center, reach + 600.0)
        val areas = if (zoom >= POSITION_DOT_ZOOM) OsmStations.areasNear(center, reach) else emptyList()
        if (stations.isEmpty() && areas.isEmpty()) return base
        val bitmap = if (base.isMutable) base else base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(bitmap)
        val density = ratio
        // Stop positions first (under the signs): dots, from closer in pills "Pos. 4" with their lines.
        val positions = areas.flatMap { it.positions }.distinct()
        for (position in positions) {
            val at = pixel(position.position)
            if (!inView(at)) continue
            if (zoom >= POSITION_PILL_ZOOM && position.pos != null) {
                val lines = if (zoom >= POSITION_LINES_ZOOM) position.lines else emptyList()
                drawPositionPin(canvas, at.first, at.second, PositionPin(position.position, position.pos, lines, POSITION_GROUND), density * 0.85f)
            } else {
                dot(canvas, at, density)
            }
        }
        // Entrances, lifts and escalators: each from its zoom, near-identical ones once.
        val symbols = ArrayList<Pair<DetailSymbol, () -> Unit>>()
        for (station in stations) {
            if (zoom >= (detailMinZoom(DetailKind.Entrance, marked = false) ?: Double.MAX_VALUE)) {
                for (entrance in station.entrances) {
                    val at = pixel(entrance.position)
                    if (!inView(at)) continue
                    val marker = entrance.marker ?: station.products.ifEmpty { "U" }
                    val sign = when {
                        'S' in marker && 'U' in marker -> "S+U"
                        'S' in marker -> "S"
                        'U' in marker -> "U"
                        'R' in marker -> "R"
                        else -> "U"
                    }
                    val label = entrance.ref?.takeIf { it.length <= 3 } ?: sign
                    val ground = when {
                        entrance.throughBuilding -> BUILDING_GROUND
                        sign == "S" -> SUBURBAN_GROUND
                        sign == "R" -> REGIONAL_GROUND
                        else -> SUBWAY_GROUND
                    }
                    symbols.add(
                        DetailSymbol(DetailKind.Entrance, "e$sign", entrance.latitude, entrance.longitude, named = entrance.ref != null || entrance.name != null) to
                            { drawEntranceSign(canvas, at.first, at.second, label, entrance.throughBuilding, density, ground) },
                    )
                }
            }
            for (lift in station.lifts) {
                val kind = if (lift.escalator) DetailKind.Escalator else DetailKind.Lift
                if (zoom < (detailMinZoom(kind, marked = false) ?: Double.MAX_VALUE)) continue
                val at = pixel(lift.latitude to lift.longitude)
                if (!inView(at)) continue
                symbols.add(
                    DetailSymbol(kind, kind.key, lift.latitude, lift.longitude) to
                        { drawLiftSign(canvas, at.first, at.second, lift.escalator, broken = false, density = density) },
                )
            }
        }
        // Escalators under lifts under entrances, as on the phone's map.
        val kept = mergeNearSymbols(symbols.map { it.first }).map { symbols[it] }
        for (kind in listOf(DetailKind.Escalator, DetailKind.Lift, DetailKind.Entrance)) {
            kept.filter { it.first.kind == kind }.forEach { it.second() }
        }
        // The station signs on top: their modes and their name.
        if (zoom >= STATION_SIGN_ZOOM) {
            for (station in stations) {
                val at = pixel(station.position)
                if (!inView(at)) continue
                stationSign(canvas, at, station.products, station.name.shortStopName(), density)
            }
        }
        return bitmap
    }

    private fun dot(canvas: Canvas, at: Pair<Float, Float>, density: Float) {
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6FFFFFF.toInt() }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = POSITION_GROUND }
        canvas.drawCircle(at.first, at.second, 4f * density, rim)
        canvas.drawCircle(at.first, at.second, 3f * density, fill)
    }

    /** The sign of a station: a chip per mode (S green, U blue, R grey) and its name beside them, on white. */
    private fun stationSign(canvas: Canvas, at: Pair<Float, Float>, products: String, name: String, density: Float) {
        val modes = listOfNotNull(
            "S".takeIf { 'S' in products },
            "U".takeIf { 'U' in products },
            "R".takeIf { 'R' in products && 'S' !in products && 'U' !in products },
        ).ifEmpty { listOf("U") }
        val chipInk = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 10f * density
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val nameInk = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF2B2F36.toInt()
            textSize = 11f * density
            typeface = Typeface.DEFAULT_BOLD
        }
        val chip = 14f * density
        val gap = 2f * density
        val padding = 3f * density
        val text = name.take(28)
        val textWidth = nameInk.measureText(text)
        val total = padding + modes.size * (chip + gap) + textWidth + padding
        val height = chip + 2 * padding
        val left = at.first - total / 2f
        val top = at.second - height / 2f
        val ground = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF2FFFFFF.toInt() }
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFC3C8D0.toInt()
            style = Paint.Style.STROKE
            strokeWidth = max(1f, density * 0.75f)
        }
        val box = RectF(left, top, left + total, top + height)
        canvas.drawRoundRect(box, height / 2f, height / 2f, ground)
        canvas.drawRoundRect(box, height / 2f, height / 2f, edge)
        var x = left + padding
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        for (mode in modes) {
            fill.color = when (mode) {
                "S" -> SUBURBAN_GROUND
                "R" -> REGIONAL_GROUND
                else -> SUBWAY_GROUND
            }
            val chipBox = RectF(x, top + padding, x + chip, top + padding + chip)
            if (mode == "S") canvas.drawOval(chipBox, fill) else canvas.drawRoundRect(chipBox, 2f * density, 2f * density, fill)
            canvas.drawText(mode, chipBox.centerX(), chipBox.centerY() - (chipInk.descent() + chipInk.ascent()) / 2f, chipInk)
            x += chip + gap
        }
        canvas.drawText(text, x, box.centerY() - (nameInk.descent() + nameInk.ascent()) / 2f, nameInk)
    }

    private const val WORLD_TILE = 512.0
    private const val EARTH_RADIUS = 6_378_137.0
    private val SUBWAY_GROUND = 0xFF0664AB.toInt()
    private val SUBURBAN_GROUND = 0xFF008D4F.toInt()
    private val REGIONAL_GROUND = 0xFF5F6670.toInt()
    private val BUILDING_GROUND = 0xFF5F6670.toInt()
    private val POSITION_GROUND = 0xFF6B7280.toInt()

    // Web Mercator, 0 to 1 over the world.
    private fun mercatorX(longitude: Double): Double = (longitude + 180.0) / 360.0

    private fun mercatorY(latitude: Double): Double {
        val phi = Math.toRadians(latitude.coerceIn(-85.0, 85.0))
        return (1.0 - ln(tan(phi) + 1.0 / cos(phi)) / PI) / 2.0
    }
}
