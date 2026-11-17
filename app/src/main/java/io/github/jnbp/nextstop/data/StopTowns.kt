package io.github.jnbp.nextstop.data

import java.util.Locale

/** A stop name split into the stop and the town it lies in, where the name says the town. */
data class StopTown(val stop: String, val town: String?)

// The longest part before a comma that is still taken as a town.
private const val MAX_TOWN_CHARS = 40

// The signs of the network in front of the name of a station.
private val STOP_SIGNS = listOf("S ", "U ", "S+U ")

// "Stop (Town)" at the end of a name; the town without brackets of its own.
private val TOWN_IN_BRACKETS = Regex("""^(.*\S)\s+\(([^()\[\]]+)\)$""")

// Words in brackets that belong to the name of a town ("Frankfurt (Oder)", "Dahme (Mark)",
// "Halle (Saale)", "Neustadt (Dosse)", "Aue (Sachsen)", "Landau (Pfalz)"), tell a part of a
// town or of a stop ("(Nord)", "(Kehre)", "(Schienenersatzverkehr)") or say something else
// ("(Bus)"): never a town of their own. Also none: a district's code ("(BAR)", "(MOL)") and
// fewer than three letters ("(S)", "(A)"). Found over all packs with tools/audit_sign_merges.py.
private val NOT_A_TOWN = setOf(
    "mitte", "nord", "süd", "west", "ost", "nordost", "nordwest", "südost", "südwest", "zentrum", "ort", "dorf",
    "bahnhof", "bf", "hbf", "zob", "kehre", "wendeplatz", "wendeschleife", "schleife", "schule", "schulbus",
    "schülerverkehr", "bürgerbus", "fähre", "stadtbahn", "friedhof", "kirche", "feuerwehr", "eingang", "ausgang",
    "vorplatz", "aussiedlerhof", "bushaltestelle", "haltestelle", "ersatzhaltestelle", "ersatzhalt", "ausstieg",
    "einstieg", "schienenersatzverkehr", "ast", "rufbus", "anruflinienbus", "netliner-spot", "strandexpress",
    "dünenbahn", "baden", "pfalz", "saar", "thür", "thüringen", "meckl", "lahn", "sieg", "neckar", "erzgeb",
    "erzgebirge", "rhein", "oberpf", "württemberg", "oberbay", "oberbayern", "niederbay", "inn", "schwab", "harz",
    "mosel", "sachsen", "bay", "bayern", "han", "wümme", "nahe", "vogtl", "vogtland", "fils", "schwarzwald",
    "mittelfr", "oberfr", "ofr", "ndb", "alz", "oberlausitz", "niederlausitz", "lausitz", "eifel", "taunus",
    "westerwald", "odenwald", "allgäu", "ostfriesl", "holstein", "westfalen", "hessen",
    "oder", "havel", "mark", "spree", "saale", "elbe", "main", "dosse", "prignitz", "elster", "lausitz",
    "uckermark", "altmark", "ostprignitz", "westf", "westf.", "rhld", "rhld.", "oldb", "oldb.", "sachs",
    "sachs.", "holst", "holst.", "württ", "württ.", "hess", "hess.", "mecklenburg", "vorpommern", "ruppin",
    "bus", "tram", "ubahn", "u-bahn", "sbahn", "s-bahn", "zug", "bahn", "ersatzverkehr", "sev",
    "tief", "oben", "unten",
)

/**
 * [name] as stop and town, generally for every source: "Town, Stop" as providers of the
 * regions write it ("Zagelsdorf, Kirchstr.", "Frankfurt (Oder), Bahnhof") and "Stop (Town)"
 * ("S Ostkreuz Bhf (Berlin)"). Brackets that are part of a town's name ("Frankfurt (Oder)")
 * are no town of their own; a part before the comma with a digit in it is a street, not a
 * town. A name without a town is the stop alone.
 */
fun splitStopTown(name: String): StopTown {
    val whole = name.trim().replace(Regex("\\s+"), " ")
    val comma = whole.indexOf(", ")
    if (comma > 0) {
        val town = whole.substring(0, comma).trim()
        val stop = whole.substring(comma + 2).trim()
        // "S Hauptbahnhof, Bus": a stop in front, not a town.
        val stopInFront = STOP_SIGNS.any { town.startsWith(it) }
        if (stop.isNotEmpty() && !stopInFront && town.length <= MAX_TOWN_CHARS && town.none { it.isDigit() }) {
            return StopTown(stop, town)
        }
    }
    return townInBrackets(whole) ?: StopTown(whole, null)
}

