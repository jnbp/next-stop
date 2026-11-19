package io.github.jnbp.nextstop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.data.PlaceKind
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.TransferComfort
import io.github.jnbp.nextstop.data.shortStopName
import io.github.jnbp.nextstop.ui.theme.AppColors
import io.github.jnbp.nextstop.wear.WatchSettingKeys
import io.github.jnbp.nextstop.wear.WatchSettings
import io.github.jnbp.nextstop.wear.WatchState
import io.github.jnbp.nextstop.wear.WatchStatus
import io.github.jnbp.nextstop.wear.settingFlag

/*
 * The category "Watch" of the settings: the state of the watch and every setting of the watch
 * app, each with its line of explanation and a "Preview" of a round watch drawn in Compose
 * (schematic, as the other previews). The watch's own settings (live departures on its start
 * page, animations, map style, the destination of its tile) are edited in the phone's copy
 * WatchSettings and reach the watch over the Data Layer, also later when it is away now (a
 * hint says so); the reminder before getting off is the phone's own setting, which the watch
 * follows; the change time of the watch's searches is the one of the phone's searches.
 */

// The rows of the group: the state of the watch, the hint, then the settings (for groupShape).
private const val WATCH_GROUP_LAST = 9

// The watch map styles, in the order of the choice; the names are those the watch stores (MapStyleSetting).
private val WATCH_MAP_STYLES = listOf("Auto", "Light", "Dark")

