#!/usr/bin/env python3
"""
Audit of the stop signs the map merges, over Berlin and every state pack: which bus and tram
stops have no sign of their own because another sign stands for them, how stop names are
shown, and which merged signs stand far from their parts. Replicates the rules of the app
(keep the two alike):

- offline: `OsmStations.mapStops` leaves out a stop area of the name of a station (`nameFits`)
  near the station: before (rule "before") within 300 m of the mean of its positions, after
  (rule "after", `streetStopJoinsStation`) only within STREET_STOP_OWN_SIGN_METERS (120 m) of
  its sign point (tools/stop_sign.py); stop areas of one name within 250 m are one sign;
- with the provider's station: `mergeMapStops` leaves out an offline stop that the provider's
  stop stands for. The provider's rail station is taken at the station's point with the
  station's name (the provider lists it near the map centre). Before: within 250 m and of a
  fitting name (`sameStopKey`), or within 40 m. After: a bus or tram stop within 40 m, or
  within 120 m and of a fitting name or the same stop place (VBB number, DHID);
- rail stations: `stationGroups` joins rail stops of the same stop place within 400 m or of
  one base name (`stationBaseKey`, `baseKeysFit`) within 250 m;
- names: `stopNameParts` (data/StopTowns.kt) shows "S+U Alexanderplatz (Bln) [Bus
  K.-Liebknecht-Str]" as "S+U Alexanderplatz" with the line "Bus K.-Liebknecht-Str." where the
  town in brackets is the town the app is used in; here per pack the town most names give in
  brackets (as `LocalTown` takes it from the stops around). The words in brackets that are no
  town (NOT_A_TOWN) are read from StopTowns.kt.

    python3 tools/audit_sign_merges.py [--regions berlin,sachsen] [--report /tmp/merges.json]
        [--assets app/src/main/assets] [--worst 20]

Prints per pack: bus/tram stop areas, those without a sign of their own (offline, with the
provider's station, either) before -> after, those of them farther than 120 m from the sign
that stands for them, names with a qualifier (brackets, town) and how many of them change,
and rail stations joined farther than 120 m apart; then the worst examples.

Data (c) OpenStreetMap contributors, ODbL 1.0.
"""

import argparse
import json
import math
import os
import re
import sys
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)
import stop_sign  # noqa: E402

ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
STOP_TOWNS_KT = os.path.join(ROOT, "app", "src", "main", "java", "io", "github", "jnbp", "nextstop", "data", "StopTowns.kt")

AREA_AT_STATION_BEFORE = 300.0
STREET_STOP_OWN_SIGN_METERS = 120.0
SAME_AREA_METERS = 250.0
SAME_STOP_METERS = 250.0
SAME_SPOT_METERS = 40.0
STATION_MERGE_METERS = 400.0
STATION_NAME_MERGE_METERS = 250.0
MIN_PART_KEY = 5
CELL = 0.01

meters = stop_sign.meters


# --- names, as data/ChangePositions.kt stationNameKey and data/OsmStations.kt osmNameKey ---

def station_name_key(name):
    text = name.lower()
    text = re.sub(r"\(.*?\)", " ", text)
    text = text.replace("straße", "str").replace("strasse", "str")
    text = re.sub(r"[^a-z0-9äöüß]+", " ", text).strip()
    text = re.sub(r"^(s u|s|u)\s+", "", text)
    text = re.sub(r"(\s+(s|u|re|rb)\d+)+$", "", text)
    if text.startswith("berlin "):
        text = text[len("berlin "):]
    for suffix in (" berlin", " bhf"):
        if text.endswith(suffix):
            text = text[: -len(suffix)]
    return text.strip().replace(" ", "")


def osm_name_key(name):
    return station_name_key(name).replace("bhf", "")


def name_fits(a, b):
    if not a or not b:
        return False
    if a == b:
        return True
    short, long = (a, b) if len(a) <= len(b) else (b, a)
    return len(short) >= MIN_PART_KEY and (long.startswith(short) or long.endswith(short))


def same_stop_key(a, b):
    if not a or not b:
        return False
    if a == b:
        return True
    short, long = (a, b) if len(a) <= len(b) else (b, a)
    return len(short) >= 5 and short in long


# --- rail stations, as ui/StationSigns.kt ---

STATION_PARTS = {"tunnel", "hp", "bf", "bft", "bahnhof", "s", "u", "sbahn", "ubahn", "tief"}
COMMON_WORDS = {"bahnhof", "bhf", "station", "gleis", "tief", "berlin"}


