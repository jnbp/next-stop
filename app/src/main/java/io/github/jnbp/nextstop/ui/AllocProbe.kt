package io.github.jnbp.nextstop.ui

import android.os.Debug
import android.os.Looper
import android.os.SystemClock
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.layout
import io.github.jnbp.nextstop.data.ChangeRatings
import io.github.jnbp.nextstop.data.DiagLog
import java.util.Locale

/*
 * "Alloc by area" of the performance lab: a cheap counter per part of the map work that runs
 * on the main thread while the map moves (the glide of the vehicles, the camera listeners, the
 * stop signs, the glass ...). Each part is wrapped in begin/end (or measure); while the lab's
 * panel is open, they count the time spent, the calls, the bytes and objects the main thread
 * allocated in them (ART's per-thread allocation counters, switched on with the panel), and
 * items the part reports itself (features built). Every two seconds a line goes to logcat under
 * NextStopPerf, so a test on the phone shows which part allocates what; the rest of the main
 * thread (Compose, MapLibre's own Java side) and all threads together are given beside it.
 * With the panel closed nothing is counted: begin and end return at once.
 *
 * The connections chart and its journey page have areas of their own (composition of the chart,
 * its columns, axis and block names, measuring and drawing a column, the springs of the blocks,
 * the journey lists AppRoot works out, the change ratings asked for and worked out); an area of
 * composition counts each composition as a call, so its calls per second say how often that
 * part was composed again.
 */
internal object AllocProbe {
    const val VEHICLE_FRAME = 0
    const val VEHICLE_PUSH = 1
    const val CAMERA_MOVE = 2
    const val DECLUTTER = 3
    const val STOP_SIGNS = 4
    const val GLASS = 5
    const val LIVE_PULSE = 6
    const val STATION_DETAILS = 7
    const val VEHICLE_LOAD = 8

    // The connections chart and its journey page. Areas of composition count each composition
    // as a call, so their calls/s are how often that part composed again.
    const val CHART_COMPOSE = 9
    const val CHART_BODY = 10
    const val CHART_AXIS = 11
    const val COLUMN_COMPOSE = 12
    const val COLUMN_MEASURE = 13
    const val COLUMN_DRAW = 14
    const val BLOCK_LABEL = 15
    const val FOLLOWER = 16
    const val CHART_CHANGES = 17
    const val PAGE_LISTS = 18
    const val DETAIL_COMPOSE = 19
    const val GLASS_DRAW = 20
    const val FADE_EDGES = 21
    const val NOW_TICK = 22
    const val APP_ROOT = 23
    const val CONNECTIONS = 24
    const val CHART_GRID = 25
    // The chart while it scrolls sideways: its height asked for, the row of columns measured
    // (with the columns it composes and measures) and drawn, the geometry of a column worked
    // out, and the targets of the springs of the blocks read.
    const val CHART_HEIGHT = 26
    const val CHART_ROW_LAYOUT = 27
    const val CHART_ROW_DRAW = 28
    const val COLUMN_GEOMETRY = 29
    const val FOLLOWER_TARGET = 30
    // The loaders of the start, mostly off the main thread (see measureAny): they are counted on
    // whatever thread they run, so a run right after the start names what allocates there.
    const val RAPID_NETWORK = 31
    const val TRAM_NETWORK = 32
    const val OSM_BASE = 33
    const val OSM_PACKS = 34
    const val CHANGE_POSITIONS = 35
    const val DETAIL_BUILD = 36
    const val SIGNS_BUILD = 37
    const val MAP_STOPS = 38
    const val NOTICE_MARKS = 39
    // The app's own networks put into the map (main thread).
    const val NETWORK_PUSH = 40
    private const val AREAS = 41

    // The names in the log; parts that run inside another one are marked, they are not added up for "other".
    private val NAMES = arrayOf(
        "vehicle frame", "vehicle push (in frame)", "camera move", "pill declutter (in others)", "stop signs",
        "glass", "live pulse", "station details", "vehicle load",
        "chart compose", "chart body compose", "chart axis compose", "column compose", "column measure",
        "column draw", "block label compose (in column)", "follower ticks", "chart changes (in others)",
        "journey lists (AppRoot)", "journey detail compose", "glass draw", "fade edges draw", "now ticks",
        "AppRoot compositions", "connections page compositions", "chart grid draw",
        "chart height", "chart row layout (with columns, in others)", "chart row draw (with columns, in others)",
        "column geometry (in others)", "follower targets",
        "rapid network load (any thread)", "tram network load (any thread)", "osm base load (any thread)",
        "osm packs (any thread)", "change positions load (any thread)", "station details build (any thread)",
        "stop signs build (any thread)", "osm map stops (any thread)", "notice marks (any thread)",
        "network push",
    )
    private val NESTED = booleanArrayOf(
        false, true, false, true, false, false, false, false, false,
        false, false, false, false, false, false, true, false, true, false, false, false, false, false, false, false, false,
        false, true, true, true, false,
        false, false, false, false, false, false, false, false, false,
        false,
    )
    // Areas counted on any thread (see measureAny); the vehicle load is one too (its parsing).
    private val ANY_THREAD = BooleanArray(AREAS) { it == VEHICLE_LOAD || it in RAPID_NETWORK..NOTICE_MARKS }
    // What the areas of ANY_THREAD allocated on the main thread, part of "main".
    private var anyOnMainBytes = 0L
    private val anyLock = Any()

