package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** #185: a followed trip on a stand-in goes back to the preferred provider, without a jump. */
class SourceReturnTest {
    private fun at(clock: String): OffsetDateTime = OffsetDateTime.parse("2026-01-01T${clock}:00+01:00")

    private fun stop(id: String, name: String, north: Double) = Stop(id, name, 52.4 + north, 13.2, null)

    // The same three stations as two providers name them.
    private val trA = stop("tr:de-DELFI_de:11000:900000001", "Example A", 0.0)
    private val trB = stop("tr:de-DELFI_de:11000:900000002", "Example B", 0.05)
    private val trC = stop("tr:de-DELFI_de:11000:900000003", "Example C", 0.1)
    private val vbbA = stop("900000001", "S Example A", 0.0)
    private val vbbB = stop("900000002", "S Example B", 0.05)
    private val vbbC = stop("900000003", "S Example C", 0.1)

    private fun ride(line: String, from: Stop, to: Stop, dep: String, arr: String, liveDep: String = dep, token: String = "") = Leg(
        line, "suburban", false, from.name, to.name, at(liveDep), at(dep), at(arr), at(arr), null, null, null, false,
        realtime = token.isNotEmpty(), fromPlace = from, toPlace = to, tripId = token + line,
    )

    private val onTransitous = Journey(
        listOf(ride("S7", trA, trB, "16:00", "16:10"), ride("S1", trB, trC, "16:16", "16:28")),
        refreshToken = "tr:abc",
    )
    private val onVbb = Journey(
        listOf(ride("S7", vbbA, vbbB, "16:00", "16:10", token = "vbb~"), ride("S1", vbbB, vbbC, "16:16", "16:28", liveDep = "16:19", token = "vbb~")),
        refreshToken = "vbb~xyz",
    )

    @Test
    fun theProbeBacksOffUpToTenMinutes() {
        assertEquals(listOf(1L, 2L, 4L, 8L, 10L, 10L), (0..5).map { returnProbeDelayMillis(it) / 60_000L })
        val probe = ReturnProbe(0L)
        assertFalse(probe.due(59_000L))
        assertTrue(probe.due(60_000L))
        probe.failed(60_000L)
        assertFalse(probe.due(170_000L))
        assertTrue(probe.due(180_000L))
        probe.failed(180_000L)
        assertTrue(probe.due(420_000L))
        probe.reset(500_000L)
        assertTrue(probe.due(560_000L))
    }

    @Test
    fun theSameJourneyComesBackWithItsIdAndTheLiveTimes() {
        val back = preferredTwin(onTransitous, listOf(onVbb))
        assertNotNull(back)
        back!!
        // The id stays: names, lines and planned times of the followed journey.
        assertEquals(onTransitous.id, back.id)
        assertEquals("Example B", back.legs[1].from)
        // The live times and the handle of the preferred provider.
        assertEquals(at("16:19"), back.legs[1].departure)
        assertEquals("vbb~xyz", back.refreshToken)
        assertEquals("vbb~S1", back.legs[1].tripId)
    }

    @Test
    fun anotherJourneyIsNotTaken() {
        val otherLine = onVbb.copy(legs = listOf(onVbb.legs[0], onVbb.legs[1].copy(line = "S25")))
        assertNull(ontoKnown(otherLine, onTransitous))
        val later = onVbb.copy(legs = listOf(onVbb.legs[0], ride("S1", vbbB, vbbC, "16:36", "16:48")))
        assertNull(ontoKnown(later, onTransitous))
        val fewer = onVbb.copy(legs = listOf(onVbb.legs[1]))
        assertNull(ontoKnown(fewer, onTransitous))
        assertNull(preferredTwin(onTransitous, listOf(otherLine, later)))
    }

    @Test
    fun laterRefreshesKeepTheNamesAndTheId() {
        val back = preferredTwin(onTransitous, listOf(onVbb))!!
        // The next refresh of the preferred provider names the stations its own way again.
        val refreshed = onVbb.copy(legs = listOf(onVbb.legs[0], ride("S1", vbbB, vbbC, "16:16", "16:28", liveDep = "16:20", token = "vbb~")))
        val kept = withKnownNames(refreshed, back)
        assertEquals(onTransitous.id, kept.id)
        assertEquals(at("16:20"), kept.legs[1].departure)
        // A refresh of one provider that names everything alike stays as it is.
        assertSame(onTransitous, withKnownNames(onTransitous, onTransitous))
    }

    private fun walk(from: Stop, to: Stop, dep: String, arr: String, meters: Int) = Leg(
        null, "walking", true, from.name, to.name, at(dep), at(dep), at(arr), at(arr), null, null, null, false,
        fromPlace = from, toPlace = to, walkMeters = meters,
    )

