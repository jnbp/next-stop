package io.github.jnbp.nextstop.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchMapBudgetTest {
    @Test
    fun theRequestEndsBeforeTheWatchGivesUp() {
        assertTrue(WatchMapBudget.REQUEST_MILLIS + WatchMapBudget.SEND_MARGIN_MILLIS <= WatchMapBudget.WATCH_WAIT_MILLIS)
        // A cold quick picture and two cold tries of the full one fit (the last try has the rest).
        val afterQuick = WatchMapBudget.REQUEST_MILLIS - WatchMapBudget.quickMillis(cold = true, leftMillis = WatchMapBudget.REQUEST_MILLIS)
        assertTrue(afterQuick >= WatchMapBudget.FULL_RESERVE_MILLIS)
        assertTrue(afterQuick - WatchMapBudget.tryMillis(cold = true, attempt = 1, attempts = 2, leftMillis = afterQuick) > 0)
    }

    @Test
    fun aColdQuickPictureWaitsLongerThanAWarmOne() {
        val cold = WatchMapBudget.quickMillis(cold = true, leftMillis = 35_000)
        val warm = WatchMapBudget.quickMillis(cold = false, leftMillis = 35_000)
        assertEquals(WatchMapBudget.QUICK_COLD_MILLIS, cold)
        assertEquals(WatchMapBudget.QUICK_WARM_MILLIS, warm)
        assertTrue(cold > warm)
    }

    @Test
    fun theQuickPictureLeavesRoomForTheFullOne() {
        assertEquals(4_000L, WatchMapBudget.quickMillis(cold = true, leftMillis = WatchMapBudget.FULL_RESERVE_MILLIS + 4_000))
        assertEquals(0L, WatchMapBudget.quickMillis(cold = true, leftMillis = WatchMapBudget.FULL_RESERVE_MILLIS))
        assertEquals(0L, WatchMapBudget.quickMillis(cold = false, leftMillis = 1_000))
    }

    @Test
    fun theLastTryHasAllThatIsLeft() {
        assertEquals(21_000L, WatchMapBudget.tryMillis(cold = true, attempt = 2, attempts = 2, leftMillis = 21_000))
        assertEquals(WatchMapBudget.TRY_COLD_MILLIS, WatchMapBudget.tryMillis(cold = true, attempt = 1, attempts = 2, leftMillis = 30_000))
        assertEquals(WatchMapBudget.TRY_WARM_MILLIS, WatchMapBudget.tryMillis(cold = false, attempt = 1, attempts = 2, leftMillis = 30_000))
        // Never more than is left, never negative.
        assertEquals(3_000L, WatchMapBudget.tryMillis(cold = true, attempt = 1, attempts = 2, leftMillis = 3_000))
        assertEquals(0L, WatchMapBudget.tryMillis(cold = false, attempt = 2, attempts = 2, leftMillis = -5))
    }

    @Test
    fun warmUpIsRateLimited() {
        assertTrue(WatchMapBudget.warmUpDue(now = 100_000, last = 0, warm = false))
        assertFalse(WatchMapBudget.warmUpDue(now = 100_000, last = 0, warm = true))
        assertFalse(WatchMapBudget.warmUpDue(now = 100_000, last = 100_000 - WatchMapBudget.WARM_UP_MIN_GAP_MILLIS + 1, warm = false))
        assertTrue(WatchMapBudget.warmUpDue(now = 100_000, last = 100_000 - WatchMapBudget.WARM_UP_MIN_GAP_MILLIS, warm = false))
        // A clock that went back does not block warm-ups for good.
        assertTrue(WatchMapBudget.warmUpDue(now = 1_000, last = 100_000, warm = false))
    }
}
