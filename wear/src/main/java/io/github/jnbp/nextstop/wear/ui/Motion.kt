package io.github.jnbp.nextstop.wear.ui

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.delay
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import io.github.jnbp.nextstop.wear.data.WatchStore

/*
 * The motion of the watch app: the same feel as the phone, held back. Buttons give way a
 * little, the arrival pops a check with a short ring of confetti along the round edge, and a
 * few moments buzz. Nothing of it in ambient mode, and nothing when the system has animations
 * switched off or the app's setting "Animations" is off (rememberWatchAnimationsOn).
 */

/** Whether the system lets apps animate, for an animator duration scale of [durationScale]. */
fun watchAnimationsAllowed(durationScale: Float): Boolean = durationScale > 0f

/** Whether the watch animates: the app's setting "Animations" ([appSetting]) and the system's ([durationScale]) both on. */
fun watchAnimationsAllowed(durationScale: Float, appSetting: Boolean): Boolean = appSetting && watchAnimationsAllowed(durationScale)

/**
 * The one switch every motion of the watch app reads: false when the system has animations
 * switched off or the app's setting "Animations" is off (WatchStore.animations, for older or
 * slower watches). Then everything shows its final state at once.
 */
@Composable
fun rememberWatchAnimationsOn(): Boolean {
    val context = LocalContext.current
    val system = remember(context) {
        val scale = try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        } catch (e: Exception) {
            1f
        }
        watchAnimationsAllowed(scale)
    }
    val setting by WatchStore.animations.collectAsState()
    return system && setting
}

// A pressed button on the watch gives way less than on the phone.
private const val WATCH_PRESS_SCALE = 0.94f

/** Lets a button give way under the finger and spring back; [interactions] is the source of its clicks. */
fun Modifier.watchSpringPress(interactions: InteractionSource): Modifier = composed {
    val on = rememberWatchAnimationsOn()
    val scale = remember { Animatable(1f) }
    LaunchedEffect(interactions, on) {
        if (!on) return@LaunchedEffect
        interactions.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> launch {
                    scale.animateTo(WATCH_PRESS_SCALE, spring(dampingRatio = 1f, stiffness = Spring.StiffnessHigh))
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> launch {
                    scale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
                }
                else -> Unit
            }
        }
    }
    graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/** The moments that buzz: a trip starts, a change was made, the trip arrived. */
enum class WatchMoment { Start, ChangeMade, Arrived }

/** A short haptic for [moment]: a click for the start, a heavy click for a change made and for the arrival. */
fun buzz(context: Context, moment: WatchMoment) {
    val vibrator = context.getSystemService(Vibrator::class.java) ?: return
    if (!vibrator.hasVibrator()) return
    val effect = when (moment) {
        WatchMoment.Start -> VibrationEffect.EFFECT_CLICK
        WatchMoment.ChangeMade, WatchMoment.Arrived -> VibrationEffect.EFFECT_HEAVY_CLICK
    }
    vibrator.vibrate(VibrationEffect.createPredefined(effect))
}

/** How long the arrival on the watch is celebrated, in seconds. */
const val WATCH_ARRIVAL_SECONDS = 1.6f

/** How many pieces fly in the ring of confetti along the edge. */
const val EDGE_CONFETTI_COUNT = 36

/**
 * One piece of the confetti ring: where on the edge it starts ([angle], degrees clockwise from
 * the top), how fast it runs along it (degrees per second), how it spins, its colour (index)
 * and whether it is a dot or a little strip.
 */
class EdgePiece(val angle: Float, val drift: Float, val spin: Float, val colorIndex: Int, val dot: Boolean)

/** The pieces of one ring in [colors] colours, the same for the same [seed]. */
fun edgeConfetti(count: Int, colors: Int, seed: Long): List<EdgePiece> {
    val random = java.util.Random(seed)
    return List(count) { index ->
        EdgePiece(
            angle = index * 360f / count + (random.nextFloat() - 0.5f) * 8f,
            drift = (if (index % 2 == 0) 1f else -1f) * (30f + random.nextFloat() * 50f),
            spin = (random.nextFloat() - 0.5f) * 720f,
            colorIndex = if (colors <= 0) 0 else index % colors,
            dot = index % 3 == 0,
        )
    }
}

