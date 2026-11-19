package io.github.jnbp.nextstop.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.animation.DecelerateInterpolator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.data.OsmAreaPosition
import io.github.jnbp.nextstop.data.OsmLine
import io.github.jnbp.nextstop.data.OsmStation
import io.github.jnbp.nextstop.data.OsmStations
import io.github.jnbp.nextstop.data.Stop
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
 * "Haltepositionen und Eingänge": what OpenStreetMap knows about the stations and stops close
 * up, from zoom 16 on. The entrances of U-Bahn and S-Bahn stations as small "U ↗" and "S ↗"
 * signs ("R ↗" in grey at a station of regional trains only), accesses through a building (a
 * mall or hotel door with a way on to the station) as a pale chip with a door and the letter,
 * elevators as a small lift sign.
 *
 * Bus and tram stop positions show only for the selected stop (see setSelectedStopPositions):
 * each as a white pill on a small pin standing on the position, "Pos. 3" in grey and the lines
 * that stop there as bold text in their colours ("M43 104"). White with coloured text, unlike
 * the live vehicles (filled badges in the line colour with direction noses), and below them.
 * The positions of all other stops stay hidden, so the map does not fill with them; every stop
 * keeps its classic "H" sign at every zoom (see MapLayers). The details give way to the stop
 * signs and the vehicles (MapLibre places the layers above first) and lie below them.
 * Data © OpenStreetMap contributors, ODbL 1.0.
 */

/** From this zoom on the stop positions, entrances and lifts show. */
const val STATION_DETAIL_ZOOM = 16.0

private const val DETAIL_SOURCE = "ns-station-details"
private const val DETAIL_ENTRANCE_LAYER = "ns-detail-entrances"
private const val DETAIL_LIFT_LAYER = "ns-detail-lifts"
private const val DETAIL_ESCALATOR_LAYER = "ns-detail-escalators"
// The stop positions of the selected stop with their lines (see setSelectedStopPositions).
private const val SELECTED_POS_SOURCE = "ns-selected-positions"
private const val SELECTED_POS_LAYER = "ns-selected-positions"
private const val SELECTED_POS_IMAGE = "ns-selected-pos:"
// The entrances, lifts and escalators of the marked station, from their own source, each kind
// in its own layer for its own zoom (see detailMinZoom), right below the pills.
private const val SELECTED_DETAIL_SOURCE = "ns-selected-details"
private const val SELECTED_ENTRANCE_LAYER = "ns-selected-entrances"
private const val SELECTED_LIFT_LAYER = "ns-selected-lifts"
private const val SELECTED_ESCALATOR_LAYER = "ns-selected-escalators"
private val DETAIL_LAYERS = arrayOf(
    SELECTED_POS_LAYER, SELECTED_ENTRANCE_LAYER, SELECTED_LIFT_LAYER, SELECTED_ESCALATOR_LAYER,
    DETAIL_ENTRANCE_LAYER, DETAIL_LIFT_LAYER, DETAIL_ESCALATOR_LAYER,
)

/**
 * From this zoom on the positions of the selected stop show (fading in over half a step); further
 * out the positions of one stop would lie on top of each other.
 */
const val SELECTED_POSITION_ZOOM = 15.5

// The image of a stop position with its lines, made when a map first asks for it (see
// [provideStationDetailImage]); its name says what it shows: prefix, number, "|", the lines as
// "t" or "b" and the name, separated by commas. The main map no longer draws these images.
private const val POS_LINES_IMAGE = "ns-pos-lines:"

// The most chips drawn beside one pill; more are summed up as "+n".
private const val MAX_LINE_CHIPS = 6

// The density the images are drawn for, set when the layers are installed.
private var detailDensity = 1f
private const val ENTRANCE_IMAGE = "ns-detail-entrance-"
private const val BUILDING_ACCESS_IMAGE = "ns-detail-building-access-"
private val ENTRANCE_SIGNS = listOf("U", "S", "SU", "R")
private const val LIFT_IMAGE = "ns-detail-lift"
private const val ESCALATOR_IMAGE = "ns-detail-escalator"

// The lowest layer of the vehicles: the details go below it (see installAppLayers).
private const val BELOW_LAYER = "ns-vehicle-dots"

// The colours of the signs: U-Bahn blue, S-Bahn green, the pills neutral.
private val SUBWAY_GROUND = 0xFF0664AB.toInt()
private val SUBURBAN_GROUND = 0xFF008D4F.toInt()
private val REGIONAL_GROUND = 0xFF5F6670.toInt()
private val BUILDING_ACCESS_GROUND = 0xFFF2F4F7.toInt()
private val PILL_INK = 0xFF2B2F36.toInt()
private val PILL_EDGE = 0xFF9AA0A8.toInt()
// The pill of a stop position of the selected stop: "Pos. 3" in grey, the pin dark.
private val POSITION_INK = 0xFF6B7280.toInt()
private val POSITION_EDGE = 0xFFC3C8D0.toInt()
private val POSITION_PIN = 0xFF2B2F36.toInt()
private val LIFT_GROUND = 0xFF5A6072.toInt()

/** The setting "Haltepositionen auf der Karte" while the app runs; stored in [AppPrefs.stationDetails]. */
object StationDetailSetting {
    var shown by mutableStateOf(true)
        private set
    private var loaded = false

    /** Reads the stored setting once per process. */
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        shown = AppPrefs(context).stationDetails
    }

    fun update(context: Context, value: Boolean) {
        shown = value
        AppPrefs(context).stationDetails = value
    }
}

/** The setting "Ein- und Ausgänge anzeigen" while the app runs; stored in [AppPrefs.entranceMode]. */
object EntranceSetting {
    var mode by mutableStateOf(EntranceMode.Auto)
        private set
    private var loaded = false

    /** Reads the stored setting once per process. */
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        mode = EntranceMode.of(AppPrefs(context).entranceMode)
    }

    fun update(context: Context, value: EntranceMode) {
        mode = value
        AppPrefs(context).entranceMode = value.key
    }
}

// The details' layers with their kind and whether they belong to the marked station.
private val KIND_LAYERS = listOf(
    Triple(DETAIL_ENTRANCE_LAYER, DetailKind.Entrance, false),
    Triple(DETAIL_LIFT_LAYER, DetailKind.Lift, false),
    Triple(DETAIL_ESCALATOR_LAYER, DetailKind.Escalator, false),
    Triple(SELECTED_ENTRANCE_LAYER, DetailKind.Entrance, true),
    Triple(SELECTED_LIFT_LAYER, DetailKind.Lift, true),
    Triple(SELECTED_ESCALATOR_LAYER, DetailKind.Escalator, true),
)

// Whether the map is free for the details at all (see showStationDetails).
private var detailsFree = true

// The features of the details, made once from the loaded data (again when a pack of another
// state was added: OsmStations.generation).
@Volatile
private var detailData: FeatureCollection? = null

@Volatile
private var detailGeneration = -1

/**
 * The entrances and lifts as features; empty until [OsmStations] is loaded. Made once: the
 * layers pick what they show by zoom (minZoom) and by "kind". The stop positions are not among
 * them: only those of the selected stop show, from their own source.
 */
private fun detailFeatures(): FeatureCollection {
    val generation = OsmStations.generation
    detailData?.takeIf { detailGeneration == generation }?.let { return it }
    if (!OsmStations.loaded) return FeatureCollection.fromFeatures(emptyList<Feature>())
    val features = mergedFeatures(OsmStations.stations.flatMap { stationFeatures(it) })
    detailGeneration = generation
    // Made safe for the map once, off the main thread, where the details are built (#284).
    return mapSafe(FeatureCollection.fromFeatures(features), properties = false).also { detailData = it }
}

