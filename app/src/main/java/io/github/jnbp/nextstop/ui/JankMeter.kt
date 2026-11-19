package io.github.jnbp.nextstop.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.metrics.performance.FrameDataApi24
import androidx.metrics.performance.FrameDataApi31
import androidx.metrics.performance.JankStats
import io.github.jnbp.nextstop.BuildConfig
import io.github.jnbp.nextstop.R
import org.maplibre.android.maps.MapView
import java.lang.ref.WeakReference

/**
 * The jank meter of the developer options ("Jank-Messer"): while it is on and the app is
 * resumed, JankStats counts every frame of the window for the screen in front (see
 * [JankScreenTag]): frames, janky share, the share that missed its deadline, p95 and the longest
 * frame (see [JankTally]); and the map counts the frames it draws while its camera rests ("map
 * fps while idle"), by what asked for them (see [markMap]). A frame's duration is the work for
 * it on the main and the render thread, the figure to hold against the budget of one refresh
 * (8.3 ms at 120 Hz); not the time from its vsync to the screen, which with the pipeline of
 * the display is two or three refreshes even for a smooth frame. Off, nothing
 * listens. A frame costs no allocation: the tallies are fixed histograms, the listeners made once.
 * The numbers stay on the phone; "Als Text kopieren" puts them on the clipboard only.
 */
object JankMeter {
    /** The developer option; not kept over a restart of the app. */
    var enabled by mutableStateOf(false)

    // The screens met so far and their tallies, grown on the main thread only.
    private val names = ArrayList<String>()
    private val tallies = ArrayList<JankTally>()

    // The tally of the screen in front; read by the frame listener on the frame metrics thread.
    @Volatile
    private var current: JankTally? = null

    // Guards the tallies between the frame thread that counts and the main thread that reads.
    private val lock = Any()

    // The map's frames while its camera rested, and how long it rested (ms); main thread only.
    private var idleFrames = 0L
    private var idleMillis = 0L
    private var idleSince = 0L

    // What asked the map for its next frame (MAP_CAUSE_ bits), and the frames at rest by cause.
    private var pending = 0
    private val causes = MapRestCauses()

    private var map: WeakReference<MapView>? = null
    private var mapListening = false

    // The change of page running now and all changes so far by name (see JankTransitions.kt);
    // guarded by [lock], as the frame listener counts the run's frames on its own thread.
    private var run: TransitionRun? = null
    private val transitions = LinkedHashMap<String, TransitionStats>()

    // The page in front at the last composition, per kind of change ("page" for the pages of
    // the root, "journey" for the page of a journey over the chart); main thread only.
    private val lastShown = HashMap<String, String>()

    // Each frame of the window: counted for the screen in front.
    private val frameListener = JankStats.OnFrameListener { frame ->
        val tally = current ?: return@OnFrameListener
        // The work of the frame (main and render thread), not the time from its vsync to the screen.
        val nanos = (frame as? FrameDataApi24)?.frameDurationCpuNanos ?: frame.frameDurationUiNanos
        val late = ((frame as? FrameDataApi31)?.frameOverrunNanos ?: 0L) > 0L
        synchronized(lock) {
            tally.record(nanos, frame.isJank, late)
            run?.frame(frame.frameStartNanos, nanos)
        }
    }

    // Each frame the map draws (on the main thread): counted while the camera rests.
    private val mapListener = MapView.OnDidFinishRenderingFrameListener { _, _, _ ->
        if (!MapCamera.moving) {
            idleFrames++
            causes.record(pending)
        }
        pending = 0
    }

    /**
     * [cause] (a MAP_CAUSE_ bit) has just asked the map for a new frame. Main thread; a field
     * written, nothing more, so it may be called in every frame.
     */
    fun markMap(cause: Int) {
        pending = pending or cause
    }

    /**
     * The root composed with [name] as the page in front (called during its composition, so the
     * time is that of the frame that takes the change in, before the new page is composed): a
     * change of page starts a run (see TransitionRun). Main thread; nothing while the meter is off.
     */
    fun noteScreen(name: String) = noteShown("page", name)

    /**
     * As [noteScreen] for another kind of change, [kind] (the page of a journey opening over the
     * chart: "Connections" to "Detail" and back), composed where that change is.
     */
    fun noteShown(kind: String, name: String) {
        val before = lastShown.put(kind, name)
        if (!enabled || before == null || before == name) return
        val now = System.nanoTime()
        synchronized(lock) {
            run?.let { finish(it, now) }
            run = TransitionRun(before, name, now)
        }
    }

