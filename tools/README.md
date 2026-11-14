# Tools

Fetch the U-Bahn and S-Bahn stations of Berlin with their platforms, steps, elevators and
line routes from OpenStreetMap (writes `tools/osm-stations.json`, caches raw answers in
`tools/osm-cache/`):

    python3 tools/fetch_osm_stations.py

Berlin is asked in nine tiles and the stations in batches of five, from four public Overpass
servers in turn, with growing pauses (and the Retry-After of a server) when they are busy.
A run that stops keeps what it loaded: run it again later and it goes on from there.

It also fetches the bus and tram route relations (line ref, name, colour and their stop and
platform members), so the build knows which lines depart at which stop position. To fetch
only those and merge them into an existing `tools/osm-stations.json`:

    python3 tools/fetch_osm_stations.py --only bus-routes

Data (c) OpenStreetMap contributors, available under the Open Database License (ODbL 1.0):
https://www.openstreetmap.org/copyright

Build the assets of the app from that extract (standard library only):

    python3 tools/build_station_asset.py

It writes `app/src/main/assets/osm-berlin.json` (station complexes with VBB ids, platform
axes, entrances, elevators and escalators; bus and tram stop positions with their "Pos."
number; every bus and tram stop area with all its platforms, stop positions standing in
where a platform is missing, and the lines that stop at each; see "Platforms and signs of bus
and tram stops" below) and `app/src/main/assets/osm-change-positions.ndjson` (boarding positions for the
changes between U-Bahn and S-Bahn lines, in the format of vbb-change-positions plus
`"source":"osm"` and `"confidence"`). It checks the change positions against
vbb-change-positions and prints how often they agree. `tools/osm-stations.json` and
`tools/osm-cache/` are not committed.

Entrances: only those of the stations are kept (transit tags, stop-area membership, the
station's name, or a door of the station building or on its mapped steps and footways; not
the doors of shops and houses around it). Each goes to the station it leads to (its stop
area, else the shortest walk over the station's steps, footways and elevators to the
platforms), and its sign "m" is what its tags say or else the modes of the platforms it
reaches within a short walk. Doors of malls, hotels, shops and houses are kept as
accesses through a building ("k":"b", with "oh" for tagged opening hours) when they lead into
the station: a member of its stop area, in the outline of a station building, or on its
mapped steps, footways, corridors or elevators (within 3 m, 8 m below ground) with a way on
to a platform of at most 200 m (or, below ground, on that network at all). A plain door of that kind
(door tags only, no shop, address or name) that is mapped on a level of the station and lies
within 30 m of one of its platforms is the station's own door and stays an entrance (the door
at the south end of Südkreuz, level 1). The build prints
them, what it dropped and any entrance far from the platforms of its sign. Station products
are "U", "S" (both for one station node of both, like Wuhletal) and, from the "modes" of the
PBF extract, "R" for stations of regional trains only. Names lose a leading "S "/"U "/"S+U ", and a complex of several station nodes the bracket that
names one part of it ("Südkreuz (Ringbahn)" becomes "Südkreuz").
Whether a door lies in a station building comes from the fetch ("stationBuilding"); an
extract from before that falls back to the walking network.

## From a downloaded OpenStreetMap file instead of Overpass

`tools/extract_osm_pbf.py` writes the same `tools/osm-stations.json` from local `.osm.pbf`
files of Geofabrik, without asking any public server, so `build_station_asset.py` runs on it
unchanged. It needs pyosmium 4 or newer. On the Mac (Python 3.9 of the command line tools):

    pip3 install --user osmium
    mkdir -p tools/osm-pbf
    curl -L -o tools/osm-pbf/brandenburg-latest.osm.pbf https://download.geofabrik.de/europe/germany/brandenburg-latest.osm.pbf
    python3 tools/extract_osm_pbf.py tools/osm-pbf/brandenburg-latest.osm.pbf
    python3 tools/build_station_asset.py

The default region is the box around Berlin that the Overpass fetch covers (it reaches into
Brandenburg). Geofabrik's Brandenburg file contains Berlin, so it is all that is needed (about
20 s); several files may be given all the same (objects in more than one are taken once). Other regions:
`--region brandenburg|hamburg|munich|germany` or `--bbox south,west,north,east`, e.g. with
`germany-latest.osm.pbf` (about 4.5 GB; no big temporary file: only the nodes that are needed are located). The previous output is kept as `tools/osm-stations.json.bak`;
`--out` writes elsewhere. `tools/osm-pbf/` is not committed.

