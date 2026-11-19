package io.github.jnbp.nextstop.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.BuildConfig
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.Accessibility
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.data.PRODUCT_KEYS
import io.github.jnbp.nextstop.data.ProjectLinks
import io.github.jnbp.nextstop.data.SearchOptions
import io.github.jnbp.nextstop.ui.theme.AppColors
import io.github.jnbp.nextstop.ui.theme.Appearance
import io.github.jnbp.nextstop.ui.theme.ColorChoice
import io.github.jnbp.nextstop.ui.theme.DarkChoice
import io.github.jnbp.nextstop.ui.theme.MapStyleChoice
import io.github.jnbp.nextstop.wear.WatchSettingKeys
import io.github.jnbp.nextstop.wear.WatchSettings
import io.github.jnbp.nextstop.wear.WatchState
import io.github.jnbp.nextstop.wear.WatchStatus
import io.github.jnbp.nextstop.wear.settingFlag
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/*
 * The settings page in categories that fold (see SettingsScreen): each category is one glass
 * header with its title, a line that sums up its state ("Colourful · Disruptions on the map:
 * on") and a chevron; a tap unfolds its groups below it with the motion of SettingReveal, and
 * with animations off they are simply there. All categories start folded, so the page fits on
 * a screen; which ones are open survives a rotation (rememberSaveable), not a new start of the
 * app. Only a way into the settings that aims at one setting (the bell's "Set times", which
 * leads to the disruption watch) opens its category and scrolls to it ([ScrollTarget]).
 * The open categories are kept as their ids joined by commas: a plain String needs no Saver.
 *
 * The header of an open category sticks to the top of the scrolled page while its content
 * scrolls by ([StickyCategories], [pinnedCategory]): a copy of it ([PinnedCategoryHeader]) is
 * drawn over the page at the top edge, and the header in the page is hidden meanwhile (and
 * silent for TalkBack), so it is never there twice. As the end of the content comes up, the
 * copy is pushed up with it, and the next open category takes over once its own header reaches
 * the top. The page stays a scrolled Column, not a LazyColumn with sticky headers: the way into
 * one setting ([ScrollTarget], [scrollWhenSettled]) and the unfolding with SettingReveal work on
 * the whole laid-out page, which a lazy list would not keep. A tap on the pinned copy folds
 * its category and scrolls so that its header stays where the copy was, then (if the copy was
 * pushed up) to the header's own place.
 */

/**
 * The title of the category drawn here: a [SettingsGroup] with the same label leaves its label
 * out, since the header above says it; groups with labels of their own keep them as subheadings.
 */
internal val LocalSettingsCategory = staticCompositionLocalOf<String?> { null }

/**
 * True where a [SettingsGroup] draws its rows without a glass frame of its own: in an open
 * category, under its glass header, and on the page "Advanced". The rows are then the one
 * surface, so a group does not look like a card in a card (#133).
 */
internal val LocalFlatSettingsGroups = staticCompositionLocalOf { false }

/** The ids of the categories of the settings page; kept in the saved state, so they stay as they are. */
internal object SettingsCategoryId {
    const val BATTERY = "battery"
    const val APPEARANCE = "appearance"
    const val MAP = "map"
    const val CONNECTIONS = "connections"
    const val ON_THE_WAY = "on_the_way"
    const val START = "start"
    const val WATCH = "watch"
    const val ACCESSIBILITY = "accessibility"
    const val LIVE_LINK = "live_link"
    const val ABOUT = "about"
}

// The separator of the ids in the saved String of open categories; no id contains it.
private const val CATEGORY_SEPARATOR = ','

/** True when the category [id] is among the [open] ones (ids joined by commas). */
internal fun isCategoryOpen(open: String, id: String): Boolean = id in open.split(CATEGORY_SEPARATOR)

