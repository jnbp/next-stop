package io.github.jnbp.nextstop.data

import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/*
 * Whether the sun really shines on a ride, for the hint of the sunny side (see SunSide): the
 * sun must stand above the horizon and the sky must be mostly clear. The cloud cover comes from
 * Open-Meteo (free, no key, CC BY 4.0), hour by hour, for an area of about 11 km around the
 * middle of the ride. It is asked only while a hint is on screen (in the foreground or the
 * open companion) or a followed trip goes to the watch (in the background, see keptCloudCover), at most once per area in [WEATHER_CACHE_MILLIS], and only the rounded
 * area is sent. Without network or weather the hint stays away.
 */

/** A cloud cover below this share (in percent) counts as sunny. */
const val SUNNY_CLOUD_COVER_MAX = 60

/** How long the cloud cover of an area is kept before it is asked again. */
const val WEATHER_CACHE_MILLIS = 30 * 60_000L

/** How long a failed request keeps the next one away for the same area. */
const val WEATHER_FAILURE_MILLIS = 5 * 60_000L

// The most areas kept at a time; the one used longest ago goes first.
private const val WEATHER_MAX_AREAS = 8

private const val HOUR_MILLIS = 3_600_000L

/** An area of about 11 km: a position rounded to a tenth of a degree. Only this is ever sent. */
data class WeatherArea(val latitudeTenths: Int, val longitudeTenths: Int) {
    val latitude: Double get() = latitudeTenths / 10.0
    val longitude: Double get() = longitudeTenths / 10.0
}

/** The area of the position [latitude], [longitude]. */
fun weatherArea(latitude: Double, longitude: Double): WeatherArea =
    WeatherArea((latitude * 10).roundToInt(), (longitude * 10).roundToInt())

/** The cloud cover of an area, hour by hour: [hourStarts] in epoch millis (UTC), [percent] the same length. */
data class CloudCover(val hourStarts: List<Long>, val percent: List<Int>)

/**
 * The mean cloud cover in percent of the hours that [from] to [to] (epoch millis) touch; null
 * where the forecast covers none of them.
 */
fun cloudCoverDuring(cover: CloudCover, from: Long, to: Long): Int? {
    val end = maxOf(from, to)
    var sum = 0
    var count = 0
    for (index in cover.hourStarts.indices) {
        val start = cover.hourStarts[index]
        if (start + HOUR_MILLIS <= from || start > end) continue
        sum += cover.percent[index]
        count++
    }
    return if (count == 0) null else (sum.toDouble() / count).roundToInt()
}

/** True where the sun shows: it stands above the horizon and the [cloudCover] is known and below [SUNNY_CLOUD_COVER_MAX]. */
fun sunShines(cloudCover: Int?, sunElevation: Double): Boolean =
    sunElevation > 0.0 && cloudCover != null && cloudCover < SUNNY_CLOUD_COVER_MAX

// The arrays of an answer of Open-Meteo with timeformat=unixtime: "time":[…] and "cloud_cover":[…].
private val TIME_ARRAY = Regex(""""time"\s*:\s*\[([^\]]*)]""")
private val COVER_ARRAY = Regex(""""cloud_cover"\s*:\s*\[([^\]]*)]""")

/**
 * The hourly cloud cover in an answer of Open-Meteo asked with `hourly=cloud_cover` and
 * `timeformat=unixtime`; null where the answer has none. Hours without a value are left out.
 * A plain scan, so it needs no JSON library.
 */
fun parseOpenMeteoCloudCover(body: String): CloudCover? {
    val times = TIME_ARRAY.find(body)?.groupValues?.get(1)?.split(',')?.map { it.trim() } ?: return null
    val covers = COVER_ARRAY.find(body)?.groupValues?.get(1)?.split(',')?.map { it.trim() } ?: return null
    if (times.size != covers.size) return null
    val starts = ArrayList<Long>(times.size)
    val percent = ArrayList<Int>(times.size)
    for (index in times.indices) {
        val second = times[index].toLongOrNull() ?: continue
        val value = covers[index].toDoubleOrNull() ?: continue
        starts += second * 1000L
        percent += value.roundToInt().coerceIn(0, 100)
    }
    return if (starts.isEmpty()) null else CloudCover(starts, percent)
}

/**
 * The cloud cover per area as last asked, in memory only: a fresh answer for [ttlMillis], a
 * failure for [failureMillis] (so a missing network is not asked again and again), at most
 * [maxAreas] areas.
 */
