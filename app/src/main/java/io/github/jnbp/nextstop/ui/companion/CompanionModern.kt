package io.github.jnbp.nextstop.ui.companion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import io.github.jnbp.nextstop.ui.BatterySaver
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.data.CompanionPosition
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.Leg
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.TripCourse
import io.github.jnbp.nextstop.data.Warning
import io.github.jnbp.nextstop.data.wakeTargets
import io.github.jnbp.nextstop.ui.ConnectionRescue
import io.github.jnbp.nextstop.ui.RescueOffer
import io.github.jnbp.nextstop.ui.AFTER_ARRIVAL_MINUTES
import io.github.jnbp.nextstop.ui.Alternatives
import io.github.jnbp.nextstop.ui.ArrivedCard
import io.github.jnbp.nextstop.ui.Celebrations
import io.github.jnbp.nextstop.ui.CompanionExtrasStrip
import io.github.jnbp.nextstop.ui.ConfettiBurst
import io.github.jnbp.nextstop.ui.FasterCard
import io.github.jnbp.nextstop.ui.FasterOffer
import io.github.jnbp.nextstop.ui.LocalBackdrop
import io.github.jnbp.nextstop.ui.LocalPageInFront
import io.github.jnbp.nextstop.ui.QuietNotices
import io.github.jnbp.nextstop.ui.ScreenRoom
import io.github.jnbp.nextstop.ui.WakeAlarm
import io.github.jnbp.nextstop.ui.WarningSummary
import io.github.jnbp.nextstop.ui.arrivalKey
import io.github.jnbp.nextstop.ui.lineColor
import io.github.jnbp.nextstop.ui.rememberAnimationsOn
import io.github.jnbp.nextstop.ui.screenRoom
import io.github.jnbp.nextstop.ui.theme.AppColors
import io.github.jnbp.nextstop.ui.WalkDetour
import io.github.jnbp.nextstop.ui.minutesBetween
import java.time.OffsetDateTime

/**
 * True where the new companion views take the place of the classic one: chosen in the
 * settings, on a phone held upright. A wide screen, a phone held sideways and the cover screen
 * of a flip phone keep the classic view, which has its own layouts for them. The root of the
 * app asks the same, so the tab bar and the map follow the view that is really shown.
 */
@Composable
fun companionModernHere(): Boolean {
    if (!CompanionLayout.modern) return false
    val config = LocalConfiguration.current
    return screenRoom(config.screenWidthDp.dp, config.screenHeightDp.dp) == ScreenRoom.Phone
}

/** True where the calm companion is shown now: the map behind it may rest (see the root of the app). */
@Composable
fun companionCalmHere(): Boolean = companionModernHere() && !CompanionLayout.showsMap

// The room the floating bar of actions takes at the foot, and its gap.
private val ACTION_BAR_ROOM = 84.dp

