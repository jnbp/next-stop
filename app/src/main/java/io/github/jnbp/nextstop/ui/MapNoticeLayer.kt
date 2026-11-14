package io.github.jnbp.nextstop.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.PointF
import android.graphics.RectF
import org.maplibre.android.geometry.LatLng
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import com.google.gson.JsonArray
import io.github.jnbp.nextstop.data.NoticeMarker
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.briefStopName
import java.util.Locale
import io.github.jnbp.nextstop.ui.theme.AppColors
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/*
 * The disruptions on the start page map and the map tab: a small warning sign on the upper
 * right corner of the sign of a stop a known notice is about (see MapNotices.kt in data), like
 * the badge on an app icon, with the badge of its line, or how many notices there are where a
 * stop has several. Where it sits follows the size of the sign it belongs to (an "H", a round
 * "U", the wide "S U R" of a big station, the ringed sign of the marked stop, or the green name
 * of a stop shown by name close up; see NoticeSignSpot). It is drawn right above the stop signs
 * and their names, so no sign hides it, and it never culls a sign or a vehicle (allowed to
 * overlap, ignored in the placement); the vehicles lining up beside a sign start below it (see
 * layoutGroup).
 */

/** From this zoom on the main maps show the signs of disruptions. */
const val MAP_NOTICE_ZOOM = 12.5

private const val NOTICE_SOURCE = "ns-notices"
private const val NOTICE_LAYER = "ns-notice-marks"
// The signs of stops shown by their green name close up (the setting "stop names"): on their
// "H" below STOP_NAME_ZOOM, at the end of their name from it.
private const val NOTICE_NAMED_LAYER = "ns-notice-marks-named"
private const val NOTICE_NAME_LAYER = "ns-notice-marks-name"
private const val NOTICE_IMAGE = "ns-notice-"

// Over this many zoom levels the signs fade in above MAP_NOTICE_ZOOM.
private const val NOTICE_FADE = 0.6

/**
 * A sign of a stop on the map that a sign of disruptions can sit on: the stop's [id] and
 * [name], where it is, and half its width and height in dp as the map draws it. A stop shown by
 * its name from STOP_NAME_ZOOM on has the half width and height of that name too (else 0).
 */
data class NoticeSignSpot(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val halfWidthDp: Float,
    val halfHeightDp: Float,
    val nameHalfWidthDp: Float = 0f,
    val nameHalfHeightDp: Float = 0f,
)

/**
 * The signs now on the map (see setStopSigns, setSelectedStop), for the place of the signs of
 * disruptions, and the stops that carry one: the vehicles beside such a stop start below it.
 */
object NoticeSignSpots {
    @Volatile
    var signs: List<NoticeSignSpot> = emptyList()

    @Volatile
    var marked: NoticeSignSpot? = null

    /** Per stop id with a sign of disruptions: the highest a vehicle beside its sign may reach, in dp from the sign's middle (down positive). */
    @Volatile
    var columnTops: Map<String, Float> = emptyMap()
}

/** The colours of the signs, as the app's warnings have them in its current look (see AppColors). */
data class NoticeMarkColors(val ground: Int, val warning: Int)

/** The colours of the signs from the look of the app now. */
fun noticeMarkColors(): NoticeMarkColors = NoticeMarkColors(ground = AppColors.WarningContainer.toArgb(), warning = AppColors.Warning.toArgb())

/**
 * Adds the source and the layers of the signs to [style], right above the layer [above] (the
 * topmost of the stop signs and their names); call after those are in. A style that has them
 * already is left alone.
 */
