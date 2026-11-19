package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.PlaceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The naming of a place chosen on the map (see MapPick.kt). */
class MapPickTest {
    private val near: (String) -> String = { "Point near $it" }

    @Test
    fun anAddressNamesThePlace() {
        assertEquals("Example Street 12", mapPickName("Example Street 12", "Some Stop", "Point", near))
    }

    @Test
    fun withoutAnAddressThePointIsNamedAfterTheStopNextToIt() {
        assertEquals("Point near Some Stop", mapPickName(null, "Some Stop", "Point", near))
        assertEquals("Point near Some Stop", mapPickName("  ", "Some Stop", "Point", near))
    }

    @Test
    fun withNeitherItIsJustAPoint() {
        assertEquals("Point", mapPickName(null, null, "Point", near))
        assertEquals("Point", mapPickName("", "", "Point", near))
    }

    @Test
    fun theTakenPlaceIsAnAddressAtThePin() {
        val place = mapPickPlace(52.5, 13.4, "Point")
        assertEquals(PlaceKind.Address, place.kind)
        assertEquals(52.5 to 13.4, place.position)
        assertEquals("Point", place.name)
        assertNull(place.distanceMeters)
        assertEquals("pin:52.5,13.4", place.id)
    }
}
