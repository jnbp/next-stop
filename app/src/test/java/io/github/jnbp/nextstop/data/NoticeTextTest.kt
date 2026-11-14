package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Link placeholders of BVG notices become links (see NoticeText.kt). Texts as v6.bvg.transport.rest sends them. */
class NoticeTextTest {
    // Html.fromHtml stands in: tags out, nothing else.
    private val plain: (String) -> String = { it.replace(Regex("<[^>]+>"), "").trim() }

    @Test
    fun moreLinkLeavesTheText() {
        val html = "Tram 12: Ersatzverkehr aufgrund von Bauarbeiten zwischen Am Kupfergraben und U Eberswalder Straße.\n" +
            "<a href=\"https://www.bvg.de/de/verbindungen/stoerungsmeldungen?modalDisruptionId=b11ea83f\" target=\"_blank\" rel=\"noopener\">[MEHR/MORE]</a>"
        val parsed = parseNoticeText(html, plain)
        assertEquals("Tram 12: Ersatzverkehr aufgrund von Bauarbeiten zwischen Am Kupfergraben und U Eberswalder Straße.", parsed.text)
        assertEquals(
            listOf(NoticeLink(NoticeLinkKind.More, "https://www.bvg.de/de/verbindungen/stoerungsmeldungen?modalDisruptionId=b11ea83f")),
            parsed.links,
        )
    }

    @Test
    fun mapLinkIsAMapLink() {
        val html = "Bus 248: Umleitung. <a href=\"https://fahrinfo.vbb.de/him-uploads/1_Kartenausschnitt.jpg\" target=\"_blank\">[KARTE/MAPS]</a>"
        val parsed = parseNoticeText(html, plain)
        assertEquals("Bus 248: Umleitung.", parsed.text)
        assertEquals(NoticeLinkKind.Map, parsed.links.single().kind)
    }

    @Test
    fun barePlaceholdersNeverShow() {
        val parsed = parseNoticeText("Haltestelle verlegt. [MEHR/MORE] [KARTE/MAP]", plain)
        assertFalse(parsed.text.contains("["))
        assertEquals(listOf(NoticeLinkKind.More, NoticeLinkKind.Map), parsed.links.map { it.kind })
        assertEquals(listOf<String?>(null, null), parsed.links.map { it.url })
    }

    @Test
    fun namesInBracketsStay() {
        val text = "Ab S+U Alexanderpl./Grunerstr. [Grunerstr.]"
        assertEquals(text, parseNoticeText(text, plain).text)
        assertEquals(text, withoutPlaceholders(text))
    }
}
