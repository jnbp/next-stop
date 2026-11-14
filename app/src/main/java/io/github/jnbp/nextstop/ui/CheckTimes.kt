package io.github.jnbp.nextstop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerLayoutType
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.ui.theme.AppColors

/*
 * The times of the daily disruption checks (see DisruptionWatch.checkTimes): a chip per time,
 * each removed with a tap on it (✕), and "+ Time" that asks for another one on a clock. No
 * time: the checks are off. Used in the settings and in the setup.
 */

/** A time of day in minutes after midnight as "7:00". */
internal fun clockLabel(minutes: Int): String = "%d:%02d".format(minutes / 60, minutes % 60)

/** The check times [times] as chips with ✕ and "+ Time"; [onTimes] gets the new list, sorted. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CheckTimesEditor(
    times: List<Int>,
    onTimes: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
    ground: Color = AppColors.Chip,
) {
    var picking by remember { mutableStateOf(false) }
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (times.isEmpty()) {
            Box(modifier = Modifier.defaultMinSize(minHeight = 40.dp), contentAlignment = Alignment.Center) {
                Text(text = stringResource(R.string.disruption_check_off), color = AppColors.Muted, fontSize = 14.sp)
            }
        }
        times.forEach { minutes ->
            val label = clockLabel(minutes)
            val remove = stringResource(R.string.disruption_check_remove, label)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(AppColors.Blue)
                    .clickable(role = Role.Button, onClickLabel = remove) { onTimes(times - minutes) }
                    .defaultMinSize(minHeight = 40.dp)
                    .padding(start = 14.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(text = label, color = AppColors.OnBlue, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                Text(text = "✕", color = AppColors.OnBlue.copy(alpha = 0.8f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(ground)
                .clickable(role = Role.Button) { picking = true }
                .defaultMinSize(minHeight = 40.dp)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.disruption_check_add),
                color = AppColors.Blue,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
            )
        }
    }
    if (picking) {
        // An hour after the last time, or seven in the morning.
        val suggested = times.maxOrNull()?.let { (it + 60) % (24 * 60) } ?: DEFAULT_CHECK_MINUTES
        CheckTimeDialog(
            initialMinutes = suggested,
            onApply = { minutes ->
                picking = false
                onTimes((times + minutes).distinct().sorted())
            },
            onDismiss = { picking = false },
        )
    }
}

/** The suggested daily check: seven in the morning. */
internal const val DEFAULT_CHECK_MINUTES = 7 * 60

/** A clock to pick one time of day, with "Add". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckTimeDialog(initialMinutes: Int, onApply: (Int) -> Unit, onDismiss: () -> Unit) {
    val clock = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = true,
    )
    Dialog(onDismissRequest = onDismiss) {
        // A dialog is a window of its own: the picture of the map does not line up behind it.
        CompositionLocalProvider(LocalBackdrop provides null) {
            GlassSurface(shape = RoundedCornerShape(32.dp)) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.disruption_check_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                    TimePicker(state = clock, layoutType = TimePickerLayoutType.Vertical)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(AppColors.Blue)
                            .clickable(role = Role.Button) { onApply(clock.hour * 60 + clock.minute) }
                            .defaultMinSize(minHeight = 56.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.disruption_check_apply),
                            color = AppColors.OnBlue,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                        )
                    }
                }
            }
        }
    }
}
