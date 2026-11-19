package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contacts and appointments the search finds while typing (#68). Made-up names and places. */
class PeopleMatchTest {
    private data class Person(val name: String, val address: String)

    private val people = listOf(
        Person("Erika Musterfrau", "Musterweg 3, 12345 Beispielstadt"),
        Person("Max Mustermann", "Beispielallee 7, 54321 Musterhausen"),
        Person("Jörg Ärmel", "Probestraße 1, 11111 Teststadt"),
        Person("Anna Beispiel", "Hauptstraße 2, 22222 Musterdorf"),
    )

    private fun find(text: String, limit: Int = 5) = peopleMatching(people, text, limit) { listOf(it.name, it.address) }.map { it.name }

    @Test
    fun aNameIsFoundByTheStartOfItsWords() {
        assertEquals(listOf("Erika Musterfrau"), find("eri"))
        assertEquals(listOf("Erika Musterfrau", "Max Mustermann"), find("muster").take(2))
    }

    @Test
    fun namesComeBeforeAddresses() {
        // "Muster" stands in two names and in two addresses: the names first.
        val found = find("muster")
        assertEquals(listOf("Erika Musterfrau", "Max Mustermann", "Anna Beispiel"), found)
    }

    @Test
    fun everyWordHasToFit() {
        assertEquals(listOf("Max Mustermann"), find("max must"))
        assertTrue(find("max erika").isEmpty())
    }

    @Test
    fun umlautsAndCaseDoNotCount() {
        assertEquals(listOf("Jörg Ärmel"), find("joerg"))
        assertEquals(listOf("Jörg Ärmel"), find("ÄRM"))
        assertEquals(listOf("Jörg Ärmel"), find("probestr"))
    }

    @Test
    fun tooShortOrEmptyFindsNothingAndTheLimitHolds() {
        assertTrue(find("m").isEmpty())
        assertTrue(find("  ").isEmpty())
        assertEquals(1, find("muster", limit = 1).size)
    }

    @Test
    fun aWordInsideAnotherDoesNotCount() {
        // "frau" is inside "Musterfrau", not the start of a word.
        assertTrue(find("frau").isEmpty())
    }

    @Test
    fun anIndexMadeOnceFindsTheSame() {
        val index = peopleIndex(people) { listOf(it.name, it.address) }
        for (text in listOf("eri", "muster", "max must", "joerg", "ÄRM", "probestr", "frau", "m")) {
            assertEquals(text, find(text), peopleMatchingIn(index, text, 5).map { it.name })
        }
    }

    @Test
    fun accentsAreTakenOff() {
        assertEquals(listOf("cafe", "creme"), matchWords("Café Crème"))
        assertEquals(listOf("muster", "1"), matchWords("Muster-1"))
    }
}
