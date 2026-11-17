package io.github.jnbp.nextstop.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.ExperimentalTransitionApi
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

// Predictive back (see PredictiveBackHandler in AppRoot and [PredictiveNestedBackHandler]):
// how long the change takes as the gesture moves it, how small the page in front gets, how
// far it moves from the edge, how far it sinks (share of its height) and how round its
// corners get. The same for every back of the app, between pages and inside one.
internal const val BACK_PAGE_MILLIS = 300
internal const val BACK_MIN_SCALE = 0.9f
internal const val BACK_SHIFT_DP = 8f
internal const val BACK_SINK = 0.04f
internal const val BACK_CORNER_DP = 28f

/** The page under a back gesture is there after the first quarter of it. */
internal val BackUnderEasing = Easing { fraction -> (fraction * 4f).coerceAtMost(1f) }

/** The page a back gesture leaves stays until the last fifth: it fades once the gesture was let go. */
internal val BackAwayEasing = Easing { fraction -> ((fraction - 0.8f) / 0.2f).coerceIn(0f, 1f) }

/**
 * The page a back gesture leaves keeps its full opacity all the way: the transition never
 * fades it (it goes at the very end); a page that fades once let go does so by itself (see
 * [backFront]).
 */
internal val BackHoldEasing = Easing { fraction -> if (fraction >= 1f) 1f else 0f }

/**
 * The place in the order of drawing of the content that back gesture number [gestures] of
 * one transition leads to: below every content of that transition, also below one that came
 * by an earlier back gesture (equal places put the content back leads to on top, over the
 * page that shrinks).
 */
internal fun backUnderZ(gestures: Int): Float = -1f - gestures

/**
 * The change of contents while a back gesture runs: the content back leads to is there
 * underneath almost at once and always below the one in front; the one in front only fades
 * at the very end, once the gesture is let go, or with [hold] not at all (it fades by
 * itself, see [backFront]). The content in front shrinks with [backShrink] and is never
 * clipped by the container.
 */
internal fun backContentTransform(preview: BackPreview? = null, hold: Boolean = false): ContentTransform =
    ContentTransform(
        targetContentEnter = fadeIn(tween(BACK_PAGE_MILLIS, easing = BackUnderEasing)),
        initialContentExit = fadeOut(tween(BACK_PAGE_MILLIS, easing = if (hold) BackHoldEasing else BackAwayEasing)),
        targetContentZIndex = backUnderZ(preview?.serial ?: 0),
        sizeTransform = SizeTransform(clip = false),
    )

/**
 * The page a back gesture leaves shrinks and rounds with it ([progress], 0 to 1), moves a
 * little away from the edge the gesture came from and sinks a little, as a sheet does;
 * [fade] is its opacity. With [screenCorner] (dp) its corners show that round on the screen
 * all the way instead, whatever the scale: for a card that goes back into a card of that
 * radius, so the two match while the gesture runs.
 */
internal fun Modifier.backShrink(
    progress: () -> Float,
    fromLeft: () -> Boolean,
    fade: () -> Float = { 1f },
    screenCorner: Float = Float.NaN,
): Modifier = graphicsLayer {
    val shown = progress()
    if (shown > 0f) {
        val scale = 1f - (1f - BACK_MIN_SCALE) * shown
        scaleX = scale
        scaleY = scale
        translationX = (if (fromLeft()) 1f else -1f) * BACK_SHIFT_DP.dp.toPx() * shown
        translationY = size.height * BACK_SINK * shown
        // The outline is scaled with the layer: a radius on the screen is that much larger in it.
        val corner = if (screenCorner.isNaN()) BACK_CORNER_DP * shown else screenCorner / scale
        shape = backCornerShape(corner)
        clip = true
    }
    val opacity = fade()
    if (opacity < 1f) alpha = opacity.coerceIn(0f, 1f)
}

// The shapes of the corners of backShrink by a quarter dp (under a pixel), up to 64 dp, made
// once each: the layer takes a new outline only from another shape, and a new
// RoundedCornerShape every frame is garbage. Only touched while drawing, on the main thread.
private const val BACK_CORNER_STEPS = 4
private val backCornerShapes = arrayOfNulls<Shape>(64 * BACK_CORNER_STEPS + 1)

