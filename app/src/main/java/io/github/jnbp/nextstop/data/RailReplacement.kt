package io.github.jnbp.nextstop.data

import java.util.Locale

/**
 * A rail replacement service ("Schienenersatzverkehr", SEV): a bus that runs in place of a
 * train. [replaced] is the line it stands in for ("S1", "RE7") where that is known.
 */
data class RailReplacement(val replaced: String?, val replacedKind: String? = null)

/** The product of the line [RailReplacement.replaced] stands for: as found, else by its name. */
val RailReplacement.replacedProductOrGuess: String?
    get() = replaced?.let { replacedKind ?: replacedProduct(it) }

// A line of a train by its kind and number: S1, U9, S41, RE 7, RB23, IRE 3, FEX.
private val RAIL_LINE = Regex("""\b(S|U|RE|RB|IRE|RS|MEX|FEX)\s?(\d{1,3}[A-Z]?)\b""")
private val FEX = Regex("""\bFEX\b""")

// The words of a line name that make it a replacement: "SEV", "EV" and "Bus" around them.
private val REPLACEMENT_NAME = Regex("""^(?:BUS\s+)?(?:SEV|EV)(?:[\s\-/:]+(.+))?$|^(.+?)[\s\-/(]+(?:SEV|EV)\)?$""")

// A notice that announces a replacement, in German or English.
private val REPLACEMENT_WORDS = Regex(
    """(?i)schienenersatzverkehr|ersatzverkehr|ersatzbus|bus\s+statt\s+(?:bahn|zug)|rail\s+replacement|replacement\s+bus|\bSEV\b""",
)

/** The train line named in [text] ("S1", "RE 7" as "RE7"); null where none is. */
private fun railLineIn(text: String): String? {
    val upper = text.uppercase(Locale.ROOT)
    RAIL_LINE.find(upper)?.let { return it.groupValues[1] + it.groupValues[2] }
    return if (FEX.containsMatchIn(upper)) "FEX" else null
}

/**
 * The product of a train line by its name, for its colour: S-Bahn, U-Bahn or regional.
 */
fun replacedProduct(line: String): String = when {
    line.startsWith("S") -> "suburban"
    line.startsWith("U") -> "subway"
    else -> "regional"
}

/**
 * Whether a ride of [line] of [product] with the notices [warnings] is a rail replacement
 * service, and for which line. Read generally, whatever the source: a line named "SEV", "Bus
 * SEV", "EV", "SEV S1", "S1 SEV", "S1 (SEV)"; a bus that carries the name of a train line
 * ("S1" as a bus); a bus whose notices say it is a replacement ("Ersatzverkehr", "SEV").
 * Notices alone count only for a bus: a train with a notice on a replacement elsewhere is
 * still a train. Null for every other ride.
 */
fun railReplacement(
    line: String?,
    product: String,
    warnings: List<Warning> = emptyList(),
    direction: String? = null,
): RailReplacement? {
    val name = line?.trim()?.replace(Regex("\\s+"), " ")?.uppercase(Locale.ROOT).orEmpty()
    val bus = product == "bus" || product == "coach"
    if (name.isNotEmpty()) {
        val named = REPLACEMENT_NAME.matchEntire(name)
        if (named != null) {
            val rest = named.groupValues[1].ifEmpty { named.groupValues[2] }
            return RailReplacement(railLineIn(rest))
        }
        // A bus under the name of a train line.
        if (bus) {
            val plain = name.removePrefix("BUS ").trim()
            val rail = railLineIn(plain)
            if (rail != null && plain.replace(" ", "") == rail) return RailReplacement(rail)
        }
    }
    if (!bus) return null
    // A bus whose direction says it is a replacement ("Ersatzverkehr Richtung S+U Warschauer
    // Str."): it stands for the line whose name it carries, a train line or else a tram ("M10").
    if (direction != null && REPLACEMENT_WORDS.containsMatchIn(direction)) {
        val plain = name.removePrefix("BUS ").trim()
        val rail = railLineIn(plain)?.takeIf { plain.replace(" ", "") == it }
        return when {
            rail != null -> RailReplacement(rail)
            plain.isNotEmpty() -> RailReplacement(line?.trim(), "tram")
            else -> RailReplacement(null)
        }
    }
    val notice = warnings.firstOrNull { warning ->
        REPLACEMENT_WORDS.containsMatchIn(warning.summary) || REPLACEMENT_WORDS.containsMatchIn(warning.text.take(NOTICE_HEAD_CHARS))
    } ?: return null
    return RailReplacement(railLineIn(notice.summary) ?: railLineIn(notice.text.take(NOTICE_HEAD_CHARS)))
}

// Only the start of a long notice is read: further on it often speaks of other lines.
private const val NOTICE_HEAD_CHARS = 160

/** The rail replacement of the ride [leg] (see [railReplacement]); null for any other. */
val Leg.railReplacement: RailReplacement?
    get() = if (walking) null else io.github.jnbp.nextstop.data.railReplacement(line, product, warnings, direction)

/** The rail replacement of [departure] (see [railReplacement]); null for any other. */
val Departure.railReplacement: RailReplacement?
    get() = io.github.jnbp.nextstop.data.railReplacement(line, product, warnings, direction)

/** The rail replacement of [vehicle] by its line and direction (see [railReplacement]); null for any other. */
val Vehicle.railReplacement: RailReplacement?
    get() = io.github.jnbp.nextstop.data.railReplacement(line, product, emptyList(), direction)
