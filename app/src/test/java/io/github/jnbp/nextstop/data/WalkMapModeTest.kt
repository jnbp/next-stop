package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Minikarte für Fußwege" in three ways (#202): the stored value, its migration and the next walk. */
class WalkMapModeTest {
    @Test
    fun theOldSwitchMigrates() {
        // "immer aufgeklappt" on: all unfolded, as before.
        assertEquals(PickerMode.AllOpen, walkMapModeOf(stored = null, oldAlwaysOpen = true))
        // Off, or never set: the new default, only the next one.
        assertEquals(PickerMode.NextOpen, walkMapModeOf(stored = null, oldAlwaysOpen = false))
        assertEquals(PickerMode.NextOpen, walkMapModeOf(stored = null, oldAlwaysOpen = null))
        // A choice made with the new setting wins over the old switch.
        assertEquals(PickerMode.Folded, walkMapModeOf(stored = "Folded", oldAlwaysOpen = true))
        assertEquals(PickerMode.NextOpen, walkMapModeOf(stored = "Sideways", oldAlwaysOpen = null))
    }

    @Test
    fun whichMapStartsUnfolded() {
        assertTrue(walkMapStartsOpen(PickerMode.AllOpen, next = false))
        assertTrue(walkMapStartsOpen(PickerMode.AllOpen, next = null))
        assertFalse(walkMapStartsOpen(PickerMode.Folded, next = true))
        assertTrue(walkMapStartsOpen(PickerMode.NextOpen, next = true))
        assertFalse(walkMapStartsOpen(PickerMode.NextOpen, next = false))
        // A page that does not say which walk is next leaves it folded.
        assertFalse(walkMapStartsOpen(PickerMode.NextOpen, next = null))
    }

    private data class Part(val key: String, val map: Boolean, val over: Boolean = false)

    @Test
    fun theNextWalkIsTheFirstWithAMapNotYetOver() {
        val parts = listOf(
            Part("walk0", map = true, over = true),
            Part("ride1", map = false),
            Part("change2", map = false),
            Part("change3", map = true),
            Part("walk5", map = true),
        )
        assertEquals("change3", nextWalkKey(parts, key = { it.key }, hasMap = { it.map }, over = { it.over }))
        // On the detail page nothing is over: the first walk with a map.
        assertEquals("walk0", nextWalkKey(parts, key = { it.key }, hasMap = { it.map }))
        assertNull(nextWalkKey(parts.map { it.copy(map = false) }, key = { it.key }, hasMap = { it.map }))
    }
}
