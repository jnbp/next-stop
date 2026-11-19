package io.github.jnbp.nextstop.ui

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.OffsetDateTime
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.data.CompanionPosition
import io.github.jnbp.nextstop.data.FixKind
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.PLANNED_LEAD_CHOICES
import io.github.jnbp.nextstop.data.PlannedTrip
import io.github.jnbp.nextstop.data.PlannedTrips
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.LaunchedEffect
import io.github.jnbp.nextstop.data.WAKE_LEAD_CHOICES
import io.github.jnbp.nextstop.data.WakeAlarmMode
import io.github.jnbp.nextstop.data.plainStopName
import io.github.jnbp.nextstop.ui.theme.AppColors

/**
 * What the companion knows beyond the times, below its banner, each part coming and going
 * softly (see [SettingReveal]): where the position puts the traveller ("Next stop: …", "On
 * board already"), a change of platform ahead (see [PlatformAlerts]), and the wake-up alarm of
 * the trip ([WakeAlarm]) while it rings: "Ich bin wach" (its switch is the alarm tile, WakeAlarmTile).
 * [journey] is the journey the companion shows (plausible times). Reads the process-wide
 * states itself, so the screen around it passes nothing new.
 */
@Composable
internal fun CompanionExtrasStrip(journey: Journey, now: OffsetDateTime, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val fixState by CompanionPosition.fix.collectAsState()
    val shifts by PlatformAlerts.shifts.collectAsState()
    val ringing by WakeAlarm.ringing.collectAsState()
    // Fresh and of this journey only (a position that stopped coming hands back to the times).
    val fix = fixState?.takeIf { CompanionPosition.fixFor(journey, now.toInstant().toEpochMilli()) === it }
    val positionText = fix?.let { found ->
        val leg = journey.legs.getOrNull(found.legIndex)
        when {
            leg == null -> null
            // On board by the times only (#224): nothing the position says.
            found.byTimes -> null
            found.kind == FixKind.OnBoardEarly -> stringResource(R.string.companion_on_board, leg.to.plainStopName())
            // The vehicle stands (jam, stop): the time of getting off is the live one (see RideState).
            found.kind == FixKind.OnRide && currentRideState(journey, now).standing -> stringResource(R.string.companion_standing)
            // Waiting at the stop, or on foot from the ride just left: what the position says.
            found.kind == FixKind.AtBoarding && found.legIndex > 0 -> stringResource(R.string.companion_waiting, shortLine(leg.line) ?: leg.line.orEmpty())
            found.kind == FixKind.Alighted -> journey.legs.drop(found.legIndex + 1).firstOrNull { !it.walking }?.let { next ->
                stringResource(R.string.companion_walking, shortLine(next.line) ?: next.line.orEmpty())
            }
            found.kind == FixKind.OnRide -> leg.stopovers.getOrNull(found.nextStop)?.let { stop ->
                // One station, one name on the page (#157).
                val name = io.github.jnbp.nextstop.data.journeyStopName(journey, stop.name)
                if (found.nextStop == leg.stopovers.lastIndex) {
                    stringResource(R.string.companion_next_stop_exit, name)
                } else {
                    stringResource(R.string.companion_next_stop, name)
                }
            }
            else -> null
        }
    }
    // Changes of platform of rides not over yet.
    val legs = journey.legs
    val shift = shifts.lastOrNull { legs.getOrNull(it.legIndex)?.arrival?.isAfter(now) == true }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SettingReveal(visible = positionText != null) {
            FadeSwap(target = positionText.orEmpty()) { text ->
                ExtraRow(
                    glyph = Glyph.Locate,
                    title = text,
                    caption = stringResource(R.string.companion_by_location),
                    ground = AppColors.BlueContainer,
                    ink = AppColors.OnBlueContainer,
                )
            }
        }
        SettingReveal(visible = shift != null) {
            val shown = shift
            if (shown != null) {
                ExtraRow(
                    glyph = Glyph.Station,
                    title = stringResource(R.string.platform_changed_short, shown.now),
                    caption = PlatformAlerts.text(context, journey, shown),
                    ground = AppColors.AmberContainer,
                    ink = AppColors.AmberInk,
                )
            }
        }
        SettingReveal(visible = ringing != null) {
            PrimaryButton(
                text = stringResource(R.string.wake_confirm),
                onClick = { WakeAlarm.confirm(context) },
                modifier = Modifier.fillMaxWidth(),
                minHeight = 64.dp,
            )
        }
        // Switching the alarm for this trip is the alarm tile of the bottom row (see WakeAlarmTile).
    }
}

