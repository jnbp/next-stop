package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The short-lived answers of Transitous (#72): the map asks for its vehicles with the time in the
 * address, so no answer was ever asked for again, yet the last 32 stayed in memory (each the
 * vehicles of a whole city with their ways, megabytes of text). The cache now forgets answers
 * once they are too old to be used and keeps no more than a budget of text.
 */
class ResponseCacheTest {
    private fun body(chars: Int) = "x".repeat(chars)

    @Test
    fun aFreshAnswerIsGivenAgain() {
        val cache = ResponseCache(maxEntries = 8, maxChars = 1_000, maxEntryChars = 500)
        cache.put("/a", "answer", keepMillis = 1_000, now = 0)
        assertEquals("answer", cache.get("/a", keepMillis = 1_000, now = 999))
        assertNull(cache.get("/a", keepMillis = 1_000, now = 1_000))
    }

    @Test
    fun answersNeverAskedAgainDoNotPileUp() {
        // The vehicles of the map, every 10 s with the time in the address, each 3 MB of text.
        val cache = ResponseCache(maxEntries = 32, maxChars = 4_000_000, maxEntryChars = 1_500_000)
        for (i in 0 until 200) cache.put("/map?startTime=$i", body(300_000), keepMillis = 15_000, now = i * 10_000L)
        // Only those still fresh (15 s) stay: one or two, not the last 32.
        assertTrue("kept ${cache.size}", cache.size <= 2)
        assertTrue("kept ${cache.chars} characters", cache.chars <= 600_000)
    }

    @Test
    fun theTextKeptStaysWithinItsBudget() {
        val cache = ResponseCache(maxEntries = 32, maxChars = 1_000_000, maxEntryChars = 400_000)
        for (i in 0 until 32) cache.put("/plan?$i", body(300_000), keepMillis = 60_000, now = 0)
        assertTrue("kept ${cache.chars} characters", cache.chars <= 1_000_000)
        // The newest stays.
        assertEquals(300_000, cache.get("/plan?31", keepMillis = 60_000, now = 1)?.length)
        assertNull(cache.get("/plan?0", keepMillis = 60_000, now = 1))
    }

    @Test
    fun anAnswerTooLargeIsNotKept() {
        val cache = ResponseCache(maxEntries = 8, maxChars = 1_000, maxEntryChars = 500)
        cache.put("/big", body(501), keepMillis = 60_000, now = 0)
        assertNull(cache.get("/big", keepMillis = 60_000, now = 1))
        assertEquals(0L, cache.chars)
    }

    @Test
    fun aRepeatedAddressReplacesItsAnswer() {
        val cache = ResponseCache(maxEntries = 8, maxChars = 1_000, maxEntryChars = 500)
        cache.put("/a", body(400), keepMillis = 60_000, now = 0)
        cache.put("/a", body(300), keepMillis = 60_000, now = 1)
        assertEquals(1, cache.size)
        assertEquals(300L, cache.chars)
    }
}
