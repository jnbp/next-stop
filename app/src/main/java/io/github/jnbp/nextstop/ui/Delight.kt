package io.github.jnbp.nextstop.ui

import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import io.github.jnbp.nextstop.R
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch
import io.github.jnbp.nextstop.ui.theme.AppColors
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path

/*
 * The small delights of the app in one place: buttons that give way under the finger and
 * spring back, numbers that roll, lists that come in one row after the other, the little
 * train that waits with the user, pages that grow out of the row they were opened from, and
 * the confetti of an arrival. Everything here stands still when the system has animations
 * switched off ("Remove animations", an animator duration scale of 0).
 */

/** Whether the system lets apps animate, for an animator duration scale of [durationScale]. */
fun animationsAllowed(durationScale: Float): Boolean = durationScale > 0f

/** False when the system has animations switched off ("Remove animations"). */
@Composable
fun rememberAnimationsOn(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val scale = try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        } catch (e: Exception) {
            1f
        }
        animationsAllowed(scale)
    }
}

/** How small a pressed button, chip or round button gets before it springs back. */
const val PRESS_SCALE = 0.88f

// A tap that was over before the press got far still dips at least to here.
private const val PRESS_DIP = 0.92f

/**
 * Lets a control give way under the finger and spring back with a small overshoot when it is
 * let go. [interactions] must be the source its clickable reports to. Put it before the clip
 * and background of the control, so all of it moves.
 */