Unlike the Overpass fetch it takes every rail station of the region, however it is mapped
(railway=station|halt or public_transport=station, as node, way or multipolygon; with
station=subway|light_rail|train, subway/light_rail/train=yes or no station tag), and gives
each a `"modes"` list from its tags and the rail routes of its stop areas; a station drawn
twice (a node and an area) is taken once. `--modes subway,light_rail` keeps only U-Bahn and
S-Bahn stations. Not taken: park railways (station=miniature, even with train=yes) and
stations of trains alone with no platform and no stop position near them (operating points
and junctions mapped as railway=station). Besides the doors the Overpass fetch takes (with a
name, ref, wheelchair or level tag, near a station), it keeps every door the steps, footways,
corridors or elevators of a station pass through, whatever its tags and distance, marked
`"viaNetwork": true`: the build keeps those only as accesses through a building (a mall,
hotel or passage on the way) when the network leads on to a platform, or as station doors
when they lie in a station building. Self-test on a small synthetic file (also compares the keys with an
existing `tools/osm-stations.json`):

    python3 tools/test_extract_osm_pbf.py

## Exact change walks (transfer-walks-berlin.json)

How long a change takes on foot is measured over the walking network of OpenStreetMap for
every station complex, so the app does not have to guess from straight lines:

    python3 tools/walk_graph.py tools/osm-pbf/brandenburg-latest.osm.pbf   # or extract_osm_pbf.py ... --walk-graph
    python3 tools/build_station_asset.py
    python3 tools/build_transfer_walks.py [--report /tmp/walks.json]
    python3 tools/test_build_transfer_walks.py

`tools/walk_graph.py` keeps every way a person may walk on (footways, paths, steps, escalators,
elevators, corridors, pedestrian and indoor areas, streets as stand-ins for their sidewalks,
rail and bus platforms, platforms drawn as multipolygons) with a node within 300 m of a
station's box (its node and rail platforms), with their level, step_count and whether they
are areas, plus the elevator nodes and doors. It writes `tools/osm-walk-graph.json` (Berlin
box: about 400,000 nodes, 146,000 ways, 14 MB; not committed) in three cached steps beside the
input file: the walkable ways without locations, the locations of a few probe nodes per way
to pick those near a station, and the locations of all nodes of those (Brandenburg file:
about 40 s, then seconds from the cache).

`tools/build_transfer_walks.py` builds a graph from it (areas are crossed in a straight line;
an elevator node joins its ways only through the lift, 60 s; loose ends join the area they
end in or the nearest node within 6 m on the same level) and runs Dijkstra from every rail
platform of a complex (any point of it) to every other platform and to every bus and tram
stop position of osm-berlin.json within 250 m, and between two such stops at least 60 m
apart; once as it is and once without steps and escalators (step-free). Pace 75 m/min; steps
+1 s per step (step_count, else 20 per level they join, else one per 30 cm up to 25),
escalators 0.75 m/s but at least 15 s per level, at least 30 s per level between two levels
however the network joins them. Where the network does not join two places, or its way is
more than three times the estimate and 2 minutes longer (a gap in the mapping), the straight
line x 1.3 stands in and is marked as an estimate; the app does not use estimates. Platforms
are taken from the same extract and matched to the asset by their axis, so build both
assets from one `tools/osm-stations.json`.

The asset `app/src/main/assets/transfer-walks-berlin.json` holds per complex its first id and
name, the platform axes ("p", as in osm-berlin.json), the stop positions ("s") and the walks
("w": `[i, j, seconds, stepFreeSeconds or 0, estimate]`, platforms first, then stops, each
pair once). Berlin: 321 complexes, 4,509 platform walks (4,183 over the network, 3,663 of them
with a step-free way, 326 estimates) and 8,831 walks between stops; 306 KB (95 KB gzip).

### How the app uses the walks (transfer levels by walking pace)

The seconds of the asset are at the usual pace (75 m/min, stairs, escalators and lifts as
above). The app (`data/WalkPace.kt`, `paceTightness` in `data/Models.kt`) rates a change of
`gap` minutes with a walk of `s` seconds:

- need at the usual pace: `ceil((s + 30) / 60)` minutes, at least 2 (1 on one platform);
  relaxed from 5 minutes beyond it, normal from 2;
- knapp: the change works at a brisk pace, `gap >= ceil(s x 0.8 / 60)` and `gap >= 2`;
- Sprint: anything else (running or luck only; also every change of 0 or 1 minute).

"Langsamer unterwegs" (luggage, a pram, a walking aid) makes every walk `s x 1.3` at every
level and asks the providers for `walkingSpeed=slow`; the searches of "Jede Minute zählt" ask
them for `fast`. Without a measured walk the rules of thumb decide (3 minutes to another
platform, 5 at a large rail station, a provider walk and one minute more).

To check a walk, `tools/walk_path.py` prints the way the builder finds as GeoJSON with its
seconds, metres and levels (`"<complex>:<platform>:<platform or s<stop>>"`, indices as in
osm-berlin.json and "s" of transfer-walks-berlin.json):

    python3 tools/walk_path.py "Yorckstraße:1:2" "Südkreuz:1:s4" --geojson /tmp/walks.geojson

