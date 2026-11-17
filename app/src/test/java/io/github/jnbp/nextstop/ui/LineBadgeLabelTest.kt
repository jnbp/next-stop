package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Long line names on badges, chips and blocks (see badgeLine, compactLine). */
class LineBadgeLabelTest {
    @Test
    fun shortNamesStayAsTheyAre() {
        assertEquals("U8", badgeLine("U8"))
        assertEquals("M27", badgeLine("Bus M27"))
        assertEquals("ICE 1234", badgeLine("ICE 1234"))
        assertEquals("RE1", badgeLine("RE1 3809"))
    }

    @Test
    fun coachesGoByTheShortNameOfTheirOperator() {
        assertEquals("Flix N1334", badgeLine("FlixBus N1334"))
        assertEquals("BlaBla B40", badgeLine("BlaBlaCar B40"))
        // Too long with the short name too: the number alone.
        assertEquals("B4021", badgeLine("BlaBlaCar B4021"))
    }

    @Test
    fun unknownOperatorsKeepTheirNumberOrAreCut() {
        assertEquals("X1234", badgeLine("Fernbusreisen X1234"))
        val cut = badgeLine("Sonderverkehr Ausflug")!!
        assertTrue(cut.endsWith("…"))
        assertEquals(BADGE_MAX_CHARS, cut.length)
    }

    @Test
    fun neverLongerThanABadgeTakes() {
        for (name in listOf("FlixBus N1334", "FlixBus N13345678", "Irgendein sehr langer Linienname", "BlaBlaCar 40211234")) {
            assertTrue(name, badgeLine(name)!!.length <= BADGE_MAX_CHARS)
        }
    }

    @Test
    fun narrowBlocksTakeTheNumberAlone() {
        assertEquals("N1334", compactLine("FlixBus N1334"))
        assertEquals("ICE 29", compactLine("ICE 29"))
        assertEquals("S41", compactLine("S41"))
    }

    @Test
    fun everyFlixBusAlikeOnABadge() {
        // One scheme in a chart: never "FlixBus" beside "Flix 260".
        assertEquals("Flix 260", badgeLine("FlixBus 260"))
        assertEquals("Flix 60", badgeLine("FlixBus 60"))
        assertEquals("Flix", badgeLine("FlixBus"))
        assertEquals("FLX 30", badgeLine("FLX 30"))
        assertEquals("260", compactLine("FlixBus 260"))
        assertEquals("FLX 30", compactLine("FLX 30"))
    }

    @Test
    fun modeIconOnlyWhereTheNameDoesNotTell() {
        assertEquals(TravelMode.Bus, modeHint("FlixBus 260", "coach"))
        assertEquals(TravelMode.Bus, modeHint("BlaBlaCar B40", "coach"))
        assertEquals(TravelMode.Train, modeHint("FLX 30", "express"))
        assertEquals(TravelMode.Train, modeHint("174", "express"))
        assertEquals(TravelMode.Train, modeHint("Leo Express", "express"))
        assertEquals(TravelMode.Bus, modeHint("Ruftaxi", "bus"))
        for ((line, product) in listOf(
            "ICE 29" to "express", "IC 2041" to "express", "RE1" to "regional", "S41" to "suburban",
            "U8" to "subway", "M10" to "tram", "100" to "bus", "M48" to "bus", "RJ 174" to "express",
        )) assertNull(line, modeHint(line, product))
    }
}
