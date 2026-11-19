package io.github.jnbp.nextstop.ui.companion

import io.github.jnbp.nextstop.ui.delayedTimeText
import io.github.jnbp.nextstop.data.alightingTime
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.ui.minutesBetween
import io.github.jnbp.nextstop.ui.MapFold
import io.github.jnbp.nextstop.data.wayRowsShown
import io.github.jnbp.nextstop.data.wayRowsMore
import io.github.jnbp.nextstop.data.wayChain
import io.github.jnbp.nextstop.data.WayChainStep
import io.github.jnbp.nextstop.data.Leg
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.RoutingSource
import io.github.jnbp.nextstop.data.TransitousSource
import io.github.jnbp.nextstop.data.WayCompare
import io.github.jnbp.nextstop.data.otherWays
import io.github.jnbp.nextstop.data.ownServerUrl
import io.github.jnbp.nextstop.data.spliceJourney
import io.github.jnbp.nextstop.data.wayKey
import io.github.jnbp.nextstop.ui.CheckMark
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.role
import io.github.jnbp.nextstop.data.WaysChipTap
import io.github.jnbp.nextstop.data.WaysSearch
import io.github.jnbp.nextstop.data.waysChipOn
import io.github.jnbp.nextstop.data.waysChipTap
import io.github.jnbp.nextstop.ui.Glyph
import io.github.jnbp.nextstop.ui.GlyphIcon
import io.github.jnbp.nextstop.ui.JourneyPreviewDialog
import io.github.jnbp.nextstop.ui.LoadingRow
import io.github.jnbp.nextstop.ui.TripChange
import io.github.jnbp.nextstop.ui.TripState
import io.github.jnbp.nextstop.ui.badgeLine
import io.github.jnbp.nextstop.ui.choiceDeltaLabel
import io.github.jnbp.nextstop.ui.clock
import io.github.jnbp.nextstop.ui.lineColor
import io.github.jnbp.nextstop.ui.lineInk
import io.github.jnbp.nextstop.ui.theme.AppColors
import java.time.OffsetDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * #164: "Andere Wege" at a change (since #243 a chip beside "Anschluss", see ChangeChips.kt; the
 * detail page too): a search, only on a tap, from
 * the change stop (from the arrival and a short change on) to the destination, for completely
 * different ways (direct trains, other lines). Each is shown against the rest of the plan with
 * "Vorschau" (the detail of the whole journey with it) and "Übernehmen" (it replaces the plan
 * from this change on). Kept per change, so it is searched once.
 */

/** The searches of other ways, by journey and change; snapshot state, read by the timeline. */
internal object OtherWaysSearch {
    sealed interface State {
        data object Loading : State
        data class Done(val ways: List<Pair<Journey, WayCompare>>) : State
        data object Failed : State
    }

    private val states = mutableStateMapOf<String, State>()

    // What was found and folded away by its link (#193); open again on the next tap or search.
    private val folded = mutableStateMapOf<String, Boolean>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private fun key(journey: Journey, keepLegs: Int) = "${journey.id}|$keepLegs"

    /** What the search at the change keeping [keepLegs] legs of [journey] found so far; null before any. */
    fun state(journey: Journey, keepLegs: Int): State? = states[key(journey, keepLegs)]

    /** Whether what was found at the change keeping [keepLegs] legs of [journey] shows (#193). */
    fun isOpen(journey: Journey, keepLegs: Int): Boolean = folded[key(journey, keepLegs)] != true

    /** Folds ([open] false) or unfolds what was found at the change keeping [keepLegs] legs of [journey] (#193). */
    fun setOpen(journey: Journey, keepLegs: Int, open: Boolean) {
        if (open) folded.remove(key(journey, keepLegs)) else folded[key(journey, keepLegs)] = true
    }

