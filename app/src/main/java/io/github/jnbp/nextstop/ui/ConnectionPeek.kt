package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.boardingTime
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.alertingWarnings
import io.github.jnbp.nextstop.data.plainStopName
import io.github.jnbp.nextstop.ui.theme.AppColors
import java.time.OffsetDateTime

/**
 * The connection held down in the chart or the list of connections, shown in a small card over
 * it while the finger stays (see [closePeekOnRelease]): what a glance needs, without opening it.
 * A long press by a screen reader opens it too; a tap anywhere else closes it then.
 */
@Stable
internal class PeekState {
    /** The connection in the card; kept after it closes, so the card leaves with it. */
    var journey by mutableStateOf<Journey?>(null)
        private set
    var open by mutableStateOf(false)
        private set

    /** Where the held connection lies in the window. */
    var anchor by mutableStateOf(IntRect.Zero)
        private set

    /** Counts the times the card opened: it keeps its place for as long as it stays open (#86). */
    var opening by mutableStateOf(0)
        private set

    fun show(journey: Journey, bounds: Rect?) {
        anchor = bounds?.roundToIntRect() ?: IntRect.Zero
        this.journey = journey
        if (!open) opening++
        open = true
    }

    fun hide() {
        if (open) open = false
    }

    /** The connections a held finger can scrub to, by id (see PeekScrub.kt); not state. */
    internal val targets = HashMap<String, PeekTarget>()

    /** Where the chart or list that scrubs lies (see peekScrub); not state. */
    internal var area: androidx.compose.ui.layout.LayoutCoordinates? = null
}

/**
 * Closes [peek] when the finger that held a connection lets go (or the gesture is taken over,
 * as by a scroll). Only watches: the press goes on to the clickable of the element as before.
 */
internal fun Modifier.closePeekOnRelease(peek: PeekState): Modifier = pointerInput(peek) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Final)
            var pressed = false
            for (i in event.changes.indices) if (event.changes[i].pressed) pressed = true
            if (!pressed) break
        }
        peek.hide()
    }
}

/**
 * Puts the card above the held connection where it fits, else below it, inside the window. Once
 * placed it stays there while it is open, also when the finger scrubs to another connection or
 * the card changes its size (#86): it never jumps, and it may cover the chart.
 */
private class PeekPosition(private val anchor: IntRect, private val margin: Int) : PopupPositionProvider {
    private var placed: IntOffset? = null

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        // Without bounds of the held element (none known yet), over the element the popup belongs to.
        val maxY = (windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin)
        placed?.let { kept ->
            val keptMaxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)
            return IntOffset(kept.x.coerceIn(margin, keptMaxX), kept.y.coerceIn(margin, maxY))
        }
        val target = if (anchor == IntRect.Zero) anchorBounds else anchor
        val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)
        val x = (target.center.x - popupContentSize.width / 2).coerceIn(margin, maxX)
        val above = target.top - margin - popupContentSize.height
        val below = target.bottom + margin
        val y = when {
            above >= margin -> above
            below + popupContentSize.height <= windowSize.height - margin -> below
            // Neither fits whole: over the middle of the element, inside the window.
            else -> (target.center.y - popupContentSize.height / 2).coerceIn(margin, maxY)
        }
        // Kept once the card has its size (a first pass may measure it empty).
        if (popupContentSize.height > 0) placed = IntOffset(x, y)
        return IntOffset(x, y)
    }
}

/** The peek card of [peek] while it is open, and while it leaves. Composes nothing when closed. */
@Composable
internal fun ConnectionPeek(peek: PeekState, now: OffsetDateTime) {
    val journey = peek.journey ?: return
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = peek.open
    if (!visible.currentState && !visible.targetState) return
    val animate = rememberAnimationsOn()
    // The popup holds the room of the card's shadow around it (see PeekFrame): the gap to the held
    // connection and the edges is what is left of PEEK_MARGIN.
    val margin = with(LocalDensity.current) { (PEEK_MARGIN - PEEK_SHADOW_ROOM).coerceAtLeast(0.dp).roundToPx() }
    // One position per opening: scrubbing to another connection does not move the card (#86).
    val anchor = peek.anchor
    val position = remember(peek.opening, margin) { PeekPosition(anchor, margin) }
    Popup(
        popupPositionProvider = position,
        onDismissRequest = { peek.hide() },
        properties = PopupProperties(focusable = false, dismissOnClickOutside = true),
    ) {
        AnimatedVisibility(
            visibleState = visible,
            enter = if (animate) scaleIn(Motion.pop(), initialScale = 0.82f) + fadeIn(Motion.fadeIn()) else EnterTransition.None,
            exit = if (animate) scaleOut(Motion.quick(), targetScale = 0.92f) + fadeOut(Motion.fadeOut()) else ExitTransition.None,
        ) {
            PeekFrame {
                // Scrubbed to another connection: the card's content cross-fades and the one card
                // takes its size softly (one shadow, not two fading cards over each other, #125).
                if (animate) {
                    AnimatedContent(
                        targetState = journey,
                        contentKey = { it.id },
                        transitionSpec = {
                            fadeIn(tween(160, delayMillis = 40)) togetherWith fadeOut(tween(100)) using SizeTransform(clip = false)
                        },
                        label = "peekSwap",
                    ) { shown ->
                        PeekCard(shown, now)
                    }
                } else {
                    PeekCard(journey, now)
                }
            }
        }
    }
}

