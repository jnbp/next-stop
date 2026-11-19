package io.github.jnbp.nextstop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.LineColors
import io.github.jnbp.nextstop.data.DelayDisplay
import io.github.jnbp.nextstop.ui.theme.AppColors
import java.time.OffsetDateTime

/*
 * The choice how delays are shown (see DelayDisplay), for the settings and the setup: one row
 * per option, each with a live sample of a departure that leaves at 12:07 and runs 2 minutes late.
 */

/** The name of an option of the delay display. */
@Composable
fun delayDisplayLabel(display: DelayDisplay): String = stringResource(
    when (display) {
        DelayDisplay.RealtimeAndDelay -> R.string.delay_display_realtime
        DelayDisplay.PlannedAndDelay -> R.string.delay_display_planned
        DelayDisplay.RealtimeOnly -> R.string.delay_display_only
        DelayDisplay.StruckPlanned -> R.string.delay_display_struck
    },
)

/**
 * A late departure as the times show with [display]: "S1 Wannsee" and its time, planned 12:07,
 * expected 12:09 (+2).
 */
@Composable
fun DelaySampleRow(display: DelayDisplay, modifier: Modifier = Modifier, ground: Color = AppColors.Chip) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ground)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LineBadge(line = "S1", product = "suburban", minWidth = 34.dp, at = LineColors.BERLIN_CENTRE)
        Text(
            text = stringResource(R.string.delay_sample_destination),
            color = AppColors.Ink,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (display == DelayDisplay.StruckPlanned) StruckTime(SAMPLE_PLANNED_TIME, fontSize = 12.sp)
        Text(
            text = if (display == DelayDisplay.PlannedAndDelay) SAMPLE_PLANNED else SAMPLE_EXPECTED,
            color = if (display == DelayDisplay.PlannedAndDelay) AppColors.Ink else AppColors.DelayRed,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp,
            maxLines = 1,
        )
        if (display.showsDelay) {
            Text(
                text = stringResource(R.string.delay_short, SAMPLE_DELAY),
                color = AppColors.OnDelayRed,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.DelayRed)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

// The sample: planned at 12:07, two minutes late.
private const val SAMPLE_PLANNED = "12:07"
private const val SAMPLE_EXPECTED = "12:09"
private const val SAMPLE_DELAY = 2
private val SAMPLE_PLANNED_TIME: OffsetDateTime = OffsetDateTime.parse("2024-01-01T12:07:00+01:00")

/**
 * The three options of the delay display as rows to pick from, each with its name, one line
 * that says what it does and its live sample; the picked one is framed in the accent colour.
 */
@Composable
fun DelayDisplayPicker(selected: DelayDisplay, onSelect: (DelayDisplay) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DelayDisplay.entries.forEach { option ->
            val picked = option == selected
            val ring by animateColorAsState(if (picked) AppColors.Blue else Color.Transparent, label = "delayRing")
            val shape = RoundedCornerShape(16.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(AppColors.Card)
                    .border(2.dp, ring, shape)
                    .selectable(selected = picked, onClick = { onSelect(option) }, role = Role.RadioButton)
                    .padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The row itself is selectable: the radio button only shows the state.
                    RadioButton(selected = picked, onClick = null, modifier = Modifier.padding(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = delayDisplayLabel(option),
                            color = if (picked) AppColors.Blue else AppColors.Ink,
                            fontWeight = if (picked) FontWeight.Bold else FontWeight.SemiBold,
                            fontSize = 15.sp,
                        )
                        Text(
                            text = stringResource(
                                when (option) {
                                    DelayDisplay.RealtimeAndDelay -> R.string.delay_display_realtime_text
                                    DelayDisplay.PlannedAndDelay -> R.string.delay_display_planned_text
                                    DelayDisplay.RealtimeOnly -> R.string.delay_display_only_text
                                    DelayDisplay.StruckPlanned -> R.string.delay_display_struck_text
                                },
                            ),
                            color = AppColors.Muted,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                        )
                    }
                }
                DelaySampleRow(option, modifier = Modifier.padding(start = 10.dp))
            }
        }
    }
}
