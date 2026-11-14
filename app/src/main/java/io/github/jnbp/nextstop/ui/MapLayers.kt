package io.github.jnbp.nextstop.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.CornerPathEffect
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.data.NetworkLine
import io.github.jnbp.nextstop.data.PlaceKind
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.Vehicle
import io.github.jnbp.nextstop.data.ridePlace
import io.github.jnbp.nextstop.data.replacedProductOrGuess
import io.github.jnbp.nextstop.data.replacedProduct
import io.github.jnbp.nextstop.data.railReplacement
import io.github.jnbp.nextstop.data.shortStopName
import org.maplibre.android.maps.ImageContent
import org.maplibre.android.maps.ImageStretches
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import com.google.gson.JsonArray
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/*
 * The app's own layers on the map, drawn by MapLibre itself instead of by Compose: the rapid
 * transit network when zoomed out to the whole city, and the live vehicles. MapLibre places
 * them with collision detection and keeps the map smooth with many of them.
 *
 * Zoom tiers on the start page and the map tab:
 * - city (below [CITY_ZOOM]): only the S- and U-Bahn network as coloured lines;
 * - district (up to [NEIGHBOURHOOD_ZOOM]): S-, U-Bahn and regional vehicles as small dots
 *   without labels, and only the stations they serve;
 * - neighbourhood (from [NEIGHBOURHOOD_ZOOM]): every vehicle as a line badge with its
 *   direction, and every stop.
 *
 * The stops are signs in a layer of their own (see [setStopSigns]): bigger stations are placed
 * first, smaller stops give way where they would cover them. Bus and tram stops keep their "H"
 * at every zoom. All stops lie below the live vehicles, which are always drawn on top.
 */

/** Below this zoom the map shows the whole city: the network, no vehicles and no stops. */
const val CITY_ZOOM = 12.0

/** From this zoom on everything is shown: every vehicle with its line, every stop. */
const val NEIGHBOURHOOD_ZOOM = 14.0

/** How much the map shows around the start page and the map tab, by zoom (see the tiers above). */
enum class MapTier { City, District, Neighbourhood }

fun mapTier(zoom: Double): MapTier = when {
    zoom < CITY_ZOOM -> MapTier.City
    zoom < NEIGHBOURHOOD_ZOOM -> MapTier.District
    else -> MapTier.Neighbourhood
}

// Products of the rapid network: shown from the district tier on, and placed first.
private val MAJOR_PRODUCTS = setOf("suburban", "subway", "regional", "express")

/** True for S-Bahn, U-Bahn and regional or long-distance trains. */
fun isMajorProduct(product: String): Boolean = product in MAJOR_PRODUCTS

private const val VEHICLE_SOURCE = "ns-vehicles"
private const val NETWORK_TRAM_LAYER = "ns-network-tram"

// Every tier change fades over this many zoom levels instead of popping.
private const val TIER_FADE = 0.8

// Tram tracks of the tiles show from this zoom on: thin and quiet.
private const val TRAM_ZOOM = 12.5

// The quiet red of tram tracks.
private val TRAM_NETWORK = Color(0xFFBE1414)

/** An opacity that rises from 0 at [from] to [full] over [TIER_FADE] zoom levels, for a layer that fades in. */
private fun fadeIn(from: Double, full: Float = 1f): Expression = Expression.interpolate(
    Expression.linear(),
    Expression.zoom(),
    Expression.stop(from, 0f),
    Expression.stop(from + TIER_FADE, full),
)

/** An opacity that fades in at [inAt] and out again towards [outAt]. */
private fun fadeInOut(inAt: Double, outAt: Double): Expression = Expression.interpolate(
    Expression.linear(),
    Expression.zoom(),
    Expression.stop(inAt, 0f),
    Expression.stop(inAt + TIER_FADE, 1f),
    Expression.stop(outAt - TIER_FADE, 1f),
    Expression.stop(outAt, 0f),
)

/**
 * [fadeIn] for the vehicle layers: the full strength is also scaled by how far each vehicle
 * has come in (its "op", see appearShare), so a vehicle that appears fades in.
 */
private fun appearFadeIn(from: Double, full: Float = 1f): Expression = Expression.interpolate(
    Expression.linear(),
    Expression.zoom(),
    Expression.stop(from, 0f),
    Expression.stop(from + TIER_FADE, appearScaled(full)),
)

/** [fadeInOut] scaled by how far each vehicle has come in, as [appearFadeIn]. */
private fun appearFadeInOut(inAt: Double, outAt: Double): Expression = Expression.interpolate(
    Expression.linear(),
    Expression.zoom(),
    Expression.stop(inAt, 0f),
    Expression.stop(inAt + TIER_FADE, appearScaled(1f)),
    Expression.stop(outAt - TIER_FADE, appearScaled(1f)),
    Expression.stop(outAt, 0f),
)

/** [full] times the share a feature has come in by: its "op", 1 where it has none. */
private fun appearScaled(full: Float): Expression =
    Expression.product(Expression.literal(full), Expression.coalesce(Expression.get("op"), Expression.literal(1f)))

private const val NETWORK_CASING_LAYER = "ns-network-casing"
private const val NETWORK_LAYER = "ns-network"
private const val VEHICLE_DOT_LAYER = "ns-vehicle-dots"
// The small dot at the middle of vehicles lined up on their way (see VehicleClusters.kt).
private const val VEHICLE_CLUSTER_DOT_LAYER = "ns-vehicle-cluster-dots"
private const val VEHICLE_ARROW_LAYER = "ns-vehicle-arrows"
private const val VEHICLE_NOSE_LAYER = "ns-vehicle-noses"
private const val RAPID_SOURCE = "ns-rapid"
private const val RAPID_CASING_LAYER = "ns-rapid-casing"
private const val RAPID_LAYER = "ns-rapid-lines"
private const val VEHICLE_MINOR_LAYER = "ns-vehicle-minor"
private const val VEHICLE_MAJOR_LAYER = "ns-vehicle-major"
// The pulse around the live dot of a train. The dot itself is part of the badge image (see
// badgeImageId), so badge and dot always show and hide together.
private const val VEHICLE_PULSE_LAYER = "ns-vehicle-live-pulse"
private const val LIVE_PULSE_IMAGE = "ns-live-pulse"
private const val NOSE_IMAGE = "ns-nose-"
private const val STOP_SOURCE = "ns-stops"
// Every stop as its sign, the classic "H" of a bus or tram stop included, at every zoom from the
// district tier on. Below the vehicles, so a live vehicle is never covered by a stop.
private const val STOP_LAYER = "ns-stop-signs"
private const val STOP_IMAGE = "ns-stop-"
// The selected stop: its sign with a green ring and its name in green below it, at every tier,
// still below the vehicles. Its stop positions with their lines lie between it and the other
// stops (see StationDetailLayer).
private const val SELECTED_STOP_SOURCE = "ns-selected-stop"
private const val SELECTED_STOP_LAYER = "ns-selected-stop"
private const val SELECTED_STOP_IMAGE = "ns-stop-selected-"
// With the setting "Haltestellen als Namen", bus and tram stops ("H") are drawn from these two
// layers instead of the stop layer: their "H" until STOP_NAME_ZOOM, their name in green from it.
private const val STOP_NAMED_SIGN_LAYER = "ns-stop-named-signs"
private const val STOP_NAME_LAYER = "ns-stop-names"
/** From this zoom on, with the setting on, bus and tram stops show their name instead of the "H". */
const val STOP_NAME_ZOOM = 16.0
// The green of the "H" and of a stop's name on the map, as the names of the stops looked before.
private val STOP_NAME_GREEN = 0xFF007A3D.toInt()

// The noses come in these distances from the middle of the badge to its edge, in dp: one
// image each, so the nose always starts at the edge of a badge of any width and heading.
private const val NOSE_REACH_MIN = 7
private const val NOSE_REACH_MAX = 22

// The vector source of the OpenFreeMap styles, in the OpenMapTiles schema.
private const val TILE_SOURCE = "openmaptiles"

/** The layers a tap on the map can hit, most important first. */
private val VEHICLE_LAYERS = arrayOf(VEHICLE_MAJOR_LAYER, VEHICLE_MINOR_LAYER, VEHICLE_DOT_LAYER)

/** Every layer of the app's own, so they can be shown and hidden together. */
private val OWN_LAYERS = listOf(
    NETWORK_CASING_LAYER, NETWORK_LAYER, NETWORK_TRAM_LAYER, RAPID_CASING_LAYER, RAPID_LAYER, VEHICLE_DOT_LAYER, VEHICLE_CLUSTER_DOT_LAYER, VEHICLE_ARROW_LAYER,
    VEHICLE_MINOR_LAYER, VEHICLE_MAJOR_LAYER, VEHICLE_NOSE_LAYER, VEHICLE_PULSE_LAYER, STOP_LAYER, STOP_NAMED_SIGN_LAYER, STOP_NAME_LAYER,
    SELECTED_STOP_LAYER,
)

// Colours of the network drawn from the tiles while the app's own one is not loaded yet:
// U-Bahn blue, S-Bahn green, other railways grey.
private val SUBURBAN_NETWORK = Color(0xFF2D8B3C)
private val SUBWAY_NETWORK = Color(0xFF115D91)
private val RAIL_NETWORK = Color(0xFF9AA0A8)

// The tile network: shown while this is true, that is until the app's own network has lines.
private var tileNetworkShown = true

// Vehicles are moved on about this often while they glide: about 30 frames per second.
private const val VEHICLE_LAYER_FRAME_MILLIS = 33L

// While nothing glides, the vehicles are looked at again this often.
private const val VEHICLE_LAYER_REST_MILLIS = 400L

// The pulse of the live dots is drawn about this often: about 15 frames per second, enough
// for a soft beat, and the map is only drawn again for it while live vehicles are in view.
private const val LIVE_PULSE_FRAME_MILLIS = 66L

/**
 * Adds the app's sources, images and layers to a [style] that has just loaded; a style that
 * already has them is left as it is. Called after every change of the map style, which drops
 * them. [density] is that of the screen, for the size of the images; [signColors] are the
 * colours of the stop signs that follow the look of the app.
 */