private fun backCornerShape(cornerDp: Float): Shape {
    val step = kotlin.math.round(cornerDp * BACK_CORNER_STEPS).toInt().coerceIn(0, backCornerShapes.size - 1)
    return backCornerShapes[step]
        ?: RoundedCornerShape((step.toFloat() / BACK_CORNER_STEPS).dp).also { backCornerShapes[step] = it }
}

/** The content in front of a back gesture of [preview]: it shrinks with it (the transition fades it). */
internal fun Modifier.backShrink(preview: BackPreview?): Modifier =
    if (preview == null) this else backShrink({ preview.progress.value }, { preview.fromLeft })

/**
 * A content laid over the one back leads to: it shrinks with the gesture and fades once let
 * go; [screenCorner] as in [backShrink].
 */
internal fun Modifier.backFront(preview: BackPreview?, screenCorner: Float = Float.NaN): Modifier =
    if (preview == null) {
        this
    } else {
        backShrink({ preview.progress.value }, { preview.fromLeft }, { preview.fade.value }, screenCorner)
    }

/**
 * A part of the content a nested back gesture of [preview] returns to: it grows and fades in
 * with the gesture (see BackUnderGrow.kt) about [pivotY] (a fraction of its height) instead of
 * lying there whole, and is as at rest once the gesture is over. Read while drawing only.
 */
internal fun GraphicsLayerScope.backUnder(preview: BackPreview, pivotY: Float = 0.5f) {
    // Over (also in the frame its values are reset in): as at rest.
    if (!preview.active) return
    val shown = backUnderShown(preview.progress.value, preview.fade.value)
    val scale = backUnderScale(shown)
    scaleX = scale
    scaleY = scale
    alpha *= backUnderAlpha(shown)
    transformOrigin = TransformOrigin(0.5f, pivotY)
}

/** The content a nested back gesture of [preview] returns to, growing about its centre (see [backUnder]). */
internal fun Modifier.backUnder(preview: BackPreview): Modifier = graphicsLayer { backUnder(preview) }

/**
 * One back gesture with a preview, as far as it went (see [follow]). Public, as the public
 * screens (MapStopSheet, FavoritesScreen) take it as a parameter.
 */
@Stable
class BackPreview {
    /** How far the gesture went, 0 to 1; springs back to 0 when it is called off. */
    val progress = Animatable(0f)

    /** The opacity of a content laid over the one underneath (see [backFront]): it fades once let go. */
    val fade = Animatable(1f)

    /** The gesture came from the left edge. */
    var fromLeft by mutableStateOf(true)

    /** A finger is on the gesture right now. */
    var gesture by mutableStateOf(false)

    /** The gesture or the animation after it runs: the content underneath is there too. */
    var active by mutableStateOf(false)

    // Counts the gestures: the end of one only clears the state when no newer one has started.
    internal var serial = 0
}

/**
 * Follows the back gesture of [events] on this preview and calls [onBack] once it is let go;
 * [onSeek] gets its progress as it goes, [onCancel] is called when it is called off. The
 * animation after the gesture runs in [scope], so a handler that the back switches off does
 * not cancel it halfway. Without [animate] (animations off) back simply goes at the end.
 */
internal suspend fun BackPreview.follow(
    events: Flow<BackEventCompat>,
    scope: CoroutineScope,
    animate: Boolean,
    onSeek: suspend (Float) -> Unit = {},
    onCancel: () -> Unit = {},
    onBack: () -> Unit,
) {
    if (!animate) {
        gesture = true
        try {
            events.collect { }
        } finally {
            gesture = false
        }
        onBack()
        return
    }
    val mine = ++serial
    fade.snapTo(1f)
    gesture = true
    active = true
    var committed = false
    try {
        events.collect { event ->
            fromLeft = event.swipeEdge == BackEventCompat.EDGE_LEFT
            val shown = event.progress.coerceIn(0f, 1f)
            progress.snapTo(shown)
            onSeek(shown)
        }
        committed = true
    } finally {
        gesture = false
        scope.launch {
            try {
                if (committed) {
                    // The page in front fades while the content underneath settles.
                    fade.animateTo(0f, tween(BACK_PAGE_MILLIS))
                } else {
                    // Called off: the page grows back.
                    progress.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                }
            } finally {
                if (serial == mine) {
                    active = false
                    scope.launch {
                        progress.snapTo(0f)
                        fade.snapTo(1f)
                    }
                }
            }
        }
        if (!committed) onCancel()
    }
    onBack()
}

