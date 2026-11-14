package io.github.jnbp.nextstop.ui

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.Accessibility
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.data.DelayDisplay
import io.github.jnbp.nextstop.data.SearchOptions
import io.github.jnbp.nextstop.data.SourceChoice
import io.github.jnbp.nextstop.ui.theme.AppColors
import io.github.jnbp.nextstop.ui.theme.AppFont
import io.github.jnbp.nextstop.ui.theme.Appearance
import io.github.jnbp.nextstop.ui.theme.ColorChoice
import io.github.jnbp.nextstop.ui.theme.DarkChoice
import io.github.jnbp.nextstop.ui.theme.MapStyleChoice
import io.github.jnbp.nextstop.ui.theme.NotificationLook
import io.github.jnbp.nextstop.ui.theme.VehicleDirectionChoice
import io.github.jnbp.nextstop.widget.WidgetColorChoice
import io.github.jnbp.nextstop.widget.WidgetUpdates

/*
 * The groups of the settings page (see SettingsScreen): the card that starts the setup again,
 * the everyday groups with small schematic previews as in the setup, and the page "Advanced"
 * with everything technical (data sources, keys, own server, live page, developer options).
 */

/** "Start setup again" at the very top, in the look of the setup: its logo on the blue of the app. */
@Composable
internal fun SetupRestartCard() {
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(AppColors.Blue, AppColors.Blue.copy(alpha = 0.82f))))
            .clickable(onClickLabel = stringResource(R.string.setup_restart_title)) { SetupState.open = true }
            .padding(start = 14.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp)),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_background),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.setup_restart_title),
                color = AppColors.OnBlue,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
            )
            Text(
                text = stringResource(R.string.setup_restart_short),
                color = AppColors.OnBlue.copy(alpha = 0.85f),
                fontSize = 13.sp,
                lineHeight = 17.sp,
            )
        }
        Text(text = "›", color = AppColors.OnBlue, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    }
}

/** Appearance: colours, light or dark, how delays look (with a live sample), and the widgets. */
@Composable
internal fun AppearanceSettings(delayDisplay: DelayDisplay, onDelayDisplay: (DelayDisplay) -> Unit) {
    val context = LocalContext.current
    val custom = Appearance.colorChoice == ColorChoice.Custom
    // The rows in order: scheme, accent colour (only for an own colour), light or dark, delays, widgets.
    val darkRow = if (custom) 2 else 1
    val delayRow = darkRow + 1
    val widgetRow = delayRow + 1
    var widgetColors by remember { mutableStateOf(AppPrefs(context).widgetColors) }
    SettingsGroup(label = stringResource(R.string.settings_group_appearance)) {
        SettingsRow(
            shape = groupShape(0, widgetRow),
            title = stringResource(R.string.color_title),
            text = stringResource(R.string.color_text),
            preview = { ColorSchemePreview(Appearance.colorChoice) },
        ) {
            SegmentedToggle(
                labels = listOf(
                    stringResource(R.string.color_next_stop),
                    stringResource(R.string.color_system),
                    stringResource(R.string.color_custom),
                ),
                selected = ColorChoice.entries.indexOf(Appearance.colorChoice),
                onSelect = { index -> Appearance.changeColorChoice(context, ColorChoice.entries[index]) },
                minHeight = 40.dp,
                ground = AppColors.Chip,
            )
        }
        SettingReveal(visible = custom) {
            SettingsRow(
                shape = groupShape(1, widgetRow),
                title = stringResource(R.string.accent_title),
                text = "",
            ) {
                AccentSwatches(
                    selected = Appearance.accent,
                    onSelect = { Appearance.changeAccent(context, it) },
                )
            }
        }
        SettingsRow(
            shape = groupShape(darkRow, widgetRow),
            title = stringResource(R.string.dark_title),
            text = stringResource(R.string.dark_text),
            preview = { DarkModePreview(Appearance.darkChoice) },
        ) {
            SegmentedToggle(
                labels = listOf(
                    stringResource(R.string.dark_light),
                    stringResource(R.string.dark_dark),
                    stringResource(R.string.dark_system),
                ),
                selected = DarkChoice.entries.indexOf(Appearance.darkChoice),
                onSelect = { index -> Appearance.changeDarkChoice(context, DarkChoice.entries[index]) },
                minHeight = 40.dp,
                ground = AppColors.Chip,
            )
        }
        SettingsRow(
            shape = groupShape(delayRow, widgetRow),
            title = stringResource(R.string.delay_display_title),
            text = stringResource(R.string.delay_display_text),
            scroll = false,
        ) {
            DelayDisplayPicker(selected = delayDisplay, onSelect = onDelayDisplay)
        }
        SettingsRow(
            shape = groupShape(widgetRow, widgetRow),
            title = stringResource(R.string.widget_colors_title),
            text = stringResource(R.string.widget_colors_text),
            preview = { WidgetColorsPreview(widgetColors) },
        ) {
            SegmentedToggle(
                labels = listOf(
                    stringResource(R.string.widget_colors_app),
                    stringResource(R.string.widget_colors_material),
                ),
                selected = WidgetColorChoice.entries.indexOf(widgetColors),
                onSelect = { index ->
                    widgetColors = WidgetColorChoice.entries[index]
                    AppPrefs(context).widgetColors = widgetColors
                    WidgetUpdates.redrawAll(context)
                },
                minHeight = 40.dp,
                ground = AppColors.Chip,
            )
        }
    }
}

