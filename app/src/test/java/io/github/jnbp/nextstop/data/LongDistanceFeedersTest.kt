package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import io.github.jnbp.nextstop.ui.journeySelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #272: long-distance trains with a local feeder at the chosen level. A U-Bahn to the first ICE
 * with too little room made the whole combination of trains "knapp"; an earlier U-Bahn to the
 * same ICE is offered at "Normal". Made-up start; stations Berlin Hbf, Leipzig Hbf, Dresden.
 */
class LongDistanceFeedersTest {
    private val day: OffsetDateTime = OffsetDateTime.parse("2026-11-19T09:00:00+01:00")
    private fun at(minutes: Long) = day.plusMinutes(minutes)

    private val start = Stop("900000001", "Start", 52.40, 13.30, null, setOf("subway"))
    private val berlin = Stop("900000002", "Berlin Hbf", 52.52, 13.37, null, setOf("express", "subway"))
    private val other = Stop("900000003", "Berlin Ostbahnhof", 52.51, 13.43, null, setOf("express"))
    private val leipzig = Stop("8010205", "Leipzig Hbf", 51.35, 12.38, null, setOf("express"))
    private val dresden = Stop("8010085", "Dresden Hbf", 51.04, 13.73, null, setOf("express", "tram"))
    private val end = Stop("8099001", "Dresden Ziel", 51.06, 13.75, null, setOf("tram"))

    private fun ride(line: String, product: String, from: Stop, to: Stop, leaves: Long, arrives: Long, trip: String = "$line@$leaves") = Leg(
        line, product, false, from.name, to.name, at(leaves), at(leaves), at(arrives), at(arrives),
        null, null, null, false, fromPlace = from, toPlace = to, tripId = trip,
    )

    private fun subway(leaves: Long, arrives: Long, to: Stop = berlin) = ride("U5", "subway", start, to, leaves, arrives)
    private val ice1 = ride("ICE 604", "express", berlin, leipzig, 60, 130)
    private val ice2 = ride("ICE 611", "express", leipzig, dresden, 151, 221)

    // The U-Bahn gets to the ICE 5 minutes before it leaves: no room beyond what the change needs (5).
    private val tight = Journey(listOf(subway(43, 55), ice1, ice2))

    // What an arrive-by search to Berlin Hbf returns: 12, 7 and 3 minutes before the ICE.
    private val roomyHead = Journey(listOf(subway(36, 48)))
    private val normalHead = Journey(listOf(subway(41, 53)))
    private val tightHead = Journey(listOf(subway(45, 57)))

    @Test
    fun theFeederMakesTheTrainsKnapp() {
        val changes = journeyChangesOf(tight)
        assertEquals(2, changes.size)
        // The overall rating is the tightest change: the U-Bahn to the ICE, not the 21 minutes between the ICEs.
        assertEquals(Tightness.Risky, changes[0].tightness)
        assertEquals(Tightness.Comfortable, changes[1].tightness)
        assertEquals(TransferComfort.Risky, journeyComfort(tight))
    }

    @Test
    fun aKnappFeederNeedsAnotherOneAtNormalAndRelaxed() {
        assertEquals(LongFeederEnd.First, longFeederEnd(tight, TransferComfort.Normal))
        assertEquals(LongFeederEnd.First, longFeederEnd(tight, TransferComfort.Relaxed))
        // At "knapp" and "Sprint" it is shown already.
        assertNull(longFeederEnd(tight, TransferComfort.Risky))
        assertNull(longFeederEnd(tight, TransferComfort.VeryRisky))
    }

    @Test
    fun aTightChangeBetweenTheTrainsNeedsNoFeeder() {
        val tightTrains = Journey(listOf(subway(30, 40), ice1, ride("ICE 611", "express", leipzig, dresden, 135, 205)))
        assertNull(longFeederEnd(tightTrains, TransferComfort.Normal))
    }

    @Test
    fun localJourneysAndSwappedOnesNeedNone() {
        val local = Journey(listOf(subway(43, 55), ride("S1", "suburban", berlin, other, 57, 65)))
        assertNull(longFeederEnd(local, TransferComfort.Normal))
        val swapped = tight.copy(split = JourneySplit(headLegs = 1, headToken = null, tailToken = null))
        assertNull(longFeederEnd(swapped, TransferComfort.Normal))
    }

    @Test
    fun aKnappLastMileNeedsALaterOne() {
        val journey = Journey(listOf(subway(30, 40), ice1, ice2, ride("3", "tram", dresden, end, 226, 235)))
        assertEquals(LongFeederEnd.Last, longFeederEnd(journey, TransferComfort.Normal))
    }

    @Test
    fun tightAtBothEndsIsLeftAlone() {
        val journey = Journey(listOf(subway(43, 55), ice1, ice2, ride("3", "tram", dresden, end, 226, 235)))
        assertNull(longFeederEnd(journey, TransferComfort.Normal))
    }

    @Test
    fun theFeederArrivesWhatTheChangeNeedsAndTheSpareBefore() {
        assertEquals(at(53), longFeederLatestArrival(at(60), need = 5, spare = 1))
        assertEquals(at(47), longFeederLatestArrival(at(60), need = 8, spare = 4))
        assertEquals(at(227), longFeederEarliestDeparture(at(221), need = 5, spare = 1))
    }