/**
 * [features] without near-identical ones: of the same sign within DETAIL_MERGE_METERS only one
 * stays, a named entrance before an unnamed one (see mergeNearSymbols). At big stations OSM
 * often maps one lift or escalator several times (per level, per direction).
 */
private fun mergedFeatures(features: List<Feature>): List<Feature> {
    val symbols = features.map { feature ->
        val point = feature.geometry() as Point
        val kind = when (feature.getStringProperty("kind")) {
            "lift" -> DetailKind.Lift
            "escalator" -> DetailKind.Escalator
            else -> DetailKind.Entrance
        }
        DetailSymbol(
            kind = kind,
            icon = feature.getStringProperty("icon"),
            latitude = point.latitude(),
            longitude = point.longitude(),
            named = feature.hasProperty("ref") || feature.hasProperty("name"),
        )
    }
    return mergeNearSymbols(symbols).map { features[it] }
}

/**
 * The entrances and lifts of [station] as features: the entrance with its sign (an access
 * through a building with the door), name, letter, level, step-free access and opening
 * hours for the tap; [anchor] where the icon stands on its point (the layer of the selected
 * stop, whose pills stand on pins, reads it).
 */
private fun stationFeatures(station: OsmStation, anchor: String = Property.ICON_ANCHOR_CENTER): List<Feature> {
    val features = ArrayList<Feature>()
    for (entrance in station.entrances) {
        val marker = entrance.marker ?: station.products.ifEmpty { "U" }
        val feature = Feature.fromGeometry(Point.fromLngLat(entrance.longitude, entrance.latitude))
        feature.addStringProperty("kind", "entrance")
        feature.addStringProperty(
            "icon",
            (if (entrance.throughBuilding) BUILDING_ACCESS_IMAGE else ENTRANCE_IMAGE) + entranceSign(marker),
        )
        feature.addStringProperty("anchor", anchor)
        feature.addStringProperty("station", station.name)
        if (entrance.throughBuilding) feature.addStringProperty("building", "yes")
        entrance.openingHours?.let { feature.addStringProperty("hours", it) }
        entrance.name?.let { feature.addStringProperty("name", it) }
        entrance.ref?.let { feature.addStringProperty("ref", it) }
        entrance.level?.let { feature.addStringProperty("level", it) }
        entrance.wheelchair?.let { feature.addStringProperty("wheelchair", it) }
        feature.addNumberProperty("rank", detailSortKey(DetailKind.Entrance, named = entrance.ref != null || entrance.name != null))
        features.add(feature)
    }
    for (lift in station.lifts) {
        val feature = Feature.fromGeometry(Point.fromLngLat(lift.longitude, lift.latitude))
        feature.addStringProperty("kind", if (lift.escalator) "escalator" else "lift")
        feature.addStringProperty("icon", if (lift.escalator) ESCALATOR_IMAGE else LIFT_IMAGE)
        feature.addStringProperty("anchor", anchor)
        feature.addStringProperty("station", station.name)
        lift.level?.let { feature.addStringProperty("level", it) }
        feature.addNumberProperty("rank", detailSortKey(if (lift.escalator) DetailKind.Escalator else DetailKind.Lift, named = false))
        features.add(feature)
    }
    return features
}

/**
 * Builds the detail features off the main thread, after [OsmStations] is loaded, so that
 * [setStationDetails] and [installStationDetailLayers] find them made. A duplicate build in a
 * race is harmless.
 */
suspend fun prepareStationDetails() {
    withContext(Dispatchers.Default) { AllocProbe.measureAny(AllocProbe.DETAIL_BUILD) { detailFeatures() } }
}

/** The sign of an entrance: "U", "S", "SU" (both) or "R" (regional trains only). */
private fun entranceSign(marker: String): String = when {
    'S' in marker && 'U' in marker -> "SU"
    'S' in marker -> "S"
    'U' in marker -> "U"
    'R' in marker -> "R"
    else -> "U"
}

/**
 * Adds the source, images and layers of the details to [style], below the vehicles. Called by
 * installAppLayers after the vehicle layers are in; a style that has them already is left alone.
 */
fun installStationDetailLayers(style: Style, density: Float, font: Array<String>) {
    detailDensity = density
    for (sign in ENTRANCE_SIGNS) {
        if (style.getImage(ENTRANCE_IMAGE + sign) == null) style.addImage(ENTRANCE_IMAGE + sign, entranceBitmap(sign, density))
        if (style.getImage(BUILDING_ACCESS_IMAGE + sign) == null) {
            style.addImage(BUILDING_ACCESS_IMAGE + sign, buildingAccessBitmap(sign, density))
        }
    }
    if (style.getImage(LIFT_IMAGE) == null) style.addImage(LIFT_IMAGE, liftBitmap(density, escalator = false))
    if (style.getImage(ESCALATOR_IMAGE) == null) style.addImage(ESCALATOR_IMAGE, liftBitmap(density, escalator = true))
    if (style.getSource(DETAIL_SOURCE) == null) style.addSource(GeoJsonSource(DETAIL_SOURCE, detailFeatures()))
    val shown = if (StationDetailSetting.shown) Property.VISIBLE else Property.NONE
    // Added from the top down, each below the one before, so the entrances are placed first
    // among the details, then the lifts, then the escalators; each kind from its own zoom
    // (see detailMinZoom). They give way to the stop signs and pills above them and to each
    // other, never covering them.
    var below = BELOW_LAYER
    for ((id, kind) in listOf(
        DETAIL_ENTRANCE_LAYER to DetailKind.Entrance,
        DETAIL_LIFT_LAYER to DetailKind.Lift,
        DETAIL_ESCALATOR_LAYER to DetailKind.Escalator,
    )) {
        if (style.getLayer(id) == null) {
            addDetailLayer(style, detailLayer(id, DETAIL_SOURCE, kind, marked = false, shown = shown), below)
        }
        below = id
    }
}

/**
 * A layer of the details of [kind] from [source], from the zoom of [kind] (for the [marked]
 * station earlier): centred on their points, colliding with everything placed before them,
 * the more important ones first (their "rank", see detailSortKey).
 */
private fun detailLayer(id: String, source: String, kind: DetailKind, marked: Boolean, shown: String): SymbolLayer =
    SymbolLayer(id, source).apply {
        setFilter(Expression.eq(Expression.get("kind"), kind.key))
        minZoom = (detailMinZoom(kind, marked, EntranceSetting.mode) ?: MAX_MAP_ZOOM).toFloat()
        setProperties(
            PropertyFactory.iconImage(Expression.get("icon")),
            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
            PropertyFactory.iconAllowOverlap(false),
            PropertyFactory.iconIgnorePlacement(false),
            PropertyFactory.iconPadding(1f),
            PropertyFactory.symbolSortKey(Expression.coalesce(Expression.get("rank"), Expression.literal(1f))),
            PropertyFactory.visibility(if (detailMinZoom(kind, marked, EntranceSetting.mode) == null) Property.NONE else shown),
        )
    }

// Beyond any zoom of the map: a layer from here on never shows.
private const val MAX_MAP_ZOOM = 24.0

/**
 * Puts the setting "Ein- und Ausgänge anzeigen" ([EntranceSetting]) into the detail layers of
 * [style]: their zooms and which of them show. Call on the main thread when it changed.
 */
