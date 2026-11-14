package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The walks of changes measured over the walking network: a tiny fixture of S Lichterfelde Ost
 * from transfer-walks-berlin.json (© OpenStreetMap contributors): platform 1/2 of the S25, the
 * platforms 3 and 4, and the stop of the bus 184 in the street.
 */
class TransferWalkTest {
    private val start: OffsetDateTime = OffsetDateTime.parse("2026-01-01T10:00:00+01:00")
    private fun at(minutes: Long) = start.plusMinutes(minutes)

    private val axis12 = doubleArrayOf(52.429445, 13.327536, 52.430902, 13.329573)
    private val axis3 = doubleArrayOf(52.429281, 13.327974, 52.43022, 13.329589)
    private val axis4 = doubleArrayOf(52.429131, 13.328235, 52.43009, 13.329607)
    private val busStop = 52.428718 to 13.328268
    private val farStop = 52.429919 to 13.333118

    private val complex = TransferWalkStation(
        id = "900064301",
        name = "Lichterfelde Ost",
        platforms = listOf(axis12, axis3, axis4),
        stops = listOf(busStop, farStop),
        walks = mapOf(
            TransferWalkStation.pairKey(0, 1) to TransferWalk(101, 156, false),
            TransferWalkStation.pairKey(0, 2) to TransferWalk(125, 178, false),
            TransferWalkStation.pairKey(0, 3) to TransferWalk(139, 163, false),
            TransferWalkStation.pairKey(1, 3) to TransferWalk(118, null, false),
            TransferWalkStation.pairKey(0, 4) to TransferWalk(400, null, true),
            TransferWalkStation.pairKey(3, 4) to TransferWalk(330, 330, false),
        ),
    )
    private val index = TransferWalkIndex(listOf(complex))

    private fun platform(axis: DoubleArray) = WalkEnd.Platform(axis[0] to axis[1], axis[2] to axis[3])

    @Test
    fun placesAreFoundByAxisAndPosition() {
        assertEquals(0, complex.indexOf(platform(axis12)))
        assertEquals(2, complex.indexOf(platform(axis4)))
        assertNull(complex.indexOf(WalkEnd.Platform(52.0 to 13.0, 52.1 to 13.1)))
        // A stop position a metre off is the same stop; one 20 m off is not.
        assertEquals(3, complex.indexOf(WalkEnd.Stop(busStop.first + 0.00001 to busStop.second)))
        assertNull(complex.indexOf(WalkEnd.Stop(busStop.first + 0.0002 to busStop.second)))
    }

    @Test
    fun walksCountBothWaysAndStepFree() {
        val there = complex.walk(platform(axis12), WalkEnd.Stop(busStop))!!
        val back = complex.walk(WalkEnd.Stop(busStop), platform(axis12))!!
        assertEquals(there, back)
        assertEquals(139, there.seconds(stepFree = false))
        assertEquals(163, there.seconds(stepFree = true))
        // Without a step-free way the walk with steps is all there is.
        assertEquals(118, complex.walk(platform(axis3), WalkEnd.Stop(busStop))!!.seconds(stepFree = true))
        // The same platform: no walk.
        assertEquals(0, complex.walk(platform(axis3), platform(axis3))!!.seconds)
    }

    @Test
    fun severalPlatformsCountTheLongestEstimatesNot() {
        val stop = listOf(WalkEnd.Stop(busStop))
        assertEquals(139, changeWalkSeconds(complex, listOf(platform(axis12)), stop, stepFree = false))
        assertEquals(139, changeWalkSeconds(complex, listOf(platform(axis12), platform(axis3)), stop, stepFree = false))
        // An estimate (straight line) is left to the rules of thumb.
        assertNull(changeWalkSeconds(complex, listOf(platform(axis12)), listOf(WalkEnd.Stop(farStop)), stepFree = false))
        // A pair without a walk: unknown.
        assertNull(changeWalkSeconds(complex, listOf(platform(axis4)), listOf(WalkEnd.Stop(farStop)), stepFree = false))
    }

    @Test
    fun theComplexIsFoundByIdOrName() {
        val station = OsmStation(
            "Lichterfelde Ost", listOf("900064301"), "SR", 52.42975, 13.328452,
            listOf(OsmPlatform(axis12[0] to axis12[1], axis12[2] to axis12[3], "1;2", "1", listOf("S25", "S26"))),
            emptyList(), emptyList(),
        )
        assertSame(complex, index.of(station))
        assertSame(complex, index.of(station.copy(ids = emptyList())))
        assertNull(index.of(station.copy(name = "Somewhere", ids = listOf("900000001"))))
    }

