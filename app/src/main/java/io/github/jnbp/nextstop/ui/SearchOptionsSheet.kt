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
            }
        }
    }
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
