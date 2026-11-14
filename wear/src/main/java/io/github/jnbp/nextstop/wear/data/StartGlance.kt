package io.github.jnbp.nextstop.wear.data

/*
 * The small pieces of logic behind the start page of the watch, free of Android so they run as
 * JVM tests: which departures the live card and the favourites show, when the minutes of a
 * countdown change next (so the page redraws then and not every second), how far a ride is on
 * its way to the stop to get off, and which lines a connection rides (the "usual" lines a
 * favourite shows without a search).
 */

/** A departure counts as gone this long after its time, as in the cached board (it may be late without live data). */
const val GLANCE_GONE_MILLIS = 30_000L

/** The most lines a favourite shows as its usual connection. */
const val USUAL_LINES_MAX = 3

/**
 * The next [count] departures of [departures] that can still be caught at [now]: not cancelled,
 * with a time, not gone yet (see [GLANCE_GONE_MILLIS]), soonest first. The board itself keeps
 * runs by their planned time; the live card wants the one that really leaves next.
 */
fun upcomingDepartures(departures: List<Departure>, now: Long, count: Int): List<Departure> {
    if (count <= 0) return emptyList()
    return departures
        .filter { !it.cancelled && it.time != null && it.time >= now - GLANCE_GONE_MILLIS }
        .sortedBy { it.time }
        .take(count)
}

/** The one departure a favourite stop shows: the next that can be caught (see [upcomingDepartures]); null when none. */
fun nextCatchable(departures: List<Departure>?, now: Long): Departure? =
    if (departures == null) null else upcomingDepartures(departures, now, 1).firstOrNull()

/**
 * Milliseconds from [now] until the minutes of any of [times] change (see [minutesUntil], which
 * rounds to the nearest minute): the countdown is redrawn then instead of on a fixed tick.
 * Times already at "now" do not change any more and are left out. At least [MIN_TICK_MILLIS],
 * at most a minute (also when nothing counts down).
 */
fun millisToNextMinuteChange(times: List<Long>, now: Long): Long {
    var soonest = MINUTE_MILLIS
    for (time in times) {
        val shifted = time - now + 30_000
        if (shifted < MINUTE_MILLIS) continue
        // minutesUntil is shifted / 60 s; it drops by one once shifted falls below its full minutes.
        val wait = shifted % MINUTE_MILLIS + 1
        if (wait < soonest) soonest = wait
    }
    return soonest.coerceIn(MIN_TICK_MILLIS, MINUTE_MILLIS)
}

/**
 * How far a ride is on its way from boarding to getting off at [now], 0 to 1, from the times of
 * its stops ([stopTimes], boarding first, the stop to get off last; 0 for a stop without a time):
 * whole stops passed plus the share of the way to the next one by time, so each stop is an equal
 * step of the ring that shows it. Null with fewer than two stops or without times to go by.
 */
fun rideProgress(stopTimes: List<Long>, now: Long): Float? {
    if (stopTimes.size < 2 || stopTimes.none { it > 0 }) return null
    val last = stopTimes.lastIndex
    val current = stopTimes.indexOfLast { it in 1..now }
    if (current < 0) return 0f
    if (current >= last) return 1f
    val from = stopTimes[current]
    // The next stop with a time; stops between without one are passed in equal parts of its way.
    val next = (current + 1..last).firstOrNull { stopTimes[it] > 0 } ?: return current.toFloat() / last
    val to = stopTimes[next]
    val share = if (to > from) ((now - from).toFloat() / (to - from)).coerceIn(0f, 1f) else 0f
    return ((current + share * (next - current)) / last).coerceIn(0f, 1f)
}

/**
 * The stops left on board until getting off: as the phone counts them ([reported], above 0),
 * else from the times of the stops ([stopTimes], see [rideProgress]): the stops after the one
 * passed last, at least 1 while on board. -1 when neither tells.
 */
fun ridingStopsLeft(reported: Int, stopTimes: List<Long>, now: Long): Int = when {
    reported > 0 -> reported
    stopTimes.size >= 2 -> (stopTimes.lastIndex - stopTimes.indexOfLast { it in 1..now }.coerceAtLeast(0)).coerceAtLeast(1)
    else -> -1
}

/** The lines a connection rides, in order, each once, at most [USUAL_LINES_MAX]: what a favourite shows as its usual way there. */
fun usualLinesOf(journey: Journey): List<StopLine> =
    journey.rides.mapNotNull { ride -> ride.line?.takeIf { it.isNotBlank() }?.let { StopLine(it, ride.product) } }
        .distinct()
        .take(USUAL_LINES_MAX)

private const val MINUTE_MILLIS = 60_000L

// Never redraw a countdown more often than this, whatever the times say.
private const val MIN_TICK_MILLIS = 1_000L
