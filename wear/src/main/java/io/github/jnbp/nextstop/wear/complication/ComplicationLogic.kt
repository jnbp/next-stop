package io.github.jnbp.nextstop.wear.complication

import io.github.jnbp.nextstop.wear.data.Departure
import io.github.jnbp.nextstop.wear.data.WATCH_CLOCK_FROM_MINUTES
import io.github.jnbp.nextstop.wear.data.watchMinutesLeft

/*
 * The decisions of the complications that need no Android: which mode the automatic one shows,
 * which departures go into a timeline and when each takes over, how texts are cut to the few
 * characters a watch face has room for, and how the disruptions of a stop are counted. Kept
 * free of Android classes so they run as JVM tests (ComplicationLogicTest).
 *
 * The timelines are what keep the complications cheap: one load gives the next few
 * departures, and the watch face switches from one to the next at its departure time by itself
 * (with a countdown text the system draws), so nothing wakes the app every minute.
 */

/** What the automatic complication shows. */
enum class AutoMode {
    /** A trip runs: the companion (its next step, the ring of its progress). */
    Trip,

    /** No trip: the departures of the nearest stop. */
    Departures,
}

/**
 * The mode of the automatic complication: the companion while a trip runs ([tripActive], not
 * [arrived] and not [stale], a trip of the phone that stopped writing), else the departures.
 */
fun autoMode(tripActive: Boolean, arrived: Boolean, stale: Boolean): AutoMode =
    if (tripActive && !arrived && !stale) AutoMode.Trip else AutoMode.Departures

/** Why a complication of departures has none to show (#235). */
enum class EmptyBoard {
    /** Nothing could be loaded now (only runs kept from earlier, all gone): "Laden fehlgeschlagen". */
    Failed,

    /** A load that answered found nothing to catch: "Keine Abfahrten in der nächsten Stunde". */
    NoneSoon,
}

/** Why the board is empty: [loaded] (an answer came now) means none leaves soon, else the load failed; never "no departures" after a failure. */
fun emptyBoard(loaded: Boolean): EmptyBoard = if (loaded) EmptyBoard.NoneSoon else EmptyBoard.Failed

/**
 * True when the automatic complication shows the favourite stop ([favoriteId]) in place of the
 * nearest one ([nearestId]): only when the nearest has no departure to show
 * ([nearestHasRuns] false) and the favourite is another stop (#235: never a lone dash where
 * the favourite stop has runs).
 */
fun autoTriesFavorite(nearestHasRuns: Boolean, nearestId: String, favoriteId: String?): Boolean =
    !nearestHasRuns && favoriteId != null && favoriteId != nearestId

/**
 * The departures a timeline shows, in order: not cancelled, with a time, not gone at [now]
 * (a departure of this minute still counts, see [GONE_GRACE_MILLIS]), at most [max].
 */
fun timelineDepartures(departures: List<Departure>, now: Long, max: Int = TIMELINE_DEPARTURES): List<Departure> =
    departures
        .filter { !it.cancelled && it.time != null && it.time >= now - GONE_GRACE_MILLIS }
        .sortedBy { it.time }
        .take(max)

/**
 * When each slide of a timeline starts: the first is shown from the start ([Long.MIN_VALUE]),
 * every other one from the departure [times] of the one before it, as soon as that one has
 * left. [times] in epoch ms, ascending.
 */
fun slideStarts(times: List<Long>): List<Long> =
    times.indices.map { index -> if (index == 0) Long.MIN_VALUE else times[index - 1] }

/**
 * The value of a ring (RANGED_VALUE, 0..1) for a departure at [time] whose slide is shown from
 * [shownFrom] (epoch ms): full when it leaves, empty [RING_WINDOW_MILLIS] or more before. The
 * face draws the value as it was given, so it is right when the slide appears and a little
 * behind until the next one; the countdown text beside it stays exact.
 */
fun departureRingValue(time: Long, shownFrom: Long, window: Long = RING_WINDOW_MILLIS): Float =
    (1f - (time - shownFrom).toFloat() / window).coerceIn(0f, 1f)

/**
 * [text] cut to at most [max] characters for a small slot of a watch face: words that do not
 * fit are dropped from the end, and a single word that is too long is cut with an ellipsis
 * ("Alexanderplatz" -> "Alexan…"). Blank text stays empty.
 */
fun shortLabel(text: String, max: Int = SHORT_LABEL_MAX): String {
    val clean = text.trim().replace(WHITESPACE, " ")
    if (clean.length <= max) return clean
    if (max <= 1) return clean.take(max)
    val words = clean.split(' ')
    var kept = ""
    for (word in words) {
        val next = if (kept.isEmpty()) word else "$kept $word"
        if (next.length > max) break
        kept = next
    }
    return kept.ifEmpty { clean.take(max - 1) + ELLIPSIS }
}

/** The lines of a journey for a small slot: "U7›S41", at most [max] lines, empty for a walk only. */
fun lineChain(lines: List<String>, max: Int = CHAIN_LINES): String =
    lines.filter { it.isNotBlank() }.take(max).joinToString(CHAIN_JOIN)

