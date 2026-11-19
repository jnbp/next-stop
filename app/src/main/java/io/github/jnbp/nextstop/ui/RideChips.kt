package io.github.jnbp.nextstop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.HintChipArrangement
import io.github.jnbp.nextstop.data.Leg
import io.github.jnbp.nextstop.data.RideFeature
import io.github.jnbp.nextstop.data.SunChipPage
import io.github.jnbp.nextstop.data.SunSide
import io.github.jnbp.nextstop.data.hintChipArrangement
import io.github.jnbp.nextstop.data.shade
import io.github.jnbp.nextstop.data.sunChipProminent
import io.github.jnbp.nextstop.data.sunChipRideKey
import io.github.jnbp.nextstop.ui.theme.AppColors
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius

/*
 * The hints of a ride in one row (#220, "Option F"), for the new connection detail and the
 * Live-Fahrt companion (the classic views keep RideHintsRow): the boarding hint as the main
 * chip (the drawn train and its short sentence, only as wide as it needs, wrapping, never cut),
 * and small chips at the end of the row: occupancy, step-free, bicycles and the sunny side.
 * The small chips always end at the right edge, also without a boarding hint (#236). The hint
 * keeps at least two thirds of the row and wraps there (#264): the chips stand side by side in
 * the rest, else one above the other in the right quarter, else on a line of their own below. All of them say what RideHintsRow says; the sun chip and the
 * bicycle chip explain themselves on a tap.
 */

/**
 * The form of the sun chips of the page around (#236, see SunChipPage); null outside a page
 * that provides one (each ride decides for itself then).
 */
val LocalSunChipPage = staticCompositionLocalOf<SunChipPage?> { null }

/**
 * The compact hints of [leg]. [main] draws the boarding hint (or the way out) with the modifier
 * it gets, or nothing; [modifier] places the whole row (and [main] alone, where no small chip
 * shows, so nothing changes there).
 */
