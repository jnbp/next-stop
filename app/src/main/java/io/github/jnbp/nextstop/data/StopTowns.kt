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

// #278: a street or a square in brackets tells two nearby stops apart ("Bf Neustadt (Hansastr.)"
// beside "Bahnhof Neustadt"): never a town, so it stays in the name everywhere.
private val STREET_QUALIFIER = Regex("""(?:str\.?|straße|strasse|weg|allee|platz|gasse|ufer|chaussee|damm)$""")

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
        '&' in plain || STREET_QUALIFIER.containsMatchIn(plain)
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
        // A stop that is only a generic word ("Hauptbahnhof (Musterstadt)") keeps its town (#255).
        town != null && town.town != null && sameTown(town.town, home) -> stationWithTownIfBare(town.stop, town.town)
        else -> withoutTownAfterComma(rest, home)
    }
    return StopNameParts(title.ifEmpty { name.trim() }, parts.filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { null })
}

// "Stop, Town" at the end of a name.
private val TOWN_AFTER_COMMA = Regex("""^(.*\S),\s*([^,()\[\]]+)$""")

/** [name] without ", Town" at its end where that town is [home] ("Rathaus, Springfield" in Springfield); else as it is. */
private fun withoutTownAfterComma(name: String, home: String?): String {
    val match = TOWN_AFTER_COMMA.matchEntire(name) ?: return name
    val town = match.groupValues[2].trim()
    return if (sameTown(town, home)) stationWithTownIfBare(match.groupValues[1].trim(), town) else name
}

// A name that is only a generic word for a station, which every town has (#255).
private val BARE_STATION_WORD = Regex("^(?:hauptbahnhof|hbf\\.?|bahnhof|bf\\.?|bhf\\.?|busbahnhof|zob)$", RegexOption.IGNORE_CASE)

/** True for a name that is only a generic word for a station ("Hauptbahnhof", "Hbf", "Bahnhof", "Busbahnhof", "ZOB", #255). */
fun isBareStationWord(name: String): Boolean = BARE_STATION_WORD.matches(name.trim())

/**
 * The generic station word [word] with its [town] (#255), as the railway names it: "Musterstadt
 * Hbf", "Musterstadt" for a plain "Bahnhof", "Musterstadt ZOB".
 */
fun stationWithTown(word: String, town: String): String {
    val plain = word.trim()
    return when {
        MAIN_STATION_WORD.matches(plain) -> "$town Hbf"
        STATION_ONLY_WORD.matches(plain) -> town
        else -> "$town $plain"
    }
}

/**
 * [stop] without its [town] where that town goes unsaid, but a stop that is only a generic word
 * never stands alone (#255: "München Hbf" read "Hauptbahnhof"): then [stationWithTown].
 */
private fun stationWithTownIfBare(stop: String, town: String): String =
    if (isBareStationWord(stop)) stationWithTown(stop, town) else stop

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

/**
 * #278: the [shown] names of the stops [names] of one list (a course, the stops of a ride), where
 * two different stops would read as the same one ("Bahnhof" and "Bf" alike, a part in square
 * brackets left out): those show the part of the station too, and where that still reads alike,
 * the name as the provider sent it. The same stop twice (a loop) stays as it is.
 */
fun distinctStopNames(names: List<String>, shown: List<String>): List<String> {
    if (names.size != shown.size || names.size < 2) return shown
    val out = shown.toMutableList()
    for (round in 0..1) {
        val groups = out.indices.groupBy { readStopKey(out[it]) }
        for (group in groups.values) {
            if (group.size < 2 || group.map { rawStopKey(names[it]) }.distinct().size < 2) continue
            for (index in group) {
                val part = stopNameParts(names[index]).part
                out[index] = if (round == 0 && part != null && !out[index].endsWith(part)) out[index] + " · " + part else names[index].trim().replace(SPACES, " ")
            }
        }
    }
    return out
}

// How a shown name reads: small letters, "Bahnhof", "Bhf" and "Bf" alike, "Straße" as "str", only letters and digits.
private fun readStopKey(name: String): String =
    name.lowercase(Locale.ROOT).replace(READ_STATION_WORD, "bf").replace("straße", "str").replace("strasse", "str")
        .filter { it.isLetterOrDigit() }

private fun rawStopKey(name: String): String = name.trim().replace(SPACES, " ").lowercase(Locale.ROOT)

