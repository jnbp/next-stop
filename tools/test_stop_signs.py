#!/usr/bin/env python3
"""
Self-test of the platforms of a stop and of where its sign stands (no download, no OSM file),
on the stop area "Berlin, Wolliner Strasse" as mapped in OpenStreetMap (November 2026):

- tools/build_station_asset.py makes each of its four platforms (two tram platforms on
  Bernauer Strasse, two bus platforms on both sides of Wolliner Strasse, 17 m apart) a position
  of its own with its line, and merges each stop position into the platform a route lists it
  with: the two bus stop positions, 2.6 m apart on the road, go to their own platforms, not
  into one dot (before: one position with the 247 at the road, the east platform an empty dot);
- a stop position without a platform stays a position and takes the line of its route;
- a platform node of no route beside a platform with lines (the same platform mapped twice),
  and a stop position of no route and no platform where the stop has platforms of its mode,
  are no empty dots of their own;
- tools/stop_sign.py moves the provider's point of the stop (36 m from the nearest platform,
  south-east of the crossing in a block of houses) to the middle of the platforms, keeps a
  point within 30 m of them, and never puts a sign farther than 30 m from a position.

    python3 tools/test_stop_signs.py
"""

import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import build_station_asset as b  # noqa: E402
import stop_sign  # noqa: E402

AREA = 5824758


def element(kind, osm_type, osm_id, lat, lon, bus, name="Wolliner Straße", areas=(AREA,), **tags):
    tags = dict(tags, name=name)
    return {"type": osm_type, "id": osm_id, "kind": kind, "lat": lat, "lon": lon, "bus": bus, "tram": not bus,
            "name": name, "stopAreas": list(areas), "tags": tags}


def wolliner():
    positions = [
        element("stop_position", "node", 1837885483, 52.5396897, 13.4012039, False),
        element("stop_position", "node", 1837885485, 52.5399966, 13.4020576, False),
        element("stop_position", "node", 3925729386, 52.5400631, 13.4013771, True),
        element("stop_position", "node", 3925729387, 52.5400842, 13.4013613, True),
        element("platform", "way", 389400771, 52.5400793, 13.4021201, False),
        element("platform", "way", 389400772, 52.5396116, 13.4012188, False),
        element("platform", "way", 389400773, 52.5400786, 13.4012564, True),
        element("platform", "way", 389400774, 52.5400453, 13.4015002, True),
    ]
    routes = [
        {"id": 2077031, "route": "tram", "ref": "M10", "m": [["n", 1837885483, "stop", 52.5396897, 13.4012039],
                                                             ["w", 389400772, "platform", 52.5396116, 13.4012188]]},
        {"id": 2077032, "route": "tram", "ref": "M10", "m": [["n", 1837885485, "stop", 52.5399966, 13.4020576],
                                                             ["w", 389400771, "platform", 52.5400793, 13.4021201]]},
        {"id": 2342068, "route": "bus", "ref": "247", "m": [["n", 3925729386, "stop", 52.5400631, 13.4013771],
                                                            ["w", 389400774, "platform", 52.5400453, 13.4015002]]},
        {"id": 2342069, "route": "bus", "ref": "247", "m": [["n", 3925729387, "stop", 52.5400842, 13.4013613],
                                                            ["w", 389400773, "platform", 52.5400786, 13.4012564]]},
    ]
    area = {"id": AREA, "tags": {"name": "Berlin, Wolliner Straße", "public_transport": "stop_area",
                                 "website": "https://fahrinfo.vbb.de/bin/stboard.exe/dn?start=yes&input=900007105"}}
    return {"stopAreas": [area], "routes": [], "busRoutes": routes, "stopPositions": positions}


def check_platforms(problems):
    areas = b.build_stop_areas(wolliner())
    if len(areas) != 1:
        problems.append("Wolliner Strasse: %d areas" % len(areas))
        return
    got = sorted((round(p[0], 6), round(p[1], 6), p[2], tuple(sorted((p[4] if len(p) > 4 else {}).items())))
                 for p in areas[0]["p"])
    want = sorted([
        (52.540079, 13.40212, "t", (("t", ["M10"]),)),
        (52.539612, 13.401219, "t", (("t", ["M10"]),)),
        (52.540079, 13.401256, "b", (("b", ["247"]),)),
        (52.540045, 13.4015, "b", (("b", ["247"]),)),
    ])
    if got != want:
        problems.append("Wolliner Strasse positions:\n    got  %r\n    want %r" % (got, want))


