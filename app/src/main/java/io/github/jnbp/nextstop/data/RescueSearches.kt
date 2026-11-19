package io.github.jnbp.nextstop.data

/**
 * The searches for the ways on at a broken change (#98), shared by the companion of the app (in
 * the foreground, every 45 s while its view is open) and the trip service (in the background,
 * at most once per broken change), so the two never search the same change twice over. Keyed
 * by [changeKey]: the followed journey and the change. Holds the latest options ([T]) found for
 * a change by either; the background searches a change only when nothing of it is kept, never
 * again after a search that worked, and after one that failed only after [retryMillis]; never
 * with the battery saver at Extreme.
 */
class RescueSearches<T>(private val retryMillis: Long = RETRY_MILLIS) {
    private val found = HashMap<String, T>()
    private val failedAt = HashMap<String, Long>()

    /** The options found for [key] by either path, null for none yet. */
    @Synchronized
    fun options(key: String): T? = found[key]

    /** Options for [key] came in (from the companion or the background). */
    @Synchronized
    fun put(key: String, options: T) {
        found[key] = options
        failedAt.remove(key)
    }

    /** The background search for [key] failed at [now]: tried again after [retryMillis] at the earliest. */
    @Synchronized
    fun failed(key: String, now: Long) {
        failedAt[key] = now
    }

    /** Whether the background may search [key] at [now]: nothing kept of it, no failure within [retryMillis], no [extremeSaver]. */
    @Synchronized
    fun mayBackgroundSearch(key: String, now: Long, extremeSaver: Boolean): Boolean {
        if (extremeSaver || key in found) return false
        val failed = failedAt[key] ?: return true
        return now < failed || now - failed >= retryMillis
    }

    /** The trip ended: nothing kept. */
    @Synchronized
    fun clear() {
        found.clear()
        failedAt.clear()
    }

    companion object {
        const val RETRY_MILLIS = 2 * 60_000L

        /** The key of the change keeping [keepLegs] legs of the journey [journeyId]. */
        fun changeKey(journeyId: String, keepLegs: Int): String = "$journeyId|$keepLegs"
    }
}