@Composable
fun CompactRideHints(leg: Leg, modifier: Modifier = Modifier, main: @Composable (Modifier) -> Unit) {
    val context = LocalContext.current
    val sun = rememberShownSunSide(leg)
    // The side last shown, so the chip keeps its arrow while it fades out.
    val lastSun = remember { arrayOfNulls<SunSide>(1) }
    if (sun != null) lastSun[0] = sun
    val sunText = sun ?: lastSun[0]
    // The first hint of the sunny side opens its explanation, once (see SunSideIntroHost).
    val introOwner = remember { Any() }
    SunSideIntroHost(owner = introOwner, shown = sun)
    val rideKey = sunChipRideKey(leg.tripId, leg.from, leg.to, leg.departure.toEpochSecond())
    // Large or small is decided when the chip first shows on this ride, so it never shrinks in
    // view, and once per page where the page says (#236): one page never mixes both forms.
    val page = LocalSunChipPage.current
    val prominent = remember(rideKey) { arrayOfNulls<Boolean>(1) }
    if (sun != null && prominent[0] == null) {
        prominent[0] = page?.prominent(SunSideSetting.chipRides) ?: sunChipProminent(SunSideSetting.chipRides)
    }
    LaunchedEffect(rideKey, sun != null) {
        if (sun != null) SunSideSetting.countChipRide(context, rideKey)
    }
    var sunInfo by remember { mutableStateOf(false) }
    var bikeInfo by remember { mutableStateOf(false) }
    if (sunInfo) SunSideInfoDialog(side = sunText ?: SunSide.Left, onDismiss = { sunInfo = false })
    if (bikeInfo) BikeInfoDialog(note = leg.bikeNote, onDismiss = { bikeInfo = false })
    val occupancy = leg.occupancy
    val bike = RideFeature.BikeCarriage in leg.features
    val stepFree = RideFeature.StepFree in leg.features
    if (occupancy == null && !bike && !stepFree && sunText == null) {
        // No small chip: the boarding hint alone, exactly as before.
        main(modifier)
        return
    }
    val animate = rememberAnimationsOn()
    Layout(
        modifier = if (animate) modifier.animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)) else modifier,
        content = {
            // The hint wraps rather than being cut (#236): its line here is as long as it needs.
            Box { CompositionLocalProvider(LocalHintRowMaxLines provides Int.MAX_VALUE) { main(Modifier) } }
            // Each small chip is a child of its own (#264): side by side, stacked or below, see hintChipArrangement.
            if (occupancy != null) {
                MiniChip {
                    OccupancyGauge(occupancy)
                    Text(text = occupancyLabel(occupancy), color = AppColors.Muted, fontSize = 12.sp, lineHeight = 14.sp, maxLines = 1)
                }
            }
            if (stepFree) {
                val label = stringResource(R.string.ride_step_free)
                MiniChip(modifier = Modifier.clearAndSetSemantics { contentDescription = label }) {
                    StepFreeMark(size = 14.dp, color = AppColors.Muted)
                }
            }
            if (bike) BikeChip(onClick = { bikeInfo = true })
            if (sunText != null) {
                if (animate) {
                    AnimatedVisibility(
                        visible = sun != null,
                        enter = fadeIn(tween(220)) + expandHorizontally(Motion.settle()),
                        exit = fadeOut(tween(140)) + shrinkHorizontally(Motion.settle()),
                    ) { SunChip(sunText, large = prominent[0] == true, test = SunSideSetting.test, onTap = { sunInfo = true }) }
                } else if (sun != null) {
                    SunChip(sunText, large = prominent[0] == true, test = SunSideSetting.test, onTap = { sunInfo = true })
                }
            }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val gap = 8.dp.roundToPx()
        val chipGap = 6.dp.roundToPx()
        val stackGap = 4.dp.roundToPx()
        // The width the hint needs on one line; it wraps in what the chips leave (#247, #264).
        val mainWide = measurables[0].maxIntrinsicWidth(loose.maxHeight.coerceAtMost(10_000))
        val chips = measurables.drop(1).map { it.measure(loose) }
        val shown = chips.filter { it.width > 0 }
        val bounded = constraints.hasBoundedWidth
        val arrangement = if (bounded) {
            // The larger gap (hint to chips) for the rule, so a hint it lets stand in full never wraps.
            hintChipArrangement(mainWide, chips.map { it.width }, gap, loose.maxWidth)
        } else {
            HintChipArrangement.Row
        }
        // The chips as a block: side by side (Row, Below) or one above the other (Stacked).
        val stacked = arrangement == HintChipArrangement.Stacked
        val blockWidth = when {
            shown.isEmpty() -> 0
            stacked -> shown.maxOf { it.width }
            else -> shown.sumOf { it.width } + chipGap * (shown.size - 1)
        }
        val blockHeight = when {
            shown.isEmpty() -> chips.maxOfOrNull { it.height } ?: 0
            stacked -> shown.sumOf { it.height } + stackGap * (shown.size - 1)
            else -> shown.maxOf { it.height }
        }
        // Places the chips with the right edge of the block at [right] and its top at [top].
        fun Placeable.PlacementScope.placeChips(right: Int, top: Int) {
            if (stacked) {
                var y = top
                shown.forEach { chip ->
                    chip.place(right - chip.width, y)
                    y += chip.height + stackGap
                }
            } else {
                var x = right - blockWidth
                chips.forEach { chip ->
                    chip.place(x, top + (blockHeight - chip.height) / 2)
                    if (chip.width > 0) x += chip.width + chipGap
                }
            }
        }
        if (arrangement != HintChipArrangement.Below) {
            val room = if (blockWidth > 0 && mainWide > 0) blockWidth + gap else blockWidth
            val mainMax = if (bounded) (loose.maxWidth - room).coerceAtLeast(0) else loose.maxWidth
            // The hint takes the rest of the row and wraps there, never cut.
            val main = measurables[0].measure(loose.copy(maxWidth = mainMax))
            val width = if (bounded) loose.maxWidth else main.width + room
            val height = maxOf(main.height, blockHeight)
            layout(width, height) {
                main.place(0, (height - main.height) / 2)
                // The small chips end at the right edge, with or without a hint (#236).
                placeChips(width, (height - blockHeight) / 2)
            }
        } else {
            // Not even one chip fits in the right quarter: the hint whole on its line, the chips below at the right.
            val main = measurables[0].measure(loose)
            val below = 4.dp.roundToPx()
            layout(loose.maxWidth, main.height + below + blockHeight) {
                main.place(0, 0)
                placeChips(loose.maxWidth, main.height + below)
            }
        }
    }
}

// The height of every small chip at the normal font size.
private val MINI_CHIP_HEIGHT = 24.dp

/** The quiet ground of a small chip. */
@Composable
private fun MiniChip(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, onClickLabel: String? = null, content: @Composable () -> Unit) {
    // One height for every small chip (#236): a 15 dp bicycle and a line of text alike, growing with the font size.
    val height = with(LocalDensity.current) { maxOf(MINI_CHIP_HEIGHT, 14.sp.toDp() + 8.dp) }
    Row(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(10.dp))
            .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick) else Modifier)
            .background(AppColors.Muted.copy(alpha = 0.10f))
            .padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) { content() }
}

