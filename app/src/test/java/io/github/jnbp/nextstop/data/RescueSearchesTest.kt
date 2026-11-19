package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RescueSearchesTest {
    private val key = RescueSearches.changeKey("j1", 2)

    @Test
    fun oneBackgroundSearchPerBrokenChange() {
        val searches = RescueSearches<List<String>>()
        assertTrue(searches.mayBackgroundSearch(key, 0L, extremeSaver = false))
        searches.put(key, listOf("S7 16:06"))
        // Found once: never searched again in the background, the options are reused.
        assertFalse(searches.mayBackgroundSearch(key, 10 * 60_000L, extremeSaver = false))
        assertEquals(listOf("S7 16:06"), searches.options(key))
        // Another change of the trip is a search of its own.
        assertTrue(searches.mayBackgroundSearch(RescueSearches.changeKey("j1", 4), 0L, extremeSaver = false))
    }

    @Test
    fun aFailureIsTriedAgainAfterTwoMinutes() {
        val searches = RescueSearches<List<String>>()
        searches.failed(key, 1_000L)
        assertFalse(searches.mayBackgroundSearch(key, 1_000L + RescueSearches.RETRY_MILLIS - 1, extremeSaver = false))
        assertTrue(searches.mayBackgroundSearch(key, 1_000L + RescueSearches.RETRY_MILLIS, extremeSaver = false))
    }

    @Test
    fun foundByTheCompanionNoBackgroundSearch() {
        val searches = RescueSearches<List<String>>()
        searches.put(key, emptyList())
        assertFalse(searches.mayBackgroundSearch(key, 0L, extremeSaver = false))
    }

    @Test
    fun neverWithTheExtremeSaver() {
        val searches = RescueSearches<List<String>>()
        assertFalse(searches.mayBackgroundSearch(key, 0L, extremeSaver = true))
    }

    @Test
    fun clearForgets() {
        val searches = RescueSearches<List<String>>()
        searches.put(key, listOf("x"))
        searches.clear()
        assertNull(searches.options(key))
        assertTrue(searches.mayBackgroundSearch(key, 0L, extremeSaver = false))
    }
}
