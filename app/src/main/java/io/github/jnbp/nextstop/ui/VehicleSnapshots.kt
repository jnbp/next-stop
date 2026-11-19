package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.Vehicle
import kotlin.math.cos

/**
 * The last vehicles of a few parts of the map, in memory only (#31): opening a vehicle's
 * course and coming back, or leaving the app for a moment, shows the vehicles of the place at
 * once instead of an empty map that fills up again; fresh ones come in the background and
 * glide over them. Each snapshot is keyed by where the camera looked ([latitude],
 * [longitude]) and the detail of the map ([tier]); a look within [reachMeters] of a kept one
 * at the same tier finds it. At most [capacity] snapshots are kept, the one used least
 * recently goes first; one older than [maxAgeMillis] is never handed out. Nothing is written
 * to disk; [clear] lets go of all (memory runs short, see NextStopApp.onTrimMemory).
 *
 * Across zoom levels (#74) the snapshots are used together: [within] hands out what all of
 * them know inside a part of the map, so zooming out after zooming in shows at once the
 * vehicles seen before further out, and zooming in keeps those of the closer view (see
 * [vehiclesForView]); a fresh answer then replaces them as before.
 *
 * Size: a snapshot holds the same objects as the map showed (no copies). The radar sends at
 * most a few hundred vehicles with a short path each, some tens to a few hundred KB, so even
 * three of them are small next to the map's own tiles.
 */
class VehicleSnapshots<T>(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS,
    private val reachMeters: Double = DEFAULT_REACH_METERS,
) {
    /** One kept look: the vehicles and the uptime they were loaded at ([since]). */
    class Snapshot<T>(
        val latitude: Double,
        val longitude: Double,
        val tier: Int,
        val items: List<T>,
        val since: Long,
    )

    // Most recently used last.
    private val entries = ArrayList<Snapshot<T>>(capacity + 1)

    /** How many snapshots are kept now (expired ones included until the next [put] or [find]). */
    val size: Int @Synchronized get() = entries.size

    /** Keeps [items] for the look at [latitude], [longitude] and [tier]; an empty list keeps nothing. */
    @Synchronized
    fun put(latitude: Double, longitude: Double, tier: Int, items: List<T>, since: Long, now: Long) {
        dropExpired(now)
        if (items.isEmpty()) return
        val same = entries.indexOfFirst { it.tier == tier && near(it, latitude, longitude) }
        if (same >= 0) entries.removeAt(same)
        entries.add(Snapshot(latitude, longitude, tier, items, since))
        while (entries.size > capacity) entries.removeAt(0)
    }

    /**
     * The snapshot of the look at [latitude], [longitude] and [tier] (the nearest one in reach),
     * when it is not older than the limit at [now]; it counts as used then. Null otherwise.
     */
    @Synchronized
    fun find(latitude: Double, longitude: Double, tier: Int, now: Long): Snapshot<T>? {
        dropExpired(now)
        var best = -1
        var bestDistance = Double.MAX_VALUE
        for (i in entries.indices) {
            val entry = entries[i]
            if (entry.tier != tier) continue
            val distance = metersBetween(entry.latitude, entry.longitude, latitude, longitude)
            if (distance <= reachMeters && distance < bestDistance) {
                best = i
                bestDistance = distance
            }
        }
        if (best < 0) return null
        val hit = entries.removeAt(best)
        entries.add(hit)
        return hit
    }

    /**
     * Everything the snapshots not older than the limit at [now] hold inside the box [north],
     * [south], [east], [west] (by [positionOf]), each item once by [keyOf]: the one of the newest
     * snapshot, with the uptime it was loaded at. Newest snapshots first. Nothing counts as used.
     */
    @Synchronized
    fun within(
        north: Double,
        south: Double,
        east: Double,
        west: Double,
        now: Long,
        positionOf: (T) -> Pair<Double, Double>?,
        keyOf: (T) -> Any,
    ): List<Pair<T, Long>> {
        dropExpired(now)
        if (entries.isEmpty()) return emptyList()
        val taken = HashSet<Any>()
        val found = ArrayList<Pair<T, Long>>()
        for (entry in entries.sortedByDescending { it.since }) {
            for (item in entry.items) {
                val (latitude, longitude) = positionOf(item) ?: continue
                if (latitude < south || latitude > north || longitude < west || longitude > east) continue
                if (taken.add(keyOf(item))) found.add(item to entry.since)
            }
        }
        return found
    }

    /** Lets go of every snapshot. */
    @Synchronized
    fun clear() {
        entries.clear()
    }

    private fun dropExpired(now: Long) {
        entries.removeAll { now - it.since > maxAgeMillis }
    }

    private fun near(entry: Snapshot<T>, latitude: Double, longitude: Double): Boolean =
        metersBetween(entry.latitude, entry.longitude, latitude, longitude) <= reachMeters

    companion object {
        // A few places and the tiers of each: zooming out and in again finds every level.
        const val DEFAULT_CAPACITY = 6
        const val DEFAULT_MAX_AGE_MILLIS = 2 * 60_000L
        const val DEFAULT_REACH_METERS = 1_500.0

        /** A plain distance in metres, good enough for a few kilometres. */
        internal fun metersBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val north = (lat2 - lat1) * METERS_PER_DEGREE
            val east = (lon2 - lon1) * METERS_PER_DEGREE * cos(Math.toRadians((lat1 + lat2) / 2))
            return kotlin.math.sqrt(north * north + east * east)
        }

        private const val METERS_PER_DEGREE = 111_320.0
    }
}

