#!/usr/bin/env python3
"""
Audit of the bus and tram stops of the station assets (Berlin and the state packs): where the
sign of a stop stands against its positions, platforms that are no position of their own, and
positions the map shows as an empty dot (no number, no lines).

    python3 tools/audit_stop_signs.py [--source-dir tools/osm-pbf] [--provider stops.csv]
        [--regions berlin,bayern] [--report /tmp/signs.json]

Per asset (app/src/main/assets/osm-berlin.json and osm-regions/osm-<state>.json):

- sign far: stop areas whose sign stands farther than 50 m from all their positions; "osm"
  for the signs the map draws from OpenStreetMap alone (before: the mean of the positions,
  as OsmStopArea did; after: tools/stop_sign.py), "provider" for the provider's points of
  --provider (a CSV id,lat,lon of VBB numbers or DHIDs, e.g. the stops.txt columns of a GTFS
  feed renamed, or a sample from the provider's nearby search) before (as they come) and
  after (tools/stop_sign.py);
- empty dots: positions with neither a number nor lines; "at a stop position" those that
  lie on a public_transport=stop_position of the extract (within 1 m);
- platforms no position: bus and tram platforms of the extract (<source-dir>/<region>-stations.json,
  Berlin: berlin-stations.json) within 100 m of the asset's positions that have no position
  of their own (none within 8 m: a platform mapped twice is one position);
- route platforms without their line: platform members of bus and tram route relations with
  no position within 8 m that lists the route's line.

Data (c) OpenStreetMap contributors, ODbL 1.0.
"""

import argparse
import csv
import json
import math
import os
import sys
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)
import stop_sign  # noqa: E402

ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
FAR_METERS = 50.0
# A platform with a position this close is one (build_station_asset.SAME_POSITION_METERS: a
# platform mapped twice, as a way and a highway=bus_stop node beside it, is one position).
OWN_METERS = 8.0
ON_METERS = 1.0
REGION_METERS = 100.0
CELL = 0.001


def meters(a, b):
    return stop_sign.meters(a, b)


