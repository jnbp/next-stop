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
    fun aLineOfNoKnownProductTakesTheColourOfItsName() {
        // A Stadtbahn of Stuttgart whose feed names a mode the app has no key for.
        val stuttgart = 48.79 to 9.18
        LineColors.addOsm(listOf(OsmLineColor("u", "U2", "f58220", stuttgart.first, stuttgart.second)))
        assertEquals(0xFFF58220.toInt(), LineColors.rideStyle("U2", "", stuttgart)?.color)
        // The colour its feed sends with it is kept, and found for it without that.
        LineColors.noteProvider("U14", "", "69be51", null, stuttgart)
        assertEquals(0xFF69BE51.toInt(), LineColors.lookup("U14", "", stuttgart)?.color)
        assertEquals("o", LineColors.kindOf(""))
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

    // Berlin's night bus N2 runs from Ruhleben along the U2; Brandenburg an der Havel, within
    // MAX_FOCUS_METERS of Ruhleben, has an N2 of its own in light blue.
    private val ruhleben = 52.5255 to 13.2418
    private val pankow = 52.5672 to 13.4120
    private val brandenburgHavel = 52.416 to 12.528

    @Test
    fun aNightBusInBerlinTakesNoColourOfATownAround() {
        LineColors.addOsm(
            listOf(
                OsmLineColor("b", "N2", "00aeef", brandenburgHavel.first, brandenburgHavel.second),
                OsmLineColor("b", "M41", "00aeef", brandenburgHavel.first, brandenburgHavel.second),
                OsmLineColor("t", "M10", "00aeef", brandenburgHavel.first, brandenburgHavel.second),
            ),
        )
        // In Berlin, wherever the ride is placed: no colour of its own, so the bus colour applies
        // (the same for header, map, vehicle and list).
        assertNull(LineColors.rideStyle("N2", "bus", ruhleben))
        assertNull(LineColors.rideStyle("N2", "bus", pankow))
        assertNull(LineColors.rideStyle("M41", "bus", ruhleben))
        assertNull(LineColors.rideStyle("M10", "tram", ruhleben))
        // Without a place of its own, with the app looking at Berlin: none either.
        LineColors.focus = ruhleben
        assertNull(LineColors.rideStyle("N2", "bus", null))
        // In Brandenburg an der Havel its own N2 keeps its colour.
        assertEquals(0xFF00AEEF.toInt(), LineColors.rideStyle("N2", "bus", brandenburgHavel)?.color)
    }

    @Test
    fun nightLinesNeverTakeTheColourOfAnSOrUBahn() {
        for (line in listOf("N1", "N2", "N5", "N7", "N8", "N9", "N10", "N42", "N91")) {
            assertNull(line, LineColors.berlinRapid(line, "bus", ruhleben))
            assertNull(line, LineColors.rideStyle(line, "bus", ruhleben))
            // Even where a feed calls it a U-Bahn: its name is no U-Bahn line.
            assertNull(line, LineColors.berlinRapid(line, "subway", ruhleben))
        }
        assertNull(LineColors.rideStyle("M48", "bus", ruhleben))
        assertNull(LineColors.rideStyle("M10", "tram", ruhleben))
    }

    @Test
    fun berlinsSAndUBahnKeepTheirColours() {
        LineColors.addOsm(listOf(OsmLineColor("u", "U2", "00aeef", brandenburgHavel.first, brandenburgHavel.second)))
        assertEquals(0xFFED3C12.toInt(), LineColors.rideStyle("U2", "subway", ruhleben)?.color)
        assertEquals(0xFF419328.toInt(), LineColors.rideStyle("U1", "subway", pankow)?.color)
        assertEquals(0xFFDA6BA2.toInt(), LineColors.rideStyle("1", "suburban", pankow)?.color)
        assertEquals(0xFFAD5937.toInt(), LineColors.rideStyle("S41", "suburban", ruhleben)?.color)
    }

    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    @Test
    fun berlinsUBahnHasTheColoursAndInkOfTheBvgSigns() {
        val signs = mapOf(
            "U1" to 0xFF419328, "U2" to 0xFFED3C12, "U3" to 0xFF016144, "U4" to 0xFFFFC101, "U5" to 0xFF603B2C,
            "U6" to 0xFF504F87, "U7" to 0xFF0279BD, "U8" to 0xFF013F75, "U9" to 0xFFF96815,
        )
        for ((line, color) in signs) {
            val style = LineColors.rideStyle(line, "subway", pankow)
            assertEquals(line, color.toInt(), style?.color)
            // White on every line, black on the U4 only: fixed, not by lightness (the U9 is white).
            assertEquals(line, if (line == "U4") black else white, style?.text)
        }
    }

    @Test
    fun berlinsUBahnTableWinsOverAProvidersColour() {
        // A feed's own orange and black text for the U9 in Berlin: the badge stays the BVG's.
        val sent = LineColors.rideStyle("U9", "subway", pankow, provided = 0xFFF3791D.toInt(), providedText = black)
        assertEquals(0xFFF96815.toInt(), sent?.color)
        assertEquals(white, sent?.text)
        // Only for the U-Bahn of Berlin: an S-Bahn of Berlin and a U1 elsewhere keep the feed's colour.
        val sBahn = LineColors.rideStyle("S41", "suburban", ruhleben, provided = 0xFF123456.toInt(), providedText = white)
        assertEquals(0xFF123456.toInt(), sBahn?.color)
        val hamburg = 53.55 to 10.0
        val elsewhere = LineColors.rideStyle("U1", "subway", hamburg, provided = 0xFF123456.toInt(), providedText = white)
        assertEquals(0xFF123456.toInt(), elsewhere?.color)
        assertEquals(white, elsewhere?.text)
        // A regional train named U1 is no U-Bahn of Berlin.
        assertNull(LineColors.berlinSubwayStyle("U1", "regional", pankow))
        assertNull(LineColors.berlinSubwayStyle("S1", "suburban", pankow))
        // Of no known product only a name with its U is one.
        assertEquals(0xFFF96815.toInt(), LineColors.berlinSubwayStyle("U9", "", pankow)?.color)
        assertNull(LineColors.berlinSubwayStyle("9", "", pankow))
    }

    @Test
    fun aProvidersColourInBerlinStaysInBerlin() {
        // BVG's own colour of the N2, sent at Ruhleben: the same for the ride at Pankow.
        LineColors.noteProvider("N2", "bus", "95276e", "ffffff", ruhleben)
        assertEquals(0xFF95276E.toInt(), LineColors.rideStyle("N2", "bus", pankow)?.color)
        LineColors.clear()
        // A provider's colour of the N2 of Brandenburg an der Havel is none of Berlin's N2.
        LineColors.noteProvider("N2", "bus", "00aeef", null, brandenburgHavel)
        assertNull(LineColors.rideStyle("N2", "bus", ruhleben))
        assertEquals(0xFF00AEEF.toInt(), LineColors.rideStyle("N2", "bus", brandenburgHavel)?.color)
        // A colour the ride itself carries comes first, everywhere.
        assertEquals(0xFF123456.toInt(), LineColors.rideStyle("N2", "bus", ruhleben, provided = 0xFF123456.toInt())?.color)
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

    @Test
    fun versionCountsUpOnlyForNewColours() {
        val start = LineColors.version.value
        LineColors.noteProvider("5", "tram", "123456", "FFFFFF", mannheim)
        val afterNew = LineColors.version.value
        assertTrue(afterNew > start)
        // The same colour of the same line nearby again: nothing to draw anew.
        LineColors.noteProvider("5", "tram", "123456", "FFFFFF", mannheim)
        assertEquals(afterNew, LineColors.version.value)
        // Another colour for it, or the line elsewhere: drawn anew.
        LineColors.noteProvider("5", "tram", "654321", "FFFFFF", mannheim)
        assertTrue(LineColors.version.value > afterNew)
        val afterChange = LineColors.version.value
        LineColors.noteProvider("5", "tram", "654321", "FFFFFF", karlsruhe)
        assertTrue(LineColors.version.value > afterChange)
    }
}
