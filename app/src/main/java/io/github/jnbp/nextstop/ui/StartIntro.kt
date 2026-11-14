package io.github.jnbp.nextstop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import org.maplibre.android.maps.MapLibreMap

// The stop signs fade in over this time when they first come.
private const val SIGNS_FADE_MILLIS = 320

/**
 * Lets the stop signs of the map come in at the start instead of appearing at once: unseen
 * until they are there ([hasSigns]) and their step of the start is due (see IntroStep), then
 * a short fade. Only once per run of the app; with animations off they are simply there.
 * Put it before the effect that fills the signs, so they are unseen before they come.
 */
@Composable
fun StopSignsIntro(map: MapLibreMap?, styleTick: Int, hasSigns: Boolean) {
    val animate = rememberAnimationsOn()
    val signsThere by rememberUpdatedState(hasSigns)
    LaunchedEffect(map, styleTick) {
        val style = map?.style?.takeIf { it.isFullyLoaded } ?: return@LaunchedEffect
        if (Startup.signsIn || !animate) {
            Startup.signsIn = true
            return@LaunchedEffect
        }
        try {
            setStopSignsShare(style, 0f)
            snapshotFlow { signsThere }.first { it }
            awaitIntroStep(IntroStep.SIGNS)
            val fade = Animatable(0f)
            fade.animateTo(1f, tween(SIGNS_FADE_MILLIS, easing = FastOutSlowInEasing)) {
                try {
                    setStopSignsShare(style, value)
                } catch (e: IllegalStateException) {
                    // The style went away meanwhile: the effect starts again with the new one.
                }
            }
        } finally {
            // Also when the start is cut short (a new style, the page gone): the signs never stay hidden.
            Startup.signsIn = true
            try {
                setStopSignsShare(style, 1f)
            } catch (e: Exception) {
                // A style that is gone needs nothing.
            }
        }
    }
}
