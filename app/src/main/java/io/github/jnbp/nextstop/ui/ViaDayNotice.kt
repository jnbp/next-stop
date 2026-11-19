package io.github.jnbp.nextstop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.Via
import io.github.jnbp.nextstop.data.plainStopName
import io.github.jnbp.nextstop.data.viaFoundTooLate
import io.github.jnbp.nextstop.ui.theme.AppColors
import java.time.OffsetDateTime

/*
 * A search through a place on the way that finds nothing soon (#35a: "über Bonn" at 20:39 came
 * back with connections from 05:21 the next morning only) says so above the connections,
 * instead of showing tomorrow's as if they were the next ones. Kept here, apart from AppRoot,
 * so the search only hands over what it found (see [ViaDayNotice.note]).
 */

/** What the note says: the name of the place on the way and when the first connection leaves. */
data class ViaDayNote(val place: String, val first: OffsetDateTime, val nextDay: Boolean)

/** The note of the search shown; null where there is none to give. */
object ViaDayNotice {
    val state = mutableStateOf<ViaDayNote?>(null)

    /** No note: a new search is on its way. */
    fun clear() {
        state.value = null
    }

    /**
     * The note for [journeys] found through [via] leaving at [asked] (now where null), or none
     * where the search had no place on the way, was by arrival, or found something soon (see
     * viaFoundTooLate). Without any journey the list says that nothing was found.
     */
    fun note(via: Via?, asked: OffsetDateTime?, byArrival: Boolean, journeys: List<Journey>?) {
        val time = asked ?: OffsetDateTime.now()
        if (via == null || byArrival || journeys.isNullOrEmpty() || !viaFoundTooLate(time, journeys)) {
            state.value = null
            return
        }
        val first = journeys.minOf { it.departure }
        val nextDay = first.atZoneSameInstant(BERLIN).toLocalDate() != time.atZoneSameInstant(BERLIN).toLocalDate()
        state.value = ViaDayNote(via.place.name.plainStopName(), first, nextDay)
    }
}

/** The note of [ViaDayNotice] as a quiet card that slides in and out. */
@Composable
fun ViaDayNoteCard(modifier: Modifier = Modifier) {
    val note = ViaDayNotice.state.value
    // The last note stays while the card slides out.
    val shown = remember { arrayOfNulls<ViaDayNote>(1) }
    if (note != null) shown[0] = note
    AnimatedVisibility(
        visible = note != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        val current = shown[0] ?: return@AnimatedVisibility
        GlassSurface(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(
                    if (current.nextDay) R.string.via_none_today else R.string.via_none_soon,
                    current.place,
                    current.first.clock(),
                ),
                color = AppColors.Muted,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            )
        }
    }
}
