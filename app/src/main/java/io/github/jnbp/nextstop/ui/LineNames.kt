package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.lineKind
import io.github.jnbp.nextstop.data.trainKindOf
import io.github.jnbp.nextstop.data.withoutProductWord

/**
 * The name of a line as its badge shows it, the same everywhere (chart, list, badges, map,
 * notification): without the word for its product in front ("Bus 585", "Tram 50", also in
 * names stored before the sources dropped it, see withoutProductWord), and without a train number where the line
 * has a name of its own: "FEX 021857" is "FEX", "RE1 3809" is "RE1". Long-distance trains
 * have no line name but their number: "ICE 29", "IC 2041" stay as they are. The whole name
 * with the train number stays for the detail of a ride and for screen readers.
 */
fun shortLine(line: String?): String? {
    val name = line?.trim()?.replace(Regex("\\s+"), " ") ?: return null
    val bare = withoutProductWord(name)
    val plain = BUS_WORD.replace(bare, "").ifEmpty { bare }
    val numbered = TRAIN_NUMBERED.matchEntire(plain) ?: return plain
    val kind = numbered.groupValues[1]
    val number = numbered.groupValues[2]
    // The kind in words ("Railjet 174", "EuroCity 171"): its code, so the number keeps its place.
    val code = trainKindOf(kind)
    if (code != null && kind.all { it.isLetter() } && !code.equals(kind, ignoreCase = true)) return shortLine("$code $number")
    return when {
        // A long-distance train: its kind and number are its name.
        kind.uppercase() in LONG_DISTANCE_KINDS -> "$kind $number"
        // A line name of its own with a train number behind it: the line name alone.
        number.length >= TRAIN_NUMBER_DIGITS || number.startsWith("0") || kind.any { it.isDigit() } -> kind
        else -> plain
    }
}

/*
 * FlixBus and FlixTrain on badges (one scheme, decided for the chart, list and watch alike):
 * the whole name ("FlixBus 260", "FlixBus", "FLX 30", see flixLineName) is in the detail and
 * read out; a badge, chip or chart block shows every FlixBus as "Flix 260" or "Flix", never
 * one as "FlixBus" beside another as "Flix 260", and a FlixTrain as "FLX 30". The mode icon in
 * the badge tells the bus from the train (see modeHint); compactLine drops "Flix" for the code.
 */
private val FLIX_BUS = Regex("^FlixBus(?: (\\S+))?$")

/**
 * The name of a line on a badge, chip or block: [shortLine], and where that is longer than
 * [BADGE_MAX_CHARS] (coaches and trains of other operators: "FlixBus N1334") a shorter form
 * of it: the operator by its short name ("Flix N1334"), else the number alone where it has
 * digits ("N1334"), else cut with an ellipsis. Never longer than [BADGE_MAX_CHARS], so no
 * badge grows past its place; the whole name stays for the detail and for screen readers.
 */
fun badgeLine(line: String?): String? {
    val short = shortLine(line) ?: return null
    // Every FlixBus alike, whatever its length: "Flix 260", "Flix N1334", "Flix" (see FLIX_BUS).
    FLIX_BUS.matchEntire(short)?.let { bus ->
        val code = bus.groupValues[1]
        if (code.isEmpty()) return "Flix"
        if (code.length + 5 <= BADGE_MAX_CHARS) return "Flix $code"
    }
    if (short.length <= BADGE_MAX_CHARS) return short
    val split = OPERATOR_AND_CODE.matchEntire(short)
    if (split != null) {
        val operator = split.groupValues[1]
        val code = split.groupValues[2]
        val brief = OPERATOR_SHORT[operator.lowercase()]
        if (brief != null && brief.length + 1 + code.length <= BADGE_MAX_CHARS) return "$brief $code"
        if (code.any { it.isDigit() } && code.length <= BADGE_MAX_CHARS) return code
    }
    return short.take(BADGE_MAX_CHARS - 1).trimEnd() + "…"
}

