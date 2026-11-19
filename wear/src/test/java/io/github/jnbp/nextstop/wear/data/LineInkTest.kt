package io.github.jnbp.nextstop.wear.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** The ink on a line badge of the watch is the phone's (onLineColor there). */
class LineInkTest {
    private val dark = 0xFF16130AL
    private val white = 0xFFFFFFFFL

    @Test
    fun lightColoursGetDarkInk() {
        assertEquals(dark, onLineArgb(0xFFF7EC6E)) // a pale yellow
        assertEquals(dark, onLineArgb(0xFF97D700)) // FLX
    }

    @Test
    fun theGreenAndOchreLinesGetWhiteInkAsOnThePhone() {
        // Compose's luminance of 0xFF66AA22 is about 0.32, of 0xFFCD9C53 about 0.37: below 0.45.
        assertEquals(white, onLineArgb(0xFF66AA22)) // S8, S85
        assertEquals(white, onLineArgb(0xFFCD9C53)) // S45, S46, S47
        assertEquals(white, onLineArgb(0xFFED3C12)) // U2
        assertEquals(white, onLineArgb(0xFF95276E)) // bus
    }

    @Test
    fun berlinSubwayLinesHaveTheInkOfTheBvgSigns() {
        assertEquals(0xFF000000L, onLineArgb(lineArgb("U4", "subway")))
        assertEquals(0xFFFFC101L, lineArgb("U4", "subway"))
        assertEquals(0xFFF96815L, lineArgb("U9", "subway"))
        // White on the orange of the U9, which the rule of lightness alone does not promise.
        assertEquals(white, onLineArgb(0xFFF96815))
        for (line in listOf("U1", "U2", "U3", "U5", "U6", "U7", "U8", "U9")) {
            assertEquals(line, white, onLineArgb(lineArgb(line, "subway")))
        }
    }
}
