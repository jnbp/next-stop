package io.github.jnbp.nextstop.ui

import androidx.compose.ui.unit.dp
import io.github.jnbp.nextstop.data.Leg
import io.github.jnbp.nextstop.data.Stop
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #35d: after an IC 55 the chart drew a small empty dashed block: the outline of the planned
 * departure of a late short ride (an RB48 of 3 minutes from Köln Hbf to Köln Messe/Deutz),
 * at least 6 dp high, above the thin bar of the ride itself.
 */
class ChartGhostTest {
    private val start: OffsetDateTime = OffsetDateTime.parse("2026-11-17T20:00:00+01:00")
    private fun at(minutes: Long) = start.plusMinutes(minutes)
    private val bonn = Stop("de:05314:61908", "Bonn Hbf", 50.732, 7.097, null)
    private val koeln = Stop("de:05315:11201", "Köln Hbf", 50.943, 6.959, null)
    private val deutz = Stop("de:05315:11209", "Köln Messe/Deutz", 50.941, 6.975, null)

    private fun ride(line: String, product: String, from: Stop, to: Stop, leaves: Long, arrives: Long, late: Long = 0) = Leg(
        line, product, false, from.name, to.name, at(leaves + late), at(leaves), at(arrives + late), at(arrives),
        null, null, null, false, fromPlace = from, toPlace = to, tripId = "$line@$leaves",
    )

    private fun minutes(legs: List<Leg>, of: (Leg) -> OffsetDateTime) =
        FloatArray(legs.size) { java.time.Duration.between(start, of(legs[it])).seconds / 60f }

    private fun geometry(legs: List<Leg>, perMinute: Float) = columnGeometry(
        legs, minutes(legs) { it.departure }, minutes(legs) { it.arrival }, 0f, perMinute.dp,
        emptySet(), labelHeight = 18.dp, cancelled = false,
    )

    @Test
    fun aShortLateRideHasNoEmptyOutlineAboveIt() {
        val legs = listOf(
            ride("IC 55", "express", bonn, koeln, 10, 40, late = 15),
            ride("RB48", "regional", koeln, deutz, 70, 73, late = 9),
        )
        val shape = geometry(legs, perMinute = 3f)
        // 3 minutes at 3 dp: lower than a block with its name, no outline above it.
        assertEquals(0f, shape.dashes[1].value, 0.01f)
        assertFalse(shape.look().dashed[1])
        // The IC itself, 30 minutes high, shows where it was planned.
        assertTrue(shape.look().dashed[0])
    }

    @Test
    fun aDelayTooSmallToSeeHasNoOutline() {
        val legs = listOf(ride("RE5", "regional", bonn, koeln, 10, 40, late = 2))
        // 2 minutes at 1 dp: 2 dp, below what is drawn as an outline (it was raised to 6 dp).
        assertEquals(0f, geometry(legs, perMinute = 1f).dashes[0].value, 0.01f)
        // At 3 dp per minute the 6 dp are the delay itself.
        assertEquals(6f, geometry(legs, perMinute = 3f).dashes[0].value, 0.01f)
    }
}
