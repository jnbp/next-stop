package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #271: the morning train Berlin Suedkreuz - Dresden-Neustadt comes from DELFI (Transitous) as
 * "ICE 171", while DB and VBB HAFAS know the run as the Railjet "RJ 171": the HAFAS name of
 * the same run (train number, station, planned minute) wins; without such an answer the
 * Transitous name stays.
 */
class RideKindsTest {
    private val planned: OffsetDateTime = OffsetDateTime.parse("2026-11-19T07:37:00+01:00")
    private val suedkreuz = Stop("tr:de-DELFI_suedkreuz", "Berlin Südkreuz", 52.4753, 13.3655, null)
    private val dresden = Stop("tr:de-DELFI_neustadt", "Dresden-Neustadt", 51.0654, 13.7408, null)

    private fun hafas(line: String, minutes: Long = 0, product: String = "express", color: Int? = null) = Departure(
        line = line, product = product, direction = "Praha hl.n.", time = planned.plusMinutes(minutes + 7),
        plannedTime = planned.plusMinutes(minutes), delaySeconds = 420, platform = "3", cancelled = false,
        tripId = "1|1234|5|86|19112026", providerColor = color,
    )

    private fun ride(line: String = "ICE 171", tripId: String = "tr:20261119_07:37_de-DELFI_171") = Leg(
        line = line, product = "express", walking = false, from = suedkreuz.name, to = dresden.name,
        departure = planned, plannedDeparture = planned, arrival = planned.plusMinutes(115), plannedArrival = planned.plusMinutes(115),
        departurePlatform = "3", arrivalPlatform = null, direction = "Decin hl.n.", cancelled = false,
        fromPlace = suedkreuz, toPlace = dresden, tripId = tripId, providerColor = 0xFFEC0016.toInt(), providerTextColor = -1,
    )

    private fun walk() = Leg(
        line = null, product = "", walking = true, from = "Start", to = suedkreuz.name,
        departure = planned.minusMinutes(10), plannedDeparture = null, arrival = planned.minusMinutes(2), plannedArrival = null,
        departurePlatform = null, arrivalPlatform = null, direction = null, cancelled = false,
    )

    @Test
    fun trainNumbers() {
        assertEquals("171", longDistanceNumber("ICE 171"))
        assertEquals("171", longDistanceNumber("RJ171"))
        assertEquals("9898", longDistanceNumber("TGV INOUI 9898"))
        assertEquals("65", longDistanceNumber("EC 065"))
        assertNull(longDistanceNumber("RE 1"))
        assertNull(longDistanceNumber("S41"))
        assertNull(longDistanceNumber("ICE"))
        assertNull(longDistanceNumber(null))
    }

    /** #279: Rejseplanen's copy of the Railjet Berlin - Hamburg came as "RJ 000384". */
    @Test
    fun trainNumbersNeverShowLeadingZeros() {
        assertEquals("RJ 384", withoutTrainNumberZeros("RJ 000384"))
        assertEquals("ICE 601", withoutTrainNumberZeros("ICE0601"))
        assertEquals("TGV INOUI 9898", withoutTrainNumberZeros("TGV INOUI 09898"))
        assertEquals("ICE 384", withoutTrainNumberZeros("ICE 384"))
        assertEquals("FLX 040", withoutTrainNumberZeros("FLX 040"))
        assertEquals("RE 01", withoutTrainNumberZeros("RE 01"))
        assertEquals("040", withoutTrainNumberZeros("040"))
        assertNull(withoutTrainNumberZeros(null))
        assertEquals("RJ 384", composeLineName("LONG_DISTANCE", null, "000384", null, "RJ", null, "DSB"))
        assertEquals("RJ 384", composeLineName("HIGHSPEED_RAIL", "RJ", "000384", null, null, null, null))
        assertEquals("RJ 384", withTrainKind("000384", "express", null, "RJ"))
        assertEquals("RJ 384", withTrainKind("RJ", "express", "000384"))
        // Matching compares the numbers without zeros: the HAFAS run "RJ 384" is the same train.
        assertEquals(longDistanceNumber("RJ 384"), longDistanceNumber("RJ 000384"))
        assertFalse(hafasNameWins("RJ 384", "RJ 384"))
        assertTrue(hafasNameWins("ICE 384", "RJ 000384"))
        assertEquals("RJ 384", matchingRun("ICE 000384", planned, listOf(hafas("RJ 384")))?.line)
    }

    @Test
    fun theHafasKindWinsForTheSameNumber() {
        assertTrue(hafasNameWins("ICE 171", "RJ 171"))
        assertTrue(hafasNameWins("ICE 179", "EC 179"))
        assertTrue(hafasNameWins("IC 2", "ICE 2"))
        // A real ICE on the line: HAFAS says ICE, nothing changes.
        assertFalse(hafasNameWins("ICE 171", "ICE 171"))
        assertFalse(hafasNameWins("ICE 171", "ICE171"))
        assertFalse(hafasNameWins("ICE 171", "RJ 173"))
        assertFalse(hafasNameWins("ICE 171", "RE 171"))
        assertFalse(hafasNameWins("RE 3", "RJ 3"))
        assertFalse(hafasNameWins("ICE 171", null))
    }

