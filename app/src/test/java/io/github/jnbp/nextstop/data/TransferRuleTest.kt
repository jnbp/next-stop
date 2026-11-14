package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rating of a change by the time it needs at its station: 1 minute across one platform,
 * 3 to another platform, 5 at a large rail station, a walk and one minute more; relaxed from
 * 5 minutes to spare, normal from 2, risky from 0, very risky below. Made-up stops only.
 */
class TransferRuleTest {
    private val start: OffsetDateTime = OffsetDateTime.parse("2026-01-01T10:00:00+01:00")
    private fun at(minutes: Long) = start.plusMinutes(minutes)
    private fun stop(id: String, products: Set<String> = emptySet()) =
        Stop("90000000$id", "Stop $id", 52.0, 13.0, null, products = products)

    private fun ride(
        product: String,
        from: Stop,
        to: Stop,
        leaves: Long,
        arrives: Long,
        platformFrom: String? = null,
        platformTo: String? = null,
        plannedArrives: Long = arrives,
        plannedLeaves: Long = leaves,
    ) = Leg(
        "L$product", product, false, from.name, to.name, at(leaves), at(plannedLeaves), at(arrives), at(plannedArrives),
        platformFrom, platformTo, null, false, fromPlace = from, toPlace = to, tripId = "t$product$leaves",
    )

    private val a = stop("1")
    private val hub = stop("2")
    private val railHub = stop("3", setOf("suburban", "regional", "express"))
    private val b = stop("4")
    private val other = stop("5")

    @Test
    fun needs() {
        assertEquals(1L, neededMinutes(ChangeKind.SamePlatform))
        assertEquals(3L, neededMinutes(ChangeKind.SameStation))
        assertEquals(5L, neededMinutes(ChangeKind.SameStation, largeStation = true))
        assertEquals(7L, neededMinutes(ChangeKind.Walk, walkMinutes = 6))
        assertEquals(4L, neededMinutes(ChangeKind.SameStation, walkMinutes = 3))
    }

    @Test
    fun categoriesBySpare() {
        assertEquals(Tightness.Comfortable, spareTightness(5))
        assertEquals(Tightness.Tight, spareTightness(4))
        assertEquals(Tightness.Tight, spareTightness(2))
        assertEquals(Tightness.Risky, spareTightness(1))
        assertEquals(Tightness.Risky, spareTightness(0))
        // Less than the change needs by the live times: a sprint.
        assertEquals(Tightness.VeryRisky, spareTightness(-1))
        assertEquals(Tightness.VeryRisky, spareTightness(-5))
    }

    @Test
    fun otherPlatformOfSubwayStation() {
        // 6 minutes, 3 needed: 3 to spare is normal; 8 minutes is relaxed; 3 minutes risky, 2 a sprint.
        assertEquals(Tightness.Tight, changeTightness(ride("subway", a, hub, 0, 10), ride("subway", hub, b, 16, 20)))
        assertEquals(Tightness.Comfortable, changeTightness(ride("subway", a, hub, 0, 10), ride("subway", hub, b, 18, 20)))
        assertEquals(Tightness.Risky, changeTightness(ride("subway", a, hub, 0, 10), ride("subway", hub, b, 13, 20)))
        assertEquals(Tightness.VeryRisky, changeTightness(ride("subway", a, hub, 0, 10), ride("subway", hub, b, 12, 20)))
    }

    @Test
    fun samePlatformNeedsOneMinute() {
        // 3 minutes across one platform: 2 to spare is normal, where another platform would be risky.
        val arriving = ride("subway", a, hub, 0, 10, platformTo = "2")
        val next = ride("subway", hub, b, 13, 20, platformFrom = "2")
        assertEquals(Tightness.Tight, changeTightness(arriving, next))
    }

    @Test
    fun largeRailStationNeedsFive() {
        // 8 minutes at a station with regional trains: 3 to spare is normal; 6 minutes is risky.
        assertEquals(Tightness.Tight, changeTightness(ride("suburban", a, railHub, 0, 10), ride("regional", railHub, b, 18, 40)))
        assertEquals(Tightness.Risky, changeTightness(ride("suburban", a, railHub, 0, 10), ride("regional", railHub, b, 16, 40)))
    }

    @Test
    fun walkToAnotherStation() {
        // 10 minutes with 4 on foot: 5 needed, 5 to spare is relaxed; with 7 minutes 2 to spare.
        assertEquals(Tightness.Comfortable, changeTightness(ride("bus", a, hub, 0, 10), ride("tram", other, b, 20, 30), walkMinutes = 4))
        assertEquals(Tightness.Tight, changeTightness(ride("bus", a, hub, 0, 10), ride("tram", other, b, 17, 30), walkMinutes = 4))
    }

