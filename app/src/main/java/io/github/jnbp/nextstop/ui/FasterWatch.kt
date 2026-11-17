package io.github.jnbp.nextstop.ui

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.graphics.toArgb
import java.time.OffsetDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import io.github.jnbp.nextstop.MainActivity
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.ownServerUrl
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.KnownWays
import io.github.jnbp.nextstop.data.Leg
import io.github.jnbp.nextstop.data.RoutingSource
import io.github.jnbp.nextstop.data.ShortcutRules
import io.github.jnbp.nextstop.data.TransitousSource
import io.github.jnbp.nextstop.data.changeRide
import io.github.jnbp.nextstop.data.journeyKey
import io.github.jnbp.nextstop.data.knownWays
import io.github.jnbp.nextstop.data.plainStopName
import io.github.jnbp.nextstop.data.plausible
import io.github.jnbp.nextstop.data.replanPoint
import io.github.jnbp.nextstop.data.shortcutComfort
import io.github.jnbp.nextstop.data.shortcutOffer
import io.github.jnbp.nextstop.data.spliceAt
import io.github.jnbp.nextstop.data.viaAhead
import io.github.jnbp.nextstop.ui.theme.storedPalette

/**
 * A faster way to the destination of the followed trip, found on the way. [journey] is the
 * followed journey with that way in place, ready to be taken; [basedOn] the id of the journey
 * it was found for. [place] is where the new way starts, [line] its first other ride, [time]
 * when that ride leaves there, and [onBoard] tells whether the traveller changes there from
 * the vehicle on board.
 */
data class FasterOffer(
    val key: String,
    val journey: Journey,
    val basedOn: String,
    val gainSeconds: Long,
    val place: String,
    val line: String?,
    val onBoard: Boolean,
    val arrival: OffsetDateTime,
    val before: OffsetDateTime,
    /** True for a made-up way of the developer option, never a real connection. */
    val simulated: Boolean = false,
    /** When the ride to change to ([line]) leaves [place]; null where it is not known. */
    val time: OffsetDateTime? = null,
) {
    val gainMinutes: Long get() = gainSeconds / 60
}

private val FASTER_CHANNEL = AppChannel.Shortcut.id
private const val FASTER_NOTIFICATION = 2
internal const val ACTION_FASTER_TAKE = "io.github.jnbp.nextstop.FASTER_TAKE"
internal const val ACTION_FASTER_IGNORE = "io.github.jnbp.nextstop.FASTER_IGNORE"
internal const val EXTRA_FASTER_KEY = "key"

// The watch looks at the trip this often; a search is only sent when one is due.
private const val TICK_MILLIS = 10_000L

// A search at least this often, and at every stop passed, but never two closer than the pause.
private const val ROUND_MILLIS = 75_000L
private const val PAUSE_MILLIS = 30_000L

// After a failed search the next one waits this long, twice as long after every further failure.
private const val BACKOFF_MILLIS = 60_000L
private const val MAX_BACKOFF_MILLIS = 600_000L

// The made-up way of the demo in the setup.
private const val DEMO_KEY = "demo"
private const val DEMO_LINE = "S41"
private const val DEMO_FROM = "S Ostkreuz"
private const val DEMO_TO = "S Gesundbrunnen"

// Ways found per search.
private const val FASTER_RESULTS = 5

/**
 * The search on the way for a followed trip, with two switches of [TripState]:
 * - "Abkürzungs-Alarm" ([TripState.shortcutAlert], from the setting [AppPrefs.shortcutAlert]):
 *   the way to the destination is searched again in the background at the user's own change
 *   level, and a way that arrives at least a minute sooner is offered.
 * - "Jede Minute zählt" ([TripState.everyMinute], this trip only): switches the alert on for
 *   the trip and searches with every change however tight (Sprint).
 * Searches start from the next point the traveller can still change at; an offer shows on the
 * page of the trip and as a notification that alerts (channel "Abkürzungs-Alarm"). Nothing is
 * switched without the user: one tap takes it over.
 *
 * Only news counts, in both modes: the ways the user saw when choosing the trip ([KnownWays],
 * from the search they picked it from, else from a first search right after the start) are
 * offered only once they win another minute against the chosen journey than they did then,
 * because they got faster or the chosen journey got worse; a chosen journey that can no longer
 * be made lets every faster way through. With the alert alone two options of the settings
 * apply (see [ShortcutRules]): only as comfortable ways, and a quiet start.
 *
 * It lives as long as the process and follows [TripState]: it starts with a trip with either
 * switch on and stops with the trip or both switches off. One search at a time; after errors
 * it waits longer.
 */