Checked in November 2026 (asset of 2026-11-13; seconds, metres, levels):

| change | s | m | levels | note |
|---|---|---|---|---|
| Yorckstraße U7 (p1) -> S1 (p2) | 184 | 155 | -2 .. 1 | west exit of the U7, along Yorckstraße, up to the north end of the S1 platform |
| Yorckstraße S2 (p0) -> S1 (p2) | 292 | 314 | 1, 0 | |
| Friedrichstraße S1/S2 (p3) -> U6 (p4) | 107 | 98 | -2, -1 | |
| Friedrichstraße S3/5/7/9 (p2) -> U6 (p4) | 150 | 118 | 3 .. -2 | the graph gives 143 s; the floor of 30 s per level (5 levels) decides |
| Friedrichstraße S3/5/7/9 (p2) -> S1/S2 (p3) | 150 | 28 | 3 .. -2 | the graph gives 101 s over 28 m (an untagged flight); the floor decides |
| Alexanderplatz U2 (p5) -> U8 (p2) | 140 | 124 | -1 .. -3 | |
| Südkreuz S2 (p1) -> bus Ostseite (s3 / s4) | 265 / 238 | 305 / 267 | 0, 1 | via the concourse to Hildegard-Knef-Platz |

The router does not over-penalise stairs: where a flight has neither `step_count` nor a level
range it counts at most 25 steps, so deep level changes come out short and the floor of 30 s
per level takes over (Friedrichstraße). No passage is missing on these ways.

### Audit of all walks (tools/audit_walks.py)

`tools/audit_walks.py` checks every walk of Berlin and the 15 packs against the geometry of the
station asset it belongs to and prints counts per region and the top outliers:

    python3 tools/audit_walks.py --report /tmp/audit.json --geojson /tmp/audit.geojson [--top 30]
    python3 tools/audit_walks.py --regions bayern --paths --geojson /tmp/by.geojson   # with the ways found

Flags: `estimate` (no way over the network, or one more than 3 x the estimate and 2 minutes
longer: a missing passage), `detour` (over 2.5 x the straight line at 75 m/min and at least a
minute more than it, beyond the level floor), `faster` (shorter than the straight line: a
platform or stop matched to the wrong place), `floor` (the 30 s per level decide, 2 levels or
more), `same-platform` (two touching platforms of one track number with a walk over a minute),
`lines` (with `--lines`: platform lines of the asset sharing none with the element's
route_ref). `--paths` (reads the extract and the walking network of each region, as
`build_transfer_walks.py`) puts the way the builder finds into the GeoJSON, else a straight
line; one region with a large graph per call keeps it within a few minutes.

What it found and what changed in the pipeline (November 2026):

- Two platforms that cross on two levels (Westkreuz, Köln Messe/Deutz, Ludwigshafen Hbf,
  Doberlug-Kirchhain) were as far apart as their nearest ends (`segments_meters` ignored the
  crossing), so their estimates and the detour limit were too long: crossing axes are 0 m apart.
- Bus bays mapped `highway=platform` without `bus=yes` (Tuttlingen), stop area relations
  carrying `railway=platform` (Weinheim (Bergstr) Hbf) and platforms not in use
  (`railway=construction`, `public_transport=no`, `disused:railway=platform`, ...) were rail
  platforms of the asset with walks of their own: `build_station_asset.is_rail_platform` leaves
  them out (648 of 14,205 platforms of the packs).
- 30 complexes of two packs had walks of an older asset: all walks were rebuilt with the packs.
- route_ref on rail platforms is rare (9 of 2,314 platforms in Bavaria carry it with lines of
  the asset), and none disagreed.

All regions, before -> after: 193,133 -> 191,233 walks, estimates 5,450 -> 4,633, faster than
the straight line 199 -> 135, platform walks 13,102 -> 11,078, complexes with stale walks
30 -> 0; detours 17,926 -> 18,062 (more stop positions). Still open: the top detours are walks
of 13 to 15 minutes (950 to 1,100 m over the network) between a platform or stop and a stop
220 to 390 m away (Zweibrücken Hbf, Weischlitz, Friedberg (Hess), Holzminden, Bonn-Beuel),
just under the search limit of 15 minutes and the 3 x estimate limit; whether the mapping lacks
a crossing there needs a look at the map. Since the platforms of bus and tram stops became
their positions (November 2026), Berlin's asset and walks are built like the packs, from
`tools/osm-pbf/berlin-stations.json` (`extract_osm_pbf.py --region berlin`) and
`tools/osm-pbf/walk-graphs/berlin.json`: 321 complexes, 3,839 walks, 6,085 between stops.

