package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.NOTIFICATION_END_HANDOFF_SECONDS
import io.github.jnbp.nextstop.data.notificationMapsHandoff
import io.github.jnbp.nextstop.data.MapsHandoff
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.res.ResourcesCompat
import java.time.Duration
import java.time.OffsetDateTime
import kotlin.math.roundToInt
import io.github.jnbp.nextstop.MainActivity
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.data.ShownTime
import io.github.jnbp.nextstop.data.shownTime
import io.github.jnbp.nextstop.data.shownTimeText
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.samePlatformByNumbers
import io.github.jnbp.nextstop.data.Leg
import io.github.jnbp.nextstop.data.railReplacement
import io.github.jnbp.nextstop.data.replacedProduct
import io.github.jnbp.nextstop.data.replacedProductOrGuess
import io.github.jnbp.nextstop.data.ridePlace
import io.github.jnbp.nextstop.data.plainStopName
import io.github.jnbp.nextstop.data.shownRideStop
import io.github.jnbp.nextstop.data.plausible
import io.github.jnbp.nextstop.ui.theme.AppPalette
import io.github.jnbp.nextstop.ui.theme.NotificationLook
import io.github.jnbp.nextstop.ui.theme.storedPalette

// The channel of a followed trip. Version 2 has default importance (without sound or vibration):
// the first one was low, and Android (on Pixel phones by default) hides silent notifications
// on the lock screen. The importance of an existing channel cannot be raised, hence a new id.
private val TRIP_CHANNEL = AppChannel.of(NotifyFeature.Trip).id

/** Creates the quiet channel of a followed trip (see [AppChannel.Live]). */
private fun ensureTripChannel(context: Context) = ensureChannel(context, AppChannel.of(NotifyFeature.Trip))
internal const val TRIP_NOTIFICATION = 1
const val ACTION_OPEN_TRIP = "io.github.jnbp.nextstop.OPEN_TRIP"

// The extras key of Android 16 by which a notification asks to be shown as a Live Update.
private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"

// Walks and waits on the bar of a Live Update, and the dots of steps on foot.
private const val WALK_ARGB = 0xFF8D96A8.toInt()

// The stops of the current ride marked on the bar of a Live Update, at most.
private const val MAX_STOP_POINTS = 10

/** True when a notification of the trip can be seen: allowed for the app, and its channel not switched off. */
fun tripNotificationsAllowed(context: Context): Boolean {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
    val channel = context.getSystemService(NotificationManager::class.java)?.getNotificationChannel(TRIP_CHANNEL)
    return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
}

/** Shows or renews the notification of the trip; without the permission nothing is shown. */
@SuppressLint("MissingPermission")
fun postTripNotification(context: Context, notification: Notification) {
    // Covers both the permission of Android 13 and notifications switched off in the system settings.
    if (!tripNotificationsAllowed(context)) return
    try {
        NotificationManagerCompat.from(context).notify(TRIP_NOTIFICATION, notification)
    } catch (e: SecurityException) {
        // The permission was taken away between the check and the call.
    }
}

/** Draws the notification of the trip again at once, after a change of its look or colours. */
fun refreshTripNotification(context: Context) {
    val journey = TripState.journey.value ?: return
    if (!TripState.serviceRunning.value) return
    postTripNotification(context, tripNotification(context, journey, OffsetDateTime.now()))
}

/**
 * The notification of a followed trip. In the look of the app: a box in the main colour
 * with the minutes, the next step and the badge of its line, and when unfolded a bar of all
 * rides and the step after this one. From Android 16 on it is a Live Update instead, unless
 * the user chose the look of the app.
 */
fun tripNotification(context: Context, journey: Journey?, now: OffsetDateTime, alert: String? = null): Notification {
    ensureTripChannel(context)
    // A reminder to get off goes out in this notification (see AlightReminders): once with the
    // sound and vibration of the channel of getting off, its words as the title; the next
    // update is quiet again on the channel of the trip. No second notification beside it.
    if (alert != null) ensureChannel(context, AppChannel.of(NotifyFeature.Alight))
    val channel = if (alert != null) AppChannel.of(NotifyFeature.Alight).id else TRIP_CHANNEL
    val palette = storedPalette(context)
    val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    // A tap opens the app on the page of the trip.
    val open = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_TRIP)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        flags,
    )
    val end = PendingIntent.getService(
        context,
        1,
        Intent(context, TripService::class.java).setAction(ACTION_END_TRIP),
        flags,
    )
    // "Beenden": short enough for the one button of a Live Update.
    val endLabel = context.getString(R.string.trip_end)
    if (journey == null) {
        return NotificationCompat.Builder(context, TRIP_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_trip)
            .setColor(palette.blue.toArgb())
            .setContentTitle(context.getString(R.string.trip_active))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .build()
    }
    val step = tripStep(journey, now)
    val minutes = minutesBetween(now, step.time).coerceAtLeast(0)
    val content = TripContent(
        journey = journey,
        // The bar by the ride state: where the position has the traveller, not where the timetable does.
        shown = rideJourney(journey, now),
        channel = channel,
        alert = alert,
        step = step,
        now = now,
        palette = palette,
        // One station, one name (#157), as the companion names it.
        label = stepLabel(context, step, io.github.jnbp.nextstop.data.railwayNamesOf(journey)),
        headline = alert ?: headline(context, step, minutes),
        short = alert ?: shortHeadline(context, step),
        place = placeLine(context, step, journey),
        then = thenLine(context, step, journey),
        time = timeLabel(context, step, minutes, about = true),
        chip = timeLabel(context, step, minutes, about = false),
        hint = boardingHint(context, step, journey),
        // As the delay setting says (see shownTime): with the coloured time alone no "+2" stands in the
        // notification either; struck through (Takt style) the pill shows the timetable time struck and the new one.
        delay = stepShownTime(context, step).delay ?: 0L,
        struck = if (stepShownTime(context, step).struck != null) struckStepTime(step) else null,
    )
    val live = Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA &&
        AppPrefs(context).notificationLook == NotificationLook.LiveUpdate
    // "Google Maps" by the situation (#189): at the end of the trip it takes over and ends the
    // Live-Fahrt ("Maps & beenden"); on a walk to a change it only opens Google Maps.
    val changeWalk = walkToChange(journey, step)
    val handoff = notificationMapsHandoff(
        lastWalk = step.kind == StepKind.WalkToEnd,
        walkToChange = changeWalk != null,
        secondsToArrival = java.time.Duration.between(now, content.shown.arrival).seconds,
        remembered = MapsHandoffSetting.load(context),
    )
    val ending = handoff == MapsHandoff.OpenAndEnd
    val maps = when (handoff) {
        null, MapsHandoff.Ask -> null
        MapsHandoff.Open -> if (changeWalk != null && step.kind != StepKind.WalkToEnd && !closeToEnd(now, content.shown)) {
            mapsIntent(context, changeWalk, end = false)
        } else {
            journey.legs.lastOrNull()?.let { mapsIntent(context, it, end = false) }
        }
        MapsHandoff.OpenAndEnd -> journey.legs.lastOrNull()?.let { mapsIntent(context, it, end = true) }
    }
    val mapsText = context.getString(if (ending) R.string.r6live_maps_and_end else R.string.navigate)
    val endText = if (maps != null && step.kind == StepKind.WalkToEnd) context.getString(R.string.trip_end_companion) else endLabel
    return if (live && Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
        liveUpdate(context, content, open, end, endText, maps, mapsText)
    } else {
        customNotification(context, content, open, end, endText, maps, mapsText)
    }
}

