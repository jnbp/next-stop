package io.github.jnbp.nextstop.wear.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Line names on the watch's badges (see badgeLine, twin of the phone's). */
class BadgeLineTest {
    @Test
    fun shortNamesStay() {
        for (line in listOf("U7", "S41", "M10", "RE 1", "ICE 1234", "Flix N1334")) assertEquals(line, badgeLine(line))
    }

    @Test
    fun anOperatorGoesByItsShortName() {
        assertEquals("Flix N1334", badgeLine("FlixBus N1334"))
        assertEquals("BlaBla 123", badgeLine("BlaBlaCar 123"))
        // A train with its kind spelled out keeps kind and number: never the number alone.
        assertEquals("RJ 1740", badgeLine("Railjet 1740"))
        assertEquals("EC 171", badgeLine("EuroCity 171"))
    }

    @Test
    fun elseTheCodeAloneOrCut() {
        assertEquals("N13345678", badgeLine("Someoperator N13345678"))
        val cut = badgeLine("Sightseeing Express")!!
        assertTrue(cut.length <= BADGE_MAX_CHARS && cut.endsWith("…"))
    }

    @Test
    fun nothingIsNothing() {
        assertEquals(null, badgeLine(null))
        assertEquals(null, badgeLine("  "))
    }

    @Test
    fun everyFlixBusAlike() {
        assertEquals("Flix 260", badgeLine("FlixBus 260"))
        assertEquals("Flix", badgeLine("FlixBus"))
        assertEquals("FlixBus 260", flixLineName("FLX 260", "bus"))
        assertEquals("FLX 30", flixLineName("FlixTrain 30", "express"))
        assertEquals("FlixBus N1334", rapidLineName("Flix N1334", "coach"))
    }

    @Test
    fun modeIconOnlyWhereTheNameDoesNotTell() {
        assertEquals(TravelMode.Bus, modeHint("Flix 260", "coach"))
        assertEquals(TravelMode.Train, modeHint("FLX 30", "express"))
        assertEquals(TravelMode.Train, modeHint("174", "express"))
        assertEquals(null, modeHint("ICE 29", "express"))
        assertEquals(null, modeHint("100", "bus"))
        assertEquals(null, modeHint("RE 1", "regional"))
    }
}
