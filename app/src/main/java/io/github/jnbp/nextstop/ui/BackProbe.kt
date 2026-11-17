package io.github.jnbp.nextstop.ui

import android.app.Activity
import android.content.Context
import android.os.Build
import android.widget.Toast
import android.window.BackEvent
import android.window.OnBackAnimationCallback
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The back probe of the performance lab ("Back probe (current page)"), only in debug and
 * staging builds (see BackLog.on; the back log keeps 1000 lines, a run takes about 250 at 120 Hz): it drives a back gesture on the page in front through the
 * app's own back handler, as the window does (the start, progress from 0 to [REACH] in
 * [RISE_MILLIS], held [HOLD_MILLIS], then called off), and logs every frame under
 * NextStopBack:
 *  - where the content back closes lies: the top (px) of its layout, without the shrinking
 *    of the gesture (see [top]: read outside the layer that shrinks), and how far that moved
 *    against the frame before the gesture;
 *  - what the rows of the bars were drawn with (0 to 1: the fog or plain ground of
 *    BackBarRows, [BackStage.rowShown], else the fog of the map's layer, [BackStage.rowMap],
 *    else nothing over the map) and by which, and how far the page back leads to is there
 *    ([BackStage.toward]); each layer writes what it drew whenever it draws, so a row that
 *    is not drawn again keeps its value, as it does on the screen;
 *  - after calling it off, the rows in the frames around the end of the gesture's state
 *    (the frame its flags are cleared, see endBack in AppRoot): the hand-over to the page's
 *    own look, which must not step;
 *  - which map shows in the row of the status bar: the live map as far as the row leaves it
 *    free, and whether the page in front took a picture of its map along.
 * It ends with a summary: the largest move of that content (0 px when nothing is laid out
 * anew at the start of a gesture) and the steps of the rows in one frame beyond what the
 * gesture moved in it.
 */
internal object BackProbe {
    private const val RISE_MILLIS = 600L
    private const val HOLD_MILLIS = 400L

    // After calling it off: the page grows back and the pictures give way (see AppRoot).
    private const val SETTLE_MILLIS = 1200L
    private const val REACH = 0.5f

    // A step of the rows in one frame larger than this (beyond the step of the page back leads to) is a jump.
    private const val ROW_JUMP = 0.1f

    // Main thread only.
    private val places = HashMap<String, LayoutCoordinates>()

    /** What back closes now: "layer:<label>" or "page:<Screen>" (set by AppRoot as it composes). */
    var front = ""

    /** The stage of AppRoot's back gestures. */
    var stage: BackStage? = null

    /** The platform back callback registered now (see PlatformBackCallback), for the platform sources. */
    var platformCallback: Any? = null

    /** Progress events the back handler got (counted by BackMilestones). */
    var events = 0

    var running by mutableStateOf(false)
        private set

    /** Where the content [key] lies: called from onGloballyPositioned outside its back transformation. */
    fun top(key: String, coordinates: LayoutCoordinates) {
        places[key] = coordinates
    }

    private fun topOf(key: String): Float? {
        val at = places[key] ?: return null
        return if (at.isAttached) at.positionInRoot().y else null
    }

