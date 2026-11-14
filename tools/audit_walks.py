#!/usr/bin/env python3
"""
Audit of the change walks of all packs (transfer-walks-berlin.json and
osm-regions/transfer-walks-<state>.json): flags walks that look wrong against the geometry of
the station asset they belong to, prints a summary per region and writes the outliers.

    python3 tools/audit_walks.py [--regions berlin,bayern,...] [--report /tmp/audit.json]
        [--geojson /tmp/audit.geojson] [--top 30] [--paths]

Flags (a walk may have several):

- estimate: no way over the network (or one more than 3 times the straight line and 2 minutes
  longer): the straight line x 1.3 stands in, a missing passage in the mapping;
- detour: over the network, but at the usual pace (75 m/min) more than DETOUR_FLAG times the
  straight line between the two platforms (or stops) and at least DETOUR_MIN_EXTRA seconds
  more;
- faster: shorter than the straight line at the usual pace (the walk cannot be right: the
  platform or stop was matched to the wrong place);
- floor: the floor of 30 s per level decides (the network gives less), with LEVEL_FLAG levels
  or more;
- same-platform: two platforms of the asset that touch (axes within SAME_PLATFORM_METERS, on one
  level, sharing a track number) with a walk longer than SAME_PLATFORM_SECONDS: one platform
  mapped as two ways, the app should count it as one;
- lines: with the extract, a platform whose lines in the asset share none with the route_ref of
  its OSM element (the platform matched to the wrong axis, or the routes to the wrong platform).

With --paths (needs the extract and the walking network of the region, see the README) the
GeoJSON holds the way the builder finds for each outlier; else the straight line between the
two ends, with the platform axes.

Data (c) OpenStreetMap contributors, ODbL 1.0.
"""

import argparse
import json
import math
import os
import sys
from collections import Counter, defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)
import build_transfer_walks as btw  # noqa: E402
import regions as rg  # noqa: E402

ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
PACKS = os.path.join(ASSETS, "osm-regions")

DETOUR_FLAG = 2.5
DETOUR_MIN_EXTRA = 60.0
FASTER_TOLERANCE_SECONDS = 5.0
LEVEL_FLAG = 2
SAME_PLATFORM_METERS = 3.0
SAME_PLATFORM_SECONDS = 60.0
# Below this straight line the detour factor says nothing (two ends of one stair).
MIN_STRAIGHT_METERS = 20.0


def region_files(name, args):
    """(station asset, walks, extract, graph) of a region."""
    if name == "berlin":
        return (os.path.join(ASSETS, "osm-berlin.json"), os.path.join(ASSETS, "transfer-walks-berlin.json"),
                args.berlin_extract, args.berlin_graph)
    return (os.path.join(PACKS, "osm-%s.json" % name), os.path.join(PACKS, "transfer-walks-%s.json" % name),
            os.path.join(args.extract_dir, "%s-stations.json" % name), os.path.join(args.graph_dir, "%s.json" % name))


def tokens(value):
    return {t.strip() for t in (value or "").replace(",", ";").split(";") if t.strip()}


def level_value(platform):
    try:
        return float(platform.get("level", ""))
    except (TypeError, ValueError):
        return None


def end_of(index, walk_station, station):
    """("p", axis, platform) or ("s", point, None) of index [index] of a walk."""
    axes = walk_station["p"]
    if index < len(axes):
        platform = station["platforms"][index] if station and index < len(station["platforms"]) else {}
        return ("p", axes[index], platform)
    return ("s", walk_station["s"][index - len(axes)], None)


def straight_meters(a, b):
    if a[0] == "p" and b[0] == "p":
        x, y = a[1], b[1]
        return btw.segments_meters((x[0], x[1]), (x[2], x[3]), (y[0], y[1]), (y[2], y[3]))
    if a[0] == "p":
        a, b = b, a
    if b[0] == "p":
        y = b[1]
        return btw.segment_meters(tuple(a[1]), (y[0], y[1]), (y[2], y[3]))
    return btw.meters(tuple(a[1]), tuple(b[1]))


def stations_by_key(asset):
    """The stations of the asset by (name, first platform axis), as build_transfer_walks keys them."""
    out = {}
    for station in asset["stations"]:
        axes = tuple(tuple(p["a"]) for p in station["platforms"])
        out[(station["name"], axes)] = station
    return out


