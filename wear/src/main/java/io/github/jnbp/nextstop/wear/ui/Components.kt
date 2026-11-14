package io.github.jnbp.nextstop.wear.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.em
import kotlinx.coroutines.delay
import io.github.jnbp.nextstop.wear.data.millisToNextMinuteChange
import io.github.jnbp.nextstop.wear.data.stopNameParts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import io.github.jnbp.nextstop.wear.R
import io.github.jnbp.nextstop.wear.data.Departure
import io.github.jnbp.nextstop.wear.data.lineArgb
import io.github.jnbp.nextstop.wear.data.minutesUntil
import io.github.jnbp.nextstop.wear.data.onLineArgb

/** The badge of a line in its colour, as on the phone; [color] the colour the phone sent for the ride. */
@Composable
fun LineBadge(line: String?, product: String, modifier: Modifier = Modifier, color: Long? = null) {
    val argb = lineArgb(line, product, color)
    Box(
        modifier = modifier
            .widthIn(min = 30.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(argb))
            .padding(horizontal = 5.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = line ?: "?",
            color = Color(onLineArgb(argb)),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/** "now" or "5 min" until [time]. */
@Composable
fun minutesLabel(time: Long?): String {
    if (time == null) return "–"
    val minutes = minutesUntil(time)
    return if (minutes <= 0) stringResource(R.string.now) else stringResource(R.string.in_min, minutes.toInt())
}

/** "now" or "5'" until [time] at [now]: the big minutes of the start page and the board (see [DepartureRow]). */
@Composable
fun bigMinutesLabel(time: Long?, now: Long): String {
    if (time == null) return "–"
    val minutes = minutesUntil(time, now)
    return if (minutes <= 0) stringResource(R.string.now) else stringResource(R.string.minutes_short, minutes.toInt())
}

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

fun clock(time: Long): String = CLOCK.format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()))

/** How old something loaded at [time] is: "just now", "5 min ago", or "at 14:02" after an hour. */
fun ageText(context: Context, time: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = ((now - time) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> context.getString(R.string.age_now)
        minutes < 60 -> context.getString(R.string.age_minutes, minutes.toInt())
        else -> context.getString(R.string.age_at, clock(time))
    }
}

/** "Last loaded 5 min ago": the note over data that could not be loaded again. */
@Composable
fun lastLoadedText(time: Long): String {
    val context = LocalContext.current
    return stringResource(R.string.last_loaded, ageText(context, time))
}

// From this delay on (minutes) the minutes of a run are amber, as on the phone.
private const val DELAY_TINT_MINUTES = 3

// The amber of delayed minutes.
private val DELAY_AMBER = Color(0xFFFFB74D)

// Extra room at both sides of a departure row, as a share of the screen width, on top of the
// list's own padding (ScreenScaffoldDefaults.contentPadding): rows that scroll towards the top or
// bottom of a round screen keep their minutes and badge inside the circle (4 dp at 192 dp).
private const val ROW_EDGE_FRACTION = 0.02f

// The least width of the big minutes, so "5'" and "12'" line up and nothing pushes them to the edge.
private val MINUTES_MIN_WIDTH = 30.dp

/**
 * One departure, with its minutes as a big bold number with an apostrophe ("5'", "now" at 0)
 * counted at [now] (the page's clock, see [rememberCountdownClock]), rolling as they count
 * down, amber from DELAY_TINT_MINUTES of delay. Compact (the start page): badge, direction in
 * one line, minutes. With [details] (the departure board) two lines: the badge with the
 * platform, the delay and "cancelled" beside it, the direction below in up to two lines, and
 * the minutes at the side over both, so long destinations wrap instead of being cut and the
 * minutes never meet the round edge. A cancelled run keeps its place by its planned time (see
 * departureBoard): its direction struck through, its minutes too (board) or "cancelled" in red
 * in their place (compact).
 */
@Composable
fun DepartureRow(departure: Departure, now: Long, details: Boolean = false, modifier: Modifier = Modifier) {
    val edge = (LocalConfiguration.current.screenWidthDp * ROW_EDGE_FRACTION).dp
    val delay = departure.delayMinutes ?: 0
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val minutesColor = when {
        departure.cancelled -> if (details) muted else MaterialTheme.colorScheme.error
        delay >= DELAY_TINT_MINUTES -> DELAY_AMBER
        else -> MaterialTheme.colorScheme.onSurface
    }
    val struck = if (departure.cancelled) TextDecoration.LineThrough else null
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = edge, vertical = if (details) 4.dp else 2.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (details) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LineBadge(departure.line, departure.product)
                    val notes = listOfNotNull(
                        platformText(departure.platform),
                        if (delay > 0 && !departure.cancelled) stringResource(R.string.delay_plus, delay) else null,
                    )
                    if (notes.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = notes.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (delay >= DELAY_TINT_MINUTES && !departure.cancelled) DELAY_AMBER else muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    if (departure.cancelled) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.cancelled),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = departure.direction,
                    style = MaterialTheme.typography.bodyMedium,
                    textDecoration = struck,
                    color = if (departure.cancelled) muted else Color.Unspecified,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            LineBadge(departure.line, departure.product)
            Spacer(Modifier.width(6.dp))
            Text(
                text = departure.direction,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                textDecoration = struck,
                color = if (departure.cancelled) muted else Color.Unspecified,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(6.dp))
        // The minutes roll as they count down (see WatchSwap).
        val bigText = !departure.cancelled || details
        WatchSwap(
            target = if (bigText) bigMinutesLabel(departure.time, now) else stringResource(R.string.cancelled),
            roll = !departure.cancelled,
            contentAlignment = Alignment.CenterEnd,
            modifier = Modifier.widthIn(min = MINUTES_MIN_WIDTH),
        ) { label ->
            Text(
                text = label,
                color = minutesColor,
                style = if (bigText) MaterialTheme.typography.numeralExtraSmall else MaterialTheme.typography.labelMedium,
                fontWeight = if (bigText) FontWeight.Bold else null,
                textDecoration = if (details) struck else null,
                textAlign = TextAlign.End,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

// Room at both sides of a stop header, as a share of the screen width, on top of the list's own
// padding: the header is the first thing at the narrow top of a round screen (Wear's list
// headers keep about this much more than the rows; 23 dp at 192 dp).
private const val HEADER_EDGE_FRACTION = 0.12f

// The size the name of a header may shrink to before it is cut with "…", in sp.
private const val HEADER_MIN_SP = 12

// The least height of a header that can be tapped (Wear's touch target).
private val HEADER_MIN_HEIGHT = 48.dp

/**
 * The header of a stop's departures (the start page and the board): the stop's name as the
 * watch shows it (see stopNameParts: without the town it is used in) in up to two centred lines
 * that shrink a little before anything is cut, and below it in small type the part of the
 * station, then [detail] ("120 m · 5 min ago"). Kept clear of the round edge at the top. With
 * [onClick] the whole header is a touch target; [description] is read out instead of the text.
 */
@Composable
fun StopHeader(
    name: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    description: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val parts = stopNameParts(name)
    val small = listOfNotNull(parts.part, detail?.takeIf { it.isNotBlank() }).joinToString(" · ").ifEmpty { null }
    val edge = (LocalConfiguration.current.screenWidthDp * HEADER_EDGE_FRACTION).dp
    val titleStyle = MaterialTheme.typography.titleMedium
    val onSurface = MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = HEADER_MIN_HEIGHT)
            .then(if (onClick != null) Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick) else Modifier)
            .padding(horizontal = edge, vertical = 6.dp)
            .semantics(mergeDescendants = true) {
                heading()
                if (description != null) contentDescription = description
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BasicText(
            text = parts.title,
            style = titleStyle.merge(TextStyle(color = onSurface, textAlign = TextAlign.Center, lineHeight = 1.15.em)),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = HEADER_MIN_SP.sp, maxFontSize = titleStyle.fontSize),
        )
        if (small != null) {
            Text(
                text = small,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The time now for the countdowns of [times], set anew each time the minutes of one of them
 * change (see millisToNextMinuteChange), at least once a minute, while [running]. No request:
 * the minutes count down from the departures already loaded.
 */
@Composable
fun rememberCountdownClock(times: List<Long>, running: Boolean): State<Long> {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(times, running) {
        now.longValue = System.currentTimeMillis()
        if (!running) return@LaunchedEffect
        while (true) {
            delay(millisToNextMinuteChange(times, System.currentTimeMillis()))
            now.longValue = System.currentTimeMillis()
        }
    }
    return now
}

/** A thin bar for how much of a trip lies behind. */
@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(4.dp)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/**
 * A small action: a compact pill of one line with an optional icon, filled for the one main
 * action of a page ([primary]), tonal for the others.
 */
@Composable
fun SmallAction(
    text: String,
    onClick: () -> Unit,
    icon: Int? = null,
    primary: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // Gives way a little under the finger and springs back, as the buttons of the phone do.
    val press = remember { MutableInteractionSource() }
    CompactButton(
        onClick = onClick,
        modifier = modifier.watchSpringPress(press),
        interactionSource = press,
        colors = if (primary) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
        icon = if (icon != null) {
            { Icon(painterResource(icon), contentDescription = null) }
        } else {
            null
        },
        label = { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    )
}

/** A short centred note, for empty and loading states. */
@Composable
fun Note(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
