package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Changes of platform on a followed journey, with made-up stations. */
class PlatformWatchTest {
    private fun at(clock: String): OffsetDateTime = OffsetDateTime.parse("2026-01-01T${clock}:00+01:00")

    private fun ride(dep: String?, arr: String?, plannedDep: String? = null, plannedArr: String? = null) = Leg(
        "RE1", "regional", false, "Example A", "Example B", at("10:00"), at("10:00"), at("10:30"), at("10:30"),
        dep, arr, null, false, tripId = "re1", plannedDeparturePlatform = plannedDep, plannedArrivalPlatform = plannedArr,
    )

    @Test
    fun theProvidersPlannedPlatformCounts() {
        val shifts = platformShifts(Journey(listOf(ride("5", "2", plannedDep = "3"))), emptyMap())
        assertEquals(1, shifts.size)
        assertEquals("3", shifts[0].planned)
        assertEquals("5", shifts[0].now)
        assertTrue(shifts[0].departure)
    }

    @Test
    fun withoutAPlannedPlatformTheFirstSeenOneCounts() {
        val first = Journey(listOf(ride("3", "2")))
        val seen = rememberPlatforms(first, emptyMap())
        assertTrue(platformShifts(first, seen).isEmpty())
        val later = Journey(listOf(ride("3", "4")))
        val shifts = platformShifts(later, rememberPlatforms(later, seen))
        assertEquals(1, shifts.size)
        assertEquals("2", shifts[0].planned)
        assertEquals("4", shifts[0].now)
        assertEquals(false, shifts[0].departure)
    }

    @Test
    fun theSamePlatformWrittenOtherwiseIsNoChange() {
        assertTrue(platformShifts(Journey(listOf(ride(" 3", "2", plannedDep = "3"))), emptyMap()).isEmpty())
    }

    @Test
    fun aChangeIsToldOnceAndNotForARideThatIsOver() {
        val shifts = platformShifts(Journey(listOf(ride("5", "2", plannedDep = "3"))), emptyMap())
        assertEquals(1, newPlatformShifts(shifts, emptySet(), -1).size)
        assertTrue(newPlatformShifts(shifts, setOf(shifts[0].key), -1).isEmpty())
        assertTrue(newPlatformShifts(shifts, emptySet(), 0).isEmpty())
    }
}
