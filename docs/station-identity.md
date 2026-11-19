# Station identity

Issues #96 and #85: Transitous (MOTIS v6) merges the timetables of many railways. One physical
station shows up as several stops, one per feed, each with its own id, name and position. This
document describes the one layer that decides "are these two stops the same station?" and which
stop stands for it: `data/StationIdentity.kt`. It replaces the first fix of #96
(`data/ForeignTwins.kt`, a list of foreign town names such as "Francfort" for Frankfurt).

## What the server really sends

Answers recorded from api.transitous.org on 18 Nov 2026 (`app/src/test/resources/transitous/
stations-20261118.json`, generated into `RealTransitousData.kt` for the tests) show:

| Station | Stops (feed tag, id after `_`, name) |
|---|---|
| Frankfurt (Main) Hbf | `de-DELFI_de:06412:10_G`, `de-DELFI_de:06412:10:6:31` "Frankfurt (Main) Hauptbahnhof"; `de-DELFI_de:06412:7010` "… Hauptbahnhof tief" (another DHID); `at-Railway-Current-Reference-Data-2026_de:06412:10:16:16` "Frankfurt (Main) Hbf"; `fr-horaires-sncf_FR::LMO:<uuid>:` "Francfort sur le Main", 117 m away, only the ICE/TGV to France |
| Frankfurt Flughafen Fernbf | `de-DELFI_de:06412:11511`, `at-Railway…_de:06412:11511:1:1` "Frankfurt Flughafen Fernb", `nl-OpenOV_2992809` "Frankfurt Flughafen Fernb" |
| Saarbrücken Hbf | `de-DELFI_de:10041:8000323_G_G` "Hauptbahnhof, Saarbrücken" (town behind!), `fr-horaires-sncf_…` "Sarrebruck" (2 m) |
| München Hbf | `at-Railway…_de:09162:100:11:11`, `fr-horaires-sncf_…` "Munich" (93 m); Karlsplatz `de-DELFI_de:09162:1_G` 600 m away |
| Basel SBB | `ch-opentransportdataswiss26_Parentch:1:sloid:10`, `de-amarillo-bw_ch:1:sloid:10`; Basel Bad Bf `de-DELFI_ch:1:sloid:90` (2.7 km, another station) |
| Brussel-Zuid / Bruxelles-Midi | `be-sncb_S8814001`, `eu-european-sleeper_8814001`, `nl-OpenOV_stoparea:482359` "Brussel-Zuid" (138 m), bus stops of be-tec/be-delijn |
| Amsterdam Centraal | `de-DELFI_000008400058` (zero-padded UIC), many `nl-OpenOV_29921xx` "Amsterdam Centraal" |
| Aachen Hbf | `be-sncb_8015345` "AACHEN Hauptbahnhof", `de-DELFI_de:05334:1008` "Aachen, Hbf" |
| Salzburg Hbf | `at-Railway…_Pat:45:50002`, `at-PTA-Salzburg-Flex-2026_at:45:50002:0:20` |
| Strasbourg | `de-DELFI_fr:24067:1284`, `fr-horaires-sncf_FR::LMU:<uuid>:` |
| Berlin (second batch) | VBB and DELFI copies share the VBB number (`de-VBB_de:11000:900100001:3:55`, `de-DELFI_de:11000:900100001`); at-Railway writes parents as `Pde:11000:900058101`; Polish Trains `pl-Polish-Trains_140168` "Berlin Ostkreuz" (7 m, no shared id); bus stops of the station square have own VBB numbers ("S Südkreuz Bhf/Ostseite" 900058100) |
| Praha hl.n. | `at-Railway…_cz:0:5457076:0:1`, `ch-opentransportdataswiss27_Parent5457076`, `dk-rejseplanen_000005457076` (2.5 km off!) |
| København H | `de-DELFI_000008601309`, `nl-OpenOV_4177268` "København Hovedb.", the metro `dk-rejseplanen_000008603330` "København H (Metro)" |

