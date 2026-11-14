#!/usr/bin/env python3
"""
Self-test of tools/build_station_asset.py outside Berlin, on a small synthetic extract in
the format of tools/osm-stations.json (no download, no pyosmium): in Cologne a Stadtbahn
station (light_rail routes "16" and "18" of the KVB) with tram stops in its stop area, an
S-Bahn station (route=train with ref "S 11") that regional and long-distance trains also
serve, a Stadtbahn station that no route reaches (operator Koelner Verkehrs-Betriebe); in
Stuttgart a Stadtbahn station of the lines U6 and U12 (light_rail with U refs); in Munich
an U-Bahn station; all with DHIDs (ref:IFOPT), and bus stops with and without stop area.
Builds Nordrhein-Westfalen, then Germany with the per-state packs, into a temporary folder and checks the
kinds, products, ids, the cut by state and the packs. Also checks route_kind on its own.

    python3 tools/test_build_station_asset.py
"""

import json
import math
import os
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import build_station_asset as b  # noqa: E402
import regions as rg  # noqa: E402
import fetch_osm_stations as ov  # noqa: E402


def offset(origin, north, east):
    lat = origin[0] + north / 111320.0
    lon = origin[1] + east / (111320.0 * math.cos(math.radians(origin[0])))
    return round(lat, 7), round(lon, 7)


class Extract(object):
    def __init__(self):
        self.next_id = 1000
        self.stations, self.areas, self.routes, self.stops, self.bus_routes = [], [], [], [], []

    def nid(self):
        self.next_id += 1
        return self.next_id

    def station(self, name, origin, modes, tags=None, ifopt=None, platforms=1):
        tags = dict(tags or {}, name=name, railway="station")
        if ifopt:
            tags["ref:IFOPT"] = ifopt
        station = {"type": "node", "id": self.nid(), "name": name, "lat": origin[0], "lon": origin[1],
                   "tags": tags, "modes": modes, "platforms": [], "stop_positions": [], "steps": [],
                   "elevators": [], "escalators": [], "footways": []}
        for i in range(platforms):
            a = offset(origin, -60, 10 + 20 * i)
            z = offset(origin, 60, 10 + 20 * i)
            station["platforms"].append({"type": "way", "id": self.nid(), "geometry": [list(a), list(z)],
                                         "tags": {"public_transport": "platform", "railway": "platform",
                                                  "ref": str(i + 1), "ref:IFOPT": "%s:1:%d" % (ifopt, i + 1) if ifopt else ""}})
            p = offset(origin, 0, 10 + 20 * i)
            station["stop_positions"].append({"type": "node", "id": self.nid(), "lat": p[0], "lon": p[1],
                                              "tags": {"public_transport": "stop_position", "name": name}})
        self.stations.append(station)
        return station

    def area(self, name, members, ifopt=None, **tags):
        tags = dict(tags, name=name, public_transport="stop_area", type="public_transport")
        if ifopt:
            tags["ref:IFOPT"] = ifopt
        area = {"id": self.nid(), "tags": tags,
                "members": [{"type": m["type"], "ref": m["id"], "role": ""} for m in members]}
        self.areas.append(area)
        return area

    def route(self, members, **tags):
        tags = dict(tags, type="route")
        self.routes.append({"id": self.nid(), "tags": tags,
                            "members": [{"type": m["type"], "ref": m["id"], "role": role} for m, role in members]})

    def stop(self, name, point, tram=False, area=None, ifopt=None, local_ref=None):
        tags = {"public_transport": "stop_position", "name": name, ("tram" if tram else "bus"): "yes"}
        if ifopt:
            tags["ref:IFOPT"] = ifopt
        stop = {"type": "node", "id": self.nid(), "kind": "stop_position", "lat": point[0], "lon": point[1],
                "bus": not tram, "tram": tram, "name": name, "stopAreas": [area["id"]] if area else [], "tags": tags}
        if ifopt:
            stop["ref:IFOPT"] = ifopt
        if local_ref:
            stop["local_ref"] = local_ref
            tags["local_ref"] = local_ref
        self.stops.append(stop)
        if area:
            area["members"].append({"type": "node", "ref": stop["id"], "role": "stop"})
        return stop

    def json(self):
        return {"license": "test", "generated": "2026-01-01T00:00:00Z", "radiusMeters": 150,
                "stations": self.stations, "stopAreas": self.areas, "routes": self.routes, "entrances": [],
                "stopPositions": self.stops, "busRoutes": self.bus_routes,
                "source": {"region": "germany", "bbox": list(rg.BOX_REGIONS["germany"])}}


