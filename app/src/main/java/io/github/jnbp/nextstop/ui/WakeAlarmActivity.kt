package io.github.jnbp.nextstop.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.AppLanguage
import io.github.jnbp.nextstop.data.plainStopName
import io.github.jnbp.nextstop.ui.theme.AppColors
import io.github.jnbp.nextstop.ui.theme.Appearance
import io.github.jnbp.nextstop.ui.theme.NextStopTheme

/**
 * The wake-up alarm over the lock screen ([WakeAlarm]): where to get off and when, and one big
 * button "Ich bin wach". It closes by itself once the alarm stops (confirmed here, in the
 * notification or on the watch, or over by itself).
 */
class WakeAlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLanguage.init(this)
        Appearance.load(this)
        // Over the lock screen and with the screen switched on, like an alarm clock.
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()
        setContent {
            NextStopTheme {
                val ringing by WakeAlarm.ringing.collectAsState()
                LaunchedEffect(ringing) { if (ringing == null) finish() }
                val target = ringing
                WakeAlarmPage(
                    stop = target?.stop?.plainStopName().orEmpty(),
                    clock = target?.arrival?.clock().orEmpty(),
                    onConfirm = {
                        WakeAlarm.confirm(this@WakeAlarmActivity)
                        finish()
                    },
                )
            }
        }
    }
}

/** The page of the ringing alarm: a pulsing ring, the stop and time, and "Ich bin wach". */
@Composable
private fun WakeAlarmPage(stop: String, clock: String, onConfirm: () -> Unit) {
    val animate = rememberAnimationsOn()
    // Read in the draw phase only (graphicsLayer): the pulse does not compose again per frame.
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        pulse.animateTo(1.12f, infiniteRepeatable(tween(700), RepeatMode.Reverse))
    }
    val entry = remember { Animatable(0.85f) }
    LaunchedEffect(Unit) { entry.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Blue)
            .systemBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.graphicsLayer {
                scaleX = entry.value
                scaleY = entry.value
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .graphicsLayer {
                        scaleX = pulse.value
                        scaleY = pulse.value
                    }
                    .clip(CircleShape)
                    .background(AppColors.Lime),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(Glyph.Alarm, color = AppColors.Ink, size = 60.dp)
            }
            Text(
                text = stringResource(R.string.wake_title),
                color = AppColors.OnBlue,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 30.sp,
                lineHeight = 36.sp,
                textAlign = TextAlign.Center,
            )
            if (stop.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.wake_text, stop, clock),
                    color = AppColors.OnBlue,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Center,
                )
            }
            SecondaryButton(
                text = stringResource(R.string.wake_confirm),
                onClick = onConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                shape = RoundedCornerShape(36.dp),
                ground = AppColors.Lime,
                ink = AppColors.OnLime,
                minHeight = 72.dp,
            )
        }
    }
}
