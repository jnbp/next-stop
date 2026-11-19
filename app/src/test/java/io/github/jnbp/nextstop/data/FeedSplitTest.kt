package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One train in two feeds inside one journey (#96). Transitous, Frankfurt (Main) Hbf → Paris Est,
 * 18 Nov 2026 (plan_frankfurt-to-paris_now, second itinerary, as recorded): the ICE 9552 of DELFI
 * to "Mannheim, Hauptbahnhof" (arrives 12:38, track 1), a walk of 2 minutes to the SNCF's
 * "Mannheim Hbf" 36 m away, the ICE 9552 of the SNCF from there at 12:42 to Paris Est. The
 * traveller stays on board: one ride, no change.
 */
class FeedSplitTest {
    private fun at(utc: String): OffsetDateTime = OffsetDateTime.parse("2026-11-18T${utc}:00Z")
    private val frankfurt = Stop("tr:de-DELFI_de:06412:10_G", "Frankfurt (Main) Hauptbahnhof", 50.10668, 8.662828, null, setOf("express"))
    private val mannheimDelfi = Stop("tr:de-DELFI_de:08222:2417:1:1", "Mannheim, Hauptbahnhof", 49.47928, 8.47008, null, setOf("express"))
    private val mannheimSncf = Stop("tr:fr-horaires-sncf_FR::LMO:71cdf190-3a69-11e9-8417-bb1d8a705241:", "Mannheim Hbf", 49.4794, 8.4696, null, setOf("express"))
    private val paris = Stop("tr:fr-horaires-sncf_FR::LMU:6cbbab30-cb0a-11e8-8bfa-f784c1c7c611:", "Paris Est", 48.876742, 2.358424, null, setOf("express"))

    private fun ride(line: String, from: Stop, to: Stop, leaves: String, arrives: String, trip: String, direction: String, fromTrack: String?, toTrack: String?) = Leg(
        line, "express", false, from.name, to.name, at(leaves), at(leaves), at(arrives), at(arrives),
        fromTrack, toTrack, direction, false,
        fromPlace = from, toPlace = to, tripId = trip,
        stopovers = listOf(Stopover(from.name, null, at(leaves), from), Stopover(to.name, at(arrives), null, to)),
        path = listOf(from.position!!, to.position!!),
    )

    private val delfi = ride("ICE 9552", frankfurt, mannheimDelfi, "11:53", "12:38", "tr:20261118_12:53_de-DELFI_3486157656", "Saarbrücken Hbf", null, "1")
    private val walk = Leg(
        null, "walking", true, mannheimDelfi.name, mannheimSncf.name, at("12:38"), at("12:38"), at("12:40"), at("12:40"),
        "1", null, null, false, fromPlace = mannheimDelfi, toPlace = mannheimSncf,
    )
    private val sncf = ride("ICE 9552", mannheimSncf, paris, "12:42", "15:50", "tr:20261118_12:53_fr-horaires-sncf_FR:ServiceJourney::SN9552FERRE_13365145", "Paris Est", "1", null)

    @Test
    fun theSameIceInTwoFeedsIsOneRide() {
        assertTrue(isFeedSplit(delfi, sncf))
        val joined = mergeThroughRuns(Journey(listOf(delfi, walk, sncf)))
        assertEquals(1, joined.legs.size)
        val ice = joined.legs.single()
        assertEquals(frankfurt.name, ice.from)
        assertEquals(paris.name, ice.to)
        assertEquals(at("11:53"), ice.departure)
        assertEquals(at("15:50"), ice.arrival)
        assertEquals(0, joined.transfers)
    }

    @Test
    fun anotherTrainOrALongStopIsAChange() {
        assertFalse(isFeedSplit(delfi, sncf.copy(line = "ICE 9553")))
        val late = sncf.copy(departure = at("12:55"), plannedDeparture = at("12:55"))
        assertFalse(isFeedSplit(delfi, late))
        assertEquals(3, mergeThroughRuns(Journey(listOf(delfi, walk, late))).legs.size)
        // Another station of the town: a change with a walk, as before.
        val elsewhere = Stop("tr:fr-horaires-sncf_X", "Mannheim-Neckarau", 49.4560, 8.4880, null, setOf("express"))
        assertFalse(isFeedSplit(delfi, sncf.copy(from = elsewhere.name, fromPlace = elsewhere)))
        // Regional line numbers are no train numbers.
        val re = delfi.copy(line = "RE 1", product = "regional")
        assertFalse(isFeedSplit(re, sncf.copy(line = "RE 1", product = "regional")))
    }
}
