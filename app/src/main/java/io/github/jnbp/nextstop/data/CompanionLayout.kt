package io.github.jnbp.nextstop.data

import android.content.Context
import kotlin.math.floor

/**
 * The setting "Begleiter-Ansicht" (companion view), in the order offered:
 * - [Auto] ("Automatisch", the default): calm without the map while riding or waiting, the map
 *   while walking (see [autoShowsMap]); the map button wins from then on, for this and every
 *   later companion, until it is pressed again or the setting changes (see [companionMapChoice]).
 * - [NoMap] ("Ohne Karte"): always calm; the map button shows the map for a moment, never remembered.
 * - [AlwaysMap] ("Mit Karte"): the view with the map, every time.
 * - [Classic] ("Klassisch"): the view up to release 0.2 (map, blue box, sheet, buttons and the tab bar), unchanged.
 * Stored by name: the names of the three earlier modes stay, so a stored choice keeps its view
 * (the earlier Auto, calm with a remembered map button, becomes the new automatic one).
 */
enum class CompanionLayoutMode {
    Auto, NoMap, AlwaysMap, Classic;

    companion object {
        /** The mode stored as [name]; [Auto] for none or an unknown one. */
        fun fromStored(name: String?): CompanionLayoutMode = entries.firstOrNull { it.name == name } ?: Auto
    }
}

/**
 * Whether the companion shows the map in [mode]: in [CompanionLayoutMode.Auto] as [autoShowsMap]
 * says for the [stage] and the choice of the map button on this leg ([legChoice], null for
 * none); in [CompanionLayoutMode.NoMap] only while the button asked for it on this leg.
 */
fun companionShowsMap(mode: CompanionLayoutMode, stage: RideStage?, legChoice: Boolean?): Boolean = when (mode) {
    CompanionLayoutMode.Auto -> autoShowsMap(stage, legChoice)
    CompanionLayoutMode.NoMap -> legChoice == true
    CompanionLayoutMode.AlwaysMap -> true
    CompanionLayoutMode.Classic -> true
}

/**
 * The rule of "Automatisch": the map while the traveller is on foot (to the first stop, between
 * two rides, from the last stop to the destination), the calm view while waiting at a stop, on
 * board, getting off and once arrived. The map button of the companion ([userChoiceThisLeg], null
 * while untouched) wins until the next leg (see [autoLegKey]). Without a stage yet: calm.
 */
fun autoShowsMap(stage: RideStage?, userChoiceThisLeg: Boolean?): Boolean {
    if (userChoiceThisLeg != null) return userChoiceThisLeg
    return when (stage) {
        RideStage.Before, RideStage.Walking -> true
        RideStage.OnBoard, RideStage.Alighting, RideStage.Waiting, RideStage.Arrived, null -> false
    }
}

/**
 * What counts as one leg for the map button: the ride or walk [legIndex] of the stage, and
 * whether the traveller is on foot or not. The choice of the button lasts while this stays.
 */
fun autoLegKey(journeyId: String, stage: RideStage, legIndex: Int): String =
    "$journeyId|$legIndex|${if (autoShowsMap(stage, null)) "foot" else "ride"}"

/**
 * The choice of the map button that counts in [mode]: in [CompanionLayoutMode.Auto] the stored
 * one ([saved], kept across companions until changed), in [CompanionLayoutMode.NoMap] the one of
 * this leg only ([leg]); null for none. The other modes have no button.
 */
fun companionMapChoice(mode: CompanionLayoutMode, saved: Boolean?, leg: Boolean?): Boolean? = when (mode) {
    CompanionLayoutMode.Auto -> saved
    CompanionLayoutMode.NoMap -> leg
    CompanionLayoutMode.AlwaysMap, CompanionLayoutMode.Classic -> null
}

/**
 * The companion view in effect: the battery saver ([saving]) keeps the companion calm and
 * without the map, whatever is chosen (no map is even made, see BatterySaver).
 */
fun effectiveCompanionLayout(mode: CompanionLayoutMode, saving: Boolean): CompanionLayoutMode =
    if (saving) CompanionLayoutMode.NoMap else mode

