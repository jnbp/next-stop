package io.github.jnbp.nextstop.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import io.github.jnbp.nextstop.data.Stopover
import io.github.jnbp.nextstop.data.plausible
import io.github.jnbp.nextstop.data.ridePlace
import io.github.jnbp.nextstop.data.TripCourse
import io.github.jnbp.nextstop.data.Vehicle
import io.github.jnbp.nextstop.ui.theme.AppColors
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

// A vehicle is shown at its first stop this long before it leaves there.
const val LEAD_MILLIS = 120_000L

// A live position farther than this from the way of a trip is not put on its track.
private const val OFF_TRACK_METRES = 400.0

// A live position moves along the way the provider gives with it over this time.
const val LIVE_GLIDE_MILLIS = 30_000L

/**
 * A vehicle as the provider reports it live, with the moment ([since], uptime) the report
 * came in: from then on it moves along the way the report gives over [LIVE_GLIDE_MILLIS],
 * the same way the vehicles of the map move.
 */
class LiveVehicle(val vehicle: Vehicle, val since: Long) {
    /** Writes latitude and longitude at [millis] (uptime) into [out]; false without a position. */
    fun locate(millis: Long, out: DoubleArray): Boolean {
        val path = vehicle.path
        if (path.isEmpty()) return false
        val progress = ((millis - since).toFloat() / LIVE_GLIDE_MILLIS).coerceIn(0f, 1f)
        if (path.size < 2) {
            out[0] = path[0].first
            out[1] = path[0].second
            return true
        }
        val scaled = progress * (path.size - 1)
        val index = scaled.toInt().coerceAtMost(path.size - 2)
        val part = scaled - index
        out[0] = path[index].first + (path[index + 1].first - path[index].first) * part
        out[1] = path[index].second + (path[index + 1].second - path[index].second) * part
        return true
    }
}

// Moving marks are drawn again this often. They cover a few pixels per second at most, so this looks smooth.
private const val BEAT_MILLIS = 120L

// How quickly a moving mark takes over a new place: after this time about two thirds of the
// way are done, after a second nearly all of it.
private const val GLIDE_MILLIS = 250.0

/**
 * Lets a moving mark follow the place the data gives it without jumps. Moving on by the
 * clock it follows closely; when live data corrects the place, forward or back (a delay can
 * put a vehicle behind where it was drawn), it glides there over about a second. [size]
 * numbers are followed together, for example latitude and longitude.
 */
class Glide(size: Int) {
    private val values = DoubleArray(size) { Double.NaN }
    private var at = 0L

    /** Moves the followed numbers towards [target] for the time [millis] and writes them back into [target]. */
    fun follow(target: DoubleArray, millis: Long) {
        if (values[0].isNaN()) {
            target.copyInto(values)
        } else {
            val share = 1.0 - exp(-(millis - at).coerceAtLeast(0L) / GLIDE_MILLIS)
            for (index in values.indices) values[index] += (target[index] - values[index]) * share
            values.copyInto(target)
        }
        at = millis
    }

    /** Forgets the place: the next one is taken as it is, as for a mark that was hidden. */
    fun reset() {
        values[0] = Double.NaN
    }
}

/**
 * A number that goes up a few times per second while it is on screen. A draw or layout
 * step that reads it runs again at every beat, without any recomposition. The beat waits
 * for a frame, so it rests while the app is not shown.
 */
@Composable
fun rememberBeat(): IntState {
    val beat = remember { mutableIntStateOf(0) }
    LaunchedEffect(beat) {
        while (true) {
            delay(BEAT_MILLIS)
            withFrameMillis { beat.intValue++ }
        }
    }
    return beat
}

/**
 * The times of a row of stops as plain numbers, to ask where between its stops a vehicle
 * is at any moment without creating objects. A stop without any time takes the time of
 * the stop before it.
 */
class StopClock(stops: List<Stopover>) {
    val size: Int = stops.size
    private val arrive = LongArray(size)
    private val depart = LongArray(size)

    init {
        var last = Long.MIN_VALUE
        for (index in 0 until size) {
            val stop = stops[index]
            val arrival = (stop.arrival ?: stop.departure)?.toInstant()?.toEpochMilli() ?: last
            // Times never run backwards, whatever the data says.
            arrive[index] = maxOf(arrival, last)
            val departure = (stop.departure ?: stop.arrival)?.toInstant()?.toEpochMilli() ?: arrive[index]
            depart[index] = maxOf(departure, arrive[index])
            last = depart[index]
        }
    }

    /**
     * True from [lead] before the first stop is left until the last one is reached: the
     * vehicle is on the road, or stands ready at its first stop.
     */
    fun active(millis: Long, lead: Long = LEAD_MILLIS): Boolean =
        size >= 2 && millis >= depart[0] - lead && millis <= arrive[size - 1]

