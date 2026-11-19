package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #57: a search in Berlin early in the morning. Two U3 trains ran bunched: the first one five
 * minutes late, the second one on time right behind it. The chart showed a U3 block that ran on
 * past the departure of the next bus (an X11 drawn inside the U3), a U3 with a second U3 inside,
 * and dozens of columns that only differed in the last bus. Made-up times at real stops of the
 * line's outer end.
 */
class ChartSanityTest {
    private fun at(clock: String): OffsetDateTime = OffsetDateTime.parse("2026-11-18T$clock:00+01:00")
    private val thielplatz = Stop("900000051201", "U Thielplatz", 52.451, 13.282, null)
    private val dahlem = Stop("900000051301", "U Dahlem-Dorf", 52.457, 13.290, null)
    private val helene = Stop("900000050201", "U Oskar-Helene-Heim", 52.450, 13.269, null)
    private val krumme = Stop("900000050301", "U Krumme Lanke", 52.443, 13.241, null)
    private val zehlendorf = Stop("900000049201", "Rathaus Zehlendorf", 52.434, 13.259, null)

    private fun ride(
        line: String,
        product: String,
        from: Stop,
        to: Stop,
        leaves: String,
        planLeaves: String,
        arrives: String,
        planArrives: String,
        trip: String,
        direction: String?,
        fromPlatform: String? = null,
        toPlatform: String? = null,
    ) = Leg(
        line, product, false, from.name, to.name, at(leaves), at(planLeaves), at(arrives), at(planArrives),
        fromPlatform, toPlatform, direction, false, realtime = true,
        fromPlace = from, toPlace = to, tripId = trip,
    )

    private fun walk(from: Stop, to: Stop, leaves: String, arrives: String) = Leg(
        null, "", true, from.name, to.name, at(leaves), at(leaves), at(arrives), at(arrives),
        null, null, null, false, fromPlace = from, toPlace = to,
    )

    // The first U3, five minutes late: the traveller's.
    private val u3aToHelene = ride("U3", "subway", dahlem, helene, "06:51", "06:46", "06:54", "06:49", "u3-a", "U Krumme Lanke", "1", "1")
    // Its own prognosis further on keeps the whole delay (and more): 07:03 at Krumme Lanke.
    private val u3aToKrumme = ride("U3", "subway", dahlem, krumme, "06:51", "06:46", "07:03", "06:53", "u3-a", "U Krumme Lanke", "1", "2")
    // The second U3, on time behind it.
    private val u3bFromHelene = ride("U3", "subway", helene, krumme, "06:58", "06:58", "07:01", "07:01", "u3-b", "U Krumme Lanke", "1", "2")
    private val x11 = ride("X11", "bus", krumme, zehlendorf, "07:02", "07:02", "07:08", "07:08", "x11-1", "S Lichterfelde Ost")
    private val bus118 = ride("118", "bus", krumme, zehlendorf, "07:11", "07:11", "07:19", "07:19", "118-1", "Rathaus Zehlendorf")

    // The journey the provider sent: the first U3 all the way, then the 118.
    private val provider = Journey(listOf(walk(thielplatz, dahlem, "06:43", "06:51"), u3aToKrumme, bus118))
    // A tighter journey made in the app (TightJourneys): the first U3 to Oskar-Helene-Heim, then
    // the connections from there: the second U3 and the X11. Staying seated joins the two U3.
    private val tight = spliceJourney(
        Journey(listOf(walk(thielplatz, dahlem, "06:43", "06:51"), u3aToHelene)),
        2,
        Journey(listOf(u3bFromHelene, x11)),
    )

    @Test
    fun aSeatedRideNeverEndsAfterTheNextRideLeaves() {
        assertEquals("the two U3 are one ride", 2, tight.rides.size)
        assertEquals("joined by staying seated", 2, tight.rides[0].seatedParts.size)
        assertTrue(tight.split != null)
        val joined = withSeatedTimes(listOf(provider, tight))[1]
        val u3 = joined.rides[0]
        val bus = joined.rides[1]
        assertEquals("one U3, one label", "U3", u3.line)
        assertEquals(2, joined.rides.size)
        assertFalse("the U3 must be off before the X11 leaves", u3.arrival.isAfter(bus.departure))
        // The vehicle ahead cannot get there after the one behind: the time of the one behind stands.
        assertEquals(at("07:01"), u3.arrival)
    }

    @Test
    fun aPlausibleOwnTimeOfTheSeatedVehicleIsStillTaken() {
        // Its own prognosis says 07:00 at Krumme Lanke: earlier than the vehicle behind, so it is taken.
        val own = provider.copy(legs = listOf(provider.legs[0], u3aToKrumme.copy(arrival = at("07:00")), bus118))
        val u3 = withSeatedTimes(listOf(own, tight))[1].rides[0]
        assertEquals(at("07:00"), u3.arrival)
    }

