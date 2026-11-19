package io.github.jnbp.nextstop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** The kinds of live data the app shows with their age (see [FreshnessChip]). */
enum class LiveSource { Journeys, Departures, Trip }

/**
 * When each kind of live data last came in, and asks for it to come again now. Process-wide,
 * so the places that load it and the ones that show it need not hand it through; nothing of
 * it is stored.
 */
object LiveFreshness {
    // Wall-clock milliseconds of the last answer, 0 for none yet; one state per source.
    private val at = Array(LiveSource.entries.size) { mutableLongStateOf(0L) }

    // Counts the requests for a refresh by hand, per source (see awaitRefresh).
    private val asks = Array(LiveSource.entries.size) { MutableStateFlow(0) }

    /** Fresh live data of [source] came in just now. */
    fun mark(source: LiveSource) {
        at[source.ordinal].longValue = System.currentTimeMillis()
    }

    /** The data of [source] is gone (a closed search): nothing to tell the age of. */
    fun clear(source: LiveSource) {
        at[source.ordinal].longValue = 0L
    }

    /** When live data of [source] last came in (wall clock, ms), 0 for none. Observable. */
    fun at(source: LiveSource): Long = at[source.ordinal].longValue

    /**
     * How old the live data of [source] is at [nowMillis], in whole seconds; null before the first
     * answer. For "Live · vor x min" wherever times of the followed trip are shown. Not observable
     * by itself: read it with a ticking clock.
     */
    fun ageSeconds(source: LiveSource, nowMillis: Long = System.currentTimeMillis()): Long? {
        val marked = at[source.ordinal].longValue
        if (marked == 0L) return null
        return ((nowMillis - marked) / 1000L).coerceAtLeast(0L)
    }

    /** Asks the loop that keeps [source] fresh to load it now (see [awaitRefresh]). */
    fun ask(source: LiveSource) {
        asks[source.ordinal].value++
    }

    /**
     * Waits [millis] before the next round of a refresh loop of [source], or less when the
     * user asks for fresh data by hand meanwhile.
     */
    suspend fun awaitRefresh(source: LiveSource, millis: Long) {
        val flow = asks[source.ordinal]
        val seen = flow.value
        withTimeoutOrNull(millis) { flow.first { it != seen } }
    }
}

/**
 * The one clock of every freshness indicator: whole seconds of the wall clock, resting with
 * the app in the background (it waits for a frame, and the frame clock pauses then). One loop
 * for all consumers, whichever came first; when it leaves, another one takes over. It wakes
 * only when a consumer's label changes ([tickerNextWake]): every 5 s while only freshness
 * chips use it, every second while a consumer that shows seconds attends with
 * [nextWholeSecondMillis]. [poke] wakes it at once (fresh data came in, a consumer came).
 */
internal object SecondTicker {
    val seconds = mutableLongStateOf(System.currentTimeMillis() / 1000L)
    private val driven = MutableStateFlow(false)
    private val pokes = MutableStateFlow(0)

    // Each consumer and when it wants the clock to wake next, for a given now (wall ms). Main thread only.
    private val consumers = LinkedHashMap<Any, (Long) -> Long>()

    /** Wakes the clock now: it moves on and plans its next wake anew. */
    fun poke() {
        pokes.value++
    }

    /**
     * Keeps the clock going while the caller (a LaunchedEffect of [owner]) runs; [nextWake]
     * says when its label next changes (by default every whole second).
     */
    suspend fun attend(owner: Any, nextWake: (Long) -> Long = ::nextWholeSecondMillis) {
        consumers[owner] = nextWake
        poke()
        try {
            while (true) {
                if (!driven.value) {
                    driven.value = true
                    try {
                        drive()
                    } finally {
                        driven.value = false
                    }
                } else {
                    driven.first { !it }
                }
            }
        } finally {
            consumers.remove(owner)
            poke()
        }
    }