/** The map: its style as small maps (as in the setup), the vehicles and what shows close up. */
@Composable
internal fun MapSettings(
    startVehicles: Boolean,
    onStartVehicles: (Boolean) -> Unit,
    fitLine: Boolean,
    onFitLine: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val last = 8
    SettingsGroup(label = stringResource(R.string.settings_group_map)) {
        SettingsRow(
            shape = groupShape(0, last),
            title = stringResource(R.string.map_style_title),
            text = stringResource(R.string.map_style_text),
            scroll = false,
        ) {
            // Two by two, so every name fits; tapped, the style is the map's at once.
            val styles = listOf(
                MapStyleChoice.Light to R.string.map_style_light,
                MapStyleChoice.Colorful to R.string.map_style_colorful,
                MapStyleChoice.Dark to R.string.map_style_dark,
                MapStyleChoice.Auto to R.string.map_style_auto,
            )
            Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                styles.chunked(2).forEach { pair ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { (style, label) ->
                            val picked = Appearance.mapStyle == style
                            val reveal = rememberReveal(picked)
                            VisualOption(
                                label = stringResource(label),
                                selected = picked,
                                onClick = { Appearance.changeMapStyle(context, style) },
                                modifier = Modifier.weight(1f),
                                previewHeight = 56.dp,
                            ) { mapStyleSketch(style, reveal.value) }
                        }
                    }
                }
            }
        }
        SettingsRow(
            shape = groupShape(1, last),
            title = stringResource(R.string.vehicle_direction_title),
            text = stringResource(R.string.vehicle_direction_text),
            preview = { VehicleDirectionPreview(Appearance.vehicleDirection) },
        ) {
            SegmentedToggle(
                labels = listOf(
                    stringResource(R.string.vehicle_direction_off),
                    stringResource(R.string.vehicle_direction_on),
                ),
                selected = VehicleDirectionChoice.entries.indexOf(Appearance.vehicleDirection),
                onSelect = { index -> Appearance.changeVehicleDirection(context, VehicleDirectionChoice.entries[index]) },
                minHeight = 40.dp,
                ground = AppColors.Chip,
            )
        }
        SwitchRow(
            shape = groupShape(2, last),
            title = stringResource(R.string.start_vehicles_title),
            text = stringResource(R.string.start_vehicles_text),
            preview = { StartVehiclesPreview(startVehicles) },
            checked = startVehicles,
            onChange = onStartVehicles,
        )
        // Stop positions, entrances and lifts from OpenStreetMap on the map, close up.
        SwitchRow(
            shape = groupShape(3, last),
            title = stringResource(R.string.station_details_title),
            text = stringResource(R.string.station_details_text),
            preview = { StationDetailsPreview(StationDetailSetting.shown) },
            checked = StationDetailSetting.shown,
            onChange = { value -> StationDetailSetting.update(context, value) },
        )
        // Reserved: the setting "show entrances and exits" (automatic / always / only at the
        // selected station) of the map2 work goes here, after the station details.
        EntranceModeRow(shape = groupShape(4, last))
        SwitchRow(
            shape = groupShape(5, last),
            title = stringResource(R.string.stop_names_title),
            text = stringResource(R.string.stop_names_text),
            preview = { StopNamesPreview(StopNameSetting.named) },
            checked = StopNameSetting.named,
            onChange = { value -> StopNameSetting.update(context, value) },
        )
        // A warning sign at the stops known disruptions are about (see MapNoticeLayer).
        SwitchRow(
            shape = groupShape(6, last),
            title = stringResource(R.string.map_notices_title),
            text = stringResource(R.string.map_notices_text),
            preview = { MapNoticesPreview(MapNoticeSetting.shown) },
            checked = MapNoticeSetting.shown,
            onChange = { value -> MapNoticeSetting.update(context, value) },
        )
        // The small hint on the map that vehicle positions come from live data, not GPS.
        var vehicleHint by remember { mutableStateOf(VehicleHintSetting.shown(context)) }
        SwitchRow(
            shape = groupShape(7, last),
            title = stringResource(R.string.vehicle_hint_setting_title),
            text = stringResource(R.string.vehicle_hint_setting_text),
            preview = { VehicleHintPreview(vehicleHint) },
            checked = vehicleHint,
            onChange = { value ->
                vehicleHint = value
                VehicleHintSetting.update(context, value)
            },
        )
        SwitchRow(
            shape = groupShape(8, last),
            title = stringResource(R.string.fit_line_title),
            text = stringResource(R.string.fit_line_text),
            preview = { FitLinePreview(fitLine) },
            checked = fitLine,
            onChange = onFitLine,
        )
    }
}

