package io.github.jnbp.nextstop.ui

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import io.github.jnbp.nextstop.data.DiagLog
import org.maplibre.android.camera.CameraUpdate
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Geometry
import org.maplibre.geojson.LineString
import org.maplibre.geojson.MultiLineString
import org.maplibre.geojson.MultiPoint
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

/*
 * The boundary to MapLibre (#284): features and cameras pass the checks of MapGuards.kt before
 * the map gets them. A feature with a point that is no place on the earth, a line of fewer than
 * two points or a ring of fewer than four is left out; a number property that is not finite
 * (an offset, an opacity, a bearing) becomes 0; a camera with such a value is not moved to.
 * What is left out is logged once per kind, so a bad source can be found.
 */

private const val LOG_TAG = "MapGuard"

// What was logged already: one line per kind and place in the process.
private val loggedGuards = HashSet<String>()

private fun noteGuard(what: String) {
    val first = synchronized(loggedGuards) { loggedGuards.add(what) }
    if (first) DiagLog.w(LOG_TAG) { "left out: $what" }
}

private fun pointOk(point: Point): Boolean = mapPointOk(point.latitude(), point.longitude())

/**
 * [geometry] as the map may draw it: points that are no place on the earth dropped, and of a
 * line a point repeated right after itself (see mapLine); null where nothing drawable is left.
 * Kinds not checked here pass as they are.
 */
private fun safeGeometry(geometry: Geometry?): Geometry? = when (geometry) {
    null -> null
    is Point -> geometry.takeIf { pointOk(it) }
    is LineString -> {
        val points = geometry.coordinates()
        safeLine(points)?.let { if (it === points) geometry else LineString.fromLngLats(it) }
    }
    is MultiPoint -> {
        val points = geometry.coordinates()
        points.filter { pointOk(it) }.takeIf { it.isNotEmpty() }?.let { if (it.size == points.size) geometry else MultiPoint.fromLngLats(it) }
    }
    is MultiLineString -> {
        val all = geometry.coordinates()
        val lines = all.mapNotNull { safeLine(it) }
        when {
            lines.isEmpty() -> null
            lines.size == all.size && lines.indices.all { lines[it] === all[it] } -> geometry
            else -> MultiLineString.fromLngLats(lines)
        }
    }
    is Polygon -> {
        val rings = geometry.coordinates()
        // A ring needs four points (the first again at its end); an outer ring that is none is no area.
        if (rings.isEmpty() || rings.first().size < 4 || rings.any { ring -> ring.any { !pointOk(it) } }) null else geometry
    }
    else -> geometry
}

// The points of a line the map may draw (see mapLine): [points] itself where all are fine; null for no line.
private fun safeLine(points: List<Point>): List<Point>? {
    if (points.size >= 2 && points.all { pointOk(it) } && (1 until points.size).all { !samePoint(points[it - 1], points[it]) }) return points
    val kept = ArrayList<Point>(points.size)
    for (point in points) {
        if (!pointOk(point)) continue
        if (kept.isNotEmpty() && samePoint(kept.last(), point)) continue
        kept.add(point)
    }
    return kept.takeIf { it.size >= 2 }
}

private fun samePoint(a: Point, b: Point): Boolean = a.latitude() == b.latitude() && a.longitude() == b.longitude()

// [element] with every number that is not finite replaced by 0; [element] itself where all are.
private fun finiteJson(element: JsonElement): JsonElement = when {
    element is JsonPrimitive && element.isNumber && !element.asDouble.isFinite() -> JsonPrimitive(0)
    element is JsonArray && element.any { it is JsonPrimitive && it.isNumber && !it.asDouble.isFinite() } ->
        JsonArray(element.size()).also { array -> element.forEach { array.add(finiteJson(it)) } }
    else -> element
}

// True when [properties] hold a number (or a number in an array) that is not finite; then fixed in place.
private fun fixedProperties(properties: JsonObject?): Boolean {
    if (properties == null) return false
    var fixed = false
    for (entry in properties.entrySet()) {
        val value = entry.value
        val safe = finiteJson(value)
        if (safe !== value) {
            entry.setValue(safe)
            fixed = true
        }
    }
    return fixed
}

/**
 * [features] as the map may be given them (#284): a feature without a drawable geometry left
 * out, a line cleaned of points that are no place on the earth, numbers that are not finite
 * among the properties set to 0 (with [properties]; off for big sets of plain points). The
 * same collection where nothing had to change, so the common case costs no copy.
 */
fun mapSafe(features: FeatureCollection, properties: Boolean = true): FeatureCollection {
    val list = features.features() ?: return features
    // Made only once a feature had to change, so the common case allocates nothing.
    var kept: ArrayList<Feature>? = null
    for (index in list.indices) {
        val feature = list[index]
        val geometry = feature.geometry()
        val safe = safeGeometry(geometry)
        if (safe == null) {
            noteGuard("feature with ${geometry?.type() ?: "no"} geometry")
            if (kept == null) kept = ArrayList<Feature>(list.size).apply { addAll(list.subList(0, index)) }
            continue
        }
        if (properties && fixedProperties(feature.properties())) noteGuard("number property that is not finite")
        if (safe === geometry) {
            kept?.add(feature)
        } else {
            noteGuard("points of a ${geometry?.type()}")
            if (kept == null) kept = ArrayList<Feature>(list.size).apply { addAll(list.subList(0, index)) }
            kept.add(Feature.fromGeometry(safe, feature.properties(), feature.id()))
        }
    }
    return if (kept != null) FeatureCollection.fromFeatures(kept) else features
}

/**
 * True when [update] leads the camera of this map to values MapLibre may take (see cameraOk):
 * a target on the earth, a finite zoom, bearing and tilt, a padding of finite pixels. False
 * where it leads elsewhere or cannot be worked out; then the camera stays where it is.
 */
fun MapLibreMap.cameraUpdateOk(update: CameraUpdate): Boolean {
    val position = try {
        update.getCameraPosition(this)
    } catch (e: Exception) {
        null
    } ?: return false.also { noteGuard("camera that could not be worked out") }
    val target = position.target
    val ok = cameraOk(
        latitude = target?.latitude ?: Double.NaN,
        longitude = target?.longitude ?: Double.NaN,
        zoom = position.zoom,
        bearing = position.bearing,
        tilt = position.tilt,
        padding = position.padding,
    )
    if (!ok) noteGuard("camera with a value that is no number or out of range")
    return ok
}

/** moveCamera, only to a camera MapLibre may take (see cameraUpdateOk). */
fun MapLibreMap.moveCameraSafely(update: CameraUpdate, callback: MapLibreMap.CancelableCallback? = null) {
    if (!cameraUpdateOk(update)) return
    if (callback == null) moveCamera(update) else moveCamera(update, callback)
}

/** setGeoJson with [features] made safe for the map first (see mapSafe). */
fun GeoJsonSource.setGeoJsonSafely(features: FeatureCollection, properties: Boolean = true) {
    setGeoJson(mapSafe(features, properties))
}
