package io.github.jnbp.nextstop.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.BuildConfig
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.data.DiagLog
import io.github.jnbp.nextstop.data.DiagReportInput
import io.github.jnbp.nextstop.ui.theme.AppColors
import io.github.jnbp.nextstop.ui.theme.AppFont
import io.github.jnbp.nextstop.wear.PhoneBridge
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * The end of Settings > Advanced: the version of the app, and above it the developer options
 * once the version was tapped [DEVELOPER_TAPS] times. They hold aids for testing and for bug
 * reports: the detailed recording, the bug report with the log of what happened before (see
 * DiagCapture), the last crash, the performance lab, the test of the sunny side and the
 * simulated faster connection.
 */

// Taps on the version that unlock the developer options.
private const val DEVELOPER_TAPS = 7

// From this many taps left, each tap says how many are left, as Android does.
private const val TAPS_TOLD = 3

// The longest text of "What happened?".
private const val NOTE_LENGTH = 2000

private const val LOG_TAG = "Developer"

// The numbers of the jank meter are read again this often while the options show them (ms).
private const val JANK_REFRESH_MILLIS = 1_000L

/** The version of the app, and above it the developer options once they are unlocked. */
@Composable
internal fun DeveloperSection() {
    val context = LocalContext.current
    val prefs = remember(context) { AppPrefs(context) }
    var unlocked by remember { mutableStateOf(prefs.developerOptions) }
    var taps by remember { mutableIntStateOf(0) }
    // The toast shown last, replaced by the next one so quick taps do not queue them.
    val toast = remember { arrayOfNulls<Toast>(1) }
    SettingReveal(visible = unlocked) {
        DeveloperOptions(
            prefs = prefs,
            onHide = {
                unlocked = false
                taps = 0
                prefs.developerOptions = false
                PerfLab.unlock(false)
                DiagCapture.setVerbose(context, false)
                SunSideSetting.updateTest(context, false)
                DiagLog.i(LOG_TAG) { "developer options hidden" }
            },
        )
    }
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
            color = AppColors.Muted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    val text = if (unlocked) {
                        context.getString(R.string.developer_already)
                    } else {
                        taps++
                        val left = DEVELOPER_TAPS - taps
                        when {
                            left <= 0 -> {
                                unlocked = true
                                prefs.developerOptions = true
                                PerfLab.unlock(true)
                                DiagLog.i(LOG_TAG) { "developer options unlocked" }
                                context.getString(R.string.developer_unlocked)
                            }
                            left <= TAPS_TOLD -> context.resources.getQuantityString(R.plurals.developer_taps_left, left, left)
                            else -> null
                        }
                    }
                    if (text != null) {
                        toast[0]?.cancel()
                        toast[0] = Toast.makeText(context, text, Toast.LENGTH_SHORT).also { it.show() }
                    }
                }
                .padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

