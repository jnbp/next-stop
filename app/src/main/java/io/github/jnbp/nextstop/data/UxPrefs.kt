package io.github.jnbp.nextstop.data

import android.content.Context

/**
 * The settings of release 0.2 for comfort and battery (battery saver, haptics, the pace by
 * bicycle, appointments after their start), kept in the same file as [AppPrefs] but apart
 * from it, so they stay easy to find.
 */
class UxPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)

    /** The battery saver of the app (see [BatterySaverMode]); [BatterySaverMode.Auto] unless chosen. */
    var batterySaver: BatterySaverMode
        get() = BatterySaverMode.fromStored(prefs.getString(KEY_BATTERY_SAVER, null))
        set(value) = prefs.edit().putString(KEY_BATTERY_SAVER, value.name).apply()

    /** How far the battery saver goes while active (see [BatterySaverLevel]); [BatterySaverLevel.Save] unless chosen. */
    var batterySaverLevel: BatterySaverLevel
        get() = BatterySaverLevel.fromStored(prefs.getString(KEY_BATTERY_SAVER_LEVEL, null))
        set(value) = prefs.edit().putString(KEY_BATTERY_SAVER_LEVEL, value.name).apply()

    /** True (the default): taps on chips, switches and tabs give a light tick. */
    var haptics: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTICS, value).apply()

    /** True (the default): rides show on which side the sun shines in, where it really shines (see SunWeather). */
    var sunSide: Boolean
        get() = prefs.getBoolean(KEY_SUN_SIDE, true)
        set(value) = prefs.edit().putBoolean(KEY_SUN_SIDE, value).apply()

    /** Developer option: the hint of the sunny side shows on every ride, whatever the sun and the weather. */
    var sunSideTest: Boolean
        get() = prefs.getBoolean(KEY_SUN_SIDE_TEST, false)
        set(value) = prefs.edit().putBoolean(KEY_SUN_SIDE_TEST, value).apply()

    /** True once the explanation of the sunny side was seen (it opens with the first hint; #90). */
    var sunSideIntroSeen: Boolean
        get() = prefs.getBoolean(KEY_SUN_SIDE_INTRO_SEEN, false)
        set(value) = prefs.edit().putBoolean(KEY_SUN_SIDE_INTRO_SEEN, value).apply()

    /** How many rides the sun chip of the compact ride card was shown on, up to SUN_CHIP_LEARN_RIDES (#220). */
    var sunChipRides: Int
        get() = prefs.getInt(KEY_SUN_CHIP_RIDES, 0)
        set(value) = prefs.edit().putInt(KEY_SUN_CHIP_RIDES, value).apply()

    /** The pace by bicycle in km/h (see [BikePace]); [BikePace.NORMAL_KMH] unless chosen. */
    var bikeSpeedKmh: Int
        get() = BikePace.clamp(prefs.getInt(KEY_BIKE_SPEED, BikePace.NORMAL_KMH))
        set(value) = prefs.edit().putInt(KEY_BIKE_SPEED, BikePace.clamp(value)).apply()

    /** How long after its start an appointment is still offered as a destination, in minutes. */
    var appointmentAfterMinutes: Int
        get() = prefs.getInt(KEY_APPOINTMENT_AFTER, APPOINTMENT_AFTER_DEFAULT).coerceIn(0, APPOINTMENT_AFTER_MAX)
        set(value) = prefs.edit().putInt(KEY_APPOINTMENT_AFTER, value.coerceIn(0, APPOINTMENT_AFTER_MAX)).apply()

    companion object {
        private const val KEY_BATTERY_SAVER = "batterySaver"
        private const val KEY_BATTERY_SAVER_LEVEL = "batterySaverLevel"
        private const val KEY_HAPTICS = "haptics"
        private const val KEY_SUN_SIDE = "sunSide"
        private const val KEY_SUN_SIDE_TEST = "sunSideTest"
        private const val KEY_SUN_SIDE_INTRO_SEEN = "sunSideIntroSeen"
        private const val KEY_SUN_CHIP_RIDES = "sunChipRides"
        private const val KEY_BIKE_SPEED = "bikeSpeedKmh"
        private const val KEY_APPOINTMENT_AFTER = "appointmentAfterMinutes"
        const val APPOINTMENT_AFTER_DEFAULT = 60
        const val APPOINTMENT_AFTER_MAX = 180
    }
}
