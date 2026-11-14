package io.github.jnbp.nextstop.wear.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RoundScreenTest {
    @Test
    fun chordAndBottomAgree() {
        // A round screen of 192 dp: 14 dp above its lowest point it is about 100 dp wide.
        assertEquals(99.8f, RoundScreen.chord(192f, 14f), 0.1f)
        assertEquals(14f, RoundScreen.bottomFor(192f, RoundScreen.chord(192f, 14f)), 0.01f)
        assertEquals(192f, RoundScreen.chord(192f, 96f), 0.001f)
        assertEquals(0f, RoundScreen.chord(192f, 0f), 0.001f)
    }
}
