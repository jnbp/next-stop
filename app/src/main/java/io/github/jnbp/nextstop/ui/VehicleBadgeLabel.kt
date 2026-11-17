package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.Vehicle
import io.github.jnbp.nextstop.data.railReplacement

/*
 * The text of a vehicle's badge on the map, in one place: the badge is drawn with it, and the
 * row of badges beside a stop is laid out by its width (see layoutGroup). Two texts would
 * leave a long badge sticking out of its row on the left of the stop's dot.
 */

/** The text of the badge of a vehicle of [line] and [product] heading for [direction] on the map. */
fun vehicleBadgeLabel(line: String, product: String, direction: String = ""): String {
    val replacement = railReplacement(line, product, emptyList(), direction)
        ?: return badgeLine(line) ?: line
    return replacementBadgeText(replacement.replaced)
}

/** The text of the badge of [vehicle] on the map (see [vehicleBadgeLabel]). */
fun vehicleBadgeLabel(vehicle: Vehicle): String = vehicleBadgeLabel(vehicle.line, vehicle.product, vehicle.direction)

/**
 * The badge of a rail replacement bus on the map: "SEV" and the train line it stands in for,
 * short ("SEV S7"); the letter of a branch or variant of that line is left out ("S7A" as
 * "S7"), the colour already says which line it is. "SEV" alone where the line is not known.
 */
fun replacementBadgeText(replaced: String?): String {
    val line = replaced?.let { shortLine(it) ?: it }?.replace(" ", "") ?: return SEV_MARK
    val base = BRANCH_LETTER.matchEntire(line)?.groupValues?.get(1) ?: line
    return "$SEV_MARK $base"
}

// A train line with the letter of a branch behind its number: "S7A", "RE1B".
private val BRANCH_LETTER = Regex("^([A-Z]{1,3}[0-9]{1,3})[A-Z]$")
