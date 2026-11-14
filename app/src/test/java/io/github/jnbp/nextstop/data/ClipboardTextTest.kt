package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardTextTest {
    @Test
    fun oneLineStaysAsItIs() {
        assertEquals("Alexanderplatz 1, 10178 Berlin", pasteSearchText("  Alexanderplatz 1, 10178 Berlin \n"))
    }

    @Test
    fun linesBecomeOneAddress() {
        val mail = "Hauptbahnhof\r\nEuropaplatz 1,\n\n10557 Berlin\n"
        assertEquals("Hauptbahnhof, Europaplatz 1, 10557 Berlin", pasteSearchText(mail))
    }

    @Test
    fun spacesCollapse() {
        assertEquals("Friedrichstraße 1, Berlin", pasteSearchText("Friedrichstraße\t  1,​ Berlin"))
    }

    @Test
    fun nothingButSpace() {
        assertNull(pasteSearchText(""))
        assertNull(pasteSearchText(" \n\t \r\n"))
        assertNull(pasteSearchText(",\n;"))
    }

    @Test
    fun longTextIsCutAtAWord() {
        val long = (1..80).joinToString(" ") { "word$it" }
        val text = pasteSearchText(long)!!
        assertTrue(text.length <= PASTE_MAX_CHARS)
        assertTrue(long.startsWith(text))
        // It ends with a whole word.
        assertTrue(long.split(' ').contains(text.substringAfterLast(' ')))
        val oneWord = "x".repeat(500)
        assertEquals(PASTE_MAX_CHARS, pasteSearchText(oneWord)!!.length)
    }
}
