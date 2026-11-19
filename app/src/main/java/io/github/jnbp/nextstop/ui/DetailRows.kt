package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.StopRowSpec
import io.github.jnbp.nextstop.ui.companion.JourneyRidePhase
import io.github.jnbp.nextstop.ui.companion.JourneyRow
import io.github.jnbp.nextstop.ui.companion.journeyRows
import io.github.jnbp.nextstop.ui.companion.journeyStopRows

/*
 * The plan of the connection detail in the look of the Live-Fahrt (#129), free of Compose and
 * of the data of the trip, so it can be tested: which items the lazy list holds in which order.
 * Every ride is split into items of its own (the head that stays pinned while its stops scroll,
 * its hints, one item per stop row, its foot), so a long ride is never composed at once.
 */

/** How the body of the connection detail looks (#129): the Live-Fahrt timeline (default) or the classic one. */
enum class DetailStyle {
    Timeline,
    Classic,
    ;

    companion object {
        /** The stored [name], the timeline for anything unknown. */
        fun fromStored(name: String?): DetailStyle = entries.firstOrNull { it.name == name } ?: Timeline
    }
}

/** One row of the stops of a ride in the detail: a stop, folded stops, or a note in place of them. */
sealed class DetailStopRow {
    abstract val key: String

    /** The stop at [index] of the ride's stops. */
    data class Stop(val index: Int) : DetailStopRow() {
        override val key: String get() = "s$index"
    }

    /** The stops [from] to [to] folded into one row ("3 weitere Halte"). */
    data class Folded(val from: Int, val to: Int) : DetailStopRow() {
        val count: Int get() = to - from + 1
        override val key: String get() = "f$from-$to"
    }

    /** In place of the stops between the ends: loading, failed, or none there. */
    data class Note(val kind: StopsNote) : DetailStopRow() {
        override val key: String get() = "n"
    }
}

/** What stands between the two ends of an unfolded ride whose stops are not shown. */
enum class StopsNote { Loading, Failed, None }

/**
 * The stop rows of a ride with [count] stops in the detail: folded, the first and the last
 * stop with the middle in one row (as a later ride in the Live-Fahrt); [expanded] all of them.
 * Where the stops are not [known] yet (the search leaves them out) only the two ends, and once
 * unfolded the note that they load or [failed] between them; a ride without stops between its
 * ends says so when unfolded.
 */
fun detailStopRows(count: Int, known: Boolean, expanded: Boolean, failed: Boolean): List<DetailStopRow> {
    if (count < 2) return (0 until count).map { DetailStopRow.Stop(it) }
    val last = count - 1
    if (!known || count == 2) {
        if (!expanded) return listOf(DetailStopRow.Stop(0), DetailStopRow.Stop(last))
        val note = when {
            known -> StopsNote.None
            failed -> StopsNote.Failed
            else -> StopsNote.Loading
        }
        return listOf(DetailStopRow.Stop(0), DetailStopRow.Note(note), DetailStopRow.Stop(last))
    }
    return journeyStopRows(count, -1f, JourneyRidePhase.Later, expanded).map { spec ->
        when (spec) {
            is StopRowSpec.Single -> DetailStopRow.Stop(spec.index)
            is StopRowSpec.Folded -> DetailStopRow.Folded(spec.from, spec.to)
        }
    }
}

/** One item of the lazy list of the detail body. [key] tells the items apart for Compose. */
sealed class DetailItem {
    abstract val key: String

    /** The notices of the whole journey (changed connection, disruptions, lifts, fare zones). */
    data object Top : DetailItem() {
        override val key: String get() = "top"
    }

    /** The stay at a place on the way, [afterLegs] legs into the journey. */
    data class Stay(val afterLegs: Int) : DetailItem() {
        override val key: String get() = "stay"
    }

    /** A walk (or bike leg) at the start or the end, or one beside a stay. */
    data class Walk(val legIndex: Int, val endsTrip: Boolean) : DetailItem() {
        override val key: String get() = "walk$legIndex"
    }

    /** What comes before a ride outside its card: the way to its stop position or platform. */
    data class RidePre(val legIndex: Int) : DetailItem() {
        override val key: String get() = "pre$legIndex"
    }

    /** The head of a ride's card: badge, direction, times; pinned while its stops scroll. */
    data class RideHead(val legIndex: Int) : DetailItem() {
        override val key: String get() = "head$legIndex"
    }

    /** The hints of a ride under its head: cancelled, replacement, occupancy, where to board. */
    data class RideInfo(val legIndex: Int) : DetailItem() {
        override val key: String get() = "info$legIndex"
    }

