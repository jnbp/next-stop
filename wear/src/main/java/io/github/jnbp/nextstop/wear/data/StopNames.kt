package io.github.jnbp.nextstop.wear.data

import java.util.Locale

/*
 * Stop names as the watch shows them: without the town the watch is used in ("S Ostkreuz Bhf
 * (Berlin)" is "S Ostkreuz Bhf" in Berlin, "S Golm (Potsdam)" keeps its town there) and with the
 * part of a big station a provider writes in square brackets ("[Gleis 1-8]") apart, for a small
 * line below. The raw name stays what requests, ids and the cache use; this is only for showing.
 *
 * Twin of stopNameParts, sameTown, isTownAbbreviation and dominantTown in the phone's
 * data/StopTowns.kt (the watch cannot use the phone's code): change both in one commit and run
 * both tests. The town the watch is used in is [WatchTown], taken from the stops around as the
 * phone's LocalTown does.
 */

/**
 * A stop's name as the watch shows it: [title] the name (with the town in brackets only where it
 * is another than the town the watch is used in), [part] what the provider writes in square
 * brackets behind it, the part of a big station the stop is ("Bus K.-Liebknecht-Str.", "Gleis 1-8");
 * null where there is none.
 */
data class StopNameParts(val title: String, val part: String?)

// "Stop (Town)" at the end of a name; the town without brackets of its own.
private val NAME_TOWN_IN_BRACKETS = Regex("""^(.*\S)\s+\(([^()\[\]]+)\)$""")

// One part in square brackets at the end of a name, and the abbreviation "Str" without its dot.
private val NAME_TRAILING_PART = Regex("""^(.*?\S)\s*\[([^\[\]]+)\]$""")
private val NAME_STREET_WITHOUT_DOT = Regex("""(?<=[Ss]tr)(?=$|[\s,/)])""")
private val NAME_SPACES = Regex("\\s+")

// Words in brackets that belong to the name of a town ("Frankfurt (Oder)"), tell a part of a town
// or a stop ("(Nord)", "(Kehre)") or say something else ("(Bus)"): never a town of their own. The
// same list as NOT_A_TOWN of the phone (found over all packs with tools/audit_sign_merges.py).
private val NAME_NOT_A_TOWN = setOf(
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
    "oder", "havel", "mark", "spree", "saale", "elbe", "main", "dosse", "prignitz", "elster",
    "uckermark", "altmark", "ostprignitz", "westf", "westf.", "rhld", "rhld.", "oldb", "oldb.", "sachs",
    "sachs.", "holst", "holst.", "württ", "württ.", "hess", "hess.", "mecklenburg", "vorpommern", "ruppin",
    "bus", "tram", "ubahn", "u-bahn", "sbahn", "s-bahn", "zug", "bahn", "ersatzverkehr", "sev",
    "tief", "oben", "unten",
)

// #278: a street or a square in brackets tells two nearby stops apart ("Bf Neustadt (Hansastr.)"
// beside "Bahnhof Neustadt"): never a town, so it stays in the name (the phone's STREET_QUALIFIER).
private val NAME_STREET_QUALIFIER = Regex("""(?:str\.?|straße|strasse|weg|allee|platz|gasse|ufer|chaussee|damm)$""")

/** "Stop (Town)" at the end of [whole] as stop and town; null where the brackets name no town. */
private fun nameTownInBrackets(whole: String): Pair<String, String>? {
    val bracket = NAME_TOWN_IN_BRACKETS.matchEntire(whole) ?: return null
    val town = bracket.groupValues[2].trim()
    val plain = town.lowercase(Locale.ROOT)
    val letters = town.count { it.isLetter() }
    val districtCode = town.length <= 4 && town.all { it.isUpperCase() || !it.isLetter() }
    val qualifier = plain in NAME_NOT_A_TOWN || plain.startsWith("b ") || plain.startsWith("b. ") ||
        plain.startsWith("bei ") || plain.startsWith("kr ") || plain.startsWith("kr. ") || town.any { it.isDigit() } ||
        letters < 3 || districtCode || plain.startsWith("richtung ") || plain.startsWith("ri. ") || plain.startsWith("fr ") ||
        '&' in plain || NAME_STREET_QUALIFIER.containsMatchIn(plain)
    return if (!qualifier && town.isNotEmpty()) bracket.groupValues[1].trim() to town else null
}

