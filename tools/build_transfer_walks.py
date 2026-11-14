#!/usr/bin/env python3
"""
Exact walks of the changes at every station complex, over the walking network of
OpenStreetMap: between each pair of rail platforms (S-Bahn, U-Bahn, regional and long-distance
trains) and between each rail platform and each bus or tram stop position within
STOP_RADIUS_METERS of it. Writes app/src/main/assets/transfer-walks-berlin.json:

    python3 tools/walk_graph.py tools/osm-pbf/brandenburg-latest.osm.pbf   # tools/osm-walk-graph.json
    python3 tools/build_station_asset.py                                   # osm-berlin.json
    python3 tools/build_transfer_walks.py

Inputs: tools/osm-stations.json (the extract, for the outlines of the platforms),
tools/osm-walk-graph.json (tools/walk_graph.py) and the station asset osm-berlin.json (whose
platforms and stop positions the walks are keyed by, so both assets must come from the same
extract).

A walk is the shortest way (Dijkstra) from any point of the one platform to any point of the
other platform or to the stop position (and between two stops of the complex at least
STOP_PAIR_METERS apart, through the station or around it), at WALK_METERS_PER_MINUTE; steps cost
STEP_SECONDS per step more (step_count; else 20 per level the steps join where their level tag
says, else one per 30 cm of the flight up to 25), escalators ESCALATOR_METERS_PER_SECOND but
at least ESCALATOR_LEVEL_SECONDS per level, a change between the ways at an elevator
ELEVATOR_SECONDS; and a walk between two levels at least LEVEL_CHANGE_SECONDS per level.
A second search without steps and escalators gives the step-free minutes. Where the network
does not join the two, or its way is more than SUSPICIOUS_DETOUR times longer (a gap in the
mapped network), the straight line x DETOUR at the same pace stands in ("estimate").

Asset (one object per station complex, in the order of osm-berlin.json):
    {"id": first id of the complex, "n": name,
     "p": [[lat, lon, lat, lon], ...]   the platform axes as in osm-berlin.json (index 0..P-1)
     "s": [[lat, lon], ...]             the stop positions (index P, P+1, ...)
     "w": [[i, j, seconds, stepFreeSeconds, estimate], ...]}
seconds rounded up (the app makes minutes of them); stepFreeSeconds 0 where no step-free way
is known; estimate 1 for the straight line. Each pair once (i < j); a walk counts both ways.

Data (c) OpenStreetMap contributors, ODbL 1.0.
"""

import argparse
import heapq
import json
import math
import os
import sys
import time
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)
import build_station_asset as bsa  # noqa: E402

GRAPH = os.path.join(HERE, "osm-walk-graph.json")
STATION_ASSET = bsa.STATION_ASSET
OUTPUT = os.path.join(bsa.ASSETS, "transfer-walks-berlin.json")

WALK_METERS_PER_MINUTE = 75.0
SPEED = WALK_METERS_PER_MINUTE / 60.0
STEP_SECONDS = 1.0
STEP_DEPTH_METERS = 0.3
ESTIMATED_FLIGHT_STEPS = 25
# Steps of a flight per level where the steps join tagged levels (about 3.5 m).
LEVEL_STEPS = 20
ESCALATOR_METERS_PER_SECOND = 0.75
ESCALATOR_LEVEL_SECONDS = 15.0
ELEVATOR_SECONDS = 60.0
# A change of level takes at least this long per level, however the network joins the two
# (a footway with no level between two levels hides stairs or a ramp).
LEVEL_CHANGE_SECONDS = 30.0
DETOUR = 1.3
STOP_RADIUS_METERS = 250.0
# A stop position joins the network at the nodes this close to it (at street level).
STOP_ATTACH_METERS = 30.0
STOP_ATTACH_NODES = 8
# Two stops of a complex at least this far apart get their walk too (closer ones are one stop).
STOP_PAIR_METERS = 60.0
# The loose end of a way joins the nearest node of another way this close (same level).
SNAP_METERS = 6.0
# The search gives up beyond this (the straight line stands in).
MAX_SECONDS = 15 * 60.0
# Points of an area farther apart than this are not all joined (a sample of them is).
AREA_MAX_NODES = 60
# A way over the network longer than this many times the estimate and this much more is taken
# for a gap in the network: the estimate stands in.
SUSPICIOUS_DETOUR = 3.0
SUSPICIOUS_EXTRA_SECONDS = 120.0
GROUND = {"", "0"}
VERTICAL = {"s", "e", "l"}
CELL = 0.0002


