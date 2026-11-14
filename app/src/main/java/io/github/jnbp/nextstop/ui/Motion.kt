package io.github.jnbp.nextstop.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import io.github.jnbp.nextstop.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * The motion of the app in one place: the springs and short fades every part uses, so
 * sheets, cards, chips and markers move alike.
 */
object Motion {
    /** Sheets, cards and things that settle into place: a little give, no wobble. */
    fun <T> settle(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)

    /** Things that pop in, as a pin or a card: a small bounce. */
    fun <T> pop(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)

    /** Quick answers to a touch, as a pressed tile or a picked chip. */
    fun <T> quick(): FiniteAnimationSpec<T> = spring(stiffness = Spring.StiffnessMedium)

    /** A short fade in, and a shorter one out. */
    fun <T> fadeIn(): FiniteAnimationSpec<T> = tween(180)
    fun <T> fadeOut(): FiniteAnimationSpec<T> = tween(120)

    /** The pause between parts that come in one after the other at the start. */
    const val STAGGER_MILLIS = 70L
}

/**
 * What the start of the app has done so far: [splashDone] once the splash screen has gone
 * (the map comes in only after it, so its entrance is seen), [mapReady] once the map has its
 * style, [introPlayed] once the start page came in, so it does so only once.
 */
object Startup {
    @Volatile
    var mapReady: Boolean = false

    /** True once the splash screen is gone, also its end in Compose (see MainActivity). Observable. */
    var splashDone: Boolean by mutableStateOf(false)

    /** True once the map starts to come in: what is drawn over it (the own position, saved places) follows it. */
    var mapIn: Boolean by mutableStateOf(false)

    var introPlayed: Boolean = false

    /** When the map began to come in (uptime), 0 before: the beat the rest of the start follows. */
    @Volatile
    var mapShownAt: Long = 0L

    /** True once the stop signs of the map came in, so they fade in only at the start. */
    var signsIn: Boolean = false

    /** True once the first vehicles were let in (see VehicleLayer). */
    var vehiclesIn: Boolean = false

    /** True once the signs of disruptions came in at the start (see IntroStep.NOTICES). */
    var noticesIn: Boolean = false

    /** True once the bar of the tabs came in at the start (see IntroStep.NAV). */
    var navIn: Boolean = false
}

/**
 * The order in which the start comes in once the map has begun to fade in (step 0), one step
 * of [INTRO_STEP_MILLIS] apart: the stop signs, the vehicles, the signs of disruptions, the
 * sheet with its cards (one after the other), the bar of the tabs, and last the live hint.
 * A part whose data comes later comes in as soon as it is there.
 */
object IntroStep {
    const val SIGNS = 2
    const val VEHICLES = 3
    const val NOTICES = 4
    const val CARDS = 5
    const val NAV = 8
    const val LIVE = 10
}

/** The time between two steps of the start (see IntroStep). */
const val INTRO_STEP_MILLIS = 110L

private const val INTRO_MAP_POLL_MILLIS = 30L

/**
 * Waits until the [step] of the start is due (see IntroStep): that many steps after the map
 * began to come in. Returns at once when it is due already, as for data that came late.
 * Nothing comes before the map: a map that does not load still comes in after its own
 * latest time (see MapBackground), and a page without the map gives the beat at once.
 */
suspend fun awaitIntroStep(step: Int) {
    while (Startup.mapShownAt == 0L) delay(INTRO_MAP_POLL_MILLIS)
    val left = Startup.mapShownAt + step * INTRO_STEP_MILLIS - SystemClock.uptimeMillis()
    if (left > 0L) delay(left)
}

/**
 * A soft light that runs over a placeholder while its content loads: a band of [highlight]
 * moving across [ground] once a second. Drawn only, no recomposition per frame.
 */
