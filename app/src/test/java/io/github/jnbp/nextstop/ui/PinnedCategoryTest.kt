package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which header of the settings page sticks to the top, and how far it is pushed up. */
class PinnedCategoryTest {
    // Two open categories with a header of 64 px: "map" from 100 to 600, "about" from 612 to 1000.
    private val bounds = mapOf(
        SettingsCategoryId.MAP to CategoryBounds(top = 100, headerHeight = 64, bottom = 600),
        SettingsCategoryId.ABOUT to CategoryBounds(top = 612, headerHeight = 64, bottom = 1000),
    )
    private val bothOpen = setOf(SettingsCategoryId.MAP, SettingsCategoryId.ABOUT)

    @Test
    fun nothingIsPinnedBeforeAHeaderReachesTheTop() {
        assertNull(pinnedCategory(bounds, bothOpen, scrolled = 0))
        // Exactly at the top the header in the page is where the copy would be.
        assertNull(pinnedCategory(bounds, bothOpen, scrolled = 100))
    }

    @Test
    fun theHeaderSticksWhileItsContentScrollsBy() {
        assertEquals(PinnedCategory(SettingsCategoryId.MAP, 0), pinnedCategory(bounds, bothOpen, scrolled = 101))
        assertEquals(PinnedCategory(SettingsCategoryId.MAP, 0), pinnedCategory(bounds, bothOpen, scrolled = 536))
    }

    @Test
    fun theEndOfTheContentPushesItUp() {
        assertEquals(PinnedCategory(SettingsCategoryId.MAP, -14), pinnedCategory(bounds, bothOpen, scrolled = 550))
        assertEquals(PinnedCategory(SettingsCategoryId.MAP, -63), pinnedCategory(bounds, bothOpen, scrolled = 599))
        // Past its content, in the gap before the next category: none.
        assertNull(pinnedCategory(bounds, bothOpen, scrolled = 605))
    }

    @Test
    fun theNextOpenCategoryTakesOver() {
        assertEquals(PinnedCategory(SettingsCategoryId.ABOUT, 0), pinnedCategory(bounds, bothOpen, scrolled = 700))
    }

    @Test
    fun foldedCategoriesAreNeverPinned() {
        assertNull(pinnedCategory(bounds, setOf(SettingsCategoryId.ABOUT), scrolled = 300))
        assertNull(pinnedCategory(bounds, emptySet(), scrolled = 700))
    }

    @Test
    fun unmeasuredOrEmptyCategoriesAreNeverPinned() {
        val odd = mapOf(
            SettingsCategoryId.MAP to CategoryBounds(top = 100, headerHeight = 0, bottom = 600),
            SettingsCategoryId.ABOUT to CategoryBounds(top = 612, headerHeight = 64, bottom = 676),
        )
        assertNull(pinnedCategory(odd, bothOpen, scrolled = 300))
        assertNull(pinnedCategory(odd, bothOpen, scrolled = 650))
    }
}