fun installAppLayers(style: Style, density: Float, signColors: StopSignColors) {
    // The badge images come per line colour as vehicles appear (see BadgeImages).
    if (style.getImage(LIVE_PULSE_IMAGE) == null) style.addImage(LIVE_PULSE_IMAGE, livePulseBitmap(density), true)
    // The images of every style of direction, so switching between them needs no new style.
    DirectionStyle.entries.forEach { it.drawing.installImages(style, density) }
    if (style.getImage(STOP_IMAGE + BUS_SIGN) == null) setStopSignColors(style, density, signColors)
    if (style.getSource(STOP_SOURCE) == null) {
        style.addSource(GeoJsonSource(STOP_SOURCE, FeatureCollection.fromFeatures(emptyList<Feature>())))
    }
    if (style.getSource(SELECTED_STOP_SOURCE) == null) {
        style.addSource(GeoJsonSource(SELECTED_STOP_SOURCE, FeatureCollection.fromFeatures(emptyList<Feature>())))
    }
    if (style.getSource(RAPID_SOURCE) == null) {
        style.addSource(GeoJsonSource(RAPID_SOURCE, rapidNetworkData ?: FeatureCollection.fromFeatures(emptyList<Feature>())))
    }
    if (style.getSource(VEHICLE_SOURCE) == null) {
        style.addSource(GeoJsonSource(VEHICLE_SOURCE, FeatureCollection.fromFeatures(emptyList<Feature>())))
    }
    val font = boldFont(style)
    // The first label layer of the style: the network goes below it, so names stay readable.
    val firstLabels = style.layers.firstOrNull { it is SymbolLayer }?.id

    if (style.getSource(TILE_SOURCE) != null && style.getLayer(NETWORK_LAYER) == null) {
        // Only the railways: U-Bahn, S-Bahn (light rail in the tiles) and the main lines; trams and the rest stay out.
        val rapid = tileNetworkFilter()
        // The network shows at the city tier and fades out above it, where the map has its own rails.
        val fade = Expression.interpolate(
            Expression.linear(),
            Expression.zoom(),
            Expression.stop(CITY_ZOOM - 0.5, 1f),
            Expression.stop(CITY_ZOOM + 0.5, 0f),
        )
        val width = Expression.interpolate(
            Expression.linear(),
            Expression.zoom(),
            Expression.stop(9, 1.2f),
            Expression.stop(CITY_ZOOM, 3f),
        )
        val casing = LineLayer(NETWORK_CASING_LAYER, TILE_SOURCE).apply {
            sourceLayer = "transportation"
            setFilter(rapid)
            maxZoom = (CITY_ZOOM + 0.5).toFloat()
            setProperties(
                PropertyFactory.lineColor(Color.White.toArgb()),
                PropertyFactory.lineWidth(
                    Expression.interpolate(
                        Expression.linear(),
                        Expression.zoom(),
                        Expression.stop(9, 2.6f),
                        Expression.stop(CITY_ZOOM, 5f),
                    ),
                ),
                PropertyFactory.lineOpacity(fade),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        }
        val network = LineLayer(NETWORK_LAYER, TILE_SOURCE).apply {
            sourceLayer = "transportation"
            setFilter(rapid)
            maxZoom = (CITY_ZOOM + 0.5).toFloat()
            setProperties(
                PropertyFactory.lineColor(
                    Expression.switchCase(
                        Expression.eq(Expression.get("subclass"), "subway"),
                        Expression.color(SUBWAY_NETWORK.toArgb()),
                        Expression.eq(Expression.get("subclass"), "light_rail"),
                        Expression.color(SUBURBAN_NETWORK.toArgb()),
                        Expression.color(RAIL_NETWORK.toArgb()),
                    ),
                ),
                PropertyFactory.lineWidth(width),
                PropertyFactory.lineOpacity(fade),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        }
        addBelow(style, casing, firstLabels)
        addBelow(style, network, firstLabels)
    }
    if (style.getSource(TILE_SOURCE) != null && style.getLayer(NETWORK_TRAM_LAYER) == null) {
        // Tram tracks from a closer zoom on, thin and quiet, fading in. Bus routes have no
        // geometry in the tiles (only roads), so buses show by their vehicles alone.
        val trams = LineLayer(NETWORK_TRAM_LAYER, TILE_SOURCE).apply {
            sourceLayer = "transportation"
            setFilter(Expression.all(Expression.eq(Expression.get("class"), "rail"), Expression.eq(Expression.get("subclass"), "tram")))
            minZoom = TRAM_ZOOM.toFloat()
            setProperties(
                PropertyFactory.lineColor(TRAM_NETWORK.toArgb()),
                PropertyFactory.lineWidth(
                    Expression.interpolate(
                        Expression.linear(),
                        Expression.zoom(),
                        Expression.stop(TRAM_ZOOM, 0.8f),
                        Expression.stop(16, 2.4f),
                    ),
                ),
                PropertyFactory.lineOpacity(fadeIn(TRAM_ZOOM, full = 0.45f)),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        }
        addBelow(style, trams, firstLabels)
    }

    if (style.getLayer(RAPID_LAYER) == null) {
        // The app's own network: every S- and U-Bahn line in its colour, lines on the same
        // track a little apart, side by side (see setRapidNetwork).
        val fade = Expression.interpolate(
            Expression.linear(),
            Expression.zoom(),
            Expression.stop(CITY_ZOOM - 0.5, 1f),
            Expression.stop(CITY_ZOOM + 0.5, 0f),
        )
        val offset = Expression.interpolate(
            Expression.linear(),
            Expression.zoom(),
            Expression.stop(9, Expression.product(Expression.get("slot"), Expression.literal(1.1f))),
            Expression.stop(CITY_ZOOM, Expression.product(Expression.get("slot"), Expression.literal(2.6f))),
        )
        val casing = LineLayer(RAPID_CASING_LAYER, RAPID_SOURCE).apply {
            maxZoom = (CITY_ZOOM + 0.5).toFloat()
            setProperties(
                PropertyFactory.lineColor(Color.White.toArgb()),
                PropertyFactory.lineWidth(
                    Expression.interpolate(
                        Expression.linear(),
                        Expression.zoom(),
                        Expression.stop(9, 2.6f),
                        Expression.stop(CITY_ZOOM, 4.8f),
                    ),
                ),
                PropertyFactory.lineOffset(offset),
                PropertyFactory.lineOpacity(fade),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        }
        val lines = LineLayer(RAPID_LAYER, RAPID_SOURCE).apply {
            maxZoom = (CITY_ZOOM + 0.5).toFloat()
            setProperties(
                PropertyFactory.lineColor(Expression.toColor(Expression.get("color"))),
                PropertyFactory.lineWidth(
                    Expression.interpolate(
                        Expression.linear(),
                        Expression.zoom(),
                        Expression.stop(9, 1.2f),
                        Expression.stop(CITY_ZOOM, 2.8f),
                    ),
                ),
                PropertyFactory.lineOffset(offset),
                PropertyFactory.lineOpacity(fade),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        }
        addBelow(style, casing, firstLabels)
        addBelow(style, lines, firstLabels)
    }
    applyTileNetwork(style)

    if (style.getLayer(VEHICLE_DOT_LAYER) == null) {
        // District tier: rapid vehicles as small dots in their line colour.
        style.addLayer(
            CircleLayer(VEHICLE_DOT_LAYER, VEHICLE_SOURCE).apply {
                // The chips "+N" beside a stop are no vehicle of their own.
                setFilter(Expression.all(Expression.eq(Expression.get("major"), true), Expression.not(Expression.has("chip"))))
                minZoom = CITY_ZOOM.toFloat()
                maxZoom = (NEIGHBOURHOOD_ZOOM + TIER_FADE / 2).toFloat()
                // Fades in above the city tier and out while the badges fade in.
                val opacity = appearFadeInOut(CITY_ZOOM, NEIGHBOURHOOD_ZOOM + TIER_FADE / 2)
                setProperties(
                    PropertyFactory.circleColor(Expression.toColor(Expression.get("color"))),
                    PropertyFactory.circleRadius(4.5f),
                    PropertyFactory.circleStrokeColor(Color.White.toArgb()),
                    PropertyFactory.circleStrokeWidth(1.5f),
                    PropertyFactory.circleOpacity(opacity),
                    PropertyFactory.circleStrokeOpacity(opacity),
                )
            },
        )
    }
    if (style.getLayer(VEHICLE_CLUSTER_DOT_LAYER) == null) {
        // Neighbourhood tier: where vehicles on their way line up because their badges would
        // overlap, a small dot marks the place they are at, the column beside it.
        style.addLayer(
            CircleLayer(VEHICLE_CLUSTER_DOT_LAYER, VEHICLE_SOURCE).apply {
                setFilter(Expression.has("cdot"))
                minZoom = (NEIGHBOURHOOD_ZOOM - TIER_FADE / 2).toFloat()
                val opacity = appearFadeIn(NEIGHBOURHOOD_ZOOM - TIER_FADE / 2)
                setProperties(
                    PropertyFactory.circleColor(GROUP_CHIP_COLOR),
                    PropertyFactory.circleRadius(CLUSTER_DOT_RADIUS_DP),
                    PropertyFactory.circleStrokeColor(Color.White.toArgb()),
                    PropertyFactory.circleStrokeWidth(1.2f),
                    PropertyFactory.circleOpacity(opacity),
                    PropertyFactory.circleStrokeOpacity(opacity),
                )
            },
        )
    }
    // What shows the direction of the vehicles: parts below the badges and parts above them.
    DirectionStyle.entries.forEach { direction ->
        direction.drawing.layersBelow().forEach { layer -> if (style.getLayer(layer.id) == null) style.addLayer(layer) }
    }
    // Buses, trams and ferries below the trains.
    if (style.getLayer(VEHICLE_MINOR_LAYER) == null) style.addLayer(badgeLayer(VEHICLE_MINOR_LAYER, major = false, font = font))
    // The pulse around the live dot of a train, right below the train badges: it rings out
    // around the dot baked into the badge without veiling it.
    if (style.getLayer(VEHICLE_PULSE_LAYER) == null) style.addLayer(pulseLayer())
    // Trains are always shown, placed first.
    if (style.getLayer(VEHICLE_MAJOR_LAYER) == null) style.addLayer(badgeLayer(VEHICLE_MAJOR_LAYER, major = true, font = font))
    DirectionStyle.entries.forEach { direction ->
        direction.drawing.layersAbove().forEach { layer -> if (style.getLayer(layer.id) == null) style.addLayer(layer) }
    }
    // Stop positions, entrances and lifts from OpenStreetMap close up, below the vehicles.
    installStationDetailLayers(style, density, font)
    // The stop signs on top of everything of the app, the vehicles included: a station is the
    // most important thing on the map and is never hidden by a bus or train passing it.
    // MapLibre places the layers from the top down, so the signs are placed first; a vehicle
    // that would cover one is still drawn, below it (badges allow overlap, see badgeLayer).
    if (style.getLayer(STOP_LAYER) == null) addBelow(style, stopLayer(), null)
    // Bus and tram stops shown by name: below the other signs, so stations are placed first.
    if (style.getLayer(STOP_NAMED_SIGN_LAYER) == null) addBelow(style, namedSignLayer(), STOP_LAYER)
    if (style.getLayer(STOP_NAME_LAYER) == null) addBelow(style, stopNameLayer(font), STOP_NAMED_SIGN_LAYER)
    // The selected stop above the other stops and the vehicles.
    if (style.getLayer(SELECTED_STOP_LAYER) == null) addBelow(style, selectedStopLayer(font), null)
    // Its position pills (and its entrances, lifts, escalators) right below the vehicles and
    // above the other details: a bus or tram standing at a position is never hidden or culled
    // by its pill. From the top: station signs, vehicles, notices, pills, entrances.
    installSelectedPositionLayer(style, VEHICLE_DOT_LAYER)
    // The signs of disruptions right above the stop signs and their names, the marked one's
    // included (see MapNoticeLayer): a sign of disruptions belongs to its stop's sign and is
    // never hidden by it.
    installNoticeLayer(style, SELECTED_STOP_LAYER)
}

/**
 * How a vehicle badge on the map shows the way it heads. Each style draws through its own
 * [DirectionDrawing], so another one plugs in by a new entry here and a drawing for it: for
 * example dots trailing behind the badge, a chevron inside the badge at its leading end, a
 * ring with a tip around a smaller badge, or the badge itself shaped as an arrow. No style
 * at all (null where a style is asked for) shows no direction.
 */
enum class DirectionStyle {
    /** A small rounded nose at the edge of the badge, on the side it heads to. */
    NOSE,
}

/** The style of direction the map uses where the direction is shown at all. */
val DEFAULT_DIRECTION_STYLE = DirectionStyle.NOSE

/** The drawing of this style of direction. */
private val DirectionStyle.drawing: DirectionDrawing
    get() = when (this) {
        DirectionStyle.NOSE -> NoseDrawing
    }

/**
 * What one style of direction adds to the vehicle layers: its images, its layers below and
 * above the badges, and the properties each vehicle needs for them. Its layers draw only the
 * vehicles that carry its properties, so a vehicle without them shows no direction.
 */
private interface DirectionDrawing {
    /** Adds the images of the style to [style], unless they are there already. */
    fun installImages(style: Style, density: Float)

    /** Layers that lie below the badges. */
    fun layersBelow(): List<Layer>

    /** Layers that lie above the badges. */
    fun layersAbove(): List<Layer>

    /**
     * Adds to [feature], a vehicle with the badge text [label], what the layers need to show
     * it heading [bearing] (degrees clockwise from north) on a map turned by [mapBearing].
     */
    fun annotate(feature: Feature, label: String, bearing: Double, mapBearing: Double)
}

/**
 * The nose sits at the edge of the badge on the side it heads to and turns with the map, as it
 * points along the ground. The layer below the badges draws the nose with its soft white
 * outline; the same nose without outline is drawn again above them, so the outline of the
 * badge does not cut it off and badge and nose read as one shape.
 */
private object NoseDrawing : DirectionDrawing {
    override fun installImages(style: Style, density: Float) {
        for (reach in NOSE_REACH_MIN..NOSE_REACH_MAX) {
            if (style.getImage(NOSE_IMAGE + reach) == null) style.addImage(NOSE_IMAGE + reach, noseBitmap(density, reach), true)
        }
    }

    override fun layersBelow(): List<Layer> = listOf(noseLayer(VEHICLE_ARROW_LAYER, outline = true))

    override fun layersAbove(): List<Layer> = listOf(noseLayer(VEHICLE_NOSE_LAYER, outline = false))

    override fun annotate(feature: Feature, label: String, bearing: Double, mapBearing: Double) {
        feature.addNumberProperty("bearing", bearing)
        feature.addStringProperty("nose", NOSE_IMAGE + noseReach(label, bearing - mapBearing))
    }
}

private fun noseLayer(id: String, outline: Boolean): SymbolLayer =
    SymbolLayer(id, VEHICLE_SOURCE).apply {
        setFilter(Expression.has("nose"))
        minZoom = (NEIGHBOURHOOD_ZOOM - TIER_FADE / 2).toFloat()
        setProperties(
            PropertyFactory.iconOpacity(appearFadeIn(NEIGHBOURHOOD_ZOOM - TIER_FADE / 2)),
            PropertyFactory.iconImage(Expression.get("nose")),
            PropertyFactory.iconRotate(Expression.get("bearing")),
            PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
            PropertyFactory.iconColor(Expression.toColor(Expression.get("color"))),
            PropertyFactory.iconHaloColor(Color.White.toArgb()),
            PropertyFactory.iconHaloWidth(if (outline) 1.2f else 0f),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
        )
    }

/** The railways of the tiles that stand for the network while the app's own one is missing. */
private fun tileNetworkFilter(): Expression = Expression.any(
    Expression.eq(Expression.get("subclass"), "subway"),
    Expression.eq(Expression.get("subclass"), "light_rail"),
    Expression.all(Expression.eq(Expression.get("class"), "rail"), Expression.eq(Expression.get("subclass"), "rail")),
)

/**
 * The tile network where the app's own one has nothing: all of it until the own network has
 * lines; then still the main railways (the own one has no regional or long-distance lines),
 * and U-Bahn or S-Bahn tracks while the own network has no line of that kind (a network built
 * when one of them did not run), so neither is ever missing on the map.
 */
private fun applyTileNetwork(style: Style) {
    val filter = if (tileNetworkShown) {
        tileNetworkFilter()
    } else {
        Expression.any(
            Expression.all(Expression.eq(Expression.get("class"), "rail"), Expression.eq(Expression.get("subclass"), "rail")),
            Expression.all(Expression.literal(!ownHasSubway), Expression.eq(Expression.get("subclass"), "subway")),
            Expression.all(Expression.literal(!ownHasSuburban), Expression.eq(Expression.get("subclass"), "light_rail")),
        )
    }
    (style.getLayer(NETWORK_CASING_LAYER) as? LineLayer)?.setFilter(filter)
    (style.getLayer(NETWORK_LAYER) as? LineLayer)?.setFilter(filter)
}

// The app's own network as last set, so a new style gets it at once.
private var rapidNetworkData: FeatureCollection? = null

// Whether the app's own network has U-Bahn and S-Bahn lines (see applyTileNetwork).
private var ownHasSubway = false
private var ownHasSuburban = false

/**
 * Puts the app's own S- and U-Bahn network ([features] from [rapidNetworkFeatures]) into
 * [style]; the network of the tiles then steps back. Call on the main thread.
 */
fun setRapidNetwork(style: Style, features: FeatureCollection?) {
    if (features == null || features.features().isNullOrEmpty()) return
    rapidNetworkData = features
    style.getSourceAs<GeoJsonSource>(RAPID_SOURCE)?.setGeoJson(features)
    val names = features.features().orEmpty().mapNotNull { it.getStringProperty("line") }
    ownHasSubway = names.any { it.startsWith("U") }
    ownHasSuburban = names.any { it.startsWith("S") }
    tileNetworkShown = false
    applyTileNetwork(style)
}

/**
 * The [lines] of the network as map features with their colour (by [colorOf]) and a slot:
 * lines that share long stretches of track get different slots, which the map turns into a
 * small sideways offset, so they run side by side. May run off the main thread.
 */
fun rapidNetworkFeatures(lines: List<NetworkLine>, colorOf: (String) -> Color): FeatureCollection {
    val slots = networkSlots(lines)
    val features = lines.map { line ->
        val feature = Feature.fromGeometry(
            LineString.fromLngLats(line.points.map { (latitude, longitude) -> Point.fromLngLat(longitude, latitude) }),
        )
        feature.addStringProperty("line", line.line)
        feature.addStringProperty("color", hexColor(colorOf(line.line)))
        feature.addNumberProperty("slot", slots[line.line] ?: 0f)
        feature
    }
    return FeatureCollection.fromFeatures(features)
}

// Size of the cells, in degrees (about 110 by 70 metres), in which lines are found to share track.
private const val SLOT_CELL = 0.001

// Two lines that share at least this many cells run side by side.
private const val SHARED_CELLS = 6

/** Slots 0, 1, -1, 2, -2 … per line: lines sharing track get different ones. */
private fun networkSlots(lines: List<NetworkLine>): Map<String, Float> {
    val cells = lines.associate { line ->
        val set = HashSet<Long>()
        for (index in line.points.indices) {
            val (latitude, longitude) = line.points[index]
            set.add(cellOf(latitude, longitude))
            // Long straight stretches between the points of a simplified way count too.
            if (index > 0) {
                val (lastLatitude, lastLongitude) = line.points[index - 1]
                val steps = (maxOf(abs(latitude - lastLatitude), abs(longitude - lastLongitude)) / SLOT_CELL).toInt()
                for (step in 1 until steps) {
                    val share = step.toDouble() / steps
                    set.add(cellOf(lastLatitude + (latitude - lastLatitude) * share, lastLongitude + (longitude - lastLongitude) * share))
                }
            }
        }
        line.line to set
    }
    val order = listOf(0f, 1f, -1f, 2f, -2f, 3f, -3f)
    val result = HashMap<String, Float>()
    for (line in lines.sortedBy { it.line }) {
        val mine = cells.getValue(line.line)
        val taken = result.filterKeys { other -> cells.getValue(other).count { it in mine } >= SHARED_CELLS }.values.toSet()
        result[line.line] = order.firstOrNull { it !in taken } ?: 0f
    }
    // Centre the slots of each group a little: a line alone keeps 0, two side by side get ±0.5.
    return result.mapValues { (name, slot) ->
        val mine = cells.getValue(name)
        val partners = result.filterKeys { other -> other != name && cells.getValue(other).count { it in mine } >= SHARED_CELLS }
        if (partners.isNotEmpty() && partners.values.all { it == 0f || it == 1f } && (slot == 0f || slot == 1f)) slot - 0.5f else slot
    }
}

private fun cellOf(latitude: Double, longitude: Double): Long =
    (floor(latitude / SLOT_CELL).toLong() shl 32) xor (floor(longitude / SLOT_CELL).toLong() and 0xFFFFFFFFL)


/** Shows or hides all of the app's own layers, for example while a journey is on the map. */
fun showAppLayers(style: Style, shown: Boolean) {
    val value = if (shown) Property.VISIBLE else Property.NONE
    OWN_LAYERS.forEach { id -> style.getLayer(id)?.setProperties(PropertyFactory.visibility(value)) }
    showStationDetails(style, shown)
    showNoticeLayer(style, shown)
}

/**
 * The trip id of the vehicle drawn at [point] on the screen, if any: a little room around
 * the finger counts, the badges of trains before the others.
 */
fun vehicleAt(map: MapLibreMap, point: PointF, slop: Float): String? {
    val area = RectF(point.x - slop, point.y - slop, point.x + slop, point.y + slop)
    for (layer in VEHICLE_LAYERS) {
        val hit = map.queryRenderedFeatures(area, layer).firstOrNull { it.hasProperty("trip") }
        if (hit != null) return hit.getStringProperty("trip")
    }
    return null
}

/** The id of the stop whose sign is drawn at [point] on the screen, if any, with a little room around the finger. */
fun stopAt(map: MapLibreMap, point: PointF, slop: Float): String? {
    val area = RectF(point.x - slop, point.y - slop, point.x + slop, point.y + slop)
    return map.queryRenderedFeatures(area, SELECTED_STOP_LAYER, STOP_LAYER, STOP_NAMED_SIGN_LAYER, STOP_NAME_LAYER).firstOrNull { it.hasProperty("stop") }?.getStringProperty("stop")
}

/**
 * A named place of the style's own map (a shop, a park, a station) at [point] on the screen:
 * its name and position, or null where the map shows no such place. Labels of streets and
 * towns do not count.
 */
fun placeAt(map: MapLibreMap, point: PointF, slop: Float): Pair<String, Pair<Double, Double>>? {
    val style = map.style ?: return null
    val layers = style.layers.filter { it is SymbolLayer && it.id.startsWith("poi") }.map { it.id }.toTypedArray()
    if (layers.isEmpty()) return null
    val area = RectF(point.x - slop, point.y - slop, point.x + slop, point.y + slop)
    // The name in the language of the device where the map has it.
    val language = io.github.jnbp.nextstop.data.AppLanguage.code
    for (feature in map.queryRenderedFeatures(area, *layers)) {
        val name = listOf("name:$language", "name_$language", "name").firstNotNullOfOrNull { key ->
            if (feature.hasProperty(key)) feature.getStringProperty(key)?.takeIf { it.isNotBlank() } else null
        } ?: continue
        val spot = feature.geometry() as? Point ?: continue
        return name to (spot.latitude() to spot.longitude())
    }
    return null
}

private fun badgeLayer(id: String, major: Boolean, font: Array<String>): SymbolLayer =
    SymbolLayer(id, VEHICLE_SOURCE).apply {
        setFilter(Expression.eq(Expression.get("major"), major))
        minZoom = (NEIGHBOURHOOD_ZOOM - TIER_FADE / 2).toFloat()
        setProperties(
            // Fades in while the dots of the district tier fade out.
            PropertyFactory.iconOpacity(appearFadeIn(NEIGHBOURHOOD_ZOOM - TIER_FADE / 2)),
            PropertyFactory.textOpacity(appearFadeIn(NEIGHBOURHOOD_ZOOM - TIER_FADE / 2)),
            PropertyFactory.textField(Expression.get("label")),
            PropertyFactory.textFont(font),
            PropertyFactory.textSize(BADGE_TEXT_SP),
            // Beside a stop the badge sits in its row (see VehicleGroups.kt): the text moves,
            // and the badge, fitted around it, goes along.
            PropertyFactory.textOffset(Expression.get("toff")),
            PropertyFactory.textColor(Expression.toColor(Expression.get("ink"))),
            // A badge image per line colour, with the live dot baked into its corner for a
            // vehicle placed by live data: one symbol, so the dot never stands alone.
            PropertyFactory.iconImage(Expression.get("badge")),
            PropertyFactory.iconTextFit(Property.ICON_TEXT_FIT_BOTH),
            PropertyFactory.iconTextFitPadding(arrayOf(2f, 5f, 1f, 5f)),
            PropertyFactory.symbolSortKey(Expression.get("rank")),
            // Every vehicle is always drawn, never culled by a stop sign, a name or another
            // vehicle placed before it (the signs lie above and are placed first), and takes
            // its room: the pills and details below give way to it (see BADGE_COLLISION).
            PropertyFactory.iconAllowOverlap(BADGE_COLLISION.allowOverlap),
            PropertyFactory.textAllowOverlap(BADGE_COLLISION.allowOverlap),
            PropertyFactory.iconIgnorePlacement(BADGE_COLLISION.ignorePlacement),
            PropertyFactory.textIgnorePlacement(BADGE_COLLISION.ignorePlacement),
            PropertyFactory.iconPadding(1f),
        )
    }

/**
 * The soft ring that grows out of the live dot of a train and fades: a distance field, so its
 * halo widens it, and halo and opacity are animated as paint properties alone (see
 * VehicleLayer), which needs no new placement of the symbols. Only trains get it: their badges
 * are always drawn (see BADGE_COLLISION), so the ring never stands alone. The dot of buses,
 * trams and ferries, baked into the badge, does not pulse.
 */
private fun pulseLayer(): SymbolLayer =
    SymbolLayer(VEHICLE_PULSE_LAYER, VEHICLE_SOURCE).apply {
        setFilter(Expression.all(Expression.has("live"), Expression.eq(Expression.get("major"), true)))
        minZoom = (NEIGHBOURHOOD_ZOOM - TIER_FADE / 2).toFloat()
        // At the corner of the badge, which grows with its text, in its row beside a stop too
        // (worked out per vehicle, see addOffsets).
        setProperties(
            PropertyFactory.iconImage(LIVE_PULSE_IMAGE),
            PropertyFactory.iconOffset(Expression.get("poff")),
            PropertyFactory.iconColor(LIVE_GREEN.toArgb()),
            PropertyFactory.iconHaloColor(LIVE_GREEN.toArgb()),
            PropertyFactory.iconHaloWidth(0f),
            PropertyFactory.iconHaloBlur(0.6f),
            // Unseen until the beat starts.
            PropertyFactory.iconOpacity(0f),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
        )
    }

/**
 * The ring of the pulse as a distance field: a disc as large as the live dot, full inside and
 * 0.75 at its edge, falling off outside it over [LIVE_PULSE_REACH_DP], so a halo of up to that
 * width in dp grows it (the same field as the noses, see noseBitmap).
 */
private fun livePulseBitmap(density: Float): Bitmap {
    val core = (LIVE_DOT_RADIUS_DP + LIVE_DOT_OUTLINE_DP) * density
    val bands = (LIVE_PULSE_REACH_DP / SDF_BAND_DP).roundToInt()
    val size = (2 * (core + (LIVE_PULSE_REACH_DP + 1) * density)).toInt().coerceAtLeast(8)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val middle = size / 2f
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
    }
    for (band in bands downTo 1) {
        val distance = band * SDF_BAND_DP
        paint.alpha = ((0.75f - distance / 8f) * 255).roundToInt().coerceIn(0, 255)
        canvas.drawCircle(middle, middle, core + distance * density, paint)
    }
    paint.alpha = 255
    canvas.drawCircle(middle, middle, core, paint)
    return bitmap
}

private fun addBelow(style: Style, layer: Layer, below: String?) {
    if (below != null) style.addLayerBelow(layer, below) else style.addLayer(layer)
}

/** A bold font the style's own labels use, so its glyphs are served; a common one otherwise. */
private fun boldFont(style: Style): Array<String> {
    val fonts = style.layers.filterIsInstance<SymbolLayer>().mapNotNull { layer ->
        try {
            @Suppress("UNCHECKED_CAST")
            (layer.textFont.value as? Array<String>)?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }
    return fonts.firstOrNull { font -> font.any { it.contains("Bold") } } ?: fonts.firstOrNull() ?: arrayOf("Noto Sans Bold")
}

// The badge image before it is stretched around the text, in dp: a rounded box, nearly a
// pill so the round nose joins it softly, with a thin white rim as an outline.
private const val BADGE_BODY_WIDTH_DP = 20f
private const val BADGE_BODY_HEIGHT_DP = 14f
private const val BADGE_RADIUS_DP = 6.5f
private const val BADGE_RIM_DP = 0.4f

/**
 * The badge of a vehicle in the line colour [argb], stretched by the map around its text: only
 * the straight middle of the box stretches, so the round ends and the live dot keep their
 * shape. With [live] the live dot sits at its upper right corner, reaching a little beyond the
 * box; the image grows by that much above and to the right, and its content box (what the text
 * is fitted into) stays the box alone, so a badge with the dot is as large as one without.
 */
private fun addBadgeImage(style: Style, id: String, argb: Int, live: Boolean, density: Float) {
    val bodyWidth = (BADGE_BODY_WIDTH_DP * density).roundToInt().coerceAtLeast(8)
    val bodyHeight = (BADGE_BODY_HEIGHT_DP * density).roundToInt().coerceAtLeast(6)
    val margin = if (live) kotlin.math.ceil(liveDotOverhangDp() * density + 1f).toInt() else 0
    val bitmap = Bitmap.createBitmap(bodyWidth + margin, bodyHeight + margin, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    val body = RectF(0f, margin.toFloat(), bodyWidth.toFloat(), (margin + bodyHeight).toFloat())
    val radius = BADGE_RADIUS_DP * density
    canvas.drawRoundRect(body, radius, radius, paint)
    val rim = (BADGE_RIM_DP * density).coerceAtLeast(1f)
    paint.color = argb or 0xFF000000.toInt()
    canvas.drawRoundRect(RectF(body.left + rim, body.top + rim, body.right - rim, body.bottom - rim), radius - rim, radius - rim, paint)
    if (live) {
        val inset = LIVE_DOT_INSET_DP * density
        val x = body.right - inset
        val y = body.top + inset
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(x, y, (LIVE_DOT_RADIUS_DP + LIVE_DOT_OUTLINE_DP) * density, paint)
        paint.color = LIVE_GREEN.toArgb()
        canvas.drawCircle(x, y, LIVE_DOT_RADIUS_DP * density, paint)
    }
    // Stretch only between the round ends, which hold the dot; the dot lies within the end.
    val stretchX = listOf(ImageStretches(radius, bodyWidth - radius))
    val stretchY = listOf(ImageStretches(body.top + radius, body.bottom - radius))
    val content = ImageContent(body.left, body.top, body.right, body.bottom)
    style.addImage(id, bitmap, false, stretchX, stretchY, content)
}

/**
 * The badge images a style has got so far, one per line colour and kind (with or without the
 * live dot), made the first time a vehicle needs them: the map drops them with its style, so
 * a new style starts afresh. At most twice the number of line colours on the map.
 */
private class BadgeImages {
    private var style: Style? = null
    private val made = HashSet<String>()

    /** The id of the badge image for [argb] and [live] in [style], made there if it is new. */
    fun ensure(style: Style, argb: Int, hex: String, live: Boolean, density: Float): String {
        if (this.style !== style) {
            this.style = style
            made.clear()
        }
        val id = badgeImageId(hex, live)
        if (made.add(id) && style.getImage(id) == null) addBadgeImage(style, id, argb, live, density)
        return id
    }
}

// The distance field of the nose reaches this many bands of this many dp beyond its edge.
private const val SDF_BANDS = 6
private const val SDF_BAND_DP = 0.5f

/**
 * A small rounded nose pointing up, white so the map tints it in the line colour, whose base
 * lies [reach] dp above the middle of the image: at the edge of the badge, and a little inside
 * it, so badge and nose join. The image is turned about its middle to the heading.
 */
private fun noseBitmap(density: Float, reach: Int): Bitmap {
    val length = 5.5f * density
    val half = 4.2f * density
    val inside = 2.5f * density
    // Room around the shape for its outline.
    val size = (2 * (reach * density + length + (SDF_BANDS * SDF_BAND_DP + 1) * density)).toInt().coerceAtLeast(8)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val middle = size / 2f
    val base = middle - reach * density + inside
    val tip = middle - reach * density - length
    val path = AndroidPath().apply {
        moveTo(middle - half, base)
        lineTo(middle - half, base - inside)
        lineTo(middle, tip)
        lineTo(middle + half, base - inside)
        lineTo(middle + half, base)
        close()
    }
    // The map reads the image as a distance field: full inside, 0.75 at the edge and fading
    // outside it, so the white halo becomes a soft outline of the given width. The bands are
    // drawn from the outside in, each replacing what lies under it.
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = AndroidPaint.Style.FILL_AND_STROKE
        strokeJoin = AndroidPaint.Join.ROUND
        strokeCap = AndroidPaint.Cap.ROUND
        // Rounded tips: every corner of the shape is softened.
        pathEffect = CornerPathEffect(2.2f * density)
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
    }
    val canvas = AndroidCanvas(bitmap)
    for (band in SDF_BANDS downTo 1) {
        val distance = band * SDF_BAND_DP
        paint.strokeWidth = 2 * distance * density
        paint.alpha = ((0.75f - distance / 8f) * 255).roundToInt().coerceIn(0, 255)
        canvas.drawPath(path, paint)
    }
    paint.style = AndroidPaint.Style.FILL
    paint.strokeWidth = 0f
    paint.alpha = 255
    canvas.drawPath(path, paint)
    return bitmap
}

/** How one vehicle moves on the map between two updates. */
private class VehicleMotion(var path: List<Pair<Double, Double>>, var since: Long) {
    /** When the vehicle first appeared (uptime): it fades in from then (see appearShare). */
    val born: Long = since
    val glide = Glide(2)
    val spot = DoubleArray(2)

    // The last known direction on the ground (north and east share) and when the vehicle last moved in it.
    val way = doubleArrayOf(Double.NaN, Double.NaN, 0.0)

    // Beside a stop: the stop it stands at now (null on its way), the point it is drawn at
    // ([anchor]), how far it has moved there from its own place (0 to 1), and its offset in dp
    // from that point. All three ease, so a vehicle slides into its row and out of it.
    var anchorId: String? = null
    val anchor = DoubleArray(2)
    var snap = 0.0
    val offset = FloatArray(2)
    private var easedAt = -1L

    /**
     * Eases towards [slot] beside [anchor] for the time up to [millis]; with [jump] (hidden
     * behind the chip) at once, so it comes out of the chip when its row frees. True while it
     * still moves.
     */
    fun settle(anchor: GroupAnchor, slot: GroupSlot, millis: Long, jump: Boolean = false): Boolean {
        val share = if (jump || easedAt < 0) 1.0 else 1.0 - exp(-(millis - easedAt).coerceAtLeast(0L) / GROUP_EASE_MILLIS)
        if (snap < 1e-3 || jump) {
            this.anchor[0] = anchor.latitude
            this.anchor[1] = anchor.longitude
        } else {
            this.anchor[0] += (anchor.latitude - this.anchor[0]) * share
            this.anchor[1] += (anchor.longitude - this.anchor[1]) * share
        }
        snap += (1.0 - snap) * share
        offset[0] += ((slot.dx - offset[0]) * share).toFloat()
        offset[1] += ((slot.dy - offset[1]) * share).toFloat()
        easedAt = millis
        return 1.0 - snap > 1e-3 || abs(slot.dx - offset[0]) > 0.1f || abs(slot.dy - offset[1]) > 0.1f
    }

    /** Eases back onto its own way for the time up to [millis]; true while it still moves. */
    fun release(millis: Long): Boolean {
        if (snap == 0.0 && offset[0] == 0f && offset[1] == 0f) {
            easedAt = millis
            return false
        }
        val share = if (easedAt < 0) 1.0 else 1.0 - exp(-(millis - easedAt).coerceAtLeast(0L) / GROUP_EASE_MILLIS)
        snap -= snap * share
        offset[0] -= (offset[0] * share).toFloat()
        offset[1] -= (offset[1] * share).toFloat()
        if (snap < 1e-3 && abs(offset[0]) < 0.1f && abs(offset[1]) < 0.1f) {
            snap = 0.0
            offset[0] = 0f
            offset[1] = 0f
        }
        easedAt = millis
        return snap > 0.0
    }

    // The direction as drawn: it turns towards [way] softly, so a bend of the way is no jump.
    val heading = doubleArrayOf(Double.NaN, Double.NaN)
    var headingAt = 0L

    /** Turns the drawn heading towards the way for the time up to [millis]. */
    fun turn(millis: Long) {
        if (heading[0].isNaN()) {
            heading[0] = way[0]
            heading[1] = way[1]
        } else {
            val share = 1.0 - exp(-(millis - headingAt).coerceAtLeast(0L) / HEADING_EASE_MILLIS)
            heading[0] += (way[0] - heading[0]) * share
            heading[1] += (way[1] - heading[1]) * share
        }
        headingAt = millis
    }
}

// How softly the drawn heading of a vehicle follows its way: the time constant, in milliseconds.
private const val HEADING_EASE_MILLIS = 600.0

/**
 * The live [vehicles] in the vehicle layers of the map: each glides along its way from one
 * update to the next, about 30 times per second while any of them moves and [moving] is true.
 * With [shown] false the layers are emptied. [styleTick] changes whenever a style has loaded,
 * so the vehicles are put into the new one at once. [direction] is how the badges show the way
 * the vehicles head; null shows none.
 */
@Composable
fun VehicleLayer(
    map: MapLibreMap?,
    styleTick: Int,
    vehicles: List<Vehicle>,
    moving: Boolean,
    shown: Boolean,
    direction: DirectionStyle?,
) {
    val motions = remember { HashMap<String, VehicleMotion>() }
    val badges = remember { BadgeImages() }
    val groups = remember { VehicleGroups() }
    val density = LocalDensity.current.density
    val current by rememberUpdatedState(vehicles)
    val currentDirection by rememberUpdatedState(direction)
    // From when vehicles may come in (see appearShare): at the start held until their step,
    // after it each new one fades in; with animations off all are there at once.
    val animate = rememberAnimationsOn()
    val appearFrom = remember { longArrayOf(if (!animate) APPEAR_AT_ONCE else if (Startup.vehiclesIn) 0L else APPEAR_HELD) }
    LaunchedEffect(Unit) {
        if (appearFrom[0] != APPEAR_HELD) return@LaunchedEffect
        try {
            awaitIntroStep(IntroStep.VEHICLES)
        } finally {
            appearFrom[0] = SystemClock.uptimeMillis()
            Startup.vehiclesIn = true
        }
    }
    LaunchedEffect(map, styleTick, shown, moving) {
        val readyMap = map ?: return@LaunchedEffect
        var emptied = false
        while (true) {
            val style = readyMap.style?.takeIf { it.isFullyLoaded }
            val source = style?.getSourceAs<GeoJsonSource>(VEHICLE_SOURCE)
            var gliding = false
            if (style != null && source != null) {
                // Zoomed out to the city nothing is drawn: the layers rest.
                val visible = shown && readyMap.cameraPosition.zoom >= CITY_ZOOM
                if (visible) {
                    gliding = try {
                        val camera = readyMap.cameraPosition
                        pushVehicles(style, source, current, motions, badges, groups, density, camera.bearing, camera.zoom, currentDirection, appearFrom[0])
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        false
                    }
                    emptied = false
                } else if (!emptied) {
                    source.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()))
                    // No columns of vehicles any more: the pills take their own place again.
                    if (setPillVehicleBoxes(emptyList())) {
                        val camera = readyMap.cameraPosition
                        declutterSelectedPositions(style, camera.zoom, camera.bearing)
                    }
                    emptied = true
                }
            }
            delay(if (gliding && moving) VEHICLE_LAYER_FRAME_MILLIS else VEHICLE_LAYER_REST_MILLIS)
        }
    }
    // The beat of the live dots of trains: only while such trains are drawn close enough for badges.
    // Each frame waits for the app to draw, so the beat rests while the app is not shown.
    LaunchedEffect(map, styleTick, shown) {
        val readyMap = map ?: return@LaunchedEffect
        var beating = false
        while (true) {
            val layer = readyMap.style?.takeIf { it.isFullyLoaded }?.getLayer(VEHICLE_PULSE_LAYER)
            val wanted = layer != null && shown && current.any { it.realtime && isMajorProduct(it.product) } &&
                readyMap.cameraPosition.zoom >= NEIGHBOURHOOD_ZOOM - TIER_FADE / 2
            if (wanted && layer != null) {
                withFrameMillis { }
                val phase = livePulsePhase(SystemClock.uptimeMillis())
                try {
                    layer.setProperties(
                        PropertyFactory.iconHaloWidth(livePulseReach(phase)),
                        PropertyFactory.iconOpacity(appearFadeIn(NEIGHBOURHOOD_ZOOM - TIER_FADE / 2, full = livePulseOpacity(phase))),
                    )
                } catch (e: Exception) {
                    // The style went away while waiting for the frame: the next round finds the new one.
                }
                beating = true
                delay(LIVE_PULSE_FRAME_MILLIS)
            } else {
                if (beating) {
                    try {
                        layer?.setProperties(PropertyFactory.iconOpacity(0f))
                    } catch (e: Exception) {
                        // A layer of a style that is gone needs no rest.
                    }
                    beating = false
                }
                delay(VEHICLE_LAYER_REST_MILLIS)
            }
        }
    }
}

/**
 * The stops the vehicles line up beside (see VehicleGroups.kt): the signs on the map and the
 * selected stop. [version] changes with them, so the candidates of the vehicles are found anew.
 */
private object VehicleAnchors {
    var signs: List<GroupAnchor> = emptyList()
    var selected: GroupAnchor? = null
    var version = 0

    fun index(): AnchorIndex = AnchorIndex(selected?.let { chosen -> signs.filter { it.id != chosen.id } + chosen } ?: signs)
}

/** The stops the vehicles of the last refresh may stand at, found once per refresh and per change of the stops. */
private class VehicleGroups {
    private var vehicles: List<Vehicle>? = null
    private var version = -1
    private val candidates = HashMap<String, GroupAnchor>()

    /** The cluster each vehicle on its way was in during the last frame (see findClusters), so pairs hold together. */
    val clusterOf = HashMap<String, String>()

    /** The stop each trip of [vehicles] may stand at during this refresh. */
    fun candidates(vehicles: List<Vehicle>): Map<String, GroupAnchor> {
        if (vehicles !== this.vehicles || version != VehicleAnchors.version) {
            this.vehicles = vehicles
            version = VehicleAnchors.version
            candidates.clear()
            val index = VehicleAnchors.index()
            for (vehicle in vehicles) candidateAnchor(index, vehicle.path, vehicle.product)?.let { candidates[vehicle.tripId] = it }
        }
        return candidates
    }
}

// How softly a vehicle moves into its row beside a stop and out of it: the time constant, in ms.
private const val GROUP_EASE_MILLIS = 250.0

// The text size of the badges, in sp: text offsets are given in multiples of it.
private const val BADGE_TEXT_SP = 11f

// The grey of the chip "+N" for the vehicles that find no row beside a stop.
private const val GROUP_CHIP_COLOR = 0xFF5A6072.toInt()

// The from-time of appearShare that lets vehicles be there at once (animations off), and the
// one that keeps them unseen (the start has not reached them yet).
private const val APPEAR_AT_ONCE = Long.MIN_VALUE
private const val APPEAR_HELD = Long.MAX_VALUE

// A vehicle that appears fades in over this time.
private const val VEHICLE_APPEAR_MILLIS = 320f

/**
 * How far a vehicle [born] at that uptime has come in at [millis], 0 to 1, eased out: it fades
 * in from [born], or from [from] where that is later (see APPEAR_AT_ONCE and APPEAR_HELD).
 */
private fun appearShare(born: Long, from: Long, millis: Long): Float {
    if (from == APPEAR_AT_ONCE) return 1f
    if (from == APPEAR_HELD) return 0f
    val t = ((millis - maxOf(born, from)) / VEHICLE_APPEAR_MILLIS).coerceIn(0f, 1f)
    return 1f - (1f - t) * (1f - t)
}

/** Puts every vehicle at the place of its glide right now; true while any of them still moves. */
private fun pushVehicles(
    style: Style,
    source: GeoJsonSource,
    vehicles: List<Vehicle>,
    motions: HashMap<String, VehicleMotion>,
    badges: BadgeImages,
    groups: VehicleGroups,
    density: Float,
    mapBearing: Double,
    zoom: Double,
    direction: DirectionStyle?,
    appearFrom: Long,
): Boolean {
    val millis = SystemClock.uptimeMillis()
    var gliding = false
    val seen = HashSet<String>()
    val candidates = groups.candidates(vehicles)
    // First where each vehicle is, and which of them stand at a stop now.
    val placed = ArrayList<Pair<Vehicle, VehicleMotion>>(vehicles.size)
    val members = HashMap<String, MutableList<GroupMember>>()
    val anchors = HashMap<String, GroupAnchor>()
    for (vehicle in vehicles) {
        if (vehicle.path.isEmpty() || !seen.add(vehicle.tripId)) continue
        val motion = motions.getOrPut(vehicle.tripId) { VehicleMotion(vehicle.path, millis) }
        // Each update restarts the glide from the fresh position over the next half minute.
        if (motion.path !== vehicle.path) {
            motion.path = vehicle.path
            motion.since = millis
        }
        val progress = (millis - motion.since).toFloat() / VEHICLE_GLIDE_MILLIS
        if (progress < 1f + VEHICLE_SETTLE_SHARE) gliding = true
        val (latitude, longitude) = pointOnPath(vehicle.path, progress)
        motion.spot[0] = latitude
        motion.spot[1] = longitude
        motion.glide.follow(motion.spot, millis)
        groundDirection(vehicle.path, progress)?.let { (north, east) ->
            motion.way[0] = north
            motion.way[1] = east
            if (progress < 1f) motion.way[2] = millis.toDouble()
        }
        val anchor = candidates[vehicle.tripId]?.takeIf { isAt(it, motion.spot[0], motion.spot[1]) }
        if (anchor != null) {
            anchors[anchor.id] = anchor
            val label = shortLine(vehicle.line) ?: vehicle.line
            members.getOrPut(anchor.id) { ArrayList(4) }.add(GroupMember(vehicle.tripId, vehicleRank(vehicle.product), label, vehicle.direction))
        }
        motion.anchorId = anchor?.id
        placed.add(vehicle to motion)
    }
    // Then the vehicles on their way whose badges would overlap: they line up beside a dot at
    // their middle, as at a stop (see VehicleClusters.kt).
    val free = ArrayList<FreeVehicle>()
    for ((vehicle, motion) in placed) {
        if (motion.anchorId == null) free.add(FreeVehicle(vehicle.tripId, motion.spot[0], motion.spot[1], shortLine(vehicle.line) ?: vehicle.line))
    }
    val clusters = findClusters(free, zoom, groups.clusterOf)
    groups.clusterOf.clear()
    if (clusters.isNotEmpty()) {
        val byTrip = HashMap<String, Pair<Vehicle, VehicleMotion>>(placed.size * 2)
        for (entry in placed) byTrip[entry.first.tripId] = entry
        for (cluster in clusters) {
            anchors[cluster.id] = GroupAnchor(cluster.id, cluster.latitude, cluster.longitude, CLUSTER_DOT_RADIUS_DP)
            val group = ArrayList<GroupMember>(cluster.keys.size)
            for (key in cluster.keys) {
                val (vehicle, motion) = byTrip[key] ?: continue
                motion.anchorId = cluster.id
                groups.clusterOf[key] = cluster.id
                group.add(GroupMember(key, vehicleRank(vehicle.product), shortLine(vehicle.line) ?: vehicle.line, vehicle.direction))
            }
            members[cluster.id] = group
        }
    }
    val layouts = members.mapValues { (id, group) ->
        val anchor = anchors.getValue(id)
        // Below the green name from STOP_NAME_ZOOM on, where it stands instead of the "H".
        val below = if (zoom >= STOP_NAME_ZOOM) anchor.nameHalfHeightDp else 0f
        // Below the sign of disruptions on the sign's corner, where it has one.
        layoutGroup(anchor.halfWidthDp, group, below, topDp = NoticeSignSpots.columnTops[id])
    }
    // The columns take their room: the pills of the marked stop move aside or show as their pin
    // (see groupColumnBox). Only where the badges are drawn.
    val columns = ArrayList<SignBox>(layouts.size)
    if (zoom >= CLUSTER_MIN_ZOOM) {
        for ((id, layout) in layouts) groupColumnBox(anchors.getValue(id), layout, members.getValue(id))?.let { columns.add(it) }
    }
    if (setPillVehicleBoxes(columns)) declutterSelectedPositions(style, zoom, mapBearing)

    val features = ArrayList<Feature>(placed.size + layouts.size)
    for ((vehicle, motion) in placed) {
        val anchor = motion.anchorId?.let { anchors[it] }
        val layout = motion.anchorId?.let { layouts[it] }
        // In a row beside its stop, or on its own way; hidden behind the chip "+N".
        if (anchor != null && layout != null && vehicle.tripId in layout.hidden) {
            motion.settle(anchor, layout.chip ?: GroupSlot(0f, 0f), millis, jump = true)
            continue
        }
        val slot = layout?.slots?.get(vehicle.tripId)
        if (anchor != null && slot != null) {
            if (motion.settle(anchor, slot, millis)) gliding = true
        } else if (motion.release(millis)) {
            gliding = true
        }
        val latitude = motion.spot[0] + (motion.anchor[0] - motion.spot[0]) * motion.snap
        val longitude = motion.spot[1] + (motion.anchor[1] - motion.spot[1]) * motion.snap
        // A rail replacement bus ("Ersatzverkehr"): "SEV" and the line it stands in for, in that line's colour.
        val replacement = vehicle.railReplacement
        val replaced = replacement?.replaced
        val color = if (replaced != null) {
            lineColor(replaced, replacement?.replacedProductOrGuess ?: replacedProduct(replaced), vehicle.ridePlace)
        } else {
            lineColor(vehicle)
        }
        val feature = Feature.fromGeometry(Point.fromLngLat(longitude, latitude))
        feature.addStringProperty("trip", vehicle.tripId)
        val plainLabel = shortLine(vehicle.line) ?: vehicle.line
        val label = if (replacement != null) listOfNotNull(SEV_MARK, replaced?.let { shortLine(it) ?: it }).joinToString(" ") else plainLabel
        feature.addStringProperty("label", label)
        val hex = hexColor(color)
        feature.addStringProperty("color", hex)
        feature.addStringProperty("ink", hexColor(lineInk(vehicle, color)))
        feature.addBooleanProperty("major", isMajorProduct(vehicle.product))
        feature.addNumberProperty("rank", vehicleRank(vehicle.product))
        // Placed by live data: the badge with the live dot, whatever the vehicle. Placed by the
        // timetable: the badge without.
        if (vehicle.realtime) feature.addBooleanProperty("live", true)
        // A vehicle that just appeared fades in (see appearFadeIn); it glides meanwhile, so
        // the frames keep coming.
        val share = appearShare(motion.born, appearFrom, millis)
        if (share < 1f) {
            feature.addNumberProperty("op", share)
            gliding = true
        }
        feature.addStringProperty("badge", badges.ensure(style, color.toArgb(), hex, vehicle.realtime, density))
        addOffsets(feature, label, motion.offset[0], motion.offset[1])
        val known = !motion.way[0].isNaN() && millis - motion.way[2] < VEHICLE_HEADING_KEEP_MILLIS
        // In a row beside a stop the vehicle stands: no nose, which would point off its badge.
        if (known && direction != null && motion.snap < 0.05) {
            motion.turn(millis)
            // Compass bearing, clockwise from north, as the map turns icons. Halfway through
            // a turn back the eased heading can be nearly nothing: then the way itself counts.
            val eased = abs(motion.heading[0]) + abs(motion.heading[1]) > 1e-3
            val north = if (eased) motion.heading[0] else motion.way[0]
            val east = if (eased) motion.heading[1] else motion.way[1]
            val bearing = Math.toDegrees(atan2(east, north))
            direction.drawing.annotate(feature, label, bearing, mapBearing)
        } else {
            // Standing or unknown: nothing is drawn, and the next heading starts fresh.
            motion.heading[0] = Double.NaN
        }
        features.add(feature)
    }
    // The chips and dots of groups come in with the first vehicles, not before them.
    val introShare = appearShare(0L, appearFrom, millis)
    if (introShare < 1f) gliding = true
    // The chips "+N" of stops with more vehicles than rows, in grey; drawn always where a
    // train is among them, as its badges are.
    for ((id, layout) in layouts) {
        val chip = layout.chip ?: continue
        val label = layout.chipLabel ?: continue
        val anchor = anchors.getValue(id)
        val group = members.getValue(id)
        val feature = Feature.fromGeometry(Point.fromLngLat(anchor.longitude, anchor.latitude))
        feature.addStringProperty("label", label)
        feature.addBooleanProperty("chip", true)
        if (introShare < 1f) feature.addNumberProperty("op", introShare)
        val hex = String.format("#%06X", 0xFFFFFF and GROUP_CHIP_COLOR)
        feature.addStringProperty("color", hex)
        feature.addStringProperty("ink", "#FFFFFF")
        val rank = group.minOf { it.rank }
        feature.addBooleanProperty("major", rank <= 1)
        feature.addNumberProperty("rank", rank)
        feature.addStringProperty("badge", badges.ensure(style, GROUP_CHIP_COLOR, hex, false, density))
        addOffsets(feature, label, chip.dx, chip.dy)
        features.add(feature)
    }
    // The dot at the middle of each cluster of vehicles on their way.
    for (cluster in clusters) {
        val feature = Feature.fromGeometry(Point.fromLngLat(cluster.longitude, cluster.latitude))
        feature.addBooleanProperty("cdot", true)
        if (introShare < 1f) feature.addNumberProperty("op", introShare)
        features.add(feature)
    }
    motions.keys.retainAll(seen)
    source.setGeoJson(FeatureCollection.fromFeatures(features))
    return gliding
}

/**
 * Moves the badge of [feature] with the text [label] by ([dx], [dy]) dp from its point: the
 * text, which the badge is fitted around, and the pulse of the live dot at its corner.
 */
private fun addOffsets(feature: Feature, label: String, dx: Float, dy: Float) {
    feature.addProperty("toff", JsonArray().apply {
        add(dx / BADGE_TEXT_SP)
        add(dy / BADGE_TEXT_SP)
    })
    val corner = liveDotOffset(label.length)
    feature.addProperty("poff", JsonArray().apply {
        add(dx + corner[0])
        add(dy + corner[1])
    })
}

/**
 * How far, in dp, the edge of the badge with [label] lies from its middle in the direction
 * [screenBearing] (degrees clockwise from up on the screen): the badge is a box about as wide
 * as its text and stays upright, while the nose turns with the heading.
 */
private fun noseReach(label: String, screenBearing: Double): Int =
    badgeEdge(label, screenBearing).roundToInt().coerceIn(NOSE_REACH_MIN, NOSE_REACH_MAX)

/**
 * How far, in dp, the edge of the upright badge with [label] lies from its middle in the
 * direction [screenBearing] (degrees clockwise from up on the screen), kept within the sizes
 * a badge can have.
 */
private fun badgeEdge(label: String, screenBearing: Double): Double {
    val halfWidth = (label.length * BADGE_CHAR_DP + BADGE_SIDE_DP) / 2.0
    val halfHeight = BADGE_HEIGHT_DP / 2.0
    val angle = Math.toRadians(screenBearing)
    val across = abs(sin(angle))
    val along = abs(cos(angle))
    val reach = minOf(if (across > 1e-6) halfWidth / across else Double.MAX_VALUE, if (along > 1e-6) halfHeight / along else Double.MAX_VALUE)
    return reach.coerceIn(NOSE_REACH_MIN.toDouble(), NOSE_REACH_MAX.toDouble())
}

// The size of a badge as the map draws it: see BADGE_CHAR_DP and its neighbours (LiveDot.kt).

/** The order in which badges are placed: trains first, then trams, then buses. */
private fun vehicleRank(product: String): Int = when (product) {
    "subway", "suburban" -> 0
    "regional", "express" -> 1
    "tram" -> 2
    "bus" -> 3
    else -> 4
}

private fun hexColor(color: Color): String = String.format("#%06X", 0xFFFFFF and color.toArgb())

/** The colours of the stop signs that follow the look of the app, as ARGB: the ground of the sign and the dots of places and addresses. */
data class StopSignColors(val ground: Int, val poi: Int, val address: Int)

// Sizes of a stop sign, in dp: the symbols, the gap between them, the padding of the sign, its
// corner and its shadow.
private const val SIGN_SYMBOL_DP = 22f
private const val SIGN_GAP_DP = 3f
private const val SIGN_PADDING_DP = 5f
private const val SIGN_CORNER_DP = 16f
private const val SIGN_SHADOW_DP = 3f
// The ring of the selected stop: the white gap between sign and ring, and the ring.
private const val SIGN_RING_GAP_DP = 2f
private const val SIGN_RING_DP = 2.5f

// The signs of stops: S, U, R and F for rail in this order, H for bus and tram stops.
private const val RAIL_LETTERS = "SURF"
private const val POI_SIGN = "poi"
private const val ADDRESS_SIGN = "adr"
private const val BUS_SIGN = "H"

/** Every sign a stop can have: each combination of the rail letters, the H, and the dots. */
private val ALL_SIGNS: List<String> = buildList {
    add(BUS_SIGN)
    add(POI_SIGN)
    add(ADDRESS_SIGN)
    for (mask in 1 until (1 shl RAIL_LETTERS.length)) {
        add(RAIL_LETTERS.filterIndexed { index, _ -> (mask and (1 shl index)) != 0 })
    }
}

/**
 * Draws the stop signs again in [colors] (after a change of the look of the app); a style
 * without the sign images gets them. Call on the main thread.
 */
fun setStopSignColors(style: Style, density: Float, colors: StopSignColors) {
    ALL_SIGNS.forEach { sign ->
        style.addImage(STOP_IMAGE + sign, stopSignBitmap(sign, density, colors))
        style.addImage(SELECTED_STOP_IMAGE + sign, stopSignBitmap(sign, density, colors, ring = true))
    }
}

/** The key of the sign of [stop]: its rail letters, H, or a dot for a place or an address. */
private fun signOf(stop: Stop): String = when (stop.kind) {
    PlaceKind.Poi -> POI_SIGN
    PlaceKind.Address -> ADDRESS_SIGN
    PlaceKind.Stop -> buildString {
        if ("suburban" in stop.products) append('S')
        if ("subway" in stop.products) append('U')
        if ("regional" in stop.products) append('R')
        if ("express" in stop.products) append('F')
    }.ifEmpty { BUS_SIGN }
}

/** The order in which stops are placed, lowest first: long-distance and regional stations, then S and U, then the rest. */
private fun stopRank(stop: Stop): Int {
    val products = stop.products
    var weight = 0
    if ("express" in products) weight += 8
    if ("regional" in products) weight += 4
    if ("suburban" in products) weight += 3
    if ("subway" in products) weight += 3
    return -(weight * 10 + products.size.coerceAtMost(9))
}

/** The setting "Haltestellen als Namen (grün)" while the app runs; stored in [AppPrefs.stopNames]. */
object StopNameSetting {
    var named by mutableStateOf(false)
        private set
    private var loaded = false

    /** Reads the stored setting once per process. */
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        named = AppPrefs(context).stopNames
    }

    fun update(context: Context, value: Boolean) {
        named = value
        AppPrefs(context).stopNames = value
    }
}

/**
 * Puts [stops] as signs into the stop layer of [style]; stops without a position are left
 * out. The caller picks the stops by the zoom tier and leaves out the marked one, which is
 * drawn apart. With [named], bus and tram stops ("H") carry their name and go to the layers
 * that show it close up (see stopNameLayer). Call on the main thread.
 */
fun setStopSigns(style: Style, stops: List<Stop>, named: Boolean = false) {
    val source = style.getSourceAs<GeoJsonSource>(STOP_SOURCE) ?: return
    val features = ArrayList<Feature>(stops.size)
    val anchors = ArrayList<GroupAnchor>()
    val spots = ArrayList<NoticeSignSpot>(stops.size)
    for (stop in stops) {
        val latitude: Double = stop.latitude ?: continue
        val longitude: Double = stop.longitude ?: continue
        val feature = Feature.fromGeometry(Point.fromLngLat(longitude, latitude))
        feature.addStringProperty("stop", stop.id)
        val sign = signOf(stop)
        // Where a sign of disruptions sits on it (see MapNoticeLayer): its size, and that of its name close up.
        val shownByName = named && sign == BUS_SIGN
        val title = stop.name.shortStopName()
        spots.add(
            NoticeSignSpot(
                id = stop.id,
                name = stop.name,
                latitude = latitude,
                longitude = longitude,
                halfWidthDp = signHalfWidthDp(sign.length),
                halfHeightDp = SIGN_HALF_HEIGHT_DP,
                nameHalfWidthDp = if (shownByName) stopNameHalfWidthDp(title) else 0f,
                nameHalfHeightDp = if (shownByName) stopNameHalfHeightDp(title) else 0f,
            ),
        )
        // Vehicles at a stop line up beside its sign (see VehicleGroups.kt).
        if (stop.kind == PlaceKind.Stop) {
            // A stop shown by its green name close up keeps its vehicles below the name.
            val nameHalf = if (named && sign == BUS_SIGN) stopNameHalfHeightDp(stop.name.shortStopName()) else 0f
            anchors.add(GroupAnchor(stop.id, latitude, longitude, signHalfWidthDp(sign.length), anchorProducts(stop.products), nameHalf))
        }
        feature.addStringProperty("sign", STOP_IMAGE + sign)
        feature.addNumberProperty("rank", stopRank(stop))
        if (named && sign == BUS_SIGN) {
            feature.addBooleanProperty("named", true)
            feature.addStringProperty("name", stop.name.shortStopName())
        }
        features.add(feature)
    }
    source.setGeoJson(FeatureCollection.fromFeatures(features))
    NoticeSignSpots.signs = spots
    // The station signs win over the marked stop's pills, which keep clear of them.
    setPillSignBoxes(
        stops.mapNotNull { stop ->
            val latitude = stop.latitude ?: return@mapNotNull null
            val longitude = stop.longitude ?: return@mapNotNull null
            if (stop.kind != PlaceKind.Stop) return@mapNotNull null
            val sign = signOf(stop)
            if (sign == BUS_SIGN) return@mapNotNull null
            SignBox(latitude, longitude, signHalfWidthDp(sign.length), SIGN_HALF_HEIGHT_DP)
        },
    )
    if (anchors != VehicleAnchors.signs) {
        VehicleAnchors.signs = anchors
        VehicleAnchors.version++
    }
}

/**
 * Lets the stop signs of [style] show at [share] (0 to 1) of their strength, for their fade
 * at the start (see StopSignsIntro). Call on the main thread.
 */
fun setStopSignsShare(style: Style, share: Float) {
    val opacity = fadeIn(CITY_ZOOM, full = share)
    style.getLayer(STOP_LAYER)?.setProperties(PropertyFactory.iconOpacity(opacity))
    style.getLayer(STOP_NAMED_SIGN_LAYER)?.setProperties(PropertyFactory.iconOpacity(opacity))
    style.getLayer(STOP_NAME_LAYER)?.setProperties(PropertyFactory.textOpacity(share))
}

/**
 * The layer of the stop signs: they give way to each other, bigger stations placed first. A
 * bus or tram stop keeps its "H" at every zoom; a tap on it opens the stop with its lines.
 */
private fun stopLayer(): SymbolLayer =
    SymbolLayer(STOP_LAYER, STOP_SOURCE).apply {
        minZoom = CITY_ZOOM.toFloat()
        // Stops shown by name are drawn by namedSignLayer and stopNameLayer.
        setFilter(Expression.not(Expression.has("named")))
        setProperties(
            PropertyFactory.iconOpacity(fadeIn(CITY_ZOOM)),
            PropertyFactory.iconImage(Expression.get("sign")),
            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
            PropertyFactory.iconAllowOverlap(false),
            PropertyFactory.iconIgnorePlacement(false),
            PropertyFactory.iconPadding(1f),
            PropertyFactory.symbolSortKey(Expression.get("rank")),
        )
    }

/** The "H" of the bus and tram stops shown by name, until [STOP_NAME_ZOOM]; as the stop layer otherwise. */
private fun namedSignLayer(): SymbolLayer =
    SymbolLayer(STOP_NAMED_SIGN_LAYER, STOP_SOURCE).apply {
        minZoom = CITY_ZOOM.toFloat()
        maxZoom = STOP_NAME_ZOOM.toFloat()
        setFilter(Expression.has("named"))
        setProperties(
            PropertyFactory.iconOpacity(fadeIn(CITY_ZOOM)),
            PropertyFactory.iconImage(Expression.get("sign")),
            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
            PropertyFactory.iconAllowOverlap(false),
            PropertyFactory.iconIgnorePlacement(false),
            PropertyFactory.iconPadding(1f),
            PropertyFactory.symbolSortKey(Expression.get("rank")),
        )
    }

/**
 * From [STOP_NAME_ZOOM] on, the bus and tram stops shown by name: their name in the green of
 * the selected stop's name, white haloed, centred on the stop; names give way to each other.
 */
private fun stopNameLayer(font: Array<String>): SymbolLayer =
    SymbolLayer(STOP_NAME_LAYER, STOP_SOURCE).apply {
        minZoom = STOP_NAME_ZOOM.toFloat()
        setFilter(Expression.has("named"))
        setProperties(
            PropertyFactory.textField(Expression.get("name")),
            PropertyFactory.textFont(font),
            PropertyFactory.textSize(12f),
            PropertyFactory.textColor(STOP_NAME_GREEN),
            PropertyFactory.textHaloColor(android.graphics.Color.WHITE),
            PropertyFactory.textHaloWidth(1.8f),
            PropertyFactory.textAnchor(Property.TEXT_ANCHOR_CENTER),
            PropertyFactory.textMaxWidth(8f),
            PropertyFactory.textAllowOverlap(false),
            PropertyFactory.textIgnorePlacement(false),
            PropertyFactory.textPadding(2f),
            PropertyFactory.symbolSortKey(Expression.get("rank")),
        )
    }

/**
 * Puts [stop] as the selected stop into its layer of [style]: its sign with the ring and its
 * name; null clears it. The other stops leave it out (see setStopSigns). Call on the main thread.
 */
fun setSelectedStop(style: Style, stop: Stop?) {
    val source = style.getSourceAs<GeoJsonSource>(SELECTED_STOP_SOURCE) ?: return
    val latitude = stop?.latitude
    val longitude = stop?.longitude
    if (stop == null || latitude == null || longitude == null) {
        source.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()))
        setPillMarkedSign(emptyList())
        NoticeSignSpots.marked = null
        if (VehicleAnchors.selected != null) {
            VehicleAnchors.selected = null
            VehicleAnchors.version++
        }
        return
    }
    val feature = Feature.fromGeometry(Point.fromLngLat(longitude, latitude))
    feature.addStringProperty("stop", stop.id)
    val sign = signOf(stop)
    feature.addStringProperty("sign", SELECTED_STOP_IMAGE + sign)
    val anchor = if (stop.kind == PlaceKind.Stop) {
        GroupAnchor(stop.id, latitude, longitude, signHalfWidthDp(sign.length, ring = true), anchorProducts(stop.products))
    } else {
        null
    }
    if (anchor != VehicleAnchors.selected) {
        VehicleAnchors.selected = anchor
        VehicleAnchors.version++
    }
    feature.addStringProperty("name", stop.name.shortStopName())
    source.setGeoJson(FeatureCollection.fromFeatures(listOf(feature)))
    // Its sign with the ring, for a sign of disruptions on its corner (see MapNoticeLayer).
    NoticeSignSpots.marked = NoticeSignSpot(
        id = stop.id,
        name = stop.name,
        latitude = latitude,
        longitude = longitude,
        halfWidthDp = signHalfWidthDp(sign.length, ring = true),
        halfHeightDp = SIGN_HALF_HEIGHT_DP + SIGN_RING_GAP_DP + SIGN_RING_DP,
    )
    // Its sign and its name below it win over its own pills (see placePills).
    setPillMarkedSign(listOf(SignBox(latitude, longitude, signHalfWidthDp(sign.length, ring = true), SIGN_HALF_HEIGHT_DP + SIGN_RING_GAP_DP + SIGN_RING_DP)) + nameBox(latitude, longitude, stop.name.shortStopName()))
}

