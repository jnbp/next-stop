package io.github.jnbp.nextstop.ui

import android.content.Context
import android.graphics.PointF
import android.os.Build
import android.os.Debug
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.withFrameNanos
import io.github.jnbp.nextstop.BuildConfig
import io.github.jnbp.nextstop.data.DiagLog
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraUpdateFactory

/*
 * The auto test of the performance lab: it moves the map by itself along a fixed path for each
 * of a few settings of the lab and writes what the frames did as one small table, to logcat
 * (tag NextStopPerf), to a file in the app's external files and to the panel (copy, share).
 * The camera is moved frame by frame the way a finger moves it (scrollBy and setZoom go the
 * same way into the native map as a pan or a pinch), not with a camera animation of the map.
 * Started while the connections chart is open, it scrolls the chart sideways instead (see
 * CHART_CASES), so the chart and the glide of its time window are measured on their own.
 */

/** The tag of the auto test in logcat. */
internal const val PERF_LOG_TAG = "NextStopPerf"

// How long the camera moves per case, how long the map then rests, and how long each case waits
// after its switches were set before it starts (new layers, a new style of the vehicles).
private const val PATH_MILLIS = 8_000L
// The rest is longer than the wait of the glass for an idle map (SNAPSHOT_IDLE_WAIT_MILLIS in
// MapBackground), so the snapshot after the camera came to rest falls into it and shows as idle.
private const val IDLE_MILLIS = 3_000L
// Long enough for the snapshot of the glass after the camera came to rest (see MapBackground)
// to be over before the next case moves the map.
private const val SETTLE_MILLIS = 3_000L

/**
 * The path of the camera: times in milliseconds, then where the middle of the map is moved to
 * from where it started, in widths and heights of the map, and the change of the zoom. Between
 * two points it eases in and out, like a single swipe or pinch.
 */
private class CameraPath(
    val times: LongArray,
    val x: FloatArray,
    val y: FloatArray,
    val zoom: FloatArray,
    // With this, zoom is a share of the way from the zoom at the start out to COUNTRY_ZOOM.
    val toCountry: Boolean = false,
)

/** The pan and pinch of a finger near where the map stands. */
private val PAN_PATH = CameraPath(
    times = longArrayOf(0, 1_600, 2_800, 4_000, 5_200, 6_400, 8_000),
    x = floatArrayOf(0f, 0.6f, 0.6f, 0.6f, 0f, 0f, 0f),
    y = floatArrayOf(0f, 0f, 0.5f, 0.5f, 0.5f, 0.5f, 0f),
    zoom = floatArrayOf(0f, 0f, 0f, 1.2f, 1.2f, 0f, 0f),
)

/** Zoomed out to the whole country and back in to where it started, as a few pinches would. */
private val COUNTRY_PATH = CameraPath(
    times = longArrayOf(0, 3_000, 4_000, 7_000, 8_000),
    x = floatArrayOf(0f, 0f, 0f, 0f, 0f),
    y = floatArrayOf(0f, 0f, 0f, 0f, 0f),
    zoom = floatArrayOf(0f, 1f, 1f, 0f, 0f),
    toCountry = true,
)

// The zoom of the whole of Germany on a phone, for COUNTRY_PATH.
private const val COUNTRY_ZOOM = 5.5

/** Where [path] is after [millis]: x and y (shares of the map) and the change of zoom from [startZoom], into [into]. */
private fun pathAt(path: CameraPath, millis: Long, startZoom: Double, into: FloatArray) {
    val times = path.times
    var segment = 0
    while (segment < times.size - 2 && millis >= times[segment + 1]) segment++
    val from = times[segment]
    val to = times[segment + 1]
    val share = ((millis - from).toFloat() / (to - from)).coerceIn(0f, 1f)
    val eased = share * share * (3f - 2f * share)
    into[0] = path.x[segment] + (path.x[segment + 1] - path.x[segment]) * eased
    into[1] = path.y[segment] + (path.y[segment + 1] - path.y[segment]) * eased
    val zoom = path.zoom[segment] + (path.zoom[segment + 1] - path.zoom[segment]) * eased
    into[2] = if (path.toCountry) zoom * (COUNTRY_ZOOM - startZoom).toFloat().coerceAtMost(0f) else zoom
}

