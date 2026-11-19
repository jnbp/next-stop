package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A failed request is asked again soon at first, then less often (#59). */
class RetryBackoffTest {
    @Test
    fun theWaitsGrowAndThenStay() {
        assertEquals(5_000L, screenRetryWaitMillis(0))
        assertEquals(15_000L, screenRetryWaitMillis(1))
        assertEquals(30_000L, screenRetryWaitMillis(2))
        assertEquals(60_000L, screenRetryWaitMillis(3))
        assertEquals(60_000L, screenRetryWaitMillis(50))
        for (attempt in 0 until 10) assertTrue(screenRetryWaitMillis(attempt + 1) >= screenRetryWaitMillis(attempt))
    }

    @Test
    fun aNegativeAttemptIsTheFirst() {
        assertEquals(5_000L, screenRetryWaitMillis(-1))
    }
}