// Half the height of a stop sign in dp: its symbol and the padding around it.
private const val SIGN_HALF_HEIGHT_DP = SIGN_SYMBOL_DP / 2 + SIGN_PADDING_DP

/**
 * The name of the marked stop below its sign as a box (12 sp text from 1.9 em below the
 * middle of the sign, at most 9 em wide; rough widths per letter), for placePills.
 */
private fun nameBox(latitude: Double, longitude: Double, name: String): SignBox {
    val full = name.length * 6.6f
    val width = full.coerceAtMost(108f)
    val lines = kotlin.math.ceil(full / 108f).coerceIn(1f, 3f)
    val height = lines * 15f
    return SignBox(latitude, longitude, width / 2, height / 2, downDp = 22.8f + height / 2)
}

/**
 * One frame of the hop of the selected stop (see stopHop): its sign lifted and sized, its
 * name faded in and lifted with it, its positions popping in one after the other. Called for
 * every frame of the animation; call on the main thread.
 */
fun setSelectedStopAppear(style: Style, hop: StopHop) {
    val lift = arrayOf(0f, -hop.lift * STOP_HOP_DP)
    style.getLayer(SELECTED_STOP_LAYER)?.setProperties(
        PropertyFactory.iconSize(hop.size),
        PropertyFactory.iconTranslate(lift),
        PropertyFactory.textTranslate(lift),
        PropertyFactory.textOpacity(hop.nameAlpha),
    )
    setSelectedPositionsAppear(style, hop.positionsMillis)
}