def check_lone_stop_position(problems):
    # A stop position with no platform within 25 m keeps its place and takes its route's line.
    data = wolliner()
    data["stopPositions"] = [element("stop_position", "node", 7, 52.5, 13.4, True, name="Wendeschleife", areas=())]
    data["busRoutes"] = [{"id": 1, "route": "bus", "ref": "123", "m": [["n", 7, "stop", 52.5, 13.4]]}]
    data["stopAreas"] = []
    areas = b.build_stop_areas(data)
    if [a["p"] for a in areas] != [[[52.5, 13.4, "b", "", {"b": ["123"]}]]]:
        problems.append("lone stop position: %r" % areas)


def check_bare_twin(problems):
    # A highway=bus_stop node of no route 12 m from the platform way the 247 lists (the same
    # platform mapped twice) folds into it instead of being an empty dot beside it.
    data = wolliner()
    data["stopPositions"].append(element("platform", "node", 99, 52.5400453 + 12 / 111320.0, 13.4015002, True,
                                         areas=(), highway="bus_stop"))
    # And a stop position of the area 60 m down the road, of no route and no platform: no dot.
    data["stopPositions"].append(element("stop_position", "node", 98, 52.5400453 + 60 / 111320.0, 13.4015002, True))
    areas = b.build_stop_areas(data)
    if len(areas) != 1 or len(areas[0]["p"]) != 4 or any(len(p) < 5 for p in areas[0]["p"]):
        problems.append("bare twin of a platform: %r" % areas)


def check_numbered_island(problems):
    # Two numbered stop positions on both sides of one island platform: the platform takes the
    # first number, the other keeps its own position (its number is the stand riders look for).
    platform = element("platform", "way", 10, 52.5, 13.4, False, areas=(1,))
    one = element("stop_position", "node", 11, 52.50005, 13.4, False, areas=(1,), local_ref="1")
    two = element("stop_position", "node", 12, 52.49995, 13.4, False, areas=(1,), local_ref="2")
    one["local_ref"], two["local_ref"] = "1", "2"
    kept = b.area_positions([platform, one, two], {})
    refs = sorted(k["ref"] for k in kept)
    if refs != ["1", "2"] or len(kept) != 2:
        problems.append("island platform with two numbers: %r" % refs)


def check_sign(problems):
    platforms = [(52.5400793, 13.4021201), (52.5396116, 13.4012188), (52.5400786, 13.4012564), (52.5400453, 13.4015002)]
    provider = (52.539774, 13.401802)  # the VBB's point of 900007105
    sign = stop_sign.sign_point(provider, platforms)
    nearest = min(stop_sign.meters(sign, p) for p in platforms)
    if sign == provider or nearest > 30.0:
        problems.append("Wolliner Strasse sign at %r, %.0f m from the nearest platform" % (sign, nearest))
    close = (52.54000, 13.40150)
    if stop_sign.sign_point(close, platforms) != close:
        problems.append("a provider point beside the platforms was moved")
    # Two directions 150 m apart: the sign stands between them, at most 30 m from one.
    apart = [(52.5, 13.4), (52.5 + 150 / 111320.0, 13.4)]
    sign = stop_sign.sign_point((52.5, 13.41), apart)
    if min(stop_sign.meters(sign, p) for p in apart) > 30.001:
        problems.append("sign between two far positions: %r" % (sign,))
    # Positions of one id at both ends of a village: only those near the provider's point count.
    village = [(52.5, 13.4), (52.5 + 0.0001, 13.4), (52.51, 13.4)]
    sign = stop_sign.sign_point((52.4995, 13.4), village)
    if stop_sign.meters(sign, village[0]) > 30:
        problems.append("sign pulled towards the far end of a village: %r" % (sign,))


def main():
    problems = []
    check_platforms(problems)
    check_lone_stop_position(problems)
    check_bare_twin(problems)
    check_numbered_island(problems)
    check_sign(problems)
    if problems:
        print("FAILED:")
        for p in problems:
            print("  " + p)
        return 1
    print("OK: every platform a position with its lines, stop positions merged, signs beside their positions")
    return 0


if __name__ == "__main__":
    sys.exit(main())
