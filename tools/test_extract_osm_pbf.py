#!/usr/bin/env python3
"""
Self-test of tools/extract_osm_pbf.py on a small synthetic OSM file (no download needed):
a U-Bahn station node, an S-Bahn station tagged station=train + light_rail=yes inside a
second drawing of itself and a station building, a halt that only its stop area makes an
S-Bahn station, platforms (a way and a multipolygon), stop positions, steps, an escalator,
an elevator, footways, entrances and doors, bus and tram stops with their stop area, and
rail, bus and tram routes in and outside the region. Writes the file as .osm and .osm.pbf,
runs the extractor on both and checks the result. With a real tools/osm-stations.json from
tools/fetch_osm_stations.py present (or --compare FILE), also checks that every key of it
appears with the same type in the extract.

    python3 tools/test_extract_osm_pbf.py [--compare tools/osm-stations.json]
"""

import json
import math
import os
import sys
import tempfile
import time
from xml.sax.saxutils import quoteattr

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import extract_osm_pbf as ex  # noqa: E402
import osmium  # noqa: E402

ORIGIN = (52.60, 13.60)


def at(north, east):
    """A point [north] and [east] metres from ORIGIN."""
    lat = ORIGIN[0] + north / 111320.0
    lon = ORIGIN[1] + east / (111320.0 * math.cos(math.radians(ORIGIN[0])))
    return round(lat, 7), round(lon, 7)


class Data(object):
    def __init__(self):
        self.nodes, self.ways, self.relations = [], [], []

    def node(self, nid, point, **tags):
        self.nodes.append((nid, point, tags))

    def way(self, wid, refs, **tags):
        self.ways.append((wid, refs, tags))

    def relation(self, rid, members, **tags):
        self.relations.append((rid, members, tags))

    def xml(self):
        def tag_lines(tags):
            return "".join('  <tag k=%s v=%s/>\n' % (quoteattr(k.replace("__", ":")), quoteattr(v))
                           for k, v in sorted(tags.items()))
        out = ['<?xml version="1.0" encoding="UTF-8"?>\n<osm version="0.6" generator="test">\n']
        common = 'version="1" timestamp="2026-01-01T00:00:00Z"'
        for nid, (lat, lon), tags in sorted(self.nodes, key=lambda n: n[0]):
            out.append('<node id="%d" %s lat="%.7f" lon="%.7f">\n%s</node>\n' % (nid, common, lat, lon, tag_lines(tags)))
        for wid, refs, tags in sorted(self.ways, key=lambda w: w[0]):
            nds = "".join('  <nd ref="%d"/>\n' % r for r in refs)
            out.append('<way id="%d" %s>\n%s%s</way>\n' % (wid, common, nds, tag_lines(tags)))
        for rid, members, tags in sorted(self.relations, key=lambda r: r[0]):
            ms = "".join('  <member type="%s" ref="%d" role=%s/>\n' % (t, r, quoteattr(role)) for t, r, role in members)
            out.append('<relation id="%d" %s>\n%s%s</relation>\n' % (rid, common, ms, tag_lines(tags)))
        out.append("</osm>\n")
        return "".join(out)


def square(data, first_id, center, half):
    """Four corner nodes around [center] (north, east in metres); returns the closed ring of ids."""
    n, e = center
    corners = [(n - half, e - half), (n - half, e + half), (n + half, e + half), (n + half, e - half)]
    for i, (cn, ce) in enumerate(corners):
        data.node(first_id + i, at(cn, ce))
    return [first_id, first_id + 1, first_id + 2, first_id + 3, first_id]


