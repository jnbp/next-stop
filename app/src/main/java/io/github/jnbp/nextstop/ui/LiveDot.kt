package io.github.jnbp.nextstop.ui

import android.content.Context
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.LineColors
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.ui.theme.AppColors
import kotlinx.coroutines.delay

/** The green of live times: a vehicle whose position rests on current live data. */
val LIVE_GREEN = Color(0xFF1D9E75)

/** One beat of the live dot: it pulses gently once in this time. */
const val LIVE_PULSE_MILLIS = 1_600L

// The live dot: green inside, a thin white outline around it, in dp.
internal const val LIVE_DOT_RADIUS_DP = 2.2f
internal const val LIVE_DOT_OUTLINE_DP = 1f

// How far the soft ring of the pulse reaches beyond the dot at its widest, in dp, and how
// strong it is when it starts.
internal const val LIVE_PULSE_REACH_DP = 4f
internal const val LIVE_PULSE_OPACITY = 0.5f

// How far the middle of the dot lies inside the corner of the badge, in dp: on its rounding.
internal const val LIVE_DOT_INSET_DP = 1.8f

// The size of a vehicle badge as the map draws it: about this wide per character plus the
// padding at its sides, and this high, in dp (11 sp bold text with the padding of the badge layer).
internal const val BADGE_CHAR_DP = 6.6
internal const val BADGE_SIDE_DP = 10.0
internal const val BADGE_HEIGHT_DP = 16.0

/** Where the beat of the pulse is at [millis]: 0 at its start, rising towards 1 at its end. */
fun livePulsePhase(millis: Long): Float {
    val within = Math.floorMod(millis, LIVE_PULSE_MILLIS)
    return within.toFloat() / LIVE_PULSE_MILLIS
}

/** How far the ring of the pulse reaches beyond the dot at [phase], in dp: quickly out, then slower. */
fun livePulseReach(phase: Float): Float {
    val p = phase.coerceIn(0f, 1f)
    val eased = 1f - (1f - p) * (1f - p)
    return LIVE_PULSE_REACH_DP * eased
}

/** How strong the ring of the pulse is at [phase]: it fades out as it grows. */
fun livePulseOpacity(phase: Float): Float {
    val p = phase.coerceIn(0f, 1f)
    return LIVE_PULSE_OPACITY * (1f - p)
}

/**
 * Where the middle of the live dot lies from the middle of a badge with a text of [length]
 * characters, in dp, right and down positive: at its upper right corner, a little inside.
 */
fun liveDotOffset(length: Int): FloatArray {
    val halfWidth = (length.coerceAtLeast(1) * BADGE_CHAR_DP + BADGE_SIDE_DP) / 2.0
    val halfHeight = BADGE_HEIGHT_DP / 2.0
    return floatArrayOf((halfWidth - LIVE_DOT_INSET_DP).toFloat(), (-halfHeight + LIVE_DOT_INSET_DP).toFloat())
}

/** How far the live dot, white outline included, reaches beyond the corner of its badge, in dp. */
fun liveDotOverhangDp(): Float = (LIVE_DOT_RADIUS_DP + LIVE_DOT_OUTLINE_DP - LIVE_DOT_INSET_DP).coerceAtLeast(0f)

/**
 * The id of the map image of a vehicle badge in the line colour [hex] ("#RRGGBB"), with the
 * live dot baked into its corner when [live]: one image per colour and kind.
 */
fun badgeImageId(hex: String, live: Boolean): String =
    "ns-badge-" + hex.removePrefix("#").uppercase() + if (live) "-live" else ""

/** Whether the hint about vehicle positions belongs on the map now. */
fun vehicleHintWanted(hintOn: Boolean, vehiclesShown: Boolean, vehicleCount: Int): Boolean =
    hintOn && vehiclesShown && vehicleCount > 0

/** How long the vehicles must have been there before the hint shows, in milliseconds. */
const val HINT_SHOW_AFTER_MILLIS = 400L

