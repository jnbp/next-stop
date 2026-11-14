package io.github.jnbp.nextstop.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.chipPlaceName

/**
 * The name of one end of an "A → B" label as short as it can be and still be clear: without the
 * signs of the network in front ("S ", "U ", "S+U "), without "(Berlin)", without a trailing
 * ", Berlin" or ", Berlin-Steglitz", and an address only by its street and number.
 */
fun routeEndName(place: Stop): String = routeEndName(chipPlaceName(place))

/** [routeEndName] for a name alone. */
fun routeEndName(name: String): String {
    val short = name
        .replace(Regex("\\s*\\(Berlin\\)"), "")
        .replace(Regex(",\\s*Berlin(-[^,]*)?\\s*$"), "")
        .replace(Regex("\\s*\\[[^\\]]*\\]$"), "")
        .replace(Regex("^(S\\+U|S|U)\\s+"), "")
        .trim()
    return short.ifEmpty { name.trim() }
}

private const val ELLIPSIS = "…"

/**
 * [name] cut at its end ("Hermannstr./Fla…") so it is at most [budget] pixels wide in [style];
 * the whole name where it fits. At least two letters stay before the ellipsis.
 */
private fun endEllipsize(measurer: TextMeasurer, name: String, budget: Int, style: TextStyle): String {
    fun width(text: String) = measurer.measure(text, style, softWrap = false, maxLines = 1).size.width
    if (width(name) <= budget) return name
    // The start of a name says the most, so only its end is cut.
    fun cut(keep: Int): String = name.take(keep).trimEnd() + ELLIPSIS
    var low = 2
    var high = name.length - 1
    var best = cut(2)
    while (low <= high) {
        val mid = (low + high) / 2
        val candidate = cut(mid)
        if (width(candidate) <= budget) {
            best = candidate
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return best
}

/**
 * "[from] → [to]" fitted to [maxWidth] pixels: when the whole label is too wide, each name is cut
 * at its end, so the start and the destination both stay visible. A short name keeps its
 * room and leaves the rest to the other one.
 */
fun fitRoutePair(measurer: TextMeasurer, from: String, to: String, arrowed: (String, String) -> String, maxWidth: Int, style: TextStyle): String {
    val whole = arrowed(from, to)
    if (maxWidth == Constraints.Infinity) return whole
    fun width(text: String) = measurer.measure(text, style, softWrap = false, maxLines = 1).size.width
    if (width(whole) <= maxWidth) return whole
    val room = (maxWidth - width(arrowed("", ""))).coerceAtLeast(0)
    val fromWidth = width(from)
    val toWidth = width(to)
    val half = room / 2
    val (fromBudget, toBudget) = when {
        fromWidth <= half -> fromWidth to room - fromWidth
        toWidth <= half -> room - toWidth to toWidth
        else -> half to room - half
    }
    return arrowed(
        endEllipsize(measurer, from, fromBudget, style),
        endEllipsize(measurer, to, toBudget, style),
    )
}

/**
 * "A → B" fitted to [maxWidth] for a label of a fixed largest width (a chip): the text to show.
 * [fontSize] and [fontWeight] must be those of the label that shows it.
 */
@Composable
fun rememberRoutePair(from: Stop, to: Stop, maxWidth: Dp, fontSize: TextUnit, fontWeight: FontWeight): String {
    val measurer = rememberTextMeasurer()
    val style = LocalTextStyle.current.merge(TextStyle(fontSize = fontSize, fontWeight = fontWeight))
    val pattern = stringResource(R.string.route_pair, "\u0000", "\u0001")
    val pixels = with(LocalDensity.current) { maxWidth.roundToPx() }
    val fromName = routeEndName(from)
    val toName = routeEndName(to)
    return remember(fromName, toName, pixels, style, pattern) {
        fitRoutePair(measurer, fromName, toName, { a, b -> pattern.replace("\u0000", a).replace("\u0001", b) }, pixels, style)
    }
}

/**
 * "A → B" on one line in the room it is given: both ends stay visible, each cut at its end
 * where the whole label does not fit (see [fitRoutePair]).
 */
@Composable
fun RoutePairText(
    from: Stop,
    to: Stop,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    val measurer = rememberTextMeasurer()
    val style = LocalTextStyle.current.merge(TextStyle(fontSize = fontSize, fontWeight = fontWeight))
    val pattern = stringResource(R.string.route_pair, "\u0000", "\u0001")
    val fromName = routeEndName(from)
    val toName = routeEndName(to)
    BoxWithConstraints(modifier = modifier) {
        val maxWidth = constraints.maxWidth
        val text = remember(fromName, toName, maxWidth, style, pattern) {
            fitRoutePair(measurer, fromName, toName, { a, b -> pattern.replace("\u0000", a).replace("\u0001", b) }, maxWidth, style)
        }
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
