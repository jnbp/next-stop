# Play Console for 0.2: new declarations and Data safety

What has to change in the Play Console for version 0.2.0 (`versionCode 4`, watch `1_000_004`) of
`io.github.jnbp.nextstop`. This file **adds to** [play-console-answers.md](play-console-answers.md); where the two
disagree, this file is the newer one (that file still says the companion uses no location and that the app
does not use `USE_FULL_SCREEN_INTENT`, which was true up to 0.1.2).

Checked against the code of branch `rel020` (`fe75807`); every claim names where it comes from. Console
field names follow *Policy and programs > App content*. The declarations are reviewed by Google in
English, so they are given in English; German is given where users see the text (in-app disclosure).
Claims that could not be verified in the code are listed at the end.

## 0. What is new in 0.2 (from the code)

| Permission / feature | Where | Console form? |
|---|---|---|
| `FOREGROUND_SERVICE_LOCATION`, service type `specialUse\|location` | `app/src/main/AndroidManifest.xml` (`.ui.TripService`) | **Yes**: foreground service permissions, type *Location* (with video) |
| `USE_FULL_SCREEN_INTENT` | phone and watch manifests; `ui/WakeAlarm.kt`, `wear/.../WakeRing.kt`, `wear/.../CompanionAutoOpen.kt` | **Yes**: *Full-screen intent* declaration |
| `SCHEDULE_EXACT_ALARM` | phone manifest; `ui/ExactAlarms.kt` | No form (the user grants it in the system; `USE_EXACT_ALARM`, which would need a form, is not used) |
| `RECEIVE_BOOT_COMPLETED` | phone manifest; `.ui.PlannedTripReceiver` | No form |
| No `ACCESS_BACKGROUND_LOCATION` | both manifests (grep finds none) | No background location form |

## 1. Foreground service type *location* (phone)

### Facts from the code

- `TripService` asks for the type location **only** when the setting *Standort in der Live-Fahrt nutzen* is on
  **and** a location permission is granted (`CompanionLocation.wanted`, `TripService.enterForeground`). Otherwise it
  runs as before with `specialUse` alone. If Android refuses the type (a start from the background, e.g. a
  planned trip), it falls back to `specialUse` without position.
- Positions are requested only while the service runs and stopped with it (`CompanionLocation.start/stop`,
  called from `onStartCommand`/`onDestroy`): balanced accuracy every 15 s, precise only within 2.5 km of where
  the user gets off (`INTERVAL_MILLIS`, `PRECISE_METERS`).
- Each position is matched to the followed journey (`data/CompanionPosition.kt`, `matchPosition`) and used for the
  next stop, the get-off reminder, the wake-up alarm time, the remaining time and the step shown
  (`ui/TripSteps.kt`, `ui/AlightReminders.kt`, `data/RideState.kt`). The watch gets only the derived step and the
  number of stops left, not coordinates (`wear/PhoneBridge.kt` `tripJson`).
- The setting is **on by default** (`AppPrefs.companionLocation`), can be switched off in the setup wizard
  (`SetupWizard.kt`) and in the settings (`CompanionLocationRow`), and switching it off stops the position at once.
- The service starts only from a user action (start the live trip) or a planned trip the user set up, and ends
  with the trip.

### Console answers (English)

**Which foreground service type:** Location.

**Describe the feature that uses this foreground service type:**

> Next Stop is a public transport app. When the user starts the live trip for a journey, a foreground
> service shows the next step of the trip in an ongoing notification (and on a paired watch) until the trip ends.
> With the setting "Use location in the live trip" (on by default, can be switched off), the service also
> receives the device position while it runs, and only then. The position is matched to the route of the
> current vehicle to tell which stop comes next, when to get off and whether the user is already on board or at
> the next stop, also when the live data of the vehicle are late or missing. Positions are processed on the device
> only and are not sent to any server. The position stops when the trip ends or the user ends the live trip.

