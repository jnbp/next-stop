#!/usr/bin/env python3
"""
Extract the stations, stops, entrances and routes of a region from local OpenStreetMap
.osm.pbf files (e.g. from Geofabrik) and write tools/osm-stations.json in the same format
as tools/fetch_osm_stations.py, so tools/build_station_asset.py works on either. No public
Overpass server is asked.

    python3 tools/extract_osm_pbf.py tools/osm-pbf/brandenburg-latest.osm.pbf
    python3 tools/extract_osm_pbf.py tools/osm-pbf/berlin-latest.osm.pbf tools/osm-pbf/brandenburg-latest.osm.pbf
    python3 tools/extract_osm_pbf.py --region germany tools/osm-pbf/germany-latest.osm.pbf --out /tmp/de.json
    python3 tools/extract_osm_pbf.py --bbox 52.33,13.08,52.68,13.77 tools/osm-pbf/germany-latest.osm.pbf

The region is a bounding box (south, west, north, east); the default is the box around
Berlin that tools/fetch_osm_stations.py asks in tiles. Several files are merged (objects in
more than one file are taken once).

Needs pyosmium 4 or newer (pip3 install --user osmium). Python 3.9 or newer.

The selection follows the Overpass queries of tools/fetch_osm_stations.py (constants and
helpers are imported from there), with one deliberate difference: the stations. Overpass
asks only railway=station with station=subway|light_rail; here every rail station is
caught, however it is drawn (node, way or multipolygon) and tagged (railway=station|halt,
public_transport=station; station=subway|light_rail|train or subway/light_rail/train=yes, or
none, which means train), and each station gets a "modes" list (subway, light_rail, train)
from its tags and from the rail routes stopping in its stop areas. A station drawn twice
(a node and an area around it) is taken once, as the node. --modes picks which stations are
kept (default: all three).

The file is read in three passes: the relations (stop areas, routes, platform and station
relations, station buildings), then the nodes and the tagged ways with the locations of all
nodes kept in an index (flex_mem in memory for small files, a sparse file in the temporary folder
for big ones such as Germany), then the untagged member ways of the relations that were
kept.

Data (c) OpenStreetMap contributors, available under the Open Database License (ODbL 1.0).
"""

import argparse
import gc
import hashlib
import json
import math
import os
import pickle
import re
import shutil
import sys
import time
from array import array

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import fetch_osm_stations as ov  # noqa: E402  (the constants and helpers of the Overpass fetch)

# Small read-ahead queues of libosmium (default 20 buffers each): with them a pass over all of
# Germany needs about 1.5 GB less, at hardly any cost in time.
for _queue in ("OSMIUM_MAX_INPUT_QUEUE_SIZE", "OSMIUM_MAX_OSMDATA_QUEUE_SIZE", "OSMIUM_MAX_WORK_QUEUE_SIZE"):
    os.environ.setdefault(_queue, "4")

try:
    import osmium
    import osmium.filter
    import osmium.index
    import osmium.io
except ImportError:
    raise SystemExit("pyosmium is missing: pip3 install --user osmium  (version 4 or newer)")

if not hasattr(osmium, "FileProcessor"):
    raise SystemExit("pyosmium is too old: pip3 install --user --upgrade osmium  (version 4 or newer)")

OUTPUT = ov.OUTPUT
AROUND_METERS = ov.AROUND_METERS

# Regions as bounding boxes (south, west, north, east). "berlin" is the box the Overpass
# fetch covers with its tiles; the others are prepared for per-region packs.
REGIONS = {
    "berlin": (ov.BERLIN_SOUTH, ov.BERLIN_WEST, ov.BERLIN_NORTH, ov.BERLIN_EAST),
    "brandenburg": (51.35, 11.26, 53.56, 14.77),
    "hamburg": (53.39, 9.73, 53.74, 10.33),
    "munich": (47.98, 11.36, 48.25, 11.72),
    "germany": (47.27, 5.86, 55.06, 15.05),
}
# And the 16 German states by their (generous) boxes, from tools/regions.py; an extract of a
# state box holds parts of its neighbours, which build_station_asset.py --region cuts off.
import regions as _regions  # noqa: E402
for _name, _box in _regions.REGIONS.items():
    REGIONS.setdefault(_name, _box)

RAIL_MODES = ("subway", "light_rail", "train")
RAIL_ROUTES = ("subway", "light_rail", "train")
BUS_ROUTES = ("bus", "tram", "trolleybus")
# station=* values that are no train, U-Bahn or S-Bahn (unless a mode tag says otherwise).
NOT_RAIL_STATIONS = ("miniature", "funicular", "monorail", "tram", "bus", "ferry", "aerialway")
# Doors with these railway tags are transit entrances; the others get "stationBuilding".
TRANSIT_ENTRANCES = ("subway_entrance", "train_station_entrance", "light_rail_entrance")
# Entrances asked in the tiles of the Overpass fetch.
TILE_ENTRANCES = ("subway_entrance", "train_station_entrance")
# A second drawing of a station (an area, or public_transport=station alone) within this
# distance of a station node with the same name is taken as the same station.
DUPLICATE_METERS = 300.0
# Words left out when comparing station names.
NAME_STOP_WORDS = {"s", "u", "bahnhof", "bhf", "station", "berlin", "hbf", "tief", "tunnel"}
# Grid cell for the neighbour searches, in degrees (more than AROUND_METERS everywhere in Europe).
CELL = 0.005


def log(message):
    print(message, flush=True)