/**
 * Where [piece] is [seconds] after the arrival: its angle (degrees clockwise from the top),
 * its distance from the middle as a share of the radius (it flies out from inside to just
 * within the edge and sinks back a little) and how visible it is.
 */
fun edgePieceAt(piece: EdgePiece, seconds: Float): FloatArray {
    val out = (seconds / 0.45f).coerceIn(0f, 1f)
    val eased = 1f - (1f - out) * (1f - out) * (1f - out)
    val sink = ((seconds - 0.45f).coerceAtLeast(0f)) * 0.06f
    val reach = 0.55f + 0.37f * eased - sink
    val alpha = ((WATCH_ARRIVAL_SECONDS - seconds) / 0.5f).coerceIn(0f, 1f)
    return floatArrayOf(piece.angle + piece.drift * seconds, reach, alpha)
}

/**
 * The arrival on the watch, played once when [play] turns true: a check that pops in the
 * middle and draws itself, and a short ring of confetti in [colors] along the round edge.
 * Gone again after WATCH_ARRIVAL_SECONDS. Never in [ambient] mode.
 */
@Composable
fun ArrivalCelebration(play: Boolean, colors: List<Color>, ambient: Boolean, check: Color, onCheck: Color) {
    val on = rememberWatchAnimationsOn()
    val time = remember { Animatable(0f) }
    val pop = remember { Animatable(0f) }
    val pieces = remember(colors) { edgeConfetti(EDGE_CONFETTI_COUNT, colors.size, seed = colors.hashCode().toLong()) }
    val active = play && on && !ambient
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        launch { pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)) }
        time.animateTo(WATCH_ARRIVAL_SECONDS, tween((WATCH_ARRIVAL_SECONDS * 1_000).toInt(), easing = LinearEasing))
    }
    if (!active || colors.isEmpty()) return
    Canvas(modifier = Modifier.fillMaxSize()) {
        val seconds = time.value
        if (seconds >= WATCH_ARRIVAL_SECONDS) return@Canvas
        val middle = Offset(size.width / 2, size.height / 2)
        val radius = min(size.width, size.height) / 2
        val fade = ((WATCH_ARRIVAL_SECONDS - seconds) / 0.4f).coerceIn(0f, 1f)
        // The check: a disc that pops, the tick drawn after it.
        val scale = pop.value
        val disc = 30.dp.toPx() * scale
        drawCircle(check.copy(alpha = fade), radius = disc, center = middle)
        val tick = ((scale - 0.35f) / 0.65f).coerceIn(0f, 1f) * ((seconds - 0.12f) / 0.35f).coerceIn(0f, 1f)
        if (tick > 0f) {
            val w = 26.dp.toPx()
            val a = middle + Offset(-w * 0.42f, 0f)
            val b = middle + Offset(-w * 0.1f, w * 0.3f)
            val c = middle + Offset(w * 0.44f, -w * 0.28f)
            val first = (tick / 0.35f).coerceIn(0f, 1f)
            val second = ((tick - 0.35f) / 0.65f).coerceIn(0f, 1f)
            val stroke = 4.dp.toPx()
            drawLine(onCheck.copy(alpha = fade), a, a + (b - a) * first, strokeWidth = stroke, cap = StrokeCap.Round)
            if (second > 0f) drawLine(onCheck.copy(alpha = fade), b, b + (c - b) * second, strokeWidth = stroke, cap = StrokeCap.Round)
        }
        // The ring of confetti along the edge.
        val edge = 5.dp.toPx()
        for (piece in pieces) {
            val at = edgePieceAt(piece, seconds)
            val angle = Math.toRadians(at[0].toDouble())
            val center = Offset(
                middle.x + (sin(angle) * radius * at[1]).toFloat(),
                middle.y - (cos(angle) * radius * at[1]).toFloat(),
            )
            val color = colors[piece.colorIndex % colors.size].copy(alpha = at[2])
            if (piece.dot) {
                drawCircle(color, radius = edge / 2.2f, center = center)
            } else {
                rotate(at[0] + piece.spin * seconds, pivot = center) {
                    drawRoundRect(
                        color,
                        topLeft = Offset(center.x - edge * 0.25f, center.y - edge),
                        size = Size(edge * 0.5f, edge * 2f),
                        cornerRadius = CornerRadius(edge * 0.2f, edge * 0.2f),
                    )
                }
            }
        }
    }
}

