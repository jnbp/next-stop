package io.github.jnbp.nextstop.data

import java.time.Duration
import java.time.OffsetDateTime
import kotlin.math.abs

/**
 * "Schnellere Wege melden" for a followed trip, one control with three states: [Off], from the
 * user's threshold ([FromThreshold], "ab 3 min", the shortcut alert at the user's change level)
 * and [EveryMinute] ("Jede Minute zählt": Sprint changes, from one minute on).
 */
enum class ShortcutMode {
    Off,
    FromThreshold,
    EveryMinute,
    ;

    /** True when the search on the way runs at all. */
    val alert: Boolean get() = this != Off

    /** True for "Jede Minute zählt". */
    val everyMinute: Boolean get() = this == EveryMinute
}

/** The state of the control from the two switches of a trip ("Jede Minute zählt" wins). */
fun shortcutMode(everyMinute: Boolean, alert: Boolean): ShortcutMode = when {
    everyMinute -> ShortcutMode.EveryMinute
    alert -> ShortcutMode.FromThreshold
    else -> ShortcutMode.Off
}

/** How many minutes sooner a way must arrive before the shortcut alert speaks up, by default. */
const val DEFAULT_SHORTCUT_MINUTES = 3

/** The thresholds offered in the settings. */
val SHORTCUT_MINUTE_CHOICES: List<Int> = listOf(2, 3, 5, 10)

/** A way the user put aside comes back only once it wins this much more than it did then. */
const val IGNORED_BETTER_SECONDS = 120L

/** The least gain an offer needs: a minute with "Jede Minute zählt", else the user's [minutes]. */
fun shortcutThresholdSeconds(everyMinute: Boolean, minutes: Int): Long =
    if (everyMinute) MIN_GAIN_SECONDS else minutes.coerceAtLeast(1) * 60L

/** What the traveller does differently, now or at a later stop, to take a faster way. */
enum class ShortcutActionKind {
    /** Leave the own vehicle at [ShortcutAction.at], where the plan does not change, and take [ShortcutAction.line]. */
    GetOffAndTake,

    /** At [ShortcutAction.at], where the plan changes anyway, take [ShortcutAction.line] instead of [ShortcutAction.instead]. */
    ChangeInstead,

    /** Still waiting: take [ShortcutAction.line] from [ShortcutAction.at] instead of [ShortcutAction.instead]. */
    TakeInstead,

    /** Stay on [ShortcutAction.line] beyond [ShortcutAction.at] (where the plan changes) up to [ShortcutAction.until]. */
    StaySeated,

    /** Get off at [ShortcutAction.at] and go on foot. */
    GetOffAndWalk,
}

/**
 * The one thing a faster way asks the traveller to do differently (see [shortcutAction]):
 * [kind] at the stop [at]; [line] is the ride to take there (for [ShortcutActionKind.StaySeated]
 * the ride to stay on) and [time] when it leaves there (for staying seated: when it arrives at
 * [until]); [instead] the line of the plan given up, where there is one. [key] names the action
 * the same way in every search, for putting it aside.
 */
data class ShortcutAction(
    val kind: ShortcutActionKind,
    val at: String,
    val line: String?,
    val time: OffsetDateTime?,
    val instead: String? = null,
    val until: String? = null,
    val key: String,
)

/**
 * When the own vehicle [own] (a ride of the followed journey) is at the stop [place] ([name]) by
 * its current realtime: its departure where it starts, else its arrival on the way or at its end.
 * Null where it does not call there. The single place the shortcut alert asks this: a
 * position-based estimate of the ride can take over here.
 */
fun currentArrivalAt(own: Leg, place: Stop?, name: String): OffsetDateTime? {
    val stops = own.stopovers
    for (index in 1 until stops.size) {
        val stop = stops[index]
        if (sameStation(stop.place, stop.name, place, name)) return stop.arrival ?: stop.departure
    }
    if (sameStation(own.toPlace, own.to, place, name)) return own.arrival
    if (sameStation(own.fromPlace, own.from, place, name)) return own.departure
    return null
}

// Two rides with the same line are one vehicle where their planned times at common stops differ by at most this.
private const val SAME_RIDE_SECONDS = 120L

