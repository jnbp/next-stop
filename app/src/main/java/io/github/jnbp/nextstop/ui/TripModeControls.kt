package io.github.jnbp.nextstop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.OffsetDateTime
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.ShortcutMode
import io.github.jnbp.nextstop.ui.theme.AppColors

/**
 * "Schnellere Wege melden" for the followed trip, one control in place of the two switches
 * "Jede Minute zählt" and "Abkürzungs-Alarm": Aus / ab [minutes] min / Jede Minute, with a short
 * line and the details behind ⓘ (they unfold softly). Self-contained, for any sheet or screen.
 */
@Composable
fun ShortcutModeControl(mode: ShortcutMode, minutes: Int, onMode: (ShortcutMode) -> Unit, shape: Shape, modifier: Modifier = Modifier) {
    var details by remember { mutableStateOf(false) }
    val infoLabel = stringResource(R.string.shortcut_mode_info)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.Card)
            .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = stringResource(R.string.shortcut_mode_title), color = AppColors.Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(text = stringResource(R.string.shortcut_mode_text), color = AppColors.Muted, fontSize = 13.sp, lineHeight = 17.sp)
            }
            Text(
                text = "ⓘ",
                color = if (details) AppColors.Blue else AppColors.Muted,
                fontSize = 18.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClickLabel = infoLabel) { details = !details }
                    .semantics { contentDescription = infoLabel }
                    .defaultMinSize(minWidth = 40.dp, minHeight = 40.dp)
                    .padding(8.dp),
            )
        }
        SettingReveal(visible = details) {
            Text(
                text = stringResource(R.string.shortcut_mode_details, minutes),
                color = AppColors.Muted,
                fontSize = 13.sp,
                lineHeight = 17.sp,
            )
        }
        SegmentedToggle(
            labels = listOf(
                stringResource(R.string.shortcut_mode_off),
                stringResource(R.string.shortcut_mode_from, minutes),
                stringResource(R.string.shortcut_mode_every_minute),
            ),
            selected = mode.ordinal,
            onSelect = { index -> onMode(ShortcutMode.entries[index]) },
            modifier = Modifier.fillMaxWidth(),
            minHeight = 40.dp,
            ground = AppColors.Chip,
            fill = true,
        )
    }
}

/**
 * The wake-up alarm of the followed [journey] as a tile of its own in the companion's bottom
 * row: the alarm icon and, while it is on, the time it rings; a tap switches it for this trip ([WakeAlarm]).
 * The colours move softly between on and off. Self-contained: reads the alarm's state itself.
 */
@Composable
fun WakeAlarmTile(journey: Journey, now: OffsetDateTime, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val forTrip by WakeAlarm.forTrip.collectAsState()
    val prefs = remember(context) { AppPrefs(context) }
    val targets = remember(journey, forTrip) { WakeAlarm.targets(context, journey) }
    val on = forTrip ?: targets.isNotEmpty()
    val next = targets.firstOrNull { it.arrival.isAfter(now) }
    val ground by animateColorAsState(if (on) AppColors.Blue else AppColors.Card, Motion.quick(), label = "wakeTileGround")
    val ink by animateColorAsState(if (on) AppColors.OnBlue else AppColors.Ink, Motion.quick(), label = "wakeTileInk")
    val description = if (on) {
        stringResource(R.string.wake_tile_on, next?.at?.clock() ?: stringResource(R.string.wake_tile_no_ring))
    } else {
        stringResource(R.string.wake_tile_off, prefs.wakeAlarmMinutes)
    }
    val interactions = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .then(appearModifier(2))
            .semantics { contentDescription = description }
            .springPress(interactions)
            .clip(RoundedCornerShape(10.dp))
            .background(ground)
            .clickable(interactionSource = interactions, indication = LocalIndication.current) { WakeAlarm.setForTrip(context, !on) }
            .width(size)
            .height(size),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        GlyphIcon(Glyph.Alarm, color = ink, size = 20.dp)
        FadeSwap(target = if (on) next?.at?.clock().orEmpty() else stringResource(R.string.wake_alarm_off)) { label ->
            if (label.isNotEmpty()) Text(text = label, color = ink, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 1)
        }
    }
}

/**
 * The ⋯ of the companion's bottom row: the quiet actions that need no place of their own, today
 * "End trip". A narrow tile of [height]; its menu opens right at it.
 */
@Composable
fun CompanionOverflowTile(onEnd: () -> Unit, height: Dp, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.more_actions)
    val interactions = remember { MutableInteractionSource() }
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .then(appearModifier(3))
                .semantics { contentDescription = label }
                .springPress(interactions)
                .clip(RoundedCornerShape(10.dp, 28.dp, 28.dp, 10.dp))
                .background(AppColors.Card)
                .clickable(interactionSource = interactions, indication = LocalIndication.current) { open = true }
                .width(44.dp)
                .height(height),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(Glyph.More, color = AppColors.Ink, size = 20.dp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.trip_end_action)) },
                leadingIcon = { GlyphIcon(glyph = Glyph.Close, size = 20.dp) },
                onClick = {
                    open = false
                    onEnd()
                },
            )
        }
    }
}
