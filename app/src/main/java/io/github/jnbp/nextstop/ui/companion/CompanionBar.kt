package io.github.jnbp.nextstop.ui.companion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.ui.Chevron
import io.github.jnbp.nextstop.ui.GlassSurface
import io.github.jnbp.nextstop.ui.Glyph
import io.github.jnbp.nextstop.ui.GlyphIcon
import io.github.jnbp.nextstop.ui.RideKind
import io.github.jnbp.nextstop.ui.ShareTripButton
import io.github.jnbp.nextstop.ui.lateRideRes
import io.github.jnbp.nextstop.ui.missedRideRes
import io.github.jnbp.nextstop.ui.rideKindOf
import io.github.jnbp.nextstop.ui.wrongRideRes
import io.github.jnbp.nextstop.ui.theme.AppColors
import kotlinx.coroutines.delay

/*
 * #116: the bottom bar of the new companion views, one row that fits 360 dp:
 * "Plan B" (what to do when something goes wrong), "Ändern" (#214), share, the ⋯ of the quiet
 * switches, and at the right end a red ✕ that ends the trip (as Google Maps' exit). The bar on
 * top keeps only back, the destination, the arrival and the map button.
 */

/** What the bottom bar can do; a null action is not offered. */
internal class CompanionBarActions(
    val missedFrom: Stop?,
    val onMissed: (Stop) -> Unit,
    /** Opens the choice of connections at the change ahead; null without a change ahead. */
    val onMissedChange: (() -> Unit)?,
    /** A new search from where the traveller is ("Neu planen von hier"). */
    val onReplan: (() -> Unit)?,
    val onChangeDestination: (() -> Unit)?,
    /** "Anderen Anschluss wählen": the same choice as [onMissedChange]. */
    val onOtherConnection: (() -> Unit)?,
    /** "Andere Wege ab Umstieg" (#164): searches other ways from the change ahead; null without one. */
    val onOtherWays: (() -> Unit)? = null,
    val onShare: () -> Unit,
    val shortcutAlert: Boolean,
    val onShortcutAlert: ((Boolean) -> Unit)?,
    val everyMinute: Boolean,
    val onEveryMinute: ((Boolean) -> Unit)?,
    val onWake: (() -> Unit)?,
    val onEnd: () -> Unit,
    val arrived: Boolean,
    /** The product of the ride Plan B is about (the one ridden, else the next), so it names it (#212). */
    val rideProduct: String? = null,
)

