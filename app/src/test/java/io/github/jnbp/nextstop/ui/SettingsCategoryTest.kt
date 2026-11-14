package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The open categories of the settings page, kept as ids joined by commas. */
class SettingsCategoryTest {
    @Test
    fun allCategoriesStartFolded() {
        assertFalse(isCategoryOpen("", SettingsCategoryId.MAP))
    }

    @Test
    fun aTapOpensAndASecondOneFolds() {
        val open = toggleCategory("", SettingsCategoryId.MAP)
        assertTrue(isCategoryOpen(open, SettingsCategoryId.MAP))
        assertFalse(isCategoryOpen(open, SettingsCategoryId.ABOUT))
        assertEquals("", toggleCategory(open, SettingsCategoryId.MAP))
    }

    @Test
    fun severalCategoriesStayOpenTogether() {
        val open = toggleCategory(toggleCategory("", SettingsCategoryId.MAP), SettingsCategoryId.ABOUT)
        assertTrue(isCategoryOpen(open, SettingsCategoryId.MAP))
        assertTrue(isCategoryOpen(open, SettingsCategoryId.ABOUT))
        assertEquals(SettingsCategoryId.ABOUT, toggleCategory(open, SettingsCategoryId.MAP))
    }

    @Test
    fun aDeepLinkOpensItsCategoryWithoutFoldingIt() {
        val open = withCategoryOpen("", SettingsCategoryId.ON_THE_WAY)
        assertTrue(isCategoryOpen(open, SettingsCategoryId.ON_THE_WAY))
        assertEquals(open, withCategoryOpen(open, SettingsCategoryId.ON_THE_WAY))
    }

    @Test
    fun noIdIsAPrefixMatch() {
        // "map" must not count as open because another id merely contains it.
        assertFalse(isCategoryOpen("map_extra", SettingsCategoryId.MAP))
    }

    @Test
    fun addressesAreShownWithoutScheme() {
        assertEquals("github.com/jnbp/next-stop", shownAddress("https://github.com/jnbp/next-stop"))
        assertEquals("nextstop.bapo.me/datenschutz", shownAddress("https://nextstop.bapo.me/datenschutz/"))
    }
}
