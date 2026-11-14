package io.github.jnbp.nextstop.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The colours of lines from the providers and from OpenStreetMap, told apart by place, and the
 * matching of the providers' stops outside the VBB to the packs (DHIDs, names with the town).
 */
class LineColorsTest {
    private val mannheim = 49.4818 to 8.4733
    private val karlsruhe = 49.0094 to 8.4044

    @After
    fun tearDown() = LineColors.clear()

    @Test
    fun hexColoursParse() {
        assertEquals(0xFFE3000B.toInt(), LineColors.parseHex("e3000b"))
        assertEquals(0xFFE3000B.toInt(), LineColors.parseHex("#E3000B"))
        assertEquals(0xFFEE0000.toInt(), LineColors.parseHex("#e00"))
        assertNull(LineColors.parseHex(""))
        assertNull(LineColors.parseHex("red"))
        assertNull(LineColors.parseHex(null))
    }

    @Test
    fun lineNamesInOneForm() {
        assertEquals("4A", LineColors.nameKey("Tram 4a"))
        assertEquals("S1", LineColors.nameKey("S 1"))
        assertEquals("SB16", LineColors.nameKey("SB16"))
        assertNull(LineColors.nameKey("?"))
        assertNull(LineColors.kindOf("regional"))
    }

    @Test
    fun theColourNearestToTheFocusWins() {
        LineColors.addOsm(
            listOf(
                OsmLineColor("t", "1", "f39b9b", mannheim.first, mannheim.second),
                OsmLineColor("t", "1", "e2001a", karlsruhe.first, karlsruhe.second),
            ),
        )
        LineColors.focus = mannheim
        assertEquals(0xFFF39B9B.toInt(), LineColors.lookup("1", "tram")?.color)
        LineColors.focus = karlsruhe
        assertEquals(0xFFE2001A.toInt(), LineColors.lookup("1", "tram")?.color)
        // Far from both (Hamburg): none.
        LineColors.focus = 53.55 to 10.0
        assertNull(LineColors.lookup("1", "tram"))
        // A bus 1 is another line.
        LineColors.focus = mannheim
        assertNull(LineColors.lookup("1", "bus"))
    }

    @Test
    fun aStadtbahnMayBeATramForTheProvider() {
        LineColors.addOsm(listOf(OsmLineColor("u", "U47", "8a2be2", 51.51, 7.47)))
        LineColors.focus = 51.51 to 7.47
        assertEquals(0xFF8A2BE2.toInt(), LineColors.lookup("U47", "tram")?.color)
    }

    @Test
    fun theProvidersColourComesFirst() {
        LineColors.addOsm(listOf(OsmLineColor("t", "5", "00975f", mannheim.first, mannheim.second)))
        LineColors.focus = mannheim
        LineColors.noteProvider("5", "tram", "123456", "FFFFFF", mannheim)
        val found = LineColors.lookup("5", "tram")
        assertEquals(0xFF123456.toInt(), found?.color)
        assertEquals(0xFFFFFFFF.toInt(), found?.text)
        // No colour from the provider: OpenStreetMap's stays.
        LineColors.noteProvider("6", "tram", "", null, mannheim)
        assertNull(LineColors.lookup("6", "tram"))
    }

    @Test
    fun berlinBoxIsBerlin() {
        assertTrue(LineColors.inBerlin(52.52, 13.40))
        assertFalse(LineColors.inBerlin(mannheim.first, mannheim.second))
    }

    @Test
    fun dhidsOfProviderIds() {
        assertEquals("de:05913:1128", dhidOf("de-DELFI_de:05913:1128:1:1"))
        assertEquals("de:05913:1128", dhidOf("de:05913:1128"))
        assertNull(dhidOf("900023302"))
        assertEquals("de:05913:1128", stopPlaceId("de:05913:1128"))
        // Berlin keeps its VBB numbers.
        assertEquals("900023302", stopPlaceId("de:11000:900023302"))
        assertEquals(listOf("de:08222:2417"), idKeys("t:de-DELFI_de:08222:2417:2:4"))
        assertEquals(listOf("900023302", "de:11000:900023302"), idKeys("de:11000:900023302::2"))
    }

    @Test
    fun namesWithTheirTownFit() {
        assertTrue(nameFits(osmNameKey("Dortmund Heiliger Weg"), osmNameKey("Heiliger Weg")))
        assertTrue(nameFits(osmNameKey("Heiliger Weg, Dortmund"), osmNameKey("Heiliger Weg")))
        assertTrue(nameFits(osmNameKey("Mannheim Tattersall"), osmNameKey("Tattersall")))
        assertTrue(nameFits("tattersall", "tattersall"))
        // A short name is no part of another one.
        assertFalse(nameFits(osmNameKey("Dortmund Post"), osmNameKey("Post")))
        assertFalse(nameFits(osmNameKey("Mannheim Tattersall"), osmNameKey("Kunsthalle")))
    }
}
