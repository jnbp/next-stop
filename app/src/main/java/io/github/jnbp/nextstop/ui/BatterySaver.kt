package io.github.jnbp.nextstop.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.jnbp.nextstop.data.BatterySaverLevel
import io.github.jnbp.nextstop.data.BatterySaverMode
import io.github.jnbp.nextstop.data.DiagLog
import io.github.jnbp.nextstop.data.UxPrefs
import io.github.jnbp.nextstop.data.batterySaverActive
import io.github.jnbp.nextstop.data.batterySaverExtreme
import io.github.jnbp.nextstop.data.batterySaverPace
import io.github.jnbp.nextstop.ui.theme.MapVehiclesChoice

private const val LOG_TAG = "BatterySaver"

/**
 * The battery saver of the app while it runs (see [BatterySaverMode]): the one place every
 * consumer asks. [active] is snapshot state, so a composable or a draw lambda that reads it
 * follows a change of the setting or of the system's saver at once; loops read [pace] on
 * every round. What it does while active ("Sparen", and "Automatisch" while the system saves):
 * - no map behind the start page and the pages of a search (plain ground; the map is not even
 *   made while it is not shown, see MapBackground); the tab "Karte" still shows the map,
 * - no live vehicles on any map ([mapVehicles]),
 * - the companion calm and without the map (CompanionLayout),
 * - widgets load only when tapped or when the app opens (WidgetUpdates),
 * - no position in the background: the companion goes by the times (CompanionLocation),
 * - the check "Unterwegs prüfen" pauses (DisruptionWatch),
 * - refreshes wait longer ([pace]), idle animations rest ([rememberIdleAnimationsOn]), glass
 *   surfaces show their plain fallback.
 * At the level "Extrem" ([extreme], see [BatterySaverLevel]) on top of that: no animations at
 * all ([rememberAnimationsOn]), the position only when asked for (the locate button), widgets
 * only when tapped, refreshes far apart.
 */
object BatterySaver {
    /** The setting as chosen. */
    var mode by mutableStateOf(BatterySaverMode.Auto)
        private set

    /** How far it goes while active. */
    var level by mutableStateOf(BatterySaverLevel.Save)
        private set

    /** The battery saver of the system is on. */
    var systemSaver by mutableStateOf(false)
        private set

    /** True while the app saves battery. */
    val active: Boolean get() = batterySaverActive(mode, systemSaver)

    /** True while the app saves at the level "Extrem". */
    val extreme: Boolean get() = batterySaverExtreme(mode, systemSaver, level)

    private var installed = false

    /** Reads the setting and follows the system's battery saver from now on; once per process. */
    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val prefs = UxPrefs(app)
        mode = prefs.batterySaver
        level = prefs.batterySaverLevel
        val power = app.getSystemService(PowerManager::class.java)
        systemSaver = power?.isPowerSaveMode == true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val on = power?.isPowerSaveMode == true
                if (on != systemSaver) DiagLog.i(LOG_TAG) { "system saver ${if (on) "on" else "off"} (mode $mode)" }
                val before = active
                systemSaver = on
                followed(app, before)
            }
        }
        val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                app.registerReceiver(receiver, filter)
            }
        } catch (e: Exception) {
            DiagLog.w(LOG_TAG, e) { "cannot follow the system saver" }
        }
    }

    /** Chooses and stores [value]. */
    fun change(context: Context, value: BatterySaverMode) {
        val before = active
        mode = value
        UxPrefs(context).batterySaver = value
        DiagLog.i(LOG_TAG) { "mode $value, active $active" }
        followed(context.applicationContext, before)
    }

    /**
     * What follows when the saver starts or ends ([before] is whether it was active): the
     * check "Unterwegs prüfen" pauses or runs again, and the position of the companion stops
     * (or starts again for a trip that is followed).
     */
    private fun followed(app: Context, before: Boolean) {
        val now = active
        if (now == before) return
        try {
            DisruptionWatch.schedule(app)
        } catch (e: Exception) {
            DiagLog.w(LOG_TAG, e) { "cannot plan the disruption check" }
        }
        if (now) {
            CompanionLocation.stop()
        } else if (TripState.serviceRunning.value) {
            CompanionLocation.start(app)
        }
    }

    /** Chooses and stores the [value] of the level. */
    fun changeLevel(context: Context, value: BatterySaverLevel) {
        level = value
        UxPrefs(context).batterySaverLevel = value
        DiagLog.i(LOG_TAG) { "level $value, extreme $extreme" }
    }

    /** The pause [millis] between two refreshes, longer while active, longest at "Extrem". */
    fun pace(millis: Long): Long = batterySaverPace(millis, active, extreme)

    /** Which vehicles the map shows: none while active (no live vehicles while saving). */
    fun mapVehicles(choice: MapVehiclesChoice): MapVehiclesChoice = if (active) MapVehiclesChoice.Off else choice
}

/**
 * True where idle animations may run: the loading train, shimmering placeholders, pulsing
 * dots, a breathing logo. False with the system's animations off and while the battery saver
 * is active; transitions that follow a tap are not idle and keep [rememberAnimationsOn].
 */
@Composable
fun rememberIdleAnimationsOn(): Boolean = rememberAnimationsOn() && !BatterySaver.active