/**
 * The sun chip: a small sun and an arrow to the side the sun shines in on, seen in the direction
 * of travel; [large] (on the first rides it shows on) also says "Sonne links" in words. Read out
 * as "Sonne links in Fahrtrichtung, Schatten rechts"; a tap explains it. With the developer
 * test on ([test], a side on every ride, also at night) it says "Test", so a forced hint is
 * never taken for a real one (#241).
 */
@Composable
private fun SunChip(side: SunSide, large: Boolean, test: Boolean, onTap: () -> Unit) {
    val sideName = sunSideName(side)
    val plainDescription = stringResource(R.string.r7card_sun_chip_a11y, sideName, sunSideName(side.shade))
    val testDescription = stringResource(R.string.r8fix_sun_chip_test_a11y)
    val description = if (test) "$plainDescription, $testDescription" else plainDescription
    val clickLabel = stringResource(R.string.sun_side_info_open)
    MiniChip(
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = description
            role = Role.Button
            onClick(label = clickLabel) { onTap(); true }
        },
        onClick = onTap,
        onClickLabel = clickLabel,
    ) {
        // The car seen from above with the sun on its side (#269).
        SunWagonMark(left = side == SunSide.Left, height = if (large) 16.dp else 14.dp, ink = AppColors.Muted)
        if (large) {
            Text(
                text = stringResource(R.string.r7card_sun_chip, sideName),
                color = AppColors.Ink,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                maxLines = 1,
            )
        }
        if (test) {
            Text(
                text = stringResource(R.string.r8fix_sun_chip_test),
                color = AppColors.Muted,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 1,
            )
        }
    }
}

/**
 * The sunny side as a small picture (#269): a rail car seen from above, heading up, the sun
 * beside it shining onto its [left] or right side, and a soft shadow on the other side.
 */
@Composable
internal fun SunWagonMark(left: Boolean, height: Dp, ink: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = height * 22f / 16f, height = height)) {
        val u = this.size.height / 16f
        val sun = Color(0xFFF2A900)
        // Drawn for the left side; the right side is its mirror image.
        withTransform({ if (!left) scale(-1f, 1f, pivot = center) }) {
            drawCircle(sun, radius = 2.4f * u, center = Offset(4f * u, 8f * u))
            drawLine(sun, Offset(8.5f * u, 3.5f * u), Offset(6f * u, 5f * u), strokeWidth = 1.2f * u, cap = StrokeCap.Round)
            drawLine(sun, Offset(8.5f * u, 12.5f * u), Offset(6f * u, 11f * u), strokeWidth = 1.2f * u, cap = StrokeCap.Round)
            drawRoundRect(
                ink,
                topLeft = Offset(9f * u, 1.5f * u),
                size = Size(8f * u, 13f * u),
                cornerRadius = CornerRadius(3f * u),
                style = Stroke(width = 1.2f * u),
            )
            val shade = Path().apply {
                moveTo(17.6f * u, 3.5f * u)
                lineTo(20.6f * u, 4.5f * u)
                lineTo(20.6f * u, 12.5f * u)
                lineTo(17.6f * u, 13.5f * u)
                close()
            }
            drawPath(shade, ink.copy(alpha = 0.18f))
        }
    }
}

/** The bicycle chip: bicycles may be taken along; a tap says what that means. */
@Composable
private fun BikeChip(onClick: () -> Unit) {
    val label = stringResource(R.string.ride_bike_carriage)
    val clickLabel = stringResource(R.string.r7card_bike_info_open)
    MiniChip(
        modifier = Modifier.semantics { contentDescription = label },
        onClick = onClick,
        onClickLabel = clickLabel,
    ) {
        GlyphIcon(Glyph.Bike, color = AppColors.Muted, size = 15.dp)
    }
}

/**
 * What the bicycle chip means; above it the provider's own words about bicycles on this ride
 * ([note], e.g. "Fahrradmitnahme begrenzt möglich", #248) where it sends some.
 */
@Composable
private fun BikeInfoDialog(note: String?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.ride_bike_carriage)) },
        text = {
            if (note == null) {
                Text(text = stringResource(R.string.r7card_bike_info))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = stringResource(R.string.r8live_bike_provider_note), color = AppColors.Muted, fontSize = 13.sp)
                        Text(text = note, fontWeight = FontWeight.SemiBold)
                    }
                    Text(text = stringResource(R.string.r7card_bike_info))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.hint_understood)) }
        },
    )
}
