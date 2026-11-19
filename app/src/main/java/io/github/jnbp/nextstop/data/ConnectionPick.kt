package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime

/*
 * The choice of connections at a change of a followed trip (ui/Companion.kt ConnectionChips).
 * #94: the search from the change does not always find the connection the trip follows (the
 * FEX at Berlin Hbf, the search found S-Bahn ways), so it was missing from the choice. #97: with
 * the current choice missing and every other one out of reach, the row was empty. #98: when a
 * delay makes the chosen connection unreachable, the best one that still works is offered.
 */

/**
 * Two rides are the same choice (the same run of a line at the change) when their lines agree
 * in one form ("STR 3" and "3", see [normalLine]) and they leave in the same minute: by the
 * timetable where both know it, else by the time each leaves (#275). Times are compared as
 * instants, so two providers that write them with another offset ("+01:00", "Z") or seconds
 * agree; before, the same tram from the search and from the followed journey were two tiles.
 */
fun sameChoiceRide(a: Leg, b: Leg): Boolean {
    if (choiceLine(a) != choiceLine(b)) return false
    val bothPlanned = a.plannedDeparture != null && b.plannedDeparture != null
    val first = if (bothPlanned) a.plannedDeparture!! else a.plannedDeparture ?: a.departure
    val second = if (bothPlanned) b.plannedDeparture!! else b.plannedDeparture ?: b.departure
    if (sameMinute(first, second)) return true
    // One of them without a timetable: the time it leaves at stands for it on both sides.
    return !bothPlanned && sameMinute(a.departure, b.departure)
}

private fun choiceLine(leg: Leg): String = normalLine(leg.timetableLine ?: leg.line ?: "")

private fun sameMinute(a: OffsetDateTime, b: OffsetDateTime): Boolean =
    a.toEpochSecond() / 60 == b.toEpochSecond() / 60

/**
 * [options] with every run once (see [sameChoiceRide]): of the ways on after one departure the
 * first in the given order stands for it. A run found by the search and the same run of the
 * followed journey make one tile (#275).
 */
fun distinctChoices(options: List<Journey>): List<Journey> {
    val kept = ArrayList<Journey>(options.size)
    for (option in options) {
        val ride = option.rides.firstOrNull()
        if (ride == null || kept.none { other -> other.rides.firstOrNull()?.let { sameChoiceRide(it, ride) } == true }) kept.add(option)
    }
    return kept
}

/**
 * [options] with the connection the trip follows now ([current], whose first ride is [next])
 * among them: added where the search did not find it, in the order of [optionOrder]; never
 * twice. Without [current] the options as they are.
 */
fun withCurrentChoice(options: List<Journey>, current: Journey?, next: Leg): List<Journey> {
    if (current == null || current.rides.isEmpty()) return options
    if (options.any { option -> option.rides.firstOrNull()?.let { sameChoiceRide(it, next) } == true }) return options
    return (options + current).sortedWith(optionOrder)
}

/**
 * What the choice marks: [earliest] the index of the earliest departure that can be reached
 * without running (not [Tightness.VeryRisky]), [sprintEarlier] that of an earlier one that only
 * works at a run (Sprint); null where there is none.
 */
data class ChoiceMarks(val earliest: Int?, val sprintEarlier: Int?)

/** The [ChoiceMarks] of options with the change [times] and the [departures] of their first rides, index for index. */
fun choiceMarks(times: List<ChangeTime>, departures: List<OffsetDateTime>): ChoiceMarks {
    val reachable = times.indices.filter { times[it].possible && it < departures.size }
    val earliest = reachable.filter { times[it].tightness != Tightness.VeryRisky }.minByOrNull { departures[it] }
    val sprint = reachable
        .filter { times[it].tightness == Tightness.VeryRisky }
        .filter { earliest == null || departures[it].isBefore(departures[earliest]) }
        .minByOrNull { departures[it] }
    return ChoiceMarks(earliest, sprint)
}

/**
 * The option to offer in place of the [chosen] one (an index of [times]) when that can no
 * longer be reached: the first in the order of the options (by arrival, see [optionOrder]) that
 * works without running, else the first that works at a run; null while the chosen one works or
 * nothing does.
 */
fun rescueChoice(chosen: Int, times: List<ChangeTime>): Int? {
    if (chosen in times.indices && times[chosen].possible) return null
    val reachable = times.indices.filter { it != chosen && times[it].possible }
    return reachable.firstOrNull { times[it].tightness != Tightness.VeryRisky } ?: reachable.firstOrNull()
}

/**
 * Which broken changes were told about (#98): the notification "Anschluss klappt nicht mehr"
 * comes once per change of a followed trip, not with every refresh of the offers.
 */
class RescueNotices {
    private val told = HashSet<String>()

    /** True the first time for the change keeping [keepLegs] legs of the journey [journeyId]. */
    @Synchronized
    fun firstTime(journeyId: String, keepLegs: Int): Boolean = told.add("$journeyId|$keepLegs")

    /** Forgets them all (the trip ended or changed). */
    @Synchronized
    fun clear() = told.clear()
}

/**
 * The name of a rescue offer for the watch: the followed journey [journeyId], the change keeping
 * [keepLegs] of its legs and the offered connection [optionId]. The watch sends it back to
 * switch; the phone switches only while its offer of the moment has the same name.
 */
fun rescueKey(journeyId: String, keepLegs: Int, optionId: String): String = "$journeyId#$keepLegs#$optionId"

/**
 * The order in which the choice shows its tiles (#215): strictly by the [departures] at the
 * change, the earliest first, the chosen one at its place also when it can no longer be reached
 * ("zu spät"). Before, the tiles went by the minutes at the change with the unreachable ones last
 * (#118), which put a chosen connection that a delay made unreachable at the far right. Equal
 * departures keep the order of the options (by arrival, see [optionOrder]). Returns indices of
 * [departures].
 */
fun tileOrder(departures: List<OffsetDateTime>): List<Int> =
    departures.indices.sortedWith(compareBy<Int> { departures[it].toInstant() }.thenBy { it })

/** A line name in one form: no spaces, upper case, without a word for the product in front ("S 2", "Bus M41"). */
fun normalLine(line: String): String {
    val plain = line.trim().replace(Regex("\\s+"), " ")
    val word = plain.substringBefore(' ', "")
    val rest = if (word.lowercase() in PRODUCT_WORDS && plain.length > word.length) plain.substring(word.length + 1) else plain
    return rest.replace(" ", "").uppercase()
}

private val PRODUCT_WORDS = setOf("bus", "tram", "str", "u-bahn", "s-bahn", "ubahn", "sbahn", "metro", "fähre", "ferry")