    @Test
    fun oneSearchPerTrainWithTheTimeOfItsChange() {
        val second = Journey(listOf(subway(44, 56), ice1, ice2))
        val requests = longFeederRequests(listOf(tight, second), TransferComfort.Normal)
        assertEquals(1, requests.size)
        val request = requests.single()
        assertEquals(LongFeederEnd.First, request.end)
        assertEquals(berlin.id, request.station.id)
        assertEquals(longFeederLatestArrival(ice1.departure, 5, normalSpare), request.time)
        assertEquals(2, request.journeys.size)
        // Asked once, never again; and never more than the cap.
        assertTrue(longFeederRequests(listOf(tight), TransferComfort.Normal, setOf(request.key)).isEmpty())
        assertTrue(longFeederRequests(listOf(tight), TransferComfort.Normal, cap = 0).isEmpty())
        // Another level is another search.
        assertTrue(longFeederRequests(listOf(tight), TransferComfort.Relaxed, setOf(request.key)).isNotEmpty())
    }

    @Test
    fun theLatestFeederAtTheLevelIsTaken() {
        val request = longFeederRequests(listOf(tight), TransferComfort.Normal).single()
        val found = listOf(roomyHead, normalHead, tightHead)
        val normal = longFeederVariants(request, found, TransferComfort.Normal).single()
        assertEquals(at(41), normal.departure)
        assertEquals(TransferComfort.Normal, journeyComfort(normal))
        // The same trains as before.
        assertEquals(ridesKey(Journey(tight.legs.drop(1))), ridesKey(Journey(normal.legs.drop(1))))
        assertEquals(tight.arrival, normal.arrival)
        val relaxedRequest = longFeederRequests(listOf(tight), TransferComfort.Relaxed).single()
        val relaxed = longFeederVariants(relaxedRequest, found, TransferComfort.Relaxed).single()
        assertEquals(at(36), relaxed.departure)
    }

    @Test
    fun aFeederToAnotherStationOrTooLateIsNone() {
        assertNull(withEarlierFeeder(tight, 1, Journey(listOf(subway(36, 48, to = other)))))
        assertNull(withEarlierFeeder(tight, 1, Journey(listOf(subway(50, 62)))))
        assertNotNull(withEarlierFeeder(tight, 1, normalHead))
        val request = longFeederRequests(listOf(tight), TransferComfort.Normal).single()
        assertTrue(longFeederVariants(request, listOf(tightHead), TransferComfort.Normal).isEmpty())
    }

    @Test
    fun theLaterLastMileArrivesAtTheLevel() {
        val journey = Journey(listOf(subway(30, 40), ice1, ice2, ride("3", "tram", dresden, end, 226, 235)))
        val request = longFeederRequests(listOf(journey), TransferComfort.Normal).single()
        assertEquals(LongFeederEnd.Last, request.end)
        val rests = listOf(
            Journey(listOf(ride("3", "tram", dresden, end, 229, 240))),
            Journey(listOf(ride("3", "tram", dresden, end, 234, 245))),
        )
        val later = longFeederVariants(request, rests, TransferComfort.Normal).single()
        assertEquals(at(240), later.arrival)
        assertTrue(journeyAtLevel(later, TransferComfort.Normal))
    }

    @Test
    fun atNormalTheTrainsShowWithTheEarlierFeeder() {
        val request = longFeederRequests(listOf(tight), TransferComfort.Normal).single()
        val fed = longFeederVariants(request, listOf(normalHead), TransferComfort.Normal).single()
        val before = journeySelection(listOf(tight), TransferComfort.Normal)
        assertTrue(before.shown.orEmpty().isEmpty())
        val after = journeySelection(listOf(tight, fed), TransferComfort.Normal)
        assertEquals(listOf(fed.id), after.shown.orEmpty().map { it.id })
        // At "knapp" the knapp twin with the same trains and arrival goes too: one column, not two.
        val knapp = journeySelection(listOf(tight, fed), TransferComfort.Risky)
        assertEquals(listOf(fed.id), knapp.shown.orEmpty().map { it.id })
    }

    @Test
    fun aLaterLastMileShowsAtNormal() {
        val journey = Journey(listOf(subway(30, 40), ice1, ice2, ride("3", "tram", dresden, end, 226, 235)))
        val request = longFeederRequests(listOf(journey), TransferComfort.Normal).single()
        val later = longFeederVariants(request, listOf(Journey(listOf(ride("3", "tram", dresden, end, 229, 240)))), TransferComfort.Normal).single()
        assertEquals(listOf(later.id), journeySelection(listOf(journey, later), TransferComfort.Normal).shown.orEmpty().map { it.id })
        // At "knapp" the two differ only in the short last tram: one column with the other counted (see groupFeederAlternatives).
        val knapp = journeySelection(listOf(journey, later), TransferComfort.Risky).shown.orEmpty()
        assertEquals(1, knapp.size)
        assertEquals(1, knapp.single().groupedAlternatives)
    }
}
