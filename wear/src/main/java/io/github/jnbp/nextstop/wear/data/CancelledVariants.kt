package io.github.jnbp.nextstop.wear.data

/*
 * #233 on the watch: one train, two results, once running and once "Fällt aus". A search from
 * an address finds several ways to the same long-distance train (another feeder to another
 * station on its run). When one of them cannot be done (the train skips the stop it is boarded
 * at today, or its feeder is cancelled), the provider marks that way cancelled while the same
 * train runs in the other. The twin of withoutCancelledVariants in P data/JourneyGroups.kt
 * (change both together); the watch knows no trip ids nor stop ids of its journeys, so the
 * same train is the same product and line, and the same station the same name or position.
 * Plain Kotlin, tested on the JVM (CancelledVariantsTest).
 */

/** True when any ride or walk of [journey] is cancelled (the phone's Journey.cancelled). */
val Journey.cancelledAnywhere: Boolean get() = legs.any { it.cancelled }

/**
 * [journeys] without a cancelled variant of a way that another one of them goes: a cancelled
 * journey is left out when a journey that is not cancelled rides the same train as its main
 * ride (its longest by planned time) to the same station at the same planned time and gets to
 * the destination no later. A train cancelled all along stays visible in every journey that
 * takes it, as does a cancelled way that would have got there earlier. The order is kept.
 */
fun withoutCancelledVariants(journeys: List<Journey>): List<Journey> {
    if (journeys.size < 2 || journeys.none { it.cancelledAnywhere }) return journeys
    val running = journeys.filter { !it.cancelledAnywhere && it.rides.isNotEmpty() }
    if (running.isEmpty()) return journeys
    val kept = journeys.filter { journey ->
        if (!journey.cancelledAnywhere) return@filter true
        val main = mainRideOf(journey) ?: return@filter true
        running.none { other -> other.arrival <= journey.arrival && other.rides.any { ridesTheSameTrainTo(it, main) } }
    }
    return if (kept.size == journeys.size) journeys else kept
}

/** The ride a journey is made for: its longest ride by planned time (the long-distance train after a short bus). */
private fun mainRideOf(journey: Journey): Leg? = journey.rides.maxByOrNull { plannedArrivalOf(it) - plannedDepartureOf(it) }

/** True when [ride] (of a running journey) is the vehicle of [main] and leaves it at the same station at the same planned time. */
private fun ridesTheSameTrainTo(ride: Leg, main: Leg): Boolean {
    if (ride.walking || ride.cancelled) return false
    val line = ride.line ?: return false
    val mainLine = main.line ?: return false
    if (ride.product != main.product || plainLine(line) != plainLine(mainLine)) return false
    return plannedArrivalOf(ride) == plannedArrivalOf(main) && sameStationOf(ride, main)
}

private fun plainLine(line: String): String = line.replace(Regex("\\s+"), "").uppercase()

private fun plannedArrivalOf(leg: Leg): Long = leg.arrival - leg.arrivalDelayMinutes * 60_000L

private fun plannedDepartureOf(leg: Leg): Long = leg.departure - leg.departureDelayMinutes * 60_000L

/** The stops where both rides end: by name, else (other spellings of providers) within a few steps of each other. */
private fun sameStationOf(a: Leg, b: Leg): Boolean {
    if (a.to.trim().equals(b.to.trim(), ignoreCase = true)) return true
    val p = a.toPosition ?: return false
    val q = b.toPosition ?: return false
    return metresApart(p.first, p.second, q.first, q.second) <= SAME_STATION_METRES
}

private fun metresApart(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val east = (lon2 - lon1) * 111_320.0 * kotlin.math.cos(Math.toRadians((lat1 + lat2) / 2))
    val north = (lat2 - lat1) * 111_320.0
    return kotlin.math.sqrt(east * east + north * north)
}

// Two platforms of one station lie within this distance (m).
private const val SAME_STATION_METRES = 300.0
