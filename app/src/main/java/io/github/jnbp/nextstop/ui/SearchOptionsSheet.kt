package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.ui.theme.AppColors
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.ChartTotalTime
import io.github.jnbp.nextstop.data.ChartShortLegs
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.data.PRODUCT_KEYS
import io.github.jnbp.nextstop.data.SearchOptions
import io.github.jnbp.nextstop.data.TicketFilter

/** The chip that opens the options of the open search (see [SearchOptionsSheet]); marked while a ticket filter is on. */
@Composable
fun SearchOptionsChip(options: SearchOptions, onClick: () -> Unit, minHeight: Dp = 48.dp) {
    ChoiceChip(
        text = stringResource(R.string.search_options_chip),
        active = options.bike || options.bikeCombo || !options.products.containsAll(PRODUCT_KEYS),
        onClick = onClick,
        minHeight = minHeight,
    )
}

/**
 * The search options ("Suchoptionen"): the kinds of transport (the same as in the settings, e.g.
 * without bus) with "Only Deutschlandticket", a shorthand that switches long-distance trains and
 * coaches off and on again and shows on whenever both are off; "With bicycle" and the bike combo.
 * Every change searches again and is kept for the next searches: the same stored values as in
 * the settings. A place on the way and the mode "Every minute counts" stay with their search.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SearchOptionsSheet(
    options: SearchOptions,
    onProducts: (Set<String>) -> Unit,
    onLocalOnly: (Boolean) -> Unit,
    onBike: (Boolean) -> Unit,
    onBikeCombo: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    totalTime: ChartTotalTime? = null,
    onTotalTime: (ChartTotalTime) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = stringResource(R.string.search_options_title),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(start = 8.dp, bottom = 10.dp),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(groupShape(0, 3))
                        .background(AppColors.Card)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(text = stringResource(R.string.products_title), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        PRODUCT_KEYS.forEach { product ->
                            val on = product in options.products
                            ChoiceChip(
                                text = stringResource(productLabel(product)),
                                active = on,
                                strong = true,
                                minHeight = 40.dp,
                                onClick = {
                                    val changed = if (on) options.products - product else options.products + product
                                    // A search without any product finds nothing: the last one stays on.
                                    if (changed.isNotEmpty()) onProducts(changed)
                                },
                            )
                        }
                    }
                }
                SheetSwitchRow(
                    shape = groupShape(1, 3),
                    title = stringResource(R.string.local_only_title),
                    text = stringResource(R.string.local_only_search_text),
                    checked = options.localOnly,
                    onChange = onLocalOnly,
                )
                SheetSwitchRow(
                    shape = groupShape(2, 3),
                    title = stringResource(R.string.bike_title),
                    text = stringResource(R.string.bike_search_text),
                    // The bike combo carries the bicycle anyway.
                    checked = options.carriesBike,
                    enabled = !options.bikeCombo,
                    onChange = onBike,
                )
                SheetSwitchRow(
                    shape = groupShape(3, 3),
                    title = stringResource(R.string.bike_combo_title),
                    text = stringResource(R.string.bike_combo_search_text),
                    checked = options.bikeCombo,
                    onChange = onBikeCombo,
                )
                // How the chart looks: not part of the search, nothing is searched again.
                if (totalTime != null) {
                    Column(
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .fillMaxWidth()
                            .clip(groupShape(0, 0))
                            .background(AppColors.Card)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(text = stringResource(R.string.chart_total_time_title), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(
                                text = stringResource(R.string.chart_total_time_text),
                                color = AppColors.Muted,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                            )
                        }
                        ChartTotalTimeToggle(selected = totalTime, onSelect = onTotalTime)
                        PreviewExpander(key = "chart_total_time") { ChartTotalTimePreview(totalTime) }
                        Column(modifier = Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(text = stringResource(R.string.chart_short_legs_title), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(
                                text = stringResource(R.string.chart_short_legs_text),
                                color = AppColors.Muted,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                            )
                        }
                        ChartShortLegsToggle()
                        PreviewExpander(key = "chart_short_legs") {
                            ChartShortLegsPreview(ChartShortLegsSetting.load(androidx.compose.ui.platform.LocalContext.current))
                        }
                    }
                }
            }
        }
    }
}

/** The choice where the chart shows the total time of each journey: off, above or below its column, or pinned to an edge (see ChartTotalTime). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChartTotalTimeToggle(selected: ChartTotalTime, onSelect: (ChartTotalTime) -> Unit) {
    // Five choices as chips that wrap onto a second line where the row is too narrow: a
    // segmented toggle of five does not fit, and in a row that scrolls sideways it got no width.
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ChartTotalTime.entries.forEach { setting ->
            ChoiceChip(
                text = stringResource(chartTotalTimeLabel(setting)),
                active = setting == selected,
                strong = true,
                minHeight = 40.dp,
                onClick = { onSelect(setting) },
            )
        }
    }
}

/**
 * The setting how the chart draws short rides (see ChartShortLegs), read once per process and
 * changed from the settings and the options of the chart; no search follows it.
 */
