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
}