fun applyEntranceMode(style: Style) {
    val mode = EntranceSetting.mode
    for ((id, kind, marked) in KIND_LAYERS) {
        val layer = style.getLayer(id) ?: continue
        val from = detailMinZoom(kind, marked, mode)
        layer.minZoom = (from ?: MAX_MAP_ZOOM).toFloat()
        val visible = from != null && detailsFree && StationDetailSetting.shown
        layer.setProperties(PropertyFactory.visibility(if (visible) Property.VISIBLE else Property.NONE))
    }
}

/**
 * Adds the source and the layer of the selected stop's positions to [style], below the layer
 * [below] (the lowest vehicle layer): below the stop signs and the vehicles, above the other
 * details. The pills never push a sign or a vehicle aside: those are placed before them, and
 * the vehicles are drawn over them.
 */
fun installSelectedPositionLayer(style: Style, below: String) {
    if (style.getSource(SELECTED_POS_SOURCE) == null) {
        style.addSource(GeoJsonSource(SELECTED_POS_SOURCE, FeatureCollection.fromFeatures(emptyList<Feature>())))
    }
    if (style.getSource(SELECTED_DETAIL_SOURCE) == null) {
        style.addSource(GeoJsonSource(SELECTED_DETAIL_SOURCE, FeatureCollection.fromFeatures(emptyList<Feature>())))
    }
    val shown = if (StationDetailSetting.shown) Property.VISIBLE else Property.NONE
    if (style.getLayer(SELECTED_POS_LAYER) == null) {
        val layer = SymbolLayer(SELECTED_POS_LAYER, SELECTED_POS_SOURCE).apply {
            minZoom = SELECTED_POSITION_ZOOM.toFloat()
            setProperties(
                PropertyFactory.iconImage(Expression.get("icon")),
                // The tip of the pin on the position; a pill moved aside stands with the end of
                // its leader on it (see placePills), the hollow dot of a bare position centred.
                PropertyFactory.iconAnchor(Expression.coalesce(Expression.get("anchor"), Expression.literal(Property.ICON_ANCHOR_BOTTOM))),
                // Always drawn (pills that would cover a more important one are already shrunk
                // to their pin, see declutterSelectedPositions), and taking their room: the
                // details and other stops below give way to them, never cover them.
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(false),
                PropertyFactory.symbolSortKey(Expression.coalesce(Expression.get("rank"), Expression.literal(0f))),
                PropertyFactory.iconOpacity(selectedPositionOpacity(1f)),
                PropertyFactory.visibility(shown),
            )
        }
        addDetailLayer(style, layer, below)
    }
    // The marked station's entrances, lifts and escalators right below its pills, above the
    // other details: placed after the pills, before the other details.
    var under = SELECTED_POS_LAYER
    for ((id, kind) in listOf(
        SELECTED_ENTRANCE_LAYER to DetailKind.Entrance,
        SELECTED_LIFT_LAYER to DetailKind.Lift,
        SELECTED_ESCALATOR_LAYER to DetailKind.Escalator,
    )) {
        if (style.getLayer(id) == null) {
            addDetailLayer(style, detailLayer(id, SELECTED_DETAIL_SOURCE, kind, marked = true, shown = shown), under)
        }
        under = id
    }
}

/** The opacity of the selected stop's positions: [alpha], faded in by zoom from SELECTED_POSITION_ZOOM. */
private fun selectedPositionOpacity(alpha: Any): Expression = Expression.interpolate(
    Expression.linear(),
    Expression.zoom(),
    Expression.stop(SELECTED_POSITION_ZOOM, 0f),
    Expression.stop(SELECTED_POSITION_ZOOM + 0.5, alpha),
)

/**
 * Lets the positions of the selected stop pop in one after the other, [elapsedMillis] after
 * the first one started (see stopHop): each by its "order", POSITION_POP_STAGGER_MILLIS after
 * the one before, fading in while it grows past its size and settles. Call on the main thread.
 */
fun setSelectedPositionsAppear(style: Style, elapsedMillis: Float) {
    appearFrameAt = SystemClock.uptimeMillis()
    // How far each position has come in, 0 to 1, by its own place in the row.
    val progress = Expression.min(
        Expression.literal(1f),
        Expression.max(
            Expression.literal(0f),
            Expression.division(
                Expression.subtract(
                    Expression.literal(elapsedMillis),
                    Expression.product(
                        Expression.coalesce(Expression.get("order"), Expression.literal(0f)),
                        Expression.literal(POSITION_POP_STAGGER_MILLIS.toFloat()),
                    ),
                ),
                Expression.literal(POSITION_POP_MILLIS.toFloat()),
            ),
        ),
    )
    style.getLayer(SELECTED_POS_LAYER)?.setProperties(
        PropertyFactory.iconOpacity(selectedPositionOpacity(progress)),
        PropertyFactory.iconSize(
            Expression.interpolate(
                Expression.linear(),
                progress,
                Expression.stop(0f, 0.5f),
                Expression.stop(0.6f, 1.12f),
                Expression.stop(1f, 1f),
            ),
        ),
    )
}

/**
 * Puts the stop positions of [stop], the selected stop, with their lines into [style]: those of
 * the OSM bus and tram stop areas that belong to it (by its VBB number or its name, near it).
 * Null, or a stop without such areas, clears them, so only the selected stop ever shows its
 * positions. Call on the main thread after [OsmStations] is loaded; the images are made here.
 * Returns how many stop positions it put in.
 */
fun setSelectedStopPositions(style: Style, stop: Stop?): Int {
    val source = style.getSourceAs<GeoJsonSource>(SELECTED_POS_SOURCE) ?: return 0
    val latitude = stop?.latitude
    val longitude = stop?.longitude
    val areas = if (stop == null || latitude == null || longitude == null || !OsmStations.loaded) {
        emptyList()
    } else {
        OsmStations.areasOf(stop.id, stop.name, latitude to longitude)
    }
    // A marked station of the U-Bahn, S-Bahn or trains shows its entrances, lifts and
    // escalators in layers of their own, from an earlier zoom than the other stations.
    val station = if (stop == null || latitude == null || longitude == null || !OsmStations.loaded) {
        null
    } else {
        OsmStations.station(stop.id, stop.name, latitude to longitude)
    }
    style.getSourceAs<GeoJsonSource>(SELECTED_DETAIL_SOURCE)?.setGeoJsonSafely(
        FeatureCollection.fromFeatures(station?.let { mergedFeatures(stationFeatures(it)) }.orEmpty()),
    )
    val pills = ArrayList<SelectedPill>()
    for (area in areas) {
        for (position in area.positions) {
            // The colours of lines depend on the place (a bus 585 here is another one there):
            // the picture is kept per place, roughly.
            val here = position.latitude to position.longitude
            val icon = SELECTED_POS_IMAGE + (position.pos ?: "") + "|" +
                position.lines.joinToString(",") { (if (it.product == "tram") "t" else "b") + it.name } +
                "@" + (here.first * 10).roundToInt() + "," + (here.second * 10).roundToInt()
            // A position without a number and without lines: a small hollow dot, centred on it.
            val bare = position.pos == null && position.lines.isEmpty()
            val bitmap = if (bare) {
                style.getImage(BARE_POSITION_IMAGE) ?: barePositionBitmap(detailDensity).also { style.addImage(BARE_POSITION_IMAGE, it) }
            } else {
                style.getImage(icon) ?: selectedPositionBitmap(position.pos, position.lines, detailDensity, here).also {
                    style.addImage(icon, it)
                }
            }
            pills.add(
                SelectedPill(
                    area = area.name,
                    position = position,
                    icon = if (bare) BARE_POSITION_IMAGE else icon,
                    here = here,
                    bare = bare,
                    box = PillBox(
                        latitude = position.latitude,
                        longitude = position.longitude,
                        widthDp = bitmap.width / detailDensity,
                        heightDp = bitmap.height / detailDensity,
                        // Positions with more lines matter more; a numbered one before one without.
                        priority = if (bare) -1 else position.lines.size * 2 + (if (position.pos != null) 1 else 0),
                        pillWidthDp = if (bare) 0f else selectedPillWidth(position.pos, position.lines, detailDensity, here) / detailDensity,
                    ),
                ),
            )
        }
    }
    selectedPills = pills
    shownSides = null
    // New pills: nothing to hold on to, nothing to switch from.
    placedSides = null
    switchAnimator?.cancel()
    switchAnimator = null
    nearSigns = null
    if (pills.isNotEmpty() && style.getImage(PIN_ONLY_IMAGE) == null) {
        style.addImage(PIN_ONLY_IMAGE, selectedPositionBitmap(null, emptyList(), detailDensity, null))
    }
    source.setGeoJsonSafely(FeatureCollection.fromFeatures(pillFeatures(style, pills, List(pills.size) { PillSide.Above })))
    return pills.size
}