    @Test
    fun theIndexesOfTwoPacksAreJoined() {
        // Hamburg Hauptbahnhof from transfer-walks-hamburg.json (two of its platforms).
        val hamburgAxis = doubleArrayOf(53.553311, 10.007786, 53.551215, 10.008667)
        val hamburgAxis2 = doubleArrayOf(53.554441, 10.00613, 53.550776, 10.008424)
        val hamburg = TransferWalkStation(
            "de:02000:10950", "Hamburg Hauptbahnhof", listOf(hamburgAxis, hamburgAxis2), emptyList(),
            mapOf(TransferWalkStation.pairKey(0, 1) to TransferWalk(58, 102, false)),
        )
        val other = TransferWalkIndex(listOf(hamburg))
        val joined = index + other
        val hamburgStation = OsmStation(
            "Hamburg Hauptbahnhof", listOf("de:02000:10950"), "SR", 53.5528, 10.0067,
            listOf(OsmPlatform(hamburgAxis[0] to hamburgAxis[1], hamburgAxis[2] to hamburgAxis[3], "11;12", null, emptyList())),
            emptyList(), emptyList(),
        )
        val lichterfelde = OsmStation(
            "Lichterfelde Ost", listOf("900064301"), "SR", 52.42975, 13.328452,
            listOf(OsmPlatform(axis12[0] to axis12[1], axis12[2] to axis12[3], "1;2", "1", listOf("S25"))),
            emptyList(), emptyList(),
        )
        // Both packs' complexes are found, by id and by name.
        assertSame(complex, joined.of(lichterfelde))
        assertSame(hamburg, joined.of(hamburgStation))
        assertSame(hamburg, joined.of(hamburgStation.copy(ids = emptyList())))
        assertNull(index.of(hamburgStation))
        assertEquals(58, joined.of(hamburgStation)!!.walk(0, 1)!!.seconds)
        assertSame(complex, joined.ofStops(busStop, farStop))
        // Joining with nothing keeps the index.
        assertSame(index, index + TransferWalkIndex.EMPTY)
        assertSame(other, TransferWalkIndex.EMPTY + other)

        // The same complex in two packs: the first one's is found, unless only the other knows its platforms.
        val again = TransferWalkStation("900064301", "Lichterfelde Ost", listOf(axis12), emptyList(), emptyMap())
        assertSame(complex, (index + TransferWalkIndex(listOf(again))).of(lichterfelde))
        assertSame(again, (TransferWalkIndex(listOf(again)) + index).of(lichterfelde))
        val elsewhere = TransferWalkStation("900064301", "Lichterfelde Ost", listOf(doubleArrayOf(52.0, 13.0, 52.1, 13.1)), emptyList(), emptyMap())
        assertSame(complex, (TransferWalkIndex(listOf(elsewhere)) + index).of(lichterfelde))
    }

    @Test
    fun twoStopsOfOneComplex() {
        // A bus on one side of the station, the next on the other: the complex knows the walk.
        assertSame(complex, index.ofStops(busStop, farStop))
        assertSame(complex, index.ofStops(farStop, busStop))
        assertEquals(330, changeWalkSeconds(complex, listOf(WalkEnd.Stop(busStop)), listOf(WalkEnd.Stop(farStop)), stepFree = false))
        assertNull(index.ofStops(busStop, 52.0 to 13.0))
    }

    private val place = Stop("900064301", "S Lichterfelde Ost", 52.42975, 13.328452, null, setOf("suburban", "bus"))
    private val elsewhere = Stop("900000001", "Somewhere", 52.5, 13.4, null)
    private val s25 = Leg(
        "S25", "suburban", false, elsewhere.name, place.name, at(0), at(0), at(10), at(10), null, "1", null, false,
        fromPlace = elsewhere, toPlace = place, tripId = "s25",
    )
    private val bus = Leg(
        "184", "bus", false, place.name, elsewhere.name, at(15), at(15), at(30), at(30), null, null, null, false,
        fromPlace = place, toPlace = elsewhere, tripId = "184",
        boardingSpot = WalkTarget(WalkTargetKind.StopPosition, null, busStop.first, busStop.second),
    )

    @Test
    fun theNeedOfAChange() {
        // 139 s and half a minute: 3 minutes; a change of 5 minutes leaves 2: normal.
        assertEquals(3L, exactWalkNeed(s25, bus) { _, _ -> 139 })
        // A short walk still needs 2, the same platform 1, an unknown walk is left to the rules.
        assertEquals(2L, exactWalkNeed(s25, bus) { _, _ -> 20 })
        assertEquals(1L, exactWalkNeed(s25, bus) { _, _ -> 0 })
        assertNull(exactWalkNeed(s25, bus) { _, _ -> null })
        assertEquals(3L, walkSecondsToMinutes(139))

        val previous = transferWalkSeconds
        try {
            transferWalkSeconds = { _, _ -> 139 }
            assertEquals(3L, changeNeed(s25, bus))
            assertEquals(Tightness.Tight, changeTightness(s25, bus))
            // The provider's walk of the whole wait counts no more than the measured walk.
            val walk = Leg(
                null, "walk", true, place.name, place.name, at(10), at(10), at(15), at(15), null, null, null, false,
                fromPlace = place, toPlace = place,
            )
            assertEquals(3L, changeWalkMinutes(walk, s25, bus))
        } finally {
            transferWalkSeconds = previous
        }
    }
}
