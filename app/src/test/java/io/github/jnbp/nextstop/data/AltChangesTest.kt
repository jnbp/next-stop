package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The same bus and the same train, changed at one of two stops: one journey offering the other
 * change point. Made-up stops only.
 */
class AltChangesTest {
    private val start: OffsetDateTime = OffsetDateTime.parse("2026-01-01T19:00:00+01:00")
    private fun at(minutes: Long) = start.plusMinutes(minutes)
    private fun stop(id: String, north: Double) = Stop("90000000$id", "Stop $id", 52.0 + north, 13.0, null)
    private val stops = mapOf("1" to stop("1", 0.0), "2" to stop("2", 0.01), "3" to stop("3", 0.02), "4" to stop("4", 0.05))
    private fun ride(line: String, product: String, from: String, to: String, leaves: Long, arrives: Long, trip: String) = Leg(
        line, product, false, "Stop $from", "Stop $to", at(leaves), at(leaves), at(arrives), at(arrives),
        null, null, null, false, fromPlace = stops.getValue(from), toPlace = stops.getValue(to), tripId = trip,
    )

    // Bus 1 → 2 (19:10) → 3 (19:11); the train 2 (19:14) → 3 (19:16) → 4 (19:25).
    private val atTwo = Journey(listOf(ride("X1", "bus", "1", "2", 0, 10, "bus"), ride("U5", "subway", "2", "4", 14, 25, "train")))
    private val atThree = Journey(listOf(ride("X1", "bus", "1", "3", 0, 11, "bus"), ride("U5", "subway", "3", "4", 16, 25, "train")))

    @Test
    fun theSameRidesChangedElsewhereAreOneJourney() {
        assertTrue(sameRidesOtherChange(atTwo, atThree))
        val merged = withAltChanges(listOf(atTwo, atThree))
        assertEquals(1, merged.size)
        // More time to spare at stop 3 (5 minutes against 4): that change stays, stop 2 is offered.
        assertEquals(journeyKey(atThree), journeyKey(merged.single()))
        val alt = merged.single().altChanges.single()
        assertEquals("Stop 2", alt.at)
        assertEquals(0, alt.changeIndex)
        assertEquals(journeyKey(atTwo), journeyKey(alt.journey))
    }

    @Test
    fun anotherTrainOrAnotherArrivalStaysApart() {
        val laterTrain = Journey(listOf(ride("X1", "bus", "1", "3", 0, 11, "bus"), ride("U5", "subway", "3", "4", 21, 30, "train2")))
        assertFalse(sameRidesOtherChange(atTwo, laterTrain))
        val otherLine = Journey(listOf(ride("X1", "bus", "1", "3", 0, 11, "bus"), ride("U6", "subway", "3", "4", 16, 25, "other")))
        assertFalse(sameRidesOtherChange(atTwo, otherLine))
        assertFalse(sameRidesOtherChange(atTwo, atTwo.copy()))
        assertEquals(2, withAltChanges(listOf(atTwo, laterTrain)).size)
    }

    @Test
    fun switchingKeepsTheOtherOnOfferAndSticks() {
        val merged = withAltChanges(listOf(atTwo, atThree)).single()
        val switched = switchToAltChange(merged, merged.altChanges.single())
        assertEquals(journeyKey(atTwo), journeyKey(switched))
        assertEquals("Stop 3", switched.altChanges.single().at)
        // A later page with the better change again keeps the variant the user picked.
        val again = withAltChanges(listOf(switched, atThree))
        assertEquals(listOf(journeyKey(atTwo)), again.map { journeyKey(it) })
    }

    @Test
    fun pagesMergeTheTwins() {
        val shown = mergeSearchPages(emptyList(), listOf(atTwo))
        val both = mergeSearchPages(shown, listOf(atThree))
        assertEquals(1, both.size)
        assertEquals(1, both.single().altChanges.size)
    }

    @Test
    fun theShownTwinStaysAndOffersTheBetterOne() {
        // Stop 2 is shown first; the better change at stop 3 comes with a later page.
        val shown = mergeSearchPages(emptyList(), listOf(atTwo))
        val both = mergeSearchPages(shown, listOf(atThree))
        assertEquals(listOf(journeyKey(atTwo)), both.map { journeyKey(it) })
        assertEquals("Stop 3", both.single().altChanges.single().at)
        // Without anything shown the better change leads.
        assertEquals(listOf(journeyKey(atThree)), withAltChanges(listOf(atTwo, atThree), emptySet()).map { journeyKey(it) })
        assertEquals(listOf(journeyKey(atTwo)), withAltChanges(listOf(atTwo, atThree), setOf(journeyKey(atTwo))).map { journeyKey(it) })
    }
}
