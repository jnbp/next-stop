package io.github.jnbp.nextstop.ui

/** Up to this age, in seconds, live data counts as "just now". */
internal const val FRESH_JUST_NOW_SECONDS = 5L

/**
 * Past this age, in milliseconds, live data is no longer called live: three rounds of the
 * refresh (every 30 s) missed. Stretched like the refresh while the battery saver is active.
 */
internal const val FRESH_STALE_MILLIS = 100_000L

/** The seconds of the freshness indicator count in steps this long ("vor 5 s", "vor 10 s", ...; #70). */
internal const val FRESH_SECONDS_STEP = 5L

/**
 * What the freshness indicator says for live data [ageSeconds] old, as one number that only
 * changes when the words change (so the seconds recompose every [FRESH_SECONDS_STEP] seconds
 * and a minute-old age once a minute, not each second): 0 "just now", 5, 10 ... 55 seconds,
 * [FRESH_MINUTES_KEY] + minutes, and below 0 for data older than [staleSeconds] (minus one
 * minus its minutes). An age below 0 (a clock that went back) counts as just now.
 */
internal fun freshLabelKey(ageSeconds: Long, staleSeconds: Long): Long {
    val age = ageSeconds.coerceAtLeast(0L)
    return when {
        age >= staleSeconds -> -1L - age / 60L
        age < FRESH_JUST_NOW_SECONDS -> 0L
        age < 60L -> age / FRESH_SECONDS_STEP * FRESH_SECONDS_STEP
        else -> FRESH_MINUTES_KEY + age / 60L
    }
}

/**
 * The text of a [template] ("Live · vor %1$d s") before and after its number, so the number
 * can roll on its own between them; null where the template has no "%1$d" (or "%d").
 */
internal fun aroundNumber(template: String): Pair<String, String>? {
    val mark = listOf("%1\$d", "%d").firstOrNull { template.contains(it) } ?: return null
    val at = template.indexOf(mark)
    return template.substring(0, at) to template.substring(at + mark.length)
}

internal const val FRESH_MINUTES_KEY = 1_000L

/** Whether [key] (see [freshLabelKey]) is for data that is no longer live. */
internal fun freshKeyStale(key: Long): Boolean = key < 0L

/** The minutes of a stale [key], or of a key in minutes; the seconds of a key in seconds. */
internal fun freshKeyValue(key: Long): Long = when {
    key < 0L -> -1L - key
    key >= FRESH_MINUTES_KEY -> key - FRESH_MINUTES_KEY
    else -> key
}

/** Whether [key] counts in minutes (stale keys do too). */
internal fun freshKeyInMinutes(key: Long): Boolean = key < 0L || key >= FRESH_MINUTES_KEY

/**
 * The age (whole seconds) at which the label of live data [ageSeconds] old next changes (see
 * [freshLabelKey]): the end of "just now", the next step of five seconds, the next whole
 * minute, or the age it goes stale ([staleSeconds]), whichever comes first. Always later than
 * [ageSeconds]; an age below 0 counts as 0.
 */
internal fun freshNextChangeAge(ageSeconds: Long, staleSeconds: Long): Long {
    val age = ageSeconds.coerceAtLeast(0L)
    val next = when {
        age >= staleSeconds -> (age / 60L + 1L) * 60L
        age < FRESH_JUST_NOW_SECONDS -> FRESH_JUST_NOW_SECONDS
        age < 60L -> (age / FRESH_SECONDS_STEP + 1L) * FRESH_SECONDS_STEP
        else -> (age / 60L + 1L) * 60L
    }
    return if (age < staleSeconds) minOf(next, staleSeconds) else next
}

/**
 * When (wall clock, ms) the clock should wake next for a freshness label of data marked at
 * [markedAtMillis]: the moment its whole-second age reaches [freshNextChangeAge]. Never at or
 * before [nowMillis].
 */
internal fun freshNextWakeMillis(nowMillis: Long, markedAtMillis: Long, staleSeconds: Long): Long {
    val markedSecond = markedAtMillis / 1000L
    val age = nowMillis / 1000L - markedSecond
    return maxOf((markedSecond + freshNextChangeAge(age, staleSeconds)) * 1000L, nowMillis + 1L)
}

/** The next whole second after [nowMillis] (ms): the cadence of a consumer that shows seconds. */
internal fun nextWholeSecondMillis(nowMillis: Long): Long = (nowMillis / 1000L + 1L) * 1000L

/** How long the clock sleeps at most when no consumer asks for a wake (a safety net). */
internal const val TICKER_IDLE_MILLIS = 60_000L

/**
 * The cadence rule of the shared clock: it wakes at the earliest of the moments its
 * consumers ask for ([wakes], wall-clock ms), so only as often as the shown labels change
 * (every 5 s while freshness chips alone use it, every second while one shows seconds).
 * Moments already past count as the next millisecond; with none it sleeps [TICKER_IDLE_MILLIS].
 */
internal fun tickerNextWake(nowMillis: Long, wakes: List<Long>): Long {
    if (wakes.isEmpty()) return nowMillis + TICKER_IDLE_MILLIS
    return maxOf(wakes.min(), nowMillis + 1L)
}
