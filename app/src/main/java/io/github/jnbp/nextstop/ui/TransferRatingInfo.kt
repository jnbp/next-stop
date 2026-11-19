package io.github.jnbp.nextstop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.data.CHANGE_BUFFER_STEPS
import io.github.jnbp.nextstop.data.NEARBY_RADIUS_MAX
import io.github.jnbp.nextstop.data.NEARBY_RADIUS_MIN
import io.github.jnbp.nextstop.data.NEARBY_RADIUS_STEP
import io.github.jnbp.nextstop.data.NORMAL_SPARE_RANGE
import io.github.jnbp.nextstop.data.RELAXED_SPARE_RANGE
import io.github.jnbp.nextstop.data.TransferComfort
import io.github.jnbp.nextstop.data.TransferTuning
import io.github.jnbp.nextstop.data.currentTransferTuning
import io.github.jnbp.nextstop.data.nearbyRadiusOf
import io.github.jnbp.nextstop.data.ratingSettingsVersion
import io.github.jnbp.nextstop.data.transferRatingFacts
import io.github.jnbp.nextstop.ui.theme.AppColors
import kotlin.math.roundToInt

/*
 * How changes are rated, explained in the app: a small ⓘ beside the transfer levels and a link
 * in the settings open [TransferRatingSheet]. Its text is built from the numbers the rating
 * uses (transferRatingFacts), so it never tells another story than the rating; the buffers it
 * names can be tuned right there and in the settings ([TransferTuningControls]).
 */

/**
 * The tuning of the transfer rating as state of the interface: the rating itself reads the
 * plain values of data/TransferTuning.kt, this only lets the sheet and the settings show a
 * change at once.
 */
private val transferTuningShown = mutableStateOf(currentTransferTuning())

/**
 * [ratingSettingsVersion] as state of the interface: what shows ratings of changes reads it, so
 * it is rated afresh when the transfer tuning or the walking pace changes (see
 * [noteRatingSettings]).
 */
internal val ratingSettingsShown = mutableIntStateOf(ratingSettingsVersion)

/** Shows the rating settings in use now (after a change of the tuning or the walking pace). */
internal fun noteRatingSettings() {
    ratingSettingsShown.intValue = ratingSettingsVersion
}

/** Stores [tuning], makes it the one every rating uses and shows it. */
private fun storeTransferTuning(prefs: AppPrefs, tuning: TransferTuning) {
    prefs.transferTuning = tuning
    transferTuningShown.value = currentTransferTuning()
    noteRatingSettings()
}

/** The ⓘ beside the transfer levels: opens [TransferRatingSheet]. [size] is its tap target. */
@Composable
fun TransferRatingInfoButton(modifier: Modifier = Modifier, size: Dp = 36.dp) {
    var open by rememberSaveable { mutableStateOf(false) }
    val label = stringResource(R.string.rating_info_open)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = label) { open = true }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        InfoDot()
    }
    if (open) TransferRatingSheet(onDismiss = { open = false })
}

/** The quiet link "How transfers are rated ⓘ" of the settings: opens [TransferRatingSheet]. */
@Composable
fun TransferRatingInfoLink(modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button) { open = true }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.rating_info_title),
            color = AppColors.Blue,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
        )
        InfoDot(color = AppColors.Blue, size = 16.dp)
    }
    if (open) TransferRatingSheet(onDismiss = { open = false })
}

/** A small "i" in a ring, drawn here as the app has no icon library. */
@Composable
private fun InfoDot(color: androidx.compose.ui.graphics.Color = AppColors.Muted, size: Dp = 20.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .border(1.5.dp, color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "i",
            color = color,
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Normal,
            fontSize = (size.value * 0.6f).sp,
        )
    }
}

