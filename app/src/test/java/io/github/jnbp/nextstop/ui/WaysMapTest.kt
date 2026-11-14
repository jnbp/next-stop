package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.Disruption
import io.github.jnbp.nextstop.data.Stop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The camera and the markers of the map of "My routes" (see WaysMap.kt). */
class WaysMapTest {
    private val north = 52.56 to 13.40
    private val south = 52.48 to 13.42

    @Test
    fun theCameraShowsEveryPoint() {
        val camera = waysCamera(listOf(north, south), 360.0, 200.0, 34.0)!!
        listOf(north, south).forEach { point ->
            val (x, y) = camera.place(point, 360.0, 200.0)
            assertTrue("x $x", x in 0.0..360.0)
            assertTrue("y $y", y in 30.0..170.0)
        }
    }

    @Test
    fun onePointIsShownCloseUpInTheMiddle() {
        val camera = waysCamera(listOf(north), 360.0, 200.0, 34.0)!!
        assertEquals(14.0, camera.zoom, 0.001)
        val (x, y) = camera.place(north, 360.0, 200.0)
        assertEquals(180.0, x, 0.01)
        assertEquals(100.0, y, 0.01)
        assertNull(waysCamera(emptyList(), 360.0, 200.0, 34.0))
    }

    @Test
    fun farPointsStayWithinTheCity() {
        val camera = waysCamera(listOf(north, 48.1 to 11.6), 360.0, 200.0, 34.0)!!
        assertEquals(9.5, camera.zoom, 0.001)
    }

    private fun stop(id: String, latitude: Double) = Stop(id, "Stop $id", latitude, 13.4, null)

    @Test
    fun aNoticeIsMarkedAtItsFewStopsOnce() {
        val local = Disruption("a", "Umleitung", "Bus 100: Umleitung", setOf("100"), setOf("bus"), stops = listOf(stop("1", 52.5), stop("1b", 52.5), stop("2", 52.51)))
        assertEquals(listOf(52.5 to 13.4, 52.51 to 13.4), noticeSpots(listOf(local)).map { it.position })
        // Found all over the city: no one place.
        val wide = local.copy(id = "b", stops = (1..6).map { stop("$it", 52.4 + it / 100.0) })
        assertTrue(noticeSpots(listOf(wide)).isEmpty())
    }

    @Test
    fun aNoticeAllOverTheCityIsMarkedWithinTheRadiusOfAPlace() {
        val wide = Disruption("c", "Umleitung", "Bus 100: Umleitung", setOf("100"), setOf("bus"), stops = (1..6).map { stop("$it", 52.4 + it / 100.0) })
        val third = wide.stops[2].position!!
        val area = WaysMapArea(third, 500, true)
        assertEquals(listOf(third), noticeSpots(listOf(wide), listOf(area)).map { it.position })
    }

    @Test
    fun theWholeCircleOfTheRadiusIsShown() {
        val area = WaysMapArea(north, 1000, false)
        val camera = waysCamera(listOf(north) + areaBounds(area), 360.0, 200.0, 34.0)!!
        val (x, y) = camera.place(north, 360.0, 200.0)
        val radius = camera.length(1000.0, north.first)
        assertTrue("left ${x - radius}", x - radius >= 0.0)
        assertTrue("top ${y - radius}", y - radius >= 0.0)
        assertTrue("bottom ${y + radius}", y + radius <= 200.0)
        // 1 km at this zoom is about as many dp as the bounds say.
        val (_, top) = camera.place(areaBounds(area).first(), 360.0, 200.0)
        assertEquals(radius, y - top, radius * 0.05)
    }
}
