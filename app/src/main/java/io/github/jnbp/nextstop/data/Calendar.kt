package io.github.jnbp.nextstop.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One upcoming appointment of the device calendar with a place to go to. Read on demand, never stored. */
data class Appointment(val eventId: Long, val title: String, val begin: OffsetDateTime, val location: String) {
    /** Tells the occurrences of a repeating event apart. */
    val key: String get() = "$eventId@${begin.toInstant().toEpochMilli()}"
}

fun hasCalendarPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

/**
 * The next appointments that start within [days] days from now, have a location and are
 * not all-day, in the order they start. Empty without the permission to read the calendar.
 */
suspend fun upcomingAppointments(context: Context, days: Int = 7, limit: Int = 4): List<Appointment> =
    withContext(Dispatchers.IO) {
        if (!hasCalendarPermission(context)) return@withContext emptyList()
        val found = mutableListOf<Appointment>()
        val start = System.currentTimeMillis()
        val end = start + days * 24L * 60 * 60 * 1000
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also { builder ->
            ContentUris.appendId(builder, start)
            ContentUris.appendId(builder, end)
        }.build()
        val selection = "${CalendarContract.Instances.ALL_DAY} = 0 AND " +
            "${CalendarContract.Instances.EVENT_LOCATION} IS NOT NULL AND " +
            "${CalendarContract.Instances.EVENT_LOCATION} != '' AND " +
            "${CalendarContract.Instances.BEGIN} >= ?"
        try {
            context.contentResolver.query(
                uri,
                arrayOf(
                    CalendarContract.Instances.EVENT_ID,
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.EVENT_LOCATION,
                    CalendarContract.Instances.STATUS,
                ),
                selection,
                arrayOf(start.toString()),
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { cursor ->
                val zone = ZoneId.systemDefault()
                while (cursor.moveToNext() && found.size < limit) {
                    // A cancelled occurrence is no reason to leave.
                    if (!cursor.isNull(4) && cursor.getInt(4) == CalendarContract.Events.STATUS_CANCELED) continue
                    val location: String = cursor.getString(3)?.replace(Regex("\\s*\\n\\s*"), ", ")?.trim() ?: continue
                    if (location.isEmpty()) continue
                    val begin = OffsetDateTime.ofInstant(Instant.ofEpochMilli(cursor.getLong(2)), zone)
                    found.add(
                        Appointment(
                            eventId = cursor.getLong(0),
                            title = cursor.getString(1)?.trim().orEmpty(),
                            begin = begin,
                            location = location,
                        ),
                    )
                }
            }
        } catch (e: SecurityException) {
            // The permission was taken back: nothing to list.
        } catch (e: IllegalArgumentException) {
            // A calendar provider that does not know a column: nothing to list.
        }
        found
    }
