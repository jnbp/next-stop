package io.github.jnbp.nextstop.data

/**
 * The setting "Akkusparen" (battery saver): never, with the battery saver of the system
 * ([Auto], the default), or always. While it is active (see [batterySaverActive]) the app
 * draws fewer vehicles, refreshes more slowly ([batterySaverPace]), rests its idle animations
 * and shows plain surfaces instead of the frosted glass.
 */
enum class BatterySaverMode {
    Off, Auto, Always;

    companion object {
        /** The mode stored as [name]; [Auto] for none or an unknown one. */
        fun fromStored(name: String?): BatterySaverMode = entries.firstOrNull { it.name == name } ?: Auto
    }
}

/** Whether the battery saver of the app is active in [mode] while the system's saver is [systemSaver]. */
fun batterySaverActive(mode: BatterySaverMode, systemSaver: Boolean): Boolean = when (mode) {
    BatterySaverMode.Off -> false
    BatterySaverMode.Auto -> systemSaver
    BatterySaverMode.Always -> true
}

/**
 * How far the battery saver goes while it is active (see [BatterySaverMode] for when):
 * - [Save] ("Sparen", the default and what the saver did before there were levels): fewer
 *   vehicles, slower refreshes, no idle animations, plain surfaces instead of glass;
 * - [Extreme] ("Extrem"): on top of that no map on the start page and no vehicles at all, no
 *   animations, refreshes far apart, and widgets that load only when tapped.
 * Stored apart from the mode, so the mode chosen before keeps its meaning.
 */
enum class BatterySaverLevel {
    Save, Extreme;

    companion object {
        /** The level stored as [name]; [Save] for none or an unknown one. */
        fun fromStored(name: String?): BatterySaverLevel = entries.firstOrNull { it.name == name } ?: Save
    }
}

/** Whether the battery saver saves to the [BatterySaverLevel.Extreme] level right now. */
fun batterySaverExtreme(mode: BatterySaverMode, systemSaver: Boolean, level: BatterySaverLevel): Boolean =
    level == BatterySaverLevel.Extreme && batterySaverActive(mode, systemSaver)

/** How much longer the app waits between refreshes while the battery saver is active. */
const val BATTERY_SAVER_SLOWDOWN = 2L

/** ... and while it saves to the extreme level. */
const val BATTERY_SAVER_EXTREME_SLOWDOWN = 4L

/** The pause [millis] between two refreshes, longer while the battery saver is [active], longest when [extreme]. */
fun batterySaverPace(millis: Long, active: Boolean, extreme: Boolean = false): Long = when {
    extreme -> millis * BATTERY_SAVER_EXTREME_SLOWDOWN
    active -> millis * BATTERY_SAVER_SLOWDOWN
    else -> millis
}
