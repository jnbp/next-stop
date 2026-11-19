package io.github.jnbp.nextstop.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.MapsHandoff
import io.github.jnbp.nextstop.data.mapsHandoffOf
import io.github.jnbp.nextstop.ui.theme.AppColors

/**
 * The remembered choice for "Google Maps" on the last way of a trip (#143); [MapsHandoff.Ask]
 * until the user ticks "Auswahl merken" or picks one in the settings. Read once per process.
 */
object MapsHandoffSetting {
    var value by mutableStateOf(MapsHandoff.Ask)
        private set
    private var loaded = false

    private fun store(context: Context) = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    fun load(context: Context): MapsHandoff {
        if (!loaded) {
            loaded = true
            value = mapsHandoffOf(store(context).getString(KEY, null))
        }
        return value
    }

    fun update(context: Context, choice: MapsHandoff) {
        loaded = true
        value = choice
        store(context).edit().putString(KEY, choice.name).apply()
    }

    private const val STORE = "maps_handoff"
    private const val KEY = "choice"
}

/** The label of a choice of [MapsHandoff]. */
private fun handoffLabel(choice: MapsHandoff): Int = when (choice) {
    MapsHandoff.Ask -> R.string.r6_handoff_ask
    MapsHandoff.Open -> R.string.r6_handoff_open
    MapsHandoff.OpenAndEnd -> R.string.r6_handoff_open_end
}

/**
 * Asked on the last way of a trip: "Google Maps öffnen" or "Google Maps öffnen und Fahrt
 * beenden", and "Auswahl merken" (changeable in the settings). [onChoice] gets the choice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapsHandoffSheet(onChoice: (MapsHandoff) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var keep by remember { mutableStateOf(false) }
    val pick: (MapsHandoff) -> Unit = { choice ->
        if (keep) MapsHandoffSetting.update(context, choice)
        onChoice(choice)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.PlainGround,
        contentColor = AppColors.Ink,
    ) {
        // A sheet is a window of its own: the picture of the map does not line up behind it.
        CompositionLocalProvider(LocalBackdrop provides null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = stringResource(R.string.r6_handoff_title), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(text = stringResource(R.string.r6_handoff_text), fontSize = 15.sp, lineHeight = 21.sp)
                Button(
                    onClick = { pick(MapsHandoff.Open) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Blue, contentColor = AppColors.OnBlue),
                ) {
                    Text(text = stringResource(R.string.r6_handoff_open), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
                OutlinedButton(
                    onClick = { pick(MapsHandoff.OpenAndEnd) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Text(text = stringResource(R.string.r6_handoff_open_end), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
                // The whole row is the checkbox: one touch target, read as one.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .toggleable(value = keep, role = Role.Checkbox) { keep = it },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Checkbox(checked = keep, onCheckedChange = null, modifier = Modifier.minimumInteractiveComponentSize())
                    Text(text = stringResource(R.string.r6_handoff_remember), fontSize = 15.sp)
                }
            }
        }
    }
}

/**
 * The setting of [MapsHandoffSetting] as a row of the settings: its title, what it is for and
 * the choice; a tap anywhere on the row asks for another one.
 */
@Composable
internal fun MapsHandoffRow(shape: Shape) {
    val context = LocalContext.current
    val current = MapsHandoffSetting.load(context)
    var asking by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.Card)
            .clickable(role = Role.Button) { asking = true }
            .defaultMinSize(minHeight = 56.dp)
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SettingText(
                title = stringResource(R.string.r6_handoff_setting_title),
                text = stringResource(R.string.r6_handoff_setting_text),
            )
            FadeSwap(target = current) { shown ->
                Text(text = stringResource(handoffLabel(shown)), color = AppColors.Blue, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(text = stringResource(R.string.r6_handoff_setting_title)) },
            text = {
                Column {
                    MapsHandoff.entries.forEach { choice ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .selectable(selected = choice == current, role = Role.RadioButton) {
                                    MapsHandoffSetting.update(context, choice)
                                    asking = false
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = choice == current, onClick = null, modifier = Modifier.minimumInteractiveComponentSize())
                            Text(text = stringResource(handoffLabel(choice)), fontSize = 15.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { asking = false }) { Text(text = stringResource(R.string.cancel)) }
            },
        )
    }
}
