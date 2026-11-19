package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChangeWordsTest {
    @Test
    fun aMissingOrBlankPositionIsNotPrinted() {
        assertNull(shownPositionLabel(null))
        assertNull(shownPositionLabel(""))
        assertNull(shownPositionLabel("  "))
        assertEquals("3", shownPositionLabel(" 3 "))
    }

    @Test
    fun theStepFallsBackToTheOtherStopOrThePlainWord() {
        // Seen: "Gleis 2 → Pos.  (Tram M13)". Without any position the other stop or the plain word is said.
        assertEquals("Pos. 4", changeStepTarget("Pos. 4", "Gleis 1", null, null, null))
        assertEquals("Gleis 1", changeStepTarget(null, "Gleis 1", "Pos. 2", null, null))
        assertEquals("Pos. 2", changeStepTarget(null, null, "Pos. 2", "Am Testpark", "Haltestelle"))
        assertEquals("Am Testpark", changeStepTarget(null, null, null, "Am Testpark", "Haltestelle"))
        assertEquals("Haltestelle", changeStepTarget(null, null, "", null, "Haltestelle"))
        assertNull(changeStepTarget(null, null, null, null, null))
    }

    @Test
    fun anExitNameLosesItsOwnWord() {
        // Seen: "über Ausgang Ausgang Richtung …".
        assertEquals("Richtung Musterweg", doorLabelWithoutWord("Ausgang Richtung Musterweg"))
        assertEquals("Richtung Musterweg", doorLabelWithoutWord("ausgang Richtung Musterweg"))
        assertEquals("B", doorLabelWithoutWord("AUSGANG B"))
        assertEquals("B", doorLabelWithoutWord("Ausgang: B"))
        assertEquals("H", doorLabelWithoutWord("H"))
        assertEquals("", doorLabelWithoutWord("Ausgang"))
        // A word that only starts like it stays.
        assertEquals("Ausgangsstraße", doorLabelWithoutWord("Ausgangsstraße"))
    }
}
