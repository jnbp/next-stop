package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** The change level of the search on the way: Sprint for "Every minute counts", else the user's. */
class ShortcutLevelTest {
    @Test
    fun everyMinuteTakesSprint() {
        assertEquals(TransferComfort.VeryRisky, shortcutComfort(everyMinute = true, userLevel = TransferComfort.Relaxed))
        assertEquals(TransferComfort.VeryRisky, shortcutComfort(everyMinute = true, userLevel = TransferComfort.Normal))
    }

    @Test
    fun alertAloneKeepsTheUsersLevel() {
        assertEquals(TransferComfort.Normal, shortcutComfort(everyMinute = false, userLevel = TransferComfort.Normal))
        assertEquals(TransferComfort.Relaxed, shortcutComfort(everyMinute = false, userLevel = TransferComfort.Relaxed))
    }
}
