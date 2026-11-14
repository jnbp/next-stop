package io.github.jnbp.nextstop.ui

import java.time.OffsetDateTime
import io.github.jnbp.nextstop.data.TransferComfort
import io.github.jnbp.nextstop.data.Tightness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

/** The tiles of the connecting departures: the arrival delta against the chosen one, and its label. */
class ChoiceTilesTest {
    private val chosen = OffsetDateTime.parse("2026-11-14T04:52:00+01:00")

    @Test
    fun laterAndSoonerArrivalsAreCountedInMinutes() {
        assertEquals(12L, choiceDelta(chosen.plusMinutes(12), chosen))
        assertEquals(-3L, choiceDelta(chosen.minusMinutes(3), chosen))
        assertEquals(0L, choiceDelta(chosen, chosen))
    }

    @Test
    fun anUnknownArrivalHasNoDelta() {
        assertNull(choiceDelta(null, chosen))
        assertNull(choiceDelta(chosen, null))
    }

    @Test
    fun theChipSaysPlusMinusOrNeither() {
        assertEquals("+12", choiceDeltaLabel(12))
        assertEquals("−3", choiceDeltaLabel(-3))
        assertEquals("±0", choiceDeltaLabel(0))
    }

    @Test
    fun theSetupExamplesFollowTheTransferLevel() {
        assertTrue(setupExampleShown(Tightness.Comfortable, TransferComfort.Relaxed))
        assertFalse(setupExampleShown(Tightness.Tight, TransferComfort.Relaxed))
        assertTrue(setupExampleShown(Tightness.Tight, TransferComfort.Normal))
        assertFalse(setupExampleShown(Tightness.Risky, TransferComfort.Normal))
        assertTrue(setupExampleShown(Tightness.Risky, TransferComfort.Risky))
        assertFalse(setupExampleShown(Tightness.VeryRisky, TransferComfort.Risky))
        assertTrue(setupExampleShown(Tightness.VeryRisky, TransferComfort.VeryRisky))
    }
}
