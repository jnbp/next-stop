package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Line names on badges: line names without train numbers, long-distance trains by kind and number. */
class LineNamesTest {
    @Test
    fun regionalLinesDropTheTrainNumber() {
        assertEquals("FEX", shortLine("FEX 021857"))
        assertEquals("RE1", shortLine("RE1 3809"))
        assertEquals("RE", shortLine("RE 73804"))
        assertEquals("RB23", shortLine("RB23"))
        assertEquals("FEX 021857", fullLineName("FEX 021857"))
    }

    @Test
    fun longDistanceKeepsItsNumber() {
        assertEquals("ICE 29", shortLine("ICE 29"))
        assertEquals("IC 2041", shortLine("IC  2041"))
        assertEquals("ICE 29", compactLine("ICE 29"))
        assertEquals(null, fullLineName("ICE 29"))
    }

    @Test
    fun trainsAlwaysShowKindAndNumber() {
        // The kind in words becomes its code, so the number keeps its place on every badge.
        assertEquals("RJ 174", shortLine("Railjet 174"))
        assertEquals("RJ 174", badgeLine("Railjet 174"))
        assertEquals("EC 171", badgeLine("EuroCity 171"))
        // Narrow chart blocks keep the number of a train, too.
        assertEquals("RJ 174", compactLine("RJ 174"))
        assertEquals("IC 2433", compactLine("IC 2433"))
        assertEquals("EC 171", compactLine("EuroCity 171"))
        assertEquals("Railjet 174", fullLineName("Railjet 174"))
        assertEquals(null, modeHint("RJ 174", "express"))
    }

    @Test
    fun cityLinesStayAsTheyAre() {
        assertEquals("187", shortLine("Bus 187"))
        assertEquals("50", shortLine("Tram 50"))
        assertEquals("50", shortLine("STR 50"))
        assertEquals("M48", shortLine("Bus M48"))
        assertEquals("M48", shortLine("M48"))
        assertEquals("S41", shortLine("S41"))
        assertEquals("U7", compactLine("U7"))
        assertEquals(null, fullLineName("Bus 187"))
        assertEquals(null, fullLineName("M48"))
    }
}
