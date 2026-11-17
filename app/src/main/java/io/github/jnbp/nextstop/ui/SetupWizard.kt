package io.github.jnbp.nextstop.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import io.github.jnbp.nextstop.ui.theme.MapStyleChoice
import io.github.jnbp.nextstop.ui.theme.MapVehiclesChoice
import io.github.jnbp.nextstop.data.LineColors
import io.github.jnbp.nextstop.data.Tightness
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.time.OffsetDateTime
import kotlin.math.absoluteValue
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.Accessibility
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.data.TransferComfort
import io.github.jnbp.nextstop.ui.theme.AppColors
import io.github.jnbp.nextstop.ui.theme.Appearance
import io.github.jnbp.nextstop.ui.theme.ColorChoice
import io.github.jnbp.nextstop.ui.theme.DarkChoice
import io.github.jnbp.nextstop.ui.theme.NotificationLook
import io.github.jnbp.nextstop.wear.WatchState
import io.github.jnbp.nextstop.wear.WatchStatus

/**
 * Whether the setup is on screen instead of the app. It opens by itself until it was finished
 * or skipped once, and again from the settings ("Restart setup").
 */
object SetupState {
    var open by mutableStateOf(false)
}

/**
 * The pages of the setup, in order: the choices that change how the app works first, the
 * cosmetic ones later (the colours next to the name for the live link, both about the look),
 * permissions and the watch at the end. Nothing depends on the order but the pager itself.
 */
private enum class SetupStep {
    Welcome, Connections, Delays, Transfers, StepFree, ConnectionChoice, Start, Vehicles, Notifications,
    ShortcutAlert, DisruptionCheck, EveryMinute, Colors, LiveName, Permissions, Watch, Done,
}

/**
 * The setup on first launch: one page per topic on glass cards over a slowly drifting ground,
 * swiped or stepped through, every page skippable. Every choice is stored at once, so the
 * app shows it as soon as the setup closes; [onDone] closes it after "setupDone" was stored.
 */
@Composable
fun SetupWizard(onDone: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    // The page about the watch only when a Wear OS watch is connected to this phone. The
    // status is polled while the wizard is open: once the page is there it stays (a watch
    // that drops out for a moment does not take it away), and it only comes in while the user
    // is still before its place, so no page shifts under the user's finger.
    val watch = rememberWatchStatus()
    var showWatch by rememberSaveable { mutableStateOf(false) }
    val steps = remember(showWatch) { SetupStep.entries.filter { it != SetupStep.Watch || showWatch } }
    val pager = rememberPagerState(pageCount = { steps.size })
    val watchConnected = watch != null && watch.state != WatchState.None
    LaunchedEffect(watchConnected) {
        // The watch page goes where "Done" is now.
        if (watchConnected && !showWatch && pager.currentPage < steps.indexOf(SetupStep.Done)) showWatch = true
    }
    LaunchedEffect(pager.currentPage) {
        // Once the page for the name was seen, the first live link does not ask again.
        if (steps.getOrNull(pager.currentPage) == SetupStep.LiveName) prefs.liveNameAsked = true
    }
    val scope = rememberCoroutineScope()

    fun finish() {
        prefs.setupDone = true
        onDone()
    }

    // Back to the step before is predictive: the pager follows the gesture, the step before
    // comes in from the side as far as the finger went; let go it snaps there, called off it
    // springs back. [backFrom] is the step the gesture started on (-1: none), [backMoved] how
    // far it has scrolled the pager so far, in pixels.
    val backFrom = remember { intArrayOf(-1) }
    val backMoved = remember { floatArrayOf(0f) }
    PredictiveNestedBackHandler(
        enabled = pager.currentPage > 0,
        onSeek = { progress ->
            if (backFrom[0] < 0) {
                backFrom[0] = pager.currentPage
                backMoved[0] = 0f
            }
            val pageWidth = (pager.layoutInfo.pageSize + pager.layoutInfo.pageSpacing).toFloat()
            val wanted = -progress * pageWidth
            pager.scroll { backMoved[0] += scrollBy(wanted - backMoved[0]) }
        },
        onCancel = {
            val from = backFrom[0]
            backFrom[0] = -1
            if (from >= 0) scope.launch { pager.animateScrollToPage(from) }
        },
    ) {
        val from = backFrom[0].takeIf { it >= 0 } ?: pager.currentPage
        backFrom[0] = -1
        scope.launch { pager.animateScrollToPage((from - 1).coerceAtLeast(0)) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.PlainGround)
            .driftingGround(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
        ) {
            // On top: how far the setup has come, filling with the swipe, and "Skip" on every
            // step; skipped, the rest keeps its defaults and the setup does not come back by itself.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
                    .height(40.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SetupProgress(
                    progress = { ((pager.currentPage + pager.currentPageOffsetFraction + 1f) / steps.size).coerceIn(0f, 1f) },
                    description = stringResource(R.string.setup_progress, pager.currentPage + 1, steps.size),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.setup_skip),
                    color = AppColors.Blue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { finish() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            HorizontalPager(
                state = pager,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .pageMotion(pager, page)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // Small phones get the tighter layout; only the settled page peeks at
                    // what lies below it.
                    val info = SetupPageInfo(compact = maxHeight < SETUP_COMPACT_HEIGHT, active = pager.settledPage == page)
                    CompositionLocalProvider(LocalSetupPage provides info) {
                        SetupPageContent(steps[page], prefs, watch)
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    // Called on the row: inside a Box the RowScope overload is only reachable explicitly.
                    this@Row.AnimatedVisibility(visible = pager.currentPage > 0, enter = fadeIn(), exit = fadeOut()) {
                        SecondaryButton(
                            text = stringResource(R.string.back),
                            onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                            minHeight = 48.dp,
                            ground = AppColors.Chip,
                        )
                    }
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    val last = pager.currentPage == steps.lastIndex
                    PrimaryButton(
                        text = stringResource(if (last) R.string.setup_finish else R.string.setup_next),
                        onClick = {
                            if (last) finish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                        },
                        minHeight = 48.dp,
                    )
                }
            }
        }
    }
}

/** One page of the setup, by its step. */
@Composable
private fun SetupPageContent(step: SetupStep, prefs: AppPrefs, watch: WatchStatus?) {
    when (step) {
        SetupStep.Welcome -> WelcomePage()
        SetupStep.Connections -> ConnectionsPage(prefs)
        SetupStep.Delays -> DelaysPage(prefs)
        SetupStep.Transfers -> TransfersPage(prefs)
        SetupStep.StepFree -> StepFreePage(prefs)
        SetupStep.ConnectionChoice -> ConnectionChoicePage(prefs)
        SetupStep.Start -> StartPage(prefs)
        SetupStep.Vehicles -> VehiclesPage()
        SetupStep.Notifications -> NotificationsPage(prefs)
        SetupStep.ShortcutAlert -> ShortcutAlertPage()
        SetupStep.DisruptionCheck -> DisruptionCheckPage()
        SetupStep.EveryMinute -> EveryMinutePage()
        SetupStep.Colors -> ColorsPage()
        SetupStep.LiveName -> LiveNamePage(prefs)
        SetupStep.Permissions -> PermissionsPage()
        SetupStep.Watch -> WatchPage(watch)
        SetupStep.Done -> DonePage()
    }
}

/**
 * What a page knows about its place: [compact] on a short screen (a small phone), where the
 * sketches and paddings shrink so the main choice of every step fits without scrolling;
 * [active] while it is the settled page of the pager.
 */
private class SetupPageInfo(val compact: Boolean, val active: Boolean)

private val LocalSetupPage = compositionLocalOf { SetupPageInfo(compact = false, active = true) }

// Below this height of a page the setup uses its compact layout (a 360 x 640 dp phone leaves
// about 440 dp between the progress bar and the buttons).
private val SETUP_COMPACT_HEIGHT = 560.dp

// How far a page that holds more peeks down once it settled, and how far "more" scrolls.
private val PEEK_DISTANCE = 48.dp
private val MORE_STEP = 240.dp

/**
 * A column that scrolls when it is taller than its room, and then says so: its bottom edge
 * fades softly, a small "more ↓" sits on the fade (tapped, it scrolls on), and once the page
 * has settled it peeks down a little and back, unless animations are off.
 */
@Composable
private fun ScrollWithHint(
    modifier: Modifier = Modifier,
    inner: Modifier = Modifier,
    spacing: Dp = 14.dp,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    val page = LocalSetupPage.current
    val animate = rememberAnimationsOn()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    // Before the first layout maxValue is Int.MAX_VALUE: no hint until it is known.
    val measured by remember { derivedStateOf { scroll.maxValue in 1 until Int.MAX_VALUE } }
    val more by remember { derivedStateOf { measured && scroll.canScrollForward } }
    var peeked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(page.active, measured) {
        if (!page.active || !measured || peeked || !animate || scroll.value != 0) return@LaunchedEffect
        delay(700)
        peeked = true
        val peek = with(density) { PEEK_DISTANCE.roundToPx() }.coerceAtMost(scroll.maxValue)
        scroll.animateScrollTo(peek, tween(420))
        delay(260)
        // Back up, unless the user took over in the meantime.
        if (!scroll.isScrollInProgress && scroll.value == peek) scroll.animateScrollTo(0, tween(420))
    }
    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fadeEdges(scroll, horizontal = false, length = 40.dp)
                .verticalScroll(scroll)
                .then(inner),
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
        AnimatedVisibility(
            visible = more,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp),
        ) {
            Text(
                text = stringResource(R.string.setup_more_below),
                color = AppColors.OnBlueContainer,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(AppColors.BlueContainer)
                    .clickable(onClickLabel = stringResource(R.string.setup_more_below_action)) {
                        scope.launch {
                            val step = with(density) { MORE_STEP.roundToPx() }
                            val target = (scroll.value + step).coerceAtMost(scroll.maxValue)
                            if (animate) scroll.animateScrollTo(target) else scroll.scrollTo(target)
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}

/** Two soft blobs of the accent colour that drift slowly behind the cards. */
@Composable
private fun Modifier.driftingGround(): Modifier {
    val transition = rememberInfiniteTransition(label = "setupGround")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9_000), RepeatMode.Reverse),
        label = "setupDrift",
    )
    val accent = AppColors.Blue
    val second = AppColors.Lime
    return this.drawBehind {
        val first = Offset(size.width * (0.15f + 0.25f * drift), size.height * (0.18f + 0.08f * drift))
        drawCircle(
            Brush.radialGradient(listOf(accent.copy(alpha = 0.30f), Color.Transparent), center = first, radius = size.width * 0.8f),
            radius = size.width * 0.8f,
            center = first,
        )
        val other = Offset(size.width * (0.9f - 0.3f * drift), size.height * (0.82f - 0.1f * drift))
        drawCircle(
            Brush.radialGradient(listOf(second.copy(alpha = 0.22f), Color.Transparent), center = other, radius = size.width * 0.7f),
            radius = size.width * 0.7f,
            center = other,
        )
    }
}

/** A page fades and shrinks a little while it is swiped away, and grows back in. */
private fun Modifier.pageMotion(pager: PagerState, page: Int): Modifier = graphicsLayer {
    val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
    alpha = 1f - offset * 0.7f
    val scale = 1f - offset * 0.08f
    scaleX = scale
    scaleY = scale
}

/** How far the setup has come: a bar that fills in the blue of the app, following the swipe. */
@Composable
private fun SetupProgress(progress: () -> Float, description: String, modifier: Modifier = Modifier) {
    val track = AppColors.Muted.copy(alpha = 0.22f)
    val fill = AppColors.Blue
    Canvas(
        modifier = modifier
            .height(6.dp)
            .semantics { contentDescription = description },
    ) {
        val radius = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(track, cornerRadius = radius)
        val width = (size.width * progress()).coerceAtLeast(size.height)
        drawRoundRect(fill, size = Size(width, size.height), cornerRadius = radius)
    }
}

/**
 * One page: a glass card with a title, one or two short sentences, the details behind "Learn
 * more" ([more]) and the choices with their live previews; when it is too tall it scrolls
 * and shows that there is more (see ScrollWithHint). Tighter on a small phone.
 */
@Composable
private fun SetupCard(title: String, text: String, more: String? = null, content: @Composable ColumnScope.() -> Unit = {}) {
    val compact = LocalSetupPage.current.compact
    GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp)) {
        ScrollWithHint(
            inner = Modifier.padding(horizontal = 18.dp, vertical = if (compact) 14.dp else 20.dp),
            spacing = if (compact) 10.dp else 14.dp,
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.ExtraBold,
                fontSize = if (compact) 22.sp else 26.sp,
                lineHeight = if (compact) 26.sp else 30.sp,
            )
            if (text.isNotEmpty()) Text(text = text, color = AppColors.Muted, fontSize = 15.sp, lineHeight = 20.sp)
            if (more != null) MoreText(more)
            content()
        }
    }
}

