package io.github.jnbp.nextstop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.BatterySaverLevel
import io.github.jnbp.nextstop.data.BatterySaverMode
import io.github.jnbp.nextstop.data.BikePace
import io.github.jnbp.nextstop.data.UxPrefs
import io.github.jnbp.nextstop.ui.theme.AppColors

/*
 * The settings of release 0.2 for handling and battery: the battery saver and the haptics as
 * a group of their own, and the pace by bicycle as a row for the group of the tickets. Each
 * reads and writes its setting itself (BatterySaver, HapticSetting, UxPrefs), so nothing has
 * to be handed down from the root of the app.
 */

/**
 * "Bedienung": the haptics, with a live preview, and the hint of the sunny side (the battery
 * saver has a category of its own, see [BatterySaverSettings]).
 */
@Composable
internal fun ComfortSettings(label: String? = stringResource(R.string.settings_group_comfort)) {
    SettingsGroup(label = label) {
        HapticsRow(shape = groupShape(0, 1))
        SunSideRow(shape = groupShape(1, 1))
    }
}

/** The category "Akku": the battery saver, on its own so it is easy to find. */
@Composable
internal fun BatterySaverSettings() {
    SettingsGroup(label = null) {
        BatterySaverRow(shape = groupShape(0, 0))
    }
}

/** The summary of the category "Akku": when it saves, how far, and whether it saves right now. */
@Composable
internal fun batterySaverSummary(): String {
    val mode = BatterySaver.mode
    val parts = mutableListOf(
        stringResource(
            when (mode) {
                BatterySaverMode.Off -> R.string.battery_saver_off
                BatterySaverMode.Auto -> R.string.battery_saver_auto
                BatterySaverMode.Always -> R.string.battery_saver_always
            },
        ),
    )
    if (mode != BatterySaverMode.Off) {
        parts += stringResource(
            if (BatterySaver.level == BatterySaverLevel.Extreme) R.string.battery_saver_level_extreme else R.string.battery_saver_level_save,
        )
    }
    if (BatterySaver.active) parts += stringResource(R.string.battery_saver_now_on)
    return parts.joinToString(" · ")
}

/**
 * The battery saver: when (off, automatic, always), how far (save or extreme, shown while it
 * is not off), a line whether it saves right now, and its preview.
 */
