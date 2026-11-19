package io.github.jnbp.nextstop.data

import java.util.Locale
import kotlin.math.ceil

/**
 * The pace by bicycle for the rides by bike of the bike combo (see [SearchOptions.bikeCombo]):
 * relaxed 12, normal 16 (the default) or fast 20 km/h, or a value of one's own between
 * [MIN_KMH] and [MAX_KMH]. [kmh] is the one in use, set from the settings ([UxPrefs.bikeSpeedKmh]).
 */
object BikePace {
    const val RELAXED_KMH = 12
    const val NORMAL_KMH = 16
    const val FAST_KMH = 20
    const val MIN_KMH = 6
    const val MAX_KMH = 40

    /** The three paces offered, slowest first. */
    val PRESETS = intArrayOf(RELAXED_KMH, NORMAL_KMH, FAST_KMH)

    /** The pace in use, in km/h. */
    @Volatile
    var kmh: Int = NORMAL_KMH

    /** [value] within the paces the app accepts. */
    fun clamp(value: Int): Int = value.coerceIn(MIN_KMH, MAX_KMH)

    /** [kmh] in metres per second. */
    fun metresPerSecond(kmh: Int): Double = clamp(kmh) / 3.6

    /** [kmh] as the routing servers take it: metres per second with one decimal, a dot as separator. */
    fun speedParam(kmh: Int): String = String.format(Locale.ROOT, "%.1f", metresPerSecond(kmh))

    /** Whole minutes to cover [metres] at [kmh], at least 1 for any way at all. */
    fun minutesFor(metres: Double, kmh: Int): Int =
        if (metres <= 0.0) 0 else ceil(metres / metresPerSecond(kmh) / 60.0).toInt().coerceAtLeast(1)

    /** Which preset [kmh] is (0, 1, 2), or -1 for a pace of one's own. */
    fun presetIndex(kmh: Int): Int = PRESETS.indexOf(kmh)
}