// The hollow dot of a stop position without a number and without lines.
private const val BARE_POSITION_IMAGE = SELECTED_POS_IMAGE + "bare"

// The station signs on the map (see setPillSignBoxes) and the marked stop's sign with its
// name (see setPillMarkedSign): the pills of the marked stop keep clear of them.
private var stationSignBoxes: List<SignBox> = emptyList()
private var markedSignBoxes: List<SignBox> = emptyList()

// The columns of vehicles beside a stop or on their way (see groupColumnBox): the pills keep
// clear of them too, so a bus standing at a position is never hidden by its pill nor the other way.
private var vehicleColumnBoxes: List<SignBox> = emptyList()

// Of those, the ones near the marked stop's pills; null: not picked yet.
private var nearSigns: List<SignBox>? = null

// Signs farther than this (in degrees, about 700 m) from every pill are left out of the declutter.
private const val NEAR_SIGN_DEGREES = 0.0065

/**
 * The station signs (S, U, R, F) now on the map as boxes the pills of the marked stop keep
 * clear of: a station sign always wins over a pill. Call on the main thread whenever the signs
 * change, then declutterSelectedPositions.
 */
fun setPillSignBoxes(signs: List<SignBox>) {
    if (signs == stationSignBoxes) return
    stationSignBoxes = signs
    nearSigns = null
    shownSides = null
}

/**
 * The columns of vehicles now on the map as boxes the pills of the marked stop keep clear of:
 * the vehicles keep their place, a pill moves aside or shows as its pin. True when they
 * changed: then call declutterSelectedPositions. Called per frame of the vehicles, so cheap.
 */
fun setPillVehicleBoxes(boxes: List<SignBox>): Boolean {
    if (boxes == vehicleColumnBoxes) return false
    vehicleColumnBoxes = boxes
    return true
}

/** The marked stop's sign and the name below it as boxes the pills keep clear of; empty for none. */
fun setPillMarkedSign(signs: List<SignBox>) {
    if (signs == markedSignBoxes) return
    markedSignBoxes = signs
    nearSigns = null
    shownSides = null
}

// The signs near [pills], picked once per change of the pills or the signs.
private fun signsNear(pills: List<SelectedPill>): List<SignBox> {
    nearSigns?.let { return it }
    val all = markedSignBoxes + stationSignBoxes
    val near = if (pills.isEmpty()) {
        emptyList()
    } else {
        val south = pills.minOf { it.position.latitude } - NEAR_SIGN_DEGREES
        val north = pills.maxOf { it.position.latitude } + NEAR_SIGN_DEGREES
        val west = pills.minOf { it.position.longitude } - NEAR_SIGN_DEGREES * 1.6
        val east = pills.maxOf { it.position.longitude } + NEAR_SIGN_DEGREES * 1.6
        all.filter { it.latitude in south..north && it.longitude in west..east }
    }
    nearSigns = near
    return near
}

// The columns of vehicles near [pills]; few, and they move, so picked anew each time.
private fun vehiclesNear(pills: List<SelectedPill>): List<SignBox> {
    if (pills.isEmpty() || vehicleColumnBoxes.isEmpty()) return emptyList()
    val south = pills.minOf { it.position.latitude } - NEAR_SIGN_DEGREES
    val north = pills.maxOf { it.position.latitude } + NEAR_SIGN_DEGREES
    val west = pills.minOf { it.position.longitude } - NEAR_SIGN_DEGREES * 1.6
    val east = pills.maxOf { it.position.longitude } + NEAR_SIGN_DEGREES * 1.6
    return vehicleColumnBoxes.filter { it.latitude in south..north && it.longitude in west..east }
}

// The pin of a stop position alone, for a pill that gives way to a more important one.
private const val PIN_ONLY_IMAGE = SELECTED_POS_IMAGE + "|"

/**
 * A stop position of the marked stop with its pill picture ([icon]), where it is ([here], for
 * the colours of its lines), whether it is [bare] (no number, no lines) and the room it takes.
 */
private class SelectedPill(
    val area: String,
    val position: OsmAreaPosition,
    val icon: String,
    val here: Pair<Double, Double>,
    val bare: Boolean,
    val box: PillBox,
)

// The pills of the marked stop, and where each of them stands at the moment (null: not worked out yet).
private var selectedPills: List<SelectedPill> = emptyList()
private var shownSides: List<PillSide>? = null

/**
 * The features of [pills], each at its side of [sides]: on its pin, moved aside with a leader
 * (the picture made here when first needed), or its pin alone. A bare position always as its
 * hollow dot.
 */
private fun pillFeatures(style: Style, pills: List<SelectedPill>, sides: List<PillSide>): List<Feature> = pills.mapIndexed { index, pill ->
    pillFeature(style, index, pill, sides.getOrNull(index) ?: PillSide.Above)
}

/** The feature of the pill [pill] (the [index]th) at [side]; a [tappable] one carries the "kind" a tap looks for. */
private fun pillFeature(style: Style, index: Int, pill: SelectedPill, side: PillSide, tappable: Boolean = true): Feature {
    val position = pill.position
    val feature = Feature.fromGeometry(Point.fromLngLat(position.longitude, position.latitude))
    if (tappable) feature.addStringProperty("kind", "pos")
    val icon = when {
        pill.bare -> pill.icon
        side == PillSide.Above -> pill.icon
        side == PillSide.Pin -> PIN_ONLY_IMAGE
        else -> {
            val id = pill.icon + "#" + side.code
            if (style.getImage(id) == null) {
                style.addImage(id, shiftedPositionBitmap(position.pos, position.lines, detailDensity, pill.here, side))
            }
            id
        }
    }
    feature.addStringProperty("icon", icon)
    feature.addStringProperty(
        "anchor",
        when {
            pill.bare -> Property.ICON_ANCHOR_CENTER
            side == PillSide.Right -> Property.ICON_ANCHOR_LEFT
            side == PillSide.Left -> Property.ICON_ANCHOR_RIGHT
            side == PillSide.Below -> Property.ICON_ANCHOR_TOP
            else -> Property.ICON_ANCHOR_BOTTOM
        },
    )
    feature.addNumberProperty("order", index)
    // Lower is placed first: the most important pill.
    feature.addNumberProperty("rank", -pill.box.priority)
    position.pos?.let {
        feature.addStringProperty("pos", it)
        feature.addStringProperty("label", "Pos. $it")
    }
    if (pill.area.isNotBlank()) feature.addStringProperty("name", pill.area)
    if (position.lines.isNotEmpty()) {
        feature.addStringProperty("lines", position.lines.joinToString(", ") { it.name })
        feature.addStringProperty("lineCodes", position.lines.joinToString(",") { (if (it.product == "tram") "t" else "b") + it.name })
    }
    return feature
}