/**
 * The shortest form of a line for a narrow block that cannot take [badgeLine]: the number of a
 * coach without its operator ("Flix N1334" as "N1334"). A train keeps its kind and number
 * ("RJ 174", "IC 2433"): the number is what tells one train from another, so a narrow block
 * shows it smaller rather than without it. Other names are already short.
 */
fun compactLine(line: String?): String? {
    val short = badgeLine(line) ?: return null
    OPERATOR_AND_CODE.matchEntire(short)?.let { split ->
        val operator = split.groupValues[1]
        val code = split.groupValues[2]
        if (operator in OPERATOR_SHORT.values && code.any { it.isDigit() }) return code
    }
    return short
}

/** The most characters of a line name on a badge (see [badgeLine]): "Flix N1334", "RE1", "ICE 1234". */
const val BADGE_MAX_CHARS = 10

// An operator's name, then the code of its line: "FlixBus N1334", "BlaBlaCar 1234".
private val OPERATOR_AND_CODE = Regex("^([A-Za-zÀ-ÿ]{4,}) (\\S+)$")

// Operators of coaches and trains by the short name their vehicles carry.
private val OPERATOR_SHORT = mapOf(
    "flixbus" to "Flix",
    "flixtrain" to "Flix",
    "blablacar" to "BlaBla",
    "blablabus" to "BlaBla",
    "eurolines" to "EL",
    "nightjet" to "NJ",
    "leokexpress" to "Leo",
)

/** The whole name of a line where it says more than the badge ([shortLine]); null where both agree. */
fun fullLineName(line: String?): String? {
    val whole = line?.trim()?.replace(Regex("\\s+"), " ") ?: return null
    return whole.takeIf { it != shortLine(line) && BUS_WORD.replace(whole, "") != shortLine(line) }
}

// The word for a bus in front of the number of its line.
private val BUS_WORD = Regex("^bus\\s+(?=\\S)", RegexOption.IGNORE_CASE)

// A name, then a blank and a number: "ICE 29", "FEX 021857", "RE1 3809", "S 41".
private val TRAIN_NUMBERED = Regex("^([A-Za-z]+[0-9]*)\\s([0-9]+)$")

// Kinds of trains that are named by their number, as there is no line.
private val LONG_DISTANCE_KINDS = setOf("ICE", "IC", "EC", "ECE", "EN", "NJ", "RJ", "RJX", "FLX", "TGV", "ES", "EST", "D")

// From this many digits a number behind a line name is a train number, not part of the line.
private const val TRAIN_NUMBER_DIGITS = 4

/** The kind of vehicle a badge shows as a small icon where its name does not tell it (see [modeHint]). */
enum class TravelMode { Bus, Train }

/**
 * The kind of vehicle to show beside the name of [line] of [product], only where the name
 * does not make it plain: a coach (FlixBus, BlaBlaCar: "Flix 260" could be a train, FlixTrain
 * has the same green), a FlixTrain ("FLX 30"), a train named by a bare number ("174"), and a
 * name that is only an operator's ("Leo Express", "Ruftaxi"). Null for ICE, IC, RE, S, U,
 * trams and city buses, whose names and colours say it. A rail replacement bus has its own
 * badge ("SEV", see RailReplacementBadge), which shows the bus as well.
 */
fun modeHint(line: String?, product: String): TravelMode? {
    val name = shortLine(line).orEmpty()
    val train = product == "express" || product == "regional"
    return when {
        product == "coach" -> TravelMode.Bus
        train && (name.isEmpty() || name.all { it.isDigit() }) -> TravelMode.Train
        (train || product == "") && lineKind(name) == "FLX" -> TravelMode.Train
        operatorOnly(name) && train -> TravelMode.Train
        operatorOnly(name) && product == "bus" -> TravelMode.Bus
        else -> null
    }
}

// A name of only letters, longer than the kind of a line ("RE", "ICE", "SEV", "Tram"): an operator's.
private fun operatorOnly(name: String): Boolean =
    name.length >= 5 && name.none { it.isDigit() } && name.all { it.isLetter() || it == ' ' || it == '-' }
