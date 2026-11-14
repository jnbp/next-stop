#!/usr/bin/env python3
"""
The way of one change walk as tools/build_transfer_walks.py finds it, for checking the asset:
the seconds, the metres, the levels it passes and the way itself as a GeoJSON LineString.

    python3 tools/walk_path.py "Yorckstraße:1:2" "Südkreuz:1:s3" [--step-free] [--geojson out.json]

Each place is "<complex name>:<from platform>:<to platform>" with the platform indices of
osm-berlin.json (and of "p" in transfer-walks-berlin.json), or "s<k>" for the k-th stop
position ("s") of the complex in transfer-walks-berlin.json. Inputs as for
build_transfer_walks.py (the extract, the walking network and the station asset).

Data (c) OpenStreetMap contributors, ODbL 1.0.
"""

import argparse
import heapq
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import build_transfer_walks as btw  # noqa: E402


def shortest(graph, sources, targets, step_free):
    """(seconds, nodes) of the shortest way from any of [sources] to one of [targets] (node ->
    seconds to add there); (None, None) where none is found."""
    best = dict.fromkeys(sources, 0.0)
    prev = {}
    heap = [(0.0, s) for s in sources]
    heapq.heapify(heap)
    done = set()
    found = None
    while heap:
        seconds, v = heapq.heappop(heap)
        if v in done:
            continue
        done.add(v)
        if found is not None and seconds > found[0]:
            break
        if v in targets and (found is None or seconds + targets[v] < found[0]):
            found = (seconds + targets[v], v)
        for u, cost, free in graph.adj[v]:
            if step_free and not free:
                continue
            if seconds + cost < best.get(u, float("inf")):
                best[u] = seconds + cost
                prev[u] = v
                heapq.heappush(heap, (seconds + cost, u))
    if found is None:
        return None, None
    nodes = [found[1]]
    while nodes[-1] in prev:
        nodes.append(prev[nodes[-1]])
    return found[0], nodes[::-1]


def describe(graph, elements, asset, walks, spec, step_free):
    name, i, j = spec.split(":")
    station = next(s for s in asset["stations"] if s["name"] == name)
    platforms = station["platforms"]
    axes = [tuple(p["a"]) for p in platforms]
    i = int(i)
    sources = btw.platform_sources(graph, elements[axes[i]])
    if j.startswith("s"):
        stop = tuple(next(w for w in walks["stations"] if w["n"] == name)["s"][int(j[1:])])
        targets = {v: d / btw.SPEED for v, d in btw.stop_attach(graph, stop)}
        to, to_platform = "stop %.6f,%.6f" % stop, None
    else:
        targets = dict.fromkeys(btw.platform_sources(graph, elements[axes[int(j)]]), 0.0)
        to_platform = platforms[int(j)]
        to = ",".join(to_platform.get("lines") or [to_platform.get("ref") or j])
    seconds, nodes = shortest(graph, sources, targets, step_free)
    if nodes is None:
        return {"walk": spec, "found": False}
    points = [graph.points[v] for v in nodes]
    metres = sum(btw.meters(points[k], points[k + 1]) for k in range(len(points) - 1))
    levels = []
    for v in nodes:
        for level in sorted(graph.levels[v]):
            if level and level not in levels:
                levels.append(level)
    floor = btw.LEVEL_CHANGE_SECONDS * btw.level_gap(platforms[i], to_platform)
    label = "%s %s -> %s" % (name, ",".join(platforms[i].get("lines") or [str(i)]), to)
    return {
        "walk": label,
        "seconds": round(seconds, 1),
        "assetSeconds": btw.whole_seconds(max(seconds, floor)),
        "levelFloorSeconds": floor,
        "metres": round(metres),
        "levels": levels,
        "stepFree": step_free,
        "feature": {
            "type": "Feature",
            "properties": {"name": label, "metres": round(metres), "seconds": round(seconds)},
            "geometry": {"type": "LineString", "coordinates": [[round(lon, 6), round(lat, 6)] for lat, lon in points]},
        },
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description="Print the way of change walks as GeoJSON.")
    parser.add_argument("walks", nargs="+", help='"<complex>:<from platform>:<to platform or s<stop>>"')
    parser.add_argument("--step-free", action="store_true", help="without steps and escalators")
    parser.add_argument("--source", default=btw.bsa.SOURCE)
    parser.add_argument("--graph", default=btw.GRAPH)
    parser.add_argument("--asset", default=btw.STATION_ASSET)
    parser.add_argument("--transfer-walks", default=btw.OUTPUT)
    parser.add_argument("--geojson", default=None, help="also write all ways as one FeatureCollection here")
    args = parser.parse_args(argv)
    with open(args.source, "r", encoding="utf-8") as handle:
        data = json.load(handle)
    with open(args.asset, "r", encoding="utf-8") as handle:
        asset = json.load(handle)
    with open(args.transfer_walks, "r", encoding="utf-8") as handle:
        walks = json.load(handle)
    with open(args.graph, "r", encoding="utf-8") as handle:
        graph = btw.Graph(json.load(handle))
    elements = btw.platform_elements(data)
    features = []
    for spec in args.walks:
        result = describe(graph, elements, asset, walks, spec, args.step_free)
        feature = result.pop("feature", None)
        if feature:
            features.append(feature)
        print(json.dumps(result, ensure_ascii=False))
    if args.geojson:
        with open(args.geojson, "w", encoding="utf-8") as handle:
            json.dump({"type": "FeatureCollection", "features": features}, handle, ensure_ascii=False)
    return 0


if __name__ == "__main__":
    sys.exit(main())