/**
 * True when [a] and [b] are the same vehicle: a common run handle, or the same line with the
 * same planned times (within two minutes) at every stop of the two that both name, of the ends
 * of either ride (a vehicle of the other direction differs at the second stop).
 */
fun sameVehicle(a: Leg, b: Leg): Boolean {
    if (a.walking || b.walking) return false
    val idsA = a.tripIds
    if (idsA.isNotEmpty() && b.tripIds.any { it in idsA }) return true
    val lineA = a.line ?: return false
    val lineB = b.line ?: return false
    if (normalLine(lineA) != normalLine(lineB)) return false
    val stations = listOf(b.fromPlace to b.from, b.toPlace to b.to, a.fromPlace to a.from, a.toPlace to a.to)
    var matched = false
    for ((place, name) in stations) {
        val first = plannedAt(a, place, name) ?: continue
        val second = plannedAt(b, place, name) ?: continue
        if (abs(Duration.between(first, second).seconds) > SAME_RIDE_SECONDS) return false
        matched = true
    }
    return matched
}

/** The planned time of [leg] at a stop: its departure where it starts, else its arrival; null where it does not call. */
private fun plannedAt(leg: Leg, place: Stop?, name: String): OffsetDateTime? {
    if (sameStation(leg.fromPlace, leg.from, place, name)) return leg.plannedDeparture ?: leg.departure
    if (sameStation(leg.toPlace, leg.to, place, name)) return leg.plannedArrival ?: leg.arrival
    val stop = leg.stopovers.firstOrNull { sameStation(it.place, it.name, place, name) } ?: return null
    stop.plannedDeparture?.let { return it }
    stop.plannedArrival?.let { return it }
    val live = stop.time ?: return null
    return stop.delaySeconds?.let { live.minusSeconds(it.toLong()) } ?: live
}

/**
 * The rides of [current] the traveller is on or still boards from [point] on: on board of a ride
 * cut at [point], that whole ride first (the own vehicle).
 */
fun ownRidesFrom(current: Journey, point: ReplanPoint): List<Leg> {
    val from = if (point.onBoard && point.cut) point.kept.size - 1 else point.kept.size
    return current.legs.drop(from.coerceAtLeast(0)).filter { !it.walking }
}

/**
 * What [option], found from [point], asks the traveller of [current] to do differently, or null
 * when it asks nothing: the same vehicles to the same stop, only found again under another label
 * (another handle, other live data). Rides of [option] that are rides of [current] (see
 * [sameVehicle]) are followed as planned; the first that differs is the action.
 */
fun shortcutAction(current: Journey, point: ReplanPoint, option: Journey): ShortcutAction? {
    val own = ownRidesFrom(current, point)
    val rides = option.rides
    if (rides.isEmpty()) return null
    val boardCut = point.onBoard && point.cut
    for ((index, ride) in rides.withIndex()) {
        val planned = own.getOrNull(index)
        if (planned != null && sameVehicle(planned, ride)) continue
        val key = actionKeyOf(ride)
        if (index == 0) {
            return when {
                // On board: leave the own vehicle at the next stop (a walk may follow) for another one.
                boardCut -> ShortcutAction(ShortcutActionKind.GetOffAndTake, point.place.name, ride.line, ride.departure, key = "off:$key")
                // At the end of the own ride the plan changes anyway.
                point.onBoard -> ShortcutAction(ShortcutActionKind.ChangeInstead, point.place.name, ride.line, ride.departure, planned?.line, key = "change:$key")
                // Still waiting: another ride from here (or from a stop a walk away).
                else -> ShortcutAction(ShortcutActionKind.TakeInstead, ride.from, ride.line, ride.departure, planned?.line, key = "take:$key")
            }
        }
        val before = rides[index - 1]
        val plannedBefore = own[index - 1]
        val changesThereAnyway = sameStation(before.toPlace, before.to, plannedBefore.toPlace, plannedBefore.to)
        return if (changesThereAnyway) {
            ShortcutAction(ShortcutActionKind.ChangeInstead, before.to, ride.line, ride.departure, planned?.line, key = "change:$key")
        } else {
            ShortcutAction(ShortcutActionKind.GetOffAndTake, before.to, ride.line, ride.departure, key = "off:$key")
        }
    }
    // Every ride of the option is a ride of the plan: only where the last one is left can differ.
    val last = rides.last()
    val plannedLast = own.getOrNull(rides.lastIndex) ?: return null
    val sameEnd = sameStation(last.toPlace, last.to, plannedLast.toPlace, plannedLast.to)
    if (sameEnd && rides.size == own.size) return null
    val stopKey = stationNameKey(last.toPlace?.name ?: last.to)
    // The option's ride passes the stop where the plan leaves it: it stays on board beyond it.
    val passes = last.stopovers.dropLast(1).any { sameStation(it.place, it.name, plannedLast.toPlace, plannedLast.to) }
    return if (!sameEnd && (passes || last.arrival.isAfter(plannedLast.arrival))) {
        ShortcutAction(
            ShortcutActionKind.StaySeated,
            plannedLast.to,
            last.line,
            last.arrival,
            until = last.to,
            key = "stay:${actionKeyOf(last)}>$stopKey",
        )
    } else {
        ShortcutAction(ShortcutActionKind.GetOffAndWalk, last.to, null, last.arrival, key = "walk:${actionKeyOf(last)}>$stopKey")
    }
}

