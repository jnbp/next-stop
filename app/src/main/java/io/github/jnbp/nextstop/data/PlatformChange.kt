package io.github.jnbp.nextstop.data

import kotlin.math.ceil
import kotlin.math.roundToInt

private typealias Spot = Pair<Double, Double>

/**
 * Changes between two rides of the U-Bahn, S-Bahn or trains that meet at one station complex
 * without a walk between them from the provider (BVG HAFAS at Karl-Bonhoeffer-Nervenklinik:
 * S25 to U8 under one station id): where they use different platforms, a walk of the app's
 * own is put between them, so the map draws it and the change says how long it takes; and
 * each ride's way is cut at its own platform instead of the coordinate of the whole station.
 * Pure logic: the platforms and the measured walks come from [OsmStations].
 */
object PlatformChange {
    // Pace and detour of a walk between platforms whose walk is not measured (as for stop positions).
    private const val METERS_PER_MINUTE = 75.0
    private const val DETOUR = 1.3

    // Platforms closer than this are one island or side by side: nothing to walk.
    const val MIN_WALK_METERS = 25.0

    // A walk shorter than this is drawn straight (a way inside the station, no foot router).
    private const val INSIDE_METERS = 150.0

    /**
     * The platforms at a station complex where a ride is boarded or left: [station] tells
     * complexes apart (the same object for one complex), [platforms] those the ride may use.
     */
    class Ends(val station: Any, val platforms: List<OsmPlatform>)

    /** True where the rides use one platform: the platform of one of them is among those of the other, and it is the only one. */
    fun samePlatform(from: Ends, to: Ends): Boolean {
        if (from.station !== to.station) return false
        val shared = from.platforms.filter { it in to.platforms }
        if (shared.isEmpty()) return false
        return from.platforms.size == 1 || to.platforms.size == 1
    }

    /**
     * The two nearest points of [from] and [to] (platform axes), the start and end of a walk
     * between them; null without platforms.
     */
    fun closestPoints(from: List<OsmPlatform>, to: List<OsmPlatform>): Pair<Spot, Spot>? {
        var best: Pair<Spot, Spot>? = null
        var bestMeters = Double.MAX_VALUE
        for (a in from) for (b in to) {
            // Each axis sampled, then the nearest point of the other axis to each sample.
            for (i in 0..SAMPLES) {
                val p = along(a, i.toDouble() / SAMPLES)
                val q = PathJoin.nearest(listOf(b.a, b.b), p).point
                val d = PathJoin.meters(p, q)
                if (d < bestMeters) {
                    bestMeters = d
                    best = p to q
                }
            }
            for (i in 0..SAMPLES) {
                val q = along(b, i.toDouble() / SAMPLES)
                val p = PathJoin.nearest(listOf(a.a, a.b), q).point
                val d = PathJoin.meters(p, q)
                if (d < bestMeters) {
                    bestMeters = d
                    best = p to q
                }
            }
        }
        return best
    }

    private const val SAMPLES = 10

    private fun along(platform: OsmPlatform, share: Double): Spot =
        (platform.a.first + (platform.b.first - platform.a.first) * share) to
            (platform.a.second + (platform.b.second - platform.a.second) * share)

    /** The middle of [platform]. */
    fun middle(platform: OsmPlatform): Spot = along(platform, 0.5)

    /**
     * [ride]'s way cut at its platforms: at the start where it is boarded ([boarding], the one
     * whose middle is nearest to where the way starts), at the end where it is left. Unchanged
     * where a platform is not known or lies off the way.
     */
    fun clipAtPlatforms(ride: Leg, boarding: List<OsmPlatform>, alighting: List<OsmPlatform>): Leg {
        if (ride.walking || ride.path.size < 2) return ride
        var path = ride.path
        boarding.map { middle(it) }.minByOrNull { PathJoin.meters(it, path.first()) }?.let { spot ->
            path = PathJoin.clipStartAtStop(path, spot, PathJoin.PLATFORM_CLIP_METERS, PathJoin.PLATFORM_WINDOW_METERS)
        }
        alighting.map { middle(it) }.minByOrNull { PathJoin.meters(it, path.last()) }?.let { spot ->
            path = PathJoin.clipEndAtStop(path, spot, PathJoin.PLATFORM_CLIP_METERS, PathJoin.PLATFORM_WINDOW_METERS)
        }
        return if (path == ride.path) ride else ride.copy(path = path)
    }

    /**
     * The walk between [arriving] and [next] where both are rides of rail ([railProducts]) at one
     * complex ([ends]: where a ride is left, false, or boarded, true) on different platforms and
     * the provider has no walk between them: from the nearest point of the one platform to the
     * other, lasting the walk measured over the walking network ([measured], seconds) or else
     * the straight line at an easy pace. Null where this does not apply.
     */
    fun walkBetween(
        arriving: Leg,
        next: Leg,
        railProducts: Set<String>,
        ends: (Leg, Boolean) -> Ends?,
        measured: (Leg, Leg) -> Int?,
    ): Leg? {
        if (arriving.walking || next.walking || arriving.cancelled || next.cancelled) return null
        if (arriving.product !in railProducts || next.product !in railProducts) return null
        if (isThroughRun(arriving, next)) return null
        val from = ends(arriving, false) ?: return null
        val to = ends(next, true) ?: return null
        if (from.station !== to.station) return null
        if (from.platforms.isEmpty() || to.platforms.isEmpty()) return null
        if (from.platforms.any { it in to.platforms }) return null
        val (start, end) = closestPoints(from.platforms, to.platforms) ?: return null
        val straight = PathJoin.meters(start, end)
        // Two edges of one island (or platforms side by side): a change across, no walk.
        if (straight < MIN_WALK_METERS) return null
        val exact = measured(arriving, next)
        if (exact != null && exact <= 0) return null
        // At the usual pace, then at the walking speed set (see pacedSeconds); the metres from the former.
        val usual = exact ?: ceil(straight * DETOUR / METERS_PER_MINUTE * 60.0).toInt()
        val seconds = pacedSeconds(usual)
        val path = if (straight < INSIDE_METERS) {
            listOf(start, ((start.first + end.first) / 2) to ((start.second + end.second) / 2), end)
        } else {
            listOf(start, end)
        }
        val departure = arriving.arrival
        val planned = arriving.plannedArrival
        return Leg(
            line = null,
            product = "",
            walking = true,
            from = arriving.to,
            to = next.from,
            departure = departure,
            plannedDeparture = planned,
            arrival = departure.plusSeconds(seconds.toLong()),
            plannedArrival = planned?.plusSeconds(seconds.toLong()),
            departurePlatform = arriving.arrivalPlatform,
            arrivalPlatform = next.departurePlatform,
            direction = null,
            cancelled = false,
            realtime = arriving.realtime,
            path = path,
            fromPlace = arriving.toPlace,
            toPlace = next.fromPlace,
            walkMeters = (usual * METERS_PER_MINUTE / 60.0).roundToInt(),
        )
    }

    /** [legs] with a walk (see [walkBetween]) between every two rides that need one. */
    fun withWalks(
        legs: List<Leg>,
        railProducts: Set<String>,
        ends: (Leg, Boolean) -> Ends?,
        measured: (Leg, Leg) -> Int?,
    ): List<Leg> {
        if (legs.size < 2) return legs
        val out = ArrayList<Leg>(legs.size + 2)
        for (index in legs.indices) {
            out.add(legs[index])
            val next = legs.getOrNull(index + 1) ?: continue
            walkBetween(legs[index], next, railProducts, ends, measured)?.let { out.add(it) }
        }
        return if (out.size == legs.size) legs else out
    }
}
