package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The camera of a course frames its points in the part of the map that the cards leave free. */
class CourseFrameViewTest {
    @Test
    fun withoutCoversThePointsAreCentred() {
        val view = courseView(listOf(52.50 to 13.30, 52.52 to 13.34), 400.0, 800.0, 0.0, 0.0, 0.0, 0.0, 18.0)!!
        assertEquals(13.32, view.longitude, 1e-9)
        assertEquals(52.51, view.latitude, 1e-3)
    }

    @Test
    fun aSheetBelowMovesTheMiddleDown() {
        // A sheet covers the lower half: the points stand in the upper half, so the camera looks further south.
        val open = courseView(listOf(52.50 to 13.30, 52.52 to 13.34), 400.0, 800.0, 0.0, 0.0, 0.0, 0.0, 18.0)!!
        val covered = courseView(listOf(52.50 to 13.30, 52.52 to 13.34), 400.0, 800.0, 0.0, 0.0, 0.0, 400.0, 18.0)!!
        assertTrue(covered.latitude < open.latitude)
        assertTrue(covered.zoom <= open.zoom)
    }

    @Test
    fun theVehicleIsFramedWithTheStopsAheadNotBehind() {
        // A line running north–south; the vehicle in the middle of it.
        val stops = (0..10).map { (52.40 + it * 0.01) to 13.33 }
        val vehicle = 52.452 to 13.33
        val north = aheadFrame(stops, 5.2f, vehicle)
        val south = aheadFrame(stops.reversed(), 4.8f, vehicle)
        val northView = courseView(north, 400.0, 800.0, 0.0, 100.0, 0.0, 300.0, 18.0)!!
        val southView = courseView(south, 400.0, 800.0, 0.0, 100.0, 0.0, 300.0, 18.0)!!
        // Northbound the camera looks north of the vehicle, southbound south of it.
        assertTrue(northView.latitude > vehicle.first)
        assertTrue(southView.latitude < vehicle.first)
        // Never the stops behind: nothing south of the vehicle in the northbound frame.
        assertTrue(north.all { it.first >= vehicle.first })
        assertTrue(south.all { it.first <= vehicle.first })
    }

    @Test
    fun theVehicleStandsNearTheEdgeItComesFrom() {
        val vehicle = 52.50 to 13.30
        val ahead = listOf(vehicle, 52.51 to 13.30, 52.52 to 13.30)
        val view = courseView(ahead, 400.0, 800.0, 0.0, 0.0, 0.0, 0.0, 18.0)!!
        // The middle of the frame lies between the vehicle and the last stop ahead.
        assertEquals(13.30, view.longitude, 1e-9)
        assertEquals(52.51, view.latitude, 1e-3)
    }

    @Test
    fun oneSpotIsShownAtTheLargestZoom() {
        assertEquals(16.0, courseView(listOf(52.5 to 13.3), 400.0, 800.0, 0.0, 0.0, 0.0, 0.0, 16.0)!!.zoom, 1e-9)
    }

    @Test
    fun nothingFreeNothingShown() {
        assertNull(courseView(listOf(52.5 to 13.3), 400.0, 800.0, 0.0, 500.0, 0.0, 400.0, 16.0))
    }
}