/**
 * The sheet "How transfers are rated": what a change needs, the four levels by the minutes to
 * spare, the extra rules, and the buffers to tune (see [TransferTuningControls]). Every number
 * comes from the rating as it is tuned now.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferRatingSheet(onDismiss: () -> Unit) {
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.rating_info_title),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(start = 8.dp),
                )
                TransferRatingExplanation()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(AppColors.Card)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(text = stringResource(R.string.rating_tuning_title), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    TransferTuningControls()
                }
            }
        }
    }
}

/** The explanation of the rating in three cards, its numbers from transferRatingFacts. */
@Composable
private fun TransferRatingExplanation() {
    val facts = transferRatingFacts(transferTuningShown.value)
    InfoCard(
        title = stringResource(R.string.rating_need_title),
        lines = listOf(
            stringResource(R.string.rating_need_known, durationText(facts.changeBufferSeconds), facts.minExactMinutes),
            stringResource(R.string.rating_need_street, durationText(facts.platformStreetSeconds)),
            stringResource(R.string.rating_need_unknown, facts.samePlatform, facts.sameStation, facts.railStation, facts.walkExtra),
        ),
    )
    InfoCard(
        title = stringResource(R.string.rating_levels_title),
        lines = emptyList(),
    ) {
        LevelLine(TransferComfort.Relaxed, stringResource(R.string.rating_level_relaxed, facts.relaxedSpare))
        LevelLine(TransferComfort.Normal, stringResource(R.string.rating_level_normal, facts.normalSpare))
        LevelLine(TransferComfort.Risky, stringResource(R.string.rating_level_risky, facts.briskPercent, facts.minKnappMinutes))
        LevelLine(TransferComfort.VeryRisky, stringResource(R.string.rating_level_sprint))
    }
    InfoCard(
        title = stringResource(R.string.rating_extra_title),
        lines = listOf(
            stringResource(R.string.rating_extra_live, facts.relaxedSpare),
            stringResource(R.string.rating_extra_same),
        ),
    )
}

/** A white card with a [title], [lines] as short paragraphs and any [more] below them. */
@Composable
private fun InfoCard(title: String, lines: List<String>, more: @Composable () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.Card)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        lines.forEach { line -> Text(text = line, color = AppColors.Muted, fontSize = 13.sp, lineHeight = 18.sp) }
        more()
    }
}

/** One level: its name in its colour, then what makes a change that level. */
@Composable
private fun LevelLine(level: TransferComfort, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(levelStrongColor(level)),
        )
        Text(
            text = stringResource(levelNameRes(level)),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier.widthIn(min = 72.dp),
        )
        Text(text = text, color = AppColors.Muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
    }
}

/** [seconds] as "30 s", "1 min" or "1 min 30 s". */
@Composable
private fun durationText(seconds: Long): String = when {
    seconds < 60 -> stringResource(R.string.duration_seconds, seconds)
    seconds % 60 == 0L -> stringResource(R.string.duration_minutes, seconds / 60)
    else -> stringResource(R.string.duration_minutes_seconds, seconds / 60, seconds % 60)
}

/**
 * The three buffers of the rating, each a slider within its choices: the buffer for Normal
 * (NORMAL_SPARE_RANGE), the one for Relaxed (RELAXED_SPARE_RANGE, never below the first) and
 * the change buffer (CHANGE_BUFFER_STEPS, in half minutes); and the way back to the
 * defaults. Every change is stored and rates the next journeys at once.
 */
@Composable
fun TransferTuningControls(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember(context) { AppPrefs(context) }
    val tuning = transferTuningShown.value
    fun set(changed: TransferTuning) = storeTransferTuning(prefs, changed)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        StepSlider(
            title = stringResource(R.string.rating_tuning_normal),
            value = tuning.normalSpare.toInt(),
            values = NORMAL_SPARE_RANGE.map { it.toInt() },
            label = { stringResource(R.string.duration_minutes, it.toLong()) },
            onValue = { set(tuning.copy(normalSpare = it.toLong(), relaxedSpare = maxOf(tuning.relaxedSpare, it.toLong()))) },
        )
        StepSlider(
            title = stringResource(R.string.rating_tuning_relaxed),
            value = tuning.relaxedSpare.toInt(),
            values = RELAXED_SPARE_RANGE.map { it.toInt() },
            label = { stringResource(R.string.duration_minutes, it.toLong()) },
            onValue = { set(tuning.copy(relaxedSpare = it.toLong())) },
        )
        StepSlider(
            title = stringResource(R.string.rating_tuning_buffer),
            value = tuning.changeBufferSeconds.toInt(),
            values = CHANGE_BUFFER_STEPS.map { it.toInt() },
            label = { durationText(it.toLong()) },
            onValue = { set(tuning.copy(changeBufferSeconds = it.toLong())) },
        )
        // Comes and goes with a change away from the defaults: grows in and fades, never jumps (#200;
        // at once without animations, see SettingReveal).
        SettingReveal(visible = tuning != TransferTuning()) {
            Text(
                text = stringResource(R.string.rating_tuning_reset),
                color = AppColors.Blue,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button) { set(TransferTuning()) }
                    .padding(vertical = 4.dp),
            )
        }
    }
}

