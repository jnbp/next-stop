# Development guide

Everything you need to build, run, test and regenerate data for Next Stop. For how the code is
organised read [ARCHITECTURE.md](../ARCHITECTURE.md); for the rules to follow when changing it,
[conventions.md](conventions.md).

Commands are run from the repository root.

## Prerequisites

| Need | Version / note | Where it is set |
|---|---|---|
| Android Studio | A recent stable release that supports Android Gradle Plugin 9.5 | `gradle/libs.versions.toml` (`agp = "9.5.1"`) |
| JDK | 17 or newer to start the Gradle wrapper (the JetBrains Runtime of Android Studio works). The Gradle daemon itself asks for JDK 25 (`toolchainVersion=25` in `gradle/gradle-daemon-jvm.properties`); Gradle downloads it through the foojay resolver plugin in `settings.gradle.kts`, so the first build needs network | `gradle/gradle-daemon-jvm.properties` |
| Gradle | 9.6.0 through the wrapper, use `./gradlew`, never a local Gradle | `gradle/wrapper/gradle-wrapper.properties` |
| Android SDK | Platform **37** (`compileSdk`, phone `targetSdk`), platform **36** for the watch `targetSdk`. The matching build tools are chosen by AGP | `app/build.gradle.kts`, `wear/build.gradle.kts` |
| min SDK | Phone 31 (Android 12), watch 30 (Wear OS 3) | same |
| Kotlin / Compose | Kotlin 2.2.10, Compose BOM 2026.02.01 | `gradle/libs.versions.toml` |
| Node.js | Any current LTS, for the web tests (no packages to install) | `web/test/` |
| Python 3 | The tools use the standard library; `tools/extract_osm_pbf.py` also needs `osmium` (pyosmium 4 or newer), and `tools/walk_graph_states.py` needs `numpy`. `pytest` is **not** used | `tools/README.md` |
| Network on first build | Maven Central, Google, and JitPack (only for `public-transport-enabler`, pinned to a commit) | `settings.gradle.kts` |

Source language level is Java 11 (`compileOptions`), Kotlin official code style
(`kotlin.code.style=official`). The Gradle configuration cache is on (`gradle.properties`).

## Building

```bash
./gradlew assembleDebug            # phone and watch debug APKs
./gradlew :app:assembleDebug       # phone only
./gradlew :wear:assembleDebug      # watch only
./gradlew :app:installDebug        # build and install the phone app on a connected device
```

Outputs are under `app/build/outputs/apk/debug/` and `wear/build/outputs/apk/debug/`.

### Without public-transport-enabler

If JitPack cannot build the library, set `nextstop.pte=false` in `gradle.properties`. The app then
compiles `app/src/nopte/java` (a `PteSource` stand-in that reports "not set up") instead of
`app/src/pte/java`, and the settings show the source as "not included in this build". Both
directories hold one file, `data/PteSource.kt`; keep the two in step when the `TransitSource`
interface changes.

### Keys

Copy `secrets.properties.example` to `secrets.properties` (git-ignored) and fill in what you have.
Empty or missing keys are fine; the build embeds them as `BuildConfig` fields. In CI the same keys
can come from environment variables named `NEXTSTOP_<KEY_IN_SNAKE_CASE>` (for example
`NEXTSTOP_VBB_ACCESS_ID`). A key the user enters in the settings always wins over the built-in one.
Never commit `secrets.properties`, never paste a key into an issue, log or screenshot, and do not
publish builds with keys built in. Details and the list of keys: [keys.md](keys.md).

### Release builds and signing

The release build type has R8 optimisation on for the app's own code and `androidx.**`, `kotlin.**`,
`kotlinx.**` (`optimization { enable = true; packageScope = ... }`) and **no signing config**: the
repository holds no keystore and no signing properties. Signed bundles are made with Android
Studio's *Build > Generate Signed App Bundle / APK* using the upload key, outside the repository.
The whole procedure (upload key, Play App Signing, tracks) is in [play-release.md](play-release.md);
the answers for the Play Console forms are in [play-console-answers.md](play-console-answers.md).
`.gitignore` already excludes `*.jks`, `*.keystore`, `keystore.properties`, `*.aab` and `*.apk`.
Always run a release (optimised) build once on a real phone and watch before uploading: R8 can break
what a debug build does not.

