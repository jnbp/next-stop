package io.github.jnbp.nextstop.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Debug
import android.os.SystemClock
import android.view.Choreographer
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.BuildConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer

/*
 * A hidden lab for measuring the map on a real phone, in debug and staging builds and behind
 * the unlocked developer options: a small panel over the app with a live frame counter and
 * switches for each suspected cost (the frosted glass, the vehicles, the app's own layers, the
 * labels of the base map). It is opened by "Open performance lab" in the developer options
 * (Settings > Advanced). Locked in a release build it cannot be
 * opened, and every switch keeps its normal value, so the app behaves as without the lab.
 */

/** The switches of the performance lab; all on means the app as it ships. */
object PerfLab {
    /** True in debug and staging builds: the lab and its probes are built in. */
    val labBuild: Boolean = BuildConfig.DEBUG || BuildConfig.BUILD_TYPE == "staging"

    /**
     * The lab can be opened: in debug and staging builds, and in any build while the developer
     * options are unlocked (see [unlock]). A plain field, cheap to read anywhere; the costly
     * probes in hot paths (see Modifier.probed) follow [labBuild] instead.
     */
    @JvmField
    var available: Boolean = labBuild

    /** Follows the developer options: unlocked, the lab can be opened in a release build too. */
    fun unlock(developerOptions: Boolean) {
        available = labBuild || developerOptions
        if (!available) shown = false
    }

    /** The panel is on screen. */
    var shown by mutableStateOf(false)

    /** The frosted glass reads the map back as its picture; off, glass falls back to plain white. */
    var glass by mutableStateOf(true)

    /** The glass picture moves with a panning or zooming camera instead of being read back each 100 ms (new in 0.1.2). */
    var glassFollows by mutableStateOf(true)

    /** With a SurfaceView map: the glass shows the blurred map from snapshots taken while the camera rests; off, the frosted tint alone. */
    var glassSnapshot by mutableStateOf(true)

    /** The vehicles are put into their layer at all. */
    var vehicleUpdates by mutableStateOf(true)

    /** Vehicle updates only when a vehicle moved visibly (new in 0.1.2); off is the old 30 updates per second. */
    var vehicleGate by mutableStateOf(true)

    /** The beat of the live dots of trains. */
    var livePulse by mutableStateOf(true)

    /** The stop signs are worked out off the main thread (new in 0.1.2). */
    var signsInBackground by mutableStateOf(true)

    /** Groups of map layers hidden in the lab, by the start of their ids (see LAYER_GROUPS). */
    var hiddenLayers by mutableStateOf<Set<String>>(emptySet())

    /** The map shown now draws into a TextureView (true) or a SurfaceView (false); set by the map as it is made. */
    var textureMapInUse = false
        internal set

    /**
     * Whether a new map draws into a TextureView, the old way, which lets the glass read the map
     * back, instead of a SurfaceView of its own. Kept in the preferences of the lab; off by
     * default and always off outside the lab (see MapBackground).
     */
    fun textureMap(context: Context): Boolean =
        available && labPrefs(context).getBoolean(KEY_TEXTURE_MAP, false)

    /** Chooses the kind of map for the next one that is made. */
    fun setTextureMap(context: Context, on: Boolean) {
        labPrefs(context).edit().putBoolean(KEY_TEXTURE_MAP, on).apply()
    }

    /** Whether the case "fresh start" of the auto test runs at the next start (see runFreshStartTest). */
    fun freshStartArmed(context: Context): Boolean =
        available && labPrefs(context).getBoolean(KEY_FRESH_START, false)

    /** Arms the case "fresh start" for the next start, or disarms it. */
    fun armFreshStart(context: Context, on: Boolean) {
        labPrefs(context).edit().putBoolean(KEY_FRESH_START, on).apply()
    }

    private fun labPrefs(context: Context) = context.getSharedPreferences(LAB_PREFS, Context.MODE_PRIVATE)

    // The map shown now and its view, for the auto test (see PerfAutoTest); set and cleared by MapBackground.
    internal var map: MapLibreMap? = null
        private set
    internal var mapView: View? = null
        private set

