package io.github.jnbp.nextstop.wear.data

/*
 * The rules of a departures board, the same as in the phone app (data/Models.kt,
 * data/DepartureBoard.kt and data/ChangePositions.kt there; the watch cannot use the code of
 * the phone module): line names given by their number alone, replacement runs and runs shown
 * twice, cancelled runs at their planned time. Plain Kotlin, tested on the JVM (DepartureBoardTest); keep both
 * sides alike when one changes.
 */

/**
 * The name of an S- or U-Bahn line that the provider names by its number alone, with its
 * letter in front: an additional S1 train comes from BVG HAFAS as line "1" with product
 * "suburban" and is "S1" here, so it gets the name and colour of the S1. Other names stay.
 */
fun rapidLineName(name: String?, product: String): String? {
    val plain = name?.trim() ?: return null
    val letter = when (product) {
        "suburban" -> "S"
        "subway" -> "U"
        else -> return plain
    }
    return if (plain.length in 1..2 && plain.all { it.isDigit() }) "$letter$plain" else plain
}

/** The remark code of HAFAS for a run that is not in the timetable but added ("Zusatzfahrt"). */
const val ADDITIONAL_SERVICE_CODE = "text.realtime.journey.additional.service"

/** How far apart the planned times of a cancelled run and the extra run that replaces it may be. */
const val REPLACEMENT_WINDOW_MINUTES = 3L

/**
 * A board where a cancelled run and an additional run of the same line, the same way and
 * platform leave at the same planned time (within [REPLACEMENT_WINDOW_MINUTES]) shows one
 * row: the additional run, marked as standing in for the cancelled one, which is left out.
 * BVG HAFAS announces a replacement S1 that way: the S1 "fällt aus" and an extra "1" leaves
 * from the same platform at the same minute. An additional run that doubles a regular run
 * which still goes (same line, way, platform and planned minute) is one train shown twice:
 * only the regular run stays. Other runs stay as they are, in their order.
 */
fun mergeReplacements(departures: List<Departure>): List<Departure> {
    if (departures.none { it.additional }) return departures
    val replaced = mutableSetOf<Int>()
    val merged = departures.mapIndexed { index, departure ->
        if (!departure.additional || departure.cancelled) return@mapIndexed departure
        val match = departures.indices.firstOrNull { other ->
            other != index && other !in replaced && replaces(departure, departures[other])
        } ?: return@mapIndexed departure
        replaced += match
        departure.copy(replaces = departures[match].line)
    }
    val doubled = merged.indices.filter { index ->
        val extra = merged[index]
        index !in replaced && extra.additional && !extra.cancelled && extra.replaces == null &&
            merged.indices.any { other -> other != index && other !in replaced && doubles(extra, merged[other]) }
    }.toSet()
    return merged.filterIndexed { index, _ -> index !in replaced && index !in doubled }
}

/** True when [extra] (an additional run) stands in for [regular], a cancelled run. */
private fun replaces(extra: Departure, regular: Departure): Boolean {
    if (!regular.cancelled || regular.additional) return false
    return sameRun(extra, regular, REPLACEMENT_WINDOW_MINUTES)
}

/** True when [extra] (an additional run that goes) is the same train as [regular], a regular run that goes too. */
private fun doubles(extra: Departure, regular: Departure): Boolean {
    if (regular.cancelled || regular.additional) return false
    // Two trains can follow each other within minutes; the very same minute is one train.
    return sameRun(extra, regular, windowMinutes = 0)
}

/** Same line, way, platform and planned minute (within [windowMinutes]); the watch shows one stop at a time. */
private fun sameRun(extra: Departure, regular: Departure, windowMinutes: Long): Boolean {
    if (!extra.line.equals(regular.line, ignoreCase = true) || extra.product != regular.product) return false
    if (!extra.direction.trim().equals(regular.direction.trim(), ignoreCase = true)) return false
    if (extra.platform?.trim().orEmpty() != regular.platform?.trim().orEmpty()) return false
    val a = (extra.plannedTime ?: extra.time)?.div(60_000) ?: return false
    val b = (regular.plannedTime ?: regular.time)?.div(60_000) ?: return false
    return kotlin.math.abs(a - b) <= windowMinutes
}

/**
 * The time a departure takes its place in a board by: the live time of a run that goes, the
 * planned time of a cancelled one. BVG HAFAS sends a cancelled run without a live time, and
 * its answer sorts such runs by an empty time, so they would come first.
 */