/** The [open] categories with [id] opened when it was closed, and closed when it was open. */
internal fun toggleCategory(open: String, id: String): String {
    val ids = open.split(CATEGORY_SEPARATOR).filter { it.isNotEmpty() }
    return (if (id in ids) ids - id else ids + id).joinToString(CATEGORY_SEPARATOR.toString())
}

/** The [open] categories with [id] open, whether it was or not. */
internal fun withCategoryOpen(open: String, id: String): String =
    if (isCategoryOpen(open, id)) open else toggleCategory(open, id)

/**
 * One category of the settings page: a header with [title], the [summary] of its state and a
 * chevron, and below it [content] while it is open (one of the [open] ids is [id]). A tap on
 * the header gives [onOpen] the new set of open ids. The summary cross-fades when it changes.
 * With [sticky], the header sticks to the top of the page while the content scrolls by.
 */
@Composable
internal fun SettingsCategory(
    id: String,
    title: String,
    summary: String,
    open: String,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    sticky: StickyCategories? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val unfolded = isCategoryOpen(open, id)
    // The copy over the page needs the title and the summary of whichever category it shows.
    if (sticky != null) SideEffect { sticky.label(id, title, summary) }
    // While its copy is pinned over the page, this header is hidden and not read out twice.
    val pinnedHere = sticky != null && sticky.pinnedId == id
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (sticky != null) Modifier.onGloballyPositioned { sticky.placeCategory(id, it) } else Modifier),
    ) {
        CategoryHeader(
            title = title,
            summary = summary,
            unfolded = unfolded,
            onClick = { onOpen(toggleCategory(open, id)) },
            modifier = Modifier
                .then(if (sticky != null) Modifier.onGloballyPositioned { sticky.placeHeader(id, it) } else Modifier)
                .then(if (pinnedHere) Modifier.alpha(0f).clearAndSetSemantics { } else Modifier),
        )
        SettingReveal(visible = unfolded) {
            CompositionLocalProvider(LocalSettingsCategory provides title, LocalFlatSettingsGroups provides true) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content,
                )
            }
        }
    }
}

/**
 * The glass header of a category: [title], the [summary] of its state (cross-faded when it
 * changes) and a chevron that points up while [unfolded]. Drawn in the page and, for the
 * category whose content is passing the top, once more pinned over it.
 */
@Composable
private fun CategoryHeader(
    title: String,
    summary: String,
    unfolded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val action = stringResource(if (unfolded) R.string.settings_category_hide else R.string.settings_category_show)
    GlassSurface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(CATEGORY_CORNER)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = action, role = Role.Button, onClick = onClick)
                .defaultMinSize(minHeight = CATEGORY_MIN_HEIGHT)
                .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                FadeSwap(target = summary) { shown ->
                    Text(
                        text = shown,
                        color = AppColors.Muted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Chevron(open = unfolded)
        }
    }
}

// The roundness and the least height of the header of a category.
private val CATEGORY_CORNER = 24.dp
private val CATEGORY_MIN_HEIGHT = 64.dp

/** Where a category lies in the scrolled content, in pixels from the top of the content. */
internal data class CategoryBounds(val top: Int, val headerHeight: Int, val bottom: Int)

/** The category whose header is pinned at the top, and how far it is pushed up (0 or less, in pixels). */
internal data class PinnedCategory(val id: String, val offset: Int)

/**
 * Which header is pinned at the top of the page scrolled by [scrolled] pixels, given where the
 * categories lie ([bounds]) and which are [open]: the open category whose header has started to
 * go above the top edge while its content still reaches below it. Its copy sits at the top edge
 * until the end of the content comes within a header's height of it, and then moves up with
 * that end ([PinnedCategory.offset]), so the next header can take its place. Folded categories,
 * open ones that have not unfolded yet (no content below the header) and ones not measured yet
 * (no header height) are never pinned; with none such, null.
 */
