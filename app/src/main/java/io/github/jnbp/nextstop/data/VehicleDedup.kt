package io.github.jnbp.nextstop.data

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sqrt

// Two vehicles of one line closer than this, heading for the same next stop at about the same
// time, are one vehicle reported twice.
const val SAME_VEHICLE_METERS = 120.0

// How far apart their times at that next stop may be, in seconds.
const val SAME_VEHICLE_SECONDS = 90L

// Without a next stop on either side only vehicles this close, in the same direction, count as one.
const val SAME_VEHICLE_BLIND_METERS = 40.0

/**
 * The vehicles with every physical vehicle once. The radar of the provider reports some runs
 * twice under two trip ids, the ring lines (S41, S42) in particular: one run hands over to the
 * next while the train is still on its way, and both runs then put a vehicle on the same spot,
 * driving along together. Two vehicles are taken as one when they have the same line (and
 * product), stand within [SAME_VEHICLE_METERS] of each other and head for the same next stop
 * within [SAME_VEHICLE_SECONDS]; where a next stop is missing, the same direction and
 * [SAME_VEHICLE_BLIND_METERS] decide. Of two, the one with live data is kept, then the one
 * shown before ([shownBefore], trip ids: so the badge on the map does not jump between runs),
 * then the one with the longer way ahead, then the earlier one in the list. The order of the
 * list stays as it was.
 */
fun distinctVehicles(vehicles: List<Vehicle>, shownBefore: Set<String> = emptySet()): List<Vehicle> {
    if (vehicles.size < 2) return vehicles
    val kept = ArrayList<Vehicle>(vehicles.size)
    // The index in [kept] of each vehicle kept so far, by line, to compare only within a line.
    val byLine = HashMap<String, MutableList<Int>>()
    for (vehicle in vehicles) {
        val key = vehicle.product + "|" + vehicle.line.trim().uppercase()
        val sameLine = byLine.getOrPut(key) { ArrayList(2) }
        val twin = sameLine.firstOrNull { sameVehicle(kept[it], vehicle) }
        when {
            twin == null -> {
                sameLine.add(kept.size)
                kept.add(vehicle)
            }
            better(vehicle, kept[twin], shownBefore) -> kept[twin] = vehicle
        }
    }
    return if (kept.size == vehicles.size) vehicles else kept
}

/** Whether [a] and [b] (of the same line) are the same vehicle reported twice (see [distinctVehicles]). */
fun sameVehicle(a: Vehicle, b: Vehicle): Boolean {
    if (a.tripId == b.tripId) return true
    val here = a.path.firstOrNull() ?: return false
    val there = b.path.firstOrNull() ?: return false
    val meters = metersApart(here, there)
    val nextA = a.nextStop?.trim()?.lowercase()
    val nextB = b.nextStop?.trim()?.lowercase()
    if (nextA.isNullOrEmpty() || nextB.isNullOrEmpty()) {
        return meters <= SAME_VEHICLE_BLIND_METERS && a.direction.trim().equals(b.direction.trim(), ignoreCase = true)
    }
    if (meters > SAME_VEHICLE_METERS || nextA != nextB) return false
    val timeA = a.nextStopTime
    val timeB = b.nextStopTime
    if (timeA == null || timeB == null) return a.direction.trim().equals(b.direction.trim(), ignoreCase = true)
    val seconds = kotlin.math.abs(timeA.toEpochSecond() - timeB.toEpochSecond())
    return seconds <= SAME_VEHICLE_SECONDS
}

// Whether [candidate] should stand for the vehicle rather than [current] (see distinctVehicles).
private fun better(candidate: Vehicle, current: Vehicle, shownBefore: Set<String>): Boolean {
    if (candidate.realtime != current.realtime) return candidate.realtime
    val candidateShown = candidate.tripId in shownBefore
    val currentShown = current.tripId in shownBefore
    if (candidateShown != currentShown) return candidateShown
    return candidate.path.size > current.path.size
}

private fun metersApart(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
    val radius = 6_371_000.0
    val toRadians = Math.PI / 180.0
    val dLat = (b.first - a.first) * toRadians
    val dLon = (b.second - a.second) * toRadians
    val h = kotlin.math.sin(dLat / 2).let { it * it } +
        cos(a.first * toRadians) * cos(b.first * toRadians) * kotlin.math.sin(dLon / 2).let { it * it }
    return 2 * radius * asin(sqrt(h.coerceIn(0.0, 1.0)))
}