/** Whether the tab bar goes away while the companion is open: in the new views, not in the classic one. */
fun companionHidesTabs(mode: CompanionLayoutMode): Boolean = mode != CompanionLayoutMode.Classic

/** The stored choices of the companion view, in the file of [AppPrefs]. */
class CompanionLayoutPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)

    var mode: CompanionLayoutMode
        get() = CompanionLayoutMode.fromStored(prefs.getString(KEY_MODE, null))
        set(value) = prefs.edit().putString(KEY_MODE, value.name).apply()

    /** The map button in "Automatisch" (see [companionMapChoice]): null while never pressed. */
    var mapChoice: Boolean?
        get() = if (prefs.contains(KEY_MAP_CHOICE)) prefs.getBoolean(KEY_MAP_CHOICE, false) else null
        set(value) = prefs.edit().apply { if (value == null) remove(KEY_MAP_CHOICE) else putBoolean(KEY_MAP_CHOICE, value) }.apply()

    /** "Anschlusswahl im Begleiter" (#138): how the connection pickers show at first. */
    var pickers: PickerMode
        get() = PickerMode.fromStored(prefs.getString(KEY_PICKERS, null))
        set(value) = prefs.edit().putString(KEY_PICKERS, value.name).apply()

    companion object {
        private const val KEY_PICKERS = "companionPickers"
        private const val KEY_MODE = "companionLayout"
        private const val KEY_MAP_CHOICE = "companionMapChoice"
    }
}

/**
 * Where the traveller is among the [times] of the stops of a ride (epoch millis of each stop,
 * null where a stop has no time), in stop units: 0 at the first stop, 1.5 halfway between the
 * second and the third, the last index at the end. Between two timed stops by time; stops
 * without a time are spread evenly between their timed neighbours. 0 before the first time,
 * the last index after the last one, and 0 without any time.
 */
fun stopPosition(times: List<Long?>, nowMillis: Long): Float {
    if (times.size < 2) return 0f
    val timed = times.indices.filter { times[it] != null }
    if (timed.isEmpty()) return 0f
    val first = timed.first()
    val last = timed.last()
    if (nowMillis <= times[first]!!) return first.toFloat()
    if (nowMillis >= times[last]!!) return last.toFloat()
    for (k in 0 until timed.size - 1) {
        val a = timed[k]
        val b = timed[k + 1]
        val ta = times[a]!!
        val tb = times[b]!!
        if (nowMillis in ta..tb) {
            if (tb <= ta) return b.toFloat()
            val share = (nowMillis - ta).toFloat() / (tb - ta).toFloat()
            return a + share * (b - a)
        }
    }
    return last.toFloat()
}

/**
 * The position by the times ([timed], see [stopPosition]) held to what the position of the
 * device says: with [nextStop] the next stop ahead (-1 where unknown), the traveller is past
 * the stop before it and short of it. The position wins where the two disagree.
 */
fun fixedStopPosition(timed: Float, nextStop: Int, lastIndex: Int): Float {
    if (nextStop < 1 || nextStop > lastIndex) return timed.coerceIn(0f, lastIndex.toFloat())
    return timed.coerceIn((nextStop - 1).toFloat(), nextStop - NEXT_STOP_MARGIN)
}

// The traveller is never drawn on the next stop itself while the position says it is ahead.
private const val NEXT_STOP_MARGIN = 0.05f

/** One row of the stops of the ride in the calm companion: a stop, or several folded into one row. */
sealed class StopRowSpec {
    /** The first stop index this row stands for. */
    abstract val from: Int

    /** The last stop index this row stands for. */
    abstract val to: Int

    data class Single(val index: Int) : StopRowSpec() {
        override val from: Int get() = index
        override val to: Int get() = index
    }

    /** The stops [from] to [to], folded ("2 more stops ▾"); [passed]: stops already ridden past ("5 Halte gefahren ▾", #187). */
    data class Folded(override val from: Int, override val to: Int, val passed: Boolean = false) : StopRowSpec() {
        val count: Int get() = to - from + 1
    }
}