/** "Learn more": a quiet link that unfolds the details of a step with a spring, and folds them again. */
@Composable
private fun MoreText(text: String, center: Boolean = false) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = stringResource(if (open) R.string.comfort_less else R.string.comfort_more),
            color = AppColors.Blue,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable { open = !open }
                .padding(vertical = 4.dp, horizontal = if (center) 8.dp else 0.dp),
        )
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(),
        ) {
            Text(
                text = text,
                color = AppColors.Muted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = if (center) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** A choice of a few options with its title and an explaining line. */
@Composable
private fun SetupChoice(
    title: String,
    text: String,
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    preview: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.Card)
            .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SetupText(title, text)
        preview?.invoke()
        SegmentedToggle(
            labels = labels,
            selected = selected,
            onSelect = onSelect,
            modifier = Modifier.fillMaxWidth(),
            minHeight = 40.dp,
            ground = AppColors.Chip,
            fill = true,
            twoLines = true,
        )
    }
}

/** A setting with a switch at its end. */
@Composable
private fun SetupSwitch(title: String, text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.Card)
            .clickable { onChange(!checked) }
            .defaultMinSize(minHeight = 56.dp)
            .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SetupText(title, text, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SetupText(title: String, text: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        if (text.isNotEmpty()) Text(text = text, color = AppColors.Muted, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

/** A permission: what it is for, and a button to ask for it, or a mark once it is given. */
@Composable
private fun PermissionRow(title: String, text: String, granted: Boolean, onAllow: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.Card)
            .defaultMinSize(minHeight = 56.dp)
            .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SetupText(title, text, modifier = Modifier.weight(1f))
        if (granted) {
            Text(
                text = stringResource(R.string.setup_granted),
                color = AppColors.CalmInk,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppColors.CalmContainer)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        } else {
            PrimaryButton(
                text = stringResource(R.string.setup_allow),
                onClick = onAllow,
                shape = RoundedCornerShape(16.dp),
                minHeight = 40.dp,
            )
        }
    }
}

/** The logo of the app, which springs in. */
@Composable
private fun SetupLogo(edge: Int = 112) {
    val grow = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow)) }
    Box(
        modifier = Modifier
            .size(edge.dp)
            .graphicsLayer {
                scaleX = grow.value
                scaleY = grow.value
                alpha = ((grow.value - 0.6f) / 0.4f).coerceIn(0f, 1f)
            }
            .clip(RoundedCornerShape((edge * 0.28f).dp)),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun WelcomePage() {
    // Scrolls once "Learn more" is open, or with a large font on a small phone.
    ScrollWithHint(spacing = 18.dp, horizontalAlignment = Alignment.CenterHorizontally) {
        SetupLogo(edge = if (LocalSetupPage.current.compact) 88 else 112)
        Text(
            text = stringResource(R.string.setup_welcome_title),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 30.sp,
            lineHeight = 34.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.setup_welcome_short),
            color = AppColors.Muted,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        MoreText(stringResource(R.string.setup_welcome_text), center = true)
    }
}

@Composable
private fun ColorsPage() {
    val context = LocalContext.current
    SetupCard(
        title = stringResource(R.string.setup_colors_title),
        text = stringResource(R.string.setup_colors_short),
        more = stringResource(R.string.setup_colors_text),
    ) {
        // On a small phone without its "Preview" line, so the dark choice still fits.
        PalettePreview(label = !LocalSetupPage.current.compact)
        SetupChoice(
            title = stringResource(R.string.color_title),
            text = "",
            labels = listOf(
                stringResource(R.string.color_next_stop),
                stringResource(R.string.setup_material_you),
                stringResource(R.string.color_custom),
            ),
            selected = ColorChoice.entries.indexOf(Appearance.colorChoice),
            onSelect = { index -> Appearance.changeColorChoice(context, ColorChoice.entries[index]) },
        )
        SettingReveal(visible = Appearance.colorChoice == ColorChoice.Custom) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(AppColors.Card)
                    .horizontalScroll(rememberScrollState())
                    .padding(10.dp),
            ) {
                AccentSwatches(selected = Appearance.accent, onSelect = { Appearance.changeAccent(context, it) })
            }
        }
        SetupChoice(
            title = stringResource(R.string.dark_title),
            text = "",
            labels = listOf(
                stringResource(R.string.dark_light),
                stringResource(R.string.dark_dark),
                stringResource(R.string.dark_system),
            ),
            selected = DarkChoice.entries.indexOf(Appearance.darkChoice),
            onSelect = { index -> Appearance.changeDarkChoice(context, DarkChoice.entries[index]) },
        )
    }
}

/**
 * How connections are shown: as the chart or as the list, and whether a connection opens in
 * a card below the overview or on a page of its own. The delays have their own step.
 */
@Composable
private fun ConnectionsPage(prefs: AppPrefs) {
    var list by remember { mutableStateOf(prefs.connectionsList) }
    var full by remember { mutableStateOf(prefs.connectionsFull) }
    val compact = LocalSetupPage.current.compact
    SetupCard(
        title = stringResource(R.string.setup_connections_title),
        text = stringResource(R.string.setup_connections_text),
        more = stringResource(R.string.setup_full_text),
    ) {
        // No line of its own: the text of the card already says what this is.
        VisualChoice(title = stringResource(R.string.view_title), text = "") {
            VisualOption(
                label = stringResource(R.string.view_chart),
                selected = !list,
                onClick = {
                    list = false
                    prefs.connectionsList = false
                },
                modifier = Modifier.weight(1f),
                previewHeight = if (compact) 64.dp else 96.dp,
            ) { chartSketch(0f, 0f, size.width, size.height) }
            VisualOption(
                label = stringResource(R.string.view_list),
                selected = list,
                onClick = {
                    list = true
                    prefs.connectionsList = true
                },
                modifier = Modifier.weight(1f),
                previewHeight = if (compact) 64.dp else 96.dp,
            ) { listSketch(0f, 0f, size.width, size.height) }
        }
        VisualChoice(title = stringResource(R.string.connections_full_title), text = "") {
            val splitReveal = rememberReveal(!full)
            VisualOption(
                label = stringResource(R.string.setup_full_off),
                selected = !full,
                onClick = {
                    full = false
                    prefs.connectionsFull = false
                },
                modifier = Modifier.weight(1f),
                previewHeight = if (compact) 84.dp else 120.dp,
            ) { splitSketch(splitReveal.value) }
            val singleReveal = rememberReveal(full)
            VisualOption(
                label = stringResource(R.string.setup_full_on),
                selected = full,
                onClick = {
                    full = true
                    prefs.connectionsFull = true
                },
                modifier = Modifier.weight(1f),
                previewHeight = if (compact) 84.dp else 120.dp,
            ) { singleSketch(singleReveal.value) }
        }
    }
}

