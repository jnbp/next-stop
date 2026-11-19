package io.github.jnbp.nextstop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import io.github.jnbp.nextstop.R

/**
 * [text] (a time shown with the prime, "in 5′", "1:20 h") as a screen reader should read it:
 * "in 5 Minuten", "1 Stunde 20 Minuten" (see [spokenTimeText]).
 */
@Composable
fun spokenTime(text: String): String {
    val resources = LocalContext.current.resources
    return remember(text, resources) {
        spokenTimeText(
            text,
            minutes = { resources.getQuantityString(R.plurals.r5_minutes_spoken, it, it) },
            hours = { resources.getQuantityString(R.plurals.r5_hours_spoken, it, it) },
        )
    }
}

/** A duration of [minutes] as a screen reader should read it ("1 Stunde 20 Minuten"). */
@Composable
fun spokenDuration(minutes: Long): String {
    val resources = LocalContext.current.resources
    return remember(minutes, resources) {
        val (hours, rest) = durationSpan(minutes)
        listOfNotNull(
            hours?.toInt()?.let { resources.getQuantityString(R.plurals.r5_hours_spoken, it, it) },
            rest?.toInt()?.let { resources.getQuantityString(R.plurals.r5_minutes_spoken, it, it) },
        ).joinToString(" ")
    }
}

/** Reads this element out as [spoken] alone (a time drawn in parts, a number with a prime). */
fun Modifier.speaks(spoken: String): Modifier = clearAndSetSemantics { contentDescription = spoken }

/** The size of a [unit] after a large number: the prime of minutes at [primeSize], other units ("h") at [size]. */
fun unitSize(unit: String, size: TextUnit, primeSize: TextUnit): TextUnit = if (unit == MINUTE_PRIME) primeSize else size

/** The room before a [unit] after its number: the prime of minutes hugs it, other units keep [gap]. */
fun unitGap(unit: String, gap: Dp): Dp = if (unit == MINUTE_PRIME) 1.dp else gap