def audit_region(name, args, flagged, totals):
    asset_path, walks_path, extract_path, _ = region_files(name, args)
    if not (os.path.exists(asset_path) and os.path.exists(walks_path)):
        print("  %s: no asset or walks, skipped" % name)
        return
    with open(asset_path, encoding="utf-8") as handle:
        asset = json.load(handle)
    with open(walks_path, encoding="utf-8") as handle:
        walks = json.load(handle)
    by_key = stations_by_key(asset)
    route_refs = None
    if args.lines and os.path.exists(extract_path):
        with open(extract_path, encoding="utf-8") as handle:
            data = json.load(handle)
        route_refs = {axis: tokens(item["tags"].get("route_ref")) for axis, item in btw.platform_elements(data).items()}
        del data
    counts = Counter()
    for ws in walks["stations"]:
        station = by_key.get((ws["n"], tuple(tuple(a) for a in ws["p"])))
        if station is None and ws["p"]:
            counts["unmatched complexes"] += 1
        if station is not None and route_refs is not None:
            for k, platform in enumerate(station["platforms"]):
                lines = set(platform.get("lines") or [])
                refs = route_refs.get(tuple(platform["a"]))
                if lines and refs and not ({l.replace(" ", "") for l in lines} & {r.replace(" ", "") for r in refs}):
                    counts["lines"] += 1
                    flagged.append({"region": name, "station": ws["n"], "i": k, "j": k, "flags": ["lines"],
                                    "lines": sorted(lines), "routeRef": sorted(refs), "score": 1000.0,
                                    "a": ("p", platform["a"], platform), "b": ("p", platform["a"], platform)})
        for i, j, seconds, free, estimate in ws["w"]:
            counts["walks"] += 1
            a, b = end_of(i, ws, station), end_of(j, ws, station)
            kind = "platform" if b[0] == "p" else ("stop" if a[0] == "p" else "stop pair")
            counts[kind] += 1
            straight = straight_meters(a, b)
            walk_m = seconds * btw.SPEED
            flags = []
            score = 0.0
            if estimate:
                flags.append("estimate")
                score = max(score, 300.0 + straight)
            gap = 0
            if a[0] == "p" and b[0] == "p":
                la, lb = level_value(a[2]), level_value(b[2])
                if la is not None and lb is not None:
                    gap = int(round(abs(la - lb)))
            elif a[0] == "p" or b[0] == "p":
                p = a if a[0] == "p" else b
                lv = level_value(p[2])
                if lv is not None:
                    gap = int(round(abs(lv)))
            floor = btw.LEVEL_CHANGE_SECONDS * gap
            if not estimate:
                if straight >= MIN_STRAIGHT_METERS and walk_m > DETOUR_FLAG * straight and \
                        seconds - straight / btw.SPEED >= DETOUR_MIN_EXTRA + floor:
                    flags.append("detour")
                    score = max(score, seconds - straight / btw.SPEED)
                if seconds + FASTER_TOLERANCE_SECONDS < straight / btw.SPEED:
                    flags.append("faster")
                    score = max(score, 500.0 + straight / btw.SPEED - seconds)
                if gap >= LEVEL_FLAG and seconds == btw.whole_seconds(floor):
                    flags.append("floor")
                    score = max(score, floor / 2.0)
            if a[0] == "p" and b[0] == "p" and straight <= SAME_PLATFORM_METERS and \
                    level_value(a[2]) == level_value(b[2]) and \
                    tokens(a[2].get("ref")) & tokens(b[2].get("ref")) and seconds > SAME_PLATFORM_SECONDS:
                flags.append("same-platform")
                score = max(score, 400.0 + seconds)
            for flag in flags:
                counts[flag] += 1
            if flags:
                flagged.append({"region": name, "station": ws["n"], "i": i, "j": j, "kind": kind,
                                "seconds": seconds, "stepFreeSeconds": free, "straightMeters": round(straight, 1),
                                "walkMeters": round(walk_m), "levels": gap, "flags": flags, "score": score,
                                "a": a, "b": b})
    totals[name] = counts
    print("  %-24s %s" % (name, ", ".join("%s %d" % kv for kv in sorted(counts.items()))), flush=True)


def feature_of(item, path=None):
    props = {k: v for k, v in item.items() if k not in ("a", "b", "score")}
    props["name"] = "%s %s %s:%s %s" % (item["region"], item["station"], item["i"], item["j"], ",".join(item["flags"]))
    if path:
        coords = path
    else:
        def point(end):
            if end[0] == "p":
                x = end[1]
                return [round((x[1] + x[3]) / 2, 6), round((x[0] + x[2]) / 2, 6)]
            return [end[1][1], end[1][0]]
        coords = [point(item["a"]), point(item["b"])]
    features = [{"type": "Feature", "properties": props, "geometry": {"type": "LineString", "coordinates": coords}}]
    for end in (item["a"], item["b"]):
        if end[0] == "p":
            x = end[1]
            features.append({"type": "Feature", "properties": {"name": "platform %s" % (end[2] or {}).get("ref", ""),
                                                               "lines": (end[2] or {}).get("lines", [])},
                             "geometry": {"type": "LineString", "coordinates": [[x[1], x[0]], [x[3], x[2]]]}})
    return features


