package io.github.jnbp.nextstop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import io.github.jnbp.nextstop.R

// The splash screen draws its icon this big (Android 12+, with an icon background).
private val SPLASH_ICON = 240.dp

/**
 * The end of the splash screen in Compose: the blue with the logo, just as the system splash
 * left it, over the app. The logo grows a little and fades while the blue fades, so the app
 * comes in from behind it; then [onDone]. Only shown when animations are on (see MainActivity).
 */
@Composable
fun SplashContinuation(onDone: () -> Unit) {
    val ground = remember { Animatable(1f) }
    val logo = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        listOf(
            async { logo.animateTo(1f, tween(SPLASH_FADE_MILLIS, easing = FastOutSlowInEasing)) },
            async { ground.animateTo(0f, tween(SPLASH_FADE_MILLIS, delayMillis = 60, easing = FastOutSlowInEasing)) },
        ).awaitAll()
        onDone()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = ground.value }
            .background(colorResource(R.color.trip_blue)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .size(SPLASH_ICON)
                .graphicsLayer {
                    val grow = 1f + 0.12f * logo.value
                    scaleX = grow
                    scaleY = grow
                    alpha = 1f - logo.value
                },
        )
    }
}

// How long the blue and the logo take to fade into the app.
private const val SPLASH_FADE_MILLIS = 340