    @Test
    fun noRideOfAColumnRunsIntoTheNextOne() {
        // As the chart got it: the first U3 until 07:08, a second U3 from 07:01 to 07:03, the X10 at 07:09.
        val legs = listOf(
            ride("U3", "subway", dahlem, helene, "06:51", "06:46", "07:08", "06:56", "u3-a", "U Krumme Lanke"),
            ride("U3", "subway", helene, krumme, "07:01", "07:01", "07:03", "07:03", "u3-b", "U Krumme Lanke"),
            walk(krumme, krumme, "07:03", "07:04"),
            ride("X10", "bus", krumme, zehlendorf, "07:09", "07:09", "07:13", "07:13", "x10-1", "Rathaus Zehlendorf"),
        )
        val starts = floatArrayOf(51f, 61f, 63f, 69f)
        val ends = floatArrayOf(68f, 63f, 64f, 73f)
        val fixed = rideEndsWithoutOverlap(legs, starts, ends)
        assertEquals(61f, fixed[0])
        assertEquals(63f, fixed[1])
        assertEquals(73f, fixed[3])
        assertTrue(ridesOverlap(legs, starts, ends))
        assertFalse(ridesOverlap(legs, starts, fixed))
        // Nothing to mend: the very same array.
        val fine = floatArrayOf(60f, 63f, 64f, 73f)
        assertTrue(rideEndsWithoutOverlap(legs, starts, fine) === fine)
    }

    @Test
    fun aSeatedRideThatLaterLiveTimesPushPastTheNextRideIsClamped() {
        val merged = tight
        val late = merged.copy(legs = merged.legs.map { if (it.seatedParts.isNotEmpty()) it.copy(arrival = at("07:05")) else it })
        val fixed = withSeatingInBounds(late)
        assertEquals(at("07:02"), fixed.rides[0].arrival)
        assertTrue(withSeatingInBounds(merged) === merged)
    }

    @Test
    fun theSameRidesTwiceAreOneColumn() {
        // The provider's journey and the one made in the app ride the very same vehicles.
        val providerTo118 = provider
        val madeTo118 = spliceJourney(
            Journey(listOf(walk(thielplatz, dahlem, "06:42", "06:51"), u3aToHelene)),
            2,
            Journey(listOf(u3bFromHelene, bus118)),
        )
        assertEquals(ridesKey(providerTo118), ridesKey(madeTo118))
        assertEquals(1, withoutDuplicateRides(listOf(providerTo118, madeTo118)).size)
        // Another bus is another journey.
        assertEquals(2, withoutDuplicateRides(listOf(providerTo118, tight)).size)
    }

    private fun lastBus(line: String, arrives: String) = Journey(
        listOf(
            walk(thielplatz, dahlem, "06:43", "06:51"),
            u3aToKrumme,
            ride(line, "bus", krumme, zehlendorf, "07:03", "07:03", arrives, arrives, "$line-1", "Rathaus Zehlendorf"),
        ),
    )

    @Test
    fun theSameRidesWithOnlyAnotherLastBusAreOneColumnWithAlternatives() {
        val buses = listOf(
            lastBus("X10", "07:12"),
            lastBus("112", "07:13"),
            lastBus("118", "07:11"),
            lastBus("115", "07:14"),
            lastBus("623", "07:15"),
            lastBus("285", "07:16"),
        )
        val grouped = groupFeederAlternatives(buses)
        assertEquals(1, grouped.size)
        assertEquals("118", grouped[0].rides.last().line)
        assertEquals(5, grouped[0].groupedAlternatives)
        // An arrival more than five minutes after the best one is a column of its own.
        val late = lastBus("285", "07:17")
        assertEquals(2, groupFeederAlternatives(buses.dropLast(1) + late).size)
        // A long last ride is no feeder: kept apart.
        val long = lastBus("X10", "07:12").let { j -> j.copy(legs = j.legs.dropLast(1) + j.legs.last().copy(departure = at("06:55"), plannedDeparture = at("06:55"))) }
        assertEquals(2, groupFeederAlternatives(listOf(lastBus("118", "07:11"), long)).size)
        // Other first rides: kept apart.
        val otherTrain = Journey(listOf(u3bFromHelene, ride("115", "bus", krumme, zehlendorf, "07:03", "07:03", "07:12", "07:12", "115-2", "Rathaus Zehlendorf")))
        assertEquals(2, groupFeederAlternatives(listOf(lastBus("118", "07:11"), otherTrain)).size)
    }

    @Test
    fun onlyGenuinelyNewJourneysCountAsTighterAndAtMostSix() {
        val shown = listOf(lastBus("118", "07:11"))
        // Variants of the shown one (another last bus): no news.
        val variants = listOf(lastBus("X10", "07:12"), lastBus("623", "07:13"))
        // Ten journeys with ten other trains: new, but at most six count.
        val others = (0 until 10).map { n ->
            val leaves = "07:%02d".format(20 + n * 3)
            val arrives = "07:%02d".format(29 + n * 3)
            Journey(listOf(ride("U3", "subway", dahlem, krumme, leaves, leaves, arrives, arrives, "u3-$n", "U Krumme Lanke")))
        }
        val extra = newExtras(variants + others, shown)
        assertEquals(TIGHTER_EXTRA_CAP, extra.size)
        assertEquals(others.take(TIGHTER_EXTRA_CAP), extra)
    }
}
