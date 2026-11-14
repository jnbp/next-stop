#!/usr/bin/env python3
"""
The pedestrian network in and around every rail station of an extract, from local .osm.pbf
files, for tools/build_transfer_walks.py. Writes tools/osm-walk-graph.json:

    python3 tools/walk_graph.py tools/osm-pbf/brandenburg-latest.osm.pbf
    python3 tools/walk_graph.py --stations /tmp/de.json --out /tmp/de-walk.json tools/osm-pbf/germany-latest.osm.pbf

The stations come from tools/osm-stations.json (written by tools/extract_osm_pbf.py, which
also calls this with --walk-graph). Kept: every way a person may walk on (footways, paths,
steps, corridors, elevators, pedestrian areas, streets, rail and bus platforms, indoor
corridors and areas) with a node within WALK_RADIUS_METERS of a station's box (its node and
its rail platforms), cut to the part near the box; the elevator nodes and the doors on them.

Output (compact, coordinates with 6 decimals, about 0.1 m):
    "nodes": [lat, lon, lat, lon, ...]               node i at 2i, 2i+1
    "ways":  [[kind, level, steps, area, i, j, ...]]  kind: w walk, s steps, e escalator,
             l elevator, r rail platform, p other platform (bus, tram), a walkable area;
             level the level tag ("" none); steps the step_count (0 unknown); area 1 for a
             closed area to be crossed in a straight line
    "elevators": [i, ...]  highway=elevator nodes   "doors": [i, ...]  entrance=* nodes

Read in three steps, each cached beside the input file (.extract-cache/): the walkable ways
without locations (their tags decide), the locations of a few probe nodes of each way to
pick the ways near a station (so for Germany not all node references are located), and the
locations of all nodes of those ways.

Data (c) OpenStreetMap contributors, ODbL 1.0.
"""

import argparse
import json
import math
import os
import sys
import time
from array import array

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import extract_osm_pbf as ex  # noqa: E402  (pyosmium, cache and location index)

osmium = ex.osmium

STATIONS = os.path.join(HERE, "osm-stations.json")
OUTPUT = os.path.join(HERE, "osm-walk-graph.json")

# How far around a station's box (its node and rail platforms) the network is kept: the bus
# and tram stops within 250 m of a platform and the way to them.
WALK_RADIUS_METERS = 300.0
# A probe node (every PROBE_EVERY-th node, the first and the last of a way) this close to a
# box makes a way a candidate; nodes of streets lie at most a few hundred metres apart.
PROBE_MARGIN_METERS = 1500.0
PROBE_EVERY = 8
# Nodes of a kept way this far beyond the radius are cut off (one more stays for the edge).
CUT_MARGIN_METERS = 50.0
GRAPH_VERSION = 1

# Streets and paths a person may walk on (the street stands for its sidewalks).
WALK_HIGHWAYS = {
    "footway", "path", "pedestrian", "steps", "corridor", "elevator", "platform", "living_street",
    "residential", "service", "unclassified", "tertiary", "tertiary_link", "secondary",
    "secondary_link", "primary", "primary_link", "trunk", "trunk_link", "track", "cycleway",
    "bridleway", "road", "busway",
}
NO_FOOT = {"no", "private", "use_sidepath"}
FOOT_OK = {"yes", "designated", "permissive", "destination"}
WALK_INDOOR = {"corridor", "area", "room"}


