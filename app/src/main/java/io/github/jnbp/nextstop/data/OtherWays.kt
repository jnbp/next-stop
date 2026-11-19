package io.github.jnbp.nextstop.data

import java.time.Duration

/*
 * #164: "Andere Wege ab hier" at a change of a followed trip. A search from the change stop to
 * the destination finds completely different ways (a direct train, other lines), not only other
 * departures of the next ride. Each is compared with the rest of the current plan: when it
 * arrives ("an 16:01 · 4′ früher") and how many changes it has ("1 Umstieg weniger").
 */

/** How a way compares with the rest of the plan: minutes later at the destination (below 0: earlier) and changes more (below 0: fewer). */
data class WayCompare(val arrivalMinutes: Long, val changes: Int)

/** What tells two ways apart: their rides by line and planned departure. */
fun wayKey(journey: Journey): String =
    journey.legs.filter { !it.walking }.joinToString("|") { "${it.line}@${it.plannedDeparture ?: it.departure}" }

/** [option] compared with [current] (see [WayCompare]); changes are the rides less one. */
fun compareWay(current: Journey, option: Journey): WayCompare = WayCompare(
    arrivalMinutes = Math.floorDiv(Duration.between(current.arrival, option.arrival).seconds, 60L),
    changes = option.legs.count { !it.walking } - current.legs.count { !it.walking },
)

/**
 * The ways worth offering instead of [current] (the rest of the plan from the change) among
 * [options]: not the plan itself, each way once, none that is worse in both (later and with
 * more changes); earliest first, then fewer changes; at most [limit].
 */
fun otherWays(current: Journey, options: List<Journey>, limit: Int = 4): List<Pair<Journey, WayCompare>> {
    val own = wayKey(current)
    return options
        .filter { it.legs.any { leg -> !leg.walking } && wayKey(it) != own }
        .distinctBy { wayKey(it) }
        .map { it to compareWay(current, it) }
        .filterNot { (_, compare) -> compare.arrivalMinutes > 0 && compare.changes > 0 }
        .sortedWith(compareBy<Pair<Journey, WayCompare>> { it.second.arrivalMinutes }.thenBy { it.second.changes })
        .take(limit)
}
