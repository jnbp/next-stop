package io.github.jnbp.nextstop.data

import java.util.Locale

/*
 * The long-distance network for the map far out (a state, the whole country): which hops and
 * tracks of ICE, IC and EC trains go into it, under which key, and how important each is. The
 * tiles of the map carry railways only from zoom 8 on; further out the map would show no
 * railway at all. Pure logic, no Android (see LongDistanceNetwork for the loading).
 */

/**
 * A hop of a long-distance train from stop to stop as the map of MOTIS sends it: the [mode] of
 * its trip ("HIGHSPEED_RAIL", "LONG_DISTANCE", "NIGHT_RAIL"), the name of its [line] where
 * given, and its way.
 */
data class LongDistanceHop(val mode: String, val line: String?, val points: List<Pair<Double, Double>>)

/** The main axes: high-speed trains (ICE, ECE, TGV, Railjet Xpress), shown from the whole country on. */
const val LONG_DISTANCE_MAIN = 0

/** The other long-distance trains (IC, EC, night trains, Flixtrain): shown from the zoom of a state on. */
const val LONG_DISTANCE_OTHER = 1

// The kinds of high-speed trains, by the name of their line (see trainKind).
private val HIGH_SPEED_KINDS = setOf("ICE", "ECE", "TGV", "RJX", "ES", "EST", "THA", "EUR")

// The kinds of other long-distance trains.
private val LONG_DISTANCE_KINDS = setOf("IC", "EC", "EN", "NJ", "RJ", "FLX", "WB", "D", "IR")

/** How important a hop of [mode] on [line] is: LONG_DISTANCE_MAIN or LONG_DISTANCE_OTHER. */
fun longDistanceRank(mode: String, line: String?): Int =
    if (mode == "HIGHSPEED_RAIL" || trainKind(line) in HIGH_SPEED_KINDS) LONG_DISTANCE_MAIN else LONG_DISTANCE_OTHER

/** The rank of the track of a train named [line] (see longDistanceRank); null for a train that is no long-distance one. */
fun longDistanceTrackRank(line: String?): Int? = when (trainKind(line)) {
    in HIGH_SPEED_KINDS -> LONG_DISTANCE_MAIN
    in LONG_DISTANCE_KINDS -> LONG_DISTANCE_OTHER
    else -> null
}

/**
 * The key of a way of [rank] from its ends: "rank|kind|a|b" with the ends rounded to about
 * 100 m and in a fixed order, so the same hop of every train, in either direction, is one
 * entry. [kind] tells hops ("h") from whole tracks ("t").
 */
fun longDistanceKey(rank: Int, kind: String, points: List<Pair<Double, Double>>): String {
    val a = String.format(Locale.ROOT, "%.3f,%.3f", points.first().first, points.first().second)
    val b = String.format(Locale.ROOT, "%.3f,%.3f", points.last().first, points.last().second)
    return if (a <= b) "$rank|$kind|$a|$b" else "$rank|$kind|$b|$a"
}

/** The rank in a key of longDistanceKey; LONG_DISTANCE_OTHER for anything else. */
fun longDistanceKeyRank(key: String): Int = if (key.startsWith("$LONG_DISTANCE_MAIN|")) LONG_DISTANCE_MAIN else LONG_DISTANCE_OTHER

/**
 * The lines of [hops] for the kept network: one per key (see longDistanceKey), the one with the
 * most points where several trains run the same hop, each way first [simplified].
 */
fun longDistanceLines(
    hops: List<LongDistanceHop>,
    simplified: (List<Pair<Double, Double>>) -> List<Pair<Double, Double>> = { it },
): List<NetworkLine> {
    val best = LinkedHashMap<String, LongDistanceHop>()
    for (hop in hops) {
        if (hop.points.size < 2 || hop.points.first() == hop.points.last() && hop.points.size == 2) continue
        val key = longDistanceKey(longDistanceRank(hop.mode, hop.line), "h", hop.points)
        val known = best[key]
        if (known == null || hop.points.size > known.points.size) best[key] = hop
    }
    return best.map { (key, hop) -> NetworkLine(key, simplified(hop.points)) }.filter { it.points.size >= 2 }
}

/** A kept long-distance track with a straight step longer than this is an air line (see longDistanceShown). */
const val TRACK_STRAIGHT_MAX_METRES = 25_000.0

/**
 * The keys of [lines] to draw: all, but a way kept under both ranks (an IC on a high-speed
 * line, say) only once, as a main axis, and no whole track that is only straight lines from
 * station to station: such tracks of a foreign feed's copy of a German train were kept on
 * 19 Nov 2026 (#280) and stay in the kept network until it forgets them. New ones are not
 * taken (see LongDistanceNetwork.noteTrack); a kept track is simplified, so it is told by a
 * step longer than [TRACK_STRAIGHT_MAX_METRES], which no real railway keeps within the
 * tolerance of the simplification.
 */
fun longDistanceShown(lines: List<NetworkLine>): List<NetworkLine> {
    val drawn = lines.filter { line -> line.line.split('|').getOrNull(1) != "t" || longestGap(line.points) <= TRACK_STRAIGHT_MAX_METRES }
    val main = HashSet<String>()
    for (line in drawn) if (longDistanceKeyRank(line.line) == LONG_DISTANCE_MAIN) main.add(line.line.substringAfter('|'))
    return drawn.filter { line ->
        longDistanceKeyRank(line.line) == LONG_DISTANCE_MAIN || line.line.substringAfter('|') !in main
    }
}