/** The vehicles of the map, kept for the process (see [VehicleSnapshots]). */
object MapVehicleCache {
    val snapshots = VehicleSnapshots<Vehicle>()

    /** Memory runs short: everything goes. */
    fun clear() = snapshots.clear()
}

/** Keeps the [vehicles] the map shows at [center] and [tier] (see [MapVehicleCache]). */
internal fun keepMapVehicles(center: Pair<Double, Double>?, tier: MapTier, vehicles: List<Vehicle>, since: Long) {
    if (center == null || vehicles.isEmpty()) return
    MapVehicleCache.snapshots.put(center.first, center.second, tier.ordinal, vehicles, since, android.os.SystemClock.uptimeMillis())
}

/** The recent vehicles kept for the box [north], [south], [east], [west] at any tier, with the uptime each was loaded at. */
internal fun keptMapVehiclesWithin(north: Double, south: Double, east: Double, west: Double): List<Pair<Vehicle, Long>> =
    MapVehicleCache.snapshots.within(north, south, east, west, android.os.SystemClock.uptimeMillis(), { it.path.lastOrNull() }, { it.tripId })

/**
 * The vehicles for a view of the box [north], [south], [east], [west] at once, before any
 * answer (#74): those of [shown] inside it (zoomed in, the rest is not needed and stays in the
 * snapshots) and the [cached] ones inside it that are not shown yet (zoomed out again, or
 * moved back). [seenAt] (when each was last seen, by trip id) learns when the added ones were
 * loaded, so a narrower request after it keeps them as it keeps the shown ones. [shown] itself
 * when nothing changes, so nothing is drawn again for it.
 */
internal fun vehiclesForView(
    shown: List<Vehicle>,
    cached: List<Pair<Vehicle, Long>>,
    seenAt: MutableMap<String, Long>,
    north: Double,
    south: Double,
    east: Double,
    west: Double,
): List<Vehicle> {
    fun inside(vehicle: Vehicle): Boolean {
        val (latitude, longitude) = vehicle.path.lastOrNull() ?: return false
        return latitude in south..north && longitude in west..east
    }
    val kept = shown.filter { inside(it) }
    val ids = kept.mapTo(HashSet()) { it.tripId }
    var added: ArrayList<Vehicle>? = null
    for ((vehicle, since) in cached) {
        if (!inside(vehicle) || !ids.add(vehicle.tripId)) continue
        if (added == null) added = ArrayList()
        added.add(vehicle)
        val before = seenAt[vehicle.tripId]
        if (before == null || before < since) seenAt[vehicle.tripId] = since
    }
    if (added == null && kept.size == shown.size) return shown
    return if (added == null) kept else kept + added
}
