package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The check that a found place agrees with the postcode or town of a contact address. Neutral sample data only. */
class ContactPlaceTest {
    private fun place(name: String, latitude: Double, postcode: String? = null, areas: List<String> = emptyList()) = Stop(
        id = "address:$latitude,10.0",
        name = name,
        latitude = latitude,
        longitude = 10.0,
        distanceMeters = null,
        kind = PlaceKind.Address,
        postcode = postcode,
        areas = areas,
    )

    @Test
    fun postcodeFieldAgrees() {
        assertTrue(addressAgrees("99001", listOf("Example Road 1"), "99001", "Sampletown"))
    }

    @Test
    fun postcodeInNameAgrees() {
        assertTrue(addressAgrees(null, listOf("99001 Sampletown, Example Road 1"), "99001", null))
    }

    @Test
    fun townInAreasAgreesIgnoringCaseAndUmlauts() {
        assertTrue(addressAgrees(null, listOf("Example Road 1", "Muehlbach"), null, "Mühlbach"))
        assertTrue(addressAgrees(null, listOf("Example Road 1, SAMPLETOWN-North"), null, "Sampletown"))
    }

    @Test
    fun townAsPartOfAnotherWordDoesNotAgree() {
        // A street named after the town lies elsewhere.
        assertFalse(addressAgrees(null, listOf("Sampletowner Road 1, Othercity"), "99001", "Sampletown"))
    }

    @Test
    fun otherPostcodeAndTownDoNotAgree() {
        assertFalse(addressAgrees("11111", listOf("Example Road 1, Othercity"), "99001", "Sampletown"))
    }

    @Test
    fun nothingToCompareNeverAgrees() {
        assertFalse(addressAgrees("99001", listOf("Example Road 1, Sampletown"), null, null))
    }

    @Test
    fun pickSkipsSameStreetInAnotherTown() {
        val elsewhere = place("Example Road 1, Othercity", 50.0, postcode = "11111", areas = listOf("Othercity"))
        val right = place("Example Road 1", 51.0, areas = listOf("Sampletown", "Samplestate"))
        assertEquals(right, pickMatching(listOf(elsewhere, right), "99001", "Sampletown"))
    }

    @Test
    fun pickGivesNothingWithoutAgreement() {
        val elsewhere = place("Example Road 1, Othercity", 50.0, postcode = "11111")
        assertNull(pickMatching(listOf(elsewhere), "99001", "Sampletown"))
    }

    @Test
    fun queryUsesAllFields() {
        val contact = ContactAddress(
            name = "Sample Person",
            address = "Example Road 1\n99001 Sampletown",
            street = "Example Road 1",
            postcode = "99001",
            city = "Sampletown",
            country = "Sampleland",
        )
        assertEquals("Example Road 1, 99001 Sampletown, Sampleland", contactQuery(contact))
    }

    private fun stop(name: String, latitude: Double, areas: List<String> = emptyList()) = Stop(
        id = "stop:$latitude",
        name = name,
        latitude = latitude,
        longitude = 10.0,
        distanceMeters = null,
        kind = PlaceKind.Stop,
        areas = areas,
    )

    @Test
    fun addressWithHouseNumberBeatsStopOfSameStreet() {
        val station = stop("Musterstraße", 52.0, areas = listOf("Musterstadt"))
        val house = place("Musterstraße 12", 52.1, postcode = "12345", areas = listOf("Musterstadt"))
        assertEquals(house, pickAddress(listOf(station, house), "Musterstraße 12", "12345", "Musterstadt", exact = true))
        assertEquals(house, pickAddress(listOf(station, house), "Musterstraße 12", "12345", "Musterstadt", exact = false))
    }

    @Test
    fun stopIsNeverTakenForAnAddress() {
        val station = stop("Musterstraße 12, Musterstadt", 52.0, areas = listOf("Musterstadt"))
        assertNull(pickAddress(listOf(station), "Musterstraße 12", "12345", "Musterstadt", exact = true))
        assertNull(pickAddress(listOf(station), "Musterstraße 12", "12345", "Musterstadt", exact = false))
    }

    @Test
    fun exactNumberBeatsOtherNumberOfSameStreet() {
        val other = place("Musterstraße 1", 52.0, postcode = "12345")
        val right = place("Musterstraße 12", 52.1, postcode = "12345")
        assertEquals(right, pickAddress(listOf(other, right), "Musterstraße 12", "12345", "Musterstadt", exact = false))
        assertNull(pickAddress(listOf(other), "Musterstraße 12", "12345", "Musterstadt", exact = true))
        // Without the exact number the address in the street is the best fallback.
        assertEquals(other, pickAddress(listOf(other), "Musterstraße 12", "12345", "Musterstadt", exact = false))
    }

    @Test
    fun abbreviatedStreetMatches() {
        val house = place("Musterstr. 12a", 52.1, postcode = "12345")
        assertEquals(house, pickAddress(listOf(house), "Musterstraße 12a", "12345", null, exact = true))
    }

    @Test
    fun addressInAnotherTownIsSkipped() {
        val elsewhere = place("Musterstraße 12", 50.0, postcode = "99999", areas = listOf("Othertown"))
        assertNull(pickAddress(listOf(elsewhere), "Musterstraße 12", "12345", "Musterstadt", exact = false))
    }

    @Test
    fun houseNumbers() {
        assertEquals("12", houseNumberOf("Musterstraße 12"))
        assertEquals("12a", houseNumberOf("Musterstraße 12a"))
        assertNull(houseNumberOf("Musterstraße"))
        assertEquals("12", houseNumberOf("Musterstr.12"))
    }

    @Test
    fun streetWrittenWithoutSpaceBeforeTheNumberMatches() {
        val house = place("Musterstraße 12", 52.1, postcode = "12345")
        assertEquals(house, pickAddress(listOf(house), "Musterstr.12", "12345", null, exact = true))
    }

    @Test
    fun numberInAnotherTextIsNotThatHouse() {
        // The street in the name, the "12" only in an area: not the house at number 12.
        val street = place("Musterstraße", 52.1, postcode = "12345", areas = listOf("Block 12"))
        assertNull(pickAddress(listOf(street), "Musterstraße 12", "12345", null, exact = true))
        assertEquals(street, pickAddress(listOf(street), "Musterstraße 12", "12345", null, exact = false))
    }

    @Test
    fun placeNameIsTheContactAddress() {
        val contact = ContactAddress(
            name = "Sample Person",
            address = "Musterstraße 12, 12345 Musterstadt",
            kind = "Home",
            street = "Musterstraße 12",
            postcode = "12345",
            city = "Musterstadt",
        )
        assertEquals("Musterstraße 12, Musterstadt", contactPlaceName(contact))
    }
}
