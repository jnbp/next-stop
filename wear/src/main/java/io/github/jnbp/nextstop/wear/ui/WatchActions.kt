package io.github.jnbp.nextstop.wear.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.wear.compose.foundation.ScrollInfoProvider
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.LocalContentColor
import androidx.wear.compose.material3.LocalTextStyle
import androidx.wear.compose.material3.Text

/*
 * The action buttons of the watch in Wear OS Material 3 (#145): full-width rounded buttons with
 * an icon and a label of up to two lines, broken only between words (filled in the brand blue for the one main action of a screen, brand tonal for
 * the others), and the main action of a list screen as the EdgeButton hugging the bottom of
 * the round screen. Lists inset their items for the round screen, so full width there is the
 * right width for the item's position.
 */

/**
 * An action of a screen: a full-width M3 [Button] with [icon] and [text], filled when
 * [primary], else tonal. [widthFraction] narrows it where it sits low in a column that is not
 * inset for the round screen (the foot of a trip page).
 */
@Composable
fun WatchAction(
    text: String,
    onClick: () -> Unit,
    icon: Int? = null,
    primary: Boolean = false,
    modifier: Modifier = Modifier,
    widthFraction: Float = 1f,
) {
    // Gives way a little under the finger and springs back, as the buttons of the phone do.
    val press = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(widthFraction).watchSpringPress(press),
        interactionSource = press,
        colors = if (primary) brandButtonColors() else brandTonalButtonColors(),
        icon = if (icon != null) {
            { Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize)) }
        } else {
            null
        },
        label = { WordSafeLabel(text) },
    )
}

/**
 * The label of a [WatchAction]: up to two lines, broken only between words (#179). Where its
 * widest word does not fit the width the button leaves (a large font scale, a button low on
 * the round screen), the label shrinks until it does ([wordFitScale]); where even the smallest
 * size is too wide it stays on one line and ends in an ellipsis rather than in half a word.
 * Laid out and drawn by hand (no subcomposition), so the pages that measure their lines
 * before placing them (FitColumn, #180) can ask for its height.
 */
@Composable
internal fun WordSafeLabel(text: String) {
    val style = LocalTextStyle.current.merge(TextStyle(color = LocalContentColor.current))
    val measurer = rememberTextMeasurer()
    val laid = remember { LaidLabel() }
    Spacer(
        Modifier
            .semantics { this.text = AnnotatedString(text) }
            .layout { measurable, constraints ->
                val result = measureWordSafe(measurer, text, style, constraints)
                laid.result = result
                val width = constraints.constrainWidth(result.size.width)
                val height = constraints.constrainHeight(result.size.height)
                val placeable = measurable.measure(Constraints.fixed(width, height))
                layout(width, height) { placeable.place(0, 0) }
            }
            .drawBehind { laid.result?.let { drawText(it) } },
    )
}

// The text of a WordSafeLabel as last laid out, for drawing it.
private class LaidLabel {
    var result: TextLayoutResult? = null
}

/** [text] in [style] laid out within [constraints] as [WordSafeLabel] shows it. */
private fun measureWordSafe(measurer: TextMeasurer, text: String, style: TextStyle, constraints: Constraints): TextLayoutResult {
    val available = if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else 0f
    val widest = labelWords(text).maxOfOrNull { word -> measurer.measure(word, style, softWrap = false, maxLines = 1).size.width.toFloat() } ?: 0f
    val scale = wordFitScale(widest, available)
    val factor = scale ?: MIN_LABEL_SCALE
    val sized = if (factor < 1f && style.fontSize.isSpecified) {
        style.copy(
            fontSize = style.fontSize * factor,
            lineHeight = if (style.lineHeight.isSpecified) style.lineHeight * factor else style.lineHeight,
        )
    } else {
        style
    }
    return measurer.measure(
        text,
        sized,
        overflow = TextOverflow.Ellipsis,
        softWrap = scale != null,
        maxLines = if (scale != null) 2 else 1,
        constraints = if (constraints.hasBoundedWidth) Constraints(maxWidth = constraints.maxWidth) else Constraints(),
    )
}

/**
 * The main action of a list screen as the [EdgeButton] at the bottom edge: it grows to its full
 * size as the list reaches its end (pass the screen's scaffold [edgeScrollInfo]); icon and label
 * as in [WatchAction], tonal unless [primary].
 */
@Composable
fun EdgeAction(text: String, onClick: () -> Unit, icon: Int? = null, primary: Boolean = false) {
    val press = remember { MutableInteractionSource() }
    EdgeButton(
        onClick = onClick,
        buttonSize = EdgeButtonSize.Medium,
        colors = if (primary) brandButtonColors() else brandTonalButtonColors(),
        interactionSource = press,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/**
 * The scroll info of [state] for a scaffold with an [EdgeButton], without moving the time at the
 * top: the time of the trip pages stays put (see TIME_TEXT_NOTE in TripCompanion), only the
 * EdgeButton follows the list.
 */
fun edgeScrollInfo(state: ScalingLazyListState): ScrollInfoProvider {
    val base = ScrollInfoProvider(state)
    return object : ScrollInfoProvider by base {
        override val isScrollAwayValid: Boolean get() = false
    }
}
