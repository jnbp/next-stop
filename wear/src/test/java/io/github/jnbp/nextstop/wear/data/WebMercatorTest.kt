package io.github.jnbp.nextstop.wear.data

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebMercatorTest {
    @Test
    fun originAndEdges() {
        assertEquals(0.5, WebMercator.x(0.0), 1e-12)
        assertEquals(0.5, WebMercator.y(0.0), 1e-12)
        assertEquals(0.0, WebMercator.x(-180.0), 1e-12)
        assertEquals(1.0, WebMercator.x(180.0), 1e-12)
        // About 85.05° is the top of the square world.
        assertEquals(0.0, WebMercator.y(85.05112878), 1e-6)
        assertEquals(1.0, WebMercator.y(-85.05112878), 1e-6)
    }

    @Test
    fun roundTrip() {
        val latitude = 52.4889
        val longitude = 13.3394
        assertEquals(latitude, WebMercator.latitude(WebMercator.y(latitude)), 1e-9)
        assertEquals(longitude, WebMercator.longitude(WebMercator.x(longitude)), 1e-9)
    }

    @Test
    fun northIsUp() {
        assertTrue(WebMercator.y(52.6) < WebMercator.y(52.4))
    }

    @Test
    fun zoomAndScaleAgree() {
        // At zoom 0 the world is 512 logical pixels wide; with ratio 2 that is 1024 pixels.
        assertEquals(1024.0, WebMercator.pixelsPerUnit(0.0, 2.0), 1e-9)
        assertEquals(15.25, WebMercator.zoomFor(WebMercator.pixelsPerUnit(15.25, 2.0), 2.0), 1e-9)
    }

    @Test
    fun metresMatchTheGround() {
        // 1 km east at 52.5° N in units, then back in metres.
        val latitude = 52.5
        val units = 1000.0 / WebMercator.metresPerUnit(latitude)
        val east = (WebMercator.longitude(0.5 + units) - 0.0)
        // One degree of longitude there is about 67.8 km.
        assertTrue(abs(east * 67_800 - 1000) < 10)
    }

    private fun view(x: Double, y: Double, zoom: Double = 16.0) =
        MapImageRequest(WebMercator.latitude(y), WebMercator.longitude(x), zoom, 400, 400, 2.0, dark = false)

    @Test
    fun aPictureWithMarginCoversSmallMoves() {
        val x = WebMercator.x(13.34)
        val y = WebMercator.y(52.49)
        val image = view(x, y).withMargin()
        assertEquals(500, image.width)
        val pixel = 1 / image.pixelsPerUnit
        assertTrue(image.covers(view(x, y)))
        assertTrue(image.covers(view(x + 40 * pixel, y - 40 * pixel)))
        assertFalse(image.covers(view(x + 60 * pixel, y)))
        assertFalse(image.covers(view(x, y, zoom = 16.5)))
        assertFalse(image.covers(view(x, y).copy(dark = true)))
    }

    @Test
    fun marginIsCapped() {
        val big = MapImageRequest(52.5, 13.4, 15.0, 600, 600, 2.0, dark = false).withMargin()
        assertEquals(MapImageRequest.MAX_SIDE, big.width)
    }

    @Test
    fun viewZoomIsClamped() {
        val request = MapImageRequest.ofView(0.5, 0.5, 1e12, 100, 100, 2.0, dark = false)
        assertEquals(MapImageRequest.MAX_ZOOM, request.zoom, 1e-9)
    }

    @Test
    fun aPictureWithMarginLiesCentredOnItsView() {
        // A round watch of 454 px at density 1.75: the picture asked with the margin is
        // 567 px, drawn by the phone over 324 logical pixels. It must lie centred on the
        // screen and reach past it on both sides, not only cover the left half.
        val x = WebMercator.x(13.3394)
        val y = WebMercator.y(52.4889)
        val ratio = 1.75
        val perUnit = WebMercator.pixelsPerUnit(16.3, ratio)
        val view = MapImageRequest.ofView(x, y, perUnit, 454, 454, ratio, dark = true)
        val asked = view.withMargin()
        assertEquals(567, asked.width)
        // The area the phone answers it drew: its camera and its logical size (567 / 1.75 = 324).
        val drawn = MapFrame.drawn(asked.latitude, asked.longitude, asked.zoom, 324.0, 324.0, dark = true)
        val place = drawn.placement(x, y, perUnit, 454.0, 454.0)
        assertEquals(567.0, place.width, 0.5)
        assertEquals(567.0, place.height, 0.5)
        assertEquals((454 - 567) / 2.0, place.left, 0.5)
        assertEquals((454 - 567) / 2.0, place.top, 0.5)
        assertEquals(454.0 / 2, place.left + place.width / 2, 1e-6)
        assertTrue(drawn.covers(view))
    }

    @Test
    fun aCappedPictureStaysCentredToo() {
        // A big screen where the margin hits the cap of 640 px in one direction only.
        val x = 0.53
        val y = 0.33
        val ratio = 2.0
        val perUnit = WebMercator.pixelsPerUnit(15.0, ratio)
        val view = MapImageRequest.ofView(x, y, perUnit, 600, 480, ratio, dark = false)
        val asked = view.withMargin()
        assertEquals(MapImageRequest.MAX_SIDE, asked.width)
        assertEquals(600, asked.height)
        val place = asked.frame.placement(x, y, perUnit, 600.0, 480.0)
        assertEquals(640.0, place.width, 1e-6)
        assertEquals(600.0, place.height, 1e-6)
        assertEquals(-20.0, place.left, 1e-6)
        assertEquals(-60.0, place.top, 1e-6)
    }

    @Test
    fun theFrameNotTheBitmapSizeDecidesTheArea() {
        // A phone that drew at its own density (a bitmap of another pixel size) still drew the
        // same logical area: the picture is stretched over it and lies where it belongs.
        val request = MapImageRequest(52.5, 13.4, 16.0, 500, 500, 2.0, dark = false)
        val drawn = MapFrame.drawn(52.5, 13.4, 16.0, 250.0, 250.0, dark = false)
        assertEquals(request.unitsWide, drawn.unitsWide, 1e-15)
        assertEquals(request.frame.x, drawn.x, 1e-12)
        assertEquals(request.frame.y, drawn.y, 1e-12)
    }

    @Test
    fun aPannedViewShowsThePictureOffByThePan() {
        val x = 0.5
        val y = 0.5
        val perUnit = WebMercator.pixelsPerUnit(16.0, 2.0)
        val asked = MapImageRequest.ofView(x, y, perUnit, 400, 400, 2.0, dark = false).withMargin()
        // The view moved 100 screen pixels east: the picture moves 100 pixels to the left.
        val place = asked.frame.placement(x + 100 / perUnit, y, perUnit, 400.0, 400.0)
        assertEquals(-50.0 - 100.0, place.left, 1e-6)
        assertEquals(-50.0, place.top, 1e-6)
    }

    @Test
    fun aQuickPictureShowsTheSameAreaSmaller() {
        val asked = MapImageRequest(52.5, 13.4, 16.0, 567, 567, 1.75, dark = true)
        val quick = asked.quick()!!
        assertEquals(MapImageRequest.QUICK_SIDE, quick.width)
        assertTrue(quick.zoom < asked.zoom)
        assertEquals(asked.unitsWide, quick.unitsWide, asked.unitsWide * 0.01)
        assertEquals(asked.pixelRatio, quick.pixelRatio, 0.0)
        // A quick picture never counts as covering: the full one is still asked for.
        assertFalse(quick.covers(asked))
        // Small pictures need no quick one.
        assertEquals(null, MapImageRequest(52.5, 13.4, 16.0, 260, 200, 2.0, dark = true).quick())
    }

    @Test
    fun overlapOfAMovedPicture() {
        val view = MapImageRequest(52.5, 13.4, 16.0, 400, 400, 2.0, dark = false)
        assertEquals(1.0, view.frame.overlap(view), 1e-9)
        val half = view.frame.copy(x = view.x + view.unitsWide / 2)
        assertEquals(0.5, half.overlap(view), 1e-9)
        assertEquals(0.0, view.frame.copy(x = view.x + view.unitsWide * 2).overlap(view), 1e-9)
    }
}
