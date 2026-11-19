package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** "Anschluss unterwegs wählen" as one setting: off, or how the pickers show (#201). */
class ConnectionPickerSettingTest {
    @Test
    fun offIsTheFirstChoiceWhateverTheMode() {
        for (mode in PickerMode.entries) assertEquals(0, PickerMode.choiceIndex(on = false, mode = mode))
        assertNull(PickerMode.ofChoice(0))
    }

    @Test
    fun theModesFollowInTheirShownOrder() {
        assertEquals(listOf(PickerMode.Folded, PickerMode.NextOpen, PickerMode.AllOpen), PickerMode.shownOrder)
        assertEquals(1, PickerMode.choiceIndex(on = true, mode = PickerMode.Folded))
        assertEquals(2, PickerMode.choiceIndex(on = true, mode = PickerMode.NextOpen))
        assertEquals(3, PickerMode.choiceIndex(on = true, mode = PickerMode.AllOpen))
        for (mode in PickerMode.entries) assertEquals(mode, PickerMode.ofChoice(PickerMode.choiceIndex(true, mode)))
        assertNull(PickerMode.ofChoice(4))
    }

    @Test
    fun storedNamesKeepWorking() {
        for (mode in PickerMode.entries) assertEquals(mode, PickerMode.fromStored(mode.name))
    }

    @Test
    fun theDefaultOfBothIsOnlyTheNextOneUnfolded() {
        // #203: the pickers and the walk maps, where nothing (or nothing known) is stored.
        assertEquals(PickerMode.NextOpen, PickerMode.fromStored(null))
        assertEquals(PickerMode.NextOpen, PickerMode.fromStored("Unknown"))
        assertEquals(PickerMode.NextOpen, walkMapModeOf(null, null))
    }
}
