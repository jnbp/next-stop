package io.github.jnbp.nextstop.ui

import java.time.Duration
import java.time.OffsetDateTime
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.Leg
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.TIGHTER_EXTRA_CAP
import io.github.jnbp.nextstop.data.TransferComfort
import io.github.jnbp.nextstop.data.journeyChangesOf
import io.github.jnbp.nextstop.data.journeyComfort
import io.github.jnbp.nextstop.data.journeyKey
import io.github.jnbp.nextstop.data.mergeSearchPages
import io.github.jnbp.nextstop.data.rideEndsWithoutOverlap
import io.github.jnbp.nextstop.data.ridesKey
import io.github.jnbp.nextstop.data.ridesOverlap
import io.github.jnbp.nextstop.data.searchOrder
import io.github.jnbp.nextstop.data.spliceJourney
import io.github.jnbp.nextstop.data.withSeatingInBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #57 as a whole: a large, realistic search (pages merged, tighter journeys made in the app and
 * added, then selected for the chart) keeps its invariants: no ride runs into the next one, the
 * same rides show once, the tightest level adds at most six, a ride joined by staying seated is
 * one ride with one label. Made-up times at real stops of the U3's outer end.
 */
class BigSearchInvariantsTest {
    private fun at(minutes: Long): OffsetDateTime = OffsetDateTime.parse("2026-11-18T06:00:00+01:00").plusMinutes(minutes)
    private val thielplatz = Stop("900000051201", "U Thielplatz", 52.451, 13.282, null)
    private val dahlem = Stop("900000051301", "U Dahlem-Dorf", 52.457, 13.290, null)
    private val helene = Stop("900000050201", "U Oskar-Helene-Heim", 52.450, 13.269, null)
    private val krumme = Stop("900000050301", "U Krumme Lanke", 52.443, 13.241, null)
    private val zehlendorf = Stop("900000049201", "Rathaus Zehlendorf", 52.434, 13.259, null)

    private fun ride(line: String, product: String, from: Stop, to: Stop, leaves: Long, arrives: Long, delay: Long = 0, trip: String, platform: String? = null) = Leg(
        line, product, false, from.name, to.name, at(leaves + delay), at(leaves), at(arrives + delay), at(arrives),
        platform, platform, if (product == "subway") "U Krumme Lanke" else "Rathaus Zehlendorf", false, realtime = true,
        fromPlace = from, toPlace = to, tripId = trip,
    )

    private fun walk(from: Stop, to: Stop, leaves: Long, arrives: Long) = Leg(
        null, "", true, from.name, to.name, at(leaves), at(leaves), at(arrives), at(arrives),
        null, null, null, false, fromPlace = from, toPlace = to,
    )

    private val buses = listOf("X10" to 0L, "112" to 1L, "118" to 2L, "115" to 3L, "623" to 4L, "285" to 5L)

    /** One U3 run (planned at [leaves] at Dahlem-Dorf, [delay] late) to Krumme Lanke, then one of the six buses. */
    private fun viaKrumme(run: Int, leaves: Long, delay: Long, bus: Pair<String, Long>, busLeaves: Long): Journey {
        val u3 = ride("U3", "subway", dahlem, krumme, leaves, leaves + 7, delay, "u3-$run", "1")
        val last = ride(bus.first, "bus", krumme, zehlendorf, busLeaves + bus.second, busLeaves + bus.second + 8, trip = "${bus.first}-$busLeaves")
        return Journey(listOf(walk(thielplatz, dahlem, leaves + delay - 8, leaves + delay), u3, last), refreshToken = "p$run-${bus.first}")
    }

    private fun page1(): List<Journey> {
        val list = ArrayList<Journey>()
        // Two bunched U3: the first five minutes late (planned 06:46, leaves 06:51), the second on time at 06:52.
        for (bus in buses) list += viaKrumme(1, 46, 5, bus, 60)
        for (bus in buses) list += viaKrumme(2, 52, 0, bus, 61)
        return list
    }

    private fun page2(): List<Journey> {
        val list = ArrayList<Journey>()
        // Later runs every five minutes, some late, each with the 118 or the X10; one late enough to run into its bus.
        for (n in 0 until 12) {
            val leaves = 60L + n * 5
            val delay = if (n % 3 == 0) 4L else 0L
            list += viaKrumme(10 + n, leaves, delay, if (n % 2 == 0) buses[2] else buses[0], leaves + 9)
        }
        // An exact duplicate of a journey of the first page (another request handle).
        list += viaKrumme(1, 46, 5, buses[2], 60).copy(refreshToken = "dup")
        return list
    }

