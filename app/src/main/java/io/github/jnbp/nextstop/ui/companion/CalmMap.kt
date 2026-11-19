package io.github.jnbp.nextstop.ui.companion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import android.os.SystemClock
import io.github.jnbp.nextstop.ui.Startup
import kotlinx.coroutines.flow.first
import io.github.jnbp.nextstop.data.DiagLog
import io.github.jnbp.nextstop.ui.MapAlive
import io.github.jnbp.nextstop.ui.MapCamera
import org.maplibre.android.maps.MapLibreMap

private const val LOG_TAG = "CalmMap"

/**
 * Stands beside the map of the app (MapBackground) in the root of the app. While the calm
 * companion is alone on screen the root composes neither (see mapComposed): the map view is
 * destroyed then, nothing is drawn, loaded or moved. When they go, the handle of the map in
 * [state] goes with them, so nothing writes sources, vehicles or camera moves to a map that is
 * gone; the next map sets it again once it is ready (and fades in, see MapBackground).
 */
@Composable
fun ForgetMapWhenGone(state: MutableState<MapLibreMap?>) {
    DisposableEffect(state) {
        onDispose {
            state.value?.let {
                MapAlive.release(it)
                MapCamera.detach(it)
            }
            state.value = null
            DiagLog.i(LOG_TAG) { "map released" }
        }
    }
}

/**
 * Whether the map is composed: while it is [wanted], and one frame longer once it is not. In
 * that frame the map is hidden (its visible is false then), which brings its view to rest in
 * that same frame (see MapBackground): its render thread is paused before the view is detached.
 * MapLibre resets the native renderer as its surface view is detached and only then stops the
 * render thread; one that still drew then rendered without a renderer (#284).
 */
@Composable
fun rememberMapKept(wanted: Boolean): Boolean {
    val kept = remember { mutableStateOf(wanted) }
    LaunchedEffect(wanted) {
        if (wanted) {
            kept.value = true
            return@LaunchedEffect
        }
        // The frame that hid the map is over: its view rests.
        withFrameNanos { }
        kept.value = false
    }
    return wanted || kept.value
}

/**
 * In place of the map while there is none (the calm companion alone, see mapComposed): a start
 * of the app straight into it has no map to wait for, so the rest of the start (the beat of
 * Startup) begins once the splash screen is gone, as it does with the map switched off.
 */
@Composable
fun NoMapStart() {
    LaunchedEffect(Unit) {
        if (Startup.mapShownAt != 0L) return@LaunchedEffect
        snapshotFlow { Startup.splashDone }.first { it }
        if (Startup.mapShownAt == 0L) Startup.mapShownAt = SystemClock.uptimeMillis()
        Startup.mapIn = true
    }
}
