package io.github.jnbp.nextstop.data

/*
 * Generic English words in stop names from data built for the whole of Europe (Transitous: the
 * feeds of coach and train operators name "Berlin Südkreuz train station", "Göttingen central
 * bus station") in the language of the app. Only such a word at the end of a name is changed;
 * the name in front of it, a proper name, stays as it is. Applied where a provider's answer
 * is read (see RoutingSource), with the stop's position where there is one; ids keep the raw
 * names. The watch has a twin of this in its data/StopNameTerms.kt: change both together.
 */

/**
 * A generic ending in English and what it is in German: a word, or null to leave it out
 * ([keepAlone] the word for a name that is a town alone).
 */
private class StopTerm(val ending: Regex, val german: String?, val keepAlone: String? = german, val alone: Boolean = true)

// Longest first: "central bus station" before "bus station" before "station".
private val STOP_TERMS = listOf(
    StopTerm(term("central bus station|central coach station|main bus station"), "ZOB"),
    // Any bus station of a town is its "ZOB" in German, as on the signs (Hamburg ZOB).
    StopTerm(term("bus station|coach station|bus terminal|coach terminal"), "ZOB"),
    StopTerm(term("central train station|main train station|central railway station|main railway station|central station|main station"), "Hbf"),
    // A train station: the name already says which (a town and a station, a "Bahnhof" in it): left
    // out; a town alone says "Bahnhof".
    StopTerm(term("train station|railway station|rail station"), null, keepAlone = "Bahnhof"),
    StopTerm(term("bus stop|tram stop"), null, keepAlone = null),
)

// Further generic words, only for stops known to lie in Germany (see insideGermany): elsewhere they
// may be part of the local name. Each needs a name in front of it ("Hamburg Harbour").
private val GERMAN_STOP_TERMS = listOf(
    StopTerm(term("trade fair|trade fair centre|trade fair center|exhibition centre|exhibition center"), "Messe", alone = false),
    StopTerm(term("harbour|harbor|port"), "Hafen", alone = false),
    StopTerm(term("university"), "Universität", alone = false),
    StopTerm(term("old town"), "Altstadt", alone = false),
    StopTerm(term("cathedral"), "Dom", alone = false),
)

// "<Town> station", a town of one word: in Germany its "Bahnhof".
private val TOWN_STATION = Regex("^(\\S+)\\s+station$", RegexOption.IGNORE_CASE)

// Words before "station" that name no town ("Train Station", "Bus Station").
private val NOT_A_TOWN = setOf("train", "railway", "rail", "bus", "coach", "main", "central", "tram", "subway", "underground", "metro", "fire", "police", "petrol", "gas", "service")

// A longer name of an airport in Germany: "Berlin Brandenburg Airport" as "Flughafen Berlin Brandenburg".
private val AIRPORT_NAMED = Regex("^(.*\\S)\\s+airport(\\s+terminal(?:\\s+\\S+)?)?$", RegexOption.IGNORE_CASE)

