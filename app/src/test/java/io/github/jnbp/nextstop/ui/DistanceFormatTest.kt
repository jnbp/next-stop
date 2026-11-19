package io.github.jnbp.nextstop.ui

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Distances for people: metres rounded to 10 below 1 km, one decimal below 10 km, whole km beyond. */
class DistanceFormatTest {
    private val de = Locale.GERMANY
    private val en = Locale.UK

    @Test
    fun metresRoundToTen() {
        assertEquals("850 m", formatDistance(847.0, de))
        assertEquals("850 m", formatDistance(853, de))
        assertEquals("120 m", formatDistance(115.0, de))
        assertEquals("990 m", formatDistance(994.0, de))
    }

    @Test
    fun neverBelowTenMetres() {
        assertEquals("10 m", formatDistance(0.0, de))
        assertEquals("10 m", formatDistance(3, de))
        assertEquals("10 m", formatDistance(-5.0, de))
        assertEquals("10 m", formatDistance(Double.NaN, de))
    }

    @Test
    fun kilometresWithOneDecimalInTheLocalesWay() {
        assertEquals("4,4 km", formatDistance(4377, de))
        assertEquals("4.4 km", formatDistance(4377, en))
        assertEquals("1,0 km", formatDistance(995.0, de))
        assertEquals("1,2 km", formatDistance(1_249.0, de))
        assertEquals("9,9 km", formatDistance(9_940.0, de))
    }

    @Test
    fun wholeKilometresFromTen() {
        assertEquals("10 km", formatDistance(9_960.0, de))
        assertEquals("254 km", formatDistance(253_978, de))
        assertEquals("254 km", formatDistance(253_978, en))
        assertEquals("12 km", formatDistance(12_400.0, de))
    }

    @Test
    fun walkTimeOnlyWhereItHelps() {
        assertTrue(walkTimeShown(850.0))
        assertTrue(walkTimeShown(5_000.0))
        assertFalse(walkTimeShown(5_001.0))
        assertFalse(walkTimeShown(461_300.0))
        assertFalse(walkTimeShown(Double.NaN))
    }

    @Test
    fun walkSpanInHoursFromAnHour() {
        assertEquals(null to 12L, walkSpan(12))
        assertEquals(null to 1L, walkSpan(0))
        assertEquals(1L to 10L, walkSpan(70))
        assertEquals(2L to 0L, walkSpan(120))
    }

    @Test
    fun durationsReadAsHoursAndMinutes() {
        // "143 min" is hard to read: 2:23 h.
        assertEquals(2L to 23L, durationSpan(143))
        assertEquals(null to 48L, durationSpan(48))
        assertEquals(null to 0L, durationSpan(0))
        assertEquals(1L to null, durationSpan(60))
        assertEquals(2L to null, durationSpan(120))
        assertEquals(1L to 1L, durationSpan(61))
        // A long ride: 413 min is "6:53 h", more than a day stays hours ("41:03 h"), never minutes alone.
        assertEquals(6L to 53L, durationSpan(413))
        assertEquals(41L to 3L, durationSpan(2463))
        assertEquals(null to 59L, durationSpan(59))
    }

    @Test
    fun durationsFromAnHourInTheCompactFormOfTheDb() {
        // #106: "5:20 h", minutes always two digits, whole hours with ":00"; below an hour "45′".
        assertEquals("5:20", durationClock(320))
        assertEquals("1:05", durationClock(65))
        assertEquals("2:00", durationClock(120))
        assertEquals("1:00", durationClock(60))
        assertEquals("41:03", durationClock(2463))
        assertEquals(null, durationClock(59))
        assertEquals(null, durationClock(0))
        assertEquals(null, durationClock(-4))
    }
}
