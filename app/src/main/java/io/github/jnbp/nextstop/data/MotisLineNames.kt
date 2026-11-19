package io.github.jnbp.nextstop.data

/*
 * The names of MOTIS lines (Transitous), apart from the source so they can be tested without
 * Android. See TransitousSource.lineName.
 */

internal val TRAIN_MODES = setOf("HIGHSPEED_RAIL", "LONG_DISTANCE", "NIGHT_RAIL", "REGIONAL_RAIL", "REGIONAL_FAST_RAIL", "RAIL")

/**
 * The name of a line. Trains and coaches get their kind in front of a bare number:
 * "ICE 29" for a route named "29" without a trip number, "ICE 1234" for a route named only
 * "ICE" (or a line such as "ICE 41") with trip number 1234, "RE 1" for a regional route "1". The kind comes from the category, else
 * from the display name, the route names, and last from the mode and the operator.
 * Other lines keep the short name of the route or what MOTIS shows.
 */
internal fun composeLineName(
    mode: String?,
    route: String?,
    number: String?,
    display: String?,
    category: String?,
    longName: String?,
    agency: String?,
): String? {
    val name = composedLineName(mode, route, number, display, category, longName, agency)
    // Foreign feeds write train numbers with leading zeros (Rejseplanen: "000384"): a train
    // never shows them ("RJ 384", #279).
    return if (mode in TRAIN_MODES) withoutTrainNumberZeros(name) else name
}

private fun composedLineName(
    mode: String?,
    route: String?,
    number: String?,
    display: String?,
    category: String?,
    longName: String?,
    agency: String?,
): String? {
    // A name that is only the word for the product ("Bus") is no name of a line: the number
    // comes from another field then.
    val routeName = route?.trim()?.takeIf { it.isNotEmpty() && !isProductWord(it) }
    val trip = number?.trim()?.takeIf { it.isNotEmpty() }
    val shown = display?.trim()?.takeIf { it.isNotEmpty() && !isProductWord(it) }
    val shortLong = longName?.trim()?.takeIf { it.length in 1..5 && it.any { char -> char.isDigit() } && !it.contains(' ') }
    val categoryName = category?.trim()?.takeIf { it.isNotEmpty() }
    // A FlixBus by the code of its line ("N1334", "260"): "FlixBus N1334" (see flixLineName).
    if (mode == "COACH" && agency.orEmpty().contains("Flix", ignoreCase = true)) {
        val code = routeName ?: shown ?: trip
        return if (code == null || code.contains("flix", ignoreCase = true) || code.startsWith("FLX", ignoreCase = true)) {
            code ?: "FlixBus"
        } else {
            "FlixBus $code"
        }
    }
    if (mode in TRAIN_MODES || mode == "COACH") {
        val kind = trainKindOf(categoryName) ?: categoryName?.takeIf { it.none { char -> char.isDigit() } }
            ?: kindOf(shown) ?: kindOf(routeName) ?: kindOf(longName)
            ?: trainKindOf(longName) ?: trainKindOf(agency) ?: defaultKind(mode, agency)
        val routeIsNumber = routeName != null && routeName.all { it.isDigit() }
        val tripIsNumber = trip != null && trip.all { it.isDigit() }
        val routeKind = routeName?.takeIf { it.none { char -> char.isDigit() } }?.let { trainKindOf(it) }
        // A long-distance train goes by its train number, as on every board and at the DB
        // ("ICE 621"), never by the number of its line in the route ("41", "ICE 41": #85).
        if (mode in TRAIN_MODES && kind != null && tripIsNumber && isLongDistanceTrainName("$kind 1")) return "$kind $trip"
        // DELFI may write the kind into the trip's number too ("ICE 9573" with route "83",
        // Transitous at Strasbourg, Nov 2026): the train number still wins over the line's.
        val tripRun = trip?.let { TRIP_WITH_KIND.matchEntire(it) }
        if (mode in TRAIN_MODES && kind != null && tripRun != null && isLongDistanceTrainName("$kind 1") &&
            tripRun.groupValues[1].equals(kind, ignoreCase = true)
        ) return "$kind ${tripRun.groupValues[2]}"
        when {
            // The route names only the kind: the trip number goes behind it.
            routeName != null && kind != null && routeName.equals(kind, ignoreCase = true) && tripIsNumber ->
                return "$routeName $trip"
            routeName != null && routeIsNumber && kind != null -> return "$kind $routeName"
            routeName == null && kind != null && shown != null && shown.all { it.isDigit() } -> return "$kind $shown"
            routeName == null && shown == null && kind != null && tripIsNumber -> return "$kind $trip"
            // The route names only the kind in words ("Railjet", "EuroCity"): the kind's code
            // and the number, from the trip or the shown name ("RJ 174"), never the word alone.
            // Trains only: a coach named "Intercity" is no IC.
            mode in TRAIN_MODES && routeKind != null -> {
                val code = routeKind
                if (tripIsNumber) return "$code $trip"
                if (shown != null && shown.all { it.isDigit() }) return "$code $shown"
                if (shown != null && shown.any { it.isDigit() } && kindOf(shown) != null) return shown
            }
        }
    }
    if (routeName == null && shown == null && shortLong == null) {
        // Only the product is named: a bus or tram without its number shows the trip number
        // only where nothing else is known.
        val word = route?.trim()?.takeIf { it.isNotEmpty() } ?: display?.trim()?.takeIf { it.isNotEmpty() }
        return trip?.takeIf { word == null || mode in TRAIN_MODES || it.length <= 4 } ?: word ?: categoryName
    }
    return routeName ?: shown ?: shortLong ?: trip ?: categoryName
}

// A trip number with its kind in front: "ICE 9573".
private val TRIP_WITH_KIND = Regex("^([A-Za-z]{1,5}) ?([0-9]{1,6})$")

/**
 * The kind at the start of a line name such as "ICE 29", "RE1" or "OUIGO 7692": two to five
 * capitals before a number or the end (five: the SNCF's OUIGO keeps its own kind, never the
 * German default ICE).
 */
private fun kindOf(name: String?): String? {
    val text = name?.trim() ?: return null
    val kind = text.takeWhile { it.isLetter() && it.isUpperCase() }
    if (kind.length !in 2..5) return null
    val rest = text.substring(kind.length)
    return if (rest.isEmpty() || rest.first() == ' ' || rest.first().isDigit()) kind else null
}

/** The kind of a long-distance train in Germany when MOTIS names none: by the operator and the mode. */
private fun defaultKind(mode: String?, agency: String?): String? {
    val operator = agency.orEmpty()
    if (operator.contains("Flix", ignoreCase = true)) return "FLX"
    val germanRail = agency == null || operator.contains("DB ") || operator.startsWith("DB") ||
        operator.contains("Deutsche Bahn", ignoreCase = true)
    if (!germanRail) return null
    return when (mode) {
        "HIGHSPEED_RAIL" -> "ICE"
        "LONG_DISTANCE" -> "IC"
        else -> null
    }
}