/** "Stop (Town)" at the end of [whole] as stop and town; null where the brackets name no town. */
private fun townInBrackets(whole: String): StopTown? {
    val bracket = TOWN_IN_BRACKETS.matchEntire(whole) ?: return null
    val town = bracket.groupValues[2].trim()
    val plain = town.lowercase(Locale.ROOT)
    val letters = town.count { it.isLetter() }
    val districtCode = town.length <= 4 && town.all { it.isUpperCase() || !it.isLetter() }
    val qualifier = plain in NOT_A_TOWN || plain.startsWith("b ") || plain.startsWith("b. ") ||
        plain.startsWith("bei ") || plain.startsWith("kr ") || plain.startsWith("kr. ") || town.any { it.isDigit() } ||
        letters < 3 || districtCode || plain.startsWith("richtung ") || plain.startsWith("ri. ") || plain.startsWith("fr ") ||
        '&' in plain
    return if (!qualifier && town.isNotEmpty()) StopTown(bracket.groupValues[1].trim(), town) else null
}

// A town for comparing: small letters, without a part in brackets, "/" or "-" as a blank.
private fun townKey(town: String): String =
    town.lowercase(Locale.ROOT).replace(Regex("\\([^)]*\\)"), "").replace(Regex("[/\\-]"), " ")
        .replace(Regex("\\s+"), " ").trim()

/** Whether two towns are the same, however each name writes it ("Dahme/Mark", "Dahme (Mark)"). */
fun sameTown(a: String?, b: String?): Boolean {
    if (a == null || b == null) return false
    val keyA = townKey(a)
    val keyB = townKey(b)
    if (keyA.isEmpty() || keyB.isEmpty()) return a.equals(b, ignoreCase = true)
    return keyA == keyB || keyA.substringBefore(' ') == keyB && keyB.length >= 4 || keyB.substringBefore(' ') == keyA && keyA.length >= 4 ||
        isTownAbbreviation(keyA, keyB) || isTownAbbreviation(keyB, keyA)
}

/**
 * True when [short] is a short form of the town [long] as the providers write them in brackets:
 * two to four letters, the first one the town's, all of them in its name in that order ("Bln"
 * for Berlin, "Ffm" for Frankfurt am Main).
 */
internal fun isTownAbbreviation(short: String, long: String): Boolean {
    val letters = short.lowercase(Locale.ROOT).filter { it.isLetter() }
    val full = long.lowercase(Locale.ROOT).filter { it.isLetter() }
    if (letters.length !in 2..4 || full.length <= letters.length || letters[0] != full[0]) return false
    if (short.trim().removeSuffix(".").any { !it.isLetter() }) return false
    var at = 0
    for (letter in full) if (at < letters.length && letter == letters[at]) at++
    return at == letters.length
}

/**
 * A stop's name as the app shows it: [title] the name of the stop (with the town in brackets
 * only where it is another than the town the app is used in), [part] what the provider writes
 * in square brackets behind it, the part of a big station the stop is ("Bus K.-Liebknecht-Str.",
 * "Gleis 1-8", "U2"), shown as a small line below the title; null where there is none.
 */
data class StopNameParts(val title: String, val part: String?)

// One part in square brackets at the end of a name, and the abbreviation "Str" without its dot.
private val TRAILING_PART = Regex("""^(.*?\S)\s*\[([^\[\]]+)\]$""")
private val STREET_WITHOUT_DOT = Regex("""(?<=[Ss]tr)(?=$|[\s,/)])""")
private val SPACES = Regex("\\s+")

/**
 * [name] as [StopNameParts], generally for every provider: "S+U Alexanderplatz (Bln) [Bus
 * K.-Liebknecht-Str]" is "S+U Alexanderplatz" with the part "Bus K.-Liebknecht-Str." where the
 * app is used in Berlin ([home], see [LocalTown]), "S Golm (Potsdam)" keeps its town there.
 * Brackets that belong to a town's name ("Frankfurt (Oder)") stay. The raw name stays what
 * requests and ids use; this is only for showing it. Generic English endings of names from
 * Europe-wide data come in the app's language already (see [localStopTerms], applied where a
 * provider's answer is read, where the position tells whether the stop lies in Germany);
 * [language] is no longer used here. The watch has a twin of this, [sameTown]
 * and [dominantTown] in its data/StopNames.kt: change both together.
 */
@Suppress("UNUSED_PARAMETER")
fun stopNameParts(name: String, home: String? = LocalTown.reference, language: String = AppLanguage.code): StopNameParts {
    var rest = name.trim().replace(SPACES, " ")
    val parts = ArrayList<String>(1)
    while (true) {
        val match = TRAILING_PART.matchEntire(rest) ?: break
        parts.add(0, match.groupValues[2].trim().replace(STREET_WITHOUT_DOT, "."))
        rest = match.groupValues[1].trim()
    }
    // Generic English words at the end came in the app's language already where the provider's
    // answer was read (see localStopTerms, which knows there whether the stop lies in Germany).
    // An address with the house number in front ("32 Musterstraße") as Germans write it.
    rest = germanStreetOrder(rest)
    // "S+U Zoologischer Garten Bhf" as "S+U Zoologischer Garten": the S+U says it is a station.
    rest = withoutRedundantStationWord(rest)
    val town = townInBrackets(rest)
    val title = when {
        town != null && town.town != null && sameTown(town.town, home) -> town.stop
        else -> withoutTownAfterComma(rest, home)
    }
    return StopNameParts(title.ifEmpty { name.trim() }, parts.filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { null })
}

