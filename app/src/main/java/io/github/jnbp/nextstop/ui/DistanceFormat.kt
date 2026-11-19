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

/**
 * A duration of [minutes] (a journey, a ride) split for the label: below an hour the minutes
 * alone (hours null), from an hour on the whole hours and the minutes left, those null for whole
 * hours. Shown in the compact form of the DB ("2:23 h", "2:00 h"; see [durationClock]). Never below 0.
 */
fun durationSpan(minutes: Long): Pair<Long?, Long?> {
    val safe = minutes.coerceAtLeast(0L)
    if (safe < 60L) return null to safe
    return safe / 60L to (safe % 60L).takeIf { it > 0L }
}

/**
 * A duration of [minutes] from an hour on in the compact form of the DB, the number before its
 * "h": "5:20", "1:05", "2:00" (minutes always two digits); null below an hour, which stays "45′".
 */
fun durationClock(minutes: Long): String? {
    val (hours, rest) = durationSpan(minutes)
    if (hours == null) return null
    return "$hours:" + (rest ?: 0L).toString().padStart(2, '0')
}

/** A walk of [minutes] split for the label: whole hours and the minutes left, hours null below one hour. */
fun walkSpan(minutes: Long): Pair<Long?, Long> {
    val safe = minutes.coerceAtLeast(1L)
    return if (safe < 60L) null to safe else safe / 60L to safe % 60L
}

/** The mark of minutes after a number everywhere in the app ("5′"; from an hour on "1:20 h", #67, #106). */
const val MINUTE_PRIME = "′"

// A number of minutes ("5′", "+3′", "−2′"), a duration in hours and minutes ("1:20 h") and a
// number of hours ("1 h") in a shown text.
private val SHOWN_HOURS_MINUTES = Regex("""(\d+):(\d{2}) h(?![\p{L}\d])""")
private val PRIMED_MINUTES = Regex("""(\d+)\s?′""")
private val SHOWN_HOURS = Regex("""(\d+) h(?![\p{L}\d])""")

/**
 * [text] as a screen reader should say it: every "5′" with the words of [minutes] ("5
 * Minuten"), every "1:20 h" with those of [hours] and [minutes] ("1 Stunde 20 Minuten", "2:00 h"
 * "2 Stunden") and every "1 h" with those of [hours] ("1 Stunde"); the rest unchanged. A
 * prime alone says nothing to a listener, so wherever a time is shown with it, its
 * description goes through here.
 */
fun spokenTimeText(text: String, minutes: (Int) -> String, hours: (Int) -> String): String =
    text.replace(SHOWN_HOURS_MINUTES) { match ->
        val spokenHours = hours(match.groupValues[1].toIntOrNull() ?: 0)
        val rest = match.groupValues[2].toIntOrNull() ?: 0
        if (rest == 0) spokenHours else spokenHours + " " + minutes(rest)
    }
        .replace(SHOWN_HOURS) { hours(it.groupValues[1].toIntOrNull() ?: 0) }
        .replace(PRIMED_MINUTES) { minutes(it.groupValues[1].toIntOrNull() ?: 0) }