internal fun pinnedCategory(bounds: Map<String, CategoryBounds>, open: Set<String>, scrolled: Int): PinnedCategory? {
    for ((id, at) in bounds) {
        if (id !in open || at.headerHeight <= 0) continue
        // Only the header and nothing below it: nothing to scroll by under a pinned copy.
        if (at.bottom <= at.top + at.headerHeight) continue
        if (at.top < scrolled && scrolled < at.bottom) {
            return PinnedCategory(id, minOf(0, at.bottom - scrolled - at.headerHeight))
        }
    }
    return null
}

/** The title and the summary of a category, for its pinned copy. */
internal data class CategoryLabel(val title: String, val summary: String)

/**
 * The sticky headers of the settings page scrolled by [scroll]; [open] gives the open
 * categories (ids joined by commas) and is read as state. Each [SettingsCategory] reports where
 * it lies ([placeCategory], [placeHeader]) relative to [content], the layout of the scrolled
 * column, and what its header says ([label]); [PinnedCategoryHeader] draws the pinned copy.
 * The positions are kept only when they change, so scrolling, which moves nothing within the
 * content, writes nothing.
 */
@Stable
internal class StickyCategories(val scroll: ScrollState, private val open: () -> String) {
    /** The layout of the scrolled column; a plain field, read when a category reports. */
    var content: LayoutCoordinates? = null

    private val bounds = mutableStateMapOf<String, CategoryBounds>()
    private val labels = mutableStateMapOf<String, CategoryLabel>()
    private val openIds: Set<String> by derivedStateOf {
        open().split(CATEGORY_SEPARATOR).filter { it.isNotEmpty() }.toSet()
    }

    /** The pinned header right now; read while placing, it changes with every scrolled pixel. */
    fun pinned(): PinnedCategory? = pinnedCategory(bounds, openIds, scroll.value)

    /** The id of the pinned header; changes only when another one (or none) is pinned. */
    val pinnedId: String? by derivedStateOf { pinned()?.id }

    /** Where the category [id] lies, from the layout of its whole column. */
    fun placeCategory(id: String, category: LayoutCoordinates) {
        val column = content ?: return
        if (!column.isAttached || !category.isAttached) return
        val top = column.localPositionOf(category, Offset.Zero).y.roundToInt()
        val bottom = top + category.size.height
        val old = bounds[id]
        if (old != null && old.top == top && old.bottom == bottom) return
        bounds[id] = (old ?: CategoryBounds(top = top, headerHeight = 0, bottom = bottom)).copy(top = top, bottom = bottom)
    }

    /** How high the header of the category [id] is. */
    fun placeHeader(id: String, header: LayoutCoordinates) {
        val height = header.size.height
        val old = bounds[id]
        if (old != null && old.headerHeight == height) return
        // Before its column reports, the header lies at the top of the content; the column corrects it.
        bounds[id] = (old ?: CategoryBounds(top = 0, headerHeight = height, bottom = 0)).copy(headerHeight = height)
    }

    /** How high the header of the category [id] is, in pixels; 0 before it is measured. */
    fun headerHeightOf(id: String): Int = bounds[id]?.headerHeight ?: 0

    /** What the header of the category [id] says. */
    fun label(id: String, title: String, summary: String) {
        val now = CategoryLabel(title, summary)
        if (labels[id] != now) labels[id] = now
    }

    /** What the header of the category [id] says, once it has been drawn. */
    fun labelOf(id: String): CategoryLabel? = labels[id]

    /**
     * Folds the category [id] from its pinned header: first scrolls so that its header in the
     * page lies exactly where the pinned copy is (no jump), then hands [onOpen] the open
     * categories without it. If the copy was pushed up, the header then scrolls fully into view
     * (at once with animations off, [animate] false).
     */
    suspend fun collapse(id: String, animate: Boolean, onOpen: (String) -> Unit) {
        val top = bounds[id]?.top
        val shift = pinned()?.takeIf { it.id == id }?.offset ?: 0
        if (top != null) scroll.scrollTo(top - shift)
        val now = open()
        if (isCategoryOpen(now, id)) onOpen(toggleCategory(now, id))
        if (top != null && shift < 0) {
            if (animate) scroll.animateScrollTo(top) else scroll.scrollTo(top)
        }
    }
}