def synthetic():
    d = Data()
    # Stations.
    d.node(1, at(0, 0), railway="station", station="subway", subway="yes", public_transport="station", name="Testplatz")
    d.node(2, at(0, 2000), railway="station", station="train", light_rail="yes", train="yes",
           public_transport="station", name="Musterbahnhof")
    d.node(3, at(2000, 0), railway="halt", name="Waldhalt")
    d.node(4, at(-1000, 0), railway="station", station="miniature", train="yes", name="Parkbahn")
    d.node(5, at(-1000, 500), public_transport="station", amenity="bus_station", bus="yes", name="Busbahnhof")
    d.node(6, (53.2, 13.6), railway="station", station="subway", name="Far away")
    # An operating point mapped as a station: trains alone, no platform, no stop position.
    d.node(7, at(3000, -3000), railway="station", train="yes", name="Abzweig Testkreuz")
    # Station building of Musterbahnhof with a door on its outline, one inside, and a house door.
    d.way(1001, square(d, 101, (0, 2000), 60), building="train_station")
    d.node(105, at(-60, 2000), entrance="main", wheelchair="yes")
    d.way(1003, [101, 105, 102], highway="footway")  # puts the door into the way chain, unused
    d.node(106, at(10, 1990), entrance="yes", ref="7")
    d.node(107, at(0, 2100), entrance="yes", ref="3", **{"addr__housenumber": "1"})
    # Musterbahnhof drawn a second time, as an area.
    d.way(1002, square(d, 111, (0, 2000), 80), railway="station", public_transport="station",
          name="Berlin Musterbahnhof")
    # Around Testplatz.
    d.node(120, at(50, 50), railway="subway_entrance", name="Testplatz Nord", level="0")
    d.node(130, at(-20, -40))
    d.node(131, at(-20, 40))
    d.way(1010, [130, 131], railway="platform", public_transport="platform", level="-1", ref="1")
    d.node(132, at(-20, 0), public_transport="stop_position", railway="stop", subway="yes", name="Testplatz")
    d.node(133, at(30, 0), highway="elevator", level="-1;0")
    d.node(134, at(10, 10))
    d.node(135, at(10, 30))
    d.way(1011, [134, 135], highway="steps", level="-1;0")
    d.node(136, at(15, 10))
    d.node(137, at(15, 30))
    d.way(1012, [136, 137], highway="footway", conveying="forward")
    d.way(1016, [134, 136], highway="steps", conveying="yes")  # steps first, as kind_of says
    d.node(144, at(40, 5))
    d.node(145, at(40, 8))
    d.way(1017, [144, 145], highway="elevator")
    d.node(138, at(-10, -10))
    d.node(139, at(-10, 60))
    d.node(148, at(-10, 25), entrance="yes")  # a door on the footway, with no other tag
    d.way(1013, [138, 148, 139], highway="footway", level="-1")
    d.node(149, at(60, -60), entrance="yes")  # a bare door off the network: not kept
    d.node(140, at(5, -10))
    d.node(141, at(5, 20))
    d.way(1014, [140, 141], highway="footway")
    d.node(142, at(-1000, -1000))
    d.node(143, at(-1000, -980))
    d.way(1015, [142, 143], highway="footway", level="-1")
    d.node(146, at(-5, -10))
    d.node(147, at(-5, 30))
    d.way(1018, [146, 147], highway="corridor", level="-1")  # an indoor corridor, kept as a footway
    # A corridor on from it to a door 240 m away (a mall): the door leads into Testplatz.
    d.node(157, at(-5, 240), entrance="main")
    d.way(1019, [147, 157], highway="corridor", level="-1")
    d.relation(2011, [("n", 1, "station"), ("w", 1010, "platform"), ("n", 132, "stop")],
               type="public_transport", public_transport="stop_area", name="U Testplatz")
    # Musterbahnhof: a multipolygon platform from an untagged way, a stop position.
    d.way(1020, square(d, 150, (20, 2000), 10))
    d.relation(2001, [("w", 1020, "outer")], type="multipolygon", railway="platform", public_transport="platform")
    d.node(154, at(20, 2000), public_transport="stop_position", railway="stop", train="yes", light_rail="yes")
    # Waldhalt: a stop position and its stop area.
    d.node(160, at(2010, 0), public_transport="stop_position", railway="stop", train="yes")
    d.relation(2010, [("n", 3, "station"), ("n", 160, "stop")], type="public_transport",
               public_transport="stop_area", name="S Waldhalt")
    # Rail routes: an S-Bahn line with stops, an old route of tracks only through the region,
    # and one far away.
    d.node(170, at(100, 2000))
    d.node(171, at(1900, 0))
    d.way(1030, [170, 171], railway="light_rail")
    d.relation(2020, [("n", 154, "stop"), ("w", 1030, ""), ("n", 160, "stop")], type="route",
               route="light_rail", ref="S99", name="S99: Musterbahnhof => Waldhalt")
    d.node(172, at(-3000, -3000))
    d.node(173, at(-3000, 3000))
    d.way(1031, [172, 173], railway="rail")
    d.relation(2021, [("w", 1031, "")], type="route", route="train", ref="RE99")
    d.node(174, (53.2, 13.0))
    d.node(175, (53.2, 13.1))
    d.way(1032, [174, 175], railway="rail")
    d.relation(2022, [("w", 1032, "")], type="route", route="train", ref="RE98")
    # A station drawn only as a multipolygon relation.
    d.way(1050, square(d, 190, (2000, 2000), 50))
    d.relation(2040, [("w", 1050, "outer")], type="multipolygon", railway="station", station="light_rail",
               public_transport="station", name="Ringhalt")
    # Bus and tram.
    d.node(180, at(-500, 1000), public_transport="stop_position", bus="yes", name="Testweg", local_ref="1", ref="1",
           **{"ref__BVG": "123456", "ref__IFOPT": "de:11000:900999999::1"})
    d.node(181, at(-505, 1000), highway="bus_stop", public_transport="platform", bus="yes", name="Testweg")
    d.node(182, at(-505, 1010))
    d.node(183, at(-505, 1030))
    d.way(1040, [182, 183], public_transport="platform", bus="yes", name="Testweg")
    d.relation(2030, [("n", 180, "stop"), ("n", 181, "platform"), ("w", 1040, "platform")],
               type="public_transport", public_transport="stop_area", name="Testweg")
    d.node(184, at(-500, 900))
    d.node(185, at(-500, 1100))
    d.way(1041, [184, 185], highway="residential")
    d.node(186, (53.2, 13.2), public_transport="stop_position", bus="yes")
    d.relation(2031, [("n", 180, "stop"), ("w", 1040, "platform"), ("w", 1041, ""), ("n", 186, "stop_exit_only")],
               type="route", route="bus", ref="999", name="Bus 999", colour="#A5027D", network="VBB",
               operator="Testbetrieb")
    d.relation(2032, [("n", 186, "stop")], type="route", route="bus", ref="998")
    d.node(187, at(-800, 1200), railway="tram_stop", name="Teststraße")
    d.relation(2033, [("n", 187, "stop")], type="route", route="tram", ref="M99")
    return d


