package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.ui.theme.AppColors
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.LiftKeys
import io.github.jnbp.nextstop.data.LiftOutage
import io.github.jnbp.nextstop.data.LiftStatus
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.plainStopName
import io.github.jnbp.nextstop.ui.theme.AppFont

/**
 * In the detail of a journey, with step-free travel set in the settings: the lifts out of
 * order where it starts, changes and ends. Nothing without the setting, without a key for a
 * lift source, or when all lifts there work.
 */
@Composable
fun JourneyLiftWarning(journey: Journey, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Follows the setting live (see rememberStepFree).
    val stepFree = rememberStepFree()
    val sources = remember { LiftKeys(context).sources() }
    if (!stepFree || sources.isEmpty()) return
    var found by remember(journey.id) { mutableStateOf<List<Pair<String, List<LiftOutage>>>>(emptyList()) }
    LaunchedEffect(journey.id) {
        found = try {
            LiftStatus.onJourney(sources, journey)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }
    if (found.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AppColors.WarningContainer)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        WarningMark(modifier = Modifier.padding(top = 2.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.lift_journey_title),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = AppColors.Ink,
            )
            found.forEach { (station, outages) ->
                Text(
                    text = liftLine(station.plainStopName(), outages),
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = AppColors.Ink,
                )
            }
        }
    }
}

/**
 * Under the name of a station on the departures page: its lifts, when a lift source has a key.
 * "All lifts working", or which are out of order.
 */
@Composable
fun StationLiftStatus(stop: Stop?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sources = remember { LiftKeys(context).sources() }
    if (stop == null || sources.isEmpty()) return
    var found by remember(stop.id) { mutableStateOf<List<LiftOutage>?>(null) }
    LaunchedEffect(stop.id) {
        found = try {
            LiftStatus.at(sources, stop)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
    val outages = found ?: return
    Row(
        modifier = modifier.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (outages.isNotEmpty()) WarningMark(size = 14.dp)
        Text(
            text = if (outages.isEmpty()) {
                stringResource(R.string.lift_all_working)
            } else {
                liftLine(stringResource(R.string.lift_station_prefix), outages)
            },
            color = if (outages.isEmpty()) AppColors.Muted else AppColors.Ink,
            fontSize = 12.sp,
            lineHeight = 15.sp,
        )
    }
}

@Composable
private fun liftLine(place: String, outages: List<LiftOutage>): String {
    val which = outages.mapNotNull { it.description }.distinct().take(2).joinToString("; ")
    return if (which.isEmpty()) {
        stringResource(R.string.lift_out_count, place, outages.size)
    } else {
        stringResource(R.string.lift_out_which, place, which)
    }
}

/** A field for a key or token: hidden while typed, kept as it is entered. */
@Composable
fun KeyField(value: String, onChange: (String) -> Unit, placeholder: String) {
    BasicTextField(
        value = value,
        onValueChange = { onChange(it.take(200)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        textStyle = TextStyle(color = AppColors.Ink, fontSize = 14.sp, fontFamily = AppFont),
        cursorBrush = SolidColor(AppColors.Blue),
        modifier = Modifier
            .widthIn(max = 260.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Chip)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        decorationBox = { innerTextField ->
            Box {
                if (value.isEmpty()) Text(text = placeholder, color = AppColors.Muted, fontSize = 14.sp, maxLines = 1)
                innerTextField()
            }
        },
    )
}

/**
 * A line under a key field when this build has a key of its own for it (see BuiltInKeys):
 * "Integrierter Schlüssel aktiv" while the field is empty, else that the entered key replaces
 * it. Nothing in a build without one. The built-in key itself is never shown.
 */
@Composable
fun BuiltInKeyNote(builtIn: Boolean, ownEntered: Boolean) {
    if (!builtIn) return
    Text(
        text = stringResource(if (ownEntered) R.string.builtin_key_overridden else R.string.builtin_key_active),
        color = if (ownEntered) AppColors.Muted else AppColors.Early,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 16.sp,
    )
}