### Change walks of the state packs (osm-regions/transfer-walks-<state>.json)

The walk graph of all of Germany does not fit the memory of a small machine in one run of
`walk_graph.py` (some 25 million walkable ways are held before they are picked).
`tools/walk_graph_states.py` builds one graph per state from `germany-latest.osm.pbf` instead,
matched to the extract each state pack was built from (`tools/osm-pbf/<state>-stations.json`,
see below), in steps of a few minutes each that are kept under `<out-dir>/work` (call it again
until it says "All graphs written"):

    python3 tools/walk_graph_states.py --stations-dir tools/osm-pbf --out-dir tools/osm-pbf/walk-graphs \
        tools/osm-pbf/germany-latest.osm.pbf
    for s in baden-wuerttemberg bayern brandenburg bremen hamburg hessen mecklenburg-vorpommern \
             niedersachsen nordrhein-westfalen rheinland-pfalz saarland sachsen sachsen-anhalt \
             schleswig-holstein thueringen; do
      python3 tools/build_transfer_walks.py --source tools/osm-pbf/$s-stations.json \
          --graph tools/osm-pbf/walk-graphs/$s.json --asset app/src/main/assets/osm-regions/osm-$s.json \
          --out app/src/main/assets/osm-regions/transfer-walks-$s.json
    done

It cuts the file into chunks of 32 MB of blocks (each read on its own with the header block in
front, 146 for Germany), keeps the locations of the nodes near any station's box (a Python
test per node, 4 processes; 65 million of the nodes of Germany, 1.5 GB of numpy arrays on
disk, in id order as the file is), then reads the ways chunk by chunk and keeps the runs of
nodes near a station of each state, and writes one graph per state (Bavaria: 1.5 million
nodes, 457,000 ways, 52 MB). About 18 minutes on 4 cores, in calls of under 3 minutes.
For Berlin it gives the graph of `walk_graph.py` but for the end node of two ways that leave
the box (a node more than 250 m beyond it is not located). `build_transfer_walks.py` then
takes up to 3.4 GB for Bavaria (the 140 MB extract in memory) and 1 to 40 s per state.

Germany (Nov 2026): 7,655 complexes in 15 files of 4.5 MB (1.45 MB gzip) besides Berlin;
every complex of a pack with two or more platforms has its walks. Berlin keeps its asset
`transfer-walks-berlin.json` beside `osm-berlin.json` (built from `tools/osm-stations.json`).
The app reads the walks of a pack when it loads the pack (`OsmStations.ensureRegions`) and
joins them to those loaded before (`TransferWalkIndex.plus`; where two packs know a complex,
the one loaded first wins unless only the other has its platforms).

The app (`OsmStations.transferWalkSeconds`, `exactWalkNeed` in Models.kt) finds the platforms
of a ride by its line and platform number (the longest walk where several fit) and the stop
position of a bus or tram, and then needs the walk and half a minute more, rounded up, at
least 2 minutes (1 on the same platform), before the rules of thumb; a provider's walk counts
no more than the measured one. With an accessibility setting the step-free seconds count.

## Other regions and all of Germany

`build_station_asset.py` builds any region of an extract; Berlin stays the default and keeps
its file names (`app/src/main/assets/osm-berlin.json`, `osm-change-positions.ndjson`):

    python3 tools/build_station_asset.py                                  # Berlin, as before
    python3 tools/build_station_asset.py --region nordrhein-westfalen     # -> tools/osm-packs/osm-nordrhein-westfalen.json
    python3 tools/build_station_asset.py --region germany --split-regions # osm-germany.json + one pack per state
    python3 tools/build_station_asset.py --bbox 50.85,6.80,51.05,7.10 --name koeln

Options: `--source FILE` (default `tools/osm-stations.json`), `--region` (berlin, munich,
germany, or one of the 16 states: schleswig-holstein, hamburg, niedersachsen, bremen,
nordrhein-westfalen, hessen, rheinland-pfalz, baden-wuerttemberg, bayern, saarland, berlin,
brandenburg, mecklenburg-vorpommern, sachsen, sachsen-anhalt, thueringen), `--bbox`,
`--name`, `--out-dir` (default: the app's assets for berlin, else `tools/osm-packs/`, not
committed), `--split-regions` with `--packs-dir` (default `tools/osm-packs/states/`).
Other regions are written as `osm-<region>.json` and `osm-change-positions-<region>.ndjson`.
A region that holds the whole extract (Berlin from the Berlin extract) is built unchanged;
otherwise the extract is first cut to the region's box plus 2 km and the result to the region.

