package io.github.jnbp.nextstop.wear.data

import java.io.File
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The map pictures of the phone kept on the watch's disk, so a map opens with a picture at
 * once: the newest [capacity] (least recently used out first), one per centre tile, zoom
 * step, style and size (a newer picture of the same replaces the older). Each is the WebP
 * as received and its [MapFrame] beside it (`<key>.webp`, `<key>.frame`, one line of text). Plain files and
 * no Android: the caller runs it off the main thread and serialises the calls.
 */
class MapImageCache(private val dir: File, private val capacity: Int = CAPACITY) {
    /** A kept picture: where it lies, its file, when it was last used. */
    class Entry(val key: String, val frame: MapFrame, val file: File, var used: Long)

    private var entries: MutableList<Entry>? = null

    private fun index(): MutableList<Entry> {
        entries?.let { return it }
        val found = mutableListOf<Entry>()
        dir.listFiles { file -> file.name.endsWith(".frame") }?.forEach { meta ->
            val key = meta.name.removeSuffix(".frame")
            val picture = File(dir, "$key.webp")
            // Pictures of an older look (without the station details) are not shown again.
            val frame = if (!key.startsWith(KEY_PREFIX)) {
                null
            } else {
                try {
                    frameOf(meta.readText())
                } catch (e: Exception) {
                    null
                }
            }
            if (frame == null || !picture.isFile) {
                meta.delete()
                picture.delete()
            } else {
                found += Entry(key, frame, picture, picture.lastModified())
            }
        }
        entries = found
        return found
    }

    /** All kept frames, the most recently used first. */
    fun frames(): List<Entry> = index().sortedByDescending { it.used }

    /** A kept picture that covers [view], the most recently used first; null when none does. */
    fun covering(view: MapImageRequest): Entry? = frames().firstOrNull { it.frame.covers(view) }

    /**
     * A kept picture to show meanwhile for [view]: of its style, at most [PLACEHOLDER_ZOOM]
     * zoom steps off, showing at least [PLACEHOLDER_OVERLAP] of it; the one showing most (at
     * a near zoom when equal). Null when none is near enough.
     */
    fun nearest(view: MapImageRequest): Entry? = pickNearest(frames(), view)

    /** The bytes of [entry], and it counts as used now; null when its file is gone. */
    fun read(entry: Entry, now: Long = System.currentTimeMillis()): ByteArray? = try {
        val bytes = entry.file.readBytes()
        entry.used = now
        entry.file.setLastModified(now)
        bytes
    } catch (e: Exception) {
        forget(entry)
        null
    }

    /** Keeps [bytes] of a picture of [frame]; a kept one of the same key is replaced, the least used beyond [capacity] go. */
    fun put(frame: MapFrame, bytes: ByteArray, now: Long = System.currentTimeMillis()) {
        val list = index()
        dir.mkdirs()
        val key = keyOf(frame)
        list.firstOrNull { it.key == key }?.let { forget(it) }
        val picture = File(dir, "$key.webp")
        try {
            picture.writeBytes(bytes)
            File(dir, "$key.frame").writeText(frameText(frame))
            picture.setLastModified(now)
        } catch (e: Exception) {
            picture.delete()
            File(dir, "$key.frame").delete()
            return
        }
        list += Entry(key, frame, picture, now)
        while (list.size > capacity) forget(list.minBy { it.used })
    }

    private fun forget(entry: Entry) {
        index().remove(entry)
        entry.file.delete()
        File(dir, "${entry.key}.frame").delete()
    }

    companion object {
        const val CAPACITY = 20
        const val PLACEHOLDER_ZOOM = 1.5
        const val PLACEHOLDER_OVERLAP = 0.3

        /**
         * The look of the pictures kept: raised whenever the phone draws something new on them
         * (v2: the station details, see the phone's WatchMapDetails), so older ones are dropped.
         */
        const val LOOK = "v2"
        private const val KEY_PREFIX = LOOK + "_"

        /** The key of a picture: its look ([LOOK]), style, zoom in steps of 0.2, the tile of its centre at that zoom, its width in logical pixels (powers of 1.5). */
        fun keyOf(frame: MapFrame): String {
            val zoomStep = (frame.zoom / MapImageRequest.ZOOM_SLACK).roundToInt()
            val scale = 2.0.pow(floor(frame.zoom))
            val tileX = floor(frame.x * scale).toLong()
            val tileY = floor(frame.y * scale).toLong()
            val logical = frame.unitsWide * WebMercator.pixelsPerUnit(frame.zoom, 1.0)
            val size = (ln(logical.coerceAtLeast(1.0)) / ln(1.5)).roundToInt()
            return "${KEY_PREFIX}${if (frame.dark) "d" else "l"}_${zoomStep}_${tileX}_${tileY}_$size"
        }

        fun pickNearest(entries: List<Entry>, view: MapImageRequest): Entry? = entries
            .filter { it.frame.dark == view.dark && abs(it.frame.zoom - view.zoom) <= PLACEHOLDER_ZOOM }
            .map { it to it.frame.overlap(view) }
            .filter { it.second >= PLACEHOLDER_OVERLAP }
            .maxWithOrNull(compareBy<Pair<Entry, Double>> { (it.second * 10).roundToInt() }.thenBy { -abs(it.first.frame.zoom - view.zoom) })
            ?.first

        /**
         * The most recently used of [entries] (most recent first, as [frames] gives them) drawn
         * at [minZoom] or closer whose centre lies within [maxUnits] world units of ([x], [y]);
         * with no point given, the most recent one at that zoom. For the small preview on the
         * start page: any picture of the streets around the user will do.
         */
        fun pickLatestNear(entries: List<Entry>, x: Double?, y: Double?, maxUnits: Double, minZoom: Double): Entry? =
            entries.firstOrNull { entry ->
                val frame = entry.frame
                frame.zoom >= minZoom && (x == null || y == null || hypot(frame.x - x, frame.y - y) <= maxUnits)
            }

        /** A frame as one line of text ("x y w h zoom dark"), beside its picture. */
        fun frameText(frame: MapFrame): String =
            listOf(frame.x, frame.y, frame.unitsWide, frame.unitsHigh, frame.zoom).joinToString(" ") { it.toString() } + " " + frame.dark

        fun frameOf(text: String): MapFrame? {
            val parts = text.trim().split(' ')
            if (parts.size != 6) return null
            val values = parts.take(5).map { it.toDoubleOrNull() ?: return null }
            if (values.any { it.isNaN() } || values[2] <= 0 || values[3] <= 0) return null
            return MapFrame(values[0], values[1], values[2], values[3], values[4], parts[5] == "true")
        }
    }
}
