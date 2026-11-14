package io.github.jnbp.nextstop.data

import kotlin.math.cos
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the sign of a stop stands: "Wolliner Str." (VBB 900007105) with its four platforms of
 * OpenStreetMap (© OpenStreetMap contributors, November 2026) and the provider's point 36 m
 * from the nearest of them; the same cases as tools/test_stop_signs.py.
 */
class StopSignPlaceTest {
    private val platforms = listOf(
        52.5400793 to 13.4021201, // tram M10, east of the crossing
        52.5396116 to 13.4012188, // tram M10, west
        52.5400786 to 13.4012564, // bus 247, west side of Wolliner Strasse
        52.5400453 to 13.4015002, // bus 247, east side
    )
    private val provider = 52.539774 to 13.401802

    private fun meters(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val north = (b.first - a.first) * 111_320.0
        val east = (b.second - a.second) * 111_320.0 * cos(Math.toRadians((a.first + b.first) / 2))
        return sqrt(north * north + east * east)
    }

    @Test
    fun providerPointInABlockMovesToThePlatforms() {
        val sign = stopSignPoint(provider, platforms)!!
        assertNotEquals(provider, sign)
        assertTrue(platforms.minOf { meters(sign, it) } <= 30.0)
        // Inside the box of the platforms: on the crossing, not south-east of it.
        assertTrue(sign.first in platforms.minOf { it.first }..platforms.maxOf { it.first })
        assertTrue(sign.second in platforms.minOf { it.second }..platforms.maxOf { it.second })
    }

    @Test
    fun providerPointBesideThePlatformsStays() {
        val close = 52.54 to 13.4015
        assertEquals(close, stopSignPoint(close, platforms))
    }

    @Test
    fun noPositionsKeepThePoint() {
        assertEquals(provider, stopSignPoint(provider, emptyList()))
        assertNull(stopSignPoint(null, emptyList()))
    }

    @Test
    fun twoDirectionsFarApartGetASignBesideOne() {
        val apart = listOf(52.5 to 13.4, 52.5 + 150 / 111_320.0 to 13.4)
        val sign = stopSignPoint(52.5 to 13.41, apart)!!
        assertTrue(apart.minOf { meters(sign, it) } <= 30.001)
        val osmOnly = stopSignPoint(null, apart)!!
        assertTrue(apart.minOf { meters(osmOnly, it) } <= 30.001)
    }

    @Test
    fun farEndOfAVillageDoesNotPullTheSign() {
        val village = listOf(52.5 to 13.4, 52.5001 to 13.4, 52.51 to 13.4)
        val sign = stopSignPoint(52.4995 to 13.4, village)!!
        assertTrue(meters(sign, village[0]) <= 30.0)
    }
}
