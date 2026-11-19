package io.github.jnbp.nextstop.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.SunSide
import io.github.jnbp.nextstop.data.sunIntroSunPoint
import io.github.jnbp.nextstop.ui.theme.AppColors

/*
 * The explanation of the sunny side (#90): it opens once, with the first hint of the sunny
 * side a ride shows (see SunSideSetting.claimIntro), as a sheet with a small moving picture:
 * the sun wanders over the train, the windows on its side light up, and a seat waits on the
 * shady side. The developer option "Sonnenseite-Einführung zeigen" opens it again.
 */

/**
 * Opens the explanation for the hint row [owner] when it is due: the first time the row
 * shows a hint of the sunny side ([shown], on [side]). Closing it marks it seen for good.
 */
@Composable
fun SunSideIntroHost(owner: Any, shown: SunSide?) {
    val context = LocalContext.current
    SunSideSetting.load(context)
    val hintShown = shown != null
    if (hintShown && !SunSideSetting.introSeen && SunSideSetting.introOwner == null) {
        // Claimed after the composition, so the first row that shows a hint wins.
        androidx.compose.runtime.SideEffect { SunSideSetting.claimIntro(owner, hintShown = true) }
    }
    DisposableEffect(owner) { onDispose { SunSideSetting.releaseIntro(owner) } }
    if (SunSideSetting.introOwner === owner) {
        SunSideIntroSheet(side = shown ?: SunSide.Left, onDone = { SunSideSetting.finishIntro(context) })
    }
}

/** The sheet itself: the picture, what it means, the switch of the hint and "Verstanden". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SunSideIntroSheet(side: SunSide, onDone: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDone,
        sheetState = sheetState,
        containerColor = AppColors.PlainGround,
        contentColor = AppColors.Ink,
    ) {
        // A sheet is a window of its own: the picture of the map does not line up behind it.
        CompositionLocalProvider(LocalBackdrop provides null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = stringResource(R.string.sun_side_info_title),
                    color = AppColors.Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
                val description = stringResource(R.string.r5_sun_intro_picture)
                SunSidePicture(
                    sunLeft = side == SunSide.Left,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .semantics { contentDescription = description },
                )
                SunChipLegend(side)
                Text(
                    text = stringResource(R.string.r5_sun_intro_text),
                    color = AppColors.Ink,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                )
                val ticks = rememberTicks()
                // The whole row is the switch: one touch target, read as one switch.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = SunSideSetting.on, role = Role.Switch) { value ->
                            ticks.toggle(value)
                            SunSideSetting.update(context, value)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.sun_side_switch),
                        color = AppColors.Ink,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = SunSideSetting.on,
                        onCheckedChange = null,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    )
                }
                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Blue, contentColor = AppColors.OnBlue),
                ) {
                    Text(text = stringResource(R.string.hint_understood), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }
        }
    }
}

// The sun of the picture and its light.
private val SUN_YELLOW = Color(0xFFFFC233)

// One way of the sun over the train, in milliseconds (there and back again is twice as long).
private const val SUN_WANDER_MILLIS = 3_600

/**
 * The picture: a train seen from above on its track, heading right where the sun is on its
 * left ([sunLeft]), else left, so the sun above is always on its sunny side. The sun wanders
 * along its arc and back (resting in the middle when animations are off or the battery saver
 * is on); its rays fall on the windows of that side, which glow, and a seat on the other side
 * stands in the shade, in the colour of the app.
 */
@Composable
internal fun SunSidePicture(sunLeft: Boolean, modifier: Modifier = Modifier) {
    val moving = rememberIdleAnimationsOn()
    val phase = if (moving) {
        val transition = rememberInfiniteTransition(label = "sunIntro")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(SUN_WANDER_MILLIS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "sunPhase",
        )
        value
    } else {
        0.5f
    }
    val ground = AppColors.Chip
    val body = AppColors.Card
    val rim = AppColors.Muted
    val seat = AppColors.Blue
    Canvas(modifier = modifier) {
        drawRoundRect(ground, cornerRadius = CornerRadius(20.dp.toPx()))
        val w = size.width
        val h = size.height
        val (sx, sy) = sunIntroSunPoint(phase)
        val sun = Offset(w * sx, h * sy)
        val top = h * 0.50f
        val bottom = h * 0.80f
        val left = w * 0.12f
        val right = w * 0.88f
        // The track under the train.
        val track = h * 0.90f
        drawLine(
            rim.copy(alpha = 0.5f), Offset(w * 0.04f, track), Offset(w * 0.96f, track),
            strokeWidth = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx())),
        )
        // The rays onto the sunny side, then the shade along the other one.
        val windows = 5
        for (i in 0 until windows) {
            val x = left + (right - left) * (i + 0.5f) / windows
            drawLine(SUN_YELLOW.copy(alpha = 0.32f), sun, Offset(x, top), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
        drawRect(Color.Black.copy(alpha = 0.06f), topLeft = Offset(left, bottom), size = Size(right - left, h * 0.05f))
        // The body, its nose towards where it heads.
        val radius = CornerRadius((bottom - top) / 2f)
        drawRoundRect(body, topLeft = Offset(left, top), size = Size(right - left, bottom - top), cornerRadius = radius)
        drawRoundRect(rim, topLeft = Offset(left, top), size = Size(right - left, bottom - top), cornerRadius = radius, style = Stroke(1.5.dp.toPx()))
        drawHeading(if (sunLeft) right else left, (top + bottom) / 2f, if (sunLeft) 1f else -1f, rim)
        // The windows: those of the sunny side glow, the shady ones stay plain.
        val pane = Size((right - left) / windows * 0.56f, (bottom - top) * 0.18f)
        for (i in 0 until windows) {
            val cx = left + (right - left) * (i + 0.5f) / windows
            drawRoundRect(
                SUN_YELLOW, topLeft = Offset(cx - pane.width / 2f, top + (bottom - top) * 0.10f), size = pane,
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
            drawRoundRect(
                rim.copy(alpha = 0.25f), topLeft = Offset(cx - pane.width / 2f, bottom - (bottom - top) * 0.10f - pane.height), size = pane,
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
        }
        // The seat on the shady side, in the middle of the car.
        val seatWidth = (right - left) / windows * 0.62f
        val seatTop = top + (bottom - top) * 0.46f
        drawRoundRect(seat, topLeft = Offset(w / 2f - seatWidth / 2f, seatTop), size = Size(seatWidth, (bottom - top) * 0.30f), cornerRadius = CornerRadius(4.dp.toPx()))
        drawRoundRect(seat, topLeft = Offset(w / 2f - seatWidth / 2f, bottom - (bottom - top) * 0.16f), size = Size(seatWidth, (bottom - top) * 0.08f), cornerRadius = CornerRadius(2.dp.toPx()))
        // The sun with its glow.
        val r = h * 0.075f
        drawCircle(SUN_YELLOW.copy(alpha = 0.22f), radius = r * 1.9f, center = sun)
        drawCircle(SUN_YELLOW, radius = r, center = sun)
    }
}

// A small chevron at the nose of the train, pointing where it heads ([direction] 1 right, -1 left).
private fun DrawScope.drawHeading(noseX: Float, y: Float, direction: Float, color: Color) {
    val tip = noseX + direction * size.width * 0.05f
    val back = noseX + direction * size.width * 0.02f
    val spread = size.height * 0.06f
    val stroke = 2.dp.toPx()
    drawLine(color, Offset(back, y - spread), Offset(tip, y), strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(color, Offset(back, y + spread), Offset(tip, y), strokeWidth = stroke, cap = StrokeCap.Round)
}
