package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** B2: the second line of a blue change card, "Gleis 1 → Pos. 1 (Bus 285)". */
class ChangeStepLineTest {
    @Test
    fun fromPlatformToPositionWithTheNextLine() {
        assertEquals("Gleis 1 → Pos. 1 (Bus 285)", changeStepLine("Gleis 1", "Pos. 1", "Bus 285"))
    }

    @Test
    fun theSamePlatformIsNamedOnce() {
        assertEquals("Gleis 2 (S1)", changeStepLine("Gleis 2", "Gleis 2", "S1"))
    }

    @Test
    fun oneSideAloneWhereTheOtherIsUnknown() {
        assertEquals("Pos. 4 (Bus 100)", changeStepLine(null, "Pos. 4", "Bus 100"))
        assertEquals("Gleis 7 (U2)", changeStepLine("Gleis 7", null, "U2"))
        assertEquals("Gleis 7 → Gleis 3", changeStepLine("Gleis 7", "Gleis 3", null))
    }

    @Test
    fun theLineAloneOrNothing() {
        assertEquals("Tram M10", changeStepLine(null, null, "Tram M10"))
        assertEquals("Tram M10", changeStepLine(" ", "", "Tram M10"))
        assertNull(changeStepLine(null, null, null))
        assertNull(changeStepLine(null, null, " "))
    }
}