// Germany as a rough outline of latitude and longitude, a few kilometres off at most at the borders;
// the towns right across them (Strasbourg, Basel, Salzburg, Venlo, Szczecin …) lie outside.
private val GERMANY_OUTLINE: List<Pair<Double, Double>> = listOf(
    55.06 to 8.30, 54.91 to 8.63, 54.90 to 9.00, 54.82 to 9.37, 54.84 to 9.70, 54.75 to 10.30,
    54.56 to 11.10, 54.53 to 11.60, 54.45 to 12.20, 54.50 to 12.70, 54.72 to 13.10, 54.72 to 13.50,
    54.50 to 13.80, 54.15 to 14.20, 53.95 to 14.22, 53.80 to 14.25, 53.45 to 14.40, 53.27 to 14.42,
    52.84 to 14.14, 52.60 to 14.62, 52.35 to 14.54, 52.05 to 14.73, 51.55 to 14.73, 51.25 to 15.03,
    50.87 to 14.82, 51.05 to 14.40, 50.97 to 14.27, 50.85 to 14.00, 50.68 to 13.50, 50.45 to 12.95,
    50.30 to 12.25, 50.25 to 12.10, 50.10 to 12.15, 49.95 to 12.45, 49.60 to 12.50, 49.30 to 12.85,
    49.10 to 13.30, 48.77 to 13.83, 48.57 to 13.52, 48.45 to 13.41, 48.27 to 13.02, 48.17 to 12.82,
    48.00 to 12.75, 47.85 to 12.95, 47.70 to 13.05, 47.55 to 13.05, 47.68 to 12.60, 47.60 to 12.20,
    47.48 to 11.60, 47.39 to 11.26, 47.43 to 10.98, 47.55 to 10.70, 47.30 to 10.20, 47.45 to 9.98,
    47.54 to 9.72, 47.57 to 9.60, 47.655 to 9.13, 47.70 to 8.90, 47.80 to 8.75, 47.78 to 8.50,
    47.62 to 8.42, 47.60 to 8.25, 47.57 to 7.95, 47.59 to 7.62, 47.60 to 7.56, 48.03 to 7.565,
    48.58 to 7.785, 48.97 to 8.22, 49.06 to 7.92, 49.12 to 7.42, 49.11 to 7.12, 49.21 to 6.92,
    49.47 to 6.36, 49.70 to 6.52, 49.80 to 6.48, 50.13 to 6.13, 50.32 to 6.40, 50.50 to 6.27,
    50.62 to 6.15, 50.75 to 6.02, 50.87 to 6.08, 51.00 to 6.00, 51.20 to 6.08, 51.37 to 6.21,
    51.60 to 6.10, 51.80 to 5.97, 51.87 to 6.20, 51.85 to 6.70, 52.03 to 6.83, 52.23 to 6.96,
    52.45 to 7.03, 52.65 to 6.78, 52.85 to 7.08, 53.18 to 7.22, 53.32 to 7.05, 53.60 to 6.55,
    54.40 to 7.60,
)

/** True when the place lies in Germany (by [GERMANY_OUTLINE]). */
internal fun insideGermany(latitude: Double, longitude: Double): Boolean {
    if (latitude !in 47.2..55.1 || longitude !in 5.8..15.1) return false
    var inside = false
    var j = GERMANY_OUTLINE.size - 1
    for (i in GERMANY_OUTLINE.indices) {
        val (latI, lonI) = GERMANY_OUTLINE[i]
        val (latJ, lonJ) = GERMANY_OUTLINE[j]
        if ((latI > latitude) != (latJ > latitude) &&
            longitude < (lonJ - lonI) * (latitude - latI) / (latJ - latI) + lonI
        ) {
            inside = !inside
        }
        j = i
    }
    return inside
}

// The ending alone ("Erfurt central station") or in brackets ("Frankfurt (Main) Hbf (main station)"),
// with an addition in brackets after it ("Hamburg central bus station (ZOB)"), or in front of
// the town after a comma ("Central Bus Station, Hamburg"), or the words alone, the town said
// apart ("central bus station" after "Hamburg").
private fun term(words: String): Regex =
    Regex(
        "^(?:(.*?\\S)\\s*,?\\s+)?(?:$words|\\((?:$words)\\))(?:\\s*\\(([^()]*)\\))?$" +
            "|^(?:$words)\\s*,\\s*([^,()]*\\S)$",
        RegexOption.IGNORE_CASE,
    )

// An airport of a town or a code with a terminal: "Airport BER Terminal 1", "Leipzig/Halle airport terminal".
private val AIRPORT_FIRST = Regex("^airport\\s+(\\S+)(\\s+terminal(?:\\s+\\S+)?)?$", RegexOption.IGNORE_CASE)
private val AIRPORT_AFTER = Regex("^(\\S+)\\s+airport(\\s+terminal(?:\\s+\\S+)?)?$", RegexOption.IGNORE_CASE)

/**
 * "Airport BER Terminal 1" and "BER airport terminal 1" as "Flughafen BER Terminal 1": only for
 * a town or a code (one word); a longer name ("Berlin Brandenburg Airport") is a proper name.
 */
private fun localAirport(name: String): String? {
    val match = AIRPORT_FIRST.matchEntire(name) ?: AIRPORT_AFTER.matchEntire(name) ?: return null
    val place = match.groupValues[1]
    val terminal = match.groupValues[2].trim().takeIf { it.isNotEmpty() }
        ?.replaceFirst(Regex("^terminal", RegexOption.IGNORE_CASE), "Terminal")
    return listOfNotNull("Flughafen", place, terminal).joinToString(" ")
}

// A name that already says it is a station.
private val STATION_WORD = Regex("(bahnhof|\\bhbf\\b|\\bbf\\b|\\bbhf\\b|^S\\+U |^[SU] )", RegexOption.IGNORE_CASE)

