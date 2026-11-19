package io.github.jnbp.nextstop.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.jnbp.nextstop.data.KeptPage
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.keptPageJson
import io.github.jnbp.nextstop.data.keptPageOf
import io.github.jnbp.nextstop.data.keptPageUsable
import io.github.jnbp.nextstop.data.startAfterClose
import java.io.File
import java.time.OffsetDateTime
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Keeps the open journey page in a small file, so that it comes back when Android ends the
 * process in the background (the trip of [TripState] and the state of AppRoot live in memory
 * only). Written when what is open changes and when the app leaves the screen, never per frame;
 * read once when the page is restored ([created] with a saved state). The file goes when
 * nothing is open any more, and a kept page older than ~12 hours or whose journey has arrived
 * is not shown again.
 */
object OpenJourneyKeeper {
    private const val FILE_NAME = "open_journey.json"

    // One thread writes; the latest page wins (see save).
    private val writer = Executors.newSingleThreadExecutor { task -> Thread(task, "OpenJourneyKeeper").apply { isDaemon = true } }

    @Volatile
    private var pending: KeptPage? = null

    // True until the first page of this process is created: a saved state then means Android
    // ended the process, not that the page was only made anew (rotation, theme).
    @Volatile
    private var processStart = true

    /**
     * The start of the page restored after the end of the process, until the connections of
     * that search close (see startAfterClose and [startClosed]); null otherwise.
     */
    @Volatile
    private var restoredOrigin: Stop? = null

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE_NAME)

    /**
     * Called from onCreate of the page: with [restored] (a saved state, as after the end of the
     * process) the kept page is read for AppRoot to take; a fresh start shows nothing of it.
     */
    fun created(context: Context, restored: Boolean) {
        pending = null
        val newProcess = processStart
        processStart = false
        if (!restored) return
        val page = try {
            val file = file(context)
            if (file.exists()) keptPageOf(file.readText()) else null
        } catch (e: Exception) {
            null
        }
        pending = page?.takeIf { keptPageUsable(it, System.currentTimeMillis(), OffsetDateTime.now()) }
        // A recreation (a rotation) after such a restore reads the same start again: it stays the restored one.
        val again = restoredOrigin != null && restoredOrigin?.id == pending?.origin?.id
        restoredOrigin = if (newProcess || again) pending?.origin else null
        ResumeLog.log(
            when {
                pending != null -> "kept journey page restored"
                page != null -> "kept journey page too old, dropped"
                else -> "no kept journey page"
            },
        )
    }

    /** The page to restore, once; null when there is none. */
    fun take(): KeptPage? {
        val page = pending
        pending = null
        return page
    }

    /**
     * The start to keep when the connections of a search close: [origin], unless it is the one
     * the restored page brought back (see startAfterClose). Forgets that one either way.
     */
    fun startClosed(origin: Stop?): Stop? {
        val kept = startAfterClose(origin, restoredOrigin)
        restoredOrigin = null
        return kept
    }

    // The page waiting to be written; a newer one replaces it, so a burst of saves (a change and
    // the app leaving the screen) is written once, with the latest. Null when none waits.
    private val waiting = AtomicReference<Waiting?>(null)

    private class Waiting(val file: File, val page: KeptPage?)

    /** Writes [page], or removes the file when nothing worth keeping is open. */
    fun save(context: Context, page: KeptPage?) {
        // Only one write is queued at a time: a page that waits is replaced, not queued behind.
        if (waiting.getAndSet(Waiting(file(context), page)) == null) writer.execute { writeWaiting() }
    }

    private fun writeWaiting() {
        val next = waiting.getAndSet(null) ?: return
        val file = next.file
        val page = next.page
        val temp = File(file.parentFile, "$FILE_NAME.tmp")
        try {
            if (page == null || page.empty) {
                file.delete()
            } else {
                temp.writeText(keptPageJson(page))
                if (!temp.renameTo(file)) {
                    file.delete()
                    temp.renameTo(file)
                }
            }
        } catch (e: Throwable) {
            // Only a copy: the page works without it. Never let it end the app, not even when
            // memory runs short (#72): the half-built text is garbage at once, the old file stays.
            temp.delete()
        }
    }
}

/**
 * Writes the open journey page of AppRoot through [OpenJourneyKeeper]: a moment after [key]
 * changes (what is open, not the live times) and whenever the app leaves the screen, with the
 * latest live data then. [snapshot] reads the page as it is.
 */
@Composable
internal fun KeepOpenJourney(key: () -> Any?, snapshot: () -> KeptPage) {
    val context = LocalContext.current
    val latest = rememberUpdatedState(snapshot)
    val latestKey = rememberUpdatedState(key)
    LaunchedEffect(Unit) {
        snapshotFlow { latestKey.value() }.distinctUntilChanged().collectLatest {
            delay(KEEP_DELAY_MILLIS)
            OpenJourneyKeeper.save(context, latest.value())
        }
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) OpenJourneyKeeper.save(context, latest.value())
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

// A change is written this long after the last one, so a burst of changes is written once.
private const val KEEP_DELAY_MILLIS = 1_500L