private val READ_STATION_WORD = Regex("\\b(?:bahnhof|bhf|bf)\\b\\.?")

/**
 * The names of the stops [stops] of the ride [leg] as one list shows them (see [rideStopName]):
 * the first is its boarding stop and the last where it ends ([Leg.from], [Leg.to]), and never
 * two different stops alike (#278, see [distinctStopNames]).
 */
fun shownRideStopNames(leg: Leg, stops: List<Stopover>, town: String?): List<String> {
    val names = stops.mapIndexed { index, stop ->
        when {
            stops.size < 2 -> stop.name
            index == 0 -> leg.from
            index == stops.lastIndex -> leg.to
            else -> stop.name
        }
    }
    return distinctStopNames(names, names.map { rideStopName(leg, it, town) })
}

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

/**
 * [name] as the railway names the station on a long-distance ride (#93, DB style): without the
 * signs of the network in front ("S+U Berlin Hauptbahnhof" is "Berlin Hbf"), "Hauptbahnhof" as
 * "Hbf", "Fernbahnhof" as "Fernbf", without a "Bahnhof", "Bhf" or "Bf" behind ("Fulda",
 * "Köln Messe/Deutz"), the town of a regional provider's name in front ("S Südkreuz Bhf
 * (Berlin)" is "Berlin Südkreuz", "Leipzig, Hauptbahnhof" is "Leipzig Hbf"). Only for showing:
 * the name itself stays what requests and ids use. A name that is only the word stays.
 */
fun railwayStationName(name: String): String {
    var rest = name.trim().replace(SPACES, " ")
    for (sign in STOP_SIGNS) rest = rest.removePrefix(sign)
    if (MAIN_STATION_WORD.matches(rest) || RAILWAY_ONLY_WORD.matches(rest)) return name.trim()
    val split = splitStopTown(rest)
    val town = split.town
    var stop = if (town != null) split.stop else rest
    stop = stop.replace(RAILWAY_MAIN, "Hbf").replace(RAILWAY_LONG, "Fernbf")
    if (!RAILWAY_ONLY_WORD.matches(stop)) stop = stop.replace(RAILWAY_STATION_WORD, "")
    stop = stop.trim()
    if (town == null) return stop.ifEmpty { name.trim() }
    // The town in front, unless the stop names it already.
    return when {
        RAILWAY_ONLY_WORD.matches(stop) || stop.isEmpty() -> town
        stop.startsWith(town) -> stop
        else -> "$town $stop"
    }
}

private val RAILWAY_MAIN = Regex("\\bHauptbahnhof\\b")
private val RAILWAY_LONG = Regex("\\bFernbahnhof\\b")
private val RAILWAY_STATION_WORD = Regex("\\s+(?:Bahnhof|Bhf\\.?|Bf\\.?)$")
private val RAILWAY_ONLY_WORD = Regex("^(?:Bahnhof|Bhf\\.?|Bf\\.?)$")

/** True for a ride whose stations read as the railway names them (see [railwayStationName]). */
val Leg.railwayNames: Boolean get() = !walking && product == "express"

/** True for a regional train (RE, RB), whose stations get the railway's short words but keep their name otherwise (#168). */
val Leg.regionalRailNames: Boolean get() = !walking && product == "regional"

/**
 * [name] with the railway's short words (#168), nothing else: "Lutherstadt Wittenberg,
 * Hauptbahnhof" and "Cottbus Hauptbahnhof" as "... Hbf", "Frankfurt (Oder), Bahnhof" and
 * "... Bhf" without the word. Signs in front ("S", "U", "S+U") stay, as on the local network's
 * signs; a name that is only the word stays.
 */
fun railShortName(name: String): String {
    val plain = name.plainStopName().trim()
    if (MAIN_STATION_WORD.matches(plain) || RAILWAY_ONLY_WORD.matches(plain)) return plain
    val short = plain.replace(RAIL_MAIN_END, " Hbf").replace(RAIL_STATION_END, "").trim()
    return short.ifEmpty { plain }
}

// "..., Hauptbahnhof" or "... Hauptbahnhof" at the end of a name.
private val RAIL_MAIN_END = Regex(",?\\s+Hauptbahnhof$")