object ChartShortLegsSetting {
    // The stored setting, read once per process in a plain field: reading it in composition writes no state.
    @Volatile
    private var stored: ChartShortLegs? = null

    // A setting changed in this process; snapshot state, so what shows it follows. Written only by update.
    private var changed by androidx.compose.runtime.mutableStateOf<ChartShortLegs?>(null)

    /** The setting now: a change of this process, else the stored one (read once per process). */
    fun load(context: android.content.Context): ChartShortLegs =
        changed ?: stored ?: AppPrefs(context).chartShortLegs.also { stored = it }

    fun update(context: android.content.Context, setting: ChartShortLegs) {
        changed = setting
        AppPrefs(context).chartShortLegs = setting
    }
}

/** The choice how the chart draws short rides: each by itself, or a run of them combined (see ChartShortLegs). */
@Composable
fun ChartShortLegsToggle() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val selected = ChartShortLegsSetting.load(context)
    SegmentedToggle(
        labels = listOf(stringResource(R.string.chart_short_legs_separate), stringResource(R.string.chart_short_legs_combine)),
        selected = selected.ordinal,
        onSelect = { index -> ChartShortLegsSetting.update(context, ChartShortLegs.entries[index]) },
        modifier = Modifier.fillMaxWidth(),
        minHeight = 40.dp,
        ground = AppColors.Chip,
        fill = true,
    )
}

/** The name of a place of the total time in the chart, for its choice. */
fun chartTotalTimeLabel(setting: ChartTotalTime): Int = when (setting) {
    ChartTotalTime.Off -> R.string.chart_total_time_off
    ChartTotalTime.Above -> R.string.chart_total_time_above
    ChartTotalTime.Below -> R.string.chart_total_time_below
    ChartTotalTime.PinnedTop -> R.string.chart_total_time_pinned_top
    ChartTotalTime.PinnedBottom -> R.string.chart_total_time_pinned_bottom
}

@Composable
private fun SheetSwitchRow(
    shape: Shape,
    title: String,
    text: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.Card)
            .clickable(enabled = enabled) { onChange(!checked) }
            .defaultMinSize(minHeight = 56.dp)
            .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(text = text, color = AppColors.Muted, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** A ticket filter that is on, said aloud above the connections; a tap turns it off, also for the next searches. */
@Composable
fun TicketFilterChip(filter: TicketFilter, onOff: () -> Unit, minHeight: Dp = 48.dp) {
    ChoiceChip(
        text = stringResource(
            when (filter) {
                TicketFilter.LocalOnly -> R.string.local_only_chip
                TicketFilter.Bike -> R.string.bike_chip
                TicketFilter.BikeCombo -> R.string.bike_combo_chip
            },
        ) + "  ✕",
        active = true,
        onClick = onOff,
        description = stringResource(R.string.ticket_filter_off),
        minHeight = minHeight,
    )
}