def check(result):
    problems = []

    def expect(condition, message):
        if not condition:
            problems.append(message)

    expect(set(result) >= {"license", "generated", "radiusMeters", "stations", "stopAreas", "routes",
                           "entrances", "stopPositions", "busRoutes"}, "top-level keys")
    stations = {s["id"]: s for s in result["stations"]}
    expect(sorted(stations) == [1, 2, 3, 2040], "stations: %s" % sorted(stations))
    if sorted(stations) == [1, 2, 3, 2040]:
        r = stations[2040]
        expect(r["type"] == "relation" and r["modes"] == ["light_rail"] and [r["lat"], r["lon"]] == list(ex.bbox_center([at(1950, 1950), at(2050, 2050)])), "relation station %s" % r)
        a, b, c = stations[1], stations[2], stations[3]
        expect(a["modes"] == ["subway"], "Testplatz modes %s" % a["modes"])
        expect(b["modes"] == ["light_rail", "train"], "Musterbahnhof modes %s" % b["modes"])
        expect(c["modes"] == ["light_rail"], "Waldhalt modes %s" % c["modes"])
        ids = lambda items: sorted(i["id"] for i in items)
        expect(ids(a["platforms"]) == [1010], "Testplatz platforms %s" % ids(a["platforms"]))
        expect(ids(a["stop_positions"]) == [132], "Testplatz stop positions")
        expect(ids(a["steps"]) == [1011, 1016], "Testplatz steps %s" % ids(a["steps"]))
        expect(ids(a["escalators"]) == [1012], "Testplatz escalators")
        expect(ids(a["elevators"]) == [133, 1017], "Testplatz elevators")
        expect(ids(a["footways"]) == [1013, 1018, 1019], "Testplatz footways %s" % ids(a["footways"]))
        expect(ids(b["platforms"]) == [2001], "Musterbahnhof platforms %s" % ids(b["platforms"]))
        if b["platforms"]:
            g = b["platforms"][0].get("geometry")
            expect(isinstance(g, list) and g[0]["role"] == "outer" and len(g[0]["points"]) == 5,
                   "multipolygon platform geometry %s" % g)
        expect(ids(b["stop_positions"]) == [154], "Musterbahnhof stop positions")
        expect(a["platforms"][0]["geometry"] == [list(at(-20, -40)), list(at(-20, 40))], "way geometry")
        expect([a["lat"], a["lon"]] == list(at(0, 0)), "node position")
    entrances = {e["id"]: e for e in result["entrances"]}
    expect(sorted(entrances) == [105, 106, 107, 120, 148, 157], "entrances %s" % sorted(entrances))
    if 148 in entrances and 157 in entrances:
        expect(entrances[148]["stations"] == ["node/1"], "bare door on a footway %s" % entrances[148]["stations"])
        expect(entrances[148].get("viaNetwork") is True and entrances[157].get("viaNetwork") is True, "viaNetwork")
        expect(entrances[157]["stations"] == ["node/1"], "door at the end of a corridor %s" % entrances[157]["stations"])
    if 120 in entrances:
        expect(entrances[120]["stations"] == ["node/1"] and "stationBuilding" not in entrances[120], "subway entrance")
    if 105 in entrances:
        expect(entrances[105]["stationBuilding"] is True and entrances[105]["wheelchair"] == "yes"
               and "viaNetwork" not in entrances[105], "door on outline")
    if 106 in entrances:
        expect(entrances[106]["stationBuilding"] is True, "door inside the building")
    if 107 in entrances:
        expect(entrances[107]["stationBuilding"] is False and entrances[107]["stations"] == ["node/2"], "house door")
    expect(sorted(r["id"] for r in result["routes"]) == [2020, 2021], "rail routes %s" % [r["id"] for r in result["routes"]])
    bus = {r["id"]: r for r in result["busRoutes"]}
    expect(sorted(bus) == [2031, 2033], "bus routes %s" % sorted(bus))
    if 2031 in bus:
        r = bus[2031]
        expect(r["route"] == "bus" and r["ref"] == "999" and r["colour"] == "#A5027D", "bus route tags")
        expect([m[:3] for m in r["m"]] == [["n", 180, "stop"], ["w", 1040, "platform"], ["n", 186, "stop_exit_only"]],
               "bus route members %s" % r["m"])
        expect(r["m"][1][3:] == list(ex.bbox_center([at(-505, 1010), at(-505, 1030)])), "platform way centre")
        expect(r["m"][2][3:] == [53.2, 13.2], "member outside the region keeps its position")
    stops = {(s["type"], s["id"]): s for s in result["stopPositions"]}
    expect(sorted(stops) == [("node", 180), ("node", 181), ("node", 187), ("way", 1040)], "stops %s" % sorted(stops))
    if ("node", 180) in stops:
        s = stops[("node", 180)]
        expect(s["kind"] == "stop_position" and s["bus"] and not s["tram"] and s["local_ref"] == "1"
               and s["ref:BVG"] == "123456" and s["stopAreas"] == [2030], "bus stop position %s" % s)
    if ("node", 187) in stops:
        expect(stops[("node", 187)]["tram"] and stops[("node", 187)]["kind"] == "platform", "tram stop")
    expect(sorted(a["id"] for a in result["stopAreas"]) == [2010, 2011, 2030], "stop areas %s"
           % sorted(a["id"] for a in result["stopAreas"]))
    return problems