object FasterWatch {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    // Ways the user put aside on this trip, by their key, with the minutes won they had then.
    private val ignored = mutableMapOf<String, Long>()

    // The way the notification last alerted for: the same way again is only updated quietly.
    private var alerted: String? = null

    // The ways known when the followed journey was chosen; null until known (a first search then).
    private var known: KnownWays? = null

    // When the followed journey was chosen, for the quiet start.
    private var startedAt: OffsetDateTime = OffsetDateTime.now()

    // The ways of the last search, known as well once one of them is taken.
    private var lastPage: List<Journey> = emptyList()

    /** Starts following [TripState]; once is enough, a second call changes nothing. */
    fun ensure(context: Context) {
        if (job?.isActive == true) return
        val app = context.applicationContext
        job = scope.launch {
            combine(TripState.journey, TripState.everyMinute, TripState.shortcutAlert) { journey, minute, alert ->
                (minute || alert) && journey != null
            }
                .distinctUntilChanged()
                .collectLatest { on ->
                    if (on) watch(app) else drop(app)
                }
        }
    }

    /**
     * A new trip: what was put aside on the last one counts no more. [chosen] is the journey
     * now followed and [alternatives] the connections the user picked it from; without them
     * the first search of the trip tells what counts as known.
     */
    fun reset(context: Context, chosen: Journey? = null, alternatives: List<Journey>? = null) {
        ignored.clear()
        known = if (chosen != null && alternatives != null) knownWays(chosen, alternatives) else null
        startedAt = OffsetDateTime.now()
        lastPage = emptyList()
        drop(context)
    }

    /** Takes the offered way: the followed journey goes on with it from where it starts. */
    fun take(context: Context, key: String?) {
        val offer = TripState.faster.value
        cancelNotification(context)
        if (offer == null || (key != null && offer.key != key)) return
        TripState.faster.value = null
        // A simulation that was taken is not offered again on the same trip.
        if (offer.simulated) ignored[offer.key] = offer.gainSeconds
        val current = TripState.journey.value ?: return
        // A journey changed in another way since then is not overwritten.
        if (current.id != offer.basedOn) return
        TripState.journey.value = offer.journey
        // The way taken is the new choice; the other ways of its search were seen with it.
        if (!offer.simulated) known = known?.rebased(offer.journey, lastPage)
        refreshTripNotification(context)
    }

    /** Puts the offered way aside: it comes back only once it wins another minute. */
    fun ignore(context: Context, key: String?) {
        val offer = TripState.faster.value
        cancelNotification(context)
        if (offer == null || (key != null && offer.key != key)) return
        ignored[offer.key] = offer.gainSeconds
        TripState.faster.value = null
    }

    private fun drop(context: Context) {
        TripState.faster.value = null
        alerted = null
        cancelNotification(context)
    }

