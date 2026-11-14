package io.github.jnbp.nextstop.wear.data

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapImageCacheTest {
    private fun dir(): File = Files.createTempDirectory("watchmap").toFile().also { it.deleteOnExit() }

    private fun view(longitude: Double, zoom: Double = 16.0, dark: Boolean = false) =
        MapImageRequest(52.5, longitude, zoom, 400, 400, 2.0, dark)

    @Test
    fun keepsAndFindsACoveringPictureAcrossInstances() {
        val dir = dir()
        val picture = view(13.4).withMargin()
        MapImageCache(dir).put(picture.frame, byteArrayOf(1, 2, 3), now = 1_000)
        // A new cache on the same folder (the app started again) knows it.
        val again = MapImageCache(dir)
        val entry = again.covering(view(13.4))
        assertNotNull(entry)
        assertEquals(listOf<Byte>(1, 2, 3), again.read(entry!!)!!.toList())
        assertNull(again.covering(view(13.4, dark = true)))
        assertNull(again.covering(view(13.4, zoom = 17.0)))
    }

    @Test
    fun aNearPictureServesMeanwhile() {
        val cache = MapImageCache(dir())
        val picture = view(13.4).withMargin()
        cache.put(picture.frame, byteArrayOf(1), now = 1_000)
        // Moved by a third of the view: not covered, but most of it shown.
        val moved = view(13.4 + WebMercator.longitude(0.5 + picture.unitsWide / 3) - WebMercator.longitude(0.5))
        assertNull(cache.covering(moved))
        assertNotNull(cache.nearest(moved))
        // Far away: nothing.
        assertNull(cache.nearest(view(13.5)))
    }

    @Test
    fun theLeastUsedGoFirst() {
        val cache = MapImageCache(dir(), capacity = 3)
        val longitudes = listOf(13.30, 13.32, 13.34, 13.36)
        longitudes.forEachIndexed { index, longitude -> cache.put(view(longitude).frame, byteArrayOf(index.toByte()), now = 1_000L + index) }
        assertEquals(3, cache.frames().size)
        assertNull(cache.covering(view(13.30)))
        assertNotNull(cache.covering(view(13.36)))
    }

    @Test
    fun theSamePlaceIsReplaced() {
        val cache = MapImageCache(dir())
        cache.put(view(13.4).frame, byteArrayOf(1), now = 1_000)
        cache.put(view(13.4).frame, byteArrayOf(2), now = 2_000)
        assertEquals(1, cache.frames().size)
        assertEquals(2.toByte(), cache.read(cache.frames().first())!!.single())
    }

    @Test
    fun frameTextRoundTrip() {
        val frame = view(13.4, dark = true).frame
        assertEquals(frame, MapImageCache.frameOf(MapImageCache.frameText(frame)))
        assertNull(MapImageCache.frameOf("broken"))
    }

    @Test
    fun picturesOfAnOlderLookAreDropped() {
        val dir = dir()
        val frame = view(13.4).withMargin().frame
        // As written before the station details were drawn on the pictures: no look in the key.
        File(dir, "l_80_35204_21494_9.webp").writeBytes(byteArrayOf(1))
        File(dir, "l_80_35204_21494_9.frame").writeText(MapImageCache.frameText(frame))
        val cache = MapImageCache(dir)
        assertNull(cache.covering(view(13.4)))
        assertTrue(!File(dir, "l_80_35204_21494_9.webp").exists())
        cache.put(frame, byteArrayOf(2), now = 1_000)
        assertTrue(MapImageCache.keyOf(frame).startsWith(MapImageCache.LOOK + "_"))
        assertNotNull(MapImageCache(dir).covering(view(13.4)))
    }

    @Test
    fun thePreviewTakesTheLatestPictureNearTheUser() {
        val dir = dir()
        val cache = MapImageCache(dir)
        // Around Alexanderplatz (older) and Zoologischer Garten (newer), both street-level.
        val alex = MapImageRequest(52.5219, 13.4132, 16.0, 400, 400, 2.0, false)
        val zoo = MapImageRequest(52.5069, 13.3326, 16.0, 400, 400, 2.0, false)
        cache.put(alex.frame, byteArrayOf(1), now = 1_000)
        cache.put(zoo.frame, byteArrayOf(2), now = 2_000)
        val maxUnits = 1_500.0 / WebMercator.metresPerUnit(52.52)
        // Without a position: the newest.
        assertEquals(MapImageCache.keyOf(zoo.frame), MapImageCache.pickLatestNear(cache.frames(), null, null, maxUnits, 13.0)?.key)
        // Near Alexanderplatz: the picture there, though older.
        val near = MapImageCache.pickLatestNear(cache.frames(), WebMercator.x(13.4140), WebMercator.y(52.5225), maxUnits, 13.0)
        assertEquals(MapImageCache.keyOf(alex.frame), near?.key)
        // Far from both, or only zoomed-out pictures: none.
        assertNull(MapImageCache.pickLatestNear(cache.frames(), WebMercator.x(11.58), WebMercator.y(48.14), maxUnits, 13.0))
        assertNull(MapImageCache.pickLatestNear(cache.frames(), null, null, maxUnits, 17.0))
    }
}