// "..., Bahnhof", "... Bahnhof", "... Bhf", "... Bf" at the end of a name.
private val RAIL_STATION_END = Regex(",?\\s+(?:Bahnhof|Bhf\\.?|Bf\\.?)$")

/**
 * The name of a stop of the ride [leg] in a list: railway style on a long-distance ride (see
 * [railwayStationName]), else as [townedStopName] gives it for [town].
 */
fun rideStopName(leg: Leg, name: String, town: String?): String = when {
    leg.railwayNames -> railwayStationName(name).withoutStationPart()
    leg.regionalRailNames -> railShortName(townedStopName(name, town))
    else -> townedStopName(name, town)
}

/** A place of the ride [leg] on its own (a step, a direction): railway style on a long-distance ride, else [plainStopName]. */
fun shownRideStop(leg: Leg?, name: String): String {
    val shown = when {
        leg?.railwayNames == true -> railwayStationName(name).withoutStationPart()
        leg?.regionalRailNames == true -> railShortName(name)
        else -> name.plainStopName()
    }
    // Never a bare "Hauptbahnhof" where the name says its town (#255).
    if (!isBareStationWord(shown)) return shown
    val town = townOf(name.replace(Regex("\\s*\\[[^\\]]*\\]$"), "")) ?: return shown
    return stationWithTown(shown, town)
}

/**
 * The destination of a trip whose last leg is [last], short as a small screen shows it (#146):
 * the end of a last ride as that ride names it ([shownRideStop], railway style on a long-distance
 * ride), a stop by its plain name, an address or a point of interest by the part before its
 * first comma ("Town Hall" of "Town Hall, Main Street 1, 12345 Springfield"), without postcode,
 * town or district.
 */
fun shortDestinationName(last: Leg): String {
    if (!last.walking) return shownRideStop(last, last.to)
    val name = last.to.plainStopName()
    val kind = last.toPlace?.kind
    val place = kind == PlaceKind.Poi || kind == PlaceKind.Address || (kind == null && DESTINATION_POSTCODE.containsMatchIn(name))
    if (!place) return name
    return name.substringBefore(',').trim().ifEmpty { name }
}

// A postcode in a name of unknown kind marks an address ("..., 12345 Town").
private val DESTINATION_POSTCODE = Regex("""\b\d{4,5}\b""")

private fun String.withoutStationPart(): String = replace(Regex("\\s*\\[[^\\]]*\\]$"), "")

// A direction that is only the word for a kind of place, which every town has (#85).
private val GENERIC_HEADSIGN = Regex(
    "^(?:hauptbahnhof|hbf\\.?|bahnhof|bf\\.?|bhf\\.?|ostbahnhof|westbahnhof|südbahnhof|nordbahnhof|messe|flughafen|zentrum|zob|busbahnhof)$",
    RegexOption.IGNORE_CASE,
)

/**
 * The direction of a trip as shown: [headsign] as the feed has it, but where it is only the word
 * for a kind of place ("Hauptbahnhof", "Messe", #85: a train to Frankfurt said "Hauptbahnhof"),
 * the name of the trip's last stop [tripEnd] when that holds the word: "Frankfurt (Main) Hbf",
 * "Leipzig Hbf". Without a headsign the last stop; otherwise as it is.
 */
fun fullHeadsign(headsign: String?, tripEnd: String?): String? {
    val sign = headsign?.trim()?.takeIf { it.isNotEmpty() } ?: return tripEnd
    if (tripEnd == null || !GENERIC_HEADSIGN.matches(sign)) return sign
    val main = MAIN_STATION_WORD.matches(sign)
    val word = if (main) "hauptbahnhof" else sign.lowercase().removeSuffix(".")
    val end = tripEnd.trim()
    val holds = end.lowercase().contains(word) || (main && Regex("\\bhbf\\b", RegexOption.IGNORE_CASE).containsMatchIn(end))
    if (!holds || end.equals(sign, ignoreCase = true)) return sign
    return if (main) railwayStationName(end) else end
}

/** The direction [direction] of the ride [leg] as sent on: railway style on a long-distance ride, else as it is. */
fun railwayDirection(leg: Leg?, direction: String): String = when {
    leg?.railwayNames == true -> railwayStationName(direction).withoutStationPart()
    // A regional train: the railway's short words, the name otherwise as sent (#168).
    leg?.regionalRailNames == true -> railShortName(direction)
    else -> direction
}
