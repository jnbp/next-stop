package io.github.jnbp.nextstop.ui

import java.time.OffsetDateTime
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.Leg
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.TransferComfort
import io.github.jnbp.nextstop.data.journeyComfort
import io.github.jnbp.nextstop.data.journeyKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A change tighter than the provider's own minimum change time at a station is never in a
 * search of the provider, but in one from that station. Made-up stops; the times follow the
 * case of an S1 to a hub and the ring line on from there.
 */
class TightJourneysTest {
    private val start: OffsetDateTime = OffsetDateTime.parse("2026-01-01T13:41:00+01:00")
    private fun at(minutes: Long) = start.plusMinutes(minutes)
    private fun stop(id: String) = Stop("90000000$id", "Stop $id", 52.0, 13.0, null)
    private fun ride(line: String, from: Stop, to: Stop, leaves: Long, arrives: Long, platformFrom: String, platformTo: String) = Leg(
        line, "suburban", false, from.name, to.name, at(leaves), at(leaves), at(arrives), at(arrives),
        platformFrom, platformTo, null, false, fromPlace = from, toPlace = to, tripId = "$line@$leaves",
    )

    private val origin = stop("1")
    private val hub = stop("2")
    private val target = stop("3")

    // S1 13:41 to the hub (13:51), on with the ring at 13:58: what the searches of the provider return.
    private val s1 = ride("S1", origin, hub, 0, 10, "1", "1")
    private val found = Journey(listOf(s1, ride("S42", hub, target, 17, 19, "3", "3")), foundLevel = TransferComfort.Normal)

    // What a search from the hub from 13:45 on offers: the ring at 13:54 (3 minutes to change,
    // all of what another platform needs: tight), 13:58 and 14:03.
    private val options = listOf(
        Journey(listOf(ride("S42", hub, target, 13, 15, "3", "3"))),
        Journey(listOf(ride("S42", hub, target, 17, 19, "3", "3"))),
        Journey(listOf(ride("S46", hub, target, 22, 24, "4", "4"))),
        // Leaves before the S1 is there: cannot be caught.
        Journey(listOf(ride("S41", hub, target, 8, 11, "3", "3"))),
    )

    @Test
    fun theProviderListAloneLacksTheTightJourney() {
        // The drop: the risky level shows nothing tighter than what the provider's minimum allowed.
        val shown = journeySelection(listOf(found), TransferComfort.Risky).shown!!
        assertEquals(listOf(found), shown)
        assertTrue(shown.none { journeyComfort(it) == TransferComfort.Risky })
    }

    @Test
    fun theSearchFromTheChangeAddsTheTighterDeparture() {
        val requests = tightRequests(listOf(found))
        assertEquals(1, requests.size)
        assertEquals(hub, requests.single().stop)
        val (journey, keep) = requests.single().journeys.single()
        val added = tighterAlternatives(journey, keep, options)
        // Only the 13:54 ring: it leaves earlier and gets there earlier; 13:58 is the journey itself,
        // 14:03 arrives later, 13:49 cannot be caught.
        assertEquals(1, added.size)
        val tight = added.single()
        assertEquals(at(13), tight.legs[1].departure)
        assertEquals(TransferComfort.Risky, tight.foundLevel)
        assertEquals(TransferComfort.Risky, journeyComfort(tight))

        val all = withTighterJourneys(listOf(found), added)
        assertEquals(listOf(found, tight).map { journeyKey(it) }.toSet(), all.map { journeyKey(it) }.toSet())
        // The risky level shows both, the normal level only the journey of the provider.
        assertEquals(2, journeySelection(all, TransferComfort.Risky).shown!!.size)
        val normal = journeySelection(all, TransferComfort.Normal)
        assertEquals(listOf(found), normal.shown)
        assertEquals(TransferComfort.Risky, normal.nextLevel)
        assertEquals(1, normal.nextCount)
    }

    @Test
    fun askedChangesAreNotAskedAgainAndNothingIsAddedTwice() {
        val key = tightRequests(listOf(found)).single().key
        assertTrue(tightRequests(listOf(found), setOf(key)).isEmpty())
        val added = tighterAlternatives(found, 1, options)
        val once = withTighterJourneys(listOf(found), added)
        assertEquals(once, withTighterJourneys(once, added))
    }

    @Test
    fun aJourneyWithoutChangeAsksNothing() {
        val direct = Journey(listOf(ride("S1", origin, target, 0, 20, "1", "1")))
        assertTrue(tightRequests(listOf(direct)).isEmpty())
        assertFalse(tighterAlternatives(direct, 1, options).isNotEmpty())
    }
}