**User impact if the system defers or stops the service:**

> The get-off reminder and the wake-up alarm on long rides would be based on timetable times only. When a bus or
> train is late, the user would be told to get off too early or too late and could miss the stop. The next stop
> shown on the lock screen and the watch would be wrong.

**Why not an alternative (if asked):**

> The position is needed continuously during the ride while the screen is off; WorkManager or a one-time location
> request cannot follow a moving vehicle. The service already runs for the trip (special use); it uses location
> only for the duration of that user-started trip. No background location permission is requested.

**Update of the existing *special use* declaration** (its use case text says "uses no location"). Replace the
sentence "It starts only from a user action while the app is visible, uses no location, microphone or camera, and
ends itself five minutes after arrival or when the user ends the trip." with:

> It starts from a user action (or a trip the user planned), uses no microphone or camera, and ends itself five
> minutes after arrival or when the user ends the trip. Where the user allows it, the same service is also of
> type location to follow the ride by position (declared separately).

### Video (at most 30 s, screen recording of the phone, no personal data)

Upload as an unlisted YouTube link or as the file Console accepts. Use a demo journey (emulator with a mocked
route or a real ride on a line far from home); keep the status bar clock visible.

| Time | Shot |
|---|---|
| 0–4 s | Settings › *Unterwegs & Benachrichtigungen*: show the switch **Standort in der Live-Fahrt nutzen** with its text, switch it on (system location dialog: "While using the app"). |
| 4–9 s | Search a connection (e.g. Hauptbahnhof → Alexanderplatz), open it, tap the button that starts the live trip. |
| 9–14 s | Live trip screen: the blue dot moves on the ride; the hint "Nächster Halt … nach deinem Standort". |
| 14–20 s | Lock the screen: the ongoing live trip notification with next stop and countdown; the location indicator in the status bar. |
| 20–26 s | The get-off reminder arrives (or the step jumps to the next stop as the position passes it). |
| 26–30 s | End the live trip from the notification: notification and location indicator disappear. |

### In-app disclosure (recommended)

The setting text already says when the position is used (`companion_location_text`). The general location text
of the setup wizard, however, says **"It stays on the device" / "Er bleibt auf dem Gerät"**
(`setup_permission_location_text`), which is not true for searches (coordinates go to the timetable services, see
section 3). Replaced in 0.2 with:

- DE: *Für Abfahrten in deiner Nähe, Verbindungen ab deinem Standort und – während einer Live-Fahrt – den
  nächsten Halt. Für Suchen geht er an die Fahrplandienste; in der Live-Fahrt bleibt er auf dem Gerät.*
- EN: *For departures near you, connections from where you are and – during a live trip – the next stop.
  Searches send it to the timetable services; in the live trip it stays on the device.*

## 2. `USE_FULL_SCREEN_INTENT`: wake-up alarm

### Facts from the code

- Phone: `ui/WakeAlarm.kt` posts the wake-up alarm with `CATEGORY_ALARM` and `setFullScreenIntent` to
  `WakeAlarmActivity` (`showWhenLocked`, `turnScreenOn`). The time is set with `AlarmManager.setAlarmClock`
  (`ui/ExactAlarms.kt`). It rings every minute until the user taps *Ich bin wach* / *I'm awake*.
- It is armed automatically for rides of 60 min or more (`WakeAlarmMode.Auto` is the default), 10 min before
  getting off (`wakeAlarmMinutes`), switchable per trip in the live trip and in the settings (*Alarme unterwegs*).
- Without the grant (`canUseFullScreenIntent()` false, Android 14+) the alarm falls back to a heads-up
  notification and the settings show how to allow it (`fullScreenAllowed`, `TripAlarmsGroup`).
- Watch: `wear/.../WakeRing.kt` rings the same alarm on the watch (`CATEGORY_ALARM`, full screen).

### Console answers (English)

**Does your app use the full-screen intent permission?** Yes.
**Core functionality:** *Alarm clock* (alarms set by the user or the app for a time the user chose).

