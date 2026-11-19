package io.github.jnbp.nextstop.data

import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/*
 * One station, many stops (#96, #85). Europe-wide data (Transitous, MOTIS) merges the feeds of
 * many railways: Frankfurt (Main) Hauptbahnhof comes from the German feed (DELFI, by its DHID
 * de:06412:10 and the platforms below it) and a second time from the SNCF's feed as "Francfort
 * sur le Main", about 100 m away, with only the trains to France. This file decides, for any
 * station of any feed and language, whether two stops are one station, by evidence:
 *
 *  1. hard ids: the same stop place (IFOPT/DHID "de:06412:10", also inside "de:06412:10:3:4",
 *     "de:06412:10_G", "Pat:45:50002", "Parentch:1:sloid:10"), the same VBB number, or the same
 *     UIC station code where a feed's id is one (SNCB "be-sncb_8015345", "be-sncb_S8814001",
 *     "eu-european-sleeper_8814001", DELFI "000008400058"). The SNCF's ids
 *     ("FR::LMO:<uuid>:") hold none: its stops are found by place and name;
 *  2. the same station complex of the bundled OpenStreetMap data (passed in, see complexOf);
 *  3. place and name: rail stops at most [TWIN_METERS] apart whose names agree once station
 *     words of many languages, articles, rivers ("am Main", "sur le Main", "(M)"), accents and
 *     brackets are gone, in any order ("Hauptbahnhof, Saarbrücken"), abbreviations read as the
 *     word they begin ("Fernb", "Fernbahnhof"); or, between stops of feeds of two countries at
 *     most [EXONYM_METERS] apart,
 *     names that differ only in the town (the town's name in another language: "Francfort",
 *     "Cologne", "Vienne"), read from the geography rather than from a word list: two rail
 *     stops of two railways this close, each named as the plain station of a town, are that
 *     town's station. A name with a part that tells stations of a town apart ("Ost", "Midi",
 *     "Karlsplatz") never takes this way.
 *
 * Which stop stands for the station (its name and id): the one of the feed of the country the
 * station lies in (by its hard ids), then the one with the most kinds of trains.
 * Pure logic, cached per stop: it runs for every pair of stops of the map.
 */

/** Rail stops of one station named alike lie at most this far apart. */
const val TWIN_METERS = 250.0

/**
 * Rail stops of feeds of two countries named after the town alone (in two languages) lie at most
 * this far apart (Transitous, 18 Nov 2026: "Francfort sur le Main" 117 m from Frankfurt (Main)
 * Hbf, "Munich" 93 m from München Hbf, "Sarrebruck" 2 m from Saarbrücken Hbf).
 */
const val EXONYM_METERS = 150.0

/** Stops of one hard id (stop place, UIC code) farther apart than this are not taken for one station. */
const val HARD_ID_METERS = 3_000.0

/** Kept for the callers of the first fix of #96: the distance of a foreign feed's twin. */
const val FOREIGN_TWIN_METERS = EXONYM_METERS

/** How two stops were found to be one station. */
enum class StationEvidence { HardId, Complex, Name, Exonym }

/** What the resolver reads off one stop, once. */
data class StationFacts(
    /** The MOTIS feed tag ("de-DELFI", "fr-sncf"); null for a stop of the local provider. */
    val feed: String?,
    /** The country of the feed ("de", "fr"); for a local provider's stop that of its ids. */
    val feedCountry: String?,
    /** The country the station lies in, by its hard ids; null where none tells. */
    val country: String?,
    /** The stop place (IFOPT/DHID) without its platform parts. */
    val place: String?,
    /** The VBB station number ("900" and six digits). */
    val vbb: String?,
    /** The UIC/EVA station code (seven digits). */
    val uic: String?,
    /** The name in one form: words without station words, articles, rivers, accents. */
    val tokens: List<String>,
    /** True when a word of the name tells stations of one town apart ("ost", "midi", "flughafen"). */
    val qualified: Boolean,
    /** The words in brackets ("berlin" of "S Ostkreuz Bhf (Berlin)"): where the provider names the town. */
    val bracketed: Set<String>,
    val rail: Boolean,
    val railKnown: Boolean,
)

object StationIdentity {
    private val cache = ConcurrentHashMap<String, StationFacts>()

    /** The facts of [stop], cached by its id, name and products. */
    fun facts(stop: Stop): StationFacts {
        val key = stop.id + '\u0001' + stop.name + '\u0001' + stop.products.size
        cache[key]?.let { return it }
        if (cache.size > 8_192) cache.clear()
        return compute(stop).also { cache[key] = it }
    }

