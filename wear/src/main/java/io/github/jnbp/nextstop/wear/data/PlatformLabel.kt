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