private val PEEK_MARGIN = 12.dp

// Room around the card inside its popup for its shadow: the popup's window cuts off whatever
// lies outside it, and a shadow of 12 dp in 4 dp of room was cut to a hard grey edge (#125).
private val PEEK_SHADOW_ROOM = 12.dp
private val PEEK_ELEVATION = 8.dp

/**
 * The one surface of the peek card: an opaque card with its shadow, drawn as one picture
 * (offscreen), so while it fades in or out the shadow never shows through the card as a grey
 * shading (#125: a layer's alpha is otherwise applied to the shadow and the card one by one).
 */
@Composable
private fun PeekFrame(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .padding(PEEK_SHADOW_ROOM)
            .widthIn(min = 200.dp, max = 320.dp)
            .shadow(PEEK_ELEVATION, shape, clip = false)
            .clip(shape)
            .background(AppColors.Card)
            .border(1.dp, AppColors.GlassBorder, shape),
    ) {
        content()
    }
}

@Composable
private fun PeekCard(journey: Journey, now: OffsetDateTime) {
    val stepFree = rememberStepFree()
    val rows = remember(journey) { peekRows(journey) }
    val warnings = remember(journey, stepFree) { alertingWarnings(journey.warnings, stepFree) }
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = journeySpanText(journey, R.string.time_range, AppColors.Ink),
                color = AppColors.Ink,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false),
            )
            MinutesPill(minutes = minutesBetween(now, journey.departure), live = false, cancelled = journey.cancelled, at = journey.departure)
        }
        Text(
            text = stringResource(R.string.peek_from_to, journey.legs.first().from, journey.legs.last().to),
            color = AppColors.Muted,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.duration_and_transfers, durationLabel(journey.durationMinutes), journey.transfers),
            color = AppColors.Muted,
            fontSize = 13.sp,
            maxLines = 1,
        )
        // The rides one below the other, with the changes and walks between them (#4).
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (row in rows) PeekListRow(row)
        }
        if (warnings.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WarningMark()
                Text(
                    text = warnings.first().summary,
                    color = AppColors.Ink,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (warnings.size > 1) {
                    Text(
                        text = stringResource(R.string.peek_more_warnings, warnings.size - 1),
                        color = AppColors.Muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/**
 * One row of the list of a peek card: a ride as its badge, when it leaves and where to, and
 * its minutes; a change or walk as a quieter line under the badges, indented.
 */
@Composable
private fun PeekListRow(row: PeekRow) {
    when (row) {
        is PeekRow.Ride -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LineBadge(row.leg, minWidth = 40.dp)
            Text(
                // The departure as the delay setting says (#227).
                text = delayedTimeText(R.string.peek_from_to, boardingTime(row.leg), AppColors.Ink, listOf(null, row.leg.to.plainStopName())),
                color = AppColors.Ink,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.r3_peek_minutes, row.minutes.toInt()),
                color = AppColors.Muted,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
        is PeekRow.Change -> Row(
            modifier = Modifier.padding(start = PEEK_ROW_INDENT),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (row.walkMinutes > 0) WalkMark(minutes = row.walkMinutes, unit = true, cycling = row.cycling)
            Text(
                text = stringResource(R.string.r3_peek_change, row.minutes.toInt()),
                color = AppColors.Muted,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
        is PeekRow.Walk -> Row(modifier = Modifier.padding(start = PEEK_ROW_INDENT), verticalAlignment = Alignment.CenterVertically) {
            WalkMark(minutes = row.walkMinutes, unit = true, cycling = row.cycling)
        }
    }
}

// Changes and walks stand in a little, under the badges of the rides.
private val PEEK_ROW_INDENT = 10.dp
