package io.github.jnbp.nextstop.data

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * A line of the kept network map: its way, when a trip of it was last found ([seen]) and when
 * the way was taken ([wayAt]). See [mergeNetwork].
 */
data class KeptNetworkLine(val line: NetworkLine, val seen: Long, val wayAt: Long)

// A line no build has found for this long is gone from the network (closed or renamed).
internal const val NETWORK_KEEP_MILLIS = 30L * 24 * 60 * 60 * 1000

// A new way of a line replaces the kept one only when it is about as long: a trip that turns
// back early (at night, during works) must not cut a line short.
internal const val NETWORK_SHORTER_SHARE = 0.9

/**
 * The kept network [old] with the lines of a new build [fresh] (made at [now]) merged in: a
 * union, never a smaller set. Every line of either stays; a line found again takes the new way
 * only when it is at least NETWORK_SHORTER_SHARE as long as the kept one, or the kept way is
 * older than NETWORK_KEEP_MILLIS; only lines no build has found for NETWORK_KEEP_MILLIS drop
 * out. A build at night that finds half the lines thus removes none.
 */
fun mergeNetwork(old: List<KeptNetworkLine>, fresh: List<NetworkLine>, now: Long): List<KeptNetworkLine> {
    val result = LinkedHashMap<String, KeptNetworkLine>()
    for (kept in old) {
        if (now - kept.seen <= NETWORK_KEEP_MILLIS) result[kept.line.line] = kept
    }
    for (line in fresh) {
        if (line.points.size < 2) continue
        val kept = result[line.line]
        result[line.line] = when {
            kept == null -> KeptNetworkLine(line, now, now)
            now - kept.wayAt > NETWORK_KEEP_MILLIS ||
                wayMetres(line.points) >= NETWORK_SHORTER_SHARE * wayMetres(kept.line.points) ->
                KeptNetworkLine(line, now, now)
            else -> kept.copy(seen = now)
        }
    }
    return result.values.sortedBy { it.line.line }
}

/** The way of [points] in metres, flat on the earth (a city's size is plenty for that). */
internal fun wayMetres(points: List<Pair<Double, Double>>): Double {
    var total = 0.0
    for (index in 1 until points.size) {
        val a = points[index - 1]
        val b = points[index]
        val dy = (b.first - a.first) * 111_320.0
        val dx = (b.second - a.second) * 111_320.0 * cos(Math.toRadians((a.first + b.first) / 2))
        total += sqrt(dx * dx + dy * dy)
    }
    return total
}

// Hours of the day (local) in which the network is sampled as it runs right now; at other hours
// the radar is asked for the next midday instead, when every line runs.
private const val DAY_FROM_HOUR = 7
private const val DAY_UNTIL_HOUR = 20
private const val MIDDAY_HOUR = 12

/**
 * The moment whose running trips a build of the network samples: null (right now) by day, else
 * the next midday in [zone]. At night most U-Bahn and many S-Bahn and tram lines do not run, and
 * a radar of the night found only those that do.
 */
fun networkSampleTime(now: Long, zone: ZoneId): OffsetDateTime? {
    val local = Instant.ofEpochMilli(now).atZone(zone)
    if (local.hour in DAY_FROM_HOUR until DAY_UNTIL_HOUR) return null
    var midday = local.withHour(MIDDAY_HOUR).withMinute(0).withSecond(0).withNano(0)
    if (!midday.isAfter(local)) midday = midday.plusDays(1)
    return midday.toOffsetDateTime()
}
