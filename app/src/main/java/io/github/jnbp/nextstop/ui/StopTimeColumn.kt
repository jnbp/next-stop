package io.github.jnbp.nextstop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

/*
 * The time at the end of a stop row of the new detail and the Live-Fahrt (#237): figures of
 * one width ("tnum") in a column at least as wide as "00:00", so the platform before it stands
 * on the same line in every row, whatever the digits and whether the row has its ⋮ or not.
 */

// Tabular figures: every digit as wide as the others.
private const val TABULAR_FIGURES = "tnum"

/** [content] (a time) at the end of a column as wide as the widest plain time, in tabular figures. */
@Composable
internal fun StopTimeColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    // Measured once for the whole app (per density and font size), not again in every row as it
    // comes into view: a measurer only where the width is not known yet.
    val width = StopTimeWidth.known(density) ?: run {
        val measurer = rememberTextMeasurer(cacheSize = 0)
        remember(measurer, density.density, density.fontScale) { StopTimeWidth.measure(measurer, density) }
    }
    Box(modifier = modifier.widthIn(min = width), contentAlignment = Alignment.CenterEnd) {
        CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.merge(TextStyle(fontFeatureSettings = TABULAR_FIGURES))) {
            content()
        }
    }
}

/** The width of "00:00" in the time's style, kept for the density and font size it was measured at. */
private object StopTimeWidth {
    private var density = 0f
    private var fontScale = 0f
    private var width: Dp? = null

    fun known(at: Density): Dp? = width?.takeIf { density == at.density && fontScale == at.fontScale }

    fun measure(measurer: TextMeasurer, at: Density): Dp {
        val style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_FIGURES)
        val measured = with(at) { measurer.measure("00:00", style).size.width.toDp() }
        density = at.density
        fontScale = at.fontScale
        width = measured
        return measured
    }
}
