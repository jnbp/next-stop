package io.github.jnbp.nextstop.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import io.github.jnbp.nextstop.data.DiagLog
import io.github.jnbp.nextstop.ui.theme.AppColors
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import org.maplibre.android.maps.MapLibreMap

/**
 * A back inside a page (the page of one journey over the connections, "Advanced" over the
 * settings): not a back handler of its own but a layer of the one handler of AppRoot, which
 * decides at the start of every gesture where back goes (see [BackLayers.top]). [preview]
 * follows the gesture for the page to draw; [onSeek], [onCancel] and [onBack] are the page's.
 */
@Stable
internal class BackLayer(val label: String) {
    val preview = BackPreview()

    /** Back would close this layer now (its page says so, see PredictiveNestedBackHandler). */
    var enabled by mutableStateOf(false)

    /** The page of this layer is the page in front, not one underneath or on its way out. */
    var inFront by mutableStateOf(true)

    var onSeek: suspend (Float) -> Unit = {}
    var onCancel: () -> Unit = {}
    var onBack: () -> Unit = {}
}

/**
 * The layers of back inside the pages of AppRoot, in the order they were added. There is no
 * count kept anywhere: what is open is read from the layers themselves when it is needed, so
 * nothing can be left over from a page that went or a gesture that ended.
 */
@Stable
internal class BackLayers {
    private val layers = mutableStateListOf<BackLayer>()

    fun add(layer: BackLayer) {
        layers.add(layer)
        BackLog.log("layer added ${layer.label} (${layers.size})")
    }

    fun remove(layer: BackLayer) {
        layers.remove(layer)
        BackLog.log("layer removed ${layer.label} (${layers.size})")
    }

    /** The layer back closes now: the last one added that is open on the page in front. */
    fun top(): BackLayer? = layers.lastOrNull { it.enabled && it.inFront }
}

/**
 * What the page a back gesture of AppRoot leaves takes along besides itself, and how it goes
 * (see the back handler of AppRoot). The map lies under all pages and keeps showing the page
 * in front until back is let go: the page takes a picture of the map and of the ways and
 * marks drawn over it ([marks], recorded as they are drawn), so it shrinks whole and what
 * lies under it does not change while the finger is on the gesture.
 */
@Stable
internal class BackStage {
    /** The ways, markers and pills over the map, recorded each time they are drawn. */
    var marks: GraphicsLayer? = null

    /** The picture of [marks] at the start of the gesture, drawn over the map's picture. */
    var marksShot by mutableStateOf<ImageBitmap?>(null)

    /** Opacity of the page a gesture leaves once let go: 1 while the finger is on it. */
    val fade = Animatable(1f)

    /** Opacity of the pictures the page took along: they fade into the live map when called off. */
    val shots = Animatable(1f)

    /**
     * The gesture leaves a page on the map for a page on the map: the map stays full-size and
     * in place, also behind the status bar, and only the page's UI shrinks, as one group.
     */
    var mapStays by mutableStateOf(false)

    /**
     * The gesture leaves a page on the map for a page on its own ground: the page shrinks whole
     * as a card, its map picture ([BackStage] marks and the picture of the map) inside it.
     */
    var mapCard by mutableStateOf(false)

    /** The page the gesture leads to has the map off: the row of the status bar takes the plain ground with it. */
    var plainAfter by mutableStateOf(false)

    /** The page the gesture leaves has the map off and the page it leads to has it: the plain ground goes once let go. */
    var plainBefore by mutableStateOf(false)

    /** The blurred picture of the map, for a card whose picture of the map did not come. */
    var backdrop: Backdrop? = null

    /** The page the gesture leaves lies on fog, the page it leads to does not: the fog in the rows of the bars goes with the gesture. */
    var fogBefore by mutableStateOf(false)

    /** The page the gesture leads to lies on fog, the page it leaves does not: the fog in the rows of the bars comes with the gesture. */
    var fogAfter by mutableStateOf(false)

    /** How far the gesture went (0 to 1), the one of the back handler of AppRoot. */
    var progress: Animatable<Float, AnimationVector1D>? = null

    /** The change of the pages of AppRoot, for how far a change outside a gesture went. */
    var seek: SeekableTransitionState<Screen>? = null

    /** Where the pages lie on the screen (px): their top and bottom; above and below them lie the bars. */
    val pagesBox = FloatArray(2)

    /** The paint the fog in the rows of the bars is drawn with, at the opacity of [barFog]. */
    val fogPaint = Paint()

    /** The paint of the fog of [BackBarRows]. */
    val rowPaint = Paint()

    /** What the rows of the bars were last drawn with (the fog or the plain ground, 0 to 1), -1 for nothing: for the back probe. */
    var rowShown = -1f

