package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Whether the sun really shines on a ride: the gate, the hourly cloud cover, the answer of Open-Meteo and the cache. */
class SunWeatherTest {
    private val hour = 3_600_000L

    // Three hours from 2026-06-01 10:00 UTC: clear, half cloudy, overcast.
    private val start = 1_780_308_000_000L
    private val cover = CloudCover(listOf(start, start + hour, start + 2 * hour), listOf(10, 50, 100))

    @Test
    fun sunShinesOnlyWithTheSunUpAndFewClouds() {
        assertTrue(sunShines(cloudCover = 20, sunElevation = 30.0))
        assertTrue(sunShines(cloudCover = SUNNY_CLOUD_COVER_MAX - 1, sunElevation = 1.0))
        // Too cloudy, exactly at the limit, or no forecast at all.
        assertFalse(sunShines(cloudCover = 85, sunElevation = 30.0))
        assertFalse(sunShines(cloudCover = SUNNY_CLOUD_COVER_MAX, sunElevation = 30.0))
        assertFalse(sunShines(cloudCover = null, sunElevation = 30.0))
        // The sun below the horizon, however clear the sky.
        assertFalse(sunShines(cloudCover = 0, sunElevation = -4.0))
        assertFalse(sunShines(cloudCover = 0, sunElevation = 0.0))
    }

    @Test
    fun cloudCoverIsTheMeanOfTheHoursOfTheRide() {
        // Within the first hour only.
        assertEquals(10, cloudCoverDuring(cover, start + 10 * 60_000L, start + 40 * 60_000L))
        // Across the first two hours.
        assertEquals(30, cloudCoverDuring(cover, start + 30 * 60_000L, start + hour + 20 * 60_000L))
        // All three.
        assertEquals(53, cloudCoverDuring(cover, start, start + 2 * hour + 1))
        // An hour that only ends where the ride starts does not count.
        assertEquals(50, cloudCoverDuring(cover, start + hour, start + hour + 30 * 60_000L))
    }

    @Test
    fun noCloudCoverOutsideTheForecast() {
        assertNull(cloudCoverDuring(cover, start - 5 * hour, start - 4 * hour))
        assertNull(cloudCoverDuring(cover, start + 3 * hour, start + 4 * hour))
    }

    @Test
    fun parsesTheHourlyAnswerOfOpenMeteo() {
        val body = """
            {"latitude":51.3,"longitude":12.4,"generationtime_ms":0.05,"utc_offset_seconds":0,
             "hourly_units":{"time":"unixtime","cloud_cover":"%"},
             "hourly":{"time":[1780308000,1780311600,1780315200],"cloud_cover":[12,null,99.6]}}
        """.trimIndent()
        val parsed = parseOpenMeteoCloudCover(body)
        assertNotNull(parsed)
        // The hour without a value is left out; seconds become millis, values rounded.
        assertEquals(listOf(1_780_308_000_000L, 1_780_315_200_000L), parsed!!.hourStarts)
        assertEquals(listOf(12, 100), parsed.percent)
    }

    @Test
    fun anAnswerWithoutCloudCoverIsNone() {
        assertNull(parseOpenMeteoCloudCover("""{"error":true,"reason":"Parameter out of range"}"""))
        assertNull(parseOpenMeteoCloudCover("""{"hourly":{"time":[1,2],"cloud_cover":[5]}}"""))
        assertNull(parseOpenMeteoCloudCover("""{"hourly":{"time":[],"cloud_cover":[]}}"""))
    }

    @Test
    fun theAreaIsRoundedToATenthOfADegree() {
        assertEquals(WeatherArea(513, 124), weatherArea(51.3397, 12.3731))
        assertEquals(weatherArea(51.33, 12.37), weatherArea(51.34, 12.38))
        assertEquals(51.3, weatherArea(51.31, 12.38).latitude, 1e-9)
        // Negative positions round as well.
        assertEquals(WeatherArea(-339, -706), weatherArea(-33.87, -70.62))
    }

    @Test
    fun anAnswerIsKeptForThirtyMinutes() {
        val cache = WeatherCache()
        val area = weatherArea(50.73, 7.10)
        assertNull(cache.lookup(area, now = 0L))
        cache.store(area, cover, now = 1_000L)
        assertSame(cover, cache.lookup(area, now = 1_000L + WEATHER_CACHE_MILLIS - 1)?.cover)
        // Asked again once it is old.
        assertNull(cache.lookup(area, now = 1_000L + WEATHER_CACHE_MILLIS))
        assertTrue(WEATHER_CACHE_MILLIS >= 30 * 60_000L)
    }

    @Test
    fun aFailureKeepsTheNextRequestAwayOnlyBriefly() {
        val cache = WeatherCache()
        val area = weatherArea(50.73, 7.10)
        cache.store(area, null, now = 0L)
        val kept = cache.lookup(area, now = WEATHER_FAILURE_MILLIS - 1)
        assertNotNull(kept)
        assertNull(kept!!.cover)
        assertNull(cache.lookup(area, now = WEATHER_FAILURE_MILLIS))
    }

    @Test
    fun theAreaUsedLongestAgoGoesFirst() {
        val cache = WeatherCache(maxAreas = 2)
        val a = WeatherArea(500, 70)
        val b = WeatherArea(510, 120)
        val c = WeatherArea(480, 90)
        cache.store(a, cover, now = 0L)
        cache.store(b, cover, now = 0L)
        // Using a makes b the oldest.
        assertNotNull(cache.lookup(a, now = 1L))
        cache.store(c, cover, now = 2L)
        assertEquals(2, cache.size())
        assertNotNull(cache.lookup(a, now = 3L))
        assertNull(cache.lookup(b, now = 3L))
        assertNotNull(cache.lookup(c, now = 3L))
    }

    @Test
    fun aClockThatWentBackAsksAgain() {
        val cache = WeatherCache()
        val area = weatherArea(50.73, 7.10)
        cache.store(area, cover, now = 10_000L)
        assertNull(cache.lookup(area, now = 5_000L))
    }

    @Test
    fun theTripStateForTheWatchNeverWaitsAndAsksOnce() {
        // #3 of the review: the watch's trip state takes only a kept forecast and asks in the background.
        val cache = WeatherCache()
        val area = weatherArea(50.73, 7.10)
        // Nothing kept: none now, asked in the background (once, not again while asked, not at "Extrem").
        val none = keptWeather(cache, area, 1_000L, emptySet(), mayAsk = true)
        assertNull(none.cover)
        assertTrue(none.ask)
        assertFalse(keptWeather(cache, area, 1_000L, setOf(area), mayAsk = true).ask)
        assertFalse(keptWeather(cache, area, 1_000L, emptySet(), mayAsk = false).ask)
        // Kept: taken as it is, not asked again within the 30 minutes.
        cache.store(area, cover, 2_000L)
        val kept = keptWeather(cache, area, 2_000L + WEATHER_CACHE_MILLIS - 1, emptySet(), mayAsk = true)
        assertSame(cover, kept.cover)
        assertFalse(kept.ask)
        assertTrue(keptWeather(cache, area, 2_000L + WEATHER_CACHE_MILLIS, emptySet(), mayAsk = true).ask)
        // A failure keeps the next request away for a while too.
        cache.store(area, null, 10_000_000L)
        assertFalse(keptWeather(cache, area, 10_000_000L + WEATHER_FAILURE_MILLIS - 1, emptySet(), mayAsk = true).ask)
    }
}
