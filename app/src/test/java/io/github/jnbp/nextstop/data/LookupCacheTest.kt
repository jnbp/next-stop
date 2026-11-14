package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The memo of repeated lookups (the change positions): once per key, null kept, cleared with the data. */
class LookupCacheTest {
    // The shape of a change position query: a data class, equal when all fields are.
    private data class Query(val station: String, val fromLine: String, val toLine: String)

    @Test
    fun theSameQueryIsComputedOnce() {
        val cache = LookupCache<Query, String>(capacity = 16)
        var calls = 0
        repeat(1_500) {
            // A new but equal object each time, as a recomposition builds it.
            val answer = cache.getOrCompute(Query("Friedrichstraße", "S1", "S3")) { calls++; "front" }
            assertEquals("front", answer)
        }
        assertEquals(1, calls)
    }

    @Test
    fun aMissingRecordIsKeptToo() {
        val cache = LookupCache<Query, String>(capacity = 16)
        var calls = 0
        repeat(10) {
            assertNull(cache.getOrCompute(Query("Alexanderplatz", "U2", "U8")) { calls++; null })
        }
        assertEquals(1, calls)
        assertEquals(1, cache.size)
    }

    @Test
    fun clearingAsksAgain() {
        val cache = LookupCache<Query, String>(capacity = 16)
        var calls = 0
        val query = Query("Hauptbahnhof", "S5", "S7")
        cache.getOrCompute(query) { calls++; null }
        cache.clear()
        assertEquals("rear", cache.getOrCompute(query) { calls++; "rear" })
        assertEquals(2, calls)
    }

    @Test
    fun anAnswerFromBeforeAClearIsNotKept() {
        val cache = LookupCache<Query, String>(capacity = 16)
        val query = Query("Zoologischer Garten", "S3", "U9")
        // The data changes while the old answer is computed.
        assertEquals("old", cache.getOrCompute(query) { cache.clear(); "old" })
        assertEquals("new", cache.getOrCompute(query) { "new" })
    }

    @Test
    fun theLeastRecentlyUsedGoFirst() {
        val cache = LookupCache<Query, String>(capacity = 2)
        var calls = 0
        val a = Query("A", "S1", "S2")
        val b = Query("B", "S1", "S2")
        val c = Query("C", "S1", "S2")
        cache.getOrCompute(a) { calls++; "a" }
        cache.getOrCompute(b) { calls++; "b" }
        cache.getOrCompute(a) { calls++; "a" }
        cache.getOrCompute(c) { calls++; "c" }
        assertEquals(3, calls)
        assertEquals(2, cache.size)
        // a was used after b, so b went.
        cache.getOrCompute(a) { calls++; "a" }
        assertEquals(3, calls)
        cache.getOrCompute(b) { calls++; "b" }
        assertEquals(4, calls)
    }
}
