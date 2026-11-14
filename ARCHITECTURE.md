# Architecture

How Next Stop is put together, for someone who is about to change it. The code is the authority:
every file, class and constant named here exists, and where a rule is stated, the place that
implements it is named so you can read it. For setup, builds and tests see
[docs/development.md](docs/development.md); for the engineering rules
[docs/conventions.md](docs/conventions.md).

Paths below are relative to the repository root. `P` stands for
`app/src/main/java/io/github/jnbp/nextstop/` (the phone app) and `W` for
`wear/src/main/java/io/github/jnbp/nextstop/wear/` (the watch app).

## Contents

1. [Modules](#1-modules)
2. [Phone app: package map](#2-phone-app-package-map)
3. [Data sources and routing](#3-data-sources-and-routing)
4. [Offline OpenStreetMap data](#4-offline-openstreetmap-data)
5. [Map rendering](#5-map-rendering)
6. [Disruptions](#6-disruptions)
7. [Walks](#7-walks)
8. [Startup choreography](#8-startup-choreography)
9. [Wear OS app](#9-wear-os-app)
10. [Widgets and shortcuts](#10-widgets-and-shortcuts)
11. [Settings, storage and backup](#11-settings-storage-and-backup)
12. [Localisation and stop names](#12-localisation-and-stop-names)

## 1. Modules

| Path | What it is |
|---|---|
| `app/` | The phone app, `io.github.jnbp.nextstop`. Kotlin, Jetpack Compose (Material 3), MapLibre, Glance widgets, WorkManager. min SDK 31, target 37. |
| `wear/` | The Wear OS app, Kotlin package `io.github.jnbp.nextstop.wear` but the **same applicationId** as the phone app (see [section 9](#9-wear-os-app)). Compose for Wear OS, Tiles, a complication, Data Layer link to the phone. min SDK 30, target 36. |
| `web/` | Static pages, no build step: the live page (`web/live/`), the route page (`web/route/`), the project site (`web/index.html`, `web/en/`, `web/site/`). Shared logic in `web/shared/core.js`. Offline tests in `web/test/`. See [web/README.md](web/README.md). |
| `tools/` | Python (standard library plus pyosmium for extracts) that turns OpenStreetMap data into the assets under `app/src/main/assets/`. See [tools/README.md](tools/README.md). |
| `fastlane/metadata/android/` | Store texts (`de-DE`, `en-US`), changelogs, feature graphic. Also read by F-Droid. |
| `docs/` | Design spec, keys, Play release notes; this file and the developer docs. |

The two Android modules do not share code. What both need (line colours, the departures board
rules, the Data Layer protocol) exists twice and the files say "keep both sides alike"
(for example `W data/DepartureBoard.kt`, `W data/LineColors.kt`, `W data/Protocol.kt` against
`P data/DepartureBoard.kt`, `P ui/Components.kt`, `P wear/PhoneBridge.kt`).

Build switch: `nextstop.pte` in `gradle.properties`. With `true` (default) the app compiles
`app/src/pte/java` (the real `PteSource` over public-transport-enabler), with `false`
`app/src/nopte/java` (a stand-in `PteSource` that reports "not set up"). See `app/build.gradle.kts`.

## 2. Phone app: package map

Single activity (`P MainActivity.kt`), Compose throughout. There is no ViewModel layer or DI
framework: state lives in composables, small `object` holders and `AppPrefs`.

### `data/` (no Compose; Android only for Context, JSON, assets)

| Area | Key files |
|---|---|
| Source layer | `TransitSource.kt` (the one interface the UI talks to), `RoutingSource.kt` (picks a provider per request; `DataSource`, `SourceChoice`, `inGermany`, `inWideArea`, `inVbbArea`), `SourceChain.kt` (`Backend`, `SourceHealth`, `SourceChain`), `FamilySource.kt` (backends of one id family as one source), `HafasRestSource.kt` (shared hafas-rest-api v6 client), `BvgRestSource.kt`, `VbbHafasSource.kt`, `TransitousSource.kt`, `PhotonSearch.kt` (points of interest, addresses), `FootPaths.kt` (real foot ways from the public OSRM foot server), `LiftStatus.kt` (lifts out of order), `BuiltInKeys.kt` |
| Domain model | `Models.kt` (places, legs, journeys, departures, vehicles, `SearchOptions`, stop-name helpers), `Products.kt`, `LineColors.kt` (colour of a ride, always from the ride itself), `VbbZones.kt`, `BikeRules.kt`, `TicketOptions.kt` |
| Departures and journeys | `DepartureBoard.kt`, `DepartureStops.kt`, `ViaRoute.kt`, `FasterRoute.kt`, `Retarget.kt`, `LeaveTime.kt`, `Prognosis.kt`, `AlightEvents.kt`, `RailReplacement.kt`, `PlatformChange.kt` |
| Changes and boarding | `ChangePositions.kt` (where to stand in the train: dataset `vbb-change-positions` plus OSM-derived records), `BoardingGeometry.kt`, `TransferWalks.kt`, `WalkPace.kt` (transfer levels by walking pace), `WaitTone.kt` |
| Offline OSM | `OsmStations.kt` (stations, platforms, entrances, lifts, stop areas and positions, packs), `StopSignPlace.kt`, `StopTowns.kt`, `PathJoin.kt` |
| Disruptions | `Disruptions.kt`, `MapNotices.kt`, `NoticeText.kt`, `WayNotices.kt` |
| Walks | `WalkRoute.kt`, `WalkDetails.kt`, `WalkPace.kt` |
| Places | `PlaceResolver.kt`, `PlaceRanking.kt`, `Contacts.kt`, `ContactPlace.kt`, `Calendar.kt`, `SharedPlace.kt`, `NavLinks.kt` ("navigate here" links: geo:, google.navigation:, Google Maps), `MapShortLinks.kt` (follows maps.app.goo.gl), `ClipboardText.kt` (a pasted text as a search), `SavedPatterns.kt` |
| Settings and language | `AppPrefs.kt`, `AppLanguage.kt`, `RequestLog.kt` (a request as it may be logged: positions cut, address names left out) |

### `ui/`

| Area | Key files |
|---|---|
| Shell | `AppRoot.kt` (the root composable and most screen wiring; four tabs `Tab { Route, Departures, Map, Favorites }`, screens `Screen { Start, Search, Settings, Plan, Connections, Trip, Course, Departures, MapSheet, Favorites }`), `Adaptive.kt` (small, phone, wide), `AppLinks.kt` (`nextstop://open/...`, shares and "navigate here" links of other apps), `AppShortcuts.kt` |
| Screens | `Connections.kt` (connections and the chart view), `Companion.kt` (trip companion), `OtherScreens.kt` (search, stop and place screens), `SetupWizard.kt`, `SettingsSections.kt`, `SettingsPreviews.kt`, `SavedPlaces.kt`, `SavedPatternsUi.kt`, `DisruptionsUi.kt`, `Course.kt`, `JourneyShare.kt` (texts and links to share), `ShareSheet.kt` (the one share sheet of a connection and a followed trip) |
| Map | `MapBackground.kt`, `MapLayers.kt`, `StationDetailLayer.kt`, `MapNoticeLayer.kt`, `MapNoticeUi.kt`, `MapStops.kt`, `StationSigns.kt`, `VehicleGroups.kt`, `VehicleClusters.kt`, `MapDeclutter.kt`, `MapPositionCard.kt`, `WaysMap.kt` |
| Walks | `WalkMarks.kt`, `WalkMiniMap.kt`, `WalkMapFold.kt`, `WalkFocus.kt`, `WalkFocusDetails.kt`, `WalkSummary.kt` |
| Trip following | `TripService.kt` (`TripState`, foreground `TripService`), `TripNotification.kt`, `AlightReminders.kt`, `FasterWatch.kt`, `RetargetUi.kt`, `TripTrack.kt`, `CarriageHint.kt` |
| Disruption watch | `DisruptionWatch.kt` (`DisruptionWatch`, `DisruptionWorker`), `NotificationChannels.kt` (`AppChannel`) |
| Look and motion | `theme/Color.kt`, `theme/Theme.kt` (`AppColors`), `Glass.kt` (blurred map picture behind glass surfaces), `Motion.kt` (`Motion`, `Startup`, `IntroStep`, `awaitIntroStep`), `Delight.kt` (`rememberAnimationsOn`, `TrainLoader`, small motions) |
| Start | `StartIntro.kt`, `StartLogo.kt`, `SplashContinuation.kt` |

### `widget/`

Glance home-screen widgets: `Widgets.kt` (shared drawing and state), `WidgetModel.kt` (the kinds),
`WidgetLoader.kt`, `WidgetUpdates.kt` (WorkManager every 15 minutes plus on demand),
`WidgetPosition.kt` (where the widget thinks the user is, without background location),
`WidgetUi.kt`, `WidgetLaunch.kt`, `WidgetConfigActivity.kt`.

### `wear/` (phone side of the watch link)

`PhoneBridge.kt` (paths, pushes to the watch, `PhoneBridgeService`, `WatchQueries`),
`WatchLink.kt` (is a watch connected and does it have Next Stop; capability `nextstop_watch`),
`WatchSettingsSync.kt` (`WatchSettings`: the phone's copy of the watch app's settings, edited in Settings › Watch, `ui/WatchSettingsUi.kt`),
`WatchMapImages.kt` (renders map pictures for the watch), `WatchMapDetails.kt` (station details
drawn onto them). Details in [section 9](#9-wear-os-app).

## 3. Data sources and routing

The UI only talks to `TransitSource` (`P data/TransitSource.kt`). `RoutingSource` implements it
and decides which provider answers.

**Two levels.**

- `DataSource` (in `RoutingSource.kt`) is an *id family*: `Bvg` (key `bvg`, no id prefix, area
  `inVbbArea`, a polygon around Berlin and Brandenburg) and `Transitous` (prefix `tr:`, no area,
  covers the wide box `inWideArea`, roughly Europe). Everything that comes from a family carries
  its prefix in front of ids, trip handles, refresh tokens and page references, so a later
  request about it goes back to the same provider, also after a restart.
- `Backend` (in `SourceChain.kt`) is one concrete server or library: `Vbb` (official VBB HAFAS,
  needs an access id), `Bvg` (community REST API, no key), `Pte` (public-transport-enabler,
  needs a HAFAS authorization string), `Transitous`. The first three belong to family `Bvg` and
  share stop ids (VBB numbers such as `900100003` for Alexanderplatz), so a stop found by one
  can be asked about at another. Their trip ids carry a tag (`vbb~`, `pte~`, none for BVG).

`SourceChoice` (stored in settings, read anew for every request) holds the user's order and the
switched-on backends; a backend that needs a key is only "in use" with one
(`SourceChoice.configured`, `inUse`, `active`). With nothing else in use Transitous is the
fallback, so the app always has a source. Keys: see [docs/keys.md](docs/keys.md).

**Which family answers.**

- Places around a position (`sourceAt`): the first active family in the user's order that covers
  the position, else the first without an area (Transitous).
- A journey between two places (`journeySource`): the first active family that covers both,
  else the family the start came from. The bike combo goes to Transitous where it is active
  (only MOTIS rides by bicycle to and from the stops).
- A request about an id, handle or token (`sourceOf`): by its prefix.
- Search (`searchStops`): all active families are asked at once, plus Photon for points of
  interest and addresses; one failure leaves the others' results (`mergeStops`, `rankPlaces`).
- Station complexes: `joinComplex` and `complexes` remember that several stops are one station,
  so a departures request fans out to all members and merges them (`mergeDepartures`: the same
  ride from two providers shows once).

**Stand-ins.** Inside a family, `FamilySource` + `SourceChain` try backends in order; a backend
that failed `failuresToTrip` times in a row (2) is skipped for `backoffMillis` (2 minutes) by the
process-wide `SourceHealth`. Across families, when every backend of the first family fails with
`SourceUnreachableException`, `RoutingSource` lets another active family that covers the place
answer (`nearbyStops`, `journeys`) and records it with `noteStandIn`, which drives the hint
"Daten: Transitous". Requests about something one backend made (trip ids, refresh tokens, page
references) go back to that backend only and have no fallback; `NotConfiguredException` and
`NotSupportedException` mean "skip me".

**Language.** Providers answer in the app's language. `AppLanguage` (`P data/AppLanguage.kt`)
reads the string resource `api_language` (`en` in `values/`, `de` in `values-de/`) through the
application context, not `Locale.getDefault()`, because Android picks resources from the whole
system language list while the default locale is only the first entry. `HafasRestSource`,
`TransitousSource`, `VbbHafasSource`, `PhotonSearch` and `LiftStatus` use `AppLanguage.code`.
The watch has its own simpler rule (system language `de`, else `en`).

**Own server.** `AppPrefs.ownServer` (`ownServerUrl`) replaces the BVG base URL by a server with the same
hafas-rest-api v6 API.

## 4. Offline OpenStreetMap data

Everything the providers do not know (platform axes, entrances, lifts, stop positions of bus and
tram stops, exact change walks, boarding positions) is shipped as assets and read on demand.

| Asset (`app/src/main/assets/`) | Content | Read by |
|---|---|---|
| `osm-berlin.json` | Station complexes with VBB ids, platform axes, entrances, lifts and escalators, bus and tram stop areas with their positions and lines | `OsmStations.load` |
| `osm-change-positions.ndjson`, `vbb-change-positions.ndjson` | Where to board for changes between U-Bahn and S-Bahn lines (`vbb-change-positions` is an open dataset, the OSM one is derived) | `ChangePositions` |
| `transfer-walks-berlin.json` | Measured walking seconds between platforms and stops per complex (with a step-free variant) | `OsmStations` (`TransferWalkIndex`) |
| `osm-regions/index.json` | Per state, the 0.1 degree cells that hold stations or stops | `OsmStations.ensureRegions` |
| `osm-regions/osm-<state>.json` | The pack of one of the other 15 states (same format as Berlin) | `OsmStations.ensureRegions` |
| `osm-regions/transfer-walks-<state>.json`, `osm-change-positions-<state>.ndjson` | Walks and boarding positions of that state | loaded with the pack |

`OsmStations` (`P data/OsmStations.kt`) is the one reader. Berlin is built in; the packs of the
states around the map centre and around both ends of a journey are loaded by
`ensureRegions(points)`, as a stream, only in tiles of 0.5 degrees near where the app looks, so
a big pack never has to fit the heap. A file in the app's `files/osm-regions/` (a download, for
a later variant) wins over the asset. Where two packs know the same thing (same name close by)
it is taken once. Without packs the app knows Berlin only.

Pipeline (`tools/`, see [tools/README.md](tools/README.md) for every option):

```
Geofabrik .osm.pbf ──extract_osm_pbf.py──▶ tools/osm-pbf/<region>-stations.json
                                              │
              build_station_asset.py ◀────────┤   (--region berlin | <state> | germany --split-regions)
                    │                         │
                    ▼                         ▼
   osm-berlin.json / osm-<state>.json    walk_graph.py / walk_graph_states.py ──▶ walk graphs
   osm-change-positions*.ndjson                                   │
                    │                                             ▼
                    └──────────────▶ build_transfer_walks.py ──▶ transfer-walks-*.json
build_region_index.py [--install]  ──▶ osm-regions/index.json (+ copies the packs into the app)
```

The raw extracts, graphs and caches (`tools/osm-pbf/`, `tools/osm-cache/`,
`tools/osm-stations.json`, `tools/osm-packs/`) are git-ignored; only the built assets are
committed. Build the assets and the walks from the **same** extract: walks match platforms to the
asset by their axis. The audits (`audit_walks.py`, `audit_stop_signs.py`,
`audit_sign_merges.py`) check the assets against the rules the app applies; some of those rules
exist twice, once in Python and once in Kotlin, and must stay alike (see
[docs/conventions.md](docs/conventions.md#keep-twin-implementations-alike)).

Data (c) OpenStreetMap contributors, ODbL 1.0.

## 5. Map rendering

### Basemap

`MapBackground` (`P ui/MapBackground.kt`) is one MapLibre `MapView` in texture mode (so the map
can be read back as the blurred picture behind glass surfaces). Styles come from OpenFreeMap
(`positron` light, `liberty` colourful, `dark`); no API key. `mapStyleUrl(choice, appDark)` picks
one. A new style drops all sources and layers, so `onStyleLoaded` makes the app call
`installAppLayers` again after every style change.

### Zoom tiers

`MapLayers.kt` defines `CITY_ZOOM = 12`, `NEIGHBOURHOOD_ZOOM = 14` and `MapTier { City, District,
Neighbourhood }`: city shows only the S- and U-Bahn network as lines; district adds trains as
small dots and the stops they serve; neighbourhood shows every vehicle as a line badge with its
direction and every stop. Other thresholds: `MAP_NOTICE_ZOOM = 12.5`, `SELECTED_POSITION_ZOOM = 15.5`,
`STATION_DETAIL_ZOOM = 16` (entrances), lifts from 17 and escalators from 18 (a step earlier for
the marked station; `detailMinZoom` in `MapDeclutter.kt`), `STOP_NAME_ZOOM = 16`.

### Layer order (the rule)

`installAppLayers` in `MapLayers.kt` is the one place that defines the order. MapLibre places
symbols from the top layer down: what is higher is placed first and what is lower gives way. From
the top:

1. **Disruption signs** (`ns-notice-marks*`, `installNoticeLayer`, `MapNoticeLayer.kt`): a small
   badge on the upper right corner of the stop sign it belongs to. They allow overlap and are
   ignored in the placement, so they never push a sign or vehicle aside.
2. **Station and stop signs** (`ns-selected-stop`, `ns-stop-signs`, `ns-stop-named-signs`,
   `ns-stop-names`): added on top of everything else of the app, so a station is never hidden by a
   bus passing it. Within the signs, bigger stations are placed first and smaller stops give way
   (`stopRank` in `MapLayers.kt`: long-distance and regional stations, then S and U, then the rest).
3. **Vehicles** (`ns-vehicle-major` for trains, `ns-vehicle-minor` for buses, trams and ferries,
   the live pulse, direction noses, dots): `BADGE_COLLISION` in `VehicleGroups.kt`
   (`allowOverlap = true`, `ignorePlacement = false`): drawn whatever was placed before them (a
   vehicle never vanishes), and they take their room, so what is below gives way.
4. **Stop position pills of the selected stop** (`ns-selected-positions`,
   `installSelectedPositionLayer`, `StationDetailLayer.kt`): white pills "Pos. 3" with line chips.
   Also allow overlap; pills that would cover something more important are shrunk to their pin
   beforehand (`declutterPills`, `placePills` in `MapDeclutter.kt`).
5. **Entrances, lifts and escalators** (`ns-selected-entrances` etc. for the marked station, then
   `ns-detail-*` for all others), merged where near-identical symbols lie within
   `DETAIL_MERGE_METERS` (`mergeNearSymbols`).
6. **The network** (tile railways, the app's own `ns-rapid-*` lines, tram tracks) below the first
   label layer of the style so names stay readable.

If you add a layer, put it in this order and update this list.

### Stop signs

- **Grouping** (`stationGroups` in `StationSigns.kt`): providers list the parts of a big station
  as stops of their own. Two *rail* stops (`isRailStop`) at most `STATION_MERGE_METERS = 400` m
  apart are one station when they share a stop-place id (VBB number or DHID), or, at most
  `STATION_NAME_MERGE_METERS = 250` m apart, when their base names agree (brackets, "Gleis ...",
  "tief" ignored) or they are the same OSM complex. Stops known only from OSM need a word of the
  name in common. Bus and tram stops in the street never merge into the station sign: they keep
  their "H". A provider stop named as the bus or tram part of a station ("[Bus ...]",
  `STREET_PART`) is a street stop whatever products it lists.
- **The 120 m street-stop rule** (`STREET_STOP_OWN_SIGN_METERS = 120.0` in `OsmStations.kt`, used by
  `mergeMapStops` in `MapStops.kt` and by `OsmStations.mapStops` through `streetStopJoinsStation`):
  a bus or tram stop keeps its own "H" unless its sign stands within 120 m of the sign of a rail
  station of its name or stop-place number. An OSM stop within 40 m of a provider stop is that stop.
  The audit `tools/audit_sign_merges.py` replicates this rule; keep both alike.
- **Where the sign of a bus or tram stop stands** (`stopSignPoint` in `P data/StopSignPlace.kt`,
  twin: `tools/stop_sign.py`): take the OSM positions of the stop within 250 m of the one nearest
  to the provider's point; keep the provider's point if it lies within 30 m of one of them and inside
  their bounding box plus 20 m; otherwise put the sign at their centre, pulled to at most 30 m from
  a position. Applied to nearby and name searches (`RoutingSource.signed`) and to stop areas known
  from OSM alone (`OsmStations.mapStops`).
- **Merging provider and OSM stops**: `mergeMapStops` draws the provider's stops as they are and
  the OSM ones only where no provider stop stands for them; a tapped OSM-only sign is resolved to
  the provider's stop by name and distance (`resolveOsmStop`, ids start with `OSM_STOP_PREFIX`).

### Vehicles: groups and clusters

- **At a station** (`VehicleGroups.kt`): vehicles within `GROUP_RADIUS_METERS = 100` m of a sign
  that serves their product line up in a column beside it (`GroupAnchor`, `AnchorIndex`,
  `candidateAnchor`, `layoutGroup`), trains first, at most `GROUP_MAX_SLOTS = 4` rows and a chip
  "+N" for the rest. A station sign serves rail products only; an "H" serves buses, trams and
  ferries (`anchorProducts`). A column starts below a disruption sign if the stop has one
  (`NoticeSignSpots.columnTops`).
- **On the way** (`VehicleClusters.kt`): vehicles not at a station whose badges would overlap at
  the current zoom line up beside a small dot at their common middle (`FreeVehicle`,
  `VehicleCluster`), from `CLUSTER_MIN_ZOOM` on. A pair that was together stays together until its
  badges are `CLUSTER_KEEP_DP` apart, so it does not flicker.
- The vehicle layer moves badges about 30 times per second while they glide
  (`VEHICLE_LAYER_FRAME_MILLIS`) and looks again every 400 ms at rest.

### Collisions that live outside MapLibre

MapLibre's own collision handles symbols; the rules that need app knowledge are plain Kotlin
in `MapDeclutter.kt` (pill placement, near-identical symbol merging, zoom per detail kind) and
`VehicleGroups.kt`/`VehicleClusters.kt`, with unit tests (`MapDeclutterTest`,
`VehicleGroupsTest`, `VehicleClustersTest`, `BadgeCollisionTest`, `StationSignsTest`).

## 6. Disruptions

**Model** (`P data/Disruptions.kt`). A `Disruption` is the text of a provider notice plus where it
was found: `lines`, `products`, `carriers` (each run with its own product), `stops`,
`validFrom`/`validUntil`, `links`, `providerIds`. Sources: the `warning` remarks of the BVG REST API
and the GTFS-RT alerts that Transitous sends on legs. There is no list of all current notices, so
the overview is put together from the departures of large stations (`HUB_STOPS`).

**Ids and de-duplication.** `disruptionId(warning)` is a hash of the *normalised* summary and text
(`normalNotice`: lower case, no placeholders or punctuation), so copies that differ only in case,
spacing or link placeholders have the same id. `mergeDisruptions` also treats two notices as one
when the provider gives them a common id or when one text holds the other under the same headline
(at least `MIN_HELD_TEXT = 40` characters), and `combine` unions lines, stops and validity.

**Which lines a notice is about** is parsed from its text, not from where it was found
(`linesNamedIn`, regex `NAMED_LINES`: "Tram 12:", "Bus M45, 245:", "U6:", "S41/S42:", also in
English); BVG puts a notice on the replacement buses of a tram, and a lift notice on every
departure of the station. The kind of transport comes from the word before the line, else the
name (`U...`, `S...`, `RE...`), else a carrier of the same name. `Disruption.about` is that list,
`badges` what the card shows.

**Station notices** (`stationNotice`, `STATION_WORDS`): a lift or escalator out of order or a stop
that moved, by the headline or the first 120 characters; they form the group
`DisruptionGroup.Stations`. Groups: S, U, tram, bus, other, stations (`groupDisruptions`).

**Location and radius.** A notice says where it is only in its text. `disruptionsWithin(spot,
radius, near, disruptions)` keeps a notice when it is a station notice at a carrier stop inside the
radius, or when its text names a stop (whole-word match of the name forms, at least 5 letters,
`stopsNamedIn`/`nameInText`) that lies inside the radius. A notice of a line that only passes by
is left out. Radii: `PLACE_RADII = 250, 500, 1000, 2000` m, default `DEFAULT_PLACE_RADIUS = 500`.
`placeDisruptions` asks the departures of the stops nearest to a place (at most `placeStopCap`);
`routeStatus` does it for a saved route's start, end and changes (`routeSpots`, at most 6,
150 m apart) and keeps the notices on its next journeys separate (`RouteStatus.disruptions` vs
`around`).

**Watch** (`P ui/DisruptionWatch.kt`, `DisruptionWatch`, `DisruptionWorker`). WorkManager runs a
periodic job every 15, 30 or 60 minutes (or none; `WATCH_PERIODS`, `watchSchedule`) and one
one-time job per daily check time (`scheduleCheck`, each re-plans itself the next day). Nothing
runs while no route has the bell on and no place is watched. A notice is reported once per route
or place: what was reported is kept for `SEEN_DAYS = 3` days, and the first look at a place (or at
a radius) only learns what is there ("baseline" keys), so it does not notify. Notifications go to
the channel `AppChannel.Disruptions`. State lives in its own preferences file `disruptions`.

**Map notices.** `P data/MapNotices.kt` decides where a notice lies on the main maps: a station
notice at the stops that carry it; any other notice at the stops its text names, searched only
within `NOTICE_AREA_METERS = 15 km` of the carrying stops (`noticePlaces`, `NamedStops`, at most 3
places); a notice of a whole line that names no stop has no marker. `noticeMarkers` groups notices
per stop (`groupNoticePlaces`: stops close together, or of one name in one station, are one marker).
`P ui/MapNoticeUi.kt` holds `MapNoticePool` (the notices known, fed by the departures the map
loads and by an area request for the big stops in view, `bigStopsIn`), `MapNoticeSetting` (setting
"map notices") and `HiddenNotices`: a hidden notice is remembered by id with its headline and
dropped `HIDDEN_NOTICE_KEEP_MILLIS` (5 days) after it was last seen (`prunedHidden`). Drawing:
`MapNoticeLayer.kt`.

## 7. Walks

Two different things are called a walk: the **measured walk of a change** (seconds from the
offline assets, used for the transfer levels) and the **walk as drawn** (a leg of a journey).

**Change walks.** `TransferWalks.kt` and `OsmStations.transferWalkSeconds` give seconds at 75 m per
minute (stairs, escalators and lifts included), also step-free. `WalkPace.kt` rates a change by
pace: usual pace need `ceil((s + 30) / 60)` minutes, "knapp" at a brisk pace (x
`BRISK_WALK_FACTOR = 0.8`), otherwise sprint; "Langsamer unterwegs" multiplies every walk by
`SLOWER_WALK_FACTOR = 1.3`. Rules and numbers: [tools/README.md](tools/README.md#how-the-app-uses-the-walks-transfer-levels-by-walking-pace).

**Walk as drawn.**

- *Way and minutes* (`P data/WalkRoute.kt`, `P data/FootPaths.kt`): a provider often knows a walk
  only as a straight line. `FootPaths` asks the public OSRM foot server for a real way (one
  request at a time, at most one per second, cached by rounded ends, only for walks on screen; a
  `FootWay` status says `Real`, `Pending`, `Straight` or `Short`). Minutes are **path-based**:
  `shownWalkMinutes(walk)` is the larger of the planned minutes and the drawn way at the app's
  pace (`walkMinutesFor`, 75 m/min times the walking factor, rounded up, at least 1), so a 300 m
  way through a station that a provider counts as one minute shows four. A ride by bike keeps its
  planned time.
- *Leaving the way* (`OffRouteWatch`, `routeFix`): more than 40 m beside the way, or walking away
  from its end, for 20 s (positions worse than 30 m count for nothing) asks for a new way.
- *What the walk passes* (`P data/WalkDetails.kt`, `walkDetails`): from the loaded OSM data, the
  stop positions at both ends (the one it leads to, the one it starts at), the entrances and exits
  it passes, and lifts and escalators with their direction (`WalkPassage`, `PassageKind`).
- *Summary line* (`P ui/WalkSummary.kt`, `walkSummaryText`): "Ausgang B · Rolltreppe runter · zu
  Pos. 9 (4 min)"-style text from those details, equal items in a row counted.
- *Marks* (`P ui/WalkMarks.kt`): **one drawing** of the marks (pale stop-position dots under the
  way; entrances, lifts and position pins over it) used by the small map (`WalkMiniMap`), the big
  map while a walk is in focus (`WalkDetailsOverlay` in `WalkFocusDetails.kt`) and the watch's map
  pictures (`WatchMapImages`), so they cannot drift apart. Do not draw a second version.
- *Small maps* are folded behind a "Show map" button unless the setting is on (`WalkMapFold.kt`);
  a folded map takes no map picture.

## 8. Startup choreography

The start page comes in step by step so every part is seen. The state lives in `P ui/Motion.kt`.

- `MainActivity` keeps the system splash until its logo animation has played (800 ms; not at all
  with animations off), then Compose continues it (`SplashContinuation`: the blue and the logo fade
  into the app). `Startup.splashDone` is set at the latest 600 ms after the splash let go, also when
  the system shows no splash.
- The map starts to come in only when **both** its first picture is complete (style loaded, camera
  placed, tiles rendered) and `Startup.splashDone` (`MapBackground`). It fades in over 520 ms
  settling from a scale of 1.04; if style or tiles never load it comes in after 6 s anyway
  (`MAP_SHOW_LATEST`). The map itself is never made transparent: an app-ground cover (`MapLoading`)
  fades out over it.
- `MapLoading` shows the app's logo in grey on the app ground, breathing, only if the map takes
  longer than 250 ms (so a quick map never flashes it); with animations off the logo stands still.
- At that moment `Startup.mapShownAt` is set and `Startup.mapIn = true`; everything drawn over the
  map in Compose (own position, saved places) follows (`rememberMapInAlpha`).
- `awaitIntroStep(step)` waits until `step * INTRO_STEP_MILLIS (110 ms)` after `mapShownAt`;
  returns at once for data that came late. Steps (`IntroStep`): `SIGNS = 2`, `VEHICLES = 3`,
  `NOTICES = 4`, `CARDS = 5`, `NAV = 8`, `LIVE = 10`. Each part has a once-only flag in `Startup`
  (`signsIn`, `vehiclesIn`, `noticesIn`, `navIn`, `introPlayed`) so it animates only on the first
  start of the run. The stop-signs intro sets its flag in a `finally` block, so signs never stay hidden when the
  start is cut short.
- A start without the map (map off for the start page) gives the beat as soon as the splash is gone.

All of it respects "animations off" (`rememberAnimationsOn`): then everything is simply there.

## 9. Wear OS app

The watch app is a **standalone** app (`com.google.android.wearable.standalone = true` in
`wear/src/main/AndroidManifest.xml`) that works without the phone with its own network and
location; the phone adds favourites, its own sources, its followed trip and map pictures.

### Identity, versions, build

- `applicationId = "io.github.jnbp.nextstop"` in **both** modules, namespace
  `io.github.jnbp.nextstop.wear` on the watch. Same id and same signing key are what make the two
  apps find each other over the Data Layer and what Play needs for the Wear OS listing.
- Version codes must be unique across form factors: phone `versionCode = n`, watch
  `1_000_000 + n` (`wear/build.gradle.kts`: `1_000_001`).
- The watch needs `uses-feature android.hardware.type.watch`. min SDK 30, target SDK 36.

### Package map (`wear/src/main/java/io/github/jnbp/nextstop/wear/`)

| Package | Key files |
|---|---|
| `ui/` | `MainActivity.kt` (permissions, `WatchStore.load`, on every resume `WatchStore.refresh` and `DepartureCache.prefetchFavorites`; `AppForeground`), `WearApp.kt` (`Routes`, `Selection`, swipe-dismissable navigation, opens the trip page when a trip starts, ends it when it ends; settings page, `TripCard`), `StartPage.kt` (`StartScreen`: header with the app icon, live departures, route, map preview, favourites), `Theme.kt` (`Brand` colours, button colours), `TripRing.kt` (`drawRideWay`, `RideRing`: the way of a ride on the ride page and the trip card), `Screens.kt` (nearby stops, departures, route search, results, settings), `TripCompanion.kt` (`TripScreen`, the pages of a followed trip), `NearbyMap.kt` (`MapScreen`), `MapImageLayer.kt` (`rememberMapImage`, `MapImageStatusLine`, `drawMapImage`), `DestinationLists.kt`, `Components.kt` (line badge, `DepartureRow`), `Loading.kt` (`TrainLoader`, `LoadingNote`), `Motion.kt`, `OpenRequest.kt` (a page asked for by a tile or the ongoing activity) |
| `data/` | `Protocol.kt` (paths, keys, `TripSnapshot`, JSON parsing, `snapshotOf` for trips the watch follows itself), `WatchStore.kt` (what the watch knows; flows `trip`, `places`, `comfort`, `mapStyle`, `liveDepartures`, `animations`, `phone`), `WatchSettingsSync.kt` (settings edited on both sides, merge by time), `StartGlance.kt` (pure logic of the start and ride pages), `NearbyGlance.kt` (`NearbyGlances`: the live departures of the start page), `UsualLines.kt` (lines of the last trip started to each destination), `WatchDataService.kt`, `PhoneQuery.kt`, `TransitRouter.kt`, `BvgClient.kt`, `TransitousClient.kt`, `NearbyFallback.kt` (`TransitousNearby`, `withHardTimeout`), `LocalFollow.kt`, `TripService.kt`, `TripOngoing.kt`, `MapImages.kt`, `MapImageCache.kt`, `WebMercator.kt` (`MapImageRequest`, `MapFrame`), `ChangeMapFit.kt` (`TripMapPrefetch`), `DepartureBoard.kt`, `DepartureCache.kt`, `Position.kt`, `Haptics.kt`, `LineColors.kt`, `PhoneSetup.kt`, `ContactUsage.kt`, `RoundScreen.kt`, `Models.kt`, `Diagnostics.kt` |
| `tile/` | `NextTileService.kt` ("Next departure": the next step of the followed trip, else the next three departures of the first favourite stop; freshness 60 s), `FavoriteTileService.kt` ("To a favourite": the next connection to the chosen or home destination) |
| `complication/` | `NextComplicationService.kt` (SHORT_TEXT: minutes and line of the next step, else of the next departure at the first favourite; the system asks every 300 s) |

Screens (routes in `Routes`): `start`, `nearby`, `departures`, `map`, `route`, `results`, `trip`,
`settings`, `tile-target`, `all-recent`, `all-contacts`. The trip screen is a horizontal pager whose
pages depend on the phase (`TripPage`: `Depart` countdown before boarding, `Ride` progress line,
`Change` card with the change map and rating, `Overview` of the whole connection always last;
`pagesOf`, `desiredPage`). Ambient mode is respected (`rememberAmbient`).

### Data flow phone to watch (Wearable Data Layer)

Defined twice, keep in step: `W data/Protocol.kt` and `P wear/PhoneBridge.kt`.

Data items (phone writes, the Data Layer keeps them, the watch also sees them later; every item is
one JSON string under the key `json`):

- `/nextstop/places`: favourites, saved places, recent destinations, the phone's last position,
  optionally `appointments`, `contacts`, `transferComfort`, `alightReminders` (every key optional).
  Written on every start of the app, when those lists change (debounced), while a trip runs and when
  the watch asks.
- `/nextstop/trip`: the next step of the trip the phone follows, or `active = false`; written while
  the phone's `TripService` runs, at least every minute (the watch treats a phone trip as stale after
  3 minutes of silence or 5 minutes after its arrival: `TripSnapshot.stale`).
- `/nextstop/mapimg/<slot>` and `.../<slot>-q`: a map picture as a WebP `Asset` with the area it
  drew (key `lat`, `lon`, `zoom`, `lw`, `lh`) and the key of the request.

Settings of the watch app edited on both sides (live departures, animations, map style, tile
target): each side writes its own data item, the phone `/nextstop/watch-settings`, the watch
`/nextstop/watch-settings-w`, one JSON `{"v": 1, "settings": {key: {"value", "at"}}}`. Each reads
the other's and keeps, per key, the newer change (its own on a tie); keys it does not know are kept
and written on (`W data/WatchSettingsSync.kt`, `P wear/WatchSettingsSync.kt`). The Data Layer
hands an item over once the devices are connected, so a change made offline arrives later. The
reminder before getting off and the change time keep their older way (the phone's own setting in
`/nextstop/places`, a watch override held until the phone changes its own).

Messages (watch to phone): `/nextstop/sync`, `/nextstop/start-trip` (payload: refresh token of
a journey), `/nextstop/stop-trip`, `/nextstop/map-image` (a view: centre, zoom, size, pixel ratio,
dark, optionally a `quick` small version), `/nextstop/warm-up` (the watch app opened: style, pixel
ratio and, if known, the position; no answer, see below), `/nextstop/query` (a lookup with the phone's own
sources: `journeys`, `departures`, `search`). Message phone to watch: `/nextstop/answer` (the answer to
a query, same `id`). Capabilities: the phone app declares `nextstop_phone`
(`app/src/main/res/values/wear.xml`), the watch app `nextstop_watch`
(`wear/src/main/res/values/wear.xml`); that is how each side tells "a phone/watch is connected" from
"... with Next Stop on it" (`PhoneLink`, `WatchState`).

The watch side is a `WearableListenerService` (`WatchDataService`, filter `wear://*/nextstop`) that
works while the app is closed: it stores places and trip in `WatchStore` (preferences `wear-store`),
asks the tile and complication to update, and vibrates once per ride when the phone says the next
stop is the one to get off (`alertGetOff` in `W data/Haptics.kt`: one pattern for the end of the trip, another for a change).
The phone side is `PhoneBridgeService` (`MESSAGE_RECEIVED`, same prefix). `WatchQueries` (in
`PhoneBridge.kt`) answers queries with the phone's whole source chain, so the watch finds journeys
all over Germany.

### Map pictures

The watch stays light: it does not render maps. For a view it sends `MSG_MAP_IMAGE`; the phone
(`P wear/WatchMapImages.kt`) renders it with MapLibre's `MapSnapshotter` in the style of its own map
(Positron or dark), keeping one snapshotter warm for a while, draws the station details onto it
with the same signs as the phone map (`WatchMapDetails`: signs, entrances from zoom 16, lifts 17,
escalators 18, stop-position pills), and writes it as WebP at `/nextstop/mapimg/<slot>`. A newer
request of a slot cancels the older. If drawing fails, the answer has the key and no picture, and
the watch draws its vector sketch.

Time on the phone (`P wear/WatchMapBudget.kt`): one deadline per request, 35 s after it came (the
watch waits 40 s), shared by the quick picture and two tries of the full one; a try on a fresh
snapshotter gets a longer limit than one on the warm snapshotter, the last try has what is left.
Only one still picture is drawn at a time in the app (`P ui/SnapshotGate.kt`, shared with the
small maps of walks, disruptions and ways). Each try holds MapLibre's file source active, which
the live map pauses while a screen covers it. When the watch app opens it sends `/nextstop/warm-up`
(`W data/MapWarmUp.kt`, at most every 5 min); the phone (`WatchMapImages.warmUp`, at most every
minute, not while warm) sets up MapLibre and draws a small picture at the watch's position, so the
first real picture finds the snapshotter warm and the tiles cached. A real request gives a
warm-up at work up. Everything is logged with the tag `WatchMap` on both sides.

On the watch (`W data/MapImages.kt`, `W ui/MapImageLayer.kt`): `rememberMapImage(slot, view)` first
looks for a kept picture that covers the view, then a near one to show meanwhile
(`MapImageCache.nearest`: same style, at most 1.5 zoom steps off, at least 30 % overlap), then
asks the phone (a `quick` small picture first while little is shown, then the full one; waits at
most 40 s, retries). `MapImageStatus` (`Waiting`, `Ready`, `NoPhone`, `NoAnswer`, `NotDrawn`) feeds
the status line: a first failure is retried quietly while it shimmers ("Map from phone…"), only a
second one shows "Map not loaded · Try again", which asks again on a tap (`W data/MapImageRetry.kt`). The picture is stretched over the area the phone says it drew (`MapFrame`), so it
sits right whatever its pixel size; the watch draws its own signs in the same Web Mercator
projection (`WebMercator.kt`). `TripMapPrefetch` asks for the pictures of the walk and the next
change before their card is shown, and silently again (twice, a minute later at the earliest) when
one did not come.

**Cache and versioning.** `MapImageCache` keeps the newest 20 (`CAPACITY`) pictures as plain files in
`cacheDir/watchmap` (`<key>.webp` plus `<key>.frame`), least recently used out first. The key is
`v2_<l|d>_<zoomStep>_<tileX>_<tileY>_<size>` (style, zoom in steps of `ZOOM_SLACK = 0.2`, the tile
of the centre at that zoom, logical width in powers of 1.5). The prefix is `LOOK = "v2"`: **raise it
whenever the phone draws something new onto the pictures** (the station details were the change from
v1 to v2), and every older file is dropped on the next index. The decoded last 6 stay in memory.

### What runs where

| | Watch | Phone |
|---|---|---|
| Journeys, departures, search | Asks the phone first (`PhoneQuery`, `TransitRouter`); on its own `BvgClient` in the VBB area and `TransitousClient` elsewhere, each standing in for the other. Departures of a VBB stop come from BVG directly, the phone stands in. | Full `RoutingSource` chain (all backends, the user's order and keys) |
| Positions | Own location (`currentPosition`); the phone's last position as fallback | Own location |
| Followed trip | Shows the phone's (`PATH_TRIP`) while the phone follows; with no phone, follows by itself (`LocalFollow`: reloads the journey every 30 s by its handle, works out the next step every 15 s) under a foreground service (`wear TripService`) whose notification is an Ongoing Activity (`TripOngoing`) | `TripService`, alight reminders, faster-way watch, writes the state |
| Map pictures | Cache and draw | Render |
| Favourites, saved places, change time | Read only (change time and alight reminder can be switched on the watch; the watch value stays until the phone changes its own) | Source of truth |
| Settings of the watch app (live departures, animations, map style, tile target) | Edited on the watch, merged with the phone's by time | Edited in Settings › Watch, merged with the watch's by time |

**One trip, two devices.** The phone is the source of truth when reachable. A trip started on the
watch is followed there at once and sent to the phone (`MSG_START_TRIP`); once `/nextstop/trip`
says the phone follows a trip, the watch stops its own and shows the phone's. Ending it on either
side ends it on both (`MSG_STOP_TRIP`). Without the phone a trip found via Transitous has no
refresh token: the watch follows it by its times only. A change rating on a watch-only trip is a
rough estimate (`ChangeRating.estimate`); a phone trip carries the phone's real one.

### Loader and motion

`TrainLoader` (`W ui/Loading.kt`, with `LoadingNote`, the watch's counterpart of the phone's `LoadingRow`) is the phone's loader (`P ui/Delight.kt`
`TrainLoader`) reduced for the round screen: three plain rounded cars gliding over a dashed track,
eased like a train pulling out and braking in (cubic-bezier .45/.05/.55/.95, 2.4 s per run), fading
at both edges, standing in the middle when animations are off. `W ui/Motion.kt` gives buttons and the
arrival (a check with confetti along the round edge) the phone's feel, held back; nothing animates
in ambient mode or with animations off (`rememberWatchAnimationsOn`: the system's
`ANIMATOR_DURATION_SCALE` and the app's setting "Animations", `WatchStore.animations`; every motion
reads it, the loader too). Every page comes in softly once when it opens (`watchScreenEnter`, around each destination in
`WearApp.kt`), full-size buttons and cards give way under the finger (`rememberWatchPress`,
with `watchSpringPress`), lists of the start page enter row by row (`watchEnter`), loading rows
shimmer (`watchShimmer`).

### Start page and ride page

The start page (`StartPage.kt`) shows, with the setting "Live departures on the start page" on, the
nearest stop and its next five departures (four below 200 dp) as plain list rows (`DepartureRow`
with big minutes, compact) and a button to its whole board; off, the button "Departures nearby" alone.
The board uses the same row with `details` (two lines: badge with platform and delay, direction
below) and a full-size button each for a route from and to the stop after the departures. Both
title the stop with `StopHeader`: the name from `stopNameParts` (`W data/StopNames.kt`, the twin of
the phone's, without the town the watch is used in, `WatchTown`) in two lines that shrink before
an ellipsis and keep clear of the round top.
`NearbyGlances` loads them through the usual pipeline every 45 s while the app is in front
(`AppForeground`) and looks the nearest stop up again only after 80 m or 10 minutes. Favourite
stops show their next departure from `DepartureCache`; other favourites the lines of the last
trip started to them on the watch (`UsualLines`, no search per favourite). The ride page draws the
way to the stop to get off along the round edge (`drawRideWay`; a bar on square screens, outlines
in ambient mode); the buzz before getting off stays `alertGetOff`.

### Other facts

- Permissions: INTERNET, location (coarse and fine, no background location), VIBRATE,
  POST_NOTIFICATIONS, foreground service (special use, for the trip service).
- Text input: `rememberTextInput` (`Screens.kt`) uses the system keyboard/voice
  (`REMOTE_INPUT` and speech recognition are declared under `<queries>`).
- `PhoneSetup` opens Next Stop on the phone (`nextstop://open`, via `RemoteActivityHelper`) or its
  download page.
- Logcat tags: `NextStopWear` (`Diagnostics.kt`), `WatchMap` (both sides, the whole way of a map
  picture with times), `NextStopWatchLink` (phone side `WatchLink`).
- Backup rules: `wear/src/main/res/xml/wear_backup_rules.xml`, `wear_data_extraction_rules.xml`.
- Unit tests: `wear/src/test` (`DepartureBoardTest`, `MapImageCacheTest`, `WebMercatorTest`,
  `RoundScreenTest`, `MotionTest`, `StartGlanceTest`, `WatchSettingsSyncTest`).

## 10. Widgets and shortcuts

- **Widgets** (Glance): departures, favourite, trip, mini, appointment and auto widgets, declared in
  `app/src/main/AndroidManifest.xml` (`*WidgetReceiver`) with `res/xml/widget_*_info.xml`. A widget
  works without being configured (nearest stop, first favourite). Data is loaded by WorkManager every
  15 minutes (with a network), when the app comes to the front, when a widget is set up and when a
  widget showing a soon departure has data older than four minutes; the minutes are worked out from
  absolute times at every draw, and `WidgetTickReceiver` redraws at the start of a minute while
  something leaves soon (`WidgetUpdates.kt`). Without background location Android gives a job no fresh
  position, so `WidgetPosition.kt` uses the last known one.
- **Shortcuts** (`P ui/AppShortcuts.kt`): "Departures here" is static (`res/xml/shortcuts.xml`);
  home, work and recent destinations are dynamic; a stop can be pinned. Every shortcut is a
  `nextstop://open/...` link handled by `AppLinks` in `MainActivity`.
- Notification channels in one place: `P ui/NotificationChannels.kt` (`AppChannel`: `Alight`,
  `Trip`, `Shortcut`, `Watch`, `Disruptions`).

## 11. Settings, storage and backup

- `AppPrefs` (`P data/AppPrefs.kt`) wraps the SharedPreferences file `next-stop` (settings,
  favourites, saved places, saved routes and patterns, recent searches). Keys of data backends live
  in a file of their own, `source_keys` (`KEYS_PREFS_NAME`); lift sources use `lift_keys`. The
  disruption watch has `disruptions`, the widgets `next-stop-widgets`.
- Stored names (setting keys, `DataSource.key`, `Backend.key`, palette names in `theme/Theme.kt`)
  "must stay as they are": a rename loses the user's setting. Add new keys, migrate in the
  `init` block of `AppPrefs` as the existing migrations do.
- **Android backup** (`app/src/main/res/xml/data_extraction_rules.xml` for Android 12+,
  `backup_rules.xml` for `fullBackupContent`): only shared preferences are included, minus
  `source_keys.xml`, `lift_keys.xml` and `next-stop-widgets.xml`. Pack downloads, caches, the tile
  cache and the WorkManager database are not backed up. A new file with user data outside
  `shared_prefs/` needs an `<include>` in **both** files; a new key file needs an `<exclude>` in both.
- No tracking, no analytics, no crash reporter. Requests that are logged go through `RequestLog`.

## 12. Localisation and stop names

**Strings.** English is the default (`app/src/main/res/values/`), German is `values-de/`. Both are
split into the same set of files (`strings.xml`, `strings_modes.xml`, `strings_anim.xml`,
`strings_feat.xml`, `strings_set.xml`, `strings_extras.xml`, `strings_wear.xml`); the watch has its
own `wear/src/main/res/values{,-de}/strings.xml`. `api_language` is a string resource on purpose
(see [section 3](#3-data-sources-and-routing)).

**Stop names** are shown, never stored, in a normalised form; requests and ids keep the raw name.
All of it is in `P data/StopTowns.kt` and `P data/Models.kt`:

- `splitStopTown(name)`: "Town, Stop" and "Stop (Town)" split into stop and town. Brackets that
  belong to a town's name ("Frankfurt (Oder)"), directions, parts ("(Nord)", "(Kehre)"), landscapes
  and district codes are not towns (`NOT_A_TOWN`, found with the audit tools).
- `stopNameParts(name, home)` returns `StopNameParts(title, part)`: the title without the town in
  brackets when it is the town the app is used in, and the part in square brackets behind it
  ("[Bus K.-Liebknecht-Str]", "[Gleis 1-8]", "[U2]") as a small line of its own.
- `shortStopName()` = `stopNameParts(...).title`; `plainStopName()` additionally drops a trailing
  bracket; `briefStopName()` also drops the network sign in front ("S+U ", "S ", "U ") and "Bhf"
  behind, for narrow labels; `listedStopName()` is the one-line form "Stop · part";
  `townedStopName` adds the town for a list that belongs to another town.
- `LocalTown` holds the town the app is used in: the town most stops around name in brackets
  (at least three stops of different names and half of all), noted by `LocalTown.noteStops`, else
  Berlin. So in Potsdam "S Golm (Potsdam)" loses its town and a Berlin stop shows it.
- Matching names between providers and OSM uses keys (`stationNameKey`, `osmNameKey`, `nameFits`),
  never display names.