/** The look of the notification of a trip; only on Android 16 and later, where a Live Update exists. */
@Composable
internal fun NotificationLookCard() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return
    val context = LocalContext.current
    SettingsGroup(label = null) {
        SettingsRow(
            shape = groupShape(0, 0),
            title = stringResource(R.string.notification_look_title),
            text = stringResource(R.string.notification_look_text),
            preview = { NotificationLookPreview(Appearance.notificationLook) },
        ) {
            SegmentedToggle(
                labels = listOf(
                    stringResource(R.string.notification_look_live),
                    stringResource(R.string.notification_look_app),
                ),
                selected = NotificationLook.entries.indexOf(Appearance.notificationLook),
                onSelect = { index ->
                    Appearance.changeNotificationLook(context, NotificationLook.entries[index])
                    refreshTripNotification(context)
                },
                minHeight = 40.dp,
                ground = AppColors.Chip,
            )
        }
    }
}

/** The start page: its layout, its cards and the radius of the departures nearby. */
@Composable
internal fun StartPageSettings(
    startClassic: Boolean,
    onStartClassic: (Boolean) -> Unit,
    startCards: Boolean,
    onStartCards: (Boolean) -> Unit,
    startDepartures: Boolean,
    onStartDepartures: (Boolean) -> Unit,
    startCalendar: Boolean,
    onStartCalendar: (Boolean) -> Unit,
    startMap: Boolean,
    onStartMap: (Boolean) -> Unit,
    nearbyRadius: Int,
    onNearbyRadius: (Int) -> Unit,
) {
    val last = 5
    SettingsGroup(label = stringResource(R.string.settings_group_start)) {
        SettingsRow(
            shape = groupShape(0, last),
            title = stringResource(R.string.start_layout_title),
            text = stringResource(R.string.start_layout_text),
            preview = { StartLayoutPreview(startClassic) },
        ) {
            SegmentedToggle(
                labels = listOf(stringResource(R.string.start_layout_sheet), stringResource(R.string.start_layout_classic)),
                selected = if (startClassic) 1 else 0,
                onSelect = { index -> onStartClassic(index == 1) },
                minHeight = 40.dp,
                ground = AppColors.Chip,
            )
        }
        SwitchRow(
            shape = groupShape(1, last),
            title = stringResource(R.string.start_cards_title),
            text = stringResource(R.string.start_cards_text),
            preview = { StartPagePreview(StartPart.Cards, map = startMap, cards = startCards, departures = startDepartures, calendar = startCalendar) },
            checked = startCards,
            onChange = onStartCards,
        )
        SwitchRow(
            shape = groupShape(2, last),
            title = stringResource(R.string.start_departures_title),
            text = stringResource(R.string.start_departures_text),
            preview = { StartPagePreview(StartPart.Departures, map = startMap, cards = startCards, departures = startDepartures, calendar = startCalendar) },
            checked = startDepartures,
            onChange = onStartDepartures,
        )
        SwitchRow(
            shape = groupShape(3, last),
            title = stringResource(R.string.start_calendar_title),
            text = stringResource(R.string.start_calendar_text),
            preview = { NextAppointmentPreview(on = startCalendar) },
            checked = startCalendar,
            onChange = onStartCalendar,
        )
        SwitchRow(
            shape = groupShape(4, last),
            title = stringResource(R.string.start_map_title),
            text = stringResource(R.string.start_map_text),
            preview = { StartPagePreview(StartPart.Map, map = startMap, cards = startCards, departures = startDepartures, calendar = startCalendar) },
            checked = startMap,
            onChange = onStartMap,
        )
        SettingsRow(
            shape = groupShape(5, last),
            title = stringResource(R.string.nearby_radius_title),
            text = stringResource(R.string.nearby_radius_text),
            preview = { NearbyRadiusPreview(nearbyRadius) },
        ) {
            SegmentedToggle(
                labels = AppPrefs.NEARBY_RADII.map { stringResource(R.string.metres, it) },
                selected = AppPrefs.NEARBY_RADII.indexOf(nearbyRadius),
                onSelect = { index -> onNearbyRadius(AppPrefs.NEARBY_RADII[index]) },
                minHeight = 40.dp,
                ground = AppColors.Chip,
            )
        }
    }
}

