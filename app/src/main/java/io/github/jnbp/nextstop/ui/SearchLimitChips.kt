package io.github.jnbp.nextstop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.PRODUCT_KEYS
import io.github.jnbp.nextstop.ui.theme.AppColors
import kotlin.math.sqrt

/*
 * The small chip that says a search leaves out some kinds of transport ("Ohne Fernbus"): no
 * row of its own any more but a compact chip in the row of the search's chips, a "left out"
 * mark and the short name ("⊘ Fernbus ✕"; "⊘ Fernbus +1" for more), sliding in and out.
 */

/**
 * The text of the compact chip for the kinds of transport [names] left out (in the order of
 * the settings): the one name, or the first and how many more ("Fernbus +1"); null for none.
 */
internal fun productsOffShort(names: List<String>, more: (first: String, count: Int) -> String): String? = when {
    names.isEmpty() -> null
    names.size == 1 -> names[0]
    else -> more(names[0], names.size - 1)
}

/**
 * Says that the search leaves out some kinds of transport (set under Settings, and shared
 * with the departures), so that a missing feeder bus or tram does not go unnoticed: a compact
 * chip that comes and goes softly. A tap searches again with every kind.
 */
@Composable
fun ProductsOffChip(off: Set<String>, onUseAll: () -> Unit, modifier: Modifier = Modifier, minHeight: Dp = 48.dp) {
    // The kinds last left out, so the chip keeps its text while it goes.
    val kept = remember { arrayOfNulls<Set<String>>(1) }
    if (off.isNotEmpty()) kept[0] = off
    val shown = kept[0] ?: return
    val animate = rememberAnimationsOn()
    AnimatedVisibility(
        visible = off.isNotEmpty(),
        modifier = modifier,
        enter = if (animate) fadeIn(Motion.fadeIn()) + expandHorizontally(Motion.settle(), expandFrom = Alignment.Start) else EnterTransition.None,
        exit = if (animate) fadeOut(Motion.fadeOut()) + shrinkHorizontally(Motion.settle(), shrinkTowards = Alignment.Start) else ExitTransition.None,
    ) {
        val names = PRODUCT_KEYS.filter { it in shown }.map { key -> stringResource(productLabel(key)) }
        val moreFormat = stringResource(R.string.products_off_more)
        val text = productsOffShort(names) { first, count -> String.format(moreFormat, first, count) } ?: return@AnimatedVisibility
        val spoken = stringResource(R.string.products_off, names.joinToString(", ")) + ". " + stringResource(R.string.products_use_all)
        val shape = RoundedCornerShape(24.dp)
        val interactions = remember { MutableInteractionSource() }
        val ticks = rememberTicks()
        Row(
            modifier = Modifier
                .clearAndSetSemantics {
                    contentDescription = spoken
                    role = Role.Button
                }
                .springPress(interactions)
                .clip(shape)
                .background(AppColors.Card)
                .border(1.dp, glassRim(), shape)
                .clickable(interactionSource = interactions, indication = LocalIndication.current) {
                    ticks.tick()
                    onUseAll()
                }
                .defaultMinSize(minHeight = minHeight)
                .widthIn(max = 180.dp)
                .padding(start = 10.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            LeftOutMark(color = AppColors.Muted, size = 14.dp)
            Text(
                text = text,
                color = AppColors.Ink,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            GlyphIcon(Glyph.Close, color = AppColors.Muted, size = 12.dp)
        }
    }
}

/** "Left out": a ring with a slash through it, as on a sign. */
@Composable
private fun LeftOutMark(color: Color, size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val stroke = this.size.minDimension * 0.13f
        val radius = (this.size.minDimension - stroke) / 2f
        drawCircle(color, radius = radius, center = center, style = Stroke(width = stroke))
        // The slash from upper left to lower right, inside the ring.
        val d = radius / sqrt(2f)
        drawLine(
            color,
            Offset(center.x - d, center.y - d),
            Offset(center.x + d, center.y + d),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}