/**
 * How a late departure looks: three options, each with its line of explanation and its live
 * sample (see DelayDisplay). No "Learn more": the options explain themselves, and without it
 * all three fit on a small phone.
 */
@Composable
private fun DelaysPage(prefs: AppPrefs) {
    var display by remember { mutableStateOf(prefs.delayDisplay) }
    SetupCard(
        title = stringResource(R.string.setup_delay_title),
        text = stringResource(R.string.setup_delay_short),
    ) {
        DelayDisplayPicker(
            selected = display,
            onSelect = { value ->
                display = value
                prefs.delayDisplay = value
            },
        )
    }
}

@Composable
private fun TransfersPage(prefs: AppPrefs) {
    var on by remember { mutableStateOf(prefs.transferChoice) }
    var level by remember { mutableStateOf(prefs.searchOptions.comfort) }
    SetupCard(
        title = stringResource(R.string.setup_transfers_title),
        text = stringResource(R.string.setup_transfers_short),
        more = stringResource(R.string.setup_transfers_text),
    ) {
        // Without the choice the app works with Normal; the sketch shows that gap then.
        TransferSketch(level = if (on) level else TransferComfort.Normal)
        // The choice right below its sketch, so it is on screen without scrolling.
        TransferChoiceRow(
            shape = RoundedCornerShape(20.dp),
            on = on,
            onChange = { value ->
                on = value
                prefs.transferChoice = value
            },
            level = level,
            onLevel = { value ->
                level = value
                prefs.searchOptions = prefs.searchOptions.copy(comfort = value)
            },
        )
        // Example changes that re-rate while the level moves: what is offered, what is left out.
        TransferExamples(level = if (on) level else TransferComfort.Normal)
        // What the levels mean as a pace; Sprint is a gamble, said plainly.
        TransferLevelLines()
    }
}

/**
 * Accessibility, compact: whether journeys must be step-free (any, partly, fully; as small
 * sketches of stairs and lifts) and "slower on the move" (every walk counts 1.3 times as
 * long). Both are defaults of every search, as in the settings.
 */
@Composable
private fun StepFreePage(prefs: AppPrefs) {
    var level by remember { mutableStateOf(prefs.searchOptions.accessibility) }
    var slower by remember { mutableStateOf(prefs.searchOptions.slower) }
    val compact = LocalSetupPage.current.compact
    SetupCard(
        title = stringResource(R.string.accessibility_title),
        text = stringResource(R.string.setup_access_short),
    ) {
        VisualChoice(title = stringResource(R.string.accessibility_text), text = "") {
            Accessibility.entries.forEach { option ->
                val picked = option == level
                val reveal = rememberReveal(picked)
                VisualOption(
                    label = stringResource(
                        when (option) {
                            Accessibility.None -> R.string.accessibility_none
                            Accessibility.Partial -> R.string.accessibility_partial
                            Accessibility.Complete -> R.string.accessibility_complete
                        },
                    ),
                    selected = picked,
                    onClick = {
                        level = option
                        prefs.searchOptions = prefs.searchOptions.copy(accessibility = option)
                    },
                    modifier = Modifier.weight(1f),
                    previewHeight = if (compact) 52.dp else 72.dp,
                ) { accessibilitySketch(option, reveal.value) }
            }
        }
        SlowerSketch(on = slower, height = if (compact) 32.dp else 40.dp)
        SetupSwitch(
            title = stringResource(R.string.slower_title),
            text = stringResource(R.string.slower_text),
            checked = slower,
            onChange = { value ->
                slower = value
                prefs.searchOptions = prefs.searchOptions.copy(slower = value)
            },
        )
    }
}

/**
 * Which connecting departure to take at a change, asked on the way: as the chips it shows, or
 * not at all. A step of its own, after the transfer times.
 */
@Composable
private fun ConnectionChoicePage(prefs: AppPrefs) {
    var choice by remember { mutableStateOf(prefs.connectionChoice) }
    val choiceHeight = if (LocalSetupPage.current.compact) 72.dp else 96.dp
    SetupCard(
        title = stringResource(R.string.connection_choice_title),
        text = stringResource(R.string.connection_choice_text),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            val choiceReveal = rememberReveal(choice)
            VisualOption(
                label = stringResource(R.string.setup_choice_on),
                selected = choice,
                onClick = {
                    choice = true
                    prefs.connectionChoice = true
                },
                modifier = Modifier.weight(1f),
                previewHeight = choiceHeight,
            ) { choiceSketch(choice = true, reveal = choiceReveal.value) }
            val plainReveal = rememberReveal(!choice)
            VisualOption(
                label = stringResource(R.string.setup_choice_off),
                selected = !choice,
                onClick = {
                    choice = false
                    prefs.connectionChoice = false
                },
                modifier = Modifier.weight(1f),
                previewHeight = choiceHeight,
            ) { choiceSketch(choice = false, reveal = plainReveal.value) }
        }
    }
}

@Composable
private fun StartPage(prefs: AppPrefs) {
    var calendar by remember { mutableStateOf(prefs.startCalendar) }
    SetupCard(title = stringResource(R.string.setup_start_title), text = stringResource(R.string.setup_start_text)) {
        SetupSwitch(
            title = stringResource(R.string.start_calendar_title),
            text = stringResource(R.string.start_calendar_text),
            checked = calendar,
            onChange = { value ->
                calendar = value
                prefs.startCalendar = value
            },
        )
        NextAppointmentPreview(on = calendar)
    }
}

/** The first name for shared live links: optional, an empty field just goes on. */
@Composable
private fun LiveNamePage(prefs: AppPrefs) {
    var name by remember { mutableStateOf(prefs.liveName) }
    SetupCard(
        title = stringResource(R.string.setup_live_name_title),
        text = stringResource(R.string.setup_live_name_short),
        more = stringResource(R.string.setup_live_name_text),
    ) {
        LiveNameField(
            value = name,
            onValue = { value ->
                name = value
                prefs.liveName = value
            },
        )
        LiveNamePreview(name)
    }
}

/**
 * How the map shows vehicles: a badge with the live dot from live data, one without from the
 * timetable; and the look of the map. On a small phone the choice of the look comes first
 * and the track below it is lower, so the choice fits without scrolling.
 */
@Composable
private fun VehiclesPage() {
    val context = LocalContext.current
    val compact = LocalSetupPage.current.compact
    SetupCard(
        title = stringResource(R.string.setup_vehicles_title),
        text = stringResource(R.string.setup_vehicles_short),
        more = stringResource(R.string.vehicle_hint_text),
    ) {
        // Which vehicles on the map: preselected only close up (the default), all, or none
        // (less powerful phones).
        val levels = MapVehiclesChoice.entries
        SetupChoice(
            title = stringResource(R.string.map_vehicles_title),
            text = stringResource(mapVehiclesTextRes(Appearance.mapVehicles)),
            labels = levels.map { stringResource(mapVehiclesLabelRes(it)) },
            selected = levels.indexOf(Appearance.mapVehicles),
            onSelect = { index -> Appearance.changeMapVehicles(context, levels[index]) },
        )
        // A little map with vehicles on it, live, as on the real map: zoomed out and close up as the choice shows them.
        if (compact) {
            MapStyleChoiceGrid(previewHeight = 48.dp)
            VehicleMapSketch(Appearance.mapVehicles, sketchHeight = 92.dp)
        } else {
            VehicleMapSketch(Appearance.mapVehicles)
            MapStyleChoiceGrid(previewHeight = 64.dp)
        }
    }
}

/** The look of the map: tapped, it is the map's at once. Two by two, so every name fits. */
@Composable
private fun MapStyleChoiceGrid(previewHeight: Dp) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SetupText(
            stringResource(R.string.map_style_title),
            stringResource(R.string.setup_map_style_short),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        val styles = listOf(
            MapStyleChoice.Light to R.string.map_style_light,
            MapStyleChoice.Colorful to R.string.map_style_colorful,
            MapStyleChoice.Dark to R.string.map_style_dark,
            MapStyleChoice.Auto to R.string.map_style_auto,
        )
        Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            styles.chunked(2).forEach { pair ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { (style, label) ->
                        val picked = Appearance.mapStyle == style
                        val reveal = rememberReveal(picked)
                        VisualOption(
                            label = stringResource(label),
                            selected = picked,
                            onClick = { Appearance.changeMapStyle(context, style) },
                            modifier = Modifier.weight(1f),
                            previewHeight = previewHeight,
                        ) { mapStyleSketch(style, reveal.value) }
                    }
                }
            }
        }
    }
}

/**
 * What the choice of map vehicles [level] means, as a small map in the look of the chosen map
 * style: the city zoomed out, with vehicles as dots on its lines for "all" and none for the
 * others; and for "all" and "dynamic" a close-up beside a magnifier with the vehicles as badges
 * (see [CloseVehicleSketch]). "Off" shows the city without any vehicle. A change of the choice
 * cross-fades. No real places. Also the preview of the setting in the map settings, lower there.
 */
