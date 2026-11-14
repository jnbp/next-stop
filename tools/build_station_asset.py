#!/usr/bin/env python3
"""
Build the OpenStreetMap assets of the app from tools/osm-stations.json (written by
tools/fetch_osm_stations.py):

  app/src/main/assets/osm-berlin.json
      The U-Bahn and S-Bahn station complexes of Berlin (names, VBB ids, platform axes,
      entrances, elevators and escalators), the bus and tram stop positions with a
      "Pos." number (local_ref) ("stops"), and every bus and tram stop area with all its
      platforms, each stop position merged into its platform (see area_positions)
      ("stopAreas": name "n", VBB numbers "ids", positions "p" as
      [lat, lon, mode "b"/"t"/"bt", optional number, optional lines]), compact and with the
      ODbL header. The number is "" where a position has none but lines; the lines are
      {"b": [bus lines], "t": [tram lines]} that stop there, from the bus and tram route
      relations ("busRoutes" of the extract) or, without them, the route_ref tags.

  app/src/main/assets/osm-change-positions.ndjson
      Boarding positions for the changes between U-Bahn and S-Bahn lines, in the record
      format of vbb-change-positions.ndjson plus "source":"osm" and "confidence". Each
      record is estimated from the platform axis, the direction of travel (the order of
      the members of the route relation) and the way from the arrival platform to the
      departing platform (steps, escalators, elevators and footways, shortest path).
      They are checked against vbb-change-positions.ndjson; only records with a
      confidence at or above MIN_CONFIDENCE are written.

Run from the root of the repository (standard library only):

    python3 tools/build_station_asset.py
    python3 tools/build_station_asset.py --region nordrhein-westfalen
    python3 tools/build_station_asset.py --source de.json --region germany --split-regions

Berlin (the default) writes the two assets above; any other region (a German state, munich,
germany, --bbox) writes osm-<region>.json and osm-change-positions-<region>.ndjson into
tools/osm-packs/ (or --out-dir), and --split-regions also one pair per German state. Stations
carry "kinds" (S-Bahn, U-Bahn, Stadtbahn L, tram T, regional R, long-distance F; see
route_kind and station_kinds) besides the "products" S/U/R the app understands, and the ids
hold DHIDs (de:xxxxx:yyyy) besides VBB numbers. See tools/README.md.

Data (c) OpenStreetMap contributors, available under the Open Database License (ODbL 1.0).
"""

import argparse
import gzip
import heapq
import json
import math
import os
import re
import sys
import time
from collections import Counter, defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)
import regions as rg  # noqa: E402

SOURCE = os.path.join(HERE, "osm-stations.json")
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
STATION_ASSET = os.path.join(ASSETS, "osm-berlin.json")
CHANGE_ASSET = os.path.join(ASSETS, "osm-change-positions.ndjson")
VBB_ASSET = os.path.join(ASSETS, "vbb-change-positions.ndjson")
# Where the assets of other regions and the per-state packs go by default (not committed).
PACKS = os.path.join(HERE, "osm-packs")

# A numbered stop (platform or stop position) of the same name, number and mode as one
# already taken is the same stop within this distance, another one farther away.
SAME_STOP_METERS = 1000.0
# Records below this confidence are not written. Picked from the check against the VBB
# dataset (see the table this script prints): the agreement on the third of the train
# falls off below it.
MIN_CONFIDENCE = 0.8
# Stations of one name within this distance form one complex.
COMPLEX_METERS = 500.0
# A way point this close to a platform outline (or inside it) touches the platform.
TOUCH_METERS = 4.0
# Points of different ways this close count as joined (OSM data has small gaps).
JOIN_METERS = 1.5
LINE_RE = re.compile(r"^[SU]\d{1,2}$")
VBB_RE = re.compile(r"(?<!\d)900(\d{6})(?!\d)")
EARTH = 6371000.0


def log(text):
    print(text, flush=True)


# ---------------------------------------------------------------------------------------
# Geometry in metres around a reference point

class Frame:
    def __init__(self, lat, lon):
        self.lat0 = lat
        self.lon0 = lon
        self.kx = math.radians(1) * math.cos(math.radians(lat)) * EARTH
        self.ky = math.radians(1) * EARTH

    def xy(self, lat, lon):
        return ((lon - self.lon0) * self.kx, (lat - self.lat0) * self.ky)

    def latlon(self, x, y):
        return (self.lat0 + y / self.ky, self.lon0 + x / self.kx)


def dist(a, b):
    return math.hypot(a[0] - b[0], a[1] - b[1])


def meters(lat1, lon1, lat2, lon2):
    x = math.radians(lon2 - lon1) * math.cos(math.radians((lat1 + lat2) / 2))
    y = math.radians(lat2 - lat1)
    return math.hypot(x, y) * EARTH


def seg_dist(p, a, b):
    dx, dy = b[0] - a[0], b[1] - a[1]
    length2 = dx * dx + dy * dy
    if length2 == 0:
        return dist(p, a)
    t = max(0.0, min(1.0, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / length2))
    return dist(p, (a[0] + t * dx, a[1] + t * dy))


def inside(p, ring):
    x, y = p
    result = False
    n = len(ring)
    for i in range(n):
        x1, y1 = ring[i]
        x2, y2 = ring[(i + 1) % n]
        if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / (y2 - y1 + 1e-12) + x1:
            result = not result
    return result


def r6(value):
    return round(value, 6)


def element_rings(element):
    """The outlines of a way or relation as lists of (lat, lon), or [] for a node."""
    geometry = element.get("geometry")
    if not geometry:
        return []
    if isinstance(geometry[0], dict):
        parts = [[tuple(p) for p in member["points"]] for member in geometry if member.get("role") != "inner" and member.get("points")]
        return join_rings(parts)
    return [[tuple(p) for p in geometry]]


def join_rings(parts):
    """The outer ways of a multipolygon joined end to end into rings where they meet."""
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


def element_points(element):
    if element.get("type") == "node":
        return [(element["lat"], element["lon"])]
    return [p for ring in element_rings(element) for p in ring]


def center_of(element):
    points = element_points(element)
    if not points:
        return None
    return (sum(p[0] for p in points) / len(points), sum(p[1] for p in points) / len(points))


class Shape:
    """A platform in metres: its rings, whether they are areas, and its axis."""

    def __init__(self, element, frame):
        self.element = element
        self.rings = [[frame.xy(*p) for p in ring] for ring in element_rings(element)]
        self.area = any(len(r) > 3 and dist(r[0], r[-1]) < 0.5 for r in self.rings)
        points = [p for ring in self.rings for p in ring]
        if element.get("type") == "node":
            points = [frame.xy(element["lat"], element["lon"])]
        self.points = points
        self.axis = principal_axis(points) if len(points) >= 2 else None

    def distance(self, p):
        if not self.points:
            return float("inf")
        if self.area:
            for ring in self.rings:
                if len(ring) > 3 and inside(p, ring):
                    return 0.0
        best = float("inf")
        for ring in self.rings:
            for i in range(len(ring) - 1):
                best = min(best, seg_dist(p, ring[i], ring[i + 1]))
        if len(self.points) == 1:
            best = dist(p, self.points[0])
        return best

    def nearest_point(self, p):
        """The point of the outline nearest to [p] (or [p] itself inside an area)."""
        if self.area and self.distance(p) == 0.0:
            return p
        best, at = float("inf"), self.points[0]
        for ring in self.rings:
            for i in range(len(ring) - 1):
                a, b = ring[i], ring[i + 1]
                dx, dy = b[0] - a[0], b[1] - a[1]
                length2 = dx * dx + dy * dy
                t = 0.0 if length2 == 0 else max(0.0, min(1.0, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / length2))
                q = (a[0] + t * dx, a[1] + t * dy)
                d = dist(p, q)
                if d < best:
                    best, at = d, q
        return at


def principal_axis(points):
    """The two ends of the long axis of [points]: the extremes of their projection on the
    principal direction, through their centre."""
    n = len(points)
    cx = sum(p[0] for p in points) / n
    cy = sum(p[1] for p in points) / n
    sxx = sum((p[0] - cx) ** 2 for p in points)
    syy = sum((p[1] - cy) ** 2 for p in points)
    sxy = sum((p[0] - cx) * (p[1] - cy) for p in points)
    angle = 0.5 * math.atan2(2 * sxy, sxx - syy)
    ux, uy = math.cos(angle), math.sin(angle)
    projections = [(p[0] - cx) * ux + (p[1] - cy) * uy for p in points]
    lo, hi = min(projections), max(projections)
    return ((cx + lo * ux, cy + lo * uy), (cx + hi * ux, cy + hi * uy))


def along(axis, p):
    """Where [p] lies along [axis], 0 at its first end and 1 at its second."""
    (ax, ay), (bx, by) = axis
    dx, dy = bx - ax, by - ay
    length2 = dx * dx + dy * dy
    if length2 == 0:
        return 0.5
    return max(0.0, min(1.0, ((p[0] - ax) * dx + (p[1] - ay) * dy) / length2))


# ---------------------------------------------------------------------------------------
# Names, ids, levels

def name_key(name):
    """Like stationNameKey of the app: lower case, without S/U/S+U, brackets and lines."""
    text = (name or "").lower()
    text = re.sub(r"\(.*?\)", " ", text)
    text = text.replace("straße", "str").replace("strasse", "str")
    text = re.sub(r"[^a-z0-9äöüß]+", " ", text).strip()
    text = re.sub(r"^(s u|s|u)\s+", "", text)
    text = re.sub(r"(\s+(s|u|re|rb)\d+)+$", "", text)
    if text.startswith("berlin "):
        text = text[len("berlin "):]
    for suffix in (" berlin", " bhf"):
        if text.endswith(suffix):
            text = text[: -len(suffix)]
    return text.strip().replace(" ", "")


def vbb_ids(*values):
    """The VBB numbers ("900" and six digits) in [values]: ref:VBB, a fahrinfo.vbb.de link,
    or a DHID of Berlin or Brandenburg (de:11xxx / de:12xxx). A DHID of another state is no
    VBB number, even when its last part happens to look like one."""
    found = []
    for value in values:
        if (value or "").startswith("de:") and all(rg.dhid_state(v) not in ("11", "12") for v in value.split(";")):
            continue
        for match in VBB_RE.finditer(value or ""):
            vid = "900" + match.group(1)
            if vid not in found:
                found.append(vid)
    return found


def dhids(*values):
    """The German stop place ids (DHID, "de:05315:11201") in [values] (ref:IFOPT tags, also
    several separated by ";"), each cut to its stop place (without area and platform parts)."""
    found = []
    for value in values:
        for part in (value or "").split(";"):
            place = rg.dhid_stop_place(part)
            if place and place not in found:
                found.append(place)
    return found


def levels(value):
    result = set()
    for part in (value or "").split(";"):
        part = part.strip()
        if not part:
            continue
        if "-" in part[1:]:
            a, _, b = part[0] + part[1:].partition("-")[0], None, part[1:].partition("-")[2]
            try:
                lo, hi = sorted((float(a), float(b)))
                x = math.ceil(lo)
                while x <= hi:
                    result.add(float(x))
                    x += 1
                continue
            except ValueError:
                pass
        try:
            result.add(float(part))
        except ValueError:
            pass
    return result


def level_ok(a, b):
    """Two level tags are compatible when one is missing or they share a level."""
    la, lb = levels(a), levels(b)
    return not la or not lb or bool(la & lb)


# The product letters in the order they are written: S-Bahn, U-Bahn, regional trains. These
# are the letters of "products" in the asset, which the app understands.
PRODUCT_ORDER = "SUR"

# The finer kinds of the asset field "kinds" (see KINDS.md in tools/README.md):
#   S  S-Bahn: a light_rail or train route with network:metro=s-bahn, a ref "S1" / "S 1",
#      or "S-Bahn" in its network, operator or name
#   U  U-Bahn: route=subway
#   L  Stadtbahn (light rail that is no S-Bahn: Cologne, Bonn, Hannover, Stuttgart, the Ruhr,
#      Duesseldorf, Frankfurt where mapped as light_rail); signs show "U" in tunnels
#   T  tram stops in a stop area of the station
#   R  regional trains (route=train, not S-Bahn, not long distance)
#   F  long-distance trains (service=long_distance|high_speed|international|national|night,
#      or a ref ICE/IC/EC/ECE/EN/NJ/FLX/RJ/RJX/TGV)
KIND_ORDER = "SULTRF"
# What an app that knows only "products" letters sees for each kind.
KIND_TO_PRODUCT = {"S": "S", "U": "U", "L": "U", "T": "", "R": "R", "F": "R"}
S_REF_RE = re.compile(r"^S\s?\d{1,2}[A-Z]?$")
LONG_SERVICES = ("long_distance", "high_speed", "international", "national", "night")
LONG_REF_RE = re.compile(r"^(ICE|IC|EC|ECE|EN|NJ|FLX|RJX?|TGV|THA)(\s|\d|$)")
S_HINT_RE = re.compile(r"s-bahn|\bdb\b|deutsche bahn", re.IGNORECASE)
L_HINT_RE = re.compile(r"stadtbahn|straßenbahn|strassenbahn|verkehrsbetrieb|verkehrs-betrieb|stadtwerke|üstra", re.IGNORECASE)


def route_kind(tags):
    """The kind letter of a rail route relation (see KIND_ORDER), or None for other routes."""
    route = tags.get("route")
    if route == "subway":
        return "U"
    if route == "tram":
        return "T"
    if route not in ("light_rail", "train"):
        return None
    ref = (tags.get("ref") or "").strip().upper()
    text = " ".join(tags.get(k) or "" for k in ("network", "network:short", "operator", "name"))
    s_bahn = tags.get("network:metro") == "s-bahn" or bool(S_REF_RE.match(ref)) or "s-bahn" in text.lower()
    if route == "light_rail":
        return "S" if s_bahn else "L"
    long_distance = tags.get("service") in LONG_SERVICES or bool(LONG_REF_RE.match(ref))
    if s_bahn and not long_distance:
        return "S"
    return "F" if long_distance else "R"


