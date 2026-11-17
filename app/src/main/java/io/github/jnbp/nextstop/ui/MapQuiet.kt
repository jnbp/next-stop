package io.github.jnbp.nextstop.ui

import android.os.SystemClock
import kotlinx.coroutines.delay

/*
 * When the map is quiet enough for heavy work that is no hurry: the networks of S-, U-Bahn and
 * tram, the packs of other states, the change positions and the station details put into the
 * map. Right after the start the map loads its tiles while people already pan it; all of that
 * work landing in the same seconds (each with megabytes of garbage on other threads and a new
 * source for the renderer) made the first pan run at half the frame rate of a warm one. Such
 * work waits until the map has been shown a while and the camera has rested a moment, but never
 * longer than a cap, so it still comes for someone who keeps panning (or with no map at all).
 */
internal object MapQuiet {
    // When the first map of the process was ready (uptime), 0 before.
    @Volatile
    var shownAt = 0L
        private set

    // Whether the camera moves now, and since when it rests.
    @Volatile
    private var moving = false

    @Volatile
    private var restingSince = 0L

    /** Called when a map is ready. */
    fun mapReady() {
        val now = SystemClock.uptimeMillis()
        if (shownAt == 0L) shownAt = now
        moving = false
        restingSince = now
    }

    /** Called when the camera starts to move (any reason). */
    fun moveStarted() {
        moving = true
    }

    /** Called for each step of the camera: the rest counts from the last one. */
    fun moved() {
        restingSince = SystemClock.uptimeMillis()
    }

    /** Called when the camera comes to rest. */
    fun idle() {
        moving = false
        restingSince = SystemClock.uptimeMillis()
    }

    /** True in the first FRESH_MAP_MILLIS after the first map was shown (and before it is). */
    fun fresh(now: Long = SystemClock.uptimeMillis()): Boolean = shownAt == 0L || now - shownAt < FRESH_MAP_MILLIS

    /** True when the camera has rested [restMillis] and the map has been shown [afterShownMillis] at [now]. */
    fun quiet(now: Long, afterShownMillis: Long, restMillis: Long = REST_MILLIS): Boolean =
        shownAt != 0L && now - shownAt >= afterShownMillis && !moving && now - restingSince >= restMillis

    /**
     * Waits until the map has been shown [afterShownMillis] and the camera rested
     * [restMillis], at most [maxWaitMillis] from now.
     */
    suspend fun awaitRest(afterShownMillis: Long, restMillis: Long = REST_MILLIS, maxWaitMillis: Long = MAX_WAIT_MILLIS) {
        val until = SystemClock.uptimeMillis() + maxWaitMillis
        while (true) {
            val now = SystemClock.uptimeMillis()
            if (now >= until || quiet(now, afterShownMillis, restMillis)) return
            delay(POLL_MILLIS)
        }
    }
}

// The first seconds of the map, while it loads its tiles (see MapQuiet.fresh).
internal const val FRESH_MAP_MILLIS = 8_000L

// How long the camera must rest before heavy work goes on.
private const val REST_MILLIS = 700L

// The longest such work waits for a quiet map.
private const val MAX_WAIT_MILLIS = 20_000L

private const val POLL_MILLIS = 150L
