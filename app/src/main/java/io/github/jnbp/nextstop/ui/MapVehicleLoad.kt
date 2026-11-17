package io.github.jnbp.nextstop.ui

import android.graphics.PointF
import android.os.SystemClock
import io.github.jnbp.nextstop.data.TransitSource
import io.github.jnbp.nextstop.data.Vehicle
import io.github.jnbp.nextstop.data.distinctVehicles
import org.maplibre.android.maps.MapLibreMap

/*
 * Which live vehicles the map asks for, by how far it is zoomed out, and which of those it
 * already knows it keeps. Close up every vehicle of the screen; for a district the trains
 * of the screen and, in a second request, everything else, so trams and buses stay as dots
 * where they were seen close up; for the whole city the trains of a wider box. Vehicles
 * seen a moment ago that a narrower request leaves out stay a while (KNOWN_KEEP_MILLIS), so
 * zooming out does not make them vanish. Far out (a state, the country) nothing is asked
 * for and the last vehicles stay as they are, so zooming back in finds them still there.
 */

// Close up the map asks for vehicles in at most this many degrees of latitude ...
private const val CLOSE_VEHICLE_SPAN = 0.2

// ... and further out in at most this many, around the middle of the view (about 65 km).
private const val WIDE_VEHICLE_SPAN = 0.6

// Longitude spans this much more than latitude at Berlin's latitude, for a box about square.
private const val LONGITUDE_STRETCH = 1.6

// No more vehicles than this are asked for per request (see the limit of the radar).
private const val VEHICLES_PER_REQUEST = 150

// No more dots than this are drawn further out: trains first, then trams, then buses.
private const val MAX_DOT_VEHICLES = 300

// A vehicle a request leaves out (a narrower choice of products) stays this long after it was last seen.
private const val KNOWN_KEEP_MILLIS = 60_000L

// The products of the trains: shown as dots first.
private val TRAIN_PRODUCTS = setOf("suburban", "subway", "regional", "express")

/** When each vehicle the map shows was last seen in an answer, by trip id (see loadMapVehicles). */
internal class SeenVehicles {
    val at = HashMap<String, Long>()
}

/**
 * The vehicles for the view of [map] at [tier], from [source]; [before] are those shown now.
 * Null where nothing is asked for (zoomed out beyond the dots): the caller keeps [before].
 */
internal suspend fun loadMapVehicles(
    source: TransitSource,
    map: MapLibreMap,
    tier: MapTier,
    before: List<Vehicle>,
    seen: SeenVehicles,
): List<Vehicle>? {
    val camera = map.cameraPosition
    if (camera.zoom < VEHICLE_DOT_MIN_ZOOM) return null
    val first = map.projection.fromScreenLocation(PointF(0f, 0f))
    val second = map.projection.fromScreenLocation(PointF(map.width, map.height))
    // Sorted by hand, so a rotated map still gives a valid box.
    var north = maxOf(first.latitude, second.latitude)
    var south = minOf(first.latitude, second.latitude)
    var east = maxOf(first.longitude, second.longitude)
    var west = minOf(first.longitude, second.longitude)
    // A box larger than the span (zoomed out, or a tilted map reaching towards the horizon) is
    // cut to the span around the middle of the view instead of loading nothing.
    val span = if (tier == MapTier.Neighbourhood) CLOSE_VEHICLE_SPAN else WIDE_VEHICLE_SPAN
    if (north - south > span) {
        val middle = camera.target
        if (middle != null) {
            val half = span / 2
            north = middle.latitude + half
            south = middle.latitude - half
            east = minOf(east, middle.longitude + half * LONGITUDE_STRETCH)
            west = maxOf(west, middle.longitude - half * LONGITUDE_STRETCH)
        }
    }
    val shownBefore = before.mapTo(HashSet()) { it.tripId }
    // Close up: everything. For a district: the trains first (unfiltered, the limit fills
    // up with buses and trams of the large box first), then the rest. For the city: trains.
    val answer = when (tier) {
        MapTier.Neighbourhood -> source.vehicles(north, west, south, east, VEHICLES_PER_REQUEST, null)
        MapTier.District -> source.vehicles(north, west, south, east, VEHICLES_PER_REQUEST, TRAIN_PRODUCTS) +
            source.vehicles(north, west, south, east, VEHICLES_PER_REQUEST, null)
        MapTier.City -> source.vehicles(north, west, south, east, VEHICLES_PER_REQUEST, TRAIN_PRODUCTS)
    }
    // The radar reports some runs twice under two trip ids (the ring lines above all): each
    // vehicle once, the one shown so far where there is a choice.
    val fresh = distinctVehicles(answer.distinctBy { it.tripId }, shownBefore)
    val now = SystemClock.uptimeMillis()
    val merged = keepKnownVehicles(fresh, before, seen, now, north, south, east, west, keepOthers = tier != MapTier.Neighbourhood)
    return thinnedVehicles(merged, if (tier == MapTier.Neighbourhood) Int.MAX_VALUE else MAX_DOT_VEHICLES)
}

/**
 * [fresh] and, where [keepOthers], those of [before] not in it that were seen within
 * KNOWN_KEEP_MILLIS and still stand inside the box: a narrower request (trains only) does not
 * take away the trams and buses seen a moment ago. [seen] is brought up to date.
 */
internal fun keepKnownVehicles(
    fresh: List<Vehicle>,
    before: List<Vehicle>,
    seen: SeenVehicles,
    now: Long,
    north: Double,
    south: Double,
    east: Double,
    west: Double,
    keepOthers: Boolean,
): List<Vehicle> {
    for (vehicle in fresh) seen.at[vehicle.tripId] = now
    val result = ArrayList<Vehicle>(fresh.size + if (keepOthers) before.size else 0)
    result.addAll(fresh)
    if (keepOthers) {
        val freshIds = fresh.mapTo(HashSet()) { it.tripId }
        for (vehicle in before) {
            if (vehicle.tripId in freshIds) continue
            val seenAt = seen.at[vehicle.tripId] ?: continue
            if (now - seenAt > KNOWN_KEEP_MILLIS) continue
            val (latitude, longitude) = vehicle.path.lastOrNull() ?: continue
            if (latitude < south || latitude > north || longitude < west || longitude > east) continue
            result.add(vehicle)
        }
    }
    val kept = result.mapTo(HashSet()) { it.tripId }
    seen.at.keys.retainAll(kept)
    return result
}

/** At most [cap] of [vehicles]: trains first, then trams, then buses; the order within each kind kept. */
internal fun thinnedVehicles(vehicles: List<Vehicle>, cap: Int): List<Vehicle> {
    if (vehicles.size <= cap) return vehicles
    return vehicles.sortedBy { vehicleRank(it.product) }.take(cap)
}
