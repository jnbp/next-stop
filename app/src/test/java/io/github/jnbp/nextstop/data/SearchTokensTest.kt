package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** The words the search compares places by: made once per text and kept (#109). Made-up names. */
class SearchTokensTest {
    @Test
    fun streetsEndInStr() {
        assertEquals(listOf("gartenstr", "12"), searchTokens("Gartenstraße 12"))
        assertEquals(listOf("gartenstr"), searchTokens("Gartenstrasse"))
        assertEquals(listOf("gartenstr"), searchTokens("Gartenstr."))
        assertEquals(listOf("str"), searchTokens("Straße"))
        // Only at the end of a word.
        assertEquals(listOf("strassenbahn"), searchTokens("Straßenbahn"))
    }

    @Test
    fun umlautsAccentsAndSigns() {
        assertEquals(listOf("muehlenweg"), searchTokens("Mühlenweg"))
        assertEquals(listOf("cafe", "am", "see"), searchTokens("Café am See"))
        assertEquals(listOf("s", "u", "beispielplatz"), searchTokens("S+U Beispielplatz"))
        assertEquals(emptyList<String>(), searchTokens("  "))
    }

    @Test
    fun theSameTextGivesTheKeptWords() {
        val first = searchTokens("Musterallee 5")
        assertSame(first, searchTokens("Musterallee 5"))
    }

    @Test
    fun aTrailingNoteInBracketsGoes() {
        assertEquals("Beispielbahnhof", "Beispielbahnhof [Bus Musterweg]".plainStopName())
    }
}