    /** One stop row of a ride; [first] and [last] row of its rail. */
    data class RideStop(val legIndex: Int, val row: DetailStopRow, val first: Boolean, val last: Boolean) : DetailItem() {
        override val key: String get() = "stop$legIndex:${row.key}"
    }

    /** The foot of a ride's card (the link to the line's course while unfolded). */
    data class RideFoot(val legIndex: Int) : DetailItem() {
        override val key: String get() = "foot$legIndex"
    }

    /** The change between the rides at [arrivingIndex] and [nextIndex], with the walk at [walkIndex]. */
    data class Change(val arrivingIndex: Int, val nextIndex: Int, val walkIndex: Int?) : DetailItem() {
        val keepLegs: Int get() = arrivingIndex + 1
        override val key: String get() = "change$keepLegs"
    }

    /** The note at the foot that the times are live. */
    data object Foot : DetailItem() {
        override val key: String get() = "end"
    }
}

/**
 * The items of the detail body of a journey whose legs are on foot where [walking] says so,
 * with [walkMinutes] per leg and a stay at a place on the way after [stayAfter] legs (null for
 * none). Every two rides in a row have their change, as in the classic detail. [stopRows] gives the stop rows of the
 * ride at a leg index (see [detailStopRows]). The order is that of the Live-Fahrt (see
 * journeyRows), seen before the trip: walks, every ride as head, hints, stops and foot, and
 * between two rides their change; a change across the stay is the stay (and its walk) instead.
 */
fun detailItems(
    walking: List<Boolean>,
    walkMinutes: List<Long>,
    stayAfter: Int?,
    stopRows: (Int) -> List<DetailStopRow>,
): List<DetailItem> {
    val firstRide = walking.indexOfFirst { !it }.takeIf { it >= 0 }
    val changes = walking.indices.filter { !walking[it] }.zipWithNext().mapTo(HashSet()) { (arriving, _) -> arriving + 1 }
    val rows = journeyRows(walking, walkMinutes, changes, focus = firstRide, onBoard = false)
    val stay = stayAfter?.takeIf { it > 0 }
    var stayPlaced = stay == null
    val items = mutableListOf<DetailItem>(DetailItem.Top)
    fun placeStayBefore(start: Int) {
        if (!stayPlaced && stay != null && start >= stay) {
            items += DetailItem.Stay(stay)
            stayPlaced = true
        }
    }
    for (row in rows) {
        when (row) {
            is JourneyRow.Walk -> {
                placeStayBefore(row.legIndex)
                items += DetailItem.Walk(row.legIndex, row.endsTrip)
            }
            is JourneyRow.Ride -> {
                placeStayBefore(row.legIndex)
                items += DetailItem.RidePre(row.legIndex)
                items += DetailItem.RideHead(row.legIndex)
                items += DetailItem.RideInfo(row.legIndex)
                val stops = stopRows(row.legIndex)
                stops.forEachIndexed { k, stop ->
                    items += DetailItem.RideStop(row.legIndex, stop, first = k == 0, last = k == stops.lastIndex)
                }
                items += DetailItem.RideFoot(row.legIndex)
            }
            is JourneyRow.Change -> {
                if (!stayPlaced && stay != null && stay in (row.arrivingIndex + 1)..row.nextIndex) {
                    items += DetailItem.Stay(stay)
                    stayPlaced = true
                    row.walkIndex?.let { items += DetailItem.Walk(it, endsTrip = false) }
                } else {
                    items += DetailItem.Change(row.arrivingIndex, row.nextIndex, row.walkIndex)
                }
            }
        }
    }
    if (!stayPlaced && stay != null && stay < walking.size) items += DetailItem.Stay(stay)
    items += DetailItem.Foot
    return items
}

/** How the menu of a stop of a ride opens (#186). */
enum class StopMenuOpening {
    /** A visible ⋮ at the end of the row (and a tap on the row): where the traveller gets on or off. */
    Dots,

    /**
     * No ⋮, so their times stay at the edge: the stops between. The row itself is the control,
     * a tap opens the menu and so does a long press (never a long press alone, see rowMenu).
     */
    Row,
}

/** How the menu of the stop [index] of a ride with stops up to [lastIndex] opens: the dots at both ends, else the row. */
fun stopMenuOpening(index: Int, lastIndex: Int): StopMenuOpening =
    if (index == 0 || index == lastIndex) StopMenuOpening.Dots else StopMenuOpening.Row
