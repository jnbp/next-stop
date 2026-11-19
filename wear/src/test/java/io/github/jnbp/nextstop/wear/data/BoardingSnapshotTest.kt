package io.github.jnbp.nextstop.wear.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * A trip started on the watch and followed there (before or without the phone taking it over)
 * shows where to board as a trip of the phone does (W3): the phone sends it with the journeys it
 * finds for the watch, the watch's own steps carry it, and a reload of the journey keeps it.
 */
class BoardingSnapshotTest {
    private val minute = 60_000L

    private fun ride(line: String, from: String, to: String, dep: Long, arr: Long, hint: String? = null, part: Int = -1, rating: ChangeRating? = null) =
        Leg(
            line, "subway", false, from, to, dep * minute, arr * minute, 0, 0, "2", "Towards", false,
            changeRating = rating, boardHint = hint, boardPart = part,
        )

    private fun walk(from: String, to: String, dep: Long, arr: Long) =
        Leg(null, "walk", true, from, to, dep * minute, arr * minute, 0, 0, null, null, false)

    private val found = Journey(
        listOf(
            walk("Somewhere", "U Stop A", 1101, 1113),
            ride("U3", "U Stop A", "U Mittelplatz", 1113, 1116, hint = "Mitte einsteigen – kurzer Weg zur U2", part = 1),
            ride("U2", "U Mittelplatz", "S+U Alexanderplatz", 1118, 1134, hint = "Hinten einsteigen – kurzer Weg zum Ausgang", part = 0, rating = ChangeRating.Risky),
        ),
        "token",
    )

    @Test
    fun theBoardingStepCarriesTheHint() {
        // On the way to the first ride and waiting for it: the train and the hint of the U3.
        val walking = snapshotOf(found, 1105 * minute)
        assertEquals(StepKind.Board, walking.kind)
        assertEquals("Mitte einsteigen – kurzer Weg zur U2", walking.hint)
        assertEquals(1, walking.boardPart)
        // At the change: the hint of the U2.
        val change = snapshotOf(found, 1117 * minute)
        assertEquals(StepKind.Board, change.kind)
        assertEquals(0, change.boardPart)
        // On board there is none.
        assertEquals(-1, snapshotOf(found, 1114 * minute).boardPart)
        assertNull(snapshotOf(found, 1114 * minute).hint)
    }

    @Test
    fun aJourneyTheWatchFoundItselfHasNone() {
        val own = Journey(found.legs.map { it.copy(boardHint = null, boardPart = -1, changeRating = null) }, "own")
        val step = snapshotOf(own, 1105 * minute)
        assertNull(step.hint)
        assertEquals(-1, step.boardPart)
    }

    @Test
    fun aReloadKeepsWhereToBoard() {
        // The reload by its handle: live times, nothing of the phone's.
        val fresh = Journey(found.legs.map { it.copy(boardHint = null, boardPart = -1, changeRating = null) }, "token")
        val kept = withSearchFacts(fresh, found)
        assertEquals("Mitte einsteigen – kurzer Weg zur U2", kept.legs[1].boardHint)
        assertEquals(0, kept.legs[2].boardPart)
        // The change keeps its minutes: the phone's rating stays.
        assertEquals(ChangeRating.Risky, kept.legs[2].changeRating)
        assertEquals(1, snapshotOf(kept, 1105 * minute).boardPart)
    }

    @Test
    fun aDelayThatChangesTheChangeDropsThePhonesRating() {
        val legs = found.legs.map { it.copy(boardHint = null, boardPart = -1, changeRating = null) }.toMutableList()
        // The U3 arrives a minute late: the watch rates the change anew.
        legs[1] = legs[1].copy(arrival = 1117 * minute)
        val kept = withSearchFacts(Journey(legs, "token"), found)
        assertNull(kept.legs[2].changeRating)
        assertEquals("Hinten einsteigen – kurzer Weg zum Ausgang", kept.legs[2].boardHint)
    }

    @Test
    fun withoutAnythingOfThePhoneTheReloadStaysAsIs() {
        val own = Journey(found.legs.map { it.copy(boardHint = null, boardPart = -1, changeRating = null) }, "own")
        val fresh = own.copy(refreshToken = "fresh")
        assertSame(fresh, withSearchFacts(fresh, own))
    }
}
