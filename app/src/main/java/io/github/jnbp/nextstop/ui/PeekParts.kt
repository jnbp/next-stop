package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.Leg
import java.time.Duration

/**
 * One part of a connection in its peek card (see ConnectionPeek): a ride ([ride] set) or the
 * time on foot between two rides, or before the first and after the last ([walkMinutes], by
 * bike when [cycling]).
 */
internal class PeekPart(val ride: Leg?, val walkMinutes: Long, val cycling: Boolean)

/**
 * The parts of [journey] in their order, for its peek card: every ride, and the walks between
 * them joined into one with their minutes added. Walks of no whole minute (a change on the
 * same platform) are left out.
 */
internal fun peekParts(journey: Journey): List<PeekPart> {
    val parts = ArrayList<PeekPart>(journey.legs.size)
    var walk = 0L
    var cycling = false
    fun flushWalk() {
        if (walk > 0) parts += PeekPart(null, walk, cycling)
        walk = 0L
        cycling = false
    }
    for (leg in journey.legs) {
        if (leg.walking) {
            walk += Duration.between(leg.departure, leg.arrival).toMinutes().coerceAtLeast(0)
            cycling = cycling || leg.cycling
        } else {
            flushWalk()
            parts += PeekPart(leg, 0L, false)
        }
    }
    flushWalk()
    return parts
}

/** One row of the peek card's list, top to bottom (see [peekRows]). */
internal sealed class PeekRow {
    /** A ride ([leg]) and how many minutes it takes. */
    class Ride(val leg: Leg, val minutes: Long) : PeekRow()

    /** On foot (by bike when [cycling]) before the first or after the last ride. */
    class Walk(val walkMinutes: Long, val cycling: Boolean) : PeekRow()

    /** A change between two rides: [minutes] from arriving to leaving, [walkMinutes] of them on foot. */
    class Change(val minutes: Long, val walkMinutes: Long, val cycling: Boolean) : PeekRow()
}

/**
 * The rows of [journey] for the vertical list of its peek card: every ride, a change between
 * two rides (also one on the same platform, with no walk), and the walk before the first and
 * after the last ride where it takes a whole minute. A ride that goes on in the same vehicle
 * (stayOnBoard) has no change before it.
 */
internal fun peekRows(journey: Journey): List<PeekRow> {
    val rows = ArrayList<PeekRow>(journey.legs.size * 2)
    var walk = 0L
    var cycling = false
    var lastRide: Leg? = null
    for (leg in journey.legs) {
        if (leg.walking) {
            walk += Duration.between(leg.departure, leg.arrival).toMinutes().coerceAtLeast(0)
            cycling = cycling || leg.cycling
            continue
        }
        val before = lastRide
        if (before == null) {
            if (walk > 0) rows += PeekRow.Walk(walk, cycling)
        } else if (!leg.stayOnBoard) {
            rows += PeekRow.Change(Duration.between(before.arrival, leg.departure).toMinutes().coerceAtLeast(0), walk, cycling)
        }
        walk = 0L
        cycling = false
        rows += PeekRow.Ride(leg, Duration.between(leg.departure, leg.arrival).toMinutes().coerceAtLeast(0))
        lastRide = leg
    }
    if (walk > 0) rows += PeekRow.Walk(walk, cycling)
    return rows
}
