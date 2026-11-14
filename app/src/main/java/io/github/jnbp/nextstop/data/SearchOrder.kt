package io.github.jnbp.nextstop.data

import java.time.Duration

/*
 * The order of the connections a search shows (the list, the chart and the plan view): by the
 * time on the card, the moment the traveller has to set off. That is the departure of the first
 * leg, a walk to the first stop included, live where the provider knows it (Journey.departure,
 * as journeySpanText and the minutes on the card show it). At the same moment the earlier
 * arrival comes first, then fewer rides, then less walking, and last the id, so the order is
 * total: it does not depend on which page or which level of transfer comfort a journey came
 * with, and two journeys swap places only when their times really cross.
 *
 * The list is put in this order every time it changes: when a page of earlier or later
 * journeys comes in, when tighter journeys are added, and when live data replaces a journey.
 * Before, a refresh put new times in place without sorting again, so a delayed journey kept
 * its slot ahead of one that now leaves earlier, and the next page then moved it at once.
 *
 * The choice offered at a change in the trip companion has its own order, by arrival
 * (optionOrder in OptionOrder.kt). The watch sorts its few results by the same rule
 * (searchOrder in W data/TransitRouter.kt); change both together.
 */

/**
 * Connections of a search in the order they are shown: the earliest time to set off first
 * (the start of a leading walk, live where known); then the earlier arrival, fewer rides,
 * less walking; last the [Journey.id], so every list of the same journeys sorts alike.
 * Times compare as instants, so two providers' offsets for the same moment sort together.
 */
val searchOrder: Comparator<Journey> = compareBy<Journey> { it.departure.toInstant() }
    .thenBy { it.arrival.toInstant() }
    .thenBy { it.rides.size }
    .thenBy { journey -> journey.legs.filter { it.walking }.sumOf { Duration.between(it.departure, it.arrival).seconds } }
    .thenBy { it.id }

/**
 * The connections of a search with [added] (a new page, or the pages of several levels of
 * transfer comfort) merged into [shown]: each connection once, and in [searchOrder]. The same
 * connection found twice (by [journeyKey]: its rides, not the handle of the request) keeps
 * the copy that is shown already, so a pick, the stops loaded for it and a swapped rest stay
 * with it; fresh times reach it with the next refresh. A cancelled journey whose ride another
 * one replaces with an extra run is left out (see [withoutReplacedJourneys]).
 */
fun mergeSearchPages(shown: List<Journey>, added: List<Journey>): List<Journey> =
    withoutReplacedJourneys((shown + added).distinctBy { journeyKey(it) }).sortedWith(searchOrder)
