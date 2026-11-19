package io.github.jnbp.nextstop.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The contrast helper behind the readable colours of the schemes (see readableArgb). */
class ContrastTest {
    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()

    @Test
    fun ratiosAsWcagHasThem() {
        assertEquals(21f, contrastRatio(black, white), 0.01f)
        assertEquals(1f, contrastRatio(white, white), 0.001f)
        // The order does not matter.
        assertEquals(contrastRatio(0xFF1B4FD8.toInt(), white), contrastRatio(white, 0xFF1B4FD8.toInt()), 0.0001f)
        // Mid grey (#777777) on white: about 4.48, just short of AA.
        assertEquals(4.48f, contrastRatio(0xFF777777.toInt(), white), 0.02f)
    }

    @Test
    fun readableInkIsKept() {
        val ink = 0xFF171A21.toInt()
        assertEquals(ink, readableArgb(white, ink))
    }

    @Test
    fun darkOnDarkIsLightened() {
        // A dark container of a dark wallpaper scheme under dark ink.
        val ground = 0xFF2B2F45.toInt()
        val ink = 0xFF3A3F5C.toInt()
        val shown = readableArgb(ground, ink)
        assertTrue(contrastRatio(ground, shown) >= TEXT_CONTRAST)
        assertTrue(relativeLuminance(shown) > relativeLuminance(ink))
    }

    @Test
    fun whiteOnWhiteIsDarkened() {
        // A pale container of a light wallpaper scheme under white text.
        val ground = 0xFFE8EAFF.toInt()
        val shown = readableArgb(ground, white)
        assertTrue(contrastRatio(ground, shown) >= TEXT_CONTRAST)
        assertTrue(relativeLuminance(shown) < 0.2)
    }

    @Test
    fun marksNeedLess() {
        val accent = 0xFF8C8CFF.toInt()
        val mark = readableArgb(white, accent, MARK_CONTRAST)
        assertTrue(contrastRatio(white, mark) >= MARK_CONTRAST)
        assertTrue(contrastRatio(white, mark) < TEXT_CONTRAST + 1f)
    }

    @Test
    fun allGroundsAreMet() {
        val grounds = intArrayOf(white, 0xFFECEEF4.toInt(), 0xFFDBE3FF.toInt())
        val shown = readableOnAll(grounds, 0xFF9AA0B0.toInt())
        for (ground in grounds) assertTrue(contrastRatio(ground, shown) >= TEXT_CONTRAST)
    }
}
