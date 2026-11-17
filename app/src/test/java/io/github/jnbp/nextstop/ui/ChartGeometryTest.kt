package io.github.jnbp.nextstop.ui

import androidx.compose.ui.unit.dp
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.Leg
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.TransferComfort
import io.github.jnbp.nextstop.data.changeNeed
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The geometry of the connections chart, worked out while it is laid out (so the window and the
 * scale can glide without composing it), and the rule by which a change shows in it. Made-up stops.
 */
class ChartGeometryTest {
    private val start: OffsetDateTime = OffsetDateTime.parse("2026-01-01T08:00:00+01:00")
    private fun at(minutes: Long) = start.plusMinutes(minutes)
    private fun stop(id: String) = Stop("90000000$id", "Stop $id", 52.0, 13.0, null)
    private val a = stop("1")
    private val hub = stop("2")
    private val c = stop("3")

    private fun ride(
        line: String,
        from: Stop,
        to: Stop,
        leaves: Long,
        arrives: Long,
        late: Long = 0,
        cancelled: Boolean = false,
        trip: String = "$line@$leaves",
    ) = Leg(
        line, "bus", false, from.name, to.name, at(leaves + late), at(leaves), at(arrives + late), at(arrives),
        "1", "1", null, cancelled, fromPlace = from, toPlace = to, tripId = trip,
    )

    private fun minutes(legs: List<Leg>, of: (Leg) -> OffsetDateTime) =
        FloatArray(legs.size) { java.time.Duration.between(start, of(legs[it])).seconds / 60f }

    private fun geometry(legs: List<Leg>, windowStart: Float, perMinute: Float = 3f, labels: Set<Int> = emptySet()) =
        columnGeometry(
            legs, minutes(legs) { it.departure }, minutes(legs) { it.arrival }, windowStart, perMinute.dp,
            labels, labelHeight = 18.dp, cancelled = legs.any { it.cancelled },
        )

    private val twoRides = listOf(ride("X1", a, hub, 10, 20), ride("X2", hub, c, 30, 50))

    @Test
    fun movingTheWindowOnlyMovesTheColumn() {
        val before = geometry(twoRides, windowStart = 0f)
        val after = geometry(twoRides, windowStart = 4f)
        // 4 minutes at 3 dp per minute: everything 12 dp higher, nothing else changes.
        before.parts.zip(after.parts).forEach { (old, new) ->
            assertEquals(old.top.value - 12f, new.top.value, 0.01f)
            assertEquals(old.height.value, new.height.value, 0.01f)
        }
        assertEquals(before.departureTop.value - 12f, after.departureTop.value, 0.01f)
        assertEquals(before.arrivalTop.value - 12f, after.arrivalTop.value, 0.01f)
        // So the column is not composed again while the window glides.
        assertEquals(before.look(), after.look())
    }

    @Test
    fun theLookChangesOnlyWhereAHeightThatDecidesItIsCrossed() {
        // At 1 dp per minute the gap of 10 minutes is too low for the name of the stop, at 3 dp it is not.
        assertFalse(geometry(twoRides, 0f, perMinute = 1f).look().roomyGaps[0])
        assertTrue(geometry(twoRides, 0f, perMinute = 3f).look().roomyGaps[0])
        assertEquals(listOf(0, 1), geometry(twoRides, 0f).look().legIndices)
    }

    @Test
    fun aLateRideShowsItsPlannedDepartureWhereThereIsRoom() {
        val legs = listOf(ride("X1", a, hub, 10, 20), ride("X2", hub, c, 30, 50, late = 3))
        val shape = geometry(legs, 0f)
        assertEquals(0f, shape.dashes[0].value, 0.01f)
        // 3 minutes late at 3 dp per minute: 9 dp of dashed outline above the block.
        assertEquals(9f, shape.dashes[1].value, 0.01f)
        assertEquals(listOf(false, true), shape.look().dashed)
    }

    @Test
    fun theCancelledRideHoldsItsTagWhereItIsTallEnough() {
        val legs = listOf(ride("X1", a, hub, 10, 20), ride("X2", hub, c, 30, 50, cancelled = true))
        assertEquals(1, geometry(legs, 0f, perMinute = 3f).cancelTagPart)
        // 20 minutes at 1 dp: lower than the tag needs, so it stands below the column.
        assertEquals(-1, geometry(legs, 0f, perMinute = 1f).cancelTagPart)
    }