fun Modifier.shimmer(
    ground: Color = AppColors.Chip,
    highlight: Color = AppColors.Card,
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shift = transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1_100, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerShift",
    )
    drawWithContent {
        val width = size.width
        val x = shift.value * width
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(ground, highlight, ground),
                start = Offset(x - width * 0.6f, 0f),
                end = Offset(x, size.height),
            ),
        )
        drawContent()
    }
}

/** A rounded grey bar that shimmers, standing in for a line of text that is still loading. */
@Composable
fun SkeletonLine(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .shimmer(),
    )
}

/**
 * How far the things drawn over the map in Compose (the own position, saved places) have
 * come in: 0 until the map starts to come in, then up to 1 with it, so nothing stands on the
 * loading ground before the map. At once with animations off.
 */
@androidx.compose.runtime.Composable
fun rememberMapInAlpha(): androidx.compose.runtime.State<Float> {
    val animate = rememberAnimationsOn()
    return androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (Startup.mapIn) 1f else 0f,
        animationSpec = if (animate) androidx.compose.animation.core.tween<Float>(420, delayMillis = 120) else androidx.compose.animation.core.snap<Float>(),
        label = "map-in",
    )
}

/**
 * Lets an element take its new size softly when what it holds changes (a row more or less, a
 * line that comes or goes), with the spring of [Motion.settle]; at once with animations off.
 */
fun Modifier.softSize(): Modifier = composed {
    if (rememberAnimationsOn()) Modifier.animateContentSize(Motion.settle()) else Modifier
}

/**
 * Lets a row of a plain column (one that is not a lazy list, where `animateItem` does this)
 * glide to its new place when the list is put in another order, as when live times move a
 * connection past another: it is drawn where it was seen and settles at its new place with
 * the spring of [Motion.settle]. Rows that only move because the list grows or shrinks glide
 * the same way. The row must be inside `key(...)` of its item, so the place it was seen at
 * follows the item. With [animate] false (animations off) it simply is at its place.
 */
fun Modifier.glideToPlace(animate: Boolean): Modifier = composed {
    val shift = remember { Animatable(0f) }
    // Where the row was laid out last, in its parent; NaN before the first layout.
    val placedAt = remember { floatArrayOf(Float.NaN) }
    val scope = rememberCoroutineScope()
    Modifier
        .onPlaced { coordinates ->
            val y = coordinates.positionInParent().y
            val before = placedAt[0]
            placedAt[0] = y
            if (animate && !before.isNaN() && before != y) {
                // From where it is seen now (a glide on its way included) to the new place.
                val from = before + shift.value - y
                // Undispatched, so the jump back is in place before the frame is drawn.
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    shift.snapTo(from)
                    shift.animateTo(0f, Motion.settle())
                }
            }
        }
        .graphicsLayer { translationY = shift.value }
}

/**
 * Shows [target] with [content]; when it changes (a status text, a time, loading to content),
 * the new one cross-fades in while the old one fades out and the size follows softly.
 * [contentKey] says which changes count (all, by default). At once with animations off.
 */
@Composable
fun <T> FadeSwap(
    target: T,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    contentKey: (T) -> Any? = { it },
    content: @Composable (T) -> Unit,
) {
    if (!rememberAnimationsOn()) {
        Box(modifier = modifier, contentAlignment = contentAlignment) { content(target) }
        return
    }
    AnimatedContent(
        targetState = target,
        modifier = modifier,
        contentAlignment = contentAlignment,
        contentKey = contentKey,
        transitionSpec = {
            fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(140)) using SizeTransform(clip = false)
        },
        label = "fadeSwap",
    ) { shown -> content(shown) }
}

/**
 * Shows [content] while [visible], coming in with a fade as it unfolds and going the same
 * way; simply there or gone with animations off. For lines and rows that come and go.
 */
@Composable
fun SoftAppear(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (!rememberAnimationsOn()) {
        if (visible) Box(modifier = modifier) { content() }
        return
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(220)) + expandVertically(Motion.settle()),
        exit = fadeOut(tween(140)) + shrinkVertically(Motion.settle()),
    ) { content() }
}
