package io.github.jnbp.nextstop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.OffsetDateTime
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.Appointment
import io.github.jnbp.nextstop.ui.theme.AppColors

/**
 * What the tile of the next appointment shows. [appointments] is null until the calendar
 * was read; [shown] is the one in front; [leaveAt] is when to leave for it, once a journey
 * there was found; [placeMissing] is true when its location could not be found.
 */
data class AppointmentTileState(
    val granted: Boolean,
    val denied: Boolean,
    val appointments: List<Appointment>?,
    val shown: Appointment?,
    val leaveAt: OffsetDateTime?,
    val placeMissing: Boolean = false,
)

/** When an appointment starts, with its day where that is not today. */
@Composable
fun appointmentTime(appointment: Appointment, now: OffsetDateTime): String {
    val day = appointment.begin.berlinDate()
    return if (day == now.berlinDate()) {
        appointment.begin.clock()
    } else {
        stringResource(R.string.appointment_when, day.dayLabel(), appointment.begin.clock())
    }
}

/**
 * The next appointment of the device calendar with a place: title, start and when to
 * leave, and a tap searches the way there for arrival by its start. Below it the
 * following ones, to pick one of them instead. Without the permission it offers to ask
 * for it; without appointments it says so in one calm line.
 */
@Composable
fun AppointmentTile(
    state: AppointmentTileState,
    now: OffsetDateTime,
    onEnable: () -> Unit,
    onOpen: (Appointment) -> Unit,
    onPick: (Appointment) -> Unit,
    modifier: Modifier = Modifier,
    onRemove: () -> Unit = {},
    onLater: () -> Unit = {},
) {
    val shown = state.shown
    GlassSurface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
        if (!state.granted) {
            // Without the calendar the card asks once, with a clear button; once refused, it
            // offers to go away for good (the settings bring it back) or for now.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(if (state.denied) R.string.appointment_remove_ask else R.string.appointment_ask),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.denied) {
                        PrimaryButton(
                            text = stringResource(R.string.appointment_remove),
                            onClick = onRemove,
                            minHeight = 40.dp,
                        )
                        SecondaryButton(
                            text = stringResource(R.string.appointment_later),
                            onClick = onLater,
                            ground = AppColors.Chip,
                            minHeight = 40.dp,
                        )
                    } else {
                        PrimaryButton(
                            text = stringResource(R.string.appointment_allow),
                            onClick = onEnable,
                            minHeight = 40.dp,
                        )
                    }
                }
            }
        } else if (shown == null) {
            val text = when {
                !state.granted && state.denied -> stringResource(R.string.appointment_denied)
                !state.granted -> stringResource(R.string.appointment_enable)
                state.appointments == null -> stringResource(R.string.appointment_loading)
                else -> stringResource(R.string.appointment_none)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (!state.granted) Modifier.clickable(onClick = onEnable) else Modifier)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.appointment_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                )
                if (state.granted && state.appointments == null) {
                    // Still being read: the little train, as everywhere content is on its way.
                    LoadingRow(text = text, compact = true, modifier = Modifier.weight(1f))
                } else {
                    Text(
                        text = text,
                        color = if (!state.granted && !state.denied) AppColors.Blue else AppColors.Muted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            // The following appointments wait behind one small "+N" chip, so the card stays short.
            var othersOpen by remember(shown.key) { mutableStateOf(false) }
            val others = state.appointments.orEmpty().filter { it.key != shown.key }.take(3)
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(shown) }
                        .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Left: a tiny label, the title, and when and where with the "+N" chip at its end.
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.appointment_title),
                            color = AppColors.Muted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            maxLines = 1,
                        )
                        Text(
                            text = shown.title.ifBlank { shown.location },
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            lineHeight = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (state.placeMissing) {
                                    stringResource(R.string.appointment_place_missing)
                                } else {
                                    stringResource(R.string.appointment_line, appointmentTime(shown, now), shown.location)
                                },
                                color = if (state.placeMissing) AppColors.DelayRed else AppColors.Muted,
                                fontSize = 12.sp,
                                lineHeight = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (others.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                MoreChip(count = others.size, open = othersOpen, onClick = { othersOpen = !othersOpen })
                            }
                        }
                    }
                    // Right: when to leave, in the accent colour; never cut short.
                    val leave = state.leaveAt
                    if (leave != null && !state.placeMissing) {
                        Text(
                            text = stringResource(R.string.leave_pill, leave.clock()),
                            color = AppColors.OnBlue,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(AppColors.Blue)
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
                AnimatedVisibility(
                    visible = othersOpen && others.isNotEmpty(),
                    enter = expandVertically(APPOINTMENT_SPRING) + fadeIn(),
                    exit = shrinkVertically(APPOINTMENT_SPRING) + fadeOut(),
                ) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        others.forEach { other ->
                            key(other.key) {
                                ChoiceChip(
                                    text = stringResource(
                                        R.string.appointment_chip,
                                        appointmentTime(other, now),
                                        other.title.ifBlank { other.location },
                                    ),
                                    active = false,
                                    onClick = { onPick(other) },
                                    minHeight = 32.dp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// The further appointments open and close with the spring of the other folding parts of the app;
// the card, and the sheet around it, grow and shrink with them.
private val APPOINTMENT_SPRING = spring<IntSize>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)

/** The small "+N" chip that opens and closes the further appointments. */
@Composable
private fun MoreChip(count: Int, open: Boolean, onClick: () -> Unit) {
    val description = stringResource(R.string.appointment_more, count)
    Text(
        text = "+$count",
        color = if (open) AppColors.OnBlueContainer else AppColors.Ink,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 13.sp,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .semantics { contentDescription = description }
            .clip(RoundedCornerShape(9.dp))
            .background(if (open) AppColors.BlueContainer else AppColors.Chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}