    /** When the vehicle leaves its first stop, in epoch milliseconds; null without stops. */
    val firstDeparture: Long? get() = if (size == 0) null else depart[0]

    /** When the vehicle reaches its last stop, in epoch milliseconds; null without stops. */
    val lastArrival: Long? get() = if (size == 0) null else arrive[size - 1]

    /**
     * Where the vehicle is at [millis], counted in stops: 3 is at the fourth stop, 3.5
     * half way from there to the fifth. Between two stops it moves evenly with the time.
     */
    fun progress(millis: Long): Float {
        if (size == 0 || millis <= depart[0]) return 0f
        for (index in 0 until size - 1) {
            val reached = arrive[index + 1]
            if (millis < reached) {
                val left = depart[index]
                if (millis <= left || reached <= left) return index.toFloat()
                return index + (millis - left).toFloat() / (reached - left).toFloat()
            }
        }
        return (size - 1).toFloat()
    }
}

/**
 * One trip as a track a vehicle moves along: its way on the map and the times of its
 * stops. Says where the vehicle is at any moment, by its times and not by a position the
 * provider reports, so it moves evenly between two updates. [lead] is how long before it
 * leaves its first stop the vehicle already stands there; the run that brings it there
 * can be drawn as a track of its own.
 */
class TripTrack(course: TripCourse, private val lead: Long = LEAD_MILLIS) {
    val tripId: String = course.tripId
    val line: String = course.line ?: ""
    val product: String = course.product
    // The colour of its line, by the trip itself: the provider's colour and where it runs.
    private val place: Pair<Double, Double>? = course.ridePlace
    private val providerColor: Int? = course.providerColor
    private val providerTextColor: Int? = course.providerTextColor

    /** The colour of the line of this trip (see [lineColor]). */
    val color: Color get() = lineColor(line, product, place, providerColor)

    /** The colour of the text on [color] (see [lineInk]). */
    fun ink(background: Color): Color = lineInk(line, product, background, place, providerColor, providerTextColor)
    // By the plausible times: where the provider's prognosis is implausible, by the estimate.
    val clock = StopClock(course.plausible().stops)
    private val latitudes: DoubleArray
    private val longitudes: DoubleArray

    // How far along the way every point of it lies, and every stop, in metres.
    private val along: FloatArray
    private val stopAlong: FloatArray

    /** False when neither a way nor two stops with a position are known: there is nothing to draw then. */
    val usable: Boolean

    init {
        // Without a way the straight lines between the stops stand in for it.
        val way = course.path.takeIf { it.size >= 2 } ?: course.stops.mapNotNull { it.place?.position }
        latitudes = DoubleArray(way.size) { way[it].first }
        longitudes = DoubleArray(way.size) { way[it].second }
        along = FloatArray(way.size)
        for (index in 1 until way.size) {
            along[index] = along[index - 1] + metres(
                latitudes[index - 1],
                longitudes[index - 1],
                latitudes[index],
                longitudes[index],
            )
        }
        stopAlong = FloatArray(course.stops.size)
        // Each stop lies at the point of the way nearest to it, and never behind the stop before it.
        var from = 0
        course.stops.forEachIndexed { index, stop ->
            val position = stop.place?.position
            if (position != null && way.isNotEmpty()) {
                var best = from
                var bestDistance = Float.MAX_VALUE
                for (point in from until way.size) {
                    val distance = metres(position.first, position.second, latitudes[point], longitudes[point])
                    if (distance < bestDistance) {
                        bestDistance = distance
                        best = point
                    }
                }
                from = best
            }
            stopAlong[index] = if (way.isEmpty()) 0f else along[from]
        }
        usable = way.size >= 2 && course.stops.size >= 2
    }

    /**
     * Writes latitude and longitude of the vehicle at [millis] into [out]. False when the
     * track has nothing to draw or the vehicle is not on the road.
     */
    fun locate(millis: Long, out: DoubleArray): Boolean {
        if (!usable || !clock.active(millis, lead)) return false
        val at = clock.progress(millis)
        val stop = at.toInt().coerceAtMost(stopAlong.size - 2)
        val target = stopAlong[stop] + (stopAlong[stop + 1] - stopAlong[stop]) * (at - stop)
        // The stretch of the way the vehicle is on: the last point that lies not beyond it.
        var low = 0
        var high = along.size - 1
        while (high - low > 1) {
            val middle = (low + high) / 2
            if (along[middle] <= target) low = middle else high = middle
        }
        val length = along[high] - along[low]
        val part = if (length <= 0f) 0f else ((target - along[low]) / length).coerceIn(0f, 1f)
        out[0] = latitudes[low] + (latitudes[high] - latitudes[low]) * part
        out[1] = longitudes[low] + (longitudes[high] - longitudes[low]) * part
        return true
    }