def synthetic():
    x = Extract()
    # Cologne: Neumarkt, Stadtbahn 16 and 18 underground, trams at the surface in its stop area.
    neumarkt = x.station("Neumarkt", (50.9360, 6.9470), ["light_rail"], {"station": "light_rail"},
                         ifopt="de:05315:11201", platforms=2)
    area = x.area("Neumarkt", [neumarkt] + neumarkt["platforms"] + neumarkt["stop_positions"], ifopt="de:05315:11201",
                  operator="Kölner Verkehrs-Betriebe")
    x.stop("Neumarkt", offset((50.9360, 6.9470), 80, 0), tram=True, area=area, ifopt="de:05315:11201:2:3")
    x.stop("Neumarkt", offset((50.9360, 6.9470), 80, 30), tram=False, area=area, ifopt="de:05315:11201:3:5", local_ref="5")
    # Hansaring: S-Bahn S 11 (route=train), RE 1, ICE.
    hansaring = x.station("Köln Hansaring", (50.9480, 6.9440), ["light_rail", "train"],
                          {"station": "light_rail", "train": "yes"}, ifopt="de:05315:11111")
    # Köln Hbf: S 11, RE 1, ICE 10 and Stadtbahn 16, 18.
    hbf = x.station("Köln Hbf", (50.9430, 6.9590), ["train"], {}, ifopt="de:05315:11000", platforms=2)
    hbf_u = x.station("Köln Hbf", (50.9425, 6.9585), ["light_rail"], {"station": "subway"}, ifopt="de:05315:11000")
    x.route([(neumarkt["stop_positions"][0], "stop"), (hbf_u["stop_positions"][0], "stop")],
            route="light_rail", ref="16", network="VRS", operator="Kölner Verkehrs-Betriebe")
    x.route([(neumarkt["stop_positions"][1], "stop"), (hbf_u["stop_positions"][0], "stop")],
            route="light_rail", ref="18", network="VRS", operator="Kölner Verkehrs-Betriebe")
    x.route([(hansaring["stop_positions"][0], "stop"), (hbf["stop_positions"][0], "stop")],
            route="train", ref="S 11", service="commuter", network="VRS")
    x.route([(hansaring["stop_positions"][0], "stop"), (hbf["stop_positions"][1], "stop")],
            route="train", ref="RE 1", service="regional")
    x.route([(hbf["stop_positions"][1], "stop")], route="train", ref="ICE 10", service="high_speed")
    # A Stadtbahn station no route reaches.
    x.station("Wiener Platz", (50.9620, 7.0040), ["light_rail"], {"station": "light_rail",
                                                                  "operator": "Kölner Verkehrs-Betriebe"},
              ifopt="de:05315:12001")
    # Loose bus stops in Bonn (no stop area), one with a DHID.
    x.stop("Bonn Bertha-von-Suttner-Platz", (50.7370, 7.1010), ifopt="de:05314:61111:1:1", local_ref="1")
    x.stop("Bonn Bertha-von-Suttner-Platz", (50.7372, 7.1013), local_ref="2")
    # Cologne, Ebertplatz: a tram stop area with two stop positions, and a bus platform beside
    # it that has no stop area and its stand in its name ("Ebertplatz Steig F"); tram 5 with a
    # colour; the Stadtbahn 16 above with one.
    ebert = (50.9510, 6.9580)
    ebert_area = x.area("Ebertplatz", [])
    tram_a = x.stop("Ebertplatz", offset(ebert, 0, 0), tram=True, area=ebert_area)
    tram_b = x.stop("Ebertplatz", offset(ebert, 0, 25), tram=True, area=ebert_area)
    x.stops.append({"type": "node", "id": x.nid(), "kind": "platform", "lat": offset(ebert, 60, 40)[0],
                    "lon": offset(ebert, 60, 40)[1], "bus": True, "tram": False, "name": "Ebertplatz Steig F",
                    "stopAreas": [], "tags": {"highway": "bus_stop", "public_transport": "platform",
                                              "name": "Ebertplatz Steig F"}})
    x.bus_routes.append({"id": x.nid(), "route": "tram", "ref": "5", "colour": "#E3000B", "network": "VRS",
                         "m": [["n", tram_a["id"], "stop", tram_a["lat"], tram_a["lon"]],
                               ["n", tram_b["id"], "stop", tram_b["lat"], tram_b["lon"]]]})
    x.routes[0]["tags"]["colour"] = "#00A0DE"
    # Stuttgart: Stadtbahn U6 / U12 (light_rail with U refs, VVS / SSB).
    sh = x.station("Stadtmitte", (48.7760, 9.1730), ["light_rail"], {"station": "light_rail"},
                   ifopt="de:08111:6056")
    nach = x.station("Rotebühlplatz", (48.7755, 9.1710), ["light_rail"], {"station": "light_rail"},
                     ifopt="de:08111:6057")
    x.route([(sh["stop_positions"][0], "stop"), (nach["stop_positions"][0], "stop")],
            route="light_rail", ref="U6", network="VVS", operator="Stuttgarter Straßenbahnen")
    # Munich: U-Bahn.
    x.station("Marienplatz", (48.1370, 11.5755), ["subway"], {"station": "subway"}, ifopt="de:09162:2")
    return x.json()


