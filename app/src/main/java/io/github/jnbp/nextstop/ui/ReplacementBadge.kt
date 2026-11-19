package io.github.jnbp.nextstop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.Leg
import io.github.jnbp.nextstop.data.RailReplacement
import io.github.jnbp.nextstop.data.replacedProduct
import io.github.jnbp.nextstop.data.replacedProductOrGuess
import io.github.jnbp.nextstop.data.ridePlace

/**
 * A calm badge of a rail replacement for the row of a ride in the connection detail: a plain
 * line badge in the colour of the line it stands in for ("RE8"), with a small "SEV" mark in
 * it; no stripes and no extra tag beside it, so the destination after it stays readable.
 * Without a known line just "SEV" with the bus icon, on the bus's colour.
 */
@Composable
fun PlainReplacementBadge(leg: Leg, replacement: RailReplacement, modifier: Modifier = Modifier, minWidth: Dp = 40.dp) {
    val replaced = replacement.replaced
    val at = leg.ridePlace
    val product = if (replaced != null) replacement.replacedProductOrGuess ?: replacedProduct(replaced) else leg.product
    val color = if (replaced != null) lineColor(replaced, product, at) else lineColor(leg)
    val ink = if (replaced != null) lineInk(replaced, product, color, at) else lineInk(leg, color)
    val outline = badgeOutline(color)
    val shape = RoundedCornerShape(10.dp)
    val spoken = listOfNotNull(stringResource(R.string.rail_replacement), replaced ?: leg.line?.takeIf { it.isNotBlank() }).joinToString(" ")
    Row(
        modifier = modifier
            .clearAndSetSemantics { contentDescription = spoken }
            .defaultMinSize(minWidth = minWidth)
            .clip(shape)
            .background(color)
            .then(if (outline != null) Modifier.border(1.dp, outline, shape) else Modifier)
            .padding(horizontal = 7.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (replaced == null) {
            // A replacement is a bus, whatever its name.
            ModeMark(TravelMode.Bus, ink, MODE_MARK_SIZE)
            Text(text = SEV_MARK, color = ink, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1)
        } else {
            Text(text = replaced, color = ink, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, softWrap = false)
            // The small mark: the ink of the badge as its ground, the line's colour as its letters.
            Text(
                text = SEV_MARK,
                color = color,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 9.sp,
                lineHeight = 10.sp,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(ink)
                    .padding(horizontal = 3.dp, vertical = 1.dp),
            )
        }
    }
}
