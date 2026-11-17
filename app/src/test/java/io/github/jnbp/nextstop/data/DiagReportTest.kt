package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagReportTest {
    private val redaction = DiagRedaction(
        secrets = listOf("abcd1234secret", "tok-987654", ""),
        privateTerms = listOf("Example Street 12", "Home", "https://my.server.example/api", "Al"),
    )

    @Test
    fun coordinatesKeepTwoDecimals() {
        assertEquals("at 52.51,13.38 and -0.12", redaction.redact("at 52.512345,13.384567 and -0.12345"))
        // Numbers with up to two decimals stay.
        assertEquals("delay 1.5 min, 12.34", redaction.redact("delay 1.5 min, 12.34"))
    }

    @Test
    fun timesOfLogLinesStay() {
        val line = "2026-11-17 01:02:03.456 I/App: started"
        assertEquals(line, redaction.redact(line))
    }

    @Test
    fun secretsAndKeyParametersGo() {
        val out = redaction.redact("GET /x?accessId=zzz999&format=json key abcd1234secret Authorization: Bearer xyz.789 apikey=q tok-987654")
        assertFalse(out.contains("abcd1234secret"))
        assertFalse(out.contains("zzz999"))
        assertFalse(out.contains("xyz.789"))
        assertFalse(out.contains("tok-987654"))
        assertTrue(out.contains("accessId=[key]&format=json"))
        assertTrue(out.contains("apikey=[key]"))
    }

    @Test
    fun privatePlacesAndServerGo() {
        val out = redaction.redact("route from home to Example Street 12 via https://my.server.example/api/journeys")
        assertEquals("route from [private] to [private] via [private]/journeys", out)
        // Too short to be replaced safely.
        assertTrue(redaction.redact("Alexanderplatz").contains("Alexanderplatz"))
    }

    @Test
    fun credentialsMailAndPlaceParametersGo() {
        val out = redaction.redact("https://user:pw@host.example/x?from.name=Example%20Way%201&to.address=Sample%20Avenue mail me@example.org")
        assertFalse(out.contains("user:pw"))
        assertFalse(out.contains("Example%20Way"))
        assertFalse(out.contains("Sample%20Avenue"))
        assertFalse(out.contains("me@example.org"))
        assertTrue(out.contains("https://[cred]@host.example"))
        assertTrue(out.contains("[email]"))
    }

    @Test
    fun typedSearchTextsGo() {
        val hafas = redaction.redact("GET /locations?query=Example%20Street%2012&results=8 200 120ms")
        val transitous = redaction.redact("GET /api/v1/geocode?text=Sample%20Avenue%205&language=de 200 90ms")
        assertFalse(hafas.contains("Example%20Street"))
        assertFalse(transitous.contains("Sample%20Avenue"))
        assertTrue(hafas.contains("results=8"))
        assertTrue(transitous.contains("language=de"))
    }

    @Test
    fun reportHoldsEverySectionRedacted() {
        val input = DiagReportInput(
            time = 0L,
            note = "Map froze near 52.4999,13.3555 at Home",
            about = listOf("Version" to "0.1.2 (3)", "Device" to "Pixel"),
            settings = listOf("Sources" to "bvg, transitous"),
            crash = null,
            log = listOf("2026-11-17 01:02:03.456 W/Routing: hafas failed accessId=abc"),
        )
        val text = diagReportText(input, redaction)
        assertTrue(text.contains("## What happened\nMap froze near 52.49,13.35 at [private]"))
        assertTrue(text.contains("Version: 0.1.2 (3)"))
        assertTrue(text.contains("Sources: bvg, transitous"))
        assertTrue(text.contains("## Last crash\n(none)"))
        assertTrue(text.contains("## Log (1 events, oldest first)"))
        assertTrue(text.contains("accessId=[key]"))
        assertTrue(text.contains("01:02:03.456"))
    }
}
