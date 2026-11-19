package io.github.jnbp.nextstop.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/**
 * Whether a page of the app is on screen (MainActivity between onStart and onStop). The
 * battery rule: the app works only in the front or during a followed trip, so loops that do
 * not wait for a frame (a poll on a timer) wait here before each round: [awaitVisible] returns
 * at once in the front and suspends, costing nothing, while the app is in the background.
 */
object AppVisibility {
    private val shown = MutableStateFlow(false)

    /** True while a page of the app is on screen. */
    val visible: StateFlow<Boolean> get() = shown

    /** MainActivity started (true) or stopped (false). */
    fun set(value: Boolean) {
        shown.value = value
    }

    /** Returns once the app is on screen. */
    suspend fun awaitVisible() {
        if (shown.value) return
        shown.first { it }
    }
}