// A trip whose start is at most this old buzzes when its screen opens.
private const val START_FRESH_MILLIS = 20_000L

// A trip that ends this close to (or after) its arrival counts as arrived, not as ended early.
private const val ARRIVED_SLACK_MILLIS = 60_000L

/**
 * Follows the snapshots of a trip and tells the moments worth a buzz: its start (a trip that
 * began just now, or a new one while the screen is open), every change made (the traveller
 * rides again after a ride before), and the arrival (the step "Arrived", or a trip that ends
 * at its arrival time). What was already so when the screen opened is not told again.
 */
class TripMoments {
    private var known = false
    private var key: String? = null
    private var lastKind: io.github.jnbp.nextstop.wear.data.StepKind? = null
    private var rides = 0
    private var arrived = false

    fun update(
        tripKey: String?,
        active: Boolean,
        kind: io.github.jnbp.nextstop.wear.data.StepKind,
        arrival: Long,
        started: Long,
        now: Long,
    ): List<WatchMoment> {
        val riding = io.github.jnbp.nextstop.wear.data.StepKind.GetOff
        val isArrived = if (active) {
            kind == io.github.jnbp.nextstop.wear.data.StepKind.Arrived
        } else {
            arrival > 0 && now >= arrival - ARRIVED_SLACK_MILLIS
        }
        if (!known || (active && tripKey != key)) {
            val firstLook = !known
            known = true
            key = tripKey
            lastKind = kind
            rides = if (active && kind == riding) 1 else 0
            arrived = isArrived || !active
            val fresh = !firstLook || now - started < START_FRESH_MILLIS
            return if (active && !isArrived && fresh) listOf(WatchMoment.Start) else emptyList()
        }
        val moments = ArrayList<WatchMoment>()
        if (active && kind != lastKind) {
            if (kind == riding) {
                rides++
                if (rides >= 2) moments.add(WatchMoment.ChangeMade)
            }
            lastKind = kind
        }
        if (isArrived && !arrived) {
            arrived = true
            moments.add(WatchMoment.Arrived)
        }
        if (!active) arrived = true
        return moments
    }
}

/**
 * Shows [target] with [content]; when it changes, the new one comes in softly instead of
 * jumping: rolling up from below with [roll] (a countdown), else cross-fading. Simply there
 * in [ambient] mode and with animations off.
 */
@Composable
fun <T> WatchSwap(
    target: T,
    modifier: Modifier = Modifier,
    ambient: Boolean = false,
    roll: Boolean = false,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable (T) -> Unit,
) {
    if (ambient || !rememberWatchAnimationsOn()) {
        Box(modifier = modifier, contentAlignment = contentAlignment) { content(target) }
        return
    }
    AnimatedContent(
        targetState = target,
        modifier = modifier,
        contentAlignment = contentAlignment,
        transitionSpec = {
            if (roll) {
                val slide = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)
                (slideInVertically(slide) { it / 2 } + fadeIn(tween(160))) togetherWith
                    (slideOutVertically(slide) { -it / 2 } + fadeOut(tween(120))) using SizeTransform(clip = false)
            } else {
                fadeIn(tween(200, delayMillis = 40)) togetherWith fadeOut(tween(120)) using SizeTransform(clip = false)
            }
        },
        label = "watchSwap",
    ) { shown -> content(shown) }
}

/** Lets an element take its new size softly when what it holds changes; at once in ambient mode or with animations off. */
fun Modifier.watchSoftSize(ambient: Boolean = false): Modifier = composed {
    if (!ambient && rememberWatchAnimationsOn()) Modifier.animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)) else Modifier
}

/**
 * Lets a row of a list fade in and rise a little when it first shows, as departures and
 * stops arrive after loading; simply there with animations off.
 */