def walk_kind(tags):
    """(kind, level, steps, area) of a way with [tags] a person may walk on, else None."""
    highway = tags.get("highway")
    railway = tags.get("railway")
    pt = tags.get("public_transport")
    indoor = tags.get("indoor")
    if tags.get("foot") in NO_FOOT:
        return None
    if tags.get("access") in ("no", "private") and tags.get("foot") not in FOOT_OK:
        return None
    if highway in ("cycleway", "bridleway") and tags.get("foot") not in FOOT_OK:
        return None
    if highway in ("trunk", "trunk_link") and tags.get("motorroad") == "yes":
        return None
    if tags.get("disused") == "yes" or tags.get("abandoned") == "yes":
        return None
    area = 1 if tags.get("area") == "yes" else 0
    if railway == "platform" or (pt == "platform" and tags.get("bus") != "yes" and tags.get("tram") != "yes"
                                 and highway not in ("bus_stop",) and (tags.get("train") == "yes"
                                                                       or tags.get("subway") == "yes"
                                                                       or tags.get("light_rail") == "yes")):
        kind = "r"
        area = 1  # a closed platform outline is crossed straight; an open one is a line
    elif pt == "platform" or highway == "platform":
        kind = "p"
        area = 1
    elif highway == "elevator":
        kind = "l"
    elif highway == "steps":
        kind = "e" if tags.get("conveying") in ("yes", "forward", "backward", "reversible") else "s"
    elif highway in WALK_HIGHWAYS:
        kind = "w"
        if highway == "pedestrian" and tags.get("area") == "yes":
            area = 1
    elif indoor in WALK_INDOOR:
        kind = "a" if indoor != "corridor" else "w"
        area = 1 if indoor != "corridor" else area
    else:
        return None
    level = (tags.get("level") or tags.get("level:ref") or "").strip()
    steps = 0
    if kind == "s":
        try:
            steps = int(float(tags.get("step_count", "0").split(";")[0]))
        except ValueError:
            steps = 0
    return kind, level, steps, area


def scan_walk_ways(path):
    """(id, (kind, level, steps, area), node ids) of the ways a person may walk on."""
    clock = ex.Clock()
    processor = osmium.FileProcessor(path, osmium.osm.WAY).with_filter(
        osmium.filter.KeyFilter("highway", "railway", "public_transport", "indoor"))
    records = []
    count = 0
    for way in processor:
        count += 1
        kind = walk_kind(ex.tag_dict(way.tags))
        if kind is not None and len(way.nodes) >= 2:
            records.append((way.id, kind, array("q", [node.ref for node in way.nodes])))
    ex.log("  %s  %d ways read, %d walkable" % (clock, count, len(records)))
    return records


def scan_walk_nodes(path):
    """(elevator node ids, door node ids)."""
    clock = ex.Clock()
    elevators = array("q")
    doors = array("q")
    processor = osmium.FileProcessor(path, osmium.osm.NODE).with_filter(osmium.filter.KeyFilter("highway", "entrance"))
    for node in processor:
        tags = node.tags
        if tags.get("highway") == "elevator":
            elevators.append(node.id)
        if "entrance" in tags:
            doors.append(node.id)
    ex.log("  %s  %d elevator nodes, %d doors" % (clock, len(elevators), len(doors)))
    return elevators, doors


def scan_platform_relations(path):
    """(id, (kind, level, steps, area), outer way ids) of the platforms drawn as multipolygons."""
    clock = ex.Clock()
    processor = osmium.FileProcessor(path, osmium.osm.RELATION).with_filter(
        osmium.filter.TagFilter(("railway", "platform"), ("public_transport", "platform"), ("highway", "pedestrian"),
                                ("indoor", "area"), ("indoor", "corridor")))
    records = []
    for relation in processor:
        tags = ex.tag_dict(relation.tags)
        if tags.get("type") != "multipolygon":
            continue
        kind = walk_kind(dict(tags, area="yes"))
        if kind is None:
            continue
        ways = array("q", [m.ref for m in relation.members if m.type == "w" and m.role in ("outer", "")])
        if ways:
            records.append((relation.id, kind, ways))
    ex.log("  %s  %d platforms and areas drawn as multipolygons" % (clock, len(records)))
    return records


def join_rings(parts):
    """Lists of node ids joined end to end into closed rings where they meet."""
    rings = []
    open_parts = [list(p) for p in parts if len(p) >= 2]
    while open_parts:
        ring = open_parts.pop(0)
        changed = True
        while changed and ring[0] != ring[-1]:
            changed = False
            for i, part in enumerate(open_parts):
                if part[0] == ring[-1]:
                    ring += part[1:]
                elif part[-1] == ring[-1]:
                    ring += part[-2::-1]
                elif part[-1] == ring[0]:
                    ring = part[:-1] + ring
                elif part[0] == ring[0]:
                    ring = part[:0:-1] + ring
                else:
                    continue
                open_parts.pop(i)
                changed = True
                break
        rings.append(ring)
    return rings


