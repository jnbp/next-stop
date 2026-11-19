package io.github.jnbp.nextstop.wear.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Button labels that shrink rather than split a word (#179). */
class LabelFitTest {
    @Test
    fun aLabelThatFitsStaysAsItIs() {
        assertEquals(1f, wordFitScale(widestWord = 80f, available = 100f))
        assertEquals(1f, wordFitScale(widestWord = 100f, available = 100f))
        // Not measured yet: nothing to shrink.
        assertEquals(1f, wordFitScale(widestWord = 0f, available = 100f))
        assertEquals(1f, wordFitScale(widestWord = 80f, available = 0f))
    }

    @Test
    fun aWordTooWideShrinksTheLabelUntilItFits() {
        // "Übersicht" a little too wide for the button low on the round screen.
        val scale = wordFitScale(widestWord = 110f, available = 100f)!!
        assertTrue(scale < 1f && 110f * scale <= 100f)
        assertTrue(scale >= MIN_LABEL_SCALE)
    }

    @Test
    fun farTooWideEndsInAnEllipsisNotASplitWord() {
        assertNull(wordFitScale(widestWord = 200f, available = 100f))
    }

    @Test
    fun wordsAreWhereALabelMayBreak() {
        assertEquals(listOf("Fahrt", "beenden"), labelWords("  Fahrt   beenden "))
        assertEquals(listOf("Übersicht"), labelWords("Übersicht"))
        assertEquals(emptyList<String>(), labelWords(" "))
    }

    @Test
    fun piecesFitOnOneLineOnlyWithTheirGaps() {
        // "Mitte einsteigen" and the sun after it (#176).
        assertTrue(fitsOnOneLine(listOf(80f, 20f), gap = 6f, available = 106f))
        assertTrue(!fitsOnOneLine(listOf(80f, 20f), gap = 6f, available = 105f))
        assertTrue(!fitsOnOneLine(listOf(10f), gap = 6f, available = 0f))
    }
}