/** The switches of the lab at one moment, to be set again later. */
private class LabSwitches(
    val glass: Boolean,
    val glassFollows: Boolean,
    val vehicleUpdates: Boolean,
    val vehicleGate: Boolean,
    val livePulse: Boolean,
    val signsInBackground: Boolean,
    val hiddenLayers: Set<String>,
    val glassSnapshot: Boolean,
) {
    fun write() {
        PerfLab.glass = glass
        PerfLab.glassFollows = glassFollows
        PerfLab.vehicleUpdates = vehicleUpdates
        PerfLab.vehicleGate = vehicleGate
        PerfLab.livePulse = livePulse
        PerfLab.signsInBackground = signsInBackground
        PerfLab.hiddenLayers = hiddenLayers
        PerfLab.glassSnapshot = glassSnapshot
    }

    companion object {
        fun read() = LabSwitches(
            PerfLab.glass,
            PerfLab.glassFollows,
            PerfLab.vehicleUpdates,
            PerfLab.vehicleGate,
            PerfLab.livePulse,
            PerfLab.signsInBackground,
            PerfLab.hiddenLayers,
            PerfLab.glassSnapshot,
        )

        /** Everything on: the app as it ships. */
        val allOn = LabSwitches(true, true, true, true, true, true, emptySet(), true)
    }
}

/**
 * One case of the auto test: its name, the camera [path] it moves the map along, and what it
 * switches off, starting from all on. A case that [opens] opens journeys out of the chart instead
 * of scrolling it (see measureChartOpenCase).
 */
private class LabCase(
    val name: String,
    val path: CameraPath = PAN_PATH,
    val opens: Boolean = false,
    // A case that leaves the chart alone and only watches it (see measureChartIdleCase).
    val idle: Boolean = false,
    val apply: () -> Unit,
)

private val LAB_CASES = listOf(
    LabCase("all on") {},
    // Out to the country and back: what comes back in (vehicles, signs, details) after a while far out.
    LabCase("zoom out+in", COUNTRY_PATH) {},
    LabCase("glass snapshot off") { PerfLab.glassSnapshot = false },
    LabCase("glass follow off") { PerfLab.glassFollows = false },
    LabCase("vehicle gate off") { PerfLab.vehicleGate = false },
    LabCase("signs bg off") { PerfLab.signsInBackground = false },
    LabCase("layers off") { PerfLab.hiddenLayers = allLabLayers() },
    LabCase("all off") {
        PerfLab.glass = false
        PerfLab.glassFollows = false
        PerfLab.vehicleUpdates = false
        PerfLab.vehicleGate = false
        PerfLab.livePulse = false
        PerfLab.signsInBackground = false
        PerfLab.hiddenLayers = allLabLayers()
    },
)

/**
 * The cases where the connections chart is open: it is scrolled sideways instead of the map
 * being moved, as it ships and without the glass.
 */
private val CHART_CASES = listOf(
    // Nothing touched for a while: what the open chart costs by itself (clocks, springs, refreshes).
    LabCase("chart idle", idle = true) {},
    LabCase("chart") {},
    LabCase("chart glass off") { PerfLab.glass = false },
    LabCase("chart open", opens = true) {},
)

/**
 * The path of the chart: times in milliseconds and how far it is scrolled sideways, in widths
 * of its view. Each move eases in and out like a swipe; the pauses let the window glide to its
 * place, which is measured too.
 */
private val CHART_TIMES = longArrayOf(0, 1_500, 3_000, 4_500, 6_000, 8_000)
private val CHART_X = floatArrayOf(0f, 1.2f, 1.2f, 0.4f, 0.4f, 0f)

/** Where the chart path is after [millis], in widths of the view. */
private fun chartPathAt(millis: Long): Float {
    var segment = 0
    while (segment < CHART_TIMES.size - 2 && millis >= CHART_TIMES[segment + 1]) segment++
    val from = CHART_TIMES[segment]
    val to = CHART_TIMES[segment + 1]
    val share = ((millis - from).toFloat() / (to - from)).coerceIn(0f, 1f)
    val eased = share * share * (3f - 2f * share)
    return CHART_X[segment] + (CHART_X[segment + 1] - CHART_X[segment]) * eased
}

/** What one case measured. Frame times are the intervals between frames of the main thread. */
private class CaseResult(
    val name: String,
    val uiFps: Float,
    val jankPercent: Float,
    val worstMillis: Float,
    val p95Millis: Float,
    val idleWorstMillis: Float,
    val mapFps: Float,
    val mapMillis: Float,
    val churnMbPerSecond: Float,
    val gcs: Long,
    val hertz: Float,
    // What the alloc counters saw during the move (see AllocProbe); empty where they were off.
    val alloc: String = "",
)

