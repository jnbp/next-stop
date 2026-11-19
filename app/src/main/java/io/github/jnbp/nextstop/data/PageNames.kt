package io.github.jnbp.nextstop.data

/*
 * #157: one station, one name on a page. A long-distance ride names its stations as the railway
 * does ("Berlin Hbf", see railwayStationName); the local network calls the same station
 * "S+U Berlin Hauptbahnhof". Where the journey's long-distance rides show a station by its
 * railway name, every other row of the page (the change, the walk to it, the next stop, the
 * reminder to get off) uses that name too.
 */

/** The railway names of the stations the long-distance rides of [journey] call at. */
fun railwayNamesOf(journey: Journey): Set<String> =
    journey.legs.filter { !it.walking && it.product == "express" }
        .flatMap { leg -> listOf(leg.from, leg.to) + leg.stopovers.map { it.name } }
        .map { railwayStationName(it) }
        .toSet()

/** [name] as a page with the railway names [railwayNames] shows it: by its railway name where that is among them. */
fun pageStopName(name: String, railwayNames: Set<String>): String {
    if (railwayNames.isEmpty()) return name.plainStopName()
    val railway = railwayStationName(name)
    return if (railway in railwayNames) railway else name.plainStopName()
}

/** [name] as the page of [journey] shows it (see [pageStopName]). */
fun journeyStopName(journey: Journey, name: String): String = pageStopName(name, railwayNamesOf(journey))

/** The second line of a change's walk card (#161): "Gleis 16 → Gleis 2"; null where a platform is unknown or both are the same. */
fun platformStep(from: String?, to: String?): String? =
    if (from != null && to != null && from != to) "$from → $to" else null

/**
 * The second line of a blue change card (B2): from the platform the ride arrives at to the
 * platform or stop position the next one leaves from, and the next line in brackets:
 * "Gleis 1 → Pos. 1 (Bus 285)". One side alone where the other is not known, one platform
 * where both are the same; the line alone where no platform is known; null for nothing.
 */
fun changeStepLine(from: String?, to: String?, line: String?): String? {
    val a = from?.takeIf { it.isNotBlank() }
    val b = to?.takeIf { it.isNotBlank() }
    val step = when {
        a != null && b != null -> if (a == b) b else "$a → $b"
        else -> b ?: a
    }
    val ride = line?.takeIf { it.isNotBlank() }
    return when {
        step != null && ride != null -> "$step ($ride)"
        else -> step ?: ride
    }
}

/**
 * The stop [name] of a step on [leg] as the page with the railway names [names] shows it (#157):
 * the railway's name where a long-distance ride of the journey names it so, else as the ride
 * names its stops (see shownRideStop).
 */
fun pageRideStop(leg: Leg?, name: String, names: Set<String>): String {
    val railway = railwayStationName(name)
    return if (railway in names) railway else shownRideStop(leg, name)
}