States (`--region <state>` and `--split-regions`): each station, numbered stop, stop area and
change record goes to the state of its DHID (the second part of `ref:IFOPT`,
`de:05315:11201` lies in state 05, Nordrhein-Westfalen), else of the nearest thing with a
DHID (about 15 km), else of the first state box holding it (`tools/regions.py`; city states
first). Only a state whose box holds the place counts, for its own DHID too (a de:05156 of
Nordrhein-Westfalen mapped on stops in the Harz is an error). `--region <state>` keeps in
addition a place without a DHID of its own whose nearest DHID of that state is at most 2 km
farther than its nearest DHID at all (`STATE_BAND_METERS`): the extracts of two neighbours
differ near their border, and before, such places (Eisenach next to Hessen, Hermeskeil next to
the Saarland, Wildau next to Berlin, whose pack is not shipped) were placed in the other state
by each build and lost from both. The app loads such a place once (`OsmStations.merge` drops
the second copy). November 2026, all 15 packs: OSM bus/tram stops (in each state's box) with no
pack position within 25 m: named 5,011 -> 36, unnamed 11,543 -> 10,100 (of 1.21 million); 247,656 stop areas
(+6,565) with 427,315 positions (+14,633), 8.95 MB gzip (+2.8 %). `python3
tools/test_stop_positions.py` checks these rules. No admin boundaries are needed, so the extract does not have to carry them. The
build prints a table of the packs, plain and gzip, against the one file.

Ids: `"ids"` of stations, stops and stop areas hold the VBB numbers as before (only from VBB
sources: ref:VBB, fahrinfo links, DHIDs of Berlin and Brandenburg) and now also the DHIDs at
stop place level (`de:05315:11201`, from ref:IFOPT of the station, its stop areas and, for a
station without them outside Berlin/Brandenburg, the most common one of its platforms and
stop positions). DHIDs are the stop ids of DELFI's GTFS and so of Transitous. Change records
use the long VBB form where there is a VBB number, else the DHID (outside Berlin and
Brandenburg).

### Platforms and signs of bus and tram stops

The positions of a stop area are its platforms, each one of its own with the lines of the
routes that list it or the stop position beside it (two bus platforms on both sides of a
street are two positions, however close). A `public_transport=stop_position` is merged into
its platform: the one a route relation lists it with (PTv2: stop, then platform), else the
nearest of its mode, within 25 m (`STOP_PLATFORM_METERS`), unless that platform has another
number. A stop position without a platform stays a position and takes the lines of its
routes. A position with neither number nor lines is left out where a position of its mode
with lines lies within 25 m (a platform mapped twice) or, for a bare stop position, where the
stop has a platform of its mode. Before (November 2026), stop positions were the positions and
a platform counted only when it was the mutually nearest of one; two stop positions 2.6 m
apart on the road (both directions) were one, so at "Wolliner Straße" (Bernauer Straße) one
247 stood on the road and the other bus platform was an empty dot with no lines (the routes'
stop members won over their platform members).

The sign of a bus or tram stop on the map stands where its positions are, not at the
provider's point when that lies away from them (the VBB's point of Wolliner Str. lies 36 to
50 m from all four platforms, in a block of houses): `stopSignPoint` in
`app/src/main/java/io/github/jnbp/nextstop/data/StopSignPlace.kt` and `tools/stop_sign.py`
(the same rule) keep the provider's point within 30 m of a position and inside the positions'
box plus 20 m, else take the centre of the positions (those within 250 m of the nearest),
pulled to at most 30 m from a position. The app applies it to the stops of the nearby and name
searches (`RoutingSource.signed`) and to the signs of stop areas known from OpenStreetMap
alone (`OsmStations.mapStops`). Buildings are not known to the app.

`tools/audit_stop_signs.py` counts over Berlin and all packs the signs farther than 50 m from
all positions of their stop, empty dots (no number, no lines; those on a stop position),
platforms of the extract with no position of their own (none within 8 m) and route platforms
whose position lacks the route's line; `--provider` takes provider points (CSV id,lat,lon),
`--assets` another assets folder (`git archive <commit> app/src/main/assets`) for a before/after:

    python3 tools/audit_stop_signs.py --source-dir tools/osm-pbf --regions berlin
    python3 tools/test_stop_signs.py

November 2026, Berlin and the 15 packs, before -> after: route platforms whose position lacks
the line 166,520 -> 221 (of 582,095), platforms with no position of their own 58,422 -> 11,403
(those left are bare duplicates within 25 m of a platform with lines), empty dots 103,026 ->
75,829 (on a stop position 24,331 -> 3,536; the rest are platforms no route relation lists),
signs of OpenStreetMap stop areas farther than 50 m from all their positions 10,744 -> 0;
positions 437,915 -> 436,665. Berlin: empty dots 2,229 -> 475, route platforms without their
line 8,187 -> 0. Of 25 provider stops around Wolliner Str. (bus and tram only), 13 signs move
(2 were farther than 50 m from all positions; none after).

