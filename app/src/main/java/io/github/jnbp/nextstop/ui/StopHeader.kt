package io.github.jnbp.nextstop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.data.Departure
import io.github.jnbp.nextstop.data.briefStopName
import io.github.jnbp.nextstop.data.sameTown
import io.github.jnbp.nextstop.data.splitStopTown
import io.github.jnbp.nextstop.ui.theme.AppColors
import io.github.jnbp.nextstop.ui.theme.readableOn

/*
 * The header of a departure in a list of several stops ("All stops nearby", a group of stops):
 * a small dot in the colour of the stop, the stop by name, and in the order of "Wann los?"
 * when to set off and how long the walk is: "● 🚶 Hauptstraße · los in 2 min · 9 min zu Fuß".
 * The stop stands in front and is cut last, so every row says where it leaves from; the line
 * under the direction keeps only the time, the delay and the platform.
 */

/** The stop [departure] leaves from, as a row of a list of several stops shows it; null in a list of one stop. */
internal fun departureStopLabel(departure: Departure, boardTown: String?): String? {
    val name = departure.stop?.name ?: return null
    val split = splitStopTown(name)
    val brief = split.stop.briefStopName()
    val town = split.town
    return if (town == null || sameTown(town, boardTown)) brief else "$brief · $town"
}

// So many marker colours; more stops than this share colours (a list of nearby stops has at most ten).
internal const val STOP_MARKER_COUNT = 10

/**
 * A marker colour for each of the stops [stopIds] (an index below [STOP_MARKER_COUNT]): taken
 * from the stop's id, so a stop keeps its colour while the list refreshes, and different from
 * the others of the list as long as there are colours left (a taken one moves on to the next
 * free one, the stops in the order of their ids, so the result does not depend on the order
 * of the departures).
 */
internal fun stopMarkers(stopIds: Collection<String>): Map<String, Int> {
    val taken = BooleanArray(STOP_MARKER_COUNT)
    val result = LinkedHashMap<String, Int>()
    stopIds.distinct().sorted().forEach { id ->
        val start = Math.floorMod(id.hashCode(), STOP_MARKER_COUNT)
        var index = start
        if (taken.all { it }) taken.fill(false)
        while (taken[index]) index = (index + 1) % STOP_MARKER_COUNT
        taken[index] = true
        result[id] = index
    }
    return result
}

// The hues of the markers, spread over the colour wheel in an order that puts neighbours apart.
private val MARKER_HUES = floatArrayOf(4f, 200f, 45f, 280f, 140f, 330f, 25f, 175f, 240f, 90f)

/** The colour of marker [index], readable as a mark on the cards of the scheme. */
internal fun stopMarkerColor(index: Int): Color {
    val hue = MARKER_HUES[Math.floorMod(index, MARKER_HUES.size)]
    val hsl = floatArrayOf(hue, 0.65f, if (AppColors.palette.dark) 0.62f else 0.45f)
    val color = Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
    return readableOn(AppColors.Card, color, minimum = 3f)
}

// Room the stop name keeps before the walk gives way: about 16 characters at its size.
private val STOP_NAME_MIN = 124.dp

// The space between the parts of the header.
private val HEADER_GAP = 6.dp

/**
 * The header of a row of several stops: [marker] dot and, with [leave], the walk icon in
 * [leaveColor]; the [stopName] (medium weight); then [leave] ("· los in 2 min") and [walk]
 * ("· 9 min zu Fuß"). When room is short the walk is cut first, then the stop name, but not
 * below about 16 characters; the time to set off is never cut.
 */
@Composable
internal fun StopRowHeader(
    stopName: String,
    marker: Color?,
    modifier: Modifier = Modifier,
    leaveColor: Color = AppColors.Muted,
    leave: (@Composable () -> Unit)? = null,
    walk: String? = null,
) {
    Layout(
        modifier = modifier,
        content = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (marker != null) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(marker))
                }
                if (leave != null) {
                    if (marker != null) Box(modifier = Modifier.size(HEADER_GAP))
                    GlyphIcon(Glyph.Walk, color = leaveColor, size = 14.dp)
                }
            }
            Text(
                text = stopName,
                color = AppColors.Ink,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leave != null) {
                    Text(text = "· ", color = AppColors.Muted, fontSize = 14.sp, maxLines = 1, softWrap = false)
                    leave()
                }
            }
            Text(
                text = walk?.let { "· $it" }.orEmpty(),
                color = AppColors.Muted,
                fontSize = 13.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        },
    ) { measurables, constraints ->
        val gap = HEADER_GAP.roundToPx()
        val loose = Constraints(maxHeight = constraints.maxHeight)
        val prefix = measurables[0].measure(loose)
        val leavePlaceable = measurables[2].measure(loose)
        val total = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE / 2
        fun gapAfter(width: Int) = if (width > 0) gap else 0
        // What the stop name and the walk share once the marker and the time to set off stand.
        val shared = (total - prefix.width - gapAfter(prefix.width) - leavePlaceable.width - gapAfter(leavePlaceable.width)).coerceAtLeast(0)
        val nameWanted = measurables[1].maxIntrinsicWidth(constraints.maxHeight)
        val walkWanted = measurables[3].maxIntrinsicWidth(constraints.maxHeight)
        val walkRoom = if (walkWanted > 0) walkWanted + gap else 0
        // The name first, at least its minimum; the walk takes what is left.
        val nameWidth = minOf(nameWanted, maxOf(STOP_NAME_MIN.roundToPx(), shared - walkRoom), shared)
        val name = measurables[1].measure(Constraints(maxWidth = nameWidth, maxHeight = constraints.maxHeight))
        val walkLeft = shared - name.width - gap
        // A walk cut to almost nothing is left out rather than shown as "…".
        val walkPlaceable = if (walkWanted > 0 && walkLeft >= MIN_WALK_ROOM.roundToPx()) {
            measurables[3].measure(Constraints(maxWidth = minOf(walkLeft, walkWanted), maxHeight = constraints.maxHeight))
        } else {
            null
        }
        val parts = listOfNotNull(prefix.takeIf { it.width > 0 }, name, leavePlaceable.takeIf { it.width > 0 }, walkPlaceable)
        val height = parts.maxOf { it.height }
        val width = parts.sumOf { it.width } + gap * (parts.size - 1)
        layout(if (constraints.hasBoundedWidth) constraints.maxWidth else width, height) {
            var x = 0
            parts.forEach { part ->
                part.placeRelative(x, (height - part.height) / 2)
                x += part.width + gap
            }
        }
    }
}

// Less room than this and the walk is left out of the header.
private val MIN_WALK_ROOM = 40.dp

/** A row of a list of several stops in the order of departure: its header (the stop and its dot) over the departure. */
@Composable
internal fun StopDepartureHeader(departure: Departure, boardTown: String?, markers: Map<String, Int>, modifier: Modifier = Modifier) {
    val name = departureStopLabel(departure, boardTown) ?: return
    val marker = departure.stop?.id?.let { markers[it] }?.let { stopMarkerColor(it) }
    StopRowHeader(stopName = name, marker = marker, modifier = modifier)
}
