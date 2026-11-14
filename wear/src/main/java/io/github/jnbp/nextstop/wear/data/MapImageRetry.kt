package io.github.jnbp.nextstop.wear.data

/*
 * When the watch asks the phone again for a map picture that did not come, and when it tells
 * the user. A picture that fails once is usually a cold start of MapLibre on the phone or a
 * slow network, and the next try a moment later works: so the first failure is retried
 * quietly ([QUIET_RETRIES], after [QUIET_PAUSE_MILLIS]) while the map keeps its loading
 * shimmer, and only a failure after that shows "Map not loaded · Try again". "No phone
 * connected" is said at once: waiting does not help there. Then the map keeps asking every
 * [PAUSE_MILLIS] while it is open. The pictures fetched ahead for a trip (TripMapPrefetch)
 * are tried again [PREFETCH_RETRIES] times, [PREFETCH_PAUSE_MILLIS] later, silently.
 */
object MapImageRetry {
    /** Failures retried without telling the user. */
    const val QUIET_RETRIES = 1

    /** The pause before a quiet retry (milliseconds): the phone's snapshotter is warm by then. */
    const val QUIET_PAUSE_MILLIS = 1_500L

    /** The pause between later retries while the map is open (milliseconds). */
    const val PAUSE_MILLIS = 15_000L

    /** How often a picture fetched ahead is tried again after it failed. */
    const val PREFETCH_RETRIES = 2

    /** The least time before a picture fetched ahead is tried again (milliseconds). */
    const val PREFETCH_PAUSE_MILLIS = 60_000L

    /** Whether the user is told of [status] after [failures] failures in a row; else the map keeps loading. */
    fun showProblem(status: MapImageStatus, failures: Int): Boolean = when (status) {
        MapImageStatus.Ready, MapImageStatus.Waiting -> false
        MapImageStatus.NoPhone -> true
        MapImageStatus.NoAnswer, MapImageStatus.NotDrawn -> failures > QUIET_RETRIES
    }

    /** The pause before the next try after [failures] failures in a row. */
    fun pauseAfter(failures: Int): Long = if (failures <= QUIET_RETRIES) QUIET_PAUSE_MILLIS else PAUSE_MILLIS

    /** Whether a picture fetched ahead that failed [failures] times is tried again. */
    fun prefetchAgain(failures: Int): Boolean = failures in 1..PREFETCH_RETRIES
}
