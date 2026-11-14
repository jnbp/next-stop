package io.github.jnbp.nextstop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.OffsetDateTime
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.Departure
import io.github.jnbp.nextstop.data.OsmLine
import io.github.jnbp.nextstop.ui.theme.AppColors

/** How many departures the card of a stop position lists. */
private const val POSITION_DEPARTURES = 3

/**
 * The content of the card of a tapped stop position of the marked stop ("Pos. 3 · S+U
 * Hauptbahnhof"), in place of the stop's own card: its lines as badges (a tap opens the
 * line's course from here, [onLine]), the next [departures] from this position only, and
 * "Route from here" ([onRouteHere]) and "Walk here" ([onWalkHere]). [onBack] leads back to the
 * card of the stop.
 */
@Composable
fun PositionCardContent(
    position: MapPositionPick,
    stopName: String,
    departures: List<Departure>,
    now: OffsetDateTime,
    isFavorite: Boolean,
    onDepartureAction: (Departure, DepartureAction) -> Unit,
    onLine: (OsmLine) -> Unit,
    onRouteHere: () -> Unit,
    onWalkHere: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Departures that arrive take their room softly and come in one after the other.
    Column(modifier = modifier.softSize().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundButton(
                glyph = Glyph.Back,
                description = stringResource(R.string.position_back),
                onClick = onBack,
                size = 36.dp,
                solid = true,
                modifier = Modifier.padding(end = 8.dp),
            )
            val pos = position.pos
            Text(
                text = if (pos != null) {
                    stringResource(R.string.position_card_title, pos, stopName)
                } else {
                    stringResource(R.string.position_card_plain, stopName)
                },
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        if (position.lines.isNotEmpty()) {
            val scroll = rememberScrollState()
            Row(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .fadeEdges(scroll, horizontal = true)
                    .horizontalScroll(scroll),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                position.lines.forEach { line ->
                    LineBadge(
                        line = line.name,
                        product = line.product,
                        minWidth = 38.dp,
                        at = position.position,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onLine(line) },
                    )
                }
            }
        }
        if (departures.isEmpty()) {
            Text(
                // A bare position (the hollow dot on the map): OSM says nothing about it.
                text = if (position.pos == null && position.lines.isEmpty()) {
                    stringResource(R.string.position_no_lines)
                } else {
                    stringResource(R.string.position_no_departures)
                },
                color = AppColors.Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 10.dp),
            )
        } else {
            departures.take(POSITION_DEPARTURES).forEachIndexed { index, departure ->
                if (index > 0) RowDivider()
                DepartureItem(
                    departure = departure,
                    now = now,
                    stopFavorite = isFavorite,
                    onAction = { action -> onDepartureAction(departure, action) },
                    modifier = appearModifier(index).clip(RoundedCornerShape(14.dp)),
                )
            }
        }
        Row(
            modifier = Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PrimaryButton(
                text = stringResource(R.string.route_from_here),
                onClick = onRouteHere,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(26.dp, 10.dp, 10.dp, 26.dp),
                minHeight = 52.dp,
            )
            SecondaryButton(
                text = stringResource(R.string.position_walk_here),
                onClick = onWalkHere,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp, 26.dp, 26.dp, 10.dp),
                minHeight = 52.dp,
            )
        }
    }
}