> The wake-up alarm wakes the user before they have to get off a long bus or train ride (for example a night
> bus). The user switches it on per trip or lets the app arm it for rides of 60 minutes or more; it rings a
> set number of minutes (default 10) before the stop over the lock screen, like an alarm clock, and repeats every
> minute until the user confirms "I'm awake". A heads-up notification could be missed by a sleeping passenger,
> who would then miss their stop. The full-screen intent is used only for this alarm.

**Watch:** the watch app also uses the full-screen intent for the companion auto-open (see *Wear OS* below), so
the sentence "used only for this alarm" applies to the phone app only. Declare the watch use as described there.

### Wear OS: companion auto-open (decided by Joel on 18.11.: yes, like Google Maps)

- **What:** setting *Live-Fahrt auf der Uhr öffnen* / *Open live trip when the phone starts a trip*
  (`WatchSettingKeys.OPEN_COMPANION`), **off by default**, switched on by the user on the watch or the phone.
  When the user starts a trip on the phone, the watch shows the live trip, so it is on screen when the user
  raises the wrist, the way Google Maps opens navigation on the watch.
- **Permission:** `android.permission.USE_FULL_SCREEN_INTENT` in `wear/src/main/AndroidManifest.xml` (declared once,
  also for the wake-up alarm, `WakeRing.kt`). No `SYSTEM_ALERT_WINDOW`, no foreground service started from the
  background.
- **How (`wear/.../CompanionAutoOpen.kt`):** the trip itself is an Ongoing Activity (`TripOngoing.kt`, on the watch
  face and in the recents). At the start of a trip one notification of the category *navigation* with a
  full-screen intent opens the live trip; with the screen in use it is a heads-up that opens it with one tap. It is
  cancelled when the app comes to the front and times out after 2 minutes. Only for a trip that started on the
  phone within the last 5 minutes (`CompanionStart.kt`), never for a sync or reconnect, never with the setting off.
- **Without the grant** (`canUseFullScreenIntent()` false on Wear OS 5 / Android 14+): heads-up only; the watch
  settings say so under the switch.
- **Console:** the full-screen intent declaration of the watch is the same app listing. Suggested text (English):

> On Wear OS the full-screen intent is also used, only when the user switched on "Open live trip when the phone
> starts a trip" (off by default): when the user starts a public transport trip on the phone, the watch opens the
> live trip once, like turn-by-turn navigation, so the next step is visible when the user raises the wrist.
> It is never used for advertising or for anything else.

- **Risk:** Google Play grants the permission by default only to alarm and calling apps. The phone's use (wake-up
  alarm) qualifies; the watch's navigation use may be judged outside the categories. If the review rejects it,
  the fallback is already built: without the grant the heads-up remains (nothing else changes in the code).

## 3. Data safety: what changes in 0.2

**Short answer: no new data type, no change of the answers in `play-console-answers.md` section 2**, provided the
decisions below go as recommended. **One new network host:** Open-Meteo (`api.open-meteo.com`) for the sun-side
hint; it receives an approximate location, which is already declared as shared (see the table). Details:

