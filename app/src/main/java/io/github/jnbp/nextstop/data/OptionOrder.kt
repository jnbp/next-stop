package io.github.jnbp.nextstop.data

import java.time.Duration

/*
 * The order in which connections are offered as a choice for the rest of a trip ("Which
 * connection do you want to take?"): by when they get the traveller to the destination, the
 * one they follow now included, so the tiles read 18:08 | 18:12 (chosen) | 18:17 and the
 * "-4" and "+5" beside them grow from left to right. Sorting by departure put a later departure
 * that arrives earlier behind one that arrives later. The order of a search result list stays
 * by departure: that list pages to earlier and later departures.
 */

/**
 * Connections offered as a choice, in the order they are shown: earliest arrival at the
 * destination first; at the same arrival the earlier departure of the first ride, then fewer
 * rides, then less walking. The chosen one sorts like every other one; a screen finds it by
 * its ride, not by its place in the list.
 */
val optionOrder: Comparator<Journey> = compareBy<Journey> { it.arrival }
    .thenBy { it.rides.firstOrNull()?.departure ?: it.departure }
    .thenBy { it.rides.size }
    .thenBy { journey -> journey.legs.filter { it.walking }.sumOf { Duration.between(it.departure, it.arrival).toMinutes() } }
