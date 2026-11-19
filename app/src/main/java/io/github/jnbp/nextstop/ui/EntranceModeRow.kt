package io.github.jnbp.nextstop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.ui.theme.AppColors

/**
 * The setting "Ein- und Ausgänge anzeigen" as a row of its own (see [EntranceSetting]):
 * automatically by zoom, always from zoom 16, or only for the marked station. Self-contained,
 * so it can stand in any group of the map settings with the [shape] of its place there.
 */
@Composable
fun EntranceModeRow(shape: Shape, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    EntranceSetting.load(context)
    val modes = EntranceMode.entries
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.Card)
            .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = stringResource(R.string.entrance_mode_title), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(
                text = stringResource(R.string.entrance_mode_text),
                color = AppColors.Muted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
        SegmentedToggle(
            labels = modes.map { mode ->
                stringResource(
                    when (mode) {
                        EntranceMode.Auto -> R.string.entrance_mode_auto
                        EntranceMode.Always -> R.string.entrance_mode_always
                        EntranceMode.MarkedOnly -> R.string.entrance_mode_marked
                    },
                )
            },
            selected = modes.indexOf(EntranceSetting.mode),
            onSelect = { index -> EntranceSetting.update(context, modes[index]) },
            modifier = Modifier.fillMaxWidth(),
            minHeight = 40.dp,
            ground = AppColors.Chip,
            fill = true,
            twoLines = true,
        )
        // The three choices as small maps, the chosen one with a ring (see SettingsPreviews).
        // A tap on one of the small maps picks its mode.
        PreviewExpander(key = "entrance_mode", onPick = { index -> EntranceSetting.update(context, modes[index]) }) {
            EntranceModePreview(EntranceSetting.mode)
        }
    }
}