fun installNoticeLayer(style: Style, above: String) {
    if (style.getSource(NOTICE_SOURCE) == null) {
        style.addSource(GeoJsonSource(NOTICE_SOURCE, FeatureCollection.fromFeatures(emptyList<Feature>())))
    }
    if (style.getLayer(NOTICE_LAYER) != null) return
    // Stops with a sign at every zoom; stops shown by name: on the "H", then at the name's end.
    val plain = noticeLayer(NOTICE_LAYER, "off").apply {
        minZoom = MAP_NOTICE_ZOOM.toFloat()
        setFilter(Expression.not(Expression.has("noff")))
    }
    val named = noticeLayer(NOTICE_NAMED_LAYER, "off").apply {
        minZoom = MAP_NOTICE_ZOOM.toFloat()
        maxZoom = STOP_NAME_ZOOM.toFloat()
        setFilter(Expression.has("noff"))
    }
    val name = noticeLayer(NOTICE_NAME_LAYER, "noff").apply {
        minZoom = STOP_NAME_ZOOM.toFloat()
        setFilter(Expression.has("noff"))
    }
    var below = above
    for (layer in listOf(plain, named, name)) {
        if (style.getLayer(below) != null) style.addLayerAbove(layer, below) else style.addLayer(layer)
        below = layer.id
    }
}

// One layer of the signs, each placed by its offset in the property [offset] (see noticeOffsetDp).
private fun noticeLayer(id: String, offset: String): SymbolLayer = SymbolLayer(id, NOTICE_SOURCE).apply {
    setProperties(
        PropertyFactory.iconImage(Expression.get("icon")),
        // The lower left corner of the sign on the upper right corner of the stop's sign.
        PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM_LEFT),
        PropertyFactory.iconOffset(Expression.get(offset)),
        PropertyFactory.iconOpacity(noticeOpacity(1f)),
        PropertyFactory.symbolSortKey(Expression.get("rank")),
        // Always drawn, never culling a stop sign or a vehicle.
        PropertyFactory.iconAllowOverlap(true),
        PropertyFactory.iconIgnorePlacement(true),
        PropertyFactory.iconPadding(1f),
    )
}

private val NOTICE_LAYERS = arrayOf(NOTICE_LAYER, NOTICE_NAMED_LAYER, NOTICE_NAME_LAYER)

private fun noticeOpacity(appear: Float): Expression = Expression.interpolate(
    Expression.linear(),
    Expression.zoom(),
    Expression.stop(MAP_NOTICE_ZOOM, 0f),
    Expression.stop(MAP_NOTICE_ZOOM + NOTICE_FADE, appear.coerceIn(0f, 1f)),
)

/** How far the signs have come in, 0 to 1, for their soft appearance (none with animations off). */
fun setNoticeAppear(style: Style, appear: Float) {
    for (id in NOTICE_LAYERS) style.getLayer(id)?.setProperties(PropertyFactory.iconOpacity(noticeOpacity(appear)))
}

/** Shows or hides the signs with the app's other layers (see showAppLayers). */
fun showNoticeLayer(style: Style, shown: Boolean) {
    for (id in NOTICE_LAYERS) {
        style.getLayer(id)?.setProperties(PropertyFactory.visibility(if (shown) Property.VISIBLE else Property.NONE))
    }
}

/**
 * Puts [markers] as signs into [style], each image made the first time it is needed, each on the
 * corner of the sign its stop has on the map now (see NoticeSignSpots, noticeSpotFor); call
 * again when the signs change. Call on the main thread; an empty list clears them.
 */
