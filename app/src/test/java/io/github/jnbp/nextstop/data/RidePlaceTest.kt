package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The colour, name and rules of a ride come from the ride itself (its product, its name,
 * where it runs, the colour its provider sent), never from where the app looks.
 */
class RidePlaceTest {
    private val berlin = LineColors.BERLIN_CENTRE
    // A town in the north of Bohemia, far outside the VBB.
    private val bohemia = 50.46 to 13.42
    private val hamburg = 53.55 to 10.0

    @After
    fun tearDown() = LineColors.clear()

    @Test
    fun aRegionalTrainNamedU1OutsideTheVbbIsNoBerlinU1() {
        // The app looks at Berlin (a journey that starts there), the train runs in Bohemia.
        LineColors.focus = berlin
        assertNull(LineColors.rideStyle("U1", "regional", bohemia))
        assertNull(LineColors.berlinRapid("U1", "regional", bohemia))
        // Not even in Berlin: a train of another product is no U-Bahn.
        assertNull(LineColors.berlinRapid("U1", "regional", berlin))
        assertNull(LineColors.berlinRapidName("U1", "regional"))
        assertNull(LineColors.berlinRapidName("S1", "subway"))
        // A U-Bahn U1 of another city is no Berlin U1 either.
        assertNull(LineColors.berlinRapid("U1", "subway", hamburg))
    }

    @Test
    fun theU1OfBerlinHasTheColourOfTheNetworkMap() {
        LineColors.focus = bohemia
        assertEquals(0xFF419328.toInt(), LineColors.rideStyle("U1", "subway", berlin)?.color)
        // An extra S1 named "1" is the S1.
        assertEquals(0xFFDA6BA2.toInt(), LineColors.rideStyle("1", "suburban", berlin)?.color)
        assertEquals("S41", LineColors.berlinRapidName("S 41", "suburban"))
    }

    @Test
    fun aRideWithTheColourOfItsProviderKeepsItWhereverTheAppLooks() {
        val green = 0xFF00A650.toInt()
        LineColors.noteProvider("585", "bus", "8e44ad", null, bohemia)
        for (focus in listOf(berlin, bohemia, null, berlin)) {
            LineColors.focus = focus
            assertEquals(green, LineColors.rideStyle("585", "bus", bohemia, provided = green)?.color)
            assertEquals(green, LineColors.rideStyle("585", "bus", berlin, provided = green)?.color)
        }
    }

    @Test
    fun aRideWithoutAColourOfItsOwnGoesByWhereItRuns() {
        LineColors.noteProvider("585", "bus", "00a650", "ffffff", bohemia)
        for (focus in listOf(berlin, bohemia, berlin)) {
            LineColors.focus = focus
            assertEquals(0xFF00A650.toInt(), LineColors.rideStyle("585", "bus", bohemia)?.color)
            // A bus 585 in Berlin is another line.
            assertNull(LineColors.rideStyle("585", "bus", berlin))
        }
    }

    @Test
    fun aColourWithoutAPlaceIsNotKept() {
        LineColors.focus = bohemia
        LineColors.noteProvider("7", "tram", "123456", null, null)
        assertNull(LineColors.lookup("7", "tram", bohemia))
    }

    @Test
    fun theRingRuleByNameHoldsForTheBerlinSBahnOnly() {
        assertEquals(RingWay.Clockwise, ringWayOf("S41", "Ring S41", "suburban", berlin))
        assertNull(ringWayOf("S41", "Ring", "suburban", hamburg))
        assertNull(ringWayOf("S41", "Ring", "bus", berlin))
        // Elsewhere the arrows still count.
        assertEquals(RingWay.Clockwise, ringWayOf("S41", "Ring ⟳", "suburban", hamburg))
    }