    /** The feed tag of a MOTIS stop id ("tr:de-DELFI_de:06412:10" → "de-DELFI"); null for other ids. */
    fun feedOf(id: String): String? = FEED.find(id.removePrefix(TRANSITOUS_MARK))?.groupValues?.get(1)

    /** How [a] and [b] are one station; null when nothing says so. [complexOf]: OpenStreetMap's complex of a rail stop. */
    fun evidence(a: Stop, b: Stop, complexOf: ((Stop) -> String?)? = null): StationEvidence? {
        if (a.id == b.id && a.id.isNotEmpty()) return StationEvidence.HardId
        if (a.kind != PlaceKind.Stop || b.kind != PlaceKind.Stop) return null
        val at = a.position ?: return null
        val other = b.position ?: return null
        val meters = metersApart(at, other)
        if (meters > HARD_ID_METERS) return null
        val fa = facts(a)
        val fb = facts(b)
        // Equal codes only: some feeds number their stops with seven digits that are no UIC code.
        if (fa.uic != null && fa.uic == fb.uic && fa.rail && fb.rail) return StationEvidence.HardId
        if ((fa.place != null && fa.place == fb.place) || (fa.vbb != null && fa.vbb == fb.vbb)) return StationEvidence.HardId
        if (meters > TWIN_METERS || !fa.rail || !fb.rail) return null
        if (complexOf != null) {
            val ca = complexOf(a)
            if (ca != null && ca == complexOf(b)) return StationEvidence.Complex
        }
        val otherFeeds = fa.feed != fb.feed
        if (namesAgree(fa, fb, townMissing = otherFeeds)) return StationEvidence.Name
        val otherCountries = fa.feedCountry != null && fb.feedCountry != null && fa.feedCountry != fb.feedCountry
        // Two feeds that both know the local stop place and name different ones know better
        // (at-Railway's "Berlin-Spandau" and the VBB's "S+U Rathaus Spandau", 190 m).
        val placesDiffer = (fa.place != null && fb.place != null) || (fa.vbb != null && fb.vbb != null)
        if (otherCountries && !placesDiffer && meters <= EXONYM_METERS && fa.railKnown && fb.railKnown && townOnlyApart(fa, fb)) {
            return StationEvidence.Exonym
        }
        return null
    }

    /** True when [a] and [b] are one station (see [evidence]). */
    fun same(a: Stop, b: Stop, complexOf: ((Stop) -> String?)? = null): Boolean = evidence(a, b, complexOf) != null

    /**
     * True when [a] and [b] are one station listed by two feeds (different feed tags, or a local
     * provider's stop and one of Transitous): the evidence beyond ids and names of one feed.
     */
    fun otherFeedTwins(a: Stop, b: Stop): Boolean {
        if (facts(a).feed == facts(b).feed) return false
        return evidence(a, b) != null
    }

    /** The stops of [nearby] that are the station [stop] (not [stop] itself). */
    fun membersOf(stop: Stop, nearby: List<Stop>): List<Stop> =
        nearby.filter { it.id != stop.id && same(stop, it) }

    /**
     * The index in [stops] (one station) of the stop that stands for it: a stop of the feed of
     * the country the station lies in, then the one with the most kinds of trains, then the
     * first. Stops of no station country known are all of the right country.
     */
    fun preferredIndex(stops: List<Stop>): Int {
        if (stops.size < 2) return 0
        val country = stationCountry(stops)
        return stops.indices.minWithOrNull(
            compareBy<Int> { index -> if (country == null || facts(stops[index]).feedCountry == country) 0 else 1 }
                .thenByDescending { index -> stops[index].products.count { it in RAIL } }
                .thenBy { it },
        ) ?: 0
    }

    /** The country the stops of one station lie in, by the most of their hard ids; null where none tells. */
    fun stationCountry(stops: List<Stop>): String? =
        stops.mapNotNull { facts(it).country }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

    /**
     * A key of the station of [stop] to store (favourites, share links, the watch): its stop
     * place, else its UIC code, else its VBB number, else null (keep the stop's own id then).
     * A stop of any feed of that station gives the same key where the feeds share the id.
     */
    fun stationKey(stop: Stop): String? {
        val facts = facts(stop)
        return facts.place?.let { "ifopt:$it" } ?: facts.uic?.let { "uic:$it" } ?: facts.vbb?.let { "vbb:$it" }
    }