/**
 * Places the pills of the marked stop at [zoom] with the map turned by [bearing] (see
 * placePills): station signs always win; a pill that would cover one moves to a free side with
 * a leader line to its position, else shows as its pin; one that would cover a more important
 * pill shows as its pin alone. A pill that changes its place cross-fades there instead of
 * jumping (see animateSwitch). Cheap when nothing changes (a few boxes, no allocation of
 * features); call on the main thread when the camera moved.
 */
fun declutterSelectedPositions(style: Style, zoom: Double, bearing: Double) {
    val pills = selectedPills
    if (pills.isEmpty()) return
    // The places of the pills depend on the zoom, the turn of the map and what they keep clear
    // of, not on where the map stands: a pan (called for every frame of it) changes nothing.
    if (shownSides != null && zoom == decluttered[0] && bearing == decluttered[1] && vehicleColumnBoxes === declutteredVehicles) return
    AllocProbe.begin(AllocProbe.DECLUTTER)
    try {
        decluttered[0] = zoom
        decluttered[1] = bearing
        declutteredVehicles = vehicleColumnBoxes
        placeSelectedPositions(style, pills, zoom, bearing)
    } finally {
        AllocProbe.end(AllocProbe.DECLUTTER, pills.size)
    }
}

// The zoom and turn of the map the pills were last placed for, and the columns of vehicles then (see declutterSelectedPositions).
private val decluttered = doubleArrayOf(Double.NaN, Double.NaN)
private var declutteredVehicles: List<SignBox>? = null

/** Places the pills of the marked stop for [zoom] and [bearing]: see declutterSelectedPositions. */
private fun placeSelectedPositions(style: Style, pills: List<SelectedPill>, zoom: Double, bearing: Double) {
    val before = placedSides
    // From where they stand now, so a pill does not flicker between two places (see placePills).
    val sides = placePills(pills.map { it.box }, signsNear(pills) + vehiclesNear(pills), zoom, bearing, previous = before)
    if (sides == shownSides) return
    shownSides = sides
    placedSides = sides
    val switched = switchedPills(before, sides)
    if (before == null || switched.isEmpty() || !switchAnimated()) {
        // A switch still running ends where this one stands; the pop-in of new pills is left alone.
        val running = switchAnimator
        switchAnimator = null
        running?.cancel()
        if (running != null) resetSwitch(style)
        style.getSourceAs<GeoJsonSource>(SELECTED_POS_SOURCE)?.setGeoJsonSafely(FeatureCollection.fromFeatures(pillFeatures(style, pills, sides)))
        return
    }
    animateSwitch(style, pills, before, sides, switched)
}

// The sides the pills of the marked stop were last placed at: where the hysteresis and an
// animated switch start from; null for pills just put in.
private var placedSides: List<PillSide>? = null

// The switch of pills between their pin and a side running now, if one does.
private var switchAnimator: ValueAnimator? = null

// When the marked stop's pills last drew a frame of their pop-in (see setSelectedPositionsAppear).
private var appearFrameAt = 0L

// A pill moving between its pin and a side cross-fades over this long.
private const val PILL_SWITCH_MILLIS = 220L

/** True when a switch of pills is animated: animations on, and the pills not popping in right now. */
private fun switchAnimated(): Boolean =
    ValueAnimator.areAnimatorsEnabled() && SystemClock.uptimeMillis() - appearFrameAt > 2 * POSITION_POP_MILLIS

/**
 * Moves the [switched] pills of [pills] from their sides [before] to [after] as a short
 * cross-fade instead of a jump: the pill at its new place fades and grows in while its picture
 * at the old place fades out; then only the new ones stay. The others stand still.
 */
private fun animateSwitch(style: Style, pills: List<SelectedPill>, before: List<PillSide>, after: List<PillSide>, switched: Set<Int>) {
    val source = style.getSourceAs<GeoJsonSource>(SELECTED_POS_SOURCE) ?: return
    switchAnimator?.cancel()
    val features = ArrayList<Feature>(pills.size + switched.size)
    pills.forEachIndexed { index, pill ->
        val feature = pillFeature(style, index, pill, after[index])
        if (index in switched) {
            feature.addStringProperty("fade", "in")
            // The old picture: drawn, never tapped (it has no "kind"), faded out.
            val old = pillFeature(style, index, pill, before[index], tappable = false)
            old.addStringProperty("fade", "out")
            features.add(old)
        }
        features.add(feature)
    }
    setSwitchShare(style, 0f)
    source.setGeoJsonSafely(FeatureCollection.fromFeatures(features))
    val final = pillFeatures(style, pills, after)
    switchAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = PILL_SWITCH_MILLIS
        interpolator = DecelerateInterpolator()
        addUpdateListener { animation ->
            if (style.isFullyLoaded) setSwitchShare(style, animation.animatedValue as Float)
        }
        addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false

            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (switchAnimator === animation) switchAnimator = null
                // Cut short by a newer switch or new pills: those have set what is shown.
                if (cancelled || !style.isFullyLoaded) return
                resetSwitch(style)
                style.getSourceAs<GeoJsonSource>(SELECTED_POS_SOURCE)?.setGeoJsonSafely(FeatureCollection.fromFeatures(final))
            }
        })
        start()
    }
}

/** How far a switch of pills has come, 0 to 1: the new pictures fade and grow in, the old ones fade out. */
private fun setSwitchShare(style: Style, share: Float) {
    val fade = Expression.coalesce(Expression.get("fade"), Expression.literal(""))
    style.getLayer(SELECTED_POS_LAYER)?.setProperties(
        PropertyFactory.iconOpacity(
            selectedPositionOpacity(
                Expression.match(fade, Expression.literal(1f), Expression.stop("in", share), Expression.stop("out", 1f - share)),
            ),
        ),
        PropertyFactory.iconSize(
            Expression.match(fade, Expression.literal(1f), Expression.stop("in", 0.85f + 0.15f * share), Expression.stop("out", 1f)),
        ),
    )
}

/** The pills as they stand at rest: full, at their size. */
private fun resetSwitch(style: Style) {
    if (switchAnimator != null) return
    style.getLayer(SELECTED_POS_LAYER)?.setProperties(
        PropertyFactory.iconOpacity(selectedPositionOpacity(1f)),
        PropertyFactory.iconSize(1f),
    )
}

/** A tapped stop position of the marked stop: its number, its stop area's name, its lines and where it is. */
data class MapPositionPick(
    val pos: String?,
    val areaName: String?,
    val lines: List<OsmLine>,
    val latitude: Double,
    val longitude: Double,
) {
    val position: Pair<Double, Double> get() = latitude to longitude
}

/** The stop position pill of the marked stop at [point] on the screen, with a little room around the finger; null where none. */
fun stopPositionAt(map: MapLibreMap, point: PointF, slop: Float): MapPositionPick? {
    val area = RectF(point.x - slop, point.y - slop, point.x + slop, point.y + slop)
    val feature = map.queryRenderedFeatures(area, SELECTED_POS_LAYER).firstOrNull { it.hasProperty("kind") } ?: return null
    val spot = feature.geometry() as? Point ?: return null
    fun text(key: String): String? = if (feature.hasProperty(key)) feature.getStringProperty(key)?.takeIf { it.isNotBlank() } else null
    val lines = text("lineCodes")?.split(',')?.filter { it.length > 1 }?.map { item ->
        OsmLine(item.substring(1), if (item[0] == 't') "tram" else "bus")
    }.orEmpty()
    return MapPositionPick(text("pos"), text("name"), lines, spot.latitude(), spot.longitude())
}