`tools/audit_sign_merges.py` checks over Berlin and all packs which bus and tram stops have no
sign of their own on the map because another sign stands for them, with the rules of the app
replicated (`OsmStations.mapStops`/`streetStopJoinsStation` offline, `mergeMapStops` beside the
provider's station, `stationGroups` for rail stations, `stopNameParts` for names; keep them
alike): stops without a sign of their own before -> after, those farther than 120 m from the
sign that stands for them, stop names with a qualifier (brackets) and how they are shown, rail
stations joined farther than 120 m apart, the worst examples; `--report` writes all as JSON:

    python3 tools/audit_sign_merges.py [--regions berlin] [--worst 20]
    python3 tools/test_sign_merges.py

Rule since November 2026: a bus or tram stop keeps its "H" unless its sign stands within 120 m
(STREET_STOP_OWN_SIGN_METERS) of the sign of a rail station of its name or stop place number
(before: within 300 m of a station of its name offline, 250 m beside the provider's station,
and any distance up to 250 m with the station's number). The case that showed it: the bus stop
"S+U Alexanderplatz/Memhardstraße" in Karl-Liebknecht-Straße (the VBB's "S+U Alexanderplatz
(Bln) [Bus K.-Liebknecht-Str]"), 230 m from the station's sign, had no sign unless selected.
A provider's stop named as the bus or tram part of a station ("[Bus ...]", "[Tram]") never joins
the rail station, whatever products it lists.

November 2026, bus and tram stop areas without a sign of their own before -> after, and of
those farther than 120 m from the sign standing for them:

| pack | bus/tram stops | no own sign | no own sign >120 m | names with brackets | shown shorter | rail joined >120 m |
|---|---|---|---|---|---|---|
| berlin | 4,087 | 434 -> 263 | 173 -> 0 | 74 | 2 | 1 |
| baden-wuerttemberg | 30,682 | 1,449 -> 1,027 | 505 -> 0 | 827 | 0 | 1 |
| bayern | 48,624 | 1,408 -> 918 | 583 -> 0 | 1,850 | 10 | 3 |
| brandenburg | 10,486 | 291 -> 197 | 121 -> 0 | 879 | 1 | 0 |
| bremen | 952 | 10 -> 10 | 1 -> 0 | 11 | 0 | 0 |
| hamburg | 2,712 | 137 -> 92 | 45 -> 0 | 413 | 0 | 4 |
| hessen | 14,506 | 396 -> 297 | 104 -> 0 | 150 | 1 | 1 |
| mecklenburg-vorpommern | 7,977 | 218 -> 132 | 94 -> 0 | 84 | 0 | 0 |
| niedersachsen | 22,549 | 404 -> 259 | 155 -> 0 | 305 | 0 | 0 |
| nordrhein-westfalen | 51,753 | 954 -> 925 | 222 -> 0 | 354 | 3 | 5 |
| rheinland-pfalz | 12,473 | 379 -> 212 | 168 -> 0 | 638 | 1 | 0 |
| saarland | 3,960 | 136 -> 71 | 67 -> 0 | 51 | 0 | 0 |
| sachsen-anhalt | 7,157 | 217 -> 102 | 116 -> 0 | 172 | 0 | 0 |
| sachsen | 16,316 | 484 -> 293 | 218 -> 0 | 399 | 3 | 0 |
| schleswig-holstein | 11,634 | 235 -> 126 | 109 -> 0 | 453 | 0 | 0 |
| thueringen | 5,874 | 174 -> 93 | 83 -> 0 | 78 | 0 | 0 |
| total | 251,742 | 7,326 -> 5,017 | 2,764 -> 0 | 6,738 | 21 | 15 |

Worst before (distance to the sign that stood for them): Hubacker Brücke/Bahnhof 341 m
(Hubacker), Oppurg Wartehalle 332 m, Bärenstein, Bahnhofstraße 324 m, Wechselburg, Rathaus
315 m, Adelsheim, Nordbahnhof 306 m, Günzach, Gemeindeamt 306 m, Gaimersheim, Magnusweg 305 m,
Lorch, Pfäfflestraße/Schillerschule 300 m, Rehau am alten Markt 300 m, Kusel, Fachmarktzentrum
300 m, Gneisenaustraße/Baerwaldstraße 299 m (Berlin), Lauffen, Lindenstraße 299 m, Bruchsal
Siemens 299 m, Pulling 299 m; in Berlin also Platz der Luftbrücke 292 m, S+U
Lichtenberg/Gudrunstraße 282 m, S Warschauer Straße 272 m, S Südkreuz/Ostseite 261 m and
the stop areas of Alexanderplatz (Memhardstraße 224 m and 199 m, Grunerstraße 191 m, U
Alexanderplatz bus 243 m, tram 177 m). After: none. The stops left without a sign of their own stand within 120 m of their
station's sign. Rail stations joined farther than 120 m are parts of one station (Hamburg
Hauptbahnhof Nord/Süd 304 m, München Hbf and its Holzkirchner Bahnhof 288 m, Berlin Hauptbahnhof
Tunnel 179 m); they stay joined.

Names: the town in brackets is left out only where it is the town of the stops around (at least
three stops of different names, half of all, name it; `LocalTown`), else Berlin. The audit found
the words in brackets of the packs that are no town (directions "(Nord)", parts "(Kehre)",
"(Schienenersatzverkehr)", landscapes "(Sachsen)", "(Pfalz)", district codes "(BAR)", "Richtung
...") and the app keeps them (NOT_A_TOWN in data/StopTowns.kt, read by the audit). The packs
carry OpenStreetMap's names, which rarely have the provider's "(Bln)": the town rule is checked
on provider names in the unit tests (StopTownsTest).

Bus stops of the countryside: the extract takes as bus stops besides `public_transport=*` with
`bus=yes`/`tram=yes` every `highway=bus_stop` and `highway=platform` (node or way, not tagged as
rail), as villages are often mapped with nothing else (Zagelsdorf near Dahme: two
`highway=bus_stop` platforms on both sides of the L 71, 105 m apart, no stop area, no
ref:IFOPT). `highway=platform` nodes are read in a short pass of their own
(`platform-nodes-v1` in the cache, half a minute for Germany), since pass 4 of an older cache
did not keep them. In the build, positions of one name without a stop area form one stop when
each lies within 250 m of another (both directions of a village stop), farther ones a stop of
their own of the same name; the app takes all of them near the provider's stop. A position
without a name joins the stop whose nearest position of its mode lies within 80 m (else it is
left out: nobody can tell whose it is), and a member of a stop area more than 500 m from the
middle of it (a "stop area" of a whole town) is taken as a loose position of its own name
instead of being dropped. The app (`OsmStations.areasOf`) measures to the nearest position of a
stop area, not its middle: up to 1 km for an area with the provider's VBB number or DHID, 400 m
for one found by name.

Outside Berlin, a bus or tram stop whose name ends with its stand ("Tattersall Steig I",
"Hbf, Bstg. 3"; Steig, Bussteig, Bstg., Bahnsteig, Mast, Pos.) loses it from the name and keeps
it as its number, so it joins the stop area of that name. Each pack also carries
`"lineColors"`: `[mode, line, "rrggbb", lat, lon]` from the colour tags of the route relations
(mode b bus, t tram, u U-Bahn/Stadtbahn, s S-Bahn; one entry per line, colour and 30 km),
which the app uses for lines its provider gives no colour of (`LineColors`).

The app reads a pack as a stream, only in tiles of 0.5 degrees near where it looks
(`OsmStations.ensureRegions`), so a big pack never has to fit its heap at once.

### Kinds of stations

Each station complex has `"products"` (the letters S, U, R the app understands, unchanged
for Berlin) and the finer `"kinds"` (letters in the order S U L T R F):

| kind | meaning | from |
|---|---|---|
| S | S-Bahn | a route=light_rail or route=train with `network:metro=s-bahn`, a ref `S1` / `S 1`, or "S-Bahn" in network, operator or name (not long distance) |
| U | U-Bahn | route=subway |
| L | Stadtbahn | a route=light_rail that is no S-Bahn (Cologne/Bonn "16", Hannover "1", Stuttgart and the Ruhr "U6", "U18", Duesseldorf "U79", Frankfurt where mapped as light_rail) |
| T | tram | tram stop positions in a stop area of the station |
| R | regional train | route=train that is neither S-Bahn nor long distance (RE, RB, ...) |
| F | long-distance train | route=train with `service=long_distance|high_speed|international|national|night` or a ref ICE, IC, EC, ECE, EN, NJ, FLX, RJ, RJX, TGV |

A route counts for a station when one of its stop or platform members is in a stop area of
the station, else at the nearest station whose stop positions or platforms hold the member.
A mode of the station (from the extract) that no route explains is added by itself: subway
U, train R, light_rail S, except L when the name does not start with "S " / "S+U ", the tags
of the station and its stop areas (network, operator) do not speak of the S-Bahn or DB, and
they do speak of a Stadtbahn, Strassenbahn, Verkehrsbetriebe, Stadtwerke or Uestra (or the
station has `tram=yes`). The tags
`station=light_rail` / `subway` alone cannot tell S-Bahn from Stadtbahn (Cologne maps its
Stadtbahn tunnels as subway, Stuttgart as light_rail), so the routes decide.

`"products"` from the kinds: S if S, U if U or L (German Stadtbahn tunnels carry the U sign),
else R; T adds nothing. Change positions are estimated for S, U and L routes whose ref looks
like `S1` or `U6` (a Stadtbahn "16" gets none).

Self-test of the build outside Berlin (synthetic Cologne, Stuttgart and Munich stations):

    python3 tools/test_build_station_asset.py
    python3 tools/test_stop_positions.py      # rural stops, unnamed positions, states near borders

### All of Germany on the Mac

    pip3 install --user osmium
    mkdir -p tools/osm-pbf
    curl -L -o tools/osm-pbf/germany-latest.osm.pbf https://download.geofabrik.de/europe/germany-latest.osm.pbf
    python3 tools/extract_osm_pbf.py --region germany --out tools/osm-pbf/germany-stations.json tools/osm-pbf/germany-latest.osm.pbf
    python3 tools/build_station_asset.py --source tools/osm-pbf/germany-stations.json --region germany --split-regions
    python3 tools/estimate_region_size.py tools/osm-packs/osm-germany.json --changes tools/osm-packs/osm-change-positions-germany.ndjson

The extract reads the file (about 4.5 GB) in five passes (relations, tagged ways, member ways
of the relations, tagged nodes, then the locations of only the nodes that are needed), so it
needs no big temporary file, a few GB of memory and some 3 to 5 minutes on a Mac. Each pass
is saved to `.extract-cache/` next to the input (about 0.5 GB for Germany), so an interrupted
run resumes where it stopped and a repeated run is fast; `--cache-dir DIR` puts the cache
elsewhere and `--no-cache` keeps none. The cache is not committed. The build holds the whole extract in
memory (several GB for Germany: a synthetic extract of three Berlins, 72 MB, needed 575 MB) and takes roughly half a minute per Berlin's worth of stations. Without that much
memory, build state by state from the state files of Geofabrik instead:

    for s in baden-wuerttemberg bayern berlin brandenburg bremen hamburg hessen mecklenburg-vorpommern \
             niedersachsen nordrhein-westfalen rheinland-pfalz saarland sachsen sachsen-anhalt \
             schleswig-holstein thueringen; do
      curl -L -o tools/osm-pbf/$s-latest.osm.pbf https://download.geofabrik.de/europe/germany/$s-latest.osm.pbf
      python3 tools/extract_osm_pbf.py --region $s --out tools/osm-pbf/$s-stations.json tools/osm-pbf/$s-latest.osm.pbf
      python3 tools/build_station_asset.py --source tools/osm-pbf/$s-stations.json --region $s
    done

(Geofabrik's Brandenburg file contains Berlin; for the state of Berlin use it or
berlin-latest.osm.pbf.)

With little memory (a machine with about 4 GB of RAM) all of Germany in one
extract does not fit, but the state extracts can all be cut from the one Germany file: the
five passes are cached for the whole file once (the first run of pass 4 needs about 2.5 GB:
it runs before the relations are read, and tags are interned), and each `--region <state>`
run then takes 1 to 1.5 minutes and well under 3.5 GB. The extra passes for routes placed by
their ways are cached too (per region). The extract keeps the read-ahead queues of libosmium
small (`OSMIUM_MAX_*_QUEUE_SIZE=4` unless set otherwise):

    for s in baden-wuerttemberg bayern berlin brandenburg bremen hamburg hessen mecklenburg-vorpommern \
             niedersachsen nordrhein-westfalen rheinland-pfalz saarland sachsen sachsen-anhalt \
             schleswig-holstein thueringen; do
      python3 tools/extract_osm_pbf.py --region $s --out tools/osm-pbf/$s-stations.json tools/osm-pbf/germany-latest.osm.pbf
      python3 tools/build_station_asset.py --source tools/osm-pbf/$s-stations.json --region $s --out-dir tools/osm-packs/states
    done
    python3 tools/build_region_index.py            # tools/osm-packs/states/index.json
    python3 tools/build_region_index.py --install  # and the packs into app/src/main/assets/osm-regions/

(`--region berlin` is the Overpass box and gives the app's Berlin asset again; the app has it
built in, so the index and `--install` leave it out.) Berlin and Brandenburg have VBB numbers
instead of DHIDs: 900000001 to 900199999 count as Berlin, the higher ones as Brandenburg.

### State packs in the app

`OsmStations.ensureRegions` loads the packs of the states around the map centre and around
both ends of a journey search, from the app's files under `osm-regions/` (a download, for a
later GitHub Releases variant) or else from the assets under `osm-regions/`; `index.json`
there (cells of 0.1 degrees with stations or stops, per state) picks the packs, else the boxes
of the states do. Things a loaded pack has already (the same name close by) are not taken
twice. Without packs the app knows Berlin only, as before. `tools/estimate_region_size.py FILE` prints the size of each
section of an asset (or of an extract, built in memory), plain and gzip, and with
`--scale-stations N --scale-stop-areas M` a rough extrapolation from its averages.
