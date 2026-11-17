package io.github.jnbp.nextstop.data

/*
 * Addresses as they are written in Germany: the street first, then the house number
 * ("Musterstraße 32"). The device's geocoder and contacts kept in an English format write the
 * number first ("32 Musterstraße", "10b Musterstraße"); every place the app shows (the header
 * of a search, the walk "Fußweg 8 min nach …", saved places) takes the German order.
 */

// A house number at the start of a part: digits, an optional letter, an optional range ("12-14").
private val LEADING_NUMBER = Regex("""^(\d{1,5}\s?[A-Za-z]?(?:\s?[-/]\s?\d{1,5}\s?[A-Za-z]?)?)\s+(\p{L}.*)$""")

// Endings that make a word a street ("Musterstraße", "Am Mustergraben", "Musterstr."): only
// those that town and station names hardly end with.
private val STREET_ENDING = Regex(
    """(?i)(straße|strasse|str\.?|weg|allee|platz|damm|gasse|ufer|chaussee|steig|stieg|pfad|zeile|promenade|graben|twete)$""",
)

// Words that make a part a street only as a whole word ("Am Berg", "Muster St."): as endings they
// are towns and stations ("Nürnberg", "Bahnhof", "Ernst").
private val STREET_WORD = Regex(
    """(?i)(ring|hof|berg|tor|park|garten|markt|kamp|brücke|bruecke|steg|wall|street|st\.?|road|rd\.?|avenue|ave\.?|lane|way)""",
)

// The words German street names start with that no town or station starts with that way
// ("Am Dachsbau", "An der Mühle", "Zur Linde", "Unter den Eichen", "Alt-Musterdorf").
private val STREET_PREFIX = Regex(
    """^(?:(?:Am|An|Auf|Im|In|Zum|Zur|Unter|Hinter|Vor|Neben|Über|Ueber|Beim|Bei|Hinterm|Unterm|Vorm|Überm|Op|Up)\s+\p{L}|Alt-\p{L})""",
)

/** The street with its house number as Germans write it, from the fields of an address: "Musterstraße 32". */
fun streetWithNumber(street: String?, number: String?): String? {
    val road = street?.trim()?.takeIf { it.isNotEmpty() } ?: return number?.trim()?.takeIf { it.isNotEmpty() }
    val house = number?.trim()?.takeIf { it.isNotEmpty() && it != road } ?: return road
    return "$road $house"
}

/**
 * [text] with the house number behind the street in each part between commas where a part
 * starts with one followed by a street ("32 Musterstraße, 12345 Musterstadt" as
 * "Musterstraße 32, 12345 Musterstadt"). A postcode before a town, a number alone or a name
 * that only starts with a number ("1. FC Platz" aside) stays as it is. A street is known by its
 * ending ("…straße", "…weg"), a street word ("Am Berg") or the first word of its name ("Am
 * Dachsbau", "Zur Linde"); with [address], where the source says the text is an address (a
 * contact's address, the device's geocoder), any name after the number is the street.
 */
fun germanStreetOrder(text: String, address: Boolean = false): String {
    val parts = text.split(',')
    if (parts.none { it.trim().firstOrNull()?.isDigit() == true }) return text
    var changed = false
    val result = parts.map { part ->
        val reordered = reorderPart(part.trim(), address)
        if (reordered != null) {
            changed = true
            val lead = part.takeWhile { it.isWhitespace() }
            lead + reordered
        } else {
            part
        }
    }
    return if (changed) result.joinToString(",") else text
}

private fun reorderPart(part: String, address: Boolean): String? {
    val match = LEADING_NUMBER.matchEntire(part) ?: return null
    val number = match.groupValues[1].replace(" ", "")
    val street = match.groupValues[2].trim()
    // A postcode has five digits; no house number has: "12345 Musterstadt" is a postcode and a town.
    if (number.takeWhile { it.isDigit() }.length >= 5) return null
    // The source says it is an address: the name after the number is its street.
    if (!address) {
        // Else the name has to look like a street: by its last word or by its first.
        val lastWord = street.split(' ').last()
        val streetLike = STREET_ENDING.containsMatchIn(lastWord) || STREET_WORD.matches(lastWord) || STREET_PREFIX.containsMatchIn(street)
        if (!streetLike) return null
    }
    return "$street $number"
}
