package io.github.jnbp.nextstop.wear

/*
 * How long the phone may take for a map picture of the watch, and when a warm-up of the watch
 * is worth doing. Pure rules, no Android, so they are tested on the JVM (WatchMapBudgetTest).
 *
 * The watch waits [WATCH_WAIT_MILLIS] (40 s, ANSWER_TIMEOUT_MILLIS in the watch's MapImages.kt)
 * for an answer. The phone must answer before that, with or without a picture, or the watch
 * shows nothing better than its sketch and asks again. So every request has one deadline,
 * [REQUEST_MILLIS] after it was received, which leaves [SEND_MARGIN_MILLIS] to write and send
 * the answer. The quick picture and the tries of the full one share it.
 *
 * A try on a snapshotter that is already warm (style loaded, tiles in memory) takes well under a
 * second when it works; one on a fresh snapshotter loads the style and the tiles first, which
 * over a slow network takes several seconds. So a cold try gets the longer limit, a warm one the
 * shorter: a try that hangs is given up sooner and the next one, on a fresh snapshotter, still
 * has time. The last try always has all that is left.
 */
internal object WatchMapBudget {
    /** How long the watch waits for an answer (the watch's MapImages.ANSWER_TIMEOUT_MILLIS). */
    const val WATCH_WAIT_MILLIS = 40_000L

    /** Kept free before the watch gives up: writing the answer and the Data Layer's way to the watch. */
    const val SEND_MARGIN_MILLIS = 5_000L

    /** From the request received to the answer written, quick picture and all tries together. */
    const val REQUEST_MILLIS = WATCH_WAIT_MILLIS - SEND_MARGIN_MILLIS

    /** A quick picture on a fresh snapshotter: style, tiles and the first render. */
    const val QUICK_COLD_MILLIS = 12_000L

    /** A quick picture on the warm snapshotter. */
    const val QUICK_WARM_MILLIS = 5_000L

    /** Left at least for the full picture after the quick one (two tries of a warm one). */
    const val FULL_RESERVE_MILLIS = 2 * 8_000L

    /** One try of the full picture on a fresh snapshotter that is not the last try. */
    const val TRY_COLD_MILLIS = 14_000L

    /** One try of the full picture on the warm snapshotter that is not the last try. */
    const val TRY_WARM_MILLIS = 8_000L

    /** The phone warms up at most this often, however often the watch asks (milliseconds). */
    const val WARM_UP_MIN_GAP_MILLIS = 60_000L

    /**
     * The time the quick picture may take with [leftMillis] left of the request: its own limit,
     * cold or warm, but never so much that less than [FULL_RESERVE_MILLIS] is left for the full
     * one. 0 means: no quick picture, straight to the full one.
     */
    fun quickMillis(cold: Boolean, leftMillis: Long): Long {
        val own = if (cold) QUICK_COLD_MILLIS else QUICK_WARM_MILLIS
        return minOf(own, leftMillis - FULL_RESERVE_MILLIS).coerceAtLeast(0L)
    }

    /**
     * The limit of try [attempt] of [attempts] of the full picture with [leftMillis] left: the
     * last try has all that is left, an earlier one its own limit (cold or warm) at most.
     */
    fun tryMillis(cold: Boolean, attempt: Int, attempts: Int, leftMillis: Long): Long {
        if (leftMillis <= 0) return 0L
        if (attempt >= attempts) return leftMillis
        return minOf(leftMillis, if (cold) TRY_COLD_MILLIS else TRY_WARM_MILLIS)
    }

    /**
     * Whether a warm-up asked for at [now] should run: not while the snapshotter of that style is
     * [warm] already, and not within [WARM_UP_MIN_GAP_MILLIS] of the last one ([last], 0: never).
     * A clock that went back counts as long ago.
     */
    fun warmUpDue(now: Long, last: Long, warm: Boolean): Boolean {
        if (warm) return false
        if (last <= 0L || now < last) return true
        return now - last >= WARM_UP_MIN_GAP_MILLIS
    }
}
