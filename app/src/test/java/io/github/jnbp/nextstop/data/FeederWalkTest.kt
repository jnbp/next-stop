package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A short bus to the train also comes as a walk to the train. Made-up stops only. */
class FeederWalkTest {
    private val start: OffsetDateTime = OffsetDateTime.parse("2026-01-01T19:00:00+01:00")
    private fun at(minutes: Long) = start.plusMinutes(minutes)
    private fun place(id: String, name: String, north: Double) = Stop(id, name, 52.0 + north, 13.0, null)
    private val home = place("address:1", "Musterweg 1", 0.0)
    private val busStop = place("900000001", "Stop A", 0.001)
    private val station = place("900000002", "S Teststation", 0.006) // about 670 m from the start
    private val farStation = place("900000003", "S Fernstation", 0.03) // about 3.3 km
    private val end = place("900000004", "Stop Z", 0.1)

    private fun walk(from: Stop, to: Stop, leaves: Long, arrives: Long) = Leg(
        null, "", true, from.name, to.name, at(leaves), at(leaves), at(arrives), at(arrives),
        null, null, null, false, fromPlace = from, toPlace = to,
    )
    private fun ride(line: String, product: String, from: Stop, to: Stop, leaves: Long, arrives: Long) = Leg(
        line, product, false, from.name, to.name, at(leaves), at(leaves), at(arrives), at(arrives),
        null, null, null, false, fromPlace = from, toPlace = to, tripId = "$line@$leaves",
    )

    private val withBus = Journey(
        listOf(walk(home, busStop, 0, 2), ride("X1", "bus", busStop, station, 3, 6), ride("S1", "suburban", station, end, 9, 25)),
    )

    @Test
    fun aShortFeederAlsoComesOnFoot() {
        val onFoot = walkInsteadOfFeeder(withBus)
        assertNotNull(onFoot)
        onFoot!!
        assertEquals(1, onFoot.rides.size)
        assertEquals("S1", onFoot.rides.single().line)
        val walk = onFoot.legs.first()
        assertTrue(walk.walking)
        assertEquals(home, walk.fromPlace)
        assertEquals(station, walk.toPlace)
        // At the station two minutes before the train, the same arrival.
        assertEquals(at(7), walk.arrival)
        assertEquals(withBus.arrival, onFoot.arrival)
        assertTrue(walk.departure.isBefore(walk.arrival))
    }

    @Test
    fun aFarStationOrNoTrainGetsNoWalk() {
        val far = Journey(
            listOf(walk(home, busStop, 0, 2), ride("X1", "bus", busStop, farStation, 3, 12), ride("S1", "suburban", farStation, end, 15, 25)),
        )
        assertNull(walkInsteadOfFeeder(far))
        val busToBus = Journey(listOf(ride("X1", "bus", busStop, station, 3, 6), ride("X2", "bus", station, end, 9, 25)))
        assertNull(walkInsteadOfFeeder(busToBus))
        val trainFirst = Journey(listOf(walk(home, station, 0, 8), ride("S1", "suburban", station, end, 9, 25)))
        assertNull(walkInsteadOfFeeder(trainFirst))
    }

    @Test
    fun aWalkThatStartsTooEarlyOrInThePastIsLeftOut() {
        // The walk of about 11 minutes starts at 18:56, four minutes before the journey: offered.
        val walk = walkInsteadOfFeeder(withBus)!!.legs.first()
        assertFalse(walk.realtime)
        // Not when it would have to start before now.
        assertNull(walkInsteadOfFeeder(withBus, now = at(0)))
        assertNotNull(walkInsteadOfFeeder(withBus, now = walk.departure))
        assertEquals(1, mergeSearchPages(emptyList(), listOf(withBus), now = at(0)).size)
        // Not when it starts more than 15 minutes before the journey with the feeder: a feeder at
        // 19:23 to a train at 19:24, at the station by 19:22 on foot; a walk of 15 minutes leaves at 19:07.
        val tightFeeder = Journey(listOf(ride("X1", "bus", busStop, station, 23, 24), ride("S1", "suburban", station, end, 24, 45)))
        assertNull(walkInsteadOfFeeder(tightFeeder, walkMinutes = { 15L }))
        // A walk of 14 minutes leaves at 19:08, 15 minutes before: offered.
        assertNotNull(walkInsteadOfFeeder(tightFeeder, walkMinutes = { 14L }))
    }

    @Test
    fun theWalkStaysThroughTheFiltersOfTheSearch() {
        val merged = mergeSearchPages(emptyList(), listOf(withBus))
        assertEquals(2, merged.size)
        // Leaves earlier for the same arrival with one change fewer: never dominated by the feeder.
        assertEquals(2, withoutDominated(merged).size)
        // Asked again, the variant is not made twice.
        assertEquals(2, mergeSearchPages(merged, listOf(withBus)).size)
    }
}
