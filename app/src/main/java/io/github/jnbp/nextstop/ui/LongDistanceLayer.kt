package io.github.jnbp.nextstop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import io.github.jnbp.nextstop.data.DiagLog
import io.github.jnbp.nextstop.data.LONG_DISTANCE_MAIN
import io.github.jnbp.nextstop.data.LongDistanceNetwork
import io.github.jnbp.nextstop.data.NetworkLine
import io.github.jnbp.nextstop.data.TransitousSource
import io.github.jnbp.nextstop.data.longDistanceKeyRank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/*
 * The long-distance railways on the map far out (see LongDistanceNetwork): calm grey lines of
 * the ICE, IC and EC trains where the tiles have no railways yet. At the zoom of the whole
 * country only the main axes (high-speed trains), from that of a state the other long-distance
 * lines fade in, and as the railways of the tiles come in (from NETWORK_MIN_ZOOM, see
 * MapLayers) these fade out. One source, one line layer, its data set only when it changes.
 */

internal const val LONG_DISTANCE_LAYER = "ns-long-distance"
private const val LONG_DISTANCE_SOURCE = "ns-long-distance"

// The zooms of the layer: in from the whole country, the other lines from about a state, out
// while the tile railways come in (they show from zoom 8, whole at 8.6).
private const val LONG_DISTANCE_MIN_ZOOM = 4.0
private const val OTHER_LINES_FROM = 6.0
private const val LONG_DISTANCE_OUT = 8.2
private const val LONG_DISTANCE_GONE = 8.9

// A calm grey, a little darker than the tile railways it hands over to.
private val LONG_DISTANCE_COLOR = Color(0xFF7D8590)

// How long after the start the network is read: after the S-Bahn, U-Bahn and tram networks.
private const val LONG_DISTANCE_DELAY_MILLIS = 9_000L

private const val LOG_TAG = "LongDistance"

// The network as last set, so a new style gets it at once.
private var longDistanceData: FeatureCollection? = null

/** An opacity by the zoom for the main axes ([main]) and the other lines at a stop of the zoom. */
private fun byRank(main: Float, other: Float): Expression =
    Expression.switchCase(Expression.eq(Expression.get("rank"), LONG_DISTANCE_MAIN), Expression.literal(main), Expression.literal(other))

/** Adds the source and layer of the long-distance network to [style], below the layer [below] (the first labels). */
fun installLongDistanceLayer(style: Style, below: String?) {
    if (style.getSource(LONG_DISTANCE_SOURCE) == null) {
        style.addSource(GeoJsonSource(LONG_DISTANCE_SOURCE, mapSafe(longDistanceData ?: FeatureCollection.fromFeatures(emptyList<Feature>()))))
    }
    if (style.getLayer(LONG_DISTANCE_LAYER) != null) return
    val layer = LineLayer(LONG_DISTANCE_LAYER, LONG_DISTANCE_SOURCE).apply {
        minZoom = LONG_DISTANCE_MIN_ZOOM.toFloat()
        maxZoom = LONG_DISTANCE_GONE.toFloat()
        setProperties(
            PropertyFactory.lineColor(LONG_DISTANCE_COLOR.toArgb()),
            PropertyFactory.lineWidth(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.zoom(),
                    Expression.stop(LONG_DISTANCE_MIN_ZOOM, 0.7f),
                    Expression.stop(OTHER_LINES_FROM, 1.1f),
                    Expression.stop(LONG_DISTANCE_GONE, 1.6f),
                ),
            ),
            // Fades in and out by the zoom, the other lines only from about a state on.
            PropertyFactory.lineOpacity(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.zoom(),
                    Expression.stop(LONG_DISTANCE_MIN_ZOOM, 0f),
                    Expression.stop(LONG_DISTANCE_MIN_ZOOM + 0.6, byRank(0.7f, 0f)),
                    Expression.stop(OTHER_LINES_FROM, byRank(0.7f, 0f)),
                    Expression.stop(OTHER_LINES_FROM + 0.6, byRank(0.75f, 0.55f)),
                    Expression.stop(LONG_DISTANCE_OUT, byRank(0.75f, 0.55f)),
                    Expression.stop(LONG_DISTANCE_GONE, 0f),
                ),
            ),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
    }
    if (below != null && style.getLayer(below) != null) style.addLayerBelow(layer, below) else style.addLayer(layer)
}

/** Puts the long-distance network ([features] of [longDistanceFeatures]) into [style]. Call on the main thread. */
fun setLongDistanceNetwork(style: Style, features: FeatureCollection) {
    longDistanceData = features
    style.getSourceAs<GeoJsonSource>(LONG_DISTANCE_SOURCE)?.setGeoJsonSafely(features)
}

/** The [lines] of the long-distance network as map features with their rank. May run off the main thread. */
fun longDistanceFeatures(lines: List<NetworkLine>): FeatureCollection = FeatureCollection.fromFeatures(
    lines.map { line ->
        Feature.fromGeometry(
            LineString.fromLngLats(line.points.map { (latitude, longitude) -> Point.fromLngLat(longitude, latitude) }),
        ).apply { addNumberProperty("rank", longDistanceKeyRank(line.line)) }
    },
)

/**
 * Loads the long-distance network (see LongDistanceNetwork) once the map rests a while after
 * the start, keeps it up to date while the app runs and puts it into the map's style whenever
 * it or the style changes ([styleTick]), while the map rests. While the battery saver is on no
 * new sample is asked for; the kept network still shows.
 */
@Composable
fun LongDistanceNetworkLayer(map: MapLibreMap?, styleTick: Int) {
    val context = LocalContext.current
    var features by remember { mutableStateOf<FeatureCollection?>(null) }
    LaunchedEffect(Unit) {
        MapQuiet.awaitRest(LONG_DISTANCE_DELAY_MILLIS)
        val app = context.applicationContext
        LongDistanceNetwork.load(app, TransitousSource(TransitousSource.userAgent(app)), sampleAllowed = { !BatterySaver.active }) { lines ->
            launch {
                features = withContext(Dispatchers.Default) { longDistanceFeatures(lines) }
                DiagLog.i(LOG_TAG) { "network: ${lines.size} ways" }
            }
        }
    }
    LaunchedEffect(map, styleTick, features) {
        val network = features ?: return@LaunchedEffect
        // A new source is cut into tiles anew: while the map rests, not during a gesture.
        MapQuiet.awaitRest(0L)
        map?.style?.takeIf { it.isFullyLoaded }?.let { setLongDistanceNetwork(it, network) }
    }
}
