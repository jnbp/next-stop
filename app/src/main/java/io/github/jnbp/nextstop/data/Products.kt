package io.github.jnbp.nextstop.data

/**
 * The products of long-distance travel: trains such as ICE, IC, EC, RJ, NJ or FlixTrain
 * ("express", "Fernverkehr") and coaches such as FlixBus ("coach", "Fernbus"). The
 * Deutschlandticket covers neither.
 */
val LONG_DISTANCE_PRODUCTS: Set<String> = setOf("express", "coach")

// Kinds of long-distance trains at the start of a line name ("ICE 1234", "IC 2", "NJ 40490").
private val LONG_DISTANCE_TRAIN_KINDS = setOf(
    "ICE", "IC", "EC", "ECE", "EN", "NJ", "RJ", "RJX", "FLX", "TGV", "ES", "EST", "THA", "EUR", "D",
)

// Kinds of coaches at the start of a line name: FlixBus ("FLX 040" as a bus), DB IC Bus.
private val COACH_KINDS = setOf("FLX", "ICB")

// Words in the name of a coach line or its operator.
private val COACH_WORDS = listOf("flix", "blablacar", "fernbus", "eurolines", "regiojet", "ic bus", "ic-bus", "intercitybus")

/**
 * The kind at the start of a line name, in capitals: "ICE" of "ICE 1234" or "ICE1234", "RE" of
 * "RE 1", "BUS" of "Bus 100"; null when the name does not start with one to four letters followed
 * by a blank, a digit or the end.
 */
fun lineKind(line: String?): String? {
    val text = line?.trim() ?: return null
    val letters = text.takeWhile { it.isLetter() }
    if (letters.isEmpty() || letters.length > 4) return null
    val rest = text.substring(letters.length)
    return if (rest.isEmpty() || rest.first() == ' ' || rest.first().isDigit()) letters.uppercase() else null
}

// Kinds of trains (and of FlixBus) that are written in capitals wherever they stand in front
// of a number: a provider that sends "rj 171" or "Ice 29" means RJ 171 and ICE 29. Kinds that
// some networks write in small letters on purpose (Czech "Os", "Sp", "Ex", "R") are not here.
private val CANONICAL_TRAIN_KINDS = setOf(
    "ICE", "IC", "EC", "ECE", "EN", "NJ", "RJ", "RJX", "TGV", "ES", "EST", "FLX", "THA", "EUR",
    "IRE", "RE", "RB", "IR", "MEX", "FEX",
)

// The products whose names start with the kind of a train: trains, coaches, and unknown ones.
private val TRAIN_KIND_PRODUCTS = setOf("express", "regional", "coach", "")

/**
 * [name] with the kind of a train in front written as everywhere: "rj 171" as "RJ 171",
 * "Ice29" as "ICE29", "re 1" as "RE 1"; only for [product]s of trains and coaches (see
 * TRAIN_KIND_PRODUCTS), only for the known kinds (see CANONICAL_TRAIN_KINDS), and only where
 * the kind stands alone before a blank, a digit or the end. Anything else, a bus "Bus 585",
 * an S-Bahn "S 1", a tram "4a" or a Czech "Os 6123", stays as it is.
 */
fun canonicalTrainKind(name: String, product: String): String {
    if (product !in TRAIN_KIND_PRODUCTS) return name
    val letters = name.takeWhile { it.isLetter() }
    if (letters.length !in 2..4) return name
    val rest = name.substring(letters.length)
    if (rest.isNotEmpty() && rest.first() != ' ' && !rest.first().isDigit()) return name
    val kind = letters.uppercase()
    if (kind !in CANONICAL_TRAIN_KINDS || kind == letters) return name
    return kind + rest
}

// Words for a local product that some providers (Transitous, some EFA and HAFAS feeds) put in
// front of the name of a line: "Tram 50", "STR 50", "Bus 585", "Bus M48". Kinds of trains
// ("S", "U", "RE", "ICE", "FEX" ...) are not here: there the letters are part of the name.
private val PRODUCT_PREFIXES = setOf(
    "bus", "tram", "str", "strab", "straßenbahn", "strassenbahn", "fähre", "faehre", "ferry",
)

// What may stand behind such a word as the name of the line: one short word with a digit in it
// ("50", "585", "M48", "X7", "N5", "4a") or a short code in capitals ("SEV").
private val PREFIXED_LINE = Regex("^(?=.*\\d)[\\p{L}\\d/.-]{1,6}$|^[\\p{Lu}]{2,4}$")