def check_route_kinds():
    cases = [
        ({"route": "subway", "ref": "U2"}, "U"),
        ({"route": "light_rail", "ref": "S41", "network": "VBB"}, "S"),
        ({"route": "light_rail", "ref": "S 1"}, "S"),
        ({"route": "light_rail", "ref": "1", "network:metro": "s-bahn"}, "S"),
        ({"route": "light_rail", "ref": "16", "operator": "Kölner Verkehrs-Betriebe"}, "L"),
        ({"route": "light_rail", "ref": "U6", "network": "VVS"}, "L"),
        ({"route": "train", "ref": "S 11", "service": "commuter"}, "S"),
        ({"route": "train", "ref": "S2", "network": "MVV"}, "S"),
        ({"route": "train", "ref": "RE 1", "service": "regional"}, "R"),
        ({"route": "train", "ref": "RB 23"}, "R"),
        ({"route": "train", "ref": "ICE 10", "service": "high_speed"}, "F"),
        ({"route": "train", "ref": "IC 2023"}, "F"),
        ({"route": "train", "ref": "NJ 40470", "service": "night"}, "F"),
        ({"route": "tram", "ref": "M10"}, "T"),
        ({"route": "bus", "ref": "100"}, None),
    ]
    return ["route_kind(%s) = %r, wanted %r" % (tags, b.route_kind(tags), want)
            for tags, want in cases if b.route_kind(tags) != want]


def check_stands():
    cases = [
        ("Tattersall Steig I", ("Tattersall", "I")),
        ("Kunsthalle Steig F", ("Kunsthalle", "F")),
        ("Hauptbahnhof, Bstg. 3", ("Hauptbahnhof", "3")),
        ("Marktplatz (Bussteig B)", ("Marktplatz", "B")),
        ("Busbahnhof Mast 12", ("Busbahnhof", "12")),
        ("Alte Post", ("Alte Post", None)),
        ("Steig A", ("Steig A", None)),
        ("Heiliger Weg", ("Heiliger Weg", None)),
    ]
    out = ["split_stand(%r) = %r, wanted %r" % (name, b.split_stand(name), want)
           for name, want in cases if b.split_stand(name) != want]
    for value, want in (("#E3000B", "e3000b"), ("#e00", "ee0000"), ("red", "ff0000"), ("nope", None)):
        if b.norm_colour(value) != want:
            out.append("norm_colour(%r) = %r" % (value, b.norm_colour(value)))
    return out


