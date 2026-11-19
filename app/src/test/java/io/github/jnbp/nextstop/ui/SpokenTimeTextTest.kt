package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Times shown with the prime are read out in words (#67). */
class SpokenTimeTextTest {
    private fun spoken(text: String) = spokenTimeText(
        text,
        minutes = { if (it == 1) "$it Minute" else "$it Minuten" },
        hours = { if (it == 1) "$it Stunde" else "$it Stunden" },
    )

    @Test
    fun minutesAndHoursInWords() {
        assertEquals("in 5 Minuten", spoken("in 5′"))
        assertEquals("1 Stunde 20 Minuten", spoken("1:20 h"))
        assertEquals("5 Stunden 20 Minuten", spoken("5:20 h"))
        assertEquals("1 Stunde 5 Minuten", spoken("1:05 h"))
        assertEquals("1 Stunde 1 Minute", spoken("1:01 h"))
        assertEquals("2 Stunden", spoken("2:00 h"))
        assertEquals("ca. 2 Stunden 58 Minuten", spoken("ca. 2:58 h"))
        assertEquals("Aussteigen in 2 Stunden 58 Minuten · um 03:11", spoken("Aussteigen in 2:58 h · um 03:11"))
        assertEquals("2 Stunden", spoken("2 h"))
        assertEquals("Live · vor 1 Minute", spoken("Live · vor 1′"))
        assertEquals("In ca. 3 Minuten aussteigen", spoken("In ca. 3′ aussteigen"))
    }

    @Test
    fun otherTextStaysAsItIs() {
        assertEquals("Hbf (tief) · 13:41", spoken("Hbf (tief) · 13:41"))
        assertEquals("4 km/h", spoken("4 km/h"))
        assertEquals("S41 hält", spoken("S41 hält"))
        // A clock time without an "h" after it is no duration.
        assertEquals("ab 12:30 heute", spoken("ab 12:30 heute"))
    }

    @Test
    fun theMarkIsThePrime() {
        assertEquals("′", MINUTE_PRIME)
    }
}