    @Test
    fun trainKindsAreWrittenInCapitals() {
        assertEquals("RJ 171", rapidLineName("rj 171", "express"))
        assertEquals("RJ 171", rapidLineName("rj 171", "regional"))
        assertEquals("ICE 29", rapidLineName("Ice 29", "express"))
        assertEquals("RE1", rapidLineName("re1", "regional"))
        assertEquals("RJ 171", rapidLineName("RJ 171", "express"))
        // Kinds written in small letters on purpose, and other products, stay as they are.
        assertEquals("Os 6123", rapidLineName("Os 6123", "regional"))
        assertEquals("Sp 1910", rapidLineName("Sp 1910", "regional"))
        assertEquals("R 650", rapidLineName("R 650", "regional"))
        assertEquals("Ex 1", rapidLineName("Ex 1", "regional"))
        // The word for a local product goes (see withoutProductWord): "Bus 585" is "585".
        assertEquals("585", rapidLineName("Bus 585", "bus"))
        assertEquals("re 5", rapidLineName("re 5", "bus"))
        assertEquals("4a", rapidLineName("4a", "tram"))
        assertEquals("S 1", rapidLineName("S 1", "suburban"))
        assertEquals("U1", rapidLineName("U1", "regional"))
        // A word that only begins like a kind is no kind.
        assertEquals("Regio 5", rapidLineName("Regio 5", "regional"))
        assertEquals("express", classifyProduct("regional", rapidLineName("rj 171", "regional")))
    }

    @Test
    fun aVehicleOfAKnownRideIsNamedAsTheRide() {
        val night = Vehicle("t1", "N18", "bus", "", listOf(berlin), null, null)
        val other = Vehicle("t2", "M27", "tram", "", listOf(berlin), null, null)
        val names = listOf(RideName("t1", "M48", "bus", 0xFF112233.toInt(), null))
        val shown = withRideLines(listOf(night, other), names)
        assertEquals("M48", shown[0].line)
        assertEquals(0xFF112233.toInt(), shown[0].providerColor)
        // A vehicle of no known ride keeps its own name.
        assertSame(other, shown[1])
        val unchanged = listOf(other)
        assertSame(unchanged, withRideLines(unchanged, names))
    }

    @Test
    fun theRunsOfARideAreNamedByIt() {
        val time = OffsetDateTime.parse("2026-11-14T10:00:00+01:00")
        val ride = Leg(
            line = "RE 3", product = "regional", walking = false, from = "A", to = "C",
            departure = time, plannedDeparture = time, arrival = time.plusHours(1), plannedArrival = time.plusHours(1),
            departurePlatform = null, arrivalPlatform = null, direction = null, cancelled = false,
            tripId = "a", providerColor = 0xFFDA251D.toInt(),
            throughRuns = listOf(ThroughRun("b", "RB 23", "B", null, time.plusMinutes(30), lineChanges = true)),
        )
        val walk = ride.copy(line = null, walking = true, tripId = "w", throughRuns = emptyList())
        val names = rideNames(listOf(walk, ride))
        assertEquals(listOf("a", "b"), names.map { it.tripId })
        assertEquals("RE 3", names[0].line)
        assertEquals(0xFFDA251D.toInt(), names[0].color)
        // The later part under another name has its own name and no colour of the ride.
        assertEquals("RB 23", names[1].line)
        assertNull(names[1].color)
    }

    @Test
    fun aRideRunsWhereItIsBoarded() {
        val time = OffsetDateTime.parse("2026-11-14T10:00:00+01:00")
        val ride = Leg(
            line = "U1", product = "regional", walking = false, from = "A", to = "B",
            departure = time, plannedDeparture = null, arrival = time, plannedArrival = null,
            departurePlatform = null, arrivalPlatform = null, direction = null, cancelled = false,
            path = listOf(bohemia),
        )
        assertEquals(bohemia, ride.ridePlace)
        val departure = Departure("U1", "regional", "B", time, time, null, null, false)
        assertNull(departure.ridePlace)
        assertEquals(bohemia, listOf(departure).placedAt(bohemia).single().ridePlace)
    }
}