fun Modifier.springPress(interactions: InteractionSource): Modifier = composed {
    val on = rememberAnimationsOn()
    val scale = remember { Animatable(1f) }
    LaunchedEffect(interactions, on) {
        if (!on) return@LaunchedEffect
        interactions.interactions.collect { interaction ->
            when (interaction) {
                // A new animation of the same Animatable stops the one before.
                is PressInteraction.Press -> launch {
                    scale.animateTo(PRESS_SCALE, spring(dampingRatio = 1f, stiffness = Spring.StiffnessHigh))
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> launch {
                    if (scale.value > PRESS_DIP) scale.animateTo(PRESS_DIP, tween(60))
                    scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
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

/** Items of a list come in this far apart, in milliseconds. */
const val STAGGER_ITEM_MILLIS = 80L

// Items further down than this come in together with it, so a long list is never slow.
private const val STAGGER_CAP = 8

/** How long the item at [index] of a list waits before it comes in. */
fun staggerDelay(index: Int): Long = index.coerceIn(0, STAGGER_CAP) * STAGGER_ITEM_MILLIS

/**
 * Which way a number rolls when it changes from [from] to [to]: true when the new one comes
 * in from below (the number got smaller, as a countdown does), false when it comes from
 * above (it grew, as a delay that got longer).
 */
fun rollsUp(from: Long?, to: Long?): Boolean = from == null || to == null || to <= from

/** The roll of a changing number: the new one slides in, the old one out the other way, with a spring. */
fun <S> AnimatedContentTransitionScope<S>.rollTransform(up: Boolean): ContentTransform {
    val slide = spring(
        dampingRatio = 0.62f,
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = IntOffset.VisibilityThreshold,
    )
    return (slideInVertically(slide) { height -> if (up) height * 2 / 3 else -height * 2 / 3 } + fadeIn(tween(160))) togetherWith
        (slideOutVertically(slide) { height -> if (up) -height * 2 / 3 else height * 2 / 3 } + fadeOut(tween(120))) using
        SizeTransform(clip = false)
}

/** No transition at all: the new content is simply there (animations off). */
fun <S> AnimatedContentTransitionScope<S>.noTransform(): ContentTransform =
    EnterTransition.None togetherWith ExitTransition.None using null

/** [rollTransform] with animations on ([animate]); with them off the new content is simply there. */
fun <S> AnimatedContentTransitionScope<S>.rollOrSnap(up: Boolean, animate: Boolean): ContentTransform =
    if (animate) rollTransform(up) else (EnterTransition.None togetherWith ExitTransition.None using null)

/**
 * [text] for the number [value]: when the number changes, the new text rolls in from below or above.
 *
 * The roll happens inside a slot of fixed size: as wide as [slotDigits] digits (or the text,
 * where that is wider) and as tall as one line, with digits of equal width. Its baseline is
 * that of the resting text, also while two numbers roll past each other: a unit aligned by
 * baseline beside it ("3 min") never moves, whatever the number, its width or the roll.
 * Without this the slot took the baseline of whichever number stood highest in the roll, so
 * "min" jumped up for a moment at every change, and the row moved with every narrower number.
 */
@Composable
fun RollingText(
    value: Long,
    text: String,
    color: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.ExtraBold,
    slotDigits: Int = 0,
) {
    // Digits of equal width ("tnum"), so a number of the same length always takes the same room.
    val style = LocalTextStyle.current.merge(
        TextStyle(color = color, fontSize = fontSize, fontWeight = fontWeight, fontFeatureSettings = "tnum"),
    )
    val measurer = rememberTextMeasurer()
    val template = if (text.isNotEmpty() && text.all { it.isDigit() }) "0".repeat(maxOf(text.length, slotDigits)) else text
    val slot = remember(template, style, measurer) { measurer.measure(template, style, maxLines = 1, softWrap = false) }
    val animate = rememberAnimationsOn()
    Layout(
        content = {
            AnimatedContent(
                targetState = value to text,
                transitionSpec = { rollOrSnap(rollsUp(initialState.first, targetState.first), animate) },
                contentAlignment = Alignment.CenterEnd,
                label = "roll",
            ) { (_, shown) ->
                Text(text = shown, style = style, maxLines = 1, softWrap = false)
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val placeable = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val width = maxOf(slot.size.width, placeable.width).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = slot.size.height.coerceIn(constraints.minHeight, constraints.maxHeight)
        // The baselines of the resting text, given explicitly: they win over those of the rolling children.
        layout(
            width,
            height,
            mapOf(FirstBaseline to slot.firstBaseline.roundToInt(), LastBaseline to slot.lastBaseline.roundToInt()),
        ) {
            // The number stands at the end of the slot, next to what follows it (the unit).
            placeable.place(width - placeable.width, (height - placeable.height) / 2)
        }
    }
}

/** Where the train of [TrainLoader] is, as the left edge of a train [trainWidth] wide on a track [trackWidth] wide. */
fun trainOffset(progress: Float, trackWidth: Float, trainWidth: Float): Float =
    -trainWidth + progress.coerceIn(0f, 1f) * (trackWidth + trainWidth)

/**
 * The pace of the little train through one run, as the share of the way it has gone at [time]
 * (both 0..1): quick while it is still hidden, slowing as it comes in so its front is in sight
 * for a good moment, a calm steady middle, then speeding up again as it leaves. A cubic around
 * the middle: speed 1 - [TRAIN_GLIDE_CALM] there and 1 + 2 * [TRAIN_GLIDE_CALM] at both ends.
 */
fun trainGlide(time: Float): Float {
    val s = time.coerceIn(0f, 1f) - 0.5f
    return 0.5f + (1f - TRAIN_GLIDE_CALM) * s + 4f * TRAIN_GLIDE_CALM * s * s * s
}

/** How much slower the train is in the middle of its run than at its ends (see [trainGlide]). */
const val TRAIN_GLIDE_CALM = 0.5f

/** The widest share of its track the train may take, so it is always seen whole for a while. */
const val TRAIN_MAX_TRACK_SHARE = 0.5f

/** Pixels per unit of the train's drawing on a track [trackWidth] wide: [preferred], or less on a short track. */
fun trainUnit(preferred: Float, trackWidth: Float): Float =
    minOf(preferred, trackWidth * TRAIN_MAX_TRACK_SHARE / TRAIN_LENGTH_UNITS)

// One run of the little train across its track, in milliseconds: a calm pass, slower than a
// quick dash (2.0 s at an even speed went by too fast to see the front of the train).
private const val TRAIN_RUN_MILLIS = 3_400

// Ease out coming in, steady in the middle, ease in going out (see trainGlide). An even speed,
// and an ease in and out before it, both spent too little of the run with the whole train in sight.
private val TRAIN_EASING = Easing { trainGlide(it) }

// Dp per unit of the drawing (a car is 30 x 18 units) on the phone.
private const val TRAIN_SCALE = 0.75f

/**
 * While content is on its way: a train of three plain rounded cars in the colour of the app
 * gliding over a dashed track from the left to the right, slowing down while it is in sight
 * ([TRAIN_EASING], [TRAIN_RUN_MILLIS]), again and again; it fades in at the left edge and out
 * at the right one. No wheels, no couplings, nothing bobs. Standing in the middle when the
 * system has animations switched off. [description] is read out to accessibility services
 * ("Loading…" when null; empty for none, where a text beside the train says it already).
 */
@Composable
fun TrainLoader(
    modifier: Modifier = Modifier,
    color: Color = AppColors.Blue,
    width: Dp = 112.dp,
    description: String? = null,
) {
    val on = rememberAnimationsOn()
    val transition = rememberInfiniteTransition(label = "train")
    val run by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(TRAIN_RUN_MILLIS, easing = TRAIN_EASING),
            RepeatMode.Restart,
            // The first run starts as if it had been going for a while: the train is already in
            // sight, so even a quick search shows it rather than an empty track.
            initialStartOffset = StartOffset((TRAIN_RUN_MILLIS * 0.4f).toInt(), StartOffsetType.FastForward),
        ),
        label = "trainRun",
    )
    val label = description ?: stringResource(R.string.loading)
    Canvas(
        modifier = modifier
            .width(width)
            .height(22.dp)
            // A layer of its own, so the fade at both edges takes away only the train and the track.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .then(if (label.isEmpty()) Modifier else Modifier.semantics { contentDescription = label }),
    ) {
        val unit = trainUnit(TRAIN_SCALE.dp.toPx(), size.width)
        val trainWidth = TRAIN_LENGTH_UNITS * unit
        val left = if (on) trainOffset(run, size.width, trainWidth) else (size.width - trainWidth) / 2
        drawMinimalTrain(left, unit, color, TRACK_GREY)
    }
}

/**
 * The one way the app shows that content is on its way: the little train ([TrainLoader])
 * and a short line of [text] beside it, in the muted grey of notes. [compact] for small
 * places (a row in a list, a line of chips): a shorter train and smaller text. Small buttons
 * keep their own tiny spinner.
 */
@Composable
fun LoadingRow(text: String, modifier: Modifier = Modifier, compact: Boolean = false) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
    ) {
        // The text says it: the train itself is not read out a second time.
        TrainLoader(width = if (compact) 80.dp else 96.dp, description = "")
        Text(
            text = text,
            color = AppColors.Muted,
            fontSize = if (compact) 13.sp else 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// The train of the loader in units (the mockup's pixels): three cars of 30 x 18 with gaps of 3.
private const val CAR_WIDTH_UNITS = 30f
private const val CAR_HEIGHT_UNITS = 18f
private const val CAR_GAP_UNITS = 3f
private const val CAR_CORNER_UNITS = 6f
private const val NOSE_CORNER_UNITS = 12f
private const val TRAIN_LENGTH_UNITS = CAR_WIDTH_UNITS * 3 + CAR_GAP_UNITS * 2

// The track: 4 units tall, dashes of 10 with gaps of 6, its top this far below the cars.
private const val TRACK_HEIGHT_UNITS = 4f
private const val TRACK_DASH_UNITS = 10f
private const val TRACK_GAP_UNITS = 6f
private const val TRACK_CLEARANCE_UNITS = 3f

// Share of the width over which the train and track fade in at the left and out at the right.
private const val EDGE_FADE = 0.18f

// The grey of the dashed track.
private val TRACK_GREY = Color(0xFFB9C2CF)

/**
 * The loader's picture: the dashed track along the bottom, the train with its left edge at
 * [left] above it ([unit] pixels per unit), the last car's top front corner rounder; then both
 * ends faded out. The layer must be drawn offscreen for the fade (CompositingStrategy.Offscreen).
 */
private fun DrawScope.drawMinimalTrain(left: Float, unit: Float, train: Color, track: Color) {
    val trackHeight = TRACK_HEIGHT_UNITS * unit
    val trackMiddle = size.height - trackHeight / 2
    drawLine(
        color = track,
        start = Offset(trackHeight / 2, trackMiddle),
        end = Offset(size.width - trackHeight / 2, trackMiddle),
        strokeWidth = trackHeight,
        cap = StrokeCap.Round,
        // A round cap adds half the height at each end of a dash: drawn that much shorter, it is 10 long.
        pathEffect = PathEffect.dashPathEffect(
            floatArrayOf((TRACK_DASH_UNITS - TRACK_HEIGHT_UNITS) * unit, (TRACK_GAP_UNITS + TRACK_HEIGHT_UNITS) * unit),
        ),
    )
    val car = Size(CAR_WIDTH_UNITS * unit, CAR_HEIGHT_UNITS * unit)
    val top = size.height - trackHeight - TRACK_CLEARANCE_UNITS * unit - car.height
    val corner = CornerRadius(CAR_CORNER_UNITS * unit)
    for (index in 0 until 3) {
        val x = left + index * (car.width + CAR_GAP_UNITS * unit)
        if (index < 2) {
            drawRoundRect(train, topLeft = Offset(x, top), size = car, cornerRadius = corner)
        } else {
            val front = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(Offset(x, top), car),
                        topLeft = corner,
                        topRight = CornerRadius(NOSE_CORNER_UNITS * unit),
                        bottomRight = corner,
                        bottomLeft = corner,
                    ),
                )
            }
            drawPath(front, train)
        }
    }
    // Soft ends: transparent at the very edges, whole from EDGE_FADE of the width inwards.
    drawRect(
        brush = Brush.horizontalGradient(
            0f to Color.Transparent,
            EDGE_FADE to Color.Black,
            1f - EDGE_FADE to Color.Black,
            1f to Color.Transparent,
        ),
        blendMode = BlendMode.DstIn,
    )
}

/**
 * The place on the screen a page was opened from: the row or card that was tapped, in root
 * coordinates. Set right before the page opens; [take] hands it to the page once.
 */
object OpenOrigin {
    private var rect: Rect? = null
    private var at = 0L

    fun set(bounds: Rect?) {
        rect = bounds
        at = SystemClock.uptimeMillis()
    }

    /** The origin when it was set a moment ago, else null; either way it is used up. */
    fun take(): Rect? {
        val result = rect?.takeIf { SystemClock.uptimeMillis() - at <= ORIGIN_FRESH_MILLIS && !it.isEmpty }
        rect = null
        return result
    }
}

// An origin older than this belongs to another tap.
private const val ORIGIN_FRESH_MILLIS = 1_500L

/** Keeps the bounds of this element in root coordinates in [holder] (its first place), for OpenOrigin. */
fun Modifier.boundsInto(holder: Array<Rect?>): Modifier = onGloballyPositioned { holder[0] = it.boundsInRoot() }

/**
 * How a page that grows out of a row stands at [progress] (0 the row, 1 the page): scale x
 * and y and the shift x and y, for a transform around the top left corner of the page.
 * [origin] and [page] are left, top, width and height.
 */
fun growFrame(origin: FloatArray, page: FloatArray, progress: Float): FloatArray {
    val rest = 1f - progress
    val scaleX = (origin[2] / page[2]) * rest + progress
    val scaleY = (origin[3] / page[3]) * rest + progress
    return floatArrayOf(scaleX, scaleY, (origin[0] - page[0]) * rest, (origin[1] - page[1]) * rest)
}

/**
 * Lets a page of an AnimatedContent grow out of [origin] (the row it was opened from) when it
 * comes in, and shrink back into it when it goes: a container transform. Without an origin,
 * or with animations off, the page is left alone.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun Modifier.growFrom(scope: AnimatedVisibilityScope, origin: Rect?): Modifier {
    if (origin == null || !rememberAnimationsOn()) return this
    val grow by scope.transition.animateFloat(
        transitionSpec = { spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow) },
        label = "growFrom",
    ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
    val own = remember { arrayOfNulls<Rect>(1) }
    return this
        .boundsInto(own)
        .graphicsLayer {
            val page = own[0] ?: return@graphicsLayer
            if (page.width <= 0f || page.height <= 0f) return@graphicsLayer
            val progress = grow
            if (progress >= 0.999f && progress <= 1.001f) return@graphicsLayer
            val frame = growFrame(
                floatArrayOf(origin.left, origin.top, origin.width, origin.height),
                floatArrayOf(page.left, page.top, page.width, page.height),
                progress,
            )
            transformOrigin = TransformOrigin(0f, 0f)
            scaleX = frame[0]
            scaleY = frame[1]
            translationX = frame[2]
            translationY = frame[3]
            alpha = (progress / 0.35f).coerceIn(0f, 1f)
            shape = RoundedCornerShape((24f * (1f - progress.coerceIn(0f, 1f))).dp)
            clip = true
        }
}

/** The trips whose arrival was celebrated already, so the confetti comes once per trip. */
object Celebrations {
    private val played = HashSet<String>()

    /** True the first time it is asked for [key], false after that. */
    @Synchronized
    fun claim(key: String): Boolean = played.add(key)
}

/** How long the confetti of an arrival flies, in seconds. */
const val CONFETTI_SECONDS = 1.8f

/** How many pieces of confetti an arrival throws. */
const val CONFETTI_COUNT = 45

// The pull down on the confetti, in dp per second squared, and how fast the air slows it sideways.
private const val CONFETTI_GRAVITY = 820f
private const val CONFETTI_DRAG = 1.6f

/** The kinds of confetti: a round dot, a short ribbon that flutters, a thin strip. */
enum class ConfettiShape { Dot, Ribbon, Strip }

/**
 * One piece of confetti: its speed at the start ([vx], [vy] in dp per second, up negative),
 * how fast it spins (degrees per second) from [angle], its shape, colour (index into the
 * colours) and size in dp.
 */
class ConfettiPiece(
    val vx: Float,
    val vy: Float,
    val spin: Float,
    val angle: Float,
    val shape: ConfettiShape,
    val colorIndex: Int,
    val size: Float,
)

/** [count] pieces for a burst in [colors] colours, the same for the same [seed]. */
fun confettiPieces(count: Int, colors: Int, seed: Long): List<ConfettiPiece> {
    val random = java.util.Random(seed)
    return List(count) { index ->
        // Mostly up, fanned out to both sides.
        val direction = (-PI / 2 + (random.nextDouble() - 0.5) * PI * 0.85).toFloat()
        val speed = 300f + random.nextFloat() * 260f
        ConfettiPiece(
            vx = cos(direction) * speed,
            vy = sin(direction) * speed,
            spin = (random.nextFloat() - 0.5f) * 1_080f,
            angle = random.nextFloat() * 360f,
            shape = ConfettiShape.entries[index % ConfettiShape.entries.size],
            colorIndex = if (colors <= 0) 0 else index % colors,
            size = 4f + random.nextFloat() * 4f,
        )
    }
}

/** Where [piece] is [seconds] after the burst, in dp from where it started (x right, y down). */
fun confettiPosition(piece: ConfettiPiece, seconds: Float): Offset {
    val x = piece.vx * (1f - exp(-CONFETTI_DRAG * seconds)) / CONFETTI_DRAG
    val y = piece.vy * seconds + 0.5f * CONFETTI_GRAVITY * seconds * seconds
    return Offset(x, y)
}

/** How visible confetti is [seconds] after the burst: whole, then fading out over its last half second. */
fun confettiAlpha(seconds: Float): Float = ((CONFETTI_SECONDS - seconds) / 0.5f).coerceIn(0f, 1f)

/**
 * A burst of confetti in [colors], thrown up from [origin] (in this element's coordinates)
 * once when [play] is true: about 45 dots, ribbons and strips that shoot up, spin and fall.
 * Drawn on a canvas that does not clip, so the pieces fly beyond it. Nothing with animations off.
 */
@Composable
fun ConfettiBurst(colors: List<Color>, play: Boolean, origin: DrawScope.() -> Offset, modifier: Modifier = Modifier) {
    val on = rememberAnimationsOn()
    val time = remember { Animatable(0f) }
    val pieces = remember(colors) { confettiPieces(CONFETTI_COUNT, colors.size, seed = colors.hashCode().toLong()) }
    LaunchedEffect(play, on) {
        if (!play || !on) return@LaunchedEffect
        time.snapTo(0f)
        time.animateTo(CONFETTI_SECONDS, tween((CONFETTI_SECONDS * 1_000).toInt(), easing = LinearEasing))
    }
    if (!play || !on || colors.isEmpty()) return
    Canvas(modifier = modifier) {
        val seconds = time.value
        if (seconds <= 0f || seconds >= CONFETTI_SECONDS) return@Canvas
        val start = origin()
        val fade = confettiAlpha(seconds)
        for (piece in pieces) {
            val at = confettiPosition(piece, seconds)
            val center = Offset(start.x + at.x.dp.toPx(), start.y + at.y.dp.toPx())
            val color = colors[piece.colorIndex % colors.size].copy(alpha = fade)
            val angle = piece.angle + piece.spin * seconds
            val edge = piece.size.dp.toPx()
            when (piece.shape) {
                ConfettiShape.Dot -> drawCircle(color, radius = edge / 2.4f, center = center)
                ConfettiShape.Ribbon -> rotate(angle, pivot = center) {
                    // Turning about its long side: its width breathes with the spin.
                    val width = edge * 1.8f * (0.25f + 0.75f * abs(cos(Math.toRadians(angle.toDouble() * 2)).toFloat()))
                    drawRoundRect(
                        color,
                        topLeft = Offset(center.x - width / 2, center.y - edge * 0.35f),
                        size = Size(width, edge * 0.7f),
                        cornerRadius = CornerRadius(edge * 0.3f, edge * 0.3f),
                    )
                }
                ConfettiShape.Strip -> rotate(angle, pivot = center) {
                    drawRect(
                        color,
                        topLeft = Offset(center.x - edge * 0.2f, center.y - edge),
                        size = Size(edge * 0.4f, edge * 2f),
                    )
                }
            }
        }
    }
}

/** How high the marked stop hops on the map, in dp. */
const val STOP_HOP_DP = 9f

/** The stop positions of a marked stop pop in this far apart, in milliseconds, each taking POSITION_POP_MILLIS. */
const val POSITION_POP_STAGGER_MILLIS = 120L
const val POSITION_POP_MILLIS = 240L

// The phases of the hop, in milliseconds from its start: up, down, the squash of landing, settled.
private const val HOP_UP_END = 240f
private const val HOP_DOWN_END = 460f
private const val HOP_SQUASH_END = 560f
private const val HOP_SETTLE_END = 900f

// Where the hop starts and its sizes: small, stretched at the top, squashed on landing.
private const val HOP_START_SIZE = 0.72f
private const val HOP_TOP_SIZE = 1.08f
private const val HOP_LAND_SIZE = 0.95f

// The name fades in as the sign comes down; the positions start once it has landed.
private const val HOP_NAME_FROM = 420f
private const val HOP_NAME_MILLIS = 240f
private const val HOP_POSITIONS_FROM = 520f

/**
 * One frame of the hop of a stop marked on the map: how far the sign is lifted (0 to 1 of
 * STOP_HOP_DP), its size, how visible its name is, and how long ago its positions started to
 * pop in (negative before). The map draws signs with one size for both directions, so the
 * stretch and squash are a larger and a smaller sign.
 */
data class StopHop(val lift: Float, val size: Float, val nameAlpha: Float, val positionsMillis: Float)

/** The hop [millis] after a stop was marked (see StopHop): up, down, squash, settle, then at rest. */
fun stopHop(millis: Float): StopHop {
    val t = millis.coerceAtLeast(0f)
    val lift: Float
    val size: Float
    when {
        t < HOP_UP_END -> {
            val k = t / HOP_UP_END
            val up = 1f - (1f - k) * (1f - k)
            lift = up
            size = HOP_START_SIZE + (HOP_TOP_SIZE - HOP_START_SIZE) * up
        }
        t < HOP_DOWN_END -> {
            val k = (t - HOP_UP_END) / (HOP_DOWN_END - HOP_UP_END)
            val down = k * k
            lift = 1f - down
            size = HOP_TOP_SIZE + (1f - HOP_TOP_SIZE) * down
        }
        t < HOP_SQUASH_END -> {
            val k = (t - HOP_DOWN_END) / (HOP_SQUASH_END - HOP_DOWN_END)
            lift = 0f
            size = 1f + (HOP_LAND_SIZE - 1f) * sin(k * PI.toFloat() / 2f)
        }
        t < HOP_SETTLE_END -> {
            // From the squash back to its size with one small swing past it.
            val k = (t - HOP_SQUASH_END) / (HOP_SETTLE_END - HOP_SQUASH_END)
            lift = 0f
            size = 1f + (HOP_LAND_SIZE - 1f) * cos(k * PI.toFloat() * 1.5f) * exp(-2f * k)
        }
        else -> {
            lift = 0f
            size = 1f
        }
    }
    val name = ((t - HOP_NAME_FROM) / HOP_NAME_MILLIS).coerceIn(0f, 1f)
    return StopHop(lift = lift, size = size, nameAlpha = name, positionsMillis = t - HOP_POSITIONS_FROM)
}

/** How long the whole hop of a stop with [positions] stop positions takes, in milliseconds. */
fun stopHopMillis(positions: Int): Long {
    val last = HOP_POSITIONS_FROM.toLong() + (positions - 1).coerceAtLeast(0) * POSITION_POP_STAGGER_MILLIS + POSITION_POP_MILLIS
    return maxOf(HOP_SETTLE_END.toLong(), last)
}

/** A time long after any hop: the stop and all its positions at rest. */
const val STOP_HOP_AT_REST = 1_000_000f
