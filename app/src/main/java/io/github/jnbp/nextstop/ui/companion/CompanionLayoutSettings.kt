package io.github.jnbp.nextstop.ui.companion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.CompanionLayoutMode
import io.github.jnbp.nextstop.data.PickerMode
import io.github.jnbp.nextstop.ui.FadeSwap
import io.github.jnbp.nextstop.ui.SegmentedToggle
import io.github.jnbp.nextstop.ui.SettingsGroup
import io.github.jnbp.nextstop.ui.SettingsRow
import io.github.jnbp.nextstop.ui.SketchLines
import io.github.jnbp.nextstop.ui.BatterySaver
import io.github.jnbp.nextstop.ui.SettingReveal
import io.github.jnbp.nextstop.ui.SketchOptions
import io.github.jnbp.nextstop.ui.WalkMapSetting
import io.github.jnbp.nextstop.ui.VisualOption
import io.github.jnbp.nextstop.ui.block
import io.github.jnbp.nextstop.ui.groupShape
import io.github.jnbp.nextstop.ui.theme.AppColors

/*
 * The setting "Begleiter-Ansicht" (companion view) in Settings › Appearance and its page in
 * the setup, with its four views. Both read and write CompanionLayout themselves, so nothing is handed down.
 */

/**
 * What the connection detail and the Live-Fahrt unfold at the changes (#201), in Settings ›
 * Darstellung right after "Verbindungsdetails" and "Live-Fahrt-Ansicht". [connectionChoice]
 * is the stored on/off (AppPrefs.connectionChoice), kept by the root of the app.
 */
@Composable
fun TripFoldSettings(connectionChoice: Boolean, onConnectionChoice: (Boolean) -> Unit) {
    SettingsGroup(label = null) {
        ConnectionPickerRow(shape = groupShape(0, 1), on = connectionChoice, onOn = onConnectionChoice)
        // Beside it, in the same words (#203): which small maps of walks start unfolded.
        WalkMapsRow(shape = groupShape(1, 1))
    }
}

/**
 * "Minikarte für Fußwege" (#202): which small maps of walks start unfolded, in the Live-Fahrt and
 * the connection detail: "Eingeklappt", "Nur nächste ausgeklappt" (on the detail page the first
 * walk, in the Live-Fahrt the next walk or change) or "Alle ausgeklappt" (WalkMapSetting). As its
 * preview two walks, each with its map unfolded or folded behind its link.
 */