// A town for comparing: small letters, without a part in brackets, "/" or "-" as a blank.
private fun nameTownKey(town: String): String =
    town.lowercase(Locale.ROOT).replace(Regex("\\([^)]*\\)"), "").replace(Regex("[/\\-]"), " ")
        .replace(NAME_SPACES, " ").trim()

/** Whether two towns are the same, however each name writes it ("Dahme/Mark", "Dahme (Mark)", "Bln" for Berlin). */
fun sameTown(a: String?, b: String?): Boolean {
    if (a == null || b == null) return false
    val keyA = nameTownKey(a)
    val keyB = nameTownKey(b)
    if (keyA.isEmpty() || keyB.isEmpty()) return a.equals(b, ignoreCase = true)
    return keyA == keyB || keyA.substringBefore(' ') == keyB && keyB.length >= 4 || keyB.substringBefore(' ') == keyA && keyA.length >= 4 ||
        isTownAbbreviation(keyA, keyB) || isTownAbbreviation(keyB, keyA)
}

/**
 * True when [short] is a short form of the town [long] as the providers write them in brackets:
 * two to four letters, the first one the town's, all of them in its name in that order ("Bln"
 * for Berlin, "Ffm" for Frankfurt am Main).
 */
fun isTownAbbreviation(short: String, long: String): Boolean {
    val letters = short.lowercase(Locale.ROOT).filter { it.isLetter() }
    val full = long.lowercase(Locale.ROOT).filter { it.isLetter() }
    if (letters.length !in 2..4 || full.length <= letters.length || letters[0] != full[0]) return false
    if (short.trim().removeSuffix(".").any { !it.isLetter() }) return false
    var at = 0
    for (letter in full) if (at < letters.length && letter == letters[at]) at++
    return at == letters.length
}

/**
 * [name] as [StopNameParts], for every provider alike: "S+U Alexanderplatz (Bln) [Bus
 * K.-Liebknecht-Str]" is "S+U Alexanderplatz" with the part "Bus K.-Liebknecht-Str." where the
 * watch is used in Berlin ([home], see [WatchTown]); "S Golm (Potsdam)" keeps its town there.
 * Brackets that belong to a town's name ("Frankfurt (Oder)") stay.
 */
fun stopNameParts(name: String, home: String? = WatchTown.reference): StopNameParts {
    var rest = name.trim().replace(NAME_SPACES, " ")
    val parts = ArrayList<String>(1)
    while (true) {
        val match = NAME_TRAILING_PART.matchEntire(rest) ?: break
        parts.add(0, match.groupValues[2].trim().replace(NAME_STREET_WITHOUT_DOT, "."))
        rest = match.groupValues[1].trim()
    }
    // Generic English words at the end came in the watch's language already where the provider's
    // answer was read (see localStopTerms, which knows there whether the stop lies in Germany).
    // "S+U Zoologischer Garten Bhf" as "S+U Zoologischer Garten": the S+U says it is a station.
    rest = withoutRedundantStationWord(rest)
    val town = nameTownInBrackets(rest)
    val title = when {
        // A stop that is only a generic word ("Hauptbahnhof (Musterstadt)") keeps its town (#255).
        town != null && sameTown(town.second, home) -> nameStationWithTownIfBare(town.first, town.second)
        else -> withoutTownAfterComma(rest, home)
    }
    return StopNameParts(title.ifEmpty { name.trim() }, parts.filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { null })
}

// "Stop, Town" at the end of a name.
private val NAME_TOWN_AFTER_COMMA = Regex("""^(.*\S),\s*([^,()\[\]]+)$""")

/** [name] without ", Town" at its end where that town is [home] ("Rathaus, Springfield" in Springfield); else as it is. */
private fun withoutTownAfterComma(name: String, home: String?): String {
    val match = NAME_TOWN_AFTER_COMMA.matchEntire(name) ?: return name
    val town = match.groupValues[2].trim()
    return if (sameTown(town, home)) nameStationWithTownIfBare(match.groupValues[1].trim(), town) else name
}

// A name that is only a generic word for a station, which every town has (#255), and those for the main and a plain station.
private val NAME_BARE_STATION_WORD = Regex("^(?:hauptbahnhof|hbf\\.?|bahnhof|bf\\.?|bhf\\.?|busbahnhof|zob)$", RegexOption.IGNORE_CASE)
private val NAME_MAIN_STATION_WORD = Regex("^(?:hauptbahnhof|hbf\\.?)$", RegexOption.IGNORE_CASE)
private val NAME_STATION_ONLY_WORD = Regex("^(?:bahnhof|bf\\.?|bhf\\.?)$", RegexOption.IGNORE_CASE)