/**
 * [name] without the word for its product in front: "Tram 50" and "STR 50" as "50", "Bus 585"
 * as "585", "Bus M48" as "M48". Only where one short line name follows the word (see
 * PREFIXED_LINE); "Bus" alone, "S1", "RE1", "ICE 27", "FEX" or "X7" stay as they are.
 */
fun withoutProductWord(name: String): String {
    val trimmed = name.trim()
    val blank = trimmed.indexOfFirst { it.isWhitespace() }
    if (blank <= 0) return trimmed
    val word = trimmed.substring(0, blank)
    if (word.lowercase() !in PRODUCT_PREFIXES) return trimmed
    val rest = trimmed.substring(blank).trim()
    return if (PREFIXED_LINE.matches(rest)) rest else trimmed
}

/** True when [line] names a long-distance train by its kind ("ICE 1234", "IC 2", "FLX 30"). */
fun isLongDistanceTrainName(line: String?): Boolean = lineKind(line) in LONG_DISTANCE_TRAIN_KINDS

/** True when the [line] or its [operator] name a coach (FlixBus, BlaBlaCar Bus, IC Bus …). */
fun isCoachName(line: String?, operator: String?): Boolean {
    if (lineKind(line) in COACH_KINDS) return true
    val words = (line.orEmpty() + " " + operator.orEmpty()).lowercase()
    return COACH_WORDS.any { it in words }
}

/**
 * The product of a ride as the app names it, from the [product] the provider gives and the name
 * of its [line] (and [operator]): providers that know no long-distance class, or put an ICE under
 * regional or plain rail (GTFS route type 2), name the train by its kind, so a "regional" or
 * unknown ride named "ICE 1234" is "express"; a bus of FlixBus is a "coach". Everything else
 * stays as the provider has it.
 */
fun classifyProduct(product: String, line: String?, operator: String? = null): String = when (product) {
    // An unknown product with only "D" in front could be anything; there the plain kinds count.
    "regional" -> if (isLongDistanceTrainName(line)) "express" else product
    "" -> if (isLongDistanceTrainName(line) && lineKind(line) != "D") "express" else product
    "bus" -> if (isCoachName(line, operator)) "coach" else product
    else -> product
}

/**
 * True when every ride of [journey] is of a product the search allows. Rides of products the app
 * has no key for (cable cars, on-demand) pass. The providers are asked without the products off
 * already; this catches what they cannot filter (coaches, which HAFAS counts as buses) or ignore.
 */
fun productsFit(journey: Journey, options: SearchOptions): Boolean =
    journey.rides.all { it.product !in PRODUCT_KEYS || it.product in options.products }

/**
 * [options] with "Only Deutschlandticket" switched [on]: long-distance trains and coaches off,
 * or back on. Should nothing else be left on, the local products come on instead.
 */
fun SearchOptions.withLocalOnly(on: Boolean): SearchOptions {
    val changed = if (on) products - LONG_DISTANCE_PRODUCTS else products + LONG_DISTANCE_PRODUCTS
    return copy(products = changed.ifEmpty { PRODUCT_KEYS.toSet() - LONG_DISTANCE_PRODUCTS })
}

// Words for the kind of a train in long names, categories and operators: "Railjet", "EuroCity".
private val TRAIN_KIND_WORDS = mapOf(
    "railjet" to "RJ", "railjet xpress" to "RJX", "railjetxpress" to "RJX",
    "eurocity" to "EC", "eurocity-express" to "ECE", "eurocity express" to "ECE", "eurocityexpress" to "ECE",
    "intercity" to "IC", "intercity-express" to "ICE", "intercity express" to "ICE", "intercityexpress" to "ICE",
    "euronight" to "EN", "nightjet" to "NJ", "flixtrain" to "FLX",
    "regional-express" to "RE", "regionalexpress" to "RE", "regionalbahn" to "RB",
)

// Kinds of trains that are named by their number (or line) behind them.
private val NUMBERED_TRAIN_KINDS = LONG_DISTANCE_TRAIN_KINDS + setOf("IRE", "RE", "RB", "IR", "MEX", "FEX")

// The products of trains, and rides of no named product, whose names may be a bare number.
private val TRAIN_NAME_PRODUCTS = setOf("express", "regional", "")