def station_modes(station):
    """The rail modes of a station: its "modes" from the PBF extract ([] means train), else
    (the Overpass fetch has only U-Bahn and S-Bahn stations) from station=subway."""
    modes = station.get("modes")
    if modes is not None:
        return list(modes) or ["train"]
    return ["subway"] if station["tags"].get("station") == "subway" else ["light_rail"]


def light_rail_fallback(station, areas):
    """S or L for a light_rail station that no route explains: its tags and those of its
    stop areas; S unless they speak of a Stadtbahn and not of the S-Bahn."""
    name = station.get("name") or ""
    texts = []
    for tags in [station["tags"]] + [a["tags"] for a in areas]:
        texts += [tags.get(k) or "" for k in ("network", "network:short", "operator", "operator:short")]
    text = " ".join(texts)
    if re.match(r"^(S|S\+U|U\+S)\s", name) or S_HINT_RE.search(text):
        return "S"
    if station["tags"].get("tram") == "yes" or L_HINT_RE.search(text):
        return "L"
    return "S"


def station_kinds(data, areas_of):
    """The kinds (KIND_ORDER letters) of each station, by station key: of the rail routes
    stopping at members of its stop areas or, outside stop areas, at its nearest stop
    positions and platforms; T for tram stops in its stop areas; and for each mode of the
    station no route explains, the mode itself (subway U, light_rail S or L, train R)."""
    stations = data["stations"]
    by_key = {station_key(s): s for s in stations}
    area_stations = defaultdict(set)
    element_areas = defaultdict(set)
    for area in data["stopAreas"]:
        for member in area["members"]:
            key = "%s/%d" % (member["type"], member["ref"])
            element_areas[key].add(area["id"])
            if key in by_key:
                area_stations[area["id"]].add(key)
    owners = defaultdict(list)
    centres = {}
    for station in stations:
        for kind in ("stop_positions", "platforms"):
            for item in station.get(kind, []):
                key = "%s/%d" % (item["type"], item["id"])
                owners[key].append(station_key(station))
                if key not in centres:
                    centres[key] = center_of(item)
    found = defaultdict(set)
    for route in data["routes"]:
        kind = route_kind(route["tags"])
        if kind is None:
            continue
        for member in route["members"]:
            role = member.get("role") or ""
            if not (role.startswith("stop") or role.startswith("platform")):
                continue
            key = "%s/%d" % (member["type"], member["ref"])
            targets = set()
            for area_id in element_areas.get(key, ()):
                targets |= area_stations.get(area_id, set())
            if not targets and owners.get(key):
                c = centres.get(key)
                if c:
                    targets = {min(owners[key], key=lambda k: meters(c[0], c[1], by_key[k]["lat"], by_key[k]["lon"]))}
            for target in targets:
                found[target].add(kind)
    tram_areas = {aid for p in data["stopPositions"] if p.get("tram") for aid in p.get("stopAreas", [])}
    result = {}
    for station in stations:
        key = station_key(station)
        kinds = set(found.get(key, ()))
        areas = areas_of.get(key, [])
        if any(a["id"] in tram_areas for a in areas):
            kinds.add("T")
        for mode in station_modes(station):
            if mode == "subway" and "U" not in kinds:
                kinds.add("U")
            elif mode == "light_rail" and not kinds & {"S", "L"}:
                kinds.add(light_rail_fallback(station, areas))
            elif mode == "train" and not kinds & {"R", "F", "S"}:
                kinds.add("R")
        result[key] = "".join(c for c in KIND_ORDER if c in kinds)
    return result


def compat_products(kinds):
    """The "products" letters (PRODUCT_ORDER) of a station of [kinds]: S, U (also for a
    Stadtbahn), SU, else R (trains only; a tram stop beside it adds nothing)."""
    letters = ("S" if "S" in kinds else "") + ("U" if "U" in kinds or "L" in kinds else "")
    return letters or "R"


def products_of(station):
    """(Before the kinds; now only printed for comparison.) The products of a station in PRODUCT_ORDER: 'S', 'U', 'SU' (one node for both, like
    Wuhletal) or 'R' (regional and other trains only; train beside subway or light_rail adds
    nothing). From the "modes" of the station where the extract has them
    (tools/extract_osm_pbf.py: subway, light_rail, train; [] means train); else
    (tools/fetch_osm_stations.py fetches only U-Bahn and S-Bahn stations) from station=subway."""
    modes = station.get("modes")
    if modes is not None:
        letters = ("S" if "light_rail" in modes else "") + ("U" if "subway" in modes else "")
        return letters or "R"
    return "U" if station["tags"].get("station") == "subway" else "S"


def mode_census(stations):
    """How many stations of each mode: their "modes" where the extract has them, else the
    station tag (the Overpass fetch)."""
    return dict(Counter("+".join(s["modes"]) or "train" if s.get("modes") is not None else s["tags"].get("station")
                        for s in stations))


PREFIX_RE = re.compile(r"^(?:S\+U|U\+S|S|U)\s+(?=\S)")


def display_name(name):
    """The name of a station without a leading "S ", "U " or "S+U " ("S Frankfurter Allee")."""
    return PREFIX_RE.sub("", name or "")


# A trailing bracket that names one part of a station drawn as several nodes ("Südkreuz
# (Ringbahn)", "Südkreuz (Nord-Süd)", "Ostkreuz (Stadtbahn-F)"), not a place ("Freie
# Universität (Thielplatz)", "Bernau (bei Berlin)").
PART_QUALIFIER_RE = re.compile(
    r"\s*\((?:Ringbahn|Nord-Süd|Stadtbahn|Ostbahn|Vorortbahn|Fernbahn|Regionalbahn|S-Bahn|U-Bahn|S|U)"
    r"(?:-[SFRU])?\)\s*$")


def complex_name(members):
    """The name of a station complex: that of its first member, without a bracket naming the
    part of the station that member stands for when the complex joins several (Südkreuz is the
    Ringbahn, the Nord-Süd S-Bahn and the long-distance station: "Südkreuz", not "Südkreuz
    (Ringbahn)")."""
    name = display_name(members[0]["name"])
    if len(members) > 1:
        name = PART_QUALIFIER_RE.sub("", name) or name
    return name


# ---------------------------------------------------------------------------------------

def load():
    with open(SOURCE, "r", encoding="utf-8") as handle:
        return json.load(handle)


def station_key(station):
    return "%s/%d" % (station["type"], station["id"])


def build_complexes(data):
    """Stations grouped into complexes by base name and distance; each with its VBB ids,
    its DHIDs, its kinds and its products."""
    areas_of = defaultdict(list)
    for area in data["stopAreas"]:
        for member in area["members"]:
            areas_of["%s/%d" % (member["type"], member["ref"])].append(area)
    stations = data["stations"]
    kinds_of = station_kinds(data, areas_of)
    for station in stations:
        tags = station["tags"]
        ids = dhids(tags.get("ref:IFOPT"))
        for area in areas_of.get(station_key(station), []):
            for d in dhids(area["tags"].get("ref:IFOPT")):
                if d not in ids:
                    ids.append(d)
        if not ids:
            # The stop place most of its stop positions and platforms name (they are taken by
            # radius, so they may belong to a neighbour). Not in Berlin and Brandenburg: there a
            # DHID holds a VBB number, which the app would match, and the VBB ids already serve.
            votes = Counter(d for kind in ("stop_positions", "platforms") for item in station.get(kind, [])
                            for d in dhids(item["tags"].get("ref:IFOPT")) if rg.dhid_state(d) not in ("11", "12"))
            if votes:
                ids = [votes.most_common(1)[0][0]]
        station["dhid"] = ids
        station["kinds"] = kinds_of[station_key(station)]
    for station in stations:
        ids = []
        tags = station["tags"]
        ids += vbb_ids(tags.get("ref:IFOPT"), tags.get("ref:VBB"), tags.get("website"))
        for area in areas_of.get(station_key(station), []):
            t = area["tags"]
            for vid in vbb_ids(t.get("ref:VBB"), t.get("ref:IFOPT"), t.get("website")):
                if vid not in ids:
                    ids.append(vid)
        station["vbb"] = ids
        station["key"] = name_key(station["name"])
        station["products"] = compat_products(station["kinds"])
        station["product"] = station["products"][0]
    parent = list(range(len(stations)))

    def root(i):
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i

    # Only stations sharing a name key or an id can join: compare within those buckets.
    buckets = defaultdict(list)
    for i, station in enumerate(stations):
        if station["key"]:
            buckets["k:" + station["key"]].append(i)
        for vid in station["vbb"]:
            buckets["v:" + vid].append(i)
    pairs = set()
    for members in buckets.values():
        for x in range(len(members)):
            for y in range(x + 1, len(members)):
                pairs.add((members[x], members[y]) if members[x] < members[y] else (members[y], members[x]))
    for i, j in sorted(pairs):
        a, b = stations[i], stations[j]
        if meters(a["lat"], a["lon"], b["lat"], b["lon"]) <= COMPLEX_METERS:
            parent[root(i)] = root(j)
    groups = defaultdict(list)
    for i, station in enumerate(stations):
        groups[root(i)].append(station)
    complexes = []
    for members in groups.values():
        members.sort(key=lambda s: (PRODUCT_ORDER.index(s["product"]), s["id"]))
        complexes.append(members)
    complexes.sort(key=lambda m: (m[0]["name"], m[0]["id"]))
    return complexes, areas_of


def dedupe(items):
    seen = {}
    for item in items:
        seen["%s/%d" % (item["type"], item["id"])] = item
    return list(seen.values())


# ---------------------------------------------------------------------------------------
# Routes: which line stops at which platform, and the stations before and after

def route_visits(data, station_by_key):
    """For each U/S route relation: its line and the ordered list of visits
    (station key, stop node, platform element key)."""
    owner = {}  # element key -> list of station keys it lies near
    elements = {}
    for station in data["stations"]:
        for kind in ("stop_positions", "platforms"):
            for item in station.get(kind, []):
                key = "%s/%d" % (item["type"], item["id"])
                owner.setdefault(key, []).append(station_key(station))
                elements[key] = item
    routes = []
    for route in data["routes"]:
        tags = route["tags"]
        line = (tags.get("ref") or "").replace(" ", "").upper()
        kind = route_kind(tags)
        if kind not in ("S", "U", "L") or not LINE_RE.match(line):
            continue
        # A Stadtbahn with U lines (Stuttgart, the Ruhr) is U for the app.
        product = KIND_TO_PRODUCT[kind]
        visits = []
        for member in route["members"]:
            role = member["role"]
            if not (role.startswith("stop") or role.startswith("platform")):
                continue
            key = "%s/%d" % (member["type"], member["ref"])
            candidates = [k for k in owner.get(key, []) if product in station_by_key[k]["products"]]
            if not candidates:
                visits.append(None)
                continue
            element = elements[key]
            c = center_of(element)
            best = min(candidates, key=lambda k: meters(c[0], c[1], station_by_key[k]["lat"], station_by_key[k]["lon"]))
            entry = {"station": best, "stop": None, "platform": None}
            if role.startswith("stop") and member["type"] == "node":
                entry["stop"] = element
            else:
                entry["platform"] = key
            if visits and visits[-1] is not None and visits[-1]["station"] == best:
                previous = visits[-1]
                previous["stop"] = previous["stop"] or entry["stop"]
                previous["platform"] = previous["platform"] or entry["platform"]
            else:
                visits.append(entry)
        routes.append({"id": route["id"], "line": line, "product": product, "visits": visits})
    return routes, elements


# ---------------------------------------------------------------------------------------
# The walking network of a complex