fun setNoticeMarkers(style: Style, markers: List<NoticeMarker>, density: Float, colors: NoticeMarkColors) {
    val source = style.getSourceAs<GeoJsonSource>(NOTICE_SOURCE) ?: return
    val spots = listOfNotNull(NoticeSignSpots.marked) + NoticeSignSpots.signs
    val tops = HashMap<String, Float>()
    val placed = ArrayList<PlacedNotice>()
    val features = markers.map { marker ->
        val spot = noticeSpotFor(marker.stop, spots)
        val latitude = spot?.latitude ?: marker.position.first
        val longitude = spot?.longitude ?: marker.position.second
        val (offsetX, offsetY) = noticeOffsetDp(spot?.halfWidthDp ?: DEFAULT_SIGN_HALF_DP, spot?.halfHeightDp ?: DEFAULT_SIGN_HALF_DP)
        if (spot != null) tops[spot.id] = noticeColumnTopDp(offsetY)
        val count = marker.items.size
        val badge = if (count == 1) marker.badge else null
        val label: String?
        val ground: Int?
        val ink: Int?
        if (badge != null) {
            val color = lineColor(badge.line, badge.product, marker.position)
            label = shortLine(badge.line) ?: badge.line
            ground = color.toArgb()
            ink = lineInk(badge.line, badge.product, color, marker.position).toArgb()
        } else if (count > 1) {
            label = count.toString()
            ground = colors.warning
            ink = android.graphics.Color.WHITE
        } else {
            label = null
            ground = null
            ink = null
        }
        val id = NOTICE_IMAGE + listOf(label ?: "", ground ?: 0, ink ?: 0, colors.ground, colors.warning).joinToString("|")
        val image = style.getImage(id) ?: noticeMarkBitmap(density, colors, label, ground, ink).also { style.addImage(id, it) }
        val nameOffset = if (spot != null && spot.nameHalfWidthDp > 0f) noticeNameOffsetDp(spot.nameHalfWidthDp, spot.nameHalfHeightDp) else null
        placed += PlacedNotice(
            key = marker.key,
            latitude = latitude,
            longitude = longitude,
            offsets = listOfNotNull(offsetX to offsetY, nameOffset).map { (x, y) -> x * density to y * density },
            width = image.width.toFloat(),
            height = image.height.toFloat(),
        )
        Feature.fromGeometry(Point.fromLngLat(longitude, latitude)).apply {
            addStringProperty("icon", id)
            addStringProperty("key", marker.key)
            addNumberProperty("rank", -count)
            addProperty("off", offsetArray(offsetX, offsetY))
            if (spot != null && spot.nameHalfWidthDp > 0f) {
                val (nameX, nameY) = noticeNameOffsetDp(spot.nameHalfWidthDp, spot.nameHalfHeightDp)
                addProperty("noff", offsetArray(nameX, nameY))
            }
        }
    }
    NoticeSignSpots.columnTops = tops
    placedNotices = placed
    source.setGeoJson(FeatureCollection.fromFeatures(features))
}

private fun offsetArray(x: Float, y: Float): JsonArray = JsonArray().apply {
    add(x)
    add(y)
}

/**
 * The sign of [stop] among [spots] (the signs on the map): its own, else the nearest one of
 * its name within NOTICE_SNAP_NAME_METERS (a part of a station drawn as the station's sign),
 * else any within NOTICE_SNAP_METERS; null where none is drawn near it.
 */
internal fun noticeSpotFor(stop: Stop, spots: List<NoticeSignSpot>): NoticeSignSpot? {
    spots.firstOrNull { it.id == stop.id }?.let { return it }
    val at = stop.position ?: return null
    val name = stop.name.briefStopName().lowercase(Locale.ROOT)
    var best: NoticeSignSpot? = null
    var bestMeters = Double.MAX_VALUE
    for (spot in spots) {
        val meters = metersBetween(at.first, at.second, spot.latitude, spot.longitude)
        val reach = if (spot.name.briefStopName().lowercase(Locale.ROOT) == name) NOTICE_SNAP_NAME_METERS else NOTICE_SNAP_METERS
        if (meters <= reach && meters < bestMeters) {
            best = spot
            bestMeters = meters
        }
    }
    return best
}

/**
 * Where the lower left corner of a sign of disruptions goes from the middle of a stop's sign of
 * half width [halfWidthDp] and half height [halfHeightDp], in dp (right and down positive): on
 * the upper right of the sign's rounded end (for a round sign at 45 degrees), overlapping it a
 * little, as the badge on an app icon. The picture's bottom holds the shadow, so it goes lower by that.
 */
internal fun noticeOffsetDp(halfWidthDp: Float, halfHeightDp: Float): Pair<Float, Float> {
    val cornerX = halfWidthDp - halfHeightDp * (1f - DIAGONAL)
    val cornerY = -halfHeightDp * DIAGONAL
    return (cornerX - NOTICE_OVERLAP_X_DP) to (cornerY + NOTICE_OVERLAP_Y_DP + NOTICE_SHADOW_DP)
}

