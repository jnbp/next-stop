package io.github.jnbp.nextstop.ui

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Inflater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The addresses of shared links: without a setting they lead to the project website, a setting of
 * the user's own wins, and the fragment has the form the pages read ("#v=2&d=<base64url of the
 * raw-deflated JSON>", readFragment in web/live/app.js and readRoute in web/route/app.js).
 */
class SiteLinkTest {
    private val json = """{"legs":[],"dest":{"name":"S+U Alexanderplatz"},"t":1794640320}"""

    @Test
    fun emptySettingIsTheProjectWebsite() {
        assertEquals("https://nextstop.bapo.me", DEFAULT_SITE_ADDRESS)
        assertEquals(DEFAULT_SITE_ADDRESS, siteAddress(""))
        assertEquals(DEFAULT_SITE_ADDRESS, siteAddress("   "))
        assertEquals("https://pages.example.org/", siteAddress(" https://pages.example.org/ "))
    }

    @Test
    fun defaultLinksLeadToLiveAndRoutePages() {
        val encoded = base64Url(deflateRaw(json))
        assertEquals("https://nextstop.bapo.me/live/#v=2&d=$encoded", siteLink(siteAddress(""), "live", json))
        assertEquals("https://nextstop.bapo.me/route/#v=2&d=$encoded", siteLink(siteAddress(""), "route", json))
    }

    @Test
    fun ownAddressWinsInEveryWrittenForm() {
        val expected = "https://pages.example.org/route/#v=2&d=" + base64Url(deflateRaw(json))
        listOf(
            "pages.example.org",
            "https://pages.example.org",
            "https://pages.example.org/",
            "https://pages.example.org/live/",
            "https://pages.example.org/route",
        ).forEach { typed -> assertEquals(typed, expected, siteLink(siteAddress(typed), "route", json)) }
        assertEquals("https://example.org/next/live/#v=2&d=" + base64Url(deflateRaw(json)), siteLink("example.org/next", "live", json))
    }

    @Test
    fun noWebAddressMakesNoLink() {
        assertNull(siteLink("not a host", "live", json))
        assertNull(siteLink("next stop", "route", json))
    }

    @Test
    fun fragmentReadsBackAsThePagesDo() {
        val link = siteLink(siteAddress(""), "live", json)!!
        // As URLSearchParams on the fragment without its "#".
        val params = link.substringAfter('#').split('&').associate { it.substringBefore('=') to it.substringAfter('=') }
        assertEquals("2", params["v"])
        assertEquals(json, inflateRaw(Base64.getUrlDecoder().decode(params.getValue("d"))))
    }

    private fun inflateRaw(bytes: ByteArray): String {
        val inflater = Inflater(true)
        try {
            inflater.setInput(bytes)
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                out.write(buffer, 0, n)
            }
            return out.toString(Charsets.UTF_8.name())
        } finally {
            inflater.end()
        }
    }
}