// "Stop, Town" at the end of a name.
private val TOWN_AFTER_COMMA = Regex("""^(.*\S),\s*([^,()\[\]]+)$""")

/** [name] without ", Town" at its end where that town is [home] ("Rathaus, Springfield" in Springfield); else as it is. */
private fun withoutTownAfterComma(name: String, home: String?): String {
    val match = TOWN_AFTER_COMMA.matchEntire(name) ?: return name
    return if (sameTown(match.groupValues[2].trim(), home)) match.groupValues[1].trim() else name
}

/** The part of a big station the stop [this] is, as a small line below its name (see [stopNameParts]); null for none. */
fun String.stopNamePart(): String? = stopNameParts(this).part

/** The stop [this] in one line for a list where stops of one station stand together: "S+U Alexanderplatz · Bus K.-Liebknecht-Str.". */
fun String.listedStopName(): String {
    val parts = stopNameParts(this)
    return if (parts.part == null) parts.title else parts.title + " · " + parts.part
}

/**
 * The town the app is used in now, for the names of its stops: where most of the stops around
 * name a town in brackets ("(Bln)", "(Potsdam)"), that one (see [noteStops]); Berlin, the app's
 * first region, until the stops say otherwise.
 */
object LocalTown {
    @Volatile
    var current: String? = null
        private set

    /** The town whose name the stops do not repeat. */
    val reference: String get() = current ?: DEFAULT_TOWN

    /** Takes the town most of [names] (the stops around) name, where they name one. */
    fun noteStops(names: List<String>) {
        dominantTown(names)?.let { current = it }
    }
}

private const val DEFAULT_TOWN = "Berlin"

/**
 * The town most of [names] name in brackets ("S+U Alexanderplatz (Bln) [U2]"); null where none
 * does for at least [MIN_TOWN_STOPS] stops of different names and half of all [names] (two
 * stops "U Freie Universität (Thielplatz)" name a place, not the town the app is used in).
 */
internal fun dominantTown(names: List<String>): String? {
    val towns = names.mapNotNull { name ->
        var rest = name.trim().replace(SPACES, " ")
        while (true) rest = TRAILING_PART.matchEntire(rest)?.groupValues?.get(1)?.trim() ?: break
        townInBrackets(rest)?.let { it.town?.let { town -> it.stop to town } }
    }
    if (towns.isEmpty()) return null
    val groups = ArrayList<MutableList<Pair<String, String>>>()
    for (entry in towns) {
        val group = groups.firstOrNull { sameTown(it[0].second, entry.second) }
        if (group != null) group.add(entry) else groups.add(mutableListOf(entry))
    }
    val best = groups.maxByOrNull { it.size } ?: return null
    if (best.map { it.first }.distinct().size < MIN_TOWN_STOPS || best.size * 2 < names.size) return null
    return best.groupingBy { it.second }.eachCount().maxByOrNull { it.value }?.key
}

private const val MIN_TOWN_STOPS = 3

/** The town [name] names (see [splitStopTown]); null where it names none. */
fun townOf(name: String?): String? = name?.let { splitStopTown(it).town }

// A stop part that is only the word for the main station or a station of its town, as the
// regional providers name the stations of other towns ("Leipzig, Hauptbahnhof", "Bitterfeld, Bahnhof").
private val MAIN_STATION_WORD = Regex("^(?:hauptbahnhof|hbf\\.?)$", RegexOption.IGNORE_CASE)
private val STATION_ONLY_WORD = Regex("^(?:bahnhof|bf\\.?|bhf\\.?)$", RegexOption.IGNORE_CASE)

/**
 * The name of a stop to show in a list that belongs to the town [reference] (a board, a
 * ride): the stop alone where it lies in that town or its name says no town, else compactly
 * "Kirchstr. · Zagelsdorf". Without a [reference] a town the name says is always shown.
 *
 * A station named only by its town ("Leipzig, Hauptbahnhof", "Bitterfeld, Bahnhof", as the
 * regional providers name long-distance stations elsewhere) reads as the railway names it:
 * "Leipzig Hbf" always, "Bitterfeld" in another town; in its own town a plain "Bahnhof" stays
 * (the station of the town a local bus runs to), never "Hauptbahnhof · Leipzig".
 */
fun townedStopName(name: String, reference: String?): String {
    val split = splitStopTown(name)
    val town = split.town ?: return name.plainStopName()
    val stop = split.stop.plainStopName().ifEmpty { name.plainStopName() }
    if (MAIN_STATION_WORD.matches(stop)) return "$town Hbf"
    if (sameTown(town, reference)) return stop
    if (STATION_ONLY_WORD.matches(stop)) return town
    return "$stop · $town"
}

/**
 * The town of the ride [this]: of its boarding stop, else of its first stop that names one.
 * Its stops in other towns say theirs (see [townedStopName]).
 */
val Leg.rideTown: String?
    get() = townOf(from) ?: fromPlace?.areas?.firstOrNull() ?: stopovers.firstNotNullOfOrNull { townOf(it.name) }