/** Accessibility: whether journeys must be step-free. The keys of the lift data are under Advanced. */
@Composable
internal fun AccessibilitySettings(options: SearchOptions, onOptions: (SearchOptions) -> Unit) {
    SettingsGroup(label = stringResource(R.string.settings_group_accessibility)) {
        SettingsRow(
            shape = groupShape(0, 1),
            title = stringResource(R.string.accessibility_title),
            text = stringResource(R.string.accessibility_text),
        ) {
            SegmentedToggle(
                labels = listOf(
                    stringResource(R.string.accessibility_none),
                    stringResource(R.string.accessibility_partial),
                    stringResource(R.string.accessibility_complete),
                ),
                selected = Accessibility.entries.indexOf(options.accessibility),
                onSelect = { index -> onOptions(options.copy(accessibility = Accessibility.entries[index])) },
                minHeight = 40.dp,
                ground = AppColors.Chip,
            )
        }
        // "Langsamer unterwegs": every walk counts 1.3 times as long, at every transfer level.
        SwitchRow(
            shape = groupShape(1, 1),
            title = stringResource(R.string.slower_title),
            text = stringResource(R.string.slower_text),
            checked = options.slower,
            onChange = { on -> onOptions(options.copy(slower = on)) },
        )
    }
}

/**
 * The first name the live page shows for shared live links; stays on this phone and only
 * travels in the link. The address of the live page itself is under Advanced.
 */
@Composable
internal fun LiveNameSettings() {
    val context = LocalContext.current
    val prefs = remember(context) { AppPrefs(context) }
    var name by remember { mutableStateOf(prefs.liveName) }
    SettingsGroup(label = stringResource(R.string.settings_live_link)) {
        SettingsRow(
            shape = groupShape(0, 0),
            title = stringResource(R.string.live_name_title),
            text = stringResource(R.string.live_name_text),
            preview = { LiveNamePreview(name) },
        ) {
            LiveNameField(
                value = name,
                onValue = { value ->
                    name = value
                    prefs.liveName = value
                    prefs.liveNameAsked = true
                },
            )
        }
    }
}

/** The row at the bottom that opens the page "Advanced". */
@Composable
internal fun AdvancedEntryRow(onClick: () -> Unit) {
    SettingsGroup(label = null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(groupShape(0, 0))
                .background(AppColors.Card)
                .clickable(onClick = onClick)
                .defaultMinSize(minHeight = 56.dp)
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingText(
                title = stringResource(R.string.settings_advanced),
                text = stringResource(R.string.settings_advanced_text),
                modifier = Modifier.weight(1f),
            )
            Text(text = "›", color = AppColors.Muted, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * The page "Advanced": the data sources with their order, switches and keys, the own server,
 * the keys of the lift data, the address of the live page and the developer options.
 */
@Composable
internal fun AdvancedSettingsPage(
    sourceChoice: SourceChoice,
    onSourceChoice: (SourceChoice) -> Unit,
    ownServer: String,
    onOwnServer: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .fadeEdges(scroll, horizontal = false)
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DataSourceGroup(
            choice = sourceChoice,
            onChoice = onSourceChoice,
            ownServer = ownServer,
            onOwnServer = onOwnServer,
        )
        LiftKeysGroup()
        LiveAddressGroup()
        DeveloperSection()
        Spacer(modifier = Modifier.height(LocalNavInset.current))
    }
}

/** The address of the web pages for shared live and route links; empty for the project website ([DEFAULT_SITE_ADDRESS]). */
@Composable
private fun LiveAddressGroup() {
    val context = LocalContext.current
    val prefs = remember(context) { AppPrefs(context) }
    var address by remember { mutableStateOf(prefs.liveBaseUrl) }
    SettingsGroup(label = stringResource(R.string.settings_live_page)) {
        SettingsRow(
            shape = groupShape(0, 0),
            title = stringResource(R.string.live_base_url_title),
            text = stringResource(R.string.live_base_url_text),
        ) {
            BasicTextField(
                value = address,
                onValueChange = { value ->
                    address = value.take(200)
                    prefs.liveBaseUrl = address
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                textStyle = TextStyle(color = AppColors.Ink, fontSize = 14.sp, fontFamily = AppFont),
                cursorBrush = SolidColor(AppColors.Blue),
                modifier = Modifier
                    .width(240.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppColors.Chip)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                decorationBox = { innerTextField ->
                    Box {
                        if (address.isEmpty()) {
                            // Empty means the project website: shown as the placeholder.
                            Text(text = DEFAULT_SITE_ADDRESS, color = AppColors.Muted, fontSize = 14.sp, maxLines = 1)
                        }
                        innerTextField()
                    }
                },
            )
        }
    }
}
