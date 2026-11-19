package io.github.jnbp.nextstop

import android.app.Application
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.data.BikePace
import io.github.jnbp.nextstop.data.ChangeRatings
import io.github.jnbp.nextstop.data.UxPrefs
import io.github.jnbp.nextstop.ui.BatterySaver
import io.github.jnbp.nextstop.ui.DiagCapture
import io.github.jnbp.nextstop.ui.MapVehicleCache
import io.github.jnbp.nextstop.ui.SystemAnimation
import io.github.jnbp.nextstop.ui.PerfLab
import io.github.jnbp.nextstop.ui.PlannedTripAlarms
import io.github.jnbp.nextstop.ui.retireOldChannels
import io.github.jnbp.nextstop.ui.companion.CompanionLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The process of the app: whatever starts it (the launcher, a widget, the trip service, the
 * bridge to the watch, a worker), the settings the rating of changes depends on are in use
 * from the start (see AppPrefs.applyRatingSettings), so phone and watch never rate the same
 * change differently because the app itself was not opened. The diagnostic log and the crash
 * handler are installed here too (see DiagCapture).
 */
class NextStopApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // The diagnostic log and the crash file first, so they see everything after (see DiagCapture).
        DiagCapture.install(this)
        // The system's animator duration scale for every page, also one opened without MainActivity.
        SystemAnimation.read(this)
        val prefs = AppPrefs(this)
        prefs.applyRatingSettings()
        // Unlocked developer options open the performance lab in every build.
        PerfLab.unlock(prefs.developerOptions)
        // The battery saver follows the system's from the start (see BatterySaver).
        BatterySaver.install(this)
        // The companion view (calm, with map, classic) and its remembered map button.
        CompanionLayout.install(this)
        // The pace by bicycle of the bike combo, for every search (see BikePace).
        BikePace.kmh = UxPrefs(this).bikeSpeedKmh
        // The notification channels from before the five of today go (see ChannelKind).
        retireOldChannels(this)
        // The alarm of the planned trips, set again on every start (an alarm lost after a
        // force stop, exact alarms allowed meanwhile). Reads its file, so off the main thread.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { PlannedTripAlarms.schedule(this@NextStopApp) }
    }

    // Memory runs short: the kept ratings of changes are worked out again when asked for, and
    // the vehicles kept for the map (see VehicleSnapshots) go; they come with the next refresh.
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        ChangeRatings.clear()
        // Only leaving the screen is no shortage: kept, so coming back soon shows them at once.
        if (level != android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) MapVehicleCache.clear()
    }
}
