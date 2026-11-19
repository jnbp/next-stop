package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.Stop
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * #96: Transitous lists Frankfurt Hbf twice, the stop of the German feed with every product and
 * the one of the French long-distance feed under its French name with only the TGV/ICE to France.
 * One station, one sign, under the German name, with the departures of both.
 */
class ForeignStationTwinTest {
    private val germanFeed = Stop(
        "de-DELFI_de:06412:10", "Frankfurt (Main) Hauptbahnhof", 50.10710, 8.66330, null,
        setOf("suburban", "subway", "regional", "express"),
    )
    // About 100 m away, as on the map (#96 screenshots).
    private val frenchFeed = Stop("fr-sncf_StopArea:OCE80110007", "Francfort sur le Main", 50.10670, 8.66210, null, setOf("express"))

    @Test
    fun theFrenchNameOfTheMainStationJoinsIt() {
        val groups = stationGroups(listOf(frenchFeed, germanFeed))
        assertEquals(1, groups.size)
        assertEquals("Frankfurt (Main) Hauptbahnhof", groups[0].main.name)
        assertEquals(listOf(frenchFeed.id), groups[0].members.map { it.id })
    }

    @Test
    fun otherExonymsToo() {
        val koeln = Stop("de:05315:11201", "Köln Hbf", 50.9430, 6.9590, null, setOf("suburban", "regional", "express"))
        // The id as Transitous writes it ("<feed>_<id>", see StationIdentity; the first version had "be-sncb:").
        val cologne = Stop("be-sncb_8015458", "Cologne", 50.9432, 6.9583, null, setOf("express"))
        assertEquals(1, stationGroups(listOf(cologne, koeln)).size)
        val muenchen = Stop("de:09162:6", "München Hbf", 48.1402, 11.5600, null, setOf("suburban", "subway", "regional", "express"))
        val munich = Stop("fr-sncf_StopArea:OCE80002139", "Munich Hbf", 48.1400, 11.5590, null, setOf("express"))
        assertEquals(1, stationGroups(listOf(munich, muenchen)).size)
    }

    @Test
    fun aLongDistanceStopOfAnotherTownStaysApart() {
        // Same distance, but nothing in the names says one town: two stations.
        val other = Stop("fr-sncf_StopArea:OCE1", "Offenbach Ost", 50.10670, 8.66210, null, setOf("express"))
        assertEquals(2, stationGroups(listOf(other, germanFeed)).size)
    }

    @Test
    fun tooFarApartStaysApart() {
        // 400 m: another station, whatever the names say.
        val far = frenchFeed.copy(latitude = 50.1035)
        assertEquals(2, stationGroups(listOf(far, germanFeed)).size)
    }
}