    /**
     * Where a vehicle at [latitude] and [longitude] stands on this track, counted in stops as
     * [StopClock.progress] counts them (1.5 is halfway from the second stop to the third);
     * null when the track has nothing to draw or the position lies off its way.
     */
    fun progressAt(latitude: Double, longitude: Double): Float? {
        if (!usable) return null
        // The nearest point on the way, as a distance along it.
        val scale = cos(Math.toRadians(latitude))
        var best = Double.MAX_VALUE
        var bestAlong = 0f
        for (index in 0 until along.size - 1) {
            val ax = (longitudes[index] - longitude) * scale
            val ay = latitudes[index] - latitude
            val dx = (longitudes[index + 1] - longitude) * scale - ax
            val dy = latitudes[index + 1] - latitude - ay
            val length = dx * dx + dy * dy
            val share = if (length <= 0.0) 0.0 else (-(ax * dx + ay * dy) / length).coerceIn(0.0, 1.0)
            val px = ax + dx * share
            val py = ay + dy * share
            val distance = px * px + py * py
            if (distance < best) {
                best = distance
                bestAlong = along[index] + (along[index + 1] - along[index]) * share.toFloat()
            }
        }
        if (sqrt(best) * 111_320.0 > OFF_TRACK_METRES) return null
        for (stop in 0 until stopAlong.size - 1) {
            val start = stopAlong[stop]
            val end = stopAlong[stop + 1]
            if (bestAlong <= end || stop == stopAlong.size - 2) {
                val length = end - start
                return stop + if (length <= 0f) 0f else ((bestAlong - start) / length).coerceIn(0f, 1f)
            }
        }
        return null
    }

    // Near enough for a few kilometres: a degree of longitude shrinks with the latitude.
    private fun metres(latitude: Double, longitude: Double, otherLatitude: Double, otherLongitude: Double): Float {
        val north = (otherLatitude - latitude) * 111_320.0
        val east = (otherLongitude - longitude) * 111_320.0 * cos(Math.toRadians(latitude))
        return sqrt(north * north + east * east).toFloat()
    }
}

/**
 * The vehicles of [tracks] on the map, each as the badge of its line inside a lime ring
 * that tells them apart from all other traffic. They glide along their way with the clock.
 * The trips in [realtime] are placed by live data: their badge carries the live dot.
 */
@Composable
fun TrackMarkers(
    map: MapLibreMap?,
    cameraTick: () -> Int,
    tracks: List<TripTrack>,
    live: Map<String, LiveVehicle> = emptyMap(),
    realtime: Set<String> = emptySet(),
) {
    if (map == null) return
    val beat = rememberBeat()
    Box(modifier = Modifier.fillMaxSize()) {
        tracks.forEach { track ->
            key(track.tripId) {
                val spot = remember { DoubleArray(2) }
                val glide = remember { Glide(2) }
                val color = track.color
                val shape = RoundedCornerShape(10.dp)
                Box(
                    modifier = Modifier
                        // Read in the layout step, so moving costs no recomposition.
                        .offset {
                            cameraTick()
                            beat.intValue
                            val millis = System.currentTimeMillis()
                            // A live position of the vehicle goes first, as on the map of all
                            // vehicles; the timetable only stands in without one.
                            val reported = live[track.tripId]
                            val found = if (reported != null) {
                                reported.locate(SystemClock.uptimeMillis(), spot)
                            } else {
                                track.locate(millis, spot)
                            }
                            if (found) {
                                // Fresh live data replaces the track: the mark glides to its new place.
                                glide.follow(spot, millis)
                                val point = map.projection.toScreenLocation(LatLng(spot[0], spot[1]))
                                IntOffset(point.x.roundToInt(), point.y.roundToInt())
                            } else {
                                glide.reset()
                                // Nothing to show: far outside of every screen.
                                IntOffset(-10_000, -10_000)
                            }
                        }
                        .graphicsLayer {
                            translationX = -size.width / 2f
                            translationY = -size.height / 2f
                        },
                ) {
                    Box(
                        modifier = Modifier
                            .shadow(6.dp, shape)
                            .clip(shape)
                            .background(AppColors.Lime)
                            .border(1.5.dp, AppColors.OnLime, shape)
                            .padding(3.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(color)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = shortLine(track.line) ?: track.line,
                            color = track.ink(color),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            maxLines = 1,
                        )
                    }
                    // Placed by live data: the live dot at the upper right corner of the ring.
                    if (track.tripId in realtime) {
                        LiveDot(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .graphicsLayer {
                                    val out = (LIVE_DOT_RADIUS_DP + LIVE_DOT_OUTLINE_DP - LIVE_DOT_INSET_DP).dp.toPx()
                                    translationX = out
                                    translationY = -out
                                },
                        )
                    }
                }
            }
        }
    }
}
