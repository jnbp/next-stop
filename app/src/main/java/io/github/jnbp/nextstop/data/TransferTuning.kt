package io.github.jnbp.nextstop.data

import java.util.concurrent.atomic.AtomicInteger

/*
 * The parts of the transfer rating the traveller can tune in the settings (transfers): the
 * minutes to spare from which a change is normal or relaxed, and the buffer a change needs
 * beyond its walk for getting off and finding the way. The defaults are the constants
 * [NORMAL_SPARE], [RELAXED_SPARE] and [EXACT_WALK_EXTRA_SECONDS]; set by the interface from
 * AppPrefs (see AppPrefs.applyTransferTuning), like [walkSpeedFactor].
 */

/** The minutes to spare beyond what a change needs from which it is normal; see [NORMAL_SPARE_RANGE]. */
@Volatile
var normalSpare: Long = NORMAL_SPARE

/** The minutes to spare beyond what a change needs from which it is relaxed; see [RELAXED_SPARE_RANGE]. */
@Volatile
var relaxedSpare: Long = RELAXED_SPARE

/**
 * The seconds a change needs beyond its walk for getting off and finding the way
 * ("change buffer"), see [CHANGE_BUFFER_STEPS]; a change between a platform and a street stop
 * needs [PLATFORM_STOP_STAIRS_SECONDS] more for the stairs.
 */
@Volatile
var changeBufferSeconds: Long = EXACT_WALK_EXTRA_SECONDS

/** The choices of the setting "buffer for Normal", in minutes. */
val NORMAL_SPARE_RANGE: LongRange = 0L..3L

/** The choices of the setting "buffer for Relaxed", in minutes. */
val RELAXED_SPARE_RANGE: LongRange = 3L..10L

/** The choices of the setting "change buffer", in seconds: 0 to 2 minutes in steps of half a minute. */
val CHANGE_BUFFER_STEPS: List<Long> = listOf(0L, 30L, 60L, 90L, 120L)

/** The three tunable values, each within its choices (a stored value out of range falls back to the nearest). */
data class TransferTuning(
    val normalSpare: Long = NORMAL_SPARE,
    val relaxedSpare: Long = RELAXED_SPARE,
    val changeBufferSeconds: Long = EXACT_WALK_EXTRA_SECONDS,
) {
    /** These values within their choices: relaxed never below normal, the buffer on a step. */
    fun bounded(): TransferTuning {
        val normal = normalSpare.coerceIn(NORMAL_SPARE_RANGE)
        val relaxed = relaxedSpare.coerceIn(RELAXED_SPARE_RANGE).coerceAtLeast(normal)
        val buffer = CHANGE_BUFFER_STEPS.minByOrNull { kotlin.math.abs(it - changeBufferSeconds) } ?: EXACT_WALK_EXTRA_SECONDS
        return TransferTuning(normal, relaxed, buffer)
    }
}

private val ratingSettingsCounter = AtomicInteger(0)

/**
 * Grows by one whenever a setting the rating of changes depends on changes: the tuning (see
 * [setTransferTuning]), the walking pace ([walkSpeedFactor]) or step-free walks
 * ([stepFreeWalks]). Caches of ratings or labels made from them take it into their key, so they
 * follow a change of the settings.
 */
val ratingSettingsVersion: Int get() = ratingSettingsCounter.get()

/** Notes that a setting the rating depends on changed (see [ratingSettingsVersion]). */
internal fun ratingSettingsChanged() {
    ratingSettingsCounter.incrementAndGet()
}

/** Makes [tuning] (within its choices) the one every rating uses. */
fun setTransferTuning(tuning: TransferTuning) {
    val bounded = tuning.bounded()
    if (bounded != currentTransferTuning()) ratingSettingsChanged()
    normalSpare = bounded.normalSpare
    relaxedSpare = bounded.relaxedSpare
    changeBufferSeconds = bounded.changeBufferSeconds
}

/** The tuning every rating uses now. */
fun currentTransferTuning(): TransferTuning = TransferTuning(normalSpare, relaxedSpare, changeBufferSeconds)

/**
 * The numbers the explanation of the transfer rating names ("How transfers are rated"),
 * taken from the constants and the [tuning] in use, so the text in the app always says what
 * the rating does:
 *
 * - [changeBufferSeconds]: what a change with a known walk needs beyond it (getting off,
 *   finding the way), [platformStreetSeconds] between a platform and a stop in the street
 *   (stairs included), never less than [minExactMinutes] minutes between two places;
 * - the rules of thumb where no walk is known: [samePlatform], [sameStation], [railStation]
 *   minutes, a walk of the provider and [walkExtra] more;
 * - the levels: relaxed from [relaxedSpare] and normal from [normalSpare] minutes to spare,
 *   knapp at a brisk pace ([briskPercent] % of the usual time of the walk, no buffer) from
 *   [minKnappMinutes] minutes, a sprint otherwise;
 * - an arrival that is only estimated: normal at best, knapp below [relaxedSpare] to spare.
 */
data class TransferRatingFacts(
    val changeBufferSeconds: Long,
    val platformStreetSeconds: Long,
    val minExactMinutes: Long,
    val samePlatform: Long,
    val sameStation: Long,
    val railStation: Long,
    val walkExtra: Long,
    val relaxedSpare: Long,
    val normalSpare: Long,
    val minKnappMinutes: Long,
    val briskPercent: Int,
)

/** The facts of the rating with [tuning] (by default the one in use); see [TransferRatingFacts]. */
fun transferRatingFacts(tuning: TransferTuning = currentTransferTuning()): TransferRatingFacts {
    val bounded = tuning.bounded()
    return TransferRatingFacts(
        changeBufferSeconds = bounded.changeBufferSeconds,
        platformStreetSeconds = bounded.changeBufferSeconds + PLATFORM_STOP_STAIRS_SECONDS.toLong(),
        minExactMinutes = EXACT_CHANGE_MIN_NEED,
        samePlatform = SAME_PLATFORM_NEED,
        sameStation = SAME_STATION_NEED,
        railStation = RAIL_STATION_NEED,
        walkExtra = WALK_EXTRA_NEED,
        relaxedSpare = bounded.relaxedSpare,
        normalSpare = bounded.normalSpare,
        minKnappMinutes = MIN_KNAPP_MINUTES,
        briskPercent = Math.round(BRISK_WALK_FACTOR * 100).toInt(),
    )
}
