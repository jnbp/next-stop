package io.github.jnbp.nextstop.wear.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapImageRetryTest {
    @Test
    fun theFirstFailureIsRetriedQuietly() {
        assertFalse(MapImageRetry.showProblem(MapImageStatus.NoAnswer, failures = 1))
        assertFalse(MapImageRetry.showProblem(MapImageStatus.NotDrawn, failures = 1))
        assertEquals(MapImageRetry.QUIET_PAUSE_MILLIS, MapImageRetry.pauseAfter(1))
    }

    @Test
    fun theSecondFailureIsShown() {
        assertTrue(MapImageRetry.showProblem(MapImageStatus.NoAnswer, failures = 2))
        assertTrue(MapImageRetry.showProblem(MapImageStatus.NotDrawn, failures = 3))
        assertEquals(MapImageRetry.PAUSE_MILLIS, MapImageRetry.pauseAfter(2))
    }

    @Test
    fun noPhoneIsSaidAtOnceAndLoadingIsNoProblem() {
        assertTrue(MapImageRetry.showProblem(MapImageStatus.NoPhone, failures = 1))
        assertFalse(MapImageRetry.showProblem(MapImageStatus.Waiting, failures = 5))
        assertFalse(MapImageRetry.showProblem(MapImageStatus.Ready, failures = 5))
    }

    @Test
    fun prefetchIsTriedAgainAFewTimes() {
        assertFalse(MapImageRetry.prefetchAgain(0))
        assertTrue(MapImageRetry.prefetchAgain(1))
        assertTrue(MapImageRetry.prefetchAgain(MapImageRetry.PREFETCH_RETRIES))
        assertFalse(MapImageRetry.prefetchAgain(MapImageRetry.PREFETCH_RETRIES + 1))
    }
}