    /** The place where changes of [kind] happen left the screen: its next showing starts no run. */
    fun forgetShown(kind: String) {
        lastShown.remove(kind)
    }

    /** The page [name] was drawn; the first draw of the page a run goes to is its first frame. */
    fun pageDrawn(name: String) {
        if (!enabled) return
        synchronized(lock) {
            val running = run ?: return
            if (running.to == name && running.firstDrawNanos < 0L) running.firstDrawNanos = System.nanoTime()
        }
    }

    /** The change of page has come to rest: its run is counted. */
    fun transitionSettled() {
        synchronized(lock) {
            val running = run ?: return
            finish(running, System.nanoTime())
            run = null
        }
    }

    /** The glass backdrop took a new picture (see MapBackground): counted for the run of a change of page. */
    fun backdropTaken() {
        if (!enabled) return
        synchronized(lock) { run?.let { it.backdrops++ } }
    }

    // Under [lock].
    private fun finish(running: TransitionRun, endNanos: Long) {
        transitions.getOrPut(transitionName(running.from, running.to)) { TransitionStats() }.add(running, endNanos)
    }

    /** The section "transitions" of the report, one line per change of page; [none] without any. */
    fun transitionsText(context: Context): String {
        val first = context.getString(R.string.r6perf_jank_first_draw)
        val longest = context.getString(R.string.r6perf_jank_longest)
        val frames = context.getString(R.string.r5perf_jank_frames)
        val backdrop = context.getString(R.string.r6perf_jank_backdrop)
        val lines = synchronized(lock) {
            transitions.entries.sortedByDescending { it.value.count }.map { (name, stats) ->
                transitionText(name, stats, first, longest, frames, backdrop)
            }
        }
        return if (lines.isEmpty()) context.getString(R.string.r6perf_jank_transitions_none) else lines.joinToString("\n")
    }

    /** The screen [name] is in front now. Main thread. */
    fun tag(name: String) {
        var index = names.indexOf(name)
        if (index < 0) {
            synchronized(lock) {
                names.add(name)
                tallies.add(JankTally())
            }
            index = names.lastIndex
        }
        current = tallies[index]
    }

    /** The map view of the app was made ([attachMap]) or is gone ([detachMap]). */
    fun attachMap(view: MapView) {
        map = WeakReference(view)
        if (mapListening) view.addOnDidFinishRenderingFrameListener(mapListener)
    }

    fun detachMap(view: MapView) {
        if (map?.get() !== view) return
        if (mapListening) view.removeOnDidFinishRenderingFrameListener(mapListener)
        map = null
    }

    internal fun listenToMap(on: Boolean) {
        if (on == mapListening) return
        mapListening = on
        val view = map?.get() ?: return
        if (on) view.addOnDidFinishRenderingFrameListener(mapListener) else view.removeOnDidFinishRenderingFrameListener(mapListener)
    }

    /** The camera of the map began to move ([moving]) or came to rest, at [now] (uptime ms). */
    internal fun cameraMoving(moving: Boolean, now: Long) {
        if (moving) {
            if (idleSince != 0L) idleMillis += now - idleSince
            idleSince = 0L
        } else if (idleSince == 0L) {
            idleSince = now
        }
    }

    /** The resting time counted so far stops (the meter pauses). */
    internal fun pauseRest(now: Long) {
        if (idleSince != 0L) idleMillis += now - idleSince
        idleSince = 0L
    }

    /** The numbers of every screen with frames, most frames first. Main thread. */
    fun lines(): List<JankLine> = synchronized(lock) {
        names.indices.filter { tallies[it].frames > 0L }.map { jankLine(names[it], tallies[it]) }
    }.sortedByDescending { it.frames }

    /** Map frames per second of rest so far; NaN before a second of rest. */
    fun mapIdleFps(): Double = idleFps(idleFrames, restMillis())

    private fun restMillis(): Long {
        val running = if (idleSince != 0L) SystemClock.uptimeMillis() - idleSince else 0L
        return idleMillis + running
    }

    /** Everything counted back to none. */
    fun reset() {
        synchronized(lock) {
            tallies.forEach { it.reset() }
            transitions.clear()
            run = null
        }
        idleFrames = 0L
        idleMillis = 0L
        causes.reset()
        if (idleSince != 0L) idleSince = SystemClock.uptimeMillis()
    }