    @Test
    fun aChangeShowsItsMinutesOnlyWhereTheyFitByTheClock() {
        // Two minutes to change at 1 dp per minute: 2 dp by the clock, too low for the minutes: a dot.
        val tight = listOf(ride("X1", a, hub, 10, 30), ride("X2", hub, c, 32, 60))
        val parts = geometry(tight, 0f, perMinute = 1f, labels = setOf(1)).parts
        assertEquals(2f, (parts[1].top - (parts[0].top + parts[0].height)).value, 0.01f)
        assertEquals(0, geometry(tight, 0f, perMinute = 1f, labels = setOf(1)).look().gapRoom[0])
        // Ten minutes at 3 dp: room for the pill as well.
        assertEquals(2, geometry(twoRides, 0f, perMinute = 3f).look().gapRoom[0])
        // Five minutes at 3 dp: the minutes, not a pill.
        val five = listOf(ride("X1", a, hub, 10, 20), ride("X2", hub, c, 25, 50))
        assertEquals(1, geometry(five, 0f, perMinute = 3f).look().gapRoom[0])
    }

    // A crowded journey: four short city rides with short changes, then three long ones.
    private val crowded = listOf(
        ride("M11", a, hub, 10, 14),
        ride("S1", hub, c, 16, 21),
        ride("U9", c, a, 23, 26),
        ride("S41", a, hub, 28, 33),
        ride("N44", hub, c, 40, 220),
        ride("ICE 43", c, a, 240, 400),
        ride("IC 55", a, c, 410, 540),
    )

    private fun assertTimeTrue(legs: List<Leg>, combine: Boolean, perMinute: Float, windowStart: Float = 5f) {
        val starts = minutes(legs) { it.departure }
        val ends = minutes(legs) { it.arrival }
        val shape = columnGeometry(
            legs, starts, ends, windowStart, perMinute.dp, chartChanges(Journey(legs)).keys,
            labelHeight = 18.dp, cancelled = false, combine = combine,
        )
        val top = 34f
        shape.parts.forEach { part ->
            val first = part.index
            val last = part.members?.last() ?: part.index
            // Every stretch lies exactly where its times say: the axis rules.
            assertEquals(top + perMinute * (starts[first] - windowStart), part.top.value, 0.01f)
            assertEquals(top + perMinute * (ends[last] - windowStart), (part.top + part.height).value, 0.01f)
        }
        // The long rides keep exactly their height by the clock.
        val long = shape.parts.first { it.leg.line == "N44" }
        assertEquals(perMinute * 180f, long.height.value, 0.01f)
        assertEquals(top + perMinute * (ends.last() - windowStart) + 2f, shape.arrivalTop.value, 0.01f)
    }

    @Test
    fun blocksOfACrowdedJourneyLieAtTheirTimesEachBySelf() {
        for (scale in listOf(0.5f, 1f, 3f)) assertTimeTrue(crowded, combine = false, perMinute = scale)
        val look = geometry(crowded, 0f, perMinute = 0.5f).look()
        // Each ride its own stretch; the short ones without names, as thin bars where tiny.
        assertEquals(crowded.indices.toList(), look.legIndices)
        assertEquals(listOf(false, false, false, false, true, true, true), look.named)
        assertEquals(listOf(true, true, true, true, false, false, false), look.thin)
        assertTrue(look.combined.all { it == 0 })
        // At 1 dp per minute the rides of 5 minutes are small blocks without a name, the others thin bars.
        val closer = geometry(crowded, 0f, perMinute = 1f).look()
        assertEquals(listOf(true, false, true, false, false, false, false), closer.thin)
        assertEquals(listOf(false, false, false, false, true, true, true), closer.named)
        // And the changes of 2 minutes between them show a dot only.
        assertEquals(listOf(0, 0, 0), closer.gapRoom.take(3))
    }

