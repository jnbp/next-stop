package io.github.jnbp.nextstop.ui.companion

import org.junit.Assert.assertEquals
import org.junit.Test

/** #259: the bolt of "Every minute counts" at the top of the Live-Fahrt. */
class EveryMinuteMarkRuleTest {
    @Test
    fun offShowsNothing() {
        assertEquals(EveryMinuteMarkLook.Hidden, everyMinuteMarkLook(on = false, onSinceMillis = 0L, nowMillis = 5_000L))
        assertEquals(EveryMinuteMarkLook.Hidden, everyMinuteMarkLook(on = false, onSinceMillis = 1_000L, nowMillis = 1_500L))
    }

    @Test
    fun switchedOnItPopsInWithItsWordThenStaysAsTheBolt() {
        // On, but the moment not taken yet (the first frame): the word, never nothing.
        assertEquals(EveryMinuteMarkLook.Word, everyMinuteMarkLook(on = true, onSinceMillis = 0L, nowMillis = 0L))
        assertEquals(EveryMinuteMarkLook.Word, everyMinuteMarkLook(on = true, onSinceMillis = 10_000L, nowMillis = 10_000L))
        assertEquals(EveryMinuteMarkLook.Word, everyMinuteMarkLook(on = true, onSinceMillis = 10_000L, nowMillis = 10_000L + EVERY_MINUTE_WORD_MILLIS - 1))
        assertEquals(EveryMinuteMarkLook.Bolt, everyMinuteMarkLook(on = true, onSinceMillis = 10_000L, nowMillis = 10_000L + EVERY_MINUTE_WORD_MILLIS))
        assertEquals(EveryMinuteMarkLook.Bolt, everyMinuteMarkLook(on = true, onSinceMillis = 10_000L, nowMillis = 600_000L))
    }
}