def relation_ways(path, relations, cache):
    """The outlines of the multipolygon [relations] as ways (negative ids): one per ring."""
    wanted = array("q", sorted({w for _, _, ways in relations for w in ways}))
    members = cache.cached(path, "walk-members-%s" % ex.ids_key(wanted), "Walk 1/3, outlines of multipolygons",
                           lambda: ex.scan_member_ways(path, wanted))
    out = []
    for rid, kind, ways in relations:
        parts = [list(members[w]) for w in ways if w in members]
        for k, ring in enumerate(join_rings(parts)):
            out.append((-(rid * 100 + k), kind, array("q", ring)))
    return out


# --- Station boxes ----------------------------------------------------------------------

def element_points(element):
    if element.get("type") == "node" and "lat" in element:
        return [(element["lat"], element["lon"])]
    geometry = element.get("geometry") or []
    points = []
    for item in geometry:
        if isinstance(item, dict):
            points.extend(tuple(p) for p in item.get("points", []))
        else:
            points.append(tuple(item))
    return points


def station_boxes(stations, radius):
    """(south, west, north, east) per station: its node and rail platforms, plus [radius]."""
    boxes = []
    for station in stations:
        points = [(station["lat"], station["lon"])]
        for item in station.get("platforms", []):
            tags = item.get("tags", {})
            if tags.get("bus") == "yes" or tags.get("tram") == "yes" or tags.get("highway") == "bus_stop":
                continue
            item_points = element_points(item)
            # A platform far from the station (a bad match) does not stretch its box.
            if item_points and min(ex.distance(station["lat"], station["lon"], a, b) for a, b in item_points) < 700:
                points.extend(item_points)
        boxes.append(ex.expand(ex.bounds(points), radius))
    return boxes


class BoxGrid(object):
    """Which of many boxes hold a point, by cells of ex.CELL degrees."""

    def __init__(self, boxes):
        self.cells = {}
        for index, box in enumerate(boxes):
            for y in range(int(math.floor(box[0] / ex.CELL)), int(math.floor(box[2] / ex.CELL)) + 1):
                for x in range(int(math.floor(box[1] / ex.CELL)), int(math.floor(box[3] / ex.CELL)) + 1):
                    self.cells.setdefault((y, x), []).append(index)
        self.boxes = boxes

    def hit(self, lat, lon):
        for index in self.cells.get((int(math.floor(lat / ex.CELL)), int(math.floor(lon / ex.CELL))), ()):
            box = self.boxes[index]
            if box[0] <= lat <= box[2] and box[1] <= lon <= box[3]:
                return True
        return False


# --- The graph --------------------------------------------------------------------------

def locations(path, needed, cache, label):
    """A location index of the nodes in [needed] (cached by their ids)."""
    return ex.cached_locations(path, needed, cache, "auto", label)


