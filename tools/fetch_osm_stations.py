#!/usr/bin/env python3
"""
Fetch the U-Bahn and S-Bahn stations of Berlin from OpenStreetMap, with what lies around
each of them: platforms with their outlines, stop positions, steps, elevators, escalators
and footways with a level, and the route relations of the lines with their member order.
Besides, for all of Berlin: the entrances of the stations, and the bus and tram stops as
stop positions and platforms (name, ref, local_ref, which holds the "Pos." number in Berlin,
ref:BVG, ref:IFOPT) with the stop areas they belong to, and the bus and tram route
relations with their line (ref, name, colour) and their stop and platform members, so the
build knows which lines depart at which stop position.

Run once from the root of the repository:

    python3 tools/fetch_osm_stations.py

To fetch only the bus and tram routes and merge them into an existing
tools/osm-stations.json (the full run takes long on the public servers):

    python3 tools/fetch_osm_stations.py --only bus-routes

Standard library only. Raw answers of the Overpass API are cached in tools/osm-cache/,
so a second run is fast and asks the servers nothing new, and a run that stopped because
the servers were busy goes on where it stopped. Berlin is asked in tiles and the stations
in small batches, from several public servers in turn. The result is written to
tools/osm-stations.json. The tiles cover a box around Berlin, so a few stations just
outside the city come along.

Data (c) OpenStreetMap contributors, available under the Open Database License (ODbL 1.0).
"""

import hashlib
import json
import os
import shutil
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

# Public Overpass servers, asked in turn: one that is busy (502, 504, 429) hands over to the next.
ENDPOINTS = [
    "https://overpass-api.de/api/interpreter",
    "https://overpass.private.coffee/api/interpreter",
    "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
]
USER_AGENT = "NextStop-tools/1.0"
HERE = os.path.dirname(os.path.abspath(__file__))
CACHE_DIR = os.path.join(HERE, "osm-cache")
OUTPUT = os.path.join(HERE, "osm-stations.json")

# Seconds the server may take per query, and the client waits a little longer. Small queries
# with a short timeout get a slot on a busy server far sooner than one big query.
QUERY_TIMEOUT = 90
HTTP_TIMEOUT = QUERY_TIMEOUT + 30
# Tries per query over all servers, and the pause between them: it doubles from the first
# up to the longest.
RETRIES = 10
FIRST_WAIT_SECONDS = 5
LONGEST_WAIT_SECONDS = 120
# Pause after a good answer, to be kind to the public servers.
PAUSE_SECONDS = 3
# Stations per query for what lies around them, and the radius around each, in metres.
BATCH_SIZE = 5
AROUND_METERS = 150

# Berlin as tiles of a bounding box (south, west, north, east), asked one by one instead of
# one query over the area of the city, which the public servers often give up on.
BERLIN_SOUTH, BERLIN_NORTH = 52.33, 52.68
BERLIN_WEST, BERLIN_EAST = 13.08, 13.77
TILES_PER_SIDE = 3

# Entrances tagged entrance=* near a station are kept only with one of these keys: the doors
# of the houses around the station stay out.
ENTRANCE_KEYS = ("name", "ref", "wheelchair", "level")
# Doors per query when asking which of them lie in a station building.
ENTRANCE_ID_BATCH = 200
# What is kept of a bus or tram stop besides its position, also in the full tags.
STOP_KEYS = ("name", "ref", "local_ref", "ref:BVG", "ref:IFOPT")
# Bus and tram route relations per query, by id. Their members come along, so keep it small.
ROUTE_BATCH_SIZE = 20
# What is kept of a bus or tram route relation.
BUS_ROUTE_KEYS = ("ref", "name", "colour", "network", "operator")


def log(message):
    print(message, flush=True)


def cache_path(query):
    digest = hashlib.sha256(query.encode("utf-8")).hexdigest()[:24]
    return os.path.join(CACHE_DIR, digest + ".json")


def berlin_tiles():
    """The bounding boxes (south, west, north, east) that together cover Berlin."""
    tiles = []
    step_lat = (BERLIN_NORTH - BERLIN_SOUTH) / TILES_PER_SIDE
    step_lon = (BERLIN_EAST - BERLIN_WEST) / TILES_PER_SIDE
    for row in range(TILES_PER_SIDE):
        for column in range(TILES_PER_SIDE):
            south = BERLIN_SOUTH + row * step_lat
            west = BERLIN_WEST + column * step_lon
            tiles.append((round(south, 4), round(west, 4), round(south + step_lat, 4), round(west + step_lon, 4)))
    return tiles