/** How the glass gets its picture: read back from a texture, snapshots of a surface, or the tint alone. */
private fun glassMode(): String = when {
    !PerfLab.glass -> "off"
    PerfLab.textureMapInUse -> "readback"
    PerfLab.glassSnapshot -> if (PerfLab.snapshotPlain) "snapshot (map only)" else "snapshot (merged with view)"
    else -> "tint"
}

private fun gcCount(): Long = Debug.getRuntimeStat("art.gc.gc-count")?.toLongOrNull() ?: 0L

/**
 * Runs every case of the auto test and gives back the table of results, also written to logcat
 * and to a file. [resetPanel] resets the counters of the panel at the start of each case. The
 * switches of the lab are set back as they were at the end, also when the test is cancelled.
 */
internal suspend fun runAutoTest(context: Context, refreshRate: Float, resetPanel: () -> Unit): String {
    val vsyncMillis = 1000f / refreshRate
    val saved = LabSwitches.read()
    val results = ArrayList<CaseResult>()
    var problem: String? = null
    // With the connections chart open, the chart is scrolled instead of the map being moved.
    val chart = PerfLab.chart
    val cases = if (chart != null) CHART_CASES else LAB_CASES
    try {
        for ((index, case) in cases.withIndex()) {
            PerfLab.autoStatus = "Test ${index + 1}/${cases.size}: ${case.name}…"
            LabSwitches.allOn.write()
            case.apply()
            delay(SETTLE_MILLIS)
            resetPanel()
            val result = when {
                chart == null -> measureCase(context, case.name, case.path, vsyncMillis)
                case.idle -> measureChartIdleCase(context, case.name, vsyncMillis)
                case.opens -> measureChartOpenCase(context, case.name, vsyncMillis)
                else -> measureChartCase(context, case.name, vsyncMillis, chart)
            }
            if (result == null) {
                problem = when {
                    chart == null -> "no map to move (open the map tab)"
                    case.opens -> "no journey to open"
                    else -> "no chart to scroll"
                }
                break
            }
            results.add(result)
        }
    } finally {
        saved.write()
        PerfLab.autoStatus = null
        PerfLab.autoProgress = 0f
    }
    val text = resultTable(context, refreshRate, results, problem, chart != null)
    for (line in text.lines()) DiagLog.i(PERF_LOG_TAG) { line }
    withContext(Dispatchers.IO) { writeResults(context, text) }
    return text
}

// How long the fresh start test waits for the first map after the start.
private const val FRESH_MAP_WAIT_MILLIS = 30_000L

/**
 * The case "fresh start": armed in the lab, it runs once at the next start of the app, as soon
 * as the first map is ready, without the settling time of the other cases: the first 8 s of
 * the map, while it loads its tiles and the app its data (networks, packs, vehicles), as when
 * someone opens the map and pans at once. Gives back the table, also written to logcat and to
 * a file; null where no map came.
 */
internal suspend fun runFreshStartTest(context: Context, refreshRate: Float): String? {
    val vsyncMillis = 1000f / refreshRate
    PerfLab.autoStatus = "Fresh start: waiting for the map…"
    try {
        var waited = 0L
        var last = 0L
        while (PerfLab.map == null || (PerfLab.mapView?.width ?: 0) < 1) {
            val frame = withFrameNanos { it } / 1_000_000L
            if (last != 0L) waited += frame - last
            last = frame
            if (waited > FRESH_MAP_WAIT_MILLIS) return null
        }
        val sinceShown = if (MapQuiet.shownAt == 0L) -1L else android.os.SystemClock.uptimeMillis() - MapQuiet.shownAt
        PerfLab.autoStatus = "Fresh start: measuring…"
        val result = measureCase(context, "fresh start", PAN_PATH, vsyncMillis) ?: return null
        val text = resultTable(context, refreshRate, listOf(result), null, false) +
            "\nfresh start: the move began ${sinceShown} ms after the first map was ready"
        for (line in text.lines()) DiagLog.i(PERF_LOG_TAG) { line }
        withContext(Dispatchers.IO) { writeResults(context, text) }
        return text
    } finally {
        PerfLab.autoStatus = null
        PerfLab.autoProgress = 0f
    }
}