fun Modifier.watchAppear(): Modifier = composed {
    val on = rememberWatchAnimationsOn()
    val progress = remember { Animatable(if (on) 0f else 1f) }
    LaunchedEffect(progress) {
        if (progress.value < 1f) progress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
    }
    graphicsLayer {
        alpha = progress.value.coerceIn(0f, 1f)
        translationY = (1f - progress.value) * 10.dp.toPx()
    }
}

/** How long each row of a list waits behind the one above it when the list first shows, in milliseconds. */
const val ENTER_STEP_MILLIS = 45L

/** Rows further down than this enter together with it, so a long list is not slow to fill. */
const val ENTER_MAX_STEPS = 6

/** The wait of the row at [index] before it enters (see [watchEnter]). */
fun enterDelayMillis(index: Int): Long = index.coerceIn(0, ENTER_MAX_STEPS) * ENTER_STEP_MILLIS

/**
 * Lets the row at [index] of a page that opens rise and fade in a moment after the row above
 * it, so the page fills from the top. Only while [play] (the first moments of the page: a row
 * scrolled into view later is simply there) and with animations on.
 */
fun Modifier.watchEnter(index: Int, play: Boolean): Modifier = composed {
    val on = play && rememberWatchAnimationsOn()
    val progress = remember { Animatable(if (on) 0f else 1f) }
    LaunchedEffect(progress) {
        if (progress.value < 1f) {
            delay(enterDelayMillis(index))
            progress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    graphicsLayer {
        val shown = progress.value
        alpha = shown.coerceIn(0f, 1f)
        translationY = (1f - shown) * 14.dp.toPx()
        scaleX = 0.96f + 0.04f * shown
        scaleY = 0.96f + 0.04f * shown
    }
}

// One sweep of the shimmer over a placeholder, in milliseconds, and how bright its band is.
private const val SHIMMER_MILLIS = 1_200
private const val SHIMMER_ALPHA = 0.14f

/**
 * A soft band of light that sweeps over a placeholder while its content loads, drawn over it
 * (the sweep only redraws, nothing recomposes per frame). It stands still with animations off.
 */
fun Modifier.watchShimmer(): Modifier = composed {
    if (!rememberWatchAnimationsOn()) return@composed Modifier
    val transition = rememberInfiniteTransition(label = "watchShimmer")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SHIMMER_MILLIS, easing = LinearEasing)),
        label = "watchShimmerSweep",
    )
    drawWithContent {
        drawContent()
        val band = size.width * 0.6f
        val left = -band + sweep * (size.width + band)
        drawRect(
            Brush.horizontalGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = SHIMMER_ALPHA), Color.Transparent),
                startX = left,
                endX = left + band,
            ),
        )
    }
}

/**
 * What a full-size button of the watch needs to give way under the finger as SmallAction does
 * (see [watchSpringPress]): pass [source] as its interactionSource and add [modifier] to its own.
 * Nothing moves with animations off.
 */
class WatchPress(val source: MutableInteractionSource, val modifier: Modifier)

/** A [WatchPress] for one button, kept across recompositions. */
@Composable
fun rememberWatchPress(): WatchPress {
    val source = remember { MutableInteractionSource() }
    return remember(source) { WatchPress(source, Modifier.watchSpringPress(source)) }
}

/**
 * Lets a page come in softly when it opens: from a little smaller and fainter to its place,
 * once per page (not again when coming back to it), on top of the system's swipe between pages.
 * Simply there with animations off.
 */
fun Modifier.watchScreenEnter(): Modifier = composed {
    val on = rememberWatchAnimationsOn()
    var played by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (on && !played) 0f else 1f) }
    LaunchedEffect(progress) {
        if (progress.value < 1f) progress.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
        played = true
    }
    graphicsLayer {
        val shown = progress.value.coerceIn(0f, 1f)
        alpha = SCREEN_ENTER_ALPHA + (1f - SCREEN_ENTER_ALPHA) * shown
        scaleX = SCREEN_ENTER_SCALE + (1f - SCREEN_ENTER_SCALE) * shown
        scaleY = SCREEN_ENTER_SCALE + (1f - SCREEN_ENTER_SCALE) * shown
    }
}

// Where a page starts when it comes in (see watchScreenEnter): its opacity and its size.
private const val SCREEN_ENTER_ALPHA = 0.3f
private const val SCREEN_ENTER_SCALE = 0.94f

