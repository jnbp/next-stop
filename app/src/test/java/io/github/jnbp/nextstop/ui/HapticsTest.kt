package io.github.jnbp.nextstop.ui

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import org.junit.Assert.assertEquals
import org.junit.Test

/** The haptics are felt: the subtle types of Compose become clear clicks, only drags stay light. */
class HapticsTest {
    @Test
    fun controlsGetAClearClick() {
        assertEquals(HapticStrength.Click, hapticStrength(HapticFeedbackType.SegmentTick))
        assertEquals(HapticStrength.Click, hapticStrength(HapticFeedbackType.ToggleOff))
        assertEquals(HapticStrength.Click, hapticStrength(HapticFeedbackType.VirtualKey))
    }

    @Test
    fun aSwitchTurnedOnAndADoneThingConfirm() {
        assertEquals(HapticStrength.Confirm, hapticStrength(HapticFeedbackType.ToggleOn))
        assertEquals(HapticStrength.Confirm, hapticStrength(HapticFeedbackType.Confirm))
        assertEquals(HapticStrength.Reject, hapticStrength(HapticFeedbackType.Reject))
    }

    @Test
    fun aLongPressIsFirmAndFrequentTicksStayLight() {
        assertEquals(HapticStrength.Heavy, hapticStrength(HapticFeedbackType.LongPress))
        assertEquals(HapticStrength.Tick, hapticStrength(HapticFeedbackType.SegmentFrequentTick))
        assertEquals(HapticStrength.Tick, hapticStrength(HapticFeedbackType.TextHandleMove))
    }
}
