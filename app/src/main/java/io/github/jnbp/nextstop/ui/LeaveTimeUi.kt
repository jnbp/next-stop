package io.github.jnbp.nextstop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.OffsetDateTime
import kotlin.math.ceil
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.Departure
import io.github.jnbp.nextstop.data.LeaveTime
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.catchableLeaves
import io.github.jnbp.nextstop.data.leaveTimes
import io.github.jnbp.nextstop.data.secondsToLeave
import io.github.jnbp.nextstop.data.walkSpeedFactor
import io.github.jnbp.nextstop.ui.theme.AppColors

/**
 * The switch at the top of the departures between the order by departure and "Wann los?" (by
 * the latest time to set off on foot, see leaveTimes). With [unavailable] (the text of why:
 * no position, or a stop too far to walk to) it is greyed, does nothing, and says why.
 */
@Composable
fun LeaveOrderToggle(
    leaveOrder: Boolean,
    onLeaveOrder: (Boolean) -> Unit,
    unavailable: Int?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SegmentedToggle(
            labels = listOf(stringResource(R.string.leave_order_departure), stringResource(R.string.leave_order_leave)),
            selected = if (leaveOrder && unavailable == null) 1 else 0,
            onSelect = { index -> if (unavailable == null) onLeaveOrder(index == 1) },
            modifier = Modifier
                .alpha(if (unavailable == null) 1f else 0.45f)
                .semantics { if (unavailable != null) disabled() },
            minHeight = 40.dp,
        )
        if (unavailable != null) {
            Text(text = stringResource(unavailable), color = AppColors.Muted, fontSize = 13.sp)
        }
    }
}

/**
 * The departures in the order of "Wann los?" (see leaveTimes): those missed more than a moment
 * ago are left out; the shown ones keep their order. Each is shown as a [LeaveRow].
 */
@Composable
fun rememberLeaveRows(
    departures: List<Departure>,
    here: Pair<Double, Double>,
    boardStop: Stop?,
    now: OffsetDateTime,
): List<LeaveTime> {
    val factor = walkSpeedFactor
    // The walks and the order change with the list, the position and the pace, not with the clock.
    val all = remember(departures, here, boardStop, factor) { leaveTimes(departures, here, boardStop) }
    return catchableLeaves(all, now)
}

/**
 * One row of "Wann los?": the time left to set off above it ("los in 4 min", "jetzt los",
 * "verpasst") and its walk, then the departure as always. [modifier] shapes the row.
 */
@Composable
fun LeaveRow(
    leave: LeaveTime,
    now: OffsetDateTime,
    stopFavorite: Boolean,
    onDepartureAction: (Departure, DepartureAction) -> Unit,
    modifier: Modifier = Modifier,
    // In a list of several stops: the town of the board and the marker of each stop (see stopMarkers).
    boardTown: String? = null,
    markers: Map<String, Int> = emptyMap(),
) {
    val seconds = secondsToLeave(leave, now)
    val missed = seconds != null && seconds < 0
    // A row of several stops names its stop in the header, so the line below leaves it out.
    val stopName = departureStopLabel(leave.departure, boardTown)
    Column(modifier = modifier.alpha(if (missed) 0.5f else 1f)) {
        LeaveLabel(
            leave = leave,
            seconds = seconds,
            stopName = stopName,
            marker = leave.departure.stop?.id?.let { markers[it] }?.let { stopMarkerColor(it) },
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
        )
        DepartureItem(
            departure = leave.departure,
            now = now,
            stopFavorite = stopFavorite,
            onAction = { action -> onDepartureAction(leave.departure, action) },
            contentPadding = PaddingValues(start = 12.dp, end = 8.dp),
            boardTown = boardTown,
            stopInHeader = stopName != null,
        )
    }
}

/**
 * "los in 4 min · 6 min zu Fuß" above a departure; nothing for a cancelled run or one without a
 * time. In a list of several stops ([stopName]) the stop leads: "● 🚶 Hauptstraße · los in 4 min
 * · 6 min zu Fuß" (see StopRowHeader), and its name alone where there is no time to set off.
 */
@Composable
private fun LeaveLabel(leave: LeaveTime, seconds: Long?, stopName: String?, marker: Color?, modifier: Modifier = Modifier) {
    val walk = leave.walkSeconds
    if (seconds == null || walk == null || leave.departure.cancelled) {
        if (stopName != null) StopRowHeader(stopName = stopName, marker = marker, modifier = modifier.fillMaxWidth())
        return
    }
    val (text, color) = when {
        seconds < 0 -> stringResource(R.string.leave_order_missed) to AppColors.Muted
        seconds < 60 -> stringResource(R.string.leave_order_now) to AppColors.Blue
        else -> stringResource(R.string.leave_order_in, seconds / 60) to AppColors.Ink
    }
    val walkMinutes = ceil(walk / 60.0).toLong().coerceAtLeast(1L)
    if (stopName != null) {
        StopRowHeader(
            stopName = stopName,
            marker = marker,
            modifier = modifier.fillMaxWidth(),
            leaveColor = color,
            leave = { RollingText(value = seconds / 60, text = text, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold) },
            walk = stringResource(R.string.leave_order_walk, walkDurationLabel(walkMinutes)),
        )
        return
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        GlyphIcon(Glyph.Walk, color = color, size = 14.dp)
        // A new count rolls in as the minutes of the departure do.
        RollingText(value = seconds / 60, text = text, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(
            text = stringResource(R.string.leave_order_walk, walkDurationLabel(walkMinutes)),
            color = AppColors.Muted,
            fontSize = 13.sp,
        )
    }
}
