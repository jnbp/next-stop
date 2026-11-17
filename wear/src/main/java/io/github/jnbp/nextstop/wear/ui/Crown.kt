package io.github.jnbp.nextstop.wear.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable

/**
 * The crown (rotary input) goes to whatever follows this modifier: the list of a page (a
 * ScalingLazyColumn handles the crown itself, but only while it holds the focus) or a column
 * with [crownScroll]. The focus is asked for again each time the page comes back to the front
 * (its navigation entry or the app resumed: back from a page above it, from voice input, from
 * ambient mode) and, for a page of a pager, when it becomes the [active] one, so the crown never
 * goes dead after a detour.
 */
@Composable
fun Modifier.crownFocus(active: Boolean = true): Modifier {
    val focus = remember { FocusRequester() }
    CrownFocusEffect(focus, active)
    return this.focusRequester(focus)
}

/**
 * A vertically scrolled column ([scroll]) that the crown scrolls too, with the focus as in
 * [crownFocus]. Put it before verticalScroll.
 */
@Composable
fun Modifier.crownScroll(scroll: ScrollState, active: Boolean = true): Modifier {
    val focus = remember { FocusRequester() }
    CrownFocusEffect(focus, active)
    return this.rotaryScrollable(behavior = RotaryScrollableDefaults.behavior(scroll), focusRequester = focus)
}

@Composable
private fun CrownFocusEffect(focus: FocusRequester, active: Boolean) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(focus, active, lifecycle) {
        if (!active) return@LaunchedEffect
        lifecycle.currentStateFlow.collect { state ->
            if (state == Lifecycle.State.RESUMED) {
                // One frame later, so the focus target is attached.
                withFrameNanos { }
                try {
                    focus.requestFocus()
                } catch (e: IllegalStateException) {
                    // Not attached (the page is leaving): touch still scrolls.
                }
            }
        }
    }
}
