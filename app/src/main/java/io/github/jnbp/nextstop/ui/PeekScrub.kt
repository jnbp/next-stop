package io.github.jnbp.nextstop.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import io.github.jnbp.nextstop.data.Journey

/**
 * Scrubbing through the peek cards: while a connection is held and its peek card is open, the
 * finger slides on (sideways across the columns of the chart, up and down across the rows of
 * the list) and the card shows the connection under it, with a light tick per change. Lifting
 * the finger closes the card as before (see closePeekOnRelease).
 */

/** A connection that can be scrubbed to: its journey, and its element's coordinates once placed. */
internal class PeekTarget(val journey: Journey, val placed: Array<LayoutCoordinates?>)

/** Makes [journey], laid out at [placed], a target of [peek] while it is composed. */
@Composable
internal fun PeekScrubTarget(peek: PeekState, journey: Journey, placed: Array<LayoutCoordinates?>) {
    DisposableEffect(peek, journey, placed) {
        val target = PeekTarget(journey, placed)
        peek.targets[journey.id] = target
        onDispose {
            if (peek.targets[journey.id] === target) peek.targets.remove(journey.id)
        }
    }
}

/**
 * Lets the finger that opened [peek] (by a long press on one of its targets) scrub on to the
 * others: across them along x, or along y when [vertical]. While it scrubs, its moves are taken
 * here (Initial pass), so the chart or list does not scroll; without an open peek nothing is
 * taken, and scrolling works as before. [onChange] is called on each change (a light tick).
 */
internal fun Modifier.peekScrub(peek: PeekState, vertical: Boolean, onChange: () -> Unit): Modifier =
    onPlaced { peek.area = it }.pointerInput(peek, vertical) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            // A card already open at the touch (a screen reader opened it) is not scrubbed.
            val openAtDown = peek.open
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                var pressed = false
                for (i in event.changes.indices) if (event.changes[i].pressed) pressed = true
                if (!pressed) break
                if (openAtDown || !peek.open) continue
                val change = event.changes.firstOrNull { it.pressed } ?: continue
                for (i in event.changes.indices) event.changes[i].consume()
                val area = peek.area?.takeIf { it.isAttached } ?: continue
                val inWindow = area.localToWindow(change.position)
                val hit = peek.hitAt(if (vertical) inWindow.y else inWindow.x, vertical) ?: continue
                if (hit.journey.id != peek.journey?.id) {
                    peek.show(hit.journey, hit.placed[0]?.takeIf { it.isAttached }?.boundsInWindow())
                    onChange()
                }
            }
        }
    }

/** The target of [this] peek whose element spans [position] in the window (along y when [vertical]), or null. */
private fun PeekState.hitAt(position: Float, vertical: Boolean): PeekTarget? {
    val shown = ArrayList<PeekTarget>(targets.size)
    for (target in targets.values) if (target.placed[0]?.isAttached == true) shown.add(target)
    if (shown.isEmpty()) return null
    val starts = FloatArray(shown.size)
    val ends = FloatArray(shown.size)
    for (i in shown.indices) {
        val bounds = shown[i].placed[0]!!.boundsInWindow()
        starts[i] = if (vertical) bounds.top else bounds.left
        ends[i] = if (vertical) bounds.bottom else bounds.right
    }
    return shown.getOrNull(scrubHit(position, starts, ends))
}

/**
 * Which of the spans [starts]..[ends] (one per element, in any order) holds [position]: its
 * index, or -1 in a gap between them or outside all. Ends are exclusive; where two would
 * overlap, the first in the list wins. Empty bounds (start ≥ end) never hold anything.
 */
fun scrubHit(position: Float, starts: FloatArray, ends: FloatArray): Int {
    val count = minOf(starts.size, ends.size)
    for (i in 0 until count) {
        if (starts[i] < ends[i] && position >= starts[i] && position < ends[i]) return i
    }
    return -1
}
