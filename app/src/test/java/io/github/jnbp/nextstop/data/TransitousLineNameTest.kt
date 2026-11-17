package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Train names from MOTIS: a train always shows its kind and number (see composeLineName). */
class TransitousLineNameTest {
    private fun name(route: String?, trip: String?, display: String? = null, category: String? = null, longName: String? = null) =
        composeLineName("LONG_DISTANCE", route, trip, display, category, longName, "ÖBB")

    @Test
    fun railjetByItsRouteWordKeepsTheNumber() {
        assertEquals("RJ 174", name("Railjet", "174"))
        assertEquals("RJ 174", name("Railjet", null, display = "174"))
        assertEquals("RJ 174", name("Railjet", null, display = "RJ 174"))
        assertEquals("EC 171", name("EuroCity", "171"))
    }

    @Test
    fun kindRouteAndTripNumber() {
        assertEquals("RJ 174", name("RJ", "174", category = "RJ"))
        assertEquals("RJ 174", name("RJ", null, display = "RJ 174"))
        assertEquals("RJ 174", name("174", null, longName = "Railjet"))
        assertEquals("IC 2433", name(null, "2433", category = "IC"))
    }

    @Test
    fun coachesAndUnknownWordsKeepTheirRoute() {
        assertEquals("Intercity", composeLineName("COACH", "Intercity", "1234", null, null, null, "Some Bus"))
        assertEquals("Flughafen-Express", name("Flughafen-Express", "1234"))
    }
}
