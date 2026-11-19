package io.github.jnbp.nextstop.wear.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import io.github.jnbp.nextstop.wear.R
import io.github.jnbp.nextstop.wear.data.WakeRing

/** The wake-up alarm of the phone on the watch: where and when to get off, and "Ich bin wach". */
class WakeAlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContent {
            MaterialTheme {
                val ringing by WakeRing.ringing.collectAsState()
                LaunchedEffect(ringing) { if (ringing == null) finish() }
                val info = ringing
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                ) {
                    Text(
                        text = stringResource(R.string.wake_title),
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        color = Brand.Lime,
                    )
                    if (info != null) {
                        Text(
                            text = stringResource(R.string.wake_text, info.stop, info.arrival),
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                        )
                    }
                    val press = rememberWatchPress()
                    Button(
                        onClick = {
                            WakeRing.confirm(this@WakeAlarmActivity)
                            finish()
                        },
                        modifier = Modifier.fillMaxWidth().then(press.modifier),
                        interactionSource = press.source,
                        colors = brandButtonColors(),
                        label = { Text(stringResource(R.string.wake_confirm), maxLines = 1) },
                    )
                }
            }
        }
    }
}
