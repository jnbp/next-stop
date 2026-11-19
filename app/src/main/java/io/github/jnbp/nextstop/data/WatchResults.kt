package io.github.jnbp.nextstop.data

/**
 * The journeys of a search the watch asked for, as the phone's own search list would offer them
 * at [level]: none with a change tighter than the level (see [withinLevel], the rule of
 * journeySelection), the first [results] by the time to set off. A search at "knapp" asks the
 * provider for changes of 0 minutes and more, and a change shorter than 2 minutes is a sprint
 * by the rule (see [paceTightness]): without this the watch offered such journeys at "knapp".
 */
fun watchJourneys(journeys: List<Journey>, level: TransferComfort, results: Int): List<Journey> =
    journeys.filter { withinLevel(it, level) }.sortedWith(searchOrder).take(results)

/**
 * How many journeys to ask the source for, for [results] at [level]: a few more where the level
 * may leave some out (see [watchJourneys]), at most [max]; at "Sprint" none is left out.
 */
fun watchSearchCount(results: Int, level: TransferComfort, max: Int): Int =
    if (level == TransferComfort.VeryRisky) results else (results + WATCH_EXTRA_RESULTS).coerceAtMost(max).coerceAtLeast(results)

private const val WATCH_EXTRA_RESULTS = 3

/**
 * The rating of every change of [journey] by the index of the leg it leads to (see
 * [journeyChangesOf]): sent to the watch with the leg, so its overview, trip pages and the
 * filter of its results rate a change as the phone does.
 */
fun changeRatingsByLeg(journey: Journey): Map<Int, Tightness> =
    journeyChangesOf(journey).filter { it.nextIndex >= 0 }.associate { it.nextIndex to it.tightness }