class Network:
    def __init__(self, frame, ways):
        """[ways]: list of (points in metres, level tag, kind). Builds a graph whose
        vertices are way points, joined along each way and where points of ways meet."""
        self.points = []
        self.levels = []
        self.kinds = []
        self.edges = defaultdict(list)
        grid = defaultdict(list)

        def vertex(p, level):
            cell = (int(p[0] // 2), int(p[1] // 2))
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    for index in grid[(cell[0] + dx, cell[1] + dy)]:
                        if dist(self.points[index], p) <= JOIN_METERS:
                            return index
            self.points.append(p)
            self.levels.append(level)
            self.kinds.append(set())
            grid[cell].append(len(self.points) - 1)
            return len(self.points) - 1

        for points, level, kind in ways:
            indices = [vertex(p, level) for p in points]
            for index in indices:
                self.kinds[index].add(kind)
            for a, b in zip(indices, indices[1:]):
                if a == b:
                    continue
                cost = dist(self.points[a], self.points[b])
                # Elevators and stairs cost a little extra, like the time to wait or climb.
                if kind == "elevator":
                    cost += 20
                elif kind == "steps":
                    cost += 5
                self.edges[a].append((b, cost))
                self.edges[b].append((a, cost))

    def touching(self, shape, level, connectors_only=False):
        return [i for i, p in enumerate(self.points)
                if shape.distance(p) <= TOUCH_METERS and level_ok(self.levels[i], level)
                and (not connectors_only or self.kinds[i] - {"footway"})]

    def dijkstra(self, sources):
        """Shortest costs from [sources]: vertex indices (cost 0) or a dict index -> start cost."""
        best = dict(sources) if isinstance(sources, dict) else {i: 0.0 for i in sources}
        queue = [(d, i) for i, d in best.items()]
        heapq.heapify(queue)
        while queue:
            d, i = heapq.heappop(queue)
            if d > best.get(i, float("inf")):
                continue
            for j, cost in self.edges[i]:
                nd = d + cost
                if nd < best.get(j, float("inf")):
                    best[j] = nd
                    heapq.heappush(queue, (nd, j))
        return best


def complex_frame(members):
    lat = sum(s["lat"] for s in members) / len(members)
    lon = sum(s["lon"] for s in members) / len(members)
    return Frame(lat, lon)


def complex_ways(members, frame):
    ways = []
    connectors = []
    seen = set()
    for station in members:
        for kind in ("steps", "escalators", "footways", "elevators"):
            for item in station.get(kind, []):
                key = "%s/%d" % (item["type"], item["id"])
                if key in seen:
                    continue
                seen.add(key)
                tags = item["tags"]
                level = tags.get("level") or tags.get("repeat_on") or ""
                kind_of = "elevator" if tags.get("highway") == "elevator" else ("steps" if tags.get("highway") == "steps" else "footway")
                if item["type"] == "node":
                    p = frame.xy(item["lat"], item["lon"])
                    # A lone elevator node: a short way to itself so it is a vertex.
                    ways.append(([p, (p[0] + 0.01, p[1])], level, kind_of))
                    connectors.append({"p": p, "kind": kind_of, "level": level})
                    continue
                for ring in element_rings(item):
                    points = [frame.xy(*q) for q in ring]
                    if len(points) >= 2:
                        ways.append((points, level, kind_of))
                        if kind_of != "footway":
                            connectors.append({"p": points[0], "q": points[-1], "kind": kind_of, "level": level})
    return ways, connectors


# ---------------------------------------------------------------------------------------
# Boarding positions

def exit_point(arrival, departure, network, connectors):
    """Where on the arrival platform the way to the departure platform starts, with the method
    and a margin (how much better the best point is than the best one elsewhere on the platform).
    Returns (point, method, margin) or None."""
    level_a = arrival.element["tags"].get("level", "")
    level_d = departure.element["tags"].get("level", "")
    sources = network.touching(departure, level_d)
    # The way leaves the arrival platform by stairs, an escalator or an elevator: footways
    # that cross the platform at another level (often without a level tag) do not count.
    targets = network.touching(arrival, level_a, connectors_only=True)
    if sources and targets:
        distances = network.dijkstra(sources)
        reached = [(distances[i], i) for i in targets if i in distances]
        if reached:
            reached.sort()
            cost, index = reached[0]
            point = network.points[index]
            # The best reached point in another third of the platform, for the margin.
            t = along(arrival.axis, point)
            other = [c for c, i in reached if abs(along(arrival.axis, network.points[i]) - t) > 0.33]
            margin = (other[0] - cost) if other else 999.0
            return point, "path", margin, cost
    # Without a connected path: the stairs, escalators and elevators that touch the arrival
    # platform, the one whose far end is nearest to the departure platform.
    scored = []
    for connector in connectors:
        ends = [connector["p"]] + ([connector["q"]] if "q" in connector else [])
        near = [e for e in ends if arrival.distance(e) <= TOUCH_METERS]
        if not near:
            continue
        far = max(ends, key=lambda e: arrival.distance(e))
        scored.append((departure.distance(far), near[0]))
    if scored:
        scored.sort()
        cost, point = scored[0]
        t = along(arrival.axis, point)
        other = [c for c, p in scored if abs(along(arrival.axis, p) - t) > 0.33]
        margin = (other[0] - cost) if other else 999.0
        return point, "connector", margin, cost
    # Geometry alone: the point of the arrival platform nearest to the departure platform.
    if departure.points:
        target = min(departure.points, key=lambda p: arrival.distance(p))
        point = arrival.nearest_point(target)
        return point, "geometry", 0.0, arrival.distance(target)
    return None


def front_sign(axis, previous, following):
    """+1 when the train runs from the first end of the axis to the second, -1 the other way."""
    (ax, ay), (bx, by) = axis
    ux, uy = bx - ax, by - ay
    vx, vy = following[0] - previous[0], following[1] - previous[1]
    dot = ux * vx + uy * vy
    if abs(dot) < 1e-6:
        return 0
    return 1 if dot > 0 else -1


def visit_point(visit, frame, station_by_key, elements):
    if visit is None:
        return None
    if visit["stop"] is not None:
        return frame.xy(visit["stop"]["lat"], visit["stop"]["lon"])
    if visit["platform"] is not None:
        c = center_of(elements[visit["platform"]])
        if c:
            return frame.xy(*c)
    s = station_by_key[visit["station"]]
    return frame.xy(s["lat"], s["lon"])


def platform_for(visit, station, frame, elements):
    """The platform of the route at [station]: its platform member, else the platform of the
    station nearest to its stop. Returns (Shape, certain)."""
    if visit["platform"] is not None and visit["platform"] in elements:
        shape = Shape(elements[visit["platform"]], frame)
        if shape.axis:
            return shape, True
    if visit["stop"] is None:
        return None, False
    p = frame.xy(visit["stop"]["lat"], visit["stop"]["lon"])
    best = None
    for item in station.get("platforms", []):
        shape = Shape(item, frame)
        if not shape.axis or shape.area is False and len(shape.points) < 2:
            continue
        d = shape.distance(p)
        if d <= 15 and (best is None or d < best[0]):
            best = (d, shape)
    return (best[1], False) if best else (None, False)


def centre_point(arrival, departure):
    """The point of the arrival platform nearest to the centre of the departure platform."""
    n = len(departure.points)
    centre = (sum(p[0] for p in departure.points) / n, sum(p[1] for p in departure.points) / n)
    return arrival.nearest_point(centre)


def estimate(leg, other, network, connectors):
    """Where to be in the train of [leg] (0 rear, 1 front) for the platform of [other], the
    method that settled it and its confidence. The position comes from the centre of the other
    platform projected onto this one, which agrees best with the VBB dataset; the shortest
    path over stairs, escalators and footways and the nearest stairs check it: the
    confidence is high where they point to the same third of the train."""
    arrival, departure = leg["shape"], other["shape"]
    if not departure.points:
        return None, "none", 0.0

    def oriented(point):
        t = along(arrival.axis, point)
        return t if leg["sign"] > 0 else 1 - t

    position = oriented(centre_point(arrival, departure))
    checks = []
    found = exit_point(arrival, departure, network, connectors)
    if found is not None and found[1] != "geometry":
        checks.append((found[1], oriented(found[0])))
    agreeing = [m for m, t in checks if third(t) == third(position)]
    if agreeing:
        method = "centre+" + agreeing[0]
        confidence = 0.85
    elif checks:
        method = "centre-" + checks[0][0]
        confidence = 0.5
    else:
        method = "centre"
        confidence = 0.65
    edge = min(abs(position - 1 / 3), abs(position - 2 / 3))
    if edge < 0.05:
        confidence -= 0.1
    return position, method, confidence


def change_positions(data, complexes):
    station_by_key = {station_key(s): s for s in data["stations"]}
    complex_of = {}
    for index, members in enumerate(complexes):
        for s in members:
            complex_of[station_key(s)] = index
    routes, elements = route_visits(data, station_by_key)
    # Per complex: (route, index of the visit) for every route stopping there.
    calls = defaultdict(list)
    for route in routes:
        for i, visit in enumerate(route["visits"]):
            if visit is None:
                continue
            calls[complex_of[visit["station"]]].append((route, i))
    records = []
    for index, members in enumerate(complexes):
        here = calls.get(index, [])
        if len({r["line"] for r, _ in here}) < 2:
            continue
        frame = complex_frame(members)
        ways, connectors = complex_ways(members, frame)
        network = Network(frame, ways)
        legs = []
        for route, i in here:
            visits = route["visits"]
            visit = visits[i]
            station = station_by_key[visit["station"]]
            previous = visits[i - 1] if i > 0 else None
            following = visits[i + 1] if i + 1 < len(visits) else None
            shape, certain = platform_for(visit, station, frame, elements)
            if shape is None:
                continue
            here_point = visit_point(visit, frame, station_by_key, elements)
            p_prev = visit_point(previous, frame, station_by_key, elements) or here_point
            p_next = visit_point(following, frame, station_by_key, elements) or here_point
            sign = front_sign(shape.axis, p_prev, p_next)
            if sign == 0:
                continue
            legs.append({
                "route": route, "visit": visit, "station": station, "shape": shape, "certain": certain,
                "sign": sign, "previous": previous, "following": following,
                "both_neighbours": previous is not None and following is not None,
            })
        for a in legs:
            for b in legs:
                if a["route"]["line"] == b["route"]["line"]:
                    continue
                if a["previous"] is None or b["following"] is None:
                    continue
                prev_station = station_by_key[a["previous"]["station"]]
                next_station = station_by_key[b["following"]["station"]]
                ids4 = [record_id(x) for x in (a["station"], prev_station, b["station"], next_station)]
                if None in ids4:
                    continue
                # Where the arriving train came from is not where the departing one goes on.
                if complex_of[station_key(prev_station)] == index or complex_of[station_key(next_station)] == index:
                    continue
                same = a["shape"].element["id"] == b["shape"].element["id"] and a["shape"].element["type"] == b["shape"].element["type"]
                confidence = 0.0
                if same:
                    from_t = to_t = 0.5
                    method = "same"
                    confidence = 0.9
                else:
                    from_t, method, confidence = estimate(a, b, network, connectors)
                    to_t = estimate(b, a, network, connectors)[0]
                    if from_t is None:
                        continue
                    if to_t is None:
                        to_t = 0.5
                if not a["certain"]:
                    confidence -= 0.1
                if not a["both_neighbours"]:
                    confidence -= 0.05
                confidence = max(0.0, min(1.0, confidence))
                records.append({
                    "fromStation": {"id": ids4[0], "name": display_name(a["station"]["name"])},
                    "fromLines": [a["route"]["line"]],
                    "previousStation": {"id": ids4[1], "name": display_name(prev_station["name"])},
                    "fromPosition": round(from_t, 2),
                    "toStation": {"id": ids4[2], "name": display_name(b["station"]["name"])},
                    "toLines": [b["route"]["line"]],
                    "nextStation": {"id": ids4[3], "name": display_name(next_station["name"])},
                    "toPosition": round(to_t, 2),
                    "samePlatform": same,
                    "source": "osm",
                    "confidence": round(confidence, 2),
                    "_method": method,
                    "_complex": index,
                    "_lat": a["station"]["lat"],
                    "_lon": a["station"]["lon"],
                })
    # One record per change: the best when route variants give the same change twice.
    unique = {}
    for record in records:
        key = record_key(record)
        if key not in unique or record["confidence"] > unique[key]["confidence"]:
            unique[key] = record
    return list(unique.values())


def record_id(station):
    """The id of a station in a change record: the long VBB form ("900000" and six digits)
    where it has a VBB number, else its DHID outside Berlin and Brandenburg (where a station
    without VBB number stays out, as before), else None."""
    if station["vbb"]:
        return "900000" + station["vbb"][0][3:]
    if station["dhid"] and rg.dhid_state(station["dhid"][0]) not in ("11", "12"):
        return station["dhid"][0]
    return None


def short(vid):
    return "900" + vid[-6:] if len(vid) == 12 else vid


def record_key(record):
    return (short(record["fromStation"]["id"]), record["fromLines"][0], short(record["previousStation"]["id"]),
            short(record["toStation"]["id"]), record["toLines"][0], short(record["nextStation"]["id"]))


def loose_key(record):
    """A key by names and lines, for VBB records that use another id of the same complex."""
    return (name_key(record["fromStation"]["name"]), record["fromLines"][0], name_key(record["previousStation"]["name"]),
            record["toLines"][0], name_key(record["nextStation"]["name"]))


def third(position):
    return 0 if position < 0.34 else (2 if position > 0.66 else 1)


def validate(records):
    vbb = []
    with open(VBB_ASSET, "r", encoding="utf-8") as handle:
        for line in handle:
            if line.strip():
                vbb.append(json.loads(line))
    exact = {}
    loose = {}
    for record in vbb:
        for from_line in record["fromLines"]:
            for to_line in record["toLines"]:
                r = dict(record, fromLines=[from_line], toLines=[to_line])
                exact[record_key(r)] = record
                loose[loose_key(r)] = record
    pairs = []
    for record in records:
        match = exact.get(record_key(record)) or loose.get(loose_key(record))
        if match:
            pairs.append((record, match))
    log("")
    log("Check against vbb-change-positions: %d of %d OSM records have a VBB record (VBB has %d)"
        % (len(pairs), len(records), len(vbb)))
    buckets = defaultdict(lambda: [0, 0, 0])
    by_method = defaultdict(lambda: [0, 0])
    for record, match in pairs:
        ok = third(record["fromPosition"]) == third(match["fromPosition"]) and record["samePlatform"] == match["samePlatform"]
        near = abs(record["fromPosition"] - match["fromPosition"]) <= 0.25
        bucket = math.floor(record["confidence"] * 10) / 10
        buckets[bucket][0] += 1
        buckets[bucket][1] += ok
        buckets[bucket][2] += near
        by_method[record["_method"]][0] += 1
        by_method[record["_method"]][1] += ok
    log("  confidence  records  same third  within 0.25   (cumulative from the top: same third)")
    total = agree = 0
    for bucket in sorted(buckets, reverse=True):
        n, ok, near = buckets[bucket]
        total += n
        agree += ok
        log("  %.1f        %5d    %5.1f %%     %5.1f %%      %5.1f %% of %d"
            % (bucket, n, 100.0 * ok / n, 100.0 * near / n, 100.0 * agree / total, total))
    for method, (n, ok) in sorted(by_method.items()):
        log("  method %-9s %4d records, %5.1f %% same third" % (method, n, 100.0 * ok / n))
    kept = [(r, m) for r, m in pairs if r["confidence"] >= MIN_CONFIDENCE]
    if kept:
        ok = sum(third(r["fromPosition"]) == third(m["fromPosition"]) and r["samePlatform"] == m["samePlatform"] for r, m in kept)
        log("  at confidence >= %.2f: %d of %d agree (%.1f %%)" % (MIN_CONFIDENCE, ok, len(kept), 100.0 * ok / len(kept)))
    return pairs


# ---------------------------------------------------------------------------------------
# The station asset

def platform_lines(data, elements_lines):
    pass


# ---------------------------------------------------------------------------------------
# Entrances

# Tags of a node that make it an entrance of a station by itself.
TRANSIT_RAILWAY = ("subway_entrance", "train_station_entrance", "light_rail_entrance")
U_MARKERS = ("entrance_marker:subway",)
S_MARKERS = ("entrance_marker:s-train", "entrance_marker:light_rail", "entrance_marker:train")
# Tags of a shop, a restaurant, an office or a house: the door of something else.
BUSINESS_KEYS = ("shop", "amenity", "office", "craft", "healthcare", "tourism", "leisure", "brand",
                 "opening_hours", "club", "company", "disused:shop")
# A door without transit tags is one of the station when the station's mapped steps, footways
# or elevators pass through it, this close.
ON_NETWORK_METERS = 2.0
# A door without transit tags this many floors up or higher is one of a house or a mall.
HIGHEST_STATION_LEVEL = 3
# A name of such a door that speaks of a way in or of a street (not of a shop or house).
STATION_DOOR_NAME_RE = re.compile(
    r"(eingang|ausgang|aufgang|zugang|entrance|exit|stra(ss|ß)e|str\.|platz|ufer|allee|damm|weg|brücke|markt|ring|tor)\b",
    re.IGNORECASE)
# An entrance joins the walking network of a station at way points this close.
ENTRANCE_SNAP_METERS = 12.0
# A platform the network does not reach counts as this much farther than the straight line.
DETOUR_FACTOR = 1.5
UNREACHED_PENALTY = 50.0
# The sign of an entrance: the modes of the platforms within this walk (or the nearest).
SHORT_WALK_METERS = 120.0
# Reported as suspicious: an entrance this far (straight line) from every platform of its sign.
SUSPICIOUS_METERS = 250.0
# A door of a mall, hotel, shop or house is kept as an access through a building ("k":"b")
# when the station's mapped steps, footways, corridors or elevators pass this close to it
# (an underground door, level < 0: the second value, as basement passages are drawn loosely) ...
BUILDING_SNAP_METERS = 3.0
UNDERGROUND_SNAP_METERS = 8.0
# ... and lead from it to a platform within this walk (or when it lies in the outline of a
# station building, or is a member of a stop area of the station).
BUILDING_WALK_METERS = 200.0
# A door the station's network passes through that carries nothing but door tags (no shop,
# address or name), is mapped on a level of the station and lies this close to one of its
# platforms is a door of the station itself, not of a house beside it: a station entrance
# (like the door at the south end of Südkreuz, level 1, where the footway along the tracks
# comes in).
PLATFORM_DOOR_METERS = 30.0
# The tags such a plain door may have.
PLAIN_DOOR_KEYS = {"entrance", "door", "automatic_door", "level", "layer", "wheelchair", "access", "width",
                   "indoor", "handle", "door:width", "kerb", "step_count"}
# The stations printed with their accesses through buildings.
BUILDING_REPORT = ("Zoologischer Garten", "Kurfürstendamm", "Alexanderplatz", "Friedrichstraße", "Hauptbahnhof",
                   "Gesundbrunnen", "Potsdamer Platz", "Ostkreuz")


def platform_mode(item, lines, members):
    """'U' or 'S' for a platform: by its lines, its tags, else the nearest station listing it."""
    modes = {line[0] for line in lines}
    if len(modes) == 1:
        return modes.pop()
    tags = item["tags"]
    if tags.get("subway") == "yes" or tags.get("station") == "subway":
        return "U"
    if tags.get("light_rail") == "yes":
        # At a Stadtbahn station (no S-Bahn among the stations) a light rail platform is U.
        if not any("S" in s["products"] for s in members) and any("U" in s["products"] for s in members):
            return "U"
        return "S"
    if tags.get("train") == "yes":
        # S-Bahn platforms are often tagged train=yes (and a U-Bahn station near the S-Bahn
        # lists them too); only at a station of regional trains alone is it one of those.
        return "R" if all(s["products"] == "R" for s in members) else "S"
    key = "%s/%d" % (item["type"], item["id"])
    owners = [s for s in members if any("%s/%d" % (p["type"], p["id"]) == key for p in s.get("platforms", []))]
    c = center_of(item)
    if owners and c:
        return min(owners, key=lambda s: meters(c[0], c[1], s["lat"], s["lon"]))["product"]
    return members[0]["product"]


def explicit_mode(tags):
    """What the tags of an entrance say about its sign: 'U', 'S', 'SU' or ''."""
    mode = ""
    if tags.get("railway") in ("train_station_entrance", "light_rail_entrance") or any(tags.get(k) == "yes" for k in S_MARKERS):
        mode += "S"
    if (tags.get("railway") == "subway_entrance" or any(tags.get(k) == "yes" for k in U_MARKERS)
            or tags.get("entrance_marker") == "subway"):
        mode += "U"
    return mode


def entrance_kind(entrance, station_areas, station_keys_near, on_network=False):
    """Why [entrance] is an entrance of a station ('railway', 'marker', 'stop_area', 'name',
    'station door'), or why not ('-emergency', '-private', '-construction', '-business',
    '-house', '-other'). [on_network]: whether the steps, footways or elevators of a station
    near it pass through it. "stationBuilding" (from the fetch, when known): whether the door
    lies in the outline of a station building."""
    tags = entrance.get("tags", {})
    if tags.get("entrance") == "emergency" or tags.get("exit") == "emergency" or tags.get("emergency") == "yes":
        return "-emergency"
    if tags.get("access") in ("no", "private"):
        return "-private"
    if tags.get("railway") == "construction" or "construction" in tags or tags.get("disused") == "yes":
        return "-construction"
    if tags.get("railway") in TRANSIT_RAILWAY:
        return "railway"
    business = any(k in tags for k in BUSINESS_KEYS) or tags.get("entrance") == "shop"
    if business:
        return "-business"
    if tags.get("public_transport") == "entrance" or explicit_mode(tags):
        return "marker"
    if station_areas:
        return "stop_area"
    name = tags.get("name") or ""
    if name and any(key and key in name_key(name) for key in station_keys_near):
        return "name"
    if entrance.get("stationBuilding"):
        return "station door"
    # An address, a door for customers or one high up in a building: a house, shop or office.
    if (any(k.startswith("addr:") for k in tags) or tags.get("access") == "customers"
            or min(levels(tags.get("level")) or {0.0}) >= HIGHEST_STATION_LEVEL):
        return "-house"
    if name and not STATION_DOOR_NAME_RE.search(name):
        return "-other"
    if entrance.get("viaNetwork"):
        # Taken by the PBF extract only because the station's network passes through it, not
        # tagged as anything of the station and outside its buildings: a door of a mall, hotel
        # or passage on the way, kept only as an access through a building (building_evidence).
        return "-other"
    if tags.get("highway") == "elevator" or on_network:
        return "station door"
    return "-other"


def walks_to_platforms(entrance, place, network, mode=None):
    """Walking distance from [entrance] to each platform of a complex [place]: along the
    steps, footways and elevators of the station where the entrance joins them, else the
    straight line with a detour. Returns [(metres, platform)] for platforms of [mode] (all
    without one)."""
    frame = place["frame"]
    p = frame.xy(entrance["lat"], entrance["lon"])
    level = entrance.get("tags", {}).get("level") or ""
    platforms = [q for q in place["platforms"] if not mode or q["mode"] in mode]
    starts = {}
    for i, v in enumerate(network.points):
        d = dist(p, v)
        if d <= ENTRANCE_SNAP_METERS and level_ok(network.levels[i], level):
            starts[i] = min(starts.get(i, float("inf")), d)
    best = network.dijkstra(starts) if starts else {}
    result = []
    reached_any = False
    walks = []
    for q in platforms:
        touching = network.touching(q["shape"], q["level"]) if best else []
        on_network = min((best[i] for i in touching if i in best), default=None)
        if on_network is not None:
            reached_any = True
        walks.append(on_network)
    for q, on_network in zip(platforms, walks):
        straight = q["shape"].distance(p) * DETOUR_FACTOR
        if on_network is not None:
            result.append((on_network, q))
        elif reached_any:
            # The network of the entrance leads elsewhere: this platform is not its way.
            result.append((straight + UNREACHED_PENALTY, q))
        else:
            result.append((straight, q))
    result.sort(key=lambda r: r[0])
    return result


def network_walk(entrance, place, network, snap=BUILDING_SNAP_METERS):
    """The walk from [entrance] to the nearest platform of [place] over the mapped network
    only (no straight-line fallback), starting where a way of the network passes within
    [snap] metres of it. Returns (whether a way passes within [snap], the walk or None when
    the network does not lead from it to a platform)."""
    frame = place["frame"]
    p = frame.xy(entrance["lat"], entrance["lon"])
    level = entrance.get("tags", {}).get("level") or ""
    starts = {}
    for a, edges in network.edges.items():
        if not level_ok(network.levels[a], level):
            continue
        for b, _ in edges:
            if seg_dist(p, network.points[a], network.points[b]) <= snap:
                starts[a] = min(starts.get(a, float("inf")), dist(p, network.points[a]))
    if not starts:
        return False, None
    best = network.dijkstra(starts)
    walk = None
    for q in place["platforms"]:
        for i in network.touching(q["shape"], q["level"]):
            if i in best and (walk is None or best[i] < walk):
                walk = best[i]
    return True, walk


def building_evidence(entrance, area_stations, candidates, station_platforms, network_of):
    """Why a door that is not a station entrance proper (a mall, hotel, shop or house door)
    still leads into the station: 'station building' (it lies in the outline of one), 'stop
    area' (a member of the station's stop area), 'network' (the station's mapped steps,
    footways, corridors or elevators pass through it and reach a platform within
    BUILDING_WALK_METERS) or 'underground' (a door below ground, level < 0, that the station's
    network passes through, though not mapped on to a platform). Doors at street level that
    merely lie near a station's steps are not enough: the shop beside a stairway is no way in.
    Returns (reason, complex indices it leads to) or (None, ())."""
    door_levels = levels(entrance.get("tags", {}).get("level"))
    if min(door_levels or {0.0}) >= HIGHEST_STATION_LEVEL:
        return None, ()
    if area_stations:
        return "stop area", candidates
    underground = bool(door_levels) and max(door_levels) < 0
    reached = {}
    passed = []
    for i in candidates:
        snapped, walk = network_walk(entrance, station_platforms[i], network_of(i),
                                     UNDERGROUND_SNAP_METERS if underground else BUILDING_SNAP_METERS)
        if walk is not None and walk <= BUILDING_WALK_METERS:
            reached[i] = walk
        elif underground and network_walk(entrance, station_platforms[i], network_of(i))[0]:
            passed.append(i)
    if reached:
        return "network", tuple(sorted(reached, key=reached.get))
    if passed:
        return "underground", tuple(passed)
    if entrance.get("stationBuilding"):
        return "station building", candidates
    return None, ()


def platform_side_door(entrance, place):
    """Whether [entrance], a door only the station's network made an entrance (see
    building_evidence), is a door of the station itself: plain door tags (PLAIN_DOOR_KEYS,
    access only "yes", an entrance value of a way in), a level tag of its own (mapped as part of
    the station's levels, not underground) and a platform of [place] within PLATFORM_DOOR_METERS."""
    tags = entrance.get("tags", {})
    if not set(tags) <= PLAIN_DOOR_KEYS:
        return False
    if tags.get("access", "yes") != "yes" or tags.get("entrance") not in ("yes", "main", "staircase"):
        return False
    door_levels = levels(tags.get("level"))
    if not door_levels or min(door_levels) < 0:
        return False
    p = place["frame"].xy(entrance["lat"], entrance["lon"])
    return any(q["shape"].distance(p) <= PLATFORM_DOOR_METERS for q in place["platforms"])


def assign_entrances(data, complexes, station_platforms):
    """The entrances of the stations as (complex index, asset item).

    Kept: nodes tagged as entrances of the U-Bahn or S-Bahn (railway=subway_entrance,
    train_station_entrance, light_rail_entrance; entrance_marker:*; public_transport=entrance),
    other entrances that are members of the stop area of a station or carry its name, and
    doors in the outline of a station building (when the extract knows it) or through which
    the mapped steps, footways or elevators of the station pass. Not kept: emergency exits,
    private doors, and the doors of shops, restaurants, offices, malls and houses near a
    station (business tags, an address, access=customers, high floors, other names) unless
    they lead into the station (see building_evidence): those are kept as accesses through a
    building, "k":"b", with "oh" (opening_hours) when tagged.

    The station: the one of the stop areas the entrance is a member of, else, of the
    stations near it, the one whose platforms (of the sign of the entrance) it reaches by the
    shortest walk.

    The sign "m", always set: what the tags say (unless no letter of it fits the station);
    else the product of its stop-area stations if they are one; else the modes of the
    platforms within SHORT_WALK_METERS walk, or of the nearest. Never the products of the
    whole station by default."""
    complex_of = {}
    for index, members in enumerate(complexes):
        for s in members:
            complex_of[station_key(s)] = index
    station_by_key = {station_key(s): s for s in data["stations"]}
    areas_of_entrance = defaultdict(set)
    for area in data["stopAreas"]:
        stations = {k for m in area["members"] for k in ["%s/%d" % (m["type"], m["ref"])] if k in station_by_key}
        if not stations:
            continue
        for m in area["members"]:
            if m["type"] == "node":
                areas_of_entrance[m["ref"]] |= stations
    networks = {}

    def network_of(index):
        if index not in networks:
            place = station_platforms[index]
            ways, _ = complex_ways(place["members"], place["frame"])
            networks[index] = Network(place["frame"], ways)
        return networks[index]

    kinds = Counter()
    how = Counter()
    dropped = []
    buildings = []
    result = []
    report = []
    for entrance in data["entrances"]:
        tags = entrance.get("tags", {})
        area_stations = areas_of_entrance.get(entrance["id"], set())
        near = [k for k in entrance.get("stations", []) if k in complex_of]
        on_network = False
        for i in {complex_of[k] for k in near}:
            frame = station_platforms[i]["frame"]
            p = frame.xy(entrance["lat"], entrance["lon"])
            if any(dist(p, v) <= ON_NETWORK_METERS for v in network_of(i).points):
                on_network = True
                break
        kind = entrance_kind(entrance, area_stations, {station_by_key[k]["key"] for k in near}, on_network)
        if area_stations:
            candidates = {complex_of[k] for k in area_stations if k in complex_of}
        else:
            candidates = {complex_of[k] for k in near}
        building = None
        if kind in ("-business", "-house", "-other") and candidates:
            building, reached = building_evidence(entrance, area_stations, candidates, station_platforms, network_of)
            if building:
                candidates = set(reached)
                kind = "building (%s)" % building
            if building == "network" and platform_side_door(entrance, station_platforms[reached[0]]):
                # Beside the platforms, on a level of the station: its own door, an entrance.
                building = None
                kind = "station door (platform side)"
        kinds[kind] += 1
        if kind.startswith("-"):
            if kind in ("-business", "-house", "-other") and near:
                dropped.append((kind, entrance, station_by_key[near[0]]["name"]))
            continue
        mode = explicit_mode(tags)
        if mode:
            fitting = {i for i in candidates if any(
                c in station_platforms[i]["products"] + "".join(q["mode"] for q in station_platforms[i]["platforms"])
                for c in mode)}
            candidates = fitting or candidates
        if not candidates:
            continue
        walks = {}
        for i in candidates:
            walks[i] = walks_to_platforms(entrance, station_platforms[i], network_of(i), mode or None) \
                or walks_to_platforms(entrance, station_platforms[i], network_of(i))
        index = min(candidates, key=lambda i: walks[i][0][0] if walks[i] else
                    meters(entrance["lat"], entrance["lon"], complexes[i][0]["lat"], complexes[i][0]["lon"]) * 2)
        if mode:
            # A sign the station does not have, like an entrance of the S-Bahn tunnel tagged as
            # one of the U-Bahn.
            products = "".join(s["products"] for s in complexes[index])
            products += "".join(q["mode"] for q in station_platforms[index]["platforms"])
            fitting = "".join(c for c in mode if c in products)
            if not fitting:
                # None of its letters fits: the tags are wrong, the platforms decide. (Where
                # one of two fits, the other mode is more likely missing in the extract.)
                how["tags not fitting the station"] += 1
                mode = ""
        if mode:
            how["tags"] += 1
        elif area_stations:
            products = {c for k in area_stations if complex_of.get(k) == index for c in station_by_key[k]["products"]}
            if len(products) == 1:
                mode = products.pop()
                how["stop area"] += 1
        if not mode:
            all_walks = walks_to_platforms(entrance, station_platforms[index], network_of(index))
            if all_walks:
                limit = max(SHORT_WALK_METERS, all_walks[0][0])
                modes = {q["mode"] for d, q in all_walks if d <= limit}
                mode = "".join(c for c in PRODUCT_ORDER if c in modes)
                how["walk"] += 1
            else:
                mode = "".join(sorted({c for s in complexes[index] for c in s["products"]}, key=PRODUCT_ORDER.index))[:1]
                how["no platform"] += 1
        item = {"lat": r6(entrance["lat"]), "lon": r6(entrance["lon"]), "m": mode}
        if building:
            item["k"] = "b"
        for key in ("name", "ref", "wheelchair", "level"):
            if tags.get(key):
                item[key] = tags[key]
        if building and tags.get("opening_hours"):
            item["oh"] = tags["opening_hours"]
        result.append((index, item))
        if building:
            buildings.append((display_name(complexes[index][0]["name"]), building, entrance, mode))
        place = station_platforms[index]
        p = place["frame"].xy(entrance["lat"], entrance["lon"])
        straight = min((q["shape"].distance(p) for q in place["platforms"] if q["mode"] in mode), default=float("inf"))
        report.append((entrance, index, mode, straight, walks[index][0][0] if walks[index] else None))
    log("")
    log("Entrances: %s" % dict(sorted(kinds.items())))
    log("  sign from %s" % dict(how))
    log("  kept: %d station entrances, %d accesses through a building"
        % (len(result) - len(buildings), len(buildings)))
    log("  accesses through a building at %s:" % ", ".join(BUILDING_REPORT))
    for name, why, entrance, mode in sorted(buildings, key=lambda b: (b[0], b[2]["lat"])):
        if any(wanted in name for wanted in BUILDING_REPORT):
            t = entrance["tags"]
            what = t.get("name") or t.get("shop") or t.get("amenity") or t.get("tourism") or t.get("addr:street", "") + " " + t.get("addr:housenumber", "")
            log("    %-22s %-2s %.6f,%.6f %-16s entrance=%s %s%s" % (name[:22], mode, entrance["lat"], entrance["lon"], why,
                                                              t.get("entrance"), what.strip(),
                                                              " [oh %s]" % t["opening_hours"] if t.get("opening_hours") else ""))
    log("  dropped near stations (shops, offices, houses, unrelated doors): %d" % len(dropped))
    for kind, entrance, name in sorted(dropped, key=lambda x: (x[2], x[0])):
        t = entrance["tags"]
        what = t.get("name") or t.get("shop") or t.get("amenity") or t.get("addr:street", "") + " " + t.get("addr:housenumber", "")
        log("    %-9s %-28s %.6f,%.6f entrance=%s %s" % (kind, name[:28], entrance["lat"], entrance["lon"], t.get("entrance"), what.strip()))
    # A station without platform geometry of the sign of an entrance (only a platform node in
    # OSM, like the U9 at Rathaus Steglitz beside the mapped S-Bahn platform): no distance.
    blind = sorted({display_name(complexes[r[1]][0]["name"]) + ("" if not station_platforms[r[1]]["platforms"] else " (%s)" % r[2])
                    for r in report if r[3] == float("inf")})
    log("  stations without platform geometry of an entrance's sign (sign from tags or products only): %d %s"
        % (len(blind), blind))
    far = [r for r in report if r[3] != float("inf") and r[3] > SUSPICIOUS_METERS]
    log("  entrances more than %d m from every platform of their sign: %d" % (SUSPICIOUS_METERS, len(far)))
    for entrance, index, mode, straight, walk in far:
        log("    %-28s %-2s %.6f,%.6f %4.0f m %s" % (complexes[index][0]["name"][:28], mode, entrance["lat"], entrance["lon"],
                                                  straight, entrance["tags"].get("name", "")))
    return result


# Lifecycle values of a platform that is not (or no longer, or not yet) in use.
UNUSED_VALUES = {"construction", "disused", "abandoned", "proposed", "planned", "razed", "demolished", "no"}


def platform_in_use(tags):
    """False for a platform under construction, disused, proposed or razed (railway=construction,
    public_transport=disused, disused=yes, a disused:railway=platform...), which the extract
    takes with the station's platforms but nobody changes trains on."""
    if tags.get("railway") in UNUSED_VALUES or tags.get("public_transport") in UNUSED_VALUES:
        return False
    if any(tags.get(key) == "yes" for key in ("disused", "abandoned", "construction", "proposed")):
        return False
    return not any(key.split(":")[0] in UNUSED_VALUES and key.endswith((":railway", ":public_transport"))
                   for key in tags)


def is_rail_platform(tags):
    """True for a platform of the trains of a station, false for one of buses or trams beside it
    (bus=yes, tram=yes, highway=bus_stop, or highway=platform without railway=platform: the bays
    of a bus station, as at Tuttlingen) and for a stop area relation that carries
    railway=platform (Weinheim (Bergstr) Hbf), and for one not in use (platform_in_use)."""
    if tags.get("bus") == "yes" or tags.get("tram") == "yes" or tags.get("highway") == "bus_stop":
        return False
    if tags.get("highway") == "platform" and tags.get("railway") != "platform" and \
            not any(tags.get(mode) == "yes" for mode in ("train", "light_rail", "subway")):
        return False
    if tags.get("type") == "public_transport" or tags.get("public_transport") == "stop_area":
        return False
    return platform_in_use(tags)


def build_asset(data, complexes, areas_of, lines_of_platform):
    out_stations = []
    station_platforms = []  # per complex: the platforms as dicts with shape, mode, level
    for members in complexes:
        frame = complex_frame(members)
        shapes = []
        ids = []
        for s in members:
            for vid in s["vbb"]:
                if vid not in ids:
                    ids.append(vid)
            ifopt = s["tags"].get("ref:IFOPT")
            if ifopt and ifopt not in ids:
                ids.append(ifopt)
        for s in members:
            for d in s["dhid"]:
                if d not in ids:
                    ids.append(d)
        products = "".join(sorted({c for s in members for c in s["products"]}, key=PRODUCT_ORDER.index))
        kinds = "".join(sorted({c for s in members for c in s["kinds"]}, key=KIND_ORDER.index))
        platforms = []
        for item in dedupe([p for s in members for p in s.get("platforms", [])]):
            tags = item["tags"]
            if not is_rail_platform(tags):
                continue
            shape = Shape(item, frame)
            if not shape.axis or dist(*shape.axis) < 5:
                continue
            a = frame.latlon(*shape.axis[0])
            b = frame.latlon(*shape.axis[1])
            entry = {"a": [r6(a[0]), r6(a[1]), r6(b[0]), r6(b[1])]}
            ref = tags.get("ref") or tags.get("local_ref")
            if ref:
                entry["ref"] = ref
            if tags.get("level"):
                entry["level"] = tags["level"]
            lines = sorted(lines_of_platform.get("%s/%d" % (item["type"], item["id"]), set()))
            if not lines and tags.get("route_ref"):
                lines = sorted({x.strip() for x in tags["route_ref"].split(";") if LINE_RE.match(x.strip())})
            if lines:
                entry["lines"] = lines
            platforms.append(entry)
            shapes.append({"shape": shape, "level": tags.get("level") or "", "key": "%s/%d" % (item["type"], item["id"]),
                           "mode": platform_mode(item, lines, members), "ref": ref or ""})
        lifts = []
        seen = set()
        for s in members:
            for kind in ("elevators", "steps", "escalators"):
                for item in s.get(kind, []):
                    key = "%s/%d" % (item["type"], item["id"])
                    if key in seen:
                        continue
                    tags = item["tags"]
                    if kind == "steps" and "conveying" not in tags:
                        continue
                    seen.add(key)
                    c = center_of(item)
                    if not c:
                        continue
                    entry = {"t": "elevator" if tags.get("highway") == "elevator" else "escalator",
                             "lat": r6(c[0]), "lon": r6(c[1])}
                    if tags.get("level"):
                        entry["level"] = tags["level"]
                    lifts.append(entry)
        out_stations.append({
            "name": complex_name(members),
            "ids": ids,
            "products": products,
            "kinds": kinds,
            "lat": r6(sum(s["lat"] for s in members) / len(members)),
            "lon": r6(sum(s["lon"] for s in members) / len(members)),
            "platforms": platforms,
            "lifts": lifts,
            "entrances": [],
        })
        station_platforms.append({"frame": frame, "members": members, "platforms": shapes, "products": products})
    # The platforms of a station are fetched by radius, so a platform near a station of another
    # mode is listed with it too (the S-Bahn platforms of Hauptbahnhof with its U-Bahn station).
    # Entrances are led only to the platforms of a complex's own modes, and to those of
    # another mode no complex of that mode claims (an S-Bahn station missing in the extract).
    claimed = defaultdict(set)
    for index, place in enumerate(station_platforms):
        for q in place["platforms"]:
            if q["mode"] in place["products"]:
                claimed[q["key"]].add(index)
    for index, place in enumerate(station_platforms):
        place["platforms"] = [q for q in place["platforms"]
                              if q["mode"] in place["products"] or not (claimed[q["key"]] - {index})]
    # Entrances: only those of the stations, each to the station it leads to, with the modes
    # of the platforms it leads to (see assign_entrances).
    seen_at = {}
    for index, item in assign_entrances(data, complexes, station_platforms):
        # Two door nodes on the same spot (mapped twice in OSM): one entrance.
        at = (index, item["lat"], item["lon"])
        if at in seen_at:
            if len(item) > len(seen_at[at]):
                seen_at[at].update(item)
            continue
        seen_at[at] = item
        out_stations[index]["entrances"].append(item)
    for station in out_stations:
        station["entrances"].sort(key=lambda e: (e["lat"], e["lon"]))
    # Bus and tram stop positions with a "Pos." number.
    stop_area_ids = {}
    for area in data["stopAreas"]:
        t = area["tags"]
        stop_area_ids[area["id"]] = (vbb_ids(t.get("ref:VBB"), t.get("ref:IFOPT"), t.get("website")), dhids(t.get("ref:IFOPT")))
    stops = []
    taken = defaultdict(list)
    ordered = sorted(data["stopPositions"], key=lambda s: 0 if s["kind"] == "stop_position" else 1)
    for stop in ordered:
        number = stop.get("local_ref")
        if not number:
            ref = stop.get("ref") or ""
            if 0 < len(ref) <= 3:
                number = ref
        if not number or not stop.get("name"):
            continue
        key = (name_key(stop["name"]), number, "t" if stop["tram"] else "b")
        # A platform only where no stop position of the same name and number is known nearby
        # (the same name and number farther away is another stop: "Bahnhof" 1 of another town).
        if any(meters(stop["lat"], stop["lon"], lat, lon) <= SAME_STOP_METERS for lat, lon in taken[key]):
            continue
        taken[key].append((stop["lat"], stop["lon"]))
        ids = []
        for area_id in stop.get("stopAreas", []):
            for vid in stop_area_ids.get(area_id, ([], []))[0]:
                if vid not in ids:
                    ids.append(vid)
        for vid in vbb_ids(stop.get("ref:IFOPT")):
            if vid not in ids:
                ids.append(vid)
        for area_id in stop.get("stopAreas", []):
            for vid in stop_area_ids.get(area_id, ([], []))[1]:
                if vid not in ids:
                    ids.append(vid)
        for vid in dhids(stop.get("ref:IFOPT")):
            if vid not in ids:
                ids.append(vid)
        mode = ("b" if stop["bus"] else "") + ("t" if stop["tram"] else "")
        item = {"name": stop["name"], "pos": number, "lat": r6(stop["lat"]), "lon": r6(stop["lon"]), "mode": mode or "b"}
        if ids:
            item["ids"] = ids
        route_ref = stop["tags"].get("route_ref")
        if route_ref:
            item["lines"] = [x.strip() for x in route_ref.split(";") if x.strip()]
        stops.append(item)
    stops.sort(key=lambda s: (s["name"], s["pos"]))
    return {
        "license": "ODbL 1.0",
        "attribution": "© OpenStreetMap contributors",
        "source": "https://www.openstreetmap.org/copyright",
        "generated": time.strftime("%Y-%m-%d", time.gmtime()),
        "stations": out_stations,
        "stops": stops,
        "stopAreas": build_stop_areas(data),
    }


# ---------------------------------------------------------------------------------------
# Bus and tram stop areas with all their stop positions

# Two positions of one area this close are one (a stop position mapped twice, or for bus and
# tram at one spot).
SAME_POSITION_METERS = 8.0
# A stop position this close to a platform of its mode (the one a route lists it with, else
# the nearest) is the place on the road beside that platform: it is merged into the platform.
STOP_PLATFORM_METERS = 25.0
# Positions without a stop area join an area of their name this close, else form one with
# positions of their name this close.
LOOSE_JOIN_METERS = 250.0
# A position farther than this from the middle of its area is left out.
STRAY_METERS = 500.0
# An area without VBB number takes those of an area of its name this close.
ID_BORROW_METERS = 300.0


def short_ref(position):
    """The number on the sign of a stop position: local_ref, or a ref of up to 3 characters."""
    number = position.get("local_ref") or position["tags"].get("local_ref")
    if not number:
        ref = position.get("ref") or position["tags"].get("ref") or ""
        if 0 < len(ref) <= 3:
            number = ref
    return number.strip() if number else None


def mode_of(position):
    return ("b" if position["bus"] else "") + ("t" if position["tram"] else "") or "b"


def element_key(item):
    return "%s/%d" % (item["type"], item["id"])


def route_pairs(data):
    """Stop position -> platforms it is listed next to in a bus or tram route relation (PTv2
    lists each stop as "stop" then "platform"): {"node/1": {"way/2", ...}}."""
    types = {"n": "node", "w": "way", "r": "relation"}
    pairs = defaultdict(set)
    for route in data.get("busRoutes", []):
        members = route.get("m", [])
        for a, b in zip(members, members[1:]):
            roles = (a[2].startswith("stop"), b[2].startswith("stop"))
            if roles == (True, False) or roles == (False, True):
                stop, platform = (a, b) if roles[0] else (b, a)
                if platform[2].startswith("platform"):
                    pairs["%s/%d" % (types.get(stop[0], stop[0]), stop[1])].add(
                        "%s/%d" % (types.get(platform[0], platform[0]), platform[1]))
    return pairs


def area_positions(members, pairs=None):
    """The positions of one area: every platform a position of its own (one mapped twice, as a
    node and a way or two nodes, within SAME_POSITION_METERS is one), and every stop position
    merged into its platform: the one it is listed next to in a route relation, else the
    nearest of its mode, within STOP_PLATFORM_METERS, unless that platform has another number
    already. A stop position without a platform is a position of its own (stop positions
    within SAME_POSITION_METERS one). Each as a dict lat, lon, mode, ref, srcs."""
    pairs = pairs or {}
    stops = [m for m in members if m["kind"] == "stop_position"]
    platforms = [m for m in members if m["kind"] != "stop_position"]

    def joins(item, other):
        return (item["ref"] is None or other["ref"] is None or item["ref"] == other["ref"]) and \
            meters(item["lat"], item["lon"], other["lat"], other["lon"]) <= SAME_POSITION_METERS

    kept = []
    for platform in platforms:
        item = {"lat": platform["lat"], "lon": platform["lon"], "mode": set(mode_of(platform)),
                "ref": short_ref(platform), "src": platform, "srcs": [platform], "platform": True}
        # Two drawn platform areas are two platforms however close; a node beside one is the same.
        twin = next((k for k in kept if joins(item, k) and
                     not (platform["type"] == "way" and all(s["type"] == "way" for s in k["srcs"]))), None)
        if twin is None:
            kept.append(item)
        else:
            twin["mode"] |= item["mode"]
            twin["srcs"].append(platform)
            twin["ref"] = twin["ref"] or item["ref"]
    loose = []
    for stop in stops:
        ref = short_ref(stop)
        listed = pairs.get(element_key(stop), ())
        best = None
        for k in kept:
            if not set(mode_of(stop)) & k["mode"]:
                continue
            d = meters(stop["lat"], stop["lon"], k["lat"], k["lon"])
            if d > STOP_PLATFORM_METERS or (ref and k["ref"] and k["ref"] != ref):
                continue
            routed = any(element_key(src) in listed for src in k["srcs"])
            rank = (0 if routed else 1, d)
            if best is None or rank < best[0]:
                best = (rank, k)
        if best is None:
            loose.append({"lat": stop["lat"], "lon": stop["lon"], "mode": set(mode_of(stop)), "ref": ref,
                          "src": stop, "srcs": [stop], "platform": False})
        else:
            best[1]["srcs"].append(stop)
            best[1]["ref"] = best[1]["ref"] or ref
    merged = list(kept)
    alone = []
    for item in loose:
        twin = next((o for o in alone if joins(item, o)), None)
        if twin is None:
            alone.append(item)
        else:
            twin["mode"] |= item["mode"]
            twin["ref"] = twin["ref"] or item["ref"]
            twin["srcs"] += item["srcs"]
    return merged + alone


# A stop or platform member of a route that is not one of the positions itself goes to the
# nearest position of its mode this close.
MEMBER_SNAP_METERS = 25.0
# Tram lines of Berlin with an M; the other tram lines have a plain number below 100.
TRAM_METRO_LINES = {"M1", "M2", "M4", "M5", "M6", "M8", "M10", "M13", "M17"}
LINE_NAME_RE = re.compile(r"^(?:Bus|Tram|STR|Straßenbahn|Metrobus|Metrotram)\s+([A-Z]{0,3}\d{1,4}[A-Z]?)\b", re.I)


def line_sort_key(ref):
    """Natural order of bus and tram lines: M48 < M85 < 100 < 187 < X9 < N2, then the rest."""
    match = re.match(r"^([A-Za-z]*)(\d+)(.*)$", ref)
    if not match:
        return (9, 0, ref)
    prefix, number, rest = match.group(1).upper(), int(match.group(2)), match.group(3)
    group = {"M": 0, "": 1, "X": 2, "N": 4}.get(prefix, 3)
    return (group, number, prefix + rest)


def route_line(route):
    """The line of a bus or tram route relation: its ref, else the line in its name."""
    ref = (route.get("ref") or "").strip()
    if ref:
        return ref.split(";")[0].strip()
    match = LINE_NAME_RE.match((route.get("name") or "").strip())
    return match.group(1).upper() if match else None


def guess_product(ref, mode):
    """For a route_ref line at a position of [mode]: "t" or "b"."""
    if mode == "t":
        return "t"
    if mode == "b" or "t" not in mode:
        return "b"
    return "t" if ref.upper() in TRAM_METRO_LINES or re.match(r"^\d{1,2}$", ref) else "b"


def assign_lines(data, positions):
    """Fills [positions] (dicts with lat, lon, mode, area, srcs) with the lines that stop there:
    "lines" {"b": set, "t": set}: each position a stop or platform member of a route stands
    for (itself, or its platform, or the nearest of its mode within MEMBER_SNAP_METERS).
    Returns counts for the log."""
    # An element in two stop areas (two relations of one stop) stands in each: a list.
    index = defaultdict(list)
    grid = defaultdict(list)
    for position in positions:
        position["lines"] = {"b": set(), "t": set()}
        for src in position["srcs"]:
            index["%s/%d" % (src["type"], src["id"])].append(position)
        grid[(int(position["lat"] * 500), int(position["lon"] * 300))].append(position)

    def nearest(lat, lon, product):
        best = None
        cell = (int(lat * 500), int(lon * 300))
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                for position in grid.get((cell[0] + dy, cell[1] + dx), []):
                    if product not in position["mode"]:
                        continue
                    d = meters(lat, lon, position["lat"], position["lon"])
                    if d <= MEMBER_SNAP_METERS and (best is None or d < best[0]):
                        best = (d, position)
        return best[1] if best else None

    types = {"n": "node", "w": "way", "r": "relation"}
    used = 0
    hits = 0
    for route in data.get("busRoutes", []):
        line = route_line(route)
        if not line:
            continue
        product = "t" if route.get("route") == "tram" else "b"
        per_area = defaultdict(lambda: {"stop": [], "platform": []})
        for member in route.get("m", []):
            kind, ref, role = member[0], member[1], member[2]
            found = [p for p in index.get("%s/%d" % (types.get(kind, kind), ref), ()) if product in p["mode"]]
            if not found and len(member) >= 5:
                found = [p for p in [nearest(member[3], member[4], product)] if p is not None]
            for position in found:
                per_area[position["area"]]["stop" if role.startswith("stop") else "platform"].append(position)
        if per_area:
            used += 1
        # Stop and platform members both count: a platform is a position of its own now (one
        # stop position with the line of both directions and a bare platform beside it was
        # the "empty dot" of Wolliner Strasse).
        for found in per_area.values():
            for position in {id(p): p for p in found["stop"] + found["platform"]}.values():
                position["lines"][product].add(line)
                hits += 1
    tagged = 0
    for position in positions:
        if position["lines"]["b"] or position["lines"]["t"]:
            continue
        mode = "".join(m for m in "bt" if m in position["mode"])
        for src in position["srcs"]:
            for ref in (src["tags"].get("route_ref") or "").split(";"):
                ref = ref.strip()
                if ref:
                    position["lines"][guess_product(ref, mode)].add(ref)
        if position["lines"]["b"] or position["lines"]["t"]:
            tagged += 1
    return used, hits, tagged


def build_stop_areas(data):
    """The bus and tram stop areas of Berlin, each with all its positions. Positions of no
    stop area are grouped by name and distance. Returns the list for the asset."""
    areas = {area["id"]: area for area in data["stopAreas"]}
    pairs = route_pairs(data)
    members = defaultdict(list)
    loose = []
    for position in data["stopPositions"]:
        if position.get("stopAreas"):
            for area_id in position["stopAreas"]:
                if area_id in areas:
                    members[area_id].append(position)
        else:
            loose.append(position)
    # A member far from the middle of its stop area is mapped into the wrong one (a "stop
    # area" of a whole town, as one in Zweibruecken): it counts as a loose position, which
    # joins the stop of its own name, instead of being left out as a stray.
    far = {}
    kept_ids = set()
    for area_id, items in list(members.items()):
        lats = sorted(i["lat"] for i in items)
        lons = sorted(i["lon"] for i in items)
        mid = (lats[len(lats) // 2], lons[len(lons) // 2])
        near = [i for i in items if meters(i["lat"], i["lon"], mid[0], mid[1]) <= STRAY_METERS]
        kept_ids.update(id(i) for i in near)
        if len(near) == len(items):
            continue
        for item in items:
            if meters(item["lat"], item["lon"], mid[0], mid[1]) > STRAY_METERS:
                far[id(item)] = item
        if near:
            members[area_id] = near
        else:
            del members[area_id]
    # Only those no other of their stop areas keeps.
    unhomed = 0
    for key, item in far.items():
        if key not in kept_ids:
            loose.append(item)
            unhomed += 1
    groups = []
    for area_id, items in members.items():
        tags = areas[area_id]["tags"]
        ids = vbb_ids(tags.get("ref:VBB"), tags.get("ref:IFOPT"), tags.get("website"))
        groups.append({"area": area_id, "items": list(items), "ids": ids, "area_name": tags.get("name"),
                       "dhid": dhids(tags.get("ref:IFOPT"))})
    # Positions without an area: to an area of their name nearby, else into groups of their own.
    def centre(group):
        items = group["items"]
        return (sum(i["lat"] for i in items) / len(items), sum(i["lon"] for i in items) / len(items))

    # Groups by name key and by a cell of about 1 km around their first centre (a joined
    # position lies within LOOSE_JOIN_METERS of a centre, so centres move little).
    by_key = defaultdict(lambda: defaultdict(list))
    order = {}
    for n, group in enumerate(groups):
        order[id(group)] = n
        c = centre(group)
        cell = (int(c[0] // 0.01), int(c[1] // 0.01))
        for key in {name_key(i.get("name")) for i in group["items"]} | {name_key(group["area_name"])}:
            if key:
                by_key[key][cell].append(group)

    def candidates(key, lat, lon):
        cells = by_key.get(key)
        if not cells:
            return []
        cy, cx = int(lat // 0.01), int(lon // 0.01)
        found = [g for dy in (-1, 0, 1) for dx in (-1, 0, 1) for g in cells.get((cy + dy, cx + dx), ())]
        found.sort(key=lambda g: order[id(g)])
        return found

    joined = 0
    rest = []
    unnamed = []
    for position in loose:
        key = name_key(position.get("name"))
        if not key:
            unnamed.append(position)
            continue
        best = None
        for group in candidates(key, position["lat"], position["lon"]):
            c = centre(group)
            d = meters(position["lat"], position["lon"], c[0], c[1])
            if d <= LOOSE_JOIN_METERS and (best is None or d < best[0]):
                best = (d, group)
        if best:
            best[1]["items"].append(position)
            joined += 1
        else:
            rest.append(position)
    own = []
    own_by_key = defaultdict(list)
    for position in rest:
        key = name_key(position.get("name"))
        home = None
        for group in own_by_key[key]:
            if any(meters(position["lat"], position["lon"], i["lat"], i["lon"]) <= LOOSE_JOIN_METERS
                   for i in group["items"]):
                home = group
                break
        if home is None:
            group = {"area": None, "items": [position], "ids": [], "area_name": None, "key": key, "dhid": []}
            own.append(group)
            own_by_key[key].append(group)
        else:
            home["items"].append(position)
    groups += own
    unnamed_joined = join_unnamed(groups, unnamed)
    # VBB numbers of the positions themselves, and of areas of the same name nearby.
    for group in groups:
        for item in group["items"]:
            for vid in vbb_ids(item["tags"].get("ref:IFOPT"), item["tags"].get("ref:VBB"), item["tags"].get("website")):
                if vid not in group["ids"]:
                    group["ids"].append(vid)
            for d in dhids(item["tags"].get("ref:IFOPT")):
                if d not in group["dhid"]:
                    group["dhid"].append(d)
        names = Counter(i.get("name") for i in group["items"] if i.get("name"))
        group["name"] = names.most_common(1)[0][0] if names else (group["area_name"] or "")
        group["key"] = name_key(group["name"])
        group["centre"] = centre(group)
    with_ids = defaultdict(lambda: defaultdict(list))
    for group in groups:
        if group["ids"]:
            with_ids[group["key"]][(int(group["centre"][0] // 0.01), int(group["centre"][1] // 0.01))].append(group)
    borrowed = 0
    for group in groups:
        if group["ids"] or not group["key"]:
            continue
        cells = with_ids.get(group["key"], {})
        cy, cx = int(group["centre"][0] // 0.01), int(group["centre"][1] // 0.01)
        near = [g for dy in (-1, 0, 1) for dx in (-1, 0, 1) for g in cells.get((cy + dy, cx + dx), ())
                if meters(group["centre"][0], group["centre"][1], g["centre"][0], g["centre"][1]) <= ID_BORROW_METERS]
        if near:
            best = min(near, key=lambda g: meters(group["centre"][0], group["centre"][1], g["centre"][0], g["centre"][1]))
            group["ids"] = list(best["ids"])
            borrowed += 1
    out = []
    positions = 0
    stray = 0
    pending = []
    all_positions = []
    for group in groups:
        # A member far from the others is a mapping error (an area of the same name elsewhere).
        items = group["items"]
        lats = sorted(i["lat"] for i in items)
        lons = sorted(i["lon"] for i in items)
        mid = (lats[len(lats) // 2], lons[len(lons) // 2])
        near = [i for i in items if meters(i["lat"], i["lon"], mid[0], mid[1]) <= STRAY_METERS]
        stray += len(items) - len(near)
        kept = area_positions(near, pairs)
        if not kept or not group["name"]:
            continue
        kept.sort(key=lambda k: (k["ref"] or "~", k["lat"], k["lon"]))
        for item in kept:
            item["area"] = len(pending)
        all_positions += kept
        pending.append((group, kept))
    used, hits, tagged = assign_lines(data, all_positions)
    with_lines = 0
    folded = 0
    for group, kept in pending:
        entries = []
        # A position with neither number nor lines beside one of its mode that has lines is the
        # same place mapped twice (a highway=bus_stop node a few metres from the platform the
        # routes list): it would only be an empty dot on the map.
        def bare(item):
            return not item["ref"] and not item["lines"]["b"] and not item["lines"]["t"]
        # So is a bare stop position (no platform within reach, no route) where the stop has
        # another position of its mode: the point on the road tells a rider nothing.
        labelled = [k for k in kept if not bare(k)]
        twins = [k for k in kept if bare(k) and (any(
            k["mode"] & o["mode"] and meters(k["lat"], k["lon"], o["lat"], o["lon"]) <= STOP_PLATFORM_METERS
            for o in labelled) or (all(src["kind"] == "stop_position" for src in k["srcs"]) and any(
                o is not k and k["mode"] & o["mode"] and not all(src["kind"] == "stop_position" for src in o["srcs"])
                for o in kept)))]
        if twins and len(twins) < len(kept):
            folded += len(twins)
            kept[:] = [k for k in kept if not any(k is t for t in twins)]
        for item in kept:
            mode = "".join(m for m in "bt" if m in item["mode"])
            entry = [r6(item["lat"]), r6(item["lon"]), mode]
            lines = {}
            for product in ("b", "t"):
                if item["lines"][product]:
                    lines[product] = sorted(item["lines"][product], key=line_sort_key)
            if item["ref"] or lines:
                entry.append(item["ref"] or "")
            if lines:
                entry.append(lines)
                with_lines += 1
            entries.append(entry)
        record = {"n": group["name"]}
        ids = group["ids"] + [d for d in group["dhid"] if d not in group["ids"]]
        if ids:
            record["ids"] = ids
        record["p"] = entries
        out.append(record)
        positions += len(entries)
    log("  lines at stop positions: %d bus/tram routes used (%d in the extract), %d route stops placed, "
        "%d of %d positions with lines (%d of them from route_ref tags only), %d bare positions beside a "
        "position with lines folded into it"
        % (used, len(data.get("busRoutes", [])), hits, with_lines, len(all_positions), tagged, folded))
    out.sort(key=lambda r: (r["n"], r["p"][0][0], r["p"][0][1]))
    log("  bus/tram stop areas: %d (%d from OSM stop areas, %d of loose positions), %d positions, "
        "%d loose positions joined to an area, %d areas with ids (VBB number or DHID; %d VBB numbers borrowed by name), "
        "%d stray members left out, %d of %d unnamed loose positions joined to the nearest stop, "
        "%d members far from their stop area taken as loose positions"
        % (len(out), len(members), len(own), positions, joined, sum(1 for r in out if "ids" in r), borrowed, stray,
           unnamed_joined, len(unnamed), unhomed))
    return out


# A loose position without a name (a highway=bus_stop or platform with no name tag, often the
# other direction of a village stop) is a position of the stop whose nearest position of the
# same mode lies this close; farther away it cannot be told whose it is and is left out.
UNNAMED_JOIN_METERS = 80.0


def join_unnamed(groups, unnamed):
    """Adds each position of [unnamed] to the group (of [groups]) holding the nearest position
    of its mode within UNNAMED_JOIN_METERS. Returns how many were joined."""
    if not unnamed:
        return 0
    cell = 0.002  # degrees, about 220 m north-south: the nine cells around reach past the radius
    grid = defaultdict(list)
    for group in groups:
        for item in group["items"]:
            grid[(int(item["lat"] // cell), int(item["lon"] // cell))].append((item, group))
    joined = 0
    for position in unnamed:
        cy, cx = int(position["lat"] // cell), int(position["lon"] // cell)
        best = None
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                for item, group in grid.get((cy + dy, cx + dx), ()):
                    if not ((item["bus"] and position["bus"]) or (item["tram"] and position["tram"])):
                        continue
                    d = meters(position["lat"], position["lon"], item["lat"], item["lon"])
                    if d <= UNNAMED_JOIN_METERS and (best is None or d < best[0]):
                        best = (d, group)
        if best:
            best[1]["items"].append(position)
            joined += 1
    return joined




# ---------------------------------------------------------------------------------------
# Stands named in the name ("Tattersall Steig I") and the colours of the lines

# The word for a stand or pole of a stop and its letter or number at the end of a name:
# "Kunsthalle Steig F", "Hbf, Bstg. 3", "Marktplatz (Bussteig B)". The keyword is matched in
# any case, the letter only in capitals (so "Alte Post" is no "Alte" with stand "t").
STAND_RE = re.compile(
    r"^(?P<base>.*?\S)[\s,/\-–]+\(?(?i:steig|bussteig|bstg\.?|bahnsteig|mast|pos\.?|position)\s*"
    r"(?P<ref>[A-Z]{1,2}\d{0,2}|\d{1,3}[a-z]?)\)?$")


def split_stand(name):
    """(base name, stand) of a stop name that ends with its stand ("Tattersall Steig I" ->
    ("Tattersall", "I")); (name, None) for any other name."""
    match = STAND_RE.match((name or "").strip())
    if not match:
        return (name, None)
    base = match.group("base").strip(" ,-/(")
    if not name_key(base):
        return (name, None)
    return (base, match.group("ref"))


def normalise_stand_names(data):
    """Bus and tram stops whose name ends with their stand ("Tattersall Steig I", common in
    Baden-Wuerttemberg and the Rhine-Neckar area) lose it from the name, so they join the stop
    area of that name, and keep it as their number where they have none. Returns the count."""
    changed = 0
    for position in data["stopPositions"]:
        base, ref = split_stand(position.get("name"))
        if ref is None:
            continue
        position["name"] = base
        if not short_ref(position):
            position["local_ref"] = ref
        changed += 1
    return changed


# Colours of one line (mode, ref, colour) this close to each other are one entry.
COLOUR_MERGE_METERS = 30000.0
CSS_COLOURS = {
    "red": "ff0000", "blue": "0000ff", "green": "008000", "yellow": "ffff00", "black": "000000",
    "white": "ffffff", "orange": "ffa500", "purple": "800080", "brown": "a52a2a", "grey": "808080",
    "gray": "808080", "pink": "ffc0cb", "darkblue": "00008b", "darkgreen": "006400", "lime": "00ff00",
    "magenta": "ff00ff", "cyan": "00ffff", "navy": "000080", "maroon": "800000", "olive": "808000",
    "teal": "008080", "violet": "ee82ee", "turquoise": "40e0d0", "gold": "ffd700",
}


def norm_colour(value):
    """A colour tag as six lower-case hex digits ("#E3000B", "#e00", "red"), else None."""
    text = (value or "").strip().lower()
    if text in CSS_COLOURS:
        return CSS_COLOURS[text]
    text = text.lstrip("#")
    if re.fullmatch(r"[0-9a-f]{3}", text):
        return "".join(c * 2 for c in text)
    if re.fullmatch(r"[0-9a-f]{6}", text):
        return text
    return None


def colour_mode(route_tags):
    """The mode letter of the colour table of a route: "b" bus, "t" tram, "u" U-Bahn or
    Stadtbahn, "s" S-Bahn; None for other trains."""
    route = route_tags.get("route")
    if route in ("bus", "trolleybus"):
        return "b"
    if route == "tram":
        return "t"
    kind = route_kind(route_tags)
    if kind in ("U", "L"):
        return "u"
    if kind == "S":
        return "s"
    return None


def line_colours(data):
    """The colours of the lines from the colour tags of the route relations: a list of
    [mode, line, "rrggbb", lat, lon], the place being the middle of the route's stops (one
    entry per line, colour and area of COLOUR_MERGE_METERS). The app uses them for lines the
    provider gives no colour of."""
    where = {}
    for position in data["stopPositions"]:
        where[(position["type"][0], position["id"])] = (position["lat"], position["lon"])
    for station in data["stations"]:
        for position in station.get("stop_positions", []) + station.get("platforms", []):
            if "lat" in position and "lon" in position:
                where[(position["type"][0], position["id"])] = (position["lat"], position["lon"])
    clusters = defaultdict(list)

    def add(mode, line, colour, points):
        if not points:
            return
        lat = sum(p[0] for p in points) / len(points)
        lon = sum(p[1] for p in points) / len(points)
        for cluster in clusters[(mode, line, colour)]:
            if meters(lat, lon, cluster[0] / cluster[2], cluster[1] / cluster[2]) <= COLOUR_MERGE_METERS:
                cluster[0] += lat
                cluster[1] += lon
                cluster[2] += 1
                return
        clusters[(mode, line, colour)].append([lat, lon, 1])

    for route in data.get("busRoutes", []):
        colour = norm_colour(route.get("colour"))
        line = route_line(route)
        mode = colour_mode(route)
        if not colour or not line or not mode:
            continue
        add(mode, line, colour, [(m[3], m[4]) for m in route.get("m", []) if len(m) >= 5])
    for route in data.get("routes", []):
        tags = route.get("tags", {})
        colour = norm_colour(tags.get("colour"))
        line = (tags.get("ref") or "").split(";")[0].strip()
        mode = colour_mode(tags)
        if not colour or not line or not mode:
            continue
        points = [where[(m["type"][0], m["ref"])] for m in route.get("members", []) if (m["type"][0], m["ref"]) in where]
        add(mode, line, colour, points)
    out = []
    for (mode, line, colour), found in clusters.items():
        for lat, lon, n in found:
            out.append([mode, line, colour, round(lat / n, 3), round(lon / n, 3)])
    out.sort(key=lambda e: (e[0], line_sort_key(e[1]), e[3], e[4]))
    return out


def cut_colours(colours, part):
    """The entries of [colours] near what [part] (a cut asset) holds: within 10 km of the box
    of its stations, stops and stop areas."""
    points = [item_point(section, item) for section in ("stations", "stops", "stopAreas") for item in part[section]]
    if not points:
        return []
    box = rg.expand((min(p[0] for p in points), min(p[1] for p in points),
                     max(p[0] for p in points), max(p[1] for p in points)), 10000)
    return [e for e in colours if rg.in_box(box, e[3], e[4])]


# ---------------------------------------------------------------------------------------
# Regions: what goes into the asset of a region, and the per-state packs

def source_box(data):
    box = (data.get("source") or {}).get("bbox")
    return tuple(box) if box and len(box) == 4 else None


def region_spec(args):
    """(name, box, state code or None) of the region of the build."""
    if args.bbox:
        return (args.name or "bbox", args.bbox, None)
    name = args.region
    if name in rg.BOX_REGIONS:
        return (name, rg.BOX_REGIONS[name], None)
    code, box = rg.STATES[name]
    return (name, box, code)


def prefilter(data, box):
    """Leaves out of [data] the stations, stop positions and entrances well outside [box]
    (2 km), so a region of a big extract builds fast; the exact cut comes after the build."""
    wide = rg.expand(box, 2000)
    before = (len(data["stations"]), len(data["stopPositions"]), len(data["entrances"]))
    data["stations"] = [s for s in data["stations"] if rg.in_box(wide, s["lat"], s["lon"])]
    data["stopPositions"] = [s for s in data["stopPositions"] if rg.in_box(wide, s["lat"], s["lon"])]
    data["entrances"] = [e for e in data["entrances"] if rg.in_box(wide, e["lat"], e["lon"])]
    log("  region box: stations %d -> %d, stop positions %d -> %d, entrances %d -> %d"
        % (before[0], len(data["stations"]), before[1], len(data["stopPositions"]), before[2], len(data["entrances"])))


def item_point(section, item):
    if section == "stopAreas":
        return (item["p"][0][0], item["p"][0][1])
    if section == "changes":
        return (item["_lat"], item["_lon"])
    return (item["lat"], item["lon"])


def item_state_code(section, item):
    if section == "changes":
        ids = [item["fromStation"]["id"]]
    else:
        ids = item.get("ids") or []
    for value in ids:
        code = rg.dhid_state(value)
        if code:
            return code
    # Berlin and Brandenburg keep VBB numbers instead of DHIDs: 900000001 to 900199999 lie in
    # Berlin, the higher ones in Brandenburg (Potsdam 9002..., Cottbus 9004...). Without this
    # every place of Berlin went to the nearest thing with a DHID, often in Brandenburg.
    for value in ids:
        if len(value) == 9 and value.startswith("900") and value.isdigit():
            return "11" if int(value[3:]) < 200000 else "12"
    return None


def state_locator(asset):
    locator = rg.StateLocator()
    for section in ("stations", "stops", "stopAreas"):
        for item in asset[section]:
            code = item_state_code(section, item)
            if code:
                lat, lon = item_point(section, item)
                locator.add(lat, lon, code)
    return locator


def assign_states(asset, records, locator):
    """The state code of every item: {section: [code, ...]} in the order of the items."""
    out = {}
    for section in ("stations", "stops", "stopAreas"):
        out[section] = [locator.locate(*item_point(section, item), code=item_state_code(section, item))
                        for item in asset[section]]
    out["changes"] = [locator.locate(*item_point("changes", r), code=item_state_code("changes", r)) for r in records]
    return out


# A place without a DHID of its own goes to the pack of a state when that state's nearest DHID
# is at most this much farther than the nearest DHID of any state (see StateLocator.near_state).
STATE_BAND_METERS = 2000.0


def state_members(asset, records, locator, state_code, band=STATE_BAND_METERS):
    """{section: [bool, ...]}: which items the pack of [state_code] keeps: those of its DHIDs
    (VBB numbers), and those of none near enough to its own (see StateLocator.near_state)."""
    out = {}
    for section in ("stations", "stops", "stopAreas"):
        out[section] = [locator.near_state(*item_point(section, item), state=state_code, band_meters=band,
                                           code=item_state_code(section, item))
                        for item in asset[section]]
    out["changes"] = [locator.near_state(*item_point("changes", r), state=state_code, band_meters=band,
                                         code=item_state_code("changes", r)) for r in records]
    return out


def cut(asset, records, keep):
    """The asset and change records reduced to the items for which keep(section, index, item)."""
    part = {k: v for k, v in asset.items() if k not in ("stations", "stops", "stopAreas", "lineColors")}
    for section in ("stations", "stops", "stopAreas"):
        part[section] = [item for i, item in enumerate(asset[section]) if keep(section, i, item)]
    if "lineColors" in asset:
        part["lineColors"] = cut_colours(asset["lineColors"], part)
    kept = [r for i, r in enumerate(records) if keep("changes", i, r)]
    return part, kept


def gzip_size(raw):
    return len(gzip.compress(raw, 9))


def asset_bytes(asset):
    return json.dumps(asset, ensure_ascii=False, separators=(",", ":")).encode("utf-8")


def change_bytes(records):
    lines = []
    for record in records:
        clean = {k: v for k, v in record.items() if not k.startswith("_")}
        lines.append(json.dumps(clean, ensure_ascii=False, separators=(",", ":")) + "\n")
    return "".join(lines).encode("utf-8")


def write_bytes(path, raw):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    partial = path + ".part"
    with open(partial, "wb") as handle:
        handle.write(raw)
    os.replace(partial, path)


def asset_names(name, out_dir):
    """The station asset and the change positions of region [name] in [out_dir]; for berlin
    the names the app loads (osm-berlin.json, osm-change-positions.ndjson)."""
    if name == "berlin":
        return os.path.join(out_dir, "osm-berlin.json"), os.path.join(out_dir, "osm-change-positions.ndjson")
    return os.path.join(out_dir, "osm-%s.json" % name), os.path.join(out_dir, "osm-change-positions-%s.ndjson" % name)


def write_packs(asset, records, packs_dir):
    """One asset (and change positions) per German state, by DHID, else the nearest DHID,
    else the state boxes. Prints the sizes, plain and gzip, against the whole."""
    locator = state_locator(asset)
    codes = assign_states(asset, records, locator)
    whole = asset_bytes(asset)
    log("")
    log("Packs per state in %s:" % os.path.relpath(packs_dir, ROOT))
    log("  %-24s %8s %8s %8s %12s %10s %10s" % ("state", "stations", "stops", "areas", "bytes", "gzip", "changes"))
    total = total_gz = 0
    for slug in sorted(rg.STATES, key=lambda s: rg.STATES[s][0]):
        code = rg.STATES[slug][0]
        part, kept = cut(asset, records, lambda section, i, item: codes[section][i] == code)
        raw = asset_bytes(part)
        changes = change_bytes([r for r in kept if r["confidence"] >= MIN_CONFIDENCE])
        station_path, change_path = asset_names(slug, packs_dir)
        write_bytes(station_path, raw)
        write_bytes(change_path, changes)
        gz = gzip_size(raw)
        total += len(raw)
        total_gz += gz
        log("  %-24s %8d %8d %8d %12d %10d %10d" % (slug, len(part["stations"]), len(part["stops"]),
                                                    len(part["stopAreas"]), len(raw), gz, len(changes)))
    lost = {section: sum(1 for c in codes[section] if c is None) for section in codes}
    log("  %-24s %8s %8s %8s %12d %10d" % ("all packs", "", "", "", total, total_gz))
    log("  %-24s %8d %8d %8d %12d %10d" % ("one file", len(asset["stations"]), len(asset["stops"]),
                                           len(asset["stopAreas"]), len(whole), gzip_size(whole)))
    if any(lost.values()):
        log("  outside every state (left out of the packs): %s" % lost)


def parse_args(argv):
    parser = argparse.ArgumentParser(
        description="Build the OpenStreetMap assets of the app from tools/osm-stations.json.")
    parser.add_argument("--source", default=SOURCE, help="the extract (default: tools/osm-stations.json)")
    where = parser.add_mutually_exclusive_group()
    where.add_argument("--region", choices=sorted(rg.REGIONS), default="berlin",
                       help="berlin (default; the Overpass box, written as the app's osm-berlin.json), "
                            "munich, germany, or a German state (by DHID, else nearest DHID, else box)")
    where.add_argument("--bbox", type=parse_bbox, help="south,west,north,east in degrees")
    parser.add_argument("--name", help="file name part for --bbox (osm-NAME.json)")
    parser.add_argument("--out-dir", help="where the assets go (default: the app's assets for berlin, "
                                          "else tools/osm-packs)")
    parser.add_argument("--split-regions", action="store_true",
                        help="also write one asset per German state into --packs-dir and print the sizes")
    parser.add_argument("--packs-dir", default=os.path.join(PACKS, "states"),
                        help="where --split-regions writes (default: tools/osm-packs/states)")
    return parser.parse_args(argv)


def parse_bbox(text):
    parts = [float(p) for p in text.replace(" ", "").split(",")]
    if len(parts) != 4 or parts[0] >= parts[2] or parts[1] >= parts[3]:
        raise argparse.ArgumentTypeError("--bbox wants south,west,north,east")
    return tuple(parts)


def main(argv=None):
    args = parse_args(argv)
    name, box, state_code = region_spec(args)
    out_dir = os.path.abspath(args.out_dir) if args.out_dir else (ASSETS if name == "berlin" else PACKS)
    station_out, change_out = asset_names(name, out_dir)
    source = os.path.abspath(args.source)
    with open(source, "r", encoding="utf-8") as handle:
        data = json.load(handle)
    stations = data["stations"]
    log("Source %s (generated %s, region %s)" % (os.path.relpath(source, ROOT), data.get("generated"),
                                                 (data.get("source") or {}).get("region", "?")))
    have = source_box(data)
    # A region that holds the whole extract needs no cut (the default build of Berlin).
    whole_extract = have is not None and state_code is None and rg.box_contains(box, have)
    log("Region %s: %s%s" % (name, ",".join("%.2f" % v for v in box),
                             " (holds the whole extract)" if whole_extract else
                             (" (state %s)" % state_code if state_code else "")))
    if not whole_extract:
        prefilter(data, box)
        stations = data["stations"]
    if name != "berlin":
        # Berlin's asset keeps its names as they are (its stops carry "Pos." numbers of their own).
        log("  %d bus/tram stop names with their stand at the end (\"Steig F\") split"
            % normalise_stand_names(data))
    totals = Counter()
    for s in stations:
        for kind in ("platforms", "stop_positions", "steps", "elevators", "escalators", "footways"):
            totals[kind] += len(s.get(kind, []))
    log("  stations %d (%s), stop areas %d, routes %d, entrances %d, bus/tram stops %d"
        % (len(stations), mode_census(stations), len(data["stopAreas"]),
           len(data["routes"]), len(data["entrances"]), len(data["stopPositions"])))
    log("  per station (with repeats where stations are close): %s" % dict(totals))

    complexes, areas_of = build_complexes(data)
    log("  %d complexes, %d with more than one station, %d stations without VBB id, %d without DHID"
        % (len(complexes), sum(len(m) > 1 for m in complexes), sum(not s["vbb"] for s in stations),
           sum(not s["dhid"] for s in stations)))
    log("  station kinds: %s" % dict(Counter(s["kinds"] for s in stations).most_common()))
    log("  route kinds: %s" % dict(Counter(route_kind(r["tags"]) for r in data["routes"])))
    moved = Counter("%s->%s" % (products_of(s), s["products"]) for s in stations if products_of(s) != s["products"])
    log("  products the routes changed against the station modes alone: %s" % (dict(moved) or "none"))

    station_by_key = {station_key(s): s for s in stations}
    routes, elements = route_visits(data, station_by_key)
    lines_of_platform = defaultdict(set)
    for route in routes:
        for visit in route["visits"]:
            if visit and visit["platform"]:
                lines_of_platform[visit["platform"]].add(route["line"])
    log("  %d U/S route relations, %d lines" % (len(routes), len({r["line"] for r in routes})))

    asset = build_asset(data, complexes, areas_of, lines_of_platform)
    if name != "berlin":
        # Berlin's colours are the app's own; elsewhere those of the route relations.
        asset["lineColors"] = line_colours(data)
        log("  %d line colours" % len(asset["lineColors"]))
    records = change_positions(data, complexes)
    log("  %d OSM change records (%s)" % (len(records), dict(Counter(r["_method"] for r in records))))

    if not whole_extract:
        if state_code:
            locator = state_locator(asset)
            members = state_members(asset, records, locator, state_code)
            exact = assign_states(asset, records, locator)
            log("  state %s: %d stop areas by DHID or nearest DHID, %d more near the border"
                % (state_code, sum(1 for c in exact["stopAreas"] if c == state_code),
                   sum(1 for c, m in zip(exact["stopAreas"], members["stopAreas"]) if m and c != state_code)))
            asset, records = cut(asset, records, lambda section, i, item: members[section][i])
        else:
            asset, records = cut(asset, records, lambda section, i, item: rg.in_box(box, *item_point(section, item)))

    raw = asset_bytes(asset)
    write_bytes(station_out, raw)
    log("")
    log("Written %s: %d bytes (gzip %d), %d complexes, %d platforms, %d entrances, %d lifts, %d stop positions"
        % (os.path.relpath(station_out, ROOT), len(raw), gzip_size(raw), len(asset["stations"]),
           sum(len(s["platforms"]) for s in asset["stations"]), sum(len(s["entrances"]) for s in asset["stations"]),
           sum(len(s["lifts"]) for s in asset["stations"]), len(asset["stops"])))
    log("  %d bus/tram stop areas with %d positions (%d with a number)"
        % (len(asset["stopAreas"]), sum(len(a["p"]) for a in asset["stopAreas"]),
           sum(1 for a in asset["stopAreas"] for p in a["p"] if len(p) > 3 and p[3])))

    if any(r["fromStation"]["id"].startswith("900000") for r in records) and os.path.exists(VBB_ASSET):
        validate(records)
    else:
        log("  no VBB stations: the check against vbb-change-positions is skipped")
    kept = [r for r in records if r["confidence"] >= MIN_CONFIDENCE]
    kept.sort(key=lambda r: (r["fromStation"]["name"], r["fromLines"][0], r["toLines"][0], r["previousStation"]["name"], r["nextStation"]["name"]))
    write_bytes(change_out, change_bytes(kept))
    log("Written %s: %d records (confidence >= %.2f), %d bytes"
        % (os.path.relpath(change_out, ROOT), len(kept), MIN_CONFIDENCE, os.path.getsize(change_out)))

    if args.split_regions:
        write_packs(asset, kept, os.path.abspath(args.packs_dir))
    return 0


if __name__ == "__main__":
    sys.exit(main())
