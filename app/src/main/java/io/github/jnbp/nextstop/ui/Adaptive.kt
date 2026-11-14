package io.github.jnbp.nextstop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.OffsetDateTime
import kotlin.math.abs
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.plainStopName
import io.github.jnbp.nextstop.ui.theme.AppColors

/**
 * How much room a page has: a small screen (the cover screen of a flip phone), a phone, or a
 * wide screen (an unfolded foldable, a tablet, a phone held sideways).
 */
enum class ScreenRoom { Small, Phone, Wide }

/** From this width on, pages put their parts side by side. */
val WIDE_FROM: Dp = 600.dp

/** The widest a panel at the side of the map gets on a wide screen. */
val SIDE_PANEL_MAX: Dp = 420.dp

// Below this width or height a page shows only what matters most.
private val SMALL_WIDTH = 320.dp
private val SMALL_HEIGHT = 440.dp

// A wide page lower than this is a phone held sideways (see isShortWide).
private val SHORT_HEIGHT = 480.dp

/** A panel at the side of the map on a phone held sideways is at most this wide, so the map keeps room. */
val SHORT_SIDE_PANEL_MAX: Dp = 380.dp

/** Buttons on a phone held sideways are this high (still a comfortable touch target). */
val SHORT_BUTTON_HEIGHT: Dp = 48.dp

/**
 * Whether a page of [width] by [height] is wide but low: a phone held sideways. Such a page
 * puts its parts side by side as any wide one, but keeps its buttons near the top and its bars
 * low, as there is little height for anything else.
 */
fun isShortWide(width: Dp, height: Dp): Boolean = width >= WIDE_FROM && height < SHORT_HEIGHT

/**
 * True for a page that is wide but low, as on a phone held sideways: its parts stand side by
 * side (controls in a panel at the side, chart or map next to it) instead of on top of each other.
 * The same test as [isShortWide].
 */
fun sideBySide(width: Dp, height: Dp): Boolean = isShortWide(width, height)

/** The room of a page of [width] by [height], as BoxWithConstraints measures it at the page's root. */
fun screenRoom(width: Dp, height: Dp): ScreenRoom = when {
    width >= WIDE_FROM -> ScreenRoom.Wide
    width < SMALL_WIDTH || height < SMALL_HEIGHT -> ScreenRoom.Small
    else -> ScreenRoom.Phone
}

/**
 * The next step of a followed trip over the whole of a small screen: the minutes until then,
 * what to do and where to board. A swipe to either side (or a tap on the note at its foot)
 * calls [onShowMap].
 */
@Composable
fun CompactTripCard(
    journey: Journey,
    now: OffsetDateTime,
    onBack: () -> Unit,
    onShowMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val step = tripStep(journey, now)
    // Before boarding a train that leads to a change: where to board it; on board: the change.
    val boarding = step.leg?.takeIf { step.kind == StepKind.Board }
    val boardingNext = if (boarding != null) rideAfter(journey, boarding) else null
    val gettingOff = step.leg?.takeIf { step.kind == StepKind.GetOff }
    val gettingOffNext = if (gettingOff != null) rideAfter(journey, gettingOff) else null
    val gettingOffWalk = if (gettingOff != null) walkBetween(journey, gettingOff) else 0L
    // How far the finger went sideways, in pixels; past the threshold the map shows.
    var swiped by remember { mutableFloatStateOf(0f) }
    val swipe = rememberDraggableState { delta -> swiped += delta }
    val threshold = with(LocalDensity.current) { SWIPE_TO_MAP.toPx() }
    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(28.dp))
            .background(AppColors.Blue)
            .draggable(
                state = swipe,
                orientation = Orientation.Horizontal,
                onDragStopped = {
                    if (abs(swiped) > threshold) onShowMap()
                    swiped = 0f
                },
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RoundButton(
                glyph = Glyph.Back,
                description = stringResource(R.string.back),
                onClick = onBack,
                size = 40.dp,
                solid = true,
            )
            Text(
                text = stringResource(R.string.trip_to, journey.legs.last().to.plainStopName()),
                color = AppColors.OnBlue,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        if (step.kind != StepKind.Arrived) {
            val minutes = minutesBetween(now, step.time).coerceAtLeast(0)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    // Far ahead, the clock time says more than a large number of minutes.
                    text = if (minutes < 100) minutes.toString() else step.time.clock(),
                    color = AppColors.Lime,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 56.sp,
                    lineHeight = 58.sp,
                    maxLines = 1,
                )
                if (minutes < 100) {
                    Text(
                        text = stringResource(R.string.minutes_unit),
                        color = AppColors.Lime,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
                    )
                }
            }
        }
        Text(
            text = stepLabel(step),
            color = AppColors.OnBlue,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 22.sp,
            lineHeight = 26.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (boarding != null && boardingNext != null) {
            CarriageHint(arriving = boarding, next = boardingNext, onBlue = true)
        }
        if (gettingOff != null && gettingOffNext != null) {
            ChangeHint(arriving = gettingOff, next = gettingOffNext, walkMinutes = gettingOffWalk, onBlue = true)
        }
        Spacer(modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onShowMap)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            GlyphIcon(Glyph.Map, color = AppColors.OnBlue, size = 18.dp)
            Text(
                text = stringResource(R.string.compact_swipe_map),
                color = AppColors.OnBlue,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

// A sideways swipe this long on the card of the next step shows the map.
private val SWIPE_TO_MAP = 56.dp