### Versioning

Both modules have their own `versionCode` and `versionName` in their `defaultConfig`:

| Module | versionCode | versionName |
|---|---|---|
| `app` | `n` (now `1`) | `0.1` |
| `wear` | `1_000_000 + n` (now `1_000_001`) | `1.0` |

Rules:

- Play requires version codes to be unique across all form factors of one app, and both modules
  share one `applicationId`. So **the watch code is always the phone's `n` plus 1,000,000**.
- Raise both by the same step for a release (`n` to `n + 1`), also when only one module changed,
  so the pair stays recognisable. A code that was uploaded can never be used again.
- `versionName` is a label only; use the same name for both at release time (for example `0.1.0`).
- Changelogs for the stores are `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt`
  (today `1.txt`), 500 characters per language at most.

## Tests

### Unit tests (JVM, no device)

```bash
./gradlew test                                  # phone and watch
./gradlew :app:testDebugUnitTest                # phone only
./gradlew :wear:testDebugUnitTest               # watch only
./gradlew :app:testDebugUnitTest --tests 'io.github.jnbp.nextstop.data.DisruptionsTest'
```

Phone tests live in `app/src/test/java/io/github/jnbp/nextstop/{data,ui,widget}/`, watch tests in
`wear/src/test/`. They are plain JUnit 4 tests of pure logic (the rules of the map, the board, the
disruptions, the walks); keep logic that needs a test free of Android classes so it runs here.
`app/src/androidTest` holds only the template instrumented test.

### Web pages

```bash
for t in web/test/*.test.js; do node $t; done
```

Offline tests of `web/shared/core.js` and the two pages (`live.test.js`, `route.test.js`, fixture
in `web/test/fixtures/`); they print TAP and exit non-zero on a failure.

### Python tools

The tests are plain scripts (no pytest). Each prints "OK" or "All checks passed":

```bash
for t in tools/test_*.py; do python3 $t || break; done
```