/** One line of the strip: a glyph, a bold [title] and a small [caption]. */
@Composable
private fun ExtraRow(glyph: Glyph, title: String, caption: String, ground: androidx.compose.ui.graphics.Color, ink: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(ground)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GlyphIcon(glyph, color = ink, size = 18.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = ink, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(text = caption, color = ink.copy(alpha = 0.8f), fontSize = 12.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The switch of the wake-up alarm for the followed trip, for the sheet of the trip (see
 * [TripActionSheet]); only while the companion runs.
 */
@Composable
internal fun WakeAlarmTripSwitch(shape: androidx.compose.ui.graphics.Shape) {
    val context = LocalContext.current
    val journey by TripState.journey.collectAsState()
    val running by TripState.serviceRunning.collectAsState()
    val forTrip by WakeAlarm.forTrip.collectAsState()
    val followed = journey ?: return
    if (!running) return
    val prefs = remember(context) { AppPrefs(context) }
    val on = remember(followed, forTrip) { WakeAlarm.on(context, followed) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.Card)
            .toggleable(value = on, role = Role.Switch) { WakeAlarm.setForTrip(context, it) }
            .defaultMinSize(minHeight = 56.dp)
            .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = stringResource(R.string.wake_alarm_trip_title), color = AppColors.Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                text = stringResource(R.string.wake_alarm_trip_text, prefs.wakeAlarmMinutes),
                color = AppColors.Muted,
                fontSize = 13.sp,
                lineHeight = 17.sp,
            )
        }
        Switch(checked = on, onCheckedChange = null, modifier = Modifier.minimumInteractiveComponentSize())
    }
}

/**
 * The settings of the alarms on the way: the wake-up alarm (off, from 60 min, always; how many
 * minutes before getting off; where Android holds them back, the ways to allow exact times and
 * the page over the lock screen), platform changes, and the reminder of planned trips.
 */
@Composable
internal fun TripAlarmsGroup() {
    val context = LocalContext.current
    val prefs = remember(context) { AppPrefs(context) }
    var mode by remember { mutableStateOf(prefs.wakeAlarmMode) }
    var minutes by remember { mutableStateOf(prefs.wakeAlarmMinutes) }
    var platforms by remember { mutableStateOf(prefs.platformAlerts) }
    var lead by remember { mutableStateOf(prefs.plannedLeadMinutes) }
    val exactMissing = remember { !exactAlarmsAllowed(context) }
    val screenMissing = remember { !fullScreenAllowed(context) }
    val last = 5
    SettingsGroup(label = stringResource(R.string.settings_group_trip_alarms)) {
        SettingsRow(
            shape = groupShape(0, last),
            title = stringResource(R.string.wake_alarm_title),
            text = stringResource(R.string.wake_alarm_text),
        ) {
            SegmentedToggle(
                labels = listOf(
                    stringResource(R.string.wake_alarm_off),
                    stringResource(R.string.wake_alarm_auto),
                    stringResource(R.string.wake_alarm_always),
                ),
                selected = mode.ordinal,
                onSelect = { index ->
                    mode = WakeAlarmMode.entries[index]
                    prefs.wakeAlarmMode = mode
                    WakeAlarm.check(context)
                },
                minHeight = 40.dp,
                ground = AppColors.Chip,
            )
        }
        SettingReveal(visible = mode != WakeAlarmMode.Off) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                SettingsRow(
                    shape = groupShape(1, last),
                    title = stringResource(R.string.wake_alarm_minutes_title),
                    text = stringResource(R.string.wake_alarm_minutes_text),
                ) {
                    SegmentedToggle(
                        labels = WAKE_LEAD_CHOICES.map { stringResource(R.string.wake_minutes_choice, it) },
                        selected = WAKE_LEAD_CHOICES.indexOf(minutes).coerceAtLeast(0),
                        onSelect = { index ->
                            minutes = WAKE_LEAD_CHOICES[index]
                            prefs.wakeAlarmMinutes = minutes
                            WakeAlarm.check(context)
                        },
                        minHeight = 40.dp,
                        ground = AppColors.Chip,
                    )
                }
                if (exactMissing) {
                    LinkRow(
                        shape = groupShape(2, last),
                        title = stringResource(R.string.wake_alarm_exact_title),
                        text = stringResource(R.string.wake_alarm_exact_text),
                        onClick = { openSystemPage(context, Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM) },
                    )
                }
                if (screenMissing && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    LinkRow(
                        shape = groupShape(2, last),
                        title = stringResource(R.string.wake_alarm_screen_title),
                        text = stringResource(R.string.wake_alarm_screen_text),
                        onClick = { openSystemPage(context, Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT) },
                    )
                }
            }
        }
        SwitchRow(
            shape = groupShape(3, last),
            title = stringResource(R.string.platform_alerts_title),
            text = stringResource(R.string.platform_alerts_text),
            checked = platforms,
            onChange = { value ->
                platforms = value
                prefs.platformAlerts = value
            },
        )
        SettingsRow(
            shape = groupShape(last, last),
            title = stringResource(R.string.planned_lead_title),
            text = stringResource(R.string.planned_lead_text),
        ) {
            SegmentedToggle(
                labels = PLANNED_LEAD_CHOICES.map { stringResource(R.string.wake_minutes_choice, it) },
                selected = PLANNED_LEAD_CHOICES.indexOf(lead).coerceAtLeast(0),
                onSelect = { index ->
                    lead = PLANNED_LEAD_CHOICES[index]
                    prefs.plannedLeadMinutes = lead
                },
                minHeight = 40.dp,
                ground = AppColors.Chip,
            )
        }
    }
}