/** Writes [text] to a new file in the folder perf of the app's external files (Android/data/<app>/files/perf). */
private fun writeResults(context: Context, text: String) {
    val dir = context.getExternalFilesDir("perf") ?: return
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    val file = File(dir, "perf-$stamp.txt")
    try {
        file.writeText(text)
        DiagLog.i(PERF_LOG_TAG) { "written to ${file.absolutePath}" }
    } catch (e: IOException) {
        DiagLog.w(PERF_LOG_TAG, e) { "could not write the results" }
    }
}

/**
 * Moves the map along the path frame by frame, then lets it rest, and measures; null where
 * there is no map to move.
 */
private suspend fun measureCase(context: Context, name: String, path: CameraPath, vsyncMillis: Float): CaseResult? {
    val map = PerfLab.map ?: return null
    val view = PerfLab.mapView ?: return null
    val width = view.width.toFloat()
    val height = view.height.toFloat()
    if (width < 1f || height < 1f) return null
    val start = map.cameraPosition
    val startZoom = start.zoom
    val focal = PointF(width / 2f, height / 2f)
    val intervals = FloatArray(4_096)
    var count = 0
    var janky = 0
    val runtime = Runtime.getRuntime()
    var used = runtime.totalMemory() - runtime.freeMemory()
    var grown = 0L
    val gcsBefore = gcCount()
    val mapFramesBefore = PerfLab.mapFrames
    val mapMillisBefore = PerfLab.mapFrameMillis
    val point = FloatArray(3)
    var doneX = 0f
    var doneY = 0f
    var doneZoom = 0f
    var began = 0L
    var last = 0L
    // The alloc counters count this move alone; the panel leaves them alone meanwhile.
    AllocProbe.reset()
    AllocProbe.held = true
    var alloc = ""
    try {
        while (true) {
            val frame = withFrameNanos { it }
            if (began == 0L) {
                began = frame
            } else {
                val millis = (frame - last) / 1_000_000f
                if (count < intervals.size) intervals[count] = millis
                count++
                if (millis > vsyncMillis * 1.5f) janky++
            }
            last = frame
            val elapsed = (frame - began) / 1_000_000L
            if (elapsed >= PATH_MILLIS) break
            // Drawn by the panel each frame: the window keeps drawing as under a finger, so the
            // display keeps its rate in every case (with nothing of the app drawing, a phone with
            // an adaptive rate lowers it, and the frames of the main thread with it).
            PerfLab.autoProgress = elapsed.toFloat() / (PATH_MILLIS + IDLE_MILLIS)
            pathAt(path, elapsed, startZoom, point)
            val x = point[0] * width
            val y = point[1] * height
            if (x != doneX || y != doneY) {
                map.scrollBy(x - doneX, y - doneY)
                doneX = x
                doneY = y
            }
            if (point[2] != doneZoom) {
                map.setZoom(startZoom + point[2], focal, 0)
                doneZoom = point[2]
            }
            val nowUsed = runtime.totalMemory() - runtime.freeMemory()
            if (nowUsed > used) grown += nowUsed - used
            used = nowUsed
        }
        if (AllocProbe.on) alloc = AllocProbe.summary()
    } finally {
        AllocProbe.held = false
    }
    val seconds = ((last - began) / 1_000_000_000.0).coerceAtLeast(0.001)
    val mapFrames = PerfLab.mapFrames - mapFramesBefore
    val mapMillis = PerfLab.mapFrameMillis - mapMillisBefore
    val gcs = gcCount() - gcsBefore
    // The rate the display ran at while the camera moved.
    val hertz = try {
        context.display?.refreshRate ?: 0f
    } catch (e: UnsupportedOperationException) {
        0f
    }
    // Back where it started, as a finger lifted (the map reports its idle camera then).
    map.moveCamera(CameraUpdateFactory.newCameraPosition(start))
    // The rest after the move: late tiles and labels come in now.
    var idleWorst = 0f
    var idleLast = 0L
    var idleBegan = 0L
    while (true) {
        val frame = withFrameNanos { it }
        if (idleBegan == 0L) {
            idleBegan = frame
        } else {
            val millis = (frame - idleLast) / 1_000_000f
            if (millis > idleWorst) idleWorst = millis
        }
        idleLast = frame
        val idleElapsed = (frame - idleBegan) / 1_000_000L
        if (idleElapsed >= IDLE_MILLIS) break
        PerfLab.autoProgress = (PATH_MILLIS + idleElapsed).toFloat() / (PATH_MILLIS + IDLE_MILLIS)
    }
    val measured = minOf(count, intervals.size)
    val sorted = intervals.copyOf(measured).also { it.sort() }
    val p95 = if (measured > 0) sorted[((measured - 1) * 95) / 100] else 0f
    val worst = if (measured > 0) sorted[measured - 1] else 0f
    return CaseResult(
        name = name,
        uiFps = (count / seconds).toFloat(),
        jankPercent = if (count > 0) janky * 100f / count else 0f,
        worstMillis = worst,
        p95Millis = p95,
        idleWorstMillis = idleWorst,
        mapFps = (mapFrames / seconds).toFloat(),
        mapMillis = if (mapFrames > 0) (mapMillis / mapFrames).toFloat() else 0f,
        churnMbPerSecond = (grown / (1024.0 * 1024.0) / seconds).toFloat(),
        gcs = gcs,
        hertz = hertz,
        alloc = alloc,
    )
}