@Composable
internal fun VehicleMapSketch(level: MapVehiclesChoice, sketchHeight: Dp = MAP_SKETCH_HEIGHT) {
    val animate = rememberAnimationsOn()
    val drive = if (animate) {
        rememberInfiniteTransition(label = "wideSketch").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(WIDE_SKETCH_LOOP_MILLIS, easing = LinearEasing), RepeatMode.Restart),
            label = "wideSketchDrive",
        )
    } else {
        null
    }
    val dark = AppColors.palette.dark
    val style = when (Appearance.mapStyle) {
        MapStyleChoice.Auto -> if (dark) MapStyleChoice.Dark else MapStyleChoice.Light
        else -> Appearance.mapStyle
    }
    val colors = mapStyleColors(style)
    Crossfade(
        targetState = level,
        animationSpec = tween(if (animate) 350 else 0),
        label = "vehicleSketch",
        modifier = Modifier
            .fillMaxWidth()
            .height(sketchHeight)
            .clip(RoundedCornerShape(20.dp)),
    ) { shown ->
        Box(modifier = Modifier.fillMaxSize()) {
            // The city zoomed out: its lines, with every vehicle a dot on them only for "all".
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawWideSketch(colors, dots = shown == MapVehiclesChoice.All, phase = drive?.value ?: MAP_SKETCH_REST)
            }
            if (shown != MapVehiclesChoice.Off) {
                // Close up: the vehicles as badges, as the map shows them for "all" and "dynamic".
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 10.dp, top = 10.dp, bottom = 10.dp)
                        .fillMaxWidth(WIDE_SKETCH_INSET_WIDTH)
                        .fillMaxHeight(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .shadow(4.dp, RoundedCornerShape(14.dp))
                            .clip(RoundedCornerShape(14.dp))
                            .border(2.dp, Color.White, RoundedCornerShape(14.dp)),
                    ) {
                        CloseVehicleSketch()
                    }
                    MagnifierSign(modifier = Modifier.align(Alignment.TopStart).offset(x = (-8).dp, y = (-6).dp))
                }
            }
        }
    }
}

/** The city of the sketch zoomed out: water, parks, a grid of streets and three lines; vehicles as dots on them where [dots]. */
private fun DrawScope.drawWideSketch(colors: List<Color>, dots: Boolean, phase: Float) {
    val w = size.width
    val h = size.height
    drawRect(colors[0])
    // A river across the bottom left and two parks.
    drawRect(colors[1], topLeft = Offset(0f, h * 0.84f), size = Size(w * 0.55f, h * 0.16f))
    drawRoundRect(colors[2], topLeft = Offset(w * 0.06f, h * 0.1f), size = Size(w * 0.14f, h * 0.22f), cornerRadius = CornerRadius(4.dp.toPx()))
    drawRoundRect(colors[2], topLeft = Offset(w * 0.36f, h * 0.56f), size = Size(w * 0.1f, h * 0.18f), cornerRadius = CornerRadius(4.dp.toPx()))
    // Many small streets: far off, they are thin.
    val street = 1.5.dp.toPx()
    for (index in 1 until 9) drawLine(colors[3], Offset(w * index / 9f, 0f), Offset(w * index / 9f, h), strokeWidth = street)
    for (index in 1 until 5) drawLine(colors[3], Offset(0f, h * index / 5f), Offset(w, h * index / 5f), strokeWidth = street)
    // Three lines, and their vehicles as dots going along them.
    val lineWidth = 2.5.dp.toPx()
    WIDE_SKETCH_LINES.forEachIndexed { index, line ->
        val path = Path().apply {
            moveTo(line[0] * w, line[1] * h)
            quadraticBezierTo(line[2] * w, line[3] * h, line[4] * w, line[5] * h)
        }
        drawPath(path, WIDE_SKETCH_COLORS[index], style = Stroke(width = lineWidth, cap = StrokeCap.Round))
    }
    if (!dots) return
    val outer = 3.5.dp.toPx()
    val inner = 2.3.dp.toPx()
    WIDE_SKETCH_LINES.forEachIndexed { index, line ->
        for (dot in 0 until WIDE_SKETCH_DOTS) {
            val along = (dot.toFloat() / WIDE_SKETCH_DOTS + phase * (if (index % 2 == 0) 1f else -1f) + index * 0.13f).let { it - floor(it) }
            val u = 1f - along
            val x = (u * u * line[0] + 2f * u * along * line[2] + along * along * line[4]) * w
            val y = (u * u * line[1] + 2f * u * along * line[3] + along * along * line[5]) * h
            drawCircle(Color.White, radius = outer, center = Offset(x, y))
            drawCircle(WIDE_SKETCH_COLORS[index], radius = inner, center = Offset(x, y))
        }
    }
}

/** A small round sign with a magnifying glass: the inset beside it is the map close up. */
@Composable
private fun MagnifierSign(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .size(24.dp)
            .shadow(3.dp, CircleShape)
            .clip(CircleShape)
            .background(AppColors.Card),
    ) {
        val lens = Offset(size.width * 0.44f, size.height * 0.44f)
        val radius = size.width * 0.2f
        val stroke = 2.dp.toPx()
        drawCircle(AppColors.Ink, radius = radius, center = lens, style = Stroke(width = stroke))
        drawLine(
            AppColors.Ink,
            Offset(lens.x + radius * 0.72f, lens.y + radius * 0.72f),
            Offset(size.width * 0.76f, size.height * 0.76f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

// One loop of the dots on the zoomed-out sketch; the inset close up takes this share of its width.
private const val WIDE_SKETCH_LOOP_MILLIS = 16_000
private const val WIDE_SKETCH_INSET_WIDTH = 0.5f
// The dots on each line of the zoomed-out sketch.
private const val WIDE_SKETCH_DOTS = 4
// The lines of the zoomed-out sketch: start, control point and end (shares of width and height).
private val WIDE_SKETCH_LINES = listOf(
    floatArrayOf(0f, 0.72f, 0.5f, 0.8f, 1f, 0.18f),
    floatArrayOf(0.12f, 0f, 0.25f, 0.6f, 0.62f, 1f),
    floatArrayOf(0f, 0.4f, 0.55f, 0.3f, 1f, 0.55f),
)
private val WIDE_SKETCH_COLORS = listOf(Color(0xFF3A7BD5), Color(0xFF3F9C35), Color(0xFFBE1414))

/**
 * A small piece of map close up in the look of the chosen map style: a few streets, a park, a
 * river, one U-Bahn line with its station sign, and vehicles on it as on the real map. The train
 * stands live (with the live dot) in its row right of the sign, a bus drives down a street by
 * the timetable, and further off a tram shows as a dot in its line colour. Without animations
 * (system setting) the bus and the tram stand still. Fills what it is given.
 */
@Composable
private fun CloseVehicleSketch() {
    val animate = rememberAnimationsOn()
    val drive = if (animate) {
        rememberInfiniteTransition(label = "mapSketch").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(MAP_SKETCH_LOOP_MILLIS, easing = LinearEasing), RepeatMode.Restart),
            label = "mapSketchDrive",
        )
    } else {
        null
    }
    // The map's own colours: those of the chosen style, light or dark as the app is for "automatic".
    val dark = AppColors.palette.dark
    val style = when (Appearance.mapStyle) {
        MapStyleChoice.Auto -> if (dark) MapStyleChoice.Dark else MapStyleChoice.Light
        else -> Appearance.mapStyle
    }
    val colors = mapStyleColors(style)
    val measurer = rememberTextMeasurer()
    val signLetter = remember(measurer) {
        measurer.measure("U", TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold))
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }
        val station = mapSketchLinePoint(MAP_SKETCH_STATION_AT, width, height)
        val signHalf = with(density) { (MAP_SKETCH_SIGN / 2).toPx() }
        val rowGap = with(density) { GROUP_SIGN_GAP_DP.dp.toPx() }
        // A river along the bottom, and the U-Bahn line in a soft curve: made once per size, not every frame.
        val river = remember(width, height) {
            Path().apply {
                moveTo(0f, height * 0.9f)
                cubicTo(width * 0.3f, height * 0.78f, width * 0.6f, height * 1.02f, width, height * 0.86f)
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }
        }
        val line = remember(width, height) {
            Path().apply {
                moveTo(0f, height * MAP_SKETCH_LINE_START)
                quadraticBezierTo(width * 0.45f, height * MAP_SKETCH_LINE_START, width, height * MAP_SKETCH_LINE_END)
            }
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(colors[0])
            // The river and a park at the top left.
            drawPath(river, colors[1])
            drawRoundRect(
                colors[2],
                topLeft = Offset(size.width * 0.04f, size.height * 0.08f),
                size = Size(size.width * 0.22f, size.height * 0.34f),
                cornerRadius = CornerRadius(8.dp.toPx()),
            )
            // The streets: two across, two down, one at a slant.
            val street = 6.dp.toPx()
            val minor = 3.5.dp.toPx()
            drawLine(colors[3], Offset(0f, size.height * MAP_SKETCH_TRAM_STREET), Offset(size.width, size.height * MAP_SKETCH_TRAM_STREET), strokeWidth = street)
            drawLine(colors[3], Offset(size.width * MAP_SKETCH_BUS_STREET, 0f), Offset(size.width * MAP_SKETCH_BUS_STREET, size.height), strokeWidth = street)
            drawLine(colors[3], Offset(size.width * 0.3f, 0f), Offset(size.width * 0.3f, size.height), strokeWidth = minor)
            drawLine(colors[3], Offset(0f, size.height * 0.62f), Offset(size.width, size.height * 0.66f), strokeWidth = minor)
            drawLine(colors[3], Offset(size.width * 0.5f, 0f), Offset(size.width * 0.92f, size.height), strokeWidth = minor)
            // The U-Bahn line in a soft curve, with a light casing as on the map.
            drawPath(line, colors[0], style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round))
            drawPath(line, MAP_SKETCH_LINE_COLOR, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
            // The station sign on the line: a white edge, the blue square, its "U".
            val half = MAP_SKETCH_SIGN.toPx() / 2
            drawRoundRect(
                Color.White,
                topLeft = Offset(station.x - half - 1.5.dp.toPx(), station.y - half - 1.5.dp.toPx()),
                size = Size(half * 2 + 3.dp.toPx(), half * 2 + 3.dp.toPx()),
                cornerRadius = CornerRadius(5.dp.toPx()),
            )
            drawRoundRect(
                MAP_SKETCH_SIGN_COLOR,
                topLeft = Offset(station.x - half, station.y - half),
                size = Size(half * 2, half * 2),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
            drawText(
                signLetter,
                topLeft = Offset(station.x - signLetter.size.width / 2f, station.y - signLetter.size.height / 2f),
            )
            // Further off, where the map shows dots: a tram on the street across the top.
            val tramX = mapSketchLoop((drive?.value ?: MAP_SKETCH_REST) + 0.35f, size.width, reverse = true)
            val tram = Offset(tramX, size.height * MAP_SKETCH_TRAM_STREET)
            drawCircle(Color.White, radius = 4.5.dp.toPx(), center = tram)
            drawCircle(MAP_SKETCH_TRAM_COLOR, radius = 3.dp.toPx(), center = tram)
        }
        // The train live in its row right of the sign, as on the map.
        SketchBadge(line = "U2", product = "subway", live = true) { badge ->
            IntOffset((station.x + signHalf + rowGap).roundToInt(), (station.y - badge.height / 2f).roundToInt())
        }
        // A bus by the timetable, driving down its street.
        SketchBadge(line = "100", product = "bus", live = false) { badge ->
            val y = mapSketchLoop(drive?.value ?: MAP_SKETCH_REST, height, reverse = false)
            IntOffset((width * MAP_SKETCH_BUS_STREET - badge.width / 2f).roundToInt(), (y - badge.height / 2f).roundToInt())
        }
    }
}

