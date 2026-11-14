package io.github.jnbp.nextstop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.RetargetChoice
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.changeRide
import io.github.jnbp.nextstop.data.plainStopName
import io.github.jnbp.nextstop.ui.theme.AppColors

/**
 * A new destination for the followed trip, while it is searched and once it is found:
 * [target] is the new destination, [append] true for "go on to" (the old one stays on the way).
 */
sealed class RetargetState {
    abstract val target: Stop
    abstract val append: Boolean

    data class Searching(override val target: Stop, override val append: Boolean) : RetargetState()

    /** [journey] is the followed journey with the new way in place, [basedOn] the id of the one it was made from. */
    data class Found(
        override val target: Stop,
        override val append: Boolean,
        val choice: RetargetChoice,
        val journey: Journey,
        val basedOn: String,
    ) : RetargetState()

    data class Failed(override val target: Stop, override val append: Boolean) : RetargetState()
}

/**
 * The card that confirms a new destination: "Get off at X, then U7 towards Y, arrival 14:32",
 * with "Apply" and "Other options"; while searching the little train (see LoadingRow), without a way a short note.
 */
@Composable
fun RetargetDialog(
    state: RetargetState,
    onApply: (RetargetState.Found) -> Unit,
    onOthers: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        // A dialog is a window of its own: the picture of the map does not line up behind it.
        CompositionLocalProvider(LocalBackdrop provides null) {
            GlassSurface(shape = RoundedCornerShape(32.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val name = state.target.name.plainStopName()
                    Text(
                        text = stringResource(if (state.append) R.string.retarget_append_title else R.string.retarget_title, name),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    when (state) {
                        is RetargetState.Searching -> LoadingRow(
                            text = stringResource(R.string.retarget_searching),
                            compact = true,
                            modifier = Modifier.padding(start = 4.dp),
                        )

                        is RetargetState.Failed -> Text(
                            text = stringResource(R.string.retarget_none),
                            color = AppColors.Muted,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(start = 4.dp),
                        )

                        is RetargetState.Found -> {
                            Text(
                                text = retargetSummary(state.choice),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                lineHeight = 21.sp,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                            Text(
                                text = stringResource(R.string.retarget_arrival, state.choice.option.arrival.clock()),
                                color = AppColors.Muted,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                            PrimaryButton(
                                text = stringResource(R.string.faster_take_action),
                                onClick = { onApply(state) },
                                modifier = Modifier.fillMaxWidth(),
                                minHeight = 50.dp,
                            )
                        }
                    }
                    if (state !is RetargetState.Searching) {
                        SecondaryButton(
                            text = stringResource(R.string.retarget_others),
                            onClick = onOthers,
                            modifier = Modifier.fillMaxWidth(),
                            ground = AppColors.BlueContainer,
                            ink = AppColors.OnBlueContainer,
                            minHeight = 48.dp,
                        )
                    }
                    SecondaryButton(
                        text = stringResource(R.string.cancel),
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        ground = AppColors.Chip,
                        minHeight = 48.dp,
                    )
                }
            }
        }
    }
}

/** "Get off at X, then U7 towards Y", "From X with the U7 towards Y", or on foot from there. */
@Composable
private fun retargetSummary(choice: RetargetChoice): String {
    val point = choice.point
    val place = point.place.name.plainStopName()
    val ride = changeRide(point.kept, point.cut, choice.option)
    val line = ride?.let { shortLine(it.line) ?: it.line }
    val direction = ride?.direction?.plainStopName()
    val then = when {
        line == null -> stringResource(R.string.retarget_then_walk)
        direction != null -> stringResource(R.string.retarget_then_line_towards, line, direction)
        else -> stringResource(R.string.retarget_then_line, line)
    }
    return if (point.onBoard) {
        stringResource(R.string.retarget_get_off, place, then)
    } else {
        stringResource(R.string.retarget_from, place, then)
    }
}