/**
 * Scrolls the connections chart sideways along its path frame by frame, as a finger does, then
 * lets it rest while its window glides to its place, and measures; null where it has no width.
 */
private suspend fun measureChartCase(context: Context, name: String, vsyncMillis: Float, chart: LazyListState): CaseResult? {
    val width = chart.layoutInfo.viewportSize.width.toFloat()
    if (width < 1f) return null
    val intervals = FloatArray(4_096)
    var count = 0
    var janky = 0
    val runtime = Runtime.getRuntime()
    var used = runtime.totalMemory() - runtime.freeMemory()
    var grown = 0L
    val gcsBefore = gcCount()
    var began = 0L
    var last = 0L
    var done = 0f
    // The alloc counters count this scroll alone (and the rest after it); the panel leaves them alone meanwhile.
    AllocProbe.reset()
    AllocProbe.held = true
    try {
        chart.scroll {
            while (true) {
                val frame = withFrameNanos { it }
                if (began == 0L) {
                    began = frame
                } else {
                    val millis = (frame - last) / 1_000_000f
                    if (count < intervals.size) intervals[count] = millis
                    count++
                    if (millis > vsyncMillis * 1.5f) janky++
                }
                last = frame
                val elapsed = (frame - began) / 1_000_000L
                if (elapsed >= PATH_MILLIS) break
                val x = chartPathAt(elapsed) * width
                if (x != done) {
                    scrollBy(x - done)
                    done = x
                }
                val nowUsed = runtime.totalMemory() - runtime.freeMemory()
                if (nowUsed > used) grown += nowUsed - used
                used = nowUsed
            }
        }
    } finally {
        AllocProbe.held = false
    }
    val alloc = if (AllocProbe.on) AllocProbe.summary() else ""
    val seconds = ((last - began) / 1_000_000_000.0).coerceAtLeast(0.001)
    val gcs = gcCount() - gcsBefore
    // The rate the display ran at while the chart scrolled.
    val hertz = try {
        context.display?.refreshRate ?: 0f
    } catch (e: UnsupportedOperationException) {
        0f
    }
    // The rest after the last move: the window and the scale glide to their place now.
    var idleWorst = 0f
    var idleLast = 0L
    var idleBegan = 0L
    while (true) {
        val frame = withFrameNanos { it }
        if (idleBegan == 0L) {
            idleBegan = frame
        } else {
            val millis = (frame - idleLast) / 1_000_000f
            if (millis > idleWorst) idleWorst = millis
        }
        idleLast = frame
        if ((frame - idleBegan) / 1_000_000L >= IDLE_MILLIS) break
    }
    val measured = minOf(count, intervals.size)
    val sorted = intervals.copyOf(measured).also { it.sort() }
    val p95 = if (measured > 0) sorted[((measured - 1) * 95) / 100] else 0f
    val worst = if (measured > 0) sorted[measured - 1] else 0f
    return CaseResult(
        name = name,
        uiFps = (count / seconds).toFloat(),
        jankPercent = if (count > 0) janky * 100f / count else 0f,
        worstMillis = worst,
        p95Millis = p95,
        idleWorstMillis = idleWorst,
        mapFps = 0f,
        mapMillis = 0f,
        churnMbPerSecond = (grown / (1024.0 * 1024.0) / seconds).toFloat(),
        gcs = gcs,
        hertz = hertz,
        alloc = alloc,
    )
}

