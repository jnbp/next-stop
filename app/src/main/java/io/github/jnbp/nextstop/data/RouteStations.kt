package io.github.jnbp.nextstop.data

/**
 * The stations of [journey] as the map of a journey marks them: where each ride starts and
 * ends and its stops on the way, each once (by id), stops only (no addresses or places), and
 * only those with a position.
 */
fun journeyStations(journey: Journey): List<Stop> =
    stationsOf(journey.legs.filter { !it.walking }.flatMap { leg -> listOfNotNull(leg.fromPlace) + leg.stopovers.mapNotNull { it.place } + listOfNotNull(leg.toPlace) })

/** The stations of a line's course (see [journeyStations]). */
fun courseStations(stops: List<Stopover>): List<Stop> = stationsOf(stops.mapNotNull { it.place })

private fun stationsOf(places: List<Stop>): List<Stop> {
    val seen = HashSet<String>()
    return places.filter { it.kind == PlaceKind.Stop && it.latitude != null && it.longitude != null && seen.add(it.id) }
}

/**
 * The index of the station nearest to the point ([x], [y]) on the screen among those placed at
 * [xs] and [ys], within [reach] pixels; -1 where none is that near.
 */
fun nearestStationIndex(xs: FloatArray, ys: FloatArray, x: Float, y: Float, reach: Float): Int {
    var best = -1
    var bestSquare = reach * reach
    for (i in xs.indices) {
        val dx = xs[i] - x
        val dy = ys[i] - y
        val square = dx * dx + dy * dy
        if (square <= bestSquare) {
            bestSquare = square
            best = i
        }
    }
    return best
}
