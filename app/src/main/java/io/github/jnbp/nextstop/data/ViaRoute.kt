package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime

// Searches from or to the place on the way beyond the first one, at most, for one page.
private const val MAX_VIA_SEARCHES = 3

/**
 * Journeys from [from] through [via] to [to], put together from two kinds of [search]: to the
 * place on the way, and from there on [gapMinutes] after the arrival there. Leaving at
 * [departure], each way to the place gets the way on that arrives first among those that leave
 * after the gap; arriving by [arrival], each way on gets the way to the place that leaves last
 * among those there in time. A few more searches fill in where the first answer does not
 * reach. Of ways that share one half only the best is kept. In order of departure.
 */
suspend fun composeVia(
    from: Stop,
    to: Stop,
    via: Via,
    gapMinutes: Long,
    departure: OffsetDateTime?,
    arrival: OffsetDateTime?,
    search: suspend (Stop, Stop, OffsetDateTime?, OffsetDateTime?) -> List<Journey>,
): List<Journey> {
    val place = via.place
    val combined = mutableListOf<Journey>()
    var asked = 0
    if (arrival == null) {
        val heads = search(from, place, departure, null).filter { !it.cancelled }.sortedBy { it.arrival }
        val tails = mutableListOf<Journey>()
        for (head in heads) {
            val earliest = head.arrival.plusMinutes(gapMinutes)
            var tail = tails.filter { !it.departure.isBefore(earliest) }.minByOrNull { it.arrival }
            if (tail == null && asked < MAX_VIA_SEARCHES) {
                asked++
                tails += search(place, to, earliest, null).filter { !it.cancelled }
                tail = tails.filter { !it.departure.isBefore(earliest) }.minByOrNull { it.arrival }
            }
            if (tail != null) combined += joinedVia(head, tail, via)
        }
    } else {
        val tails = search(place, to, null, arrival).filter { !it.cancelled }.sortedByDescending { it.departure }
        val heads = mutableListOf<Journey>()
        for (tail in tails) {
            val latest = tail.departure.minusMinutes(gapMinutes)
            var head = heads.filter { !it.arrival.isAfter(latest) }.maxByOrNull { it.departure }
            if (head == null && asked < MAX_VIA_SEARCHES) {
                asked++
                heads += search(from, place, null, latest).filter { !it.cancelled }
                head = heads.filter { !it.arrival.isAfter(latest) }.maxByOrNull { it.departure }
            }
            if (head != null) combined += joinedVia(head, tail, via)
        }
    }
    // Two ways to the place that catch the same way on: only the one that leaves last is worth
    // it; two ways on after the same way to the place: only the one that arrives first.
    return combined
        .groupBy { journey -> journeyKey(Journey(journey.legs.drop(journey.via?.afterLegs ?: 0))) + journey.arrival }
        .values.map { group -> group.maxBy { it.departure } }
        .groupBy { journey -> journeyKey(Journey(journey.legs.take(journey.via?.afterLegs ?: 0))) + journey.departure }
        .values.map { group -> group.minBy { it.arrival } }
        .sortedBy { it.departure }
}

/**
 * [head] to the place on the way, then [tail] from there: one journey that is loaded again half
 * by half. Where [tail] goes on in the vehicle [head] arrives with (a place on the way the
 * traveller passes seated), that is one ride, not a change (see [mergeThroughRuns]).
 */
fun joinedVia(head: Journey, tail: Journey, via: Via): Journey = mergeThroughRuns(
    Journey(
        legs = head.legs + tail.legs,
        split = JourneySplit(headLegs = head.legs.size, headToken = head.refreshToken, tailToken = tail.refreshToken),
        via = JourneyVia(via.place, via.minutes, head.legs.size),
    ),
)

/**
 * From this many hours after the time asked for, the first connection of a search through a
 * place on the way does not count as found soon (see [viaFoundTooLate]).
 */
const val VIA_SOON_HOURS = 3L

/**
 * True when a search through a place on the way, leaving at [asked], found nothing that leaves
 * within [VIA_SOON_HOURS]: none at all, or only later ones (#35a: "über Bonn" at 20:39 came back
 * with the next morning's 05:21 alone, although trains passed Bonn Hbf all evening). The search
 * is then put together from two (see composeVia), and if that finds nothing soon either, the
 * list says so instead of showing the later ones as the next.
 */
fun viaFoundTooLate(asked: OffsetDateTime, journeys: List<Journey>): Boolean =
    journeys.isEmpty() || journeys.minOf { it.departure.toInstant() }.isAfter(asked.plusHours(VIA_SOON_HOURS).toInstant())

/**
 * The better of a search through a place on the way in one request ([single]) and the same put
 * together from two ([composed], see composeVia), leaving at [asked]: the single one where it
 * found something soon (see [viaFoundTooLate]); else the one whose first connection leaves first.
 */
fun earlierVia(asked: OffsetDateTime, single: List<Journey>, composed: List<Journey>?): Boolean {
    if (!viaFoundTooLate(asked, single) || composed.isNullOrEmpty()) return false
    if (single.isEmpty()) return true
    return composed.minOf { it.departure.toInstant() }.isBefore(single.minOf { it.departure.toInstant() })
}
