package io.github.jnbp.nextstop.wear.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The overview of a connection before it starts (#175). */
class JourneyPlanTest {
    private val minute = 60_000L

    private fun ride(line: String, from: String, to: String, dep: Long, arr: Long, platform: String? = null) =
        Leg(line, "suburban", false, from, to, dep * minute, arr * minute, 0, 0, platform, "S Spandau (Berlin)", false)

    private fun walk(from: String, to: String, dep: Long, arr: Long) =
        Leg(null, "walk", true, from, to, dep * minute, arr * minute, 0, 0, null, null, false)

    @Test
    fun ridesWithTheirChangeAndTheWalkAtTheEnd() {
        val journey = Journey(
            listOf(
                ride("S41", "S+U Westhafen (Berlin)", "S Westkreuz (Berlin)", 0, 10, platform = "2"),
                walk("S Westkreuz (Berlin)", "S Westkreuz (Berlin)", 10, 13),
                ride("S7", "S Westkreuz (Berlin)", "S Golm (Potsdam)", 20, 50),
                walk("S Golm (Potsdam)", "Am Mühlenberg 1", 50, 56),
            ),
            null,
        )
        val plan = journeyPlan(journey, "Berlin")
        assertEquals(2, plan.rides.size)
        assertEquals("S+U Westhafen", plan.rides[0].from)
        assertEquals("S Spandau", plan.rides[0].direction)
        assertEquals("2", plan.rides[0].departurePlatform)
        assertNull(plan.rides[0].rating)
        // Ten minutes for a change with three on foot: relaxed.
        assertEquals(10, plan.rides[1].gap)
        assertEquals(3, plan.rides[1].walkBefore)
        assertEquals(ChangeRating.Comfortable, plan.rides[1].rating)
        assertEquals("S Golm (Potsdam)", plan.rides[1].to)
        assertEquals(6, plan.walkEnd)
    }
}