@Composable
internal fun BatterySaverRow(shape: androidx.compose.ui.graphics.Shape) {
    val context = LocalContext.current
    val mode = BatterySaver.mode
    val level = BatterySaver.level
    SettingsRow(
        shape = shape,
        title = stringResource(R.string.battery_saver_title),
        text = stringResource(R.string.battery_saver_text),
        preview = { BatterySaverPreview(BatterySaver.active) },
        // A tap on the preview turns the saving over: off while it saves, else always on.
        previewTap = {
            BatterySaver.change(context, if (BatterySaver.active) BatterySaverMode.Off else BatterySaverMode.Always)
        },
    ) {
        SegmentedToggle(
            labels = listOf(
                stringResource(R.string.battery_saver_off),
                stringResource(R.string.battery_saver_auto),
                stringResource(R.string.battery_saver_always),
            ),
            selected = BatterySaverMode.entries.indexOf(mode),
            onSelect = { index -> BatterySaver.change(context, BatterySaverMode.entries[index]) },
            minHeight = 40.dp,
            ground = AppColors.Chip,
        )
        // How far it goes: only asked while it can save at all.
        SettingReveal(visible = mode != BatterySaverMode.Off) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SegmentedToggle(
                    labels = listOf(
                        stringResource(R.string.battery_saver_level_save),
                        stringResource(R.string.battery_saver_level_extreme),
                    ),
                    selected = BatterySaverLevel.entries.indexOf(level),
                    onSelect = { index -> BatterySaver.changeLevel(context, BatterySaverLevel.entries[index]) },
                    minHeight = 40.dp,
                    ground = AppColors.Chip,
                    fill = true,
                )
                AnimatedContent(targetState = level, label = "saverLevel") { shown ->
                    Text(
                        text = stringResource(
                            if (shown == BatterySaverLevel.Extreme) R.string.battery_saver_level_extreme_text else R.string.battery_saver_level_save_text,
                        ),
                        color = AppColors.Muted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
        }
        // Whether it saves right now: with "automatic" that follows the system.
        AnimatedContent(targetState = BatterySaver.active, label = "saverNow") { saving ->
            Text(
                text = stringResource(if (saving) R.string.battery_saver_now_on else R.string.battery_saver_now_off) +
                    if (mode == BatterySaverMode.Auto) " · " + stringResource(R.string.battery_saver_auto_text) else "",
                color = if (saving) AppColors.Early else AppColors.Muted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

/** Haptics on taps: on by default, light ticks. */
@Composable
internal fun HapticsRow(shape: androidx.compose.ui.graphics.Shape) {
    val context = LocalContext.current
    SwitchRow(
        shape = shape,
        title = stringResource(R.string.haptics_title),
        text = stringResource(R.string.haptics_text),
        preview = { HapticsPreview(HapticSetting.on) },
        checked = HapticSetting.on,
        onChange = { value -> HapticSetting.update(context, value) },
    )
}

/**
 * The pace by bicycle: relaxed, normal, fast or one's own, the own one set in steps of 1 km/h
 * between BikePace.MIN_KMH and BikePace.MAX_KMH. Used by the next search.
 */
@Composable
internal fun BikeSpeedRow(shape: androidx.compose.ui.graphics.Shape) {
    val context = LocalContext.current
    var kmh by remember { mutableIntStateOf(UxPrefs(context).bikeSpeedKmh) }
    // "Own" stays picked while its value happens to equal a preset.
    var own by remember { mutableStateOf(BikePace.presetIndex(kmh) < 0) }
    fun store(value: Int) {
        kmh = BikePace.clamp(value)
        BikePace.kmh = kmh
        UxPrefs(context).bikeSpeedKmh = kmh
    }
    val presetLabels = listOf(
        stringResource(R.string.bike_speed_relaxed),
        stringResource(R.string.bike_speed_normal),
        stringResource(R.string.bike_speed_fast),
    )
    SettingsRow(
        shape = shape,
        title = stringResource(R.string.bike_speed_title),
        text = stringResource(R.string.bike_speed_text),
        scroll = false,
        preview = { BikeSpeedPreview(kmh) },
        // A tap on the preview steps through the three speeds (an own speed goes on with the first).
        previewTap = {
            own = false
            store(BikePace.PRESETS[cycleIndex(BikePace.presetIndex(kmh), BikePace.PRESETS.size)])
        },
    ) {
        SegmentedToggle(
            labels = presetLabels + stringResource(R.string.bike_speed_custom),
            selected = if (own) BikePace.PRESETS.size else BikePace.presetIndex(kmh),
            onSelect = { index ->
                if (index < BikePace.PRESETS.size) {
                    own = false
                    store(BikePace.PRESETS[index])
                } else {
                    own = true
                }
            },
            minHeight = 40.dp,
            ground = AppColors.Chip,
            fill = true,
        )
        SettingReveal(visible = own) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.bike_speed_custom_label, BikePace.MIN_KMH, BikePace.MAX_KMH),
                    color = AppColors.Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                StepButton("−", enabled = kmh > BikePace.MIN_KMH) { store(kmh - 1) }
                AnimatedContent(
                    targetState = kmh,
                    transitionSpec = { rollTransform(up = targetState > initialState) },
                    label = "bikeKmh",
                ) { value ->
                    Text(
                        text = stringResource(R.string.bike_speed_kmh, value),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(min = 64.dp),
                    )
                }
                StepButton("+", enabled = kmh < BikePace.MAX_KMH) { store(kmh + 1) }
            }
        }
        // The pace of a preset, named in km/h below the parts.
        if (!own) {
            Text(text = stringResource(R.string.bike_speed_kmh, kmh), color = AppColors.Muted, fontSize = 12.sp)
        }
    }
}

/** A round "−" or "+" for a value set in steps; a light tick on each step. */
@Composable
private fun StepButton(sign: String, enabled: Boolean, size: Dp = 36.dp, onClick: () -> Unit) {
    val ticks = rememberTicks()
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (enabled) AppColors.Chip else AppColors.Chip.copy(alpha = 0.4f))
            .clickable(enabled = enabled) {
                ticks.tick()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = sign, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if (enabled) AppColors.Ink else AppColors.Muted)
    }
}

// The choices of how long after its start an appointment is still offered, in minutes.
private val APPOINTMENT_AFTER_CHOICES = intArrayOf(0, 30, 60, 120)

/** How long after its start an appointment stays on the start page and in the calendar list. */
@Composable
internal fun AppointmentsAfterRow(shape: androidx.compose.ui.graphics.Shape) {
    val context = LocalContext.current
    var minutes by remember { mutableIntStateOf(UxPrefs(context).appointmentAfterMinutes) }
    SettingsRow(
        shape = shape,
        title = stringResource(R.string.appointments_after_title),
        text = stringResource(R.string.appointments_after_text),
        preview = { AppointmentsAfterPreview(minutes) },
    ) {
        val labels = APPOINTMENT_AFTER_CHOICES.map { stringResource(R.string.appointments_after_minutes, it) }
        SegmentedToggle(
            labels = labels,
            selected = APPOINTMENT_AFTER_CHOICES.indexOf(minutes).coerceAtLeast(0),
            onSelect = { index ->
                minutes = APPOINTMENT_AFTER_CHOICES[index]
                UxPrefs(context).appointmentAfterMinutes = minutes
            },
            minHeight = 40.dp,
            ground = AppColors.Chip,
        )
    }
}
