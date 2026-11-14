package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RailReplacementTest {
    @Test
    fun namesOfReplacementLines() {
        assertEquals(RailReplacement(null), railReplacement("SEV", "bus"))
        assertEquals(RailReplacement(null), railReplacement("Bus SEV", "bus"))
        assertEquals(RailReplacement(null), railReplacement("EV", "bus"))
        assertEquals(RailReplacement("S1"), railReplacement("SEV S1", "bus"))
        assertEquals(RailReplacement("S1"), railReplacement("S1 SEV", "bus"))
        assertEquals(RailReplacement("S1"), railReplacement("S1 (SEV)", "bus"))
        assertEquals(RailReplacement("RE7"), railReplacement("SEV RE 7", "regional"))
        assertEquals(RailReplacement("RB23"), railReplacement("Bus EV RB23", "bus"))
    }

    @Test
    fun aBusUnderTheNameOfATrainLine() {
        assertEquals(RailReplacement("S5"), railReplacement("S5", "bus"))
        assertEquals(RailReplacement("RE1"), railReplacement("Bus RE1", "bus"))
        // The train itself is no replacement.
        assertNull(railReplacement("S5", "suburban"))
        assertNull(railReplacement("RE1", "regional"))
    }

    @Test
    fun ordinaryLinesAreNone() {
        assertNull(railReplacement("X7", "bus"))
        assertNull(railReplacement("SB10", "bus"))
        assertNull(railReplacement("M48", "bus"))
        assertNull(railReplacement("Severin-Express", "bus"))
        assertNull(railReplacement(null, "bus"))
    }

    @Test
    fun notices() {
        val notice = Warning("Ersatzverkehr", "Bus statt Bahn zwischen Rangsdorf und Zossen, Ersatz für RE 8.")
        assertEquals(RailReplacement("RE8"), railReplacement("735", "bus", listOf(notice)))
        assertEquals(RailReplacement(null), railReplacement("735", "bus", listOf(Warning("", "SEV ab Bahnhof"))))
        // A train with a notice on a replacement is still the train.
        assertNull(railReplacement("RE8", "regional", listOf(notice)))
        assertNull(railReplacement("735", "bus", listOf(Warning("Umleitung", "Die Haltestelle wird nicht bedient."))))
    }

    @Test
    fun productOfTheReplacedLine() {
        assertEquals("suburban", replacedProduct("S1"))
        assertEquals("subway", replacedProduct("U2"))
        assertEquals("regional", replacedProduct("RE7"))
    }

    @Test
    fun aBusWhoseDirectionSaysReplacementStandsForItsLine() {
        // The BVG names a replacement bus for the tram M10 "M10", product bus, direction "Ersatzverkehr Richtung …".
        assertEquals(RailReplacement("M10", "tram"), railReplacement("M10", "bus", direction = "Ersatzverkehr Richtung S+U Warschauer Str."))
        assertEquals(RailReplacement("U6"), railReplacement("U6", "bus", direction = "Ersatzverkehr bis U Seestr."))
        assertNull(railReplacement("M10", "tram", direction = "Ersatzverkehr Richtung S+U Warschauer Str."))
        assertNull(railReplacement("M10", "bus", direction = "S+U Warschauer Str."))
    }
}