/**
 * The radius of "all stops nearby" as a slider from NEARBY_RADIUS_MIN to NEARBY_RADIUS_MAX in
 * steps of NEARBY_RADIUS_STEP. The value shows while the thumb moves; [onRadius] is called once
 * it is let go, so the stops are not looked up anew for every step.
 */
@Composable
fun NearbyRadiusSlider(radius: Int, onRadius: (Int) -> Unit, modifier: Modifier = Modifier) {
    val values = remember { (NEARBY_RADIUS_MIN..NEARBY_RADIUS_MAX step NEARBY_RADIUS_STEP).toList() }
    StepSlider(
        title = null,
        value = nearbyRadiusOf(radius),
        values = values,
        label = { stringResource(R.string.metres, it) },
        onValue = onRadius,
        modifier = modifier,
        commitOnRelease = true,
    )
}

/**
 * A slider over [values] (ascending, evenly spaced or not: the thumb moves by index) with its
 * [title] and the value picked, by [label], at the end. Each new step gives a small tick. With
 * [commitOnRelease] [onValue] is called when the thumb is let go, else at every step.
 */
@Composable
internal fun StepSlider(
    title: String?,
    value: Int,
    values: List<Int>,
    label: @Composable (Int) -> String,
    onValue: (Int) -> Unit,
    modifier: Modifier = Modifier,
    commitOnRelease: Boolean = false,
) {
    val haptics = LocalHapticFeedback.current
    val index = values.indexOf(value).takeIf { it >= 0 } ?: values.indices.minByOrNull { kotlin.math.abs(values[it] - value) } ?: 0
    // While the thumb moves: the index under it; NaN when it rests on [value].
    var dragging by remember { mutableFloatStateOf(Float.NaN) }
    val shownIndex = if (dragging.isNaN()) index else dragging.roundToInt().coerceIn(0, values.lastIndex)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (title != null) {
                Text(text = title, fontSize = 13.sp, modifier = Modifier.weight(1f))
            } else {
                Box(modifier = Modifier.weight(1f))
            }
            Text(text = label(values[shownIndex]), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Slider(
            value = if (dragging.isNaN()) index.toFloat() else dragging,
            onValueChange = { moved ->
                val step = moved.roundToInt().coerceIn(0, values.lastIndex)
                if (step != shownIndex) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (commitOnRelease) {
                    dragging = moved
                } else {
                    dragging = Float.NaN
                    if (values[step] != value) onValue(values[step])
                }
            },
            onValueChangeFinished = {
                if (!dragging.isNaN()) {
                    val step = dragging.roundToInt().coerceIn(0, values.lastIndex)
                    dragging = Float.NaN
                    if (values[step] != value) onValue(values[step])
                }
            },
            valueRange = 0f..values.lastIndex.coerceAtLeast(1).toFloat(),
            steps = (values.size - 2).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = AppColors.Blue,
                activeTrackColor = AppColors.Blue,
                inactiveTrackColor = AppColors.Chip,
                activeTickColor = AppColors.OnBlue,
                inactiveTickColor = AppColors.Muted,
            ),
            modifier = Modifier.semantics { if (title != null) contentDescription = title },
        )
    }
}
