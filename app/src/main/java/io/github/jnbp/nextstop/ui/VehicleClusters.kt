package io.github.jnbp.nextstop.ui

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor

/*
 * Vehicles on their way that happen to be at the same place at the same time (two buses of
 * different lines between two stops, a bus bunching behind another) would hide each other,
 * as badges allow overlap (see BADGE_COLLISION). Instead, wherever their badges would overlap
 * on the screen at the current zoom, they line up in a column beside a small dot at their
 * common middle, just as vehicles at a station line up beside its sign (see layoutGroup).
 * Vehicles at a station are grouped there and are not looked at here. Per frame, cheap: the
 * vehicles are put into cells about a badge wide, and only neighbours are compared. Pure
 * logic, no Android.
 */

/** A vehicle on its way, not at a station: its [key] (the trip), where it is now and its badge text. */
data class FreeVehicle(val key: String, val latitude: Double, val longitude: Double, val label: String)

/** Vehicles whose badges would overlap: their [keys] (sorted), and their common middle. [id] is stable while its first key stays. */
data class VehicleCluster(val id: String, val keys: List<String>, val latitude: Double, val longitude: Double)

/** The radius of the dot at the middle of a cluster, in dp; the column starts beside it as beside a sign. */
const val CLUSTER_DOT_RADIUS_DP = 3f

/** Badges closer than they are wide or high plus this gap, in dp, count as overlapping. */
internal const val CLUSTER_GAP_DP = 2f

/**
 * Vehicles that were together in the last frame stay together until their badges are this
 * much further apart, in dp: so a pair that just touches does not flicker between column and
 * own place from frame to frame.
 */
internal const val CLUSTER_KEEP_DP = 10f

/** Clusters only form from this zoom on, where the badges are drawn; below it the vehicles are dots in their own place. */
const val CLUSTER_MIN_ZOOM = NEIGHBOURHOOD_ZOOM - 0.4

/** The id of a cluster: from its first key, so it holds while that vehicle stays in it. */
fun clusterId(firstKey: String): String = "cluster:$firstKey"

private const val CLUSTER_METERS_PER_DEGREE = 111_320.0

/**
 * Whether two badges of half widths [halfWidthA] and [halfWidthB] whose middles lie [dx] and
 * [dy] dp apart on the screen would overlap, counting [CLUSTER_GAP_DP] and an extra [slack].
 */
fun badgesOverlap(dx: Double, dy: Double, halfWidthA: Float, halfWidthB: Float, slack: Float = 0f): Boolean =
    abs(dx) < halfWidthA + halfWidthB + CLUSTER_GAP_DP + slack && abs(dy) < BADGE_HEIGHT_DP + CLUSTER_GAP_DP + slack

/**
 * The clusters of [vehicles] at [zoom]: vehicles whose badges would overlap, and those
 * overlapping one of them in turn. [previous] holds the cluster id of each vehicle in the last
 * frame; two that shared one stay together up to [CLUSTER_KEEP_DP] further apart. Only
 * clusters of two or more vehicles are returned. Below [CLUSTER_MIN_ZOOM] there are none.
 */
fun findClusters(vehicles: List<FreeVehicle>, zoom: Double, previous: Map<String, String> = emptyMap()): List<VehicleCluster> {
    if (vehicles.size < 2 || zoom < CLUSTER_MIN_ZOOM) return emptyList()
    val reference = vehicles[0].latitude
    val perDp = metersPerDp(zoom, reference)
    val eastScale = CLUSTER_METERS_PER_DEGREE * cos(Math.toRadians(reference)) / perDp
    val northScale = CLUSTER_METERS_PER_DEGREE / perDp
    val n = vehicles.size
    val x = DoubleArray(n)
    val y = DoubleArray(n)
    val half = FloatArray(n)
    var widest = 0f
    for (i in 0 until n) {
        val vehicle = vehicles[i]
        x[i] = vehicle.longitude * eastScale
        y[i] = -vehicle.latitude * northScale
        half[i] = badgeHalfWidthDp(vehicle.label)
        if (half[i] > widest) widest = half[i]
    }
    // Cells as wide as the furthest two badges can be apart and still count: neighbours suffice.
    val cell = (2 * widest + CLUSTER_GAP_DP + CLUSTER_KEEP_DP).toDouble().coerceAtLeast(BADGE_HEIGHT_DP + CLUSTER_GAP_DP + CLUSTER_KEEP_DP)
    val cells = HashMap<Long, MutableList<Int>>()
    val cellX = LongArray(n)
    val cellY = LongArray(n)
    for (i in 0 until n) {
        cellX[i] = floor(x[i] / cell).toLong()
        cellY[i] = floor(y[i] / cell).toLong()
        cells.getOrPut(cellKey(cellX[i], cellY[i])) { ArrayList(2) }.add(i)
    }
    val parent = IntArray(n) { it }
    fun root(i: Int): Int {
        var r = i
        while (parent[r] != r) r = parent[r]
        var j = i
        while (parent[j] != r) {
            val next = parent[j]
            parent[j] = r
            j = next
        }
        return r
    }
    for (i in 0 until n) {
        val before = previous[vehicles[i].key]
        for (cx in cellX[i] - 1..cellX[i] + 1) {
            for (cy in cellY[i] - 1..cellY[i] + 1) {
                val others = cells[cellKey(cx, cy)] ?: continue
                for (j in others) {
                    if (j <= i) continue
                    val together = before != null && before == previous[vehicles[j].key]
                    val slack = if (together) CLUSTER_KEEP_DP else 0f
                    if (badgesOverlap(x[j] - x[i], y[j] - y[i], half[i], half[j], slack)) {
                        val a = root(i)
                        val b = root(j)
                        if (a != b) parent[b] = a
                    }
                }
            }
        }
    }
    val members = HashMap<Int, MutableList<Int>>()
    for (i in 0 until n) members.getOrPut(root(i)) { ArrayList(2) }.add(i)
    val clusters = ArrayList<VehicleCluster>()
    for (group in members.values) {
        if (group.size < 2) continue
        val keys = group.map { vehicles[it].key }.sorted()
        val latitude = group.sumOf { vehicles[it].latitude } / group.size
        val longitude = group.sumOf { vehicles[it].longitude } / group.size
        clusters.add(VehicleCluster(clusterId(keys[0]), keys, latitude, longitude))
    }
    clusters.sortBy { it.id }
    return clusters
}

private fun cellKey(x: Long, y: Long): Long = (x shl 32) xor (y and 0xFFFFFFFFL)
