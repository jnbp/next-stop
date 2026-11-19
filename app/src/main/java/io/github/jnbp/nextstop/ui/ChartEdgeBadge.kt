package io.github.jnbp.nextstop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.jnbp.nextstop.R

/**
 * The small badge at an edge of the chart ([start]: the left one, earlier journeys): a spinner
 * with "Earlier …" or "Later …" while a page is [loading] there, otherwise the [note] on how the
 * last page ended when it showed nothing new, with what to do about it. It slides in from its
 * edge and fades; with animations switched off it simply shows and goes, and the spinner stands.
 */
@Composable
internal fun ChartEdgeBadge(start: Boolean, loading: Boolean, note: EdgeNote?, modifier: Modifier = Modifier) {
    val text = when {
        loading -> stringResource(if (start) R.string.chart_edge_loading_earlier else R.string.chart_edge_loading_later)
        note == EdgeNote.Failed -> stringResource(R.string.chart_edge_failed)
        note == EdgeNote.Hidden -> stringResource(R.string.chart_edge_hidden)
        note == EdgeNote.Nothing -> stringResource(if (start) R.string.chart_edge_nothing_earlier else R.string.chart_edge_nothing_later)
        note == EdgeNote.End -> stringResource(if (start) R.string.chart_edge_end_earlier else R.string.chart_edge_end_later)
        else -> null
    }
    // The text of before stays while the badge goes away.
    val held = remember { arrayOfNulls<String>(1) }
    val heldLoading = remember { BooleanArray(1) }
    if (text != null) {
        held[0] = text
        heldLoading[0] = loading
    }
    val animate = rememberAnimationsOn()
    val side: (Int) -> Int = { width -> if (start) -width / 2 else width / 2 }
    AnimatedVisibility(
        visible = text != null,
        modifier = modifier,
        enter = if (animate) fadeIn(tween(180)) + slideInHorizontally(tween(220), side) else EnterTransition.None,
        exit = if (animate) fadeOut(tween(200)) + slideOutHorizontally(tween(220), side) else ExitTransition.None,
    ) {
        val shape = RoundedCornerShape(14.dp)
        Row(
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .widthIn(max = 168.dp)
                .shadow(3.dp, shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, shape)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (heldLoading[0]) {
                if (animate) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                } else {
                    CircularProgressIndicator(progress = { 0.75f }, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                }
            }
            Text(
                text = held[0].orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Start,
                maxLines = 3,
            )
        }
    }
}