    private suspend fun watch(context: Context) {
        val prefs = AppPrefs(context)
        val source = RoutingSource(choice = { prefs.sourceChoice }, userAgent = TransitousSource.userAgent(context), bvgUrl = { ownServerUrl(prefs.ownServer) })
        var lastStage: String? = null
        var lastSearch = 0L
        var failures = 0
        var nextAllowed = 0L
        var lastMinute: Boolean? = null
        while (true) {
            val journey = TripState.journey.value ?: return
            // "Jede Minute zählt" takes Sprint changes; the alert alone the user's own level.
            val everyMinute = TripState.everyMinute.value
            val level = shortcutComfort(everyMinute, prefs.searchOptionsInUse.comfort)
            if (lastMinute != everyMinute) {
                // Another mode: search again at once, with what it allows.
                lastMinute = everyMinute
                lastStage = null
                lastSearch = 0L
            }
            val now = OffsetDateTime.now()
            val point = replanPoint(journey, now)
            val end = journey.legs.last().toPlace
            if (point == null || end == null) {
                // Nothing left to win: the last ride is over, or the trip has ended.
                drop(context)
            } else {
                val clock = SystemClock.elapsedRealtime()
                val due = (point.stage != lastStage || clock - lastSearch >= ROUND_MILLIS) &&
                    clock - lastSearch >= PAUSE_MILLIS && clock >= nextAllowed
                if (due && prefs.simulateFaster) {
                    // Developer option: a made-up way instead of a search, through the same offer.
                    lastStage = point.stage
                    lastSearch = clock
                    val offer = simulatedOffer(journey, now)
                    if (offer == null || offer.key in ignored) {
                        drop(context)
                    } else {
                        TripState.faster.value = offer
                        notifyOffer(context, offer, alert = offer.key != alerted)
                        alerted = offer.key
                    }
                } else if (due) {
                    lastStage = point.stage
                    lastSearch = clock
                    try {
                        val page = source.journeys(
                            from = point.place,
                            to = end.copy(distanceMeters = null),
                            // A place on the way still ahead stays part of every new way.
                            // At a brisk pace: every minute counts (unless "Langsamer unterwegs" is on).
                            options = prefs.searchOptions.copy(
                                comfort = level,
                                via = viaAhead(journey, point.kept),
                                brisk = true,
                            ),
                            results = FASTER_RESULTS,
                            departure = point.time,
                        )
                        failures = 0
                        // The journey may have changed while the search was on its way.
                        val current = TripState.journey.value
                        val knownNow = known
                        if (current != null && current.id == journey.id && knownNow?.chosenId != current.id) {
                            // No ways known for this choice (a trip not started from a list, a
                            // new way): what this first search finds counts as known, no offer.
                            if (knownNow != null) startedAt = OffsetDateTime.now()
                            known = knownWays(current, page.journeys)
                            lastPage = page.journeys
                            drop(context)
                        } else if (current != null && current.id == journey.id && knownNow != null) {
                            lastPage = page.journeys
                            val rules = ShortcutRules(
                                everyMinute = everyMinute,
                                userLevel = prefs.searchOptionsInUse.comfort,
                                sameComfort = prefs.shortcutSameComfort,
                                quietStart = prefs.shortcutQuietStart,
                            )
                            val best = shortcutOffer(
                                current, point, page.journeys, ignored, knownNow, rules, startedAt, OffsetDateTime.now(),
                            )
                            if (best == null) {
                                drop(context)
                            } else {
                                val (option, gain) = best
                                val ride = changeRide(point.kept, point.cut, option)
                                val offer = FasterOffer(
                                    key = journeyKey(option),
                                    journey = spliceAt(current, point, option),
                                    basedOn = current.id,
                                    gainSeconds = gain,
                                    place = (ride?.from ?: point.place.name).plainStopName(),
                                    line = ride?.line,
                                    onBoard = point.onBoard,
                                    arrival = option.arrival,
                                    before = current.plausible().arrival,
                                    time = ride?.departure ?: option.departure,
                                )
                                TripState.faster.value = offer
                                notifyOffer(context, offer, alert = offer.key != alerted)
                                alerted = offer.key
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failures++
                        val wait = (BACKOFF_MILLIS shl (failures - 1).coerceAtMost(4)).coerceAtMost(MAX_BACKOFF_MILLIS)
                        nextAllowed = clock + wait
                    }
                }
            }
            delay(TICK_MILLIS)
        }
    }

    /**
     * A made-up faster way for testing: the last ride of [journey] arrives 1 to 3 minutes
     * sooner (the same number for the same journey), and so does everything after it. Null
     * when the last ride is over or too short to win the minutes.
     */
    private fun simulatedOffer(journey: Journey, now: OffsetDateTime): FasterOffer? {
        val last = journey.legs.indexOfLast { !it.walking }
        if (last < 0) return null
        val ride = journey.legs[last]
        val gain = (journey.id.hashCode() and Int.MAX_VALUE) % 3 + 1L
        if (!ride.arrival.isAfter(now) || !ride.arrival.minusMinutes(gain).isAfter(ride.departure)) return null
        val legs = journey.legs.mapIndexed { index, leg ->
            when {
                index == last -> leg.copy(arrival = leg.arrival.minusMinutes(gain))
                index > last -> leg.copy(departure = leg.departure.minusMinutes(gain), arrival = leg.arrival.minusMinutes(gain))
                else -> leg
            }
        }
        val faster = journey.copy(legs = legs)
        return FasterOffer(
            key = "simulation:" + journey.id,
            journey = faster,
            basedOn = journey.id,
            gainSeconds = gain * 60,
            place = ride.from.plainStopName(),
            line = ride.line,
            onBoard = false,
            arrival = faster.arrival,
            before = journey.arrival,
            simulated = true,
            time = ride.departure,
        )
    }

    /**
     * A made-up offer for the setup, marked as a simulation and tied to no trip: taking or
     * ignoring it from the notification only clears the notification.
     */
    fun demoOffer(now: OffsetDateTime): FasterOffer {
        val start = now.plusMinutes(4).withSecond(0).withNano(0)
        val end = start.plusMinutes(14)
        val ride = Leg(
            line = DEMO_LINE,
            product = "suburban",
            walking = false,
            from = DEMO_FROM,
            to = DEMO_TO,
            departure = start,
            plannedDeparture = start,
            arrival = end,
            plannedArrival = end,
            departurePlatform = null,
            arrivalPlatform = null,
            direction = null,
            cancelled = false,
        )
        return FasterOffer(
            key = DEMO_KEY,
            journey = Journey(legs = listOf(ride)),
            basedOn = DEMO_KEY,
            gainSeconds = 3 * 60L,
            place = DEMO_FROM.plainStopName(),
            line = DEMO_LINE,
            onBoard = true,
            arrival = end,
            before = end.plusMinutes(3),
            simulated = true,
            time = start,
        )
    }

    /** Shows [offer] of [demoOffer] once as the alerting notification, as a real one would come. */
    fun showDemo(context: Context, offer: FasterOffer) = notifyOffer(context, offer, alert = true)

    /** Takes the notification of the demo away again. */
    fun dismissDemo(context: Context) = cancelNotification(context)

    /** The headline of an offer, "3 min faster", marked as such for a simulation. */
    fun offerTitle(context: Context, offer: FasterOffer): String {
        val title = context.getString(R.string.faster_title, offer.gainMinutes)
        return if (offer.simulated) context.getString(R.string.faster_simulation, title) else title
    }

    /**
     * The offer in words, with where and when: "Change at … at 10:42 to the S41" or "Take the
     * S41 from … at 10:42"; without a known time as before.
     */
    fun offerText(context: Context, offer: FasterOffer): String {
        val line = offer.line?.let { shortLine(it) ?: it }
        val time = offer.time?.clock()
        return when {
            line == null && time != null -> context.getString(R.string.faster_other_way_at, offer.place, time)
            line == null -> context.getString(R.string.faster_other_way, offer.place)
            offer.onBoard && time != null -> context.getString(R.string.faster_change_at, offer.place, line, time)
            offer.onBoard -> context.getString(R.string.faster_change, offer.place, line)
            time != null -> context.getString(R.string.faster_take_at, offer.place, line, time)
            else -> context.getString(R.string.faster_take, offer.place, line)
        }
    }

    @SuppressLint("MissingPermission")
    private fun notifyOffer(context: Context, offer: FasterOffer, alert: Boolean) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // Its own channel: it alerts with sound and vibration and pops up, unlike the quiet one of the trip.
        ensureChannel(context, AppChannel.Shortcut)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(
            context,
            10,
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_OPEN_TRIP)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            flags,
        )
        fun action(name: String, request: Int): PendingIntent = PendingIntent.getBroadcast(
            context,
            request,
            Intent(context, FasterReceiver::class.java).setAction(name).putExtra(EXTRA_FASTER_KEY, offer.key),
            flags,
        )
        val title = offerTitle(context, offer)
        val text = offerText(context, offer)
        val arrival = context.getString(R.string.faster_arrival, offer.arrival.clock(), offer.before.clock())
        val notification = NotificationCompat.Builder(context, FASTER_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_trip)
            .setColor(storedPalette(context).blue.toArgb())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\n$arrival"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setOnlyAlertOnce(!alert)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.faster_take_action), action(ACTION_FASTER_TAKE, 11))
            .addAction(0, context.getString(R.string.faster_ignore_action), action(ACTION_FASTER_IGNORE, 12))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(FASTER_NOTIFICATION, notification)
        } catch (e: SecurityException) {
            // The permission was taken away in the meantime.
        }
    }

    private fun cancelNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(FASTER_NOTIFICATION)
    }
}

/** The buttons of the notification of a faster way: take it, or put it aside. */
class FasterReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(EXTRA_FASTER_KEY)
        when (intent.action) {
            ACTION_FASTER_TAKE -> FasterWatch.take(context, key)
            ACTION_FASTER_IGNORE -> FasterWatch.ignore(context, key)
        }
    }
}