    @Test
    fun theSameRidesWithAnExtraWalkComeBack() {
        // VBB leads a walk to the platform before the first ride and a longer one at the change.
        val vbbWalks = Journey(
            listOf(
                walk(vbbA, vbbA, "15:57", "16:00", 120),
                ride("S7", vbbA, vbbB, "16:00", "16:10", token = "vbb~"),
                walk(vbbB, vbbB, "16:10", "16:14", 300),
                ride("S1", vbbB, vbbC, "16:16", "16:28", liveDep = "16:19", token = "vbb~"),
            ),
            refreshToken = "vbb~walks",
        )
        val back = preferredTwin(onTransitous, listOf(vbbWalks))
        assertNotNull(back)
        back!!
        // The walks of the preferred provider, the rides with the names and times of the followed trip.
        assertEquals(4, back.legs.size)
        assertEquals(120, back.legs[0].walkMeters)
        assertEquals(300, back.legs[2].walkMeters)
        assertEquals("Example B", back.legs[3].from)
        assertEquals(at("16:16"), back.legs[3].plannedDeparture)
        assertEquals(at("16:19"), back.legs[3].departure)
        assertEquals("vbb~walks", back.refreshToken)
        // Nothing jumps: the id stays.
        assertEquals(onTransitous.id, back.id)
        // So does the trip saved and read back (a restart of the app on the way).
        assertEquals(onTransitous.id, keptJourneyOf(keptJourneyJson(back))!!.id)
        // A later refresh of the preferred provider keeps it.
        val refreshed = vbbWalks.copy(legs = vbbWalks.legs.dropLast(1) + ride("S1", vbbB, vbbC, "16:16", "16:28", liveDep = "16:21", token = "vbb~"))
        val kept = withKnownNames(refreshed, back)
        assertEquals(onTransitous.id, kept.id)
        assertEquals(at("16:21"), kept.legs[3].departure)
        // The kept id goes with other rides (as after a change of connection).
        val adapted = back.copy(legs = back.legs.dropLast(1) + ride("S2", vbbB, vbbC, "16:30", "16:40"))
        assertFalse(adapted.id == onTransitous.id)
    }

    @Test
    fun anotherRideAmongWalksIsNotTaken() {
        val otherRide = Journey(
            listOf(
                walk(vbbA, vbbA, "15:57", "16:00", 120),
                ride("S7", vbbA, vbbB, "16:00", "16:10", token = "vbb~"),
                ride("S2", vbbB, vbbC, "16:16", "16:28", token = "vbb~"),
            ),
            refreshToken = "vbb~other",
        )
        assertNull(preferredTwin(onTransitous, listOf(otherRide)))
        // Rides in another order are not the followed journey either.
        val swapped = Journey(listOf(onVbb.legs[1], onVbb.legs[0]), refreshToken = "vbb~swap")
        assertNull(ontoKnown(swapped, onTransitous))
    }

    @Test
    fun theStayMovesWithItsRide() {
        val known = onTransitous.legs
        val fresh = listOf(walk(vbbA, vbbA, "15:57", "16:00", 120)) + onVbb.legs
        assertEquals(2, viaAfterLegs(1, known, fresh))
        assertEquals(0, viaAfterLegs(0, known, fresh))
        assertEquals(1, viaAfterLegs(1, known, onVbb.legs))
    }

    @Test
    fun aKnownJourneyWithoutTimetableTakesNoMadeUpPlannedTimes() {
        // Followed on a provider without a timetable: no planned times at all.
        val bare = Journey(onTransitous.legs.map { it.copy(plannedDeparture = null, plannedArrival = null) }, refreshToken = "tr:abc")
        val back = preferredTwin(bare, listOf(onVbb))
        assertNotNull(back)
        back!!
        assertEquals(bare.id, back.id)
        // The planned times are the timetable of the provider that has one, never the live times known before.
        assertEquals(at("16:16"), back.legs[1].plannedDeparture)
        assertEquals(3L, back.legs[1].departureDelayMinutes)
        // Neither side knows a timetable: no delay, and the id still holds.
        val bareVbb = Journey(onVbb.legs.map { it.copy(plannedDeparture = null, plannedArrival = null) }, refreshToken = "vbb~xyz")
        val both = preferredTwin(bare, listOf(bareVbb))
        assertNotNull(both)
        both!!
        assertEquals(bare.id, both.id)
        assertEquals(null, both.legs[1].plannedDeparture)
        assertEquals(0L, both.legs[1].departureDelayMinutes)
    }

    @Test
    fun aLooseIdHoldsForTheSameRidesAtOtherTimesOnly() {
        val legs = onTransitous.legs.map { it.copy(plannedDeparture = null, plannedArrival = null) }
        assertFalse(ridesPlanned(legs))
        assertTrue(ridesPlanned(onTransitous.legs))
        val kept = KeptJourneyId("x", journeyRideShape(legs), loose = true)
        assertTrue(kept.holdsFor(legs.map { it.copy(departure = it.departure.plusMinutes(2)) }))
        assertFalse(kept.holdsFor(legs.take(1)))
        // A kept id goes through the store of a followed trip with its kind.
        val stored = keptJourneyOf(keptJourneyJson(Journey(legs, keptId = kept)))
        assertEquals(kept, stored?.keptId)
    }
}
