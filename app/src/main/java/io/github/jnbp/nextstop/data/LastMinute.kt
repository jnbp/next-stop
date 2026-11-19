package io.github.jnbp.nextstop.data

/**
 * The countdown of the last minute in seconds, as the watch shows it ("in 45 s"): the seconds
 * left, rounded up, while [leftMillis] lies within the last minute; null before it, and from
 * the moment itself on ("now", or the minutes gone).
 */
fun lastMinuteSeconds(leftMillis: Long): Int? =
    if (leftMillis in 1 until LAST_MINUTE_MILLIS) ((leftMillis + 999L) / 1000L).toInt() else null

/**
 * When a countdown to [targetMillis] next changes its label at [nowMillis]: every whole second
 * within the last minute, else the moment the last minute begins; far ahead once the moment
 * is past (the minutes of the page move on by themselves).
 */
fun lastMinuteWakeMillis(nowMillis: Long, targetMillis: Long): Long {
    val left = targetMillis - nowMillis
    return when {
        left <= 0L -> Long.MAX_VALUE
        left >= LAST_MINUTE_MILLIS -> targetMillis - LAST_MINUTE_MILLIS
        // The next whole second of the countdown (its label rounds up).
        else -> nowMillis + ((left - 1L) % 1000L) + 1L
    }
}

const val LAST_MINUTE_MILLIS = 60_000L