/**
 * [name] with a generic English ending in the words of [language] ("de"): "Göttingen central
 * bus station" as "Göttingen ZOB", "Göttingen central station" as "Göttingen Hbf", "Berlin
 * Südkreuz train station" as "Berlin Südkreuz", "Göttingen train station" as "Göttingen
 * Bahnhof", "Hamburg bus station" and "Hamburg central bus station" as "Hamburg ZOB". Unchanged for English and for
 * names without such an ending; never empty. With the stop's [latitude] and [longitude]: unchanged
 * outside Germany ("London Victoria Coach Station"), and in Germany further words too ("Hamburg
 * Harbour" as "Hamburg Hafen", "Kiel station" as "Kiel Bahnhof", "Berlin Brandenburg Airport" as
 * "Flughafen Berlin Brandenburg", see GERMAN_STOP_TERMS).
 */
fun localStopTerms(
    name: String,
    language: String = AppLanguage.code,
    latitude: Double? = null,
    longitude: Double? = null,
): String {
    if (language != "de") return name
    val german = if (latitude != null && longitude != null) insideGermany(latitude, longitude) else null
    // A stop known to lie elsewhere keeps its name.
    if (german == false) return name
    val trimmed = name.trim()
    localAirport(trimmed)?.let { return it }
    if (german == true) {
        AIRPORT_NAMED.matchEntire(trimmed)?.let { match ->
            val terminal = match.groupValues[2].trim().takeIf { it.isNotEmpty() }
                ?.replaceFirst(Regex("^terminal", RegexOption.IGNORE_CASE), "Terminal")
            return listOfNotNull("Flughafen", match.groupValues[1].trim(), terminal).joinToString(" ")
        }
        TOWN_STATION.matchEntire(trimmed)?.let { match ->
            val town = match.groupValues[1]
            if (!STATION_WORD.containsMatchIn(town) && town.lowercase() !in NOT_A_TOWN) return "$town Bahnhof"
        }
    }
    for (term in if (german == true) STOP_TERMS + GERMAN_STOP_TERMS else STOP_TERMS) {
        val match = term.ending.matchEntire(trimmed) ?: continue
        val stem = (match.groupValues[1].ifEmpty { match.groupValues[3] }).trim().trimEnd(',').trim()
        // An addition in brackets after the words: left out where it only says them again.
        val added = match.groupValues[2].trim()
        val german = term.german
        // The words alone: "ZOB" and "Hbf" stand alone, other words (a proper name) stay.
        if (stem.isEmpty()) return if (german != null && added.isEmpty() && term.alone) german else name
        val station = STATION_WORD.containsMatchIn(stem)
        val said = when {
            // "Hbf" where the name does not say it is a station already.
            german == "Hbf" -> if (station) stem else "$stem Hbf"
            // Never twice: "Hamburg ZOB central bus station" is "Hamburg ZOB".
            german != null -> if (stem.endsWith(" $german", ignoreCase = true)) stem else "$stem $german"
            // A train station or a stop named by more than a town: the name says which.
            station || stem.contains(' ') -> stem
            else -> term.keepAlone?.let { "$stem $it" } ?: stem
        }
        val repeated = added.isEmpty() || added.equals(german, ignoreCase = true) ||
            said.endsWith(" $added", ignoreCase = true)
        return if (repeated) said else "$said ($added)"
    }
    return name
}

// "Bhf" or "Bahnhof" after the name of an S- or U-Bahn station, before a town in brackets if any.
private val STATION_WORD_AFTER_PREFIX = Regex("^((?:S\\+U|S|U)\\s+\\S.*?)\\s+(?:Bhf\\.?|Bahnhof)(\\s*\\([^()]*\\))?$")

/**
 * [name] without a "Bhf" or "Bahnhof" at its end where an S, U or S+U before it already says it
 * is a station (VBB names): "S+U Zoologischer Garten Bhf" as "S+U Zoologischer Garten", "S
 * Schöneweide Bhf (Berlin)" as "S Schöneweide (Berlin)". Names of other stations ("Leipzig
 * Hbf", "Erkner Bhf") stay as they are; only for showing a name.
 */
fun withoutRedundantStationWord(name: String): String =
    STATION_WORD_AFTER_PREFIX.matchEntire(name)?.let { it.groupValues[1] + it.groupValues[2] } ?: name
