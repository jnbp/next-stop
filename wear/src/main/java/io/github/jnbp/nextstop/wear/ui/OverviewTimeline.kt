package io.github.jnbp.nextstop.wear.ui

import io.github.jnbp.nextstop.wear.data.PlanRide

/*
 * "Time travel" on the overview of the live trip: the list of the whole connection is also a
 * time line. Scrolling it (with the crown or a swipe) moves through the trip, and a small pill
 * at the top tells the time and place of the row in the middle and the buffer of the next
 * change from there. Pure, so the rows and their times are tested.
 */

/** The kinds of rows of the overview that stand for a moment of the trip. */
enum class TimelineKind { Connector, Ride, EndWalk, Arrival }

/**
 * One row of the overview, in the order of the list: [ride] its ride in the plan (the plan's
 * size for the way to the end and the arrival). [time] is when the traveller is there (epoch
 * ms), [place] where, [line] the line of a ride; [buffer] the minutes to spare at the next
 * change from there on, null where none follows or the phone sent none.
 */
data class TimelineRow(
    val kind: TimelineKind,
    val ride: Int,
    val time: Long,
    val place: String,
    val line: String? = null,
    val buffer: Int? = null,
)

/**
 * The rows of the overview below its header for [plan]: before every ride with a walk or a
 * rated change its connector, then the ride; the way to the end ([planWalkEnd] minutes) and
 * the arrival at [destination] at [arrival].
 */
fun overviewTimeline(plan: List<PlanRide>, planWalkEnd: Int, arrival: Long, destination: String): List<TimelineRow> {
    val rows = ArrayList<TimelineRow>(plan.size * 2 + 2)
    plan.forEachIndexed { index, ride ->
        if (ride.walkBefore > 0 || ride.rating != null) {
            val before = plan.getOrNull(index - 1)
            rows.add(
                TimelineRow(
                    kind = TimelineKind.Connector,
                    ride = index,
                    time = before?.arrival ?: (ride.departure - ride.walkBefore * 60_000L),
                    place = before?.to ?: ride.from,
                    // The change of this connector itself.
                    buffer = ride.spare,
                ),
            )
        }
        rows.add(
            TimelineRow(
                kind = TimelineKind.Ride,
                ride = index,
                time = ride.departure,
                place = ride.from,
                line = ride.line,
                buffer = plan.getOrNull(index + 1)?.spare,
            ),
        )
    }
    val last = plan.lastOrNull()
    if (planWalkEnd > 0) {
        rows.add(TimelineRow(TimelineKind.EndWalk, plan.size, last?.arrival ?: (arrival - planWalkEnd * 60_000L), last?.to ?: destination))
    }
    rows.add(TimelineRow(TimelineKind.Arrival, plan.size, arrival, destination))
    return rows
}

/** The row of [rows] at [centre], the index of the list item in the middle (0 is the header); null for the header and the actions. */
fun timelineAt(rows: List<TimelineRow>, centre: Int): TimelineRow? = rows.getOrNull(centre - 1)

/**
 * Whether the overview shows the pill of the time travel (#254): only with the option
 * "Zeitreise mit der Krone" ([option], off by default; without it the crown just scrolls the
 * list), with a plan to travel through, and not in ambient mode.
 */
fun timeTravelShown(option: Boolean, hasPlan: Boolean, ambient: Boolean): Boolean = option && hasPlan && !ambient