def station_base_key(name):
    plain = re.sub(r"\[[^\]]*\]", " ", name)
    plain = re.sub(r"\([^)]*\)", " ", plain).split(",")[0]
    plain = re.sub(r"\s+(gleis\b|bahnsteig\b|gl\.)\s*[0-9].*$", "", plain, flags=re.I)
    plain = re.sub(r"\s+(tief|oben|unten)\s*$", "", plain, flags=re.I)
    return station_name_key(plain).replace("hauptbahnhof", "hbf")


def base_keys_fit(a, b):
    if not a or not b:
        return False
    if a == b:
        return True
    short, long = (a, b) if len(a) <= len(b) else (b, a)
    return len(short) >= 3 and long.startswith(short) and long[len(short):] in STATION_PARTS


def name_words(name):
    text = re.sub(r"\[[^\]]*\]", " ", name.lower())
    text = re.sub(r"\([^)]*\)", " ", text).replace("hbf", "hauptbahnhof")
    return {w for w in re.findall(r"[a-z0-9äöüß]{3,}", text) if w not in COMMON_WORDS}


def stop_place_key(stop_id):
    if not stop_id:
        return None
    vbb = re.search(r"900(?:000)?(\d{6})(?!\d)", stop_id)
    if vbb:
        return "900" + vbb.group(1)
    dhid = re.match(r"^[a-z]{2}:\d+:[\w]+", stop_id)
    return dhid.group(0) if dhid else None


# --- display names, as data/StopTowns.kt stopNameParts ---

def not_a_town():
    text = open(STOP_TOWNS_KT, encoding="utf-8").read()
    block = re.search(r"private val NOT_A_TOWN = setOf\((.*?)\n\)", text, re.S).group(1)
    return set(re.findall(r'"([^"]*)"', block))


NOT_A_TOWN = not_a_town()
TOWN_IN_BRACKETS = re.compile(r"^(.*\S)\s+\(([^()\[\]]+)\)$")
TRAILING_PART = re.compile(r"^(.*?\S)\s*\[([^\[\]]+)\]$")


def town_in_brackets(whole):
    m = TOWN_IN_BRACKETS.match(whole)
    if not m:
        return None
    town = m.group(2).strip()
    plain = town.lower()
    letters = sum(1 for c in town if c.isalpha())
    district_code = len(town) <= 4 and all(c.isupper() or not c.isalpha() for c in town)
    qualifier = (plain in NOT_A_TOWN or plain.startswith(("b ", "b. ", "bei ", "kr ", "kr. ")) or any(c.isdigit() for c in town)
                 or letters < 3 or district_code or plain.startswith(("richtung ", "ri. ", "fr ")) or "&" in plain)
    return None if qualifier or not town else (m.group(1).strip(), town)


def town_key(town):
    t = re.sub(r"\([^)]*\)", "", town.lower())
    return re.sub(r"\s+", " ", re.sub(r"[/\-]", " ", t)).strip()


def is_abbreviation(short, long):
    letters = "".join(c for c in short.lower() if c.isalpha())
    full = "".join(c for c in long.lower() if c.isalpha())
    if not 2 <= len(letters) <= 4 or len(full) <= len(letters) or letters[0] != full[0]:
        return False
    if any(not c.isalpha() for c in short.strip().rstrip(".")):
        return False
    at = 0
    for c in full:
        if at < len(letters) and c == letters[at]:
            at += 1
    return at == len(letters)


def same_town(a, b):
    if a is None or b is None:
        return False
    ka, kb = town_key(a), town_key(b)
    if not ka or not kb:
        return a.lower() == b.lower()
    return (ka == kb or (ka.split(" ")[0] == kb and len(kb) >= 4) or (kb.split(" ")[0] == ka and len(ka) >= 4)
            or is_abbreviation(ka, kb) or is_abbreviation(kb, ka))


def strip_parts(name):
    rest = re.sub(r"\s+", " ", name.strip())
    parts = []
    while True:
        m = TRAILING_PART.match(rest)
        if not m:
            break
        parts.insert(0, re.sub(r"(?<=[Ss]tr)(?=$|[\s,/)])", ".", m.group(2).strip()))
        rest = m.group(1).strip()
    return rest, parts


def stop_name_parts(name, home):
    rest, parts = strip_parts(name)
    town = town_in_brackets(rest)
    title = town[0] if town and same_town(town[1], home) else rest
    return (title or name.strip()), (" · ".join(p for p in parts if p) or None)


MIN_TOWN_STOPS = 3


