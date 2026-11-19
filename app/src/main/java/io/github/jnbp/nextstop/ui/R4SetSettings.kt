package io.github.jnbp.nextstop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.ui.theme.AppColors

/*
 * Settings of round 4: a row of chips that wraps instead of scrolling sideways, the check
 * "Unterwegs prüfen" of the disruption watch, and the preview of "Sonnenseite anzeigen".
 */

/** Chips side by side that wrap onto further lines where they do not fit: never a sideways scroll. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WrapRow(modifier: Modifier = Modifier, content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

/**
 * "Unterwegs prüfen": the watched routes and places checked every [period] minutes in between
 * (0 is off, the default; see DisruptionWatch.periodMinutes), paused while the battery saver
 * is active, which the row then says.
 */
@Composable
internal fun CheckOnTheWayRow(shape: Shape, period: Int, onPeriod: (Int) -> Unit) {
    SettingsRow(
        shape = shape,
        title = stringResource(R.string.dwatch_period_title),
        text = stringResource(R.string.r4_dwatch_period_text),
    ) {
        SegmentedToggle(
            labels = WATCH_PERIODS.map { minutes ->
                if (minutes == 0) stringResource(R.string.dwatch_period_off) else stringResource(R.string.dwatch_period_minutes, minutes)
            },
            selected = WATCH_PERIODS.indexOf(period).coerceAtLeast(0),
            onSelect = { index -> onPeriod(WATCH_PERIODS[index]) },
            minHeight = 40.dp,
            ground = AppColors.Chip,
        )
        SettingReveal(visible = period > 0 && BatterySaver.active) {
            Text(
                text = stringResource(R.string.r4_dwatch_period_paused),
                color = AppColors.Early,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

/**
 * "Sonnenseite anzeigen": a small ride card as the companion and the connection show it, with
 * the hint on which side the sun shines while [on]; the hint folds away while off.
 */
@Composable
internal fun SunSidePreview(on: Boolean) {
    val animate = rememberAnimationsOn()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Chip)
            .padding(10.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.Card)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LineBadge(line = "RE1", product = "regional")
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.r4_sun_preview_ride),
                        color = AppColors.Ink,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(text = stringResource(R.string.r4_sun_preview_time), color = AppColors.Muted, fontSize = 12.sp)
                }
            }
            AnimatedVisibility(
                visible = on,
                enter = if (animate) expandVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) + fadeIn() else EnterTransition.None,
                exit = if (animate) shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut() else ExitTransition.None,
            ) {
                Text(
                    text = "☀ " + stringResource(
                        R.string.sun_side_hint,
                        stringResource(R.string.sun_side_left),
                        stringResource(R.string.sun_side_right),
                    ),
                    color = AppColors.Ink,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppColors.AmberContainer)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}