Consequences:

* A MOTIS stop id is `<feed tag>_<feed's own id>`; the app keeps Transitous ids with `tr:` in front.
* Hard ids recur across feeds (DHID/IFOPT, Swiss sloid, UIC codes), with decorations: platform
  parts (`:16:16`), `_G` group marks, a `P`/`Parent` prefix, an `S` prefix, zero padding.
* The SNCF's ids hold no shared code at all: its stops are found only by place and name.
* MOTIS itself links stops of one name across feeds for boards (the Strasbourg board of the SNCF
  stop carries DELFI's departures too), but not under different names: the board of Frankfurt
  Hbf has none of "Francfort sur le Main" and vice versa.
* The same train comes twice: ICE 9591 at Strasbourg from DELFI towards "Frankfurt (Main)
  Hauptbahnhof" and from the SNCF towards "Francfort sur le Main"; TGV 9218 at Basel from SBB and
  SNCF. DELFI may name it by its line ("ICE 83" from route "83", trip "ICE 9573"): fixed in
  `composeLineName`, the train number wins.
* Journey search from a stop id already reaches the twin by a short footpath (Frankfurt → Paris:
  "walk 3 min, ICE 9552 from Francfort sur le Main"); a journey may even ride one train in two
  feeds with a "change" between them (ICE 9552 DELFI to Mannheim, ICE 9552 SNCF on).

## Evidence levels (`StationIdentity.evidence`)

1. **HardId** – within `HARD_ID_METERS` (3 km; Rejseplanen's Praha hl.n. lies 2.5 km off): the
   same stop place (IFOPT/DHID cut to country:area:place, `_G` and `P`/`Parent` prefixes removed,
   Swiss `ch:1:sloid:<n>`), the same VBB number, or the same UIC code (7 digits; `S` prefix and
   zero padding allowed; zero-padded only in German feeds, whose own ids are numbers too; country
   code must be a European railway's; a stop place numbered by its UIC code, `cz:0:5457076`,
   `de:14713:8010205`, has that code too).
   Only equality counts: some feeds number stops with seven digits that are not UIC codes.
2. **Complex** – both rail stops within `TWIN_METERS` (250 m) in the same OpenStreetMap station
   complex (optional `complexOf`, as `stationGroups` already passes it).
3. **Name** – both rail stops within 250 m whose names agree after normalisation (below), in any
   word order, abbreviations of four letters or more reading as the word they begin. Between two
   feeds one name may in addition carry the town that the other leaves out, if that word is the
   town: in the other name's brackets ("S Ostkreuz Bhf (Berlin)" / "Berlin Ostkreuz") or in front
   of the longer name; a street behind it never ("Frankfurt am Main, Hauptbahnhof/Karlstraße").
4. **Exonym** – stops of feeds of *two countries*, both with known rail products, within
   `EXONYM_METERS` (150 m), whose names differ only in the town (one or two words each) and in
   nothing that tells stations of a town apart, and never when both stops carry stop places
   (DHID/VBB) that differ: feeds that both know the local id know better (at-Railway's
   "Berlin-Spandau" and the VBB's "S+U Rathaus Spandau", 190 m). Decided by geography, not by a word list: two
   railways of two countries both naming the plain station of a town at the same spot are naming
   the same town. "Offenbach Ost", "Karlsplatz", "Midi" vs "Central" never pass.

Normalisation: lower case, ß→ss, accents removed (NFD), brackets dropped ("(Main)", "(U, Tram)"),
river/region tails dropped ("am Main", "sur le Main", "upon …", "nad …"), station words of many
languages dropped (Hbf, Hauptbahnhof, Bahnhof, Bf, S/U, tief, Gare, Centraal, Centrale, Central,
Station, Stazione, Estación, Dworzec, Główny, hlavní nádraží, pályaudvar, …), articles dropped,
compass words unified across languages (Süd/Zuid/Midi/South → one token).
Qualifiers that tell stations apart (Messe, Deutz, Flughafen, Fernbahnhof, Regionalbahnhof, Dom,
Tor, Bad, Platz/Str., the compass words, …) block the town-only rules.

Bus, tram and coach stops are never a rail station's identity twin (they may still share its
map sign through `stationGroups`' street-stop rules): a bus stop named after the station
("Hauptbahnhof/Karlstraße", "Basel SBB Bus Terminal", "Munich Bus Station") is a place to walk to.

## Which stop stands for a station (`preferredIndex`)

The station's country comes from its members' hard ids (DHID `de:`, UIC country code). The stop
of a feed of that country wins (DELFI for Germany, SBB for Switzerland …), then the one with the
most kinds of trains. The display name then goes through the existing DB-style naming
(`railwayStationName`) as before.

## Where it is used

| Decision | Place | Now |
|---|---|---|
| Map: one sign per station | `ui/StationSigns.kt` `stationGroups` | stops of two feeds join by `otherFeedTwins`; main stop by station country, not by exonym list |
| Search: one row per station | `data/RoutingSource.kt` `mergeStops` → `localStationFirst` | preferred stop by `preferredIndex` |
| Board opened from the search | `RoutingSource.foreignTwinMembers` → `foreignTwinsOf` | any feed's twin, any language |
| Board: one train from two feeds | `DepartureBoard.oneRide` / `RoutingSource.sameRide` → `sameTrainRun` | long-distance, same train number, ±2 min, directions that may name one place |
| Journey endpoints | `RoutingSource.placeFor` → `stationEndpoint` | a foreign feed's member of a known complex is replaced by the preferred stop |
| Change at one station vs walk | `Models.sameStation` (used by `changeKind`, `sameVehicle`, ShortcutTruth, SameLineChange, FasterRoute, SearchOrder) | falls back to `StationIdentity.same` |
| Walk inside one station, replacement runs | `Models` walk cap, `sameStopComplex` → `sameStopPlace` | by stop place; `normalStationId` alone took the platform's last number ("…:3:4") for the station |
| Journey dedup of one train from two feeds | `SearchOrder.withoutSameRunTwins` | already by train number and ≤400 m (unchanged) |
| Stored keys | `StationIdentity.stationKey`, `noteStored`, `resolveId` | `ifopt:` / `uic:` / `vbb:` key stored beside the id, see below |

## Cost

`facts()` is computed once per stop (id, name, product count) and cached (cleared above 8192
entries). `evidence()` first compares positions (rejects everything farther than 1.5 km before
touching names), then cached hard ids, then token lists of a few words. No per-frame work beyond
what `stationGroups` already did per pair.

## Stored keys and split trains

* Favourites, saved places/routes/groups, planned trips (kept journeys), the app's own links
  (`nextstop://open/route`, `/departures`: `fromKey`, `toKey`, `key`) and the places sent to the
  watch store the station key beside the id; old data without it reads as before. On load the
  pair is noted (`noteStored`); a stored id whose feed has answered nothing this session while a
  stop of its key has (a feed renamed by its year, `…-2026` → `…-2027`) is asked for by that
  stop's id (`resolveId`, in `departures` and journey endpoints). Before any answer of the
  session, the stored id is used as it is. The live links for the web page carry no key (the app
  never reads them back).
* A journey that rides one train in two feeds is one ride (`isFeedSplit` in `mergeThroughRuns`:
  same train run, one station, ≤10 min, the walk between the twins dropped).
## Known gaps

* Two stops of one station from two feeds of the *same* country with different names and no
  shared id stay apart unless the map's OSM complex joins them.
* Exonyms farther apart than 150 m (none in the recorded data) are not joined.
* A feed that lists rail modes for a coach stop (al-ShowMeBus "Zürich Hauptbahnhof",
  COACH + REGIONAL_RAIL) is taken at its word: it joins Zürich HB by name.
* Stops without any modes (Polish Trains, ride-sharing feeds) count as possible rail stops, as
  in `stationGroups` before; names alone then decide.