They use small synthetic data and need no download: `test_build_station_asset.py`,
`test_build_transfer_walks.py`, `test_extract_osm_pbf.py`, `test_stop_positions.py`,
`test_stop_signs.py`, `test_sign_merges.py`. Run them after any change to `tools/` and after any
change to a rule that exists in both Kotlin and Python (see [conventions.md](conventions.md#keep-twin-implementations-alike)).

## Running the phone app

Install on a device or emulator (Android 12 or newer), grant location when asked, and open the
setup wizard once. With several devices attached, pick one explicitly:

```bash
adb devices -l
ANDROID_SERIAL=emulator-5554 ./gradlew :app:installDebug
# or
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
```

`ANDROID_SERIAL` is honoured by Gradle's install tasks as it is by every adb command; without it
`installDebug` goes to **all** connected devices.

## Running the watch app

The watch app has the same `applicationId` as the phone app, so `adb install` of either replaces
the other **on the same device**; they are meant for different devices. Always address the device:

```bash
adb devices -l
ANDROID_SERIAL=emulator-5556 ./gradlew :wear:installDebug
# or
adb -s emulator-5556 install -r wear/build/outputs/apk/debug/wear-debug.apk
adb -s emulator-5556 shell am start -n io.github.jnbp.nextstop/io.github.jnbp.nextstop.wear.ui.MainActivity
```

(Emulators number their adb ports in steps of two from 5554; which one is the phone and which the
watch is up to you, check with `adb devices -l` and the model name.)

### A phone and a watch together

Features that cross the Data Layer (favourites on the watch, the phone's followed trip, map
pictures, lookups with the phone's sources) need a *paired* phone and watch with Google Play
services on both:

1. Create a phone emulator with a **Google Play** system image and a Wear OS emulator (Device
   Manager of Android Studio; a round small or large round watch is fine). A Wear OS image without
   Google Play services cannot pair.
2. Pair them with the pairing assistant of the Device Manager (it installs and drives the Wear OS
   companion on the phone), or pair a real watch with a real phone as usual.
3. Install the phone app on the phone and the watch app on the watch as above. The two sides find
   each other through the capabilities `nextstop_phone` and `nextstop_watch`; the watch settings
   page and the phone settings show whether the other side was found.
4. In the phone app favourites, saved places and recent destinations are pushed to the watch on
   start and whenever they change; the watch asks again on every resume.

A watch with no paired phone is a valid test too: it must still show nearby stops, departures, route
search and follow a trip by itself (see "Standalone" in [ARCHITECTURE.md](../ARCHITECTURE.md#9-wear-os-app)).
Test both situations when you change anything in `wear/`.

### Play specifics of the watch app

(Details and the Console steps: [play-release.md](play-release.md).)

- The watch is a separate bundle (`:wear:bundleRelease` or the signed-bundle wizard with module
  `wear`) uploaded to the **Wear OS form factor** track of the same app, with the same upload key.
- Same package name as the phone app, version code `1_000_000 + n`, a release on a test track
  before it can be published, at least one Wear OS screenshot (square, at least 384 x 384).
- Target API: Google requires API 36 for phone apps and API 35 for Wear OS since 2026-08-31; this
  repository has phone `targetSdk = 37` and watch `targetSdk = 36`, both fine. The watch stays on 36
  because the watch emulator images do not go higher yet (comment in `wear/build.gradle.kts`).
- The manifest declares `android.hardware.type.watch`. The foreground service type
  `specialUse` of the trip service needs the declaration and video in the Play Console.

## Station data: regenerate the OSM assets

The committed assets under `app/src/main/assets/` come from OpenStreetMap (Geofabrik extracts) through
the scripts in `tools/`. You only need this when the data or the rules in `tools/` change. Every
option of every script is documented in [tools/README.md](../tools/README.md); this is the short
path. Raw inputs and intermediate files are git-ignored (`tools/osm-pbf/`, `tools/osm-cache/`,
`tools/osm-stations.json`, `tools/osm-packs/`, `.extract-cache/`).

```bash
pip3 install --user osmium
mkdir -p tools/osm-pbf
```

**Berlin only** (assets `osm-berlin.json`, `osm-change-positions.ndjson`, `transfer-walks-berlin.json`):

```bash
curl -L -o tools/osm-pbf/brandenburg-latest.osm.pbf https://download.geofabrik.de/europe/germany/brandenburg-latest.osm.pbf
python3 tools/extract_osm_pbf.py tools/osm-pbf/brandenburg-latest.osm.pbf --walk-graph
python3 tools/build_station_asset.py
python3 tools/build_transfer_walks.py
```

Brandenburg's file contains Berlin. `build_station_asset.py` also prints how often the OSM boarding
positions agree with `vbb-change-positions`. (The Overpass route, `fetch_osm_stations.py`, still
works but asks public servers; prefer the file.)

**All of Germany** (the 15 state packs, the index and their walks), step by step, from one
`germany-latest.osm.pbf` (about 4.5 GB):

1. Download `https://download.geofabrik.de/europe/germany-latest.osm.pbf` to `tools/osm-pbf/`.
2. One station extract per state (each run is cached in `.extract-cache/` next to the input, so
   later runs take 1 to 1.5 minutes):
   `python3 tools/extract_osm_pbf.py --region <state> --out tools/osm-pbf/<state>-stations.json tools/osm-pbf/germany-latest.osm.pbf`
   for each state (`baden-wuerttemberg bayern berlin brandenburg bremen hamburg hessen
   mecklenburg-vorpommern niedersachsen nordrhein-westfalen rheinland-pfalz saarland sachsen
   sachsen-anhalt schleswig-holstein thueringen`).
3. One pack per state:
   `python3 tools/build_station_asset.py --source tools/osm-pbf/<state>-stations.json --region <state> --out-dir tools/osm-packs/states`.
4. The index, and install the packs into the app:
   `python3 tools/build_region_index.py` then `python3 tools/build_region_index.py --install`
   (Berlin is built in and left out of the index).
5. The walking graphs, one per state (call it again until it says "All graphs written"; about 18
   minutes on 4 cores):
   `python3 tools/walk_graph_states.py --stations-dir tools/osm-pbf --out-dir tools/osm-pbf/walk-graphs tools/osm-pbf/germany-latest.osm.pbf`
6. The walks, per state:
   `python3 tools/build_transfer_walks.py --source tools/osm-pbf/<state>-stations.json --graph tools/osm-pbf/walk-graphs/<state>.json --asset app/src/main/assets/osm-regions/osm-<state>.json --out app/src/main/assets/osm-regions/transfer-walks-<state>.json`
7. Berlin is built like a state, but it goes into the app's own asset files: run step 3 for `berlin`
   **without** `--out-dir` (writes `osm-berlin.json` and `osm-change-positions.ndjson`), and step 6 for
   `berlin` with the default `--asset` and `--out` (`osm-berlin.json`, `transfer-walks-berlin.json`) and
   `--graph tools/osm-pbf/walk-graphs/berlin.json`. The index and `--install` leave Berlin out.
8. Run the self-tests and the audits below, then commit the changed assets with a message that says
   which extract date they are from.

Build the pack and its walks from the **same** extract: walks match platforms to the pack by
their axis. Memory: the station build holds the whole extract in memory; the per-state route
exists for machines with little RAM.

## Audits: when to run them

The audits read the committed assets (and, for walks, the extracts and graphs) and print counts
and the worst examples. They are how rules are checked against real data before and after a change
(see [conventions.md](conventions.md#solve-it-for-all-of-germany-verify-against-data)).

| Script | Checks | Run when |
|---|---|---|
| `python3 tools/audit_walks.py [--regions bayern] [--paths --geojson out.geojson] [--report r.json]` | every measured change walk against the station geometry: estimates, detours, walks faster than the straight line, level floors, same-platform pairs, stale walks | you change `walk_graph*.py`, `build_transfer_walks.py`, platform rules in `build_station_asset.py` (`is_rail_platform`, `segments_meters`), or rebuild walks |
| `python3 tools/audit_stop_signs.py [--source-dir tools/osm-pbf] [--regions berlin] [--assets <folder>]` | signs of bus and tram stops far from their positions, empty dots, platforms without a position, route platforms whose position lacks the line | you change how stop positions or signs are built (`stop_sign.py` / `StopSignPlace.kt`) |
| `python3 tools/audit_sign_merges.py [--regions berlin] [--worst 20] [--report r.json]` | which bus and tram stops lose their own sign because another stands for them, stop names with qualifiers, rail stations joined too far apart | you change `streetStopJoinsStation`, `mapStops`, `mergeMapStops`, `stationGroups`, `stopNameParts` or `NOT_A_TOWN` |

Compare before and after: `--assets` takes another assets folder, for example
`git archive <commit> app/src/main/assets | tar -x -C /tmp/before` and then
`--assets /tmp/before/app/src/main/assets`. Put the before/after numbers in the commit message or
in `tools/README.md` (it has the November 2026 tables as a model).

## Debugging

- **Logcat tags** (phone and watch): `OsmStations`, `ChangePos`, `FootPaths`, `WatchMap` (the whole
  way of a watch map picture with times, on both devices), `NextStopWatchLink`, `NextStopWear` (watch).
  `adb -s <serial> logcat -s WatchMap NextStopWear OsmStations` for example. Save a log with
  `adb -s <serial> logcat -d > logcat-<name>.txt`; files named `logcat-*.txt` and `build.log` are
  git-ignored. Check a log for keys and positions before you attach it anywhere.
- Requests are logged through `RequestLog` with positions cut to a few decimals and address names left
  out; keep it that way for new requests.
- The settings page "Advanced" has the developer options (data sources and order, own server, keys).
  A source that failed shows in the hint "Daten: ..." (see `SourceHealth` in
  [ARCHITECTURE.md](../ARCHITECTURE.md#3-data-sources-and-routing)).
- Animations: to test "animations off" set *Developer options > Animator duration scale* to
  "off" (`ANIMATOR_DURATION_SCALE` 0); the app and the watch must then show everything at once.
- Background work (the disruption watch, widget updates): the Background Task Inspector of Android
  Studio shows and runs the WorkManager jobs (`disruptions-periodic`, `disruptions-check-<minutes>`).
- The map needs network for tiles (OpenFreeMap); without it the map still comes in after 6 s (see
  startup in [ARCHITECTURE.md](../ARCHITECTURE.md#8-startup-choreography)).
