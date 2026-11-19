package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #35a: a search Bonn → Köln Messe/Deutz "über Bonn" at 20:39 came back with the next morning's
 * 05:21 alone, although RB26, RB48 and RE5 passed Bonn Hbf all evening.
 */
class ViaSoonTest {
    private fun at(day: Int, clock: String): OffsetDateTime = OffsetDateTime.parse("2026-11-%02dT$clock:00+01:00".format(day))
    private val bonnHbf = Stop("de:05314:61908", "Bonn Hbf", 50.7320, 7.0970, null)
    private val deutz = Stop("de:05315:11209", "Köln Messe/Deutz", 50.9410, 6.9750, null)

    private fun journey(leaves: OffsetDateTime) = Journey(
        listOf(
            Leg(
                "RB48", "regional", false, bonnHbf.name, deutz.name, leaves, leaves, leaves.plusMinutes(25), leaves.plusMinutes(25),
                null, null, "Wuppertal Hbf", false, fromPlace = bonnHbf, toPlace = deutz,
            ),
        ),
    )

    private val asked = at(17, "20:39")
    private val tomorrow = listOf(journey(at(18, "05:21")))
    private val tonight = listOf(journey(at(17, "20:54")), journey(at(17, "21:04")))

    @Test
    fun onlyTomorrowMorningIsNotFoundSoon() {
        assertTrue(viaFoundTooLate(asked, tomorrow))
        assertTrue(viaFoundTooLate(asked, emptyList()))
        assertFalse(viaFoundTooLate(asked, tonight))
    }

    @Test
    fun theSearchPutTogetherFromTwoWinsWhereItLeavesEarlier() {
        assertTrue(earlierVia(asked, tomorrow, tonight))
        assertTrue(earlierVia(asked, emptyList(), tonight))
        // Found soon in one request: that one stays.
        assertFalse(earlierVia(asked, tonight, tomorrow))
        // Nothing better put together: the single answer stays (and the list says it is late).
        assertFalse(earlierVia(asked, tomorrow, emptyList()))
        assertFalse(earlierVia(asked, tomorrow, null))
    }
}