def dominant_town(names):
    groups = []
    for name in names:
        rest, _ = strip_parts(name)
        town = town_in_brackets(rest)
        if not town:
            continue
        for group in groups:
            if same_town(group[0][1], town[1]):
                group.append(town)
                break
        else:
            groups.append([town])
    if not groups:
        return None
    best = max(groups, key=len)
    if len({stop for stop, _ in best}) < MIN_TOWN_STOPS or len(best) * 2 < len(names):
        return None
    towns = [t for _, t in best]
    return max(set(towns), key=towns.count)


# --- the packs ---

def asset_files(regions, assets):
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


class Grid:
    def __init__(self, items, point):
        self.cells = defaultdict(list)
        for item in items:
            p = point(item)
            self.cells[(int(math.floor(p[0] / CELL)), int(math.floor(p[1] / CELL)))].append(item)

    def near(self, p):
        cy, cx = int(math.floor(p[0] / CELL)), int(math.floor(p[1] / CELL))
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                yield from self.cells.get((cy + dy, cx + dx), ())


def offline_dropped(area, stations_near, rule):
    """The station whose sign stands for the stop area [area] in OsmStations.mapStops, or None."""
    for st in stations_near:
        if not name_fits(area["key"], st["key"]):
            continue
        if rule == "before":
            if meters(area["mean"], st["pos"]) <= AREA_AT_STATION_BEFORE:
                return st, meters(area["sign"], st["pos"])
        elif meters(area["sign"], st["pos"]) <= STREET_STOP_OWN_SIGN_METERS:
            return st, meters(area["sign"], st["pos"])
    return None


def provider_dropped(area, stations_near, rule):
    """The provider's rail station that stands for the stop area [area] in mergeMapStops, or None."""
    best = None
    for st in stations_near:
        d = meters(area["sign"], st["pos"])
        if d > SAME_STOP_METERS:
            continue
        if rule == "before":
            taken = d <= SAME_SPOT_METERS or same_stop_key(area["key"], st["key"])
        else:
            same = bool(area["places"] & st["places"]) or same_stop_key(area["key"], st["key"])
            taken = d <= SAME_SPOT_METERS or (same and d <= STREET_STOP_OWN_SIGN_METERS)
        if taken and (best is None or d < best[1]):
            best = (st, d)
    return best