/**
 * The transition of a content that a back gesture of [preview] can lead from [target] to
 * [backTarget]: while the finger is on it, the transition follows the gesture there
 * (seeking). Let go, and for every other change, it animates to [target] (on to the content
 * back led to). Called off, it follows the progress back as the content springs back, and
 * settles on the content that stays once that is whole: never an animation of its own (the
 * content would come back by its own way in, as when it is opened, and out of step with the
 * spring).
 */
@OptIn(ExperimentalTransitionApi::class)
@Composable
internal fun <S> rememberBackSeek(target: S, preview: BackPreview?, backTarget: S, label: String): Transition<S> {
    val seek = remember { SeekableTransitionState(target) }
    val gesture = preview?.gesture == true
    LaunchedEffect(target, gesture) {
        if (gesture) return@LaunchedEffect
        // Called off: followed with the progress below.
        if (preview?.active == true && target == seek.currentState && seek.targetState != target) return@LaunchedEffect
        // After a gesture the rest of the way takes as long as a fade, from wherever the finger
        // left it: let go near the end, the content in front does not vanish in one frame.
        seek.animateTo(target, if (preview?.active == true) tween(BACK_PAGE_MILLIS) else null)
    }
    val currentBackTarget by rememberUpdatedState(backTarget)
    val currentTarget by rememberUpdatedState(target)
    LaunchedEffect(preview) {
        val back = preview ?: return@LaunchedEffect
        // The progress with the gesture (+2) and its end (+4) in one number: one boxed Float
        // per frame, no pair.
        snapshotFlow { back.progress.value.coerceIn(0f, 1f) + (if (back.gesture) 2f else 0f) + (if (back.active) 4f else 0f) }
            .collect { packed ->
                val active = packed >= 4f
                val rest = if (active) packed - 4f else packed
                val on = rest >= 2f
                val shown = if (on) rest - 2f else rest
                val stays = currentTarget
                val calledOff = !on && stays == seek.currentState && seek.targetState != stays
                when {
                    on -> {
                        val to = currentBackTarget
                        if (to != seek.currentState) seek.seekTo(shown, targetState = to)
                    }
                    calledOff && active -> seek.seekTo(shown, targetState = seek.targetState)
                    calledOff -> {
                        seek.seekTo(0f, targetState = seek.targetState)
                        seek.snapTo(stays)
                    }
                }
            }
    }
    return rememberTransition(seek, label = label)
}

/**
 * A predictive back inside a page: while [enabled], back is [onBack]'s and not that of the page
 * around it. Inside AppRoot this is a layer of its one back handler (see [BackLayers]), only
 * counted while its page is the page in front ([LocalPageInFront]); outside it (the setup)
 * it has a handler of its own. The returned preview follows the gesture, for the page to
 * show where back leads underneath what shrinks (see [rememberBackSeek], [backFront]);
 * [onSeek] and [onCancel] are for a page that moves by itself (the pager of the setup).
 */
@Composable
internal fun PredictiveNestedBackHandler(
    enabled: Boolean,
    label: String = "nested",
    onSeek: suspend (Float) -> Unit = {},
    onCancel: () -> Unit = {},
    onBack: () -> Unit,
): BackPreview {
    val layer = remember { BackLayer(label) }
    val layers = LocalBackLayers.current
    val inFront = LocalPageInFront.current
    SideEffect {
        if (layer.enabled != enabled || layer.inFront != inFront) {
            BackLog.log("layer ${layer.label} enabled=$enabled inFront=$inFront")
        }
        layer.enabled = enabled
        layer.inFront = inFront
        layer.onSeek = onSeek
        layer.onCancel = onCancel
        layer.onBack = onBack
    }
    if (layers != null) {
        DisposableEffect(layers, layer) {
            layers.add(layer)
            onDispose { layers.remove(layer) }
        }
    } else {
        // Outside AppRoot: a handler of its own. Once a gesture has started it stays on until
        // it ends, whatever composes in the meantime: switched off halfway, the system would
        // call the gesture off.
        val scope = rememberCoroutineScope()
        val animate = rememberAnimationsOn()
        val preview = layer.preview
        PredictiveBackHandler(enabled = enabled || preview.gesture) { events ->
            preview.follow(
                events = events,
                scope = scope,
                animate = animate,
                onSeek = { layer.onSeek(it) },
                onCancel = { layer.onCancel() },
                onBack = { layer.onBack() },
            )
        }
    }
    return layer.preview
}