class WeatherCache(
    private val ttlMillis: Long = WEATHER_CACHE_MILLIS,
    private val failureMillis: Long = WEATHER_FAILURE_MILLIS,
    private val maxAreas: Int = WEATHER_MAX_AREAS,
) {
    /** What is kept for an area: [cover] null after a failed request. */
    class Entry(val cover: CloudCover?, val at: Long)

    private val entries = LinkedHashMap<WeatherArea, Entry>(maxAreas + 1, 0.75f, true)

    /** The entry of [area] still valid at [now]; null where it must be asked (again). */
    @Synchronized
    fun lookup(area: WeatherArea, now: Long): Entry? {
        val entry = entries[area] ?: return null
        val age = now - entry.at
        val valid = if (entry.cover != null) ttlMillis else failureMillis
        if (age < 0 || age >= valid) {
            entries.remove(area)
            return null
        }
        return entry
    }

    /** Keeps [cover] (null: the request failed) for [area] from [now] on. */
    @Synchronized
    fun store(area: WeatherArea, cover: CloudCover?, now: Long) {
        entries[area] = Entry(cover, now)
        while (entries.size > maxAreas) {
            val oldest = entries.keys.iterator().next()
            entries.remove(oldest)
        }
    }

    @Synchronized
    fun size(): Int = entries.size
}

/**
 * What a caller that must not wait (the trip state for the watch) gets of the forecast of an
 * area: [cover], where one is kept, and [ask], true where it is to be asked in the background:
 * nothing kept (not even a recent failure, see [WeatherCache]), not being asked already, and
 * asking allowed ([mayAsk] false at the battery saver's "Extrem").
 */
class KeptWeather(val cover: CloudCover?, val ask: Boolean)

/** The forecast of [area] in [cache] at [now] for a caller that must not wait (see [KeptWeather]). */
fun keptWeather(cache: WeatherCache, area: WeatherArea, now: Long, asking: Set<WeatherArea>, mayAsk: Boolean): KeptWeather {
    val kept = cache.lookup(area, now)
    return KeptWeather(kept?.cover, ask = kept == null && mayAsk && area !in asking)
}

/** The cloud cover of Open-Meteo for the hint of the sunny side, cached per area (see [WeatherCache]). */
object SunWeather {
    private val cache = WeatherCache()
    private val lock = Mutex()

    // The areas asked for in the background right now (see keptCloudCover).
    private val asking = HashSet<WeatherArea>()

    /**
     * As [cloudCover], but never waiting for the network: only a kept forecast counts. Where none
     * is kept (and [mayAsk]), the area is asked in [scope] once, in the background, under the same
     * rules (one request at a time, kept for [WEATHER_CACHE_MILLIS]): a later call has it.
     */
    fun keptCloudCover(latitude: Double, longitude: Double, from: Long, to: Long, mayAsk: Boolean, scope: CoroutineScope): Int? {
        val area = weatherArea(latitude, longitude)
        val kept = synchronized(asking) {
            keptWeather(cache, area, System.currentTimeMillis(), asking, mayAsk).also { if (it.ask) asking.add(area) }
        }
        if (kept.ask) {
            scope.launch {
                try {
                    cloudCover(latitude, longitude, from, to)
                } finally {
                    synchronized(asking) { asking.remove(area) }
                }
            }
        }
        return kept.cover?.let { cloudCoverDuring(it, from, to) }
    }

    /**
     * The mean cloud cover in percent around [latitude], [longitude] from [from] to [to] (epoch
     * millis); null without network or forecast. With [mayAsk] false only a cached answer
     * counts (the battery saver at "Extrem").
     */
    suspend fun cloudCover(latitude: Double, longitude: Double, from: Long, to: Long, mayAsk: Boolean = true): Int? {
        val area = weatherArea(latitude, longitude)
        // One request at a time: rides of the same area wait for the first and share its answer.
        val cover = lock.withLock {
            val now = System.currentTimeMillis()
            val kept = cache.lookup(area, now)
            when {
                kept != null -> kept.cover
                !mayAsk -> null
                else -> {
                    val asked = try {
                        withContext(Dispatchers.IO) { fetch(area) }
                    } catch (e: CancellationException) {
                        // The hint left the screen: nothing is kept, the next one asks again.
                        throw e
                    } catch (e: Exception) {
                        // No position in the log, only that it failed.
                        DiagLog.i(LOG_TAG) { "cloud cover failed: ${e.javaClass.simpleName}" }
                        null
                    }
                    cache.store(area, asked, System.currentTimeMillis())
                    asked
                }
            }
        } ?: return null
        return cloudCoverDuring(cover, from, to)
    }

    private fun fetch(area: WeatherArea): CloudCover? {
        val url = String.format(
            Locale.ROOT,
            "%s?latitude=%.1f&longitude=%.1f&hourly=cloud_cover&timeformat=unixtime&timezone=GMT&forecast_days=3",
            OPEN_METEO_URL,
            area.latitude,
            area.longitude,
        )
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) return null
            return parseOpenMeteoCloudCover(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private const val LOG_TAG = "SunWeather"

    /** The forecast API of Open-Meteo. */
    const val OPEN_METEO_URL = "https://api.open-meteo.com/v1/forecast"

    /** The page of Open-Meteo, for the attribution. */
    const val OPEN_METEO_PAGE = "https://open-meteo.com"
}