/**
 * The new companion, calm (no map) or with the map, and the move between the two: the plain
 * ground fades away and lets the map come through, the blue box shrinks into its compact
 * place and the timeline slides into the sheet; back again the other way. With animations off
 * the change is at once. Bar on top and bar of actions stay where they are.
 *
 * [journey] has the plausible times already (see CompanionScreen); [onChangeTrip] opens the
 * sheet of "Change trip", [onShare] the share sheet. Everything shown comes from
 * [companionModel], the one binding to the data of the trip.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun ModernCompanion(
    journey: Journey,
    now: OffsetDateTime,
    courses: Map<String, TripCourse>,
    alternatives: Alternatives?,
    onMinimize: () -> Unit,
    onEnd: () -> Unit,
    onMissed: (Stop) -> Unit,
    onChangeTrip: () -> Unit,
    onShare: () -> Unit,
    onFocusWalk: (Leg) -> Unit,
    onSwitch: (Journey) -> Unit,
    onContinueTo: (() -> Unit)?,
    onRecentre: (() -> Unit)?,
    warnings: List<Warning>,
    quiet: List<Warning>,
    faster: FasterOffer?,
    onTakeFaster: (FasterOffer) -> Unit,
    onIgnoreFaster: (FasterOffer) -> Unit,
    notificationsOff: Boolean,
    onNotificationsNoteSeen: () -> Unit,
    walkDetour: WalkDetour? = null,
    ownTimes: ShortcutOwn? = null,
    ownId: String = journey.id,
    actions: io.github.jnbp.nextstop.ui.TripActions? = null,
) {
    val context = LocalContext.current
    // Read so a new position composes anew; fixFor checks that it is fresh and of this journey.
    val fixSeen by CompanionPosition.fix.collectAsState()
    val rescueShown by ConnectionRescue.suggestion.collectAsState()
    val fix = remember(fixSeen, now, journey) { CompanionPosition.fixFor(journey, now.toInstant().toEpochMilli()) }
    val model = remember(journey, now, courses, fix) { companionModel(journey, now, courses, fix) }
    // When the wake-up alarm of the ride rings, for its chip (#64).
    val forTrip by WakeAlarm.forTrip.collectAsState()
    val wakeLeadForTrip by WakeAlarm.leadForTrip.collectAsState()
    val prefs = remember(context) { AppPrefs(context) }
    val wakeMode = remember(context) { prefs.wakeAlarmMode }
    val wakeLead = remember(context) { prefs.wakeAlarmMinutes }
    val wakeTimes = remember(journey, forTrip, wakeMode, wakeLead, wakeLeadForTrip) {
        wakeTargets(journey, wakeMode, wakeLead, forTrip, lead = wakeLeadForTrip)
    }
    val rideIndex = model.ride?.legIndex
    val wakeAt = wakeTimes.firstOrNull { it.legIndex == rideIndex && it.at.isAfter(now) }?.at
    val wakeOn = forTrip ?: wakeTimes.isNotEmpty()
    var wakeOpen by remember { mutableStateOf(false) }
    if (wakeOpen) WakeSheet(journey = journey, now = now, rideIndex = rideIndex, onDismiss = { wakeOpen = false })
    // The chip of the alarm: while it rings on this ride, or off on a ride long enough to want it.
    val wakeRide = model.ride?.leg
    val wakeChip = if (wakeAt != null || (!wakeOn && wakeRide != null && minutesBetween(wakeRide.departure, wakeRide.arrival) >= WAKE_CHIP_RIDE_MINUTES)) {
        WakeChipState(
            at = wakeAt,
            on = wakeOn,
            onOpen = { wakeOpen = true },
            onToggle = { WakeAlarm.setForTrip(context, !wakeOn) },
        )
    } else {
        null
    }

    // A walk shown on the map shows the map for a moment, without changing the remembered choice.
    DisposableEffect(Unit) { onDispose { CompanionLayout.peekMap(false) } }
    // No tab bar while this page is in front (the root of the app reads it).
    val inFront = LocalPageInFront.current
    DisposableEffect(inFront) {
        CompanionLayout.onScreen = inFront
        onDispose { CompanionLayout.onScreen = false }
    }
    // A walk shown on the big map; from the calm view only where it has no small map to open in
    // place (#61), and the root of the app ends that peek again on "Back" (see closeWalk). The
    // battery saver makes no big map at all: there the walk stays where it is.
    val showWalk: (Leg) -> Unit = { leg ->
        if (!CompanionLayout.showsMap) {
            if (!BatterySaver.active) {
                CompanionLayout.peekMap(true)
                onFocusWalk(leg)
            }
        } else {
            onFocusWalk(leg)
        }
    }
    val showsMap = CompanionLayout.showsMap
    val animate = rememberAnimationsOn()
    val ground by animateFloatAsState(
        if (showsMap) 0f else 1f,
        if (animate) tween(MAP_FADE_MILLIS) else snap(),
        label = "calmGround",
    )
    // "Missed it" only while it matters: before boarding, from shortly before the departure on.
    val missedFrom = model.ride?.takeIf { missedRelevant(it.onBoard, minutesBetween(now, it.leg.departure)) }?.leg?.fromPlace
    // The plain ground also fills the row of the status bar above the page: nothing shines through.
    val statusTop = WindowInsets.statusBars.getTop(LocalDensity.current).toFloat()
    val scroll = rememberScrollState()
    val backdrop = LocalBackdrop.current
    // No shortcut right after the traveller chose this connection, until anything changed (#75).
    val shortcutShown = ShortcutGuard.allows(
        ownId,
        ownTimes ?: shortcutOwn(journey),
        faster?.let { shortcutOfferArrival(it) },
        System.currentTimeMillis(),
        // The simulation of the developer options shows at once (#120: to try the preview).
        bypass = faster?.simulated == true,
    )
    // The preview of the offered shortcut (#120), from the card or the notification.
    val previewKey by io.github.jnbp.nextstop.ui.ShortcutPreview.requested.collectAsState()
    val previewed = faster?.takeIf { it.key == previewKey }
    if (previewed != null) {
        io.github.jnbp.nextstop.ui.ShortcutPreviewDialog(
            offer = previewed,
            now = now,
            onTake = {
                io.github.jnbp.nextstop.ui.ShortcutPreview.close()
                onTakeFaster(previewed)
            },
            onIgnore = {
                io.github.jnbp.nextstop.ui.ShortcutPreview.close()
                onIgnoreFaster(previewed)
            },
            onClose = { io.github.jnbp.nextstop.ui.ShortcutPreview.close() },
        )
    }
    Box(modifier = Modifier.fillMaxSize()) {
        // The plain ground of the calm view, over the map behind; it fades as the map comes in.
        val plain = AppColors.PlainGround
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = ground }
                .drawBehind {
                    drawRect(plain, topLeft = Offset(0f, -statusTop), size = Size(size.width, size.height + statusTop))
                },
        )
        // On the plain ground, glass has no picture of the map behind it to show.
        CompositionLocalProvider(LocalBackdrop provides if (showsMap) backdrop else null) {
            Column(modifier = Modifier.fillMaxSize()) {
                CompanionTopBar(
                    model = model,
                    onMap = showsMap,
                    onBack = onMinimize,
                    onToggleMap = if (CompanionLayout.mapToggleOffered) ({ CompanionLayout.setMap(context, !showsMap) }) else null,
                    // Nothing else on top (#116): the actions are all in the bar at the foot.
                    menu = null,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                    everyMinute = actions?.everyMinute ?: false,
                    onEveryMinute = actions?.onEveryMinute,
                )
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AnimatedContent(
                        targetState = showsMap,
                        transitionSpec = {
                            if (animate) {
                                fadeIn(tween(SWAP_IN_MILLIS, delayMillis = SWAP_IN_DELAY_MILLIS)) togetherWith
                                    fadeOut(tween(SWAP_OUT_MILLIS)) using SizeTransform(clip = false)
                            } else {
                                EnterTransition.None togetherWith ExitTransition.None
                            }
                        },
                        label = "companionLayout",
                    ) { withMap ->
                        val scope: AnimatedVisibilityScope = this
                        val hero: @Composable () -> Unit = {
                            if (model.arrived) {
                                ArrivedPart(journey = journey, onEnd = onEnd, onContinueTo = onContinueTo, onLongPress = onChangeTrip)
                            } else {
                                CompanionHero(
                                    model = model,
                                    now = now,
                                    wake = wakeChip,
                                    progress = !withMap,
                                    modifier = with(scope) { Modifier.animateEnterExit(enter = heroEnter(withMap, animate), exit = heroExit(animate)) },
                                )
                            }
                        }
                        val extras: @Composable () -> Unit = {
                            if (!model.arrived) CompanionExtrasStrip(journey = journey, now = now)
                            if (faster != null && shortcutShown) {
                                FasterCard(
                                    offer = faster,
                                    onTake = { onTakeFaster(faster) },
                                    onIgnore = { onIgnoreFaster(faster) },
                                    onPreview = { io.github.jnbp.nextstop.ui.ShortcutPreview.open(faster.key) },
                                )
                            }
                            // The chosen connection broke by a delay: the best one that works, one tap away (#98).
                            val rescue = rescueShown?.takeIf { it.journeyId == journey.id }
                            if (rescue != null) RescueOffer(rescue.option, onSwitch)
                            if (notificationsOff) NotificationsNote(onNotificationsNoteSeen)
                            WarningSummary(warnings)
                            QuietNotices(quiet)
                            // Lifts out of order on the way, for step-free travel (as in the detail).
                            io.github.jnbp.nextstop.ui.JourneyLiftWarning(journey)
                        }
                        val bodyModifier = with(scope) { Modifier.animateEnterExit(enter = bodyEnter(withMap, animate), exit = bodyExit(withMap, animate)) }
                        val bottomRoom = if (model.arrived) 12.dp else ACTION_BAR_ROOM
                        if (withMap) {
                            MapCompanion(
                                model = model,
                                alternatives = alternatives,
                                onSwitch = onSwitch,
                                onFocusWalk = showWalk,
                                onRecentre = onRecentre,
                                hero = hero,
                                extras = extras,
                                sheetModifier = bodyModifier,
                                bottomRoom = bottomRoom,
                                now = now,
                                walkDetour = walkDetour,
                            )
                        } else {
                            CalmCompanion(
                                model = model,
                                alternatives = alternatives,
                                onSwitch = onSwitch,
                                onFocusWalk = showWalk,
                                hero = hero,
                                extras = extras,
                                scroll = scroll,
                                bodyModifier = bodyModifier,
                                bottomRoom = bottomRoom,
                                now = now,
                                walkDetour = walkDetour,
                            )
                        }
                    }
                    if (!model.arrived) {
                        // On the plain ground the timeline fades out under the bar, so nothing of it
                        // peeks out below the bar as a leftover (#61); over the map the map stays free.
                        val fade by animateFloatAsState(if (showsMap) 0f else 1f, if (animate) tween(MAP_FADE_MILLIS) else snap(), label = "barFade")
                        if (fade > 0f) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(BAR_FADE_HEIGHT)
                                    .graphicsLayer { alpha = fade }
                                    .background(Brush.verticalGradient(listOf(plain.copy(alpha = 0f), plain, plain), startY = 0f)),
                            )
                        }
                        // The change ahead, where "Anschluss verpasst" and "Anderen Anschluss wählen" lead.
                        val changeAhead = CompanionOffers.changeFor(journey, now)
                        val chooseConnection = changeAhead?.let { ahead -> { CompanionOffers.choose(journey, ahead.keepLegs) } }
                        CompanionBottomBar(
                            actions = CompanionBarActions(
                                missedFrom = missedFrom,
                                onMissed = onMissed,
                                onMissedChange = chooseConnection,
                                onReplan = actions?.onReplan,
                                onChangeDestination = actions?.onChangeDestination,
                                onOtherConnection = chooseConnection,
                                // Other ways from the change ahead: searched, and shown at that change (#164).
                                onOtherWays = changeAhead?.let { ahead ->
                                    {
                                        OtherWaysSearch.search(context, journey, ahead)
                                        CompanionOffers.choose(journey, ahead.keepLegs)
                                    }
                                },
                                onShare = onShare,
                                shortcutAlert = actions?.shortcutAlert ?: false,
                                onShortcutAlert = actions?.onShortcutAlert,
                                everyMinute = actions?.everyMinute ?: false,
                                onEveryMinute = actions?.onEveryMinute,
                                onWake = { wakeOpen = true },
                                onEnd = onEnd,
                                arrived = model.arrived,
                                rideProduct = model.ride?.leg?.product,
                            ),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

// The ground under the bar of actions in the calm view: it fades the timeline out below the bar.
private val BAR_FADE_HEIGHT = 56.dp

// A ride this long offers the chip of the wake-up alarm also while it is off.
private const val WAKE_CHIP_RIDE_MINUTES = 20L

// The plain ground fades this long as the map comes in or goes.
private const val MAP_FADE_MILLIS = 350
private const val SWAP_IN_MILLIS = 260
private const val SWAP_IN_DELAY_MILLIS = 60
private const val SWAP_OUT_MILLIS = 160

/** The blue box: from a larger size into its compact place over the map, from a smaller one into the calm column. */
private fun heroEnter(withMap: Boolean, animate: Boolean): EnterTransition =
    if (!animate) {
        EnterTransition.None
    } else {
        fadeIn(tween(SWAP_IN_MILLIS)) + scaleIn(
            spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
            initialScale = if (withMap) 1.06f else 0.94f,
            transformOrigin = TransformOrigin(0.5f, 0f),
        )
    }

