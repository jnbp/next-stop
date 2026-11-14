package io.github.jnbp.nextstop.ui

import java.util.Locale
import kotlin.math.roundToLong

/**
 * A distance for people, the same everywhere in the app: below 1 km in metres rounded to
 * 10 m ("850 m"), below 10 km in kilometres with one decimal in the [locale]'s way ("4,4 km"
 * in German, "4.4 km" in English), from 10 km on in whole kilometres ("254 km"). Never below
 * "10 m"; a value that rounds up to the next unit is shown in it ("1,0 km", not "1000 m").
 */
fun formatDistance(meters: Double, locale: Locale = Locale.getDefault()): String {
    val safe = if (meters.isNaN() || meters < 0.0) 0.0 else meters
    val tens = (safe / 10.0).roundToLong().coerceAtLeast(1L) * 10L
    if (tens < 1_000L) return "$tens m"
    val tenths = (safe / 100.0).roundToLong()
    if (tenths < 100L) return String.format(locale, "%.1f km", tenths / 10.0)
    return String.format(locale, "%d km", (safe / 1_000.0).roundToLong())
}

/** As [formatDistance] for whole metres. */
fun formatDistance(meters: Int, locale: Locale = Locale.getDefault()): String = formatDistance(meters.toDouble(), locale)

/**
 * Beyond this a time on foot is no help ("about 7209 min on foot" to a place 460 km away):
 * cards then show the distance alone.
 */
const val WALK_TIME_MAX_METERS = 5_000.0

/** True where a time on foot to a place [meters] away is worth showing. */
fun walkTimeShown(meters: Double): Boolean = !meters.isNaN() && meters <= WALK_TIME_MAX_METERS

/** A walk of [minutes] split for the label: whole hours and the minutes left, hours null below one hour. */
fun walkSpan(minutes: Long): Pair<Long?, Long> {
    val safe = minutes.coerceAtLeast(1L)
    return if (safe < 60L) null to safe else safe / 60L to safe % 60L
}
