package io.github.jnbp.nextstop.ui.companion

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.ui.Glyph
import io.github.jnbp.nextstop.ui.GlyphIcon
import io.github.jnbp.nextstop.ui.rememberAnimationsOn
import io.github.jnbp.nextstop.ui.theme.AppColors
import kotlinx.coroutines.delay

/**
 * #259: the lightning bolt at the top of the new Live-Fahrt views while "Every minute counts"
 * is [on] (see everyMinuteMarkLook): it pops in with its word as the mode comes on, then stays
 * as a small bolt. A tap opens the sheet of the mode, which explains it and switches it off
 * ([onSwitch]; without one the mark only shows).
 */
@Composable
internal fun EveryMinuteTopMark(on: Boolean, onSwitch: ((Boolean) -> Unit)?) {
    val animate = rememberAnimationsOn()
    var onSince by remember { mutableLongStateOf(0L) }
    var nowAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(on) {
        if (on) {
            onSince = SystemClock.uptimeMillis()
            nowAt = onSince
            delay(EVERY_MINUTE_WORD_MILLIS)
            nowAt = SystemClock.uptimeMillis()
        } else {
            onSince = 0L
        }
    }
    val look = everyMinuteMarkLook(on, onSince, nowAt)
    var open by remember { mutableStateOf(false) }
    val description = stringResource(R.string.every_minute_on)
    val clickLabel = stringResource(R.string.r8m_every_minute_mark_click)
    AnimatedVisibility(
        visible = look != EveryMinuteMarkLook.Hidden,
        enter = if (animate) fadeIn(tween(MARK_FADE_MILLIS)) + scaleIn(spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium), initialScale = 0.4f) else EnterTransition.None,
        exit = if (animate) fadeOut(tween(MARK_FADE_MILLIS)) + scaleOut(tween(MARK_FADE_MILLIS), targetScale = 0.6f) else ExitTransition.None,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.Blue)
                .then(
                    if (onSwitch != null) {
                        Modifier.clickable(onClickLabel = clickLabel, role = Role.Button) { open = true }
                    } else {
                        Modifier
                    },
                )
                .semantics(mergeDescendants = true) { contentDescription = description }
                .defaultMinSize(minWidth = 24.dp, minHeight = 24.dp)
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            GlyphIcon(Glyph.Bolt, color = AppColors.OnBlue, size = 13.dp)
            AnimatedVisibility(
                visible = look == EveryMinuteMarkLook.Word,
                enter = if (animate) fadeIn(tween(MARK_WORD_MILLIS)) + expandHorizontally(tween(MARK_WORD_MILLIS)) else EnterTransition.None,
                exit = if (animate) fadeOut(tween(MARK_WORD_MILLIS)) + shrinkHorizontally(tween(MARK_WORD_MILLIS)) else ExitTransition.None,
            ) {
                Text(
                    text = stringResource(R.string.every_minute_badge),
                    color = AppColors.OnBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(start = 3.dp),
                )
            }
        }
    }
    // Outside the mark, so it stays open while the switch in it turns the mode (and the mark) off.
    if (open && onSwitch != null) {
        BarSheet(
            title = stringResource(R.string.every_minute),
            entries = listOf(
                BarSheetEntry(
                    Glyph.Bolt,
                    stringResource(R.string.every_minute),
                    stringResource(R.string.hint_every_minute_text),
                    checked = on,
                ) { onSwitch(!on) },
            ),
            onDismiss = { open = false },
        )
    }
}

// The bolt pops in and out this long; its word folds out and away this long.
private const val MARK_FADE_MILLIS = 200
private const val MARK_WORD_MILLIS = 260