/** What the disruption complication says about a stop: the distinct notices and the cancelled runs. */
data class DisruptionSummary(
    /** The distinct warnings of the provider (their short texts), most frequent first. */
    val notices: List<String>,
    /** The cancelled runs among the departures. */
    val cancelled: Int,
) {
    /** The number shown: notices and cancelled runs together. */
    val count: Int get() = notices.size + cancelled

    /** True when there is nothing to report. */
    val clear: Boolean get() = count == 0
}

/**
 * The disruptions of a stop from its [departures]: each warning text once (most frequent
 * first, as the one that concerns most runs leads), and the runs that are cancelled.
 */
fun disruptionSummary(departures: List<Departure>): DisruptionSummary {
    val counts = LinkedHashMap<String, Int>()
    departures.forEach { departure ->
        departure.warnings.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { notice ->
            counts[notice] = (counts[notice] ?: 0) + 1
        }
    }
    val notices = counts.entries.sortedByDescending { it.value }.map { it.key }
    return DisruptionSummary(notices, departures.count { it.cancelled })
}

/**
 * One stretch of a countdown in a short slot or a ring: from [start] to [end] (epoch ms, end
 * excluded) it shows [minutes] (0: "now"), or the clock time where [minutes] is null (an hour
 * or more ahead, as the app shows it, see watchShowsClock).
 */
data class CountdownSegment(val start: Long, val end: Long, val minutes: Long?)

/**
 * The stretches of a countdown to [target] shown from [from] to [until] (epoch ms): one per
 * minute as the app counts them (watchMinutesLeft: the full minutes left), the clock time
 * while an hour or more is left, "now" for the last minute. A short slot has no room for the unit the
 * system's own countdown adds ("12 Min." in German, cut on the face), so the minutes go into
 * a timeline the face moves along by itself, without waking the app. At most [max] stretches;
 * the last one then reaches to [until].
 */
fun countdownSegments(from: Long, until: Long, target: Long, max: Int = COUNTDOWN_SEGMENTS_MAX): List<CountdownSegment> {
    if (until <= from || max <= 0) return emptyList()
    val segments = ArrayList<CountdownSegment>()
    var start = from
    while (start < until) {
        val left = watchMinutesLeft(target - start)
        val minutes = left.takeIf { it < WATCH_CLOCK_FROM_MINUTES }
        // The full minutes left drop below `shown` just after target - shown minutes.
        val shown = minutes ?: WATCH_CLOCK_FROM_MINUTES
        val change = if (minutes == 0L) Long.MAX_VALUE else target - shown * MINUTE + 1
        val last = segments.size == max - 1
        val end = if (last) until else minOf(change, until)
        segments.add(CountdownSegment(start, end, minutes))
        start = end
    }
    return segments
}

/** The minutes to [target] at [at] as the app counts them (the full minutes left); null from an hour on (then the clock time is shown). */
fun countdownMinutes(target: Long, at: Long): Long? =
    watchMinutesLeft(target - at).takeIf { it < WATCH_CLOCK_FROM_MINUTES }

/**
 * The text of a countdown in a short slot: [now] at 0 minutes, the [clock] time where
 * [minutes] is null, else the minutes in the app's short form ([minutesPattern], "%d'").
 * Always within [SHORT_LABEL_MAX] characters.
 */
fun countdownLabel(minutes: Long?, clock: String, now: String, minutesPattern: String): String = when {
    minutes == null -> clock
    minutes <= 0L -> shortLabel(now.replaceFirstChar { it.uppercaseChar() })
    else -> minutesPattern.format(minutes.toInt())
}

/**
 * The countdown of a short slot or a ring (#235): as [countdownLabel], but "now" as zero minutes
 * in the same short form ("0'"), as wide as the minutes before it. "Jetzt" beside a line in a
 * round slot was cut at its first letter on the face ("letzt"); the long slot, the tile and the
 * app keep the word.
 */
fun slotCountdownLabel(minutes: Long?, clock: String, minutesPattern: String): String =
    countdownLabel(minutes?.coerceAtLeast(0L), clock, minutesPattern.format(0), minutesPattern)

/** The most characters of a countdown in a short slot beside a line, minutes up to an hour ("59'"). */
const val SLOT_COUNTDOWN_MAX = 3

/**
 * What a short slot or a ring shows beside its text, given the slide's [icon] and [title]:
 * never both, as a face either drops one of them or runs out of room. An icon that tells what
 * the slot is about (home, a warning) wins over the title; the app's own icon ([appIcon])
 * only stands where there is no title (the face may draw the app's icon itself already).
 */
fun <I> textSlotExtras(icon: I?, title: String?, appIcon: I): Pair<I?, String?> = when {
    icon != null && icon != appIcon -> icon to null
    title != null -> null to title
    else -> icon to null
}

/**
 * Where the last slide of a timeline ends, shown from [start]: with an [expired] slide to follow
 * and a countdown to [target], once its run has gone ([GONE_GRACE_MILLIS] after it), so a
 * departure never stays on the face as "now" for hours when no new data comes (#235); else
 * [LAST_SLIDE_MILLIS] after its start, as before. Never before [start] (then the slide is left out).
 */
