package io.github.jnbp.nextstop

import android.app.Application
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.data.ChangeRatings
import io.github.jnbp.nextstop.ui.DiagCapture
import io.github.jnbp.nextstop.ui.PerfLab

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
        val prefs = AppPrefs(this)
        prefs.applyRatingSettings()
        // Unlocked developer options open the performance lab in every build.
        PerfLab.unlock(prefs.developerOptions)
    }

    // Memory runs short: the kept ratings of changes are worked out again when asked for.
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        ChangeRatings.clear()
    }
}
