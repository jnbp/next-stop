package io.github.jnbp.nextstop.data


// Positions in a logged request keep this many decimals (about 100 m).
private const val LOG_DECIMALS = 3

private val LONG_NUMBER = Regex("(-?\\d+\\.\\d{$LOG_DECIMALS})\\d+")
private val PLACE_TEXT = Regex("((?:^|[?&])(?:[A-Za-z]+\\.(?:address|name)|query|text|input)=)[^&]*")

/**
 * A request as it may be written to the log: positions cut to [LOG_DECIMALS] decimals and
 * the names of addresses and places left out, so the log shows what was asked (products,
 * walking, change time) without the exact places of the user.
 */
fun loggableRequest(url: String): String =
    url.replace(PLACE_TEXT, "$1…")
        .replace(LONG_NUMBER, "$1")

/** Writes a journey request to the log under the tag "Routing" (see [loggableRequest]); verbose recording only. */
fun logJourneyRequest(provider: String, url: String) {
    try {
        DiagLog.d("Routing") { "$provider ${loggableRequest(url)}" }
    } catch (e: RuntimeException) {
        // Outside Android (tests on the JVM) there is no log to write to.
    }
}

// The longest request path written to the log.
private const val LOG_PATH_LENGTH = 160

/**
 * Writes the answer to a request to the log under the tag "Http" (verbose recording only): the
 * provider, the path without places (see [loggableRequest]), the status and the time it took
 * since [startedNanos] (System.nanoTime).
 */
fun logHttpAnswer(provider: String, path: String, code: Int, startedNanos: Long) {
    DiagLog.d("Http") {
        "$provider ${loggableRequest(path).take(LOG_PATH_LENGTH)} -> $code in ${(System.nanoTime() - startedNanos) / 1_000_000} ms"
    }
}
