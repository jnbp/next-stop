package io.github.jnbp.nextstop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.Disruption
import io.github.jnbp.nextstop.data.SavedPattern
import io.github.jnbp.nextstop.data.linesLabel
import io.github.jnbp.nextstop.data.shortStopName
import io.github.jnbp.nextstop.ui.theme.AppColors

/**
 * Asks for an optional name before a connection is remembered: its lines ("M27 → U9") and
 * the way from start to destination are shown, the name may stay empty.
 */
@Composable
fun RememberPatternDialog(pattern: SavedPattern, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(pattern.name) }
    Dialog(onDismissRequest = onDismiss) {
        // A dialog is a window of its own: the picture of the map does not line up behind it.
        CompositionLocalProvider(LocalBackdrop provides null) {
            GlassSurface(shape = RoundedCornerShape(32.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.pattern_remember),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    Column(modifier = Modifier.padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = linesLabel(pattern.lines), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        RoutePairText(
                            from = pattern.from,
                            to = pattern.to,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            color = AppColors.Muted,
                        )
                    }
                    NameField(value = name, placeholder = stringResource(R.string.pattern_name_hint), onValue = { name = it })
                    PrimaryButton(
                        text = stringResource(R.string.pattern_save),
                        onClick = { onSave(name.trim()) },
                        modifier = Modifier.fillMaxWidth(),
                        minHeight = 50.dp,
                    )
                    SecondaryButton(
                        text = stringResource(R.string.cancel),
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        ground = AppColors.Chip,
                        minHeight = 48.dp,
                    )
                }
            }
        }
    }
}

/** A remembered connection as a row: its name or lines, the way, and a cross that forgets it. */
@Composable
fun PatternRow(
    pattern: SavedPattern,
    shape: Shape,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    disruptions: List<Disruption> = emptyList(),
    onDisruptions: (() -> Unit)? = null,
    // The key of its bell (the route between its ends, see DisruptionWatch); null: no bell.
    bellKey: String? = null,
    onWatchSettings: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.Card)
            .disruptionFrame(disruptions.isNotEmpty(), shape)
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 60.dp)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GlyphIcon(Glyph.Route)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 6.dp),
        ) {
            Text(
                text = pattern.label,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // With a name of its own the lines come first; the ends of the route stay visible below.
            if (pattern.name.isNotBlank()) {
                Text(
                    text = linesLabel(pattern.lines),
                    color = AppColors.Muted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            RoutePairText(
                from = pattern.from,
                to = pattern.to,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = AppColors.Muted,
            )
            // The notices on its lines, in one line (see CardDisruptionLine).
            CardDisruptionLine(items = disruptions, onClick = onDisruptions)
        }
        if (bellKey != null) DisruptionBell(watchKey = bellKey, onSettings = onWatchSettings)
        RoundButton(
            glyph = Glyph.Close,
            description = stringResource(R.string.pattern_forget),
            onClick = onRemove,
            solid = true,
        )
    }
}
