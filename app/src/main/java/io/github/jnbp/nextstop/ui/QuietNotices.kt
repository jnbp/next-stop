package io.github.jnbp.nextstop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.Warning
import io.github.jnbp.nextstop.data.stepFreeSetting
import io.github.jnbp.nextstop.ui.theme.AppColors

/**
 * True when the traveller travels step-free: the accessibility setting of the searches is not
 * "any". Then notices about lifts and escalators raise the warning sign (see warningAlerts).
 */
@Composable
fun rememberStepFree(): Boolean = LocalStepFree.current

/**
 * Whether the traveller travels step-free, as the setting is right now: AppRoot provides it
 * from [stepFreeSetting], so every reader follows a change at once. Only the readers compose
 * anew (the setting rarely changes); without a provider the setting as it is when read.
 */
internal val LocalStepFree = compositionLocalOf { stepFreeSetting.value }

/**
 * The notices of a journey that do not raise the warning sign (see quietWarnings): about lifts,
 * escalators and step-free access, for a traveller who does not travel step-free. One quiet row
 * without the sign and without a count; a tap unfolds them.
 */
@Composable
fun QuietNotices(warnings: List<Warning>, modifier: Modifier = Modifier) {
    if (warnings.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AppColors.Chip)
            .clickable { open = !open }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.notices_accessibility),
                color = AppColors.Muted,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Chevron(open = open)
        }
        // Unfolds and folds softly (#209); at once with animations off.
        val animate = rememberAnimationsOn()
        AnimatedVisibility(
            visible = open,
            enter = if (animate) expandVertically(Motion.settle()) + fadeIn(Motion.fadeIn()) else EnterTransition.None,
            exit = if (animate) shrinkVertically(Motion.settle()) + fadeOut(Motion.fadeOut()) else ExitTransition.None,
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            warnings.forEach { warning ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = linkedText(warning.summary.ifEmpty { warning.text }, AppColors.Blue),
                        color = AppColors.Ink,
                        fontSize = 13.sp,
                    )
                    if (warning.summary.isNotEmpty() && warning.text.isNotEmpty()) {
                        Text(text = linkedText(warning.text, AppColors.Blue), color = AppColors.Muted, fontSize = 13.sp)
                    }
                }
            }
          }
        }
    }
}