    /**
     * The same for the fog of the map's layer in the rows (1 where the page lies on fog alone,
     * the fog between the grounds at [barFog], -1 for none), for the back probe.
     */
    var rowMap = -1f

    /** The page the gesture leaves took a picture of the map along (see [mapCard]), for the back probe. */
    var mapShot = false

    /**
     * How much of the fog lies in the rows of the status bar and the gesture bar while the page
     * changes between a page on fog and one that is not (the pages bring their own fog only in
     * their own room, between the bars). Under a back gesture it follows the gesture and, once
     * let go, the fade of the page in front ([fade], let go) or its growing back ([progress],
     * called off), so the rows never change in one frame and end where the page shown ends.
     * Any other change follows the transition of the pages; [afterFog] is whether the page it
     * goes to lies on fog.
     */
    fun barFog(afterFog: Boolean): Float {
        if (fogBefore || fogAfter) return if (fogAfter) toward() else 1f - toward()
        val fraction = (seek?.fraction ?: 1f).coerceIn(0f, 1f)
        return if (afterFog) fraction else 1f - fraction
    }

    /**
     * The same for the plain ground (the map switched off) between a page on it and one on
     * the map: under a back gesture onto the plain ground ([plainAfter]) as the gesture goes,
     * else as the transition of the pages goes; [afterPlain] is whether the page it goes to
     * lies on the plain ground.
     */
    fun barPlain(afterPlain: Boolean): Float {
        if (plainAfter) return toward()
        val fraction = (seek?.fraction ?: 1f).coerceIn(0f, 1f)
        return if (afterPlain) fraction else 1f - fraction
    }

    /**
     * How far a back gesture went towards the page it leads to, as that page shows it: its
     * opacity, the fade in of the change of the pages ([BackUnderEasing] on how far the change
     * went, the progress while the finger is on it, see the transition of the pages in AppRoot).
     * Let go, it goes on with the change and with the fade of the page in front; called off, it
     * goes back to 0 with the progress. So the rows of the bars always show the page back leads
     * to just as it shows around the page that shrinks (the raw progress lagged behind it: at a
     * quarter of the gesture the page underneath was there whole, its fog in the rows a quarter).
     */
    fun toward(): Float {
        val held = progress?.value ?: 0f
        val change = seek
        val went = if (change != null && change.currentState != change.targetState) change.fraction else held
        val under = BackUnderEasing.transform(went.coerceIn(0f, 1f))
        return (under + (1f - under) * (1f - fade.value)).coerceIn(0f, 1f)
    }
}

/**
 * The outline of a page under a back gesture: rounded by [radius] (px) and reaching [above]
 * px over its top, into the row of the status bar, so what the page draws there shrinks with it.
 */
internal class BackPageShape(private val radius: Float, private val above: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(0f, -above, size.width, size.height, CornerRadius(radius)))
}

/** The layers of AppRoot; null outside it (the setup), where a nested back has a handler of its own. */
internal val LocalBackLayers = staticCompositionLocalOf<BackLayers?> { null }

/** False for a page of AppRoot that is not the page in front (underneath a back gesture, or leaving). */
internal val LocalPageInFront = compositionLocalOf { true }

/**
 * The log of back gestures, in debug and staging builds (see PerfLab.labBuild) and while the
 * verbose recording of the developer options runs (see DiagLog.verbose): every line goes to the
 * diagnostic log (and logcat) with the tag [TAG] and the last [KEEP] lines are kept for "Copy
 * back log" in the performance lab.
 */
internal object BackLog {
    const val TAG = "NextStopBack"
    private const val KEEP = 1000
    private val lines = ArrayDeque<String>()

    // The pages of AppRoot composed right now (in front and underneath), main thread only.
    private val composed = LinkedHashMap<Screen, Int>()

    // What the handler of AppRoot looked like last, to log only its changes.
    private var lastState = ""

    val on: Boolean get() = PerfLab.labBuild || DiagLog.verbose

    val size: Int get() = synchronized(lines) { lines.size }

    fun log(message: String) {
        if (!on) return
        DiagLog.d(TAG) { message }
        val millis = SystemClock.uptimeMillis()
        synchronized(lines) {
            lines.addLast("${millis / 1000 % 100000}.${(millis % 1000).toString().padStart(3, '0')} $message")
            while (lines.size > KEEP) lines.removeFirst()
        }
    }

    fun text(): String = synchronized(lines) { lines.joinToString("\n") }

    fun clear() = synchronized(lines) { lines.clear() }

    fun pageIn(page: Screen) {
        if (!on) return
        composed[page] = (composed[page] ?: 0) + 1
        log("page composed $page -> ${pages()}")
    }