/**
 * The layer of the selected stop: its sign with the green ring, and below it its name in the
 * green of the stop names, white haloed. Always drawn; the other stop signs give way to it.
 */
private fun selectedStopLayer(font: Array<String>): SymbolLayer =
    SymbolLayer(SELECTED_STOP_LAYER, SELECTED_STOP_SOURCE).apply {
        setProperties(
            PropertyFactory.iconImage(Expression.get("sign")),
            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(false),
            PropertyFactory.textField(Expression.get("name")),
            PropertyFactory.textFont(font),
            PropertyFactory.textSize(12f),
            PropertyFactory.textColor(STOP_NAME_GREEN),
            PropertyFactory.textHaloColor(android.graphics.Color.WHITE),
            PropertyFactory.textHaloWidth(1.8f),
            PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
            PropertyFactory.textOffset(arrayOf(0f, 1.9f)),
            PropertyFactory.textMaxWidth(9f),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textIgnorePlacement(false),
        )
    }

/** One symbol of a sign: a letter on a coloured ground, round or with small corners, or a dot. */
private class SignSymbol(val letter: String?, val ground: Int, val ink: Int, val round: Boolean)

/** The symbols of the sign [sign], as the app shows them in its lists (see PlaceSymbols). */
private fun signSymbols(sign: String, colors: StopSignColors): List<SignSymbol> = when (sign) {
    POI_SIGN -> listOf(SignSymbol(null, colors.poi, colors.ground, round = true))
    ADDRESS_SIGN -> listOf(SignSymbol(null, colors.address, colors.ground, round = true))
    BUS_SIGN -> listOf(SignSymbol("H", 0xFFFFD500.toInt(), 0xFF007A3D.toInt(), round = true))
    else -> sign.map { letter ->
        when (letter) {
            'S' -> SignSymbol("S", 0xFF008D4F.toInt(), android.graphics.Color.WHITE, round = true)
            'U' -> SignSymbol("U", 0xFF0664AB.toInt(), android.graphics.Color.WHITE, round = false)
            'R' -> SignSymbol("R", 0xFFDA251D.toInt(), android.graphics.Color.WHITE, round = false)
            else -> SignSymbol("F", 0xFF5A6072.toInt(), android.graphics.Color.WHITE, round = false)
        }
    }
}

