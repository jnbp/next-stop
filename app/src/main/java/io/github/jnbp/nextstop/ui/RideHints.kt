package io.github.jnbp.nextstop.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.Leg
import io.github.jnbp.nextstop.data.Occupancy
import io.github.jnbp.nextstop.data.RideFeature
import io.github.jnbp.nextstop.data.RailTunnels
import io.github.jnbp.nextstop.data.SunSide
import io.github.jnbp.nextstop.data.undergroundTest
import io.github.jnbp.nextstop.data.sunWaitsForTunnels
import io.github.jnbp.nextstop.data.shade
import io.github.jnbp.nextstop.data.sunSide
import io.github.jnbp.nextstop.ui.theme.AppColors
import kotlin.math.cos
import kotlin.math.sin

/*
 * Small hints about a ride, shown under its line in the connection detail and the companion:
 * how full it is expected to be (a gauge of three rounded segments), bicycles allowed and
 * step-free access (where the provider says so), and on which side the sun shines in on a
 * longer ride in daylight. Each shows only where it is known; with nothing known, nothing.
 */

/** The name of [occupancy], for the gauge's description. */
@Composable
fun occupancyLabel(occupancy: Occupancy): String = stringResource(
    when (occupancy) {
        Occupancy.Low -> R.string.occupancy_low
        Occupancy.Medium -> R.string.occupancy_medium
        Occupancy.High -> R.string.occupancy_high
        Occupancy.Full -> R.string.occupancy_full
    },
)

/** The colour of the filled segments: calm green, amber, orange-red, red for a full one. */
private fun occupancyColor(occupancy: Occupancy): Color = when (occupancy) {
    Occupancy.Low -> AppColors.Early
    Occupancy.Medium -> AppColors.Tight
    Occupancy.High -> AppColors.VeryRisky
    Occupancy.Full -> AppColors.DelayRed
}

/**
 * How full a ride is expected to be: three small rounded bars rising in height, as many filled
 * as [occupancy] says, the others a faint grey. Read out as its name.
 */
@Composable
fun OccupancyGauge(occupancy: Occupancy, modifier: Modifier = Modifier, height: Dp = 11.dp) {
    val label = occupancyLabel(occupancy)
    val filled = occupancyColor(occupancy)
    val empty = AppColors.Muted.copy(alpha = 0.28f)
    Canvas(
        modifier = modifier
            .size(width = height * 1.45f, height = height)
            .semantics { contentDescription = label },
    ) {
        val gap = size.width * 0.12f
        val bar = (size.width - gap * 2) / 3f
        val corner = CornerRadius(bar / 2f, bar / 2f)
        for (index in 0 until 3) {
            // Rising bars, like the bars of a signal: a third, two thirds, the whole height.
            val barHeight = size.height * (0.45f + 0.275f * index)
            drawRoundRect(
                color = if (index < occupancy.segments) filled else empty,
                topLeft = Offset(index * (bar + gap), size.height - barHeight),
                size = Size(bar, barHeight),
                cornerRadius = corner,
            )
        }
    }
}

/**
 * The side the sun shines in on [leg]; null for walks, cancelled and short rides, at night or
 * without a clear side, and for rides mostly underground (#219: only the parts above ground
 * count, by the rail tunnels of OpenStreetMap, see RailTunnels). The phone's hint and the one
 * it sends to the watch alike.
 */
fun legSunSide(leg: Leg): SunSide? {
    if (leg.walking || leg.cancelled) return null
    val way = legWay(leg)
    // The tunnels are read off the main thread (see RailTunnelAsset): until then no hint on rails in Germany.
    if (sunWaitsForTunnels(leg.product, way, RailTunnels.read)) return null
    return sunSide(
        way,
        leg.departure.toInstant().toEpochMilli(),
        leg.arrival.toInstant().toEpochMilli(),
        undergroundTest(leg.product, RailTunnels.grid),
    )
}

/**
 * "Sun mostly on the left – shady side: right", with a small sun before it and a small "i"
 * after it; a tap ([onClick]) explains it and lets it be switched off (see SunSideInfoDialog).
 */
