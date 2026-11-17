package io.github.jnbp.nextstop.ui

import java.time.OffsetDateTime
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.Leg
import io.github.jnbp.nextstop.data.Stop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** Another transfer level keeps the list on the same connection, or on the one nearest to it. Made-up stops only. */
class LevelAnchorTest {
    private val start: OffsetDateTime = OffsetDateTime.parse("2026-01-01T10:00:00+01:00")
    private fun stop(id: String) = Stop("90000000$id", "Stop $id", 52.0, 13.0, null)
    private fun journey(leaves: Long, trip: String = "t$leaves") = Journey(
        listOf(
            Leg(
                "U1", "subway", false, "Stop 1", "Stop 2", start.plusMinutes(leaves), start.plusMinutes(leaves),
                start.plusMinutes(leaves + 20), start.plusMinutes(leaves + 20), null, null, null, false,
                fromPlace = stop("1"), toPlace = stop("2"), tripId = trip,
            ),
        ),
    )

    private val at0 = journey(0)
    private val at5 = journey(5)
    private val at10 = journey(10)
    private val at15 = journey(15)

    @Test
    fun theSameConnectionStays() {
        assertSame(at10, levelAnchor(at10, listOf(at0, at10, at15)))
        // Found again as another object (another level asked for it): the same journey by its id.
        val again = journey(10)
        assertSame(again, levelAnchor(at10, listOf(at0, again)))
    }

    @Test
    fun aHiddenConnectionGivesWayToTheNextOne() {
        // The picked journey is tighter than the new level: the next one that leaves, not the first of the list.
        assertSame(at15, levelAnchor(at10, listOf(at0, at5, at15)))
        // Nothing leaves later: the last one before it.
        assertSame(at5, levelAnchor(at10, listOf(at0, at5)))
    }

    @Test
    fun nothingToKeep() {
        assertNull(levelAnchor(at10, emptyList()))
        assertNull(levelAnchor(null, listOf(at0)))
        assertEquals(at0.id, levelAnchor(at0, listOf(at0))?.id)
    }
}
