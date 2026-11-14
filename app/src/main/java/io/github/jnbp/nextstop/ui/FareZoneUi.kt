package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.ui.theme.AppColors
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.VbbZones

/** True when [journey] leaves the Berlin zones A and B for C (see [VbbZones.needsZoneC]). */
@Composable
fun rememberNeedsZoneC(journey: Journey): Boolean = remember(journey) { VbbZones.needsZoneC(journey) }

/**
 * The small "ABC" tag of a journey that reaches the fare zone C: a Berlin AB ticket is not
 * enough. Quiet on purpose: muted ink on the chip ground, as the other small tags of a card.
 */
@Composable
fun FareZoneChip(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.fare_abc_description)
    Text(
        text = stringResource(R.string.fare_abc_chip),
        color = AppColors.Muted,
        fontSize = 11.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight.ExtraBold,
        maxLines = 1,
        modifier = modifier
            .semantics { contentDescription = description }
            .clip(RoundedCornerShape(6.dp))
            .background(AppColors.Chip)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/** In the detail of a journey into zone C: the tag and one line on what it means. Nothing otherwise. */
@Composable
fun FareZoneNote(journey: Journey, modifier: Modifier = Modifier) {
    if (!rememberNeedsZoneC(journey)) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AppColors.Card)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FareZoneChip()
        Text(
            text = stringResource(R.string.fare_abc_note),
            color = AppColors.Muted,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            modifier = Modifier.weight(1f),
        )
    }
}
