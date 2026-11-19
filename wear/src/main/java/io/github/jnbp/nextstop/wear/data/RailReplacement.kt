package io.github.jnbp.nextstop.wear.data

import java.util.Locale

/*
 * A rail replacement service ("Schienenersatzverkehr", SEV) by its name, as the phone reads it
 * (data/RailReplacement.kt railReplacement there, by the name; the phone also reads its notices
 * and direction, which the watch's badges do not get): a line named "SEV", "Bus SEV", "EV",
 * "SEV S1", "S1 SEV", "S1 (SEV)", or a bus under the name of a train line ("S1" as a bus). The
 * badge then says "SEV" and the line it stands in for, outlined in that line's colour, as on the
 * phone, so it never passes for the train or for a bus line "S1". Twin: change both together.
 */

/** A rail replacement standing in for the train line [replaced] ("S1", "RE7"); null where not known. */
data class SevLine(val replaced: String?)

// A line of a train by its kind and number: S1, U9, S41, RE 7, RB23, IRE 3, FEX.
private val SEV_RAIL_LINE = Regex("""\b(S|U|RE|RB|IRE|RS|MEX|FEX)\s?(\d{1,3}[A-Z]?)\b""")
private val SEV_FEX = Regex("""\bFEX\b""")

// The words of a line name that make it a replacement: "SEV", "EV" and "Bus" around them.
private val SEV_NAME = Regex("""^(?:BUS\s+)?(?:SEV|EV)(?:[\s\-/:]+(.+))?$|^(.+?)[\s\-/(]+(?:SEV|EV)\)?$""")

private fun sevRailLineIn(text: String): String? {
    val upper = text.uppercase(Locale.ROOT)
    SEV_RAIL_LINE.find(upper)?.let { return it.groupValues[1] + it.groupValues[2] }
    return if (SEV_FEX.containsMatchIn(upper)) "FEX" else null
}

/** The rail replacement [line] of [product] is by its name; null for every other ride. */
fun sevLineOf(line: String?, product: String): SevLine? {
    val name = line?.trim()?.replace(Regex("\\s+"), " ")?.uppercase(Locale.ROOT).orEmpty()
    if (name.isEmpty()) return null
    SEV_NAME.matchEntire(name)?.let { named ->
        val rest = named.groupValues[1].ifEmpty { named.groupValues[2] }
        return SevLine(sevRailLineIn(rest))
    }
    if (product != "bus" && product != "coach") return null
    val plain = name.removePrefix("BUS ").trim()
    val rail = sevRailLineIn(plain) ?: return null
    return if (plain.replace(" ", "") == rail) SevLine(rail) else null
}

/** The product of the train line [line] by its name, for its colour: S-Bahn, U-Bahn or regional (replacedProduct on the phone). */
fun sevReplacedProduct(line: String): String = when {
    line.startsWith("S") -> "suburban"
    line.startsWith("U") -> "subway"
    else -> "regional"
}
