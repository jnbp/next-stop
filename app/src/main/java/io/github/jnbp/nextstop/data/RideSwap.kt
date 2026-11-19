package io.github.jnbp.nextstop.data

/*
 * Another connection picked at a change (the "Anschluss" tiles) or another way taken swaps
 * the rides after that change. The detail page and the Live-Fahrt keep their rows by position
 * and crossfade a row only where its ride is another one; these say which ride a row shows and
 * whether a new journey is such a swap of the one shown before.
 */

/**
 * The identity of the legs [indices] of [legs] for a swap: planned times, line and start of each
 * (as in [journeyLegsId]), so live times and platforms change nothing; "-" for an index outside.
 */
fun legSwapKey(legs: List<Leg>, vararg indices: Int): String =
    indices.joinToString("/") { index -> legs.getOrNull(index)?.let { journeyLegsId(listOf(it)) } ?: "-" }

/**
 * True when [after] is [before] with another rest: both begin with the same leg, but they are
 * not the same legs. Then the page stays where it is and swaps the rows that changed; another
 * journey altogether is read from its top. False without a journey before.
 */
fun swapsRest(before: List<Leg>?, after: List<Leg>): Boolean {
    if (before.isNullOrEmpty() || after.isEmpty()) return false
    if (legSwapKey(before, 0) != legSwapKey(after, 0)) return false
    return journeyLegsId(before) != journeyLegsId(after)
}

/**
 * The indices of [kept] (rides unfolded, pickers turned) that still show the same leg in [after]
 * as in [before], to keep them after a swap; an index whose ride was swapped is dropped.
 */
fun keptAfterSwap(kept: List<Int>, before: List<Leg>, after: List<Leg>): List<Int> =
    kept.filter { index -> index in after.indices && legSwapKey(before, index) == legSwapKey(after, index) }