private fun heroExit(animate: Boolean): ExitTransition =
    if (animate) fadeOut(tween(SWAP_OUT_MILLIS)) + scaleOut(targetScale = 0.97f, transformOrigin = TransformOrigin(0.5f, 0f)) else ExitTransition.None

/** The timeline: up into the sheet over the map, or down into the calm column. */
private fun bodyEnter(withMap: Boolean, animate: Boolean): EnterTransition =
    if (!animate) {
        EnterTransition.None
    } else {
        fadeIn(tween(SWAP_IN_MILLIS)) + slideInVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { height ->
            if (withMap) height / 2 else -height / 8
        }
    }

private fun bodyExit(withMap: Boolean, animate: Boolean): ExitTransition =
    if (!animate) {
        ExitTransition.None
    } else {
        fadeOut(tween(SWAP_OUT_MILLIS)) + slideOutVertically(tween(SWAP_OUT_MILLIS)) { height -> if (withMap) height / 3 else height / 8 }
    }

/** The arrival in the new views: the card of the classic view (the check, "End trip", "Go on to …"), with its confetti once. */
@Composable
private fun ArrivedPart(journey: Journey, onEnd: () -> Unit, onContinueTo: (() -> Unit)?, onLongPress: () -> Unit) {
    val arrivalId = arrivalKey(journey)
    val celebrate = remember(arrivalId) { Celebrations.claim(arrivalId) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(celebrate) { if (celebrate) haptics.performHapticFeedback(HapticFeedbackType.Confirm) }
    val colors = remember(journey.id) { (journey.rides.map { lineColor(it) } + listOf(CONFETTI_LIME, CONFETTI_BLUE)).distinct() }
    val density = LocalDensity.current
    Box {
        ArrivedCard(
            journey = journey,
            endsAt = journey.arrival.plusMinutes(AFTER_ARRIVAL_MINUTES),
            onEnd = onEnd,
            onContinueTo = onContinueTo,
            onLongPress = onLongPress,
        )
        ConfettiBurst(
            colors = colors,
            play = celebrate,
            origin = { with(density) { Offset(44.dp.toPx(), 44.dp.toPx()) } },
            modifier = Modifier.matchParentSize(),
        )
    }
}

private val CONFETTI_LIME = Color(0xFFC8F16B)
private val CONFETTI_BLUE = Color(0xFF1B4FD8)

/** Said once: notifications of the trip are off; a tap puts the note away. */
@Composable
private fun NotificationsNote(onSeen: () -> Unit) {
    Text(
        text = stringResource(R.string.trip_notifications_off),
        color = AppColors.Ink,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AppColors.WarningContainer)
            .clickable(onClick = onSeen)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}