def log(text):
    print(text, flush=True)


def meters(a, b):
    return bsa.meters(a[0], a[1], b[0], b[1])


def levels_of(text):
    return {part.strip() for part in text.split(";")} if text else {""}


def level_ok(a, b):
    """True when nodes with the levels [a] and [b] (sets) may be on one level."""
    if a & b:
        return True
    return bool(a & GROUND and b & GROUND)


class Graph(object):
    def __init__(self, raw):
        flat = raw["nodes"]
        self.points = [(flat[i], flat[i + 1]) for i in range(0, len(flat), 2)]
        n = len(self.points)
        self.levels = [set() for _ in range(n)]
        self.kinds = [set() for _ in range(n)]
        self.doors = set(raw.get("doors", []))
        elevators = set(raw.get("elevators", []))
        self.adj = [[] for _ in range(n)]
        self.way_of = defaultdict(set)
        ways = raw["ways"]
        for w, way in enumerate(ways):
            kind, level = way[0], way[1]
            values = levels_of(level)
            for node in way[4:]:
                # Steps, escalators and lifts join levels: a node takes their levels only where
                # no level way meets it (the foot of the stairs is on the level of its footway).
                if kind not in VERTICAL or not self.kinds[node] - VERTICAL:
                    if kind not in VERTICAL and not self.kinds[node] - VERTICAL:
                        self.levels[node] = set()
                    self.levels[node] |= values
                self.kinds[node].add(kind)
                self.way_of[node].add(w)
        # An elevator node joins its ways only through the elevator: each way gets its own
        # copy of the node (a port), and ports are joined by ELEVATOR_SECONDS.
        port = {}
        for e in elevators:
            ws = sorted(self.way_of.get(e, ()))
            for k, w in enumerate(ws):
                # Each port is on the level of its own way only.
                if k == 0:
                    port[(e, w)] = e
                    self.levels[e] = levels_of(ways[w][1])
                    self.kinds[e] = {ways[w][0]}
                    continue
                self.points.append(self.points[e])
                self.levels.append(levels_of(ways[w][1]))
                self.kinds.append({ways[w][0]})
                self.adj.append([])
                port[(e, w)] = len(self.points) - 1
            ports = [port[(e, w)] for w in ws]
            for x in range(len(ports)):
                for y in range(x + 1, len(ports)):
                    self.edge(ports[x], ports[y], ELEVATOR_SECONDS, True)
        self.areas = []
        for w, way in enumerate(ways):
            kind, steps, area = way[0], way[2], way[3]
            nodes = [port.get((v, w), v) for v in way[4:]]
            if area and len(nodes) > 3 and nodes[0] == nodes[-1]:
                ring = nodes[:-1]
                for x in range(len(ring)):
                    a, b = ring[x], ring[(x + 1) % len(ring)]
                    self.edge(a, b, self.cost(kind, meters(self.points[a], self.points[b])), kind not in ("s", "e"))
                sample = ring if len(ring) <= AREA_MAX_NODES else ring[::int(math.ceil(len(ring) / float(AREA_MAX_NODES)))]
                for x in range(len(sample)):
                    for y in range(x + 2, len(sample)):
                        a, b = sample[x], sample[y]
                        self.edge(a, b, self.cost(kind, meters(self.points[a], self.points[b])), kind not in ("s", "e"))
                if kind in ("r", "p", "a"):
                    self.areas.append((kind, levels_of(way[1]), [self.points[v] for v in ring], ring))
                continue
            lengths = [meters(self.points[nodes[k]], self.points[nodes[k + 1]]) for k in range(len(nodes) - 1)]
            total = sum(lengths) or 1.0
            if kind in ("s", "e"):
                values = [v for v in levels_of(way[1]) if v]
                try:
                    span = int(round(max(float(v) for v in values) - min(float(v) for v in values))) if values else 0
                except ValueError:
                    span = 0
            if kind == "s":
                if steps:
                    count = steps
                elif span >= 1:
                    # The levels it joins say the height: a flight per level.
                    count = LEVEL_STEPS * span
                else:
                    count = min(ESTIMATED_FLIGHT_STEPS, max(1, int(round(total / STEP_DEPTH_METERS))))
            if kind == "e":
                # An escalator drawn short still takes its ride.
                ride = max(total / ESCALATOR_METERS_PER_SECOND, ESCALATOR_LEVEL_SECONDS * max(1, span))
            for k, length in enumerate(lengths):
                a, b = nodes[k], nodes[k + 1]
                if kind == "s":
                    seconds = length / SPEED + count * STEP_SECONDS * length / total
                elif kind == "e":
                    seconds = ride * length / total
                elif kind == "l":
                    seconds = ELEVATOR_SECONDS / len(lengths)
                else:
                    seconds = self.cost(kind, length)
                self.edge(a, b, seconds, kind not in ("s", "e"))
        self.grid = defaultdict(list)
        for v, (lat, lon) in enumerate(self.points):
            self.grid[(int(lat / CELL), int(lon / CELL))].append(v)
        self.snap_ends()

    @staticmethod
    def cost(kind, length):
        if kind == "e":
            return length / ESCALATOR_METERS_PER_SECOND
        return length / SPEED

    def edge(self, a, b, seconds, step_free):
        if a == b:
            return
        self.adj[a].append((b, seconds, step_free))
        self.adj[b].append((a, seconds, step_free))

    def near(self, point, radius):
        """(metres, node) of the nodes within [radius] of [point], nearest first."""
        lat, lon = point
        reach = int(radius / (CELL * 111320.0 * math.cos(math.radians(lat)))) + 1
        cy, cx = int(lat / CELL), int(lon / CELL)
        found = []
        for y in range(cy - reach, cy + reach + 1):
            for x in range(cx - reach, cx + reach + 1):
                for v in self.grid.get((y, x), ()):
                    d = meters(point, self.points[v])
                    if d <= radius:
                        found.append((d, v))
        found.sort()
        return found

    def snap_ends(self):
        """Join the loose ends of ways to the network: inside a platform or area to its outline,
        else to the nearest node of another way within SNAP_METERS on the same level."""
        joined = 0
        area_cells = defaultdict(list)
        for index, (_, _, ring_points, _) in enumerate(self.areas):
            lats = [p[0] for p in ring_points]
            lons = [p[1] for p in ring_points]
            for y in range(int(min(lats) / CELL), int(max(lats) / CELL) + 1):
                for x in range(int(min(lons) / CELL), int(max(lons) / CELL) + 1):
                    area_cells[(y, x)].append(index)
        for v in range(len(self.points)):
            # A loose end, or the end of a step-free way that goes on only by steps (the
            # footway from a lift that meets the stairs, not the platform beside them).
            free = sum(1 for _, _, f in self.adj[v] if f)
            loose = len(self.adj[v]) == 1 or (free == 1 and len(self.adj[v]) > 1)
            if v in self.doors:
                continue
            point = self.points[v]
            linked = False
            for index in area_cells.get((int(point[0] / CELL), int(point[1] / CELL)), ()):
                kind, levels, ring_points, ring = self.areas[index]
                if v in ring:
                    continue
                # Inside a platform or area: a node on its level (a loose end, or any node where
                # the area has a level of its own) or the end of steps, an escalator or a lift.
                if levels <= GROUND or not level_ok(self.levels[v], levels):
                    if not (loose and level_ok(self.levels[v], levels)
                            or len(self.adj[v]) == 1 and self.kinds[v] <= VERTICAL):
                        continue
                if not inside(point, ring_points):
                    continue
                for d, u in sorted((meters(point, self.points[u]), u) for u in ring)[:4]:
                    self.edge(v, u, d / SPEED, True)
                linked = True
            if linked:
                joined += 1
                continue
            if not loose:
                continue
            mine = self.way_nodes(v)
            for d, u in self.near(point, SNAP_METERS):
                if u == v or u in mine or not level_ok(self.levels[v], self.levels[u]):
                    continue
                self.edge(v, u, d / SPEED, True)
                joined += 1
                break
        log("  %d loose ends joined" % joined)

    def way_nodes(self, v):
        return {u for u, _, _ in self.adj[v]} | {v}

    def search(self, sources, targets, step_free):
        """Seconds from the nearest of [sources] to each of [targets] (a set), up to MAX_SECONDS."""

        # [sources]: a set (each at 0 s) or a dict node -> seconds to start with.
        start = sources if isinstance(sources, dict) else dict.fromkeys(sources, 0.0)
        best = dict(start)
        heap = [(seconds, s) for s, seconds in start.items()]
        heapq.heapify(heap)
        left = set(targets)
        done = set()
        while heap and left:
            seconds, v = heapq.heappop(heap)
            if v in done:
                continue
            done.add(v)
            left.discard(v)
            if seconds > MAX_SECONDS:
                break
            for u, cost, free in self.adj[v]:
                if step_free and not free:
                    continue
                total = seconds + cost
                if total < best.get(u, float("inf")):
                    best[u] = total
                    heapq.heappush(heap, (total, u))
        return {t: best[t] for t in targets if t in done and best[t] <= MAX_SECONDS}