/** True within [NOTIFICATION_END_HANDOFF_SECONDS] of the final arrival of [shown] (or after it). */
private fun closeToEnd(now: OffsetDateTime, shown: Journey): Boolean =
    java.time.Duration.between(now, shown.arrival).seconds <= NOTIFICATION_END_HANDOFF_SECONDS

/**
 * The walk to the change of [step] (#189): the walk before the ride to board, where the step is
 * boarding a ride that is not the first and a real walk leads to it; null otherwise.
 */
private fun walkToChange(journey: Journey, step: TripStep): Leg? {
    if (step.kind != StepKind.Board) return null
    val index = journey.legs.indexOfFirst { it === step.leg }
    if (index <= 0) return null
    val walk = journey.legs[index - 1]
    if (!walk.walking || walk.inStation) return null
    // A walk after a ride: a change, not the way to the first ride.
    return walk.takeIf { journey.legs.subList(0, index - 1).any { !it.walking } }
}

/**
 * Directions in Google Maps (or whatever app shows the link) to the end of the way [leg]; null
 * where its end has no position. With [end] the Live-Fahrt ends with it (#189): the app is asked
 * to end the trip the way "Beenden" does and to open Google Maps right after (an activity: a
 * notification may not start one from a service).
 */
private fun mapsIntent(context: Context, leg: Leg, end: Boolean): PendingIntent? {
    val target = leg.toPlace?.position ?: leg.path.lastOrNull() ?: return null
    val mode = if (leg.walking && leg.cycling) "bicycling" else "walking"
    val url = "https://www.google.com/maps/dir/?api=1&destination=${target.first},${target.second}&travelmode=$mode"
    val intent = if (end) {
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_MAPS_AND_END)
            .putExtra(EXTRA_MAPS_URL, url)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    } else {
        Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return PendingIntent.getActivity(
        context,
        if (end) 3 else 2,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** "Maps & beenden" of the trip notification (#189): MainActivity ends the trip and opens [EXTRA_MAPS_URL]. */
const val ACTION_MAPS_AND_END = "io.github.jnbp.nextstop.MAPS_AND_END"

/** The Google Maps link of [ACTION_MAPS_AND_END]. */
const val EXTRA_MAPS_URL = "io.github.jnbp.nextstop.MAPS_URL"

/**
 * Follows "Maps & beenden" (#189): ends the followed trip as the app and "Beenden" in the
 * notification do (see [endFollowedTrip]) and opens Google Maps. True when [intent] was one.
 */
fun followMapsAndEnd(context: Context, intent: Intent?): Boolean {
    if (intent?.action != ACTION_MAPS_AND_END) return false
    val url = intent.getStringExtra(EXTRA_MAPS_URL)
    endFollowedTrip(context)
    if (url != null && url.startsWith("https://www.google.com/maps/")) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: android.content.ActivityNotFoundException) {
            // Nothing shows the link: the trip has ended all the same.
        }
    }
    return true
}

/**
 * What one round of the notification shows. [label] is the step in words ("Get off at …"),
 * [headline] the step with its minutes ("Get off in 2 min"), [place] the stop and platform,
 * [then] the step after this one and the arrival. [time] is null once the trip has arrived;
 * [chip] is the same without "ca.", short enough for the chip of the status bar.
 */
private class TripContent(
    val journey: Journey,
    // The journey with the times of the ride state (see rideJourney), for the bar.
    val shown: Journey,
    // The channel to post on: the trip's, or once the one of getting off for an alert.
    val channel: String,
    val alert: String?,
    val step: TripStep,
    val now: OffsetDateTime,
    val palette: AppPalette,
    val label: String,
    val headline: String,
    // "Aussteigen 09:58": the title of a Live Update, whose chip already has the minutes.
    val short: String,
    val place: String,
    val then: String,
    val time: String?,
    val chip: String?,
    val hint: String?,
    val delay: Long,
    // The timetable time struck through and the new time ("~~12:07~~ 12:09"); null for none.
    val struck: CharSequence? = null,
)

/**
 * Before boarding a train that leads to a change, where to board it, by the dataset or the
 * map: "Board in the middle – short way to the S2"; on board before a change, the same
 * platform. Null otherwise, and with the setting off.
 */
internal fun boardingHint(context: Context, step: TripStep, journey: Journey): String? {
    if (step.kind != StepKind.Board && step.kind != StepKind.GetOff) return null
    if (!AppPrefs(context).boardingHints) return null
    val leg = step.leg ?: return null
    // The last ride to a place: where to board for the shortest way out of the station.
    val next = rideAfter(journey, leg) ?: return if (step.kind == StepKind.Board) destinationText(context, journey) else null
    if (leg.tripId != null && leg.tripId == next.tripId) return null
    // On board, at the change ahead: only the same platform; where to board was said before.
    if (step.kind == StepKind.GetOff) return changeText(context, leg, next, walkBetween(journey, leg))
    if (samePlatformByNumbers(leg, next)) return null
    val position = boardingPositionOf(leg, next) ?: return null
    return boardingText(context, position, shortLine(next.line), next.product)
}

/**
 * The timetable time of the next step where it differs from the live one (see [stepDelay]):
 * of the departure before boarding, of the arrival on board; null otherwise.
 */
internal fun stepPlannedTime(step: TripStep): OffsetDateTime? {
    if (stepDelay(step) == 0L) return null
    val leg = step.leg ?: return null
    return when (step.kind) {
        StepKind.Board -> leg.plannedDeparture
        StepKind.GetOff -> leg.plannedArrival
        else -> null
    }
}

/** "12:07 12:09" with the timetable time struck through (DelayDisplay.StruckPlanned); null without a change. */
private fun struckStepTime(step: TripStep): CharSequence? {
    val planned = stepPlannedTime(step) ?: return null
    val old = planned.clock()
    return SpannableString(old + " " + step.time.clock()).apply {
        setSpan(StrikethroughSpan(), 0, old.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}

/**
 * The time of the next step as the delay setting says (#227, see shownTime): the timetable time
 * with "Planzeit + Verspätung", the "+2" only where the setting shows one.
 */
internal fun stepShownTime(context: Context, step: TripStep): ShownTime {
    val delay = stepDelay(step)
    return shownTime(AppPrefs(context).delayDisplay, step.time, stepPlannedTime(step), step.leg?.realtime == true && !step.leave, delay)
}

/** The clock time of the next step and, as the delay setting says, its delay ("08:37 +1"); never a delay for an estimate. */
private fun stepClockText(context: Context, step: TripStep): String {
    val shown = stepShownTime(context, step)
    return shownTimeText(if (step.estimated) shown.copy(delay = null) else shown, { it.clock() }) { delay ->
        context.getString(R.string.delay_minutes, delay.toInt())
    }
}

/** The delay of the next step in minutes: of the departure before boarding, of the arrival on board. */
internal fun stepDelay(step: TripStep): Long {
    val leg = step.leg ?: return 0
    // The time to leave is the walk's start: a delay of the ride does not belong to it.
    if (!leg.realtime || step.leave) return 0
    return when (step.kind) {
        StepKind.Board -> leg.departureDelayMinutes
        StepKind.GetOff -> leg.arrivalDelayMinutes
        else -> 0
    }
}

/**
 * "9 min" while the step is near, "now" under a minute, its clock time when it is far; null
 * once arrived. With [about], a time the app estimated (see plausible) says "ca." in front.
 */
private fun timeLabel(context: Context, step: TripStep, minutes: Long, about: Boolean): String? {
    val prefix = if (about && step.estimated) context.getString(R.string.estimated_prefix) + " " else ""
    return when {
        step.kind == StepKind.Arrived -> null
        minutes < 1 -> context.getString(R.string.trip_now)
        // As the companion's counter (#148b): the clock time from an hour on, without "an"/"um"
        // here (the chip of the status bar has its icon for that and no room).
        !NotificationClock.shows(minutes) -> prefix + context.getString(R.string.trip_unit_minutes, minutes)
        else -> prefix + stepShownTime(context, step).clock.clock()
    }
}

/**
 * Whether the notification shows the clock time with [minutes] to go (#148b): the rule of the
 * companion's counter ([heroShowsClock]), remembering what it showed last for the hysteresis.
 */
private object NotificationClock {
    @Volatile
    private var shown = false

    fun shows(minutes: Long): Boolean = heroShowsClock(minutes, shown).also { shown = it }
}

/**
 * The step with its time in front: "Get off in 2 min", "Board now", "Get off at 10:42". A time
 * the app estimated (see plausible) says so: "Get off in ca. 2 min", "Get off at ca. 10:42".
 */
private fun headline(context: Context, step: TripStep, minutes: Long): String {
    val about = step.estimated
    val ids = when (step.kind) {
        StepKind.GetOff -> intArrayOf(
            R.string.trip_get_off_now,
            if (about) R.string.trip_get_off_in_about else R.string.trip_get_off_in,
            R.string.trip_get_off_at,
        )
        StepKind.Board -> if (step.leave) {
            intArrayOf(R.string.trip_leave_now, R.string.trip_leave_in, R.string.trip_leave_at)
        } else {
            intArrayOf(R.string.trip_board_now, R.string.trip_board_in, R.string.trip_board_at)
        }
        StepKind.WalkToEnd -> intArrayOf(
            R.string.trip_walk_now,
            if (about) R.string.trip_walk_in_about else R.string.trip_walk_in,
            R.string.trip_walk_at,
        )
        StepKind.Arrived -> return context.getString(R.string.arrived)
    }
    val clock = (if (about && step.kind != StepKind.Board) context.getString(R.string.estimated_prefix) + " " else "") + stepShownTime(context, step).clock.clock()
    // From an hour on the clock time ("Get off at 03:11"), below it the minutes (#148b, as the counter).
    return when {
        minutes < 1 -> context.getString(ids[0])
        !NotificationClock.shows(minutes) -> context.getString(ids[1], minutes)
        else -> context.getString(ids[2], clock)
    }
}

/**
 * The short title of a Live Update: the step and its clock time with the delay, "Aussteigen
 * 09:58", "Einsteigen 08:37 +1", "Warte auf 66 · ca. 10:08". The minutes stand in the chip of
 * the status bar and beside the title, so they are not said twice.
 */
internal fun shortHeadline(context: Context, step: TripStep): String {
    val about = if (step.estimated) context.getString(R.string.estimated_prefix) + " " else ""
    // As the delay setting says (#227).
    val clock = about + stepClockText(context, step)
    return when (step.kind) {
        StepKind.GetOff -> context.getString(R.string.trip_short_get_off, clock)
        StepKind.Board -> when {
            step.leave -> context.getString(R.string.trip_short_leave, clock)
            step.waiting -> context.getString(R.string.trip_short_wait, shortLine(step.leg?.line) ?: "", clock)
            else -> context.getString(R.string.trip_short_board, clock)
        }
        StepKind.WalkToEnd -> context.getString(R.string.trip_short_walk, clock)
        StepKind.Arrived -> context.getString(R.string.arrived)
    }
}

/** "Platform 2", "Bay 13" for a tram or bus; texts such as "Pos. 11" already say what they are (see platformLabel). */
private fun platformText(context: Context, platform: String?, product: String?): String? = platformLabel(context, platform, product)

/** Where the step happens, with the platform when it is known: "S+U Hauptbahnhof · Platform 2". */
internal fun placeLine(context: Context, step: TripStep, journey: Journey): String {
    val leg = step.leg
    val platform = when (step.kind) {
        StepKind.GetOff -> platformText(context, leg?.arrivalPlatform, leg?.product)
        StepKind.Board -> platformText(context, leg?.departurePlatform, leg?.product)
        else -> null
    }
    val stop = if (step.kind == StepKind.Arrived) {
        journey.legs.last().to.plainStopName()
    } else {
        io.github.jnbp.nextstop.data.pageRideStop(step.leg, step.place, io.github.jnbp.nextstop.data.railwayNamesOf(journey))
    }
    // Before boarding the real departure with its delay ("08:37 +1"): "Board in 5 min" alone hides it.
    val departure = if (step.kind == StepKind.Board && !step.leave) {
        // As the delay setting says (#227).
        stepClockText(context, step)
    } else {
        null
    }
    return listOfNotNull(stop, platform, departure).joinToString(" · ")
}

/**
 * The step after this one and the arrival time: "then 4 min walk · arr. 09:52". After
 * getting off it is the walk or the next ride; after boarding, the stop to ride to.
 */
private fun thenLine(context: Context, step: TripStep, journey: Journey): String {
    // The arrival by the plausible times; "ca." where it is the app's estimate.
    val end = journey.plausible().legs.last()
    val about = if (end.arrivalEstimated) context.getString(R.string.estimated_prefix) + " " else ""
    val arrival = context.getString(R.string.trip_arrive_at, about + end.arrival.clock())
    val legs = journey.legs
    val index = step.leg?.let { current -> legs.indexOfFirst { it === current } } ?: -1
    val then = when {
        index < 0 -> null
        // Before leaving: the ride the walk leads to, at its own time.
        step.kind == StepKind.Board && step.leave ->
            context.getString(R.string.trip_then_ride, shortLine(legs[index].line) ?: "", journey.plausible().legs[index].departure.clock())
        step.kind == StepKind.Board -> context.getString(R.string.trip_then_ride_to, shownRideStop(legs[index], legs[index].to))
        step.kind == StepKind.GetOff -> {
            val next = legs.getOrNull(index + 1)
            when {
                next == null -> null
                next.walking -> context.getString(
                    if (next.cycling) R.string.trip_then_cycle else R.string.trip_then_walk,
                    durationText(context, minutesBetween(next.departure, next.arrival).coerceAtLeast(1)),
                )
                else -> context.getString(R.string.trip_then_ride, shortLine(next.line) ?: "", next.departure.clock())
            }
        }
        else -> null
    }
    return if (then == null) arrival else context.getString(R.string.trip_join, then, arrival)
}

private fun seconds(from: OffsetDateTime, to: OffsetDateTime): Int =
    Duration.between(from, to).seconds.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

/**
 * The badge of a line as in the app: its name in a rounded box of its colour, [heightDp]
 * high. It is never wider than twice its height, so it can also ride along a bar.
 */
private fun lineBadge(context: Context, leg: Leg, heightDp: Float = 24f, outline: Int? = null, plain: Boolean = false): Bitmap {
    val metrics = context.resources.displayMetrics
    val density = metrics.density
    val scale = heightDp / 24f
    // A rail replacement service: "SEV" and the line it stands in for, on white in a ring of
    // that line's colour with stripes, as the badge in the app (RailReplacementBadge).
    val replacement = leg.railReplacement
    val replaced = replacement?.replaced
    val name = if (replacement != null) listOfNotNull(SEV_MARK, replaced).joinToString(" ") else badgeLine(leg.line) ?: ""
    val tone = if (replaced != null) lineColor(replaced, replacement?.replacedProductOrGuess ?: replacedProduct(replaced), leg.ridePlace) else lineColor(leg)
    // [plain]: a replacement service as a calm badge in the colour of its line, "SEV" small in front, no stripes.
    val background = if (replacement != null && !plain) androidx.compose.ui.graphics.Color.White else tone
    val height = (heightDp * density).roundToInt()
    val maxWidth = height * 2
    val padding = 7 * density * scale
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = onLineColor(background).toArgb()
        textAlign = Paint.Align.CENTER
        textSize = 14 * density * scale
        typeface = try {
            ResourcesCompat.getFont(context, R.font.google_sans_flex_800)
        } catch (e: Exception) {
            null
        } ?: Typeface.DEFAULT_BOLD
    }
    // A long name gets smaller instead of the badge wider.
    while (paint.measureText(name) + 2 * padding > maxWidth && paint.textSize > 8 * density * scale) {
        paint.textSize -= density
    }
    val width = (paint.measureText(name) + 2 * padding).roundToInt().coerceIn(height, maxWidth)
    val bitmap = Bitmap.createBitmap(metrics, width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background.toArgb() }
    // The corner of the app's badge (LINE_BADGE_CORNER on a badge APP_BADGE_HEIGHT_DP high), to scale.
    val corner = height * LINE_BADGE_CORNER.value / APP_BADGE_HEIGHT_DP
    canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), corner, corner, fill)
    if (replacement != null && !plain) {
        val ring = 2f * density * scale
        val half = ring / 2f
        val box = RectF(half, half, width - half, height - half)
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(box, corner - half, corner - half, Path.Direction.CW) })
        val stripe = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 2f * density * scale
            color = tone.copy(alpha = 0.22f).toArgb()
        }
        var x = -height.toFloat()
        while (x < width) {
            canvas.drawLine(x, height.toFloat(), x + height, 0f, stripe)
            x += 7f * density * scale
        }
        canvas.restore()
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = ring
            color = tone.toArgb()
        }
        canvas.drawRoundRect(box, corner - half, corner - half, stroke)
    } else if (outline != null) {
        // A thin ring, so a badge in a colour close to the ground of the box stands out from it.
        val ring = 1.5f * density
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = ring
            color = outline
        }
        val half = ring / 2f
        canvas.drawRoundRect(RectF(half, half, width - half, height - half), corner - half, corner - half, stroke)
    }
    val baseline = height / 2f - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
    if (replacement != null && plain && name.startsWith(SEV_MARK) && name.length > SEV_MARK.length) {
        // "SEV" small, the line it stands in for in full size.
        val rest = name.substring(SEV_MARK.length).trimStart()
        val small = Paint(paint).apply { textSize = paint.textSize * 0.62f; textAlign = Paint.Align.LEFT }
        val big = Paint(paint).apply { textAlign = Paint.Align.LEFT }
        val gap = 3 * density * scale
        val total = small.measureText(SEV_MARK) + gap + big.measureText(rest)
        var x = (width - total) / 2f
        canvas.drawText(SEV_MARK, x, baseline, small)
        x += small.measureText(SEV_MARK) + gap
        canvas.drawText(rest, x, baseline, big)
        return bitmap
    }
    canvas.drawText(name, width / 2f, baseline, paint)
    return bitmap
}

