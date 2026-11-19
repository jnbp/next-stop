package io.github.jnbp.nextstop.ui.companion

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.WAKE_TRIP_LEADS
import io.github.jnbp.nextstop.data.WakeLead
import io.github.jnbp.nextstop.ui.FadeSwap
import io.github.jnbp.nextstop.ui.LocalBackdrop
import io.github.jnbp.nextstop.ui.Motion
import io.github.jnbp.nextstop.ui.SegmentedToggle
import io.github.jnbp.nextstop.ui.WakeAlarm
import io.github.jnbp.nextstop.ui.clock
import io.github.jnbp.nextstop.ui.groupShape
import io.github.jnbp.nextstop.ui.theme.AppColors
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId

/*
 * The wake-up alarm of the followed trip in the new companion views (#64): a chip at the end of
 * the line of the blue box (alarm icon and "15:07"), a tap opens a small sheet (on or off for this trip, and
 * when it rings), a long press switches it on or off at once. The ⋯ on top opens the same sheet.
 */

/**
 * What the chip of the wake-up alarm shows and does: [at] when it rings next (null where it does
 * not ring on this trip any more), [on] whether it is on, [onOpen] opens its sheet, [onToggle]
 * switches it for this trip.
 */
internal class WakeChipState(
    val at: OffsetDateTime?,
    val on: Boolean,
    val onOpen: () -> Unit,
    val onToggle: () -> Unit,
)

/** The chip of the wake-up alarm on the blue box: the alarm icon and "15:07" while on, a pale "Aus" while off. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WakeChip(state: WakeChipState) {
    val haptics = LocalHapticFeedback.current
    val ground by animateColorAsState(
        AppColors.OnBlue.copy(alpha = if (state.on) 0.22f else 0.10f),
        Motion.quick(),
        label = "wakeChipGround",
    )
    val label = if (state.on) state.at?.clock() ?: stringResource(R.string.wake_tile_no_ring) else stringResource(R.string.wake_alarm_off)
    val description = if (state.on) {
        stringResource(R.string.r5c_wake_chip_on, state.at?.clock() ?: stringResource(R.string.wake_tile_no_ring))
    } else {
        stringResource(R.string.r5c_wake_chip_off)
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(ground)
            .semantics { contentDescription = description }
            .combinedClickable(
                onClick = state.onOpen,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    state.onToggle()
                },
            )
            .defaultMinSize(minHeight = 24.dp)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        io.github.jnbp.nextstop.ui.GlyphIcon(
            io.github.jnbp.nextstop.ui.Glyph.Alarm,
            color = AppColors.OnBlue.copy(alpha = if (state.on) 1f else 0.75f),
            size = 14.dp,
        )
        FadeSwap(target = label) { text ->
            Text(
                text = text,
                color = AppColors.OnBlue.copy(alpha = if (state.on) 1f else 0.75f),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                maxLines = 1,
            )
        }
    }
}

/**
 * The small sheet of the wake-up alarm of [journey]: the switch for this trip, when it rings
 * now, and when it should ring ("2 Halte vorher", 5, 10 or 15 min before getting off, or an exact
 * time by a clock). A choice switches it on. [rideIndex] is the ride the exact time belongs to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WakeSheet(journey: Journey, now: OffsetDateTime, rideIndex: Int?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val forTrip by WakeAlarm.forTrip.collectAsState()
    val lead by WakeAlarm.leadForTrip.collectAsState()
    val prefs = remember(context) { AppPrefs(context) }
    val targets = remember(journey, forTrip, lead) { WakeAlarm.targets(context, journey) }
    val on = forTrip ?: targets.isNotEmpty()
    val next = targets.firstOrNull { it.at.isAfter(now) }
    var picking by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.PlainGround,
        contentColor = AppColors.Ink,
    ) {
        CompositionLocalProvider(LocalBackdrop provides null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(groupShape(0, 1))
                        .background(AppColors.Card)
                        .toggleable(value = on, role = Role.Switch) { WakeAlarm.setForTrip(context, it) }
                        .defaultMinSize(minHeight = 56.dp)
                        .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = stringResource(R.string.wake_alarm_trip_title), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        FadeSwap(
                            target = when {
                                !on -> stringResource(R.string.wake_alarm_off)
                                next != null -> stringResource(R.string.r5c_wake_rings, next.at.clock())
                                else -> stringResource(R.string.r5c_wake_no_ring)
                            },
                        ) { text -> Text(text = text, color = AppColors.Muted, fontSize = 13.sp, lineHeight = 17.sp) }
                    }
                    Switch(checked = on, onCheckedChange = null, modifier = Modifier.minimumInteractiveComponentSize())
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(groupShape(1, 1))
                        .background(AppColors.Card)
                        .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(text = stringResource(R.string.r5c_wake_when), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    // Without a choice of its own the trip follows the setting: that choice is marked where it is offered.
                    val chosen = lead ?: WakeLead.Minutes(prefs.wakeAlarmMinutes)
                    SegmentedToggle(
                        labels = WAKE_TRIP_LEADS.map { choice ->
                            when (choice) {
                                is WakeLead.Stops -> pluralStringResource(R.plurals.r5c_wake_stops, choice.stops, choice.stops)
                                is WakeLead.Minutes -> stringResource(R.string.wake_minutes_choice, choice.minutes)
                                is WakeLead.At -> ""
                            }
                        },
                        selected = WAKE_TRIP_LEADS.indexOf(chosen),
                        onSelect = { index -> WakeAlarm.setLeadForTrip(context, WAKE_TRIP_LEADS[index]) },
                        minHeight = 40.dp,
                        ground = AppColors.Chip,
                        fill = true,
                    )
                    val exact = lead as? WakeLead.At
                    Text(
                        text = if (exact != null) stringResource(R.string.r5c_wake_exact_set, exact.time.clock()) else stringResource(R.string.r5c_wake_exact),
                        color = AppColors.Blue,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { picking = true }
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
    if (picking) {
        val ride = rideIndex?.let { journey.legs.getOrNull(it) } ?: journey.rides.lastOrNull { it.arrival.isAfter(now) }
        val zone = ZoneId.of("Europe/Berlin")
        val start = (lead as? WakeLead.At)?.time ?: next?.at ?: ride?.arrival?.minusMinutes(prefs.wakeAlarmMinutes.toLong()) ?: now
        val local = start.atZoneSameInstant(zone)
        val clock = rememberTimePickerState(initialHour = local.hour, initialMinute = local.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(stringResource(R.string.r5c_wake_pick_title)) },
            text = { TimePicker(state = clock) },
            confirmButton = {
                TextButton(onClick = {
                    picking = false
                    val base = (ride?.arrival ?: now).atZoneSameInstant(zone)
                    var time = base.with(LocalTime.of(clock.hour, clock.minute))
                    // A time after the arrival on the clock of the day before (a ride past midnight).
                    if (ride != null && time.toOffsetDateTime().isAfter(ride.arrival)) time = time.minusDays(1)
                    WakeAlarm.setLeadForTrip(context, WakeLead.At(time.toOffsetDateTime()))
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
