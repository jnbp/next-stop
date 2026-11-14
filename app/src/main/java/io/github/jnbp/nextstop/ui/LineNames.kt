package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.withoutProductWord

/**
 * The name of a line as its badge shows it, the same everywhere (chart, list, badges, map,
 * notification): without the word for its product in front ("Bus 585", "Tram 50", also in
 * names stored before the sources dropped it, see withoutProductWord), and without a train number where the line
 * has a name of its own: "FEX 021857" is "FEX", "RE1 3809" is "RE1". Long-distance trains
 * have no line name but their number: "ICE 29", "IC 2041" stay as they are. The whole name
 * with the train number stays for the detail of a ride and for screen readers.
 */
fun shortLine(line: String?): String? {
    val name = line?.trim()?.replace(Regex("\\s+"), " ") ?: return null
    val bare = withoutProductWord(name)
    val plain = BUS_WORD.replace(bare, "").ifEmpty { bare }
    val numbered = TRAIN_NUMBERED.matchEntire(plain) ?: return plain
    val kind = numbered.groupValues[1]
    val number = numbered.groupValues[2]
    return when {
        // A long-distance train: its kind and number are its name.
        kind.uppercase() in LONG_DISTANCE_KINDS -> "$kind $number"
        // A line name of its own with a train number behind it: the line name alone.
        number.length >= TRAIN_NUMBER_DIGITS || number.startsWith("0") || kind.any { it.isDigit() } -> kind
        else -> plain
    }
}

/**
 * The shortest form of a line for a narrow block that cannot take [shortLine]: the kind of a
 * long-distance train without its number ("ICE 29" as "ICE"). Other names are already short.
 */
fun compactLine(line: String?): String? {
    val short = shortLine(line) ?: return null
    val numbered = TRAIN_NUMBERED.matchEntire(short) ?: return short
    return numbered.groupValues[1].takeIf { it.uppercase() in LONG_DISTANCE_KINDS } ?: short
}

/** The whole name of a line where it says more than the badge ([shortLine]); null where both agree. */
fun fullLineName(line: String?): String? {
    val whole = line?.trim()?.replace(Regex("\\s+"), " ") ?: return null
    return whole.takeIf { it != shortLine(line) && BUS_WORD.replace(whole, "") != shortLine(line) }
}

// The word for a bus in front of the number of its line.
private val BUS_WORD = Regex("^bus\\s+(?=\\S)", RegexOption.IGNORE_CASE)

// A name, then a blank and a number: "ICE 29", "FEX 021857", "RE1 3809", "S 41".
private val TRAIN_NUMBERED = Regex("^([A-Za-z]+[0-9]*)\\s([0-9]+)$")

// Kinds of trains that are named by their number, as there is no line.
private val LONG_DISTANCE_KINDS = setOf("ICE", "IC", "EC", "ECE", "EN", "NJ", "RJ", "RJX", "FLX", "TGV", "ES", "EST", "D")

// From this many digits a number behind a line name is a train number, not part of the line.
private const val TRAIN_NUMBER_DIGITS = 4