/**
 * The rows for the [count] stops of a ride while the traveller is at [position] (stop units,
 * see [stopPosition]; below 0 while not on board yet): the stops already passed (#187: the
 * boarding stop always, so it never looks as if the ride began at the last one passed; more than
 * [PASSED_SHOWN] passed fold their middle into one row, the boarding stop and the last one passed
 * stay), the last stop passed, [ahead] stops ahead of it, then the stops up to the last but
 * [beforeEnd] folded into one row, those last ones and the destination. Fewer than two stops
 * are never folded. [expanded] shows every stop.
 */
fun stopRows(count: Int, position: Float, expanded: Boolean, ahead: Int = 2, beforeEnd: Int = 1): List<StopRowSpec> {
    if (count <= 0) return emptyList()
    val last = count - 1
    if (expanded) return (0..last).map { StopRowSpec.Single(it) }
    val start = if (position < 0f) 0 else floor(position).toInt().coerceIn(0, last)
    val headEnd = (start + ahead).coerceAtMost(last)
    val tailStart = (last - beforeEnd).coerceAtLeast(headEnd + 1)
    val folded = tailStart - headEnd - 1
    val rows = mutableListOf<StopRowSpec>()
    // The stops passed before the last one: the boarding stop, the rest folded where many.
    if (start + 1 > PASSED_SHOWN && start - 1 >= 2) {
        rows += StopRowSpec.Single(0)
        rows += StopRowSpec.Folded(1, start - 1, passed = true)
    } else {
        for (index in 0 until start) rows += StopRowSpec.Single(index)
    }
    for (index in start..headEnd) rows += StopRowSpec.Single(index)
    if (folded >= 2) {
        rows += StopRowSpec.Folded(headEnd + 1, tailStart - 1)
    } else {
        for (index in headEnd + 1 until tailStart) rows += StopRowSpec.Single(index)
    }
    for (index in tailStart..last) if (index > headEnd) rows += StopRowSpec.Single(index)
    return rows
}

/** Up to this many stops passed (the boarding stop and the last one passed among them) all show; more fold their middle (#187). */
const val PASSED_SHOWN = 4

/**
 * Where [position] (stop units) lies among [rows], in row units: 0 at the middle of the first
 * row, 1 at the middle of the second. Between two rows in proportion to the stops between
 * them; inside a folded row spread over its height. Held to the first and the last row.
 */
fun rowPosition(rows: List<StopRowSpec>, position: Float): Float {
    if (rows.isEmpty()) return 0f
    if (position <= rows.first().from) return fromAnchor(rows.first(), 0)
    if (position >= rows.last().to) return toAnchor(rows.last(), rows.lastIndex)
    rows.forEachIndexed { k, row ->
        if (position >= row.from && position <= row.to) {
            val span = (row.to - row.from).toFloat()
            val a = fromAnchor(row, k)
            return if (span <= 0f) a else a + (toAnchor(row, k) - a) * (position - row.from) / span
        }
        val next = rows.getOrNull(k + 1) ?: return toAnchor(row, k)
        if (position > row.to && position < next.from) {
            val a = toAnchor(row, k)
            val b = fromAnchor(next, k + 1)
            return a + (b - a) * (position - row.to) / (next.from - row.to).toFloat()
        }
    }
    return rows.lastIndex.toFloat()
}

// Where the first and the last stop of row [k] lie, in row units: a single stop in the middle
// of its row, the stops of a folded row spread evenly over its height.
private fun fromAnchor(row: StopRowSpec, k: Int): Float =
    if (row is StopRowSpec.Folded) k - 0.5f + 0.5f / row.count else k.toFloat()

private fun toAnchor(row: StopRowSpec, k: Int): Float =
    if (row is StopRowSpec.Folded) k - 0.5f + (row.count - 0.5f) / row.count else k.toFloat()

/** How a change to the next ride looks: safe, tight, or not to be made. */
enum class ChangeReach { Safe, Tight, Late }

/** The [ChangeReach] of a change of [time] (see [changeTime]). */
fun changeReach(time: ChangeTime): ChangeReach = when {
    !time.possible -> ChangeReach.Late
    time.tightness == Tightness.Risky || time.tightness == Tightness.VeryRisky -> ChangeReach.Tight
    else -> ChangeReach.Safe
}