def inside(point, ring):
    y, x = point
    hit = False
    j = len(ring) - 1
    for i in range(len(ring)):
        yi, xi = ring[i]
        yj, xj = ring[j]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / ((yj - yi) or 1e-12) + xi:
            hit = not hit
        j = i
    return hit


def segment_meters(p, a, b):
    frame = bsa.Frame(p[0], p[1])
    return bsa.seg_dist((0.0, 0.0), frame.xy(*a), frame.xy(*b))


def segments_cross(a1, b1, a2, b2):
    """True when the two segments cross (in a plane around [a1])."""
    frame = bsa.Frame(a1[0], a1[1])
    p, q, r, t = frame.xy(*a1), frame.xy(*b1), frame.xy(*a2), frame.xy(*b2)

    def side(o, u, v):
        return (u[0] - o[0]) * (v[1] - o[1]) - (u[1] - o[1]) * (v[0] - o[0])
    d1, d2, d3, d4 = side(r, t, p), side(r, t, q), side(p, q, r), side(p, q, t)
    return (d1 > 0) != (d2 > 0) and (d3 > 0) != (d4 > 0) and d1 * d2 < 0 and d3 * d4 < 0


def segments_meters(a1, b1, a2, b2):
    """The least metres between two segments: 0 where they cross (two platforms on two levels
    crossing each other, as at Westkreuz or Köln Messe/Deutz)."""
    if segments_cross(a1, b1, a2, b2):
        return 0.0
    return min(segment_meters(a1, a2, b2), segment_meters(b1, a2, b2),
               segment_meters(a2, a1, b1), segment_meters(b2, a1, b1))


