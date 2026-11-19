package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime

/*
 * #243: under a change of the detail page and of the Live-Fahrt, two toggle chips side by side,
 * "Anschluss" (the connecting departures, as "Anschluss wechseln" before) and "Andere Wege" (the
 * search for completely different ways from the change, as "Andere Wege ab hier" before), and
 * below them ONE shared row of tiles: only the connections, only the other ways, both mixed in
 * the order they arrive, or nothing (collapsed to the chip row).
 */

/** What the shared row of tiles under a change shows by its two chips. */
enum class ChangeTileRow { Collapsed, Connections, Ways, Mixed }

/** The row for the chip "Anschluss" ([connections]) and the chip "Andere Wege" ([ways]), each on or off. */
fun changeTileRow(connections: Boolean, ways: Boolean): ChangeTileRow = when {
    connections && ways -> ChangeTileRow.Mixed
    connections -> ChangeTileRow.Connections
    ways -> ChangeTileRow.Ways
    else -> ChangeTileRow.Collapsed
}

/** Where the search of other ways at a change stands: not asked yet, searching, found (maybe none), failed. */
enum class WaysSearch { None, Loading, Done, Failed }

/** What a tap on the chip "Andere Wege" does: search (again), fold what shows, or unfold it again. */
enum class WaysChipTap { Search, Fold, Unfold }

/**
 * Whether the chip "Andere Wege" is on: something was asked for ([search] not None, a failed
 * search too, so its failure can be read and tried again) and it was not folded by hand ([open]).
 */
fun waysChipOn(search: WaysSearch, open: Boolean): Boolean = search != WaysSearch.None && open

/**
 * A tap on the chip "Andere Wege": the first one searches; an on chip folds; an off one unfolds
 * what was found, or searches again where the search failed. Every setting the same (r7plan).
 */
fun waysChipTap(search: WaysSearch, open: Boolean): WaysChipTap = when {
    search == WaysSearch.None -> WaysChipTap.Search
    open -> WaysChipTap.Fold
    search == WaysSearch.Failed -> WaysChipTap.Search
    else -> WaysChipTap.Unfold
}

/**
 * The order of the tiles when the connections and the other ways share the row (both chips on):
 * strictly by the [arrivals] at the destination, the earliest first, the chosen connection at
 * its place like every other tile; equal arrivals by the [departures], then as given. Indices
 * of [arrivals]. (One kind alone keeps its own order: the connections by their departure at the
 * change, see tileOrder, #215.)
 */
fun mixedTileOrder(arrivals: List<OffsetDateTime>, departures: List<OffsetDateTime>): List<Int> =
    arrivals.indices.sortedWith(
        compareBy<Int> { arrivals[it].toInstant() }
            .thenBy { departures.getOrNull(it)?.toInstant() }
            .thenBy { it },
    )

/**
 * How many tiles of the row in [order] stand before the [chosen] one (an index of the tiles)
 * and show ([shown]): the row is pushed so that one of them stays in view in front of it.
 */
fun tilesBeforeChosen(order: List<Int>, chosen: Int, shown: (Int) -> Boolean): Int =
    order.takeWhile { it != chosen }.count(shown)

/*
 * #257: the shared row is ONE row in all three states. A chip that is turned off only hides its
 * kind of tiles (they leave softly, the others glide to their places), one turned on lets them
 * come in; before, "Anschluss" off swapped the whole row for a row of the other ways alone, so
 * the connection tiles vanished at once.
 */

/** Which kinds of tiles the shared row shows: the connections and the other ways. */
data class ChangeTileShow(val connections: Boolean, val ways: Boolean)

/** The kinds of tiles the row [row] shows (Collapsed: none; the fold keeps what it showed). */
fun changeTileShow(row: ChangeTileRow): ChangeTileShow = ChangeTileShow(
    connections = row == ChangeTileRow.Connections || row == ChangeTileRow.Mixed,
    ways = row == ChangeTileRow.Ways || row == ChangeTileRow.Mixed,
)

