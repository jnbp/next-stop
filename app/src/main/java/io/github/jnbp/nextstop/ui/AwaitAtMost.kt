package io.github.jnbp.nextstop.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull

/**
 * #261: [work] run in [scope], waited for at most [millis]: its result, or null once the time is
 * up, at that moment, even while [work] blocks a thread (the device's geocoder does): the work
 * is cancelled and left to end on its own in [scope]. A plain withTimeoutOrNull around a
 * blocking call waits until the call returns, however long that takes, so a loader shown
 * meanwhile (the train of "Adresse wird gesucht…") stayed on far beyond its time.
 */
suspend fun <T> awaitAtMost(millis: Long, scope: CoroutineScope, work: suspend () -> T): T? {
    val running = scope.async { work() }
    // Cancelled too when the caller is (its page left while waiting): nothing keeps running for it.
    try {
        return withTimeoutOrNull(millis) { running.await() }
    } finally {
        running.cancel()
    }
}