    /** Searches other ways from [change] of [journey], unless that is done or under way. */
    fun search(context: Context, journey: Journey, change: TripChange) {
        val key = key(journey, change.keepLegs)
        folded.remove(key)
        val now = states[key]
        if (now is State.Loading || now is State.Done) return
        val to = journey.legs.last().toPlace?.copy(distanceMeters = null)
        if (to == null) {
            states[key] = State.Failed
            return
        }
        states[key] = State.Loading
        val app = context.applicationContext
        val rest = Journey(journey.legs.drop(change.keepLegs))
        scope.launch {
            states[key] = try {
                val prefs = AppPrefs(app)
                val source = RoutingSource(
                    choice = { prefs.sourceChoice },
                    userAgent = TransitousSource.userAgent(app),
                    bvgUrl = { ownServerUrl(prefs.ownServer) },
                )
                val page = withContext(Dispatchers.IO) {
                    source.journeys(
                        from = change.stop,
                        to = to,
                        options = prefs.searchOptionsInUse,
                        results = OTHER_WAYS_RESULTS,
                        departure = change.arriving.arrival.plusMinutes(OTHER_WAYS_MIN_CHANGE_MINUTES),
                        arrival = null,
                    )
                }
                State.Done(otherWays(rest, page.journeys))
            } catch (e: CancellationException) {
                states.remove(key)
                throw e
            } catch (e: Exception) {
                State.Failed
            }
        }
    }

    /** Forgets what was found at the change of [journey] (another way was taken). */
    fun forget(journey: Journey, keepLegs: Int) {
        states.remove(key(journey, keepLegs))
        folded.remove(key(journey, keepLegs))
    }
}

// The search asks for this many ways; the best few are shown (see otherWays).
private const val OTHER_WAYS_RESULTS = 8

// The search starts this long after the arrival at the change: the least time to get anywhere.
private const val OTHER_WAYS_MIN_CHANGE_MINUTES = 2L

/**
 * What "Andere Wege" has at a change (#164; the chip of #243): where the search stands
 * ([search]), whether what it found shows ([open]), what a tap on the chip does ([onChipTap]),
 * and the ways found as tiles for the shared row under the change ([tiles]).
 */
internal class OtherWaysView(
    val search: WaysSearch,
    val open: Boolean,
    val onChipTap: () -> Unit,
    val onRetry: () -> Unit,
    internal val ways: List<Pair<Journey, WayCompare>>,
    internal val picked: String?,
    internal val onPreview: (Journey) -> Unit,
    internal val onTake: (Journey) -> Unit,
) {
    /** Whether the chip "Andere Wege" is on (see waysChipOn). */
    val on: Boolean get() = waysChipOn(search, open)

    /** Whether the search found nothing. */
    val none: Boolean get() = search == WaysSearch.Done && ways.isEmpty()
}

/**
 * "Andere Wege" at the change [change] of [journey]: holds the preview of a way (#164, the
 * detail of the whole journey with it, "Übernehmen" spelled out there) and which preview was
 * opened last (#193), and hands [content] the [OtherWaysView]. [onTake] takes a way; null for
 * the followed trip (it replaces the plan from this change on, in TripState).
 */
