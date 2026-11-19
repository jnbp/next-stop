package io.github.jnbp.nextstop.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.ui.theme.AppColors

/** The type sizes of the minute count in a lime pill: the companion's large one and the card's smaller one. */
internal data class HeroCountSizes(
    val prefix: TextUnit,
    val prefixGap: Dp,
    val number: TextUnit,
    val hoursNumber: TextUnit,
    val unit: TextUnit,
    val prime: TextUnit,
    val hoursPrime: TextUnit,
    val unitGap: Dp,
)

/**
 * The minutes until [step] in the lime pill ("3′", "ca. 12′", "1:37 h"), with the digits as the
 * optical middle (#182): the prime hangs off to the right, "ca." off to the left (see
 * [heroCountPlacement]). Read out as one duration.
 */
@Composable
internal fun HeroMinutes(step: TripStep, minutes: Long, sizes: HeroCountSizes) {
    // A ride waited for that is due now or late without news (#184): not a counter stuck at "0′".
    when (step.wait) {
        WaitPhase.AnyMoment -> {
            val spoken = stringResource(R.string.r6live_any_moment_spoken)
            GlyphIcon(
                Glyph.Recent,
                modifier = Modifier.speaks(spoken),
                color = AppColors.OnLime,
                size = with(LocalDensity.current) { sizes.number.toDp() },
            )
            return
        }
        WaitPhase.Overdue -> {
            OverdueCount(step.overdueMinutes, sizes)
            return
        }
        WaitPhase.Coming -> Unit
    }
    val estimated = stringResource(R.string.estimated_prefix).takeIf { step.estimated }
    // #277: a count from the app's estimate is blue, as its clock time.
    val ink = countInk(step.estimated)
    // The last minute before boarding counts seconds ("45 s"), as on the watch, not "0′".
    val lastSeconds = rememberLastMinuteSeconds(step.time, near = step.kind == StepKind.Board && minutes in 0L..1L)
    // From an hour on "1:37" and a small "h" (#106), below it the minutes and their prime.
    val hours = durationClock(minutes)
    val number = lastSeconds?.toString() ?: hours ?: minutes.toString()
    val unit = stringResource(
        when {
            lastSeconds != null -> R.string.r9_seconds_unit
            hours == null -> R.string.minutes_unit
            else -> R.string.hours_unit
        },
    )
    val numberSize = if (hours == null) sizes.number else sizes.hoursNumber
    HangingCount(
        slotWidth = rememberDigitSlot(numberSize, 2),
        hasLead = estimated != null,
        modifier = Modifier.speaks(
            listOfNotNull(
                estimated,
                if (lastSeconds != null) stringResource(R.string.r9_in_seconds, lastSeconds) else spokenDuration(minutes),
            ).joinToString(" "),
        ),
    ) {
        // A time the app estimated (see plausible) is said to be one.
        if (estimated != null) {
            Text(
                text = estimated,
                color = ink,
                fontWeight = FontWeight.Bold,
                fontSize = sizes.prefix,
                maxLines = 1,
                modifier = Modifier.padding(end = sizes.prefixGap),
            )
        }
        RollingText(value = lastSeconds?.toLong() ?: minutes, text = number, color = ink, fontSize = numberSize)
        Text(
            text = unit,
            color = ink,
            fontWeight = FontWeight.ExtraBold,
            fontSize = unitSize(unit, sizes.unit, if (hours == null) sizes.prime else sizes.hoursPrime),
            maxLines = 1,
            modifier = Modifier.padding(start = unitGap(unit, sizes.unitGap)),
        )
    }
}

/** "+2′": the minutes a ride waited for is late without news (#184), the digits in the middle as in [HeroMinutes]. */
@Composable
private fun OverdueCount(minutes: Long, sizes: HeroCountSizes) {
    val unit = stringResource(R.string.minutes_unit)
    HangingCount(
        slotWidth = rememberDigitSlot(sizes.number, 2),
        hasLead = true,
        modifier = Modifier.speaks(stringResource(R.string.r6live_overdue_spoken, spokenDuration(minutes))),
    ) {
        Text(text = "+", color = AppColors.OnLime, fontWeight = FontWeight.ExtraBold, fontSize = sizes.prime, maxLines = 1)
        RollingText(value = minutes, text = minutes.toString(), color = AppColors.OnLime, fontSize = sizes.number)
        Text(
            text = unit,
            color = AppColors.OnLime,
            fontWeight = FontWeight.ExtraBold,
            fontSize = unitSize(unit, sizes.unit, sizes.prime),
            maxLines = 1,
            modifier = Modifier.padding(start = unitGap(unit, sizes.unitGap)),
        )
    }
}

/** The width in pixels of [digits] digits at [fontSize] as [RollingText] draws them (equal-width digits). */
@Composable
internal fun rememberDigitSlot(fontSize: TextUnit, digits: Int): Int {
    val style = LocalTextStyle.current.merge(TextStyle(fontSize = fontSize, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"))
    val measurer = rememberTextMeasurer()
    return remember(style, measurer, digits) { measurer.measure("0".repeat(digits), style, maxLines = 1, softWrap = false).size.width }
}

/**
 * A number between an optional small part before it ([hasLead]: the first child) and a unit
 * after it (the last child), all on the number's baseline, placed by [heroCountPlacement]: the
 * number in the middle of the whole, at least [slotWidth] wide.
 */
@Composable
internal fun HangingCount(slotWidth: Int, hasLead: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        val lead = if (hasLead) placeables.getOrNull(0) else null
        val number = placeables[if (hasLead) 1 else 0]
        val unit = placeables.getOrNull(if (hasLead) 2 else 1)
        val at = heroCountPlacement(lead?.width ?: 0, number.width, unit?.width ?: 0, slotWidth)
        fun baseline(placeable: Placeable): Int =
            placeable[FirstBaseline].takeIf { it != AlignmentLine.Unspecified } ?: placeable.height
        val parts = listOfNotNull(lead?.let { it to at.lead }, number to at.number, unit?.let { it to at.unit })
        // Every part on the baseline of the number.
        val numberBaseline = baseline(number)
        val tops = parts.map { (placeable, _) -> numberBaseline - baseline(placeable) }
        val top = tops.minOrNull() ?: 0
        val height = parts.indices.maxOf { tops[it] - top + parts[it].first.height }
        val line = numberBaseline - top
        layout(at.width, height, mapOf(FirstBaseline to line, LastBaseline to line)) {
            parts.forEachIndexed { index, (placeable, x) -> placeable.place(x, tops[index] - top) }
        }
    }
}