/** The developer options as one group of rows. */
@Composable
private fun DeveloperOptions(prefs: AppPrefs, onHide: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var simulate by remember { mutableStateOf(prefs.simulateFaster) }
    var verboseUntil by remember { mutableLongStateOf(DiagCapture.verboseUntil) }
    var crashTime by remember { mutableStateOf<Long?>(null) }
    var events by remember { mutableIntStateOf(DiagLog.size) }
    var reportOpen by remember { mutableStateOf(false) }
    var crashShown by remember { mutableStateOf<String?>(null) }
    var sunIntroShown by remember { mutableStateOf(false) }
    // The files are read off the main thread as the options open.
    LaunchedEffect(Unit) {
        val (crash, count) = withContext(Dispatchers.IO) { DiagCapture.lastCrashTime(context) to DiagCapture.eventCount(context) }
        crashTime = crash
        events = count
    }
    SunSideSetting.load(context)
    // Shown from here it stays due: the next hint of a ride opens it again.
    if (sunIntroShown) SunSideIntroSheet(side = io.github.jnbp.nextstop.data.SunSide.Left, onDone = { sunIntroShown = false })
    // The jank meter's numbers, read again every second while it counts (see JankMeter).
    val jankOn = JankMeter.enabled
    var jankLines by remember { mutableStateOf(JankMeter.lines()) }
    var jankMap by remember { mutableStateOf(JankMeter.mapText(context)) }
    var jankTransitions by remember { mutableStateOf(JankMeter.transitionsText(context)) }
    LaunchedEffect(jankOn) {
        while (jankOn) {
            jankLines = JankMeter.lines()
            jankMap = JankMeter.mapText(context)
            jankTransitions = JankMeter.transitionsText(context)
            delay(JANK_REFRESH_MILLIS)
        }
    }
    // The switch of the jank meter; while on, a row per screen (or one saying there are none
    // yet), the map in rest, reset and copy.
    val jankRows = 1 + if (jankOn) jankLines.size.coerceAtLeast(1) + 4 else 0
    val rows = (if (crashTime != null) 11 else 10) + jankRows
    val last = rows - 1
    SettingsGroup(label = stringResource(R.string.developer_options)) {
        // Counted afresh each time the rows are composed.
        var index = 0
        val verbose = verboseUntil > System.currentTimeMillis()
        SwitchRow(
            shape = groupShape(index++, last),
            title = stringResource(R.string.developer_verbose),
            text = if (verbose) {
                stringResource(R.string.developer_verbose_until, formatTime(verboseUntil))
            } else {
                stringResource(R.string.developer_verbose_text)
            },
            checked = verbose,
            onChange = { value ->
                DiagCapture.setVerbose(context, value)
                verboseUntil = DiagCapture.verboseUntil
            },
        )
        DevActionRow(
            shape = groupShape(index++, last),
            title = stringResource(R.string.developer_report),
            text = stringResource(R.string.developer_report_text),
            onClick = { reportOpen = true },
        )
        val crash = crashTime
        if (crash != null) {
            CrashRow(
                shape = groupShape(index++, last),
                time = crash,
                onView = {
                    scope.launch {
                        crashShown = withContext(Dispatchers.IO) { DiagCapture.lastCrash(context) }
                    }
                },
                onShare = {
                    scope.launch {
                        val text = withContext(Dispatchers.IO) {
                            DiagCapture.lastCrash(context)?.let { DiagCapture.redaction(context).redact(it) }
                        } ?: return@launch
                        DiagCapture.share(context, text, context.getString(R.string.developer_crash_subject, BuildConfig.VERSION_NAME))
                    }
                },
                onDelete = {
                    DiagCapture.deleteCrash(context)
                    crashTime = null
                },
            )
        }
        DevActionRow(
            shape = groupShape(index++, last),
            title = stringResource(R.string.developer_clear_logs),
            text = stringResource(R.string.developer_clear_logs_text, events),
            onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) { DiagCapture.clearLog(context) }
                    events = 0
                    Toast.makeText(context, R.string.developer_logs_cleared, Toast.LENGTH_SHORT).show()
                }
            },
        )
        DevActionRow(
            shape = groupShape(index++, last),
            title = stringResource(R.string.developer_perf_lab),
            text = stringResource(R.string.developer_perf_lab_text),
            onClick = { PerfLab.shown = true },
        )
        SwitchRow(
            shape = groupShape(index++, last),
            title = stringResource(R.string.r5perf_jank_meter),
            text = stringResource(R.string.r5perf_jank_meter_text),
            checked = jankOn,
            onChange = { value -> JankMeter.enabled = value },
        )
        if (jankOn) {
            if (jankLines.isEmpty()) {
                DevInfoRow(shape = groupShape(index++, last), title = stringResource(R.string.r5perf_jank_meter), text = stringResource(R.string.r5perf_jank_empty))
            } else {
                val framesWord = stringResource(R.string.r5perf_jank_frames)
                val jankyWord = stringResource(R.string.r5perf_jank_janky)
                val lateWord = stringResource(R.string.r6perf_jank_late)
                for (line in jankLines) {
                    DevInfoRow(shape = groupShape(index++, last), title = line.screen, text = jankText(line, framesWord, jankyWord, lateWord))
                }
            }
            DevInfoRow(shape = groupShape(index++, last), title = stringResource(R.string.r5perf_jank_map), text = jankMap)
            DevInfoRow(shape = groupShape(index++, last), title = stringResource(R.string.r6perf_jank_transitions), text = jankTransitions)
            DevActionRow(
                shape = groupShape(index++, last),
                title = stringResource(R.string.r5perf_jank_reset),
                text = stringResource(R.string.r5perf_jank_reset_text),
                onClick = {
                    JankMeter.reset()
                    jankLines = JankMeter.lines()
                    jankMap = JankMeter.mapText(context)
                    jankTransitions = JankMeter.transitionsText(context)
                },
            )
            DevActionRow(
                shape = groupShape(index++, last),
                title = stringResource(R.string.r5perf_jank_copy),
                text = stringResource(R.string.r5perf_jank_copy_text),
                onClick = {
                    JankMeter.copy(context)
                    Toast.makeText(context, R.string.r5perf_jank_copied, Toast.LENGTH_SHORT).show()
                },
            )
        }
        DevActionRow(
            shape = groupShape(index++, last),
            title = stringResource(R.string.developer_wake_test),
            text = stringResource(R.string.developer_wake_test_text),
            onClick = { WakeAlarm.test(context) },
        )
        // Asks the watch to open its companion now, as for a trip started here.
        DevActionRow(
            shape = groupShape(index++, last),
            title = stringResource(R.string.developer_companion_test),
            text = stringResource(R.string.developer_companion_test_text),
            onClick = {
                scope.launch {
                    val result = PhoneBridge.testCompanionOnWatch(context)
                    val text = when (result) {
                        PhoneBridge.CompanionTest.Sent -> R.string.developer_companion_test_sent
                        PhoneBridge.CompanionTest.NoWatch -> R.string.developer_companion_test_no_watch
                        PhoneBridge.CompanionTest.NoAppOnWatch -> R.string.developer_companion_test_no_app
                        PhoneBridge.CompanionTest.Failed -> R.string.developer_companion_test_failed
                    }
                    Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                }
            },
        )
        // Forces the hint of the sunny side on every ride, whatever the sun and the weather.
        SwitchRow(
            shape = groupShape(index++, last),
            title = stringResource(R.string.developer_sun_test),
            text = stringResource(R.string.developer_sun_test_text),
            checked = SunSideSetting.test,
            onChange = { value -> SunSideSetting.updateTest(context, value) },
        )
        // Opens the explanation of the sunny side now, and lets it open again with the next hint.
        DevActionRow(
            shape = groupShape(index++, last),
            title = stringResource(R.string.r5_developer_sun_intro),
            text = stringResource(R.string.r5_developer_sun_intro_text),
            onClick = {
                SunSideSetting.resetIntro(context)
                sunIntroShown = true
            },
        )
        SwitchRow(
            shape = groupShape(index++, last),
            title = stringResource(R.string.developer_simulate_faster),
            text = stringResource(R.string.developer_simulate_faster_text),
            checked = simulate,
            onChange = { value ->
                simulate = value
                prefs.simulateFaster = value
            },
        )
        DevActionRow(
            shape = groupShape(index, last),
            title = stringResource(R.string.developer_hide),
            text = stringResource(R.string.developer_hide_text),
            onClick = onHide,
        )
    }
    if (reportOpen) ReportDialog(onDismiss = { reportOpen = false })
    crashShown?.let { text -> CrashDialog(text = text, onDismiss = { crashShown = null }) }
}

