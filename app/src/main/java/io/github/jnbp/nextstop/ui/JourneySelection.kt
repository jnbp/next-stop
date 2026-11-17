package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.TransferComfort
import io.github.jnbp.nextstop.data.journeyChangesOf
import io.github.jnbp.nextstop.data.journeyComfort
import io.github.jnbp.nextstop.data.withoutDominated

/**
 * The journeys of a search as every view of it shows them (chart, list and plan), and those
 * the category chips hide right now. All views take the same lists, so they agree on whether
 * anything is left to show.
 */
class JourneySelection(
    /** The journeys to show; null while there is no result. */
    val shown: List<Journey>?,
    /** The journeys found but tighter than the chosen level. */
    val filtered: List<Journey>,
    /** The categories of the hidden journeys. */
    val filteredLevels: Set<TransferComfort>,
    /** The next tighter level that holds hidden journeys; null when nothing is hidden. */
    val nextLevel: TransferComfort?,
    /** How many hidden journeys belong to [nextLevel]. */
    val nextCount: Int,
)

/**
 * Splits [journeys] into those shown and those hidden by the chosen [threshold], the tightest
 * level of change the user accepts: a journey is shown when its category is at that level or
 * more relaxed, never when it is tighter. The category always comes from the very journey that
 * is drawn (see [journeyComfort]), so the filter can never disagree with its tag or the marks
 * of its changes; [levels] is not asked for it. A journey without a change (also two rides
 * of one trip, staying on board) has no risk of missing one and is always shown. Of journeys
 * that only differ in a tighter change the one with more room is kept (see withoutDominated).
 */
fun journeySelection(
    journeys: List<Journey>?,
    threshold: TransferComfort,
    @Suppress("UNUSED_PARAMETER") levels: Map<String, TransferComfort> = emptyMap(),
): JourneySelection {
    if (journeys == null) return JourneySelection(null, emptyList(), emptySet(), null, 0)
    fun levelOf(journey: Journey) = journeyComfort(journey)
    fun direct(journey: Journey) = journeyChangesOf(journey).isEmpty()
    // A twin with a tighter change than another of the same lines and times is no choice of its
    // own (see withoutDominated); the one kept has at least as much room, so it is shown at every
    // level the twin would be.
    val (shown, filtered) = withoutDominated(journeys).partition { direct(it) || levelOf(it).ordinal <= threshold.ordinal }
    val filteredLevels = filtered.map { levelOf(it) }
    val next = filteredLevels.minByOrNull { it.ordinal }
    return JourneySelection(shown, filtered, filteredLevels.toSet(), next, filteredLevels.count { it == next })
}

/**
 * The journey of [shown] to keep in view when another transfer level is chosen: [anchor] itself
 * where the new list still has it (by its id), else the first that leaves no earlier than it,
 * else the last that leaves before it. So the list stays where it was instead of jumping to its
 * start. Null without an anchor or journeys.
 */
fun levelAnchor(anchor: Journey?, shown: List<Journey>): Journey? {
    if (anchor == null || shown.isEmpty()) return null
    shown.firstOrNull { it.id == anchor.id }?.let { return it }
    return shown.firstOrNull { !it.departure.isBefore(anchor.departure) }
        ?: shown.lastOrNull { it.departure.isBefore(anchor.departure) }
}