    /** Called by the map once it is ready. */
    fun attach(view: View, readyMap: MapLibreMap) {
        // Only where the lab exists: no build for users keeps a view in a static field.
        if (!available) return
        mapView = view
        map = readyMap
    }

    /** Called by the map as it goes. */
    fun detach(view: View) {
        if (mapView !== view) return
        mapView = null
        map = null
    }

    // The connections chart while it is open, for the auto test (see PerfAutoTest); set and cleared by the chart.
    internal var chart: LazyListState? = null

    /** What the auto test does right now ("Test 3/6: ..."), null while it does not run. */
    var autoStatus by mutableStateOf<String?>(null)

    /** How far the case of the auto test running now is, 0 to 1; drawn each frame by the panel. */
    var autoProgress by mutableFloatStateOf(0f)

    /** The table of the last auto test, null before the first one. */
    var autoResult by mutableStateOf<String?>(null)

    // Frames of the map drawn since the panel opened, and their time, for the panel (see MapBackground).
    internal var mapFrames = 0L
    internal var mapFrameMillis = 0.0

    /** Snapshots of the map are the map alone, without MapLibre's merge of the view (see MapBackground). */
    var snapshotPlain = false
        internal set

    /** Called by the map for each frame it drew, while the panel is shown. */
    fun mapFrame(encodingSeconds: Double, renderingSeconds: Double) {
        mapFrames++
        mapFrameMillis += (encodingSeconds + renderingSeconds) * 1000.0
    }
}

/** Groups of layers the lab can hide: the label and the starts of the layer ids (null: the symbols of the base map). */
private val LAYER_GROUPS = listOf(
    "Network lines" to listOf("ns-network", "ns-rapid"),
    "Vehicles" to listOf("ns-vehicle"),
    "Stop signs" to listOf("ns-stop", "ns-selected-stop"),
    "Station details" to listOf("ns-detail", "ns-selected-pos", "ns-selected-ent", "ns-selected-lift", "ns-selected-esc"),
    "Notices" to listOf("ns-notice"),
    "Base map labels" to listOf(BASE_SYMBOLS),
)

// Stands for the symbol layers of the base map (its labels and icons) in LAYER_GROUPS.
private const val BASE_SYMBOLS = "*base-symbols"

/** Every group of layers the lab can hide, all hidden. */
internal fun allLabLayers(): Set<String> = LAYER_GROUPS.flatMap { it.second }.toSet()

// The preferences of the lab, and the choice of a TextureView for the map in them.
private const val LAB_PREFS = "next-stop-perflab"
private const val KEY_TEXTURE_MAP = "texture-map"
private const val KEY_FRESH_START = "fresh-start"

/** Hides the layer groups switched off in the lab; call after the app has set its own visibility. */
fun applyPerfLabLayers(style: Style) {
    val hidden = PerfLab.hiddenLayers
    // The base map's own layers are not shown again by the app (see showAppLayers): the lab
    // brings back those it hid.
    if (BASE_SYMBOLS !in hidden && hiddenBaseLayers.isNotEmpty()) {
        for (id in hiddenBaseLayers) style.getLayer(id)?.setProperties(PropertyFactory.visibility(Property.VISIBLE))
        hiddenBaseLayers.clear()
    }
    if (hidden.isEmpty()) return
    for (layer in style.layers) {
        val id = layer.id
        val base = layer is SymbolLayer && !id.startsWith("ns-")
        val off = hidden.any { prefix -> if (prefix == BASE_SYMBOLS) base else id.startsWith(prefix) }
        if (!off) continue
        if (base) {
            if (layer.visibility.value == Property.NONE) continue
            hiddenBaseLayers.add(id)
        }
        layer.setProperties(PropertyFactory.visibility(Property.NONE))
    }
}

// The layers of the base map the lab hid, to be shown again.
private val hiddenBaseLayers = HashSet<String>()

