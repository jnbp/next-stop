package io.github.jnbp.nextstop.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.Accessibility
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.data.Warning
import io.github.jnbp.nextstop.ui.theme.AppColors

/**
 * True when the traveller travels step-free: the accessibility setting of the searches is not
 * "any". Then notices about lifts and escalators raise the warning sign (see warningAlerts).
 */
@Composable
fun rememberStepFree(): Boolean {
    val context = LocalContext.current
    return remember(context) { AppPrefs(context).searchOptions.accessibility != Accessibility.None }
}

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
        if (open) {
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
