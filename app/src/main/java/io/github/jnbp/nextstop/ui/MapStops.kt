package io.github.jnbp.nextstop.ui

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt
import io.github.jnbp.nextstop.data.OSM_STOP_PREFIX
import io.github.jnbp.nextstop.data.OsmStations
import io.github.jnbp.nextstop.data.STREET_STOP_OWN_SIGN_METERS
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.osmNameKey
import org.maplibre.android.maps.MapLibreMap

/*
 * The stop signs of the whole screen. The provider lists only the few stops nearest to the map
 * centre (nearbyStops); the offline OpenStreetMap data (OsmStations.mapStops) knows every
 * station and stop area on the screen. The map draws both: the provider's stops as they are
 * (with their ids, for the departures), the OSM ones only where no provider stop stands for them.
 * A tapped OSM-only sign is resolved to the provider's stop by name and distance.
 */

/** True for a stop the map knows only from OpenStreetMap: its id is no provider's. */
fun isOsmOnlyStop(id: String?): Boolean = id?.startsWith(OSM_STOP_PREFIX) == true

// A provider's stop this close to an OSM stop of a fitting name is the same stop.
private const val SAME_STOP_METERS = 250.0

// A provider's stop farther than this from a tapped OSM stop is not taken for it.
private const val RESOLVE_METERS = 400.0

/**
 * The signs of the map: [provider] as they are, and of [osm] those that no stop of [provider]
 * nor [marked] (drawn in a layer of its own) stands for already.
 */
fun mergeMapStops(provider: List<Stop>, osm: List<Stop>, marked: Stop?): List<Stop> {
    if (osm.isEmpty()) return provider
    val known = if (marked != null) provider + marked else provider
    val keys = known.map { osmNameKey(it.name) }
    val out = ArrayList<Stop>(provider.size + osm.size)
    out.addAll(provider)
    for (stop in osm) {
        val at = stop.position ?: continue
        val key = osmNameKey(stop.name)
        val street = !isRailStop(stop)
        val taken = known.indices.any { index ->
            val other = known[index]
            val otherAt = other.position ?: return@any false
            val meters = metersApart(at, otherAt)
            val same = sameStopPlace(stop.id, other.id) || sameStopKey(key, keys[index])
            when {
                meters > SAME_STOP_METERS -> false
                meters <= 40.0 -> true
                // A bus or tram stop farther from the sign of a rail station is a stop of its own
                // in the street (see streetStopJoinsStation), even of the station's name or number
                // (the VBB gives the buses at many S-Bahn stations the station's number): its "H" stays.
                street && isRailStop(other) -> same && meters <= STREET_STOP_OWN_SIGN_METERS
                else -> same
            }
        }
        if (!taken) out.add(stop)
    }
    return out
}

/**
 * The station complex of OpenStreetMap that the rail stop [stop] belongs to, as a key for
 * stationGroups: for a provider's stop the station that lists its id (Berlin Hauptbahnhof lists
 * 900003201 and 900003200), for a station sign of the offline data that station itself (it
 * stands at the station's point). Null where none is known.
 */
fun osmStationComplex(stop: Stop): String? {
    val at = stop.position ?: return null
    if (isOsmOnlyStop(stop.id)) {
        return if (stop.id.startsWith(OSM_STOP_PREFIX + "s:")) "${at.first},${at.second}" else null
    }
    // By its ids only: a name alone would join stations of one name that are not one.
    val station = OsmStations.station(stop.id, null, at) ?: return null
    return "${station.latitude},${station.longitude}"
}

/**
 * The provider's stop of [nearby] that the OSM-only stop [osm] stands for: one of a fitting
 * name first, else the nearest one; none farther than RESOLVE_METERS.
 */
fun resolveOsmStop(osm: Stop, nearby: List<Stop>): Stop? {
    val at = osm.position ?: return null
    val key = osmNameKey(osm.name)
    return nearby
        .filter { !isOsmOnlyStop(it.id) }
        .mapNotNull { stop -> stop.position?.let { stop to metersApart(at, it) } }
        .filter { it.second <= RESOLVE_METERS }
        .minByOrNull { (stop, meters) -> meters + if (sameStopKey(key, osmNameKey(stop.name))) 0.0 else 1_000.0 }
        ?.first
}

/**
 * True when the OpenStreetMap stop [osmId] (its id from OsmStations.mapStops: "osm:a:<VBB number
 * or DHID>:<name key>", "osm:s:<id>") carries the stop place of the provider's stop [providerId]
 * (see stopPlaceKey).
 */
internal fun sameStopPlace(osmId: String, providerId: String): Boolean {
    if (!isOsmOnlyStop(osmId) || isOsmOnlyStop(providerId)) return false
    val rest = osmId.removePrefix(OSM_STOP_PREFIX)
    val id = when {
        rest.startsWith("a:") -> rest.removePrefix("a:").substringBeforeLast(':')
        rest.startsWith("s:") -> rest.removePrefix("s:")
        else -> return false
    }
    val place = stopPlaceKey(id) ?: return false
    return place == stopPlaceKey(providerId)
}

/** Name keys of one stop: equal, or one inside the other ("zoologischergarten" in "suzoologischergarten"). */
private fun sameStopKey(a: String, b: String): Boolean {
    if (a.isEmpty() || b.isEmpty()) return false
    if (a == b) return true
    val (short, long) = if (a.length <= b.length) a to b else b to a
    return short.length >= 5 && long.contains(short)
}

private fun metersApart(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
    val north = (b.first - a.first) * 111_320.0
    val east = (b.second - a.second) * 111_320.0 * cos((a.first + b.first) / 2 * PI / 180)
    return sqrt(north * north + east * east)
}

/**
 * The box the map [map] shows now as south, west, north, east (also when it is turned or
 * tilted: the box around the four corners), rounded to about 10 metres so a tiny move gives the
 * same box; null before the map has a size.
 */
fun visibleBox(map: MapLibreMap): List<Double>? {
    val region = map.projection.visibleRegion
    val corners = listOfNotNull(region.farLeft, region.farRight, region.nearLeft, region.nearRight)
    if (corners.size < 4) return null
    fun round(value: Double) = Math.round(value * 10_000.0) / 10_000.0
    val south = round(corners.minOf { it.latitude })
    val north = round(corners.maxOf { it.latitude })
    val west = round(corners.minOf { it.longitude })
    val east = round(corners.maxOf { it.longitude })
    if (north <= south || east <= west) return null
    return listOf(south, west, north, east)
}

/** [box] (south, west, north, east) grown by [share] of its size on every side, so signs at the edge are there before they scroll in. */
fun grownBox(box: List<Double>, share: Double = 0.25): List<Double> {
    val height = box[2] - box[0]
    val width = box[3] - box[1]
    return listOf(box[0] - height * share, box[1] - width * share, box[2] + height * share, box[3] + width * share)
}
