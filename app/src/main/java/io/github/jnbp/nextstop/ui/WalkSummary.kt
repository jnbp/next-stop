package io.github.jnbp.nextstop.ui

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.FootPaths
import io.github.jnbp.nextstop.data.Leg
import io.github.jnbp.nextstop.data.LiftKeys
import io.github.jnbp.nextstop.data.LiftStatus
import io.github.jnbp.nextstop.data.PassageKind
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.WalkDetails
import io.github.jnbp.nextstop.data.WalkTargetKind
import io.github.jnbp.nextstop.data.walkDetails
import io.github.jnbp.nextstop.ui.theme.AppColors

/*
 * The line under the small map of a walk: what the way passes and where it leads, from the
 * OSM data along it (see walkDetails): "Ausgang Europaplatz · 2× Rolltreppe runter · zu Pos. 4 (4 min)".
 */

/**
 * The summary of a walk with [details]: the exits and entrances, lifts and escalators it
 * passes in their order (a lift out of order said so, by [brokenLifts]; equal ones in a row
 * counted, "2× Rolltreppe runter"), then where it leads: the stop position ([targetPos]) or
 * else [targetName], the last part with the [minutes] on foot. Null where nothing is known to say.
 */
fun walkSummaryText(
    context: Context,
    details: WalkDetails,
    targetPos: String?,
    minutes: Long?,
    brokenLifts: List<Pair<Double, Double>> = emptyList(),
    targetName: String? = null,
): String? {
    val items = ArrayList<String>()
    for (passage in details.passages) {
        items.add(
            when (passage.kind) {
                PassageKind.Entrance -> {
                    // OSM names often say "Eingang Europaplatz" themselves: the word is ours, by the direction.
                    val label = passage.label?.let { doorName(it) }?.takeIf { it.isNotBlank() }
                    if (passage.leaving) {
                        if (label != null) context.getString(R.string.walk_sum_exit, label) else context.getString(R.string.walk_sum_exit_plain)
                    } else {
                        if (label != null) context.getString(R.string.map_entrance_named, label) else context.getString(R.string.walk_minimap_entrance)
                    }
                }
                PassageKind.Lift -> when {
                    brokenLifts.any { near(it, passage.position) } -> context.getString(R.string.walk_sum_lift_broken)
                    passage.climb > 0 -> context.getString(R.string.walk_sum_lift_up)
                    passage.climb < 0 -> context.getString(R.string.walk_sum_lift_down)
                    else -> context.getString(R.string.map_elevator)
                }
                PassageKind.Escalator -> when {
                    passage.climb > 0 -> context.getString(R.string.walk_sum_escalator_up)
                    passage.climb < 0 -> context.getString(R.string.walk_sum_escalator_down)
                    else -> context.getString(R.string.map_escalator)
                }
                PassageKind.Stairs -> when {
                    passage.climb > 0 -> context.getString(R.string.walk_sum_stairs_up)
                    passage.climb < 0 -> context.getString(R.string.walk_sum_stairs_down)
                    else -> context.getString(R.string.walk_sum_stairs)
                }
            },
        )
    }
    val parts = ArrayList<String>()
    for ((item, count) in collapseRuns(items)) {
        parts.add(if (count > 1) context.getString(R.string.walk_sum_times, count, item) else item)
    }
    // Always where it leads, last.
    val pos = targetPos?.takeIf { it.isNotBlank() } ?: details.target?.pos
    when {
        pos != null -> parts.add(context.getString(R.string.walk_sum_to_pos, pos))
        !targetName.isNullOrBlank() -> parts.add(context.getString(R.string.walk_sum_to, targetName))
    }
    if (parts.isEmpty()) return null
    if (minutes != null && minutes > 0) {
        parts[parts.size - 1] = context.getString(R.string.walk_sum_minutes, parts.last(), minutes.toInt())
    }
    return parts.joinToString(" · ")
}

/** [items] with equal neighbours put together: each item and how often it stands in a row. */
internal fun collapseRuns(items: List<String>): List<Pair<String, Int>> {
    val runs = ArrayList<Pair<String, Int>>()
    for (item in items) {
        val last = runs.lastOrNull()
        if (last != null && last.first == item) runs[runs.size - 1] = item to last.second + 1 else runs.add(item to 1)
    }
    return runs
}

// A leading word for a door in an OSM name ("Eingang Europaplatz", "Ausgang B", "Exit 3").
private val DOOR_WORD = Regex(
    "^(ein-?\\s*(/|und|&)\\s*ausgang|eingang|ausgang|zugang|aufgang|entrance|exit|way out)\\b[\\s:,.\\-–]*",
    RegexOption.IGNORE_CASE,
)

/** The name of a door without a leading "Eingang", "Ausgang", "Exit" and the like: the summary says which it is. */
internal fun doorName(label: String): String {
    // A name that is only the word ("Ausgang") leaves nothing: the plain word is said then.
    return label.trim().replace(DOOR_WORD, "").trim()
}

private fun near(a: Pair<Double, Double>, b: Pair<Double, Double>): Boolean {
    val north = (b.first - a.first) * 111_320.0
    val east = (b.second - a.second) * 111_320.0 * Math.cos(Math.toRadians(a.first))
    return north * north + east * east <= 15.0 * 15.0
}

/**
 * The summary line in small muted text, in [maxLines] (one under a small map, cut with an
 * ellipsis; as many as it needs in the card of the walk on the big map); nothing for null.
 */
@Composable
fun WalkSummaryLine(text: String?, modifier: Modifier = Modifier, maxLines: Int = 1) {
    if (text == null) return
    Text(
        text = text,
        color = AppColors.Muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * The lifts out of order near the lifts of [details] (FaSta, accessibility.cloud, where keys are
 * set; see LiftStatus), as positions; empty until known, and for a walk without lifts.
 */
@Composable
fun rememberBrokenLifts(details: WalkDetails): List<Pair<Double, Double>> {
    val context = LocalContext.current
    var broken by remember(details) { mutableStateOf<List<Pair<Double, Double>>>(emptyList()) }
    LaunchedEffect(details) {
        val lift = details.passages.firstOrNull { it.kind == PassageKind.Lift } ?: return@LaunchedEffect
        val sources = LiftKeys(context).sources()
        if (sources.isEmpty()) return@LaunchedEffect
        broken = try {
            LiftStatus.at(sources, Stop(id = "", name = "", latitude = lift.latitude, longitude = lift.longitude, distanceMeters = null))
                .map { it.latitude to it.longitude }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }
    return broken
}

/** The details along [walk] as it is drawn now (its way on foot where known, to its target). */
fun walkFocusDetails(walk: Leg): WalkDetails {
    val path = FootPaths.pathFor(walk)
    val target = walk.walkTarget
    val points = target?.position?.let { if (path.lastOrNull() != it) path + it else path } ?: path
    return walkDetails(
        points,
        targetPos = target?.takeIf { it.kind == WalkTargetKind.StopPosition }?.label,
        entrance = target?.takeIf { it.kind == WalkTargetKind.Entrance },
        exit = walk.walkExit,
    )
}