def audit(region, path, worst):
    asset = json.load(open(path, encoding="utf-8"))
    stations = []
    for s in asset.get("stations", []):
        stations.append({"name": s["name"], "key": osm_name_key(s["name"]), "pos": (s["lat"], s["lon"]),
                         "ids": s.get("ids", []), "places": {k for k in map(stop_place_key, s.get("ids", [])) if k}})
    areas = []
    for a in asset.get("stopAreas", []):
        points = [(p[0], p[1]) for p in a["p"]]
        if not points:
            continue
        mean = (sum(p[0] for p in points) / len(points), sum(p[1] for p in points) / len(points))
        areas.append({"name": a["n"], "key": osm_name_key(a["n"]), "mean": mean,
                      "sign": stop_sign.sign_point(None, points), "ids": a.get("ids", []),
                      "places": {k for k in map(stop_place_key, a.get("ids", [])) if k}})
    station_grid = Grid(stations, lambda s: s["pos"])
    counts = defaultdict(int)
    examples = defaultdict(list)
    kept = {"before": Grid([], lambda a: a["mean"]), "after": Grid([], lambda a: a["mean"])}
    merged_after = set()
    for area in areas:
        counts["bus/tram stops"] += 1
        near = list(station_grid.near(area["sign"]))
        for rule in ("before", "after"):
            off = offline_dropped(area, near, rule)
            dup = None
            if off is None:
                dup = next((k for k in kept[rule].near(area["mean"]) if k["key"] == area["key"] and
                            meters(area["mean"], k["mean"]) <= SAME_AREA_METERS), None)
                if dup is None:
                    cell = (int(math.floor(area["mean"][0] / CELL)), int(math.floor(area["mean"][1] / CELL)))
                    kept[rule].cells[cell].append(area)
            prov = provider_dropped(area, near, rule)
            if off:
                counts["merged offline %s" % rule] += 1
            if prov:
                counts["merged with provider station %s" % rule] += 1
            if dup:
                counts["same-name stop areas one sign %s" % rule] += 1
            merged = off or prov
            if merged and rule == "after":
                merged_after.add(area["name"])
            if merged:
                counts["no own sign %s" % rule] += 1
                station, d = merged
                if d > STREET_STOP_OWN_SIGN_METERS:
                    counts["no own sign >120 m %s" % rule] += 1
                    examples["no own sign >120 m %s" % rule].append(
                        (round(d), area["name"], station["name"], "offline" if off else "provider"))
    # Names: how each stop is shown, with the town of the 10 stops nearest to it as the town
    # the app is used in (LocalTown takes it from the provider's stops around), and whether its
    # sign survives (after).
    items = [dict(a, pos=a["sign"], kind="area") for a in areas] + [dict(s, kind="station") for s in stations]
    item_grid = Grid(items, lambda i: i["pos"])
    homes = defaultdict(int)
    for item in items:
        name = item["name"]
        if not re.search(r"[\[(]", name):
            continue
        counts["names with qualifier"] += 1
        around = sorted(item_grid.near(item["pos"]), key=lambda o: meters(item["pos"], o["pos"]))[:10]
        home = dominant_town([o["name"] for o in around])
        if home:
            homes[home] += 1
        title, part = stop_name_parts(name, home)
        if title != name:
            counts["names shown shorter"] += 1
            if part:
                counts["names with a part line"] += 1
            if title != strip_parts(name)[0]:
                counts["names without their town"] += 1
            if len(examples["names"]) < worst:
                examples["names"].append((name, title, part, home))
        if item["kind"] == "area" and item["name"] in merged_after:
            counts["names with qualifier without own sign"] += 1
    counts["home towns"] = ", ".join(sorted(homes, key=lambda h: -homes[h])[:5]) or "-"
    # Rail stations joined by stationGroups farther than 120 m apart.
    rail = stations
    for i, a in enumerate(rail):
        for b in station_grid.near(a["pos"]):
            if b is a or id(b) < id(a):
                continue
            d = meters(a["pos"], b["pos"])
            if d > STATION_MERGE_METERS:
                continue
            words = name_words(a["name"]) & name_words(b["name"])
            same_place = bool(a["places"] & b["places"]) and bool(words)
            if same_place or (d <= STATION_NAME_MERGE_METERS and base_keys_fit(station_base_key(a["name"]), station_base_key(b["name"]))):
                counts["rail stations joined"] += 1
                if d > STREET_STOP_OWN_SIGN_METERS:
                    counts["rail stations joined >120 m"] += 1
                    examples["rail stations joined >120 m"].append((round(d), a["name"], b["name"]))
    for key in list(examples):
        if key != "names":
            examples[key] = sorted(examples[key], reverse=True)[:worst]
    return dict(counts), dict(examples)


SHOWN = ["bus/tram stops", "no own sign before", "no own sign after", "merged offline before", "merged offline after",
         "merged with provider station before", "merged with provider station after",
         "no own sign >120 m before", "no own sign >120 m after", "names with qualifier", "names shown shorter",
         "names with a part line", "names without their town", "names with qualifier without own sign",
         "rail stations joined", "rail stations joined >120 m", "home towns"]


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--regions")
    parser.add_argument("--report")
    parser.add_argument("--assets", default=ASSETS)
    parser.add_argument("--worst", type=int, default=20)
    args = parser.parse_args(argv)
    regions = set(args.regions.split(",")) if args.regions else None
    total = defaultdict(int)
    report = {}
    worst = defaultdict(list)
    for region, path in asset_files(regions, args.assets):
        counts, examples = audit(region, path, args.worst)
        report[region] = {"counts": counts, "examples": examples}
        print("%-24s %s" % (region, json.dumps({k: counts.get(k, 0) for k in SHOWN}, ensure_ascii=False)), flush=True)
        for k, v in counts.items():
            if isinstance(v, int):
                total[k] += v
        for k, v in examples.items():
            worst[k].extend([(region,) + tuple(e) for e in v])
    print("%-24s %s" % ("total", json.dumps({k: total.get(k, 0) for k in SHOWN if k != "home towns"}, ensure_ascii=False)))
    for key in ("no own sign >120 m before", "no own sign >120 m after", "rail stations joined >120 m"):
        rows = sorted(worst.get(key, []), key=lambda r: -r[1])[: args.worst]
        print("\nworst %s (%d):" % (key, len(worst.get(key, []))))
        for row in rows:
            print("  " + " | ".join(str(x) for x in row))
    print("\nnames (first per pack):")
    for row in worst.get("names", [])[: args.worst]:
        print("  " + " | ".join(str(x) for x in row))
    if args.report:
        with open(args.report, "w", encoding="utf-8") as f:
            json.dump({"total": total, "regions": report}, f, ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
