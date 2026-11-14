package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** The parts of the line under a walk's map (see walkSummaryText). */
class WalkSummaryTest {
    @Test
    fun doorNamesLoseTheirWord() {
        assertEquals("Europaplatz", doorName("Eingang Europaplatz"))
        assertEquals("B", doorName("Ausgang B"))
        assertEquals("Invalidenstraße", doorName("Ein-/Ausgang Invalidenstraße"))
        assertEquals("3", doorName("Exit 3"))
        assertEquals("", doorName("Ausgang"))
        assertEquals("Eingangshalle", doorName("Eingangshalle"))
        assertEquals("Europaplatz", doorName("Europaplatz"))
    }

    @Test
    fun equalNeighboursAreCounted() {
        assertEquals(
            listOf("Ausgang A" to 1, "Rolltreppe" to 2, "Aufzug" to 1, "Rolltreppe" to 1),
            collapseRuns(listOf("Ausgang A", "Rolltreppe", "Rolltreppe", "Aufzug", "Rolltreppe")),
        )
    }
}