    @Test
    fun theRunIsFoundByNumberAndMinute() {
        val runs = listOf(hafas("ICE 1513", 0), hafas("RJ 171", 1), hafas("EC 171", 40))
        assertEquals("RJ 171", matchingRun("ICE 171", planned, runs)?.line)
        // Too far off in time: another run of that number (the next day's would be too).
        assertNull(matchingRun("ICE 171", planned, listOf(hafas("RJ 171", 5))))
        assertNull(matchingRun("ICE 171", planned, listOf(hafas("RJ 171", -4))))
        assertNull(matchingRun("ICE 171", null, runs))
        assertNull(matchingRun("RE 5", planned, runs))
        // The nearest in time of two.
        assertEquals("EC 171", matchingRun("ICE 171", planned, listOf(hafas("RJ 171", 3), hafas("EC 171", -1)))?.line)
    }

    @Test
    fun theFixCarriesKindProductAndColour() {
        val fix = kindFixOf("ICE 171", hafas("RJ 171"))!!
        assertEquals(RideKindFix("RJ 171", "express", null, null), fix)
        assertNull(kindFixOf("ICE 171", hafas("ICE 171")))
        val fixed = ride().withKindFix(fix)
        assertEquals("RJ 171", fixed.line)
        // The red of the ICE route goes: RJ gets the app's own colour for its name.
        assertNull(fixed.providerColor)
        assertNull(fixed.providerTextColor)
        assertEquals("ICE 171", fixed.timetableLine)
        assertSame(fixed, fixed.withKindFix(fix))
    }

    @Test
    fun aFixedJourneyKeepsItsId() {
        val journey = Journey(legs = listOf(walk(), ride()))
        val fix = RideKindFix("RJ 171", "express", null, null)
        val fixed = withKindFixes(journey) { if (it == "tr:20261119_07:37_de-DELFI_171") fix else null }
        assertEquals("RJ 171", fixed.legs[1].line)
        assertEquals(journey.id, fixed.id)
        assertEquals(journeyKey(journey), journeyKey(fixed))
        assertSame(journey, withKindFixes(journey) { null })
    }

    @Test
    fun boardsAndVehiclesTakeTheFix() {
        val fix = RideKindFix("RJ 171", "express", null, null)
        val board = listOf(hafas("ICE 171").copy(tripId = "tr:a", providerColor = 1), hafas("RE 3", product = "regional").copy(tripId = "tr:b"))
        val fixed = withDepartureKindFixes(board) { if (it == "tr:a") fix else null }
        assertEquals(listOf("RJ 171", "RE 3"), fixed.map { it.line })
        assertNull(fixed[0].providerColor)
        assertSame(board, withDepartureKindFixes(board) { null })
    }

    @Test
    fun aRestoredTripKeepsItsFixedName() {
        val known = Journey(legs = listOf(walk(), ride().withKindFix(RideKindFix("RJ 171", "express", null, null))))
        val fresh = Journey(legs = listOf(walk(), ride()))
        assertEquals("RJ 171", keptKinds(fresh, known).legs[1].line)
        // The timetable names it otherwise now: the fresh name counts.
        val renamed = Journey(legs = listOf(walk(), ride(line = "EC 171")))
        assertEquals("EC 171", keptKinds(renamed, known).legs[1].line)
        assertSame(fresh, keptKinds(fresh, fresh))
    }

    @Test
    fun lookupPlacesPreferTheRegionalArea() {
        val leg = ride().copy(
            fromPlace = dresden,
            stopovers = listOf(
                Stopover("Elsterwerda", null, null, Stop("x", "Elsterwerda", 51.46, 13.52, null), plannedDeparture = planned.plusMinutes(50)),
                Stopover("Berlin Südkreuz", null, null, suedkreuz, plannedDeparture = planned.plusMinutes(110)),
            ),
        )
        val places = kindLookupPlaces(leg, { latitude, _ -> latitude > 52.0 })
        assertEquals(2, places.size)
        assertEquals(suedkreuz.position, places[0].first)
        assertEquals(planned.plusMinutes(110), places[0].second)
        assertEquals(dresden.position, places[1].first)
    }

    @Test
    fun theStoreAsksEachRunOnce() {
        RideKindFixes.clear()
        val id = "tr:run"
        assertTrue(RideKindFixes.wanted(id, 0))
        assertTrue(RideKindFixes.claim(id))
        assertFalse(RideKindFixes.wanted(id, 0))
        assertFalse(RideKindFixes.claim(id))
        RideKindFixes.release(id)
        RideKindFixes.markChecked(id, 0)
        assertFalse(RideKindFixes.wanted(id, 60_000))
        assertTrue(RideKindFixes.wanted(id, 31L * 60 * 1000))
        val before = RideKindFixes.version.value
        RideKindFixes.put(id, RideKindFix("RJ 171", "express", null, null), 0)
        assertEquals(before + 1, RideKindFixes.version.value)
        assertEquals("RJ 171", RideKindFixes.get(id, 1000)?.line)
        assertNull(RideKindFixes.get(id, 25L * 60 * 60 * 1000))
        RideKindFixes.clear()
    }
}