/** How long the vehicles must have been gone before the hint goes (a reload is quicker), in milliseconds. */
const val HINT_HIDE_AFTER_MILLIS = 2_000L

/** The least time the hint stays shown or hidden after a change, in milliseconds. */
const val HINT_STEADY_MILLIS = 2_000L

/**
 * How long the hint waits before it follows [wanted], when its last change was
 * [sinceChangeMillis] ago: it shows after a short moment and goes only after a longer one, and
 * never changes again within [HINT_STEADY_MILLIS] of the last change.
 */
fun hintSwitchDelayMillis(wanted: Boolean, sinceChangeMillis: Long, mapLeft: Boolean = false): Long {
    // The page with the map was left (another tab, as "My ways"): the hint goes at once, it must
    // not stand over the next page for the time a reload of the vehicles is waited out (#141).
    if (!wanted && mapLeft) return 0L
    val hold = if (wanted) HINT_SHOW_AFTER_MILLIS else HINT_HIDE_AFTER_MILLIS
    return maxOf(hold, HINT_STEADY_MILLIS - sinceChangeMillis)
}

/**
 * Whether the vehicle hint is shown, following [wanted] steadily (see [hintSwitchDelayMillis]):
 * a short gap while the vehicles reload, or vehicles that come and go, do not make it flicker.
 * With [mapLeft] (no page with the map in front) it goes at once.
 */
@Composable
fun rememberSteadyHint(wanted: Boolean, mapLeft: Boolean = false): State<Boolean> {
    val shown = remember { mutableStateOf(false) }
    val changedAt = remember { longArrayOf(Long.MIN_VALUE / 2) }
    LaunchedEffect(wanted, mapLeft) {
        if (wanted == shown.value) return@LaunchedEffect
        delay(hintSwitchDelayMillis(wanted, SystemClock.uptimeMillis() - changedAt[0], mapLeft))
        shown.value = wanted
        changedAt[0] = SystemClock.uptimeMillis()
    }
    return shown
}

/**
 * The setting "Hinweis zu Fahrzeugpositionen" while the app runs; stored in
 * [AppPrefs.vehicleHint]. Read on first use, so it needs no loading step.
 */
object VehicleHintSetting {
    private var state: MutableState<Boolean>? = null

    private fun state(context: Context): MutableState<Boolean> =
        state ?: mutableStateOf(AppPrefs(context).vehicleHint).also { state = it }

    /** True while the hint is shown on the map. */
    fun shown(context: Context): Boolean = state(context).value

    fun update(context: Context, value: Boolean) {
        AppPrefs(context).vehicleHint = value
        state(context).value = value
    }
}

// A dot that beats once does so this long after its step of the start (see IntroStep.LIVE).
private const val LIVE_BEAT_ONCE_DELAY_MILLIS = 180L

/**
 * The live dot as drawn in the app outside the map: green with a white outline, and a soft
 * ring that grows out of it and fades, once per [LIVE_PULSE_MILLIS]. Only its drawing reads
 * the beat, so the pulse costs no recomposition; it rests while the app is not shown. A dot
 * without [pulse] can still beat once as it comes in ([beatOnce]), saying that live data is there.
 */