    private suspend fun drive() {
        while (true) {
            val now = System.currentTimeMillis()
            seconds.longValue = now / 1000L
            val wake = tickerNextWake(now, consumers.values.map { it(now) })
            val seen = pokes.value
            withTimeoutOrNull(wake - now) { pokes.first { it != seen } }
            withFrameNanos { }
        }
    }
}

/**
 * "Live · vor 10 s": how old the shown live data of [source] is, with a dot that beats once
 * whenever fresh data comes in and turns grey when it is no longer live. A tap asks for fresh
 * data ([onRefresh]); until it comes (or [refreshing] ends) the chip says it is updating.
 * Shows nothing before the first answer. [shortJustNow] says "Live · jetzt" for a narrow place;
 * [trailing] stands inside the chip after the text (the companion's source mark).
 */
@Composable
fun FreshnessChip(
    source: LiveSource,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    refreshing: Boolean = false,
    minHeight: Dp = 36.dp,
    shortJustNow: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    // Without the chip around it: the dot and the words in a line of text (the departures header, #223).
    plain: Boolean = false,
) {
    val markedAt = LiveFreshness.at(source)
    if (markedAt == 0L) return
    val staleSeconds = BatterySaver.pace(FRESH_STALE_MILLIS) / 1000L
    // The shared clock wakes for this chip only when its label changes (steps of 5 s, then
    // minutes), and at once when fresh data came in.
    val latestMarked by rememberUpdatedState(markedAt)
    val latestStale by rememberUpdatedState(staleSeconds)
    val owner = remember { Any() }
    LaunchedEffect(owner) { SecondTicker.attend(owner) { now -> freshNextWakeMillis(now, latestMarked, latestStale) } }
    LaunchedEffect(markedAt, staleSeconds) { SecondTicker.poke() }
    val key by remember(markedAt, staleSeconds) {
        derivedStateOf { freshLabelKey(SecondTicker.seconds.longValue - markedAt / 1000L, staleSeconds) }
    }
    // Asked by a tap: "updating" until an answer comes in (a new markedAt) or the time is up.
    var asked by remember(markedAt) { mutableStateOf(false) }
    if (asked) {
        LaunchedEffect(Unit) {
            delay(FRESH_ASK_TIMEOUT_MILLIS)
            asked = false
        }
    }
    val updating = asked || refreshing
    val stale = freshKeyStale(key)
    val value = freshKeyValue(key)
    // The words alone, or the words around a number that rolls on its own (5, 10, 15 ... s,
    // then minutes): see FreshText.
    val number = when {
        updating || key == 0L -> null
        stale -> value.coerceAtLeast(1L)
        else -> value
    }
    val template = when {
        updating -> R.string.fresh_updating
        stale -> R.string.fresh_stale_minutes
        key == 0L -> if (shortJustNow) R.string.r5c_fresh_now else R.string.fresh_just_now
        freshKeyInMinutes(key) -> R.string.fresh_minutes
        else -> R.string.fresh_seconds
    }
    val text = if (number != null) stringResource(template, number) else stringResource(template)
    val words = if (number != null) aroundNumber(rawString(template)) else null
    val ticks = rememberTicks()
    val interactions = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = modifier
            .springPress(interactions)
            .clip(shape)
            .then(if (plain) Modifier else glassGround().border(1.dp, glassRim(), shape))
            .clickable(
                interactionSource = interactions,
                indication = LocalIndication.current,
                role = Role.Button,
                onClickLabel = stringResource(R.string.fresh_refresh),
            ) {
                ticks.refresh()
                asked = true
                onRefresh()
            }
            .defaultMinSize(minHeight = minHeight)
            .padding(start = if (plain) 2.dp else 12.dp, end = if (plain) 4.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        FreshDot(markedAt = markedAt, stale = stale && !updating)
        val color = if (stale && !updating) AppColors.Muted else AppColors.Ink
        // Once the chip is on screen, a change of its words ("gerade eben" to "vor 5 s", seconds to
        // minutes, "aktualisiert…") cross-fades and the chip's width follows, and a number that
        // comes with new words rolls in from 0 (#111). Within the same words only the number rolls.
        var settled by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { settled = true }
        val animated = rememberAnimationsOn()
        FadeSwap(
            target = FreshFace(template, number, text, words, color),
            contentKey = { it.template },
            contentAlignment = Alignment.CenterStart,
        ) { face ->
            FreshFaceText(face, rollIn = settled && animated)
        }
        trailing?.invoke()
    }
}

