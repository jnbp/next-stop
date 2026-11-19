package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The framing of the small walk maps and the pan and zoom of one opened larger (#114). */
class MiniMapFrameTest {
    private val walk = listOf(52.5000 to 13.4000, 52.5030 to 13.4050, 52.5050 to 13.4020)

    @Test
    fun every_point_lands_inside_the_padded_room() {
        val frame = miniMapFrame(walk, 330.0, 140.0, side = 28.0, top = 44.0, bottom = 22.0, minZoom = 11.0, maxZoom = 18.0)
        for ((lat, lon) in walk) {
            val x = frame.x(lon, 330.0)
            val y = frame.y(lat, 140.0)
            assertTrue("x $x", x >= 28.0 - 0.5 && x <= 330.0 - 28.0 + 0.5)
            assertTrue("y $y", y >= 44.0 - 0.5 && y <= 140.0 - 22.0 + 0.5)
        }
    }

    @Test
    fun the_walk_sits_in_the_middle_of_the_room_left() {
        val frame = miniMapFrame(walk, 330.0, 300.0, side = 28.0, top = 60.0, bottom = 20.0, minZoom = 11.0, maxZoom = 18.0)
        val ys = walk.map { frame.y(it.first, 300.0) }
        assertEquals((60.0 + 280.0) / 2.0, (ys.min() + ys.max()) / 2.0, 0.5)
    }

    @Test
    fun one_point_or_a_tiny_walk_stops_at_the_closest_zoom() {
        val frame = miniMapFrame(listOf(52.5 to 13.4, 52.5 to 13.4), 330.0, 140.0, 28.0, 44.0, 22.0, 11.0, 18.0)
        assertEquals(18.0, frame.zoom, 1e-9)
    }

    @Test
    fun a_long_walk_zooms_out_below_the_old_limit() {
        val long = listOf(52.50 to 13.40, 52.53 to 13.45)
        val frame = miniMapFrame(long, 330.0, 140.0, 28.0, 44.0, 22.0, 11.0, 18.0)
        assertTrue(frame.zoom < 13.0)
    }

    @Test
    fun the_opened_map_never_leaves_its_window_empty() {
        val start = MiniView()
        val moved = panMiniView(start, 1f, 1000f, -1000f, 300f, 300f, 450f, 450f, 0.67f, 3f)
        assertEquals(75f, moved.x, 0.01f)
        assertEquals(-75f, moved.y, 0.01f)
        val zoomed = panMiniView(moved, 10f, 0f, 0f, 300f, 300f, 450f, 450f, 0.67f, 3f)
        assertEquals(3f, zoomed.scale, 0.001f)
        val out = panMiniView(zoomed, 0.01f, 0f, 0f, 300f, 300f, 450f, 450f, 0.67f, 3f)
        assertEquals(0.67f, out.scale, 0.001f)
        // Zoomed out to the whole picture there is (almost) nothing left to move.
        assertTrue(kotlin.math.abs(out.x) <= 1f && kotlin.math.abs(out.y) <= 1f)
    }
}
