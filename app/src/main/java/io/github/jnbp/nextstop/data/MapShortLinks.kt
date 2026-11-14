package io.github.jnbp.nextstop.data

import java.io.IOException
import java.io.Reader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * Short links of Google Maps (maps.app.goo.gl/…, goo.gl/maps/…) name no place by themselves. To
 * read one, the app asks the host of the short link where it leads: it reads the redirect from
 * the header of the answer and does not follow it, so only the short link goes out, to the host
 * it names, and the Google Maps page behind it is never loaded. Where the host answers with a
 * page instead of a redirect, the first link of Google Maps in that page is taken. The link
 * found is read by parseNavLink.
 *
 * UNVERIFIED: written without a device or network to try it on; Google may change how its
 * short links answer. Every failure gives null and never an exception: the caller then opens an
 * empty search.
 */

// A short link leads to its map link in one step; a few more allow for a chain of hosts.
private const val SHORT_LINK_MAX_HOPS = 4

// How long one step may take to connect and to answer, in milliseconds: the user waits for it.
private const val SHORT_LINK_TIMEOUT_MILLIS = 6_000

// The most of a page that is read for a link in it, in characters (the link is near its top).
private const val SHORT_LINK_PAGE_CHARS = 64 * 1024

private const val SHORT_LINK_USER_AGENT = "NextStop/1.0"

// A link of Google Maps inside a page, up to the quote, space or tag that ends it.
private val MAPS_LINK_IN_PAGE = Regex("""https?://(?:www\.|maps\.)?google\.[a-z]{2,3}(?:\.[a-z]{2})?/maps[^"'\s<>\\]*""")

/**
 * The link of Google Maps a short [link] leads to; null when it cannot be found (no network,
 * an unknown answer, too many steps). A link that is no short link is given back as it is.
 */
suspend fun followShortMapLink(link: String): String? = withContext(Dispatchers.IO) {
    // A plain http link would be refused by Android (no cleartext); the hosts all speak https.
    var current = link.trim().replaceFirst(Regex("(?i)^http://"), "https://")
    for (hop in 1..SHORT_LINK_MAX_HOPS) {
        if (!isShortMapLink(current)) return@withContext current
        val next = shortLinkStep(current) ?: return@withContext null
        current = unwrapConsentLink(next)
    }
    current.takeIf { !isShortMapLink(it) }
}

/** One step of a short link: where its redirect points, or the map link in the page it answers with. */
private fun shortLinkStep(link: String): String? {
    val connection = try {
        URL(link).openConnection() as? HttpURLConnection
    } catch (e: IOException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    } ?: return null
    return try {
        connection.instanceFollowRedirects = false
        connection.connectTimeout = SHORT_LINK_TIMEOUT_MILLIS
        connection.readTimeout = SHORT_LINK_TIMEOUT_MILLIS
        connection.setRequestProperty("User-Agent", SHORT_LINK_USER_AGENT)
        val code = connection.responseCode
        val location = connection.getHeaderField("Location")
        when {
            code in 300..399 && !location.isNullOrBlank() -> URL(URL(link), location).toString()
            code == HttpURLConnection.HTTP_OK -> {
                val page = connection.inputStream.bufferedReader().use { readUpTo(it, SHORT_LINK_PAGE_CHARS) }
                mapsLinkInPage(page)
            }
            else -> null
        }
    } catch (e: IOException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    } finally {
        connection.disconnect()
    }
}

/** At most [limit] characters from [reader]. */
private fun readUpTo(reader: Reader, limit: Int): String {
    val buffer = CharArray(limit)
    var length = 0
    while (length < limit) {
        val read = reader.read(buffer, length, limit - length)
        if (read < 0) break
        length += read
    }
    return String(buffer, 0, length)
}

/** The first link of Google Maps in a page, with the escapes of HTML and JavaScript undone. */
internal fun mapsLinkInPage(page: String): String? {
    val plain = page
        .replace("&amp;", "&")
        .replace("\\u0026", "&")
        .replace("\\u003d", "=")
        .replace("\\/", "/")
    return MAPS_LINK_IN_PAGE.find(plain)?.value
}
