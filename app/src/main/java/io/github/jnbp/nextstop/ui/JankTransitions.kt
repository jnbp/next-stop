package io.github.jnbp.nextstop.ui

import java.util.Locale

/*
 * The page changes the jank meter follows (see JankMeter): for each change of page (from, to)
 * how long it took from the change being noted to the first draw of the new page, the
 * longest frame while it ran, how many frames it drew in all, and how often the glass
 * backdrop got a new picture meanwhile. A 220 ms animation that drew only 1 or 2 frames shows
 * here as such. Pure logic, no Android.
 */

/** One change of page while it runs; times in nanoseconds of the monotonic clock. */
class TransitionRun(val from: String, val to: String, val startNanos: Long) {
    /** When the new page was first drawn; -1 before. */
    var firstDrawNanos = -1L

    /** Frames that ended after the start, while it ran. */
    var frames = 0

    /** The longest of them (work on the main and render thread). */
    var longestNanos = 0L

    /** Pictures the glass backdrop took meanwhile. */
    var backdrops = 0

    /** One frame that began at [frameStartNanos] and took [workNanos]: counted when it ended after the start. */
    fun frame(frameStartNanos: Long, workNanos: Long) {
        if (frameStartNanos + workNanos < startNanos) return
        frames++
        if (workNanos > longestNanos) longestNanos = workNanos
    }
}

/** All runs of one change of page (from, to). */
class TransitionStats {
    var count = 0
        private set
    var firstSumMillis = 0.0
        private set
    var firstMaxMillis = 0.0
        private set
    var longestMaxMillis = 0.0
        private set
    var framesMin = Int.MAX_VALUE
        private set
    var framesSum = 0L
        private set
    var backdrops = 0
        private set

    /** Adds [run]; a run whose new page was never drawn counts its first draw as at its end ([endNanos]). */
    fun add(run: TransitionRun, endNanos: Long) {
        count++
        val drawn = if (run.firstDrawNanos >= 0L) run.firstDrawNanos else endNanos
        val first = (drawn - run.startNanos).coerceAtLeast(0L) / 1_000_000.0
        firstSumMillis += first
        if (first > firstMaxMillis) firstMaxMillis = first
        val longest = run.longestNanos / 1_000_000.0
        if (longest > longestMaxMillis) longestMaxMillis = longest
        if (run.frames < framesMin) framesMin = run.frames
        framesSum += run.frames
        backdrops += run.backdrops
    }
}

/** The name of a change of page: "Plan→Connections". */
fun transitionName(from: String, to: String): String = "$from→$to"

/**
 * "Plan→Connections: 3× · first draw Ø 412 ms, max 950 ms · longest frame 940.0 ms ·
 * frames min 1, Ø 8.3 · backdrop 2", with the words given (in the app's language).
 */
fun transitionText(name: String, stats: TransitionStats, firstWord: String, longestWord: String, framesWord: String, backdropWord: String): String =
    String.format(
        Locale.ROOT,
        "%s: %d× · %s Ø %.0f ms, max %.0f ms · %s %.1f ms · %s min %d, Ø %.1f · %s %d",
        name,
        stats.count,
        firstWord,
        if (stats.count == 0) 0.0 else stats.firstSumMillis / stats.count,
        stats.firstMaxMillis,
        longestWord,
        stats.longestMaxMillis,
        framesWord,
        if (stats.count == 0) 0 else stats.framesMin,
        if (stats.count == 0) 0.0 else stats.framesSum.toDouble() / stats.count,
        backdropWord,
        stats.backdrops,
    )