    // Station keys stored with ids (favourites, planned trips, links, the watch), and the stops
    // seen in answers of this session by key and the feeds they came from (see resolveId).
    private val storedKeys = ConcurrentHashMap<String, String>()
    private val seenByKey = ConcurrentHashMap<String, String>()
    private val seenFeeds = ConcurrentHashMap.newKeySet<String>()

    /** Remembers that the stored stop [id] is the station [key] (read back with the id); nothing for a null key. */
    fun noteStored(id: String, key: String?) {
        if (key.isNullOrBlank() || id.isBlank()) return
        if (storedKeys.size > 4_096) storedKeys.clear()
        storedKeys[id] = key
    }

    /**
     * Remembers a stop a provider sent now: its feed is alive, and its station key stands for it.
     * A stop of the feed of the station's own country wins over others of the same key. Cheap:
     * cached facts and two map writes.
     */
    fun noteSeen(stop: Stop) {
        val facts = facts(stop)
        facts.feed?.let { seenFeeds.add(it) }
        val key = stationKey(stop) ?: return
        if (seenByKey.size > 8_192) seenByKey.clear()
        val known = seenByKey[key]
        val local = facts.country != null && facts.feedCountry == facts.country
        if (known == null || local) seenByKey[key] = stop.id
    }

    /**
     * The id to ask a provider for the stored stop [id]: itself, unless its feed has not been
     * seen in any answer of this session while a stop of its stored station key has been: a
     * feed that was renamed or retired ("at-Railway-Current-Reference-Data-2026" after its
     * year), whose old id no longer answers. Never touches ids of the local provider (no feed).
     */
    fun resolveId(id: String): String {
        val key = storedKeys[id] ?: return id
        val feed = feedOf(id) ?: return id
        if (feed in seenFeeds) return id
        return seenByKey[key] ?: id
    }

    /** The words of a station name in one form (see [StationFacts.tokens]). */
    fun nameTokens(name: String): List<String> = tokensOf(name).first

    /**
     * True when two names (of a direction, say) name one place: the same words once station
     * words and the like are gone, one the other without its town in front, or each the
     * plain name of a town in another language ("Paris Est", "Paris-Est"; not "Paris Nord").
     * Without positions, so for the directions of two runs that are one train already.
     */
    fun namesMatch(a: String, b: String): Boolean {
        val (ta, qa) = tokensOf(a)
        val (tb, qb) = tokensOf(b)
        if (ta.isEmpty() || tb.isEmpty()) return false
        val fa = StationFacts(null, null, null, null, null, null, ta, qa, bracketWords(a), true, true)
        val fb = StationFacts(null, null, null, null, null, null, tb, qb, bracketWords(b), true, true)
        return namesAgree(fa, fb, townMissing = true)
    }

    /**
     * True when two names (the directions of two runs of one train number at one minute) may
     * name one place: [namesMatch], or both the plain name of a town, perhaps in two languages
     * ("Frankfurt (Main) Hauptbahnhof" and "Francfort sur le Main"), or the same first word
     * ("Paris Gare de Lyon", "Paris Gare de Lyon Hall 1 - 2"), or one with no name left ("Hauptbahnhof").
     */
    fun directionsMayMatch(a: String, b: String): Boolean {
        val (ta, qa) = tokensOf(a)
        val (tb, qb) = tokensOf(b)
        if (ta.isEmpty() || tb.isEmpty()) return true
        if (sameWord(ta[0], tb[0]) || namesMatch(a, b)) return true
        val fa = StationFacts(null, null, null, null, null, null, ta, qa, emptySet(), true, true)
        val fb = StationFacts(null, null, null, null, null, null, tb, qb, emptySet(), true, true)
        return townOnlyApart(fa, fb)
    }

    // The words of [a] that [b] has no word for (see sameWord), each word of [b] used once.
    private fun unmatched(a: List<String>, b: List<String>): List<String> {
        val free = b.toMutableList()
        return a.filter { word ->
            val at = free.indexOfFirst { sameWord(it, word) }
            if (at >= 0) free.removeAt(at)
            at < 0
        }
    }

    // One word, or one the abbreviation of the other ("fernb", "fernbahnhof"; at least four letters).
    private fun sameWord(a: String, b: String): Boolean {
        if (a == b) return true
        val (short, long) = if (a.length <= b.length) a to b else b to a
        return short.length >= 4 && long.startsWith(short)
    }

