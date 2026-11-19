package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The levels of the battery saver ("Sparen", "Extrem") and what they mean for refreshes. */
class BatterySaverLevelTest {
    @Test
    fun storedLevelFallsBackToSave() {
        // Before there were levels nothing is stored: the saver keeps doing what it did.
        assertEquals(BatterySaverLevel.Save, BatterySaverLevel.fromStored(null))
        assertEquals(BatterySaverLevel.Save, BatterySaverLevel.fromStored("nonsense"))
        assertEquals(BatterySaverLevel.Extreme, BatterySaverLevel.fromStored("Extreme"))
    }

    @Test
    fun extremeOnlyWhileActive() {
        assertFalse(batterySaverExtreme(BatterySaverMode.Off, true, BatterySaverLevel.Extreme))
        assertFalse(batterySaverExtreme(BatterySaverMode.Auto, false, BatterySaverLevel.Extreme))
        assertTrue(batterySaverExtreme(BatterySaverMode.Auto, true, BatterySaverLevel.Extreme))
        assertTrue(batterySaverExtreme(BatterySaverMode.Always, false, BatterySaverLevel.Extreme))
        assertFalse(batterySaverExtreme(BatterySaverMode.Always, false, BatterySaverLevel.Save))
    }

    @Test
    fun extremeWaitsLongest() {
        assertEquals(30_000L, batterySaverPace(30_000L, active = false, extreme = false))
        assertEquals(60_000L, batterySaverPace(30_000L, active = true, extreme = false))
        assertEquals(120_000L, batterySaverPace(30_000L, active = true, extreme = true))
    }
}
