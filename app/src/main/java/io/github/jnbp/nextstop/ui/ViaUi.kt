package io.github.jnbp.nextstop.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.MAX_VIA_MINUTES
import io.github.jnbp.nextstop.data.Via
import io.github.jnbp.nextstop.data.plainStopName
import io.github.jnbp.nextstop.ui.theme.AppColors

/** A place on the way in words: "via X", or "via X, 10 min" with a stay. */
@Composable
fun viaLabel(via: Via): String {
    val name = via.place.name.plainStopName()
    return if (via.minutes > 0) stringResource(R.string.via_label_stay, name, via.minutes) else stringResource(R.string.via_label, name)
}

/**
 * Between start and destination of a search: a quiet "Über …" with the way-with-a-stop icon to
 * add a place on the way, or the place on the way with its stay, which a tap changes, and an x
 * that takes it away.
 */
@Composable
fun ViaLine(via: Via?, onAdd: () -> Unit, onEdit: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    if (via == null) {
        val description = stringResource(R.string.via_add_description)
        Row(
            modifier = modifier
                .clearAndSetSemantics {
                    contentDescription = description
                    role = Role.Button
                }
                .clip(CircleShape)
                .clickable(onClick = onAdd)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            GlyphIcon(Glyph.Via, color = AppColors.Blue, size = 15.dp)
            Text(
                text = stringResource(R.string.via_chip),
                color = AppColors.Blue,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                softWrap = false,
            )
        }
        return
    }
    val removeLabel = stringResource(R.string.via_remove)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = viaLabel(via),
            color = AppColors.Ink,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .clickable(onClick = onEdit)
                .padding(horizontal = 4.dp, vertical = 4.dp),
        )
        Box(
            modifier = Modifier
                .semantics { contentDescription = removeLabel }
                .clip(CircleShape)
                .clickable(onClick = onRemove)
                .padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(Glyph.Close, color = AppColors.Muted, size = 16.dp)
        }
    }
}

/**
 * The control that adds a place on the way, for headers where start and destination are set
 * already: a compact chip with the way-with-a-stop icon and the word for it ("Über …"), so it
 * says what it does instead of leaving an icon to guess. It looks like the time chip next to it
 * ("Jetzt", see [ChoiceChip]): the same glass, height, text and press spring.
 */
@Composable
fun ViaButton(onAdd: () -> Unit, modifier: Modifier = Modifier, solid: Boolean = false, minHeight: Dp = 36.dp) {
    val description = stringResource(R.string.via_add_description)
    val shape = RoundedCornerShape(24.dp)
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "viaChipPress",
    )
    Row(
        modifier = modifier
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .then(if (solid) Modifier.background(AppColors.Card) else glassGround())
            .border(1.dp, glassRim(), shape)
            .clickable(interactionSource = interactions, indication = LocalIndication.current, onClick = onAdd)
            .heightIn(min = minHeight)
            .padding(start = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        GlyphIcon(Glyph.Via, size = 16.dp)
        Text(
            text = stringResource(R.string.via_chip),
            color = AppColors.Ink,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** The next minutes of the stepper: single minutes up to ten, then steps of five. */
internal fun viaStep(minutes: Int, up: Boolean): Int = when {
    up && minutes < 10 -> minutes + 1
    up -> (minutes / 5 * 5 + 5).coerceAtMost(MAX_VIA_MINUTES)
    minutes <= 10 -> (minutes - 1).coerceAtLeast(0)
    else -> (minutes + 4) / 5 * 5 - 5
}

/**
 * How long to stay at the place on the way: 0 only passes through it, from 1 minute on the
 * traveller gets off there and goes on after the stay.
 */
@Composable
fun ViaStayDialog(via: Via, onApply: (Via) -> Unit, onDismiss: () -> Unit) {
    var minutes by remember(via) { mutableIntStateOf(via.minutes.coerceIn(0, MAX_VIA_MINUTES)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.via_stay_title, via.place.name.plainStopName())) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(text = stringResource(R.string.via_stay_text))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    RoundButton(
                        glyph = Glyph.Minus,
                        description = stringResource(R.string.via_less),
                        onClick = { minutes = viaStep(minutes, up = false) },
                    )
                    Text(
                        text = if (minutes == 0) stringResource(R.string.via_pass) else stringResource(R.string.via_minutes, minutes),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(min = 96.dp),
                    )
                    RoundButton(
                        glyph = Glyph.Plus,
                        description = stringResource(R.string.via_more),
                        onClick = { minutes = viaStep(minutes, up = true) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(via.copy(minutes = minutes)) }) { Text(text = stringResource(R.string.via_apply)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.cancel)) }
        },
    )
}
