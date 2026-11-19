package io.github.jnbp.nextstop.data

import java.io.IOException
import java.net.NoRouteToHostException
import java.net.UnknownHostException

/**
 * Why a search or a departure board has nothing to show (#196): the sources answered and found
 * nothing, or no source could be asked at all. Only the second says "Dienste nicht erreichbar".
 */
enum class FailureKind {
    /** A source answered that it knows nothing there (HTTP 404). */
    NothingFound,

    /** No source answered, and the phone itself has no network (a name that does not resolve, no route). */
    NoNetwork,

    /** No source answered: every one timed out, refused or failed on its side. */
    ServicesDown,

    /** A source answered, but the request was wrong for it (an HTTP 4xx, a kind it does not support). */
    Failed,
}

/**
 * The kind of the failure [e] of a request through the sources (see SourceChain and
 * RoutingSource.reached): a [SourceUnreachableException] is thrown only once every source asked
 * failed, so it means the services are down (or the network is: [NoNetwork] where the reason is
 * that a name did not resolve or there was no route); an [HttpStatusException] 404 is
 * [FailureKind.NothingFound], 5xx and 429 down, other codes [FailureKind.Failed].
 */
fun failureKind(e: Throwable): FailureKind = when {
    e is SourceUnreachableException && e.tried.isEmpty() && e.cause is NotSupportedException -> FailureKind.Failed
    e is SourceUnreachableException -> if (offlineCause(e)) FailureKind.NoNetwork else FailureKind.ServicesDown
    e is HttpStatusException && e.nothingFound -> FailureKind.NothingFound
    e is HttpStatusException && (e.code >= 500 || e.code == 429) -> FailureKind.ServicesDown
    e is HttpStatusException -> FailureKind.Failed
    e is NotSupportedException -> FailureKind.Failed
    e is IOException -> if (offlineCause(e)) FailureKind.NoNetwork else FailureKind.ServicesDown
    else -> FailureKind.Failed
}

/** True for a failure that means no service could be reached ("Dienste nicht erreichbar"). */
fun servicesUnreachable(e: Throwable): Boolean = failureKind(e).let { it == FailureKind.NoNetwork || it == FailureKind.ServicesDown }

/**
 * The failure to show for several requests asked together (the departures of the stops around
 * the user): the first failure when every one failed, null when one answered (also with nothing)
 * or none was asked. One stop that fails must not hide the others; all failing must not read as
 * "nothing leaves here".
 */
fun <T> failureOfAll(answers: List<Result<T>>): Throwable? =
    if (answers.isNotEmpty() && answers.all { it.isFailure }) answers.first().exceptionOrNull() else null

// Somewhere in the chain of causes: a name that did not resolve or no route at all, as without network.
private fun offlineCause(e: Throwable): Boolean {
    var cause: Throwable? = e
    var depth = 0
    while (cause != null && depth < 8) {
        if (cause is UnknownHostException || cause is NoRouteToHostException) return true
        cause = cause.cause
        depth++
    }
    return false
}
