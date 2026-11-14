#!/usr/bin/env python3
"""
How big the station asset of a region is, section by section, plain and gzip-compressed,
to decide between one file for all of Germany inside the app and per-region packs.

    python3 tools/estimate_region_size.py tools/osm-stations.json
    python3 tools/estimate_region_size.py tools/osm-packs/osm-germany.json
    python3 tools/estimate_region_size.py tools/osm-packs/osm-germany.json --changes tools/osm-packs/osm-change-positions-germany.ndjson
    python3 tools/estimate_region_size.py tools/osm-stations.json --scale-stop-areas 250000 --scale-stations 6800

Input is either an extract (tools/osm-stations.json of tools/extract_osm_pbf.py: the asset is
built in memory with tools/build_station_asset.py, for the whole extract, nothing is
written) or a finished asset (osm-*.json). The sizes of "stations" are split further into
platforms, entrances, lifts and the rest, and those of "stopAreas" into positions with and
without lines. With --scale-stop-areas / --scale-stations it extrapolates from the averages
per stop area and per station complex to other counts (e.g. all of Germany): a rough guess,
as the mapping detail differs between regions (Berlin has many entrances and lifts per
station and bus routes almost everywhere). Standard library only.
"""

import argparse
import contextlib
import gzip
import io
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)


def compact(value):
    return json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode("utf-8")


def gz(raw):
    return len(gzip.compress(raw, 9))


def build_in_memory(data, quiet=True):
    import build_station_asset as b
    sink = io.StringIO()
    with contextlib.redirect_stdout(sink if quiet else sys.stdout):
        complexes, areas_of = b.build_complexes(data)
        station_by_key = {b.station_key(s): s for s in data["stations"]}
        routes, _ = b.route_visits(data, station_by_key)
        lines_of_platform = {}
        for route in routes:
            for visit in route["visits"]:
                if visit and visit["platform"]:
                    lines_of_platform.setdefault(visit["platform"], set()).add(route["line"])
        from collections import defaultdict
        asset = b.build_asset(data, complexes, areas_of, defaultdict(set, lines_of_platform))
        records = b.change_positions(data, complexes)
    kept = [r for r in records if r["confidence"] >= b.MIN_CONFIDENCE]
    changes = "".join(json.dumps({k: v for k, v in r.items() if not k.startswith("_")}, ensure_ascii=False,
                                 separators=(",", ":")) + "\n" for r in kept).encode("utf-8")
    return asset, changes


def human(n):
    for unit in ("B", "KB", "MB", "GB"):
        if n < 1024 or unit == "GB":
            return ("%.0f %s" if unit == "B" else "%.1f %s") % (n, unit)
        n /= 1024.0


def report(asset, changes, args):
    whole = compact(asset)
    rows = []
    stations = asset.get("stations", [])
    areas = asset.get("stopAreas", [])
    stops = asset.get("stops", [])
    for key in ("platforms", "entrances", "lifts"):
        part = [s.get(key, []) for s in stations]
        rows.append(("stations." + key, compact(part)))
    rest = [{k: v for k, v in s.items() if k not in ("platforms", "entrances", "lifts")} for s in stations]
    rows.append(("stations (names, ids, kinds)", compact(rest)))
    rows.append(("stops", compact(stops)))
    rows.append(("stopAreas: names and ids", compact([{k: v for k, v in a.items() if k != "p"} for a in areas])))
    rows.append(("stopAreas: positions", compact([[p[:4] for p in a["p"]] for a in areas])))
    rows.append(("stopAreas: lines at positions", compact([[p[4] for p in a["p"] if len(p) > 4] for a in areas])))
    print("Asset: %d station complexes, %d numbered stops, %d stop areas with %d positions"
          % (len(stations), len(stops), len(areas), sum(len(a["p"]) for a in areas)))
    print("%-34s %12s %12s" % ("section (each compressed alone)", "bytes", "gzip"))
    for name, raw in rows:
        print("%-34s %12s %12s" % (name, human(len(raw)), human(gz(raw))))
    print("%-34s %12s %12s" % ("whole osm-*.json", human(len(whole)), human(gz(whole))))
    if changes is not None:
        print("%-34s %12s %12s   (%d records)" % ("change positions (.ndjson)", human(len(changes)), human(gz(changes)),
                                                 changes.count(b"\n")))
    if args.scale_stop_areas or args.scale_stations:
        per_station = len(compact(stations)) / max(1, len(stations))
        per_area = (len(compact(areas)) + len(compact(stops))) / max(1, len(areas))
        ratio = gz(whole) / max(1, len(whole))
        n_st = args.scale_stations or len(stations)
        n_ar = args.scale_stop_areas or len(areas)
        guess = n_st * per_station + n_ar * per_area
        print("")
        print("Extrapolated to %d station complexes and %d stop areas (%.0f B per complex, %.0f B per stop area): "
              "%s, gzip about %s" % (n_st, n_ar, per_station, per_area, human(guess), human(guess * ratio)))


def main(argv=None):
    parser = argparse.ArgumentParser(description="Section sizes of the station asset of a region.")
    parser.add_argument("input", help="tools/osm-stations.json (an extract) or an osm-*.json asset")
    parser.add_argument("--changes", help="an osm-change-positions*.ndjson to include (for an asset input)")
    parser.add_argument("--scale-stations", type=int, help="extrapolate to this many station complexes")
    parser.add_argument("--scale-stop-areas", type=int, help="extrapolate to this many bus/tram stop areas")
    parser.add_argument("--verbose", action="store_true", help="show the log of the build (extract input)")
    args = parser.parse_args(argv)
    with open(args.input, "r", encoding="utf-8") as handle:
        data = json.load(handle)
    if "stopPositions" in data:
        print("Extract %s (region %s): building in memory ..." % (args.input, (data.get("source") or {}).get("region", "?")))
        asset, changes = build_in_memory(data, quiet=not args.verbose)
    else:
        asset = data
        changes = None
        if args.changes:
            with open(args.changes, "rb") as handle:
                changes = handle.read()
    report(asset, changes, args)
    return 0


if __name__ == "__main__":
    sys.exit(main())
