package io.github.jnbp.nextstop.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * The diagnostic log of the app: an always-on, cheap ring of the last events, so a bug report
 * can show what happened before something went wrong, even weeks into use. Every own log call
 * of the app goes through [DiagLog]; it keeps the event and forwards it to Logcat.
 *
 * Cost: a write is O(1) into preallocated arrays, under a short lock; the only allocation is
 * the message string, which is built by an inline lambda and only when the event is kept.
 * Debug events ([DiagLog.d]) are not even built unless the verbose recording is on (or the
 * build is a debug or staging build, which logs them to Logcat as before).
 */

/**
 * A ring of the last [capacity] events: time, level, tag and message in preallocated arrays.
 * Thread-safe; pure Kotlin, so it is tested on the JVM.
 */
class DiagRing(val capacity: Int) {
    private val times = LongArray(capacity)
    private val levels = CharArray(capacity)
    private val tags = arrayOfNulls<String>(capacity)
    private val messages = arrayOfNulls<String>(capacity)

    // The slot the next event goes to, and how many slots hold an event.
    private var next = 0
    private var count = 0

    /** Events written since the start, kept or overwritten. */
    var written = 0L
        private set

    val size: Int get() = synchronized(this) { count }

    /** Keeps one event, overwriting the oldest when full. */
    fun add(time: Long, level: Char, tag: String, message: String) {
        synchronized(this) {
            val at = next
            times[at] = time
            levels[at] = level
            tags[at] = tag
            messages[at] = message
            next = if (at + 1 == capacity) 0 else at + 1
            if (count < capacity) count++
            written++
        }
    }

    fun clear() {
        synchronized(this) {
            java.util.Arrays.fill(tags, null)
            java.util.Arrays.fill(messages, null)
            next = 0
            count = 0
        }
    }

    /** The events kept, oldest first, each as one line (see [formatLine]); for a report, not for hot paths. */
    fun lines(): List<String> {
        val format = lineFormat()
        synchronized(this) {
            val out = ArrayList<String>(count)
            var at = if (count < capacity) 0 else next
            repeat(count) {
                out.add(formatLine(format, times[at], levels[at], tags[at].orEmpty(), messages[at].orEmpty()))
                at = if (at + 1 == capacity) 0 else at + 1
            }
            return out
        }
    }

    companion object {
        /** The format of the time of a line: the date with milliseconds, in the zone of the phone. */
        fun lineFormat(): SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)

        /** One event as a line: "2026-11-17 01:02:03.456 W/Tag: message" (line breaks of the message indented). */
        fun formatLine(format: SimpleDateFormat, time: Long, level: Char, tag: String, message: String): String =
            "${format.format(Date(time))} $level/$tag: ${message.replace("\n", "\n    ")}"
    }
}

/** Forwards an event to the log of the system (Logcat); set by the app, null on the JVM. */
fun interface DiagSink {
    fun write(level: Char, tag: String, message: String, error: Throwable?)
}

/**
 * The log of the app (see the top of this file). Use `DiagLog.i(TAG) { "message $x" }`: the
 * lambda is inlined and only runs when the event is kept, so a call costs nearly nothing.
 * Never call it in per-frame paths.
 */
object DiagLog {
    /** The number of events kept. */
    const val CAPACITY = 2000

    @PublishedApi
    internal val ring = DiagRing(CAPACITY)

    /**
     * The verbose recording (the switch "Detailed recording"): debug events are kept too. Off by
     * default; set by the app from the settings (it switches itself off after a day).
     */
    @JvmField
    @Volatile
    var verbose = false

    /** Debug events go to Logcat even without the verbose recording (debug and staging builds). */
    @JvmField
    @Volatile
    var debugToLogcat = false

    /** Where events are forwarded to; null: kept in the ring only. */
    @JvmField
    @Volatile
    var sink: DiagSink? = null

    /** True when a debug event would be kept or forwarded; for building costlier messages only then. */
    val debugOn: Boolean get() = verbose || debugToLogcat

    /** A debug event: only built while [debugOn]. */
    inline fun d(tag: String, message: () -> String) {
        if (verbose || debugToLogcat) record('D', tag, message(), null)
    }

    /** An event worth keeping always (rare: once per load, request or state change). */
    inline fun i(tag: String, message: () -> String) {
        record('I', tag, message(), null)
    }

    /** A warning, with the error that caused it if there is one. */
    inline fun w(tag: String, error: Throwable? = null, message: () -> String) {
        record('W', tag, message(), error)
    }

    /** An error, with the error that caused it if there is one. */
    inline fun e(tag: String, error: Throwable? = null, message: () -> String) {
        record('E', tag, message(), error)
    }

    @PublishedApi
    internal fun record(level: Char, tag: String, message: String, error: Throwable?) {
        // A debug event is kept only with the verbose recording; without it, it only went to Logcat.
        if (level != 'D' || verbose) {
            ring.add(System.currentTimeMillis(), level, tag, if (error == null) message else "$message: $error")
        }
        val out = sink ?: return
        try {
            out.write(level, tag, message, error)
        } catch (e: RuntimeException) {
            // No log of the system (tests on the JVM): the ring has the event.
        }
    }

    /** The events kept, oldest first. */
    fun lines(): List<String> = ring.lines()

    val size: Int get() = ring.size

    /** Events recorded since the process started, kept or overwritten. */
    fun ringWritten(): Long = ring.written

    fun clear() = ring.clear()
}
