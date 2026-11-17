package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** One ride, one set of live times and one rating of its changes in every journey (see SharedLive.kt). */
class SharedLiveTest {
    private val start = OffsetDateTime.parse("2026-11-14T08:00:00+01:00")
    private val origin = Stop("100", "Stop A", 52.40, 13.30, null)
    private val hub = Stop("200", "Hub", 52.45, 13.35, null)
    private val far = Stop("300", "Far", 51.00, 13.70, null)
    private val beyond = Stop("400", "Beyond", 50.90, 13.80, null)

    private fun t(minutes: Long) = start.plusMinutes(minutes)

    private fun ride(
        line: String, product: String, trip: String, from: Stop, to: Stop,
        leaves: Long, arrives: Long, liveLeaves: Long = leaves, liveArrives: Long = arrives, realtime: Boolean = false,
    ) = Leg(
        line, product, false, from.name, to.name, t(liveLeaves), t(leaves), t(liveArrives), t(arrives), "1", "2", null, false,
        realtime = realtime, fromPlace = from, toPlace = to, tripId = trip,
    )

    // The same bus and the same train in two journeys: one sent with the train 1 minute late,
    // the other (from another search) without live data for it.
    private val busA = ride("B1", "bus", "bus-1", origin, hub, 27, 37, realtime = true)
    private val trainLate = ride("T1", "express", "train-1", hub, far, 43, 120, liveLeaves = 44, liveArrives = 121, realtime = true)
    private val trainPlain = ride("T1", "express", "train-1", hub, far, 43, 120)
    private val first = Journey(listOf(busA, trainLate), foundLevel = TransferComfort.Normal)
    private val second = Journey(
        listOf(busA, trainPlain, ride("R2", "regional", "regional-2", far, beyond, 130, 150)),
        foundLevel = TransferComfort.Risky,
    )

    @Test
    fun twoJourneysSharingTheirRidesGetTheSameLiveTimesAndRatings() {
        val shared = shareLiveTimes(listOf(first, second))
        val one = shared[0]
        val two = shared[1]
        assertEquals(one.legs[1].departure, two.legs[1].departure)
        assertEquals(t(44), two.legs[1].departure)
        assertEquals(t(121), two.legs[1].arrival)
        assertTrue(two.legs[1].realtime)
        // The planned times, and so the ids, stay.
        assertEquals(listOf(first.id, second.id), shared.map { it.id })
        // The change from the bus to the train is rated the same in both, whichever search found them.
        assertEquals(journeyChangeRatings(one).first(), journeyChangeRatings(two).first())
        assertEquals(journeyChangesOf(one).first().spare, journeyChangesOf(two).first().spare)
        // A ride of only one journey keeps its own times.
        assertEquals(second.legs[2], two.legs[2])
    }

    @Test
    fun theFresherDataWins() {
        // A refresh of the second journey: the train now 3 minutes late.
        val refreshed = second.copy(legs = second.legs.mapIndexed { index, leg ->
            if (index == 1) leg.copy(departure = t(46), arrival = t(123), realtime = true) else leg
        })
        val shared = shareLiveTimes(listOf(first, refreshed), fresher = listOf(refreshed))
        assertEquals(listOf(t(46), t(46)), shared.map { it.legs[1].departure })
        assertEquals(journeyChangeRatings(shared[0]).first(), journeyChangeRatings(shared[1]).first())
        // Merging a new page shares the same way: its live data reaches the journey shown before.
        val merged = mergeSearchPages(listOf(first), listOf(refreshed))
        assertTrue(merged.all { it.legs[1].departure == t(46) })
    }

    @Test
    fun ridesWithoutARunKeepTheirOwnTimes() {
        val anonymous = busA.copy(tripId = null, departure = t(30))
        val shared = shareLiveTimes(listOf(first, Journey(listOf(anonymous, trainPlain))))
        assertEquals(t(30), shared[1].legs[0].departure)
        assertEquals(t(44), shared[1].legs[1].departure)
    }

    @Test
    fun aRunCancelledOnALaterPartLeavesAShorterLegRunning() {
        // The same train boarded at the hub: one journey rides on to Beyond, which it no longer
        // reaches (cancelled there), the other leaves it at Far before that.
        val longer = ride("T1", "express", "train-1", hub, beyond, 43, 140, liveLeaves = 45, realtime = true)
            .copy(cancelled = true)
        val shorter = ride("T1", "express", "train-1", hub, far, 43, 120)
        val shared = shareLiveTimes(listOf(Journey(listOf(busA, longer)), Journey(listOf(busA, shorter))))
        // The live departure at the hub is shared, the cancellation of the longer span is not.
        assertEquals(t(45), shared[1].legs[1].departure)
        assertFalse(shared[1].legs[1].cancelled)
        assertTrue(shared[0].legs[1].cancelled)
    }

    @Test
    fun cancellationIsSharedBetweenLegsOfTheSameSpan() {
        val cancelled = trainLate.copy(cancelled = true)
        val shared = shareLiveTimes(listOf(Journey(listOf(busA, cancelled)), second))
        assertTrue(shared[1].legs[1].cancelled)
        // Fresher data of the same span that runs again lifts it in both.
        val running = trainLate.copy(cancelled = false)
        val refreshed = shareLiveTimes(listOf(Journey(listOf(busA, cancelled)), second), fresher = listOf(Journey(listOf(busA, running))))
        assertFalse(refreshed[0].legs[1].cancelled)
        assertFalse(refreshed[1].legs[1].cancelled)
    }
}