@Composable
internal fun OtherWaysPart(
    journey: Journey,
    change: TripChange,
    now: OffsetDateTime,
    onTake: ((Journey) -> Unit)?,
    content: @Composable (OtherWaysView) -> Unit,
) {
    val context = LocalContext.current
    val state = OtherWaysSearch.state(journey, change.keepLegs)
    val open = OtherWaysSearch.isOpen(journey, change.keepLegs)
    val search = when (state) {
        null -> WaysSearch.None
        OtherWaysSearch.State.Loading -> WaysSearch.Loading
        is OtherWaysSearch.State.Done -> WaysSearch.Done
        OtherWaysSearch.State.Failed -> WaysSearch.Failed
    }
    var preview by remember(journey.id, change.keepLegs) { mutableStateOf<Journey?>(null) }
    // The way whose preview was opened last: framed, with its own small take button (#193).
    var picked by remember(journey.id, change.keepLegs) { mutableStateOf<String?>(null) }
    val take: (Journey) -> Unit = { option ->
        if (onTake != null) {
            onTake(option)
        } else {
            // From the journey as TripState has it, so nothing of the live data is lost.
            val followed = TripState.journey.value
            // The same journey as shown (the companion shows its plausible times, same legs).
            if (followed != null && followed.legs.size == journey.legs.size) {
                TripState.journey.value = spliceJourney(followed, change.keepLegs, option)
            }
        }
        OtherWaysSearch.forget(journey, change.keepLegs)
        preview = null
    }
    preview?.let { option ->
        JourneyPreviewDialog(
            caption = stringResource(R.string.r6c_other_ways),
            title = stringResource(R.string.arrival_at, option.arrival.clock()),
            journey = spliceJourney(journey, change.keepLegs, option),
            now = now,
            onTake = { take(option) },
            onIgnore = null,
            onClose = { preview = null },
        )
    }
    content(
        OtherWaysView(
            search = search,
            open = open,
            onChipTap = {
                when (waysChipTap(search, open)) {
                    WaysChipTap.Search -> OtherWaysSearch.search(context, journey, change)
                    WaysChipTap.Fold -> OtherWaysSearch.setOpen(journey, change.keepLegs, false)
                    WaysChipTap.Unfold -> OtherWaysSearch.setOpen(journey, change.keepLegs, true)
                }
            },
            onRetry = { OtherWaysSearch.search(context, journey, change) },
            ways = (state as? OtherWaysSearch.State.Done)?.ways.orEmpty(),
            picked = picked,
            onPreview = { option ->
                picked = wayKey(option)
                preview = option
            },
            onTake = take,
        ),
    )
}

/**
 * The ways found as a list of full-width rows (Variante 2, Joel 19.11.), below the row of the
 * connection tiles: [WAY_ROWS_FOLDED] at first, the rest behind "Weitere anzeigen" (unfolding
 * softly, at once with animations off). Nothing while none were found.
 */
@Composable
internal fun OtherWaysList(view: OtherWaysView, modifier: Modifier = Modifier) {
    val ways = view.ways
    if (ways.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    val first = wayRowsShown(ways.size, expanded = false)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ways.take(first).forEach { (option, compare) ->
            key(wayKey(option)) { OtherWayRow(option, compare, view) }
        }
        MapFold(open = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ways.drop(first).forEach { (option, compare) ->
                    key(wayKey(option)) { OtherWayRow(option, compare, view) }
                }
            }
        }
        MapFold(open = wayRowsMore(ways.size, expanded)) {
            Text(
                text = stringResource(R.string.r8s_ways_more),
                color = AppColors.Blue,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button) { expanded = true }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * One way found as a full-width row: its arrival large ("an 04:43"), the delta to the plan, the
 * whole chain of its lines (badges with arrows between, long walks as a small walking figure),
 * and at the right a round take button with a check mark (read out "Übernehmen"). A tap on the
 * row opens the preview, where "Übernehmen" is spelled out; the row looked at last is framed.
 */
@Composable
private fun OtherWayRow(option: Journey, compare: WayCompare, view: OtherWaysView) {
    val picked = wayKey(option) == view.picked
    val delta = choiceDeltaLabel(compare.arrivalMinutes)
    val sooner = compare.arrivalMinutes < 0
    val changes = when {
        compare.changes < 0 -> pluralStringResource(R.plurals.r6c_way_fewer, -compare.changes, -compare.changes)
        compare.changes > 0 -> pluralStringResource(R.plurals.r6c_way_more, compare.changes, compare.changes)
        else -> null
    }
    val time = when {
        compare.arrivalMinutes < 0 -> stringResource(R.string.r6c_way_earlier, -compare.arrivalMinutes)
        compare.arrivalMinutes > 0 -> stringResource(R.string.r6c_way_later, compare.arrivalMinutes)
        else -> stringResource(R.string.r6c_way_same)
    }
    val legs = option.legs
    val chain = remember(option) {
        wayChain(legs.map { it.walking }, legs.map { minutesBetween(it.departure, it.arrival) })
    }
    val lines = legs.filter { !it.walking }.joinToString(" → ") { badgeLine(it.line) ?: "?" }
    val arrival = stringResource(R.string.arrival_at, option.arrival.clock())
    val said = listOfNotNull(arrival, time, lines, changes).joinToString(", ")
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.Card)
            .then(if (picked) Modifier.border(3.dp, AppColors.Blue, shape) else Modifier)
            .semantics(mergeDescendants = true) {
                selected = picked
                contentDescription = said
            }
            .clickable(onClickLabel = stringResource(R.string.r6c_faster_preview)) { view.onPreview(option) }
            .padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // The arrival as the delay setting says (#227).
                Text(
                    text = delayedTimeText(R.string.arrival_at, alightingTime(legs.last()), AppColors.Ink),
                    color = AppColors.Ink,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    lineHeight = 20.sp,
                    maxLines = 1,
                )
                Text(
                    text = delta,
                    color = if (sooner) AppColors.Early else AppColors.Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppColors.Chip)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
                Text(
                    text = changes ?: time,
                    color = AppColors.Muted,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            WayChainRow(chain, legs)
        }
        TakeCheckButton { view.onTake(option) }
    }
}