    /** True while the lab's panel is open: only then anything is counted. */
    @Volatile
    var on = false
        private set

    private val mainThread: Thread = Looper.getMainLooper().thread
    private val startNanos = LongArray(AREAS)
    private val startBytes = IntArray(AREAS)
    private val startObjects = IntArray(AREAS)
    private val nanos = LongArray(AREAS)
    private val bytes = LongArray(AREAS)
    private val objects = LongArray(AREAS)
    private val calls = LongArray(AREAS)
    private val items = LongArray(AREAS)
    private var since = 0L
    private var mainBytesAt = 0
    private var allBytesAt = -1L
    private var lastLogAt = 0L
    // The change ratings asked for and worked out (see ChangeRatings) at the last reset.
    private var ratingsAskedAt = 0L
    private var ratingsComputedAt = 0L

    /** True while the auto test counts one case: the panel's reports leave the counters alone then. */
    var held = false

    /** The last summary, short, for the panel. */
    var shortLine: String = ""
        private set

    /** Starts counting; call on the main thread when the panel opens. */
    @Suppress("DEPRECATION")
    fun start() {
        if (on) return
        try {
            Debug.startAllocCounting()
        } catch (e: Throwable) {
            // Some runtimes have no allocation counting: time, calls and items still count.
        }
        on = true
        reset()
    }

    /** Stops counting; call when the panel closes. */
    @Suppress("DEPRECATION")
    fun stop() {
        if (!on) return
        on = false
        try {
            Debug.stopAllocCounting()
        } catch (e: Throwable) {
            // See start.
        }
    }

    /** Forgets what was counted so far. */
    @Suppress("DEPRECATION")
    fun reset() {
        nanos.fill(0L)
        bytes.fill(0L)
        objects.fill(0L)
        calls.fill(0L)
        items.fill(0L)
        anyOnMainBytes = 0L
        since = SystemClock.uptimeMillis()
        lastLogAt = since
        if (Thread.currentThread() === mainThread) mainBytesAt = Debug.getThreadAllocSize()
        allBytesAt = allBytes()
        ratingsAskedAt = ChangeRatings.asked
        ratingsComputedAt = ChangeRatings.computed
    }

    /** Marks the start of [area] on the main thread. */
    @Suppress("DEPRECATION")
    fun begin(area: Int) {
        if (!on || Thread.currentThread() !== mainThread) return
        startNanos[area] = System.nanoTime()
        startBytes[area] = Debug.getThreadAllocSize()
        startObjects[area] = Debug.getThreadAllocCount()
    }

    /** Marks the end of [area] begun before, with [counted] items the area made (features, signs). */
    @Suppress("DEPRECATION")
    fun end(area: Int, counted: Int = 0) {
        if (!on || Thread.currentThread() !== mainThread) return
        nanos[area] += System.nanoTime() - startNanos[area]
        // The counters are ints that may wrap; the difference stays right.
        bytes[area] += (Debug.getThreadAllocSize() - startBytes[area]).toLong().coerceAtLeast(0L)
        objects[area] += (Debug.getThreadAllocCount() - startObjects[area]).toLong().coerceAtLeast(0L)
        calls[area]++
        items[area] += counted.toLong()
    }

    /**
     * Counts one composition of [area] (or one tick of a clock), without timing it: cheap enough
     * for the body of a composable, where begin and end cannot wrap all of it.
     */
    fun tick(area: Int) {
        if (!on) return
        calls[area]++
    }

    /** Adds [counted] items to [area] without timing it (work done in pieces). */
    fun count(area: Int, counted: Int) {
        if (!on) return
        items[area] += counted.toLong()
    }

    /**
     * Runs [block] as [area] on whatever thread it runs (a loader on Dispatchers.Default or IO):
     * its time and the bytes and objects that thread allocated in it (ART counts them per thread
     * while the panel is open). [block] must not suspend. [counted] gives the items it made.
     */
    @Suppress("DEPRECATION")
    fun <T> measureAny(area: Int, counted: (T) -> Int = { 0 }, block: () -> T): T {
        if (!on) return block()
        val started = System.nanoTime()
        val startSize = Debug.getThreadAllocSize()
        val startCount = Debug.getThreadAllocCount()
        var result: T? = null
        try {
            val value = block()
            result = value
            return value
        } finally {
            val took = System.nanoTime() - started
            val size = (Debug.getThreadAllocSize() - startSize).toLong().coerceAtLeast(0L)
            val count = (Debug.getThreadAllocCount() - startCount).toLong().coerceAtLeast(0L)
            @Suppress("UNCHECKED_CAST")
            val made = if (result != null) counted(result as T) else 0
            synchronized(anyLock) {
                nanos[area] += took
                bytes[area] += size
                objects[area] += count
                calls[area]++
                items[area] += made.toLong()
                if (Thread.currentThread() === mainThread) anyOnMainBytes += size
            }
        }
    }