/** As noticeOffsetDp for a stop shown by its green name of half width [halfWidthDp] and half height [halfHeightDp]: at its upper right end. */
internal fun noticeNameOffsetDp(halfWidthDp: Float, halfHeightDp: Float): Pair<Float, Float> =
    (halfWidthDp - NOTICE_NAME_OVERLAP_DP) to (-halfHeightDp + NOTICE_NAME_OVERLAP_DP + NOTICE_SHADOW_DP)

/** The highest a vehicle beside the sign may reach below a sign of disruptions placed at [offsetYDp] (see noticeOffsetDp). */
internal fun noticeColumnTopDp(offsetYDp: Float): Float = offsetYDp - NOTICE_SHADOW_DP + NOTICE_COLUMN_GAP_DP

/** The key of the sign of disruptions drawn at [point] on the screen, if any, with a little room around the finger. */
fun noticeAt(map: MapLibreMap, point: PointF, slop: Float): String? {
    // The pills ignore placement (they must never push signs or vehicles away), and symbols
    // that ignore placement are not in the index queryRenderedFeatures reads: so they are hit
    // by their own rectangles, at their stop on the screen plus their offset, anchored at the
    // bottom left like the layer draws them.
    for (notice in placedNotices.asReversed()) {
        val stop = map.projection.toScreenLocation(LatLng(notice.latitude, notice.longitude))
        for ((offsetX, offsetY) in notice.offsets) {
            val left = stop.x + offsetX
            val bottom = stop.y + offsetY
            if (point.x >= left - slop && point.x <= left + notice.width + slop &&
                point.y >= bottom - notice.height - slop && point.y <= bottom + slop
            ) return notice.key
        }
    }
    val area = RectF(point.x - slop, point.y - slop, point.x + slop, point.y + slop)
    return map.queryRenderedFeatures(area, *NOTICE_LAYERS).firstOrNull { it.hasProperty("key") }?.getStringProperty("key")
}

/** A pill as last put on the map: its stop, its offsets in pixels (sign and name mode) and its size in pixels. */
private class PlacedNotice(
    val key: String,
    val latitude: Double,
    val longitude: Double,
    val offsets: List<Pair<Float, Float>>,
    val width: Float,
    val height: Float,
)

@Volatile
private var placedNotices: List<PlacedNotice> = emptyList()

// The sign of disruptions, in dp: one pill like a badge on an app icon, sitting on the upper
// right of the stop's sign.
private const val NOTICE_HEIGHT_DP = 18f
private const val NOTICE_HALO_DP = 1.5f
private const val NOTICE_SHADOW_DP = 1.5f
private const val NOTICE_MARK_DP = 10f
private const val NOTICE_TEXT_DP = 10f
private const val NOTICE_PAD_DP = 4f
private const val NOTICE_CHIP_PAD_DP = 3.5f

// How far the sign overlaps the corner of the stop's sign, so both read as one; the same at the
// end of a stop's name. cos 45 degrees for the corner of a round end.
private const val NOTICE_OVERLAP_X_DP = 3f
private const val NOTICE_OVERLAP_Y_DP = 4f
private const val NOTICE_NAME_OVERLAP_DP = 3f
private const val DIAGONAL = 0.7071f

// The room between the sign and the vehicles below it, in dp.
private const val NOTICE_COLUMN_GAP_DP = 2f

// Half the size of an "H" in dp, for a stop whose sign is not on the map (see stopSignBitmap).
private const val DEFAULT_SIGN_HALF_DP = 16f

// A stop's sign of disruptions sits on the sign of a stop of its name this close, or of any stop
// closer than the other: the station's sign where the stop is one of its parts.
private const val NOTICE_SNAP_NAME_METERS = 150.0
private const val NOTICE_SNAP_METERS = 40.0

/**
 * The sign of disruptions: one rounded pill with a white halo and a soft shadow like the stop
 * signs, in the container colour of warnings with the warning triangle (see WarningMark); with
 * [label] (a line, or how many notices) a small chip inside it on [ground] in [ink], so mark and
 * line are one piece rather than a disc with a badge beside it.
 */
