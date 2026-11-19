package io.github.jnbp.nextstop.ui.companion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.ui.Glyph
import io.github.jnbp.nextstop.ui.LocalBackdrop
import io.github.jnbp.nextstop.ui.ShareOptionCard
import io.github.jnbp.nextstop.ui.theme.AppColors

/*
 * #217: "Plan B", "Ändern" and the ⋯ of the Live-Fahrt bar open a sheet from the bottom, as
 * "Teilen" does (ShareSheet): each entry a card with its icon, its name and one line of what it
 * does. The sheet is opaque and lies over a scrim, so nothing of the timeline behind shows
 * through or beside the entries (#213).
 */

/**
 * One entry of a sheet of the bar: [glyph], [title], one line of [text], and what a tap does
 * ([onClick]). With [checked] it is a switch: a tap flips it and the sheet stays open.
 */
internal class BarSheetEntry(
    val glyph: Glyph,
    val title: String,
    val text: String,
    val checked: Boolean? = null,
    val onClick: () -> Unit,
)

/** The sheet [title] with its [entries]; a tap on an entry that is not a switch closes it first ([onDismiss]). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BarSheet(title: String, entries: List<BarSheetEntry>, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // Opaque, as the share sheet (#213).
        containerColor = AppColors.PlainGround.copy(alpha = 1f),
        contentColor = AppColors.Ink,
    ) {
        // A sheet is a window of its own: the picture of the map does not line up behind it.
        CompositionLocalProvider(LocalBackdrop provides null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
                )
                entries.forEachIndexed { index, entry ->
                    val checked = entry.checked
                    ShareOptionCard(
                        glyph = entry.glyph,
                        title = entry.title,
                        text = entry.text,
                        primary = false,
                        enabled = true,
                        index = index,
                        onClick = {
                            if (checked == null) onDismiss()
                            entry.onClick()
                        },
                        role = if (checked == null) Role.Button else Role.Switch,
                        trailing = checked?.let { on -> { Switch(checked = on, onCheckedChange = null) } },
                    )
                }
            }
        }
    }
}
