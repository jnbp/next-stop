package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fare zones A/B/C of stops and journeys (see VbbZones). Real station numbers and positions. */
class VbbZonesTest {
    private val alexanderplatz = Stop("900100003", "S+U Alexanderplatz", 52.5215, 13.4121, null)
    private val zoo = Stop("900023201", "S+U Zoologischer Garten", 52.5068, 13.3326, null)
    private val spandau = Stop("900029101", "S+U Rathaus Spandau", 52.5345, 13.1977, null)
    private val potsdam = Stop("900230999", "S Potsdam Hauptbahnhof", 52.3913, 13.0669, null)
    private val ber = Stop("900260005", "Flughafen BER", 52.3641, 13.5082, null)
    private val fuerstenwalde = Stop("900310001", "Fürstenwalde", 52.3586, 14.0606, null)
    private val ostkreuz = Stop("900120003", "S Ostkreuz", 52.5031, 13.4695, null)
    private val hamburg = Stop("de-DELFI_de:02000:10950", "Hamburg Hbf", 53.553, 10.006, null)

    private val start: OffsetDateTime = OffsetDateTime.parse("2026-01-01T10:00:00+01:00")

    private fun ride(from: Stop, to: Stop) = Leg(
        "S7", "suburban", false, from.name, to.name, start, start, start.plusMinutes(30), start.plusMinutes(30),
        null, null, null, false, fromPlace = from, toPlace = to,
    )

    @Test
    fun stopsOfBerlin() {
        assertEquals(FareZone.A, VbbZones.zoneOf(alexanderplatz))
        assertEquals(FareZone.A, VbbZones.zoneOf(zoo))
        // A ring station itself is in A.
        assertEquals(FareZone.A, VbbZones.zoneOf(ostkreuz))
        assertEquals(FareZone.B, VbbZones.zoneOf(spandau))
    }

    @Test
    fun stopsAroundBerlin() {
        assertEquals(FareZone.C, VbbZones.zoneOf(potsdam))
        assertEquals(FareZone.C, VbbZones.zoneOf(ber))
        assertEquals(FareZone.Outside, VbbZones.zoneOf(fuerstenwalde))
        assertEquals(FareZone.Outside, VbbZones.zoneOf(hamburg))
    }

    @Test
    fun idsInsideLongerIds() {
        assertEquals(VbbZones.Area.Berlin, VbbZones.areaOf("de-VBB_900100003"))
        assertEquals(VbbZones.Area.Berlin, VbbZones.areaOf("de-DELFI_de:11000:900100003::1"))
        assertEquals(VbbZones.Area.Brandenburg, VbbZones.areaOf("de:12054:900230999"))
        assertNull(VbbZones.areaOf("address:52.5,13.4"))
    }

    @Test
    fun placeWithoutIdOnlyByTheRing() {
        assertEquals(FareZone.A, VbbZones.zoneOf(Stop("x", "Somewhere", 52.52, 13.40, null)))
        assertNull(VbbZones.zoneOf(Stop("x", "Somewhere", 52.40, 13.10, null)))
    }

    @Test
    fun journeyIntoZoneC() {
        val journey = Journey(listOf(ride(zoo, potsdam)))
        assertEquals(setOf(FareZone.A, FareZone.C), VbbZones.zonesOf(journey))
        assertTrue(VbbZones.needsZoneC(journey))
        assertEquals("AC", VbbZones.label(VbbZones.zonesOf(journey)))
    }

    @Test
    fun journeyWithinBerlinOrBeyondC() {
        assertFalse(VbbZones.needsZoneC(Journey(listOf(ride(alexanderplatz, spandau)))))
        // Beyond C a Berlin ticket is the wrong question: no hint.
        assertFalse(VbbZones.needsZoneC(Journey(listOf(ride(alexanderplatz, fuerstenwalde)))))
        // Only outside Berlin: no hint either.
        assertFalse(VbbZones.needsZoneC(Journey(listOf(ride(potsdam, ber)))))
    }
}
