#!/usr/bin/env python3
"""Where the rail lines of Germany run underground, for the hint of the sunny side (#219).

Reads the rail ways (railway=subway|light_rail|rail, main lines only: no service=*, no
industrial, military or tourist usage; trams stay out) of a Geofabrik .osm.pbf extract and
writes app/src/main/assets/rail-tunnels-germany.json: one grid of cells of about 50 m over the
Germany box, listing the cells a way in a tunnel crosses (tunnel=* other than no,
location=underground, or a negative layer that is not a cutting) and the cells an open way
crosses, but the open ones only within OPEN_RADIUS cells of a tunnel cell: the app decides a
cell crossed by both (or by neither) by its eight neighbours, so open cells further away never
change a decision, and any cell not listed counts as open. A cell may be in both lists (a line
in a tunnel under an open one). See data/RailTunnels.kt.

One grid for the whole box: cellLon is about 50 m at 51 degrees north, so a cell is about 54 m
wide in the far south and about 46 m in the far north (+-8 %), which is fine for the hint.

Format: "box" [south, west, north, east], "cellLat"/"cellLon" in degrees, "cols"; "tunnel" and
"open" are the sorted cell indices (row * cols + col, row 0 at the south edge) as gaps to the
previous index in base 36, joined by ",". Needs pyosmium 4 or newer:

    python3 tools/build_tunnel_asset.py tools/osm-pbf/germany-latest.osm.pbf [--cache FILE] [--cell METERS]

--cache keeps the rail polylines read from the extract in FILE (JSON lines) and reads them from
there next time, so a second run (e.g. with another --cell) skips the slow pass over the extract.

Data (c) OpenStreetMap contributors, ODbL 1.0: https://www.openstreetmap.org/copyright
"""
import datetime
import json
import math
import os
import sys
from array import array

BOX = (47.2, 5.8, 55.1, 15.1)  # Germany with a margin
REFERENCE_LAT = 51.0  # where cellLon is as wide as cellLat is high
CELL_LAT = 0.00045  # about 50 m
CELL_LON = 0.000715  # about 50 m at 51 degrees north
OPEN_RADIUS = 2  # open cells kept within this many cells (Chebyshev) of a tunnel cell
STEP_METERS = 10.0
RAIL_KINDS = ("subway", "light_rail", "rail")
SKIP_USAGE = {"industrial", "military", "tourism", "test"}
ASSET_NAME = "rail-tunnels-germany.json"
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "assets", ASSET_NAME)


def set_cell_meters(meters):
    """Sets the cell size to about [meters] (cellLon at REFERENCE_LAT)."""
    global CELL_LAT, CELL_LON
    CELL_LAT = round(meters / 111_320.0, 6)
    CELL_LON = round(meters / (111_320.0 * math.cos(math.radians(REFERENCE_LAT))), 6)


def is_main_line(tags):
    """A rail way a ride runs on: subway, light rail or rail, no siding, yard or the like."""
    if tags.get("railway") not in RAIL_KINDS:
        return False
    if "service" in tags:
        return False
    return tags.get("usage") not in SKIP_USAGE


def is_tunnel(tags):
    """Whether the way runs underground (a cutting with a negative layer stays open)."""
    tunnel = tags.get("tunnel")
    if tunnel is not None and tunnel != "no":
        return True
    if tags.get("location") == "underground":
        return True
    try:
        layer = float(tags.get("layer", "0").split(";")[0])
    except ValueError:
        layer = 0.0
    return layer < 0 and tags.get("cutting", "no") == "no"


def cols():
    return int(math.ceil((BOX[3] - BOX[1]) / CELL_LON))


def rows():
    return int(math.ceil((BOX[2] - BOX[0]) / CELL_LAT))


def cell_of(lat, lon):
    """The index of the cell at lat, lon; None outside the box."""
    if not (BOX[0] <= lat < BOX[2] and BOX[1] <= lon < BOX[3]):
        return None
    row = int((lat - BOX[0]) / CELL_LAT)
    col = int((lon - BOX[1]) / CELL_LON)
    return row * cols() + col


def cells_along(points):
    """Every cell a polyline of (lat, lon) crosses, sampled every STEP_METERS."""
    out = set()
    for (lat1, lon1), (lat2, lon2) in zip(points, points[1:]):
        dy = (lat2 - lat1) * 111_320.0
        dx = (lon2 - lon1) * 111_320.0 * math.cos(math.radians((lat1 + lat2) / 2))
        steps = max(1, int(math.hypot(dx, dy) / STEP_METERS))
        for i in range(steps + 1):
            t = i / steps
            cell = cell_of(lat1 + (lat2 - lat1) * t, lon1 + (lon2 - lon1) * t)
            if cell is not None:
                out.add(cell)
    if len(points) == 1:
        cell = cell_of(*points[0])
        if cell is not None:
            out.add(cell)
    return out


