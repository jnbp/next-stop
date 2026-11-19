package io.github.jnbp.nextstop.wear.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/*
 * #235: when a load of departures counts as "no departures in the next hour". Only an answer
 * that really lists the departures of the stop may say so: an answer of another shape is a
 * failure (shown with "try again"), and an empty board of BVG is checked against the phone,
 * whose whole chain of sources may well know the runs. Plain Kotlin, tested on the JVM
 * (DepartureAnswersTest).
 */

/**
 * The list of departures ([key]: "departures" of hafas-rest-api, "stopTimes" of Transitous) in
 * an answer ([root]). An answer without that list (an error object, another version of the
 * API) throws [JSONException], which the clients report as a bad answer: it never becomes an
 * empty board.
 */
fun departuresOfAnswer(root: JSONObject, key: String = "departures"): JSONArray =
    root.optJSONArray(key) ?: throw JSONException("the answer lists no $key")

/**
 * True when BVG's board of a stop ([bvg]) has nothing that can still be caught at [now] (see
 * upcomingDepartures): then the phone is asked as well before the watch says that nothing
 * leaves (#235). A board with departures is taken as it is, without asking the phone.
 */
fun boardNeedsPhone(bvg: List<Departure>, now: Long): Boolean = upcomingDepartures(bvg, now, 1).isEmpty()

/**
 * The board shown after BVG listed nothing to catch ([bvg]): the phone's ([phone], null when it
 * did not answer) where it has departures that can still be caught at [now], else BVG's.
 */
fun boardWithPhone(bvg: List<Departure>, phone: List<Departure>?, now: Long): List<Departure> =
    if (phone != null && upcomingDepartures(phone, now, 1).isNotEmpty()) phone else bvg

/** How long an empty board of a stop that the phone could not fill either keeps the phone unasked. */
const val PHONE_EMPTY_RETRY_MILLIS = 5 * 60_000L

/**
 * Whether the phone is asked again for a stop whose BVG board lists nothing to catch (#235):
 * not when it was asked [lastEmptyAsk] less than [PHONE_EMPTY_RETRY_MILLIS] ago and knew nothing
 * either, so a quiet stop at night does not cost a phone request on each reload of the board.
 */
fun phoneCheckDue(lastEmptyAsk: Long?, now: Long): Boolean =
    lastEmptyAsk == null || now < lastEmptyAsk || now - lastEmptyAsk >= PHONE_EMPTY_RETRY_MILLIS