/**
 * A stop position of the selected stop: a white pill with a thin grey edge and a soft shadow,
 * "Pos. 3" in grey (left out where [pos] is null) and the [lines] as bold text in their colours
 * (at most [MAX_LINE_CHIPS], the rest as "+n"), on a short pin whose dot marks the position at
 * the bottom of the picture. Without a number and lines, the pin alone. Unlike the vehicles,
 * which are badges filled with the line colour.
 */
private fun selectedPositionBitmap(pos: String?, lines: List<OsmLine>, density: Float, at: Pair<Double, Double>?): Bitmap {
    val text = pillText(pos, lines, density, at)
    val padding = 6f * density
    val pillHeight = PILL_HEIGHT_DP * density
    val stem = 6f * density
    val dot = PILL_DOT_DP * density
    val margin = PILL_MARGIN_DP * density
    val hasPill = text.label != null || text.parts.isNotEmpty()
    val width = if (hasPill) text.width + 2 * padding + 2 * margin else 4 * dot + 2 * margin
    val height = (if (hasPill) pillHeight else 0f) + stem + 2 * dot + margin
    val bitmap = Bitmap.createBitmap(width.toInt() + 1, height.toInt() + 1, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val centerX = (width.toInt() + 1) / 2f
    val bottom = height.toInt() + 1f
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG)
    // The pin: a thin stem from the pill down to a dot on the position.
    paint.color = POSITION_PIN
    paint.strokeWidth = 1.5f * density
    paint.strokeCap = AndroidPaint.Cap.ROUND
    val pillBottom = bottom - 2 * dot - stem
    canvas.drawLine(centerX, pillBottom, centerX, bottom - dot, paint)
    drawPositionDot(canvas, centerX, bottom - dot, density)
    if (!hasPill) return bitmap
    drawPill(canvas, RectF(margin, pillBottom - pillHeight, width - margin, pillBottom), text, density)
    return bitmap
}

/** The texts of a pill: "Pos. 3" in grey and the lines in their colours, measured. */
private class PillText(
    val label: String?,
    val parts: List<Pair<String, Int>>,
    val ink: AndroidPaint,
    val labelPaint: AndroidPaint,
    val width: Float,
)

private fun pillText(pos: String?, lines: List<OsmLine>, density: Float, at: Pair<Double, Double>?): PillText {
    val ink = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f * density
        typeface = Typeface.DEFAULT_BOLD
    }
    val labelPaint = AndroidPaint(ink).apply {
        textSize = 9.5f * density
        color = POSITION_INK
    }
    val parts = ArrayList<Pair<String, Int>>()
    val shown = lines.take(MAX_LINE_CHIPS)
    shown.forEach { line -> parts.add(line.name to readableOnWhite(lineColor(line.name, line.product, at))) }
    if (lines.size > shown.size) parts.add("+${lines.size - shown.size}" to POSITION_INK)
    val label = pos?.let { "Pos. $it" }
    val gap = 4f * density
    val width = (label?.let { labelPaint.measureText(it) } ?: 0f) +
        parts.sumOf { ink.measureText(it.first).toDouble() }.toFloat() +
        gap * ((if (label != null) 1 else 0) + parts.size - 1).coerceAtLeast(0)
    return PillText(label, parts, ink, labelPaint, width)
}

/** The width of the pill alone (no pin, no shadow) of a position, in pixels. */
private fun selectedPillWidth(pos: String?, lines: List<OsmLine>, density: Float, at: Pair<Double, Double>?): Float =
    pillText(pos, lines, density, at).width + 2 * 6f * density

/** The white pill with its thin edge and soft shadow in [box], its texts inside. */
private fun drawPill(canvas: AndroidCanvas, box: RectF, text: PillText, density: Float) {
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG)
    val shadow = 2f * density
    val radius = box.height() / 2f
    paint.color = POSITION_EDGE
    paint.setShadowLayer(shadow, 0f, shadow / 2, 0x40000000)
    canvas.drawRoundRect(box, radius, radius, paint)
    paint.clearShadowLayer()
    val inset = max(1f, density)
    paint.color = android.graphics.Color.WHITE
    canvas.drawRoundRect(RectF(box.left + inset, box.top + inset, box.right - inset, box.bottom - inset), radius - inset, radius - inset, paint)
    val gap = 4f * density
    var x = box.left + 6f * density
    val ink = text.ink
    val baseline = box.centerY() - (ink.descent() + ink.ascent()) / 2f
    text.label?.let { label ->
        canvas.drawText(label, x, box.centerY() - (text.labelPaint.descent() + text.labelPaint.ascent()) / 2f, text.labelPaint)
        x += text.labelPaint.measureText(label) + gap
    }
    for ((part, color) in text.parts) {
        ink.color = color
        canvas.drawText(part, x, baseline, ink)
        x += ink.measureText(part) + gap
    }
}

/** The dot of a stop position: dark with a white rim, centred at [x], [y]. */
private fun drawPositionDot(canvas: AndroidCanvas, x: Float, y: Float, density: Float) {
    val dot = PILL_DOT_DP * density
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG)
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(x, y, dot, paint)
    paint.color = POSITION_PIN
    canvas.drawCircle(x, y, dot - 0.8f * density, paint)
}

/**
 * A pill moved aside from its position because it would cover a station sign: the dot of the
 * position at the edge of the picture that stands on the position (left edge for [PillSide.Right],
 * right edge for [PillSide.Left], top for [PillSide.Below]), a hairline leader in the ink of the
 * pill's first line at 60 % from the dot to the pill, and the pill. Sizes as in shiftedPillSize.
 */
private fun shiftedPositionBitmap(pos: String?, lines: List<OsmLine>, density: Float, at: Pair<Double, Double>?, side: PillSide): Bitmap {
    val text = pillText(pos, lines, density, at)
    val pillWidth = text.width + 2 * 6f * density
    val (widthDp, heightDp) = shiftedPillSize(pillWidth / density, side)
    val width = (widthDp * density).toInt() + 1
    val height = (heightDp * density).toInt() + 1
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val dot = PILL_DOT_DP * density
    val leader = PILL_LEADER_DP * density
    val pillHeight = PILL_HEIGHT_DP * density
    val ink = text.parts.firstOrNull()?.second ?: POSITION_PIN
    val line = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = (ink and 0x00FFFFFF) or (0x99 shl 24)
        strokeWidth = 1.2f * density
        strokeCap = AndroidPaint.Cap.ROUND
    }
    when (side) {
        PillSide.Right -> {
            val cy = height / 2f
            canvas.drawLine(dot, cy, 2 * dot + leader, cy, line)
            drawPill(canvas, RectF(2 * dot + leader, cy - pillHeight / 2, 2 * dot + leader + pillWidth, cy + pillHeight / 2), text, density)
            drawPositionDot(canvas, dot, cy, density)
        }
        PillSide.Left -> {
            val cy = height / 2f
            val right = width.toFloat()
            canvas.drawLine(right - dot, cy, right - 2 * dot - leader, cy, line)
            val pillRight = right - 2 * dot - leader
            drawPill(canvas, RectF(pillRight - pillWidth, cy - pillHeight / 2, pillRight, cy + pillHeight / 2), text, density)
            drawPositionDot(canvas, right - dot, cy, density)
        }
        else -> {
            val cx = width / 2f
            canvas.drawLine(cx, dot, cx, 2 * dot + leader, line)
            val top = 2 * dot + leader
            drawPill(canvas, RectF(cx - pillWidth / 2, top, cx + pillWidth / 2, top + pillHeight), text, density)
            drawPositionDot(canvas, cx, dot, density)
        }
    }
    return bitmap
}

