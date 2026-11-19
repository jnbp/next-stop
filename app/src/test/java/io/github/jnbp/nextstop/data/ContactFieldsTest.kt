package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** A contact address kept only in its written form is split into its fields (#58). Made-up addresses. */
class ContactFieldsTest {
    @Test
    fun theWrittenAddressGivesStreetPostcodeAndTown() {
        val contact = ContactAddress(name = "Erika", address = "Musterweg 3, 12345 Beispielstadt, Deutschland")
        val split = contactWithFields(contact)
        assertEquals("Musterweg 3", split.street)
        assertEquals("12345", split.postcode)
        assertEquals("Beispielstadt", split.city)
        assertEquals("Deutschland", split.country)
        assertEquals(contact.address, split.address)
        assertEquals("Musterweg 3, 12345 Beispielstadt, Deutschland", contactQuery(split))
    }

    @Test
    fun aCountryPrefixOnThePostcodeIsDropped() {
        val split = contactWithFields(ContactAddress(name = "Max", address = "Beispielallee 7, D-54321 Musterhausen"))
        assertEquals("54321", split.postcode)
        assertEquals("Musterhausen", split.city)
        assertEquals("Beispielallee 7", split.street)
        assertNull(split.country)
    }

    @Test
    fun aContactWithItsOwnFieldsStaysAsItIs() {
        val contact = ContactAddress(name = "Anna", address = "Probestraße 1, 11111 Teststadt", street = "Probestraße 1", postcode = "11111", city = "Teststadt")
        assertSame(contact, contactWithFields(contact))
    }

    @Test
    fun anAddressWithoutAPostcodeStaysAsItIs() {
        val contact = ContactAddress(name = "Jörg", address = "Hauptstraße 2, Musterdorf")
        assertSame(contact, contactWithFields(contact))
    }

    @Test
    fun aTownAloneHasNoStreet() {
        val split = contactWithFields(ContactAddress(name = "Anna", address = "22222 Musterdorf"))
        assertNull(split.street)
        assertEquals("22222", split.postcode)
        assertEquals("Musterdorf", split.city)
    }
}