/** What the freshness chip says: the [template] of its words, the [number] in them (null for words alone), the whole [text]. */
private data class FreshFace(
    val template: Int,
    val number: Long?,
    val text: String,
    val words: Pair<String, String>?,
    val color: Color,
)

/**
 * The words of [face], with its number rolling. With [rollIn] (the words came in place of others),
 * the number starts from 0 and rolls to its value; RollingText snaps where animations are off.
 */
@Composable
private fun FreshFaceText(face: FreshFace, rollIn: Boolean) {
    val number = face.number
    val words = face.words
    if (number == null || words == null) {
        FreshWords(face.text, face.color)
        return
    }
    var shown by remember { mutableLongStateOf(if (rollIn) 0L else number) }
    LaunchedEffect(number) { shown = number }
    // Read out as one text ("Live · vor 10 Sekunden"), drawn with a rolling number.
    Row(modifier = Modifier.speaks(spokenTime(face.text)), verticalAlignment = Alignment.CenterVertically) {
        FreshWords(words.first, face.color, Modifier.alignByBaseline())
        RollingText(
            value = shown,
            text = shown.toString(),
            color = face.color,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            slotDigits = 1,
            modifier = Modifier.alignByBaseline(),
        )
        FreshWords(words.second, face.color, Modifier.alignByBaseline())
    }
}

// A part of the text of the freshness chip.
@Composable
private fun FreshWords(text: String, color: Color, modifier: Modifier = Modifier) {
    if (text.isEmpty()) return
    Text(
        text = text,
        color = color,
        fontSize = 13.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        softWrap = false,
        modifier = modifier,
    )
}

// A string resource as written, its placeholders unformatted ("Live · vor %1$d s").
@Composable
private fun rawString(id: Int): String {
    val resources = LocalContext.current.resources
    return remember(id, resources) { resources.getString(id) }
}

// How long a tap on the indicator says "updating" at most, when no answer comes.
private const val FRESH_ASK_TIMEOUT_MILLIS = 8_000L

/**
 * The dot of [FreshnessChip]: the live dot, which beats once as data comes in ([markedAt]
 * changes) and is grey when [stale]. No idle animation: it rests between answers, and does not
 * beat at all while the battery saver is active or animations are off.
 */
@Composable
private fun FreshDot(markedAt: Long, stale: Boolean) {
    val beat = remember { Animatable(1f) }
    val beats = rememberIdleAnimationsOn()
    LaunchedEffect(markedAt, beats) {
        if (!beats) {
            beat.snapTo(1f)
            return@LaunchedEffect
        }
        beat.snapTo(0f)
        beat.animateTo(1f, tween(LIVE_PULSE_MILLIS.toInt(), easing = LinearEasing))
    }
    val outer = (LIVE_DOT_RADIUS_DP + LIVE_DOT_OUTLINE_DP).dp
    // Room for the ring of the beat around the dot, so it does not push the text.
    Canvas(modifier = Modifier.size(outer * 2 + (LIVE_PULSE_REACH_DP * 2).dp)) {
        val fill = if (stale) AppColors.Muted else LIVE_GREEN
        val edge = outer.toPx()
        val phase = beat.value
        if (!stale && phase < 1f) {
            drawCircle(
                color = LIVE_GREEN.copy(alpha = livePulseOpacity(phase)),
                radius = edge + livePulseReach(phase).dp.toPx(),
                center = center,
            )
        }
        drawCircle(color = Color.White, radius = edge, center = center)
        drawCircle(color = fill, radius = LIVE_DOT_RADIUS_DP.dp.toPx(), center = center)
    }
}
