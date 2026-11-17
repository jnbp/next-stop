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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.em
import kotlinx.coroutines.delay
import io.github.jnbp.nextstop.wear.data.hoursAndMinutes
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
import io.github.jnbp.nextstop.wear.data.badgeLine
import io.github.jnbp.nextstop.wear.data.TravelMode
import io.github.jnbp.nextstop.wear.data.modeHint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import io.github.jnbp.nextstop.wear.data.lineArgb
import io.github.jnbp.nextstop.wear.data.minutesUntil
import io.github.jnbp.nextstop.wear.data.DelayTone
import io.github.jnbp.nextstop.wear.data.MinutesTone
import io.github.jnbp.nextstop.wear.data.delayTone
import io.github.jnbp.nextstop.wear.data.minutesTone
import io.github.jnbp.nextstop.wear.data.onLineArgb

/** The badge of a line in its colour, as on the phone; [color] the colour the phone sent for the ride. */
@Composable
fun LineBadge(line: String?, product: String, modifier: Modifier = Modifier, color: Long? = null) {
    val argb = lineArgb(line, product, color)
    val mode = modeHint(line, product)
    val spoken = mode?.let { listOfNotNull(modeWord(it), line).joinToString(" ") }
    Box(
        modifier = modifier
            .then(if (spoken != null) Modifier.clearAndSetSemantics { contentDescription = spoken } else Modifier)
            // Never wider than a short name: a long one is shortened (see badgeLine), not cut off.
            .widthIn(min = 30.dp, max = if (mode != null) 104.dp else 92.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(argb))
            .padding(horizontal = 5.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        val ink = Color(onLineArgb(argb))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            // The kind of vehicle where the name does not tell it (see modeHint).
            if (mode != null) ModeMark(mode, ink, 10.dp)
            Text(
                text = badgeLine(line) ?: "?",
                color = ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The word for [mode], read out before the name of a line whose badge shows its icon. */
@Composable
fun modeWord(mode: TravelMode): String =
    stringResource(if (mode == TravelMode.Bus) R.string.mode_bus else R.string.mode_train)

/** A small icon of a bus or a train in [color], as on the phone's badges (twin of its ModeMark). */
@Composable
fun ModeMark(mode: TravelMode, color: Color, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val line = w * 0.11f
        val stroke = Stroke(width = line, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (mode) {
            TravelMode.Bus -> {
                drawRoundRect(color, Offset(w * 0.16f, w * 0.08f), Size(w * 0.68f, w * 0.7f), CornerRadius(w * 0.12f), style = stroke)
                drawLine(color, Offset(w * 0.16f, w * 0.44f), Offset(w * 0.84f, w * 0.44f), strokeWidth = line)
                drawCircle(color, radius = w * 0.09f, center = Offset(w * 0.33f, w * 0.86f))
                drawCircle(color, radius = w * 0.09f, center = Offset(w * 0.67f, w * 0.86f))
            }
            TravelMode.Train -> {
                drawRoundRect(color, Offset(w * 0.2f, w * 0.06f), Size(w * 0.6f, w * 0.7f), CornerRadius(w * 0.22f), style = stroke)
                drawLine(color, Offset(w * 0.2f, w * 0.38f), Offset(w * 0.8f, w * 0.38f), strokeWidth = line)
                drawCircle(color, radius = w * 0.06f, center = Offset(w * 0.36f, w * 0.57f))
                drawCircle(color, radius = w * 0.06f, center = Offset(w * 0.64f, w * 0.57f))
                drawLine(color, Offset(w * 0.34f, w * 0.8f), Offset(w * 0.22f, w * 0.96f), strokeWidth = line, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.66f, w * 0.8f), Offset(w * 0.78f, w * 0.96f), strokeWidth = line, cap = StrokeCap.Round)
            }
        }
    }
}

/** "now", "5 min", or from an hour on "2 h 23 min" until [time] (see [hoursAndMinutes]). */
@Composable
fun minutesLabel(time: Long?): String {
    if (time == null) return "–"
    val minutes = minutesUntil(time)
    if (minutes <= 0) return stringResource(R.string.now)
    val (hours, rest) = hoursAndMinutes(minutes)
    return when {
        hours == null -> stringResource(R.string.in_min, minutes.toInt())
        rest == null -> stringResource(R.string.in_whole_hours, hours.toInt())
        else -> stringResource(R.string.in_hours, hours.toInt(), rest.toInt())
    }
}

/** "5 min", or from an hour on "2 h 23 min", for [minutes], outside of the interface (a tile). */
fun spanText(context: Context, minutes: Long): String {
    val (hours, rest) = hoursAndMinutes(minutes)
    return when {
        hours == null -> context.getString(R.string.in_min, (rest ?: 0L).toInt())
        rest == null -> context.getString(R.string.in_whole_hours, hours.toInt())
        else -> context.getString(R.string.in_hours, hours.toInt(), rest.toInt())
    }
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

// Extra room at both sides of a departure row, as a share of the screen width, on top of the
// list's own padding (ScreenScaffoldDefaults.contentPadding): rows that scroll towards the top or
// bottom of a round screen keep their minutes and badge inside the circle (4 dp at 192 dp).
private const val ROW_EDGE_FRACTION = 0.02f

// The least width of the big minutes, so "5'" and "12'" line up and nothing pushes them to the edge.
private val MINUTES_MIN_WIDTH = 30.dp

/**
 * One departure, with its minutes as a big bold number with an apostrophe ("5'", "now" at 0)
 * counted at [now] (the page's clock, see [rememberCountdownClock]), rolling as they count
 * down, marked as on the phone (see DepartureTones: lime within three minutes, the delay
 * beside them red or green). Compact (the start page): badge, direction in
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
    // Marked as on the phone (see DepartureTones): the minutes in lime from three minutes on,
    // "now" included, late or not; the delay beside them red when late, green when early.
    val minutes = departure.time?.let { minutesUntil(it, now) }
    val minutesColor = when (minutesTone(minutes, departure.cancelled)) {
        MinutesTone.Cancelled -> if (details) muted else MaterialTheme.colorScheme.error
        MinutesTone.Gone -> muted
        MinutesTone.Soon -> Brand.Lime
        MinutesTone.Plain -> MaterialTheme.colorScheme.onSurface
    }
    // The delay ("+4", "-1") is never what gets cut off: the platform or the direction give way first.
    val delayTone = if (departure.cancelled) null else delayTone(departure.delayMinutes != null, delay.toLong())
    val delayText = delayTone?.let { stringResource(R.string.delay_plus, delay) }
    val delayColor = when (delayTone) {
        DelayTone.Late -> Brand.Late
        DelayTone.Early -> Brand.Early
        null -> muted
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
                    val platform = platformText(departure.platform, departure.line)
                    if (platform != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = platform,
                            style = MaterialTheme.typography.labelSmall,
                            color = muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    if (delayText != null) {
                        Spacer(Modifier.width(if (platform != null) 4.dp else 6.dp))
                        Text(
                            text = delayText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = delayColor,
                            maxLines = 1,
                            softWrap = false,
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
            // A late or early run says by how much, as on the phone.
            if (delayText != null) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = delayText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = delayColor,
                    maxLines = 1,
                    softWrap = false,
                )
            }
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
 * station, then [detail] ("120 m · 5 min ago"); above it in small type [kicker], what the page
 * shows of the stop ("Departures"). Kept clear of the round edge at the top. With
 * [onClick] the whole header is a touch target; [description] is read out instead of the text.
 */
@Composable
fun StopHeader(
    name: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    framed: Boolean = false,
    kicker: String? = null,
) {
    val parts = stopNameParts(name)
    val plain = listOfNotNull(parts.part, detail?.takeIf { it.isNotBlank() }).joinToString(" · ").ifEmpty { null }
    // A framed header shows that it can be pressed: a tinted ground and a chevron after its small line.
    val small = if (framed && onClick != null) listOfNotNull(plain, "\u203A").joinToString(" ") else plain
    val edge = (LocalConfiguration.current.screenWidthDp * HEADER_EDGE_FRACTION).dp
    val titleStyle = MaterialTheme.typography.titleMedium
    val onSurface = MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = HEADER_MIN_HEIGHT)
            .then(if (onClick != null) Modifier.clip(RoundedCornerShape(50)) else Modifier)
            .then(if (framed && onClick != null) Modifier.background(MaterialTheme.colorScheme.surfaceContainer) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = edge, vertical = 6.dp)
            .semantics(mergeDescendants = true) {
                heading()
                if (description != null) contentDescription = description
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // What the page shows of the stop ("Departures"), small above its name.
        if (kicker != null) {
            Text(
                text = kicker,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
