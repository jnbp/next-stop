package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.json.JSONObject

/*
 * #117: a followed trip survives an update of the app. The trip lives in memory only (see
 * ui/TripService.kt TripState); an update ends the process without a word, so the companion was
 * gone. While the companion's service runs it keeps a small mark of the trip on the device; the
 * service removes it when it ends as it should, so a mark that is still there after an update
 * tells of a trip that was cut off. The broadcast of the update (ui/TripResume.kt) then starts
 * the companion again with it, where the trip has not ended in the meantime.
 */

/** A followed trip has ended this long after its arrival: then it is not started again. */
const val RESUME_GRACE_MINUTES = 5L

/** A mark older than this is not trusted any more (a day trip at most; as a kept page). */
const val RESUME_MAX_AGE_MILLIS = KEPT_PAGE_MAX_AGE_MILLIS

/** The mark of the followed [journey], written at [savedAt] (milliseconds since the epoch), as JSON text. */
fun tripMarkJson(journey: Journey, savedAt: Long): String =
    JSONObject().put("v", 1).put("savedAt", savedAt).put("journey", keptJourneyJson(journey)).toString()

/** The time and the journey of the mark [text]; null when it cannot be read. */
fun tripMarkOf(text: String): Pair<Long, Journey>? = try {
    val json = JSONObject(text)
    if (json.optInt("v") != 1) {
        null
    } else {
        json.optJSONObject("journey")?.let { keptJourneyOf(it) }?.let { json.getLong("savedAt") to it }
    }
} catch (e: Exception) {
    null
}

/**
 * The trip to follow again after an update: the [saved] one (marked at [savedAt]) where the mark
 * is not older than [RESUME_MAX_AGE_MILLIS] at [nowMillis] and the trip has not been over for
 * [RESUME_GRACE_MINUTES] at [now]; null otherwise.
 */
fun resumableTrip(saved: Journey?, savedAt: Long, nowMillis: Long, now: OffsetDateTime): Journey? {
    if (saved == null) return null
    val age = nowMillis - savedAt
    if (age < 0 || age > RESUME_MAX_AGE_MILLIS) return null
    return saved.takeIf { it.arrival.plusMinutes(RESUME_GRACE_MINUTES).isAfter(now) }
}

/** What tells one followed trip from another for the mark: a new mark only when this changes. */
fun tripMarkKey(journey: Journey?): String? =
    journey?.let { j -> j.legs.joinToString("|") { "${it.line}@${it.plannedDeparture ?: it.departure}" } }
