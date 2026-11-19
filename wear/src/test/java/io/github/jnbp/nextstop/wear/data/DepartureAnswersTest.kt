package io.github.jnbp.nextstop.wear.data

import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** #235: only an answer that lists the departures can mean "no departures in the next hour". */
class DepartureAnswersTest {
    @Test
    fun anAnswerWithItsListGivesTheList() {
        assertEquals(2, departuresOfAnswer(JSONObject("""{"departures":[{},{}],"realtimeDataUpdatedAt":1}""")).length())
        // A stop where nothing leaves within the hour: an empty list is a real answer.
        assertEquals(0, departuresOfAnswer(JSONObject("""{"departures":[]}""")).length())
        // Transitous names its list "stopTimes".
        assertEquals(1, departuresOfAnswer(JSONObject("""{"stopTimes":[{}]}"""), "stopTimes").length())
    }

    @Test
    fun anAnswerWithoutTheListIsAFailureNotAnEmptyBoard() {
        listOf("""{"error":true,"msg":"HAFAS error"}""", """{}""", """{"departures":null}""").forEach { text ->
            val failed = try {
                departuresOfAnswer(JSONObject(text))
                false
            } catch (e: JSONException) {
                true
            }
            assertTrue(text, failed)
        }
        val transitousFailed = try {
            departuresOfAnswer(JSONObject("""{"departures":[{}]}"""), "stopTimes")
            false
        } catch (e: JSONException) {
            true
        }
        assertTrue(transitousFailed)
    }

    private val minute = 60_000L
    private val now = 1_000 * minute

    private fun run(line: String, at: Long?, cancelled: Boolean = false) =
        Departure(line, "bus", "Musterplatz", at, 0, null, cancelled)

    @Test
    fun aBoardWithRunsToCatchNeedsNoPhone() {
        assertTrue(!boardNeedsPhone(listOf(run("100", now + 3 * minute)), now))
        // Empty, only cancelled runs or only runs gone by now: the phone is asked as well.
        assertTrue(boardNeedsPhone(emptyList(), now))
        assertTrue(boardNeedsPhone(listOf(run("100", now + 3 * minute, cancelled = true)), now))
        assertTrue(boardNeedsPhone(listOf(run("100", now - 5 * minute), run("200", null)), now))
    }

    @Test
    fun anEmptyBoardOfBvgGivesWayToThePhonesDepartures() {
        val phone = listOf(run("U9", now + 2 * minute), run("U9", now + 6 * minute))
        assertEquals(phone, boardWithPhone(emptyList(), phone, now))
        // The phone did not answer, or knows nothing to catch either: BVG's board stays ("no departures").
        assertEquals(emptyList<Departure>(), boardWithPhone(emptyList(), null, now))
        assertEquals(emptyList<Departure>(), boardWithPhone(emptyList(), listOf(run("U9", now - 9 * minute)), now))
    }

    @Test
    fun aQuietStopAsksThePhoneAgainOnlyAfterAWhile() {
        assertTrue(phoneCheckDue(null, now))
        assertTrue(!phoneCheckDue(now - minute, now))
        assertTrue(phoneCheckDue(now - PHONE_EMPTY_RETRY_MILLIS, now))
        // A clock set back: asked again rather than never.
        assertTrue(phoneCheckDue(now + minute, now))
    }
}
