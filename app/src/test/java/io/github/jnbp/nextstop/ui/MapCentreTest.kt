package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.Leg
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.Stopover
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Centring the map on the traveller in the visible room between the blue box and the sheet (#273). */
class MapCentreTest {
    private val density = 2.75f
    private val own = 51.0 to 13.7

    // Where [point] shows on a map of [width] x [height] with its camera at [target], [zoom] and [bearing].
    private fun screen(point: Pair<Double, Double>, target: Pair<Double, Double>, zoom: Double, bearing: Double, width: Float, height: Float): Pair<Double, Double> {
        val world = worldPixels(zoom, density)
        val dx = (centreMercatorX(point.second) - centreMercatorX(target.second)) * world
        val dy = (centreMercatorY(point.first) - centreMercatorY(target.first)) * world
        val b = Math.toRadians(bearing)
        return (width / 2.0 + dx * cos(b) + dy * sin(b)) to (height / 2.0 - dx * sin(b) + dy * cos(b))
    }

    @Test
    fun theRoomLiesBetweenTheCovers() {
        val room = visibleRoom(1080f, 2400f, topCover = 700f, bottomCover = 2000f, minHeight = 300f)!!
        assertEquals(VisibleRoom(0f, 700f, 1080f, 2000f), room)
        assertEquals(1350f, room.centreY, 0.01f)
    }

    @Test
    fun unknownOrOutsideCoversCountAsNone() {
        assertEquals(VisibleRoom(0f, 0f, 1080f, 2400f), visibleRoom(1080f, 2400f, Float.NaN, Float.NaN))
        assertEquals(VisibleRoom(0f, 0f, 1080f, 2400f), visibleRoom(1080f, 2400f, -5f, 2600f))
        assertEquals(VisibleRoom(400f, 0f, 1080f, 2400f), visibleRoom(1080f, 2400f, Float.NaN, Float.NaN, startCover = 400f))
    }

    @Test
    fun aSheetPulledUpLeavesTheWholeMap() {
        assertEquals(VisibleRoom(0f, 0f, 1080f, 2400f), visibleRoom(1080f, 2400f, 700f, 800f, minHeight = 300f))
    }

    @Test
    fun aMapWithoutSizeHasNoRoom() {
        assertNull(visibleRoom(0f, 2400f, 100f, 2000f))
        assertNull(visibleRoom(Float.NaN, 2400f, 100f, 2000f))
    }

    @Test
    fun thePositionLandsInTheMiddleOfTheRoom() {
        val room = VisibleRoom(0f, 700f, 1080f, 2000f)
        for (bearing in listOf(0.0, 37.0, 90.0, 200.0)) {
            val target = centreTarget(own, room, 1080f, 2400f, 15.0, density, bearing)
            val (x, y) = screen(own, target, 15.0, bearing, 1080f, 2400f)
            assertEquals("x at $bearing", room.centreX.toDouble(), x, 0.5)
            assertEquals("y at $bearing", room.centreY.toDouble(), y, 0.5)
        }
    }

    @Test
    fun aRoomBelowTheMiddleMovesTheCameraNorth() {
        // The visible middle lies 150 px below the middle of the map: the camera looks north of the position.
        val target = centreTarget(own, VisibleRoom(0f, 700f, 1080f, 2000f), 1080f, 2400f, 15.0, density, 0.0)
        assertTrue(target.first > own.first)
        assertEquals(own.second, target.second, 1e-9)
        // Turned to the east (east is up), the same room puts the camera east of the position.
        val east = centreTarget(own, VisibleRoom(0f, 700f, 1080f, 2000f), 1080f, 2400f, 15.0, density, 90.0)
        assertTrue(east.second > own.second)
        assertEquals(own.first, east.first, 1e-6)
    }

    @Test
    fun aRoomInTheMiddleLeavesThePosition() {
        assertEquals(own, centreTarget(own, VisibleRoom(0f, 0f, 1080f, 2400f), 1080f, 2400f, 15.0, density, 0.0))
    }

    @Test
    fun withoutAStopAheadTheZoomIsTheClosest() {
        assertEquals(16.0, centreZoom(own, null, VisibleRoom(0f, 700f, 1080f, 2000f), density, 0.0, 100f, 12.5, 16.0), 0.0)
        assertEquals(16.0, centreZoom(own, own, VisibleRoom(0f, 700f, 1080f, 2000f), density, 0.0, 100f, 12.5, 16.0), 0.0)
    }

    @Test
    fun aNearStopFitsAtTheEdgeOfTheRoom() {
        val room = VisibleRoom(0f, 700f, 1080f, 2000f)
        // About 1.5 km north.
        val ahead = 51.0135 to 13.7
        val zoom = centreZoom(own, ahead, room, density, 0.0, 100f, 12.5, 16.0)
        assertTrue(zoom > 12.5 && zoom < 16.0)
        val target = centreTarget(own, room, 1080f, 2400f, zoom, density, 0.0)
        val (_, y) = screen(ahead, target, zoom, 0.0, 1080f, 2400f)
        // Right at the margin below the top of the room.
        assertEquals(800.0, y, 1.0)
    }

    @Test
    fun theTurnedMapFitsTheStopAcross() {
        val room = VisibleRoom(0f, 700f, 1080f, 2000f)
        val ahead = 51.0135 to 13.7
        // Turned by 90 degrees the way north lies across the narrower width: further out.
        val upright = centreZoom(own, ahead, room, density, 0.0, 100f, 10.0, 18.0)
        val turned = centreZoom(own, ahead, room, density, 90.0, 100f, 10.0, 18.0)
        assertTrue(turned < upright)
        val target = centreTarget(own, room, 1080f, 2400f, turned, density, 90.0)
        val (x, _) = screen(ahead, target, turned, 90.0, 1080f, 2400f)
        assertEquals(100.0, x, 1.0)
    }

    @Test
    fun aFarStopKeepsThePositionClose() {
        val ahead = 51.5 to 13.7
        assertEquals(12.5, centreZoom(own, ahead, VisibleRoom(0f, 700f, 1080f, 2000f), density, 0.0, 100f, 12.5, 16.0), 0.0)
    }

    private val line = (0..10).map { 51.0 + it * 0.01 to 13.7 }
    private val stops = listOf(line[0], line[3], line[6], line[10])

    @Test
    fun theNextStopAheadAlongThePath() {
        assertEquals(line[3], nextStopAhead(stops, line, 51.012 to 13.7001))
        assertEquals(line[6], nextStopAhead(stops, line, 51.041 to 13.7))
        // At a stop: the one after it.
        assertEquals(line[6], nextStopAhead(stops, line, line[3]))
        assertNull(nextStopAhead(stops, line, line[10]))
    }

    @Test
    fun withoutAPathTheStopAfterTheOnePassed() {
        assertEquals(line[6], nextStopAhead(stops, emptyList(), 51.035 to 13.7))
        assertEquals(line[3], nextStopAhead(stops, emptyList(), 51.025 to 13.7))
        assertNull(nextStopAhead(emptyList(), emptyList(), own))
    }

    private val base = OffsetDateTime.of(2026, 3, 2, 8, 0, 0, 0, ZoneOffset.ofHours(1))

    private fun stop(position: Pair<Double, Double>) = Stop("id${position.first}", "Halt", position.first, position.second, null)

    private fun ride(path: List<Pair<Double, Double>>, stopovers: List<Pair<Double, Double>>) = Leg(
        line = "RE1",
        product = "regional",
        walking = false,
        from = "A",
        to = "B",
        departure = base,
        plannedDeparture = null,
        arrival = base.plusMinutes(30),
        plannedArrival = null,
        departurePlatform = null,
        arrivalPlatform = null,
        direction = null,
        cancelled = false,
        path = path,
        fromPlace = stop(path.first()),
        toPlace = stop(path.last()),
        stopovers = stopovers.map { Stopover("Halt", null, null, stop(it)) },
    )

    @Test
    fun ridingKeepsTheNextStopInView() {
        val leg = ride(line, stops)
        val journey = Journey(legs = listOf(leg))
        val step = TripStep(StepKind.GetOff, base.plusMinutes(30), leg, "B")
        assertEquals(line[6], centreAhead(step, journey, 51.041 to 13.7))
        // Past the last stop but one: where the ride is left.
        assertEquals(line[10], centreAhead(step, journey, 51.07 to 13.7))
    }

    @Test
    fun beforeBoardingTheStopOfTheRide() {
        val leg = ride(line, stops)
        val step = TripStep(StepKind.Board, base, leg, "A")
        assertEquals(line[0], centreAhead(step, Journey(legs = listOf(leg)), 50.99 to 13.7))
    }

    @Test
    fun theTurnHelpersAgree() {
        // The latitude goes back and forth through Mercator.
        assertTrue(abs(centreLatitudeOf(centreMercatorY(52.5)) - 52.5) < 1e-9)
    }
}