    suspend fun run(context: Context) {
        if (!BackLog.on || running) return
        val driver = context.backActivity()?.let { BackDriver.of(it) }
        if (driver == null) {
            BackLog.log("probe: no back handler to drive (source ${BackSourceChoice.current.label})")
            Toast.makeText(context, "Back probe: no back handler to drive", Toast.LENGTH_SHORT).show()
            return
        }
        running = true
        try {
            withFrameNanos { }
            val key = front
            val before = topOf(key)
            val eventsBefore = events
            BackLog.log("probe start front=$key top=${px(before)} via ${driver.label}")
            var frames = 0
            var maxMove = 0f
            var moveFrame = -1
            var rowJumps = 0
            var maxRowStep = 0f
            var lastRow = Float.NaN
            var lastUnder = Float.NaN
            var lastSource = ""
            var sourceChanges = 0
            var phase = "start"
            // The rows around the end of the gesture's state, after calling it off.
            var flagged = false
            var handover = -1
            val ends = StringBuilder()
            var before2 = ""
            var before1 = ""
            var progress = 0f
            val start = withFrameNanos { it }
            driver.start(0f)
            while (true) {
                val now = withFrameNanos { it }
                val millis = (now - start) / 1_000_000
                when {
                    millis < RISE_MILLIS -> {
                        phase = "rise"
                        progress = REACH * millis / RISE_MILLIS
                        driver.progress(progress)
                    }
                    millis < RISE_MILLIS + HOLD_MILLIS -> {
                        if (phase != "hold") driver.progress(REACH)
                        phase = "hold"
                        progress = REACH
                    }
                    phase != "cancel" && phase != "settle" -> {
                        driver.cancel()
                        phase = "cancel"
                        progress = 0f
                    }
                    millis < RISE_MILLIS + HOLD_MILLIS + SETTLE_MILLIS -> phase = "settle"
                    else -> break
                }
                frames++
                // The rows as the frame before drew them, the page underneath as it is now.
                val s = stage
                val bars = s?.rowShown ?: -1f
                val map = s?.rowMap ?: -1f
                val row = if (bars >= 0f) bars else maxOf(map, 0f)
                val source = if (bars >= 0f) "bar rows" else if (map >= 0f) "map fog" else "map"
                val flags = s != null && (s.fogAfter || s.fogBefore || s.plainAfter || s.plainBefore || s.mapCard || s.mapStays)
                val rowNow = " f$frames=${fmt(row)}:$source"
                if (flagged && !flags && phase == "settle" && handover < 0) {
                    handover = frames
                    ends.append(before2).append(before1)
                }
                flagged = flags
                if (handover > 0 && frames - handover in 0..3) ends.append(rowNow)
                before2 = before1
                before1 = rowNow
                val under = s?.toward() ?: Float.NaN
                val y = topOf(key)
                val move = if (y != null && before != null) y - before else Float.NaN
                if (!move.isNaN() && abs(move) > maxMove) {
                    maxMove = abs(move)
                    moveFrame = frames
                }
                if (!lastRow.isNaN()) {
                    val step = abs(row - lastRow)
                    val expected = if (lastUnder.isNaN() || under.isNaN()) 0f else abs(under - lastUnder)
                    val beyond = step - expected
                    if (beyond > maxRowStep) maxRowStep = beyond
                    if (beyond > ROW_JUMP) rowJumps++
                }
                if (source != lastSource && frames > 1) sourceChanges++
                val live = when {
                    s == null || !(s.mapCard || s.mapStays) -> "-"
                    else -> fmt(1f - row)
                }
                BackLog.log(
                    "probe f=$frames $phase t=$millis p=${fmt(progress)} top=${px(y)} d=${px(move)} " +
                        "row=${fmt(row)} by=$source flags=$flags under=${fmt(under)} " +
                        "fogAfter=${s?.fogAfter} fogBefore=${s?.fogBefore} plainAfter=${s?.plainAfter} " +
                        "liveMapInRow=$live mapCard=${s?.mapCard} mapPicture=${s?.mapShot} marksPicture=${s?.marksShot != null}",
                )
                lastRow = row
                lastUnder = under
                lastSource = source
            }
            val seen = events - eventsBefore
            val summary = "probe summary front=$key frames=$frames events=$seen max|dTop|=${px(maxMove)}px" +
                (if (moveFrame > 0) " (frame $moveFrame)" else "") +
                " rowJumps=$rowJumps maxRowStepBeyondGesture=${fmt(maxRowStep)} rowSourceChanges=$sourceChanges" +
                " rowsAtCancelEnd=" + (if (handover > 0) "[$ends ] (flags cleared in f$handover)" else "flags not cleared within the probe") +
                (if (seen == 0) " WARNING: the handler got no progress events" else "")
            BackLog.log(summary)
            Toast.makeText(context, "Back probe: max |dTop| ${px(maxMove)} px, row jumps $rowJumps", Toast.LENGTH_LONG).show()
        } finally {
            running = false
        }
    }

    private fun px(value: Float?): String = if (value == null || value.isNaN()) "-" else (value * 10f).roundToInt().div(10f).toString()

    private fun fmt(value: Float): String = if (value.isNaN()) "-" else ((value * 1000f).roundToInt() / 1000f).toString()
}

/** How the probe hands its gesture to the back handler of AppRoot, by the back source in use (see BackSource). */
private class BackDriver(
    val label: String,
    val start: (Float) -> Unit,
    val progress: (Float) -> Unit,
    val cancel: () -> Unit,
) {
    companion object {
        // Where the probe's finger is: at the left edge, in the middle of a phone.
        private const val TOUCH_X = 4f
        private const val TOUCH_Y = 1200f

        fun of(activity: Activity): BackDriver? {
            if (Build.VERSION.SDK_INT >= 34 && BackSourceChoice.current != BackSource.Androidx) {
                val callback = BackProbe.platformCallback as? OnBackAnimationCallback ?: return null
                return platform(callback)
            }
            val dispatcher = (activity as? ComponentActivity)?.onBackPressedDispatcher ?: return null
            return BackDriver(
                label = "androidx dispatcher",
                start = { shown -> dispatcher.dispatchOnBackStarted(compat(shown)) },
                progress = { shown -> dispatcher.dispatchOnBackProgressed(compat(shown)) },
                cancel = { dispatcher.dispatchOnBackCancelled() },
            )
        }

        private fun compat(progress: Float) = BackEventCompat(TOUCH_X, TOUCH_Y, progress, BackEventCompat.EDGE_LEFT)

        @RequiresApi(34)
        private fun platform(callback: OnBackAnimationCallback): BackDriver = BackDriver(
            label = "platform callback",
            start = { shown -> callback.onBackStarted(BackEvent(TOUCH_X, TOUCH_Y, shown, BackEvent.EDGE_LEFT)) },
            progress = { shown -> callback.onBackProgressed(BackEvent(TOUCH_X, TOUCH_Y, shown, BackEvent.EDGE_LEFT)) },
            cancel = { callback.onBackCancelled() },
        )
    }
}