/**
 * The sign of a stop as a picture: its symbols side by side on a rounded ground with a soft
 * shadow. With [ring], the sign of the selected stop: a white edge and a green ring around it.
 */
internal fun stopSignBitmap(sign: String, density: Float, colors: StopSignColors, ring: Boolean = false): Bitmap {
    val symbols = signSymbols(sign, colors)
    val symbol = SIGN_SYMBOL_DP * density
    val gap = SIGN_GAP_DP * density
    val padding = SIGN_PADDING_DP * density
    val shadow = SIGN_SHADOW_DP * density
    val width = 2 * padding + symbols.size * symbol + (symbols.size - 1) * gap
    val height = 2 * padding + symbol
    // Room around the sign for its shadow, and for the ring of the selected stop.
    val ringGap = SIGN_RING_GAP_DP * density
    val ringWidth = SIGN_RING_DP * density
    val margin = 2 * shadow + if (ring) ringGap + ringWidth else 0f
    val bitmap = Bitmap.createBitmap(
        (width + 2 * margin).roundToInt(),
        (height + 2 * margin).roundToInt(),
        Bitmap.Config.ARGB_8888,
    )
    val canvas = AndroidCanvas(bitmap)
    val corner = minOf(SIGN_CORNER_DP * density, height / 2)
    val ground = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = colors.ground
        setShadowLayer(shadow, 0f, shadow / 3, 0x40000000)
    }
    if (ring) {
        // A white edge between the sign and the ring, then the ring, both following its outline.
        val edge = ringGap + ringWidth / 2
        val outline = RectF(margin - edge, margin - edge, margin + width + edge, margin + height + edge)
        val halo = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            setShadowLayer(shadow, 0f, shadow / 3, 0x40000000)
        }
        canvas.drawRoundRect(outline, corner + edge, corner + edge, halo)
        val stroke = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = STOP_NAME_GREEN
            style = AndroidPaint.Style.STROKE
            strokeWidth = ringWidth
        }
        canvas.drawRoundRect(outline, corner + edge, corner + edge, stroke)
        ground.clearShadowLayer()
    }
    canvas.drawRoundRect(RectF(margin, margin, margin + width, margin + height), corner, corner, ground)
    val fill = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG)
    val text = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, 800, false)
        textSize = symbol * 0.6f
        textAlign = AndroidPaint.Align.CENTER
    }
    val top = margin + padding
    symbols.forEachIndexed { index, item ->
        val left = margin + padding + index * (symbol + gap)
        val centerX = left + symbol / 2
        val centerY = top + symbol / 2
        fill.color = item.ground
        if (item.round) {
            canvas.drawCircle(centerX, centerY, symbol / 2, fill)
        } else {
            val small = 5 * density
            canvas.drawRoundRect(RectF(left, top, left + symbol, top + symbol), small, small, fill)
        }
        val letter = item.letter
        if (letter != null) {
            text.color = item.ink
            canvas.drawText(letter, centerX, centerY - (text.descent() + text.ascent()) / 2, text)
        } else {
            // A dot: a small hole in the ground colour of the sign.
            fill.color = item.ink
            canvas.drawCircle(centerX, centerY, symbol / 6, fill)
        }
    }
    return bitmap
}