class Grid:
    def __init__(self):
        self.cells = defaultdict(list)

    def add(self, point, item):
        self.cells[(int(point[0] // CELL), int(point[1] // CELL))].append((point, item))

    def near(self, point, radius):
        reach = int(radius / 70.0) + 1
        cy, cx = int(point[0] // CELL), int(point[1] // CELL)
        out = []
        for dy in range(-reach, reach + 1):
            for dx in range(-reach, reach + 1):
                for p, item in self.cells.get((cy + dy, cx + dx), ()):
                    d = meters(point, p)
                    if d <= radius:
                        out.append((d, p, item))
        out.sort(key=lambda t: t[0])
        return out


def asset_files(regions, assets=ASSETS):
    found = []
    if not regions or "berlin" in regions:
        found.append(("berlin", os.path.join(assets, "osm-berlin.json")))
    folder = os.path.join(assets, "osm-regions")
    for name in sorted(os.listdir(folder)) if os.path.isdir(folder) else []:
        if name.startswith("osm-") and name.endswith(".json") and not name.startswith("osm-change"):
            region = name[4:-5]
            if not regions or region in regions:
                found.append((region, os.path.join(folder, name)))
    return found


def read_provider(path):
    if not path:
        return []
    out = []
    with open(path, encoding="utf-8") as f:
        for row in csv.DictReader(f):
            out.append((row["id"].strip(), (float(row["lat"]), float(row["lon"])), row.get("products", "")))
    return out


RAIL = {"suburban", "subway", "regional", "express"}


def audit(region, path, source_dir, provider):
    asset = json.load(open(path, encoding="utf-8"))
    areas = asset.get("stopAreas", [])
    grid = Grid()
    by_id = defaultdict(list)
    counts = defaultdict(int)
    examples = defaultdict(list)
    for area in areas:
        points = [(p[0], p[1]) for p in area["p"]]
        for p in area["p"]:
            grid.add((p[0], p[1]), (area, p))
        for i in area.get("ids", []):
            by_id[i].append(area)
        counts["areas"] += 1
        counts["positions"] += len(points)
        mean = (sum(p[0] for p in points) / len(points), sum(p[1] for p in points) / len(points))
        if min(meters(mean, p) for p in points) > FAR_METERS:
            counts["osm sign far before"] += 1
        after = stop_sign.sign_point(None, points)
        if min(meters(after, p) for p in points) > FAR_METERS:
            counts["osm sign far after"] += 1
            examples["osm sign far after"].append(area["n"])
        for p in area["p"]:
            if not (len(p) > 3 and p[3]) and not (len(p) > 4 and p[4]):
                counts["empty dots"] += 1
    for vid, point, products in provider:
        if set(products.split(";")) & RAIL:
            continue
        found = by_id.get(vid) or by_id.get(vid.split(":")[-1]) or []
        positions = [(p[0], p[1]) for a in found for p in a["p"] if meters(point, (p[0], p[1])) <= 1000]
        if not positions:
            continue
        counts["provider stops"] += 1
        if min(meters(point, p) for p in positions) > FAR_METERS:
            counts["provider sign far before"] += 1
            examples["provider sign far before"].append(vid)
        sign = stop_sign.sign_point(point, positions)
        if min(meters(sign, p) for p in positions) > FAR_METERS:
            counts["provider sign far after"] += 1
        if sign != point:
            counts["provider signs moved"] += 1
    source = None
    if source_dir:
        candidate = os.path.join(source_dir, "%s-stations.json" % region)
        if os.path.exists(candidate):
            source = json.load(open(candidate, encoding="utf-8"))
    if source is not None:
        platforms = {}
        for item in source.get("stopPositions", []):
            point = (item["lat"], item["lon"])
            if item["kind"] == "stop_position":
                for d, _, (area, p) in grid.near(point, ON_METERS):
                    if not (len(p) > 3 and p[3]) and not (len(p) > 4 and p[4]):
                        counts["empty dots at a stop position"] += 1
                        if len(examples["empty dots at a stop position"]) < 20:
                            examples["empty dots at a stop position"].append(area["n"])
                        break
                continue
            platforms["%s/%d" % (item["type"], item["id"])] = item
            if not grid.near(point, REGION_METERS):
                continue
            counts["platforms"] += 1
            if not grid.near(point, OWN_METERS):
                counts["platforms no position"] += 1
                if len(examples["platforms no position"]) < 20:
                    examples["platforms no position"].append(item.get("name"))
        types = {"n": "node", "w": "way", "r": "relation"}
        seen = set()
        for route in source.get("busRoutes", []):
            line = (route.get("ref") or "").split(";")[0].strip()
            if not line:
                continue
            product = "t" if route.get("route") == "tram" else "b"
            for member in route.get("m", []):
                if not member[2].startswith("platform"):
                    continue
                item = platforms.get("%s/%d" % (types.get(member[0], member[0]), member[1]))
                if item is None or (product == "b" and not item["bus"]) or (product == "t" and not item["tram"]):
                    continue
                point = (item["lat"], item["lon"])
                if (line, member[1], product) in seen or not grid.near(point, REGION_METERS):
                    continue
                seen.add((line, member[1], product))
                counts["route platforms"] += 1
                near = grid.near(point, OWN_METERS)
                if not any(len(p) > 4 and line in p[4].get(product, []) for _, _, (_, p) in near):
                    counts["route platforms without their line"] += 1
                    if len(examples["route platforms without their line"]) < 20:
                        examples["route platforms without their line"].append("%s %s" % (item.get("name"), line))
    return dict(counts), {k: v[:20] for k, v in examples.items()}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--source-dir", default=os.path.join(HERE, "osm-pbf"))
    parser.add_argument("--provider")
    parser.add_argument("--regions")
    parser.add_argument("--report")
    parser.add_argument("--assets", default=ASSETS, help="the assets folder (default: the app's; for a "
                        "before/after, e.g. one of `git archive <commit> app/src/main/assets`)")
    args = parser.parse_args(argv)
    regions = set(args.regions.split(",")) if args.regions else None
    provider = read_provider(args.provider)
    report = {}
    total = defaultdict(int)
    for region, path in asset_files(regions, args.assets):
        counts, examples = audit(region, path, args.source_dir, provider)
        report[region] = {"counts": counts, "examples": examples}
        for k, v in counts.items():
            total[k] += v
        print("%-24s %s" % (region, json.dumps(counts, sort_keys=True)), flush=True)
    print("%-24s %s" % ("total", json.dumps(dict(total), sort_keys=True)))
    if args.report:
        with open(args.report, "w", encoding="utf-8") as f:
            json.dump({"total": total, "regions": report}, f, ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