// How long the idle case watches the chart without touching it.
private const val CHART_IDLE_MILLIS = 5_000L

/**
 * Watches the open chart for CHART_IDLE_MILLIS without touching it, and measures: what it costs
 * by itself, with its clock, springs, live refreshes and anything else that keeps running.
 */
private suspend fun measureChartIdleCase(context: Context, name: String, vsyncMillis: Float): CaseResult {
    val intervals = FloatArray(4_096)
    var count = 0
    var janky = 0
    val runtime = Runtime.getRuntime()
    var used = runtime.totalMemory() - runtime.freeMemory()
    var grown = 0L
    val gcsBefore = gcCount()
    var began = 0L
    var last = 0L
    AllocProbe.reset()
    AllocProbe.held = true
    var alloc = ""
    try {
        while (true) {
            val frame = withFrameNanos { it }
            if (began == 0L) {
                began = frame
            } else {
                val millis = (frame - last) / 1_000_000f
                if (count < intervals.size) intervals[count] = millis
                count++
                if (millis > vsyncMillis * 1.5f) janky++
            }
            last = frame
            val elapsed = (frame - began) / 1_000_000L
            // Drawn by the panel each frame, as in the other cases (the display keeps its rate).
            PerfLab.autoProgress = elapsed.toFloat() / CHART_IDLE_MILLIS
            val nowUsed = runtime.totalMemory() - runtime.freeMemory()
            if (nowUsed > used) grown += nowUsed - used
            used = nowUsed
            if (elapsed >= CHART_IDLE_MILLIS) break
        }
        if (AllocProbe.on) alloc = AllocProbe.summary()
    } finally {
        AllocProbe.held = false
    }
    val seconds = ((last - began) / 1_000_000_000.0).coerceAtLeast(0.001)
    val gcs = gcCount() - gcsBefore
    val hertz = try {
        context.display?.refreshRate ?: 0f
    } catch (e: UnsupportedOperationException) {
        0f
    }
    val measured = minOf(count, intervals.size)
    val sorted = intervals.copyOf(measured).also { it.sort() }
    val p95 = if (measured > 0) sorted[((measured - 1) * 95) / 100] else 0f
    val worst = if (measured > 0) sorted[measured - 1] else 0f
    return CaseResult(
        name = name,
        uiFps = (count / seconds).toFloat(),
        jankPercent = if (count > 0) janky * 100f / count else 0f,
        worstMillis = worst,
        p95Millis = p95,
        idleWorstMillis = worst,
        mapFps = 0f,
        mapMillis = 0f,
        churnMbPerSecond = (grown / (1024.0 * 1024.0) / seconds).toFloat(),
        gcs = gcs,
        hertz = hertz,
        alloc = alloc,
    )
}

/**
 * What the auto test needs of the connections page to open a journey out of the chart and to
 * close it again; set by the page while it shows (debug and staging only).
 */
internal object ChartOpenProbe {
    /** Opens the journey of the first column in full view as a tap on it does; false where there is none. */
    var tap: (() -> Boolean)? = null

    /** Closes the page of the journey again, back to the chart. */
    var close: (() -> Unit)? = null
}

// The chart open case: how often a journey is opened and closed again, and how long each of the
// two is watched (the growing page and its way back at an animation scale of 2 included).
private const val OPEN_ROUNDS = 3
private const val OPEN_WATCH_MILLIS = 1_600L

/**
 * Opens a journey out of the chart and goes back to the chart [OPEN_ROUNDS] times, as a tap on a
 * column and back do, and measures every frame of it; null where no journey can be opened.
 */