/** The bottom bar (#116), on glass. */
@Composable
internal fun CompanionBottomBar(actions: CompanionBarActions, modifier: Modifier = Modifier) {
    GlassSurface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp)) {
        Box {
            Row(
                modifier = Modifier.padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val planB = planBItems(
                    missedRelevant = actions.missedFrom != null,
                    changeAhead = actions.onMissedChange != null,
                    canReplan = actions.onReplan != null,
                )
                val kind = rideKindOf(actions.rideProduct)
                if (planB.isNotEmpty()) {
                    val entries = planB.map { item ->
                        BarSheetEntry(
                            glyph = planBGlyph(item),
                            title = stringResource(planBLabel(item, kind)),
                            text = stringResource(planBText(item)),
                        ) {
                            when (item) {
                                PlanB.MissedTrain -> actions.missedFrom?.let(actions.onMissed)
                                PlanB.MissedChange -> actions.onMissedChange?.invoke()
                                PlanB.Late, PlanB.WrongTrain -> actions.onReplan?.invoke()
                            }
                        }
                    }
                    BarSheetButton(
                        label = stringResource(R.string.r6c_plan_b),
                        glyph = Glyph.Route,
                        title = stringResource(R.string.r6c_plan_b),
                        entries = entries,
                    )
                }
                val change = buildList {
                    actions.onReplan?.let { replan ->
                        add(BarSheetEntry(Glyph.Locate, stringResource(R.string.replan_here), stringResource(R.string.replan_here_hint), onClick = replan))
                    }
                    actions.onChangeDestination?.let { retarget ->
                        add(BarSheetEntry(Glyph.Flag, stringResource(R.string.retarget_change), stringResource(R.string.r7p_retarget_text), onClick = retarget))
                    }
                    actions.onOtherConnection?.let { other ->
                        add(BarSheetEntry(Glyph.Swap, stringResource(R.string.r6c_other_connection), stringResource(R.string.r7p_other_connection_text), onClick = other))
                    }
                    actions.onOtherWays?.let { ways ->
                        add(BarSheetEntry(Glyph.Route, stringResource(R.string.r6c_other_ways_change), stringResource(R.string.r7p_other_ways_text), onClick = ways))
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    BarSheetButton(
                        // #214: always the short "Ändern"; "Fahrt ändern" was cut to "Fahrt änd…" in the bar.
                        label = stringResource(R.string.r6c_change_short),
                        glyph = null,
                        title = stringResource(R.string.trip_change_title),
                        entries = change,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                ShareTripButton(onClick = actions.onShare, size = 44.dp, shape = RoundedCornerShape(22.dp))
                BarMoreButton(actions)
                EndButton(actions)
            }
        }
    }
}

/** The words of [item], naming the ride by its [kind] (#212): "Bus verpasst", "Meine Tram ist zu spät / fällt aus". */
private fun planBLabel(item: PlanB, kind: RideKind): Int = when (item) {
    PlanB.MissedTrain -> missedRideRes(kind)
    PlanB.MissedChange -> R.string.r6c_missed_change
    PlanB.Late -> lateRideRes(kind)
    PlanB.WrongTrain -> wrongRideRes(kind)
}

/** The line under an entry of Plan B (#217): what it does. */
private fun planBText(item: PlanB): Int = when (item) {
    PlanB.MissedTrain -> R.string.r7p_missed_text
    PlanB.MissedChange -> R.string.r7p_other_connection_text
    PlanB.Late -> R.string.replan_here_hint
    PlanB.WrongTrain -> R.string.r7p_wrong_text
}

private fun planBGlyph(item: PlanB): Glyph = when (item) {
    PlanB.MissedTrain -> Glyph.Search
    PlanB.MissedChange -> Glyph.Swap
    PlanB.Late -> Glyph.Recent
    PlanB.WrongTrain -> Glyph.Locate
}

/** A text button of the bar with a small chevron; it opens the sheet [title] with [entries] (#217). */
@Composable
private fun BarSheetButton(
    label: String,
    glyph: Glyph?,
    title: String,
    entries: List<BarSheetEntry>,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .clip22()
            .background(AppColors.Card)
            .clickable(role = Role.Button, enabled = entries.isNotEmpty()) { open = true }
            .defaultMinSize(minHeight = 44.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        if (glyph != null) GlyphIcon(glyph, color = AppColors.Ink, size = 16.dp)
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Chevron(open = open, color = AppColors.Muted)
    }
    if (open) BarSheet(title = title, entries = entries, onDismiss = { open = false })
}

/** The ⋯ of the bar: the shortcut alert and "every minute counts" as switches, and the wake-up alarm, in a sheet (#217). */
@Composable
private fun BarMoreButton(actions: CompanionBarActions) {
    var open by remember { mutableStateOf(false) }
    io.github.jnbp.nextstop.ui.RoundButton(
        glyph = Glyph.More,
        description = stringResource(R.string.companion_more),
        onClick = { open = true },
        solid = true,
        size = 44.dp,
    )
    if (!open) return
    val entries = buildList {
        actions.onShortcutAlert?.let { switch ->
            add(
                BarSheetEntry(
                    Glyph.Navigate,
                    stringResource(R.string.r6c_menu_shortcuts),
                    stringResource(R.string.r7p_shortcuts_text),
                    checked = actions.shortcutAlert,
                ) { switch(!actions.shortcutAlert) },
            )
        }
        actions.onEveryMinute?.let { switch ->
            add(
                BarSheetEntry(
                    Glyph.Bolt,
                    stringResource(R.string.every_minute),
                    stringResource(R.string.r7p_every_minute_text),
                    checked = actions.everyMinute,
                ) { switch(!actions.everyMinute) },
            )
        }
        actions.onWake?.let { wake ->
            add(BarSheetEntry(Glyph.Alarm, stringResource(R.string.r5c_menu_wake), stringResource(R.string.r7p_wake_text), onClick = wake))
        }
    }
    BarSheet(title = stringResource(R.string.companion_more), entries = entries, onDismiss = { open = false })
}

/**
 * The red ✕ at the right end: ends the trip. While the trip still runs, the first tap turns it
 * into "Beenden?" for a few seconds and only a second tap ends it, without a dialog; once arrived
 * one tap ends it.
 */
@Composable
private fun EndButton(actions: CompanionBarActions) {
    var asking by remember { mutableStateOf(false) }
    LaunchedEffect(asking) {
        if (asking) {
            delay(END_ASK_MILLIS)
            asking = false
        }
    }
    val ground by animateColorAsState(if (asking) AppColors.DelayRed else AppColors.DelayRed.copy(alpha = 0.14f), label = "endGround")
    val ink by animateColorAsState(if (asking) AppColors.OnDelayRed else AppColors.DelayRed, label = "endInk")
    val label = stringResource(R.string.trip_end_action)
    Box(
        modifier = Modifier
            .clip22()
            .background(ground)
            .clickable(onClickLabel = label, role = Role.Button) {
                if (asking || !endAsksFirst(actions.arrived)) {
                    asking = false
                    actions.onEnd()
                } else {
                    asking = true
                }
            }
            .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(targetState = asking, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "endAsk") { ask ->
            if (ask) {
                Text(text = stringResource(R.string.r6c_end_ask), color = ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
            } else {
                GlyphIcon(Glyph.Close, color = ink, size = 18.dp, modifier = Modifier.semanticsLabel(label))
            }
        }
    }
}

private const val END_ASK_MILLIS = 4_000L

private fun Modifier.clip22(): Modifier = clip(RoundedCornerShape(22.dp))

private fun Modifier.semanticsLabel(label: String): Modifier = semantics { contentDescription = label }
