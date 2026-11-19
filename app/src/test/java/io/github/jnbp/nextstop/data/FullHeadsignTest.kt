package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** #85: a direction of only "Hauptbahnhof" says nothing away from home: the town of the trip's end goes in front. */
class FullHeadsignTest {
    @Test
    fun aBareStationWordGetsTheTown() {
        assertEquals("Frankfurt (Main) Hbf", fullHeadsign("Hauptbahnhof", "Frankfurt (Main) Hauptbahnhof"))
        assertEquals("Frankfurt (Main) Hbf", fullHeadsign("Hbf", "Frankfurt (Main) Hauptbahnhof"))
        assertEquals("Leipzig Hbf", fullHeadsign("Hauptbahnhof", "Leipzig, Hauptbahnhof"))
        assertEquals("Frankfurt (Main) Messe", fullHeadsign("Messe", "Frankfurt (Main) Messe"))
        assertEquals("Hannover Messe/Laatzen", fullHeadsign("Messe", "Hannover Messe/Laatzen"))
    }

    @Test
    fun aRealNameStays() {
        assertEquals("Nürnberg Hbf", fullHeadsign("Nürnberg Hbf", "Nürnberg Hbf"))
        assertEquals("Hauptbahnhof", fullHeadsign("Hauptbahnhof", null))
        // The end says nothing of the word: the headsign stays.
        assertEquals("Hauptbahnhof", fullHeadsign("Hauptbahnhof", "Offenbach Marktplatz"))
        assertEquals("Offenbach Marktplatz", fullHeadsign(null, "Offenbach Marktplatz"))
    }
}
