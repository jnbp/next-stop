package io.github.jnbp.nextstop.ui

import android.app.Activity
import android.content.Context
import android.os.Build
import android.window.BackEvent
import android.window.OnBackAnimationCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch

/**
 * Where the one back handler of AppRoot gets its gesture from.
 *
 * [Androidx] is PredictiveBackHandler of AndroidX (its NavigationEventDispatcher registers one
 * OnBackAnimationCallback with the window at PRIORITY_DEFAULT). The two others register an
 * android.window.OnBackAnimationCallback of the app's own directly with the window's
 * OnBackInvokedDispatcher (API 34+), at PRIORITY_OVERLAY or PRIORITY_DEFAULT, and hand its
 * events to the same handler as a flow, so the same back animation runs. Chosen in the
 * performance lab ("Back source"); outside it (and below API 34) always [DEFAULT].
 */
internal enum class BackSource(val label: String) {
    Androidx("androidx PredictiveBackHandler"),
    PlatformOverlay("platform callback, PRIORITY_OVERLAY"),
    PlatformDefault("platform callback, PRIORITY_DEFAULT"),
    ;

    companion object {
        /** What the app uses unless the lab says otherwise. */
        val DEFAULT = Androidx
    }
}

internal object BackSourceChoice {
    private const val PREFS = "next-stop-perflab"
    private const val KEY = "back-source"

    /** The source in use now (read by AppBackHandler while composing). */
    var current by mutableStateOf(BackSource.DEFAULT)
        private set

    private var loaded = false

    fun load(context: Context): BackSource {
        if (!loaded) {
            loaded = true
            if (PerfLab.available) {
                val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
                current = BackSource.entries.firstOrNull { it.name == name } ?: BackSource.DEFAULT
            }
        }
        return current
    }

    /** The next source, kept in the preferences of the lab. */
    fun cycle(context: Context) {
        if (!PerfLab.available) return
        load(context)
        current = BackSource.entries[(current.ordinal + 1) % BackSource.entries.size]
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, current.name).apply()
        BackLog.log("back source now ${current.label}")
    }
}

/**
 * The back handler of AppRoot: PredictiveBackHandler of AndroidX, or (see [BackSource]) a
 * platform OnBackAnimationCallback that drives [onBack] the same way: a flow of the gesture's
 * events that completes when back is invoked and is cancelled when the gesture is called off.
 * The platform callback is registered only while [enabled], so back on the start page stays
 * the system's (back to home).
 */
@Composable
internal fun AppBackHandler(enabled: Boolean, onBack: suspend (Flow<BackEventCompat>) -> Unit) {
    val context = LocalContext.current
    remember(context) { BackSourceChoice.load(context) }
    val source = BackSourceChoice.current
    val platform = source != BackSource.Androidx && Build.VERSION.SDK_INT >= 34
    PredictiveBackHandler(enabled = enabled && !platform, onBack = onBack)
    if (Build.VERSION.SDK_INT >= 34) {
        val priority = if (source == BackSource.PlatformDefault) {
            OnBackInvokedDispatcher.PRIORITY_DEFAULT
        } else {
            OnBackInvokedDispatcher.PRIORITY_OVERLAY
        }
        PlatformBackHandler(enabled = enabled && platform, priority = priority, onBack = onBack)
    }
}

@RequiresApi(34)
@Composable
private fun PlatformBackHandler(enabled: Boolean, priority: Int, onBack: suspend (Flow<BackEventCompat>) -> Unit) {
    val activity = LocalContext.current.backActivity() ?: return
    val scope = rememberCoroutineScope()
    val currentOnBack by rememberUpdatedState(onBack)
    val callback = remember(activity, priority) {
        PlatformBackCallback(scope, priority) { events -> currentOnBack(events) }
    }
    DisposableEffect(activity, callback, enabled) {
        if (enabled) callback.register(activity)
        onDispose { callback.unregister(activity) }
    }
}

