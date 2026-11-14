#!/usr/bin/env python3
"""
The index of the state packs for the app: in which cells of 0.1 degrees each pack
(osm-<state>.json of build_station_asset.py --split-regions or --region <state>) has stations,
stop positions or stop areas, so the app loads only the packs of the place it looks at
(OsmStations.ensureRegions; without the index it goes by the generous boxes of the states).

    python3 tools/build_region_index.py                      # tools/osm-packs/states -> its index.json
    python3 tools/build_region_index.py --install            # also copies the packs into the app

--install copies the packs (all but Berlin, which the app has built in as osm-berlin.json) and
the index into app/src/main/assets/osm-regions/, from where the app reads them. With each pack
go its boarding positions for short changes, osm-change-positions-<state>.ndjson, where the pack
has any (an empty file is left out); the app reads them with the pack (ChangePositions.loadRegions).
A pack may also be put into the app's files under osm-regions/ (a download), which wins over the assets.

Standard library only. Data (c) OpenStreetMap contributors, ODbL 1.0.
"""

import argparse
import glob
import json
import math
import os
import shutil

HERE = os.path.dirname(os.path.abspath(__file__))
PACKS = os.path.join(HERE, "osm-packs", "states")
APP_DIR = os.path.join(HERE, "..", "app", "src", "main", "assets", "osm-regions")
CELL = 0.1  # keep equal to REGION_CELL of OsmStations.kt
BUILT_IN = "berlin"


def cells_of(pack):
    cells = set()

    def add(lat, lon):
        cells.add((int(math.floor(lat / CELL)), int(math.floor(lon / CELL))))

    for station in pack.get("stations", []):
        add(station["lat"], station["lon"])
        for entrance in station.get("entrances", []):
            add(entrance["lat"], entrance["lon"])
    for stop in pack.get("stops", []):
        add(stop["lat"], stop["lon"])
    for area in pack.get("stopAreas", []):
        for position in area.get("p", []):
            add(position[0], position[1])
    return cells


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.strip().splitlines()[0])
    parser.add_argument("--packs-dir", default=PACKS, help="where the osm-<state>.json packs are")
    parser.add_argument("--install", action="store_true", help="copy packs and index into the app's assets")
    parser.add_argument("--app-dir", default=APP_DIR, help="the folder of the packs in the app")
    args = parser.parse_args(argv)
    regions = {}
    sizes = {}
    for path in sorted(glob.glob(os.path.join(args.packs_dir, "osm-*.json"))):
        name = os.path.basename(path)[len("osm-"):-len(".json")]
        if name == BUILT_IN or name.startswith("change-positions"):
            continue
        with open(path, encoding="utf-8") as handle:
            pack = json.load(handle)
        regions[name] = sorted(cells_of(pack))
        sizes[name] = os.path.getsize(path)
    index = {"cell": CELL, "regions": {name: [list(c) for c in cells] for name, cells in regions.items()}}
    out = os.path.join(args.packs_dir, "index.json")
    with open(out, "w", encoding="utf-8") as handle:
        json.dump(index, handle, separators=(",", ":"))
    print("%s: %d packs, %d bytes" % (out, len(regions), os.path.getsize(out)))
    for name in sorted(regions):
        print("  %-24s %5d cells  %8.2f MB" % (name, len(regions[name]), sizes[name] / 1e6))
    if args.install:
        os.makedirs(args.app_dir, exist_ok=True)
        changes_total = 0
        for name in sorted(regions):
            shutil.copyfile(os.path.join(args.packs_dir, "osm-%s.json" % name),
                            os.path.join(args.app_dir, "osm-%s.json" % name))
            changes = "osm-change-positions-%s.ndjson" % name
            source = os.path.join(args.packs_dir, changes)
            target = os.path.join(args.app_dir, changes)
            if os.path.isfile(source) and os.path.getsize(source) > 0:
                shutil.copyfile(source, target)
                changes_total += os.path.getsize(source)
                print("  %-24s change positions %8.2f MB" % (name, os.path.getsize(source) / 1e6))
            elif os.path.isfile(target):
                # The pack has none any more: an old file would give outdated hints.
                os.remove(target)
        shutil.copyfile(out, os.path.join(args.app_dir, "index.json"))
        print("Change positions: %.2f MB" % (changes_total / 1e6))
        print("Installed into %s" % os.path.relpath(args.app_dir))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
