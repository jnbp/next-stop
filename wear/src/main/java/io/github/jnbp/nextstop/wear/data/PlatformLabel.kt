package io.github.jnbp.nextstop.wear.data

/*
 * Platforms as providers write them for a departure: some add the line in brackets ("1 (U7)"),
 * which the line's badge beside it already says.
 */

// A part in round brackets at the end of a platform: "1 (U7)".
private val PLATFORM_TRAILING_BRACKET = Regex("""^(.*\S)\s*\(([^()]+)\)$""")

/**
 * [platform] without a line in brackets at its end where that is [line] ("1 (U7)" for the U7 is
 * "1"); other brackets ("3 (Ost)") stay. Null for no platform.
 */
fun platformWithoutLine(platform: String?, line: String?): String? {
    val trimmed = platform?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val match = PLATFORM_TRAILING_BRACKET.matchEntire(trimmed) ?: return trimmed
    return if (sameLine(match.groupValues[2], line)) match.groupValues[1].trim() else trimmed
}

private fun sameLine(a: String, b: String?): Boolean {
    if (b == null) return false
    val key = { s: String -> s.lowercase().filter { it.isLetterOrDigit() } }
    return key(a).isNotEmpty() && key(a) == key(b)
}

/*
 * Which platform to show at all (#19, #35e), as on the phone (shownPlatform in the app): the
 * track field of a provider also holds ids of stop points ("78" at Bonn Hbf, "13" for the
 * tram 66). For trains and anything unknown a track of 1 to 59 or 101 to 109 (S-Bahn tunnels)
 * is shown; for trams, buses and ferries a bay ("Steig") of 1 to 99 or a letter; bare numbers
 * beyond that and ids are left out; a text that says what it is ("Pos. 11") stays.
 */

/** True when a platform of a tram, bus or ferry stop: a bay ("Steig"), not a track. */
fun isBayProduct(product: String?): Boolean = product in setOf("tram", "bus", "coach", "ferry")

private val WATCH_PLATFORM_WORD = Regex("^(gleis|gl\\.?|platform|track|bstg\\.?|bahnsteig|steig)\\s*", RegexOption.IGNORE_CASE)
private val WATCH_NUMBERED = Regex("^(\\d{1,3})\\s?([A-Za-z]{1,2}(\\s?-\\s?[A-Za-z])?)?$")
private val WATCH_LETTERED = Regex("^[A-Za-z]{1,2}\\d{0,2}$")

/**
 * [platform] (already without its line, see [platformWithoutLine]) where it is plausible for a
 * ride of [product], without a word in front; null else. [numbered] tells whether it is a
 * number or letter to put "Gl."/"Stg." in front of.
 */
fun plausibleWatchPlatform(platform: String?, product: String?): Pair<String, Boolean>? {
    val raw = platform?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val core = raw.replace(WATCH_PLATFORM_WORD, "").trim()
    if (core.isEmpty() || ':' in core) return null
    val bay = isBayProduct(product)
    WATCH_NUMBERED.matchEntire(core)?.let { match ->
        val number = match.groupValues[1].toInt()
        val plausible = if (bay) number in 1..99 else number in 1..59 || number in 100..109
        return if (plausible) core to true else null
    }
    if (WATCH_LETTERED.matches(core)) return core to true
    if (core.all { it.isDigit() || it.isWhitespace() || it == '_' || it == '-' }) return null
    return raw to false
}