/** The chain of lines of a way: small line badges with arrows between, long walks as a walking figure and minutes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WayChainRow(chain: List<WayChainStep>, legs: List<Leg>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        chain.forEachIndexed { index, step ->
            if (index > 0) Text(text = "→", color = AppColors.Muted, fontSize = 12.sp, maxLines = 1)
            when (step) {
                is WayChainStep.Ride -> {
                    val ride = legs[step.leg]
                    val badge = lineColor(ride)
                    Text(
                        text = badgeLine(ride.line) ?: "?",
                        color = lineInk(ride, badge),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        lineHeight = 14.sp,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(badge)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
                is WayChainStep.Walk -> Row(verticalAlignment = Alignment.CenterVertically) {
                    GlyphIcon(Glyph.Walk, color = AppColors.Muted, size = 12.dp)
                    Text(text = "${step.minutes}′", color = AppColors.Muted, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
    }
}

/**
 * The line of "Andere Wege" under the shared row while it is on: searching, nothing found, or
 * the search failed (a tap tries again); nothing once ways were found (they are tiles).
 */
@Composable
internal fun OtherWaysNote(view: OtherWaysView, modifier: Modifier = Modifier) {
    when {
        view.search == WaysSearch.Loading -> LoadingRow(text = stringResource(R.string.r6c_other_ways_loading), modifier = modifier, compact = true)
        view.none -> Text(text = stringResource(R.string.r6c_other_ways_none), color = AppColors.Muted, fontSize = 13.sp, modifier = modifier)
        view.search == WaysSearch.Failed -> Text(
            text = stringResource(R.string.r6c_other_ways_failed),
            color = AppColors.Blue,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClick = view.onRetry)
                .padding(vertical = 6.dp),
        )
    }
}

/** The small round take button of a tile: a white check mark on blue, read out as "Übernehmen". */
@Composable
private fun TakeCheckButton(onTake: () -> Unit) {
    val label = stringResource(R.string.faster_take_action)
    Box(
        modifier = Modifier
            .padding(start = 4.dp)
            .size(TAKE_BUTTON)
            .clip(CircleShape)
            .background(AppColors.Blue)
            .semantics {
                contentDescription = label
                role = Role.Button
            }
            .clickable(onClick = onTake),
        contentAlignment = Alignment.Center,
    ) {
        CheckMark(color = AppColors.OnBlue, modifier = Modifier.size(16.dp))
    }
}

// The round take button of a tile (#243).
private val TAKE_BUTTON = 30.dp
