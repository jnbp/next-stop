package io.github.jnbp.nextstop.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context

/**
 * Sets an alarm that wakes the device at [atMillis] for [operation]: exact where the app may set
 * exact alarms (the permission SCHEDULE_EXACT_ALARM, which the user grants in the system
 * settings from Android 14 on), else as close as Android allows without it (it may come a few
 * minutes late while the device sleeps deeply). An exact alarm also lets its receiver start the
 * trip service from the background.
 */
internal fun setWakeUpAlarm(context: Context, atMillis: Long, operation: PendingIntent) {
    val manager = context.getSystemService(AlarmManager::class.java) ?: return
    try {
        if (manager.canScheduleExactAlarms()) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
        }
    } catch (e: SecurityException) {
        // The exact permission was taken away in between: the inexact alarm still comes.
        try {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
        } catch (e: SecurityException) {
            // Nothing more to do: the service looks every few seconds while it runs.
        }
    }
}

/** Takes back the alarm of [operation]. */
internal fun cancelWakeUpAlarm(context: Context, operation: PendingIntent) {
    context.getSystemService(AlarmManager::class.java)?.cancel(operation)
}

/**
 * An alarm clock at [atMillis] for [operation]: where exact alarms are allowed, as an alarm
 * clock of the system ([AlarmManager.setAlarmClock]), which Doze lets ring on time and which
 * shows [show] for the alarm icon of the status bar; else as [setWakeUpAlarm].
 */
internal fun setAlarmClockOrWakeUp(context: Context, atMillis: Long, operation: PendingIntent, show: PendingIntent) {
    val manager = context.getSystemService(AlarmManager::class.java) ?: return
    if (manager.canScheduleExactAlarms()) {
        try {
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, show), operation)
            return
        } catch (e: SecurityException) {
            // Taken back in between: as close as Android allows.
        }
    }
    setWakeUpAlarm(context, atMillis, operation)
}
