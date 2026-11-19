package io.github.jnbp.nextstop.ui

import java.util.Locale

/*
 * The counts of the jank meter of the developer options (see JankMeter): per screen the frames,
 * the share of janky ones, the 95th percentile and the longest frame, kept in a fixed histogram
 * so that a frame costs no allocation. Pure logic, no Android.
 */

// The histogram of frame durations: one bucket per millisecond up to this, the last for all longer.
const val JANK_BUCKETS = 250

/** The frames of one screen. Not thread-safe by itself: [JankMeter] guards it. */
class JankTally {
    private val buckets = IntArray(JANK_BUCKETS + 1)

    /** Frames counted. */
    var frames = 0L
        private set

    /** Frames that were janky (as JankStats says). */
    var janky = 0L
        private set

    /** Frames that reached the screen after their deadline (a vsync was missed). */
    var late = 0L
        private set

    /** The longest frame (ns). */
    var maxNanos = 0L
        private set

    /**
     * One frame whose work took [durationNanos] (main and render thread), [jank] or not as
     * JankStats says, and [late] when it missed its deadline.
     */
    fun record(durationNanos: Long, jank: Boolean, late: Boolean = false) {
        if (durationNanos < 0L) return
        frames++
        if (jank) janky++
        if (late) this.late++
        if (durationNanos > maxNanos) maxNanos = durationNanos
        val bucket = (durationNanos / 1_000_000L).coerceAtMost(JANK_BUCKETS.toLong()).toInt()
        buckets[bucket]++
    }

    /** The share of janky frames in percent, 0 without frames. */
    fun jankPercent(): Double = if (frames == 0L) 0.0 else janky * 100.0 / frames

    /** The share of late frames in percent, 0 without frames. */
    fun latePercent(): Double = if (frames == 0L) 0.0 else late * 100.0 / frames

    /** The duration (ms) that 95 % of the frames stay within: the upper edge of its bucket; 0 without frames. */
    fun p95Millis(): Int {
        if (frames == 0L) return 0
        val wanted = Math.ceil(frames * 0.95).toLong()
        var seen = 0L
        for (bucket in buckets.indices) {
            seen += buckets[bucket]
            if (seen >= wanted) return bucket + 1
        }
        return JANK_BUCKETS + 1
    }

    /** All counts back to none. */
    fun reset() {
        buckets.fill(0)
        frames = 0L
        janky = 0L
        late = 0L
        maxNanos = 0L
    }
}

/** One screen's numbers as the developer options show them. */
data class JankLine(
    val screen: String,
    val frames: Long,
    val jankPercent: Double,
    val p95Millis: Int,
    val maxMillis: Double,
    val latePercent: Double = 0.0,
)

/** The numbers of [screen] from [tally]. */
fun jankLine(screen: String, tally: JankTally): JankLine =
    JankLine(screen, tally.frames, tally.jankPercent(), tally.p95Millis(), tally.maxNanos / 1_000_000.0, tally.latePercent())

/**
 * "1234 frames · 2.1 % janky · 0.8 % late · p95 12 ms · max 48.3 ms", with the words given (in
 * the app's language); without [lateWord] the late share is left out.
 */
fun jankText(line: JankLine, framesWord: String, jankyWord: String, lateWord: String? = null): String =
    if (lateWord == null) {
        String.format(
            Locale.ROOT,
            "%d %s · %.1f %% %s · p95 %d ms · max %.1f ms",
            line.frames, framesWord, line.jankPercent, jankyWord, line.p95Millis, line.maxMillis,
        )
    } else {
        String.format(
            Locale.ROOT,
            "%d %s · %.1f %% %s · %.1f %% %s · p95 %d ms · max %.1f ms",
            line.frames, framesWord, line.jankPercent, jankyWord, line.latePercent, lateWord, line.p95Millis, line.maxMillis,
        )
    }

// What had the map draw a frame while its camera rested (see MapRestCauses): bits, any mix.
const val MAP_CAUSE_VEHICLES = 1
const val MAP_CAUSE_PULSE = 2

/**
 * The frames the map drew while its camera rested, by what had asked for them: new places of
 * the vehicles, a beat of the live pulse, or anything else (tiles, a fade, the map's own
 * transitions). A frame asked for by both counts for the vehicles. Not thread-safe: main thread.
 */
class MapRestCauses {
    var vehicles = 0L
        private set
    var pulse = 0L
        private set
    var other = 0L
        private set

    /** One frame drawn at rest after the causes [pending] (MAP_CAUSE_ bits) asked for it. */
    fun record(pending: Int) {
        when {
            pending and MAP_CAUSE_VEHICLES != 0 -> vehicles++
            pending and MAP_CAUSE_PULSE != 0 -> pulse++
            else -> other++
        }
    }

    fun reset() {
        vehicles = 0L
        pulse = 0L
        other = 0L
    }
}

/** "vehicles 2.1 · pulse 13.5 · other 1.0" per second of [restMillis], with the words given; empty before a second of rest. */
fun mapCauseText(causes: MapRestCauses, restMillis: Long, vehiclesWord: String, pulseWord: String, otherWord: String): String {
    if (restMillis < 1_000L) return ""
    val perSecond = 1000.0 / restMillis
    return String.format(
        Locale.ROOT,
        "%s %.1f · %s %.1f · %s %.1f",
        vehiclesWord, causes.vehicles * perSecond, pulseWord, causes.pulse * perSecond, otherWord, causes.other * perSecond,
    )
}

/** Map frames drawn while the camera rested, per second of rest; NaN before a second of rest. */
fun idleFps(idleFrames: Long, idleMillis: Long): Double =
    if (idleMillis < 1_000L) Double.NaN else idleFrames * 1000.0 / idleMillis

/** The whole report for the clipboard: a head line, one line per screen, and the map's frames in rest. */
fun jankReport(
    head: String,
    lines: List<JankLine>,
    framesWord: String,
    jankyWord: String,
    mapLine: String,
    lateWord: String? = null,
): String =
    buildString {
        append(head).append('\n')
        for (line in lines) append(line.screen).append(": ").append(jankText(line, framesWord, jankyWord, lateWord)).append('\n')
        append(mapLine)
    }