    @Test
    fun delayedConnectionCountsByItsLiveTime() {
        // Planned 1 minute before the arrival, live 6 minutes after it: 3 to spare by the live times.
        val arriving = ride("subway", a, hub, 0, 10)
        val next = ride("subway", hub, b, 16, 20, plannedLeaves = 9)
        assertEquals(Tightness.Tight, changeTightness(arriving, next))
    }

    @Test
    fun journeyCategory() {
        val journey = Journey(listOf(ride("subway", a, hub, 0, 10), ride("subway", hub, b, 16, 20)))
        assertEquals(TransferComfort.Normal, journeyComfort(journey))
        assertEquals(listOf(3L), transferBuffers(journey))
    }

    // A station complex with S-Bahn, U-Bahn and regional trains under one VBB number, as the
    // provider gives it; and its stops as platform-level ids, as another provider gives them.
    private val complex = stop("6", setOf("suburban", "subway", "regional"))
    private fun platformStop(track: String) =
        Stop("de:11000:900000006::$track", "Stop 6", 52.0, 13.0, null, products = setOf("suburban", "subway"))

    @Test
    fun subwayToSuburbanWithEqualNumbersIsNotSamePlatform() {
        // U-Bahn track 2 and S-Bahn track 2 of one complex are two platforms of separate systems.
        val subway = ride("subway", a, complex, 0, 10, platformTo = "2")
        val suburban = ride("suburban", complex, b, 14, 30, platformFrom = "2")
        assertFalse(samePlatformByNumbers(subway, suburban))
        assertFalse(samePlatformByNumbers(suburban.copy(arrivalPlatform = "2"), subway.copy(departurePlatform = "2")))
        assertEquals(ChangeKind.SameStation, changeKind(subway, suburban))
        assertFalse(samePlatformChange(subway, suburban, record = { _, _ -> false }))
        // A large station: 4 minutes against 5 needed is a sprint, not "same platform, normal".
        assertEquals(Tightness.VeryRisky, changeTightness(subway, suburban))
    }

    @Test
    fun subwayToSuburbanOnlyByDatasetRecord() {
        // The dataset names the few cross-platform interchanges of S-Bahn and U-Bahn explicitly.
        val subway = ride("subway", a, complex, 0, 10, platformTo = "2")
        val suburban = ride("suburban", complex, b, 14, 30, platformFrom = "2")
        assertTrue(samePlatformRecordAllowed(subway, suburban))
        assertTrue(samePlatformChange(subway, suburban, record = { _, _ -> true }))
        // Tram to bus: never one platform, whatever a record says.
        val tram = ride("tram", a, hub, 0, 10, platformTo = "A")
        val bus = ride("bus", hub, b, 14, 30, platformFrom = "A")
        assertFalse(samePlatformRecordAllowed(tram, bus))
        assertFalse(samePlatformChange(tram, bus, record = { _, _ -> true }))
        assertFalse(samePlatformByNumbers(tram, bus))
    }

    @Test
    fun equalNumbersAtOtherStopsOfAComplexAreNotSamePlatform() {
        // Two stops of one station (other ids) with the same track number: not one platform.
        val arriving = ride("suburban", a, platformStop("1"), 0, 10, platformTo = "2")
        val next = ride("suburban", platformStop("3"), b, 14, 30, platformFrom = "2")
        assertFalse(samePlatformByNumbers(arriving, next))
        // The very same stop and track of one family is one platform.
        val there = ride("suburban", platformStop("1"), b, 14, 30, platformFrom = "Gleis 2")
        assertTrue(samePlatformByNumbers(arriving, there))
    }

    @Test
    fun providerNormalJourneyKeepsStationRule() {
        // Found with normal change times, but only 1 minute to spare at a station with regional
        // trains (S-Bahn to S-Bahn, 6 minutes, 5 needed): still risky, the stricter rating wins.
        val journey = Journey(
            listOf(ride("suburban", a, railHub, 0, 10), ride("suburban", railHub, b, 16, 30)),
            foundLevel = TransferComfort.Normal,
        )
        assertEquals(listOf(Tightness.Risky), journeyChangeRatings(journey))
        assertEquals(TransferComfort.Risky, journeyComfort(journey))
        // With 10 minutes to spare it is relaxed.
        val roomy = journey.copy(legs = listOf(ride("suburban", a, railHub, 0, 10), ride("suburban", railHub, b, 25, 40)))
        assertEquals(TransferComfort.Relaxed, journeyComfort(roomy))
    }