// The sketch is this tall; one loop of the bus and the tram takes this long.
private val MAP_SKETCH_HEIGHT = 120.dp
private const val MAP_SKETCH_LOOP_MILLIS = 7_000
// Where the bus and the tram stand without animations, as a share of their loop.
private const val MAP_SKETCH_REST = 0.3f
// The streets of the bus (share of the width) and of the tram (share of the height).
private const val MAP_SKETCH_BUS_STREET = 0.8f
private const val MAP_SKETCH_TRAM_STREET = 0.24f
// The U-Bahn line: its height at the left and right edge (shares), and where its station is on it.
private const val MAP_SKETCH_LINE_START = 0.78f
private const val MAP_SKETCH_LINE_END = 0.34f
private const val MAP_SKETCH_STATION_AT = 0.3f
private val MAP_SKETCH_SIGN = 16.dp
private val MAP_SKETCH_LINE_COLOR = Color(0xFF3A7BD5)
private val MAP_SKETCH_SIGN_COLOR = Color(0xFF115D91)
private val MAP_SKETCH_TRAM_COLOR = Color(0xFFBE1414)

/** The point at [t] (0 to 1) of the sketch's U-Bahn line in a [width] by [height] box. */
private fun mapSketchLinePoint(t: Float, width: Float, height: Float): Offset {
    val u = 1f - t
    val x = 2f * u * t * width * 0.45f + t * t * width
    val y = (u * u + 2f * u * t) * height * MAP_SKETCH_LINE_START + t * t * height * MAP_SKETCH_LINE_END
    return Offset(x, y)
}

/** Where a vehicle that loops over a street [length] long is at [phase]: in at one end, out at the other. */
private fun mapSketchLoop(phase: Float, length: Float, reverse: Boolean): Float {
    val share = phase - floor(phase)
    val along = -0.15f * length + share * 1.3f * length
    return if (reverse) length - along else along
}

/**
 * One vehicle of the sketch: its line badge, with the live dot when [live], placed by [place]
 * from its measured size (read in the layout step, so driving costs no recomposition).
 */
@Composable
private fun SketchBadge(line: String, product: String, live: Boolean, place: (IntSize) -> IntOffset) {
    var badge by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier = Modifier
            .onSizeChanged { badge = it }
            .offset { place(badge) },
    ) {
        LineBadge(line = line, product = product, minWidth = 34.dp, at = LineColors.BERLIN_CENTRE)
        if (live) {
            LiveDot(
                // As on the map: only trains pulse, buses and trams carry a still dot.
                pulse = isMajorProduct(product),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .graphicsLayer {
                        translationX = liveDotOverhangDp().dp.toPx()
                        translationY = -liveDotOverhangDp().dp.toPx()
                    },
            )
        }
    }
}

private fun granted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

/** True when the app may show notifications: always before Android 13, after that with the permission. */
private fun notificationsGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || granted(context, Manifest.permission.POST_NOTIFICATIONS)

