package io.github.jnbp.nextstop.data

import java.util.Locale
import kotlin.math.roundToLong

/*
 * "Aktueller Standort" picked as the start or the destination shows the address of the position
 * instead (#207). The position is rounded before it is looked up (about 11 m, see
 * HERE_DECIMALS), once per rounded position while the field shows it; nothing runs in the
 * background. Without an answer (offline, no geocoder) the field keeps "Aktueller Standort"
 * with the rounded coordinates.
 */

// Decimals of a position that is looked up for its address: 4 are about 11 m.
private const val HERE_DECIMALS = 4
private const val HERE_SCALE = 10_000.0

/** [position] rounded to HERE_DECIMALS, as it is handed to the geocoder. */
fun hereRounded(position: Pair<Double, Double>): Pair<Double, Double> =
    (position.first * HERE_SCALE).roundToLong() / HERE_SCALE to (position.second * HERE_SCALE).roundToLong() / HERE_SCALE

/** The key of a looked-up position: the same for every position that rounds alike (see [hereRounded]). */
fun hereKey(position: Pair<Double, Double>): String {
    val (lat, lon) = hereRounded(position)
    return String.format(Locale.ROOT, "%.${HERE_DECIMALS}f,%.${HERE_DECIMALS}f", lat, lon)
}

/**
 * What the field shows for the position from the answer of the geocoder: the street with its
 * number (the part of [address] before the town, where it has one), else the name of the place [title]; null
 * where neither says anything.
 */
fun hereFieldLabel(title: String?, address: String?): String? =
    // A first part that starts with a digit is the postcode of the town: no street there.
    address?.substringBefore(", ")?.trim()?.takeIf { it.isNotEmpty() && it.first().isLetter() }
        ?: title?.trim()?.takeIf { it.isNotEmpty() }
        ?: address?.trim()?.takeIf { it.isNotEmpty() }

/** The field when no address was found: [name] ("Aktueller Standort") with the rounded coordinates. */
fun hereFallbackLabel(name: String, position: Pair<Double, Double>): String = "$name · " + hereKey(position).replace(",", ", ")