    @Test
    fun foundOnlyWithoutChangeTimeIsRiskyAtLeast() {
        // 6 minutes at a U-Bahn station is normal by the rule, but only the search without any
        // change time found the journey: it is risky.
        val legs = listOf(ride("subway", a, hub, 0, 10), ride("subway", hub, b, 16, 20))
        assertEquals(TransferComfort.Normal, journeyComfort(Journey(legs, foundLevel = TransferComfort.Normal)))
        assertEquals(TransferComfort.Normal, journeyComfort(Journey(legs, foundLevel = TransferComfort.Relaxed)))
        val risky = Journey(legs, foundLevel = TransferComfort.Risky)
        assertEquals(listOf(Tightness.Risky), journeyChangeRatings(risky))
        assertEquals(TransferComfort.Risky, journeyComfort(risky))
        // Of two changes only the tightest is marked.
        val two = Journey(
            listOf(ride("subway", a, hub, 0, 10), ride("subway", hub, other, 20, 30), ride("subway", other, b, 36, 40)),
            foundLevel = TransferComfort.Risky,
        )
        assertEquals(listOf(Tightness.Comfortable, Tightness.Risky), journeyChangeRatings(two))
        assertEquals(TransferComfort.Risky, loosestLevel(listOf(TransferComfort.Risky)))
        assertEquals(TransferComfort.Normal, loosestLevel(listOf(TransferComfort.Risky, TransferComfort.Normal)))
    }

    @Test
    fun veryRiskyByTheLiveTimes() {
        // Planned 1 minute before the arrival, live 6 minutes after it: rated by the live times.
        val journey = Journey(
            listOf(ride("subway", a, hub, 0, 10), ride("subway", hub, b, 16, 20, plannedLeaves = 9)),
            foundLevel = TransferComfort.Normal,
        )
        assertEquals(TransferComfort.Normal, journeyComfort(journey))
        // A walk of 4 minutes that fills the whole gap leaves less than it needs (the walk and
        // one more) but works at a brisk pace (4 x 0.8, rounded up: 4): knapp.
        val walk = Leg(null, "walk", true, hub.name, other.name, at(10), null, at(14), null, null, null, null, false)
        val walking = Journey(
            listOf(ride("bus", a, hub, 0, 10), walk, ride("bus", other, b, 14, 30)),
            foundLevel = TransferComfort.Normal,
        )
        assertEquals(listOf(Tightness.Risky), journeyChangeRatings(walking))
        // One minute less does not work even at a brisk pace: a sprint.
        val shorter = Journey(
            listOf(ride("bus", a, hub, 0, 10), walk, ride("bus", other, b, 13, 30)),
            foundLevel = TransferComfort.Normal,
        )
        assertEquals(listOf(Tightness.VeryRisky), journeyChangeRatings(shorter))
    }

    @Test
    fun changeRatingFollowsTheJourney() {
        val journey = Journey(
            listOf(ride("subway", a, hub, 0, 10), ride("subway", hub, b, 16, 20)),
            foundLevel = TransferComfort.Risky,
        )
        assertEquals(Tightness.Risky, changeRatingAt(journey, 1))
        assertEquals(null, changeRatingAt(journey, 0))
    }

    private fun spot(latitude: Double, label: String? = null) = WalkTarget(WalkTargetKind.StopPosition, label, latitude, 13.0)

    @Test
    fun busStopPositionsSetTheNeed() {
        val arriving = ride("bus", a, hub, 0, 10).copy(alightingSpot = spot(52.0))
        // The same stop (6 m apart): 1 minute; 2 minutes to spare is normal.
        val same = ride("bus", hub, b, 13, 20).copy(boardingSpot = spot(52.00005))
        assertEquals(1L, changeNeed(arriving, same))
        assertEquals(Tightness.Tight, changeTightness(arriving, same))
        // 50 m apart: 1 minute on foot and one more.
        val across = ride("tram", hub, b, 13, 20).copy(boardingSpot = spot(52.00045, "2"))
        assertEquals(2L, changeNeed(arriving, across))
        // 300 m apart: 6 minutes on foot and one more.
        val far = ride("bus", hub, b, 13, 20).copy(boardingSpot = spot(52.0027))
        assertEquals(7L, changeNeed(arriving, far))
        // Without both positions the station rule: 3 minutes.
        assertEquals(3L, changeNeed(ride("bus", a, hub, 0, 10), same))
        // A walk of the provider keeps the station rule.
        assertEquals(5L, changeNeed(arriving, far, walkMinutes = 4))
        // Not for trains.
        assertEquals(null, stopSpotNeed(ride("subway", a, hub, 0, 10).copy(alightingSpot = spot(52.0)), same))
    }