    /** Journeys made in the app at Oskar-Helene-Heim: the late U3 there, on in the U3 behind it (seated, three minutes behind), then a bus. */
    private fun tight(): List<Journey> {
        val head = Journey(listOf(walk(thielplatz, dahlem, 43, 51), ride("U3", "subway", dahlem, helene, 46, 49, 5, "u3-1", "1")))
        return buses.map { bus ->
            spliceJourney(
                head, 2,
                Journey(listOf(ride("U3", "subway", helene, krumme, 57, 60, trip = "u3-2", platform = "1"), ride(bus.first, "bus", krumme, zehlendorf, 61 + bus.second, 69 + bus.second, trip = "${bus.first}-t"))),
            ).copy(foundLevel = TransferComfort.Risky)
        }
    }

    /** As withTighterJourneys (TightJourneys.kt, not pure): new ones by journeyKey, in bounds, in search order. */
    private fun withTighter(journeys: List<Journey>, added: List<Journey>): List<Journey> {
        val known = journeys.map { journeyKey(it) }.toMutableSet()
        val fresh = added.filter { known.add(journeyKey(it)) }.map { withSeatingInBounds(it) }
        return (journeys + fresh).sortedWith(searchOrder)
    }

    private fun minutes(time: OffsetDateTime) = Duration.between(at(0), time).seconds / 60f

    private fun assertNoOverlap(journey: Journey) {
        val legs = journey.legs
        val starts = FloatArray(legs.size) { minutes(legs[it].departure) }
        val ends = FloatArray(legs.size) { minutes(legs[it].arrival) }
        // The chart model ends every ride where the next one leaves.
        assertFalse("chart: ride overlaps in ${journey.id}", ridesOverlap(legs, starts, rideEndsWithoutOverlap(legs, starts, ends)))
        // A ride joined by staying seated is off before the next ride leaves, in the data itself.
        val rides = journey.rides
        for (i in 0 until rides.size - 1) {
            if (rides[i].seatedParts.isNotEmpty()) {
                assertFalse("seated ride runs into the next in ${journey.id}", rides[i].arrival.isAfter(rides[i + 1].departure))
            }
        }
    }

    private fun assertOneLabelPerSeatedRide(journey: Journey) {
        val rides = journey.rides
        for (i in rides.indices) {
            val ride = rides[i]
            if (ride.seatedParts.isEmpty()) continue
            assertEquals("one label", setOf(ride.line), ride.seatedParts.map { it.line }.toSet())
            if (i + 1 < rides.size) assertFalse("joined ride followed by its own line again", rides[i + 1].line == ride.line && rides[i + 1].tripId == ride.seatedParts.last().tripId)
        }
    }

    private fun merged(): List<Journey> = withTighter(mergeSearchPages(mergeSearchPages(emptyList(), page1()), page2()), tight())

    @Test
    fun aLargeSearchKeepsEveryInvariant() {
        val all = merged()
        assertTrue("at least 30 journeys", page1().size + page2().size + tight().size >= 30)
        all.forEach { assertNoOverlap(it); assertOneLabelPerSeatedRide(it) }
        assertTrue("the seated U3 is there", all.any { j -> j.rides.any { it.seatedParts.isNotEmpty() } })
        for (threshold in TransferComfort.values()) {
            val selection = journeySelection(all, threshold)
            val shown = selection.shown!!
            shown.forEach { assertNoOverlap(it); assertOneLabelPerSeatedRide(it) }
            val keys = shown.map { ridesKey(it) }
            assertEquals("same rides twice at $threshold: $keys", keys.size, keys.toSet().size)
            val ids = shown.map { it.id }
            assertEquals("same journey twice at $threshold", ids.size, ids.toSet().size)
            if (selection.nextLevel == TransferComfort.VeryRisky) assertTrue("next ${selection.nextCount}", selection.nextCount <= TIGHTER_EXTRA_CAP)
            if (threshold == TransferComfort.VeryRisky) {
                val below = journeySelection(all, TransferComfort.Risky).shown!!
                val extras = shown.filter { s -> below.none { ridesKey(it) == ridesKey(s) } }
                assertTrue("extras ${extras.size}", extras.size <= TIGHTER_EXTRA_CAP)
                assertTrue("extras not sprint: ${extras.map { journeyComfort(it) }}", extras.all { journeyChangesOf(it).isNotEmpty() && journeyComfort(it) == TransferComfort.VeryRisky })
            }
        }
        // Thirty-odd columns of six buses behind a few trains become a handful.
        val relaxed = journeySelection(all, TransferComfort.Normal).shown!!
        assertTrue("grouped: ${relaxed.size} of ${all.size}", relaxed.size < all.size / 2)
        assertTrue("nothing grouped", relaxed.any { it.groupedAlternatives > 0 })
    }

    @Test
    fun theOrderOfThePagesDoesNotMatter() {
        val one = merged().map { ridesKey(it) }
        val other = withTighter(mergeSearchPages(mergeSearchPages(emptyList(), page2()), page1()), tight()).map { ridesKey(it) }
        assertEquals(one.toSet(), other.toSet())
    }
}