class ServerBusy(Exception):
    """A server that answered with an error; [retry_after] is its wish in seconds, if it said one."""

    def __init__(self, message, retry_after=None):
        super().__init__(message)
        self.retry_after = retry_after


def retry_after_seconds(value):
    """The Retry-After header as seconds: a number, or an HTTP date."""
    if not value:
        return None
    value = value.strip()
    if value.isdigit():
        return int(value)
    try:
        from email.utils import parsedate_to_datetime
        moment = parsedate_to_datetime(value)
        return max(0, int(moment.timestamp() - time.time()))
    except (TypeError, ValueError, IndexError, OverflowError):
        return None


def fetch(endpoint, body):
    """Posts the query and returns the answer. Prefers the system curl: the Python that ships
    with the macOS command line tools links an old TLS library that some servers refuse.
    Raises ServerBusy for an HTTP error, with the Retry-After of the server where it gives one."""
    curl = shutil.which("curl")
    if curl:
        os.makedirs(CACHE_DIR, exist_ok=True)
        header_file = os.path.join(CACHE_DIR, ".headers")
        result = subprocess.run(
            [curl, "-sS", "--compressed", "-m", str(HTTP_TIMEOUT),
             "-A", USER_AGENT, "-H", "Accept: application/json",
             "-D", header_file, "-w", "\n%{http_code}",
             "--data-binary", "@-", endpoint],
            input=body, capture_output=True, timeout=HTTP_TIMEOUT + 10,
        )
        if result.returncode != 0:
            raise ServerBusy("curl %d: %s" % (result.returncode, result.stderr.decode("utf-8", "replace").strip()))
        text = result.stdout.decode("utf-8", "replace")
        text, _, code = text.rpartition("\n")
        status = int(code) if code.strip().isdigit() else 0
        if status != 200:
            retry_after = None
            try:
                with open(header_file, "r", encoding="latin-1") as handle:
                    for line in handle:
                        name, _, value = line.partition(":")
                        if name.strip().lower() == "retry-after":
                            retry_after = retry_after_seconds(value)
            except OSError:
                pass
            raise ServerBusy("HTTP %d" % status, retry_after)
        return text
    request = urllib.request.Request(
        endpoint,
        data=body,
        headers={
            "User-Agent": USER_AGENT,
            "Content-Type": "application/x-www-form-urlencoded",
            "Accept": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=HTTP_TIMEOUT) as response:
            return response.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        raise ServerBusy("HTTP %d" % error.code, retry_after_seconds(error.headers.get("Retry-After")))


def overpass(query, label):
    """Runs one query, from the cache when it was asked before. Otherwise asks the servers in
    turn, waiting longer after each failure (and as long as a server asks for with Retry-After)."""
    path = cache_path(query)
    if os.path.exists(path):
        with open(path, "r", encoding="utf-8") as handle:
            log("  %s: from cache" % label)
            return json.load(handle)
    os.makedirs(CACHE_DIR, exist_ok=True)
    body = urllib.parse.urlencode({"data": query}).encode("utf-8")
    last_error = None
    for attempt in range(RETRIES):
        endpoint = ENDPOINTS[attempt % len(ENDPOINTS)]
        host = urllib.parse.urlparse(endpoint).netloc
        try:
            log("  %s: asking %s (try %d/%d)" % (label, host, attempt + 1, RETRIES))
            raw = fetch(endpoint, body)
            data = json.loads(raw)
            remark = data.get("remark")
            if remark and ("runtime error" in remark or "timed out" in remark or "out of memory" in remark):
                raise ServerBusy("server remark: " + remark)
            # Written whole and then moved, so a stopped run never leaves half an answer behind.
            partial = path + ".part"
            with open(partial, "w", encoding="utf-8") as handle:
                handle.write(raw)
            os.replace(partial, path)
            time.sleep(PAUSE_SECONDS)
            return data
        except (ServerBusy, urllib.error.URLError, TimeoutError, socket.timeout, OSError, ValueError, subprocess.SubprocessError) as error:
            last_error = error
            wait = min(LONGEST_WAIT_SECONDS, FIRST_WAIT_SECONDS * (2 ** attempt))
            asked = getattr(error, "retry_after", None)
            if asked is not None:
                wait = max(wait, min(asked, 10 * LONGEST_WAIT_SECONDS))
            if attempt + 1 < RETRIES:
                log("  %s: %s failed (%s), waiting %d s" % (label, host, error, wait))
                time.sleep(wait)
    raise SystemExit(
        "Overpass did not answer for %s: %s\nRun the script again later: what was loaded is kept in %s."
        % (label, last_error, os.path.relpath(CACHE_DIR))
    )


def stations_query(tile):
    """The stations of the U-Bahn and S-Bahn in one tile, with the stop areas they belong to."""
    box = "(%.4f,%.4f,%.4f,%.4f)" % tile
    return (
        "[out:json][timeout:%d];"
        "("
        'node["railway"="station"]["station"~"^(subway|light_rail)$"]%s;'
        'way["railway"="station"]["station"~"^(subway|light_rail)$"]%s;'
        'relation["railway"="station"]["station"~"^(subway|light_rail)$"]%s;'
        ")->.stations;"
        ".stations out center tags;"
        "rel(bn.stations)[\"public_transport\"=\"stop_area\"]->.areas_n;"
        "rel(bw.stations)[\"public_transport\"=\"stop_area\"]->.areas_w;"
        "(.areas_n;.areas_w;)->.areas;"
        ".areas out body;"
    ) % (QUERY_TIMEOUT, box, box, box)


def routes_query(tile):
    """The route relations of the U-Bahn, S-Bahn and trains with a member in one tile."""
    return (
        "[out:json][timeout:%d];"
        'relation["type"="route"]["route"~"^(subway|light_rail|train)$"](%.4f,%.4f,%.4f,%.4f);'
        "out body;"
    ) % ((QUERY_TIMEOUT,) + tile)


def around_query(batch):
    """Everything of interest within AROUND_METERS of each station of [batch]."""
    centers = []
    for station in batch:
        centers.append("%.7f,%.7f" % (station["lat"], station["lon"]))
    parts = []
    for center in centers:
        around = "(around:%d,%s)" % (AROUND_METERS, center)
        parts.append(
            'way["railway"="platform"]%(a)s;'
            'relation["railway"="platform"]%(a)s;'
            'way["public_transport"="platform"]%(a)s;'
            'relation["public_transport"="platform"]%(a)s;'
            'node["public_transport"="stop_position"]%(a)s;'
            'node["railway"="stop"]%(a)s;'
            'way["highway"="steps"]%(a)s;'
            'node["highway"="elevator"]%(a)s;'
            'way["highway"="elevator"]%(a)s;'
            'way["conveying"]%(a)s;'
            'way["highway"="footway"]["level"]%(a)s;'
            'way["highway"="footway"]["layer"]%(a)s;'
            'way["highway"="footway"]["indoor"]%(a)s;'
            'way["highway"="corridor"]%(a)s;'
            'way["indoor"="corridor"]%(a)s;' % {"a": around}
        )
    return "[out:json][timeout:%d];(%s);out body geom;" % (QUERY_TIMEOUT, "".join(parts))


def entrances_query(tile):
    """The entrances of U-Bahn and S-Bahn stations in one tile."""
    return (
        "[out:json][timeout:%d];"
        'node["railway"~"^(subway_entrance|train_station_entrance)$"](%.4f,%.4f,%.4f,%.4f);'
        "out body;"
    ) % ((QUERY_TIMEOUT,) + tile)


def entrances_around_query(batch):
    """Other entrances (entrance=*) within AROUND_METERS of each station of [batch] that carry
    one of ENTRANCE_KEYS."""
    parts = []
    for station in batch:
        around = "(around:%d,%.7f,%.7f)" % (AROUND_METERS, station["lat"], station["lon"])
        for key in ENTRANCE_KEYS:
            parts.append('node["entrance"]["%s"]%s;' % (key, around))
    return "[out:json][timeout:%d];(%s);out body;" % (QUERY_TIMEOUT, "".join(parts))


def station_buildings_query(ids):
    """The station buildings (building=train_station or transportation, railway=station,
    public_transport=station drawn as a way) that have one of the nodes [ids] in their outline."""
    return (
        "[out:json][timeout:%d];"
        "node(id:%s)->.e;"
        "("
        'way(bn.e)["building"~"^(train_station|transportation)$"];'
        'way(bn.e)["railway"="station"];'
        'way(bn.e)["public_transport"="station"];'
        ");"
        "out body;"
    ) % (QUERY_TIMEOUT, ",".join(str(i) for i in ids))


def stops_query(tile):
    """The bus and tram stops in one tile, as stop positions and platforms, with the stop
    areas they belong to. Platforms drawn as ways come with their centre."""
    box = "(%.4f,%.4f,%.4f,%.4f)" % tile
    selectors = [
        'node["public_transport"="stop_position"]["bus"="yes"]',
        'node["public_transport"="stop_position"]["tram"="yes"]',
        'node["public_transport"="platform"]["bus"="yes"]',
        'way["public_transport"="platform"]["bus"="yes"]',
        'node["public_transport"="platform"]["tram"="yes"]',
        'way["public_transport"="platform"]["tram"="yes"]',
        'node["highway"="bus_stop"]',
        'node["railway"="tram_stop"]',
    ]
    return (
        "[out:json][timeout:%d];" % QUERY_TIMEOUT
        + "(" + "".join(selector + box + ";" for selector in selectors) + ")->.stops;"
        + ".stops out center;"
        + 'rel(bn.stops)["public_transport"="stop_area"]->.areas_n;'
        + 'rel(bw.stops)["public_transport"="stop_area"]->.areas_w;'
        + "(.areas_n;.areas_w;)->.areas;"
        + ".areas out body;"
    )


def bus_routes_query(tile):
    """The ids of the bus, tram and trolleybus route relations with a member in one tile."""
    return (
        "[out:json][timeout:%d];"
        'relation["type"="route"]["route"~"^(bus|tram|trolleybus)$"](%.4f,%.4f,%.4f,%.4f);'
        "out ids;"
    ) % ((QUERY_TIMEOUT,) + tile)


def bus_route_members_query(ids):
    """The route relations [ids] with their members, their member nodes with position and their
    member platform ways with centre (the ways of the road come without anything)."""
    return (
        "[out:json][timeout:%d];"
        "relation(id:%s)->.r;"
        ".r out body;"
        "node(r.r);out skel;"
        '(way(r.r)["public_transport"="platform"];way(r.r)["highway"="platform"];way(r.r)["railway"="platform"];);'
        "out tags center;"
    ) % (QUERY_TIMEOUT, ",".join(str(i) for i in ids))


def stop_role(role):
    """True for the roles of a stop or platform member of a route ("stop", "platform_entry_only", ...)."""
    return role.startswith("stop") or role.startswith("platform")


def fetch_bus_routes(tiles):
    """The bus and tram route relations around Berlin, compact: id, route, the tags of
    BUS_ROUTE_KEYS and the stop and platform members in their order as
    [type "n"/"w"/"r", id, role, lat, lon] (no position for a relation)."""
    log("Bus and tram route relations, in %d tiles" % len(tiles))
    ids = set()
    for number, tile in enumerate(tiles, start=1):
        for element in overpass(bus_routes_query(tile), "bus routes %d/%d" % (number, len(tiles))).get("elements", []):
            if element.get("type") == "relation":
                ids.add(element["id"])
    ids = sorted(ids)
    log("  %d bus and tram route relations, %d per query" % (len(ids), ROUTE_BATCH_SIZE))
    relations = {}
    places = {}
    batches = [ids[i:i + ROUTE_BATCH_SIZE] for i in range(0, len(ids), ROUTE_BATCH_SIZE)]
    for number, batch in enumerate(batches, start=1):
        answer = overpass(bus_route_members_query(batch), "bus route batch %d/%d" % (number, len(batches)))
        for element in answer.get("elements", []):
            if element["type"] == "relation":
                relations[element["id"]] = element
            else:
                position = position_of(element)
                if position is not None:
                    places["%s/%d" % (element["type"], element["id"])] = position
    routes = []
    for element in relations.values():
        tags = element.get("tags", {})
        route = {"id": element["id"], "route": tags.get("route", "")}
        for key in BUS_ROUTE_KEYS:
            if tags.get(key):
                route[key] = tags[key]
        members = []
        for member in element.get("members", []):
            role = member.get("role", "")
            if not stop_role(role):
                continue
            item = [member["type"][0], member["ref"], role]
            place = places.get("%s/%d" % (member["type"], member["ref"]))
            if place is not None:
                item += [place[0], place[1]]
            members.append(item)
        route["m"] = members
        routes.append(route)
    routes.sort(key=lambda r: r["id"])
    log("  %d bus and tram routes with %d stop and platform members"
        % (len(routes), sum(len(r["m"]) for r in routes)))
    return routes


def merge_bus_routes():
    """Fetches only the bus and tram routes and writes them into the existing OUTPUT."""
    if not os.path.exists(OUTPUT):
        raise SystemExit("%s does not exist yet: run the full fetch first." % os.path.relpath(OUTPUT))
    with open(OUTPUT, "r", encoding="utf-8") as handle:
        result = json.load(handle)
    result["busRoutes"] = fetch_bus_routes(berlin_tiles())
    result["busRoutesGenerated"] = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    partial = OUTPUT + ".part"
    with open(partial, "w", encoding="utf-8") as handle:
        json.dump(result, handle, ensure_ascii=False, separators=(",", ":"))
    os.replace(partial, OUTPUT)
    log("")
    log("Merged %d bus and tram routes into %s" % (len(result["busRoutes"]), os.path.relpath(OUTPUT)))
    return 0


def stop_area_record(element):
    return {
        "id": element["id"],
        "tags": element.get("tags", {}),
        "members": [
            {"type": m["type"], "ref": m["ref"], "role": m.get("role", "")}
            for m in element.get("members", [])
        ],
    }


def position_of(element):
    """Latitude and longitude of a node, or the centre of a way; None where there is neither."""
    center = element.get("center") or {}
    lat = element.get("lat", center.get("lat"))
    lon = element.get("lon", center.get("lon"))
    if lat is None or lon is None:
        return None
    return round(lat, 7), round(lon, 7)


def compact_geometry(element):
    geometry = element.get("geometry")
    if geometry:
        return [[round(point["lat"], 7), round(point["lon"], 7)] for point in geometry if point]
    if "members" in element:
        outlines = []
        for member in element["members"]:
            if member.get("geometry"):
                outlines.append({
                    "role": member.get("role", ""),
                    "points": [[round(p["lat"], 7), round(p["lon"], 7)] for p in member["geometry"] if p],
                })
        return outlines
    return None


def compact(element):
    item = {"type": element["type"], "id": element["id"], "tags": element.get("tags", {})}
    if element["type"] == "node":
        item["lat"] = round(element["lat"], 7)
        item["lon"] = round(element["lon"], 7)
    else:
        geometry = compact_geometry(element)
        if geometry:
            item["geometry"] = geometry
    return item


def kind_of(element):
    tags = element.get("tags", {})
    if tags.get("railway") == "platform" or tags.get("public_transport") == "platform":
        return "platforms"
    if tags.get("public_transport") == "stop_position" or tags.get("railway") == "stop":
        return "stop_positions"
    if tags.get("highway") == "steps":
        return "steps"
    if tags.get("highway") == "elevator":
        return "elevators"
    if "conveying" in tags:
        return "escalators"
    if tags.get("highway") in ("footway", "corridor") or tags.get("indoor") == "corridor":
        # Footways (with a level, layer or indoor tag) and indoor corridors: the passages of
        # stations and of the malls and hotels connected to them.
        return "footways"
    return None


def distance_meters(lat1, lon1, lat2, lon2):
    from math import cos, radians, sqrt
    x = radians(lon2 - lon1) * cos(radians((lat1 + lat2) / 2))
    y = radians(lat2 - lat1)
    return sqrt(x * x + y * y) * 6371000


def near(element, station):
    """True when any point of [element] lies within AROUND_METERS of [station]."""
    points = []
    if element["type"] == "node":
        points.append((element["lat"], element["lon"]))
    for point in element.get("geometry") or []:
        if point:
            points.append((point["lat"], point["lon"]))
    for member in element.get("members") or []:
        for point in member.get("geometry") or []:
            if point:
                points.append((point["lat"], point["lon"]))
    return any(distance_meters(lat, lon, station["lat"], station["lon"]) <= AROUND_METERS for lat, lon in points)


def main():
    args = sys.argv[1:]
    if args:
        if args == ["--only", "bus-routes"] or args == ["--only=bus-routes"]:
            return merge_bus_routes()
        raise SystemExit("Usage: python3 tools/fetch_osm_stations.py [--only bus-routes]")
    log("Stations of the U-Bahn and S-Bahn in Berlin, in %d tiles" % (TILES_PER_SIDE * TILES_PER_SIDE))
    elements = {}
    tiles = berlin_tiles()
    for number, tile in enumerate(tiles, start=1):
        answer = overpass(stations_query(tile), "stations %d/%d" % (number, len(tiles)))
        for element in answer.get("elements", []):
            # A station or stop area on the border of two tiles comes twice.
            elements["%s/%d" % (element["type"], element["id"])] = element
    stations = []
    areas = []
    for element in elements.values():
        if element["type"] == "relation" and element.get("tags", {}).get("public_transport") == "stop_area":
            areas.append(stop_area_record(element))
            continue
        center = element.get("center") or {}
        lat = element.get("lat", center.get("lat"))
        lon = element.get("lon", center.get("lon"))
        if lat is None or lon is None:
            continue
        stations.append({
            "type": element["type"],
            "id": element["id"],
            "name": element.get("tags", {}).get("name", ""),
            "lat": round(lat, 7),
            "lon": round(lon, 7),
            "tags": element.get("tags", {}),
        })
    stations.sort(key=lambda s: (s["name"], s["id"]))
    areas.sort(key=lambda a: a["id"])
    log("  %d stations, %d stop areas" % (len(stations), len(areas)))

    log("Route relations")
    routes = []
    route_elements = {}
    for number, tile in enumerate(tiles, start=1):
        for element in overpass(routes_query(tile), "routes %d/%d" % (number, len(tiles))).get("elements", []):
            route_elements[element["id"]] = element
    for element in route_elements.values():
        routes.append({
            "id": element["id"],
            "tags": element.get("tags", {}),
            "members": [
                {"type": m["type"], "ref": m["ref"], "role": m.get("role", "")}
                for m in element.get("members", [])
            ],
        })
    routes.sort(key=lambda r: r["id"])
    log("  %d route relations" % len(routes))

    log("Around the stations, %d per query" % BATCH_SIZE)
    around = {}
    batches = [stations[i:i + BATCH_SIZE] for i in range(0, len(stations), BATCH_SIZE)]
    for number, batch in enumerate(batches, start=1):
        label = "batch %d/%d" % (number, len(batches))
        answer = overpass(around_query(batch), label)
        for element in answer.get("elements", []):
            kind = kind_of(element)
            if kind is None:
                continue
            for station in batch:
                if near(element, station):
                    key = "%s/%d" % (station["type"], station["id"])
                    bucket = around.setdefault(key, {})
                    items = bucket.setdefault(kind, {})
                    items["%s/%d" % (element["type"], element["id"])] = compact(element)

    totals = {"platforms": 0, "stop_positions": 0, "steps": 0, "elevators": 0, "escalators": 0, "footways": 0}
    for station in stations:
        key = "%s/%d" % (station["type"], station["id"])
        found = around.get(key, {})
        for kind in totals:
            station[kind] = list(found.get(kind, {}).values())
            totals[kind] += len(station[kind])

    log("Station entrances, in %d tiles and around the stations" % len(tiles))
    entrance_elements = {}
    for number, tile in enumerate(tiles, start=1):
        for element in overpass(entrances_query(tile), "entrances %d/%d" % (number, len(tiles))).get("elements", []):
            entrance_elements[element["id"]] = element
    for number, batch in enumerate(batches, start=1):
        label = "entrances batch %d/%d" % (number, len(batches))
        for element in overpass(entrances_around_query(batch), label).get("elements", []):
            entrance_elements[element["id"]] = element
    entrances = []
    for element in entrance_elements.values():
        if element.get("type") != "node":
            continue
        tags = element.get("tags", {})
        entrance = {"id": element["id"], "lat": round(element["lat"], 7), "lon": round(element["lon"], 7)}
        for key in ENTRANCE_KEYS:
            if key in tags:
                entrance[key] = tags[key]
        # The stations it lies near, as "type/id" like the keys of the stations.
        entrance["stations"] = [
            "%s/%d" % (station["type"], station["id"]) for station in stations
            if distance_meters(element["lat"], element["lon"], station["lat"], station["lon"]) <= AROUND_METERS
        ]
        entrance["tags"] = tags
        entrances.append(entrance)
    entrances.sort(key=lambda e: e["id"])
    # Doors without transit tags: whether they lie in the outline of a station building, so
    # the build can tell the doors of the station from those of the houses around it.
    doors = [e for e in entrances if e["tags"].get("railway") not in
             ("subway_entrance", "train_station_entrance", "light_rail_entrance")]
    in_building = set()
    for start in range(0, len(doors), ENTRANCE_ID_BATCH):
        ids = [e["id"] for e in doors[start:start + ENTRANCE_ID_BATCH]]
        label = "station buildings %d/%d" % (start // ENTRANCE_ID_BATCH + 1,
                                              (len(doors) + ENTRANCE_ID_BATCH - 1) // ENTRANCE_ID_BATCH)
        for element in overpass(station_buildings_query(ids), label).get("elements", []):
            in_building.update(element.get("nodes", []))
    for door in doors:
        door["stationBuilding"] = door["id"] in in_building
    log("  %d entrances, %d other doors (%d of them in a station building)"
        % (len(entrances), len(doors), sum(d["stationBuilding"] for d in doors)))

    log("Bus and tram stops, in %d tiles" % len(tiles))
    stop_elements = {}
    stop_area_elements = {}
    for number, tile in enumerate(tiles, start=1):
        for element in overpass(stops_query(tile), "stops %d/%d" % (number, len(tiles))).get("elements", []):
            if element["type"] == "relation":
                stop_area_elements[element["id"]] = element
            else:
                stop_elements["%s/%d" % (element["type"], element["id"])] = element
    # Which stop areas each stop belongs to.
    member_of = {}
    for area in stop_area_elements.values():
        for member in area.get("members", []):
            member_of.setdefault("%s/%d" % (member["type"], member["ref"]), []).append(area["id"])
    stop_positions = []
    for key, element in stop_elements.items():
        position = position_of(element)
        if position is None:
            continue
        tags = element.get("tags", {})
        kind = "stop_position" if tags.get("public_transport") == "stop_position" else "platform"
        stop = {
            "type": element["type"],
            "id": element["id"],
            "kind": kind,
            "lat": position[0],
            "lon": position[1],
            "bus": tags.get("bus") == "yes" or tags.get("highway") == "bus_stop",
            "tram": tags.get("tram") == "yes" or tags.get("railway") == "tram_stop",
        }
        for name in STOP_KEYS:
            if name in tags:
                stop[name] = tags[name]
        stop["stopAreas"] = sorted(set(member_of.get(key, [])))
        stop["tags"] = tags
        stop_positions.append(stop)
    stop_positions.sort(key=lambda s: (s["type"], s["id"]))
    # The stop areas of the bus and tram stops join those of the stations, each once.
    known_areas = {area["id"] for area in areas}
    for element in stop_area_elements.values():
        if element["id"] not in known_areas:
            areas.append(stop_area_record(element))
            known_areas.add(element["id"])
    areas.sort(key=lambda a: a["id"])
    log("  %d stop positions and platforms, %d stop areas in all" % (len(stop_positions), len(areas)))

    bus_routes = fetch_bus_routes(tiles)

    result = {
        "license": "Data (c) OpenStreetMap contributors, ODbL 1.0, https://www.openstreetmap.org/copyright",
        "generated": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "radiusMeters": AROUND_METERS,
        "stations": stations,
        "stopAreas": areas,
        "routes": routes,
        "entrances": entrances,
        "stopPositions": stop_positions,
        "busRoutes": bus_routes,
    }
    with open(OUTPUT, "w", encoding="utf-8") as handle:
        json.dump(result, handle, ensure_ascii=False, separators=(",", ":"))

    log("")
    log("Written to %s" % os.path.relpath(OUTPUT))
    log("Stations:        %d" % len(stations))
    log("Stop areas:      %d" % len(areas))
    log("Platforms:       %d" % totals["platforms"])
    log("Stop positions:  %d" % totals["stop_positions"])
    log("Steps:           %d" % totals["steps"])
    log("Elevators:       %d" % totals["elevators"])
    log("Escalators:      %d" % totals["escalators"])
    log("Footways:        %d" % totals["footways"])
    log("Route relations: %d" % len(routes))
    log("Entrances:       %d" % len(entrances))
    log("Bus/tram stops:  %d" % len(stop_positions))
    log("Bus/tram routes: %d" % len(bus_routes))
    return 0


if __name__ == "__main__":
    sys.exit(main())