    fun pageOut(page: Screen) {
        if (!on) return
        val left = (composed[page] ?: 1) - 1
        if (left <= 0) composed.remove(page) else composed[page] = left
        log("page gone $page -> ${pages()}")
    }

    /** The pages composed right now. */
    fun pages(): String = composed.keys.joinToString(",", "[", "]")

    /** The state of the handler of AppRoot, logged when it changes. */
    fun state(enabled: Boolean, step: BackStep, layer: String?, screen: Screen) {
        if (!on) return
        val now = "handler enabled=$enabled step=$step layer=${layer ?: "-"} screen=$screen"
        if (now != lastState) {
            lastState = now
            log(now)
        }
    }

    fun copy(context: Context) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("Next Stop back log", text()))
        Toast.makeText(context, "Back log copied ($size lines)", Toast.LENGTH_SHORT).show()
    }
}

/** Logs a gesture's progress at each quarter, with the pages composed then. */
internal class BackMilestones {
    private var next = 0.25f

    // A new gesture: the windows of the app as it starts (see BackWindows).
    init {
        BackWindows.logStart()
    }

    // The progress events of the gesture so far and the last progress, for the log.
    private var events = 0
    private var last = 0f

    fun at(progress: Float) {
        if (!BackLog.on) return
        BackProbe.events++
        events++
        last = progress
        if (events == 1) BackLog.log("progress first event p=${fmt(progress)} via ${BackSourceChoice.current.label}")
        while (progress >= next && next <= 1f) {
            BackLog.log("progress ${(next * 100).toInt()}% ($events events) pages=${BackLog.pages()}")
            next += 0.25f
        }
    }

    /** The gesture ended (let go, called off or back without one): what came of its progress. */
    fun done() {
        if (!BackLog.on) return
        BackLog.log("progress $events events, last ${fmt(last)} via ${BackSourceChoice.current.label}, ${BackTouch.summary()}")
    }

    private fun fmt(progress: Float): String = ((progress * 100).toInt() / 100f).toString()
}

/** A picture of the map as it is drawn now, or null if it does not come (see snapshot). */
internal suspend fun MapLibreMap.awaitBackShot(): Bitmap? = suspendCancellableCoroutine { continuation ->
    snapshot { picture ->
        if (continuation.isActive) continuation.resume(picture)
    }
}

/** The activity behind [this] context, if there is one. */
internal fun Context.backActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/**
 * The rows of the status bar and the gesture bar under a back gesture from a page on the map
 * to a page on fog ([BackStage.fogAfter]) or on the plain ground ([BackStage.plainAfter]). The
 * page back leads to brings its ground only into its own room, between the bars; here the
 * rows get the same ground, at the same opacity as that page ([BackStage.toward]) and at the
 * same place in the order of drawing: over the map and its ways, under the pages, so the
 * page that shrinks (a card with the picture of its map) lies over them as it lies over the
 * page underneath. The rows show around the card exactly what the page underneath shows.
 * (Drawn in the map's layer under the ways, at the raw progress, the live map showed through
 * them at full size above the shrunk card.)
 */
@Composable
internal fun BackBarRows(stage: BackStage) {
    val fog = glassGround(tint = AppColors.FogTint, fallback = AppColors.Fog, highlight = Color.Transparent)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithContent {
                val plain = stage.plainAfter
                stage.rowShown = -1f
                if (!plain && !stage.fogAfter) return@drawWithContent
                val shown = stage.toward()
                stage.rowShown = shown
                if (shown <= 0f) return@drawWithContent
                val top = stage.pagesBox[0]
                val bottom = stage.pagesBox[1]
                if (plain) {
                    if (top > 0f) drawRect(AppColors.PlainGround, size = Size(size.width, top), alpha = shown)
                    if (bottom > 0f && bottom < size.height) {
                        drawRect(
                            AppColors.PlainGround,
                            topLeft = Offset(0f, bottom),
                            size = Size(size.width, size.height - bottom),
                            alpha = shown,
                        )
                    }
                    return@drawWithContent
                }
                // The float bounds of the platform canvas: no Rect made each frame.
                val paint = stage.rowPaint
                paint.alpha = shown
                if (top > 0f) {
                    clipRect(bottom = top) {
                        drawContext.canvas.nativeCanvas.saveLayer(0f, 0f, size.width, top, paint.asFrameworkPaint())
                        this@drawWithContent.drawContent()
                        drawContext.canvas.nativeCanvas.restore()
                    }
                }
                if (bottom > 0f && bottom < size.height) {
                    clipRect(top = bottom) {
                        drawContext.canvas.nativeCanvas.saveLayer(0f, bottom, size.width, size.height, paint.asFrameworkPaint())
                        this@drawWithContent.drawContent()
                        drawContext.canvas.nativeCanvas.restore()
                    }
                }
            }
            .then(fog),
    )
}