/**
 * The copy of the pinned header (see [StickyCategories]), to be laid over the top of the
 * scrolled page; it moves up as its content ends. A tap folds its category ([onOpen] gets the
 * new open categories), a drag scrolls the page. Nothing while no header is pinned.
 */
@Composable
internal fun PinnedCategoryHeader(sticky: StickyCategories, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val animate = rememberAnimationsOn()
    val id = sticky.pinnedId ?: return
    val label = sticky.labelOf(id) ?: return
    // A new header pinned is a new copy: its summary must not cross-fade from the previous one's.
    key(id) {
        CategoryHeader(
            title = label.title,
            summary = label.summary,
            unfolded = true,
            onClick = { scope.launch { sticky.collapse(id, animate, onOpen) } },
            modifier = modifier
                // Read while placing: the push-up costs no recomposition.
                .offset { IntOffset(0, sticky.pinned()?.takeIf { it.id == id }?.offset ?: 0) }
                // A drag on the copy scrolls the page under it, as on the header in the page
                // (reversed like verticalScroll: a drag up moves the content up).
                .scrollable(sticky.scroll, Orientation.Vertical, reverseDirection = true),
        )
    }
}

/**
 * A place in the scrolled settings to scroll to: [content] is the layout of the scrolled column,
 * [target] the one of the group to show. Plain fields, not state: they are read when needed.
 */
internal class ScrollTarget {
    var content: LayoutCoordinates? = null
    var target: LayoutCoordinates? = null

    /** Where the target starts in the scrolled content, in pixels; null while either is not laid out. */
    fun top(): Int? {
        val column = content ?: return null
        val group = target ?: return null
        if (!column.isAttached || !group.isAttached) return null
        return column.localPositionOf(group, Offset.Zero).y.roundToInt()
    }
}

/**
 * Waits until the target of [where] stands still in the content and the height of the content
 * ([scroll]'s maxValue) does too, i.e. its category has unfolded, then scrolls to it, leaving
 * [clearance] pixels above it free (read then: the header pinned over the top, which would
 * cover it otherwise). Gives up waiting after [SETTLE_CHECKS] checks and scrolls to where it
 * is then (if anywhere).
 */
internal suspend fun scrollWhenSettled(where: ScrollTarget, scroll: ScrollState, clearance: () -> Int = { 0 }) {
    var last: Pair<Int, Int>? = null
    repeat(SETTLE_CHECKS) {
        delay(SETTLE_STEP_MILLIS)
        val top = where.top()
        val now = if (top == null) null else top to scroll.maxValue
        if (now != null && now == last) {
            scroll.animateScrollTo((now.first - clearance()).coerceAtLeast(0))
            return
        }
        last = now
    }
    last?.let { scroll.animateScrollTo((it.first - clearance()).coerceAtLeast(0)) }
}

// Every 50 ms for at most 2 s: an unfolding category settles in about half a second.
private const val SETTLE_STEP_MILLIS = 50L
private const val SETTLE_CHECKS = 40

// The parts of a summary are joined with this.
private const val SUMMARY_JOIN = " · "

/** "Title: on" or "Title: off" for the switch titled [title], in a summary. */
@Composable
private fun switchSummary(@StringRes title: Int, on: Boolean): String =
    stringResource(if (on) R.string.settings_sum_on else R.string.settings_sum_off, stringResource(title))