/**
 * "Standort im Begleiter nutzen" as a row of the settings: switched on without a location
 * permission, it asks for one.
 */
@Composable
internal fun CompanionLocationRow(shape: androidx.compose.ui.graphics.Shape) {
    val context = LocalContext.current
    val prefs = remember(context) { AppPrefs(context) }
    var on by remember { mutableStateOf(prefs.companionLocation) }
    // Granted during a trip: the running companion takes up the position at once.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (CompanionLocation.permitted(context)) restartForLocation(context)
    }
    SwitchRow(
        shape = shape,
        title = stringResource(R.string.companion_location_title),
        text = stringResource(R.string.companion_location_text),
        checked = on,
        onChange = { value ->
            on = value
            prefs.companionLocation = value
            if (value && !CompanionLocation.permitted(context)) {
                launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            } else if (value) {
                restartForLocation(context)
            }
            // Off: the running companion lets go of the position at once.
            if (!value) CompanionLocation.stop()
        },
    )
}

/**
 * Starts the running trip service again from the front (the settings), so it enters the
 * foreground with the type location and follows the position; nothing without a trip.
 */
private fun restartForLocation(context: Context) {
    if (TripState.serviceRunning.value && TripState.journey.value != null) startTripService(context)
}

/** A row that opens a page of the system settings. */
@Composable
private fun LinkRow(shape: androidx.compose.ui.graphics.Shape, title: String, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.Card)
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 56.dp)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingText(title, text, modifier = Modifier.weight(1f))
        Text(text = "›", color = AppColors.Muted, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

/** True where the app may set exact alarms. */
internal fun exactAlarmsAllowed(context: Context): Boolean =
    context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: false

/** True where the app may show a page over the lock screen from a notification. */
internal fun fullScreenAllowed(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
        context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() ?: false

private fun openSystemPage(context: Context, action: String) {
    try {
        context.startActivity(Intent(action, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        openChannelSettings(context, AppChannel.of(NotifyFeature.WakeAlarm))
    }
}

/** "Fahrt planen" of a journey: plans it, or cancels it where it is planned; says so briefly. */
internal fun togglePlannedTrip(context: Context, journey: Journey, fromName: String, toName: String) {
    PlannedTrips.load(context)
    val planned = PlannedTrips.trips.value.firstOrNull { it.journey.id == journey.id }
    if (planned != null) {
        PlannedTripAlarms.cancel(context, planned.id)
        Toast.makeText(context, R.string.planned_card_cancelled, Toast.LENGTH_SHORT).show()
        return
    }
    val trip = PlannedTripAlarms.plan(context, journey, fromName, toName)
    Toast.makeText(
        context,
        context.getString(R.string.planned_toast, trip.remindAt.clock(), trip.leaveAt.clock()),
        Toast.LENGTH_LONG,
    ).show()
}

/**
 * The trips planned ahead as compact cards at the top of the start page's sheet: when to
 * leave and where to; a tap opens the trip, a cross cancels it (with "Undo", see
 * PlannedTripActions). Each comes and goes softly; nothing shows without one. Reads
 * [PlannedTrips] itself.
 */
@Composable
fun PlannedTripCards(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val all by PlannedTrips.trips.collectAsState()
    // Read the stored list once, off the main thread, and drop what is over; the flow follows
    // every change from then on.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            PlannedTrips.load(context.applicationContext)
            PlannedTrips.prune(context.applicationContext)
        }
    }
    // A trip whose time has passed goes from the cards at once (an offered start until it arrives).
    val now = rememberMinuteClock()
    val trips = remember(all, now) { all.filter { it.leaveAt.isAfter(now) || (it.offered && it.journey.arrival.isAfter(now)) } }
    // The last trips stay drawn while the cards fold away; a plain holder, not state.
    val held = remember { arrayOfNulls<List<PlannedTrip>>(1) }
    if (trips.isNotEmpty()) held[0] = trips
    val shown = held[0].orEmpty()
    SettingReveal(visible = trips.isNotEmpty(), modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (trip in shown) {
                androidx.compose.runtime.key(trip.id) {
                    SettingReveal(visible = trips.any { it.id == trip.id }) { PlannedTripCard(trip) }
                }
            }
        }
    }
}

@Composable
private fun PlannedTripCard(trip: PlannedTrip) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(AppColors.BlueContainer)
            .clickable(onClickLabel = stringResource(R.string.r3_planned_open)) { PlannedTripActions.open(trip) }
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GlyphIcon(Glyph.Calendar, color = AppColors.Blue, size = 20.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.planned_card_title),
                color = AppColors.OnBlueContainer,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.planned_card_text, trip.leaveAt.clock(), trip.toName.plainStopName()),
                color = AppColors.OnBlueContainer,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        RoundButton(
            glyph = Glyph.Close,
            description = stringResource(R.string.planned_cancel),
            onClick = { PlannedTripActions.cancel(context, trip) },
            size = 40.dp,
        )
    }
}