# --- Platforms and stops of the station complexes ---------------------------------------

def platform_elements(data):
    """Axis (as in the asset, rounded) -> the OSM element of the platform."""
    complexes, _ = bsa.build_complexes(data)
    found = {}
    for members in complexes:
        frame = bsa.complex_frame(members)
        for item in bsa.dedupe([p for s in members for p in s.get("platforms", [])]):
            tags = item["tags"]
            if not bsa.is_rail_platform(tags):
                continue
            shape = bsa.Shape(item, frame)
            if not shape.axis or bsa.dist(*shape.axis) < 5:
                continue
            a = frame.latlon(*shape.axis[0])
            b = frame.latlon(*shape.axis[1])
            found[(bsa.r6(a[0]), bsa.r6(a[1]), bsa.r6(b[0]), bsa.r6(b[1]))] = item
    return found


def platform_level(item):
    tags = item["tags"]
    level = (tags.get("level") or "").strip()
    if level:
        return levels_of(level)
    layer = tags.get("layer", "")
    if tags.get("tunnel") == "yes" or tags.get("location") == "underground" or layer.startswith("-"):
        return {"under"}
    if tags.get("bridge") == "yes" or (layer and layer not in ("0",) and not layer.startswith("-")):
        return {"above"}
    return {""}


def platform_sources(graph, item):
    """The nodes of the network that belong to a rail platform: those of its outline or line,
    and those inside it (or within 4 m of a line) on its level."""
    rings = [[(round(p[0], 6), round(p[1], 6)) for p in ring] for ring in bsa.element_rings(item)]
    if item.get("type") == "node":
        rings = [[(round(item["lat"], 6), round(item["lon"], 6))]]
    points = [p for ring in rings for p in ring]
    if not points:
        return set()
    by_point = {}
    for p in points:
        for d, v in graph.near(p, 0.3):
            by_point[v] = True
    sources = set(by_point)
    levels = platform_level(item)
    lats = [p[0] for p in points]
    lons = [p[1] for p in points]
    center = (sum(lats) / len(lats), sum(lons) / len(lons))
    reach = max(meters(center, p) for p in points) + 5.0
    area = [r for r in rings if len(r) > 3 and r[0] == r[-1]]
    for d, v in graph.near(center, reach):
        if v in sources or v in graph.doors:
            continue
        p = graph.points[v]
        if area:
            if not any(inside(p, ring) for ring in area):
                continue
        else:
            if min(segment_meters(p, rings[0][k], rings[0][k + 1]) for k in range(len(rings[0]) - 1)) > 4.0 \
                    if len(rings[0]) > 1 else True:
                continue
        kinds = graph.kinds[v]
        node_levels = graph.levels[v]
        if levels & {"under", "above", ""}:
            # No level tag on the platform: only the platform itself and loose ends of steps,
            # escalators and elevators that end on it, not a street that passes beneath it.
            if kinds <= {"r", "p"} or (kinds & VERTICAL and len(graph.adj[v]) <= 1 + len(kinds & VERTICAL)):
                sources.add(v)
        elif node_levels & levels and len(node_levels) == 1:
            # A node on several levels joins them (the end of a footway tagged "1;2"): the
            # platform's own outline counts, such a node inside it does not.
            sources.add(v)
    return sources


