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
import io.github.jnbp.nextstop.data.StopGroup
import io.github.jnbp.nextstop.data.shortStopName
import io.github.jnbp.nextstop.ui.theme.AppColors

/** The name a favourite of several stops goes by: its own, else "Albertstr. +2". */
@Composable
fun stopGroupName(group: StopGroup): String =
    group.name.ifBlank { stopGroupDefaultName(group) }

/** "Albertstr. +2": the first stop of a favourite of several and how many more it holds. */
@Composable
fun stopGroupDefaultName(group: StopGroup): String {
    val first = group.stops.firstOrNull()?.name?.shortStopName().orEmpty()
    return stringResource(R.string.departures_stop_plus, first, group.stops.size - 1)
}

/**
 * Asks for the name of a favourite of several stops, as a place is named when it is saved
 * (see SavePlaceDialog): the field starts with [initialName], which also stands in when it is
 * emptied. OK saves with the name as typed (trimmed), Cancel keeps nothing.
 */
@Composable
fun SaveStopGroupDialog(
    initialName: String,
    stopNames: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    Dialog(onDismissRequest = onDismiss) {
        // A dialog is a window of its own: the picture of the map does not line up behind it.
        CompositionLocalProvider(LocalBackdrop provides null) {
            GlassSurface(shape = RoundedCornerShape(32.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.stop_group_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    Text(
                        text = stringResource(R.string.stop_group_text) + "\n" + stopNames,
                        color = AppColors.Muted,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    NameField(value = name, placeholder = initialName, onValue = { name = it })
                    PrimaryButton(
                        text = stringResource(R.string.stop_group_ok),
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

/** A favourite of several stops in a list: its name, its stops below; a tap shows their departures. */
@Composable
fun StopGroupRow(group: StopGroup, shape: Shape, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.Card)
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 60.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        group.stops.firstOrNull()?.let { PlaceSymbols(it) }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stopGroupName(group),
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = group.stops.joinToString(" · ") { it.name.shortStopName() },
                color = AppColors.Muted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