// The height of the app's line badge: a line of 24 sp text and 4 dp above and below (see LineBadge).
private const val APP_BADGE_HEIGHT_DP = 32f

// The side of the large icon drawn: at least the 64 dp of the classic slot, so a larger slot
// (a Live Update's right-side image) is downscaled, never blown up.
private const val LARGE_ICON_MIN_DP = 64f

/**
 * The badge of [leg]'s line as the large icon of the notification: the badge as in the app,
 * centred with clear margin in a transparent square (see [fitBadgeInIcon]). The system fills
 * the square slot with the icon and crops or masks it; a bare badge, wider than high, lost its
 * ends and its rounded corners there.
 */
private fun lineBadgeIcon(context: Context, leg: Leg): Bitmap {
    val metrics = context.resources.displayMetrics
    val density = metrics.density
    val slot = try {
        context.resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width)
    } catch (e: Exception) {
        0
    }
    val size = maxOf(slot, (LARGE_ICON_MIN_DP * density).roundToInt())
    // Drawn at the full height it may take, so the text is as large as the width allows
    // (a long name like "ICE 1092" gets smaller, see lineBadge) and no scaling blurs it.
    val badge = lineBadge(context, leg, heightDp = size * ICON_BADGE_MAX_HEIGHT / density, plain = true)
    val fit = fitBadgeInIcon(size.toFloat(), badge.width.toFloat(), badge.height.toFloat())
    val icon = Bitmap.createBitmap(metrics, size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(icon)
    canvas.translate(fit.left, fit.top)
    canvas.scale(fit.scale, fit.scale)
    canvas.drawBitmap(badge, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    return icon
}

/**
 * The small marker that rides along the bar of a trip: a dot in the colour of [leg]'s line with a white rim.
 * [sizeDp] is the square it is drawn on and [insetDp] the clear margin around the rim, so that
 * scaling and clipping to that square never cut the antialiased edge of the rim.
 */
private fun trackerMarker(context: Context, leg: Leg, sizeDp: Float = 14f, rimDp: Float = 2.5f, insetDp: Float = 0f): Bitmap {
    val density = context.resources.displayMetrics.density
    val size = (sizeDp * density).roundToInt()
    val bitmap = Bitmap.createBitmap(context.resources.displayMetrics, size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val replaced = leg.railReplacement?.replaced
    val tone = if (replaced != null) lineColor(replaced, leg.railReplacement?.replacedProductOrGuess ?: replacedProduct(replaced), leg.ridePlace) else lineColor(leg)
    val outer = size / 2f - insetDp * density
    paint.color = 0xFFFFFFFF.toInt()
    canvas.drawCircle(size / 2f, size / 2f, outer, paint)
    paint.color = tone.toArgb()
    canvas.drawCircle(size / 2f, size / 2f, outer - rimDp * density, paint)
    return bitmap
}

// The bar of a Live Update as the platform draws it (NotificationProgressBar and
// NotificationProgressDrawable): the tracker is scaled to a box 20 dp high and clipped to it,
// points are squares of 12 dp, and segments end 2 dp apart with fully rounded caps.
private const val LIVE_TRACKER_DP = 20f
private const val LIVE_POINT_RADIUS_DP = 6f
// About the margins of a promoted notification around its bar, to guess the width of the bar.
private const val LIVE_BAR_MARGINS_DP = 64f

/**
 * The parts of [track] for the bar of a Live Update: stretches too short to show as more than
 * a dot (shorter than their rounded caps and gaps) go to their neighbour, so every stretch has
 * clean caps.
 */
private fun liveParts(track: Track, minLength: Int): List<TrackPart> {
    val merged = mutableListOf<TrackPart>()
    track.parts.forEach { part ->
        val last = merged.lastOrNull()
        when {
            last != null && part.length < minLength -> merged[merged.size - 1] = TrackPart(last.length + part.length, last.color, last.ride)
            last != null && last.length < minLength -> merged[merged.size - 1] = TrackPart(last.length + part.length, part.color, part.ride)
            else -> merged.add(part)
        }
    }
    return merged
}

/**
 * The positions of the points of a Live Update that show cleanly: none so close to either end
 * that the platform pushes it half off the bar, and none under or beside the tracker. The
 * platform places the tracker at progress * (width - tracker width), not centred on the
 * progress, so a point at the progress peeks out beside it as a half ring.
 */
private fun livePointShows(at: Int, total: Int, progress: Int, hasTracker: Boolean, barPx: Float, density: Float): Boolean {
    val f = at.toFloat() / total
    val edge = (LIVE_POINT_RADIUS_DP + 2f) * density / barPx
    if (f < edge || f > 1f - edge) return false
    if (!hasTracker) return true
    val tracker = LIVE_TRACKER_DP * density
    val p = progress.toFloat() / total
    val centre = (tracker / 2f + p * (barPx - tracker)) / barPx
    val reach = (tracker / 2f + (LIVE_POINT_RADIUS_DP + 2f) * density) / barPx
    return f < minOf(p, centre) - reach || f > maxOf(p, centre) + reach
}

/** One stretch of the bar of a trip: [length] seconds in [color]; [ride] is null for walks and waits. */
private class TrackPart(val length: Int, val color: Int, val ride: Leg?)

/**
 * The whole trip as a bar: stretches in order, the positions of the changes, and the
 * positions of the stops of the ride on board, all in seconds from the start.
 */
private class Track(val parts: List<TrackPart>, val changes: List<Int>, val stops: List<Int>, val total: Int)

private fun track(journey: Journey, now: OffsetDateTime): Track {
    val legs = journey.legs
    val firstRide = legs.indexOfFirst { !it.walking }
    val parts = mutableListOf<TrackPart>()
    val changes = mutableListOf<Int>()
    val stops = mutableListOf<Int>()
    var cursor = journey.departure
    var position = 0
    legs.forEachIndexed { index, leg ->
        val wait = seconds(cursor, leg.departure)
        if (wait > 0) {
            parts.add(TrackPart(wait, WALK_ARGB, null))
            position += wait
        }
        if (!leg.walking && index != firstRide && position > 0) changes.add(position)
        // A delayed ride can overlap the next one: the bar never runs backwards.
        val begin = if (leg.departure.isAfter(cursor)) leg.departure else cursor
        val length = seconds(begin, leg.arrival)
        if (length > 0) {
            val riding = !leg.walking && !now.isBefore(leg.departure) && now.isBefore(leg.arrival)
            if (riding) {
                // The stops between boarding and getting off, where the ride calls on its way.
                leg.stopovers.drop(1).dropLast(1).mapNotNull { it.time }.forEach { time ->
                    val at = seconds(begin, time)
                    if (at in 1 until length) stops.add(position + at)
                }
            }
            // Calm: each ride in the plain colour of its line (a replacement service in that of the line it replaces).
            val replaced = leg.railReplacement?.replaced
            val color = when {
                leg.walking -> WALK_ARGB
                replaced != null -> lineColor(replaced, leg.railReplacement?.replacedProductOrGuess ?: replacedProduct(replaced), leg.ridePlace).toArgb()
                else -> lineColor(leg).toArgb()
            }
            parts.add(TrackPart(length, color, if (leg.walking) null else leg))
            position += length
        }
        if (leg.arrival.isAfter(cursor)) cursor = leg.arrival
    }
    // Evenly fewer stops when a long ride has many.
    val every = (stops.size + MAX_STOP_POINTS - 1) / MAX_STOP_POINTS
    val shownStops = if (every <= 1) stops else stops.filterIndexed { index, _ -> index % every == 0 }
    return Track(parts, changes, shownStops, position)
}

/** The ride the trip is on or waits for, whose badge rides along the bar. */
private fun trackerRide(content: TripContent): Leg? = content.step.leg ?: content.journey.rides.lastOrNull()

/**
 * The bar of the unfolded notification as a picture, since a custom notification cannot
 * draw segments: one stretch per ride in its line colour and grey for walks and waits,
 * faded ahead of now, a ring at every change, small dots at the stops of the ride on
 * board, and the badge of the current line at the position of now.
 */
private fun trackBitmap(context: Context, content: TripContent): Bitmap? {
    val track = track(content.shown, content.now)
    if (track.total <= 0) return null
    val metrics = context.resources.displayMetrics
    val density = metrics.density
    // About as wide as the content of a notification; the picture is scaled to the exact width.
    val width = (metrics.widthPixels - 112 * density).roundToInt().coerceAtLeast((160 * density).roundToInt())
    val height = (20 * density).roundToInt()
    val bitmap = Bitmap.createBitmap(metrics, width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    // A small marker in the colour of the line rides along, not the whole badge over the bar.
    val badge = trackerRide(content)?.let { trackerMarker(context, it) }
    // The bar leaves room at both ends for half a badge.
    val inset = (badge?.width ?: 0) / 2f
    val left = inset
    val right = width - inset
    val span = (right - left).coerceAtLeast(1f)
    val middle = height / 2f
    val thickness = 6 * density
    val gap = 2 * density
    val progress = seconds(content.shown.departure, content.now).coerceAtMost(track.total)
    val now = left + span * progress / track.total
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var start = 0
    track.parts.forEach { part ->
        val x0 = left + span * start / track.total + gap / 2f
        val x1 = left + span * (start + part.length) / track.total - gap / 2f
        start += part.length
        if (x1 - x0 < 1f) return@forEach
        val rect = RectF(x0, middle - thickness / 2f, x1, middle + thickness / 2f)
        // Ahead of now faded, behind it in full colour.
        paint.color = part.color
        paint.alpha = 90
        canvas.drawRoundRect(rect, thickness / 2f, thickness / 2f, paint)
        if (now > x0) {
            canvas.save()
            canvas.clipRect(x0, 0f, now, height.toFloat())
            paint.alpha = 255
            canvas.drawRoundRect(rect, thickness / 2f, thickness / 2f, paint)
            canvas.restore()
        }
    }
    paint.alpha = 255
    track.stops.forEach { at ->
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(left + span * at / track.total, middle, 1.6f * density, paint)
    }
    track.changes.forEach { at ->
        val x = left + span * at / track.total
        paint.style = Paint.Style.FILL
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(x, middle, 5 * density, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2 * density
        paint.color = content.palette.blue.toArgb()
        canvas.drawCircle(x, middle, 4 * density, paint)
        paint.style = Paint.Style.FILL
    }
    if (badge != null) {
        val x = now.coerceIn(left, right) - badge.width / 2f
        canvas.drawBitmap(badge, x, (height - badge.height) / 2f, null)
    }
    return bitmap
}

/** "2 min" with the unit smaller, as in the lime box of the app; "now" and clock times as they are. */
private fun minutesText(content: TripContent): CharSequence? {
    val time = content.time ?: return null
    // The unit follows the last space, behind the number ("ca. 2 min" as well as "2 min").
    val unit = time.lastIndexOf(' ')
    if (unit <= 0 || !time[unit - 1].isDigit()) return time
    return SpannableString(time).apply {
        setSpan(RelativeSizeSpan(0.6f), unit, time.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}

/** The box of the next step: grounds tinted in the colours of the scheme, the minutes, the step and the badge. */
private fun fillBox(context: Context, views: RemoteViews, content: TripContent) {
    val palette = content.palette
    val onBlue = palette.onBlue.toArgb()
    views.setInt(R.id.trip_box_ground, "setColorFilter", palette.blue.toArgb())
    // All text in the box in the colour on the main colour; the minutes on a slightly lighter
    // ground of the same colour, so no colour of the scheme can end up unreadable on it.
    views.setInt(R.id.trip_minutes_ground, "setColorFilter", mix(palette.blue.toArgb(), onBlue, 0.18f))
    views.setTextColor(R.id.trip_minutes, onBlue)
    views.setTextColor(R.id.trip_label, palette.onBlue.copy(alpha = 0.8f).toArgb())
    views.setTextColor(R.id.trip_step, onBlue)
    views.setTextColor(R.id.trip_hint, palette.onBlue.copy(alpha = 0.9f).toArgb())
    if (content.struck != null) {
        views.setTextViewText(R.id.trip_delay, content.struck)
        views.setViewVisibility(R.id.trip_delay, View.VISIBLE)
    } else if (content.delay > 0) {
        views.setTextViewText(R.id.trip_delay, context.getString(R.string.delay_minutes, content.delay.toInt()))
        views.setViewVisibility(R.id.trip_delay, View.VISIBLE)
    } else {
        views.setViewVisibility(R.id.trip_delay, View.GONE)
    }
    if (content.hint != null) {
        views.setTextViewText(R.id.trip_hint, content.hint)
        views.setViewVisibility(R.id.trip_hint, View.VISIBLE)
    } else {
        views.setViewVisibility(R.id.trip_hint, View.GONE)
    }
    val leg = content.step.leg
    if (leg != null) {
        views.setImageViewBitmap(R.id.trip_badge, lineBadge(context, leg, outline = onBlue, plain = true))
        views.setViewVisibility(R.id.trip_badge, View.VISIBLE)
    } else {
        views.setViewVisibility(R.id.trip_badge, View.GONE)
    }
    views.setTextViewText(R.id.trip_step, content.label)
    val minutes = minutesText(content)
    if (minutes != null) {
        views.setTextViewText(R.id.trip_minutes, minutes)
        views.setViewVisibility(R.id.trip_minutes_box, View.VISIBLE)
    } else {
        views.setViewVisibility(R.id.trip_minutes_box, View.GONE)
    }
}

/** [from] blended towards [to] by [share], as one opaque colour. */
private fun mix(from: Int, to: Int, share: Float): Int {
    fun channel(shift: Int): Int {
        val a = (from shr shift) and 0xFF
        val b = (to shr shift) and 0xFF
        return (a + (b - a) * share).roundToInt().coerceIn(0, 255)
    }
    return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}

/** The bar of all rides and the line with the step after this one. */
private fun fillTrack(context: Context, views: RemoteViews, content: TripContent) {
    val bar = trackBitmap(context, content)
    if (bar != null) {
        views.setImageViewBitmap(R.id.trip_bar, bar)
        views.setViewVisibility(R.id.trip_bar, View.VISIBLE)
    } else {
        views.setViewVisibility(R.id.trip_bar, View.GONE)
    }
    views.setTextViewText(R.id.trip_then, content.then)
}

/** The look of the app in a custom layout inside the frame of the system. Never a Live Update. */
private fun customNotification(
    context: Context,
    content: TripContent,
    open: PendingIntent,
    end: PendingIntent,
    endLabel: String,
    maps: PendingIntent? = null,
    mapsText: String? = null,
): Notification {
    val collapsed = RemoteViews(context.packageName, R.layout.notification_trip)
    fillBox(context, collapsed, content)
    val expanded = RemoteViews(context.packageName, R.layout.notification_trip_expanded)
    fillBox(context, expanded, content)
    fillTrack(context, expanded, content)
    val text = content.hint?.let { context.getString(R.string.trip_join, content.place, it) } ?: content.place
    // The same content for a lock screen that shows only public versions: in the frame of the
    // system with the same title, text, colour and action.
    val publicVersion = NotificationCompat.Builder(context, content.channel)
        .setSmallIcon(R.drawable.ic_stat_trip)
        .setColor(content.palette.blue.toArgb())
        .setContentTitle(content.headline)
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setShowWhen(false)
        .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setContentIntent(open)
        .addAction(R.drawable.ic_stat_trip, endLabel, end)
        .build()
    return NotificationCompat.Builder(context, content.channel)
        .setSmallIcon(R.drawable.ic_stat_trip)
        .setColor(content.palette.blue.toArgb())
        // What the system shows where the custom layout is not used, on a watch for example.
        .setContentTitle(content.headline)
        .setContentText(text)
        .setStyle(NotificationCompat.DecoratedCustomViewStyle())
        .setCustomContentView(collapsed)
        .setCustomBigContentView(expanded)
        .setOngoing(true)
        // No sound or vibration comes from the channel; only the first post may alert at all.
        // An alert to get off (see AlightReminders) sounds once, on the channel of getting off.
        .setOnlyAlertOnce(content.alert == null)
        .apply { if (content.alert != null) setDefaults(NotificationCompat.DEFAULT_ALL) }
        .setPublicVersion(publicVersion)
        .setShowWhen(false)
        .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .setContentIntent(open)
        .apply { if (maps != null) addAction(R.drawable.ic_stat_trip, mapsText ?: context.getString(R.string.navigate), maps) }
        .addAction(R.drawable.ic_stat_trip, endLabel, end)
        .build()
}

/**
 * From Android 16 on: a Live Update, as close to the look of the app as the platform
 * allows. The badge of the line is the large icon, the minutes are in the title and in the
 * chip of the status bar, and the bar has one segment per ride in the colour of its line,
 * grey stretches for walks and waits, a point at every change and at the stops of the ride
 * on board, and the badge riding along. A Live Update allows no custom layout and must not
 * be colorized, so the main colour of the scheme is only its accent.
 */
@RequiresApi(Build.VERSION_CODES.BAKLAVA)
private fun liveUpdate(
    context: Context,
    content: TripContent,
    open: PendingIntent,
    end: PendingIntent,
    endLabel: String,
    maps: PendingIntent? = null,
    mapsText: String? = null,
): Notification {
    val track = track(content.shown, content.now)
    val blue = content.palette.blue.toArgb()
    val style = Notification.ProgressStyle().setStyledByProgress(true)
    val tracker = trackerRide(content)
    if (track.total > 0) {
        val metrics = context.resources.displayMetrics
        val density = metrics.density
        val barPx = (metrics.widthPixels - LIVE_BAR_MARGINS_DP * density).coerceAtLeast(160 * density)
        val progress = seconds(content.shown.departure, content.now).coerceIn(0, track.total)
        val shows = { at: Int -> livePointShows(at, track.total, progress, tracker != null, barPx, density) }
        val points = track.changes.filter(shows).map { Notification.ProgressStyle.Point(it).setColor(blue) } +
            track.stops.filter(shows).map { Notification.ProgressStyle.Point(it).setColor(0xFFFFFFFF.toInt()) }
        // Shorter than two caps and a gap: shown as a dot rather than a stretch.
        val minLength = (track.total * 8f * density / barPx).roundToInt()
        style.setProgressSegments(liveParts(track, minLength).map { Notification.ProgressStyle.Segment(it.length).setColor(it.color) })
            .setProgressPoints(points)
            .setProgress(progress)
    } else {
        style.setProgressIndeterminate(true)
    }
    // A small marker rides along the bar; a whole badge (striped for a replacement service) covered it.
    // Drawn at the size of the platform's tracker box, with the rim inset from its edges, so it
    // is neither scaled up nor clipped (the same dot as before: about 19 dp with a rim of 3.4 dp).
    if (tracker != null) {
        style.setProgressTrackerIcon(Icon.createWithBitmap(trackerMarker(context, tracker, LIVE_TRACKER_DP, rimDp = 3.4f, insetDp = 0.5f)))
    }
    val builder = Notification.Builder(context, content.channel)
        .setSmallIcon(R.drawable.ic_stat_trip)
        .setColor(blue)
        // Short: the minutes are in the chip already ("Aussteigen 09:58", not "In ca. 26 min au…").
        .setContentTitle(content.short)
        // Where to board, when known, right after the place of the step.
        .setContentText(content.hint?.let { context.getString(R.string.trip_join, content.place, it) } ?: content.place)
        .setSubText(content.then)
        .setStyle(style)
        .setOngoing(true)
        // Quiet updates; an alert to get off (see AlightReminders) sounds once.
        .setOnlyAlertOnce(content.alert == null)
        .setCategory(Notification.CATEGORY_NAVIGATION)
        .setVisibility(Notification.VISIBILITY_PUBLIC)
        .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        .setContentIntent(open)
        .apply {
            if (maps != null) {
                addAction(Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_stat_trip), mapsText ?: context.getString(R.string.navigate), maps).build())
            }
        }
        .addAction(
            Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_stat_trip), endLabel, end).build(),
        )
        .addExtras(Bundle().apply { putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true) })
    // The badge of the line instead of the logo of the app.
    content.step.leg?.let { builder.setLargeIcon(Icon.createWithBitmap(lineBadgeIcon(context, it))) }
    // The minutes in the chip of the status bar. The short critical text comes first; the time
    // of the next step is the system's fallback for the chip (it counts down to it while that
    // lies ahead), for builds of the system that leave the short text out. Without it the
    // chip has nothing but the icon.
    val time = content.chip
    if (time != null) {
        builder.setShortCriticalText(time)
            .setWhen(content.step.time.toInstant().toEpochMilli())
            .setShowWhen(true)
    } else {
        builder.setShowWhen(false)
    }
    return builder.build()
}