/** Appearance: the colour scheme and light or dark. */
@Composable
internal fun appearanceSummary(): String {
    val color = when (Appearance.colorChoice) {
        ColorChoice.NextStop -> R.string.color_next_stop
        ColorChoice.System -> R.string.color_system
        ColorChoice.Custom -> R.string.color_custom
    }
    val dark = when (Appearance.darkChoice) {
        DarkChoice.Light -> R.string.dark_light
        DarkChoice.Dark -> R.string.dark_dark
        DarkChoice.System -> R.string.dark_system
    }
    return stringResource(color) + SUMMARY_JOIN + stringResource(dark)
}

/** The map: its style and whether disruptions show on it. */
@Composable
internal fun mapSummary(): String {
    val style = when (Appearance.mapStyle) {
        MapStyleChoice.Light -> R.string.map_style_light
        MapStyleChoice.Colorful -> R.string.map_style_colorful
        MapStyleChoice.Dark -> R.string.map_style_dark
        MapStyleChoice.Auto -> R.string.map_style_auto
    }
    return stringResource(style) + SUMMARY_JOIN + switchSummary(R.string.map_notices_title, MapNoticeSetting.shown)
}

/** Connections: chart or list, and how many means of transport are searched. */
@Composable
internal fun connectionsSummary(options: SearchOptions, listView: Boolean): String {
    val view = stringResource(if (listView) R.string.view_list else R.string.view_chart)
    val products = stringResource(
        R.string.settings_sum_products,
        PRODUCT_KEYS.count { it in options.products },
        PRODUCT_KEYS.size,
    )
    return view + SUMMARY_JOIN + products
}

/** On the way: the boarding hints and the reminder before getting off (read anew on every recomposition). */
@Composable
internal fun onTheWaySummary(boardingHints: Boolean): String {
    val context = LocalContext.current
    return switchSummary(R.string.boarding_hints_title, boardingHints) + SUMMARY_JOIN +
        switchSummary(R.string.alight_reminders_title, AppPrefs(context).alightReminders)
}

/** The start page: its layout and the radius of the departures nearby. */
@Composable
internal fun startSummary(startClassic: Boolean, nearbyRadius: Int): String =
    stringResource(if (startClassic) R.string.start_layout_classic else R.string.start_layout_sheet) +
        SUMMARY_JOIN + stringResource(R.string.metres, nearbyRadius)

/** The watch: what its row says, connected or not, and its live departures and animations. */
@Composable
internal fun watchSummary(status: WatchStatus?): String {
    val settings by WatchSettings.values.collectAsState()
    return stringResource(
        when (status?.state) {
            null -> R.string.watch_checking
            WatchState.None -> R.string.watch_none
            WatchState.NoApp -> R.string.watch_no_app
            WatchState.Connected -> R.string.watch_connected
        },
    ) + SUMMARY_JOIN +
        switchSummary(R.string.watch_live_departures_title, settingFlag(settings, WatchSettingKeys.LIVE_DEPARTURES, default = true)) +
        SUMMARY_JOIN +
        switchSummary(R.string.watch_animations_title, settingFlag(settings, WatchSettingKeys.ANIMATIONS, default = true))
}

/** Accessibility: how step-free journeys must be, and "slower on the move". */
@Composable
internal fun accessibilitySummary(options: SearchOptions): String {
    val level = when (options.accessibility) {
        Accessibility.None -> R.string.accessibility_none
        Accessibility.Partial -> R.string.accessibility_partial
        Accessibility.Complete -> R.string.accessibility_complete
    }
    return stringResource(level) + SUMMARY_JOIN + switchSummary(R.string.slower_title, options.slower)
}

/** The live link: the name it shows, or that none is set (read anew on every recomposition). */
@Composable
internal fun liveLinkSummary(): String {
    val name = AppPrefs(LocalContext.current).liveName
    return name.ifBlank { stringResource(R.string.settings_sum_no_name) }
}

/** About: the version and the license. */
@Composable
internal fun aboutSummary(): String =
    stringResource(R.string.about_summary, BuildConfig.VERSION_NAME, ProjectLinks.LICENSE_ID)