@Composable
private fun WalkMapsRow(shape: androidx.compose.ui.graphics.Shape) {
    val context = LocalContext.current
    val mode = WalkMapSetting.load(context)
    val modes = PickerMode.shownOrder
    val labels = modes.map { stringResource(walkMapsLabel(it)) }
    val short = modes.map { stringResource(walkMapsShortLabel(it)) }
    SettingsRow(
        shape = shape,
        title = stringResource(R.string.r6s2_walk_maps_title),
        text = stringResource(R.string.r6s2_walk_maps_text),
        preview = {
            SketchOptions(labels = short, selected = modes.indexOf(mode), height = 84.dp) { index, reveal ->
                walkMapsModeSketch(modes[index], reveal)
            }
        },
        previewPick = { index -> WalkMapSetting.update(context, modes[index]) },
    ) {
        SegmentedToggle(
            labels = labels,
            selected = modes.indexOf(mode),
            onSelect = { index -> WalkMapSetting.update(context, modes[index]) },
            minHeight = 40.dp,
            ground = AppColors.Chip,
        )
        FadeSwap(target = mode) { shown ->
            Text(
                text = stringResource(walkMapsHint(shown)),
                color = AppColors.Muted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

private fun walkMapsLabel(mode: PickerMode): Int = when (mode) {
    PickerMode.Folded -> R.string.r6s2_fold_folded
    PickerMode.AllOpen -> R.string.r6s2_fold_all
    PickerMode.NextOpen -> R.string.r6s2_fold_next_map
}

private fun walkMapsShortLabel(mode: PickerMode): Int = when (mode) {
    PickerMode.Folded -> R.string.r6s2_fold_folded
    PickerMode.AllOpen -> R.string.r6s2_fold_all_short
    PickerMode.NextOpen -> R.string.r6s2_fold_next_map_short
}

private fun walkMapsHint(mode: PickerMode): Int = when (mode) {
    PickerMode.Folded -> R.string.r6s2_walk_maps_folded_text
    PickerMode.AllOpen -> R.string.r6s2_walk_maps_all_text
    PickerMode.NextOpen -> R.string.r6s2_walk_maps_next_text
}

/**
 * The picture of a choice of the walk maps: two walks on a dotted rail; an unfolded one shows a
 * small map (a blue way on a light ground), a folded one its blue link. [reveal] lets the maps
 * grow in as the choice is picked.
 */
private fun DrawScope.walkMapsModeSketch(mode: PickerMode, reveal: Float) {
    val w = size.width
    val h = size.height
    val pad = 8.dp.toPx()
    val grow = 0.5f + 0.5f * reveal.coerceIn(0f, 1f)
    block(AppColors.PlainGround, 0f, 0f, w, h, radius = 0f)
    val railX = pad + 3.dp.toPx()
    val tops = listOf(pad, h * 0.52f)
    val partH = h * 0.48f - pad
    tops.forEachIndexed { index, top ->
        val open = when (mode) {
            PickerMode.Folded -> false
            PickerMode.AllOpen -> true
            PickerMode.NextOpen -> index == 0
        }
        // The walk: dots on the rail, its words beside them.
        var y = top + 2.dp.toPx()
        while (y < top + partH - 2.dp.toPx()) {
            block(AppColors.Muted, railX, y, 2.dp.toPx(), 2.dp.toPx(), radius = 1.dp.toPx())
            y += 5.dp.toPx()
        }
        val x = railX + 8.dp.toPx()
        block(AppColors.Ink, x, top + 2.dp.toPx(), (w - x - pad) * 0.4f, 3.dp.toPx(), radius = 1.dp.toPx(), alpha = 0.6f)
        if (open) {
            val mapTop = top + 8.dp.toPx()
            val mapH = (partH - 8.dp.toPx()) * grow
            val mapW = w - x - pad
            block(AppColors.Card, x, mapTop, mapW, mapH, radius = 4.dp.toPx())
            // The way across the little map.
            block(AppColors.Blue, x + mapW * 0.15f, mapTop + mapH * 0.45f, mapW * 0.6f, 2.dp.toPx(), radius = 1.dp.toPx())
            block(SketchLines[0], x + mapW * 0.72f, mapTop + mapH * 0.45f - 2.dp.toPx(), 5.dp.toPx(), 5.dp.toPx(), radius = 2.5.dp.toPx())
        } else {
            block(AppColors.Blue, x, top + 9.dp.toPx(), (w - x - pad) * 0.3f, 3.dp.toPx(), radius = 1.5.dp.toPx())
        }
    }
}

/** "Begleiter-Ansicht" as a group of its own in Settings › Appearance. */
@Composable
fun CompanionLayoutSettings() {
    SettingsGroup(label = null) {
        CompanionLayoutRow(shape = groupShape(0, 0))
    }
}

/**
 * "Anschluss unterwegs wählen" (#201): one setting for the connection pickers at the changes,
 * in the Live-Fahrt and in the connection detail. "Aus" leaves out every picker link (the stored
 * AppPrefs.connectionChoice, the same as in the setup); "An" reveals how they show at first:
 * "Eingeklappt", "Nur nächstes ausgeklappt" or "Alle ausgeklappt" (PickerMode, see
 * CompanionLayout.pickers). As its preview a small trip with two changes for each choice.
 */
@Composable
internal fun ConnectionPickerRow(
    shape: androidx.compose.ui.graphics.Shape,
    on: Boolean,
    onOn: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    CompanionLayout.install(context)
    val mode = CompanionLayout.pickers
    val modes = PickerMode.shownOrder
    val modeLabels = modes.map { stringResource(pickerLabel(it)) }
    val sketchLabels = listOf(stringResource(R.string.r6s2_off)) + modes.map { stringResource(pickerShortLabel(it)) }
    // In the preview: "Aus", then the three ways it shows.
    val picked = PickerMode.choiceIndex(on, mode)
    val pick = { index: Int ->
        val chosen = PickerMode.ofChoice(index)
        if (chosen == null) {
            onOn(false)
        } else {
            if (!on) onOn(true)
            CompanionLayout.changePickers(context, chosen)
        }
    }
    SettingsRow(
        shape = shape,
        title = stringResource(R.string.connection_choice_title),
        text = stringResource(R.string.connection_choice_text),
        preview = {
            SketchOptions(labels = sketchLabels, selected = picked, height = 84.dp) { index, reveal ->
                pickerSketch(if (index == 0) null else modes[index - 1], reveal)
            }
        },
        previewPick = pick,
    ) {
        SegmentedToggle(
            labels = listOf(stringResource(R.string.r6s2_off), stringResource(R.string.r6s2_on)),
            selected = if (on) 1 else 0,
            onSelect = { index -> onOn(index == 1) },
            minHeight = 40.dp,
            ground = AppColors.Chip,
        )
        SettingReveal(visible = on) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SegmentedToggle(
                    labels = modeLabels,
                    selected = modes.indexOf(mode),
                    onSelect = { index -> CompanionLayout.changePickers(context, modes[index]) },
                    minHeight = 40.dp,
                    ground = AppColors.Chip,
                )
                FadeSwap(target = mode) { shown ->
                    Text(
                        text = stringResource(pickerHint(shown)),
                        color = AppColors.Muted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
        }
    }
}

private fun pickerLabel(mode: PickerMode): Int = when (mode) {
    PickerMode.Folded -> R.string.r6s2_fold_folded
    PickerMode.AllOpen -> R.string.r6s2_fold_all
    PickerMode.NextOpen -> R.string.r6s2_fold_next_picker
}

// The short words under the small pictures.
private fun pickerShortLabel(mode: PickerMode): Int = when (mode) {
    PickerMode.Folded -> R.string.r6s2_fold_folded
    PickerMode.AllOpen -> R.string.r6s2_fold_all_short
    PickerMode.NextOpen -> R.string.r6s2_fold_next_short
}

private fun pickerHint(mode: PickerMode): Int = when (mode) {
    PickerMode.Folded -> R.string.r6c_pickers_folded_text
    PickerMode.AllOpen -> R.string.r6c_pickers_all_text
    PickerMode.NextOpen -> R.string.r6c_pickers_next_text
}

/**
 * The picture of a choice: a timeline with two changes; an open picker is a row of three
 * tiles (the chosen one framed in blue), a folded one a short blue link, none ([mode] null,
 * "Aus") only the changes. [reveal] lets the tiles grow in as the choice is picked.
 */
private fun DrawScope.pickerSketch(mode: PickerMode?, reveal: Float) {
    val w = size.width
    val h = size.height
    val pad = 8.dp.toPx()
    val grow = 0.6f + 0.4f * reveal.coerceIn(0f, 1f)
    block(AppColors.PlainGround, 0f, 0f, w, h, radius = 0f)
    // The rail on the left, the two changes on it.
    block(SketchLines[1], pad + 2.dp.toPx(), pad, 3.dp.toPx(), h - 2 * pad, radius = 1.dp.toPx())
    val rows = listOf(h * 0.28f, h * 0.68f)
    rows.forEachIndexed { index, y ->
        val open = when (mode) {
            null, PickerMode.Folded -> false
            PickerMode.AllOpen -> true
            PickerMode.NextOpen -> index == 0
        }
        val x = pad + 12.dp.toPx()
        block(AppColors.Ink, x, y - 10.dp.toPx(), (w - x - pad) * 0.5f, 3.dp.toPx(), radius = 1.dp.toPx(), alpha = 0.6f)
        when {
            mode == null -> Unit
            open -> {
                val tile = (w - x - pad - 8.dp.toPx()) / 3f
                for (t in 0 until 3) {
                    val left = x + t * (tile + 4.dp.toPx())
                    block(if (t == 0) AppColors.BlueContainer else AppColors.Card, left, y - 4.dp.toPx(), tile, 14.dp.toPx() * grow, radius = 4.dp.toPx())
                }
                block(AppColors.Blue, x, y - 4.dp.toPx(), tile, 2.dp.toPx(), radius = 1.dp.toPx())
            }
            else -> block(AppColors.Blue, x, y - 2.dp.toPx(), (w - x - pad) * 0.35f, 3.dp.toPx(), radius = 1.5.dp.toPx())
        }
    }
}

/**
 * The four views to pick from (they wrap into a grid where they do not fit), what the picked
 * one does, and as its preview a small picture of every view, each tappable to pick it.
 */
@Composable
internal fun CompanionLayoutRow(shape: androidx.compose.ui.graphics.Shape) {
    val context = LocalContext.current
    CompanionLayout.install(context)
    val mode = CompanionLayout.mode
    val all = CompanionLayoutMode.entries
    val labels = all.map { stringResource(layoutLabel(it)) }
    SettingsRow(
        shape = shape,
        title = stringResource(R.string.companion_layout_title),
        text = stringResource(R.string.companion_layout_text),
        preview = {
            SketchOptions(labels = labels, selected = all.indexOf(mode), height = 104.dp) { index, reveal ->
                companionSketch(all[index], reveal)
            }
        },
        // A tap on one of the small pictures picks its view.
        previewPick = { index -> CompanionLayout.change(context, all[index]) },
    ) {
        SegmentedToggle(
            labels = labels,
            selected = all.indexOf(mode),
            onSelect = { index -> CompanionLayout.change(context, all[index]) },
            minHeight = 40.dp,
            ground = AppColors.Chip,
        )
        LayoutHint(mode, fontSize = 12)
    }
}

/** What the picked view does, and that the battery saver keeps the map away while it saves. */
@Composable
private fun LayoutHint(mode: CompanionLayoutMode, fontSize: Int) {
    FadeSwap(target = mode) { shown ->
        Text(
            text = stringResource(layoutHint(shown)),
            color = AppColors.Muted,
            fontSize = fontSize.sp,
            lineHeight = (fontSize + 4).sp,
        )
    }
    SettingReveal(visible = BatterySaver.active && mode != CompanionLayoutMode.NoMap) {
        Text(
            text = stringResource(R.string.r4_companion_layout_saver),
            color = AppColors.Early,
            fontSize = fontSize.sp,
            lineHeight = (fontSize + 4).sp,
        )
    }
}

/** For the setup: the four views as small pictures, two by two; picked and stored at once. */
@Composable
fun CompanionLayoutChoice(previewHeight: Dp = 96.dp) {
    val context = LocalContext.current
    CompanionLayout.install(context)
    val mode = CompanionLayout.mode
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CompanionLayoutMode.entries.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                pair.forEach { each ->
                    VisualOption(
                        label = stringResource(layoutLabel(each)),
                        selected = mode == each,
                        onClick = { CompanionLayout.change(context, each) },
                        modifier = Modifier.weight(1f),
                        previewHeight = previewHeight,
                    ) { companionSketch(each, 1f) }
                }
            }
        }
    }
    LayoutHint(mode, fontSize = 13)
}

private fun layoutLabel(mode: CompanionLayoutMode): Int = when (mode) {
    CompanionLayoutMode.Auto -> R.string.companion_layout_auto
    CompanionLayoutMode.NoMap -> R.string.r4_companion_layout_no_map
    CompanionLayoutMode.AlwaysMap -> R.string.companion_layout_map
    CompanionLayoutMode.Classic -> R.string.companion_layout_classic
}

private fun layoutHint(mode: CompanionLayoutMode): Int = when (mode) {
    CompanionLayoutMode.Auto -> R.string.companion_layout_auto_text
    CompanionLayoutMode.NoMap -> R.string.r4_companion_layout_no_map_text
    CompanionLayoutMode.AlwaysMap -> R.string.companion_layout_map_text
    CompanionLayoutMode.Classic -> R.string.companion_layout_classic_text
}

// A soft green for the map in the small pictures.
private val SKETCH_MAP = Color(0xFFDDEBD3)
private val SKETCH_ROAD = Color(0xFFFFFFFF)

/**
 * The picture of a companion view: calm (plain ground, blue box with its progress, a card of
 * stops), with the map (map, compact blue box, a sheet), classic (map, blue box, sheet,
 * buttons and the tab bar), or automatic (calm above, the map coming in below with a walk on
 * it: the map on foot). [reveal] lets the blue box settle in and the map of the automatic
 * view rise as the view is picked.
 */
private fun DrawScope.companionSketch(mode: CompanionLayoutMode, reveal: Float) {
    val w = size.width
    val h = size.height
    val pad = 8.dp.toPx()
    val r = 8.dp.toPx()
    val settle = 0.9f + 0.1f * reveal.coerceIn(0f, 1f)
    val calm = mode == CompanionLayoutMode.Auto || mode == CompanionLayoutMode.NoMap
    if (calm) {
        block(AppColors.PlainGround, 0f, 0f, w, h, radius = 0f)
    } else {
        sketchMap(0f, w, h)
    }
    // The blue box: tall with its progress when calm, compact otherwise.
    val heroHeight = (if (calm) h * 0.3f else h * 0.2f) * settle
    block(AppColors.Blue, pad, pad, w - 2 * pad, heroHeight, radius = r)
    block(AppColors.Lime, pad + 4.dp.toPx(), pad + 4.dp.toPx(), heroHeight * 0.55f, heroHeight * 0.55f, radius = r * 0.7f)
    if (calm) {
        val y = pad + heroHeight * 0.8f
        block(AppColors.OnBlue, pad + 6.dp.toPx(), y, (w - 2 * pad - 12.dp.toPx()) * 0.6f, 2.dp.toPx(), radius = 1.dp.toPx(), alpha = 0.9f)
        // The card of the stops below, with its rail.
        val top = pad + heroHeight + 6.dp.toPx()
        block(AppColors.Card, pad, top, w - 2 * pad, h - top - 22.dp.toPx(), radius = r)
        block(SketchLines[1], pad + 6.dp.toPx(), top + 6.dp.toPx(), 3.dp.toPx(), h - top - 34.dp.toPx(), radius = 1.dp.toPx())
        if (mode == CompanionLayoutMode.Auto) {
            // On foot the map comes in from below, with the walk on it.
            val rise = reveal.coerceIn(0f, 1f)
            val mapTop = h * (0.62f + 0.08f * (1f - rise))
            sketchMap(mapTop, w, h)
            val dash = 3.dp.toPx()
            var x = w * 0.18f
            while (x < w * 0.7f) {
                drawLine(AppColors.Blue, Offset(x, h * 0.9f - (x - w * 0.18f) * 0.25f), Offset(x + dash, h * 0.9f - (x + dash - w * 0.18f) * 0.25f), strokeWidth = 2.dp.toPx())
                x += dash * 2
            }
            drawCircle(AppColors.Blue, radius = 3.5.dp.toPx(), center = Offset(w * 0.18f, h * 0.9f))
        }
    } else {
        // The sheet at the bottom.
        val sheetTop = if (mode == CompanionLayoutMode.Classic) h * 0.55f else h * 0.68f
        val sheetBottom = if (mode == CompanionLayoutMode.Classic) h - 30.dp.toPx() else h - 22.dp.toPx()
        block(AppColors.Card, pad, sheetTop, w - 2 * pad, sheetBottom - sheetTop, radius = r, alpha = 0.96f)
    }
    // The bar of actions at the foot; the classic view has the tab bar below its buttons.
    val barHeight = 12.dp.toPx()
    if (mode == CompanionLayoutMode.Classic) {
        block(AppColors.Card, pad, h - 26.dp.toPx(), w - 2 * pad, 10.dp.toPx(), radius = 5.dp.toPx())
        block(AppColors.Ink, w * 0.25f, h - 12.dp.toPx(), w * 0.5f, 8.dp.toPx(), radius = 4.dp.toPx(), alpha = 0.75f)
    } else if (mode != CompanionLayoutMode.Auto) {
        block(AppColors.Card, pad, h - barHeight - 6.dp.toPx(), w - 2 * pad, barHeight, radius = barHeight / 2, alpha = 0.95f)
        block(AppColors.AmberContainer, pad + 3.dp.toPx(), h - barHeight - 4.dp.toPx(), (w - 2 * pad) * 0.38f, barHeight - 4.dp.toPx(), radius = barHeight / 2)
    }
}

/** A piece of map from [top] to the foot: green ground, two roads and a line. */
private fun DrawScope.sketchMap(top: Float, w: Float, h: Float) {
    val tall = h - top
    block(SKETCH_MAP, 0f, top, w, tall, radius = 0f)
    drawLine(SKETCH_ROAD, Offset(0f, top + tall * 0.42f), Offset(w, top + tall * 0.55f), strokeWidth = 3.dp.toPx())
    drawLine(SKETCH_ROAD, Offset(w * 0.35f, top), Offset(w * 0.45f, h), strokeWidth = 3.dp.toPx())
    drawLine(SketchLines[1], Offset(w * 0.15f, top + tall * 0.7f), Offset(w * 0.8f, top + tall * 0.3f), strokeWidth = 2.5.dp.toPx())
}
