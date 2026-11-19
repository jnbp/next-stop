package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** #190: the part of an opened small map in view, drawn anew at the zoom it is seen at. */
class MiniSharpTest {
    // A made-up walk, nowhere in particular.
    private val walk = listOf(48.1000 to 11.5000, 48.1020 to 11.5040)
    private val frame = miniMapFrame(walk, 360.0, 300.0, 28.0, 24.0, 22.0, 11.0, 18.0)

    @Test
    fun not_enlarged_needs_no_new_picture() {
        assertEquals(null, miniSharpRequest(frame, 540, 450, MiniView(1f), 1080f, 900f, 3f, 20.0))
        assertEquals(null, miniSharpRequest(frame, 540, 450, MiniView(0.8f), 1080f, 900f, 3f, 20.0))
    }

    @Test
    fun the_new_picture_is_the_size_of_the_window_and_zooms_by_the_scale() {
        val sharp = miniSharpRequest(frame, 540, 450, MiniView(2f), 1080f, 900f, 3f, 22.0)!!
        assertEquals(360, sharp.widthDp)
        assertEquals(300, sharp.heightDp)
        assertEquals(frame.zoom + 1.0, sharp.frame.zoom, 1e-9)
        assertEquals(180f, sharp.width, 1e-3f)
        // Unmoved: it lies in the middle of the larger picture.
        assertEquals(270f - 90f, sharp.left, 1e-3f)
        assertEquals(225f - 75f, sharp.top, 1e-3f)
    }

    @Test
    fun every_point_lands_where_the_larger_picture_has_it() {
        val view = MiniView(scale = 2.5f, x = 120f, y = -60f)
        val sharp = miniSharpRequest(frame, 540, 450, view, 1080f, 900f, 3f, 22.0)!!
        val factor = sharp.widthDp / sharp.width
        for ((lat, lon) in walk + (48.1010 to 11.5010)) {
            val bigX = frame.x(lon, 540.0)
            val bigY = frame.y(lat, 450.0)
            assertEquals((bigX - sharp.left) * factor, sharp.frame.x(lon, sharp.widthDp.toDouble()), 0.05)
            assertEquals((bigY - sharp.top) * factor, sharp.frame.y(lat, sharp.heightDp.toDouble()), 0.05)
        }
    }

    @Test
    fun the_middle_of_the_window_is_the_middle_of_the_new_picture() {
        val view = MiniView(scale = 2f, x = 300f, y = 150f)
        val sharp = miniSharpRequest(frame, 540, 450, view, 1080f, 900f, 3f, 22.0)!!
        // Where the middle of the new picture lands on the screen: scaled about the picture's middle, then moved.
        val middleX = sharp.left + sharp.width / 2f
        val middleY = sharp.top + sharp.height / 2f
        val screenX = (middleX - 270f) * 3f * view.scale + view.x
        val screenY = (middleY - 225f) * 3f * view.scale + view.y
        assertEquals(0f, screenX, 0.01f)
        assertEquals(0f, screenY, 0.01f)
    }

    @Test
    fun the_zoom_is_capped_and_the_picture_then_covers_more() {
        val sharp = miniSharpRequest(frame, 540, 450, MiniView(3f), 1080f, 900f, 3f, frame.zoom + 1.0)!!
        assertEquals(frame.zoom + 1.0, sharp.frame.zoom, 1e-9)
        assertEquals(180f, sharp.width, 1e-3f)
        assertTrue(sharp.width * 3f * 3f > 1080f)
    }
}
