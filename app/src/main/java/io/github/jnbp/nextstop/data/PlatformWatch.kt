package io.github.jnbp.nextstop.data

/**
 * A change of platform on a followed journey: the ride [legIndex] now leaves ([departure]) or
 * arrives from/at [now] instead of [planned]. [key] stays the same for the same change, so it
 * is announced once.
 */
data class PlatformShift(
    val legIndex: Int,
    val departure: Boolean,
    val planned: String,
    val now: String,
    val stop: String,
) {
    val key: String get() = "$legIndex|${if (departure) "d" else "a"}|$stop|$now"
}

/** The key under which the platform a ride was first seen with is remembered. */
fun platformKey(leg: Leg, departure: Boolean): String =
    "${leg.tripId ?: leg.line ?: leg.product}|${if (departure) "d|" + leg.from else "a|" + leg.to}"

// A track number with its sections, as some sources send it: "1 D - G", "11 A-C", "7A-C".
private val SECTIONED_TRACK = Regex("^(\\d{1,3})(?:\\s+[A-Za-z]{1,2}(?:\\s?-\\s?[A-Za-z])?|\\s?[A-Za-z]\\s?-\\s?[A-Za-z])?$")

/** The number of the track [platform] names, with or without its sections; null for anything else ("4a" included). */
internal fun trackNumber(platform: String): Int? = SECTIONED_TRACK.matchEntire(platform.trim())?.groupValues?.get(1)?.toInt()

/**
 * Platforms written differently that are the same: " 3 " and "3", and one track with or
 * without its sections ("1" and "1 D - G", #172: one source sends the sections, the other not).
 * "Gl. 3" is not touched; "4a" and "4b" stay two.
 */
private fun same(a: String, b: String): Boolean {
    if (a.trim().equals(b.trim(), ignoreCase = true)) return true
    val first = trackNumber(a) ?: return false
    return first == trackNumber(b)
}

/**
 * The platform changes of [journey]: per ride, where it is boarded and where it is left, the
 * live platform against the timetable's ([Leg.plannedDeparturePlatform]) where the provider
 * sends both, else against the platform the ride had when it was first seen ([firstSeen], by
 * [platformKey]; see [rememberPlatforms]). Walks and cancelled rides have none.
 */
fun platformShifts(journey: Journey, firstSeen: Map<String, String>): List<PlatformShift> {
    val shifts = mutableListOf<PlatformShift>()
    journey.legs.forEachIndexed { index, leg ->
        if (leg.walking || leg.cancelled) return@forEachIndexed
        val depNow = leg.departurePlatform?.takeIf { it.isNotBlank() }
        val depPlanned = (leg.plannedDeparturePlatform ?: firstSeen[platformKey(leg, true)])?.takeIf { it.isNotBlank() }
        if (depNow != null && depPlanned != null && !same(depNow, depPlanned)) {
            shifts.add(PlatformShift(index, true, depPlanned.trim(), depNow.trim(), leg.from))
        }
        val arrNow = leg.arrivalPlatform?.takeIf { it.isNotBlank() }
        val arrPlanned = (leg.plannedArrivalPlatform ?: firstSeen[platformKey(leg, false)])?.takeIf { it.isNotBlank() }
        if (arrNow != null && arrPlanned != null && !same(arrNow, arrPlanned)) {
            shifts.add(PlatformShift(index, false, arrPlanned.trim(), arrNow.trim(), leg.to))
        }
    }
    return shifts
}

/** [firstSeen] with the platforms of [journey] it does not know yet (the first one seen stays). */
fun rememberPlatforms(journey: Journey, firstSeen: Map<String, String>): Map<String, String> {
    var out: MutableMap<String, String>? = null
    for (leg in journey.legs) {
        if (leg.walking) continue
        for (departure in booleanArrayOf(true, false)) {
            val platform = (if (departure) leg.plannedDeparturePlatform ?: leg.departurePlatform else leg.plannedArrivalPlatform ?: leg.arrivalPlatform)
                ?.takeIf { it.isNotBlank() } ?: continue
            val key = platformKey(leg, departure)
            if (key in firstSeen || out?.containsKey(key) == true) continue
            if (out == null) out = firstSeen.toMutableMap()
            out[key] = platform
        }
    }
    return out ?: firstSeen
}

/**
 * The changes of [shifts] still to announce at the step of the trip: those not in [told] whose
 * ride is not over yet (by [rideOver], the index of the last ride that is over, -1 for none).
 */
fun newPlatformShifts(shifts: List<PlatformShift>, told: Set<String>, rideOver: Int): List<PlatformShift> =
    shifts.filter { it.key !in told && it.legIndex > rideOver }