    /** Runs [block] as [area]. */
    inline fun <T> measure(area: Int, block: () -> T): T {
        begin(area)
        try {
            return block()
        } finally {
            end(area)
        }
    }

    // Bytes allocated by all threads since the start of the runtime; -1 where the runtime does not tell.
    private fun allBytes(): Long = Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull() ?: -1L

    /**
     * Logs what was counted since the last report, at most every [everyMillis], and starts
     * counting afresh. Call on the main thread (the panel's loop). Returns true when it logged.
     */
    fun report(everyMillis: Long = 2_000L): Boolean {
        if (!on || held || Thread.currentThread() !== mainThread) return false
        val now = SystemClock.uptimeMillis()
        if (now - lastLogAt < everyMillis) return false
        val line = summary()
        DiagLog.d(PERF_LOG_TAG) { "alloc by area $line" }
        reset()
        return true
    }

    /** What was counted since the last reset, as one line: per second, the biggest parts first. */
    @Suppress("DEPRECATION")
    fun summary(): String {
        val seconds = ((SystemClock.uptimeMillis() - since).coerceAtLeast(1L)) / 1000.0
        val mainBytes = if (Thread.currentThread() === mainThread) (Debug.getThreadAllocSize() - mainBytesAt).toLong().coerceAtLeast(0L) else -1L
        val all = allBytes().let { if (it < 0 || allBytesAt < 0) -1L else it - allBytesAt }
        var counted = anyOnMainBytes
        var anyThread = 0L
        for (area in 0 until AREAS) {
            if (ANY_THREAD[area]) anyThread += bytes[area] else if (!NESTED[area]) counted += bytes[area]
        }
        // Off the main thread: what the loaders counted there, and the rest (MapLibre's Java
        // side: its HTTP of tiles and glyphs; OkHttp of the app's requests; Compose's own).
        val offMain = (anyThread - anyOnMainBytes).coerceAtLeast(0L)
        val otherThreads = if (all < 0 || mainBytes < 0) -1L else (all - mainBytes - offMain).coerceAtLeast(0L)
        val out = StringBuilder()
        out.append(String.format(Locale.US, "(%.1f s): all %s, main %s, main other %s, loaders off main %s, other threads %s",
            seconds, mbps(all, seconds), mbps(mainBytes, seconds), mbps(if (mainBytes < 0) -1L else (mainBytes - counted).coerceAtLeast(0L), seconds),
            mbps(offMain, seconds), mbps(otherThreads, seconds)))
        val order = (0 until AREAS).filter { calls[it] > 0 || items[it] > 0 }.sortedByDescending { bytes[it] }
        for (area in order) {
            out.append(" | ").append(NAMES[area]).append(' ').append(mbps(bytes[area], seconds))
            out.append(String.format(Locale.US, " %.0f calls/s %.1f ms/s %.0f obj/s",
                calls[area] / seconds, nanos[area] / 1e6 / seconds, objects[area] / seconds))
            if (items[area] > 0) out.append(String.format(Locale.US, " %.0f items/s", items[area] / seconds))
        }
        val ratingsAsked = ChangeRatings.asked - ratingsAskedAt
        if (ratingsAsked > 0) {
            out.append(String.format(Locale.US, " | change ratings %.0f asked/s %.0f worked out/s",
                ratingsAsked / seconds, (ChangeRatings.computed - ratingsComputedAt) / seconds))
        }
        if (mainBytes == 0L && all != 0L) out.append(" | (no per-thread counts on this runtime)")
        shortLine = buildString {
            append("alloc ").append(mbps(all, seconds))
            append(" main ").append(mbps(mainBytes, seconds))
            for (area in order.take(2)) append(" | ").append(NAMES[area].substringBefore(" (")).append(' ').append(mbps(bytes[area], seconds))
        }
        return out.toString()
    }

    private fun mbps(value: Long, seconds: Double): String =
        if (value < 0) "n/a" else String.format(Locale.US, "%.2f MB/s", value / (1024.0 * 1024.0) / seconds)
}

/**
 * Counts measuring this element (and what it holds) as [measureArea] and drawing it as
 * [drawArea]; only in the builds with the lab (not in a release build with the developer options
 * unlocked: that would cost in every layout), elsewhere the modifier is left as it is.
 */
internal fun Modifier.probed(measureArea: Int, drawArea: Int): Modifier =
    if (!PerfLab.labBuild) {
        this
    } else {
        this
            .layout { measurable, constraints ->
                AllocProbe.begin(measureArea)
                val placeable = try {
                    measurable.measure(constraints)
                } finally {
                    AllocProbe.end(measureArea)
                }
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            .drawWithContent {
                AllocProbe.begin(drawArea)
                try {
                    drawContent()
                } finally {
                    AllocProbe.end(drawArea)
                }
            }
    }