/**
 * [stop] without its [town] where that town goes unsaid, but a stop that is only a generic word
 * never stands alone (#255: "München Hbf" read "Hauptbahnhof"): "Musterstadt Hbf",
 * "Musterstadt" for a plain "Bahnhof", "Musterstadt ZOB". Twin of the phone's stationWithTown.
 */
private fun nameStationWithTownIfBare(stop: String, town: String): String = when {
    !NAME_BARE_STATION_WORD.matches(stop) -> stop
    NAME_MAIN_STATION_WORD.matches(stop) -> "$town Hbf"
    NAME_STATION_ONLY_WORD.matches(stop) -> town
    else -> "$town $stop"
}

/** The stop [name] in one line for a list: its title as shown (see [stopNameParts]), then the part of the station. */
fun listedStopName(name: String, home: String? = WatchTown.reference): String {
    val parts = stopNameParts(name, home)
    return if (parts.part == null) parts.title else parts.title + " · " + parts.part
}

/**
 * The town most of [names] name in brackets ("S+U Alexanderplatz (Bln) [U2]"); null where none
 * does for at least [MIN_TOWN_STOPS] stops of different names and half of all [names] (two
 * stops "U Freie Universität (Thielplatz)" name a place, not the town the watch is used in).
 */
fun dominantTown(names: List<String>): String? {
    val towns = names.mapNotNull { name ->
        var rest = name.trim().replace(NAME_SPACES, " ")
        while (true) rest = NAME_TRAILING_PART.matchEntire(rest)?.groupValues?.get(1)?.trim() ?: break
        nameTownInBrackets(rest)
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

// Stops of different names that must name a town before it counts as the town the watch is used in.
private const val MIN_TOWN_STOPS = 3

// The town until the stops around say otherwise: Berlin, the app's first region (as on the phone).
private const val DEFAULT_WATCH_TOWN = "Berlin"

/**
 * The town the watch is used in now, for the names of its stops: the one most of the stops
 * around name in brackets (see [noteStops], fed by every lookup of stops nearby), Berlin until
 * they say otherwise. Kept only while the app runs.
 */
object WatchTown {
    @Volatile
    var current: String? = null
        private set

    /** The town whose name the stops do not repeat. */
    val reference: String get() = current ?: DEFAULT_WATCH_TOWN

    /** Takes the town most of [names] (the stops around) name, where they name one. */
    fun noteStops(names: List<String>) {
        dominantTown(names)?.let { current = it }
    }
}

/**
 * The name of [place] as the watch shows it (#177): a stop without the town the watch is used in
 * (see [listedStopName]), an address or a point of interest as it came. The raw name stays what
 * searches and requests use.
 */
fun shownPlaceName(place: Place, home: String? = WatchTown.reference): String =
    if (place.kind == PlaceKind.Stop) listedStopName(place.name, home) else place.name

/** The name of [place] for a small second line under [label]: null where it would only repeat the label. */
fun placeSubline(label: String, place: Place, home: String? = WatchTown.reference): String? =
    shownPlaceName(place, home).takeIf { it != label }

/**
 * [trip] with its stop names as the watch shows them (#177), for a trip the watch follows itself:
 * the phone already sends the names short (plainStopName, rideStopName), the providers the watch
 * asks on its own write "S+U Westhafen (Berlin)". Only the shown names change; [TripSnapshot.alertKey]
 * and [TripSnapshot.target] stay as they were.
 */
fun tripWithShownNames(trip: TripSnapshot, home: String? = WatchTown.reference): TripSnapshot {
    if (trip.fromPhone) return trip
    fun shown(name: String): String = if (name.isBlank()) name else stopNameParts(name, home).title
    return trip.copy(
        place = shown(trip.place),
        destination = shown(trip.destination),
        direction = trip.direction?.let(::shown),
        rideTo = trip.rideTo?.let(::shown),
        next = trip.next?.let { it.copy(stop = shown(it.stop), direction = it.direction?.let(::shown)) },
        walk = trip.walk?.let { it.copy(from = shown(it.from), to = shown(it.to)) },
        stops = trip.stops.map { it.copy(name = shown(it.name)) },
        plan = trip.plan.map { it.copy(from = shown(it.from), to = shown(it.to), direction = it.direction?.let(::shown)) },
    )
}
