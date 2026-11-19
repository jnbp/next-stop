package io.github.jnbp.nextstop.data

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** #196: "nothing found" told apart from "no source could be reached". */
class ServiceReachTest {
    private val someBackend = Backend.entries.first()

    @Test
    fun everySourceFailingWithoutNetworkIsNoNetwork() {
        val e = SourceUnreachableException(DataSource.Transitous, UnknownHostException("example.org"), listOf(someBackend))
        assertEquals(FailureKind.NoNetwork, failureKind(e))
        assertTrue(servicesUnreachable(e))
    }

    @Test
    fun everySourceTimingOutIsServicesDown() {
        val e = SourceUnreachableException(DataSource.Transitous, SocketTimeoutException("read"), listOf(someBackend))
        assertEquals(FailureKind.ServicesDown, failureKind(e))
        assertTrue(servicesUnreachable(e))
        // A server error of the last one asked, and a timeout of the chain, are down too.
        assertEquals(FailureKind.ServicesDown, failureKind(SourceUnreachableException(DataSource.Transitous, HttpStatusException(503), listOf(someBackend))))
        assertEquals(FailureKind.ServicesDown, failureKind(HttpStatusException(502)))
        assertEquals(FailureKind.ServicesDown, failureKind(HttpStatusException(429)))
    }

    @Test
    fun aPlainNetworkFailureIsClassifiedByItsCause() {
        assertEquals(FailureKind.NoNetwork, failureKind(IOException("wrapped", UnknownHostException("x"))))
        assertEquals(FailureKind.ServicesDown, failureKind(IOException("reset")))
    }

    @Test
    fun nothingFoundIsNotUnreachable() {
        assertEquals(FailureKind.NothingFound, failureKind(HttpStatusException(404)))
        assertFalse(servicesUnreachable(HttpStatusException(404)))
    }

    @Test
    fun aWrongRequestIsNeitherFoundNothingNorDown() {
        assertEquals(FailureKind.Failed, failureKind(HttpStatusException(400)))
        assertEquals(FailureKind.Failed, failureKind(IllegalStateException("bug")))
        // Nothing asked because no source supports it: not a network matter.
        assertEquals(FailureKind.Failed, failureKind(SourceUnreachableException(DataSource.Transitous, NotSupportedException("arrivals"), emptyList())))
        assertFalse(servicesUnreachable(IllegalStateException("bug")))
    }

    @Test
    fun allStopsFailingIsAFailureNotAnEmptyBoard() {
        val first = IOException("a")
        val all = listOf<Result<List<Int>>>(Result.failure(first), Result.failure(IOException("b")))
        assertSame(first, failureOfAll(all))
        // One that answered, even with nothing, is an answer.
        assertNull(failureOfAll(listOf<Result<List<Int>>>(Result.failure(first), Result.success(emptyList()))))
        // No stop asked: nothing failed.
        assertNull(failureOfAll(emptyList<Result<List<Int>>>()))
    }
}
