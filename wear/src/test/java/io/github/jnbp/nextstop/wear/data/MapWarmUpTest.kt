package io.github.jnbp.nextstop.wear.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapWarmUpTest {
    @Test
    fun theFirstWarmUpIsDue() {
        assertTrue(MapWarmUp.due(now = 5_000, last = 0))
    }

    @Test
    fun atMostOncePerGap() {
        val last = 1_000_000L
        assertFalse(MapWarmUp.due(now = last + 1, last = last))
        assertFalse(MapWarmUp.due(now = last + MapWarmUp.MIN_GAP_MILLIS - 1, last = last))
        assertTrue(MapWarmUp.due(now = last + MapWarmUp.MIN_GAP_MILLIS, last = last))
    }

    @Test
    fun aClockThatWentBackDoesNotBlockIt() {
        assertTrue(MapWarmUp.due(now = 10, last = 1_000_000))
    }
}