    private fun namesAgree(a: StationFacts, b: StationFacts, townMissing: Boolean): Boolean {
        val onlyA = unmatched(a.tokens, b.tokens)
        val onlyB = unmatched(b.tokens, a.tokens)
        if (onlyA.isEmpty() && onlyB.isEmpty()) return true
        // Between feeds: one name with the town, the other without ("Zoologischer Garten",
        // "Berlin Zoologischer Garten"), something of the name left on both.
        if (!townMissing || (onlyA.isNotEmpty() && onlyB.isNotEmpty())) return false
        val extra = onlyA.ifEmpty { onlyB }
        val shorter = if (onlyA.isEmpty()) a else b
        val longer = if (onlyA.isEmpty()) b else a
        if (shorter.tokens.isEmpty() || extra.size != 1 || extra[0] in QUALIFIERS) return false
        // The town: in the other's brackets ("(Berlin)"), else in front ("Berlin Ostkreuz"), never
        // a street behind ("Frankfurt am Main, Hauptbahnhof/Karlstraße" is no name of the station).
        return extra[0] in shorter.bracketed || longer.tokens.first() == extra[0]
    }

    // The names differ only in the town (a word or two each, "aix chapelle"), beside what both
    // share ("sbb", the side of town: "Brussel-Zuid", "Bruxelles-Midi"), and what differs has no
    // word that tells stations of a town apart ("Offenbach Ost" is no name of Frankfurt Hbf).
    private fun townOnlyApart(a: StationFacts, b: StationFacts): Boolean {
        if (a.tokens.isEmpty() || b.tokens.isEmpty()) return false
        val townA = unmatched(a.tokens, b.tokens)
        val townB = unmatched(b.tokens, a.tokens)
        if (townA.any { it in QUALIFIERS || it in COMPASS_TOKENS } || townB.any { it in QUALIFIERS || it in COMPASS_TOKENS }) return false
        return townA.isNotEmpty() && townB.isNotEmpty() && townA.size <= 2 && townB.size <= 2
    }

    /**
     * True when the stops [a] and [b] are one stop place by their ids: the same id, VBB number,
     * stop place (DHID/IFOPT) or UIC code; for ids of none of these kinds (a HAFAS id) the same
     * id in its plain form (see normalStationId). Unlike normalStationId alone, two platforms of
     * different stations ("de:06412:10:3:4", "de:08111:6115:3:4") never match by their last part.
     */
    fun sameStopPlaceId(a: String, b: String): Boolean =
        sameStopPlace(Stop(a, "", null, null, null), Stop(b, "", null, null, null))

    /** As [sameStopPlaceId], for two stops. */
    fun sameStopPlace(a: Stop, b: Stop): Boolean {
        if (a.id == b.id) return a.id.isNotEmpty()
        val fa = facts(a)
        val fb = facts(b)
        if (fa.vbb != null || fb.vbb != null) return fa.vbb == fb.vbb
        if (fa.place != null || fb.place != null) return fa.place == fb.place
        if (fa.uic != null || fb.uic != null) return fa.uic == fb.uic
        return normalStationId(a.id) == normalStationId(b.id)
    }

    private fun compute(stop: Stop): StationFacts {
        val raw = stop.id.removePrefix(TRANSITOUS_MARK)
        val feedMatch = FEED.find(raw)
        val feed = feedMatch?.groupValues?.get(1)
        val local = feedMatch?.groupValues?.get(2) ?: raw
        val place = placeOf(local)
        val vbb = normalStationId(local).takeIf { it.length == 9 && it.startsWith("900") && it.all(Char::isDigit) }
        // A stop place numbered by its UIC code ("cz:0:5457076", "de:14713:8010205") has that code too.
        val uic = (if (feed != null) uicOf(local, padded = feed.startsWith("de-")) else null)
            ?: place?.substringAfterLast(':')?.takeIf { it.length == 7 && it[0] != '0' && it.all(Char::isDigit) && it.take(2) in UIC_COUNTRIES }
        val country = place?.substringBefore(':') ?: uic?.let { UIC_COUNTRIES[it.take(2)] } ?: vbb?.let { "de" }
        val feedCountry = feed?.substringBefore('-')?.lowercase()?.takeIf { it.length == 2 }
            ?: if (feed == null) (place?.substringBefore(':') ?: vbb?.let { "de" } ?: "de") else null
        val (tokens, qualified) = tokensOf(stop.name)
        val railKnown = stop.products.any { it in RAIL }
        val rail = railKnown || (stop.products.isEmpty() && !stop.id.startsWith(OSM_STOP_PREFIX))
        return StationFacts(feed, feedCountry, country, place, vbb, uic, tokens, qualified, bracketWords(stop.name), rail, railKnown)
    }