def paths_for(name, items, args):
    """The ways the builder finds for [items] of region [name]: {id(item): [[lon, lat], ...]}."""
    asset_path, walks_path, extract_path, graph_path = region_files(name, args)
    if not (os.path.exists(extract_path) and os.path.exists(graph_path)):
        print("  %s: no extract or graph for the paths" % name)
        return {}
    import walk_path
    with open(extract_path, encoding="utf-8") as handle:
        data = json.load(handle)
    elements = btw.platform_elements(data)
    del data
    with open(graph_path, encoding="utf-8") as handle:
        graph = btw.Graph(json.load(handle))
    out = {}
    for item in items:
        a, b = item["a"], item["b"]
        if a[0] == "p":
            if tuple(a[1]) not in elements:
                continue
            sources = btw.platform_sources(graph, elements[tuple(a[1])])
        else:
            # Two stops: from the nodes the first one joins (its metres to them are left out).
            sources = {v for v, _ in btw.stop_attach(graph, tuple(a[1]))}
        if b[0] == "p":
            if tuple(b[1]) not in elements:
                continue
            targets = dict.fromkeys(btw.platform_sources(graph, elements[tuple(b[1])]), 0.0)
        else:
            targets = {v: d / btw.SPEED for v, d in btw.stop_attach(graph, tuple(b[1]))}
        if not sources or not targets:
            continue
        _, nodes = walk_path.shortest(graph, sources, targets, False)
        if nodes:
            out[id(item)] = [[round(graph.points[v][1], 6), round(graph.points[v][0], 6)] for v in nodes]
    return out


def main(argv=None):
    parser = argparse.ArgumentParser(description="Flag suspicious change walks of all packs.")
    parser.add_argument("--regions", default="berlin," + ",".join(s for s in sorted(rg.STATES) if s != "berlin"))
    parser.add_argument("--extract-dir", default=os.path.join(HERE, "osm-pbf"))
    parser.add_argument("--graph-dir", default=os.path.join(HERE, "osm-pbf", "walk-graphs"))
    parser.add_argument("--berlin-extract", default=os.path.join(HERE, "osm-stations.json"))
    parser.add_argument("--berlin-graph", default=os.path.join(HERE, "osm-walk-graph.json"))
    parser.add_argument("--lines", action="store_true", help="also compare platform lines with route_ref (reads the extracts)")
    parser.add_argument("--paths", action="store_true", help="the ways of the outliers over the network (reads the graphs)")
    parser.add_argument("--top", type=int, default=30)
    parser.add_argument("--report", default=None, help="all flagged walks as JSON")
    parser.add_argument("--geojson", default=None, help="the top outliers as a GeoJSON FeatureCollection")
    args = parser.parse_args(argv)
    flagged = []
    totals = {}
    print("Walks per region:")
    for name in [r for r in args.regions.split(",") if r]:
        audit_region(name, args, flagged, totals)
    whole = Counter()
    for counts in totals.values():
        whole.update(counts)
    print("All: %s" % ", ".join("%s %d" % kv for kv in sorted(whole.items())))
    top = sorted(flagged, key=lambda f: -f["score"])[:args.top]
    print("Top %d:" % len(top))
    for item in top:
        print("  %-20s %-32s %3s:%-3s %5s s %6s m straight %s" % (
            item["region"], item["station"][:32], item["i"], item["j"], item.get("seconds", ""),
            item.get("straightMeters", ""), ",".join(item["flags"]) + (
                " lines %s / route_ref %s" % (item["lines"], item["routeRef"]) if "lines" in item else "")))
    if args.report:
        with open(args.report, "w", encoding="utf-8") as handle:
            json.dump({"totals": totals, "flagged": [{k: v for k, v in f.items() if k not in ("a", "b")} for f in flagged]},
                      handle, ensure_ascii=False)
    if args.geojson:
        paths = {}
        if args.paths:
            by_region = defaultdict(list)
            for item in top:
                by_region[item["region"]].append(item)
            for name, items in by_region.items():
                paths.update(paths_for(name, items, args))
        features = []
        for item in top:
            features += feature_of(item, paths.get(id(item)))
        with open(args.geojson, "w", encoding="utf-8") as handle:
            json.dump({"type": "FeatureCollection", "features": features}, handle, ensure_ascii=False)
        print("Written %s" % args.geojson)
    return 0


if __name__ == "__main__":
    sys.exit(main())
