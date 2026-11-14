# Prepared answers for the Play Console forms

Answers for *App content* and the declaration forms of Next Stop (`io.github.jnbp.nextstop`, phone
and Wear OS). They follow the code as of branch `play` (main `0fc4051`); re-check them when a data flow
or permission changes. Sources: Play Console help
([Data safety 10787469](https://support.google.com/googleplay/android-developer/answer/10787469),
[foreground services 13392821](https://support.google.com/googleplay/android-developer/answer/13392821),
[IARC 9898843](https://support.google.com/googleplay/android-developer/answer/9898843)),
read on 2026-11-14. Where the help did not settle a question the answer is marked *judgement*.

## 1. What the app sends off the device

Facts from the code that the answers rest on:

| Data | What happens | Destination |
|---|---|---|
| Position (fine or coarse, **foreground only**) | Used for nearby stops, the map, "from here" and walk routes. Coordinates go into the request. | Timetable APIs: VBB HAFAS, BVG REST, Transitous (and public-transport-enabler providers); walking and bike routes: routing.openstreetmap.de (OSRM); place search bias: Photon (rounded to two decimals, about 1 km); map tiles: OpenFreeMap (the visible area, not the exact position) |
| Widget refresh | Home screen widgets refresh departures with the **last position recorded while the app was in use** (kept on the device, no location request in the background) and send it to the timetable APIs | Timetable APIs |
| Typed destination or place text | Search query | Photon, timetable APIs |
| Contact address | Only when the user opens "contacts" in the search panel and taps one: the postal address text becomes a place search query (Photon). The contact list is read on demand, held only while the panel is open, never stored or logged. Contacts with an address may also be copied to the **paired watch** over the Wearable Data Layer (device to device, no third party) | Photon (query only) |
| Calendar | Next appointments with a location (7 days, max. 4) are read on demand and never stored; the location text of the appointment the user picks becomes a destination query. "Add to calendar" only hands an event to the calendar app | Photon / timetable APIs (query only) |
| Map link opened in the app | Only when the user opens a short link of Google Maps (maps.app.goo.gl/…, goo.gl/maps/…) with Next Stop ("navigate here", "Open with"): that link is sent once to its own host to learn the full map link it redirects to; the redirect is read and not followed, so the Google Maps page is not loaded. Other map links (geo:, google.navigation:, full Google Maps links) are read on the device | maps.app.goo.gl / goo.gl (the short link only) |
| Trip state | Stays on the device (and goes to the paired watch). A **live link** puts the trip in the URL fragment of a page the user shares; fragments are never sent to a server by browsers | The viewer's own browser asks the public APIs |
| Backup | Settings, favourites, saved places and recent destinations go into Android's own backup (encrypted on the device where a screen lock is set); keys, caches and widget state are excluded | Google backup, controlled by the user's system settings |
| Everything else | No account, no analytics, no crash reporting, no advertising ID, no own server | – |

All endpoints use `https://` (no cleartext URL in the code).

## 2. Data safety form

### Definitions used (Google's, from the help page above)

- **Collected** = transmitted off the device (also by libraries).
- **Shared** = collected data transferred to a third party, i.e. anyone other than you or your *service providers*
  (processors acting only on your behalf).
- Not required to be disclosed: data processed only on the device; **ephemeral** processing (in memory, discarded once
  the request is answered, never used for profiles/ads); transfers that result from a **specific user action** where
  the sharing is reasonably expected; end-to-end encrypted data.

### Decision

The timetable, routing, search and tile services are independent third parties with their own terms: Next Stop has
no processor agreement with them, so they are **not service providers**, and we cannot vouch for how long they keep
requests, so the **ephemeral** exemption is not claimed either (it concerns the developer's own handling). Some
requests are clearly user-initiated (tapping "search connections"), but others are not a specific action (nearby
stops on opening the app, the map, background refresh of a followed trip). Mixed flows cannot be split in the form.
*Judgement:* the safe reading is to **declare location as collected and shared**. Under-declaring risks a policy
violation; over-declaring only costs a slightly more cautious store label.

### Form answers

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes** (HTTPS/TLS everywhere) |
| Do you provide a way for users to request that their data is deleted? | **Not applicable / no account.** Pick the answer for "no data stored by the developer" if offered; otherwise **Yes**, justified by: the developer holds no user data, nothing is stored on a server of ours, and the privacy policy says how to clear the data stored on the device (clear storage or uninstall) |
| Account creation (data deletion section) | The app does **not** allow users to create an account |

### Data types

| Category / type | Collected | Shared | Optional | Purpose | Why |
|---|---|---|---|---|---|
| **Location > Approximate location** | Yes | Yes (timetable, routing, search and tile services) | Yes: the app works without; it falls back to Alexanderplatz | App functionality | Coarse position used for nearby stops, the map area, search bias (rounded to about 1 km) and tiles; coarse permission only is also supported |
| **Location > Precise location** | Yes | Yes (timetable and routing services) | Yes | App functionality | Fine position is sent for nearby departures, "from here" journeys and walk routes. Foreground use only |
| Personal info > Name, e-mail, address, phone, race, politics, … | No | – | – | – | Nothing is asked for; contact and calendar entries are not collected (below) |
| **Contacts** | **No** | – | – | – | Contact data stays on the device (read on demand, not stored, not sent). The one thing that can leave the device is the postal address of a contact the user taps, which is a place-search query. *Judgement:* a user-initiated search query, not a transfer of contact data. If the reviewer disagrees, add **Personal info > Address**, shared, optional, App functionality |
| **Calendar** | **No** | – | – | – | Appointments with a location are read on device; only the location text of an appointment the user chooses is searched. Same reasoning and fallback as above |
| App activity > In-app search history, other actions | No | – | – | – | Recent destinations are stored on the device only; queries are answered in real time |
| Web browsing, messages, photos, audio, files, health, fitness, financial info | No | – | – | – | – |
| App info and performance (crash logs, diagnostics) | No | – | – | – | No crash reporting, no analytics SDK |
| Device or other IDs | No | – | – | – | No advertising ID, no tracking SDK |

IP addresses reach the services as part of every request. Google counts IP-derived data only under the category it
matches (location), which is already declared.

**Wear OS:** the watch app is part of the same listing and the same form. It sends the same kinds of requests with
its own network and location (standalone), so the answers above cover it.

### Consistency checklist

- The privacy policy must name the third-party services (Transitous, VBB, BVG, OSRM, Photon, OpenFreeMap, accessibility.cloud and DB FaSta for lift status, and Google's short-link hosts for short map links the user opens) and what each receives,
  say that there is no account and no server of ours, describe the optional contacts and calendar access,
  the Android backup, the live link (trip in the URL fragment), who to contact, and which law applies.
- The data safety label and `README.md` ("Privacy") must say the same.

## 3. Permissions and declarations

### Foreground service: `FOREGROUND_SERVICE_SPECIAL_USE` (phone and watch)

Play Console: *Policy and programs > App content > Foreground service permissions* (declare per type; it asks for
the functionality, the user impact if the system defers or stops it, a video link and a use case, reviewed by Google).

- **Type:** Special use (neither location nor mediaPlayback fits; the manifest comment says so).
  Manifest subtype property, phone: *Live trip companion: refreshes the public transport journey the user
  started and shows its next step in an ongoing notification until the trip ends.* Watch: *Follows a public transport trip the user started and counts down to the next step.*
- **Use case (free text):**
  *The user starts "follow trip" for a public transport journey. A foreground service keeps the trip current
  while the screen is off: it refreshes live departure times, checks for delays and a faster connection, and shows
  the next step, countdown and get-off reminder in one ongoing notification. It starts only from a user action while
  the app is visible, uses no location, microphone or camera, and ends itself five minutes after arrival or when
  the user ends the trip.*
- **User impact if the system defers or stops it:** *The notification and the get-off reminder would freeze or
  vanish mid-journey, so the user could miss a change or a stop. An ordinary background job cannot refresh every
  15 seconds, and WorkManager is too coarse for a countdown.*
- **Alternatives considered:** WorkManager (minimum 15 minutes), `dataSync` (a time-limited data upload type, not
  a live trip), `location` (the service does not use location).
- **Video** (YouTube unlisted link or Drive MP4, 30 to 60 seconds, phone screen recording; make a second short one on the watch if Console asks):
  1. Open the app, search Hauptbahnhof to Alexanderplatz, open a connection.
  2. Tap the button that starts the trip companion (state the notification permission prompt if it appears and allow it).
  3. Lock the screen; show the ongoing live notification with next step and countdown, updating.
  4. Show the get-off reminder (or the "faster connection" notification) arriving.
  5. End the trip from the notification or the app and show that the notification is removed.
  Use the emulator with a mocked position or sample data; show no personal data.

### Location

- Permissions: `ACCESS_COARSE_LOCATION` and `ACCESS_FINE_LOCATION` (the user may grant approximate only).
- **No background location.** The app requests location only while it is in use (position updates are registered
  by the screen and stopped with it; the foreground service uses none). The *Permissions declaration form for
  background location is therefore not needed*. If a Console warning claims otherwise, check the merged manifest.
- Prominent in-app disclosure: the setup wizard explains the permission before the system dialog (strings
  `setup_permission_location*`); keep that text accurate: location is used for nearby stops and the trip companion and is sent to the timetable services.

### READ_CONTACTS

- Not one of the restricted groups with a special Console form (*verify* in *App content*; if Console asks, use this text).
- **Rationale:** *Optional. Lets the user pick a contact's postal address as a destination ("take me to Anna's"). Contacts
  are read only when the user opens the contacts list in the search panel (and, if allowed, to offer the same
  addresses on the paired watch), only postal addresses are queried, nothing is stored, logged or transmitted
  except the address of a contact the user chooses, which is searched like any typed address.*
- The app works completely without the permission; it is asked in the setup wizard as a separate, skippable row and again
  at the moment the contacts list is opened.

### READ_CALENDAR

- **Rationale:** *Optional. Shows the next appointments that have a location (7 days, not all-day) so the user can
  get a connection to them and see when to leave. Read on demand, never stored or transmitted except the location
  text of an appointment the user chooses, which is searched as a destination.* Adding a journey to the calendar uses
  the calendar app's own insert screen and needs no permission.

### Other permissions (no Console form)

`INTERNET` (timetables, tiles, search), `POST_NOTIFICATIONS` (trip and get-off reminders, shortcut alert),
`FOREGROUND_SERVICE` (the trip companion), `POST_PROMOTED_NOTIFICATIONS` (shows the trip notification as a Live
Update on Android 16 and later), watch `VIBRATE` (get-off haptics). The app does not use `SCHEDULE_EXACT_ALARM`, `USE_FULL_SCREEN_INTENT`,
`QUERY_ALL_PACKAGES`, accessibility or any other restricted permission.

## 4. App content answers

| Form | Answer |
|---|---|
| Privacy policy | `https://nextstop.bapo.me/datenschutz/` (German; English at `/en/privacy/`), source `web/datenschutz/` and `web/en/privacy/`. It must be public, not a PDF, and not editable |
| App access | **All functionality is available without login or special access.** Reviewer note: *No account. Allow location (approximate is enough) for nearby stops; without it the app shows Alexanderplatz. Contacts and calendar are optional. Search for "Hauptbahnhof" to "Alexanderplatz" to see connections and start the trip companion.* |
| Ads | **No**, the app contains no ads |
| Content rating (IARC) | Category **Utility, productivity, communication or other**. Violence, blood, sexuality, nudity, language, controlled substances, gambling, fear, discrimination: **No** to all. Does the app let users communicate or exchange content: **No**. Does it share the user's physical location with other users: **Yes**, optionally, through the live link the user sends (this may add a "shares location" interactive element). Digital purchases: **No**. Unrestricted web access: **No** (opens links in the browser). Expected result: PEGI 3, USK 0, ESRB Everyone, IARC 3+ (the location element may be listed as a note) |
| Target audience | **18 and over** (not designed for children, does not appeal to them, no Families programme). *Judgement:* a wider adult range such as 16 and over is also fine; picking children's age groups would bring Families policy requirements |
| News app | **No** |
| Government app | **No** (the app is independent of BVG, VBB, Deutsche Bahn and any authority) |
| Financial features | **No** (no payments, tickets, loans, wallets) |
| Health | **No** (no health or medical features; the app does not use Health Connect) |
| COVID-19 / contact tracing | **No** |
| Advertising ID | **No** (no `AD_ID`; check the merged manifest) |
| Data safety | Section 2 |
| Foreground service permissions | Section 3 |
| Trader status | See the owner's decision in `play-release.md` step 1 (non-trader is plausible for a free, non-commercial project) |
| Category and tags | Maps & Navigation (alternative: Travel & Local); no tags needed |
| Contact details (public) | Developer e-mail from the account; website `https://github.com/jnbp/next-stop` or the project site; no phone number needed for a non-trader |
| Store listing languages | de-DE (default), en-US |

## 5. Open points for the owner

1. **Privacy policy page written (web/datenschutz, web/en/privacy; not yet online)** (the single blocker for the Data safety form and the first release).
2. Trader or non-trader (EU DSA) and what contact details the account shows publicly.
3. Final data safety call on contacts and calendar (this file says "not collected", with a fallback).
4. Built-in API keys in release builds (see `play-release.md` step 0).
5. Who the 12 closed testers are (friends, party colleagues) and how they will be kept opted in for 14 days.
6. The record of the foreground service video (a phone recording; emulator is fine).