| Topic | Finding in the code | Form |
|---|---|---|
| Companion position | Processed on the device only: read by `TripSteps`, `RideState`, `AlightReminders`, `OwnPosition`, companion views and `PhoneBridge` (derived step only). No network client (`data/*` timetable, routing, search, tile code) reads `CompanionPosition`; the live link (`data/SharedLive.kt`) does not include it. Not in the backup (held in memory). | Not collected, not shared. **Location stays declared** (approximate and precise, collected and shared, optional, app functionality) because of the searches, as before. |
| Network requests | Hosts in the code: `api.transitous.org`, `v6.bvg.transport.rest` (and VBB HAFAS), `tiles.openfreemap.org`, `photon.komoot.io`, `routing.openstreetmap.de`, `accessibility.cloud`, `apis.deutschebahn.com` (lift status), Google short-link hosts for map links the user opens. Disruptions across Germany use Transitous (same host). All `https://`. | Unchanged |
| Open-Meteo (new in 0.2) | `data/SunWeather.kt` asks `api.open-meteo.com/v1/forecast` for the hourly cloud cover, for the sun-side hint only. Sent: the middle point of the ride **rounded to 0.1° (about 11 km)**, no identifier, no key. Asked only while the hint is on screen (app in the foreground or the open companion), at most once per area in 30 min (cached in memory), not at night, never at battery saver *Extrem* (cache only), not at all when the hint is switched off (*Sonnenseite anzeigen*). Positions are not logged (a failure logs only the exception class). Listed in the privacy policy (DE/EN), the README sources and *Über › Quellen* (CC BY 4.0). | **Approximate location, shared** (already declared: optional, app functionality). Add Open-Meteo to the third-party list if the Console asks for recipients. No new data type. |
| Accounts | None; no login, no own server. | Unchanged ("no account") |
| Crash logs / diagnostics | `ui/DiagCapture.kt` keeps a log ring (cache, `diag/ring.txt`) and the last crash (`files/diag/last-crash.txt`) **on the device**; neither is in the backup (`data_extraction_rules.xml` includes only shared preferences). A report is built only when the user taps it in the developer options, is redacted (`DiagRedaction`: keys, e-mail addresses, exact positions, private terms) and goes **only through the Android share sheet** to an app the user picks. The app itself sends nothing. | *Judgement:* not collected (the app transmits nothing; the user hands a file to another app). If Joel wants to receive reports by e-mail and a reviewer disagrees: *App info and performance > Crash logs* and *Diagnostics*, collected, not shared, optional, purpose App functionality. |
| Wake-up alarm, planned trips, statistics, platform changes | Stored in shared preferences on the device (statistics "only on the device" per the code comments); in the Android backup like other settings. | Unchanged |

**Website text to fix with it:** the FAQ of the site says "keine Absturzberichte" / "no crash reports". Still
true in the sense of automatic reporting; if the developer options with *Fehlerbericht teilen* stay in release
builds, add to the privacy policy one sentence: *Die Entwickleroptionen können einen Fehlerbericht erstellen; er
bleibt auf dem Gerät, bis du ihn selbst teilst.*

## 4. Claims not verified in the code

1. That Android 14+ actually refuses the type location for a planned trip started from the background and the
   fallback runs (code path exists, behaviour only on a device).
2. The location indicator and texts in the video shots (UI only visible in a build).
3. That no library sends anything on its own (e.g. MapLibre telemetry): checked in earlier releases per
   `play-console-answers.md`, not re-checked for 0.2's dependencies.
4. Open-Meteo: that the request really carries only the rounded area (`String.format` with `%.1f` in
   `SunWeather.fetch`, covered by `SunWeatherTest` for the rounding) and that it stops in the background (it runs
   from a `produceState` of the hint; checked by reading, not on a device).
5. Whether developer options (and thus *Fehlerbericht teilen*) are reachable in release builds: they are unlocked
   by a preference set in `DeveloperOptions.kt`; the exact gesture was not traced.
6. Exact Console wording of the full-screen intent categories (from memory of the Play policy; check in Console).

## 5. Decisions for Joel

1. **Watch full-screen intent for the companion auto-open** (`CompanionAutoOpen.kt`): decided on 18.11.: keep it,
   like Google Maps (see section 2, *Wear OS*). If the review rejects it, the heads-up fallback stays.
2. **Default of "Standort in der Live-Fahrt nutzen"** is on: fine for Play (foreground only, user-started), but say so
   in the setup wizard (it is a row there) and fix `setup_permission_location_text` (section 1).
3. **Crash reports by e-mail:** if you want them sent to you, keep the "not collected" judgement but name it in the
   privacy policy, or declare crash logs (section 3).
