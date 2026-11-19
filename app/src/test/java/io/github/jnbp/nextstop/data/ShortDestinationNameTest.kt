package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** #146: the watch shows the destination by its short name, not the whole address. */
class ShortDestinationNameTest {
    private val at = OffsetDateTime.parse("2026-11-18T16:00:00+01:00")

    private fun walkTo(name: String, kind: PlaceKind?): Leg = Leg(
        null, "walking", true, "Some Stop", name, at, null, at.plusMinutes(5), null, null, null, null, false,
        toPlace = kind?.let { Stop("x", name, 52.0, 13.0, null, kind = it) },
    )

    @Test
    fun pointOfInterestByItsName() {
        assertEquals("Town Hall", shortDestinationName(walkTo("Town Hall, Main Street 1/3, 12345 Springfield-North", PlaceKind.Poi)))
    }

    @Test
    fun addressByStreetAndNumber() {
        assertEquals("Musterstraße 5", shortDestinationName(walkTo("Musterstraße 5, 12345 Musterstadt", PlaceKind.Address)))
        // Unknown kind, but a postcode says it is an address.
        assertEquals("Musterstraße 5", shortDestinationName(walkTo("Musterstraße 5, 12345 Musterstadt", null)))
    }

    @Test
    fun stopKeepsItsName() {
        assertEquals("Example Square", shortDestinationName(walkTo("Example Square", PlaceKind.Stop)))
    }

    @Test
    fun lastRideAsTheRideNamesIt() {
        val ice = Leg(
            "ICE 1092", "express", false, "Frankfurt (Main) Hauptbahnhof", "Hanau Hauptbahnhof",
            at, null, at.plusHours(1), null, null, null, "Hanau Hauptbahnhof", false,
        )
        assertEquals("Hanau Hbf", shortDestinationName(ice))
    }
}
