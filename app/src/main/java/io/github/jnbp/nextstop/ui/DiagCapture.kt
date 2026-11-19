package io.github.jnbp.nextstop.ui

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.core.content.FileProvider
import io.github.jnbp.nextstop.BuildConfig
import io.github.jnbp.nextstop.data.AppLanguage
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.data.BuiltInKeys
import io.github.jnbp.nextstop.data.DiagLog
import io.github.jnbp.nextstop.data.DiagRing
import io.github.jnbp.nextstop.data.DiagRedaction
import io.github.jnbp.nextstop.data.DiagReportInput
import io.github.jnbp.nextstop.data.DiagSink
import io.github.jnbp.nextstop.data.LiftKeys
import io.github.jnbp.nextstop.data.diagReportText
import java.io.File
import java.io.IOException
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/*
 * The Android side of the diagnostic log (see data/DiagLog.kt): forwarding to Logcat, keeping
 * the log across the end of the process (written to the cache when the app goes to the
 * background and on a crash), the crash file, the verbose recording and the bug report of the
 * developer options. Nothing here sends anything anywhere: a report goes where the user shares it.
 */
object DiagCapture {
    private const val TAG = "NextStop"

    // The log of the last processes, in the cache; the last crash, in the files (kept until deleted).
    private const val RING_FILE = "diag/ring.txt"
    private const val CRASH_FILE = "diag/last-crash.txt"

    // Where a report is written for sharing; must match res/xml/diag_paths.xml.
    private const val REPORT_DIR = "diag/report"
    private const val REPORT_NAME = "next-stop-report.txt"

    // The provider that hands a report to the app the user shares it with (see AndroidManifest.xml).
    private const val AUTHORITY_SUFFIX = ".diag"

    // The verbose recording switches itself off this long after it was switched on.
    const val VERBOSE_MILLIS = 24L * 60 * 60 * 1000
    private const val PREFS = "next-stop-diag"
    private const val KEY_VERBOSE_UNTIL = "verboseUntil"

    // Characters of a report given as text beside the file.
    private const val SHARE_TEXT_LIMIT = 4000

    // Log lines that go into the crash file with the stack trace.
    private const val CRASH_LOG_LINES = 300

    private var app: Context? = null

    // The log of earlier processes as read from RING_FILE, once (null: not read yet); guarded by persistLock.
    private var carried: List<String>? = null
    private val persistLock = Any()

    // Activities started right now: 0 means the app is in the background.
    private var started = 0

    /** Unix millis until which the verbose recording runs; 0 when it is off. */
    var verboseUntil = 0L
        private set

    /** Called once by the application as the process starts. */
    fun install(application: Application) {
        app = application
        DiagLog.debugToLogcat = PerfLab.labBuild
        DiagLog.sink = DiagSink { level, tag, message, error ->
            when (level) {
                'D' -> Log.d(tag, message, error)
                'W' -> Log.w(tag, message, error)
                'E' -> Log.e(tag, message, error)
                else -> Log.i(tag, message, error)
            }
        }
        verboseUntil = prefs(application).getLong(KEY_VERBOSE_UNTIL, 0L)
        refreshVerbose()
        installCrashHandler()
        application.registerActivityLifecycleCallbacks(Lifecycle)
        DiagLog.i(TAG) { "process start: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE}" }
    }

    /** Switches the verbose recording on (for [VERBOSE_MILLIS]) or off. */
    fun setVerbose(context: Context, on: Boolean) {
        verboseUntil = if (on) System.currentTimeMillis() + VERBOSE_MILLIS else 0L
        prefs(context).edit().putLong(KEY_VERBOSE_UNTIL, verboseUntil).apply()
        if (!on) DiagLog.i(TAG) { "verbose recording off" }
        DiagLog.verbose = on
        if (on) DiagLog.i(TAG) { "verbose recording on" }
    }

