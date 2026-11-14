package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** "Wann los?" (see LeaveTime.kt): the walk estimate and the order by the time to set off. */
class LeaveTimeTest {
    private val here = 52.5219 to 13.4132
    // About 111 m due north of [here].
    private val near = 52.5229 to 13.4132

    @Test
    fun straightLineWithDetourAtThePaceSet() {
        assertEquals(116, leaveWalkSeconds(here, near, knownMeters = null, factor = 1.0))
        assertEquals(151, leaveWalkSeconds(here, near, knownMeters = null, factor = SLOWER_WALK_FACTOR))
    }

    @Test
    fun aKnownWayWinsUnlessShorterThanTheStraightLine() {
        assertEquals(160, leaveWalkSeconds(here, near, knownMeters = 200.0, factor = 1.0))
        assertEquals(116, leaveWalkSeconds(here, near, knownMeters = 50.0, factor = 1.0))
    }

    @Test
    fun tooFarToWalk() {
        assertNull(leaveWalkSeconds(here, 52.5419 to 13.4132, knownMeters = null, factor = 1.0))
    }

    @Test
    fun orderedByTheLatestTimeToSetOff() {
        val at = OffsetDateTime.parse("2026-11-14T10:00:00+01:00")
        fun departure(line: String, minutes: Long?, stopId: String) = Departure(
            line = line, product = "bus", direction = "Somewhere",
            time = minutes?.let { at.plusMinutes(it) }, plannedTime = minutes?.let { at.plusMinutes(it) },
            delaySeconds = 0, platform = null, cancelled = false,
            stop = Stop(stopId, stopId, 52.52, 13.41, null),
        )
        val far = departure("100", 5, "far")
        val close = departure("200", 2, "close")
        val unknown = departure("300", null, "close")
        val walks = mapOf("far" to 600, "close" to 60)
        val result = leaveTimes(
            listOf(close, unknown, far), here, null,
            walk = { _, to -> walks[if (to.first == 1.0) "far" else "close"] },
            place = { departure, _ -> if (departure.stop?.id == "far") 1.0 to 1.0 else 2.0 to 2.0 },
        )
        assertEquals(listOf("100", "200", "300"), result.map { it.departure.line })
        assertEquals(at.plusMinutes(5).minusSeconds(600), result[0].leaveAt)
        assertEquals(60, result[1].walkSeconds)
        assertNull(result[2].leaveAt)
        assertEquals(-60L, secondsToLeave(result[0], at.minusMinutes(4)))
    }
}
