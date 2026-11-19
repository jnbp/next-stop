package io.github.jnbp.nextstop.wear.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp

/*
 * The column of a live-trip page that never scrolls (#180): its lines ([FitLine]) stand centred
 * between [top] and [bottom], each as wide as the round screen is where it stands, and where
 * they do not all fit the least important go (see planFit). Measured on the device, not set
 * per screen size.
 */

// The priority of a line of a FitColumn, carried as its layout id.
private data class FitPriority(val value: Int)

/** One line of a [FitColumn] with its [priority] (higher stays longer; [FIT_MUST] always), centred. */
@Composable
fun FitLine(priority: Int = FIT_MUST, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.layoutId(FitPriority(priority)), contentAlignment = Alignment.Center) { content() }
}

/**
 * Lines of [FitLine]s over the whole screen, between [top] and [bottom] from its edges,
 * [spacing] apart, at most the width of the screen less [horizontal] on each side and [edge]
 * inside the round edge. Lines that emit nothing take no room.
 */
@Composable
fun FitColumn(
    top: Dp,
    bottom: Dp,
    horizontal: Dp,
    edge: Dp,
    spacing: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val round = LocalConfiguration.current.isScreenRound
    Layout(content = content, modifier = modifier.fillMaxSize()) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val gap = spacing.toPx()
        val priorities = measurables.map { (it.layoutId as? FitPriority)?.value ?: FIT_MUST }
        val plan = planFit(
            priorities = priorities,
            heightAt = { index, lineWidth -> measurables[index].maxIntrinsicHeight(lineWidth.toInt()).toFloat() },
            spacing = gap,
            top = top.toPx(),
            bottom = height - bottom.toPx(),
            screenWidth = width.toFloat(),
            screenHeight = height.toFloat(),
            round = round,
            edge = edge.toPx(),
            maxWidth = width - 2 * horizontal.toPx(),
        )
        val placeables = plan.kept.mapIndexed { k, index ->
            measurables[index].measure(Constraints(maxWidth = plan.widths[k].toInt().coerceAtLeast(0), maxHeight = height))
        }
        // Placed by their real heights, centred in the band as planned.
        val shown = placeables.count { it.height > 0 }
        val total = placeables.sumOf { it.height } + (gap * (shown - 1).coerceAtLeast(0)).toInt()
        val bandTop = top.toPx()
        val band = height - bottom.toPx() - bandTop
        var y = (bandTop + ((band - total) / 2f).coerceAtLeast(0f)).toInt()
        layout(width, height) {
            placeables.forEach { placeable ->
                placeable.placeRelative((width - placeable.width) / 2, y)
                if (placeable.height > 0) y += placeable.height + gap.toInt()
            }
        }
    }
}
