package io.github.jnbp.nextstop.ui.companion

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.railwayNamesOf
import io.github.jnbp.nextstop.ui.FadeSwap
import io.github.jnbp.nextstop.ui.GlassSurface
import io.github.jnbp.nextstop.ui.LineBadge
import io.github.jnbp.nextstop.ui.WaitPhase
import io.github.jnbp.nextstop.ui.clock
import io.github.jnbp.nextstop.ui.heroClockIsArrival
import io.github.jnbp.nextstop.ui.minutesBetween
import io.github.jnbp.nextstop.ui.rememberHeroClock
import io.github.jnbp.nextstop.ui.shortLine
import io.github.jnbp.nextstop.ui.spokenDuration
import io.github.jnbp.nextstop.ui.stepLabel
import io.github.jnbp.nextstop.ui.theme.AppColors
import java.time.OffsetDateTime

/**
 * The slim status line pinned at the top of the sheet of the map view (#225), shown while the
 * sheet covers the blue box (see heroPinShown): the line of the step, the blue box's sentence
 * ("Aussteigen in …", "Warte auf …") and its lime minutes, in one line of the same words and
 * times as the blue box ([stepLabel], [MinutePill] of [model]'s step). One tap ([onClick]) brings
 * the blue box back; screen readers hear it as one sentence.
 */
@Composable
internal fun CompanionPinnedStatus(
    model: CompanionModel,
    now: OffsetDateTime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val step = model.step
    val journey = model.journey
    val label = stepLabel(step, remember(journey) { railwayNamesOf(journey) })
    val leg = step.leg?.takeIf { !it.walking }
    val minutes = minutesBetween(now, step.time).coerceAtLeast(0)
    val clockShown = rememberHeroClock(minutes)
    val about = if (step.estimated) stringResource(R.string.estimated_prefix) + " " else ""
    // As the lime pill reads itself out; a wait due now or overdue says it in the sentence already.
    val timeSpoken = when {
        step.wait != WaitPhase.Coming -> null
        clockShown -> about + stringResource(
            if (heroClockIsArrival(step.kind)) R.string.r6c_hero_spoken_arrival else R.string.r6c_hero_spoken_departure,
            step.time.clock(),
        )
        else -> about + spokenDuration(minutes)
    }
    val spoken = listOfNotNull(leg?.let { shortLine(it.line) }, label, timeSpoken).joinToString(", ")
    val clickLabel = stringResource(R.string.r7night_pin_show_step)
    val shape = RoundedCornerShape(PIN_HEIGHT / 2)
    GlassSurface(modifier = modifier.fillMaxWidth(), shape = shape) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PIN_HEIGHT)
                .clip(shape)
                .clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick)
                .clearAndSetSemantics { contentDescription = spoken }
                .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (leg != null) LineBadge(leg, minWidth = 36.dp)
            FadeSwap(target = label, modifier = Modifier.weight(1f)) { text ->
                Text(
                    text = text,
                    color = AppColors.Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            MinutePill(step = step, now = now, compact = true)
        }
    }
}

// The pinned status line is this high at least: the small lime pill and its margins.
private val PIN_HEIGHT = 42.dp