    @Test
    fun blocksOfACrowdedJourneyLieAtTheirTimesCombined() {
        for (scale in listOf(0.5f, 1f, 3f)) assertTimeTrue(crowded, combine = true, perMinute = scale)
        val shape = columnGeometry(
            crowded, minutes(crowded) { it.departure }, minutes(crowded) { it.arrival }, 0f, 0.5.dp,
            emptySet(), labelHeight = 18.dp, cancelled = false, combine = true,
        )
        // The four short rides are one stretch, from the first departure to the last arrival.
        assertEquals(listOf(0, 4, 5, 6), shape.look().legIndices)
        assertEquals(listOf(4, 0, 0, 0), shape.look().combined)
        val group = shape.parts[0]
        assertEquals(listOf(0, 1, 2, 3), group.members!!.toList())
        // Each band where its ride lies in it: S1 from minute 16 to 21, 6 to 11 minutes after the start.
        assertEquals(3f, group.bands!![2], 0.01f)
        assertEquals(5.5f, group.bands!![3], 0.01f)
        // At a scale where they fit, nothing is combined.
        assertTrue(geometry(crowded, 0f, perMinute = 6f).look().combined.all { it == 0 })
    }

    @Test
    fun axisTicksFollowTheScale() {
        assertEquals(10, axisStep(3.dp))
        assertEquals(20, axisStep(1.5.dp))
        assertEquals(30, axisStep(1.dp))
        assertEquals(60, axisStep(0.5.dp))
        assertEquals(AxisTicks(10, 75, 10), axisTicks(4f, 75.5f, 3.dp))
    }

    @Test
    fun theNowMarkerAndTheLabelsAroundIt() {
        assertTrue(nowInWindow(9.6f, 10f, 40f))
        assertFalse(nowInWindow(9f, 10f, 40f))
        assertFalse(nowInWindow(41f, 10f, 40f))
        assertTrue(givesWayToNow(100.dp, 110.dp))
        assertFalse(givesWayToNow(100.dp, 116.dp))
        assertFalse(givesWayToNow(100.dp, null))
    }

    @Test
    fun theChartScrollsOnlyWhenClearlyTallerThanTheCard() {
        // 34 + 3 * 100 + 50 = 384 dp wanted.
        assertEquals(384f, chartHeightOf(0.dp, 3.dp, 100f, 300.dp).value, 0.01f)
        // A hair more than the card is no reason to scroll.
        assertEquals(382f, chartHeightOf(0.dp, 3.dp, 100f, 382.dp).value, 0.01f)
        // The deepest column decides where it is deeper.
        assertEquals(450f, chartHeightOf(400.dp, 3.dp, 100f, 300.dp).value, 0.01f)
    }

    @Test
    fun theSameChangeLooksTheSameInEveryJourney() {
        // The same bus to the same next ride, in a journey every search found and in one that only
        // the search without change times found (which rates the journey stricter).
        val bus = ride("X1", a, hub, 27, 37)
        val need = changeNeed(bus, ride("X2", hub, c, 40, 60))
        val next = ride("X2", hub, c, 37 + need + 1, 70)
        val calm = Journey(listOf(bus, next), foundLevel = TransferComfort.Relaxed)
        val strict = Journey(listOf(bus, next), foundLevel = TransferComfort.Risky)
        val first = chartChanges(calm).getValue(1)
        val second = chartChanges(strict).getValue(1)
        assertEquals(first.minutes, second.minutes)
        assertEquals(first.tightness, second.tightness)
        assertEquals(first.tone, second.tone)
        assertEquals(first.pill, second.pill)
        assertEquals(need + 1, first.minutes)
    }

    @Test
    fun everyChangeOfAMinuteOrMoreIsLabelled() {
        val legs = listOf(ride("X1", a, hub, 10, 20), ride("X2", hub, c, 21, 40))
        assertEquals(setOf(1), chartChanges(Journey(legs)).keys)
        // Staying on board with a stop of a few minutes shows them too, as no change.
        val seated = listOf(ride("X1", a, hub, 10, 20, trip = "t"), ride("X1", hub, c, 24, 40, trip = "t"))
        val change = chartChanges(Journey(seated)).getValue(1)
        assertTrue(change.onBoard)
        assertFalse(change.pill)
        // A change to a cancelled ride does not happen: no label.
        val dropped = listOf(ride("X1", a, hub, 10, 20), ride("X2", hub, c, 30, 40, cancelled = true))
        assertNull(chartChanges(Journey(dropped))[1])
    }
}
