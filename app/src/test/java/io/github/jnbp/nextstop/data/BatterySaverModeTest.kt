package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The battery saver of the app: when it is active and how much it slows refreshes. */
class BatterySaverModeTest {
    @Test
    fun resolution() {
        assertFalse(batterySaverActive(BatterySaverMode.Off, true))
        assertFalse(batterySaverActive(BatterySaverMode.Off, false))
        assertTrue(batterySaverActive(BatterySaverMode.Auto, true))
        assertFalse(batterySaverActive(BatterySaverMode.Auto, false))
        assertTrue(batterySaverActive(BatterySaverMode.Always, false))
    }

    @Test
    fun storedNames() {
        assertEquals(BatterySaverMode.Auto, BatterySaverMode.fromStored(null))
        assertEquals(BatterySaverMode.Auto, BatterySaverMode.fromStored("nonsense"))
        assertEquals(BatterySaverMode.Always, BatterySaverMode.fromStored("Always"))
        assertEquals(BatterySaverMode.Off, BatterySaverMode.fromStored("Off"))
    }

    @Test
    fun pace() {
        assertEquals(30_000L, batterySaverPace(30_000L, false))
        assertEquals(60_000L, batterySaverPace(30_000L, true))
    }
}
