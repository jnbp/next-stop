package io.github.jnbp.nextstop.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.ui.theme.AppColors
import io.github.jnbp.nextstop.ui.theme.MapVehiclesChoice

/** The name of a level of the setting "Vehicles on the map" (All, Dynamic, Off). */
fun mapVehiclesLabelRes(choice: MapVehiclesChoice): Int = when (choice) {
    MapVehiclesChoice.All -> R.string.map_vehicles_all
    MapVehiclesChoice.Dynamic -> R.string.map_vehicles_dynamic
    MapVehiclesChoice.Off -> R.string.map_vehicles_off
}

/** The short explanation of a level of the setting "Vehicles on the map", shown under its title. */
fun mapVehiclesTextRes(choice: MapVehiclesChoice): Int = when (choice) {
    MapVehiclesChoice.All -> R.string.map_vehicles_all_text
    MapVehiclesChoice.Dynamic -> R.string.map_vehicles_dynamic_text
    MapVehiclesChoice.Off -> R.string.map_vehicles_off_text
}

/** The three levels of the setting "Vehicles on the map" side by side, [selected] marked. */
@Composable
fun MapVehiclesToggle(selected: MapVehiclesChoice, onSelect: (MapVehiclesChoice) -> Unit, modifier: Modifier = Modifier) {
    val levels = MapVehiclesChoice.entries
    SegmentedToggle(
        labels = levels.map { stringResource(mapVehiclesLabelRes(it)) },
        selected = levels.indexOf(selected),
        onSelect = { index -> onSelect(levels[index]) },
        modifier = modifier.fillMaxWidth(),
        minHeight = 40.dp,
        ground = AppColors.Chip,
        fill = true,
        twoLines = true,
    )
}