@Composable
fun LiveDot(modifier: Modifier = Modifier, pulse: Boolean = true, beatOnce: Boolean = false) {
    val once = if (!pulse && beatOnce) {
        val beat = remember { Animatable(0f) }
        LaunchedEffect(beat) {
            if (beat.value > 0f) return@LaunchedEffect
            awaitIntroStep(IntroStep.LIVE)
            animationDelay(LIVE_BEAT_ONCE_DELAY_MILLIS)
            beat.animateTo(1f, tween(LIVE_PULSE_MILLIS.toInt(), easing = LinearEasing))
        }
        beat
    } else {
        null
    }
    // The pulse is an idle animation: still while the battery saver is active.
    val phase = if (pulse && !BatterySaver.active) {
        rememberInfiniteTransition(label = "liveDot").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(LIVE_PULSE_MILLIS.toInt(), easing = LinearEasing), RepeatMode.Restart),
            label = "livePulse",
        )
    } else {
        null
    }
    val outer = (LIVE_DOT_RADIUS_DP + LIVE_DOT_OUTLINE_DP).dp
    Canvas(modifier = modifier.size(outer * 2)) {
        val middle = center
        val edge = outer.toPx()
        val beat = phase?.value ?: once?.value?.takeIf { it > 0f && it < 1f }
        if (beat != null) {
            drawCircle(
                color = LIVE_GREEN.copy(alpha = livePulseOpacity(beat)),
                radius = edge + livePulseReach(beat).dp.toPx(),
                center = middle,
            )
        }
        drawCircle(color = Color.White, radius = edge, center = middle)
        drawCircle(color = LIVE_GREEN, radius = LIVE_DOT_RADIUS_DP.dp.toPx(), center = middle)
    }
}

/**
 * The small glass pill at the bottom of the map that tells where the vehicle positions come
 * from; a tap explains it.
 */
@Composable
fun VehicleHintPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val text = stringResource(R.string.vehicle_hint_pill)
    val live = stringResource(R.string.vehicle_hint_live)
    GlassSurface(modifier = modifier, shape = RoundedCornerShape(50)) {
        Row(
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onClick)
                .padding(start = 8.dp, end = 10.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LiveDot(pulse = false, beatOnce = true)
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = AppColors.Ink)) { append(live) }
                    append(" · ")
                    append(text)
                },
                color = AppColors.Muted,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                maxLines = 1,
            )
        }
    }
}

/** Explains the vehicle positions in two sentences; the hint can be hidden from here. */
@Composable
fun VehicleHintDialog(onDismiss: () -> Unit, onHide: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.vehicle_hint_title)) },
        text = { Text(text = stringResource(R.string.vehicle_hint_text)) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.hint_understood)) }
        },
        dismissButton = {
            TextButton(onClick = onHide) { Text(text = stringResource(R.string.vehicle_hint_hide)) }
        },
    )
}

/** The hint pill with its dialog, for the bottom of a map that shows vehicles. */
@Composable
fun VehicleHint(modifier: Modifier = Modifier, open: MutableState<Boolean>) {
    val context = LocalContext.current
    VehicleHintPill(onClick = { open.value = true }, modifier = modifier)
    if (open.value) {
        VehicleHintDialog(
            onDismiss = { open.value = false },
            onHide = {
                open.value = false
                VehicleHintSetting.update(context, false)
            },
        )
    }
}

/**
 * A vehicle badge as on the map, for explaining it: the line in its colour, with the live dot
 * at its upper right corner when [live], and a caption below.
 */
@Composable
fun VehicleBadgeSample(line: String, product: String, live: Boolean, caption: String, modifier: Modifier = Modifier, drift: Dp = 0.dp) {
    // An idle drift: still while the battery saver is active.
    val shift = if (BatterySaver.active) {
        null
    } else {
        rememberInfiniteTransition(label = "badgeDrift").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2_400, easing = LinearEasing), RepeatMode.Reverse),
            label = "badgeShift",
        )
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.graphicsLayer { translationX = ((shift?.value ?: 0.5f) - 0.5f) * drift.toPx() }) {
            LineBadge(line = line, product = product, minWidth = 40.dp, at = LineColors.BERLIN_CENTRE)
            if (live) {
                LiveDot(
                    // As on the map: only trains pulse, buses and trams carry a still dot.
                    pulse = isMajorProduct(product),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .graphicsLayer {
                            translationX = liveDotOverhangDp().dp.toPx()
                            translationY = -liveDotOverhangDp().dp.toPx()
                        },
                )
            }
        }
        Text(text = caption, color = AppColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
