package io.github.jnbp.nextstop.data

import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout

// The tag of the answers and failures of the backends in the diagnostic log (see DiagLog).
private const val LOG_TAG = "Sources"

/**
 * One concrete server or library the app can get data from. The user orders them in the
 * settings; the app asks them in that order and falls back to the next one when one fails.
 *
 * [family] is the id space a backend shares with others: VBB HAFAS, the BVG community REST
 * API and public-transport-enabler all name stops by the same VBB numbers (900100003 for
 * Alexanderplatz), so a stop found by one can be asked about at another. Trip ids, refresh
 * tokens and page references are not shared: they carry [tag] in front (BVG none, as the app
 * used it alone before), so a later request about them goes back to the backend they came from.
 * [key] is stored in the settings and must stay as it is.
 */
enum class Backend(val key: String, val family: DataSource, val tag: String) {
    Vbb("vbb", DataSource.Bvg, "vbb~"),
    Bvg("bvg", DataSource.Bvg, ""),
    Pte("pte", DataSource.Bvg, "pte~"),
    Transitous("transitous", DataSource.Transitous, ""),
}

/** The backend a tagged id, token or reference of [family] came from (see [Backend.tag]). */
fun backendOf(family: DataSource, value: String): Backend {
    val members = Backend.entries.filter { it.family == family }
    return members.firstOrNull { it.tag.isNotEmpty() && value.startsWith(it.tag) }
        ?: members.first { it.tag.isEmpty() }
}

/** A backend that cannot be asked because it is not set up (no access key, say). It is skipped. */
class NotConfiguredException(val backend: Backend) : IOException("${backend.key} not configured")

/** A backend that cannot answer this kind of request at all (no radar, say). It is skipped. */
class NotSupportedException(what: String) : IOException("$what not supported")

/**
 * How the backends fared lately, shared by every [SourceChain] of the process (the app, its
 * widgets and its services each make their own sources): a backend that failed [failuresToTrip]
 * times in a row is skipped for [backoffMillis], then tried again. [answered] is the backend
 * that answered the last request when it was not the first choice for it, for the hint
 * "Daten: Transitous"; null while the first choice answers.
 */
class SourceHealth(
    private val clock: () -> Long = System::currentTimeMillis,
    val failuresToTrip: Int = 2,
    val backoffMillis: Long = 2 * 60_000L,
) {
    private class State(var failures: Int = 0, var until: Long = 0L)

    private val states = ConcurrentHashMap<Backend, State>()
    private val _answered = MutableStateFlow<Backend?>(null)
    val answered: StateFlow<Backend?> = _answered.asStateFlow()

    /** True while [backend] is skipped after repeated failures. */
    fun resting(backend: Backend): Boolean = (states[backend]?.until ?: 0L) > clock()

    fun succeeded(backend: Backend) {
        states.remove(backend)
    }

    fun failed(backend: Backend) {
        val state = states.getOrPut(backend) { State() }
        synchronized(state) {
            state.failures++
            if (state.failures >= failuresToTrip) {
                state.until = clock() + backoffMillis
                state.failures = 0
            }
        }
    }

    /** Remembers who answered a request whose first choice was [first]. */
    fun record(first: Backend, answered: Backend) {
        _answered.value = if (answered == first) null else answered
    }

    /** Forgets every failure, for a changed setting. */
    fun reset() {
        states.clear()
        _answered.value = null
    }

    companion object {
        /** The one of the app. */
        val shared = SourceHealth()
    }
}

/**
 * Asks backends one after the other until one answers. A backend is left for the next one
 * when it fails: no network, a timeout, a server error, an answer it cannot read, or not set
 * up. "Nothing found" (HTTP 404) is an answer and goes to the caller as it is. A backend that
 * keeps failing rests for a while (see [SourceHealth]); when all of them rest, they are asked
 * anyway. Each backend gets a few seconds while another one could still stand in; the last
 * one gets as long as its own connection allows.
 */
class SourceChain(
    private val health: SourceHealth = SourceHealth.shared,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    /**
     * The answer of the first of [candidates] that has one, by [block]. [candidates] are the
     * backends set up for the request, best first; when another than the first answers and
     * [record] is set, it is remembered for the hint. Throws [SourceUnreachableException]
     * naming every backend tried when none answered. With [cutLast] the last one is held to
     * [timeoutMillis] as well, for a caller that has a stand-in of its own after the chain.
     */
    suspend fun <T> first(
        family: DataSource,
        candidates: List<Backend>,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
        record: Boolean = true,
        cutLast: Boolean = false,
        block: suspend (Backend) -> T,
    ): T {
        if (candidates.isEmpty()) throw SourceUnreachableException(family, IOException("no source"), emptyList())
        val awake = candidates.filter { !health.resting(it) }
        // When all rest, they are asked anyway: better a late answer than none.
        val order = awake.ifEmpty { candidates }
        val tried = mutableListOf<Backend>()
        var lastError: Throwable? = null
        for ((index, backend) in order.withIndex()) {
            val more = index < order.lastIndex || cutLast
            val started = System.nanoTime()
            try {
                val answer = attempt(backend, if (more) timeoutMillis else 0L, block)
                DiagLog.d(LOG_TAG) { "${family.key}: ${backend.key} answered in ${millisSince(started)} ms" }
                health.succeeded(backend)
                if (record) health.record(candidates.first(), backend)
                return answer
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpStatusException) {
                if (e.nothingFound) {
                    health.succeeded(backend)
                    throw e
                }
                DiagLog.w(LOG_TAG) { "${family.key}: ${backend.key} failed after ${millisSince(started)} ms: HTTP ${e.code}" }
                tried.add(backend)
                health.failed(backend)
                lastError = e
            } catch (e: NotConfiguredException) {
                lastError = lastError ?: e
            } catch (e: NotSupportedException) {
                lastError = lastError ?: e
            } catch (e: Exception) {
                DiagLog.w(LOG_TAG) { "${family.key}: ${backend.key} failed after ${millisSince(started)} ms: ${e.javaClass.simpleName}" }
                tried.add(backend)
                health.failed(backend)
                lastError = e
            }
        }
        DiagLog.w(LOG_TAG) { "${family.key}: no source answered (tried ${tried.joinToString { it.key }})" }
        throw SourceUnreachableException(family, lastError ?: IOException("no source"), tried)
    }

    private fun millisSince(startedNanos: Long): Long = (System.nanoTime() - startedNanos) / 1_000_000

    private suspend fun <T> attempt(backend: Backend, timeoutMillis: Long, block: suspend (Backend) -> T): T {
        if (timeoutMillis <= 0L) return block(backend)
        // The clients block on their connections, which a timeout cannot cut: the request runs
        // on apart and its late answer is dropped.
        val running = scope.async { block(backend) }
        return try {
            withTimeout(timeoutMillis) { running.await() }
        } catch (e: TimeoutCancellationException) {
            running.cancel()
            throw SourceTimeoutException(backend, timeoutMillis)
        } catch (e: CancellationException) {
            // The caller gave up: the request it no longer waits for is given up too.
            running.cancel()
            throw e
        }
    }

    companion object {
        /** How long a backend may take while another one could still stand in. */
        const val DEFAULT_TIMEOUT_MILLIS = 6_000L

        /** The same for journey searches, which take longer on every backend. */
        const val JOURNEY_TIMEOUT_MILLIS = 10_000L
    }
}

/** A backend that took longer than its share of time. Not a cancellation: the next one is asked. */
class SourceTimeoutException(backend: Backend, millis: Long) : IOException("${backend.key} took over $millis ms")
