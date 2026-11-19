package io.github.jnbp.nextstop.wear.data

/**
 * Why the watch has no journeys or departures to show (#196): the sources answered and found
 * nothing, or none could be reached at all (no network, or every one timed out or refused).
 * Only the latter says "Dienste nicht erreichbar".
 */
enum class LoadFailure {
    /** A source answered: there is nothing (or the answer was of no use). */
    NothingFound,

    /** No source reached, and the watch's own request found no network. */
    NoNetwork,

    /** No source reached: the phone's sources and the watch's own all failed or timed out. */
    ServicesDown,
}

/**
 * The [LoadFailure] after the phone and the watch's own request both had nothing.
 * [phoneAnswered]: a phone with Next Stop answered at all; [phoneDown]: its answer said that
 * none of its sources could be reached (see [phoneAnswerDown]). [localDown]: the watch's own
 * request could not reach its service (no network, a timeout, HTTP 5xx); [localOffline]: the
 * reason was that there was no network. All failed only when the local request failed that
 * way and the phone either did not answer or could not reach its sources either.
 */
fun loadFailureOf(phoneAnswered: Boolean, phoneDown: Boolean, localDown: Boolean, localOffline: Boolean): LoadFailure = when {
    !localDown -> LoadFailure.NothingFound
    phoneAnswered && !phoneDown -> LoadFailure.NothingFound
    localOffline -> LoadFailure.NoNetwork
    else -> LoadFailure.ServicesDown
}

/**
 * True when the answer of the phone (`"ok": false`) says that it reached none of its sources:
 * its flag "down" (a current phone app), else, from an older one, the name of the failure it
 * sends ("SourceUnreachableException: ...") or "timeout".
 */
fun phoneAnswerDown(ok: Boolean, down: Boolean?, error: String?): Boolean = when {
    ok -> false
    down != null -> down
    error == null -> false
    else -> error == "timeout" || error.startsWith("SourceUnreachableException") ||
        error.startsWith("UnknownHostException") || error.startsWith("SocketTimeoutException") ||
        error.startsWith("ConnectException")
}
