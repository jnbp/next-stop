package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** "Aktueller Standort" as an address (#207): rounded before the lookup, the street in the field, a fallback. */
class HereAddressTextTest {
    @Test
    fun thePositionIsRoundedBeforeTheLookup() {
        assertEquals(52.5219 to 13.4132, hereRounded(52.521912 to 13.413248))
        // A few metres apart: one lookup.
        assertEquals(hereKey(52.52191 to 13.41324), hereKey(52.52186 to 13.41316))
        assertEquals("52.5219,13.4132", hereKey(52.521912 to 13.413248))
    }

    @Test
    fun theFieldShowsTheStreetWithItsNumber() {
        assertEquals("Musterstraße 12", hereFieldLabel("Musterstraße 12", "Musterstraße 12, 10178 Berlin"))
        // A named place without a street: its name.
        assertEquals("Stadtpark", hereFieldLabel("Stadtpark", "10178 Berlin"))
        assertEquals("Stadtpark", hereFieldLabel("Stadtpark", null))
        assertEquals("10178 Berlin", hereFieldLabel(null, "10178 Berlin"))
        assertNull(hereFieldLabel(null, null))
        assertNull(hereFieldLabel(" ", ""))
    }

    @Test
    fun withoutAnAnswerTheNameStaysWithTheCoordinates() {
        assertEquals("Aktueller Standort · 52.5219, 13.4132", hereFallbackLabel("Aktueller Standort", 52.521912 to 13.413248))
    }
}