/** A ride the same in every search: line, planned departure and where it leaves. */
private fun actionKeyOf(ride: Leg): String =
    "${ride.line?.let { normalLine(it) } ?: "?"}@${ride.plannedDeparture ?: ride.departure}:${stationNameKey(ride.fromPlace?.name ?: ride.from)}"

/**
 * [option] on the own vehicle's current realtime: a ride of it that is a ride of [current]
 * ([ownRidesFrom], [sameVehicle]) arrives no earlier than that ride does now
 * ([currentArrivalAt]): it keeps the lag of the search behind the followed journey at the last
 * stop both know (see [ownLagSeconds]). A walk right after moves along. A search whose
 * live data lag behind the followed journey so never makes the own train look faster.
 */
fun onOwnBasis(current: Journey, point: ReplanPoint, option: Journey): Journey {
    val own = ownRidesFrom(current, point)
    if (own.isEmpty()) return option
    var shift = 0L
    var changed = false
    val legs = option.legs.map { leg ->
        if (leg.walking) {
            if (shift > 0) {
                changed = true
                leg.copy(departure = leg.departure.plusSeconds(shift), arrival = leg.arrival.plusSeconds(shift))
            } else {
                leg
            }
        } else {
            shift = 0L
            val match = own.firstOrNull { sameVehicle(it, leg) }
            if (match == null) {
                leg
            } else {
                val late = ownLagSeconds(match, leg)
                if (late > 0) {
                    shift = late
                    changed = true
                    leg.copy(arrival = leg.arrival.plusSeconds(late))
                } else {
                    leg
                }
            }
        }
    }
    return if (changed) option.copy(legs = legs) else option
}

/**
 * [point] with the ride on board arriving there as the own vehicle does now
 * ([currentArrivalAt] of the ride of [current]), where that is later than it was cut with.
 */
fun pointOnOwnBasis(current: Journey, point: ReplanPoint): ReplanPoint {
    if (!point.onBoard) return point
    val last = point.kept.lastOrNull() ?: return point
    val whole = current.legs.getOrNull(point.kept.size - 1)?.takeIf { !it.walking } ?: return point
    val now = currentArrivalAt(whole, point.place, point.place.name) ?: return point
    if (!now.isAfter(last.arrival)) return point
    return point.copy(kept = point.kept.dropLast(1) + last.copy(arrival = now))
}

/**
 * How many seconds the search's [found] ride (the same vehicle as [own]) is behind the own
 * vehicle's current realtime ([currentArrivalAt]), at the last stop of [found] that [own] knows:
 * where it is left, else on the way, else where it is boarded. Negative where the search is
 * ahead; zero where they share no stop.
 */
fun ownLagSeconds(own: Leg, found: Leg): Long {
    currentArrivalAt(own, found.toPlace, found.to)?.let { return Duration.between(found.arrival, it).seconds }
    for (index in found.stopovers.indices.reversed()) {
        val stop = found.stopovers[index]
        val time = stop.arrival ?: stop.departure ?: continue
        currentArrivalAt(own, stop.place, stop.name)?.let { return Duration.between(time, it).seconds }
    }
    currentArrivalAt(own, found.fromPlace, found.from)?.let { return Duration.between(found.departure, it).seconds }
    return 0L
}
