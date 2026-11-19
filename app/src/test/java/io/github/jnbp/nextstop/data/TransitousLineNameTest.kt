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

    /**
     * #85/#60: DELFI names the ICE line (41: Köln - Frankfurt - Nürnberg) as the route and the
     * train number (621) as the trip. Every board, the DB and the map say "ICE 621"; the chart
     * said "ICE 41", so the train seemed missing from the connections.
     */
    @Test
    fun aLongDistanceTrainShowsItsTrainNumberNotItsLineNumber() {
        fun ice(route: String?, trip: String?, display: String? = null) =
            composeLineName("HIGHSPEED_RAIL", route, trip, display, null, null, "DB Fernverkehr AG")
        assertEquals("ICE 621", ice("41", "621"))
        assertEquals("ICE 621", ice("ICE 41", "621"))
        assertEquals("ICE 621", ice("ICE 41", "621", display = "ICE 41"))
        assertEquals("ICE 1092", ice("12", "1092"))
        assertEquals("IC 2433", composeLineName("LONG_DISTANCE", "IC 32", "2433", null, null, null, "DB Fernverkehr AG"))
        // Without a train number the line stays.
        assertEquals("ICE 41", ice("41", null))
        // The route and the trip one number: as it was.
        assertEquals("ICE 621", ice("ICE", "621"))
    }

    @Test
    fun regionalTrainsKeepTheirLine() {
        // A regional route "RE1" or "1" with trip number 3809 stays the line people know.
        assertEquals("RE 1", composeLineName("REGIONAL_RAIL", "1", "3809", null, "RE", null, "DB Regio"))
        assertEquals("RE1", composeLineName("REGIONAL_RAIL", "RE1", "3809", null, null, null, "DB Regio"))
    }

    @Test
    fun aTripNumberWithItsKindStillBeatsTheLineNumber() {
        // DELFI at Strasbourg (Transitous, Nov 2026): route "83", trip "ICE 9573". The SNCF sends
        // the same train with trip "9573": both must read "ICE 9573" to be found as one train.
        assertEquals("ICE 9573", composeLineName("HIGHSPEED_RAIL", "83", "ICE 9573", "ICE 9573", null, null, "DB Fernverkehr AG"))
        assertEquals("ICE 9573", composeLineName("HIGHSPEED_RAIL", "", "9573", "ICE 9573", null, null, "SNCF VOYAGEURS"))
    }

    @Test
    fun foreignKindsKeepTheirOwnName() {
        // As Transitous sends them (Nov 2026): route empty, trip number, display name with the kind.
        fun motis(display: String, trip: String, agency: String?, mode: String = "HIGHSPEED_RAIL") =
            composeLineName(mode, "", trip, display, null, null, agency)
        for (agency in listOf("SNCF VOYAGEURS", null)) {
            assertEquals("OUIGO 7692", motis("OUIGO 7692", "7692", agency))
            assertEquals("TGV 9898", motis("TGV INOUI 9898", "9898", agency))
            assertEquals("EST 9450", motis("EST 9450", "9450", agency))
            assertEquals("NJ 40421", motis("NJ 40421", "40421", agency, "NIGHT_RAIL"))
            assertEquals("RJX 61", motis("RJX 61", "61", agency, "LONG_DISTANCE"))
            assertEquals("EC 65", motis("EC 65", "65", agency, "LONG_DISTANCE"))
            assertEquals("IC 2027", motis("IC 2027", "2027", agency, "LONG_DISTANCE"))
            assertEquals("FLX 1234", motis("FLX 1234", "1234", agency, "LONG_DISTANCE"))
            assertEquals("ES 9551", motis("ES 9551", "9551", agency))
        }
        // The German default stays for a bare number of DB Fernverkehr.
        assertEquals("ICE 9552", motis("9552", "9552", "DB Fernverkehr AG"))
    }
}