    private fun placeOf(id: String): String? {
        // A parent written in front ("Pat:45:50002", "Parentch:1:sloid:10").
        val local = PARENT.replace(id, "")
        val match = IFOPT.find(local) ?: return null
        val (country, area, place) = match.destructured
        if (place == "sloid") {
            val number = local.substring(match.range.last + 1).removePrefix(":").substringBefore(':')
            return if (number.isEmpty()) null else "$country:$area:sloid:$number"
        }
        // Transitous marks stations it grouped with "_G" ("de:06412:10_G", "de:10041:8000323_G_G").
        return "$country:$area:${place.replace(GROUP_MARK, "")}"
    }

    // [padded]: a German feed, whose own ids are numbers too; only its zero-padded form
    // ("000008400058", Amsterdam Centraal in DELFI) is a UIC code there.
    private fun uicOf(local: String, padded: Boolean): String? {
        val code = (if (padded) PADDED_UIC else PLAIN_UIC).find(local)?.groupValues?.get(1) ?: return null
        return code.takeIf { it.take(2) in UIC_COUNTRIES }
    }

    private fun tokensOf(name: String): Pair<List<String>, Boolean> {
        var text = Normalizer.normalize(
            name.lowercase().replace("ß", "ss").replace("ł", "l").replace("ø", "o").replace("æ", "ae").replace("œ", "oe"),
            Normalizer.Form.NFD,
        ).replace(MARKS, "")
        text = text.replace(BRACKETS, " ").replace(RIVER, " ")
            .replace("strasse", "str").replace("platz", "pl")
        val words = text.split(SPLIT).filter { it.isNotEmpty() }
        // Prefixes of the S-Bahn and U-Bahn ("S+U", "S", "U") and station words go; the side of
        // town is one word in every language ("Zuid", "Midi", "Süd").
        val kept = words.filter { !isStationWord(it) && it !in ARTICLES }.map { COMPASS[it] ?: it }
        return kept to kept.any { it in QUALIFIERS }
    }

    private fun bracketWords(name: String): Set<String> =
        BRACKETS.findAll(name).flatMapTo(HashSet()) { tokensOf(it.value.drop(1).dropLast(1)).first }

    // A station word, or its abbreviation ("hovedb." of "hovedbanegard", "København Hovedb.").
    private fun isStationWord(word: String): Boolean =
        word in STATION_WORDS || (word.length >= 4 && LONG_STATION_WORDS.any { it.startsWith(word) })

    private fun metersApart(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val north = (b.first - a.first) * 111_320.0
        val east = (b.second - a.second) * 111_320.0 * cos((a.first + b.first) / 2 * PI / 180)
        return sqrt(north * north + east * east)
    }

