package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.ui.theme.MARK_CONTRAST
import io.github.jnbp.nextstop.ui.theme.contrastRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Casings on the map only where a colour melts into the ground (see LineCasing.kt). */
class LineCasingTest {
    // A pale yellow that is no line colour of the network map.
    private val yellow = 0xFFF7EC6E.toInt()
    private val u4 = 0xFFFFC101.toInt()
    private val grey = 0xFFBBBBBB.toInt()
    private val green = 0xFF008D4F.toInt()
    private val red = 0xFFED3C12.toInt()
    private val navy = 0xFF1F2A44.toInt()
    private val white = 0xFFFFFFFF.toInt()

    @Test
    fun aStrongColourOnTheLightMapIsDrawnBare() {
        assertFalse(lineNeedsCasing(green, LIGHT_MAP_GROUND))
        assertFalse(lineNeedsCasing(red, LIGHT_MAP_GROUND))
        assertEquals(0, lineCasingArgb(green, LIGHT_MAP_GROUND))
    }

    @Test
    fun aLightColourOnTheLightMapGetsADarkerEdgeOfItsOwn() {
        assertTrue(lineNeedsCasing(yellow, LIGHT_MAP_GROUND))
        val casing = lineCasingArgb(yellow, LIGHT_MAP_GROUND)
        assertTrue(casing != 0)
        assertEquals(0xFF, (casing ushr 24) and 0xFF)
        assertTrue(contrastRatio(casing, LIGHT_MAP_GROUND) >= MARK_CONTRAST)
        assertTrue(relativeLuminance(casing) < relativeLuminance(yellow))
    }

    @Test
    fun aDarkColourOnTheDarkMapGetsALighterEdge() {
        assertTrue(lineNeedsCasing(navy, DARK_MAP_GROUND))
        val casing = lineCasingArgb(navy, DARK_MAP_GROUND)
        assertTrue(contrastRatio(casing, DARK_MAP_GROUND) >= MARK_CONTRAST)
        assertTrue(relativeLuminance(casing) > relativeLuminance(navy))
    }

    @Test
    fun theSameColourCanNeedOneOnOneMapAndNoneOnTheOther() {
        assertTrue(lineNeedsCasing(white, LIGHT_MAP_GROUND))
        assertFalse(lineNeedsCasing(white, DARK_MAP_GROUND))
        assertFalse(lineNeedsCasing(navy, LIGHT_MAP_GROUND))
    }

    @Test
    fun alphaIsIgnored() {
        val seeThrough = yellow and 0x00FFFFFF
        assertEquals(lineNeedsCasing(yellow, LIGHT_MAP_GROUND), lineNeedsCasing(seeThrough, LIGHT_MAP_GROUND))
    }

    @Test
    fun theCacheGivesWhatTheRuleGives() {
        for (colour in intArrayOf(yellow, u4, grey, green, red, navy, white)) {
            for (ground in intArrayOf(LIGHT_MAP_GROUND, DARK_MAP_GROUND)) {
                assertEquals(lineCasingArgb(colour, ground), LineCasings.of(colour, ground))
                assertEquals(lineCasingArgb(colour, ground), LineCasings.of(colour, ground))
            }
        }
    }

    @Test
    fun onlyAContrastBelowOneAndAHalfGetsACasing() {
        // About 1.7:1 to the light map: was cased below 2:1, now drawn bare.
        assertFalse(lineNeedsCasing(grey, LIGHT_MAP_GROUND))
        assertEquals(0, lineCasingArgb(grey, LIGHT_MAP_GROUND))
    }

    @Test
    fun standardLineColoursAreBareOnTheLightMap() {
        // The U4 is only about 1.3:1 to the light map, still drawn without an edge.
        assertTrue(isStandardLineColor(u4))
        assertFalse(lineNeedsCasing(u4, LIGHT_MAP_GROUND))
        assertEquals(0, LineCasings.of(u4, LIGHT_MAP_GROUND))
        for (colour in io.github.jnbp.nextstop.data.LineColors.BERLIN_RAPID.values) {
            assertFalse(lineNeedsCasing(colour, LIGHT_MAP_GROUND))
        }
        // On the dark map the rule of the contrast still holds for them.
        assertFalse(lineNeedsCasing(u4, DARK_MAP_GROUND))
        assertFalse(isStandardLineColor(yellow))
    }

    @Test
    fun theGroundFollowsTheMap() {
        assertEquals(DARK_MAP_GROUND, mapGroundArgb(darkMap = true))
        assertEquals(LIGHT_MAP_GROUND, mapGroundArgb(darkMap = false))
    }
}