fun departureOrderTime(departure: Departure): Long? =
    if (departure.cancelled) departure.plannedTime ?: departure.time else departure.time ?: departure.plannedTime

/** [departures] in the order they leave (see [departureOrderTime]); those without any time at the end. */
fun inDepartureOrder(departures: List<Departure>): List<Departure> =
    departures.sortedWith(compareBy(nullsLast()) { departure: Departure -> departureOrderTime(departure) })

/**
 * [departures] with each ride once: two runs of the same line (as [normalLine] gives it, after
 * the provider's name was completed, see [rapidLineName]) towards the same place (as
 * [stationNameKey] gives it) at the same planned minute from the same platform, or where one
 * of them names no platform, are one ride. BVG HAFAS can send an S1 and an extra "1" that way,
 * also without the remark that marks the extra run. The run kept is the one that goes, then
 * the regular one (not additional), then the one with live data; it keeps its own place, the
 * others are left out. As withoutDoubleRides on the phone.
 */
fun withoutDoubleRides(departures: List<Departure>): List<Departure> {
    val dropped = mutableSetOf<Int>()
    departures.indices.forEach { index ->
        if (index in dropped) return@forEach
        val group = departures.indices.filter { other -> other !in dropped && (other == index || oneRide(departures[index], departures[other])) }
        if (group.size < 2) return@forEach
        val keep = group.minWith(compareBy<Int>({ rideRank(departures[it]) }, { it }))
        dropped += group - keep
    }
    return departures.filterIndexed { index, _ -> index !in dropped }
}

/** Lower is better: a run that goes, then a regular one, then one with live data. */
private fun rideRank(departure: Departure): Int =
    (if (departure.cancelled) 4 else 0) + (if (departure.additional) 2 else 0) +
        (if (departure.time != null && departure.delayMinutes != null) 0 else 1)

/** True when two runs of one board are one ride (see [withoutDoubleRides]). */
internal fun oneRide(a: Departure, b: Departure): Boolean {
    if (normalLine(a.line) != normalLine(b.line)) return false
    val one = stationNameKey(a.direction)
    if (one.isEmpty() || one != stationNameKey(b.direction)) return false
    val first = (a.plannedTime ?: a.time)?.div(60_000) ?: return false
    val second = (b.plannedTime ?: b.time)?.div(60_000) ?: return false
    if (first != second) return false
    val x = a.platform?.trim().orEmpty()
    val y = b.platform?.trim().orEmpty()
    return x.isEmpty() || y.isEmpty() || x.equals(y, ignoreCase = true)
}

/** A line name in one form: no spaces, upper case, without a word for the product in front ("S 2", "Bus M41"). */
fun normalLine(line: String): String {
    val plain = line.trim().replace(Regex("\\s+"), " ")
    val word = plain.substringBefore(' ', "")
    val rest = if (word.lowercase() in PRODUCT_WORDS && plain.length > word.length) plain.substring(word.length + 1) else plain
    return rest.replace(" ", "").uppercase()
}

private val PRODUCT_WORDS = setOf("bus", "tram", "str", "u-bahn", "s-bahn", "ubahn", "sbahn", "metro", "fähre", "ferry")

/**
 * A station name in one form to compare names: lower case, without "S+U", "S", "U" in front,
 * the lines and places in brackets or behind, and punctuation (as on the phone).
 */
fun stationNameKey(name: String): String {
    var text = name.lowercase()
        .replace(NAME_BRACKETS, " ")
        .replace("straße", "str").replace("strasse", "str")
        .replace(NAME_SEPARATORS, " ")
        .trim()
    text = text.replace(NAME_RAPID_PREFIX, "")
    // Lines named behind the station: "... s2 u7".
    text = text.replace(NAME_LINE_SUFFIX, "")
    text = text.removePrefix("berlin ").removeSuffix(" berlin").removeSuffix(" bhf").trim()
    return text.replace(" ", "")
}

private val NAME_BRACKETS = Regex("\\(.*?\\)")
private val NAME_SEPARATORS = Regex("[^a-z0-9äöüß]+")
private val NAME_RAPID_PREFIX = Regex("^(s u|s|u)\\s+")
private val NAME_LINE_SUFFIX = Regex("(\\s+(s|u|re|rb)\\d+)+$")

/**
 * The board as the watch shows it: replacements merged, each ride once (also without the
 * remark of an extra run), in the order they leave.
 */
fun departureBoard(departures: List<Departure>): List<Departure> =
    inDepartureOrder(withoutDoubleRides(mergeReplacements(departures)))