class Clock(object):
    def __init__(self):
        self.start = time.time()

    def __str__(self):
        seconds = int(time.time() - self.start)
        return "%d:%02d" % (seconds // 60, seconds % 60)


# --- Tags -------------------------------------------------------------------------------

# Tag values up to this length are interned: keys and common values ("yes", "footway",
# "platform") then exist once in memory and in the pickles of the cache, instead of once per
# object (for all of Germany that halves the memory of the cached passes).
INTERN_LENGTH = 40


def tag_dict(tags):
    intern = sys.intern
    return {intern(tag.k): (intern(tag.v) if len(tag.v) <= INTERN_LENGTH else tag.v) for tag in tags}


def compact_tags(tags):
    """[tags] with interned keys and short values (for records of an older cache)."""
    intern = sys.intern
    return {intern(k): (intern(v) if len(v) <= INTERN_LENGTH else v) for k, v in tags.items()}


def compact_records(records):
    """Intern the tags of the (id, tags, ...) records of a pass in place."""
    for i, record in enumerate(records):
        records[i] = (record[0], compact_tags(record[1])) + tuple(record[2:])
    return records


def split_values(value):
    return {part.strip() for part in (value or "").split(";") if part.strip()}


def station_candidate(tags):
    """The modes the tags of a station say ([] when they say none, but it is a station), or
    None when [tags] are no rail station at all."""
    railway = tags.get("railway")
    if railway not in ("station", "halt") and tags.get("public_transport") != "station":
        return None
    if tags.get("disused") == "yes" or tags.get("abandoned") == "yes" or tags.get("usage") == "tourism":
        return None
    if tags.get("railway:traffic_mode") == "freight":
        return None
    values = split_values(tags.get("station"))
    if values & set(NOT_RAIL_STATIONS) and not values & set(RAIL_MODES):
        # A park railway (station=miniature) is often tagged train=yes as well: still no rail
        # station of the network.
        return None
    modes = [mode for mode in RAIL_MODES if mode in values or tags.get(mode) == "yes"]
    if not modes and railway not in ("station", "halt"):
        # public_transport=station alone (a bus station, a tram stop area): no rail.
        return None
    return modes


def is_station_building(tags):
    """The outlines the Overpass fetch asks for the doors of a station building."""
    return (tags.get("building") in ("train_station", "transportation") or tags.get("railway") == "station"
            or tags.get("public_transport") == "station")


def is_around_node(tags):
    return (tags.get("public_transport") == "stop_position" or tags.get("railway") == "stop"
            or tags.get("highway") == "elevator")


def is_around_way(tags):
    highway = tags.get("highway")
    return (tags.get("railway") == "platform" or tags.get("public_transport") == "platform"
            or highway in ("steps", "elevator") or "conveying" in tags
            or (highway == "footway" and ("level" in tags or "layer" in tags or "indoor" in tags))
            or highway == "corridor" or tags.get("indoor") == "corridor")


def is_around_relation(tags):
    return tags.get("railway") == "platform" or tags.get("public_transport") == "platform"


def is_bus_or_tram(tags):
    return tags.get("bus") == "yes" or tags.get("tram") == "yes"


# Modes of rail and other transport that a highway=platform is never a bus platform of.
NOT_ROAD_MODES = ("train", "subway", "light_rail", "monorail", "funicular", "ferry", "aerialway")


def is_rail_tagged(tags):
    """True for something of rail transport (or a ferry or cable car): a railway tag other
    than tram_stop, or a mode such as train=yes."""
    railway = tags.get("railway")
    return (railway is not None and railway != "tram_stop") or any(tags.get(m) == "yes" for m in NOT_ROAD_MODES)


def is_road_platform(tags):
    """A platform of a bus (or tram) stop by its highway tag alone: highway=platform or
    highway=bus_stop, as rural stops are often mapped (no bus=yes, no public_transport), and
    not tagged as rail."""
    return tags.get("highway") in ("platform", "bus_stop") and not is_rail_tagged(tags)


def is_stop_node(tags):
    pt = tags.get("public_transport")
    return ((pt in ("stop_position", "platform") and is_bus_or_tram(tags))
            or tags.get("highway") == "bus_stop" or tags.get("railway") == "tram_stop"
            or is_road_platform(tags))


def is_stop_way(tags):
    return (tags.get("public_transport") == "platform" and is_bus_or_tram(tags)) or is_road_platform(tags)


def is_bus_stop_tags(tags):
    """Whether a stop of is_stop_node / is_stop_way serves buses: bus=yes, a highway=bus_stop,
    or a highway=platform that is not for trams alone."""
    highway = tags.get("highway")
    return (tags.get("bus") == "yes" or highway == "bus_stop"
            or (highway == "platform" and tags.get("tram") != "yes" and not is_rail_tagged(tags)))


def is_any_platform(tags):
    return "platform" in (tags.get("public_transport"), tags.get("highway"), tags.get("railway"))


def name_words(name):
    return frozenset(w for w in re.findall(r"\w+", (name or "").lower()) if w not in NAME_STOP_WORDS)


# --- Geometry ---------------------------------------------------------------------------

def in_box(box, lat, lon):
    return box[0] <= lat <= box[2] and box[1] <= lon <= box[3]


def any_in_box(box, points):
    for lat, lon in points:
        if box[0] <= lat <= box[2] and box[1] <= lon <= box[3]:
            return True
    return False


def bounds(points):
    lats = [p[0] for p in points]
    lons = [p[1] for p in points]
    return min(lats), min(lons), max(lats), max(lons)


def bbox_center(points):
    """The centre of the bounding box, as Overpass gives with "out center"."""
    s, w, n, e = bounds(points)
    return round((s + n) / 2, 7), round((w + e) / 2, 7)


def boxes_meet(a, b):
    return a[0] <= b[2] and b[0] <= a[2] and a[1] <= b[3] and b[1] <= a[3]


def expand(box, meters):
    dlat = meters / 111320.0
    dlon = meters / (111320.0 * math.cos(math.radians((box[0] + box[2]) / 2)))
    return box[0] - dlat, box[1] - dlon, box[2] + dlat, box[3] + dlon


def inside(lat, lon, rings):
    """Even-odd test of a point against all segments of [rings] (lists of points); the
    member ways of a multipolygon need not be joined for it, only closed together."""
    result = False
    for ring in rings:
        for i in range(1, len(ring)):
            (y1, x1), (y2, x2) = ring[i - 1], ring[i]
            if (y1 > lat) != (y2 > lat) and lon < x1 + (lat - y1) * (x2 - x1) / (y2 - y1):
                result = not result
    return result


def cell_of(lat, lon):
    return int(math.floor(lat / CELL)), int(math.floor(lon / CELL))


class Grid(object):
    """Items at points, found again near a point (within one cell around it)."""

    def __init__(self):
        self.cells = {}

    def add(self, lat, lon, item):
        self.cells.setdefault(cell_of(lat, lon), []).append(item)

    def add_box(self, box, item):
        s, w = cell_of(box[0], box[1])
        n, e = cell_of(box[2], box[3])
        for y in range(s, n + 1):
            for x in range(w, e + 1):
                self.cells.setdefault((y, x), []).append(item)

    def near(self, lat, lon):
        y, x = cell_of(lat, lon)
        found = []
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                found.extend(self.cells.get((y + dy, x + dx), ()))
        return found

    def at(self, lat, lon):
        return self.cells.get(cell_of(lat, lon), ())


# --- Relations, compact -----------------------------------------------------------------

TYPE_NAMES = {"n": "node", "w": "way", "r": "relation"}


class Members(object):
    """The members of a relation without a Python object per member."""
    __slots__ = ("types", "refs", "roles")

    def __init__(self, members):
        types = []
        refs = array("q")
        roles = []
        for member in members:
            types.append(member.type)
            refs.append(member.ref)
            roles.append(sys.intern(member.role))
        self.types = "".join(types)
        self.refs = refs
        self.roles = roles

    def __iter__(self):
        return zip(self.types, self.refs, self.roles)

    def records(self):
        return [{"type": TYPE_NAMES[t], "ref": ref, "role": role} for t, ref, role in self]


class Source(object):
    """Everything kept from the files, before the region is cut out."""

    def __init__(self):
        self.stop_areas = {}       # id -> (tags, Members)
        self.rail_routes = {}      # id -> (tags, Members)
        self.bus_routes = {}       # id -> (tags of BUS_ROUTE_KEYS and route, Members)
        self.station_rels = {}     # id -> (tags, Members)
        self.platform_rels = {}    # id -> (tags, Members)
        self.building_rels = {}    # id -> (tags, Members)
        self.station_cands = {}    # "type/id" -> dict(type, id, tags, lat, lon, rings)
        self.around = {}           # "type/id" -> compact element (see fetch_osm_stations.compact)
        self.around_points = {}    # "type/id" -> points to test the distance with
        self.tile_entrances = {}   # id -> (lat, lon, tags)
        self.doors = {}            # id -> (lat, lon, tags), doors with one of ENTRANCE_KEYS
        self.bare_doors = {}       # id -> (lat, lon, tags), the other doors (entrance=* alone)
        self.around_refs = {}      # "type/id" -> node ids of the steps, footways, corridors, elevators
        self.stops = {}            # "type/id" -> (type, id, lat, lon, tags)
        self.platform_centers = {} # way id -> (lat, lon) of every platform way
        self.buildings = []        # (box, node ids, rings)
        self.member_ways = {}      # way id -> (node ids, points) of relation members
        self.storages = []
        self.headers = []

    def locate(self, ref):
        for storage in self.storages:
            try:
                location = storage.get(ref)
            except KeyError:
                continue
            if location.valid():
                return round(location.lat, 7), round(location.lon, 7)
        return None


# --- Cache of the passes ----------------------------------------------------------------

# Raise when what a pass keeps changes, so that old caches are not taken.
CACHE_VERSION = 1


class Cache(object):
    """The results of the passes over one input file, kept in [directory] (None: no cache)
    under the name, size and modification time of the file, so that a run broken off (or a
    run for another region) does not read the file again for the passes already done. A
    pass result is written only when the pass is complete."""

    def __init__(self, directory):
        self.directory = directory

    def folder(self, path):
        if not self.directory:
            return None
        stat = os.stat(path)
        name = os.path.basename(path)
        folder = os.path.join(self.directory, "%s-%d-%d-v%d" % (name, stat.st_size, stat.st_mtime_ns, CACHE_VERSION))
        if not os.path.isdir(folder):
            os.makedirs(folder, exist_ok=True)
            # Caches of an older version of the same file are of no more use.
            for other in os.listdir(self.directory):
                if other.startswith(name + "-") and os.path.join(self.directory, other) != folder:
                    shutil.rmtree(os.path.join(self.directory, other), ignore_errors=True)
        return folder

    def load(self, path, step):
        folder = self.folder(path)
        if folder is None:
            return None
        name = os.path.join(folder, step + ".pickle")
        if not os.path.exists(name):
            return None
        with open(name, "rb") as handle:
            value = pickle.load(handle)
        if isinstance(value, list) and value and isinstance(value[0], tuple) and isinstance(value[0][1], dict):
            compact_records(value)
        return value

    def has(self, path, step):
        folder = self.folder(path)
        return folder is not None and os.path.exists(os.path.join(folder, step + ".pickle"))

    def save(self, path, step, value):
        folder = self.folder(path)
        if folder is None:
            return
        name = os.path.join(folder, step + ".pickle")
        with open(name + ".part", "wb") as handle:
            pickle.dump(value, handle, protocol=pickle.HIGHEST_PROTOCOL)
        os.replace(name + ".part", name)

    def cached(self, path, step, label, compute):
        value = self.load(path, step)
        if value is not None:
            log("%s: %s (from the cache)" % (label, os.path.basename(path)))
            return value
        log("%s: %s" % (label, os.path.basename(path)))
        value = compute()
        self.save(path, step, value)
        return value


def ids_key(ids):
    """A short name for a list of ids (an array), for the cache."""
    return hashlib.sha1(ids.tobytes()).hexdigest()[:16]


class Progress(object):
    """A line every [every] objects with the rate per minute (the total of a file is not
    known before it is read)."""

    def __init__(self, what, every):
        self.clock = Clock()
        self.what = what
        self.every = every
        self.count = 0

    def tick(self, last_id):
        self.count += 1
        if self.count % self.every == 0:
            seconds = max(time.time() - self.clock.start, 0.001)
            log("  %s  %d %s (%d per minute, at id %d)"
                % (self.clock, self.count, self.what, self.count * 60 / seconds, last_id))


# --- Pass 1: relations ------------------------------------------------------------------

def is_kept_relation(tags):
    return (tags.get("public_transport") == "stop_area"
            or (tags.get("type") == "route" and tags.get("route") in RAIL_ROUTES + BUS_ROUTES)
            or station_candidate(tags) is not None or is_around_relation(tags)
            or (is_station_building(tags) and tags.get("type") in ("multipolygon", "building")))


def scan_relations(path):
    """(id, tags, Members) of the relations that pass 1 keeps."""
    clock = Clock()
    processor = osmium.FileProcessor(path, osmium.osm.RELATION).with_filter(
        osmium.filter.KeyFilter("public_transport", "railway", "route", "building"))
    records = []
    seen = 0
    for relation in processor:
        seen += 1
        tags = tag_dict(relation.tags)
        if is_kept_relation(tags):
            records.append((relation.id, tags, Members(relation.members)))
        if seen % 200000 == 0:
            log("  %s  %d relations with transit keys read" % (clock, seen))
    return records


def add_relations(source, records):
    for rid, tags, members in records:
        if tags.get("public_transport") == "stop_area":
            source.stop_areas[rid] = (tags, members)
        if tags.get("type") == "route":
            route = tags.get("route")
            if route in RAIL_ROUTES:
                source.rail_routes[rid] = (tags, members)
            elif route in BUS_ROUTES:
                kept = {"route": route}
                for key in ov.BUS_ROUTE_KEYS:
                    if tags.get(key):
                        kept[key] = tags[key]
                source.bus_routes[rid] = (kept, members)
        if station_candidate(tags) is not None:
            source.station_rels[rid] = (tags, members)
        if is_around_relation(tags):
            source.platform_rels[rid] = (tags, members)
        if is_station_building(tags) and tags.get("type") in ("multipolygon", "building"):
            source.building_rels[rid] = (tags, members)


def read_relations(paths, source, cache):
    for path in paths:
        clock = Clock()
        add_relations(source, cache.cached(path, "relations", "Pass 1/5, relations", lambda: scan_relations(path)))
    log("  %s  %d stop areas, %d rail and %d bus/tram routes, %d station, %d platform and %d building relations"
        % (clock, len(source.stop_areas), len(source.rail_routes), len(source.bus_routes),
           len(source.station_rels), len(source.platform_rels), len(source.building_rels)))


# --- Passes 2 to 5: tagged ways, member ways, tagged nodes, node locations --------------
#
# The ways are read without node locations: pyosmium puts the location of every node into
# every way it reads before any filter is asked (FileProcessor.with_locations), so for all of
# Germany that meant a lookup in a location index of all ~600 million nodes (a 10 GB file)
# for each of ~1 billion node references of ~100 million ways, of which only ~1.5 million
# matter. Instead the tags decide first which ways are kept (with the ids of their nodes),
# and a last pass over the nodes keeps the locations of exactly the nodes needed.

WAY_TAGS = (
    ("railway", "station"), ("railway", "halt"), ("railway", "platform"),
    ("public_transport", "platform"), ("public_transport", "station"),
    ("highway", "steps"), ("highway", "elevator"), ("highway", "footway"), ("highway", "platform"),
    ("highway", "corridor"), ("indoor", "corridor"), ("highway", "bus_stop"),
    ("building", "train_station"), ("building", "transportation"),
    ("conveying", "yes"), ("conveying", "forward"), ("conveying", "backward"),
    ("conveying", "reversible"), ("conveying", "no"),
)


def way_matters(tags):
    """True when read_way may keep something of a way with [tags] (wherever it lies)."""
    return (is_any_platform(tags) or is_around_way(tags) or is_station_building(tags) or is_stop_way(tags)
            or station_candidate(tags) is not None)


def node_matters(tags, nid, way_nodes):
    """True when read_node may keep something of node [nid] with [tags] (wherever it lies).
    A door with no ENTRANCE_KEYS counts only as a node of a way ([way_nodes], an IdSet of
    the nodes of the ways kept), as network_doors finds it (a third of Germany's nodes kept
    otherwise)."""
    if (station_candidate(tags) is not None or is_around_node(tags) or tags.get("railway") in TILE_ENTRANCES
            or is_stop_node(tags)):
        return True
    if "entrance" in tags:
        return nid in way_nodes or any(key in tags for key in ov.ENTRANCE_KEYS)
    return False


def scan_ways(path):
    """(id, tags, node ids) of the tagged ways that may matter, without locations."""
    progress = Progress("tagged ways", 500000)
    processor = osmium.FileProcessor(path, osmium.osm.WAY).with_filter(osmium.filter.TagFilter(*WAY_TAGS))
    records = []
    for way in processor:
        progress.tick(way.id)
        tags = tag_dict(way.tags)
        if way_matters(tags):
            records.append((way.id, tags, array("q", [node.ref for node in way.nodes])))
    log("  %s  %d tagged ways read, %d kept" % (progress.clock, progress.count, len(records)))
    return records


def scan_member_ways(path, wanted):
    """Way id -> node ids of the ways with an id in [wanted] (an array of ids)."""
    clock = Clock()
    found = {}
    if len(wanted):
        processor = osmium.FileProcessor(path, osmium.osm.WAY).with_filter(osmium.filter.IdFilter(wanted))
        for way in processor:
            found[way.id] = array("q", [node.ref for node in way.nodes])
    log("  %s  %d of %d member ways found" % (clock, len(found), len(set(wanted))))
    return found


def scan_nodes(path, way_nodes):
    """(id, tags, lat, lon) of the tagged nodes that may matter."""
    progress = Progress("tagged nodes", 1000000)
    processor = osmium.FileProcessor(path, osmium.osm.NODE).with_filter(
        osmium.filter.KeyFilter("railway", "public_transport", "highway", "entrance"))
    records = []
    for node in processor:
        progress.tick(node.id)
        location = node.location
        if not location.valid():
            continue
        tags = tag_dict(node.tags)
        if node_matters(tags, node.id, way_nodes):
            records.append((node.id, tags, round(location.lat, 7), round(location.lon, 7)))
    log("  %s  %d tagged nodes read, %d kept" % (progress.clock, progress.count, len(records)))
    return records


# The cache name of the pass over the nodes of road platforms (bump on a change of what it keeps).
PLATFORM_NODES_STEP = "platform-nodes-v1"


def scan_platform_nodes(path):
    """(id, tags, lat, lon) of the nodes tagged highway=platform: bus platforms that carry no
    bus=yes nor public_transport tag (rural stops). Pass 4 of an older cache did not keep them;
    this short pass (a filter in C++, no Python per node) adds them to any cache."""
    clock = Clock()
    processor = osmium.FileProcessor(path, osmium.osm.NODE).with_filter(
        osmium.filter.TagFilter(("highway", "platform")))
    records = []
    for node in processor:
        location = node.location
        if not location.valid():
            continue
        tags = tag_dict(node.tags)
        if is_stop_node(tags):
            records.append((node.id, tags, round(location.lat, 7), round(location.lon, 7)))
    log("  %s  %d road platform nodes" % (clock, len(records)))
    return records


def index_kind(index, file_name):
    """The location index of the needed nodes (a few million, so memory is fine)."""
    if index and index != "auto":
        return index
    if file_name:
        return "sparse_file_array," + file_name
    return "sparse_mem_array"


def read_locations(path, needed, kind):
    """A location index of the nodes of [path] with an id in [needed], filled without any
    Python per node."""
    clock = Clock()
    if kind.startswith("sparse_file_array,"):
        open(kind.split(",", 1)[1], "wb").close()  # a file left by a broken run starts empty
    storage = osmium.index.create_map(kind)
    handler = osmium.NodeLocationsForWays(storage)
    reader = osmium.io.Reader(path, osmium.osm.NODE)
    try:
        osmium.apply(reader, osmium.filter.IdFilter(needed), handler)
    finally:
        reader.close()
    # The index is sorted when the handler sees the first way (needed for a file whose
    # nodes are not sorted by id, as a hand-written .osm).
    osmium.apply(osmium.io.FileBuffer(b"<osm version='0.6'><way id='1'/></osm>", "osm"), handler)
    log("  %s  %d MB of node locations" % (clock, storage.used_memory() // (1 << 20)))
    return storage


def cached_locations(path, needed, cache, index, label):
    folder = cache.folder(path)
    if index and index != "auto" or folder is None:
        log("%s: %s" % (label, os.path.basename(path)))
        return read_locations(path, needed, index_kind(index, None))
    name = os.path.join(folder, "locations-%s.bin" % ids_key(needed))
    if os.path.exists(name + ".done"):
        log("%s: %s (from the cache)" % (label, os.path.basename(path)))
        return osmium.index.create_map("sparse_file_array," + name)
    log("%s: %s" % (label, os.path.basename(path)))
    storage = read_locations(path, needed, index_kind("auto", name))
    open(name + ".done", "w").close()
    return storage


def located(storage, refs):
    points = []
    get = storage.get
    for ref in refs:
        try:
            location = get(ref)
        except KeyError:
            continue
        if location.valid():
            points.append((round(location.lat, 7), round(location.lon, 7)))
    return points


def relation_member_nodes(source):
    needed = array("q")
    for relations in (source.stop_areas, source.rail_routes, source.bus_routes, source.station_rels,
                      source.platform_rels, source.building_rels):
        for _, members in relations.values():
            needed.extend(ref for t, ref in zip(members.types, members.refs) if t == "n")
    return needed


def nodes_step_of(paths, ways):
    """The cache name of pass 4: it depends on the node ids of the ways of pass 2."""
    digest = hashlib.sha1()
    for path in paths:
        for _, _, refs in ways[path]:
            digest.update(refs.tobytes())
    return "nodes-%s" % digest.hexdigest()[:16]


def way_node_set(paths, ways):
    way_nodes = osmium.index.IdSet()
    for path in paths:
        for _, _, refs in ways[path]:
            for ref in refs:
                way_nodes.set(ref)
    return way_nodes


def prepare_node_pass(paths, cache):
    """Passes 2 and 4 before the relations are read: pass 4 needs only the node ids of the
    ways, and for all of Germany the relations, the ways and the tagged nodes together need
    more memory than a small machine has. Once cached, read_nodes_and_ways takes them from
    the cache."""
    if cache.directory is None:
        return
    ways = {}
    for path in paths:
        ways[path] = cache.cached(path, "ways", "Pass 2/5, tagged ways", lambda: scan_ways(path))
    nodes_step = nodes_step_of(paths, ways)
    missing = [path for path in paths if not cache.has(path, nodes_step)]
    if not missing:
        return
    way_nodes = way_node_set(paths, ways)
    ways.clear()
    gc.collect()
    for path in missing:
        cache.cached(path, nodes_step, "Pass 4/5, tagged nodes", lambda: scan_nodes(path, way_nodes))
        gc.collect()


def read_nodes_and_ways(paths, source, region, wide, index, cache, wanted):
    """Passes 2 to 5; [wanted]: the ids of the member ways of station, platform and building
    relations."""
    ways = {}
    member_ways = {}
    nodes = {}
    wanted = array("q", sorted(wanted))
    for path in paths:
        ways[path] = cache.cached(path, "ways", "Pass 2/5, tagged ways", lambda: scan_ways(path))
        member_ways[path] = cache.cached(path, "member-ways-%s" % ids_key(wanted), "Pass 3/5, member ways of relations",
                                         lambda: scan_member_ways(path, wanted))
    nodes_step = nodes_step_of(paths, ways)
    if any(not cache.has(path, nodes_step) for path in paths):
        way_nodes = way_node_set(paths, ways)
        for path in paths:
            nodes[path] = cache.cached(path, nodes_step, "Pass 4/5, tagged nodes",
                                       lambda: scan_nodes(path, way_nodes))
        del way_nodes
    needed = relation_member_nodes(source)
    for path in paths:
        for _, _, refs in ways[path]:
            needed.extend(refs)
        for refs in member_ways[path].values():
            needed.extend(refs)
    log("  %d node references to locate" % len(needed))
    storages = {}
    for path in paths:
        storages[path] = cached_locations(path, needed, cache, index, "Pass 5/5, node locations")
        source.storages.append(storages[path])
    del needed
    # The tagged nodes only now (with the cache: in a run that did not scan them), so that
    # pass 5 runs without them in memory.
    for path in paths:
        if path not in nodes:
            nodes[path] = cache.cached(path, nodes_step, "Pass 4/5, tagged nodes", lambda: None)

    platform_nodes = {}
    for path in paths:
        platform_nodes[path] = cache.cached(path, PLATFORM_NODES_STEP, "Extra pass, road platform nodes",
                                            lambda: scan_platform_nodes(path))
    clock = Clock()
    for path in paths:
        # Those pass 4 has kept already are read once (a cache made after this pass existed).
        extra = {record[0]: record for record in platform_nodes[path]}
        for nid, tags, lat, lon in nodes[path]:
            extra.pop(nid, None)
            if in_box(wide, lat, lon):
                read_node(source, nid, tags, lat, lon, in_box(region, lat, lon))
        for nid, tags, lat, lon in extra.values():
            if in_box(wide, lat, lon):
                read_node(source, nid, tags, lat, lon, in_box(region, lat, lon))
        storage = storages[path]
        for wid, tags, refs in ways[path]:
            points = located(storage, refs)
            if points:
                read_way(source, wid, tags, list(refs), points, region, wide)
    add_member_ways(source, paths, member_ways)
    log("  %s  %d station candidates, %d things around, %d entrances and doors, %d bus/tram stops, %d station buildings"
        % (clock, len(source.station_cands), len(source.around), len(source.tile_entrances) + len(source.doors),
           len(source.stops), len(source.buildings)))


def read_node(source, nid, tags, lat, lon, in_region):
    if in_region:
        modes = station_candidate(tags)
        if modes is not None:
            source.station_cands["node/%d" % nid] = {
                "type": "node", "id": nid, "tags": tags, "lat": lat, "lon": lon, "modes": modes, "rings": None}
    if is_around_node(tags):
        key = "node/%d" % nid
        source.around[key] = {"type": "node", "id": nid, "tags": tags, "lat": lat, "lon": lon}
        source.around_points[key] = [(lat, lon)]
    if in_region and tags.get("railway") in TILE_ENTRANCES:
        source.tile_entrances[nid] = (lat, lon, tags)
    if "entrance" in tags:
        if any(key in tags for key in ov.ENTRANCE_KEYS):
            source.doors[nid] = (lat, lon, tags)
        else:
            source.bare_doors[nid] = (lat, lon, tags)
    if in_region and is_stop_node(tags):
        source.stops["node/%d" % nid] = ("node", nid, lat, lon, tags)


def read_way(source, wid, tags, refs, points, region, wide):
    if is_any_platform(tags):
        # Every platform way, for the positions of the members of the bus and tram routes.
        source.platform_centers[wid] = bbox_center(points)
    if not any_in_box(wide, points):
        return
    in_region = any_in_box(region, points)
    if in_region:
        modes = station_candidate(tags)
        if modes is not None:
            lat, lon = bbox_center(points)
            closed = len(refs) > 3 and refs[0] == refs[-1]
            source.station_cands["way/%d" % wid] = {
                "type": "way", "id": wid, "tags": tags, "lat": lat, "lon": lon, "modes": modes,
                "rings": [points] if closed else None}
        if is_stop_way(tags):
            lat, lon = bbox_center(points)
            source.stops["way/%d" % wid] = ("way", wid, lat, lon, tags)
    if is_around_way(tags):
        key = "way/%d" % wid
        source.around[key] = {"type": "way", "id": wid, "tags": tags, "geometry": [[p[0], p[1]] for p in points]}
        source.around_points[key] = points
        if not is_any_platform(tags):
            source.around_refs[key] = refs
    if is_station_building(tags):
        closed = len(refs) > 3 and refs[0] == refs[-1]
        source.buildings.append((bounds(points), set(refs), [points] if closed else []))


def add_member_ways(source, paths, member_ways):
    for path in paths:
        for wid, refs in member_ways[path].items():
            if wid in source.member_ways:
                continue
            refs = list(refs)
            points = [p for p in (source.locate(ref) for ref in refs) if p is not None]
            source.member_ways[wid] = (refs, points)


# --- Routes and the member ways of relations --------------------------------------------

def route_candidates(source, region):
    """The routes with a member node in the region, and the ids of the routes that have to be
    tested by their ways: no member node in the region, but member nodes on both sides of
    it or none with a known place."""
    confirmed = set()
    by_ways = set()
    for routes in (source.rail_routes, source.bus_routes):
        for rid, (_, members) in routes.items():
            points = []
            for t, ref, _ in members:
                if t != "n":
                    continue
                place = source.locate(ref)
                if place is not None:
                    points.append(place)
                    if in_box(region, place[0], place[1]):
                        confirmed.add(rid)
                        break
            if rid in confirmed:
                continue
            if "w" in members.types and (not points or boxes_meet(bounds(points), region)):
                by_ways.add(rid)
    return confirmed, by_ways


def read_more_member_ways(paths, source, wanted, cache):
    """The member ways of the routes that only their ways can place in the region (with the
    locations of their nodes): two short passes more, cached by the ids they look for (they
    depend on the region)."""
    wanted = array("q", sorted(set(wanted) - set(source.member_ways)))
    if not len(wanted):
        return
    member_ways = {}
    for path in paths:
        member_ways[path] = cache.cached(
            path, "route-ways-%s" % ids_key(wanted),
            "Extra pass, %d member ways of routes" % len(wanted), lambda: scan_member_ways(path, wanted))
    needed = array("q")
    for path in paths:
        for refs in member_ways[path].values():
            needed.extend(refs)
    if len(needed):
        for path in paths:
            source.storages.append(cached_locations(
                path, needed, cache, "auto", "Extra pass, their %d node references" % len(needed)))
    add_member_ways(source, paths, member_ways)


def relation_rings(source, members):
    return [source.member_ways[ref][1] for t, ref, _ in members if t == "w" and ref in source.member_ways
            and source.member_ways[ref][1]]


def resolve_relations(source, region, wide):
    """Station relations, platform relations and station building multipolygons, with the
    geometry of their member ways."""
    for rid, (tags, members) in source.station_rels.items():
        points = [p for p in (source.locate(ref) for t, ref, _ in members if t == "n") if p is not None]
        rings = relation_rings(source, members)
        for ring in rings:
            points.extend(ring)
        if not points or not any_in_box(region, points):
            continue
        lat, lon = bbox_center(points)
        source.station_cands["relation/%d" % rid] = {
            "type": "relation", "id": rid, "tags": tags, "lat": lat, "lon": lon,
            "modes": station_candidate(tags), "rings": rings or None}
    for rid, (tags, members) in source.platform_rels.items():
        outlines = []
        points = []
        for t, ref, role in members:
            if t == "w" and ref in source.member_ways and source.member_ways[ref][1]:
                ring = source.member_ways[ref][1]
                outlines.append({"role": role, "points": [[p[0], p[1]] for p in ring]})
                points.extend(ring)
        if not points or not any_in_box(wide, points):
            continue
        key = "relation/%d" % rid
        element = {"type": "relation", "id": rid, "tags": tags}
        if outlines:
            element["geometry"] = outlines
        source.around[key] = element
        source.around_points[key] = points
    for rid, (tags, members) in source.building_rels.items():
        rings = relation_rings(source, members)
        points = [p for ring in rings for p in ring]
        if not points or not any_in_box(wide, points):
            continue
        refs = set()
        for t, ref, _ in members:
            if t == "w" and ref in source.member_ways:
                refs.update(source.member_ways[ref][0])
        source.buildings.append((bounds(points), refs, rings))


# --- The region -------------------------------------------------------------------------

def distance(lat1, lon1, lat2, lon2):
    return ov.distance_meters(lat1, lon1, lat2, lon2)


def stop_area_index(source):
    """'type/id' of every stop area member -> ids of its stop areas."""
    member_of = {}
    for aid, (_, members) in source.stop_areas.items():
        for t, ref, _ in members:
            member_of.setdefault("%s/%d" % (TYPE_NAMES[t], ref), []).append(aid)
    return member_of


def route_modes_of_members(source):
    """'type/id' of every stop or platform member of a rail route -> the route types."""
    modes = {}
    for tags, members in source.rail_routes.values():
        route = tags.get("route")
        for t, ref, role in members:
            if ov.stop_role(role):
                modes.setdefault("%s/%d" % (TYPE_NAMES[t], ref), set()).add(route)
    return modes


def select_stations(source, member_of, wanted_modes):
    """The stations: the candidates with their modes, a second drawing of a station dropped."""
    route_modes = route_modes_of_members(source)
    area_modes = {}
    for aid, (_, members) in source.stop_areas.items():
        found = set()
        for t, ref, _ in members:
            found |= route_modes.get("%s/%d" % (TYPE_NAMES[t], ref), set())
        if found:
            area_modes[aid] = found

    def priority(candidate):
        rail = candidate["tags"].get("railway") in ("station", "halt")
        if rail and candidate["type"] == "node":
            return 0
        return 1 if rail else 2

    candidates = sorted(source.station_cands.values(), key=lambda c: (priority(c), c["type"], c["id"]))
    kept = []
    grid = Grid()
    dropped = 0
    for candidate in candidates:
        key = "%s/%d" % (candidate["type"], candidate["id"])
        areas = set(member_of.get(key, []))
        modes = set(candidate["modes"])
        explicit = bool(modes)
        if candidate["tags"].get("railway") in ("station", "halt") or explicit:
            for aid in areas:
                modes |= area_modes.get(aid, set())
        if not modes:
            modes = {"train"}
        candidate["modes"] = [m for m in RAIL_MODES if m in modes]
        candidate["areas"] = areas
        candidate["words"] = name_words(candidate["tags"].get("name"))
        if priority(candidate) > 0 and is_duplicate(candidate, grid):
            dropped += 1
            continue
        kept.append(candidate)
        grid.add(candidate["lat"], candidate["lon"], candidate)
    stations = []
    for candidate in kept:
        if not set(candidate["modes"]) & wanted_modes:
            continue
        tags = candidate["tags"]
        stations.append({
            "type": candidate["type"],
            "id": candidate["id"],
            "name": tags.get("name", ""),
            "lat": candidate["lat"],
            "lon": candidate["lon"],
            "tags": tags,
            "modes": candidate["modes"],
        })
    stations.sort(key=lambda s: (s["name"], s["id"]))
    return stations, dropped


def is_duplicate(candidate, grid):
    """True when a station kept before is the same station drawn otherwise: in the same stop
    area, inside the outline of [candidate], or near it with the same name."""
    rings = candidate["rings"]
    for other in grid.near(candidate["lat"], candidate["lon"]) if not rings else all_near_rings(grid, rings):
        if candidate["areas"] & other["areas"]:
            return True
        if rings and inside(other["lat"], other["lon"], rings):
            return True
        if distance(candidate["lat"], candidate["lon"], other["lat"], other["lon"]) <= DUPLICATE_METERS:
            a, b = candidate["words"], other["words"]
            if not a or not b or a <= b or b <= a:
                return True
    return False


def all_near_rings(grid, rings):
    s, w, n, e = bounds([p for ring in rings for p in ring])
    found = {}
    for y in range(cell_of(s, w)[0] - 1, cell_of(n, e)[0] + 2):
        for x in range(cell_of(s, w)[1] - 1, cell_of(n, e)[1] + 2):
            for item in grid.cells.get((y, x), ()):
                found[id(item)] = item
    return list(found.values())


def assign_around(source, stations):
    """The platforms, stop positions, steps, elevators, escalators and footways within
    AROUND_METERS of each station (a point of them, as the Overpass fetch checks)."""
    grid = Grid()
    for station in stations:
        grid.add(station["lat"], station["lon"], station)
        for kind in ("platforms", "stop_positions", "steps", "elevators", "escalators", "footways"):
            station[kind] = []
    totals = {}
    for key, element in sorted(source.around.items(), key=lambda kv: (kv[1]["type"], kv[1]["id"])):
        kind = ov.kind_of(element)
        if kind is None:
            continue
        hit = {}
        for lat, lon in source.around_points[key]:
            for station in grid.near(lat, lon):
                if id(station) not in hit and distance(lat, lon, station["lat"], station["lon"]) <= AROUND_METERS:
                    hit[id(station)] = station
        for station in hit.values():
            station[kind].append(element)
            totals[kind] = totals.get(kind, 0) + 1
    return totals


NETWORK_KINDS = ("steps", "elevators", "escalators", "footways")


def network_doors(source, stations):
    """Node id of every door (entrance=*) that is a node of the steps, footways, corridors or
    elevators of a station -> the indices of those stations. Such a door leads into the
    station network whatever its tags and however far from the station node it is."""
    doors = {}
    for index, station in enumerate(stations):
        for kind in NETWORK_KINDS:
            for element in station[kind]:
                for ref in source.around_refs.get("%s/%d" % (element["type"], element["id"]), ()):
                    if ref in source.doors or ref in source.bare_doors:
                        doors.setdefault(ref, set()).add(index)
    return doors


def drop_empty_train_stations(stations):
    """The stations without those that are only of trains ("modes" ["train"]) and have no
    platform and no stop position near them: operating points and junctions mapped as
    railway=station (Biesdorfer Kreuz, Berlin Stadtforst), no stations of passengers."""
    kept = []
    dropped = []
    for station in stations:
        if station["modes"] == ["train"] and not station["platforms"] and not station["stop_positions"]:
            dropped.append(station["name"] or "%s/%d" % (station["type"], station["id"]))
        else:
            kept.append(station)
    return kept, dropped


def build_entrances(source, stations):
    grid = Grid()
    for station in stations:
        grid.add(station["lat"], station["lon"], station)
    order = {id(s): i for i, s in enumerate(stations)}
    on_network = network_doors(source, stations)

    def within(lat, lon):
        return [s for s in grid.near(lat, lon) if distance(lat, lon, s["lat"], s["lon"]) <= AROUND_METERS]

    def near_stations(nid, lat, lon):
        found = within(lat, lon)
        for index in on_network.get(nid, ()):
            if stations[index] not in found:
                found.append(stations[index])
        found.sort(key=lambda s: order[id(s)])
        return ["%s/%d" % (s["type"], s["id"]) for s in found]

    chosen = dict(source.tile_entrances)
    # The doors the Overpass fetch takes too: with one of ENTRANCE_KEYS, near a station.
    for nid, (lat, lon, tags) in source.doors.items():
        if nid not in chosen and within(lat, lon):
            chosen[nid] = (lat, lon, tags)
    # Besides, any door the steps, footways, corridors or elevators of a station pass through
    # (most doors of station halls, malls and hotels carry no name, ref, wheelchair or level
    # tag, and a mall door may lie farther than AROUND_METERS): marked "viaNetwork", as the
    # build has to find out whether it is a door of the station or of a building on the way.
    via_network = set()
    for nid in on_network:
        if nid not in chosen:
            chosen[nid] = source.doors.get(nid) or source.bare_doors[nid]
            via_network.add(nid)

    buildings = Grid()
    for building in source.buildings:
        buildings.add_box(building[0], building)
    entrances = []
    in_building = 0
    doors = 0
    for nid in sorted(chosen):
        lat, lon, tags = chosen[nid]
        entrance = {"id": nid, "lat": lat, "lon": lon}
        for key in ov.ENTRANCE_KEYS:
            if key in tags:
                entrance[key] = tags[key]
        entrance["stations"] = near_stations(nid, lat, lon)
        entrance["tags"] = tags
        if nid in via_network:
            entrance["viaNetwork"] = True
        if tags.get("railway") not in TRANSIT_ENTRANCES:
            doors += 1
            entrance["stationBuilding"] = any(
                nid in refs or (rings and box[0] <= lat <= box[2] and box[1] <= lon <= box[3] and inside(lat, lon, rings))
                for box, refs, rings in buildings.at(lat, lon))
            in_building += entrance["stationBuilding"]
        entrances.append(entrance)
    return entrances, doors, in_building


def build_stops(source, member_of):
    stops = []
    areas = set()
    for key, (t, sid, lat, lon, tags) in source.stops.items():
        kind = "stop_position" if tags.get("public_transport") == "stop_position" else "platform"
        stop = {
            "type": t, "id": sid, "kind": kind, "lat": lat, "lon": lon,
            "bus": is_bus_stop_tags(tags),
            "tram": tags.get("tram") == "yes" or tags.get("railway") == "tram_stop",
        }
        for name in ov.STOP_KEYS:
            if name in tags:
                stop[name] = tags[name]
        stop["stopAreas"] = sorted(set(member_of.get(key, [])))
        areas.update(stop["stopAreas"])
        stop["tags"] = tags
        stops.append(stop)
    stops.sort(key=lambda s: (s["type"], s["id"]))
    return stops, areas


def build_bus_routes(source, ids):
    routes = []
    for rid in sorted(ids):
        kept, members = source.bus_routes[rid]
        route = {"id": rid}
        route.update(kept)
        items = []
        for t, ref, role in members:
            if not ov.stop_role(role):
                continue
            item = [t, ref, role]
            place = None
            if t == "n":
                place = source.locate(ref)
            elif t == "w":
                place = source.platform_centers.get(ref)
            if place is not None:
                item += [place[0], place[1]]
            items.append(item)
        route["m"] = items
        routes.append(route)
    return routes


def route_in_region_by_ways(source, members, region):
    for t, ref, _ in members:
        if t == "w" and ref in source.member_ways and any_in_box(region, source.member_ways[ref][1]):
            return True
    return False


def header_timestamp(path):
    try:
        reader = osmium.io.Reader(path, osmium.osm.NOTHING)
        try:
            return reader.header().get("osmosis_replication_timestamp") or ""
        finally:
            reader.close()
    except Exception:  # noqa: BLE001 (an old file without a header is fine)
        return ""


def parse_bbox(text):
    parts = [float(p) for p in text.replace(" ", "").split(",")]
    if len(parts) != 4 or parts[0] >= parts[2] or parts[1] >= parts[3]:
        raise argparse.ArgumentTypeError("--bbox wants south,west,north,east")
    return tuple(parts)


def main(argv=None):
    parser = argparse.ArgumentParser(
        description="Write tools/osm-stations.json from local .osm.pbf files (instead of Overpass).")
    parser.add_argument("files", nargs="+", help=".osm.pbf (or .osm) files, e.g. berlin-latest.osm.pbf")
    where = parser.add_mutually_exclusive_group()
    where.add_argument("--region", choices=sorted(REGIONS), default="berlin",
                       help="a prepared bounding box (default: berlin, as the Overpass fetch)")
    where.add_argument("--bbox", type=parse_bbox, help="south,west,north,east in degrees")
    parser.add_argument("--modes", default="subway,light_rail,train",
                        help="the station modes to keep, of subway,light_rail,train (default: all)")
    parser.add_argument("--out", default=OUTPUT, help="output file (default: tools/osm-stations.json)")
    parser.add_argument("--index", default="auto",
                        help="location index of the needed nodes: auto (default: a file in the cache, or "
                             "sparse_mem_array without cache), flex_mem, sparse_mem_array or "
                             "sparse_file_array,/path/file")
    parser.add_argument("--cache-dir", default=None,
                        help="where the results of the passes are kept (default: .extract-cache/ beside "
                             "the first input file); a run broken off, or one for another region, goes on from there")
    parser.add_argument("--no-cache", action="store_true", help="keep no pass results")
    parser.add_argument("--walk-graph", nargs="?", const=os.path.join(HERE, "osm-walk-graph.json"), default=None, metavar="FILE",
                        help="also write the walking network around the stations for tools/build_transfer_walks.py "
                             "(default file: tools/osm-walk-graph.json; see tools/walk_graph.py)")
    args = parser.parse_args(argv)

    for path in args.files:
        if not os.path.exists(path):
            raise SystemExit("No such file: %s" % path)
    if not args.cache_dir:
        args.cache_dir = os.path.join(os.path.dirname(os.path.abspath(args.files[0])), ".extract-cache")
    wanted_modes = split_values(args.modes.replace(",", ";"))
    if not wanted_modes or wanted_modes - set(RAIL_MODES):
        raise SystemExit("--modes takes subway, light_rail and train")
    region = args.bbox or REGIONS[args.region]
    region_name = "bbox" if args.bbox else args.region
    # Things around a station at the edge of the region and doors near it may lie outside it.
    wide = expand(region, AROUND_METERS + 50)
    out = os.path.abspath(args.out)
    clock = Clock()
    log("Region %s: south %.4f, west %.4f, north %.4f, east %.4f" % ((region_name,) + tuple(region)))

    cache = Cache(None if args.no_cache else args.cache_dir)
    # Millions of small objects and no cycles worth a collection: the collector only slows
    # the loading of the cache down.
    gc.disable()
    prepare_node_pass(args.files, cache)
    source = Source()
    read_relations(args.files, source, cache)
    wanted = set()
    for routes in (source.station_rels, source.platform_rels, source.building_rels):
        for _, members in routes.values():
            wanted.update(ref for t, ref, _ in members if t == "w")
    read_nodes_and_ways(args.files, source, region, wide, args.index, cache, wanted)
    confirmed, by_ways = route_candidates(source, region)
    more = set()
    for rid in by_ways:
        members = (source.rail_routes.get(rid) or source.bus_routes[rid])[1]
        more.update(ref for t, ref, _ in members if t == "w")
    read_more_member_ways(args.files, source, more, cache)
    resolve_relations(source, region, wide)
    for rid in by_ways:
        members = (source.rail_routes.get(rid) or source.bus_routes[rid])[1]
        if route_in_region_by_ways(source, members, region):
            confirmed.add(rid)

    log("Building %s" % os.path.relpath(out))
    member_of = stop_area_index(source)
    stations, dropped = select_stations(source, member_of, wanted_modes)
    counts = {}
    for station in stations:
        label = "+".join(station["modes"])
        counts[label] = counts.get(label, 0) + 1
    log("  %d stations (%d second drawings of a station dropped): %s"
        % (len(stations), dropped, ", ".join("%s %d" % kv for kv in sorted(counts.items()))))
    totals = assign_around(source, stations)
    stations, empty = drop_empty_train_stations(stations)
    if empty:
        log("  %d stations of trains alone without platforms or stop positions dropped "
            "(operating points, junctions): %s" % (len(empty), ", ".join(empty)))
    entrances, doors, in_building = build_entrances(source, stations)
    stops, stop_areas_of_stops = build_stops(source, member_of)

    area_ids = set(stop_areas_of_stops)
    for station in stations:
        area_ids.update(member_of.get("%s/%d" % (station["type"], station["id"]), []))
    areas = [{"id": aid, "tags": source.stop_areas[aid][0], "members": source.stop_areas[aid][1].records()}
             for aid in sorted(area_ids)]
    routes = [{"id": rid, "tags": source.rail_routes[rid][0], "members": source.rail_routes[rid][1].records()}
              for rid in sorted(confirmed) if rid in source.rail_routes]
    bus_routes = build_bus_routes(source, [rid for rid in confirmed if rid in source.bus_routes])

    result = {
        "license": "Data (c) OpenStreetMap contributors, ODbL 1.0, https://www.openstreetmap.org/copyright",
        "generated": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "radiusMeters": AROUND_METERS,
        "stations": stations,
        "stopAreas": areas,
        "routes": routes,
        "entrances": entrances,
        "stopPositions": stops,
        "busRoutes": bus_routes,
        "source": {
            "files": [os.path.basename(p) for p in args.files],
            "dataTimestamps": [header_timestamp(p) for p in args.files],
            "region": region_name,
            "bbox": list(region),
            "modes": sorted(wanted_modes),
        },
    }
    if os.path.exists(out):
        backup = out + ".bak"
        shutil.copyfile(out, backup)
        log("  previous file kept as %s" % os.path.relpath(backup))
    partial = out + ".part"
    with open(partial, "w", encoding="utf-8") as handle:
        json.dump(result, handle, ensure_ascii=False, separators=(",", ":"))
    os.replace(partial, out)

    log("")
    log("Written to %s in %s" % (os.path.relpath(out), clock))
    log("Stations:        %d" % len(stations))
    log("Stop areas:      %d" % len(areas))
    for kind, label in (("platforms", "Platforms"), ("stop_positions", "Stop positions"), ("steps", "Steps"),
                        ("elevators", "Elevators"), ("escalators", "Escalators"), ("footways", "Footways")):
        log("%-16s %d" % (label + ":", totals.get(kind, 0)))
    log("Route relations: %d" % len(routes))
    log("Entrances:       %d (%d other doors, %d of them in a station building)" % (len(entrances), doors, in_building))
    log("Bus/tram stops:  %d" % len(stops))
    log("Bus/tram routes: %d" % len(bus_routes))
    if args.walk_graph:
        import walk_graph  # noqa: E402  (imports this module)
        log("")
        log("Walking network around the stations")
        graph = walk_graph.build_graph(args.files, stations, cache)
        walk_graph.write_graph(graph, os.path.abspath(args.walk_graph))
    return 0


if __name__ == "__main__":
    sys.exit(main())
