package io.github.jnbp.nextstop.wear.data

/**
 * How old the live times of the followed trip are, as the phone says it (#70 on the watch):
 * [JustNow] up to [JUST_NOW_SECONDS], then [Seconds] in steps of [STEP_SECONDS], [Minutes],
 * and [Stale] past [STALE_SECONDS] (no longer called live, as on the phone). Each value only
 * changes when its words change, so the line recomposes every 5 s at most.
 */
sealed class LiveAge {
    data object JustNow : LiveAge()

    data class Seconds(val seconds: Long) : LiveAge()

    data class Minutes(val minutes: Long) : LiveAge()

    data class Stale(val minutes: Long) : LiveAge()

    companion object {
        const val JUST_NOW_SECONDS = 5L
        const val STEP_SECONDS = 5L

        // As the phone's FRESH_STALE_MILLIS: three rounds of its refresh missed.
        const val STALE_SECONDS = 100L

        /** The age of live data marked at [liveAt] (epoch ms) at [now]; null without live data ([liveAt] 0). A clock that went back counts as just now. */
        fun of(liveAt: Long, now: Long): LiveAge? {
            if (liveAt <= 0L) return null
            val age = ((now - liveAt) / 1000L).coerceAtLeast(0L)
            return when {
                age >= STALE_SECONDS -> Stale(age / 60L)
                age < JUST_NOW_SECONDS -> JustNow
                age < 60L -> Seconds(age / STEP_SECONDS * STEP_SECONDS)
                else -> Minutes(age / 60L)
            }
        }
    }
}
