package io.github.jnbp.nextstop.ui

import android.view.MotionEvent
import android.view.ViewGroup
import android.view.Window
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventTransitionState

/**
 * The touches of the window around a predictive back gesture, for the back log.
 *
 * Since Android 15 the system does not send the progress of an in-app back gesture to an
 * activity window: the window works it out itself (WindowOnBackInvokedDispatcher.onMotionEvent,
 * fed by the DecorView from the touches of the gesture). So progress needs the touches of the
 * gesture to reach the window after the gesture started. This counts them: the moves of the
 * touch before the gesture started, and the moves, the cancel and the up after it.
 *
 * [beforeDispatch] runs for every touch the activity gets, before its views do (see
 * MainActivity.dispatchTouchEvent). It also clears a request of a child not to be intercepted
 * on the root, which was meant to let the root read the touches; the framework already lets
 * the root intercept during a back gesture whatever its children asked (ViewGroup), so this
 * changes nothing there and is kept only as it is harmless.
 */
internal object BackTouch {
    // The touch now going on: moves since its down, and those after the gesture started.
    private var moves = 0
    private var movesAtStart = -1
    private var cancelled = false
    private var lifted = false

    // A gesture of the platform callback (see PlatformBackCallback) runs now.
    private var platformGesture = false

    fun beforeDispatch(window: Window, event: MotionEvent, dispatcher: NavigationEventDispatcher) {
        val inGesture = platformGesture ||
            dispatcher.transitionState.value is NavigationEventTransitionState.InProgress
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // A new touch: the root clears every such request itself.
                moves = 0
                movesAtStart = if (inGesture) 0 else -1
                cancelled = false
                lifted = false
                return
            }
            MotionEvent.ACTION_MOVE -> {
                moves++
                if (inGesture && movesAtStart < 0) movesAtStart = moves - 1
            }
            MotionEvent.ACTION_CANCEL -> if (inGesture) cancelled = true
            MotionEvent.ACTION_UP -> if (inGesture) lifted = true
        }
        val root = window.peekDecorView() as? ViewGroup ?: return
        root.requestDisallowInterceptTouchEvent(false)
    }

    /** The platform callback got onBackStarted. */
    fun gestureStarted() {
        platformGesture = true
        movesAtStart = moves
        cancelled = false
        lifted = false
    }

    /** The platform callback's gesture ended. */
    fun gestureEnded() {
        platformGesture = false
    }

    /** The touch as the gesture starts. */
    fun startSummary(): String = "touch moves before start=$moves"

    /** What reached the window during the gesture now ending. */
    fun endSummary(): String {
        val after = if (movesAtStart < 0) 0 else moves - movesAtStart
        return "touch moves in gesture=$after (before ${if (movesAtStart < 0) moves else movesAtStart}), " +
            "cancel=$cancelled up=$lifted"
    }

    /** For the androidx path (see BackMilestones). */
    fun summary(): String = endSummary()
}