def main():
    problems = check_route_kinds() + check_stands()
    if rg.BERLIN_BOX != (ov.BERLIN_SOUTH, ov.BERLIN_WEST, ov.BERLIN_NORTH, ov.BERLIN_EAST):
        problems.append("regions.BERLIN_BOX differs from the box of fetch_osm_stations")
    with tempfile.TemporaryDirectory() as tmp:
        source = os.path.join(tmp, "osm-stations.json")
        with open(source, "w", encoding="utf-8") as handle:
            json.dump(synthetic(), handle)
        packs = os.path.join(tmp, "packs")
        log = os.path.join(tmp, "log.txt")
        stdout = sys.stdout
        with open(log, "w", encoding="utf-8") as sink:
            sys.stdout = sink
            try:
                b.main(["--source", source, "--region", "nordrhein-westfalen", "--out-dir", tmp])
                b.main(["--source", source, "--region", "germany", "--out-dir", tmp,
                        "--split-regions", "--packs-dir", packs])
            finally:
                sys.stdout = stdout
        with open(os.path.join(tmp, "osm-nordrhein-westfalen.json"), encoding="utf-8") as handle:
            asset = json.load(handle)
        if not os.path.exists(os.path.join(tmp, "osm-change-positions-nordrhein-westfalen.ndjson")):
            problems.append("no change positions file of the region")
        by_name = {s["name"]: s for s in asset["stations"]}
        wanted = {
            # name: (products, kinds)
            "Neumarkt": ("U", "LT"),
            "Köln Hansaring": ("S", "SR"),
            "Köln Hbf": ("SU", "SLRF"),
            "Wiener Platz": ("U", "L"),
        }
        for name, (products, kinds) in wanted.items():
            s = by_name.get(name)
            if s is None:
                problems.append("station %s missing" % name)
                continue
            if s["products"] != products or s["kinds"] != kinds:
                problems.append("%s: products %r kinds %r, wanted %r %r" % (name, s["products"], s["kinds"], products, kinds))
            if not any(i.startswith("de:05315:") for i in s["ids"]):
                problems.append("%s: no DHID in ids %r" % (name, s["ids"]))
            if any(i.startswith("900") for i in s["ids"]):
                problems.append("%s: a VBB number in ids %r" % (name, s["ids"]))
        for name in ("Stadtmitte", "Rotebühlplatz", "Marienplatz"):
            if name in by_name:
                problems.append("%s (not in Nordrhein-Westfalen) in the region's asset" % name)
        if len(by_name) != 4:
            problems.append("stations of the region: %s" % sorted(by_name))
        area = [a for a in asset["stopAreas"] if a["n"] == "Neumarkt"]
        if not area or area[0].get("ids") != ["de:05315:11201"]:
            problems.append("stop area Neumarkt: %r" % area)
        bonn = [a for a in asset["stopAreas"] if a["n"].startswith("Bonn")]
        if len(bonn) != 1 or bonn[0].get("ids") != ["de:05314:61111"] or len(bonn[0]["p"]) != 2:
            problems.append("loose stops of Bonn: %r" % bonn)
        stop = [s for s in asset["stops"] if s["name"] == "Neumarkt"]
        if not stop or stop[0].get("ids") != ["de:05315:11201"]:
            problems.append("numbered stop Neumarkt: %r" % stop)
        # A stand in the name: the platform joins the stop area of its name with the stand as its number.
        ebert = [a for a in asset["stopAreas"] if a["n"].startswith("Ebertplatz")]
        if len(ebert) != 1 or ebert[0]["n"] != "Ebertplatz" or sorted(p[3] for p in ebert[0]["p"] if len(p) > 3 and p[3]) != ["F"]:
            problems.append("Ebertplatz with its Steig F: %r" % ebert)
        tram = [p for a in ebert for p in a["p"] if p[2] == "t"]
        if len(tram) != 2 or any(len(p) < 5 or p[4].get("t") != ["5"] for p in tram):
            problems.append("tram 5 at Ebertplatz: %r" % tram)
        colours = {(e[0], e[1]): e[2] for e in asset.get("lineColors", [])}
        if colours.get(("t", "5")) != "e3000b" or colours.get(("u", "16")) != "00a0de":
            problems.append("line colours of the region: %r" % asset.get("lineColors"))
        if not os.path.exists(os.path.join(tmp, "osm-germany.json")):
            problems.append("no osm-germany.json")
        # The packs: the whole extract (germany), cut by state.
        names = {}
        for slug in rg.STATES:
            with open(os.path.join(packs, "osm-%s.json" % slug), encoding="utf-8") as handle:
                names[slug] = sorted(s["name"] for s in json.load(handle)["stations"])
        if names["nordrhein-westfalen"] != sorted(wanted):
            problems.append("pack nordrhein-westfalen: %r" % names["nordrhein-westfalen"])
        if names["baden-wuerttemberg"] != ["Rotebühlplatz", "Stadtmitte"] or names["bayern"] != ["Marienplatz"]:
            problems.append("packs baden-wuerttemberg %r, bayern %r" % (names["baden-wuerttemberg"], names["bayern"]))
        with open(os.path.join(packs, "osm-baden-wuerttemberg.json"), encoding="utf-8") as handle:
            bw_colours = json.load(handle).get("lineColors", [])
        if any(e[1] in ("5", "16") for e in bw_colours):
            problems.append("colours of Cologne in the pack of Baden-Wuerttemberg: %r" % bw_colours)
        if any(names[s] for s in rg.STATES if s not in ("nordrhein-westfalen", "baden-wuerttemberg", "bayern")):
            problems.append("stations in other packs: %r" % {k: v for k, v in names.items() if v})
        # The Stuttgart Stadtbahn counts as U for the app (U lines on a light_rail route).
        data = synthetic()
        complexes, _ = b.build_complexes(data)
        routes, _ = b.route_visits(data, {b.station_key(s): s for s in data["stations"]})
        u6 = [r for r in routes if r["line"] == "U6"]
        if not u6 or u6[0]["product"] != "U" or None in u6[0]["visits"]:
            problems.append("route U6: %r" % u6)
        # A complex of several station nodes drops the bracket naming the part; a place in
        # brackets stays.
        parts = [{"name": "Südkreuz (Ringbahn)"}, {"name": "Berlin Südkreuz"}]
        if b.complex_name(parts) != "Südkreuz":
            problems.append("complex name of %r: %r" % (parts, b.complex_name(parts)))
        place = [{"name": "Freie Universität (Thielplatz)"}, {"name": "U Freie Universität (Thielplatz)"}]
        if b.complex_name(place) != "Freie Universität (Thielplatz)":
            problems.append("complex name of %r: %r" % (place, b.complex_name(place)))
        if b.complex_name([{"name": "Südkreuz (Ringbahn)"}]) != "Südkreuz (Ringbahn)":
            problems.append("a single station keeps its name")
        # A plain door with a level, beside a platform: a door of the station, not of a house.
        origin = (52.4745, 13.3653)
        frame = b.Frame(*origin)
        platform = {"type": "way", "id": 1, "tags": {"railway": "platform"},
                    "geometry": [list(offset(origin, 10, -3)), list(offset(origin, 200, -3)),
                                 list(offset(origin, 200, 3)), list(offset(origin, 10, 3)), list(offset(origin, 10, -3))]}
        shape_place = {"frame": frame, "platforms": [{"shape": b.Shape(platform, frame), "level": "0"}]}
        door = {"lat": origin[0], "lon": origin[1], "tags": {"entrance": "yes", "level": "1", "wheelchair": "yes"}}
        if not b.platform_side_door(door, shape_place):
            problems.append("plain door with a level beside a platform not taken as a station door")
        for tags in ({"entrance": "yes"}, {"entrance": "yes", "level": "1", "shop": "bakery"},
                     {"entrance": "yes", "level": "-1"}, {"entrance": "yes", "level": "1", "addr:street": "X"}):
            if b.platform_side_door(dict(door, tags=tags), shape_place):
                problems.append("door %r taken as a station door" % tags)
        far = dict(door, lat=offset(origin, -60, 0)[0])
        if b.platform_side_door(far, shape_place):
            problems.append("door 70 m from the platform taken as a station door")
        if problems:
            with open(log, encoding="utf-8") as handle:
                print(handle.read())
    if problems:
        print("FAILED:")
        for p in problems:
            print("  " + p)
        return 1
    print("OK: kinds, products, DHIDs, the cut to Nordrhein-Westfalen and the state packs")
    return 0


if __name__ == "__main__":
    sys.exit(main())
