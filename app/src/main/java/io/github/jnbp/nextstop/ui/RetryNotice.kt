package io.github.jnbp.nextstop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.screenRetryWaitMillis
import io.github.jnbp.nextstop.ui.theme.AppColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

/*
 * A request for connections that failed on a flaky network (#59): a clear message with a
 * button to try again, and the request asked again by itself while it is in view, at longer and
 * longer waits (see screenRetryWaitMillis); a battery saver stretches them further.
 */

/**
 * Asks again by [retry] while [enabled]: after the waits of screenRetryWaitMillis, each once the
 * one before has ended (the job [retry] gives back, when it gives one). Leaving the screen ends
 * it; back on it, it starts over.
 */
@Composable
fun AutoRetry(enabled: Boolean, retry: () -> Job?) {
    if (!enabled) return
    val current by rememberUpdatedState(retry)
    LaunchedEffect(Unit) {
        var attempt = 0
        while (true) {
            delay(BatterySaver.pace(screenRetryWaitMillis(attempt)))
            attempt++
            current()?.join()
        }
    }
}

/** The message [text] of a failed request and a button that asks again ([onRetry]). */
@Composable
fun RetryCard(text: String, onRetry: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    GlassSurface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(if (compact) 22.dp else 28.dp)) {
        Column(
            modifier = Modifier.padding(if (compact) 14.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
        ) {
            Text(text = problemText(text), color = AppColors.Muted, fontSize = if (compact) 13.sp else 15.sp)
            SecondaryButton(
                text = stringResource(R.string.r5search_retry),
                onClick = onRetry,
                minHeight = if (compact) 40.dp else 44.dp,
            )
        }
    }
}
