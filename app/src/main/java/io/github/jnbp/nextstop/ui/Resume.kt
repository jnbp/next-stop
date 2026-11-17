package io.github.jnbp.nextstop.ui

import android.os.SystemClock
import io.github.jnbp.nextstop.data.DiagLog

/**
 * How the page of the app comes back. A page that comes back (the screen was off, another
 * app was in front) or is built again (the system ended the process meanwhile and the page
 * is restored, or the configuration changed) is no start: it shows what it showed at once,
 * without the splash screen, the entrance of the map or the intro of the start page, and
 * takes touches from its first frame.
 */
object Resume {
    /** How many times the page was created in this process. */
    var creations: Int = 0
        private set

    /**
     * True when this page was built again rather than started: restored after the end of the
     * process or after a change of the configuration. The map then shows as soon as it has
     * drawn, without its entrance.
     */
    @Volatile
    var rebuilt: Boolean = false
        private set

    /**
     * Notes the creation of the page; [restored] when it gets a saved state. Returns true when
     * this is a real start, the only one that keeps the splash screen and plays the intro: the
     * first page of a fresh process that was not restored.
     */
    fun created(restored: Boolean): Boolean {
        val fresh = creations == 0 && !restored && !Startup.splashDone
        creations++
        rebuilt = !fresh
        if (!fresh) skipIntro()
        ResumeLog.log(
            when {
                fresh -> "create: cold start"
                restored && creations == 1 -> "create: restored after the end of the process"
                restored -> "create: rebuilt (configuration change)"
                else -> "create: warm start (process kept)"
            },
        )
        return fresh
    }

    /** The start is over: nothing of it plays again on this page. */
    private fun skipIntro() {
        Startup.splashDone = true
        Startup.introPlayed = true
        Startup.signsIn = true
        Startup.vehiclesIn = true
        Startup.noticesIn = true
        Startup.navIn = true
        if (Startup.mapShownAt == 0L) Startup.mapShownAt = SystemClock.uptimeMillis() - 10_000L
    }
}

/**
 * The log of how the page comes back to the screen, in debug and staging builds and with the
 * verbose recording of the developer options (tag NextStopResume, like BackLog): the time from onStart to the first frame drawn and to the first
 * touch, and what came in after it (the map, the refreshes). All times are milliseconds since
 * the last onStart.
 */
internal object ResumeLog {
    const val TAG = "NextStopResume"

    val on: Boolean get() = PerfLab.labBuild || DiagLog.verbose

    // Uptime of the last onStart; 0 before the first one.
    @Volatile
    private var startedAt = 0L

    // What was reported once since the last onStart.
    private val seen = HashSet<String>()

    /** The page is on its way to the screen again. */
    fun started(kind: String) {
        startedAt = SystemClock.uptimeMillis()
        synchronized(seen) { seen.clear() }
        log("onStart ($kind)")
    }

    /** Milliseconds since the last onStart. */
    fun since(): Long = if (startedAt == 0L) -1L else SystemClock.uptimeMillis() - startedAt

    /** Logs [what] with the time since onStart, only the first time since then. */
    fun once(what: String, detail: String = "") {
        if (!on || startedAt == 0L) return
        val first = synchronized(seen) { seen.add(what) }
        if (!first) return
        log("$what after ${since()} ms${if (detail.isEmpty()) "" else " ($detail)"}")
    }

    fun log(message: String) {
        if (!on) return
        DiagLog.d(TAG) { message }
    }
}
