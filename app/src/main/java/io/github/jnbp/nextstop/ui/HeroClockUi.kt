package io.github.jnbp.nextstop.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.ui.theme.AppColors

/**
 * Whether the lime counter shows the clock time now, with [minutes] to go (#148): the rule of
 * [heroShowsClock], remembering what it showed so it does not flip at the boundary.
 */
@Composable
internal fun rememberHeroClock(minutes: Long): Boolean {
    val shown = remember { mutableStateOf(minutes >= HERO_CLOCK_FROM_MINUTES) }
    val clock = heroShowsClock(minutes, shown.value)
    SideEffect { shown.value = clock }
    return clock
}

/**
 * The clock time of [step] in the lime counter with a small "an" (arrival) or "um" (departure)
 * before it, so it is not taken for the time of day; read out as "Ankunft um 15:17".
 */
@Composable
internal fun HeroClockText(step: TripStep, fontSize: TextUnit, prefixSize: TextUnit) {
    val arrival = heroClockIsArrival(step.kind)
    val clock = step.time.clock()
    val about = if (step.estimated) stringResource(R.string.estimated_prefix) + " " else ""
    // #277: a clock time from the app's estimate is blue.
    val ink = countInk(step.estimated)
    val spoken = about + stringResource(if (arrival) R.string.r6c_hero_spoken_arrival else R.string.r6c_hero_spoken_departure, clock)
    Row(modifier = Modifier.clearAndSetSemantics { contentDescription = spoken }) {
        Text(
            text = about + stringResource(if (arrival) R.string.r6c_hero_at_arrival else R.string.r6c_hero_at_departure),
            color = ink,
            fontWeight = FontWeight.Bold,
            fontSize = prefixSize,
            maxLines = 1,
            modifier = Modifier.alignByBaseline().padding(end = 3.dp),
        )
        Text(
            text = clock,
            color = ink,
            fontWeight = FontWeight.ExtraBold,
            fontSize = fontSize,
            maxLines = 1,
            modifier = Modifier.alignByBaseline(),
        )
    }
}
