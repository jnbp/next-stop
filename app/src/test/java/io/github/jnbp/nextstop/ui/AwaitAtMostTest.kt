package io.github.jnbp.nextstop.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** #261: a lookup that blocks its thread is given up on time, so the loader beside it goes. */
class AwaitAtMostTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Test
    fun aQuickAnswerComesAsItIs() = runBlocking {
        assertEquals("Musterweg 1", awaitAtMost(1_000L, scope) { "Musterweg 1" })
    }

    @Test
    fun aBlockingLookupIsGivenUpAtTheLimit() = runBlocking {
        val start = System.nanoTime()
        // Blocks its thread like the geocoder, far beyond the limit.
        val result = awaitAtMost(100L, scope) {
            Thread.sleep(2_000L)
            "late"
        }
        val waited = (System.nanoTime() - start) / 1_000_000
        assertNull(result)
        assertTrue("waited $waited ms", waited < 1_000L)
    }

    @Test
    fun aCallerCancelledCancelsTheWork() = runBlocking {
        val cancelled = CompletableDeferred<Boolean>()
        val waiting = launch {
            awaitAtMost(10_000L, scope) {
                try {
                    awaitCancellation()
                } finally {
                    cancelled.complete(true)
                }
            }
        }
        delay(50L)
        waiting.cancel()
        assertTrue(withTimeout(1_000L) { cancelled.await() })
    }
}