fun lastSlideEnd(start: Long, target: Long?, expired: Boolean): Long =
    if (expired && target != null) maxOf(start, target + GONE_GRACE_MILLIS) else start + LAST_SLIDE_MILLIS

/**
 * True when departures kept at [keptAt] (DepartureCache) are fresh enough at [now] for a
 * complication to show them without a request of its own: the app's start page loaded them
 * moments ago, so the complication shows the same board as the app (#235).
 */
fun keptIsFresh(keptAt: Long, now: Long): Boolean = now - keptAt in 0 until KEPT_FRESH_MILLIS

/** How old kept departures may be for [keptIsFresh] (ms): about one refresh of the start page's card. */
const val KEPT_FRESH_MILLIS = 60_000L

/** The last slide of a timeline (and the slide for after it) stays this long (ms); the system asks for new data long before. */
const val LAST_SLIDE_MILLIS = 6 * 60 * 60_000L

/** Departures put into one timeline: one load of a stop covers about the next half hour. */
const val TIMELINE_DEPARTURES = 5

/** A departure stays on the face this long after its time (ms): the train is usually still at the platform. */
const val GONE_GRACE_MILLIS = 30_000L

/** The time before a departure its ring covers (ms): half an hour, about the next departures of a stop. */
const val RING_WINDOW_MILLIS = 30 * 60_000L

/** Characters of the title and the text of a SHORT_TEXT slot (the guideline of Wear OS is 7). */
const val SHORT_LABEL_MAX = 7

/** Stretches of countdowns in one timeline, at most: about an hour of minutes and the slides. */
const val COUNTDOWN_SEGMENTS_MAX = 80

private const val MINUTE = 60_000L

// Lines named in a small slot, and how they are joined.
private const val CHAIN_LINES = 2
private const val CHAIN_JOIN = "›"
private const val ELLIPSIS = "…"
private val WHITESPACE = Regex("\\s+")

/**
 * A stop or destination [name] for the text of a short slot or a ring (#252): never cut with an
 * ellipsis ("Muster…" looked broken on a round face), but a compact form of at most
 * [SHORT_LABEL_MAX] characters, or "" for none (then the slot shows its icon alone).
 *
 * The name as it is where it fits; else without the common words of a stop: the prefixes
 * "S+U", "S" and "U", a street ("Str.", "Straße", also at the end of a word: "Musterstraße"
 * -> "Muster"), "Platz" as "Pl." and "Bahnhof"/"Bhf" as "Bf". Where that is still too long,
 * the first word cut to [SLOT_NAME_CUT] characters and a full stop ("Musterw."), but only
 * where at least [SLOT_NAME_LETTERS] letters are left of it; else nothing. A long slot keeps
 * the full name.
 */
fun slotStopName(name: String): String {
    val clean = name.trim().replace(WHITESPACE, " ")
    if (clean.length <= SHORT_LABEL_MAX) return clean
    val words = clean.split(' ').toMutableList()
    while (words.size > 1 && words.first() in SLOT_PREFIXES) words.removeAt(0)
    val compact = words.mapNotNull { compactWord(it) }.ifEmpty { words }.joinToString(" ")
    if (compact.length <= SHORT_LABEL_MAX) return compact
    val first = compact.substringBefore(' ').trimEnd('-', '.', ',', '/')
    val cut = first.take(SLOT_NAME_CUT).trimEnd('-', '.', ',', '/')
    if (cut.count { it.isLetter() } < SLOT_NAME_LETTERS) return ""
    // A whole word that fits needs no full stop: it is not cut.
    return if (cut.length == first.length && first.length <= SHORT_LABEL_MAX) first else "$cut."
}

/** [word] of a stop name in its compact form (see slotStopName); null where it goes. */
private fun compactWord(word: String): String? {
    val lower = word.lowercase()
    if (lower in SLOT_STREET_WORDS) return null
    if (lower == "platz") return "Pl."
    if (lower in SLOT_STATION_WORDS) return "Bf"
    for (suffix in SLOT_STREET_SUFFIXES) {
        if (lower.endsWith(suffix) && word.length > suffix.length) return word.dropLast(suffix.length)
    }
    if (lower.endsWith("platz") && word.length > 5) return word.dropLast(5) + "pl."
    if (lower.endsWith("bahnhof") && word.length > 7 && !lower.endsWith("hauptbahnhof")) return word.dropLast(7) + "bf"
    return word
}

// The words slotStopName leaves out or shortens.
private val SLOT_PREFIXES = setOf("S+U", "S", "U")
private val SLOT_STREET_WORDS = setOf("str", "str.", "straße", "strasse")
private val SLOT_STREET_SUFFIXES = listOf("straße", "strasse", "str.")
private val SLOT_STATION_WORDS = setOf("bahnhof", "bhf", "bhf.")

/** Characters of the first word that slotStopName keeps before its full stop. */
const val SLOT_NAME_CUT = 6

/** Letters a cut name must keep (see slotStopName); fewer say too little to tell the stop. */
const val SLOT_NAME_LETTERS = 4