private suspend fun measureChartOpenCase(context: Context, name: String, vsyncMillis: Float): CaseResult? {
    val intervals = FloatArray(4_096)
    var count = 0
    var janky = 0
    val runtime = Runtime.getRuntime()
    var used = runtime.totalMemory() - runtime.freeMemory()
    var grown = 0L
    val gcsBefore = gcCount()
    var began = 0L
    var last = 0L
    val total = OPEN_ROUNDS * 2 * OPEN_WATCH_MILLIS
    var watched = 0L
    // Watches the frames for [millis], counting them as the other cases do.
    suspend fun watch(millis: Long) {
        var since = 0L
        while (true) {
            val frame = withFrameNanos { it }
            if (began == 0L) {
                began = frame
            } else {
                val interval = (frame - last) / 1_000_000f
                if (count < intervals.size) intervals[count] = interval
                count++
                if (interval > vsyncMillis * 1.5f) janky++
            }
            last = frame
            if (since == 0L) since = frame
            val elapsed = (frame - since) / 1_000_000L
            PerfLab.autoProgress = (watched + elapsed).toFloat() / total
            val nowUsed = runtime.totalMemory() - runtime.freeMemory()
            if (nowUsed > used) grown += nowUsed - used
            used = nowUsed
            if (elapsed >= millis) break
        }
        watched += millis
    }
    // The alloc counters count the openings alone; the panel leaves them alone meanwhile.
    AllocProbe.reset()
    AllocProbe.held = true
    try {
        repeat(OPEN_ROUNDS) {
            val opened = ChartOpenProbe.tap?.invoke() ?: false
            if (!opened) return null
            watch(OPEN_WATCH_MILLIS)
            ChartOpenProbe.close?.invoke() ?: return null
            watch(OPEN_WATCH_MILLIS)
        }
    } finally {
        AllocProbe.held = false
    }
    val alloc = if (AllocProbe.on) AllocProbe.summary() else ""
    val seconds = ((last - began) / 1_000_000_000.0).coerceAtLeast(0.001)
    val gcs = gcCount() - gcsBefore
    val hertz = try {
        context.display?.refreshRate ?: 0f
    } catch (e: UnsupportedOperationException) {
        0f
    }
    val measured = minOf(count, intervals.size)
    val sorted = intervals.copyOf(measured).also { it.sort() }
    val p95 = if (measured > 0) sorted[((measured - 1) * 95) / 100] else 0f
    val worst = if (measured > 0) sorted[measured - 1] else 0f
    return CaseResult(
        name = name,
        uiFps = (count / seconds).toFloat(),
        jankPercent = if (count > 0) janky * 100f / count else 0f,
        worstMillis = worst,
        p95Millis = p95,
        idleWorstMillis = 0f,
        mapFps = 0f,
        mapMillis = 0f,
        churnMbPerSecond = (grown / (1024.0 * 1024.0) / seconds).toFloat(),
        gcs = gcs,
        hertz = hertz,
        alloc = alloc,
    )
}

/** The results as a small table of plain text, with the phone and the kind of map at the top. */
private fun resultTable(context: Context, refreshRate: Float, results: List<CaseResult>, problem: String?, chart: Boolean): String {
    val out = StringBuilder()
    val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
    out.append("Next Stop perf auto test ").append(time).append('\n')
    out.append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
        .append(" | Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(')')
        .append(" | ").append(String.format(Locale.US, "%.0f Hz", refreshRate))
        .append(" | map ").append(if (PerfLab.textureMapInUse) "texture" else "surface")
        .append(" | glass ").append(glassMode())
        .append(" | ").append(BuildConfig.VERSION_NAME).append(' ').append(BuildConfig.BUILD_TYPE)
        .append('\n')
    if (chart) {
        out.append("connections chart: chart idle: 5 s untouched; 8 s sideways scroll per case, then 3 s idle; chart open: $OPEN_ROUNDS x open a journey and back. ms = frame interval, jank = frame > 1.5 vsync, Hz = display rate\n")
    } else {
        out.append("8 s pan+zoom per case (zoom out+in: out to the country and back), then 3 s idle. ms = frame interval, jank = frame > 1.5 vsync, Hz = display rate\n")
    }
    out.append(String.format(Locale.US, "%-18s %4s %5s %6s %5s %5s | %4s %5s | %5s %3s %4s\n",
        "case", "fps", "jank%", "worst", "p95", "idle", "map", "ms/f", "MB/s", "gc", "Hz"))
    for (r in results) {
        out.append(String.format(Locale.US, "%-18s %4.0f %5.1f %6.1f %5.1f %5.1f | %4.0f %5.1f | %5.2f %3d %4.0f\n",
            r.name, r.uiFps, r.jankPercent, r.worstMillis, r.p95Millis, r.idleWorstMillis,
            r.mapFps, r.mapMillis, r.churnMbPerSecond, r.gcs, r.hertz))
    }
    // Where the allocations of each move came from (see AllocProbe), one line per case.
    for (r in results) if (r.alloc.isNotEmpty()) out.append("alloc ").append(r.name).append(' ').append(r.alloc).append('\n')
    if (problem != null) out.append("stopped: ").append(problem).append('\n')
    return out.toString().trimEnd()
}