/** A stop position without a number and without lines: a small hollow ring, quieter than a pin. */
private fun barePositionBitmap(density: Float): Bitmap {
    val size = (9f * density).toInt() + 1
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG)
    val c = size / 2f
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(c, c, 3.6f * density, paint)
    paint.style = AndroidPaint.Style.STROKE
    paint.strokeWidth = 1.4f * density
    paint.color = POSITION_PIN
    canvas.drawCircle(c, c, 2.6f * density, paint)
    return bitmap
}

/** The colour of a line darkened where it would be too pale as text on white (yellow, light blue). */
private fun readableOnWhite(color: androidx.compose.ui.graphics.Color): Int {
    if (color.luminance() <= 0.45f) return color.toArgb()
    val factor = 0.62f
    return androidx.compose.ui.graphics.Color(color.red * factor, color.green * factor, color.blue * factor).toArgb()
}

private fun addDetailLayer(style: Style, layer: SymbolLayer, below: String) {
    if (style.getLayer(below) != null) style.addLayerBelow(layer, below) else style.addLayer(layer)
}

/** Puts the details into the source of [style] once [OsmStations] is loaded. Call on the main thread. */
fun setStationDetails(style: Style) {
    val source = style.getSourceAs<GeoJsonSource>(DETAIL_SOURCE) ?: return
    if (!OsmStations.loaded) return
    AllocProbe.measure(AllocProbe.STATION_DETAILS) { source.setGeoJson(detailFeatures()) }
}

/** Shows or hides the details: [shown] while the map is free, and only where the setting is on. */
fun showStationDetails(style: Style, shown: Boolean) {
    detailsFree = shown
    val value = if (shown && StationDetailSetting.shown) Property.VISIBLE else Property.NONE
    style.getLayer(SELECTED_POS_LAYER)?.setProperties(PropertyFactory.visibility(value))
    // The entrances, lifts and escalators also by the setting "Ein- und Ausgänge anzeigen".
    applyEntranceMode(style)
}

/**
 * What a tap at [point] on the screen hits among the details, as a short text to show: the
 * entrance with its name and step-free access (an access through a building also as such, with
 * its opening hours where known), the stop position with its stop, or the lift.
 * Null where it hits none.
 */
fun stationDetailAt(context: Context, map: MapLibreMap, point: PointF, slop: Float): String? {
    val area = RectF(point.x - slop, point.y - slop, point.x + slop, point.y + slop)
    val feature = map.queryRenderedFeatures(area, *DETAIL_LAYERS).firstOrNull { it.hasProperty("kind") } ?: return null
    fun text(key: String): String? = if (feature.hasProperty(key)) feature.getStringProperty(key)?.takeIf { it.isNotBlank() } else null
    return when (text("kind")) {
        "entrance" -> {
            val label = text("ref") ?: text("name")?.takeIf { it != text("station") }
            val parts = if (text("building") == "yes") {
                // "Mythos Germania · Zugang über Gebäude · Gesundbrunnen · Geöffnet Sa 11:00-17:00 (…)"
                listOfNotNull(label, context.getString(R.string.map_building_access)).toMutableList()
            } else {
                mutableListOf(
                    // "Eingang Eingang Nord" never (#240): a name that says the word itself loses it.
                    label?.let { io.github.jnbp.nextstop.data.doorLabelWithoutWord(it) }?.takeIf { it.isNotEmpty() }
                        ?.let { context.getString(R.string.map_entrance_named, it) } ?: context.getString(R.string.walk_target_entrance_plain),
                )
            }
            text("station")?.let { parts.add(it) }
            text("level")?.let { parts.add(context.getString(R.string.map_level, it)) }
            text("hours")?.let { parts.add(context.getString(R.string.map_opening_hours, it)) }
            when (text("wheelchair")) {
                "yes" -> parts.add(context.getString(R.string.map_step_free))
                "limited" -> parts.add(context.getString(R.string.map_step_free_limited))
                "no" -> parts.add(context.getString(R.string.map_not_step_free))
            }
            parts.joinToString(" · ")
        }
        "pos" -> listOfNotNull(text("label"), text("name"), text("lines"))
            .ifEmpty { listOf(context.getString(R.string.map_stop_position)) }
            .joinToString(" · ")
        "lift" -> listOfNotNull(
            context.getString(R.string.map_elevator),
            text("station"),
            text("level")?.let { context.getString(R.string.map_level, it) },
        ).joinToString(" · ")
        "escalator" -> listOfNotNull(context.getString(R.string.map_escalator), text("station")).joinToString(" · ")
        else -> null
    }
}

/**
 * Adds the image [id] to [style] when it is one of the stop positions with their lines, which
 * are made only when the map first shows them. For MapView.addOnStyleImageMissingListener.
 * True when it was one of them.
 */
fun provideStationDetailImage(style: Style, id: String): Boolean {
    if (!id.startsWith(POS_LINES_IMAGE)) return false
    val body = id.removePrefix(POS_LINES_IMAGE)
    val pos = body.substringBefore('|').takeIf { it.isNotEmpty() }
    val lines = body.substringAfter('|', "").split(',').filter { it.length > 1 }.map { item ->
        OsmLine(item.substring(1), if (item[0] == 't') "tram" else "bus")
    }
    style.addImage(id, stopPositionBitmap(pos, lines, detailDensity))
    return true
}

/**
 * A stop position as a small sign: the white pill "Pos. 3" (left out where [pos] is null) and
 * beside it a chip for each of its [lines] in the colour of the line, at most [MAX_LINE_CHIPS],
 * the rest as "+n". For the map and for the mini map of a walk, which can draw it at the end of
 * the walk with the lines of [io.github.jnbp.nextstop.data.OsmStations.linesAt].
 */
fun stopPositionBitmap(pos: String?, lines: List<OsmLine>, density: Float, at: Pair<Double, Double>? = null): Bitmap {
    val ink = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f * density
        typeface = Typeface.DEFAULT_BOLD
    }
    val height = 14f * density
    val gap = 2f * density
    val padding = 4f * density
    val pill = pos?.let { "Pos. $it" }
    val shown = lines.take(MAX_LINE_CHIPS)
    val more = lines.size - shown.size
    val chips = shown.map { it.name to lineColor(it.name, it.product, at).toArgb() } +
        (if (more > 0) listOf("+$more" to PILL_EDGE) else emptyList())
    val widths = ArrayList<Float>()
    pill?.let { widths.add(ink.measureText(it) + 2 * padding + 2f) }
    chips.forEach { (text, _) -> widths.add(max(height, ink.measureText(text) + 2 * padding)) }
    val total = (widths.sum() + gap * (widths.size - 1).coerceAtLeast(0)).coerceAtLeast(height)
    val bitmap = Bitmap.createBitmap(total.toInt() + 2, height.toInt() + 2, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val baselineOffset = -(ink.descent() + ink.ascent()) / 2f
    val fill = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG)
    var x = 1f
    var index = 0
    if (pill != null) {
        val w = widths[index++]
        val box = RectF(x, 1f, x + w, 1f + height)
        val radius = height / 2f
        fill.color = PILL_EDGE
        canvas.drawRoundRect(box, radius, radius, fill)
        val inset = max(1f, density)
        fill.color = android.graphics.Color.WHITE
        canvas.drawRoundRect(RectF(box.left + inset, box.top + inset, box.right - inset, box.bottom - inset), radius - inset, radius - inset, fill)
        ink.color = PILL_INK
        canvas.drawText(pill, box.left + padding + 1f, box.centerY() + baselineOffset, ink)
        x += w + gap
    }
    val corner = 3f * density
    for ((text, color) in chips) {
        val w = widths[index++]
        val box = RectF(x, 1f, x + w, 1f + height)
        // A thin white edge keeps light and dark chips apart from the map.
        fill.color = android.graphics.Color.WHITE
        canvas.drawRoundRect(RectF(box.left - 1f, box.top - 1f, box.right + 1f, box.bottom + 1f), corner + 1f, corner + 1f, fill)
        fill.color = color
        canvas.drawRoundRect(box, corner, corner, fill)
        ink.color = onLineColor(androidx.compose.ui.graphics.Color(color)).toArgb()
        canvas.drawText(text, box.centerX() - ink.measureText(text) / 2f, box.centerY() + baselineOffset, ink)
        x += w + gap
    }
    return bitmap
}