    // Ends the verbose recording once its day is over; checked as the app comes to the front.
    private fun refreshVerbose() {
        val on = verboseUntil > System.currentTimeMillis()
        if (!on && verboseUntil != 0L) {
            verboseUntil = 0L
            app?.let { prefs(it).edit().putLong(KEY_VERBOSE_UNTIL, 0L).apply() }
            DiagLog.i(TAG) { "verbose recording ended after a day" }
        }
        DiagLog.verbose = on
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private object Lifecycle : Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            if (started++ == 0) {
                refreshVerbose()
                DiagLog.i(TAG) { "foreground" }
            }
        }

        override fun onActivityStopped(activity: Activity) {
            if (--started <= 0) {
                started = 0
                DiagLog.i(TAG) { "background" }
                // The process may end in the background: the log is kept in the cache.
                thread(name = "NextStopDiag") { persist() }
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                writeCrash(thread, error)
            } catch (t: Throwable) {
                // Writing the crash must never hide it.
            }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    private fun writeCrash(thread: Thread, error: Throwable) {
        val context = app ?: return
        DiagLog.e("Crash", error) { "uncaught exception in thread ${thread.name}" }
        val text = buildString {
            append("Time: ").append(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", java.util.Locale.ROOT).format(java.util.Date()))
            append("\nApp: ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(") ").append(BuildConfig.BUILD_TYPE)
            append("\nThread: ").append(thread.name)
            append("\n\n").append(error.stackTraceToString())
            append("\nLast log lines:\n")
            for (line in DiagLog.lines().takeLast(CRASH_LOG_LINES)) append(line).append('\n')
        }
        val file = File(context.filesDir, CRASH_FILE)
        file.parentFile?.mkdirs()
        file.writeText(text)
        persist()
    }

    /** The time of the last crash (Unix millis), null when there is none. */
    fun lastCrashTime(context: Context): Long? = File(context.filesDir, CRASH_FILE).takeIf { it.isFile }?.lastModified()

    /** The last crash as written, null when there is none. */
    fun lastCrash(context: Context): String? = try {
        File(context.filesDir, CRASH_FILE).takeIf { it.isFile }?.readText()
    } catch (e: IOException) {
        null
    }

    fun deleteCrash(context: Context) {
        File(context.filesDir, CRASH_FILE).delete()
    }

    /** Writes the log (that of earlier processes and this one) to the cache; off the main thread. */
    fun persist() {
        val context = app ?: return
        synchronized(persistLock) {
            val lines = allLines(context)
            try {
                val file = File(context.cacheDir, RING_FILE)
                file.parentFile?.mkdirs()
                val temp = File(file.path + ".tmp")
                temp.bufferedWriter().use { out -> for (line in lines) out.append(line).append('\n') }
                if (!temp.renameTo(file)) temp.delete()
            } catch (e: IOException) {
                // The cache is full or gone: the log stays in memory.
            }
        }
    }

    // The log of earlier processes followed by this one, at most DiagLog.CAPACITY lines; under persistLock.
    private fun allLines(context: Context): List<String> {
        val before = carried ?: try {
            File(context.cacheDir, RING_FILE).takeIf { it.isFile }?.readLines()
                ?.let { lines -> DiagRing.entriesOf(lines) }?.takeLast(DiagLog.CAPACITY).orEmpty()
        } catch (e: IOException) {
            emptyList()
        }.also { carried = it }
        val now = DiagLog.lines()
        return if (before.isEmpty()) now else (before + now).takeLast(DiagLog.CAPACITY)
    }

    /** Events in the log, with those kept from earlier processes; off the main thread. */
    fun eventCount(context: Context): Int = synchronized(persistLock) { allLines(context).size }

    /** Empties the log, also that kept from earlier processes. */
    fun clearLog(context: Context) {
        synchronized(persistLock) {
            DiagLog.clear()
            carried = emptyList()
            File(context.cacheDir, RING_FILE).delete()
        }
    }

    /** What goes into a report, read now; off the main thread. */
    fun reportInput(context: Context, note: String): DiagReportInput {
        val prefs = AppPrefs(context)
        val activities = context.getSystemService(ActivityManager::class.java)
        val about = buildList {
            add("Version" to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            add("Build type" to BuildConfig.BUILD_TYPE)
            add("Device" to "${Build.MANUFACTURER} ${Build.MODEL}")
            add("Android" to "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            if (activities != null) {
                add("Memory class" to "${activities.memoryClass} MB (large ${activities.largeMemoryClass} MB, low RAM ${activities.isLowRamDevice})")
            }
            add("Max heap" to "${Runtime.getRuntime().maxMemory() / (1024 * 1024)} MB")
            add("Process uptime" to "${(SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()) / 60_000} min")
            add("Language" to AppLanguage.code)
            add("Verbose recording" to if (DiagLog.verbose) "on" else "off")
            add("Log events written in this process" to DiagLog.ringWritten().toString())
        }
        val choice = prefs.sourceChoice
        val lifts = LiftKeys(context)
        val settings = buildList {
            add("Data sources (in order)" to choice.ordered.joinToString { b -> b.key + if (b in choice.enabled) " on" else " off" })
            add("Own VBB key" to (choice.vbbAccessId.isNotBlank()).toString())
            add("Own PTE authorization" to (choice.pteAuthorization.isNotBlank()).toString())
            add("Own server" to prefs.ownServer.isNotBlank().toString())
            add("Own lift keys" to (lifts.accessibilityCloudToken.isNotBlank() || lifts.dbApiKey.isNotBlank()).toString())
            add("Delay display" to prefs.delayDisplay.toString())
            add("Map vehicles" to prefs.mapVehicles.toString())
            add("Map style" to prefs.mapStyle.toString())
            add("Dark mode" to prefs.darkChoice.toString())
            add("Start: map/classic/cards/departures" to "${prefs.startMap}/${prefs.startClassic}/${prefs.startCards}/${prefs.startDepartures}")
            add("Alight reminders" to prefs.alightReminders.toString())
            add("Boarding hints" to prefs.boardingHints.toString())
            add("Transfer choice" to prefs.transferChoice.toString())
            add("Station details" to prefs.stationDetails.toString())
            add("Map notices" to prefs.mapNotices.toString())
            add("Simulate faster" to prefs.simulateFaster.toString())
        }
        val log = synchronized(persistLock) { allLines(context) }
        return DiagReportInput(
            time = System.currentTimeMillis(),
            note = note,
            about = about,
            settings = settings,
            crash = lastCrash(context),
            log = log,
        )
    }

    /** What must not leave the phone: keys, the user's places and names (see DiagRedaction). */
    fun redaction(context: Context): DiagRedaction {
        val prefs = AppPrefs(context)
        val choice = prefs.sourceChoice
        val lifts = LiftKeys(context)
        val secrets = listOf(
            choice.vbbAccessId, choice.pteAuthorization,
            BuiltInKeys.vbbAccessId, BuiltInKeys.accessibilityCloudToken, BuiltInKeys.fastaClientId, BuiltInKeys.fastaApiKey,
            lifts.accessibilityCloudToken, lifts.dbClientId, lifts.dbApiKey,
        )
        val terms = buildList {
            add(prefs.ownServer)
            add(prefs.liveBaseUrl)
            add(prefs.liveName)
            for (favourite in prefs.loadFavoritePlaces()) {
                add(favourite.name)
                favourite.place?.let { add(it.name) }
            }
            for (saved in prefs.loadSavedPlaces()) {
                add(saved.name)
                add(saved.place.name)
            }
        }
        return DiagRedaction(secrets, terms)
    }

    /** The report as shared: redacted; off the main thread. */
    fun reportText(context: Context, input: DiagReportInput): String = diagReportText(input, redaction(context))

    /** Writes [text] to a file and opens the share sheet with it; on the main thread. */
    fun share(context: Context, text: String, subject: String) {
        val file = File(File(context.cacheDir, REPORT_DIR), REPORT_NAME)
        try {
            file.parentFile?.mkdirs()
            file.writeText(text)
        } catch (e: IOException) {
            DiagLog.w(TAG, e) { "report not written" }
            return
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_STREAM, uri)
            // For apps that take text only: the part before the log.
            .putExtra(Intent.EXTRA_TEXT, text.substringBefore("\n## Log").take(SHARE_TEXT_LIMIT))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(subject, uri)
        try {
            context.startActivity(Intent.createChooser(send, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            DiagLog.w(TAG) { "no app to share the report with" }
        }
    }
}
