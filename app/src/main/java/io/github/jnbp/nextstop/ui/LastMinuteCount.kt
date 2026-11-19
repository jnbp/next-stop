package io.github.jnbp.nextstop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import io.github.jnbp.nextstop.data.lastMinuteSeconds
import io.github.jnbp.nextstop.data.lastMinuteWakeMillis
import java.time.OffsetDateTime

/**
 * The seconds left until [at] within its last minute ("in 45 s", as on the watch), null else.
 * Only a countdown that is near ([near]: its minutes are 0 or 1) attends the shared clock
 * ([SecondTicker]), which rests with the app in the background and wakes every second only
 * while one such countdown is in its last minute; the rest of the page does not compose anew.
 */
@Composable
internal fun rememberLastMinuteSeconds(at: OffsetDateTime?, near: Boolean): Int? {
    if (at == null || !near) return null
    val target = remember(at) { at.toInstant().toEpochMilli() }
    val owner = remember { Any() }
    LaunchedEffect(owner, target) { SecondTicker.attend(owner) { now -> lastMinuteWakeMillis(now, target) } }
    val seconds by remember(target) {
        derivedStateOf {
            // The ticker holds whole seconds; the label goes by the time read now.
            SecondTicker.seconds.longValue
            lastMinuteSeconds(target - System.currentTimeMillis())
        }
    }
    return seconds
}
