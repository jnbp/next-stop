package io.github.jnbp.nextstop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** How far below its place a page starts that enters after its first frame, as a share of its height. */
internal const val PAGE_RISE_SHARE = 1f / 18f

// The fade of a page entering after its first frame, as the fade of the other pages.
private const val PAGE_ENTRANCE_FADE_MILLIS = 220

/**
 * Whether the change from [from] to [to] lets the new page come in by itself once it has drawn
 * its first frame (see [PageEntrance]) instead of with the transition of the pages: the overview
 * of connections, whose first frame (the chart with its columns) is long enough that a fade
 * started with it was over before anything of it was seen (#110). Back gestures, changes of tab,
 * the search and the course move in their own ways and are left as they are.
 */
internal fun entersAfterFirstFrame(from: Screen, to: Screen): Boolean =
    from != to && to == Screen.Connections && from != Screen.Search && from != Screen.Course &&
        tabSlideOf(from, to) == 0

/**
 * The entrance of a page that waits for its first frame: [alpha] goes from [start] to 1 and
 * [rise] (a share of [PAGE_RISE_SHARE]) from 1 - [start] to 0, both only once the page has
 * drawn. Read them while drawing only, so the entrance composes nothing anew.
 */
internal class PageEntrance(start: Float) {
    val alpha = Animatable(start)
    val rise = Animatable(1f - start)

    /** Runs the entrance from the frame after the first one the page drew; nothing when it is in already. */
    suspend fun run() {
        if (alpha.value >= 1f && rise.value <= 0f) return
        // The frame the page composes in is drawn first; the entrance starts with the one after it.
        withFrameNanos { }
        coroutineScope {
            launch { alpha.animateTo(1f, tween(PAGE_ENTRANCE_FADE_MILLIS)) }
            launch {
                rise.animateTo(
                    0f,
                    spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = 0.001f),
                )
            }
        }
    }
}
