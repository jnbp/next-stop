package io.github.jnbp.nextstop.wear.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** #196 on the watch: "nothing found" told apart from "no source could be reached". */
class LoadFailureTest {
    @Test
    fun noPhoneAndNoNetworkIsOffline() {
        assertEquals(LoadFailure.NoNetwork, loadFailureOf(phoneAnswered = false, phoneDown = false, localDown = true, localOffline = true))
    }

    @Test
    fun thePhoneDownAndTheWatchTimingOutIsServicesDown() {
        assertEquals(LoadFailure.ServicesDown, loadFailureOf(phoneAnswered = true, phoneDown = true, localDown = true, localOffline = false))
        assertEquals(LoadFailure.ServicesDown, loadFailureOf(phoneAnswered = false, phoneDown = false, localDown = true, localOffline = false))
    }

    @Test
    fun anAnswerOfNothingIsNothingFound() {
        // The watch's own source answered (with nothing or a bad answer).
        assertEquals(LoadFailure.NothingFound, loadFailureOf(phoneAnswered = false, phoneDown = false, localDown = false, localOffline = false))
        // The phone reached its sources and found nothing, although the watch has no network.
        assertEquals(LoadFailure.NothingFound, loadFailureOf(phoneAnswered = true, phoneDown = false, localDown = true, localOffline = true))
    }

    @Test
    fun thePhoneSaysWhetherItsSourcesWereDown() {
        assertTrue(phoneAnswerDown(ok = false, down = true, error = "x"))
        assertFalse(phoneAnswerDown(ok = false, down = false, error = "SourceUnreachableException: x"))
        assertFalse(phoneAnswerDown(ok = true, down = null, error = null))
        // An older phone app sends only the name of the failure.
        assertTrue(phoneAnswerDown(ok = false, down = null, error = "SourceUnreachableException: transitous unreachable"))
        assertTrue(phoneAnswerDown(ok = false, down = null, error = "timeout"))
        assertFalse(phoneAnswerDown(ok = false, down = null, error = "HttpStatusException: HTTP 404"))
        assertFalse(phoneAnswerDown(ok = false, down = null, error = null))
    }
}