    @Test
    fun walkStretchedOverTheWaitCountsByItsLength() {
        // S+U Rathaus Steglitz: the S1 arrives, the provider's walk to the bus at Pos. 2 fills
        // the whole wait of 10 minutes, though Pos. 2 lies about 110 m from the station
        // (OpenStreetMap). Counted in full the change needed 11 minutes and was "knapp".
        val station = Stop("900062202", "S+U Rathaus Steglitz", 52.456164, 13.321717, null, setOf("suburban", "subway", "bus"))
        val pos2 = 52.45649 to 13.320179
        val s1 = ride("suburban", a, station, 0, 10, platformTo = "1")
        val walk = Leg(
            null, "walk", true, station.name, station.name, at(10), at(10), at(20), at(20), null, null, null, false,
            path = listOf(station.position!!, pos2), fromPlace = station, toPlace = station,
        )
        val bus = ride("bus", station, b, 20, 30, platformFrom = "2")
        // 110 m: 2 minutes on foot and 2 for the way out of the station.
        assertEquals(4L, changeWalkMinutes(walk))
        val journey = Journey(listOf(s1, walk, bus), foundLevel = TransferComfort.Normal)
        assertEquals(listOf(Tightness.Comfortable), journeyChangeRatings(journey))
        // The metres of the provider count before the way on the map: 400 m are 7 minutes and 2.
        assertEquals(9L, changeWalkMinutes(walk.copy(walkMeters = 400)))
        // A walk the provider gives shorter than its length stays as it is.
        assertEquals(1L, changeWalkMinutes(walk.copy(arrival = at(11), plannedArrival = at(11))))
        // Without a way and without metres the provider's minutes count.
        assertEquals(10L, changeWalkMinutes(walk.copy(path = emptyList())))
    }

    @Test
    fun stretchedWalkAtOneStationWithoutLengthIsCapped() {
        // S-Bahn and bus at the same station id, the provider's walk fills the 10-minute gap and
        // gives no metres and no way: counted in full the change needed 11 minutes and was risky.
        val s = ride("suburban", a, hub, 0, 10)
        val bus = ride("bus", hub, b, 20, 30)
        val walk = Leg(
            null, "walk", true, hub.name, hub.name, at(10), at(10), at(20), at(20), null, null, null, false,
            fromPlace = hub, toPlace = hub,
        )
        assertEquals(STATION_WALK_CAP_MINUTES, changeWalkMinutes(walk, s, bus))
        val ratings = journeyChangeRatings(Journey(listOf(s, walk, bus), foundLevel = TransferComfort.Normal))
        assertTrue(ratings.single().ordinal < Tightness.Risky.ordinal)
        // Between two different stations nothing caps it.
        assertEquals(10L, changeWalkMinutes(walk.copy(fromPlace = hub, toPlace = other), s, ride("bus", other, b, 20, 30)))
        // Positions of the legs give a length: 110 m are 2 minutes on foot and 2 more.
        val near = ride("bus", hub, b, 20, 30).copy(boardingSpot = spot(52.001))
        assertEquals(4L, changeWalkMinutes(walk, s.copy(alightingSpot = spot(52.0)), near))
    }

    @Test
    fun foundOnlyWithoutChangeTimeKeepsARoomyChange() {
        // 10 minutes from the S-Bahn to the bus at one station need 3: 7 to spare is more than
        // the change time of any search, so being found only by the search without change
        // time says nothing about this change.
        val legs = listOf(ride("suburban", a, hub, 0, 10), ride("bus", hub, b, 20, 30))
        assertEquals(listOf(Tightness.Comfortable), journeyChangeRatings(Journey(legs, foundLevel = TransferComfort.Risky)))
        // With 4 to spare it is risky, as before.
        val closer = listOf(ride("suburban", a, hub, 0, 10), ride("bus", hub, b, 17, 30))
        assertEquals(listOf(Tightness.Risky), journeyChangeRatings(Journey(closer, foundLevel = TransferComfort.Risky)))
    }
}