    /** The map line of the report and the developer options. */
    fun mapText(context: Context): String {
        val fps = mapIdleFps()
        return if (fps.isNaN()) {
            context.getString(R.string.r5perf_jank_map_none)
        } else {
            val total = context.getString(R.string.r5perf_jank_map_fps, String.format(java.util.Locale.ROOT, "%.1f", fps))
            val split = mapCauseText(
                causes,
                restMillis(),
                context.getString(R.string.r6perf_jank_cause_vehicles),
                context.getString(R.string.r6perf_jank_cause_pulse),
                context.getString(R.string.r6perf_jank_cause_other),
            )
            if (split.isEmpty()) total else "$total ($split)"
        }
    }

    /** Puts the numbers on the clipboard as text; nothing else leaves the phone. */
    fun copy(context: Context) {
        val text = jankReport(
            head = "Next Stop ${BuildConfig.VERSION_NAME} · ${context.getString(R.string.r5perf_jank_meter)}",
            lines = lines(),
            framesWord = context.getString(R.string.r5perf_jank_frames),
            jankyWord = context.getString(R.string.r5perf_jank_janky),
            mapLine = mapText(context) + "\n" + context.getString(R.string.r6perf_jank_transitions) + ":\n" +
                transitionsText(context) + "\n" + context.getString(R.string.r6perf_jank_basis),
            lateWord = context.getString(R.string.r6perf_jank_late),
        )
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("Next Stop jank meter", text))
    }

    internal val listener: JankStats.OnFrameListener get() = frameListener
}

/**
 * Tells the jank meter which screen is in front ([name]); while the meter is on and the app is
 * resumed it counts the frames of the window and the map's frames in rest. Costs nothing while
 * the meter is off.
 */
@Composable
fun JankScreenTag(name: String) {
    SideEffect { JankMeter.tag(name) }
    if (!JankMeter.enabled) return
    val view = LocalView.current
    val owner = LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ -> resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(view, resumed) {
        val window = view.context.jankActivity()?.window
        if (!resumed || window == null) return@DisposableEffect onDispose { }
        val stats = JankStats.createAndTrack(window, JankMeter.listener)
        JankMeter.listenToMap(true)
        onDispose {
            stats.isTrackingEnabled = false
            JankMeter.listenToMap(false)
            JankMeter.pauseRest(SystemClock.uptimeMillis())
        }
    }
    LaunchedEffect(resumed) {
        if (!resumed) return@LaunchedEffect
        snapshotFlow { MapCamera.moving }.collect { moving -> JankMeter.cameraMoving(moving, SystemClock.uptimeMillis()) }
    }
}

private fun Context.jankActivity(): Activity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

/**
 * Follows the changes of page of [transition] for the jank meter: a change that came to rest
 * ends its run (see JankMeter.transitionSettled). Read in a flow, never in composition, so the
 * root composes no more for it. Nothing while the meter is off.
 */
@Composable
fun <S> JankTransitionProbe(transition: androidx.compose.animation.core.Transition<S>) {
    if (!JankMeter.enabled) return
    LaunchedEffect(transition) {
        snapshotFlow { transition.currentState == transition.targetState && !transition.isRunning }
            .collect { settled -> if (settled) JankMeter.transitionSettled() }
    }
}

/** Tells the jank meter when the page [name] is drawn (for the first frame of a change of page); nothing while it is off. */
fun Modifier.jankPageDrawn(name: String): Modifier =
    if (!JankMeter.enabled) this else this.drawWithContent {
        JankMeter.pageDrawn(name)
        drawContent()
    }

private val traceCookies = java.util.concurrent.atomic.AtomicInteger(0)

/**
 * A section [name] in a system trace (Perfetto) from the first composition of the composable
 * that calls this until the first time the returned modifier has drawn its content: what the
 * first frame of a page costs, layout and the lazily composed items included. Asynchronous,
 * so an abandoned composition leaves no section open on the thread. Costs nothing but a check
 * while no trace is taken.
 */
@Composable
fun rememberFirstFrameTrace(name: String): Modifier {
    val cookie = remember {
        val next = traceCookies.incrementAndGet()
        android.os.Trace.beginAsyncSection(name, next)
        intArrayOf(next)
    }
    return Modifier.drawWithContent {
        drawContent()
        if (cookie[0] != 0) {
            android.os.Trace.endAsyncSection(name, cookie[0])
            cookie[0] = 0
        }
    }
}

/** [block] as the asynchronous trace section [name] (it may suspend and move between threads). */
internal inline fun <T> traceAsync(name: String, block: () -> T): T {
    val cookie = traceCookies.incrementAndGet()
    android.os.Trace.beginAsyncSection(name, cookie)
    try {
        return block()
    } finally {
        android.os.Trace.endAsyncSection(name, cookie)
    }
}

