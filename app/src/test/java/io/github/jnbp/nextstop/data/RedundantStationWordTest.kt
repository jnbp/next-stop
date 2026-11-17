package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** An S, U or S+U before a VBB station name already says "station": a "Bhf" behind it is left out. */
class RedundantStationWordTest {
    @Test
    fun bhfAfterAnSOrUIsLeftOut() {
        assertEquals("S+U Zoologischer Garten", withoutRedundantStationWord("S+U Zoologischer Garten Bhf"))
        assertEquals("S Schöneweide", withoutRedundantStationWord("S Schöneweide Bhf"))
        assertEquals("U Kurfürstendamm", withoutRedundantStationWord("U Kurfürstendamm Bahnhof"))
        assertEquals("S Golm (Potsdam)", withoutRedundantStationWord("S Golm Bhf (Potsdam)"))
    }

    @Test
    fun otherStationsKeepTheirWords() {
        assertEquals("Leipzig Hbf", withoutRedundantStationWord("Leipzig Hbf"))
        assertEquals("Erkner Bhf", withoutRedundantStationWord("Erkner Bhf"))
        assertEquals("S+U Berlin Hauptbahnhof", withoutRedundantStationWord("S+U Berlin Hauptbahnhof"))
        assertEquals("S Bhf", withoutRedundantStationWord("S Bhf"))
        assertEquals("U Bahnhofstr.", withoutRedundantStationWord("U Bahnhofstr."))
        assertEquals("Spandau Bhf", withoutRedundantStationWord("Spandau Bhf"))
    }

    @Test
    fun shownNamesDropIt() {
        assertEquals("S+U Zoologischer Garten", stopNameParts("S+U Zoologischer Garten Bhf (Berlin)", "Berlin", "de").title)
        assertEquals("S+U Zoologischer Garten", stopNameParts("S+U Zoologischer Garten Bhf (Berlin) [U2]", "Berlin", "en").title)
        assertEquals("Leipzig Hbf", stopNameParts("Leipzig Hbf", "Berlin", "de").title)
    }
}