def build_graph(paths, stations, cache, radius=WALK_RADIUS_METERS):
    clock = ex.Clock()
    ways = []
    elevators = set()
    doors = set()
    for path in paths:
        ways.extend(cache.cached(path, "walk-ways-v%d" % GRAPH_VERSION, "Walk 1/3, walkable ways",
                                 lambda: scan_walk_ways(path)))
        e, d = cache.cached(path, "walk-nodes-v%d" % GRAPH_VERSION, "Walk 1/3, elevators and doors",
                            lambda: scan_walk_nodes(path))
        elevators.update(e)
        doors.update(d)
        relations = cache.cached(path, "walk-relations-v%d" % GRAPH_VERSION, "Walk 1/3, multipolygons",
                                 lambda: scan_platform_relations(path))
        ways.extend(relation_ways(path, relations, cache))
    # Ways in more than one file once.
    seen = set()
    unique = []
    for record in ways:
        if record[0] not in seen:
            seen.add(record[0])
            unique.append(record)
    ways = unique
    del seen
    near = BoxGrid(station_boxes(stations, radius + CUT_MARGIN_METERS))
    probe_grid = BoxGrid(station_boxes(stations, radius + PROBE_MARGIN_METERS))

    probes = array("q")
    for _, _, refs in ways:
        probes.extend(refs[::PROBE_EVERY])
        probes.append(refs[-1])
    probes = array("q", sorted(set(probes)))
    ex.log("  %d walkable ways, %d probe nodes" % (len(ways), len(probes)))
    storages = [locations(path, probes, cache, "Walk 2/3, probe locations") for path in paths]

    def locate(storages, ref):
        for storage in storages:
            try:
                location = storage.get(ref)
            except KeyError:
                continue
            if location.valid():
                return location.lat, location.lon
        return None

    candidates = []
    for record in ways:
        refs = record[2]
        for ref in list(refs[::PROBE_EVERY]) + [refs[-1]]:
            point = locate(storages, ref)
            if point and probe_grid.hit(*point):
                candidates.append(record)
                break
    del storages, probes
    needed = array("q")
    for record in candidates:
        needed.extend(record[2])
    needed = array("q", sorted(set(needed)))
    ex.log("  %d candidate ways near stations, %d nodes to locate" % (len(candidates), len(needed)))
    storages = [locations(path, needed, cache, "Walk 3/3, node locations") for path in paths]

    index_of = {}
    nodes = []
    out_ways = []
    for wid, (kind, level, steps, area), refs in candidates:
        points = [locate(storages, ref) for ref in refs]
        inside = [p is not None and near.hit(*p) for p in points]
        if not any(inside):
            continue
        # Keep the runs of nodes near a station, with one node more at each end.
        keep = [False] * len(refs)
        for i, flag in enumerate(inside):
            if flag:
                for j in (i - 1, i, i + 1):
                    if 0 <= j < len(refs) and points[j] is not None:
                        keep[j] = True
        closed = len(refs) > 3 and refs[0] == refs[-1]
        is_area = 1 if (area and closed and all(keep)) else 0
        run = []
        for i, ref in enumerate(refs):
            if keep[i]:
                if ref not in index_of:
                    index_of[ref] = len(nodes)
                    nodes.append(points[i])
                run.append(index_of[ref])
            elif run:
                if len(run) >= 2:
                    out_ways.append([kind, level, steps, is_area] + run)
                run = []
        if len(run) >= 2:
            out_ways.append([kind, level, steps, is_area] + run)
    flat = []
    for lat, lon in nodes:
        flat.append(round(lat, 6))
        flat.append(round(lon, 6))
    graph = {
        "license": "Data (c) OpenStreetMap contributors, ODbL 1.0, https://www.openstreetmap.org/copyright",
        "generated": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "version": GRAPH_VERSION,
        "radiusMeters": radius,
        "nodes": flat,
        "ways": out_ways,
        "elevators": sorted(index_of[n] for n in elevators if n in index_of),
        "doors": sorted(index_of[n] for n in doors if n in index_of),
    }
    ex.log("  %s  walk graph: %d nodes, %d ways, %d elevators, %d doors"
           % (clock, len(nodes), len(out_ways), len(graph["elevators"]), len(graph["doors"])))
    return graph


def write_graph(graph, out):
    partial = out + ".part"
    with open(partial, "w", encoding="utf-8") as handle:
        json.dump(graph, handle, ensure_ascii=False, separators=(",", ":"))
    os.replace(partial, out)
    ex.log("Written %s: %d bytes" % (os.path.relpath(out), os.path.getsize(out)))


def main(argv=None):
    parser = argparse.ArgumentParser(description="Write the walking network around the stations (tools/osm-walk-graph.json).")
    parser.add_argument("files", nargs="+", help=".osm.pbf files, the same as for extract_osm_pbf.py")
    parser.add_argument("--stations", default=STATIONS, help="the extract with the stations (default: tools/osm-stations.json)")
    parser.add_argument("--out", default=OUTPUT, help="output file (default: tools/osm-walk-graph.json)")
    parser.add_argument("--radius", type=float, default=WALK_RADIUS_METERS, help="metres around a station's box")
    parser.add_argument("--cache-dir", default=None, help="as for extract_osm_pbf.py (default: .extract-cache/ beside the first file)")
    parser.add_argument("--no-cache", action="store_true")
    args = parser.parse_args(argv)
    if not args.cache_dir:
        args.cache_dir = os.path.join(os.path.dirname(os.path.abspath(args.files[0])), ".extract-cache")
    with open(args.stations, "r", encoding="utf-8") as handle:
        stations = json.load(handle)["stations"]
    cache = ex.Cache(None if args.no_cache else args.cache_dir)
    graph = build_graph(args.files, stations, cache, args.radius)
    write_graph(graph, os.path.abspath(args.out))
    return 0


if __name__ == "__main__":
    sys.exit(main())
