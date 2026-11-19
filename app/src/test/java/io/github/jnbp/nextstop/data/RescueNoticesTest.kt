package io.github.jnbp.nextstop.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** #98: a broken change notifies once, not with every refresh of its offers. */
class RescueNoticesTest {
    @Test
    fun oncePerBrokenChange() {
        val notices = RescueNotices()
        assertTrue(notices.firstTime("trip", 2))
        assertFalse(notices.firstTime("trip", 2))
        assertTrue(notices.firstTime("trip", 4))
        // After a switch the journey is another one: its broken change is news again.
        assertTrue(notices.firstTime("other", 2))
        notices.clear()
        assertTrue(notices.firstTime("trip", 2))
    }
}
