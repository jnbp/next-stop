package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** When a change without a walk leg of its own gets a small map in the journey detail. Made-up stops and points. */
class ChangeMapTest {
    private val start: OffsetDateTime = OffsetDateTime.parse("2026-01-01T08:00:00+01:00")

    // A street stop and, about 60 m north of it, the platform of a station of the same name.
    private val street = 52.50000 to 13.30000
    private val platform = 52.50054 to 13.30000

    private fun stop(id: String, point: Pair<Double, Double>?) =
        Stop(id, "Musterplatz", point?.first, point?.second, null)

    private fun ride(
        product: String,
        from: Stop? = null,
        to: Stop? = null,
        path: List<Pair<Double, Double>> = emptyList(),
        boarding: WalkTarget? = null,
        alighting: WalkTarget? = null,
        walking: Boolean = false,
    ) = Leg(
        if (walking) null else "X1", product, walking, "A", "B",
        start, start, start.plusMinutes(10), start.plusMinutes(10), null, null, null, false,
        path = path, fromPlace = from, toPlace = to, boardingSpot = boarding, alightingSpot = alighting,
    )

    @Test
    fun busFromAStreetStopToATrainOfTheSameNameGetsAMap() {
        // The bus has no way and no stop position, the train no stop position: the stops' own points.
        val bus = ride("bus", to = stop("street-1", street))
        val train = ride("subway", from = stop("station-1", platform))
        val ends = changeMapEnds(bus, train)
        assertEquals(ChangeMapEnds(street, platform), ends)
        assertTrue(changeMovesPlace(bus, train, ends))
    }

    @Test
    fun platformFromOsmStandsInWhereTheTrainHasNoWayOrPoint() {
        val bus = ride("bus", to = stop("street-1", street))
        val train = ride("subway", from = stop("station-1", null))
        assertNull(changeMapEnds(bus, train))
        val ends = changeMapEnds(bus, train, boardPlatform = platform)
        assertEquals(ChangeMapEnds(street, platform), ends)
        assertTrue(changeMovesPlace(bus, train, ends))
    }

    @Test
    fun theWaysAndStopPositionsComeBeforeTheStopsOwnPoints() {
        val near = 52.50020 to 13.30010
        val bus = ride("bus", to = stop("street-1", street), path = listOf(near, street))
        val train = ride(
            "subway",
            from = stop("station-1", null),
            boarding = WalkTarget(WalkTargetKind.StopPosition, "2", platform.first, platform.second),
        )
        assertEquals(ChangeMapEnds(street, platform), changeMapEnds(bus, train, alightPlatform = near, boardPlatform = near))
    }

    @Test
    fun oneStopOnOneSpotGetsNone() {
        val bus = ride("bus", to = stop("stop-1", street))
        val next = ride("bus", from = stop("stop-1", street))
        assertFalse(changeMovesPlace(bus, next, changeMapEnds(bus, next)))
    }

    @Test
    fun oneStopIdFarApartGetsOne() {
        // One id for the whole station, but the ride is left 60 m from where the next is boarded.
        val bus = ride("bus", to = stop("station-1", street), path = listOf(street))
        val train = ride("subway", from = stop("station-1", street), path = listOf(platform))
        assertTrue(changeMovesPlace(bus, train, changeMapEnds(bus, train)))
    }

    @Test
    fun twoStopsCloseByCountUnlessOnOneSpot() {
        val nearBy = 52.50018 to 13.30000 // some 20 m
        val onTheSpot = 52.50005 to 13.30000 // some 6 m
        val bus = ride("bus", to = stop("a", street))
        assertTrue(changeMovesPlace(bus, ride("tram", from = stop("b", nearBy)), changeMapEnds(bus, ride("tram", from = stop("b", nearBy)))))
        val same = ride("tram", from = stop("b", onTheSpot))
        assertFalse(changeMovesPlace(bus, same, changeMapEnds(bus, same)))
        // One stop, 20 m: below the threshold for one stop.
        val sameId = ride("tram", from = stop("a", nearBy))
        assertFalse(changeMovesPlace(bus, sameId, changeMapEnds(bus, sameId)))
    }

    @Test
    fun walksAndUnknownEndsGetNone() {
        val bus = ride("bus", to = stop("street-1", street))
        val walk = ride("walking", from = stop("street-1", street), to = stop("station-1", platform), walking = true)
        assertNull(changeMapEnds(bus, walk))
        assertFalse(changeMovesPlace(bus, walk, null))
        assertNull(changeMapEnds(ride("bus"), ride("subway", from = stop("station-1", platform))))
    }

    // Two stop positions of one stop, some 90 m apart (as Pos. 6 and Pos. 5).
    private val pos6 = 52.50000 to 13.30000
    private val pos5 = 52.50081 to 13.30000

    private fun nightBuses(): Pair<Leg, Leg> {
        val station = stop("station-9", pos6)
        val first = ride(
            "bus", to = station,
            alighting = WalkTarget(WalkTargetKind.StopPosition, "6", pos6.first, pos6.second),
        )
        val second = ride(
            "bus", from = station,
            boarding = WalkTarget(WalkTargetKind.StopPosition, "5", pos5.first, pos5.second),
        )
        return first to second
    }

    @Test
    fun anotherStopPositionOfTheSameStopGetsTheMapOfBoth() {
        val (first, second) = nightBuses()
        val ends = changeMapEnds(first, second)
        assertTrue(changeMovesPlace(first, second, ends))
        // No walk leg, or one of no minutes without a way of its own: the map of both positions.
        assertEquals(ChangeMapKind.Spots, changeMapKind(first, second, ends))
        assertEquals(ChangeMapKind.Spots, changeMapKind(first, second, ends, walkMinutes = 0, walkHasMap = false))
        // A walk of no minutes that has a way of its own: its own map, as it leads elsewhere.
        assertEquals(ChangeMapKind.Walk, changeMapKind(first, second, ends, walkMinutes = 0, walkHasMap = true))
    }

    @Test
    fun aWalkWithMinutesShowsItsOwnMap() {
        val bus = ride("bus", to = stop("street-1", street))
        val train = ride("subway", from = stop("station-1", platform))
        val ends = changeMapEnds(bus, train)
        assertEquals(ChangeMapKind.Walk, changeMapKind(bus, train, ends, walkMinutes = 2, walkHasMap = true))
        // Its walk has no way of its own: the map of the change.
        assertEquals(ChangeMapKind.Change, changeMapKind(bus, train, ends, walkMinutes = 2, walkHasMap = false))
    }

    @Test
    fun theSameSpotOrPlatformShowsNoMap() {
        val bus = ride("bus", to = stop("stop-1", street))
        val next = ride("bus", from = stop("stop-1", street))
        assertEquals(ChangeMapKind.None, changeMapKind(bus, next, changeMapEnds(bus, next), walkMinutes = 0, walkHasMap = true))
        val far = ride("subway", from = stop("station-1", platform))
        assertEquals(ChangeMapKind.None, changeMapKind(bus, far, changeMapEnds(bus, far), samePlatform = true))
    }
}
