package io.github.jnbp.nextstop.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerLayoutType
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.ui.theme.AppColors

/**
 * A time the user picked for a search instead of "now". With [arrival] the journey
 * must be there by that time; otherwise it leaves at that time.
 */
data class TimeChoice(val time: OffsetDateTime, val arrival: Boolean = false)

/** The day this moment falls on in Berlin, where all times of the app are shown. */
fun OffsetDateTime.berlinDate(): LocalDate = atZoneSameInstant(BERLIN).toLocalDate()

/** Short weekday, day and month in the order and spelling of the device language. */
fun LocalDate.dayLabel(): String {
    val locale = Locale.getDefault()
    return try {
        format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale))
    } catch (e: IllegalArgumentException) {
        // A pattern of the system that java.time does not know: fall back to a plain one.
        format(DateTimeFormatter.ofPattern("EEE d MMM", locale))
    }
}

/** Day and clock time of a moment, such as "Thu 12 Nov, 08:30". */
@Composable
fun dateTimeLabel(time: OffsetDateTime): String =
    stringResource(R.string.date_and_clock, time.berlinDate().dayLabel(), time.clock())

/** The chip that shows the time a search runs for and opens the sheet to change it. */
@Composable
fun TimeChip(choice: TimeChoice?, onClick: () -> Unit, modifier: Modifier = Modifier, minHeight: Dp = 48.dp) {
    val label = when {
        choice == null -> stringResource(R.string.time_now)
        choice.arrival -> stringResource(R.string.time_arrive_by, dateTimeLabel(choice.time))
        else -> dateTimeLabel(choice.time)
    }
    ChoiceChip(
        text = label,
        active = choice != null,
        onClick = onClick,
        modifier = modifier,
        description = stringResource(R.string.time_chip, label),
        minHeight = minHeight,
    )
}

/**
 * Asks for another time and day: quick choices that apply at once, or a date and a
 * clock time with Apply. [onApply] gets null for "now". Times are Berlin times.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeSheet(
    current: TimeChoice?,
    allowArrival: Boolean,
    onApply: (TimeChoice?) -> Unit,
    onDismiss: () -> Unit,
) {
    val opened = remember { OffsetDateTime.now().atZoneSameInstant(BERLIN) }
    val start = remember { current?.time?.atZoneSameInstant(BERLIN) ?: opened }
    val today = opened.toLocalDate()
    var arrival by remember { mutableStateOf(allowArrival && current?.arrival == true) }
    var date by remember { mutableStateOf(start.toLocalDate()) }
    var pickingDate by remember { mutableStateOf(false) }
    val clockState = rememberTimePickerState(
        initialHour = start.hour,
        initialMinute = start.minute,
        is24Hour = true,
    )

    Dialog(onDismissRequest = onDismiss) {
        // A dialog is a window of its own: the picture of the map does not line up behind it.
        CompositionLocalProvider(LocalBackdrop provides null) {
        GlassSurface(shape = RoundedCornerShape(32.dp)) {
            // The clock face is tall: on a small screen the sheet scrolls.
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (allowArrival) {
                    SegmentedToggle(
                        labels = listOf(
                            stringResource(R.string.time_departure),
                            stringResource(R.string.time_arrival),
                        ),
                        selected = if (arrival) 1 else 0,
                        onSelect = { index -> arrival = index == 1 },
                        minHeight = 40.dp,
                    )
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SheetChip(text = stringResource(R.string.time_now), onClick = { onApply(null) })
                    SheetChip(
                        text = stringResource(R.string.time_plus_quarter),
                        onClick = { onApply(TimeChoice(fullMinute(OffsetDateTime.now().plusMinutes(15)), arrival)) },
                    )
                    SheetChip(
                        text = stringResource(R.string.time_plus_hour),
                        onClick = { onApply(TimeChoice(fullMinute(OffsetDateTime.now().plusHours(1)), arrival)) },
                    )
                    SheetChip(
                        text = stringResource(R.string.time_tomorrow_morning),
                        onClick = {
                            val tomorrow = OffsetDateTime.now().berlinDate().plusDays(1)
                            onApply(TimeChoice(berlinTime(tomorrow, 8, 0), arrival))
                        },
                    )
                }
                SheetChip(
                    text = stringResource(R.string.time_date, date.dayLabel()),
                    onClick = { pickingDate = true },
                )
                // Upright also on a screen held sideways, where the wide form would not fit the sheet.
                TimePicker(state = clockState, layoutType = TimePickerLayoutType.Vertical)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(AppColors.Blue)
                        .clickable { onApply(TimeChoice(berlinTime(date, clockState.hour, clockState.minute), arrival)) }
                        .defaultMinSize(minHeight = 56.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.time_apply),
                        color = AppColors.OnBlue,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                    )
                }
            }
        }
        }
    }

    if (pickingDate) {
        // The calendar counts days in UTC. Yesterday is the first day on offer:
        // shortly after midnight the last rides of the evening before still matter.
        val firstDay = today.minusDays(1)
        val firstMillis = firstDay.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            // The year of the shown day is always inside, also when it was picked long ago.
            yearRange = minOf(date.year, firstDay.year)..maxOf(date.year, today.year + 1),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= firstMillis

                override fun isSelectableYear(year: Int): Boolean = year >= firstDay.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = dateState.selectedDateMillis
                        if (millis != null) date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        pickingDate = false
                    },
                ) {
                    Text(text = stringResource(R.string.time_apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { pickingDate = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
        ) {
            DatePicker(state = dateState, title = null, headline = null, showModeToggle = false)
        }
    }
}

/** A chip inside the sheet; grey, because glass does not show on the light sheet. */
@Composable
private fun SheetChip(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.Chip)
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 40.dp)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = AppColors.Ink,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            maxLines = 1,
        )
    }
}

/** A day and a clock time in Berlin as a moment. */
private fun berlinTime(date: LocalDate, hour: Int, minute: Int): OffsetDateTime =
    ZonedDateTime.of(date, LocalTime.of(hour, minute), BERLIN).toOffsetDateTime()

private fun fullMinute(time: OffsetDateTime): OffsetDateTime = time.withSecond(0).withNano(0)
