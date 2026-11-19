package io.github.jnbp.nextstop.data

/*
 * #277b: where the times of a journey come from right now, for the small line below the
 * colour legend of the detail and the Live-Fahrt ("Daten: Transitous · Echtzeit: BVG").
 */

/** Where live times of a journey come from: its provider, a HAFAS source that knows the same run (#274), or the app's estimate. */
enum class LiveOrigin { Provider, Hafas, Estimate }

/** The [provider] of a journey (null where no handle tells it) and the origins of its live times, each once, in that order. */
data class JourneySources(val provider: Backend?, val live: List<LiveOrigin>)

/**
 * The sources of [journey]: the backend its rides came from, by the tag of their handles (see
 * [Backend.tag], [DataSource.prefix]), and whether their live times are the provider's own,
 * from HAFAS ([Leg.hafasLive]) or the app's estimate ([Leg.arrivalEstimated], [Stopover.estimated]).
 */
fun journeySources(journey: Journey): JourneySources {
    val rides = journey.legs.filter { !it.walking }
    val handle = rides.firstNotNullOfOrNull { it.tripId } ?: journey.refreshToken
    val live = ArrayList<LiveOrigin>(3)
    if (rides.any { it.realtime && !it.hafasLive }) live.add(LiveOrigin.Provider)
    if (rides.any { it.hafasLive }) live.add(LiveOrigin.Hafas)
    if (journey.legs.any { it.arrivalEstimated } || rides.any { ride -> ride.stopovers.any { it.estimated } }) live.add(LiveOrigin.Estimate)
    return JourneySources(handle?.let { backendOfHandle(it) }, live)
}

/** The backend a ride handle or refresh token came from: Transitous by its prefix, else the HAFAS family by its tag. */
fun backendOfHandle(value: String): Backend =
    if (DataSource.Transitous.prefix.isNotEmpty() && value.startsWith(DataSource.Transitous.prefix)) Backend.Transitous else backendOf(DataSource.Bvg, value)
