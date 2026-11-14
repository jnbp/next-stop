package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextLinksTest {
    private fun targets(text: String) = findTextLinks(text).map { it.target }

    private fun shown(text: String) = findTextLinks(text).map { text.substring(it.start, it.end) }

    @Test
    fun webAddresses() {
        val text = "Mehr unter https://www.example.org/stoerungen?id=1. Oder auf www.example.de, oder example.com/info!"
        assertEquals(
            listOf("https://www.example.org/stoerungen?id=1", "https://www.example.de", "https://example.com/info"),
            targets(text),
        )
        assertEquals(listOf("https://www.example.org/stoerungen?id=1", "www.example.de", "example.com/info"), shown(text))
    }

    @Test
    fun emailIsNotAlsoAWebAddress() {
        assertEquals(listOf("mailto:info@example.de"), targets("Fragen an info@example.de."))
    }

    @Test
    fun phoneNumbers() {
        assertEquals(listOf("tel:03019449"), targets("Kundendienst: 030 19449."))
        assertEquals(listOf("tel:03025414141"), targets("Rufbus unter (030) 25 41 41 41 bestellen"))
        assertEquals(listOf("tel:+49301234567"), targets("Hotline +49 30 1234567"))
        assertEquals(listOf("tel:+49301234567"), targets("Hotline +49 (0)30 1234567"))
        assertEquals(listOf("tel:08001507090"), targets("kostenlos 0800 1 507090"))
    }

    @Test
    fun datesTimesAndLinesAreNoLinks() {
        assertTrue(findTextLinks("Vom 05.11.2026 bis 20.11.2026, 22:00 bis 04:30 Uhr, Linie 100 und S1, z.B. Bus 735.").isEmpty())
    }

    @Test
    fun rangesAreInOrder() {
        val text = "Tel. 030 19449 oder www.example.de"
        val links = findTextLinks(text)
        assertEquals(2, links.size)
        assertTrue(links[0].start < links[1].start)
    }
}
