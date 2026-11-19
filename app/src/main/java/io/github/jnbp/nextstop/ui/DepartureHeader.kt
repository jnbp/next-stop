package io.github.jnbp.nextstop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.ModeIcon
import io.github.jnbp.nextstop.data.departureModeFace
import io.github.jnbp.nextstop.ui.theme.AppColors

/*
 * The parts of the header of the departures in two rows (#223): chips that open a choice
 * ("Jetzt ▾", "500 m ▾"), the mode filters ("Alle · S · U · <bus>"), a thin divider between
 * the groups, and the small chip of the disruptions beside the order toggle.
 */

// The height of the chips of the header: a little lower than a ChoiceChip, so two rows fit.
private val HEADER_CHIP_HEIGHT = 40.dp

/** A chip of the header that opens a choice: its [text] and a chevron, turned while [open]. */
@Composable
fun HeaderDropChip(
    text: String,
    open: Boolean,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val shape = RoundedCornerShape(24.dp)
    val interactions = remember { MutableInteractionSource() }
    val ticks = rememberTicks()
    Row(
        modifier = modifier
            .semantics { if (description != null) contentDescription = description }
            .springPress(interactions)
            .clip(shape)
            .then(if (active) Modifier.background(AppColors.Card) else glassGround())
            .border(1.dp, glassRim(), shape)
            .clickable(interactionSource = interactions, indication = LocalIndication.current) {
                ticks.tick()
                onClick()
            }
            .defaultMinSize(minHeight = HEADER_CHIP_HEIGHT)
            .padding(start = 14.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = text,
            color = AppColors.Ink,
            fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Chevron(open = open, color = AppColors.Ink)
    }
}

/** The thin line between two groups of chips in the header. */
@Composable
fun HeaderDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(horizontal = 2.dp)
            .width(1.dp)
            .height(24.dp)
            .background(AppColors.Muted.copy(alpha = 0.35f)),
    )
}

/**
 * A mode filter of the header: "Alle" for [product] null, else the letter or the icon of the
 * kind (see departureModeFace), read out by its name. The picked one is blue.
 */
@Composable
fun ModeFilterChip(product: String?, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    val interactions = remember { MutableInteractionSource() }
    val ticks = rememberTicks()
    val ground by animateColorAsState(
        if (active) AppColors.Blue else Color.Transparent,
        if (rememberAnimationsOn()) spring(stiffness = Spring.StiffnessMediumLow) else snap(),
        label = "modeChip",
    )
    val ink = if (active) AppColors.OnBlue else AppColors.Ink
    val name = if (product == null) stringResource(R.string.all_lines) else stringResource(productLabel(product))
    Row(
        modifier = modifier
            .semantics {
                contentDescription = name
                selected = active
            }
            .springPress(interactions)
            .clip(shape)
            .then(if (active) Modifier else glassGround())
            .background(ground)
            .border(1.dp, if (active) AppColors.Blue else glassRim(), shape)
            .clickable(interactionSource = interactions, indication = LocalIndication.current) {
                ticks.tick()
                onClick()
            }
            .defaultMinSize(minWidth = HEADER_CHIP_HEIGHT, minHeight = HEADER_CHIP_HEIGHT)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        val face = product?.let { departureModeFace(it) }
        val icon = face?.icon
        when {
            face == null -> ModeChipText(name, ink, active)
            icon != null -> {
                ModeIconMark(icon, ink, 16.dp)
                when {
                    face.regional -> ModeChipText("RE", ink, active, small = true)
                    face.longDistance -> ModeChipText(stringResource(R.string.r7search_mode_long_distance), ink, active, small = true)
                }
            }
            else -> ModeChipText(face.letter.orEmpty(), ink, active)
        }
    }
}

@Composable
private fun ModeChipText(text: String, color: Color, active: Boolean, small: Boolean = false) {
    Text(
        text = text,
        color = color,
        fontWeight = if (active || !small) FontWeight.Bold else FontWeight.SemiBold,
        fontSize = if (small) 12.sp else 14.sp,
        maxLines = 1,
    )
}

/**
 * The small icon of a kind of transport: bus and train as on the line badges (see ModeMark),
 * a tram as a car under its pantograph, a ferry as a hull with its cabin above a wave.
 */
@Composable
fun ModeIconMark(icon: ModeIcon, color: Color, size: Dp, modifier: Modifier = Modifier) {
    when (icon) {
        ModeIcon.Bus -> ModeMark(TravelMode.Bus, color, size, modifier)
        ModeIcon.Train -> ModeMark(TravelMode.Train, color, size, modifier)
        ModeIcon.Tram, ModeIcon.Ferry -> Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val line = w * 0.11f
            val stroke = Stroke(width = line, cap = StrokeCap.Round, join = StrokeJoin.Round)
            if (icon == ModeIcon.Tram) {
                // The pantograph: a V on a short post, then the car with its window band and wheels.
                drawLine(color, Offset(w * 0.32f, w * 0.04f), Offset(w * 0.5f, w * 0.2f), strokeWidth = line, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.68f, w * 0.04f), Offset(w * 0.5f, w * 0.2f), strokeWidth = line, cap = StrokeCap.Round)
                drawRoundRect(color, Offset(w * 0.16f, w * 0.24f), Size(w * 0.68f, w * 0.56f), CornerRadius(w * 0.14f), style = stroke)
                drawLine(color, Offset(w * 0.16f, w * 0.48f), Offset(w * 0.84f, w * 0.48f), strokeWidth = line)
                drawCircle(color, radius = w * 0.08f, center = Offset(w * 0.33f, w * 0.9f))
                drawCircle(color, radius = w * 0.08f, center = Offset(w * 0.67f, w * 0.9f))
            } else {
                // The cabin, the hull below it, and a wave under the hull.
                drawRoundRect(color, Offset(w * 0.34f, w * 0.12f), Size(w * 0.32f, w * 0.26f), CornerRadius(w * 0.06f), style = stroke)
                val hull = Path().apply {
                    moveTo(w * 0.08f, w * 0.44f)
                    lineTo(w * 0.92f, w * 0.44f)
                    lineTo(w * 0.78f, w * 0.68f)
                    lineTo(w * 0.22f, w * 0.68f)
                    close()
                }
                drawPath(hull, color, style = stroke)
                val wave = Path().apply {
                    moveTo(w * 0.06f, w * 0.88f)
                    quadraticBezierTo(w * 0.2f, w * 0.78f, w * 0.34f, w * 0.88f)
                    quadraticBezierTo(w * 0.48f, w * 0.98f, w * 0.62f, w * 0.88f)
                    quadraticBezierTo(w * 0.76f, w * 0.78f, w * 0.94f, w * 0.88f)
                }
                drawPath(wave, color, style = stroke)
            }
        }
    }
}

/** The small chip of the disruptions at the stop beside the order toggle ("1 Störung"); a tap shows them. */
@Composable
fun DisruptionCountChip(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = pluralStringResource(R.plurals.ways_pill_disruptions, count, count)
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .then(glassGround())
            .border(1.dp, glassRim(), shape)
            .clickable(onClickLabel = label, onClick = onClick)
            .defaultMinSize(minHeight = HEADER_CHIP_HEIGHT)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        WarningMark(size = 14.dp)
        Text(text = label, color = AppColors.Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
    }
}
