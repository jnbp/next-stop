package io.github.jnbp.nextstop.wear.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import io.github.jnbp.nextstop.wear.R
import io.github.jnbp.nextstop.wear.data.Journey
import io.github.jnbp.nextstop.wear.data.PlanRide
import io.github.jnbp.nextstop.wear.data.Place
import io.github.jnbp.nextstop.wear.data.journeyPlan
import io.github.jnbp.nextstop.wear.data.shownPlaceName

/*
 * The overview of a connection of the results before it starts (#175): a tap on its card
 * opens it (the card no longer starts the trip). Every ride with its line, its departure,
 * platform and direction, between two rides the minutes on foot and how tight the change is,
 * at the end the arrival; "Start" as the EdgeButton at the foot, as "Fahrt beenden" on the
 * overview of a trip. The crown scrolls the list.
 */

/** The overview of [journey] to [destination]; [onStart] follows it (see startJourney). */
@Composable
fun JourneyDetailScreen(journey: Journey, destination: Place?, onStart: () -> Unit) {
    val plan = remember(journey) { journeyPlan(journey) }
    val display = rememberDelayDisplay()
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
    val edgeInfo = remember(listState) { edgeScrollInfo(listState) }
    ScreenScaffold(
        scrollInfoProvider = edgeInfo,
        edgeButton = { EdgeAction(text = stringResource(R.string.start), onClick = onStart, icon = R.drawable.ic_play, primary = true) },
    ) { contentPadding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize().fadeUnderTimeText(contentPadding.calculateTopPadding()).crownFocus(),
        ) {
            item {
                ListHeader {
                    Text(destination?.let { shownPlaceName(it) } ?: stringResource(R.string.r6w2_journey), maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
            item {
                // The times as the phone's delay setting says (#227b).
                val muted = MaterialTheme.colorScheme.onSurfaceVariant
                Text(
                    shownTimesText("\u0000 – \u0001 · ", display, journeyTimes(journey), muted, muted) +
                        AnnotatedString(if (journey.transfers == 0) stringResource(R.string.direct) else stringResource(R.string.changes, journey.transfers)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            plan.rides.forEachIndexed { index, ride ->
                if (index > 0 || ride.walkBefore > 0) item { ChangeBetween(ride, first = index == 0) }
                item { PlanRideRow(ride, current = false, past = false) }
            }
            if (plan.walkEnd > 0) {
                item {
                    Text(
                        stringResource(R.string.walk_on_foot, plan.walkEnd),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                Text(
                    shownTimesText(stringResource(R.string.arrival_at, "\u0001"), display, journeyTimes(journey), MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.onSurfaceVariant) +
                        AnnotatedString(destination?.let { " · " + shownPlaceName(it) } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Before a ride: the minutes on foot (to the first ride, or of the change), and for a change
 * how tight it is as a small chip in the colour of its rating ("knapp · 3′ Umstieg").
 */
@Composable
private fun ChangeBetween(ride: PlanRide, first: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (ride.walkBefore > 0) {
            Text(
                stringResource(if (ride.bikeBefore) R.string.cycle_minutes else R.string.walk_on_foot, ride.walkBefore),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        val rating = ride.rating
        if (!first && rating != null) {
            if (ride.walkBefore > 0) Spacer(Modifier.width(6.dp))
            Text(
                ratingText(rating, ride.spare, ride.gap),
                style = MaterialTheme.typography.labelSmall,
                color = ratingColor(rating),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}
