package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The position of the sun on the device, and which side of a ride it shines in on. */
class SunSideTest {
    private val lat = 52.52
    private val lon = 13.40

    private fun millis(text: String): Long = OffsetDateTime.parse(text).toInstant().toEpochMilli()

    @Test
    fun noonInSummerIsHighInTheSouth() {
        // Solar noon in Berlin at the solstice is about 11:08 UTC.
        val sun = sunPosition(millis("2024-06-21T11:08:00Z"), lat, lon)
        assertEquals(180.0, sun.azimuth, 2.0)
        assertEquals(60.9, sun.elevation, 1.0)
    }

    @Test
    fun sunriseAtTheEquinoxIsInTheEast() {
        // Sunrise in Berlin on 20 March 2024 is at about 05:05 UTC.
        val sun = sunPosition(millis("2024-03-20T05:05:00Z"), lat, lon)
        assertEquals(90.0, sun.azimuth, 3.0)
        assertEquals(0.0, sun.elevation, 1.5)
    }

    @Test
    fun winterAfternoonIsLowInTheSouthWest() {
        val sun = sunPosition(millis("2024-12-21T13:00:00Z"), lat, lon)
        assertTrue(sun.azimuth in 205.0..225.0)
        assertTrue(sun.elevation in 5.0..12.0)
    }

    @Test
    fun midnightIsBelowTheHorizon() {
        assertTrue(sunPosition(millis("2024-06-21T23:00:00Z"), lat, lon).elevation < 0)
    }

    @Test
    fun bearings() {
        assertEquals(0.0, bearingDegrees(52.0, 13.0, 53.0, 13.0), 0.01)
        assertEquals(90.0, bearingDegrees(52.0, 13.0, 52.0, 14.0), 0.5)
        assertEquals(180.0, bearingDegrees(53.0, 13.0, 52.0, 13.0), 0.01)
        assertEquals(270.0, bearingDegrees(52.0, 14.0, 52.0, 13.0), 0.5)
    }

    // A straight way east or west of about 20 km.
    private val east = listOf(lat to 13.2, lat to 13.35, lat to 13.5)
    private val west = east.reversed()
    private val north = listOf(52.4 to lon, 52.5 to lon, 52.6 to lon)

    @Test
    fun eastAtNoonHasTheSunOnTheRight() {
        val start = millis("2024-06-21T10:50:00Z")
        assertEquals(SunSide.Right, sunSide(east, start, start + 30 * 60_000L))
        assertEquals(SunSide.Left, sunSide(west, start, start + 30 * 60_000L))
        assertEquals(SunSide.Left, SunSide.Right.shade)
    }

    @Test
    fun northAtNoonHasTheSunBehind() {
        val start = millis("2024-06-21T10:50:00Z")
        assertNull(sunSide(north, start, start + 30 * 60_000L))
    }

    @Test
    fun shortRidesAndNightsGetNoHint() {
        val noon = millis("2024-06-21T10:50:00Z")
        assertNull(sunSide(east, noon, noon + 15 * 60_000L))
        val night = millis("2024-06-21T22:30:00Z")
        assertNull(sunSide(east, night, night + 30 * 60_000L))
        assertNull(sunSide(listOf(lat to lon), noon, noon + 30 * 60_000L))
    }

    @Test
    fun aWayThereAndBackIsEven() {
        val start = millis("2024-06-21T10:50:00Z")
        assertNull(sunSide(east + west, start, start + 40 * 60_000L))
    }

    @Test
    fun theShownSideNeedsTheSettingAndTheSun() {
        // The phone's hint and the watch's (#176): off, no sun or no clear side show nothing.
        assertNull(shownSunSide(on = false, test = false, side = SunSide.Left, shines = true))
        assertNull(shownSunSide(on = true, test = false, side = SunSide.Left, shines = false))
        assertNull(shownSunSide(on = true, test = false, side = null, shines = true))
        assertEquals(SunSide.Left, shownSunSide(on = true, test = false, side = SunSide.Left, shines = true))
        // The developer test shows a side on every ride.
        assertEquals(SunSide.Left, shownSunSide(on = true, test = true, side = null, shines = false))
        assertEquals(SunSide.Right, shownSunSide(on = true, test = true, side = SunSide.Right, shines = false))
    }
}