internal fun noticeMarkBitmap(density: Float, colors: NoticeMarkColors, label: String?, ground: Int?, ink: Int?): Bitmap {
    val height = NOTICE_HEIGHT_DP * density
    val halo = NOTICE_HALO_DP * density
    val shadow = NOTICE_SHADOW_DP * density
    val pad = NOTICE_PAD_DP * density
    val mark = NOTICE_MARK_DP * density
    val textPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textSize = NOTICE_TEXT_DP * density
        color = ink ?: android.graphics.Color.WHITE
    }
    val chipHeight = height - 2 * halo - 2 * density
    val chipWidth = if (label == null) 0f else maxOf(chipHeight, textPaint.measureText(label) + 2 * NOTICE_CHIP_PAD_DP * density)
    val inner = pad + mark + (if (label == null) pad else 3 * density + chipWidth + 2 * density)
    val width = kotlin.math.ceil(inner + 2 * halo).toInt().coerceAtLeast(1) + 1
    val full = kotlin.math.ceil(height + shadow).toInt().coerceAtLeast(1) + 1
    val bitmap = Bitmap.createBitmap(width, full, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG)
    val radius = height / 2
    val outer = RectF(0f, 0f, inner + 2 * halo, height)
    // A soft shadow under it, then the white halo, then the warning ground.
    paint.color = 0x33000000
    canvas.drawRoundRect(RectF(outer.left, outer.top + shadow, outer.right, outer.bottom + shadow), radius, radius, paint)
    paint.color = android.graphics.Color.WHITE
    canvas.drawRoundRect(outer, radius, radius, paint)
    val body = RectF(halo, halo, outer.right - halo, height - halo)
    paint.color = colors.ground or 0xFF000000.toInt()
    canvas.drawRoundRect(body, radius - halo, radius - halo, paint)
    paint.color = colors.warning or 0xFF000000.toInt()
    paint.style = AndroidPaint.Style.STROKE
    paint.strokeWidth = (0.8f * density).coerceAtLeast(1f)
    canvas.drawRoundRect(body, radius - halo, radius - halo, paint)
    paint.style = AndroidPaint.Style.FILL
    // The warning triangle with rounded corners, an exclamation mark in white.
    val x0 = halo + pad
    val y0 = height / 2 - mark / 2 - 0.4f * density
    val triangle = AndroidPath().apply {
        moveTo(x0 + mark * 0.5f, y0 + mark * 0.12f)
        lineTo(x0 + mark * 0.94f, y0 + mark * 0.88f)
        lineTo(x0 + mark * 0.06f, y0 + mark * 0.88f)
        close()
    }
    paint.color = colors.warning or 0xFF000000.toInt()
    canvas.drawPath(triangle, paint)
    paint.style = AndroidPaint.Style.STROKE
    paint.strokeWidth = mark * 0.16f
    paint.strokeJoin = AndroidPaint.Join.ROUND
    canvas.drawPath(triangle, paint)
    paint.color = android.graphics.Color.WHITE
    paint.strokeWidth = mark * 0.12f
    paint.strokeCap = AndroidPaint.Cap.ROUND
    canvas.drawLine(x0 + mark * 0.5f, y0 + mark * 0.40f, x0 + mark * 0.5f, y0 + mark * 0.60f, paint)
    paint.style = AndroidPaint.Style.FILL
    canvas.drawCircle(x0 + mark * 0.5f, y0 + mark * 0.77f, mark * 0.07f, paint)
    // The line (or the count) as a chip inside, at the right end.
    if (label != null) {
        val left = x0 + mark + 3 * density
        val top = height / 2 - chipHeight / 2
        val chip = RectF(left, top, left + chipWidth, top + chipHeight)
        paint.color = (ground ?: colors.warning) or 0xFF000000.toInt()
        canvas.drawRoundRect(chip, chipHeight / 2, chipHeight / 2, paint)
        val textX = chip.centerX() - textPaint.measureText(label) / 2
        val textY = chip.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(label, textX, textY, textPaint)
    }
    return bitmap
}
