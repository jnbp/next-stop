# Next Stop for Wear OS (module `wear`)

A first version of the watch app. It shares no code with `app`: a small BVG client of its
own (`wear/.../data/BvgClient.kt`, same API as the phone, `v6.bvg.transport.rest`) and the
Wearable Data Layer for what only the phone knows.

## What it does

- **Start page**: the next step of the followed trip (from the phone, or followed on the
  watch), then "Abfahrten in der Nähe", "Route" and the favourites and saved places of the phone.
  A favourite stop opens its departures; another place (home, address) searches a route to it.
- **Departures** of the nearest stop (watch location, else the last position of the phone),
  line badges in BVG colours, minutes, delays in amber; other stops nearby below. Fresh every 30 s.
- **Trip companion**: next step, minutes, line and direction, platform, boarding hint (from
  the phone), stops left, progress, arrival. One stop before getting off the watch vibrates
  (three long pulses) and shows a notification "Nächste Station aussteigen!" – once per ride.
- **Route search**: destination by voice (speech recognition of the watch, then a pick among
  up to four matches) or from favourites / saved places / recent destinations of the phone →
  search from the current position → up to three compact cards →
  "Auf dem Handy starten" or "Hier verfolgen".
- **Tile** "Nächste Abfahrt" (Tiles 1.6 + ProtoLayout Material 3): the trip step, else the
  next three departures of the first favourite stop. Tap opens the app.
- **Complication** (SHORT_TEXT): minutes + line of the trip step or of the next departure at
  the first favourite stop.

## Data Layer protocol

Phone (`app/.../wear/PhoneBridge.kt`) and watch (`wear/.../data/Protocol.kt`) use the same paths:

| Path | Kind | Direction | Content |
|---|---|---|---|
| `/nextstop/places` | DataItem | phone → watch | favourites, saved places, 8 recent destinations, last phone position |
| `/nextstop/trip` | DataItem | phone → watch | next step of the followed trip, or `active=false` |
| `/nextstop/sync` | Message | watch → phone | phone writes both items again |
| `/nextstop/start-trip` | Message | watch → phone | `{"token": refreshToken}`; phone loads and follows the journey |

The phone writes the trip while `TripService` runs (one hook: `PhoneBridge.mirrorTrip`),
only when the step or the progress (in 5 % steps) changed, at most every 15 s.

## Feasibility and limits

- **Data Layer** needs the same `applicationId` (`io.github.jnbp.nextstop`, set in both modules) and
  the same signing key. Debug builds from the same machine share the debug key; release
  builds must be signed with the same key.
- **BVG only on the watch**: journeys and departures only in the VBB area. Places of the phone
  from Transitous (`tr:` ids) are searched by their position. Transitous on the watch is a later step.
- **"Hier verfolgen"** runs inside the app process (`LocalFollow`), no foreground service yet:
  Wear OS may stop it after a while in the background; the tile then shows the last state.
  Next step: an Ongoing Activity / foreground service on the watch.
- **"Auf dem Handy starten"**: the phone sets the trip and tries to start its trip service;
  Android usually refuses that from the background, so a notification "Fahrt auf der Uhr
  gestartet" opens the trip in the app with one tap (the service then starts as usual).
- **Phone trip without `TripService`** (trip notifications off) is not mirrored to the watch.
- **Voice input** needs a speech recogniser on the watch (Google on real watches; the emulator
  often has none – then nothing is found).
- **Location**: the watch asks for location and notifications on first start. Without a fix
  it uses the last position the phone sent ("Standort des Handys").
- **Tile** content is refreshed every minute while shown and whenever the trip changes; the
  complication every 5 minutes and on trip changes.
- `tile_preview` is a stand-in (app icon); replace it with a 400×400 screenshot of the tile.

## Versions (Nov 2026)

Wear Compose (material3, foundation, navigation) 1.7.1 · Tiles 1.6.2 · ProtoLayout (+ material3,
expression) 1.4.2 · watchface-complications-data-source-ktx 1.3.0 · play-services-wearable
20.0.1 · kotlinx-coroutines-play-services 1.10.2 (same as coroutines). minSdk 30 (Wear OS 3),
targetSdk 36, compileSdk 37.

## Try it

1. Android Studio: Gradle sync; the run configuration `wear` appears.
2. Device Manager → create a "Wear OS Large Round" (API 34 or 35/36) emulator.
3. Pair it with a phone emulator *with Google Play* (or the real phone over ADB): Device Manager →
   ⋮ → "Pair Wearable", then the Wear OS app on the phone completes the pairing.
4. Run `app` on the phone and `wear` on the watch (both debug).
5. On the phone: add a favourite stop; on the watch open the app → favourites appear.
6. Follow a trip on the phone → the watch start page and the tile show the next step.
7. Watch: Route → favourite → card → "Hier verfolgen" or "Auf dem Handy starten".
8. Tile: long-press the watch face / swipe to tiles → add "Nächste Abfahrt". Complication:
   edit a watch face → complication slot → Next Stop.
