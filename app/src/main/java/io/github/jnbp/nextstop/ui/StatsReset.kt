package io.github.jnbp.nextstop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.TripStats
import io.github.jnbp.nextstop.ui.theme.AppColors

/**
 * Settings › On the way › Statistics: one row that forgets the recorded trips behind the
 * statistics of "My ways", after a dialog asks (#135). The figures there follow at once, since
 * they read [TripStats.records].
 */
@Composable
internal fun StatsResetGroup() {
    var asking by remember { mutableStateOf(false) }
    SettingsGroup(label = stringResource(R.string.r6_stats_group)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(groupShape(0, 0))
                .background(AppColors.Card)
                .clickable(role = Role.Button) { asking = true }
                .defaultMinSize(minHeight = 56.dp)
                .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingText(
                title = stringResource(R.string.r6_stats_reset_title),
                text = stringResource(R.string.r6_stats_reset_text),
                modifier = Modifier.weight(1f),
            )
        }
    }
    if (asking) StatsResetDialog(onDone = { asking = false })
}

/**
 * The question before the recorded trips are forgotten (#135, #195): it says that this cannot be
 * undone; only "Zurücksetzen" clears them (see TripStats.clear). [onDone] closes it either way.
 */
@Composable
internal fun StatsResetDialog(onDone: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(text = stringResource(R.string.r6_stats_reset_title)) },
        text = { Text(text = stringResource(R.string.r6_stats_reset_confirm)) },
        confirmButton = {
            TextButton(
                onClick = {
                    onDone()
                    TripStats.clear(context.applicationContext)
                },
            ) { Text(text = stringResource(R.string.r6_stats_reset_yes)) }
        },
        dismissButton = {
            TextButton(onClick = onDone) { Text(text = stringResource(R.string.cancel)) }
        },
    )
}

/**
 * "Statistik zurücksetzen" at the end of the statistics in "Meine Wege" (#195): a quiet text
 * button that asks first (see [StatsResetDialog]).
 */
@Composable
internal fun StatsResetButton(modifier: Modifier = Modifier) {
    var asking by remember { mutableStateOf(false) }
    TextButton(onClick = { asking = true }, modifier = modifier) {
        Text(text = stringResource(R.string.r6_stats_reset_title), color = AppColors.Muted, fontSize = 13.sp)
    }
    if (asking) StatsResetDialog(onDone = { asking = false })
}
