package io.github.jnbp.nextstop.data

import kotlin.math.cos
import kotlin.math.sqrt

/*
 * Where the sign ("H") of a bus or tram stop stands on the map. The provider's point of a stop
 * is often not where the stop is: the VBB puts "Wolliner Str." (900007105) 36 to 50 m from all
 * four of its platforms, south-east of the crossing inside a block of houses. The positions of
 * the stop in OpenStreetMap (its platforms) say where it is. The same rule as
 * tools/stop_sign.py (keep the two alike):
 *
 * - the positions taken are those within SIGN_CLUSTER_METERS of the one nearest to the
 *   provider's point (one id can hold positions at both ends of a village);
 * - the provider's point stays where it lies within SIGN_KEEP_METERS of one of them and inside
 *   their bounding box grown by SIGN_BOX_MARGIN_METERS;
 * - else the sign goes to their centre, pulled to at most SIGN_PULL_METERS from the position
 *   nearest to it.
 *
 * So a sign never stands farther than the bounding box of the positions plus 20 m, nor (moved)
 * farther than 30 m from a position. Buildings are not known to the app; the centre of the
 * platforms of a stop lies on its street or crossing.
 */

private const val SIGN_CLUSTER_METERS = 250.0
private const val SIGN_KEEP_METERS = 30.0
private const val SIGN_BOX_MARGIN_METERS = 20.0
private const val SIGN_PULL_METERS = 30.0

/**
 * The point of the sign of a stop with the positions [positions] (latitude to longitude), whose
 * provider puts it at [provider] (null for a stop known from OpenStreetMap alone); [provider]
 * where there are no positions.
 */
fun stopSignPoint(provider: Pair<Double, Double>?, positions: List<Pair<Double, Double>>): Pair<Double, Double>? {
    if (positions.isEmpty()) return provider
    val anchor = provider ?: (positions.sumOf { it.first } / positions.size to positions.sumOf { it.second } / positions.size)
    val first = positions.minByOrNull { signMeters(anchor, it) } ?: return provider
    val cluster = positions.filter { signMeters(first, it) <= SIGN_CLUSTER_METERS }
    if (provider != null && cluster.minOf { signMeters(provider, it) } <= SIGN_KEEP_METERS && inGrownBox(provider, cluster)) {
        return provider
    }
    var centre = cluster.sumOf { it.first } / cluster.size to cluster.sumOf { it.second } / cluster.size
    val near = cluster.minByOrNull { signMeters(centre, it) } ?: return centre
    val apart = signMeters(centre, near)
    if (apart > SIGN_PULL_METERS) {
        val share = SIGN_PULL_METERS / apart
        centre = near.first + (centre.first - near.first) * share to near.second + (centre.second - near.second) * share
    }
    return centre
}

private fun inGrownBox(point: Pair<Double, Double>, items: List<Pair<Double, Double>>): Boolean {
    val dLat = SIGN_BOX_MARGIN_METERS / 111_320.0
    val dLon = SIGN_BOX_MARGIN_METERS / (111_320.0 * cos(Math.toRadians(point.first)).coerceAtLeast(0.01))
    return point.first >= items.minOf { it.first } - dLat && point.first <= items.maxOf { it.first } + dLat &&
        point.second >= items.minOf { it.second } - dLon && point.second <= items.maxOf { it.second } + dLon
}

private fun signMeters(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
    val north = (b.first - a.first) * 111_320.0
    val east = (b.second - a.second) * 111_320.0 * cos(Math.toRadians((a.first + b.first) / 2))
    return sqrt(north * north + east * east)
}

// A stop the provider serves with trains, S-Bahn or U-Bahn is a station: its point stays (the
// bus stops around a station spread over its square and streets).
private val STATION_PRODUCTS = setOf("suburban", "subway", "regional", "express")

/**
 * [stop] with its sign where its bus and tram positions in OpenStreetMap are (see
 * [stopSignPoint]); unchanged for stations, other places, stops OpenStreetMap does not know and
 * before the data is loaded.
 */
fun OsmStations.signPlace(stop: Stop): Stop {
    if (stop.kind != PlaceKind.Stop || stop.products.any { it in STATION_PRODUCTS }) return stop
    val at = stop.position ?: return stop
    val positions = areasOf(stop.id, stop.name, at).flatMap { area -> area.positions.map { it.position } }
    val sign = stopSignPoint(at, positions) ?: return stop
    return if (sign == at) stop else stop.copy(latitude = sign.first, longitude = sign.second)
}
