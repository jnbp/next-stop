package io.github.jnbp.nextstop.wear.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A rail replacement on the watch's badge, read from its name as the phone reads it. */
class SevLineTest {
    @Test
    fun namedReplacements() {
        assertEquals(SevLine("S1"), sevLineOf("SEV S1", "bus"))
        assertEquals(SevLine("S1"), sevLineOf("S1 (SEV)", "bus"))
        assertEquals(SevLine("RE7"), sevLineOf("Bus SEV RE 7", "bus"))
        assertEquals(SevLine(null), sevLineOf("SEV", "bus"))
        assertEquals(SevLine(null), sevLineOf("EV", "bus"))
    }

    @Test
    fun aBusUnderTheNameOfATrainLine() {
        assertEquals(SevLine("S1"), sevLineOf("S1", "bus"))
        assertEquals(SevLine("U9"), sevLineOf("Bus U9", "bus"))
        // The train itself, and a bus line of letters and digits, are no replacement.
        assertNull(sevLineOf("S1", "suburban"))
        assertNull(sevLineOf("M41", "bus"))
        assertNull(sevLineOf("X9", "bus"))
        assertNull(sevLineOf(null, "bus"))
    }

    @Test
    fun theColourOfTheLineItStandsInFor() {
        assertEquals("suburban", sevReplacedProduct("S1"))
        assertEquals("subway", sevReplacedProduct("U9"))
        assertEquals("regional", sevReplacedProduct("RE7"))
        assertEquals(0xFFDA6BA2, lineArgb("S1", sevReplacedProduct("S1")))
    }
}
