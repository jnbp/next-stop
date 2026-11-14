package io.github.jnbp.nextstop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.ui.theme.AppColors
import io.github.jnbp.nextstop.wear.WatchLink
import io.github.jnbp.nextstop.wear.WatchState
import io.github.jnbp.nextstop.wear.WatchStatus

/**
 * The state of the watch, checked at once and then every 10 seconds while shown, so a watch
 * paired or an app installed meanwhile shows up; null until the first check is done.
 */
@Composable
fun rememberWatchStatus(): WatchStatus? {
    val context = LocalContext.current
    var status by remember { mutableStateOf<WatchStatus?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            status = WatchLink.check(context)
            delay(10_000)
        }
    }
    return status
}

/**
 * One row about the watch: "Watch connected ✓" when Next Stop is on it, else a button that
 * opens its store page on the watch; without a watch "No watch connected".
 */
@Composable
fun WatchStatusRow(status: WatchStatus?, shape: Shape = RoundedCornerShape(20.dp)) {
    val context = LocalContext.current
    var sent by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.Card)
            .defaultMinSize(minHeight = 56.dp)
            .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val title: String
        val text: String
        when (status?.state) {
            null -> {
                title = stringResource(R.string.watch_checking)
                text = ""
            }
            WatchState.None -> {
                title = stringResource(R.string.watch_none)
                text = stringResource(R.string.watch_none_text)
            }
            WatchState.NoApp -> {
                title = stringResource(R.string.watch_no_app)
                text = if (sent) stringResource(R.string.watch_install_sent) else status?.name.orEmpty()
            }
            WatchState.Connected -> {
                title = stringResource(R.string.watch_connected)
                text = status?.name.orEmpty()
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            if (text.isNotEmpty()) Text(text = text, color = AppColors.Muted, fontSize = 12.sp, lineHeight = 16.sp)
        }
        if (status?.state == WatchState.NoApp) {
            PrimaryButton(
                text = stringResource(R.string.watch_install),
                onClick = { sent = WatchLink.installOnWatch(context, status?.nodeId) },
                shape = RoundedCornerShape(16.dp),
                minHeight = 40.dp,
            )
        }
    }
}
