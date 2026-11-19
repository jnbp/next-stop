package io.github.jnbp.nextstop.data

/*
 * Which platform to show, and as what (#19, #35e). The providers send in their track field not
 * only tracks: Transitous gave "78" at Bonn Hbf, "79" at Köln/Bonn Flughafen (track 4 in
 * truth), "84" at Troisdorf and "85" at Köln Messe/Deutz, ids of stop points or masts of the
 * data, and "13", "16" or "18" for the tram and bus 66. A train never stops at "Gleis 78" in
 * these stations; a tram or bus stops at a bay ("Steig"), not at a track. So the track field
 * (live or of the timetable, never anything taken from the id of a stop) is shown only where
 * it is plausible: for trains, U-Bahn and anything unknown a track number of 1 to
 * MAX_TRACK (the largest stations, Zürich HB for one, number their tracks up to 54) or one of
 * the S-Bahn tunnels' 101 to 109 (Frankfurt, Stuttgart), with letters for sections
 * ("4a", "11 A-C"); for trams, buses and ferries a bay of 1 to MAX_BAY or a letter ("A",
 * "B2"). Anything else that is a bare number is left out; a text that says what it is
 * ("Pos. 11") stays as it is.
 */

/** What a platform is called: a track ("Gleis"), a bay of a tram or bus stop ("Steig"), or a text that names itself. */
enum class PlatformKind { Track, Bay, Text }

/** A platform as it is shown: what it is called and its number or name, without a word in front. */
data class ShownPlatform(val kind: PlatformKind, val name: String)

// The highest track number taken for a track of a station.
private const val MAX_TRACK = 59

// The track numbers of the S-Bahn tunnels below some main stations (Frankfurt, Stuttgart).
private val TUNNEL_TRACKS = 100..109

// The highest number taken for a bay of a tram or bus stop.
private const val MAX_BAY = 99

// A word for a platform in front of its number, as some providers send it.
private val PLATFORM_WORD = Regex("^(gleis|gl\\.?|platform|track|bstg\\.?|bahnsteig|steig)\\s*", RegexOption.IGNORE_CASE)

// A number with letters for its sections: "4", "4a", "11 A-C", "1 D - F".
private val NUMBERED = Regex("^(\\d{1,3})\\s?([A-Za-z]{1,2}(\\s?-\\s?[A-Za-z])?)?$")

// A bay named by letters, with a number or not: "A", "B2", "AB".
private val LETTERED = Regex("^[A-Za-z]{1,2}\\d{0,2}$")

/**
 * [platform] as it is shown for a ride of [product] (see above), or null where there is none
 * or it is not plausible. The kind follows the family of the product (see [modeFamily]): a bay
 * for trams, buses and ferries, a track for everything else.
 */
fun shownPlatform(platform: String?, product: String?): ShownPlatform? {
    val raw = platform?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val core = raw.replace(PLATFORM_WORD, "").trim()
    // The id of a stop point ("de:05314:61908:3:78") is never a platform.
    if (core.isEmpty() || ':' in core) return null
    val family = modeFamily(product.orEmpty())
    val bay = family == ModeFamily.Tram || family == ModeFamily.Bus || family == ModeFamily.Ferry
    val numbered = NUMBERED.matchEntire(core)
    if (numbered != null) {
        val number = numbered.groupValues[1].toInt()
        val plausible = if (bay) number in 1..MAX_BAY else number in 1..MAX_TRACK || number in TUNNEL_TRACKS
        if (!plausible) return null
        return ShownPlatform(if (bay) PlatformKind.Bay else PlatformKind.Track, core)
    }
    if (LETTERED.matches(core)) return ShownPlatform(if (bay) PlatformKind.Bay else PlatformKind.Track, core)
    // A bare number of other digits is an id, not a platform; a text names itself ("Pos. 11").
    if (core.all { it.isDigit() || it.isWhitespace() || it == ':' || it == '_' || it == '-' }) return null
    return ShownPlatform(PlatformKind.Text, raw)
}

/** [platform] where it is plausible for a ride of [product] (see [shownPlatform]), as it came; null else. */
fun plausiblePlatform(platform: String?, product: String?): String? =
    if (shownPlatform(platform, product) == null) null else platform?.trim()