/** What the panel shows, worked out twice a second. */
private data class FrameReport(
    val fps: Int = 0,
    val worstMillis: Int = 0,
    val jankShare: Int = 0,
    val dropped: Long = 0,
    val heapMb: Int = 0,
    val churnMbPerSecond: Float = 0f,
    val gcs: String = "",
    val mapFps: Int = 0,
    val mapFrameMillis: Float = 0f,
    val alloc: String = "",
)

/**
 * The panel of the lab over the app, while it is open: frame figures of the main thread and of
 * the map, and the switches. It takes touches only on itself, so the map stays usable.
 */
@Composable
fun PerfLabOverlay() {
    // Read first, so the overlay comes when the developer options open the lab later.
    val shown = PerfLab.shown
    if (!PerfLab.available) return
    val context = LocalContext.current
    // Armed, the case "fresh start" opens the panel at the start (its counters run from then on).
    LaunchedEffect(Unit) {
        if (PerfLab.freshStartArmed(context)) PerfLab.shown = true
    }
    if (!shown) return
    val refreshRate = remember(context) {
        val rate = try {
            context.display?.refreshRate
        } catch (e: UnsupportedOperationException) {
            null
        }
        rate?.takeIf { it > 1f } ?: 60f
    }
    val vsyncMillis = 1000f / refreshRate
    val scope = rememberCoroutineScope()
    // The choice of a TextureView for the next map, as kept in the preferences.
    var textureNext by remember { mutableStateOf(PerfLab.textureMap(context)) }
    // Filled by the frame callback, read by the report below; only used on the main thread.
    val window = remember { FloatArray(1) }
    // Frames, janky frames and dropped frames since the last reset.
    val counts = remember { LongArray(3) }
    var report by remember { mutableStateOf(FrameReport()) }
    // The counters of "alloc by area" run while the panel is open (see AllocProbe).
    DisposableEffect(Unit) {
        AllocProbe.start()
        onDispose { AllocProbe.stop() }
    }
    // The case "fresh start", armed before: once, as soon as the first map is ready.
    LaunchedEffect(Unit) {
        if (!PerfLab.freshStartArmed(context)) return@LaunchedEffect
        PerfLab.armFreshStart(context, false)
        runFreshStartTest(context, refreshRate)?.let { PerfLab.autoResult = it }
    }
    var collapsed by remember { mutableStateOf(false) }
    var reset by remember { mutableStateOf(0) }
    DisposableEffect(reset) {
        val choreographer = Choreographer.getInstance()
        var last = 0L
        var active = true
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!active) return
                if (last != 0L) {
                    val millis = (frameTimeNanos - last) / 1_000_000f
                    counts[0]++
                    if (millis > vsyncMillis * 1.5f) {
                        counts[1]++
                        counts[2] += (millis / vsyncMillis).toLong() - 1
                    }
                    if (millis > window[0]) window[0] = millis
                }
                last = frameTimeNanos
                choreographer.postFrameCallback(this)
            }
        }
        counts.fill(0L)
        window[0] = 0f
        // The frame counts of the map only ever grow: the panel and the auto test each count from where they start.
        choreographer.postFrameCallback(callback)
        onDispose {
            active = false
            choreographer.removeFrameCallback(callback)
        }
    }
    LaunchedEffect(reset) {
        val runtime = Runtime.getRuntime()
        var frames = 0L
        var janky = 0L
        var mapFrames = PerfLab.mapFrames
        var mapMillis = PerfLab.mapFrameMillis
        var used = runtime.totalMemory() - runtime.freeMemory()
        var grown = 0L
        var since = SystemClock.uptimeMillis()
        val startGcs = Debug.getRuntimeStat("art.gc.gc-count")?.toLongOrNull() ?: 0L
        while (true) {
            delay(500)
            val now = SystemClock.uptimeMillis()
            val seconds = (now - since).coerceAtLeast(1L) / 1000f
            val nowUsed = runtime.totalMemory() - runtime.freeMemory()
            // Only growth counts: a collection in between makes the heap smaller, not the churn.
            if (nowUsed > used) grown += nowUsed - used
            used = nowUsed
            val newFrames = counts[0] - frames
            val newJanky = counts[1] - janky
            val newMapFrames = PerfLab.mapFrames - mapFrames
            val newMapMillis = PerfLab.mapFrameMillis - mapMillis
            report = FrameReport(
                fps = (newFrames / seconds).toInt(),
                worstMillis = window[0].toInt(),
                jankShare = if (newFrames > 0) (newJanky * 100 / newFrames).toInt() else 0,
                dropped = counts[2],
                heapMb = (nowUsed / (1024 * 1024)).toInt(),
                churnMbPerSecond = grown / (1024f * 1024f) / seconds,
                gcs = ((Debug.getRuntimeStat("art.gc.gc-count")?.toLongOrNull() ?: 0L) - startGcs).toString(),
                mapFps = (newMapFrames / seconds).toInt(),
                mapFrameMillis = if (newMapFrames > 0) (newMapMillis / newMapFrames).toFloat() else 0f,
                // Logged every two seconds under NextStopPerf; the panel shows the short form.
                alloc = if (AllocProbe.report()) AllocProbe.shortLine else report.alloc,
            )
            frames = counts[0]
            janky = counts[1]
            mapFrames = PerfLab.mapFrames
            mapMillis = PerfLab.mapFrameMillis
            grown = 0L
            window[0] = 0f
            since = now
        }
    }
    Box(modifier = Modifier.statusBarsPadding().padding(top = 64.dp, start = 8.dp)) {
        Column(
            modifier = Modifier
                .width(if (collapsed) 150.dp else 230.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xE0101418))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val mono = FontFamily.Monospace
            val ink = Color.White
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Perf lab", color = ink, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(if (collapsed) "+" else "-", color = ink, fontSize = 14.sp, modifier = Modifier.clickable { collapsed = !collapsed }.padding(horizontal = 6.dp))
                Text("x", color = ink, fontSize = 14.sp, modifier = Modifier.clickable { PerfLab.shown = false }.padding(horizontal = 6.dp))
            }
            val r = report
            val jankColor = if (r.jankShare > 5) Color(0xFFFF8A80) else Color(0xFFB9F6CA)
            Text("UI ${r.fps} fps  jank ${r.jankShare}%", color = jankColor, fontFamily = mono, fontSize = 11.sp)
            Text("worst ${r.worstMillis} ms  dropped ${r.dropped}", color = ink, fontFamily = mono, fontSize = 11.sp)
            Text("map ${r.mapFps} fps  ${"%.1f".format(r.mapFrameMillis)} ms", color = ink, fontFamily = mono, fontSize = 11.sp)
            Text("heap ${r.heapMb} MB  +${"%.2f".format(r.churnMbPerSecond)} MB/s  gc ${r.gcs}", color = ink, fontFamily = mono, fontSize = 11.sp)
            Text("map view " + (if (PerfLab.textureMapInUse) "texture" else "surface"), color = ink, fontFamily = mono, fontSize = 11.sp)
            if (r.alloc.isNotEmpty()) Text(r.alloc, color = ink, fontFamily = mono, fontSize = 10.sp)
            PerfLab.autoStatus?.let {
                Text(it, color = Color(0xFFFFE082), fontFamily = mono, fontSize = 11.sp)
                // Read only while drawing: the bar is drawn anew each frame of a case, nothing is composed again.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .drawBehind {
                            drawRect(Color(0xFFFFE082), size = size.copy(width = size.width * PerfLab.autoProgress))
                        },
                )
            }
            if (!collapsed) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    LabSwitch("Glass picture", PerfLab.glass) { PerfLab.glass = it }
                    LabSwitch("Glass follows camera", PerfLab.glassFollows) { PerfLab.glassFollows = it }
                    LabSwitch("Glass via snapshot", PerfLab.glassSnapshot) { PerfLab.glassSnapshot = it }
                    LabSwitch("Vehicle updates", PerfLab.vehicleUpdates) { PerfLab.vehicleUpdates = it }
                    LabSwitch("Vehicle gate", PerfLab.vehicleGate) { PerfLab.vehicleGate = it }
                    LabSwitch("Live pulse", PerfLab.livePulse) { PerfLab.livePulse = it }
                    LabSwitch("Signs off main thread", PerfLab.signsInBackground) { PerfLab.signsInBackground = it }
                    for ((label, prefixes) in LAYER_GROUPS) {
                        val on = prefixes.none { it in PerfLab.hiddenLayers }
                        LabSwitch(label, on) { show ->
                            PerfLab.hiddenLayers = if (show) PerfLab.hiddenLayers - prefixes.toSet() else PerfLab.hiddenLayers + prefixes
                        }
                    }
                    // Takes effect for the next map: the activity is made again at once.
                    LabSwitch("SurfaceView map (restart)", !textureNext) { surface ->
                        PerfLab.setTextureMap(context, !surface)
                        textureNext = !surface
                        context.findActivity()?.recreate()
                    }
                    LabAction("Reset counters") { reset++ }
                    val running = PerfLab.autoStatus != null
                    LabAction(if (running) "Auto test running..." else "Auto test") {
                        if (!running) {
                            scope.launch {
                                PerfLab.autoResult = runAutoTest(context, refreshRate) { reset++ }
                            }
                        }
                    }
                    // The first 8 s of the map after a start of the app (see runFreshStartTest).
                    LabAction("Fresh start test (then close and reopen the app)") {
                        PerfLab.armFreshStart(context, true)
                        Toast.makeText(context, "Armed: close the app from the recent apps and open it again", Toast.LENGTH_LONG).show()
                    }
                    val result = PerfLab.autoResult
                    if (result != null && !running) {
                        LabAction("Copy results") { copyResults(context, result) }
                        LabAction("Share results") { shareResults(context, result) }
                    }
                    // Back log: the last back gestures as the app saw them (see BackLog).
                    Text("Back log (${BackLog.size} lines)", color = ink, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                    LabAction("Copy back log") { BackLog.copy(context) }
                    // Drives a back gesture on the page in front and logs it frame by frame (see BackProbe).
                    LabAction(if (BackProbe.running) "Back probe running..." else "Back probe (current page)") {
                        if (!BackProbe.running) scope.launch { BackProbe.run(context) }
                    }
                    LabAction("Clear back log") { BackLog.clear() }
                    // Back probe: where the back gesture comes from (see BackSource), kept over restarts.
                    LabAction("Back source: " + BackSourceChoice.load(context).label) { BackSourceChoice.cycle(context) }
                    // Who works out the progress in the main window (see BackWindows); the activity is made again at once.
                    LabAction("Back progress: " + (if (BackWindows.load(context)) "system" else "app window") + " (restart)") {
                        BackWindows.toggle(context)
                        context.findActivity()?.recreate()
                    }
                    LabAction("Log windows") { BackWindows.logStart() }
                }
            }
        }
    }
}

/** One action of the lab: a line of blue text that does [onClick]. */
@Composable
private fun LabAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Color(0xFF80D8FF),
        fontSize = 12.sp,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 6.dp),
    )
}

/** The activity behind [this] context, if there is one. */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/** Puts the table of an auto test on the clipboard. */
private fun copyResults(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Next Stop perf", text))
    Toast.makeText(context, "Results copied", Toast.LENGTH_SHORT).show()
}

/** Opens the share sheet of the system with the table of an auto test. */
private fun shareResults(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "Next Stop perf auto test")
        .putExtra(Intent.EXTRA_TEXT, text)
    try {
        context.startActivity(Intent.createChooser(send, null))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app to share with", Toast.LENGTH_SHORT).show()
    }
}

/** One switch of the lab: a line of text that turns it on and off. */
@Composable
private fun LabSwitch(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Text(
        text = (if (on) "[x] " else "[ ] ") + label,
        color = if (on) Color.White else Color(0xFF9E9E9E),
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        modifier = Modifier.clickable { onChange(!on) }.padding(vertical = 4.dp),
    )
}