/**
 * The kind of a train that [hint] names, in capitals: "RJ" of "RJ", "RJ 174", "rj174" or
 * "Railjet", "EC" of "EuroCity", "FLX" of "FlixTrain"; null where it names none of the known
 * kinds (see NUMBERED_TRAIN_KINDS, TRAIN_KIND_WORDS): "Zug", "Bus", "České dráhy".
 */
fun trainKindOf(hint: String?): String? {
    val text = hint?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() } ?: return null
    TRAIN_KIND_WORDS[text.lowercase()]?.let { return it }
    TRAIN_KIND_WORDS[text.substringBefore(' ').lowercase()]?.let { return it }
    val letters = text.takeWhile { it.isLetter() }
    if (letters.length !in 1..4) return null
    val rest = text.substring(letters.length)
    if (rest.isNotEmpty() && rest.first() != ' ' && !rest.first().isDigit()) return null
    return letters.uppercase().takeIf { it in NUMBERED_TRAIN_KINDS }
}

/**
 * The name of a train whose provider gave only its number: "174" (or no name and trip number
 * 174) is "RJ 174" where one of [hints] (the provider's product name, category, long name or
 * operator) names the kind of the train (see [trainKindOf]). Only for trains and rides of no
 * named [product]: a bus "174" stays "174". Without a kind the bare number stays, never a
 * guessed one: the badge shows that it is a train (see modeHint). A name that is only the kind
 * ("RJ", "Railjet") with trip number 174 is "RJ 174" too. A name that is more than a
 * number ("RE1", "ICE 29", "S1") stays as it is.
 */
fun withTrainKind(name: String?, product: String, number: String?, vararg hints: String?): String? {
    if (product !in TRAIN_NAME_PRODUCTS) return name
    val plain = name?.trim()?.takeIf { it.isNotEmpty() }
    val trip = number?.trim()?.takeIf { it.isNotEmpty() && it.all { char -> char.isDigit() } }
    // A name that is only the kind ("RJ", "Railjet") takes the trip number behind it: "RJ 174".
    if (plain != null && trip != null && plain.none { it.isDigit() }) {
        trainKindOf(plain)?.let { return "$it $trip" }
    }
    if (plain != null && !plain.all { it.isDigit() }) return name
    val digits = plain ?: trip?.takeIf { product != "" } ?: return name
    val kind = hints.firstNotNullOfOrNull { trainKindOf(it) } ?: return digits
    return "$kind $digits"
}

/** True when [line] of [product] is a train named by a bare number, no kind in front ("174"). */
fun isBareTrainNumber(line: String?, product: String): Boolean {
    val name = line?.trim() ?: return false
    return product in TRAIN_NAME_PRODUCTS && product != "" && name.isNotEmpty() && name.all { it.isDigit() }
}

// A name of FlixBus or FlixTrain, with the code of its line or the number of its train behind it
// or none: "FLX 040", "FLX30", "Flix 260", "FlixBus N1334", "Flixbus", "FlixTrain 1234".
private val FLIX_NAME = Regex("^(FLX|Flix|FlixBus|FlixTrain)(?: ?([A-Za-z]?[0-9]+[A-Za-z]?))?$", RegexOption.IGNORE_CASE)

/**
 * [name] of a FlixBus or FlixTrain ride in the one form the app uses everywhere: a FlixBus
 * ([product] coach or bus) is "FlixBus 260" with the code of its line, "FlixBus" without one
 * (some providers name it "FLX 260" or "Flix 260"); a FlixTrain ([product] of a train, or none
 * named, where the name says FLX or FlixTrain) is "FLX 30" with its number, "FlixTrain" without.
 * Badges shorten the bus to "Flix 260" (see badgeLine). Other names stay as they are.
 */
fun flixLineName(name: String, product: String): String {
    val match = FLIX_NAME.matchEntire(name.trim()) ?: return name
    val word = match.groupValues[1].lowercase()
    val code = match.groupValues[2].uppercase().ifEmpty { null }
    val bus = product == "coach" || product == "bus"
    return when {
        bus -> if (code != null) "FlixBus $code" else "FlixBus"
        product in TRAIN_NAME_PRODUCTS && (word == "flx" || word == "flixtrain") ->
            if (code != null && code.all { it.isDigit() }) "FLX $code" else if (code == null) "FlixTrain" else name
        else -> name
    }
}
