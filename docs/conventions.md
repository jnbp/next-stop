# Conventions

Rules this project learned the hard way. They are short on purpose; the reason is given with each
so you can judge the edge cases. Architecture is in [ARCHITECTURE.md](../ARCHITECTURE.md), tooling in
[development.md](development.md).

## Solve it for all of Germany, verify against data

- **Never fix one city or one station.** The app serves Berlin first but ships offline data for all
  16 states and talks to providers all over Germany. A bug seen at one station is a rule that is
  wrong or missing for a class of stations. Find the class, change the rule, and do not add a special
  case keyed on a name, an id or a coordinate. If a rule really needs data per place, put the data
  in the pipeline (`tools/`) where every place gets it.
- **Check the rule against the data before and after.** The audits (`tools/audit_walks.py`,
  `tools/audit_stop_signs.py`, `tools/audit_sign_merges.py`, see [development.md](development.md#audits-when-to-run-them))
  count what a rule does over Berlin and all packs. Run the audit before the change to see how big
  the problem is and after it to see that it is gone and nothing else moved. Put the before and after
  numbers in the commit message (the tables in `tools/README.md` are the model). A change that
  fixes the example that was reported but moves hundreds of other stops is not a fix.
- **Use well-known central stations as examples** in code, tests, comments and docs (Alexanderplatz,
  Hauptbahnhof, Friedrichstraße, Zoologischer Garten, ...), never a station near anyone's home or work.
- **Generic rules over provider quirks.** Names, ids and notices differ per provider; write the
  parsing against the shape (see `splitStopTown`, `linesNamedIn`, `stopNameParts`) and test it with
  names of several providers and regions.

## Keep twin implementations alike

Some rules exist in Kotlin and in Python (or in the phone and the watch module) because one side
cannot use the other's code. Each copy says so in its comment. When you change one, change the
other in the same commit and run both tests:

| Rule | Kotlin | Twin |
|---|---|---|
| Where the sign of a bus or tram stop stands | `P data/StopSignPlace.kt` (`stopSignPoint`) | `tools/stop_sign.py` |
| Which stops keep their own sign, station grouping, stop names | `P data/OsmStations.kt`, `P ui/MapStops.kt`, `P ui/StationSigns.kt`, `P data/StopTowns.kt` | `tools/audit_sign_merges.py` (replicates them) |
| Stop names as shown (the town the device is used in left out, the part in square brackets apart) | `P data/StopTowns.kt` (`stopNameParts`, `sameTown`, `dominantTown`, `LocalTown`) | `W data/StopNames.kt` (`WatchTown`) |
| Departures board rules, line names | `P data/DepartureBoard.kt`, `P data/Models.kt` | `W data/DepartureBoard.kt` |
| Line colours | `P ui/Components.kt` (`lineColor`), `P data/LineColors.kt` | `W data/LineColors.kt` |
| Data Layer paths and keys | `P wear/PhoneBridge.kt` | `W data/Protocol.kt` |
| Settings of the watch app edited on both sides (keys, merge by time) | `P wear/WatchSettingsSync.kt` | `W data/WatchSettingsSync.kt` |
| Change levels by walking pace | `P data/WalkPace.kt`, `P data/Models.kt` | `ChangeRating.estimate` in `W data/Protocol.kt` (a rough version) |
| Order of the connections of a search | `P data/SearchOrder.kt` (`searchOrder`) | `searchOrder` in `W data/TransitRouter.kt` |

(`P` = `app/src/main/java/io/github/jnbp/nextstop/`, `W` = `wear/src/main/java/io/github/jnbp/nextstop/wear/`.)

Where a thing is **drawn** in several places, draw it once and share the function: the marks of a
walk exist once in `P ui/WalkMarks.kt` and serve the small map, the big map and the watch's map pictures.

## Animation

- Everything that appears, disappears or changes is animated with the app's own motion
  (`Motion` in `P ui/Motion.kt`: `settle`, `pop`, `quick`, `fadeIn`, `fadeOut`; `Delight.kt` for
  presses, rolling numbers, list entrances). Things settle or fade in; they do not
  pop into existence.
- **Every animation must respect "animations off".** Read the setting with `rememberAnimationsOn()`
  (`P ui/Delight.kt`; the watch has `rememberWatchAnimationsOn()` in `W ui/Motion.kt`) and with it
  off show the final state at once (`snapTo`, `snap()`, no infinite transitions). Looping loaders
  stand still. New code that starts a transition without checking it is a bug.
- The start of the app is a sequence with fixed steps; add a new part through `IntroStep` and
  `awaitIntroStep`, do not invent a second timing (see [ARCHITECTURE.md](../ARCHITECTURE.md#8-startup-choreography)).
- Per-frame work must stay cheap: no recomposition per frame (draw in `graphicsLayer`/`drawWithContent`
  or update a MapLibre source), no allocation in a frame loop where a cell index will do (see
  `VehicleClusters.kt`, which compares only neighbours in cells, and `shimmer` in `Motion.kt`, which only draws).
- Nothing animates in watch ambient mode.

## Kotlin names

- **No private top-level function or constant that has the name of a public one in the same
  package.** Kotlin resolves it to the private copy inside its file and to the public one elsewhere,
  or reports an ambiguity: this has broken the build (for example a private `segmentMeters` beside
  the shared one, fixed in `WalkDetails.kt`). Before adding a top-level helper to `data/` or `ui/`,
  search the package: `grep -rn "fun yourName\b" app/src/main/java`. Prefer reusing the existing
  one; if you need a variant, give it a different name.
- Add helpers where they belong (`data/` for logic, `ui/` for composables and drawing) rather than
  a private copy per file.
- The names stored on disk or sent over the wire (setting keys, `DataSource.key`, `Backend.key`,
  palette names, Data Layer paths, JSON keys) **must stay as they are**; their comments say so.
  Add new ones, migrate old ones, never rename in place.
- Keep logic that needs a test free of Android classes so it runs as a JVM test (see the many
  `*Test.kt` files for the pattern: a pure function in `data/` or `ui/`, a test beside it).

## Strings and language

- **Every user-visible string exists in English and German**, in the same file name in
  `res/values/` and `res/values-de/` (`strings.xml`, `strings_set.xml`, ... the watch has its own
  `wear/src/main/res/values{,-de}/strings.xml`). English is the default.
- **No duplicates**: resource names are unique across all string files of a module (a clash is a
  build error), and do not add a second string with the text of an existing one: reuse it. Name by
  area (`walk_sum_exit`, `map_entrance_named`, `disruption_notification_title`).
- Texts are written for people: short, plain, no jargon or internal names. German uses the
  informal "du" of the existing texts; copy the register of its neighbours.
- Check parity and duplicates before you commit:

```bash
for m in app wear; do python3 - "$m/src/main/res" <<'PY'
import sys, glob, re, collections
d = sys.argv[1]
def names(folder):
    c = collections.Counter()
    for f in glob.glob(f"{d}/{folder}/strings*.xml"):
        for m in re.finditer(r'<(?:string|plurals|string-array)\s+name="([^"]+)"(?![^>]*translatable="false")', open(f).read()):
            c[m.group(1)] += 1
    return c
en, de = names("values"), names("values-de")
print(d, "duplicates:", [k for k, v in {**en}.items() if v > 1] + [k for k, v in de.items() if v > 1],
      "only in en:", sorted(set(en) - set(de)), "only in de:", sorted(set(de) - set(en)))
PY
done
```

- Providers are asked in the app's language through `AppLanguage` (never `Locale.getDefault()`).
  Code that needs the language of the texts uses it.

## Code, comments, commits

- **Everything in the repository is English**: code, comments, KDoc, commit messages, docs, issue
  text. Only the German UI strings (`values-de/`) and quoted German product names are German.
- **Comment style** (read a few files to get it: `P data/StopTowns.kt`, `P ui/Motion.kt`,
  `P data/WalkRoute.kt`):
  - A block comment `/* ... */` at the top of a file after the imports says what the file owns and
    the rule it implements, in plain sentences, with the numbers and why they are what they are.
  - KDoc on every public declaration in plain language, explaining *why* and the behaviour at the
    edges, not repeating the name. Refer to other things with `[Name]` and `(see ...)`.
  - A named `const` for every number, with a comment that gives its unit and reason
    (`GROUP_RADIUS_METERS`, `MAP_SHOW_LATEST`), not a bare literal.
  - `//` comments say why a line is there, especially for things that look wrong but are not
    ("A stop position without a platform stays a position").
  - "UNVERIFIED" is written in the KDoc of code written without being able to test it
    (`VbbHafasSource`); remove it when it was checked.
  - German words appear in comments only as quoted UI terms ("Langsamer unterwegs").
- **Commits**: one idea per commit, a short imperative subject in English that says what changed for
  the user or the code, a body for the why and for the audit numbers. The commit identity is the
  project's noreply identity (`git -c user.name=jnbp -c user.email=jnbp@users.noreply.github.com commit`):
  never a private address in a commit. Commits written with an AI assistant carry the trailers
  the assistant's session prints (`Co-Authored-By: ...`, `Claude-Session: ...`) at the end of the message.
- Work on a short-lived branch; branches are merged into the main branch with a merge commit named
  `Merge <branch>: <summary>`.
- Do not leave debugging output, `build.log`, `logcat-*.txt` or local `.properties` in a commit.

## Privacy and security

- **No tracking, no analytics, no ads, no crash reporter, no accounts.** The app talks to public
  timetable APIs, OpenFreeMap tiles, Photon and the OSRM foot server, nothing of its own. Adding a
  network call to a new host means updating the README privacy section and the Play Console answers
  ([play-console-answers.md](play-console-answers.md)).
- **Keys never go in the repository, in logs, in issues or in screenshots.** `secrets.properties` is
  git-ignored; log at most whether a key is set (see `SourceChoice.toString`), never its value. A key a
  user enters stays on the device (`source_keys`, excluded from backup). See [keys.md](keys.md).
- **Personal data stays on the device.** Contacts, calendar entries and positions are read on demand,
  held only while needed, never stored in the log or sent anywhere except as a place search query or
  to the user's own paired watch. A request that is logged goes through `RequestLog` (positions cut,
  address names left out).
- **No personal data in the repository**: no names, e-mail addresses, phone numbers or home or work
  addresses in code, test data, fixtures, docs or screenshots, and no example near anyone's home.
- Permissions are the minimum and are asked for when needed. There is **no background location**;
  features that seem to need it (widgets) work from the last known position
  (`P widget/WidgetPosition.kt`).
- New user data outside SharedPreferences needs a backup decision: an `<include>` or `<exclude>` in
  both `data_extraction_rules.xml` and `backup_rules.xml`.

## Data and attribution

- OpenStreetMap-derived data is ODbL 1.0: keep the attribution in the app and in the README when you
  add or change a use of it. Transitous may only be used by free, open-source, non-commercial apps
  that name themselves in the User-Agent (`TransitousSource.userAgent`) and credit its data sources.
- The app credits everything in *Settings › About Next Stop*: a new library in
  `gradle/libs.versions.toml` needs an entry in `P data/ThirdPartyNotices.kt` (or in
  `NOT_SHIPPED_ALIASES` for tests and debug tools; `ThirdPartyNoticesTest` fails otherwise), and a
  new data source an entry with its attribution in the data sources sheet (`P ui/AboutUi.kt`),
  besides the README table and NOTICE.
- The public community services (BVG REST API, Transitous, OSRM foot, Photon, OpenFreeMap) have
  fair-use limits: cache, debounce, and ask for what is on screen only (`FootPaths` is the model: one
  request at a time, at most one per second, cached).
- A new data source goes behind `TransitSource`, is added as a `Backend` with a stable `key` and
  `tag`, and fails like the others (`SourceUnreachableException`, `NotConfiguredException`,
  `NotSupportedException`) so the chain can skip it.

## Before you commit

1. `./gradlew test` (and the web and Python tests if you touched `web/` or `tools/`).
2. The string check above if you touched strings.
3. The audit before/after if you touched a rule that the audits cover.
4. A debug build runs; for map, walk or watch changes look at it on a device or emulator in light
   and dark, with animations on and off, and (watch) with and without the phone.
5. Docs: update [ARCHITECTURE.md](../ARCHITECTURE.md) when you add a layer, a protocol key, a
   setting that crosses modules or a rule that other code must follow.