@Composable
fun SunSideHint(side: SunSide, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val sideName = sunSideName(side)
    val shadeName = sunSideName(side.shade)
    val clickLabel = stringResource(R.string.sun_side_info_open)
    Row(
        modifier = modifier.then(
            if (onClick == null) {
                Modifier
            } else {
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick)
            },
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        SunMark(size = 13.dp)
        Text(
            text = stringResource(R.string.sun_side_hint, sideName, shadeName),
            color = AppColors.Muted,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (onClick != null) {
            Text(text = "\u24D8", color = AppColors.Muted, fontSize = 12.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
internal fun sunSideName(side: SunSide): String =
    stringResource(if (side == SunSide.Left) R.string.sun_side_left else R.string.sun_side_right)

// The amber of the sun in the hint.
private val SUN_COLOR = Color(0xFFF2A900)

/** A small sun: a disc with eight short rays. */
@Composable
internal fun SunMark(size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val middle = center
        val radius = this.size.minDimension * 0.22f
        drawCircle(SUN_COLOR, radius = radius, center = middle)
        val inner = radius * 1.55f
        val outer = this.size.minDimension * 0.48f
        val stroke = this.size.minDimension * 0.09f
        for (ray in 0 until 8) {
            val angle = ray * (Math.PI / 4)
            val dx = cos(angle).toFloat()
            val dy = sin(angle).toFloat()
            drawLine(
                SUN_COLOR,
                Offset(middle.x + dx * inner, middle.y + dy * inner),
                Offset(middle.x + dx * outer, middle.y + dy * outer),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Step-free access: a ramp, a slope rising to a level. */
@Composable
internal fun StepFreeMark(size: Dp, color: Color) {
    Canvas(modifier = Modifier.size(size)) {
        val stroke = this.size.minDimension * 0.12f
        val w = this.size.width
        val h = this.size.height
        drawLine(color, Offset(w * 0.08f, h * 0.82f), Offset(w * 0.62f, h * 0.32f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.62f, h * 0.32f), Offset(w * 0.92f, h * 0.32f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.08f, h * 0.82f), Offset(w * 0.92f, h * 0.82f), strokeWidth = stroke * 0.7f, cap = StrokeCap.Round)
        drawCircle(color, radius = stroke * 1.1f, center = Offset(w * 0.32f, h * 0.22f), style = Stroke(width = stroke * 0.8f))
    }
}

/**
 * The hints of a ride in one quiet line: occupancy, bicycles, step-free, and the sun side
 * below where it is clear and the sun really shines (see rememberShownSunSide); the sun side
 * comes and goes softly, and a tap on it explains it. Nothing at all where nothing is known.
 */
@Composable
fun RideHintsRow(leg: Leg, modifier: Modifier = Modifier) {
    val sun = rememberShownSunSide(leg)
    // The side last shown, so the hint keeps its text while it fades out.
    val lastSun = remember { arrayOfNulls<SunSide>(1) }
    if (sun != null) lastSun[0] = sun
    val sunText = sun ?: lastSun[0]
    var sunInfo by remember { mutableStateOf(false) }
    // The first hint of the sunny side opens its explanation, once (see SunSideIntroHost).
    val introOwner = remember { Any() }
    SunSideIntroHost(owner = introOwner, shown = sun)
    val occupancy = leg.occupancy
    val bike = RideFeature.BikeCarriage in leg.features
    val stepFree = RideFeature.StepFree in leg.features
    val others = occupancy != null || bike || stepFree
    if (sunInfo) SunSideInfoDialog(side = sunText ?: SunSide.Left, onDismiss = { sunInfo = false })
    if (!others) {
        // The sun alone: nothing at all (no gap in the parent's spacing) until it shows.
        if (sunText != null) {
            SoftAppear(visible = sun != null, modifier = modifier) { SunSideHint(sunText, onClick = { sunInfo = true }) }
        }
        return
    }
    androidx.compose.foundation.layout.Column(
        modifier = modifier.animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (occupancy != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    OccupancyGauge(occupancy)
                    Text(text = occupancyLabel(occupancy), color = AppColors.Muted, fontSize = 12.sp, maxLines = 1)
                }
            }
            if (bike) {
                val label = stringResource(R.string.ride_bike_carriage)
                GlyphIcon(Glyph.Bike, color = AppColors.Muted, size = 15.dp, modifier = Modifier.semantics { contentDescription = label })
            }
            if (stepFree) {
                val label = stringResource(R.string.ride_step_free)
                Row(modifier = Modifier.semantics { contentDescription = label }) { StepFreeMark(size = 14.dp, color = AppColors.Muted) }
            }
        }
        if (sunText != null) {
            SoftAppear(visible = sun != null) { SunSideHint(sunText, onClick = { sunInfo = true }) }
        }
    }
}