/** The whole category "Watch": state, hint while it is away, and every setting of the watch app. */
@Composable
internal fun WatchSettingsGroup(status: WatchStatus?) {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { WatchSettings.load(context) }
        // What was changed on the watch meanwhile, in case its item came while the app was not listening.
        WatchSettings.refreshFromWatch(context)
    }
    val settings by WatchSettings.values.collectAsState()
    val live = settingFlag(settings, WatchSettingKeys.LIVE_DEPARTURES, default = true)
    val animations = settingFlag(settings, WatchSettingKeys.ANIMATIONS, default = true)
    val style = settings[WatchSettingKeys.MAP_STYLE]?.value?.takeIf { it in WATCH_MAP_STYLES } ?: WATCH_MAP_STYLES[0]
    val target = settings[WatchSettingKeys.TILE_TARGET]?.value.orEmpty()
    val openCompanion = settingFlag(settings, WatchSettingKeys.OPEN_COMPANION, default = false)
    val stateIcons = settingFlag(settings, WatchSettingKeys.STATE_ICONS, default = false)
    val timeTravel = settingFlag(settings, WatchSettingKeys.CROWN_TIME_TRAVEL, default = false)
    SettingsGroup(label = stringResource(R.string.watch_group)) {
        WatchStatusRow(status, shape = groupShape(0, WATCH_GROUP_LAST))
        // Changes made while the watch is away wait in the Data Layer and arrive once it connects.
        if (status != null && status.state != WatchState.Connected) {
            Text(
                text = stringResource(R.string.watch_settings_pending),
                color = AppColors.Muted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        SwitchRow(
            shape = groupShape(1, WATCH_GROUP_LAST),
            title = stringResource(R.string.watch_live_departures_title),
            text = stringResource(R.string.watch_live_departures_text),
            checked = live,
            onChange = { WatchSettings.set(context, WatchSettingKeys.LIVE_DEPARTURES, it.toString()) },
            preview = { WatchStartPreview(live) },
        )
        SwitchRow(
            shape = groupShape(2, WATCH_GROUP_LAST),
            title = stringResource(R.string.watch_animations_title),
            text = stringResource(R.string.watch_animations_text),
            checked = animations,
            onChange = { WatchSettings.set(context, WatchSettingKeys.ANIMATIONS, it.toString()) },
            preview = { WatchAnimationsPreview(animations) },
        )
        SettingsRow(
            shape = groupShape(3, WATCH_GROUP_LAST),
            title = stringResource(R.string.watch_map_style_title),
            text = stringResource(R.string.watch_map_style_text),
            preview = { WatchMapStylePreview(WATCH_MAP_STYLES.indexOf(style)) },
            // A tap on one of the small watches picks its style.
            previewPick = { index -> WatchSettings.set(context, WatchSettingKeys.MAP_STYLE, WATCH_MAP_STYLES[index]) },
        ) {
            SegmentedToggle(
                labels = watchMapStyleLabels(),
                selected = WATCH_MAP_STYLES.indexOf(style),
                onSelect = { index -> WatchSettings.set(context, WatchSettingKeys.MAP_STYLE, WATCH_MAP_STYLES[index]) },
                minHeight = 40.dp,
                ground = AppColors.Chip,
            )
        }
        WatchTileTargetRow(prefs, target, onTarget = { WatchSettings.set(context, WatchSettingKeys.TILE_TARGET, it) })
        var reminders by remember { mutableStateOf(prefs.alightReminders) }
        SwitchRow(
            shape = groupShape(5, WATCH_GROUP_LAST),
            title = stringResource(R.string.alight_reminders_title),
            text = stringResource(R.string.watch_reminder_text),
            checked = reminders,
            onChange = { value ->
                reminders = value
                // The phone's own setting: the watch follows it (PhoneBridge sends it with the places).
                prefs.alightReminders = value
                AlightReminders.check(context)
            },
            preview = { WatchReminderPreview(reminders) },
        )
        SettingsRow(
            shape = groupShape(6, WATCH_GROUP_LAST),
            title = stringResource(R.string.watch_comfort_title),
            text = stringResource(R.string.watch_comfort_text, stringResource(watchComfortName(prefs.searchOptionsInUse.comfort))),
        ) {}
        // A trip started here opens the companion on the watch by itself (off by default).
        SwitchRow(
            shape = groupShape(7, WATCH_GROUP_LAST),
            title = stringResource(R.string.watch_open_companion_title),
            text = stringResource(R.string.watch_open_companion_text),
            checked = openCompanion,
            onChange = { WatchSettings.set(context, WatchSettingKeys.OPEN_COMPANION, it.toString()) },
        )
        // The watch's complications and the trip's chip show the app's icon, or with this the complications their own (#282).
        SwitchRow(
            shape = groupShape(8, WATCH_GROUP_LAST),
            title = stringResource(R.string.r8wear_state_icons_title),
            text = stringResource(R.string.r8wear_state_icons_text),
            checked = stateIcons,
            onChange = { WatchSettings.set(context, WatchSettingKeys.STATE_ICONS, it.toString()) },
        )
        // The crown on the watch's trip overview shows the time travel pill (off: it just scrolls).
        SwitchRow(
            shape = groupShape(9, WATCH_GROUP_LAST),
            title = stringResource(R.string.r8wear_time_travel_title),
            text = stringResource(R.string.r8wear_time_travel_text),
            checked = timeTravel,
            onChange = { WatchSettings.set(context, WatchSettingKeys.CROWN_TIME_TRAVEL, it.toString()) },
        )
    }
}

/** The labels of the three map styles of the watch: automatic, light, dark. */
@Composable
private fun watchMapStyleLabels(): List<String> = listOf(
    stringResource(R.string.map_style_auto),
    stringResource(R.string.map_style_light),
    stringResource(R.string.map_style_dark),
)

private fun watchComfortName(level: TransferComfort): Int = when (level) {
    TransferComfort.Relaxed -> R.string.comfort_relaxed
    TransferComfort.Normal -> R.string.comfort_normal
    TransferComfort.Risky -> R.string.comfort_risky
    TransferComfort.VeryRisky -> R.string.comfort_very_risky
}

/**
 * The destination of the watch's tile "To a favourite": "Home" (the watch then takes the
 * favourite marked as home) or one of the favourites and saved places, as chips.
 */
@Composable
private fun WatchTileTargetRow(prefs: AppPrefs, target: String, onTarget: (String) -> Unit) {
    val places = remember {
        (prefs.loadFavoritePlaces().mapNotNull { favorite -> favorite.place?.let { it to favorite.name } } +
            prefs.loadSavedPlaces().map { it.place to it.name })
            .distinctBy { it.first.id }
    }
    val chosenId = remember(target) { runCatching { JSONObject(target).optString("id") }.getOrNull().orEmpty() }
    val chosenName = remember(target) { runCatching { JSONObject(target).optString("name") }.getOrNull().orEmpty() }
    SettingsRow(
        shape = groupShape(4, WATCH_GROUP_LAST),
        title = stringResource(R.string.watch_tile_title),
        text = stringResource(R.string.watch_tile_text),
        preview = { WatchTilePreview(chosenName.ifEmpty { stringResource(R.string.home) }) },
    ) {
        val chips = listOf<Pair<String, () -> Unit>>(stringResource(R.string.watch_tile_home) to { onTarget("") }) +
            places.map { (place, name) -> name.ifEmpty { place.name.shortStopName() } to { onTarget(watchPlaceJson(place)) } }
        val ids = listOf("") + places.map { it.first.id }
        // The chips wrap onto further lines where they do not fit.
        WrapRow {
            chips.indices.forEach { index ->
                ToggleChip(text = chips[index].first, on = ids[index] == chosenId, onClick = chips[index].second)
            }
        }
    }
}

/** A place as the watch reads it (its parsePlaceJson): id, name, lat, lon, kind. */
private fun watchPlaceJson(place: Stop): String = JSONObject().apply {
    put("id", place.id)
    put("name", place.name)
    place.latitude?.let { put("lat", it) }
    place.longitude?.let { put("lon", it) }
    put(
        "kind",
        when (place.kind) {
            PlaceKind.Stop -> "stop"
            PlaceKind.Poi -> "poi"
            PlaceKind.Address -> "address"
        },
    )
}.toString()

// ---------------------------------------------------------------------------------------
// The previews: a round watch, drawn.
// ---------------------------------------------------------------------------------------

/** The face of a round watch in the middle of the sketch, [dark] or light; returns its centre and radius. */
private fun DrawScope.watchFace(dark: Boolean = true): Pair<Offset, Float> {
    val radius = size.height / 2 - 6.dp.toPx()
    val centre = Offset(size.width / 2, size.height / 2)
    drawCircle(AppColors.Muted.copy(alpha = 0.5f), radius = radius + 3.dp.toPx(), center = centre)
    drawCircle(if (dark) Color.Black else Color.White, radius = radius, center = centre)
    return centre to radius
}

/** A rounded bar of the sketch: a row of text or a button. */
private fun DrawScope.bar(color: Color, left: Float, top: Float, width: Float, height: Float) {
    drawRoundRect(color, Offset(left, top), Size(width, height), CornerRadius(height / 2, height / 2))
}

/** The watch's start page: with the setting on, departure rows with big minutes; off, one button. */
@Composable
internal fun WatchStartPreview(on: Boolean) {
    SketchPane(key = on) { reveal ->
        val (centre, radius) = watchFace()
        val left = centre.x - radius * 0.62f
        val width = radius * 1.24f
        val row = radius * 0.2f
        bar(AppColors.Muted, centre.x - radius * 0.35f, centre.y - radius * 0.7f, radius * 0.7f, row * 0.45f)
        if (on) {
            val colors = listOf(lineColor("U2", "subway"), lineColor("S5", "suburban"))
            colors.forEachIndexed { index, color ->
                val y = centre.y - radius * 0.38f + index * row * 1.6f + (1f - reveal) * row
                bar(color, left, y, row * 1.4f, row)
                bar(Color.White.copy(alpha = 0.7f), left + row * 1.7f, y + row * 0.3f, width * 0.42f, row * 0.4f)
                bar(Color.White, left + width - row * 1.1f, y - row * 0.1f, row * 1.1f, row * 1.2f)
            }
            bar(AppColors.Blue.copy(alpha = 0.6f), left, centre.y + radius * 0.32f, width, row * 1.4f)
        } else {
            bar(AppColors.Blue.copy(alpha = 0.6f), left, centre.y - radius * 0.3f, width, row * 1.6f)
            bar(AppColors.Blue, left, centre.y + radius * 0.05f, width, row * 1.6f)
        }
    }
}

/** Animations on: rows that slide in one after another; off: the same rows simply there. */
@Composable
internal fun WatchAnimationsPreview(on: Boolean) {
    SketchPane(key = on) { reveal ->
        val (centre, radius) = watchFace()
        val left = centre.x - radius * 0.6f
        val width = radius * 1.2f
        val row = radius * 0.28f
        for (index in 0 until 3) {
            // Each row a little behind the one above it, as the watch lets them enter.
            val shown = if (on) ((reveal - index * 0.2f) / 0.6f).coerceIn(0f, 1f) else 1f
            val y = centre.y - radius * 0.5f + index * row * 1.35f + (1f - shown) * row
            bar(AppColors.Blue.copy(alpha = 0.35f + 0.45f * shown), left, y, width, row)
        }
    }
}

/** The three map styles of the watch side by side, the chosen one ringed. */
@Composable
internal fun WatchMapStylePreview(selected: Int) {
    SketchOptions(labels = watchMapStyleLabels(), selected = selected) { index, _ ->
        // Automatic is dark on the watch (its screen is black).
        val dark = index != 1
        val (centre, radius) = watchFace(dark)
        val ink = if (dark) Color.White.copy(alpha = 0.35f) else AppColors.Muted
        clipPath(Path().apply { addOval(Rect(centre, radius)) }) {
            drawLine(ink, Offset(centre.x - radius, centre.y - radius * 0.2f), Offset(centre.x + radius, centre.y + radius * 0.3f), strokeWidth = 3.dp.toPx())
            drawLine(ink, Offset(centre.x - radius * 0.2f, centre.y - radius), Offset(centre.x + radius * 0.1f, centre.y + radius), strokeWidth = 2.dp.toPx())
        }
        drawCircle(AppColors.Blue, radius = 4.dp.toPx(), center = centre)
    }
}

/** The tile "To a favourite": the destination's name as a bar, the next connection, a button. */
@Composable
internal fun WatchTilePreview(name: String) {
    SketchPane(key = name) { reveal ->
        val (centre, radius) = watchFace()
        val row = radius * 0.16f
        bar(AppColors.Muted, centre.x - radius * 0.4f, centre.y - radius * 0.62f, radius * 0.8f * reveal.coerceAtLeast(0.3f), row)
        bar(Color.White, centre.x - radius * 0.55f, centre.y - radius * 0.25f, radius * 1.1f, row * 1.5f)
        bar(Color.White.copy(alpha = 0.6f), centre.x - radius * 0.45f, centre.y + radius * 0.05f, radius * 0.9f, row)
        bar(AppColors.Blue, centre.x - radius * 0.45f, centre.y + radius * 0.42f, radius * 0.9f, row * 1.8f)
    }
}

/** The reminder before getting off: the ride's ring along the edge, and the buzz at its end. */
@Composable
internal fun WatchReminderPreview(on: Boolean) {
    SketchPane(key = on) { reveal ->
        val (centre, radius) = watchFace()
        val stroke = 5.dp.toPx()
        val inset = radius - stroke
        val topLeft = Offset(centre.x - inset, centre.y - inset)
        val arc = Size(inset * 2, inset * 2)
        drawArc(AppColors.Muted.copy(alpha = 0.4f), -60f, 300f, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
        drawArc(lineColor("S5", "suburban"), -60f, 300f * 0.85f * reveal, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
        if (on) {
            // Vibration marks beside the watch.
            for (side in listOf(-1f, 1f)) {
                for (k in 0 until 2) {
                    val x = centre.x + side * (radius + 10.dp.toPx() + k * 6.dp.toPx())
                    drawLine(AppColors.Blue, Offset(x, centre.y - 8.dp.toPx()), Offset(x, centre.y + 8.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                }
            }
        }
    }
}