@Composable
private fun NotificationsPage(prefs: AppPrefs) {
    val context = LocalContext.current
    var reminders by remember { mutableStateOf(prefs.alightReminders) }
    var allowed by remember { mutableStateOf(notificationsGranted(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        allowed = notificationsGranted(context)
    }
    // A Live Update needs Android 16; before that the trip always shows in the look of the app.
    val liveUpdates = Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA
    val sketchHeight = if (LocalSetupPage.current.compact) 72.dp else 96.dp
    SetupCard(
        title = stringResource(R.string.setup_notifications_title),
        text = stringResource(R.string.setup_notifications_short),
        more = stringResource(R.string.setup_notifications_text),
    ) {
        if (liveUpdates) {
            VisualChoice(
                title = stringResource(R.string.notification_look_title),
                text = stringResource(R.string.notification_look_text),
            ) {
                val live = Appearance.notificationLook == NotificationLook.LiveUpdate
                val liveReveal = rememberReveal(live)
                VisualOption(
                    label = stringResource(R.string.notification_look_live),
                    selected = live,
                    onClick = { Appearance.changeNotificationLook(context, NotificationLook.LiveUpdate) },
                    modifier = Modifier.weight(1f),
                    previewHeight = sketchHeight,
                ) { notificationSketch(chip = true, reveal = liveReveal.value) }
                val appReveal = rememberReveal(!live)
                VisualOption(
                    label = stringResource(R.string.notification_look_app),
                    selected = !live,
                    onClick = { Appearance.changeNotificationLook(context, NotificationLook.AppLook) },
                    modifier = Modifier.weight(1f),
                    previewHeight = sketchHeight,
                ) { notificationSketch(chip = false, reveal = appReveal.value) }
            }
        }
        SetupSwitch(
            title = stringResource(R.string.alight_reminders_title),
            text = stringResource(R.string.alight_reminders_text),
            checked = reminders,
            onChange = { value ->
                reminders = value
                prefs.alightReminders = value
            },
        )
        PermissionRow(
            title = stringResource(R.string.setup_permission_notifications),
            text = stringResource(R.string.setup_permission_notifications_text),
            granted = allowed,
            onAllow = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
        )
    }
}

/**
 * The shortcut alert: a remembered setting. While any trip is followed, it speaks up when
 * something changed since the journey was chosen; "only when needed" or "always on".
 */
@Composable
private fun ShortcutAlertPage() {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    var alertAlways by remember { mutableStateOf(prefs.shortcutAlert) }
    SetupCard(
        title = stringResource(R.string.shortcut_alert),
        text = stringResource(R.string.setup_shortcut_short),
        more = stringResource(R.string.shortcut_alert_text),
    ) {
        FeatureTag(text = stringResource(R.string.setup_tag_remembered))
        SetupChoice(
            title = stringResource(R.string.setup_shortcut_when),
            text = stringResource(R.string.setup_shortcut_when_text),
            labels = listOf(stringResource(R.string.setup_shortcut_on_demand), stringResource(R.string.setup_shortcut_always)),
            selected = if (alertAlways) 1 else 0,
            onSelect = { index ->
                alertAlways = index == 1
                prefs.shortcutAlert = alertAlways
            },
        )
    }
}

/**
 * The disruption check: at which times of the day the saved routes with the bell on, and the
 * places where that is switched on here, are looked at (see DisruptionWatch). Seven in the
 * morning is suggested and stored once the page is reached, unless times were chosen before;
 * the bell on a route (or the places) switches it on.
 */
@Composable
private fun DisruptionCheckPage() {
    val context = LocalContext.current
    val active = LocalSetupPage.current.active
    var times by remember {
        mutableStateOf(
            if (DisruptionWatch.checkTimesChosen(context)) DisruptionWatch.checkTimes(context) else listOf(DEFAULT_CHECK_MINUTES),
        )
    }
    var places by remember { mutableStateOf(DisruptionWatch.checkPlaces(context)) }
    LaunchedEffect(active) {
        if (active && !DisruptionWatch.checkTimesChosen(context)) DisruptionWatch.setCheckTimes(context, times)
    }
    SetupCard(
        title = stringResource(R.string.disruption_check_title),
        text = stringResource(R.string.setup_disruption_short),
        more = stringResource(R.string.disruption_check_text),
    ) {
        FeatureTag(text = stringResource(R.string.setup_tag_remembered))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(AppColors.Card)
                .padding(12.dp),
        ) {
            CheckTimesEditor(
                times = times,
                onTimes = { value ->
                    times = value
                    DisruptionWatch.setCheckTimes(context, value)
                },
            )
        }
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SmallBell()
            Text(
                text = stringResource(R.string.setup_disruption_bell_hint),
                color = AppColors.Muted,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                modifier = Modifier.weight(1f),
            )
        }
        // The places too: Home, Work and the saved places, the disruptions in their reach.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(AppColors.Card)
                .padding(start = 14.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = stringResource(R.string.dwatch_places_title), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(
                    text = stringResource(R.string.dwatch_places_text),
                    color = AppColors.Muted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
            Switch(
                checked = places,
                onCheckedChange = { value ->
                    places = value
                    DisruptionWatch.setCheckPlaces(context, value)
                },
            )
        }
    }
}

/**
 * "Every minute counts": a sprint mode switched on for one trip, in a connection. It turns on
 * sprint changes and the shortcut alert for that trip; "Try it" shows what an offer looks like.
 */
@Composable
private fun EveryMinutePage() {
    val context = LocalContext.current
    var offer by remember { mutableStateOf<FasterOffer?>(null) }
    var shown by remember { mutableStateOf(false) }
    SetupCard(
        title = stringResource(R.string.every_minute),
        text = stringResource(R.string.setup_every_minute_short),
        more = stringResource(R.string.hint_every_minute_text) + "\n\n" + stringResource(R.string.setup_every_minute_how),
    ) {
        FeatureTag(text = stringResource(R.string.setup_tag_per_trip))
        PrimaryButton(
            text = stringResource(R.string.setup_try),
            onClick = {
                // The same card and notification a real faster way brings, once per tap.
                val demo = FasterWatch.demoOffer(OffsetDateTime.now())
                offer = demo
                shown = true
                FasterWatch.showDemo(context, demo)
            },
            glyph = Glyph.Play,
            modifier = Modifier.fillMaxWidth(),
        )
        AnimatedVisibility(
            visible = shown,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            val demo = offer
            if (demo != null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FasterCard(
                        offer = demo,
                        onTake = {
                            shown = false
                            FasterWatch.dismissDemo(context)
                        },
                        onIgnore = {
                            shown = false
                            FasterWatch.dismissDemo(context)
                        },
                    )
                    if (!notificationsGranted(context)) {
                        Text(
                            text = stringResource(R.string.setup_try_no_notification),
                            color = AppColors.Muted,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }
        }
    }
}

/** A small tag that says what kind of feature a page is about ("remembered", "per trip"). */
@Composable
private fun FeatureTag(text: String) {
    Text(
        text = text,
        color = AppColors.OnBlueContainer,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.BlueContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/**
 * What each transfer level means, as a pace, one short line each; Sprint in its own colours,
 * because it is often unrealistic (only running or luck).
 */
@Composable
private fun TransferLevelLines() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.Card)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(R.string.setup_level_relaxed, R.string.setup_level_normal, R.string.setup_level_risky).forEach { line ->
            Text(text = stringResource(line), color = AppColors.Ink, fontSize = 13.sp, lineHeight = 17.sp)
        }
        Text(
            text = stringResource(R.string.setup_sprint_note),
            color = tightnessInk(Tightness.VeryRisky),
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(tightnessContainer(Tightness.VeryRisky))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun PermissionsPage() {
    val context = LocalContext.current
    // Counted up after every answer, so the marks are read again.
    var answers by remember { mutableIntStateOf(0) }
    val location = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ -> answers++ }
    val single = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> answers++ }
    val locationGranted = remember(answers) {
        granted(context, Manifest.permission.ACCESS_FINE_LOCATION) || granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    val calendarGranted = remember(answers) { granted(context, Manifest.permission.READ_CALENDAR) }
    val contactsGranted = remember(answers) { granted(context, Manifest.permission.READ_CONTACTS) }
    SetupCard(title = stringResource(R.string.setup_permissions_title), text = stringResource(R.string.setup_permissions_text)) {
        PermissionRow(
            title = stringResource(R.string.setup_permission_location),
            text = stringResource(R.string.setup_permission_location_text),
            granted = locationGranted,
            onAllow = {
                location.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            },
        )
        PermissionRow(
            title = stringResource(R.string.setup_permission_calendar),
            text = stringResource(R.string.setup_permission_calendar_text),
            granted = calendarGranted,
            onAllow = { single.launch(Manifest.permission.READ_CALENDAR) },
        )
        PermissionRow(
            title = stringResource(R.string.setup_permission_contacts),
            text = stringResource(R.string.setup_permission_contacts_text),
            granted = contactsGranted,
            onAllow = { single.launch(Manifest.permission.READ_CONTACTS) },
        )
    }
}

/** The connected watch: "Watch connected ✓" with Next Stop on it, else a button that installs it there. */
@Composable
private fun WatchPage(status: WatchStatus?) {
    SetupCard(
        title = stringResource(R.string.setup_watch_title),
        text = stringResource(R.string.setup_watch_short),
        more = stringResource(R.string.setup_watch_text),
    ) {
        WatchStatusRow(status)
    }
}

@Composable
private fun DonePage() {
    ScrollWithHint(spacing = 18.dp, horizontalAlignment = Alignment.CenterHorizontally) {
        SetupLogo(edge = if (LocalSetupPage.current.compact) 72 else 88)
        Text(
            text = stringResource(R.string.setup_done_title),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 30.sp,
            lineHeight = 34.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.setup_done_text),
            color = AppColors.Muted,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Spacer(modifier = Modifier.width(1.dp))
    }
}

// Line colours of the small sketches: S-Bahn, U-Bahn, tram and a yellow U-Bahn line.
internal val SketchLines = listOf(Color(0xFF008D4F), Color(0xFF115D91), Color(0xFFCC0A22), Color(0xFFF0B400))

/** A ride in a column of the chart sketch, from [start] to [end] of its height. */
internal class SketchBlock(val start: Float, val end: Float, val line: Int)

internal val ChartBlocks = listOf(
    listOf(SketchBlock(0.08f, 0.42f, 0), SketchBlock(0.50f, 0.86f, 1)),
    listOf(SketchBlock(0.16f, 0.74f, 2)),
    listOf(SketchBlock(0.26f, 0.50f, 1), SketchBlock(0.58f, 0.94f, 3)),
    listOf(SketchBlock(0.34f, 0.82f, 0)),
)

/**
 * A choice as pictures: [title] and [text] above, the options ([VisualOption], each with
 * Modifier.weight(1f)) side by side below.
 */
@Composable
private fun VisualChoice(title: String, text: String, options: @Composable RowScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SetupText(title, text, modifier = Modifier.padding(horizontal = 4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            options()
        }
    }
}

/**
 * One option of a [VisualChoice]: a small schematic [preview] with its [label] below. The
 * picked one wears a ring in the accent colour and springs to full size; the others rest a
 * little smaller.
 */
@Composable
internal fun VisualOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    previewHeight: Dp = 96.dp,
    preview: DrawScope.() -> Unit,
) {
    val scale by animateFloatAsState(
        if (selected) 1f else 0.94f,
        spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
        label = "optionScale",
    )
    val ring by animateColorAsState(if (selected) AppColors.Blue else Color.Transparent, label = "optionRing")
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(AppColors.Card)
            .border(2.dp, ring, shape)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(previewHeight)
                .clip(RoundedCornerShape(14.dp))
                .background(AppColors.Chip),
            onDraw = preview,
        )
        Text(
            text = label,
            color = if (selected) AppColors.Blue else AppColors.Ink,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/**
 * Runs from 0 to 1 each time [selected] turns true, for the little motion of a sketch when
 * its option is picked; it rests at 1 otherwise.
 */
@Composable
internal fun rememberReveal(selected: Boolean): Animatable<Float, AnimationVector1D> {
    val reveal = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            reveal.snapTo(0f)
            reveal.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow))
        } else {
            reveal.snapTo(1f)
        }
    }
    return reveal
}

internal fun DrawScope.block(color: Color, x: Float, y: Float, width: Float, height: Float, radius: Float = 3.dp.toPx(), alpha: Float = 1f) {
    drawRoundRect(
        color = color,
        topLeft = Offset(x, y),
        size = Size(width, height),
        cornerRadius = CornerRadius(radius, radius),
        alpha = alpha.coerceIn(0f, 1f),
    )
}

/** The chart of connections: columns with a coloured block per ride over faint hour lines. */
internal fun DrawScope.chartSketch(left: Float, top: Float, width: Float, height: Float) {
    val gap = width * 0.06f
    val columnWidth = (width - gap * 5) / 4
    for (line in 1..3) {
        val y = top + height * line / 4f
        drawLine(AppColors.Grid, Offset(left, y), Offset(left + width, y), strokeWidth = 1.dp.toPx())
    }
    ChartBlocks.forEachIndexed { column, rides ->
        val x = left + gap + column * (columnWidth + gap)
        rides.forEach { ride ->
            block(SketchLines[ride.line], x, top + height * ride.start, columnWidth, height * (ride.end - ride.start))
        }
    }
}