/** Whether a connection tile shows: its kind shows, and it can be reached or is the chosen one. */
fun connectionTileShown(show: ChangeTileShow, inReach: Boolean, picked: Boolean): Boolean =
    show.connections && (inReach || picked)

/**
 * The order of the shared row (indices: the connections first, then the other ways). With the
 * other ways showing ([waysShown]) every tile goes by its arrival ([mixedTileOrder]; that of the
 * ways alone is the same, connections hidden); without them the connections go by their
 * departure at the change ([tileOrder], #215) and the hidden ways, still leaving, stand after them.
 */
fun changeRowOrder(
    connectionDepartures: List<OffsetDateTime>,
    connectionArrivals: List<OffsetDateTime>,
    wayArrivals: List<OffsetDateTime>,
    wayDepartures: List<OffsetDateTime>,
    waysShown: Boolean,
): List<Int> {
    val count = connectionDepartures.size
    return if (waysShown && wayArrivals.isNotEmpty()) {
        mixedTileOrder(connectionArrivals + wayArrivals, connectionDepartures + wayDepartures)
    } else {
        tileOrder(connectionDepartures) + mixedTileOrder(wayArrivals, wayDepartures).map { it + count }
    }
}

/**
 * How many shown tiles of the row in [order] stand before the [chosen] connection, for the row
 * to be pushed there ([tilesBeforeChosen]); [connectionCount] connections come first in the
 * indices, [reachable] says which of them show. With the connections hidden the row starts at
 * its beginning, as the ways alone did.
 */
fun rowTilesBeforeChosen(
    order: List<Int>,
    chosen: Int,
    connectionCount: Int,
    show: ChangeTileShow,
    reachable: (Int) -> Boolean,
): Int = if (!show.connections) {
    0
} else {
    tilesBeforeChosen(order, chosen) { if (it >= connectionCount) show.ways else reachable(it) }
}

/*
 * Variante 2 (Joel 19.11.): the two chips keep their sections apart. "Anschluss" on: the compact
 * row of connection tiles (by departure, the chosen one at its place). "Andere Wege" on: below
 * it a list of full-width rows, one per way: its arrival, the delta, the whole chain of its
 * lines and a round take button. Each section folds in and out on its own (see changeTileShow).
 */

/** So many other ways show as rows at first; the rest behind "Weitere anzeigen". */
const val WAY_ROWS_FOLDED = 3

/** How many of [total] ways show as rows, all of them once [expanded]. */
fun wayRowsShown(total: Int, expanded: Boolean): Int =
    if (expanded) total else minOf(total, WAY_ROWS_FOLDED)

/** Whether "Weitere anzeigen" shows under the rows of the other ways. */
fun wayRowsMore(total: Int, expanded: Boolean): Boolean = !expanded && total > WAY_ROWS_FOLDED

/** A walk of so many minutes or more shows in the chain of lines of a way, as a small walking figure. */
const val LONG_WALK_MINUTES = 5L

/** One step of the chain of lines of a way: a ride (the index of its leg) or a long walk. */
sealed interface WayChainStep {
    data class Ride(val leg: Int) : WayChainStep
    data class Walk(val minutes: Long) : WayChainStep
}

/**
 * The chain of a way by its legs ([walking] and the [minutes] of each): every ride, and the
 * walks of [LONG_WALK_MINUTES] or more between, before or after them; short walks are left out.
 */
fun wayChain(walking: List<Boolean>, minutes: List<Long>): List<WayChainStep> =
    walking.indices.mapNotNull { index ->
        when {
            !walking[index] -> WayChainStep.Ride(index)
            (minutes.getOrNull(index) ?: 0L) >= LONG_WALK_MINUTES -> WayChainStep.Walk(minutes[index])
            else -> null
        }
    }