def shape(value, path, shapes):
    """Every key path of [value] with the JSON types found there."""
    kind = type(value).__name__
    if kind in ("int", "float"):
        kind = "number"
    shapes.setdefault(path, set()).add(kind)
    if isinstance(value, dict):
        # The tags are free; only their being a dict of strings counts.
        if path.endswith(".tags"):
            for v in value.values():
                shapes.setdefault(path + ".*", set()).add(type(v).__name__)
            return
        for k, v in value.items():
            shape(v, path + "." + k, shapes)
    elif isinstance(value, list):
        for v in value:
            shape(v, path + "[]", shapes)


def compare(real_path, result):
    for attempt in range(5):
        try:
            with open(real_path, "r", encoding="utf-8") as handle:
                real = json.load(handle)
            break
        except ValueError:
            print("  %s is being written, waiting" % real_path)
            time.sleep(10)
    else:
        return ["could not read %s" % real_path]
    theirs, ours = {}, {}
    shape(real, "", theirs)
    shape(result, "", ours)
    problems = []
    for path in sorted(theirs):
        if path not in ours:
            problems.append("missing in the extract: %s" % path)
        elif not theirs[path] <= ours[path] | {"NoneType"}:
            problems.append("other type at %s: %s vs %s" % (path, sorted(theirs[path]), sorted(ours[path])))
    extra = sorted(p for p in ours if p not in theirs)
    print("  keys only in the extract: %s" % ", ".join(extra))
    return problems


def main():
    args = sys.argv[1:]
    real = os.path.join(HERE, "osm-stations.json")
    if args[:1] == ["--compare"] and len(args) == 2:
        real = args[1]
    with tempfile.TemporaryDirectory() as tmp:
        xml = os.path.join(tmp, "test.osm")
        pbf = os.path.join(tmp, "test.osm.pbf")
        with open(xml, "w", encoding="utf-8") as handle:
            handle.write(synthetic().xml())
        with osmium.SimpleWriter(pbf) as writer:
            for obj in osmium.FileProcessor(xml):
                writer.add(obj)
        results = []
        for path, extra in ((xml, []), (pbf, []), (pbf, ["--index", "sparse_file_array," + os.path.join(tmp, "idx")])):
            out = os.path.join(tmp, "out.json")
            print("== %s %s" % (os.path.basename(path), " ".join(extra)))
            ex.main([path, "--out", out] + extra)
            with open(out, "r", encoding="utf-8") as handle:
                results.append(json.load(handle))
        problems = check(results[1])
        strip = lambda r: {k: v for k, v in r.items() if k not in ("generated", "source")}
        if strip(results[0]) != strip(results[1]) or strip(results[1]) != strip(results[2]):
            problems.append(".osm, .osm.pbf and the file index give different results")
        if os.path.exists(real):
            print("== schema against %s" % real)
            problems += compare(real, results[1])
        else:
            print("== no %s to compare the schema with" % real)
    print("")
    if problems:
        for p in problems:
            print("FAIL: %s" % p)
        return 1
    print("All checks passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
