package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** A ride at night never gets a hint of the sunny side (#241), whatever path the hint takes. */
class SunNightTest {
    // A made-up ride across a city at about 52.5 N, 13.4 E, eastwards, above ground all the way.
    private val way = listOf(52.50 to 13.20, 52.51 to 13.30, 52.52 to 13.40, 52.52 to 13.55)

    private fun millis(text: String): Long = OffsetDateTime.parse(text).toInstant().toEpochMilli()

    @Test
    fun aRideAtOneInTheMorningHasNoSide() {
        // 01:00 local time in November (UTC+1), a 30-minute ride.
        val from = millis("2026-11-19T00:56:00+01:00")
        val to = millis("2026-11-19T01:26:00+01:00")
        assertNull(sunSide(way, from, to))
        assertNull(sunSide(way, from, to, underground = { _, _ -> false }))
    }

    @Test
    fun theSunIsNotUpAtNightSoItNeverShines() {
        val middle = millis("2026-11-19T01:10:00+01:00")
        val elevation = sunPosition(middle, 52.51, 13.35).elevation
        // A clear sky does not help: the sun is far below the horizon.
        assertFalse(sunShines(cloudCover = 0, sunElevation = elevation))
        assertNull(shownSunSide(on = true, test = false, side = sunSide(way, middle - 900_000L, middle + 900_000L), shines = false))
    }

    @Test
    fun theSameRideInSummerSunshineAtNightHasNoSideEither() {
        // A long summer evening: 23:00 local time (UTC+2) is past sunset too.
        val from = millis("2026-06-21T23:00:00+02:00")
        assertNull(sunSide(way, from, from + 40 * 60_000L))
    }
}
