package io.github.jnbp.nextstop.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The badges of notices take Berlin's own colours for its S- and U-Bahn (#100): a U9 is
 * orange, never the yellow of another city's U9 a provider noted, wherever the app looks.
 */
class NoticeLineColourTest {
    private val stuttgart = 48.779 to 9.187
    private val frankfurt = 50.168 to 8.642

    @After
    fun tearDown() = LineColors.clear()

    private fun yellowU9Elsewhere() {
        LineColors.noteProvider("U9", "subway", "ffd503", null, stuttgart)
        LineColors.noteProvider("U9", "", "eed700", null, frankfurt)
        LineColors.addOsm(listOf(OsmLineColor("u", "U9", "ffd503", stuttgart.first, stuttgart.second)))
    }

    @Test
    fun u9OfANoticeIsBerlinOrangeWhileTheAppLooksElsewhere() {
        yellowU9Elsewhere()
        LineColors.focus = stuttgart
        assertEquals(0xFFF96815.toInt(), noticeLineStyle(NoticeLine("U9", "subway"))?.color)
        LineColors.focus = null
        assertEquals(0xFFF96815.toInt(), noticeLineStyle(NoticeLine("U9", "subway"))?.color)
    }

    @Test
    fun u4StaysYellowAndS41KeepsItsColour() {
        yellowU9Elsewhere()
        LineColors.focus = frankfurt
        assertEquals(0xFFFFC101.toInt(), noticeLineStyle(NoticeLine("U4", "subway"))?.color)
        assertEquals(0xFFAD5937.toInt(), noticeLineStyle(NoticeLine("S41", "suburban"))?.color)
    }

    @Test
    fun aNamedU9OfANoticeIsASubway() {
        // "U9: Kein Halt …" names its line; its product is the U-Bahn, so Berlin's colour applies.
        val about = linesNamedIn("U9: Kein Halt", emptyList())
        assertEquals(listOf(NoticeLine("U9", "subway")), about)
        yellowU9Elsewhere()
        LineColors.focus = stuttgart
        assertEquals(0xFFF96815.toInt(), noticeLineStyle(about.single())?.color)
    }

    @Test
    fun aU9OfNoKnownProductTakesBerlinsColourFromThePack() {
        yellowU9Elsewhere()
        LineColors.addOsm(listOf(OsmLineColor("u", "U9", "f3791d", 52.508, 13.339)))
        LineColors.focus = stuttgart
        assertEquals(0xFFF96815.toInt(), noticeLineStyle(NoticeLine("U9", ""))?.color)
    }

    private fun notice(id: String, stops: List<Stop>) =
        Disruption(id, "U9: Kein Halt", "", setOf("U9"), setOf("subway"), stops = stops)

    private fun colourOf(item: Disruption): Int? = noticeLineStyle(item.badges.single(), item.colourPlace)?.color

    @Test
    fun aStuttgartNoticeKeepsStuttgartsColour() {
        yellowU9Elsewhere()
        LineColors.focus = NOTICE_COLOUR_PLACE
        val item = notice("s", listOf(Stop("de:08111:6118", "Stuttgart Hbf", 48.784, 9.182, null)))
        assertEquals(0xFFFFD503.toInt(), colourOf(item))
    }

    @Test
    fun aBerlinNoticeIsOrange() {
        yellowU9Elsewhere()
        LineColors.focus = stuttgart
        val item = notice("b", listOf(Stop("900100003", "S+U Alexanderplatz", 52.5219, 13.4132, null)))
        assertEquals(0xFFF96815.toInt(), colourOf(item))
    }

    @Test
    fun aNoticeWithoutAPlaceIsColouredInBerlin() {
        yellowU9Elsewhere()
        LineColors.focus = stuttgart
        val item = notice("u", listOf(Stop("x", "Somewhere", null, null, null)))
        assertEquals(NOTICE_COLOUR_PLACE, item.colourPlace)
        assertEquals(0xFFF96815.toInt(), colourOf(item))
    }
}