def stop_attach(graph, point):
    """(node, metres) where a stop position joins the network, at street level."""
    out = []
    for d, v in graph.near(point, STOP_ATTACH_METERS):
        if not graph.levels[v] & GROUND or graph.kinds[v] <= {"r"} or graph.kinds[v] <= VERTICAL:
            continue
        out.append((v, d))
        if len(out) >= STOP_ATTACH_NODES:
            break
    return out


def level_number(platform):
    """The level of a platform of the asset as a number (0 for a stop in the street); None
    where it is not known or not one level."""
    if platform is None:
        return 0.0
    try:
        return float(platform.get("level", ""))
    except ValueError:
        return None


def level_gap(a, b):
    """The levels between two platforms of the asset (or a platform and a street stop: [b]
    None), 0 where a level is not known."""
    x, y = level_number(a), level_number(b)
    if x is None or y is None:
        return 0
    return int(round(abs(x - y)))


def whole_seconds(seconds):
    """Seconds rounded up, at least 1 (0 in the asset means none)."""
    return max(1, int(math.ceil(seconds - 1e-6)))


def estimate_seconds(straight):
    return whole_seconds(straight * DETOUR / SPEED)


def build(asset, data, graph, report=None):
    elements = platform_elements(data)
    stop_points = []
    for area in asset.get("stopAreas", []):
        for p in area["p"]:
            stop_points.append((p[0], p[1]))
    stop_points = sorted(set(stop_points))
    stop_grid = defaultdict(list)
    for p in stop_points:
        stop_grid[(int(p[0] / 0.003), int(p[1] / 0.003))].append(p)
    out = []
    counts = defaultdict(int)
    missing = 0
    for station in asset["stations"]:
        axes = [tuple(p["a"]) for p in station["platforms"]]
        items = [elements.get(a) for a in axes]
        missing += sum(1 for i in items if i is None)
        sources = [platform_sources(graph, item) if item else set() for item in items]
        # Stops within STOP_RADIUS_METERS of a platform.
        stops = []
        near_of = defaultdict(set)
        for pi, a in enumerate(axes):
            if not station["platforms"][pi]:
                continue
            for y in range(int(min(a[0], a[2]) / 0.003) - 2, int(max(a[0], a[2]) / 0.003) + 3):
                for x in range(int(min(a[1], a[3]) / 0.003) - 2, int(max(a[1], a[3]) / 0.003) + 3):
                    for p in stop_grid.get((y, x), ()):
                        if segment_meters(p, (a[0], a[1]), (a[2], a[3])) <= STOP_RADIUS_METERS:
                            near_of[pi].add(p)
        stops = sorted({p for ps in near_of.values() for p in ps})
        stop_index = {p: len(axes) + k for k, p in enumerate(stops)}
        attach = {p: stop_attach(graph, p) for p in stops}
        walks = []
        for i in range(len(axes)):
            targets = set()
            for j in range(i + 1, len(axes)):
                targets |= sources[j]
            for p in near_of[i]:
                targets |= {v for v, _ in attach[p]}
            results = {}
            for free in (False, True):
                results[free] = graph.search(sources[i], targets, free) if sources[i] else {}
            a1, b1 = (axes[i][0], axes[i][1]), (axes[i][2], axes[i][3])

            def best_to(nodes_with_extra, free):
                values = [results[free][v] + extra for v, extra in nodes_with_extra if v in results[free]]
                return min(values) if values else None

            pairs = [(j, [(v, 0.0) for v in sources[j]],
                      segments_meters(a1, b1, (axes[j][0], axes[j][1]), (axes[j][2], axes[j][3])))
                     for j in range(i + 1, len(axes))]
            pairs += [(stop_index[p], [(v, d / SPEED) for v, d in attach[p]], segment_meters(p, a1, b1))
                      for p in sorted(near_of[i])]
            for j, ends, straight in pairs:
                seconds = best_to(ends, False)
                free_seconds = best_to(ends, True)
                floor = LEVEL_CHANGE_SECONDS * level_gap(station["platforms"][i],
                                                         station["platforms"][j] if j < len(axes) else None)
                if seconds is not None:
                    seconds = max(seconds, floor)
                if free_seconds is not None:
                    free_seconds = max(free_seconds, floor)
                if seconds is not None and seconds > SUSPICIOUS_DETOUR * estimate_seconds(straight) + SUSPICIOUS_EXTRA_SECONDS:
                    # Far longer than the straight line: most likely a gap in the mapped network
                    # (an exit or a crossing not drawn); the straight line is nearer the truth.
                    counts["detour"] += 1
                    seconds = None
                if seconds is None:
                    walks.append([i, j, max(estimate_seconds(straight), whole_seconds(floor)), 0, 1])
                    counts["estimate"] += 1
                else:
                    walks.append([i, j, whole_seconds(seconds),
                                  whole_seconds(free_seconds) if free_seconds is not None else 0, 0])
                    counts["graph"] += 1
                    if free_seconds is not None:
                        counts["step-free"] += 1
                if report is not None:
                    report.append({"station": station["name"], "i": i, "j": j, "seconds": seconds,
                                   "free": free_seconds, "straight": straight,
                                   "from": station["platforms"][i], "to": station["platforms"][j] if j < len(axes) else stops[j - len(axes)]})
        # Between two stops of the complex (the bus on one side of the station, the one on the
        # other): through the station where that is shorter than around it.
        for k in range(len(stops)):
            far = [l for l in range(k + 1, len(stops)) if meters(stops[k], stops[l]) >= STOP_PAIR_METERS]
            if not far or not attach[stops[k]]:
                continue
            start = {}
            for v, d in attach[stops[k]]:
                start[v] = min(start.get(v, float("inf")), d / SPEED)
            targets = {v for l in far for v, _ in attach[stops[l]]}
            results = {free: graph.search(start, targets, free) for free in (False, True)}
            for l in far:
                straight = meters(stops[k], stops[l])
                ends = attach[stops[l]]
                values = {free: [results[free][v] + d / SPEED for v, d in ends if v in results[free]] for free in (False, True)}
                seconds = min(values[False]) if values[False] else None
                free_seconds = min(values[True]) if values[True] else None
                if seconds is not None and seconds > SUSPICIOUS_DETOUR * estimate_seconds(straight) + SUSPICIOUS_EXTRA_SECONDS:
                    seconds = None
                if seconds is None:
                    # Two stops in the street: the rules of thumb of the app know the straight line.
                    continue
                walks.append([len(axes) + k, len(axes) + l, whole_seconds(seconds),
                              whole_seconds(free_seconds) if free_seconds is not None else 0, 0])
                counts["stop pairs"] += 1
                if report is not None:
                    report.append({"station": station["name"], "i": len(axes) + k, "j": len(axes) + l, "seconds": seconds,
                                   "free": free_seconds, "straight": straight, "from": stops[k], "to": stops[l]})
        out.append({"id": (station.get("ids") or [""])[0], "n": station["name"],
                    "p": [list(a) for a in axes], "s": [list(p) for p in stops], "w": walks})
    log("  %d complexes, %d walks: %d over the network (%d with a step-free way), %d estimates (%d of them for "
        "a detour too long); %d walks between two stops; %d platforms of the asset not found in the extract"
        % (len(out), counts["graph"] + counts["estimate"], counts["graph"], counts["step-free"], counts["estimate"],
           counts["detour"], counts["stop pairs"], missing))
    return out


