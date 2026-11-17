package io.github.jnbp.nextstop.wear.data

/*
 * The name of a line on a badge of the watch, never longer than BADGE_MAX_CHARS. Twin of the
 * phone's ui/LineNames.kt badgeLine: change both together. Lines the phone sends are already
 * in this form; the watch's own answers (Transitous: "FlixBus N1334") get it here.
 */

/**
 * [line] as it fits a badge: as it is up to [BADGE_MAX_CHARS], else the operator by its short
 * name ("Flix N1334"), else the number alone where it has digits ("N1334"), else cut with an
 * ellipsis.
 */
fun badgeLine(line: String?): String? {
    val short = line?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() } ?: return null
    // Every FlixBus alike, whatever its length: "Flix 260", "Flix N1334", "Flix".
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

/** The most characters of a line name on a badge (see [badgeLine]). */
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
    // Trains whose kind is spelled out keep their number: "Railjet 1234" as "RJ 1234".
    "railjet" to "RJ",
    "eurocity" to "EC",
    "intercity" to "IC",
    "euronight" to "EN",
    "regionalbahn" to "RB",
    "regionalexpress" to "RE",
    "leokexpress" to "Leo",
)

// A FlixBus in the app's form (see flixLineName), with the code of its line or none.
private val FLIX_BUS = Regex("^FlixBus(?: (\\S+))?$")

// A name of FlixBus or FlixTrain: "FLX 040", "Flix 260", "FlixBus N1334", "FlixTrain 1234".
private val FLIX_NAME = Regex("^(FLX|Flix|FlixBus|FlixTrain)(?: ?([A-Za-z]?[0-9]+[A-Za-z]?))?$", RegexOption.IGNORE_CASE)

/**
 * [name] of a FlixBus or FlixTrain ride in the app's one form: "FlixBus 260" or "FlixBus" for a
 * bus ([product] coach or bus), "FLX 30" or "FlixTrain" for a train. Twin of the phone's
 * data/Products.kt flixLineName: change both together.
 */
fun flixLineName(name: String, product: String): String {
    val match = FLIX_NAME.matchEntire(name.trim()) ?: return name
    val word = match.groupValues[1].lowercase()
    val code = match.groupValues[2].uppercase().ifEmpty { null }
    return when {
        product == "coach" || product == "bus" -> if (code != null) "FlixBus $code" else "FlixBus"
        product in setOf("express", "regional", "") && (word == "flx" || word == "flixtrain") ->
            if (code != null && code.all { it.isDigit() }) "FLX $code" else if (code == null) "FlixTrain" else name
        else -> name
    }
}

/** The kind of vehicle a badge shows as a small icon where its name does not tell it (see [modeHint]). */
enum class TravelMode { Bus, Train }

/**
 * The kind of vehicle to show beside [line] of [product], only where the name does not make it
 * plain: a coach ("Flix 260"), a FlixTrain ("FLX 30"), a train named by a bare number ("174"),
 * a name that is only an operator's. Twin of the phone's ui/LineNames.kt modeHint: change both
 * together.
 */
fun modeHint(line: String?, product: String): TravelMode? {
    val name = line?.trim().orEmpty()
    val train = product == "express" || product == "regional"
    val kind = name.takeWhile { it.isLetter() }
    val flx = kind.equals("FLX", ignoreCase = true) && (name.length == 3 || name[3] == ' ' || name[3].isDigit())
    val operatorOnly = name.length >= 5 && name.none { it.isDigit() } && name.all { it.isLetter() || it == ' ' || it == '-' }
    return when {
        product == "coach" -> TravelMode.Bus
        train && (name.isEmpty() || name.all { it.isDigit() }) -> TravelMode.Train
        (train || product == "") && flx -> TravelMode.Train
        operatorOnly && train -> TravelMode.Train
        operatorOnly && product == "bus" -> TravelMode.Bus
        else -> null
    }
}
