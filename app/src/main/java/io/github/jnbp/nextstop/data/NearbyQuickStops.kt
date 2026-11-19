package io.github.jnbp.nextstop.data

import kotlin.math.roundToInt

/**
 * How much longer a way on foot is than the straight line between its ends, for walks the app
 * estimates without a routed way (the place card, the quick picks of "Von wo?").
 */
const val WALK_DETOUR = 1.25

/** A stop near the user for the quick picks of "Von wo?" (#173): how far as the crow flies ([meters]), and its lines. */
data class QuickStopCandidate(val stop: Stop, val meters: Int, val lines: List<StopLine>)

/** A quick pick: the stop, its minutes on foot and the lines that make it worth showing. */
data class QuickStop(val stop: Stop, val walkMinutes: Long, val newLines: List<StopLine>)

/** Up to this many quick picks are shown. */
const val QUICK_STOPS_MAX = 4

/** Only stops within this many metres on foot (about ten minutes; the straight line x [WALK_DETOUR]) are offered. */
const val QUICK_STOPS_METERS = 800

/** A line regardless of its direction: the same line at two stops counts once. */
private fun lineKey(line: StopLine): String = "${line.product}|${normalLine(line.line)}"

/**
 * The quick picks under "Aktueller Standort" in "Von wo?" (#173), Joel's rule: the stops of
 * [candidates] within [maxMeters] on foot (their straight line x [WALK_DETOUR]), nearest first; a stop is offered only when it serves at least
 * one line (whatever its direction) that no nearer stop already offered serves. At most
 * [maxStops]. Each carries the lines it adds ([QuickStop.newLines]) and its minutes on foot
 * ([minutesOf] for its metres on foot). Stops without known lines are not offered.
 */
fun nearbyQuickStops(
    candidates: List<QuickStopCandidate>,
    maxStops: Int = QUICK_STOPS_MAX,
    maxMeters: Int = QUICK_STOPS_METERS,
    minutesOf: (Int) -> Long = { meters -> walkMinutesFor(meters.toDouble()) },
): List<QuickStop> {
    val seen = HashSet<String>()
    val picked = ArrayList<QuickStop>()
    for (candidate in candidates.sortedBy { it.meters }) {
        if (picked.size >= maxStops) break
        val onFoot = (candidate.meters * WALK_DETOUR).roundToInt()
        if (onFoot > maxMeters) break
        val added = candidate.lines.distinctBy { lineKey(it) }.filter { lineKey(it) !in seen }
        if (added.isEmpty()) continue
        candidate.lines.forEach { seen += lineKey(it) }
        picked += QuickStop(candidate.stop, minutesOf(onFoot), added)
    }
    return picked
}