/** The sign of an entrance: its letters on blue (U), green (S) or both, with an arrow "↗". */
internal fun entranceBitmap(sign: String, density: Float): Bitmap {
    val text = (if (sign == "SU") "S+U" else sign) + " ↗"
    val ink = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 10f * density
        typeface = Typeface.DEFAULT_BOLD
    }
    val padding = 4f * density
    val height = (15 * density).toInt().coerceAtLeast(8)
    val width = (ink.measureText(text) + 2 * padding).toInt().coerceAtLeast(height)
    val bitmap = Bitmap.createBitmap(width + 2, height + 2, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val box = RectF(1f, 1f, width + 1f, height + 1f)
    val corner = 3f * density
    val outline = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    canvas.drawRoundRect(RectF(0f, 0f, width + 2f, height + 2f), corner + 1f, corner + 1f, outline)
    if (sign == "SU") {
        // Green on the left half, blue on the right.
        val green = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { color = SUBURBAN_GROUND }
        val blue = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { color = SUBWAY_GROUND }
        canvas.drawRoundRect(box, corner, corner, blue)
        canvas.save()
        canvas.clipRect(box.left, box.top, box.centerX(), box.bottom)
        canvas.drawRoundRect(box, corner, corner, green)
        canvas.restore()
    } else {
        val ground = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { color = signColor(sign) }
        canvas.drawRoundRect(box, corner, corner, ground)
    }
    val baseline = box.centerY() - (ink.descent() + ink.ascent()) / 2f
    canvas.drawText(text, box.left + padding, baseline, ink)
    return bitmap
}

/** The colour of a single-letter sign: S-Bahn green, U-Bahn blue, regional trains grey. */
private fun signColor(sign: String): Int = when (sign) {
    "S" -> SUBURBAN_GROUND
    "R" -> REGIONAL_GROUND
    else -> SUBWAY_GROUND
}

/**
 * The sign of an access through a building: a pale chip with a thin edge in the colour of the
 * mode, a small door on the left and the letters ("U", "S", "S+U", "R") in their colours, no
 * arrow; so it reads as a way in through a house, not as an entrance of the station.
 */
private fun buildingAccessBitmap(sign: String, density: Float): Bitmap {
    val letters = if (sign == "SU") listOf("S" to SUBURBAN_GROUND, "+" to PILL_INK, "U" to SUBWAY_GROUND) else listOf(sign to signColor(sign))
    val ink = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f * density
        typeface = Typeface.DEFAULT_BOLD
    }
    val padding = 3f * density
    val doorWidth = 6f * density
    val gap = 2f * density
    val height = (15 * density).toInt().coerceAtLeast(8)
    val textWidth = letters.sumOf { ink.measureText(it.first).toDouble() }.toFloat()
    val width = (padding + doorWidth + gap + textWidth + padding).toInt().coerceAtLeast(height)
    val bitmap = Bitmap.createBitmap(width + 2, height + 2, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val box = RectF(1f, 1f, width + 1f, height + 1f)
    val corner = 3f * density
    val edge = if (sign == "SU") SUBWAY_GROUND else signColor(sign)
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG)
    paint.color = android.graphics.Color.WHITE
    canvas.drawRoundRect(RectF(0f, 0f, width + 2f, height + 2f), corner + 1f, corner + 1f, paint)
    paint.color = edge
    canvas.drawRoundRect(box, corner, corner, paint)
    val inset = max(1f, density)
    paint.color = BUILDING_ACCESS_GROUND
    canvas.drawRoundRect(RectF(box.left + inset, box.top + inset, box.right - inset, box.bottom - inset), corner - inset / 2, corner - inset / 2, paint)
    // The door: a frame open at the bottom with a knob.
    val unit = height / 15f
    val doorLeft = box.left + padding
    val door = RectF(doorLeft, box.top + 3f * unit, doorLeft + doorWidth, box.bottom - 2.5f * unit)
    paint.color = edge
    paint.style = AndroidPaint.Style.STROKE
    paint.strokeWidth = 1.4f * unit
    paint.strokeJoin = AndroidPaint.Join.ROUND
    canvas.drawPath(
        AndroidPath().apply {
            moveTo(door.left, door.bottom)
            lineTo(door.left, door.top)
            lineTo(door.right, door.top)
            lineTo(door.right, door.bottom)
        },
        paint,
    )
    paint.style = AndroidPaint.Style.FILL
    canvas.drawCircle(door.right - 1.8f * unit, door.centerY() + 0.8f * unit, 0.9f * unit, paint)
    val baseline = box.centerY() - (ink.descent() + ink.ascent()) / 2f
    var x = door.right + gap
    for ((text, color) in letters) {
        ink.color = color
        canvas.drawText(text, x, baseline, ink)
        x += ink.measureText(text)
    }
    return bitmap
}

/** A small lift sign: two triangles, up and down, on a grey square; an escalator: a slanted step line. */
internal fun liftBitmap(density: Float, escalator: Boolean): Bitmap {
    val size = (15 * density).toInt().coerceAtLeast(8)
    val bitmap = Bitmap.createBitmap(size + 2, size + 2, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val box = RectF(1f, 1f, size + 1f, size + 1f)
    val corner = 3f * density
    canvas.drawRoundRect(RectF(0f, 0f, size + 2f, size + 2f), corner + 1f, corner + 1f, AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })
    canvas.drawRoundRect(box, corner, corner, AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { color = LIFT_GROUND })
    val ink = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    val cx = box.centerX()
    val unit = size / 15f
    if (escalator) {
        ink.style = AndroidPaint.Style.STROKE
        ink.strokeWidth = 1.6f * unit
        ink.strokeJoin = AndroidPaint.Join.ROUND
        ink.strokeCap = AndroidPaint.Cap.ROUND
        val path = AndroidPath().apply {
            moveTo(box.left + 3 * unit, box.bottom - 4 * unit)
            lineTo(box.left + 6 * unit, box.bottom - 4 * unit)
            lineTo(box.right - 6 * unit, box.top + 4 * unit)
            lineTo(box.right - 3 * unit, box.top + 4 * unit)
        }
        canvas.drawPath(path, ink)
    } else {
        val up = AndroidPath().apply {
            moveTo(cx, box.top + 2.5f * unit)
            lineTo(cx + 3.5f * unit, box.top + 6.5f * unit)
            lineTo(cx - 3.5f * unit, box.top + 6.5f * unit)
            close()
        }
        val down = AndroidPath().apply {
            moveTo(cx, box.bottom - 2.5f * unit)
            lineTo(cx + 3.5f * unit, box.bottom - 6.5f * unit)
            lineTo(cx - 3.5f * unit, box.bottom - 6.5f * unit)
            close()
        }
        canvas.drawPath(up, ink)
        canvas.drawPath(down, ink)
    }
    return bitmap
}
