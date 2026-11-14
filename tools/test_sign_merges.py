#!/usr/bin/env python3
"""
Self-test of tools/audit_sign_merges.py (the rules of the app for merged stop signs and stop
names, see there), on the stops of Alexanderplatz as the Berlin asset has them (November 2026)
and on the asset itself:

- the bus stop "S+U Alexanderplatz/Memhardstraße" in Karl-Liebknecht-Straße (the provider's
  "S+U Alexanderplatz (Bln) [Bus K.-Liebknecht-Str]"), 230 m from the station's sign, had no
  sign of its own (left out offline and beside the provider's station) and has one now; the
  trams of Dircksenstraße 20 m from the station's sign still go with it;
- after the rules no bus or tram stop of any pack is left without a sign of its own farther
  than 120 m from the sign that stands for it;
- the names: "(Bln)" left out in Berlin, the part in square brackets a line of its own,
  brackets that are no town kept.

    python3 tools/test_sign_merges.py
"""

import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import audit_sign_merges as a  # noqa: E402
import stop_sign  # noqa: E402


def area(name, points, ids=()):
    mean = (sum(p[0] for p in points) / len(points), sum(p[1] for p in points) / len(points))
    return {"name": name, "key": a.osm_name_key(name), "mean": mean, "sign": stop_sign.sign_point(None, points),
            "ids": list(ids), "places": {k for k in map(a.stop_place_key, ids) if k}}


STATION = {"name": "Alexanderplatz", "key": a.osm_name_key("Alexanderplatz"), "pos": (52.521536, 13.412089),
           "ids": ["900100003"], "places": {"900100003"}}
MEMHARD = area("S+U Alexanderplatz/Memhardstraße", [(52.52178, 13.408579), (52.521817, 13.408531), (52.521736, 13.408366)], ["900100031"])
DIRCKSEN = area("S+U Alexanderplatz/Dircksenstraße", [(52.521468, 13.412011), (52.521421, 13.411924)], ["900100024"])


def check_alexanderplatz(problems):
    for rule in ("before", "after"):
        if not a.offline_dropped(DIRCKSEN, [STATION], rule) or not a.provider_dropped(DIRCKSEN, [STATION], rule):
            problems.append("Dircksenstraße (20 m) not with the station's sign (%s)" % rule)
    if not a.offline_dropped(MEMHARD, [STATION], "before") or not a.provider_dropped(MEMHARD, [STATION], "before"):
        problems.append("the old rule should leave out the bus stop in Karl-Liebknecht-Straße")
    if a.offline_dropped(MEMHARD, [STATION], "after") or a.provider_dropped(MEMHARD, [STATION], "after"):
        problems.append("the bus stop in Karl-Liebknecht-Straße has no sign of its own")
    # The same stop place number does not join a stop in the street farther than 120 m either.
    same_number = dict(MEMHARD, places={"900100003"})
    if a.provider_dropped(same_number, [STATION], "after"):
        problems.append("a bus stop with the station's number 230 m off was joined")


def check_names(problems):
    cases = [
        (("S+U Alexanderplatz (Bln) [Bus K.-Liebknecht-Str]", "Berlin"), ("S+U Alexanderplatz", "Bus K.-Liebknecht-Str.")),
        (("S+U Berlin Hauptbahnhof [Gleis 1-8]", "Berlin"), ("S+U Berlin Hauptbahnhof", "Gleis 1-8")),
        (("S Golm (Potsdam)", "Berlin"), ("S Golm (Potsdam)", None)),
        (("Frankfurt (Oder)", "Frankfurt (Oder)"), ("Frankfurt (Oder)", None)),
        (("Aue (Sachsen)", "Sachsen"), ("Aue (Sachsen)", None)),
        (("Bernau (BAR)", "BAR"), ("Bernau (BAR)", None)),
        (("Leipzig Hbf", "Leipzig"), ("Leipzig Hbf", None)),
    ]
    for (name, home), expected in cases:
        got = a.stop_name_parts(name, home)
        if got != expected:
            problems.append("%r in %s shown as %r, not %r" % (name, home, got, expected))
    names = ["S+U Alexanderplatz (Bln) [U2]", "S+U Alexanderplatz (Bln)", "S Hackescher Markt (Bln)", "Spandauer Str. (Bln)", "Memhardstr."]
    if a.dominant_town(names) != "Bln":
        problems.append("town of the stops around: %r" % a.dominant_town(names))
    if a.dominant_town(["U Freie Universität (Thielplatz)", "Freie Universität (Thielplatz)", "Habelschwerdter Allee"]) is not None:
        problems.append("two stops of one place taken as the town")


def check_assets(problems):
    for region, path in a.asset_files({"berlin", "sachsen", "hamburg"}, a.ASSETS):
        counts, _ = a.audit(region, path, 5)
        if counts.get("no own sign >120 m after", 0):
            problems.append("%s: %d stops without a sign of their own farther than 120 m" % (region, counts["no own sign >120 m after"]))
        if counts.get("no own sign after", 0) > counts.get("no own sign before", 0):
            problems.append("%s: more stops without a sign of their own after than before" % region)


def main():
    problems = []
    check_alexanderplatz(problems)
    check_names(problems)
    check_assets(problems)
    for problem in problems:
        print("FAIL", problem)
    print("%d problems" % len(problems))
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