def near_tunnel(cell, tunnel, radius=OPEN_RADIUS):
    """Whether a tunnel cell lies within [radius] cells (rows and columns) of [cell]."""
    n = cols()
    row, col = divmod(cell, n)
    for dr in range(-radius, radius + 1):
        for dc in range(-radius, radius + 1):
            c = col + dc
            if 0 <= c < n and (row + dr) * n + c in tunnel:
                return True
    return False


def encode(cells):
    """Sorted cell indices as base-36 gaps joined by commas."""
    def b36(n):
        digits = "0123456789abcdefghijklmnopqrstuvwxyz"
        s = ""
        while True:
            n, r = divmod(n, 36)
            s = digits[r] + s
            if n == 0:
                return s
    previous = 0
    parts = []
    for cell in sorted(cells):
        parts.append(b36(cell - previous))
        previous = cell
    return ",".join(parts)


def read_ways(path):
    import osmium
    import osmium.filter
    ways = []
    rail = osmium.filter.TagFilter(*(("railway", kind) for kind in RAIL_KINDS))
    for way in osmium.FileProcessor(path, osmium.osm.WAY).with_filter(rail):
        tags = {tag.k: tag.v for tag in way.tags}
        if is_main_line(tags):
            ways.append((array("q", (node.ref for node in way.nodes)), is_tunnel(tags)))
    return ways


def read_locations(path, ways):
    import osmium
    import osmium.filter
    import osmium.index
    import osmium.io
    storage = osmium.index.create_map("sparse_mem_array")
    handler = osmium.NodeLocationsForWays(storage)
    reader = osmium.io.Reader(path, osmium.osm.NODE)
    try:
        osmium.apply(reader, osmium.filter.IdFilter(ref for refs, _ in ways for ref in refs), handler)
    finally:
        reader.close()
    osmium.apply(osmium.io.FileBuffer(b"<osm version='0.6'><way id='1'/></osm>", "osm"), handler)
    return storage


def read_polylines(path):
    """The (points, tunnel) pairs of the main rail ways of the extract at [path]."""
    ways = read_ways(path)
    storage = read_locations(path, ways)

    def locate(ref):
        try:
            location = storage.get(ref)
        except KeyError:
            return None
        return (round(location.lat, 7), round(location.lon, 7))

    out = []
    for refs, underground in ways:
        points = [p for p in (locate(ref) for ref in refs) if p is not None]
        if points:
            out.append((points, underground))
    return out


def build(polylines):
    """The tunnel cells and the open cells near them (see OPEN_RADIUS) of (points, tunnel) pairs."""
    tunnel = set()
    for points, underground in polylines:
        if underground:
            tunnel.update(cells_along(points))
    open_ = set()
    for points, underground in polylines:
        if not underground:
            open_.update(cell for cell in cells_along(points) if near_tunnel(cell, tunnel))
    return tunnel, open_


def main(argv):
    args = argv[1:]
    cache = None
    if "--cache" in args:
        i = args.index("--cache")
        cache = args[i + 1]
        del args[i:i + 2]
    if "--cell" in args:
        i = args.index("--cell")
        set_cell_meters(float(args[i + 1]))
        del args[i:i + 2]
    if len(args) != 1:
        sys.exit("usage: build_tunnel_asset.py <germany.osm.pbf> [--cache FILE] [--cell METERS]")
    if cache and os.path.exists(cache):
        with open(cache) as f:
            polylines = [(list(map(tuple, p)), t) for p, t in map(json.loads, f)]
    else:
        polylines = read_polylines(args[0])
        if cache:
            with open(cache, "w") as f:
                for points, underground in polylines:
                    f.write(json.dumps([points, underground], separators=(",", ":")) + "\n")
    tunnel, open_ = build(polylines)
    asset = {
        "license": "ODbL-1.0",
        "attribution": "(c) OpenStreetMap contributors",
        "source": "OpenStreetMap rail ways (railway=subway|light_rail|rail), tools/build_tunnel_asset.py",
        "generated": datetime.date.today().isoformat(),
        "box": list(BOX),
        "cellLat": CELL_LAT,
        "cellLon": CELL_LON,
        "cols": cols(),
        "tunnel": encode(tunnel),
        "open": encode(open_),
    }
    with open(OUT, "w") as out:
        json.dump(asset, out, separators=(",", ":"))
    print("%d ways, %d tunnel cells, %d open cells, %d x %d grid, %d bytes" % (
        len(polylines), len(tunnel), len(open_), rows(), cols(), os.path.getsize(OUT)))


if __name__ == "__main__":
    main(sys.argv)