def main(argv=None):
    parser = argparse.ArgumentParser(description="Write the exact change walks (transfer-walks-berlin.json).")
    parser.add_argument("--source", default=bsa.SOURCE, help="the extract (default: tools/osm-stations.json)")
    parser.add_argument("--graph", default=GRAPH, help="the walking network (default: tools/osm-walk-graph.json)")
    parser.add_argument("--asset", default=STATION_ASSET, help="the station asset (default: osm-berlin.json)")
    parser.add_argument("--out", default=OUTPUT, help="output (default: app/src/main/assets/transfer-walks-berlin.json)")
    parser.add_argument("--report", default=None, help="also write every walk with its seconds and metres here (JSON)")
    args = parser.parse_args(argv)
    started = time.time()
    with open(args.source, "r", encoding="utf-8") as handle:
        data = json.load(handle)
    with open(args.asset, "r", encoding="utf-8") as handle:
        asset = json.load(handle)
    with open(args.graph, "r", encoding="utf-8") as handle:
        raw = json.load(handle)
    log("Graph %s: %d nodes, %d ways" % (os.path.relpath(args.graph, ROOT), len(raw["nodes"]) // 2, len(raw["ways"])))
    graph = Graph(raw)
    del raw
    report = [] if args.report else None
    stations = build(asset, data, graph, report)
    result = {
        "license": "ODbL 1.0",
        "attribution": "© OpenStreetMap contributors",
        "source": "https://www.openstreetmap.org/copyright",
        "generated": time.strftime("%Y-%m-%d", time.gmtime()),
        "metersPerMinute": WALK_METERS_PER_MINUTE,
        "stations": stations,
    }
    raw_out = json.dumps(result, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    bsa.write_bytes(os.path.abspath(args.out), raw_out)
    log("Written %s: %d bytes (gzip %d) in %ds" % (os.path.relpath(args.out, ROOT), len(raw_out), bsa.gzip_size(raw_out),
                                                   time.time() - started))
    if report is not None:
        with open(args.report, "w", encoding="utf-8") as handle:
            json.dump(report, handle, ensure_ascii=False)
    return 0


if __name__ == "__main__":
    sys.exit(main())
