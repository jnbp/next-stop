package io.github.jnbp.nextstop.data

import java.time.Duration
import java.time.OffsetDateTime
import io.github.jnbp.nextstop.ui.shortLine

/**
 * A connection the user remembered: start, destination and the order of its lines, as
 * "M27 → U9", with an optional name of its own. Tapping it shows the next journeys that ride
 * exactly these lines.
 */
data class SavedPattern(val from: Stop, val to: Stop, val lines: List<String>, val name: String = "") {
    val id: String get() = routeId(from, to) + "#" + lines.joinToString(">") { lineKey(it) }

    /** Its own name, else its lines: "M27 → U9". */
    val label: String get() = name.ifBlank { linesLabel(lines) }
}

/** The lines as one text: "M27 → U9". */
fun linesLabel(lines: List<String>): String = lines.joinToString(" → ")

/** The lines of the rides of [journey] in their order, by the names their badges show. */
fun patternLines(journey: Journey): List<String> = journey.rides.map { shortLine(it.line) ?: it.product }

/** A line as it is compared: the badge name in capitals without blanks ("S 41" and "S41" agree). */
fun lineKey(line: String): String =
    (shortLine(line) ?: line).replace(KIND_WORD, "").uppercase().replace(" ", "")

// A word for the kind of vehicle in front of a line name, which some providers add: "Tram M27".
private val KIND_WORD = Regex("^(tram|str|bus)\\s+(?=\\S)", RegexOption.IGNORE_CASE)

/** True when [journey] rides exactly [lines], in this order and with no other ride. */
fun matchesPattern(journey: Journey, lines: List<String>): Boolean {
    val rides = patternLines(journey)
    return rides.size == lines.size && rides.zip(lines).all { (ride, line) -> lineKey(ride) == lineKey(line) }
}

/** A remembered connection counts as running when it leaves within this many minutes. */
const val PATTERN_WINDOW_MINUTES = 90L

/** Another journey is pointed out when it arrives at least this many minutes sooner. */
const val PATTERN_FASTER_MINUTES = 5L

/**
 * What a search shows for a remembered connection: [matching] are the journeys that ride its
 * lines; [running] is true when one of them leaves within [PATTERN_WINDOW_MINUTES]; then
 * [fasterMinutes] says how much sooner another journey arrives than the next matching one,
 * when that is at least [PATTERN_FASTER_MINUTES].
 */
class PatternView(val matching: List<Journey>, val running: Boolean, val fasterMinutes: Long?)

/** Sorts [journeys] for the remembered [lines] from the time [from] on; null without journeys or lines. */
fun patternView(journeys: List<Journey>?, lines: List<String>?, from: OffsetDateTime): PatternView? {
    if (journeys == null || lines.isNullOrEmpty()) return null
    val matching = journeys.filter { matchesPattern(it, lines) }
    val end = from.plusMinutes(PATTERN_WINDOW_MINUTES)
    val next = matching
        .filter { !it.cancelled && !it.departure.isBefore(from) && !it.departure.isAfter(end) }
        .minByOrNull { it.departure }
    val faster = next?.let { best ->
        journeys
            .filter { !it.cancelled && !it.departure.isBefore(from) && !matchesPattern(it, lines) }
            .minByOrNull { it.arrival }
            ?.let { Duration.between(it.arrival, best.arrival).toMinutes() }
            ?.takeIf { it >= PATTERN_FASTER_MINUTES }
    }
    return PatternView(matching, next != null, faster)
}

/** The next journey of [journeys] that rides [lines] and has not left before [from]; null when none does. */
fun nextOfPattern(journeys: List<Journey>, lines: List<String>, from: OffsetDateTime): Journey? =
    journeys.filter { !it.cancelled && !it.departure.isBefore(from) && matchesPattern(it, lines) }.minByOrNull { it.departure }