/** A short date and time in the format of the phone. */
private fun formatTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))

/** A row that only tells something (the numbers of the jank meter). */
@Composable
private fun DevInfoRow(shape: Shape, title: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.Card)
            .defaultMinSize(minHeight = 56.dp)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingText(title, text, modifier = Modifier.weight(1f))
    }
}

/** A row that does something on a tap, like the rows of the notification channels. */
@Composable
private fun DevActionRow(shape: Shape, title: String, text: String, onClick: () -> Unit) {
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

/** The last crash: its time, and buttons to view, share and delete it. */
@Composable
private fun CrashRow(shape: Shape, time: Long, onView: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.Card)
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 4.dp),
    ) {
        SettingText(
            title = stringResource(R.string.developer_crash, formatTime(time)),
            text = stringResource(R.string.developer_crash_text),
            modifier = Modifier.padding(end = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onView) { Text(text = stringResource(R.string.developer_crash_view)) }
            TextButton(onClick = onShare) { Text(text = stringResource(R.string.developer_crash_share)) }
            TextButton(onClick = onDelete) { Text(text = stringResource(R.string.developer_crash_delete)) }
        }
    }
}

/** The last crash as written, to read on the phone. */
@Composable
private fun CrashDialog(text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.developer_crash_view)) },
        text = {
            Text(
                text = text,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.developer_close)) }
        },
    )
}

/**
 * "Share bug report": what the report holds, a field for what happened, and Share, which
 * opens the share sheet with the redacted report. The log is taken as the dialog opens.
 */
@Composable
private fun ReportDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf("") }
    var input by remember { mutableStateOf<DiagReportInput?>(null) }
    var sharing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        DiagLog.i(LOG_TAG) { "bug report opened" }
        input = withContext(Dispatchers.IO) { DiagCapture.reportInput(context, "") }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.developer_report)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val ready = input
                Text(
                    text = if (ready == null) {
                        stringResource(R.string.developer_report_preparing)
                    } else {
                        reportSummary(context, ready)
                    },
                    color = AppColors.Muted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
                BasicTextField(
                    value = note,
                    onValueChange = { value -> note = value.take(NOTE_LENGTH) },
                    textStyle = TextStyle(color = AppColors.Ink, fontSize = 14.sp, fontFamily = AppFont),
                    cursorBrush = SolidColor(AppColors.Blue),
                    minLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppColors.Chip)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    decorationBox = { innerTextField ->
                        Box {
                            if (note.isEmpty()) {
                                Text(text = stringResource(R.string.developer_report_note), color = AppColors.Muted, fontSize = 14.sp)
                            }
                            innerTextField()
                        }
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = input != null && !sharing,
                onClick = {
                    val base = input ?: return@TextButton
                    sharing = true
                    scope.launch {
                        val withNote = DiagReportInput(base.time, note, base.about, base.settings, base.crash, base.log)
                        val text = withContext(Dispatchers.IO) { DiagCapture.reportText(context, withNote) }
                        DiagCapture.share(context, text, context.getString(R.string.developer_report_subject, BuildConfig.VERSION_NAME))
                        onDismiss()
                    }
                },
            ) { Text(text = stringResource(R.string.developer_report_share)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.cancel)) }
        },
    )
}

/** What a report holds, in a sentence: the events and since when, and whether a crash is in it. */
private fun reportSummary(context: Context, input: DiagReportInput): String {
    // A log line starts with "2026-11-17 01:02:03.456": the date and minute of the first one.
    val since = input.log.firstOrNull()?.take(16) ?: "–"
    val crash = context.getString(if (input.crash != null) R.string.developer_report_with_crash else R.string.developer_report_no_crash)
    return context.getString(R.string.developer_report_summary, input.log.size, since, crash)
}
