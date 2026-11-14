#!/usr/bin/env python3
"""
Self-test of the stop positions of rural bus stops (no download, no OSM file):

- tools/extract_osm_pbf.py takes highway=platform and highway=bus_stop (node or way, no
  bus=yes, no public_transport) as bus stops, but not a platform tagged as rail;
- tools/build_station_asset.py makes two loose platforms of one name on both sides of a
  village (about 100 m apart, no stop area) one stop with two positions, keeps a position of
  that name 400 m away as a stop of its own, joins an unnamed platform to the stop beside it
  and leaves a lone unnamed one out; a member of a stop area far from the rest of it (a stop
  area of a whole town) stays a stop of its own name;
- tools/regions.py places a stop without a DHID only in a state whose box holds it, and the
  state builds keep a place near a border in both packs (StateLocator.near_state).

    python3 tools/test_stop_positions.py
"""

import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import build_station_asset as b  # noqa: E402
import regions as rg  # noqa: E402

ORIGIN = (51.885, 13.458)


def at(north, east, origin=ORIGIN):
    lat = origin[0] + north / 111320.0
    lon = origin[1] + east / (111320.0 * math.cos(math.radians(origin[0])))
    return round(lat, 7), round(lon, 7)


def platform(pid, point, name=None, **tags):
    tags = dict(tags)
    if name:
        tags["name"] = name
    stop = {"type": "node", "id": pid, "kind": "platform", "lat": point[0], "lon": point[1],
            "bus": True, "tram": False, "stopAreas": [], "tags": tags}
    if name:
        stop["name"] = name
    return stop


def check_extract_tags(problems):
    try:
        import extract_osm_pbf as ex
    except ImportError as error:  # pyosmium missing: the build checks still run
        print("  (extract rules skipped: %s)" % error)
        return
    cases = [
        ({"highway": "platform", "name": "Dorf"}, True, True),
        ({"highway": "bus_stop", "name": "Dorf"}, True, True),
        ({"public_transport": "platform", "bus": "yes"}, True, True),
        ({"highway": "platform", "tram": "yes"}, True, False),
        ({"highway": "platform", "railway": "platform"}, False, None),
        ({"highway": "platform", "train": "yes"}, False, None),
        ({"public_transport": "platform"}, False, None),
    ]
    for tags, stop, bus in cases:
        if ex.is_stop_node(tags) != stop or ex.is_stop_way(tags) != stop:
            problems.append("stop rule for %r: node %s, way %s" % (tags, ex.is_stop_node(tags), ex.is_stop_way(tags)))
        if stop and ex.is_bus_stop_tags(tags) != bus:
            problems.append("bus rule for %r" % tags)


def check_rural_stop(problems):
    data = {"stopAreas": [], "routes": [], "busRoutes": [], "stopPositions": [
        # Both directions of the village stop on the main road, 105 m apart (as Zagelsdorf on the L71).
        platform(1, at(-40, 30), "Dorf", highway="bus_stop"),
        platform(2, at(40, -40), "Dorf", highway="bus_stop"),
        # Its unnamed twin 30 m along the road: a position of it.
        platform(3, at(-40, 60), None, highway="platform"),
        # The same name at the far end of a long village: a stop of its own (one more area).
        platform(4, at(400, 300), "Dorf", highway="bus_stop"),
        # An unnamed platform in the fields: nobody's, left out.
        platform(5, at(-2000, 0), None, highway="bus_stop"),
    ]}
    areas = b.build_stop_areas(data)
    dorf = sorted((a for a in areas if a["n"] == "Dorf"), key=lambda a: -len(a["p"]))
    if len(dorf) != 2 or len(dorf[0]["p"]) != 3 or len(dorf[1]["p"]) != 1:
        problems.append("village stop areas: %r" % areas)
    if sum(len(a["p"]) for a in areas) != 4:
        problems.append("the lone unnamed platform was kept: %r" % areas)


def check_town_wide_area(problems):
    # A "stop area" of a whole town: two stops 3 km apart in one relation. The far one keeps
    # its position as a stop of its own name instead of being dropped as a stray.
    near_a = platform(11, at(0, 0), "Markt")
    near_b = platform(12, at(20, 0), "Markt")
    far = platform(13, at(3000, 0), "Waldweg")
    for item in (near_a, near_b, far):
        item["stopAreas"] = [77]
    data = {"stopAreas": [{"id": 77, "tags": {"name": "Stadt", "public_transport": "stop_area"}}],
            "routes": [], "busRoutes": [], "stopPositions": [near_a, near_b, far]}
    areas = b.build_stop_areas(data)
    names = sorted((a["n"], len(a["p"])) for a in areas)
    if names != [("Markt", 2), ("Waldweg", 1)]:
        problems.append("town-wide stop area: %r" % names)


def check_states(problems):
    locator = rg.StateLocator()
    # A Hessian stop with a DHID 1 km west of a Thuringian village just outside the box of
    # Hessen (east edge 10.24), and a Thuringian stop 6 km east of it.
    village = (50.98, 10.30)
    locator.add(50.98, 10.30 - 1000 / 70000.0, "06")
    locator.add(50.98, 10.30 + 6000 / 70000.0, "16")
    if locator.locate(*village) != "16":
        problems.append("a place outside the box of Hessen located in Hessen")
    # Inside both boxes: the nearest DHID decides, and the band keeps it on both sides.
    inside = (50.98, 10.10)
    locator.add(50.98, 10.10 - 1000 / 70000.0, "06")
    locator.add(50.98, 10.10 + 2500 / 70000.0, "16")
    if locator.locate(*inside) != "06":
        problems.append("nearest DHID inside both boxes")
    if not (locator.near_state(*inside, state="06", band_meters=2000)
            and locator.near_state(*inside, state="16", band_meters=2000)):
        problems.append("a place near the border not kept by both states")
    if locator.near_state(*inside, state="16", band_meters=500):
        problems.append("the band of 500 m kept a place 2 km beyond it")
    if locator.near_state(*inside, state="16", band_meters=2000, code="06"):
        problems.append("a place with its own DHID kept by another state")
    # A DHID of a state far away (de:05156 of Nordrhein-Westfalen on a stop in the Harz) is a
    # mapping error: the place is located as one without DHID.
    harz = (51.758, 10.306)
    locator.add(51.758, 10.29, "03")
    if locator.locate(*harz, code="05") != "03" or not locator.near_state(*harz, state="03", band_meters=0, code="05"):
        problems.append("a DHID of a far state decided the state")


def main():
    problems = []
    check_extract_tags(problems)
    check_rural_stop(problems)
    check_town_wide_area(problems)
    check_states(problems)
    if problems:
        print("FAILED:")
        for p in problems:
            print("  " + p)
        return 1
    print("OK: road platforms, village stops with spread and unnamed positions, states near borders")
    return 0


if __name__ == "__main__":
    sys.exit(main())
