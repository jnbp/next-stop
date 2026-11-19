package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A followed journey keeps its legs while it is loaded again (see withKnownSeating): two trams
 * 66 bunched by their delays (#34) are one ride for the traveller, and new delays that would
 * no longer join them (or newly join them) must not change the legs or the id of the journey.
 */
class KnownSeatingTest {
    private fun at(clock: String): OffsetDateTime = OffsetDateTime.parse("2026-11-17T$clock:00+01:00")
    private val gronau = Stop("de:05314:61101", "Bonn-Gronau", 50.7130, 7.1300, null)
    private val bonnHbf = Stop("de:05314:61908", "Bonn Hbf", 50.7320, 7.0970, null)
    private val siegburg = Stop("de:05210:9022", "Siegburg Bf", 50.7930, 7.2030, null)
    private val deutz = Stop("de:05315:11209", "Köln Messe/Deutz", 50.9410, 6.9750, null)

    private fun ride(
        line: String,
        product: String,
        from: Stop,
        to: Stop,
        leaves: String,
        planLeaves: String,
        arrives: String,
        planArrives: String,
        trip: String,
        direction: String?,
        fromPlatform: String? = null,
        toPlatform: String? = null,
    ) = Leg(
        line, product, false, from.name, to.name, at(leaves), at(planLeaves), at(arrives), at(planArrives),
        fromPlatform, toPlatform, direction, false, realtime = true,
        fromPlace = from, toPlace = to, tripId = trip,
    )

    private fun journey(firstAtHbf: String, secondLeaves: String, secondArrives: String): Journey = Journey(
        listOf(
            ride("66", "tram", gronau, bonnHbf, "20:14", "19:48", firstAtHbf, "19:57", "66-a", "Siegburg Bf", "13", "13"),
            ride("66", "tram", bonnHbf, siegburg, secondLeaves, "20:17", secondArrives, "20:41", "66-b", "Siegburg Bf", "13", "18"),
            ride("ICE 41", "express", siegburg, deutz, "21:00", "21:00", "21:15", "21:15", "ice41", "Köln Hbf", "2", "11"),
        ),
        refreshToken = "token",
    )

    // As the search had it: the first 66 at Bonn Hbf before the second leaves, one ride.
    private val followed = mergeThroughRuns(journey("20:21", "20:27", "20:47"))

    @Test
    fun newDelaysKeepTheJoinedRide() {
        assertEquals(2, followed.legs.size)
        // The first 66 lost time: now at Bonn Hbf after the second left, the provider's answer
        // alone would be the change again.
        val fresh = mergeThroughRuns(journey("20:30", "20:28", "20:49"))
        assertEquals(3, fresh.legs.size)
        val kept = withKnownSeating(fresh, followed)
        assertEquals(2, kept.legs.size)
        assertEquals(followed.id, kept.id)
        val tram = kept.legs.first()
        assertEquals("66-a", tram.tripId)
        assertEquals(siegburg.name, tram.to)
        // The fresh times of the later vehicle, at the latest.
        assertEquals(at("20:49"), tram.arrival)
        assertTrue(tram.arrivalEstimated)
        assertEquals(2, tram.seatedParts.size)
    }

    @Test
    fun newDelaysDoNotJoinRidesThatWereApart() {
        // Followed as a change (the first 66 then late at Bonn Hbf) ...
        val apart = mergeThroughRuns(journey("20:30", "20:28", "20:49"))
        assertEquals(3, apart.legs.size)
        // ... and loaded again with times that would join the two.
        val fresh = mergeThroughRuns(journey("20:21", "20:27", "20:47"))
        assertEquals(2, fresh.legs.size)
        val kept = withKnownSeating(fresh, apart)
        assertEquals(3, kept.legs.size)
        assertEquals(apart.id, kept.id)
        assertEquals(at("20:27"), kept.legs[1].departure)
        assertTrue(kept.legs.none { it.seatedParts.isNotEmpty() })
    }

    @Test
    fun theSameShapeOrOtherRidesStayAsTheyCame() {
        val fresh = mergeThroughRuns(journey("20:22", "20:28", "20:48"))
        assertSame(fresh, withKnownSeating(fresh, followed))
        // Other rides altogether: nothing is forced onto them.
        val other = Journey(listOf(ride("67", "tram", gronau, siegburg, "20:14", "19:48", "20:45", "20:20", "67-a", "Siegburg Bf")))
        assertSame(other, withKnownSeating(other, followed))
    }
}
