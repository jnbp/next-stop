package io.github.jnbp.nextstop.wear.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A platform without the line the badge beside it says (see platformWithoutLine). */
class PlatformLabelTest {
    @Test
    fun theRowsLineInBracketsIsLeftOut() {
        assertEquals("1", platformWithoutLine("1 (U7)", "U7"))
        assertEquals("2", platformWithoutLine("2(S 41)", "S41"))
    }

    @Test
    fun otherBracketsStay() {
        assertEquals("1 (U8)", platformWithoutLine("1 (U8)", "U7"))
        assertEquals("3 (Ost)", platformWithoutLine("3 (Ost)", "M10"))
        assertEquals("Pos. 11", platformWithoutLine("Pos. 11", "U7"))
        assertEquals("1 (U7)", platformWithoutLine("1 (U7)", null))
    }

    @Test
    fun noPlatform() {
        assertNull(platformWithoutLine(null, "U7"))
        assertNull(platformWithoutLine(" ", "U7"))
    }
}