    private const val TRANSITOUS_MARK = "tr:"
    private val RAIL = setOf("suburban", "subway", "regional", "express")
    // A MOTIS id: the feed's tag ("de-DELFI", "fr-sncf"), an underscore, the feed's own id.
    private val FEED = Regex("^([a-z]{2}(?:-[A-Za-z0-9.]+)+)_(.+)$")
    // A stop place of IFOPT (DHID in Germany): country, area, place; platforms may follow.
    private val IFOPT = Regex("(?<![0-9A-Za-z])([a-z]{2}):([0-9]{1,6}):([0-9A-Za-z_]+)")
    private val PARENT = Regex("^(?:Parent|parent|P)(?=[a-z]{2}:)")
    private val GROUP_MARK = Regex("(_G)+$")
    // A feed id that is a UIC code, alone or with its platform ("8015345", "S8814001", "8503000:0:7", "Parent8503000").
    private val PLAIN_UIC = Regex("^(?:Parent|parent|S)?0*([1-9][0-9]{6})(?:$|[:_ ])")
    private val PADDED_UIC = Regex("^0{3,}([1-9][0-9]{6})$")
    // The UIC country codes of the railways of Europe and their countries.
    private val UIC_COUNTRIES = mapOf(
        "10" to "fi", "51" to "pl", "53" to "ro", "54" to "cz", "55" to "hu", "56" to "sk", "70" to "gb",
        "71" to "es", "74" to "se", "76" to "no", "78" to "hr", "79" to "si", "80" to "de", "81" to "at",
        "82" to "lu", "83" to "it", "84" to "nl", "85" to "ch", "86" to "dk", "87" to "fr", "88" to "be", "94" to "pt",
    )
    private val MARKS = Regex("\\p{M}+")
    private val BRACKETS = Regex("\\([^)]*\\)|\\[[^\\]]*\\]")
    // A river or region after the town: "am Main", "an der Oder", "sur le Main", "upon Tyne", "nad Labem".
    private val RIVER = Regex("\\b(am|an der|an dem|sur le|sur la|sur l'|sur|upon|on the|nad|pod|sul|sulla|aan de|aan den)\\s+[a-z]+")
    private val SPLIT = Regex("[^a-z0-9]+")
    // Words that name a station, its level or its part in many languages; not its place.
    private val STATION_WORDS = setOf(
        "s", "u", "su", "sbahn", "ubahn", "bhf", "bf", "hbf", "hauptbahnhof", "bahnhof", "hp", "haltepunkt",
        "tief", "oben", "unten", "gleis", "gl", "station", "stn", "railway", "central", "centraal", "centrale",
        "cs", "gare", "stazione", "staz", "estacion", "estacao", "dworzec", "glowny", "hlavni", "nadrazi",
        "hl", "n", "palyaudvar", "pu", "banegard", "sentralstasjon", "centralstation", "c",
        "h", "hb", "hovedbanegard", "hovedbane", "centralstationen", "hauptbahnhof", "nadrazi",
    )
    private val LONG_STATION_WORDS = STATION_WORDS.filter { it.length >= 8 }
    private val COMPASS = buildMap {
        listOf("sud", "sued", "zuid", "midi", "south", "jih", "poludnie", "syd").forEach { put(it, "~s") }
        listOf("nord", "noord", "north", "sever", "polnoc").forEach { put(it, "~n") }
        listOf("ost", "est", "oost", "east", "vychod", "wschod", "ost").forEach { put(it, "~e") }
        listOf("west", "ouest", "zapad", "zachod", "vest").forEach { put(it, "~w") }
    }
    private val COMPASS_TOKENS = setOf("~s", "~n", "~e", "~w")
    private val ARTICLES = setOf("de", "du", "des", "la", "le", "l", "d", "del", "della", "di", "van", "the", "of")
    // Words that tell stations or stops of one town apart: a name with one is never a town's plain name.
    private val QUALIFIERS = setOf(
        "~s", "~n", "~e", "~w", "mitte", "zentrum", "messe", "deutz", "flughafen", "airport", "aeroport",
        "fernbahnhof", "fernbf", "regionalbahnhof", "regionalbf", "est", "ouest", "midi", "noord", "zuid",
        "oost", "centrum", "east", "north", "south", "zapad", "vychod", "sever", "jih", "bad", "altstadt",
        "neustadt", "hafen", "port", "porte", "sued", "suedbahnhof", "wilhelmshoehe", "dom", "tor",
        "pl", "str", "allee", "rathaus", "kirche", "schloss", "park", "terminal", "messeplatz", "brucke", "bruecke", "markt", "marktpl", "nordbahnhof", "westbahnhof", "ostbahnhof",
    )
}

/** True when [a] and [b] are one station of two feeds (see StationIdentity). */
fun foreignFeedTwins(a: Stop, b: Stop): Boolean = StationIdentity.otherFeedTwins(a, b)

/**
 * The stops of [nearby] that are the same station as [stop] in another feed: their departures
 * belong on its board, also when the map never grouped them (a board opened from the search).
 */
fun foreignTwinsOf(stop: Stop, nearby: List<Stop>): List<Stop> =
    nearby.filter { it.id != stop.id && StationIdentity.otherFeedTwins(stop, it) }

/**
 * Of two stops of one station found by the search ([kept] first, [joining] after), true when
 * [joining] stands for the station rather than [kept] (see StationIdentity.preferredIndex):
 * "Frankfurt (Main) Hauptbahnhof" of the German feed, not the SNCF's "Francfort sur le Main".
 */
fun localStationFirst(kept: Stop, joining: Stop): Boolean =
    StationIdentity.otherFeedTwins(kept, joining) && StationIdentity.preferredIndex(listOf(kept, joining)) == 1