/** The list of connections: stacked cards, each with a line badge, two lines of text and its minutes. */
internal fun DrawScope.listSketch(left: Float, top: Float, width: Float, height: Float, rows: Int = 3) {
    val gap = height * 0.06f
    val rowHeight = (height - gap * (rows + 1)) / rows
    for (row in 0 until rows) {
        val y = top + gap + row * (rowHeight + gap)
        val x = left + gap
        val rowWidth = width - gap * 2
        block(AppColors.Card, x, y, rowWidth, rowHeight, radius = 4.dp.toPx())
        val inner = rowHeight * 0.22f
        val badgeHeight = rowHeight - inner * 2
        block(SketchLines[row % SketchLines.size], x + inner, y + inner, badgeHeight * 1.4f, badgeHeight, radius = 2.dp.toPx())
        val textLeft = x + inner * 2 + badgeHeight * 1.4f
        val pillWidth = rowWidth * 0.18f
        val textWidth = (x + rowWidth - inner * 2 - pillWidth) - textLeft
        if (textWidth > 0f) {
            block(AppColors.Muted, textLeft, y + inner, textWidth, badgeHeight * 0.38f, radius = 2.dp.toPx(), alpha = 0.55f)
            block(AppColors.Muted, textLeft, y + rowHeight - inner - badgeHeight * 0.38f, textWidth * 0.6f, badgeHeight * 0.38f, radius = 2.dp.toPx(), alpha = 0.3f)
        }
        block(AppColors.Lime, x + rowWidth - inner - pillWidth, y + inner, pillWidth, badgeHeight, radius = badgeHeight / 2)
    }
}

/** The frame of a small phone; gives back the screen inside it as left, top, width, height. */
internal fun DrawScope.phoneFrame(left: Float, top: Float, width: Float, height: Float): FloatArray {
    val radius = 7.dp.toPx()
    drawRoundRect(AppColors.PlainGround, Offset(left, top), Size(width, height), CornerRadius(radius, radius))
    drawRoundRect(
        color = AppColors.Muted.copy(alpha = 0.55f),
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = CornerRadius(radius, radius),
        style = Stroke(width = 1.5.dp.toPx()),
    )
    val pad = 3.dp.toPx()
    // The camera at the top edge.
    drawCircle(AppColors.Muted.copy(alpha = 0.55f), radius = 1.5.dp.toPx(), center = Offset(left + width / 2, top + pad + 1.dp.toPx()))
    return floatArrayOf(left + pad, top + pad * 2 + 2.dp.toPx(), width - pad * 2, height - pad * 3 - 2.dp.toPx())
}

/** The details of a journey: a rail in the colour of its line with a ring at each end and the stops beside it. */
internal fun DrawScope.detailSketch(left: Float, top: Float, width: Float, height: Float, header: Boolean) {
    var y = top
    var bodyHeight = height
    if (header) {
        block(AppColors.Blue, left, top, width, height * 0.22f, radius = 3.dp.toPx())
        y = top + height * 0.28f
        bodyHeight = height * 0.72f
    }
    val railX = left + width * 0.22f
    val railTop = y + bodyHeight * 0.14f
    val railBottom = y + bodyHeight * 0.86f
    val line = SketchLines[0]
    drawLine(line, Offset(railX, railTop), Offset(railX, railBottom), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
    listOf(railTop, railBottom).forEach { stop ->
        drawCircle(AppColors.Card, radius = 3.dp.toPx(), center = Offset(railX, stop))
        drawCircle(line, radius = 3.dp.toPx(), center = Offset(railX, stop), style = Stroke(width = 1.5.dp.toPx()))
        block(AppColors.Muted, railX + width * 0.14f, stop - 1.5.dp.toPx(), width * 0.5f, 3.dp.toPx(), radius = 1.5.dp.toPx(), alpha = 0.55f)
    }
    block(AppColors.Muted, railX + width * 0.14f, (railTop + railBottom) / 2 - 1.dp.toPx(), width * 0.36f, 2.dp.toPx(), radius = 1.dp.toPx(), alpha = 0.3f)
}

/** Two cards: the chart above, the details of the picked journey in a card that slides up from below. */
internal fun DrawScope.splitSketch(reveal: Float) {
    val phoneHeight = size.height - 12.dp.toPx()
    val phoneWidth = phoneHeight * 0.56f
    val screen = phoneFrame((size.width - phoneWidth) / 2, 6.dp.toPx(), phoneWidth, phoneHeight)
    val (left, top, width, height) = screen.toList()
    clipRect(left, top, left + width, top + height) {
        chartSketch(left, top, width, height * 0.5f)
        val cardTop = top + height * 0.54f + (1f - reveal) * height * 0.5f
        block(AppColors.Card, left + 1.dp.toPx(), cardTop, width - 2.dp.toPx(), height * 0.46f, radius = 5.dp.toPx())
        detailSketch(left + 2.dp.toPx(), cardTop + 2.dp.toPx(), width - 4.dp.toPx(), height * 0.46f - 4.dp.toPx(), header = false)
    }
}

/** Single pages: the list fills one screen, an arrow leads to the journey on a page of its own, which slides in. */
internal fun DrawScope.singleSketch(reveal: Float) {
    val phoneHeight = size.height - 12.dp.toPx()
    val phoneWidth = phoneHeight * 0.5f
    val arrowRoom = 20.dp.toPx()
    val start = (size.width - phoneWidth * 2 - arrowRoom) / 2
    val top = 6.dp.toPx()
    val first = phoneFrame(start, top, phoneWidth, phoneHeight)
    listSketch(first[0], first[1], first[2], first[3], rows = 4)
    // The arrow between the two pages.
    val arrowAlpha = reveal.coerceIn(0f, 1f)
    val arrowY = top + phoneHeight / 2
    val arrowStart = start + phoneWidth + 4.dp.toPx()
    val arrowEnd = start + phoneWidth + arrowRoom - 4.dp.toPx()
    val arrowColor = AppColors.Blue.copy(alpha = arrowAlpha)
    val stroke = 2.dp.toPx()
    drawLine(arrowColor, Offset(arrowStart, arrowY), Offset(arrowEnd, arrowY), strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(arrowColor, Offset(arrowEnd, arrowY), Offset(arrowEnd - 4.dp.toPx(), arrowY - 4.dp.toPx()), strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(arrowColor, Offset(arrowEnd, arrowY), Offset(arrowEnd - 4.dp.toPx(), arrowY + 4.dp.toPx()), strokeWidth = stroke, cap = StrokeCap.Round)
    val second = phoneFrame(start + phoneWidth + arrowRoom, top, phoneWidth, phoneHeight)
    val (left, screenTop, width, height) = second.toList()
    clipRect(left, screenTop, left + width, screenTop + height) {
        detailSketch(left + (1f - reveal) * width, screenTop, width, height, header = true)
    }
}

/**
 * A notification under the status bar. With [chip] (Live Update) the minutes also stand as a
 * chip in the status bar and the notification is plain; without it (app look) the notification
 * is the blue box of the app. The notification drops in while [reveal] runs to 1.
 */
internal fun DrawScope.notificationSketch(chip: Boolean, reveal: Float) {
    val margin = 8.dp.toPx()
    val barY = 9.dp.toPx()
    // The clock of the status bar, and the chip next to it.
    block(AppColors.Ink, margin, barY - 2.dp.toPx(), 14.dp.toPx(), 4.dp.toPx(), radius = 2.dp.toPx(), alpha = 0.7f)
    if (chip) {
        val chipWidth = 26.dp.toPx() * (0.4f + 0.6f * reveal.coerceIn(0f, 1.2f))
        val chipHeight = 10.dp.toPx()
        block(AppColors.Blue, margin + 18.dp.toPx(), barY - chipHeight / 2, chipWidth, chipHeight, radius = chipHeight / 2)
        block(AppColors.OnBlue, margin + 22.dp.toPx(), barY - 1.dp.toPx(), (chipWidth - 8.dp.toPx()).coerceAtLeast(0f), 2.dp.toPx(), radius = 1.dp.toPx())
    }
    listOf(0, 1, 2).forEach { dot ->
        drawCircle(AppColors.Ink.copy(alpha = 0.5f), radius = 1.8.dp.toPx(), center = Offset(size.width - margin - dot * 6.dp.toPx(), barY))
    }
    val cardTop = 20.dp.toPx() - (1f - reveal) * 10.dp.toPx()
    val cardHeight = size.height - 20.dp.toPx() - margin
    val cardWidth = size.width - margin * 2
    val alpha = reveal.coerceIn(0f, 1f)
    val ground = if (chip) AppColors.Card else AppColors.Blue
    val ink = if (chip) AppColors.Ink else AppColors.OnBlue
    block(ground, margin, cardTop, cardWidth, cardHeight, radius = 8.dp.toPx(), alpha = alpha)
    val inner = 7.dp.toPx()
    val icon = 5.dp.toPx()
    drawCircle(if (chip) AppColors.Blue else AppColors.OnBlue, radius = icon, center = Offset(margin + inner + icon, cardTop + inner + icon), alpha = alpha)
    val textLeft = margin + inner * 2 + icon * 2
    val textWidth = cardWidth - (textLeft - margin) - inner
    block(ink, textLeft, cardTop + inner, textWidth * 0.8f, 4.dp.toPx(), radius = 2.dp.toPx(), alpha = alpha * 0.8f)
    block(ink, textLeft, cardTop + inner + 7.dp.toPx(), textWidth * 0.5f, 3.dp.toPx(), radius = 1.5.dp.toPx(), alpha = alpha * 0.45f)
    // The progress of the trip along the bottom of the notification.
    val trackY = cardTop + cardHeight - inner - 3.dp.toPx()
    val trackWidth = cardWidth - inner * 2
    block(ink, margin + inner, trackY, trackWidth, 3.dp.toPx(), radius = 1.5.dp.toPx(), alpha = alpha * 0.2f)
    block(if (chip) AppColors.Blue else AppColors.OnBlue, margin + inner, trackY, trackWidth * 0.6f * alpha, 3.dp.toPx(), radius = 1.5.dp.toPx(), alpha = alpha)
}

/**
 * The choice of connecting departures at a change, as the companion shows it: with [choice]
 * the question above and three chips (a tight one, the chosen one, a relaxed one) that
 * spread out from the chosen one while [reveal] runs to 1; without it only the chosen
 * connection, which springs in.
 */
internal fun DrawScope.choiceSketch(choice: Boolean, reveal: Float) {
    val margin = 8.dp.toPx()
    val gap = 4.dp.toPx()
    val chipWidth = (size.width - margin * 2 - gap * 2) / 3
    val chipHeight = size.height * 0.5f
    val chipTop = size.height * 0.36f
    val middle = (size.width - chipWidth) / 2
    val radius = 7.dp.toPx()
    fun chip(x: Float, ground: Color, ink: Color, alpha: Float, grow: Float = 1f) {
        val width = chipWidth * grow
        val left = x + (chipWidth - width) / 2
        block(ground, left, chipTop, width, chipHeight, radius = radius, alpha = alpha)
        // The time and below it the word for the chip, as two bars.
        block(ink, left + width * 0.2f, chipTop + chipHeight * 0.26f, width * 0.6f, 4.dp.toPx(), radius = 2.dp.toPx(), alpha = alpha)
        block(ink, left + width * 0.3f, chipTop + chipHeight * 0.6f, width * 0.4f, 3.dp.toPx(), radius = 1.5.dp.toPx(), alpha = alpha * 0.7f)
    }
    if (choice) {
        // The question "Which connection do you want to take?".
        block(AppColors.Muted, margin, size.height * 0.14f, size.width * 0.66f, 4.dp.toPx(), radius = 2.dp.toPx(), alpha = 0.55f * reveal)
        val spread = (chipWidth + gap) * reveal
        chip(middle - spread, AppColors.Chip, AppColors.Ink, alpha = reveal)
        chip(middle + spread, AppColors.CalmContainer, AppColors.CalmInk, alpha = reveal)
        chip(middle, AppColors.Blue, AppColors.OnBlue, alpha = 1f)
    } else {
        chip(middle, AppColors.Blue, AppColors.OnBlue, alpha = 1f, grow = 0.6f + 0.4f * reveal)
    }
}

/**
 * Two rides with the change between them: the gap is as wide as the [level] lets the change
 * be, and grows or shrinks while the level moves.
 */
@Composable
internal fun TransferSketch(level: TransferComfort) {
    val target = when (level) {
        TransferComfort.Relaxed -> 0.42f
        TransferComfort.Normal -> 0.26f
        TransferComfort.Risky -> 0.12f
        TransferComfort.VeryRisky -> 0.04f
    }
    val gap by animateFloatAsState(target, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow), label = "changeGap")
    val color by animateColorAsState(levelStrongColor(level), label = "changeColor")
    val first = SketchLines[0]
    val second = SketchLines[2]
    val ground = AppColors.Card
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.Card),
    ) {
        val margin = 18.dp.toPx()
        val width = size.width - margin * 2
        val y = size.height / 2
        val gapWidth = width * gap.coerceIn(0.01f, 0.6f)
        val firstEnd = margin + (width - gapWidth) / 2
        val secondStart = firstEnd + gapWidth
        val bar = 8.dp.toPx()
        drawLine(first, Offset(margin, y), Offset(firstEnd, y), strokeWidth = bar, cap = StrokeCap.Round)
        drawLine(second, Offset(secondStart, y), Offset(size.width - margin, y), strokeWidth = bar, cap = StrokeCap.Round)
        // The walk of the change: dashes in the colour of the level.
        if (secondStart - firstEnd > 8.dp.toPx()) {
            drawLine(
                color = color,
                start = Offset(firstEnd + 6.dp.toPx(), y),
                end = Offset(secondStart - 6.dp.toPx(), y),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
        }
        listOf(margin to first, firstEnd to first, secondStart to second, size.width - margin to second).forEach { (x, line) ->
            drawCircle(ground, radius = 5.dp.toPx(), center = Offset(x, y))
            drawCircle(line, radius = 5.dp.toPx(), center = Offset(x, y), style = Stroke(width = 2.5.dp.toPx()))
        }
    }
}

/** A departure card in the colours picked right now, so every choice shows at once. */
@Composable
private fun PalettePreview(label: Boolean = true) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.Card)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (label) Text(text = stringResource(R.string.setup_preview), color = AppColors.Muted, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "S1",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(lineColor("S1", "suburban", LineColors.BERLIN_CENTRE))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.setup_preview_stop), color = AppColors.Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                Text(text = stringResource(R.string.direction_arrow, "Wannsee"), color = AppColors.Muted, fontSize = 12.sp, maxLines = 1)
            }
            MinutesPill(minutes = 3L, live = true)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(AppColors.Chip),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(AppColors.Blue),
            )
        }
    }
}

