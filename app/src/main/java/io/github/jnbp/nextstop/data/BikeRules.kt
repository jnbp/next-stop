package io.github.jnbp.nextstop.data

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.OffsetDateTime

/** Rough box around Hamburg (hvv core). */
fun inHamburgArea(latitude: Double, longitude: Double): Boolean = latitude in 53.35..53.75 && longitude in 9.6..10.35

/** Rough box around Munich (MVV core). */
fun inMunichArea(latitude: Double, longitude: Double): Boolean = latitude in 47.95..48.30 && longitude in 11.30..11.80

/** Whether a ride takes a bicycle along. */
enum class BikeCarriage {
    /** With a bicycle ticket, where there is room. */
    Allowed,

    /** Only with a reservation for the bicycle (ICE, IC/EC). */
    Reservation,

    /** Not at all, or not at this time. */
    NotAllowed,
}

/**
 * One rule of [BikeRules]: rides of [products] (and of the lines [lines] matches, where set) in
 * the [area] (null: everywhere) carry a bicycle as [carriage]; with [days] and [from]/[until]
 * only on those days between those times (local time of the ride).
 */
data class BikeRule(
    val name: String,
    val products: Set<String>,
    val carriage: BikeCarriage,
    val area: ((Double, Double) -> Boolean)? = null,
    val lines: Regex? = null,
    val days: Set<DayOfWeek>? = null,
    val from: LocalTime? = null,
    val until: LocalTime? = null,
) {
    fun matches(product: String, line: String?, position: Pair<Double, Double>?, time: OffsetDateTime?): Boolean {
        if (product !in products) return false
        if (lines != null && (line == null || !lines.matches(line.trim()))) return false
        if (area != null && (position == null || !area.invoke(position.first, position.second))) return false
        if (days != null || from != null || until != null) {
            val local = time ?: return false
            if (days != null && local.dayOfWeek !in days) return false
            val clock = local.toLocalTime()
            if (from != null && clock.isBefore(from)) return false
            if (until != null && !clock.isBefore(until)) return false
        }
        return true
    }
}

/**
 * Where a bicycle may come along, for the bike combo (see [SearchOptions.bikeCombo]). The first
 * rule that matches a ride counts; a ride no rule matches falls to [defaultCarriage]:
 *
 * - S-Bahn, U-Bahn, tram, ferry and regional trains carry bicycles (BVG/VBB at all times with a
 *   bicycle ticket; DB Regio nationwide), except in the rush hours of Hamburg (hvv: U-Bahn and
 *   S-Bahn not Mon–Fri 6–9 and 16–18) and Munich (MVV: S-Bahn not Mon–Fri 6–9, U-Bahn not
 *   Mon–Fri 6–9 and 16–18). Holidays and school holidays, when those bans are lifted, are not
 *   known here: the ban counts, on the safe side.
 * - Long-distance trains only with a reservation for the bicycle (ICE, IC/EC).
 * - Coaches (FlixBus …) not: only some routes with a booked place.
 * - Buses not, by default: in Berlin only the night lines N1–N9 that stand in for the U-Bahn
 *   at night (BVG: "if there is room").
 */
object BikeRules {
    private val WEEKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

    // The hours a night bus of the BVG may stand for the U-Bahn; only then is a bus worth asking for.
    private val BVG_NIGHT_FROM: LocalTime = LocalTime.of(0, 0)
    private val BVG_NIGHT_UNTIL: LocalTime = LocalTime.of(5, 30)

    val rules: List<BikeRule> = listOf(
        BikeRule("hvv rush morning", setOf("suburban", "subway"), BikeCarriage.NotAllowed, ::inHamburgArea,
            days = WEEKDAYS, from = LocalTime.of(6, 0), until = LocalTime.of(9, 0)),
        BikeRule("hvv rush evening", setOf("suburban", "subway"), BikeCarriage.NotAllowed, ::inHamburgArea,
            days = WEEKDAYS, from = LocalTime.of(16, 0), until = LocalTime.of(18, 0)),
        BikeRule("MVV rush morning", setOf("suburban", "subway"), BikeCarriage.NotAllowed, ::inMunichArea,
            days = WEEKDAYS, from = LocalTime.of(6, 0), until = LocalTime.of(9, 0)),
        BikeRule("MVV U-Bahn rush evening", setOf("subway"), BikeCarriage.NotAllowed, ::inMunichArea,
            days = WEEKDAYS, from = LocalTime.of(16, 0), until = LocalTime.of(18, 0)),
        BikeRule("BVG night lines N1-N9", setOf("bus"), BikeCarriage.Allowed, ::inVbbArea, lines = Regex("N[1-9]")),
    )

    /** What a ride of [product] carries where no rule matches. */
    fun defaultCarriage(product: String): BikeCarriage = when (product) {
        "suburban", "subway", "tram", "ferry", "regional" -> BikeCarriage.Allowed
        "express" -> BikeCarriage.Reservation
        // Buses, coaches and what the app has no product for: not, on the safe side.
        else -> BikeCarriage.NotAllowed
    }

    /** Whether a ride of [product] on [line], boarded at [position] at [time], takes a bicycle. */
    fun carriage(product: String, line: String?, position: Pair<Double, Double>?, time: OffsetDateTime?): BikeCarriage =
        rules.firstOrNull { it.matches(product, line, position, time) }?.carriage ?: defaultCarriage(product)

    fun carriage(ride: Leg): BikeCarriage =
        carriage(ride.product, ride.line, ride.fromPlace?.position ?: ride.path.firstOrNull(), ride.plannedDeparture ?: ride.departure)

    /** True when every ride of [journey] takes the bicycle along (a reservation counts). */
    fun journeyCarriesBike(journey: Journey): Boolean = journey.rides.all { carriage(it) != BikeCarriage.NotAllowed }

    /**
     * True when a bus may carry the bicycle around [position] at [time]: then a search of the bike
     * combo asks for buses too and keeps those of them that do (see [journeyCarriesBike]);
     * otherwise buses are left out of it and their stretches are cycled.
     */
    fun busesWorthAsking(position: Pair<Double, Double>?, time: OffsetDateTime?): Boolean {
        if (position == null || !inVbbArea(position.first, position.second)) return false
        val clock = (time ?: OffsetDateTime.now()).toLocalTime()
        return !clock.isBefore(BVG_NIGHT_FROM) && clock.isBefore(BVG_NIGHT_UNTIL)
    }

    /**
     * The products a search of the bike combo asks for, of the chosen [products]: without
     * coaches, and without buses unless [buses] (see [busesWorthAsking]).
     */
    fun comboProducts(products: Set<String>, buses: Boolean): Set<String> =
        (products - "coach" - (if (buses) emptySet() else setOf("bus")))
            // Only buses and coaches chosen: the trains that carry a bicycle instead.
            .ifEmpty { setOf("suburban", "subway", "tram", "regional") }
}
