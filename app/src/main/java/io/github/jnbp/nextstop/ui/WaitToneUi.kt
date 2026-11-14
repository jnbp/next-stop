package io.github.jnbp.nextstop.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.ChangeTime
import io.github.jnbp.nextstop.data.WaitTone
import io.github.jnbp.nextstop.data.tone
import io.github.jnbp.nextstop.ui.theme.AppColors

/** The strong colour of a change of [tone]: text and dots on the card ground. */
fun toneColor(tone: WaitTone): Color = when (tone) {
    WaitTone.Sprint -> AppColors.VeryRisky
    WaitTone.Tight -> AppColors.DelayRed
    WaitTone.Comfortable -> AppColors.Early
    WaitTone.Neutral -> AppColors.Muted
    WaitTone.LongWait -> AppColors.Sand
}

/** The ground of a tile or chip of a change of [tone]. */
fun toneContainer(tone: WaitTone): Color = when (tone) {
    WaitTone.Sprint -> AppColors.VeryRiskyContainer
    WaitTone.Tight -> AppColors.DelayContainer
    WaitTone.Comfortable -> AppColors.CalmContainer
    WaitTone.Neutral -> AppColors.Chip
    WaitTone.LongWait -> AppColors.SandContainer
}

/** The ink on [toneContainer]. */
fun toneInk(tone: WaitTone): Color = when (tone) {
    WaitTone.Sprint -> AppColors.VeryRiskyInk
    WaitTone.Tight -> AppColors.DelayInk
    WaitTone.Comfortable -> AppColors.CalmInk
    WaitTone.Neutral -> AppColors.Ink
    WaitTone.LongWait -> AppColors.SandInk
}

/**
 * The words for a change read out and shown: the name of its tightness (see [tightnessName]),
 * with "long wait" added where its tone says so.
 */
@Composable
fun toneLabel(time: ChangeTime): String {
    val name = stringResource(tightnessName(time.tightness))
    return if (time.tone == WaitTone.LongWait) stringResource(R.string.tone_with_long_wait, name) else name
}