/**
 * An OnBackAnimationCallback of the app's own on the window, as a flow for the handler: one
 * channel per gesture, fed by onBackProgressed, closed by onBackInvoked, cancelled by
 * onBackCancelled. Every event is counted for the back log.
 */
@RequiresApi(34)
internal class PlatformBackCallback(
    private val scope: CoroutineScope,
    private val priority: Int,
    private val handler: suspend (Flow<BackEventCompat>) -> Unit,
) : OnBackAnimationCallback {
    private var registered = false
    private var channel: Channel<BackEventCompat>? = null
    private var job: Job? = null

    // The gesture now running, for the back log.
    private var progressEvents = 0
    private var lastProgress = 0f
    private var maxProgress = 0f

    private val name: String
        get() = if (priority == OnBackInvokedDispatcher.PRIORITY_OVERLAY) "overlay" else "default"

    fun register(activity: Activity) {
        if (registered) return
        activity.onBackInvokedDispatcher.registerOnBackInvokedCallback(priority, this)
        registered = true
        if (BackLog.on) BackProbe.platformCallback = this
        BackLog.log("platform callback registered ($name)")
    }

    fun unregister(activity: Activity) {
        if (!registered) return
        // A gesture running now gets onBackCancelled from the window first.
        activity.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(this)
        registered = false
        if (BackProbe.platformCallback === this) BackProbe.platformCallback = null
        BackLog.log("platform callback unregistered ($name)")
    }

    override fun onBackStarted(backEvent: BackEvent) {
        progressEvents = 0
        lastProgress = backEvent.progress
        maxProgress = backEvent.progress
        BackTouch.gestureStarted()
        BackLog.log(
            "platform onBackStarted ($name) p=${fmt(backEvent.progress)} x=${backEvent.touchX.toInt()} " +
                "y=${backEvent.touchY.toInt()} edge=${edge(backEvent.swipeEdge)} ${BackTouch.startSummary()}",
        )
        start().trySend(BackEventCompat(backEvent))
    }

    override fun onBackProgressed(backEvent: BackEvent) {
        progressEvents++
        lastProgress = backEvent.progress
        if (backEvent.progress > maxProgress) maxProgress = backEvent.progress
        if (progressEvents == 1) BackLog.log("platform onBackProgressed first p=${fmt(backEvent.progress)}")
        channel?.trySend(BackEventCompat(backEvent))
    }

    override fun onBackInvoked() {
        BackLog.log("platform onBackInvoked ($name) ${summary()}")
        BackTouch.gestureEnded()
        // Back without a gesture before (a key, three-button navigation): a gesture of no length.
        val open = channel ?: start()
        channel = null
        open.close()
    }

    override fun onBackCancelled() {
        BackLog.log("platform onBackCancelled ($name) ${summary()}")
        BackTouch.gestureEnded()
        // The handler is cancelled before its flow ends: a flow that simply ended would be a commit.
        job?.cancel()
        job = null
        channel?.cancel()
        channel = null
    }

    /** A new gesture: the one before (if any is left) is called off, the handler runs anew. */
    private fun start(): Channel<BackEventCompat> {
        job?.cancel()
        channel?.cancel()
        val events = Channel<BackEventCompat>(Channel.BUFFERED)
        channel = events
        job = scope.launch { handler(events.consumeAsFlow()) }
        return events
    }

    private fun summary(): String =
        "after $progressEvents progress events, last p=${fmt(lastProgress)}, max p=${fmt(maxProgress)}, ${BackTouch.endSummary()}"

    private fun fmt(progress: Float): String = ((progress * 100).toInt() / 100f).toString()

    private fun edge(swipeEdge: Int): String = when (swipeEdge) {
        BackEvent.EDGE_LEFT -> "left"
        BackEvent.EDGE_RIGHT -> "right"
        else -> swipeEdge.toString()
    }
}