// The example changes of the transfer step: how tight each is, and its minutes.
private val EXAMPLE_CHANGES = listOf(
    Tightness.Comfortable to 8L,
    Tightness.Tight to 5L,
    Tightness.Risky to 3L,
    Tightness.VeryRisky to 1L,
)

/**
 * Example changes for the transfer level: those the level offers stand in their colours,
 * the ones it leaves out fade back and are struck through, each with a spring while the
 * level is dragged.
 */
@Composable
private fun TransferExamples(level: TransferComfort) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.Card)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.setup_transfers_examples),
            color = AppColors.Muted,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
        )
        EXAMPLE_CHANGES.forEach { (tightness, minutes) ->
            val shown = setupExampleShown(tightness, level)
            val presence by animateFloatAsState(
                if (shown) 1f else 0f,
                spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
                label = "examplePresence",
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = 0.4f + 0.6f * presence.coerceIn(0f, 1f)
                        val scale = 0.96f + 0.04f * presence
                        scaleX = scale
                        scaleY = scale
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(tightnessName(tightness)),
                    color = tightnessInk(tightness),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(tightnessContainer(tightness))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
                Text(
                    text = stringResource(R.string.change_minutes, minutes),
                    color = AppColors.Ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    textDecoration = if (shown) null else TextDecoration.LineThrough,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(if (shown) R.string.setup_example_shown else R.string.setup_example_hidden),
                    color = if (shown) AppColors.CalmInk else AppColors.Muted,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

/** What the people a live link goes to read on top of it: the name as it is typed, with the live dot. */
@Composable
internal fun LiveNamePreview(name: String) {
    val destination = stringResource(R.string.setup_sample_destination)
    val trimmed = name.trim()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.Card)
            .animateContentSize(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LiveDot(pulse = true)
        Text(
            text = if (trimmed.isEmpty()) {
                stringResource(R.string.setup_live_name_preview_none, destination)
            } else {
                stringResource(R.string.setup_live_name_preview, trimmed, destination)
            },
            color = AppColors.Ink,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            lineHeight = 19.sp,
        )
    }
}

/** The colours of a map style in the little sketch: ground, water, park, roads. */
internal fun mapStyleColors(style: MapStyleChoice): List<Color> = when (style) {
    MapStyleChoice.Light -> listOf(Color(0xFFF2F3F5), Color(0xFFCBD5E0), Color(0xFFDDE5DA), Color.White)
    MapStyleChoice.Colorful -> listOf(Color(0xFFF4F1EA), Color(0xFF8EC5FF), Color(0xFFA8DB94), Color.White)
    MapStyleChoice.Dark -> listOf(Color(0xFF1E2128), Color(0xFF2B3A4F), Color(0xFF26372B), Color(0xFF3A3F4A))
    MapStyleChoice.Auto -> listOf(Color(0xFFF2F3F5), Color(0xFFCBD5E0), Color(0xFFDDE5DA), Color.White)
}

/**
 * A tiny map in [style]: ground, a river, a park, two roads and a line of the app that draws
 * itself ([reveal]) when the style is picked. Automatic is light on the left, dark on the right.
 */
internal fun DrawScope.mapStyleSketch(style: MapStyleChoice, reveal: Float) {
    fun scene(colors: List<Color>) {
        drawRect(colors[0])
        drawRect(colors[1], topLeft = Offset(0f, size.height * 0.68f), size = Size(size.width, size.height * 0.14f))
        drawCircle(colors[2], radius = size.height * 0.26f, center = Offset(size.width * 0.78f, size.height * 0.3f))
        drawLine(colors[3], Offset(0f, size.height * 0.4f), Offset(size.width, size.height * 0.5f), strokeWidth = 3.dp.toPx())
        drawLine(colors[3], Offset(size.width * 0.35f, 0f), Offset(size.width * 0.45f, size.height), strokeWidth = 3.dp.toPx())
    }
    if (style == MapStyleChoice.Auto) {
        clipRect(right = size.width / 2) { scene(mapStyleColors(MapStyleChoice.Light)) }
        clipRect(left = size.width / 2) { scene(mapStyleColors(MapStyleChoice.Dark)) }
    } else {
        scene(mapStyleColors(style))
    }
    val start = Offset(size.width * 0.08f, size.height * 0.85f)
    val end = Offset(size.width * 0.9f, size.height * 0.15f)
    val tip = start + (end - start) * reveal.coerceIn(0f, 1f)
    drawLine(Color(0xFF1B4FD8), start, tip, strokeWidth = 3.5.dp.toPx(), cap = StrokeCap.Round)
    drawCircle(Color.White, radius = 4.dp.toPx(), center = tip)
    drawCircle(Color(0xFF1B4FD8), radius = 4.dp.toPx(), center = tip, style = Stroke(2.dp.toPx()))
}

