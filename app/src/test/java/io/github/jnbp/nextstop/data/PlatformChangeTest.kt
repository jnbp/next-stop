package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A change between two rail rides at one station complex without a walk from the provider:
 * the S25 to the U8 at Karl-Bonhoeffer-Nervenklinik (platform axes as in osm-berlin.json, the
 * provider's ways made up), where the U8's way started at the station's coordinate, 150 m off
 * its track, and drew a spike to the north-east and back.
 */
class PlatformChangeTest {
    private val at0: OffsetDateTime = OffsetDateTime.parse("2026-11-13T12:00:00+01:00")
    private val stationPoint = 52.578321 to 13.331339
    private val sPlatform = OsmPlatform(52.577969 to 13.328314, 52.578005 to 13.330068, "2", "1", listOf("S25"))
    private val uPlatform = OsmPlatform(52.579746 to 13.333027, 52.578414 to 13.33357, null, "-2", listOf("U8"))
    private val complex = Any()
    private val station = Stop("900096458", "Karl-Bonhoeffer-Nervenklinik", stationPoint.first, stationPoint.second, null)

    private val s25 = Leg(
        "S25", "suburban", false, "Tegel", station.name, at0, at0, at0.plusMinutes(4), at0.plusMinutes(4),
        null, "2", null, false,
        path = listOf(52.5779 to 13.3200, 52.57799 to 13.3292, stationPoint),
        toPlace = station, tripId = "s25",
    )
    private val u8 = Leg(
        "U8", "subway", false, station.name, "Hermannstr.", at0.plusMinutes(12), at0.plusMinutes(12),
        at0.plusMinutes(40), at0.plusMinutes(40), null, null, null, false,
        path = listOf(stationPoint, 52.5800 to 13.3329, 52.5791 to 13.3333, 52.5770 to 13.3340, 52.5740 to 13.3350),
        fromPlace = station, tripId = "u8",
    )

    private fun sharpest(path: List<Pair<Double, Double>>): Double =
        (1 until path.lastIndex).maxOfOrNull { PathJoin.turnDegrees(path[it - 1], path[it], path[it + 1]) } ?: 0.0

    private fun ends(ride: Leg, boarding: Boolean): PlatformChange.Ends? = when {
        ride.line == "S25" -> PlatformChange.Ends(complex, listOf(sPlatform))
        ride.line == "U8" -> PlatformChange.Ends(complex, listOf(uPlatform))
        else -> null
    }

    private val rail = setOf("subway", "suburban", "regional", "express")

    @Test
    fun theU8StartsAtItsOwnPlatformWithoutASpike() {
        assertTrue(sharpest(u8.path) > 100.0)
        val clipped = PlatformChange.clipAtPlatforms(u8, listOf(uPlatform), emptyList())
        assertTrue(segmentMeters(clipped.path.first(), uPlatform.a, uPlatform.b) < 10.0)
        assertTrue(sharpest(clipped.path) < 100.0)
        assertEquals(u8.path.last(), clipped.path.last())
    }

    @Test
    fun theS25EndsAtItsPlatform() {
        val clipped = PlatformChange.clipAtPlatforms(s25, emptyList(), listOf(sPlatform))
        assertTrue(segmentMeters(clipped.path.last(), sPlatform.a, sPlatform.b) < 10.0)
        assertEquals(s25.path.first(), clipped.path.first())
    }

    @Test
    fun aPlatformFarFromTheWayLeavesItAlone() {
        val far = OsmPlatform(52.59 to 13.30, 52.591 to 13.301, null, null, listOf("U8"))
        assertSame(u8, PlatformChange.clipAtPlatforms(u8, listOf(far), emptyList()))
    }

    @Test
    fun aWalkGoesBetweenDifferentPlatformsOfOneComplex() {
        val legs = PlatformChange.withWalks(listOf(s25, u8), rail, this::ends) { _, _ -> 374 }
        assertEquals(3, legs.size)
        val walk = legs[1]
        assertTrue(walk.walking)
        assertEquals(at0.plusMinutes(4), walk.departure)
        assertEquals(at0.plusMinutes(4).plusSeconds(374), walk.arrival)
        assertTrue(segmentMeters(walk.path.first(), sPlatform.a, sPlatform.b) < 1.0)
        assertTrue(segmentMeters(walk.path.last(), uPlatform.a, uPlatform.b) < 1.0)
        assertEquals(station, walk.fromPlace)
        // The walk counts as the change's walk: 374 s are 6 whole minutes.
        assertEquals(6L, changeWalkMinutes(walk))
    }

    @Test
    fun withoutAMeasuredWalkTheStraightLineCounts() {
        val legs = PlatformChange.withWalks(listOf(s25, u8), rail, this::ends) { _, _ -> null }
        val walk = legs[1]
        val meters = PathJoin.meters(walk.path.first(), walk.path.last())
        assertTrue(meters > 150.0)
        val seconds = java.time.Duration.between(walk.departure, walk.arrival).seconds
        assertTrue(seconds >= (meters * 1.3 / 75.0 * 60.0).toLong())
    }

    @Test
    fun noWalkOnOnePlatformOrAcross() {
        val same = { _: Leg, _: Boolean -> PlatformChange.Ends(complex, listOf(sPlatform)) }
        val s25back = s25.copy(line = "S25", tripId = "other", path = s25.path.reversed())
        assertEquals(2, PlatformChange.withWalks(listOf(s25, s25back), rail, same) { _, _ -> 30 }.size)
        assertTrue(PlatformChange.samePlatform(PlatformChange.Ends(complex, listOf(sPlatform)), PlatformChange.Ends(complex, listOf(sPlatform))))
        assertFalse(PlatformChange.samePlatform(PlatformChange.Ends(complex, listOf(sPlatform)), PlatformChange.Ends(complex, listOf(uPlatform))))
        // Two edges of one island, 10 m apart: a change across.
        val edge = OsmPlatform(52.578095 to 13.328314, 52.578131 to 13.330068, "1", "1", listOf("S8"))
        val across = { ride: Leg, _: Boolean -> PlatformChange.Ends(complex, listOf(if (ride.line == "S25") sPlatform else edge)) }
        val s8 = u8.copy(line = "S8", product = "suburban")
        assertEquals(2, PlatformChange.withWalks(listOf(s25, s8), rail, across) { _, _ -> 20 }.size)
    }

    @Test
    fun noWalkBetweenComplexesOrForBusesOrThroughRuns() {
        val other = { ride: Leg, _: Boolean -> PlatformChange.Ends(if (ride.line == "S25") complex else Any(), listOf(uPlatform)) }
        assertNull(PlatformChange.walkBetween(s25, u8, rail, other) { _, _ -> 300 })
        val bus = u8.copy(product = "bus")
        assertNull(PlatformChange.walkBetween(s25, bus, rail, this::ends) { _, _ -> 300 })
        val onBoard = u8.copy(stayOnBoard = true)
        assertNull(PlatformChange.walkBetween(s25, onBoard, rail, this::ends) { _, _ -> 300 })
    }
}
