package io.github.jnbp.nextstop.data

/*
 * How long a failed request waits before it is asked again by itself (#59): soon at first, then
 * less and less often, so a flaky network is tried again without a request every few seconds.
 */

// The waits before the first, second and third retry; every one after waits as long as the last.
private val RETRY_WAITS_MILLIS = longArrayOf(5_000L, 15_000L, 30_000L, 60_000L)

/** The wait before retry number [attempt] (0 for the first), in milliseconds. */
fun screenRetryWaitMillis(attempt: Int): Long = RETRY_WAITS_MILLIS[attempt.coerceIn(0, RETRY_WAITS_MILLIS.lastIndex)]
