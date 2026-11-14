#!/usr/bin/env python3
"""
Self-test of the walking network of tools/build_transfer_walks.py on a tiny synthetic graph:
platform A on level 1 and platform B on level 0, joined by steps and by a lift, and a street
with a bus stop beside B.

    python3 tools/test_build_transfer_walks.py
"""

import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import build_transfer_walks as bt  # noqa: E402

ORIGIN = (52.60, 13.60)


def at(north, east):
    lat = ORIGIN[0] + north / 111320.0
    lon = ORIGIN[1] + east / (111320.0 * math.cos(math.radians(ORIGIN[0])))
    return round(lat, 6), round(lon, 6)


class Builder(object):
    def __init__(self):
        self.points = []
        self.index = {}
        self.ways = []

    def node(self, north, east):
        key = (north, east)
        if key not in self.index:
            self.index[key] = len(self.points)
            self.points.append(at(north, east))
        return self.index[key]

    def way(self, kind, level, coords, steps=0, area=0):
        self.ways.append([kind, level, steps, area] + [self.node(n, e) for n, e in coords])

    def ring(self, south, north, west, east, step=10):
        coords = [(south, e) for e in range(west, east + 1, step)] + [(north, e) for e in range(east, west - 1, -step)]
        return coords + [coords[0]]

    def raw(self, elevators=()):
        flat = []
        for lat, lon in self.points:
            flat += [lat, lon]
        return {"nodes": flat, "ways": self.ways, "elevators": [self.index[e] for e in elevators], "doors": []}


def near(value, expected, slack=2.0):
    return abs(value - expected) <= slack


def main():
    b = Builder()
    ring_a = b.ring(0, 10, 0, 100)
    ring_b = b.ring(30, 40, 0, 100)
    b.way("r", "1", ring_a, area=1)
    b.way("r", "0", ring_b, area=1)
    # Steps from inside A (a loose end on the platform) down to a footway on level 0 to B.
    b.way("s", "0;1", [(5, 50), (20, 50)], steps=20)
    b.way("w", "0", [(20, 50), (30, 50)])
    # The lift: a footway on level 1 from inside A to the lift, one on level 0 on to B.
    b.way("w", "1", [(5, 10), (20, 10)])
    b.way("w", "0", [(20, 10), (30, 10)])
    # The street beside B and the way to it.
    b.way("w", "", [(40, 50), (45, 50)])
    b.way("w", "", [(45, 0), (45, 50), (45, 100)])
    graph = bt.Graph(b.raw(elevators=[(20, 10)]))

    sources_a = {b.index[p] for p in ring_a} | {b.index[(5, 10)]}
    sources_b = {b.index[p] for p in ring_b}
    normal = graph.search(sources_a, sources_b, False)
    free = graph.search(sources_a, sources_b, True)
    best = min(normal.values())
    best_free = min(free.values())
    # 5 m across A to the stairs (4 s), 15 m of stairs (12 s) and 20 steps (20 s), 10 m on (8 s).
    assert near(best, 44.0), best
    # 15 m to the lift (12 s), the lift (60 s), 10 m on (8 s).
    assert near(best_free, 80.0), best_free

    stop = at(50, 50)
    attach = bt.stop_attach(graph, stop)
    assert attach and attach[0][0] == b.index[(45, 50)], attach
    targets = {v for v, _ in attach}
    to_stop = graph.search(sources_a, targets, False)
    seconds = min(to_stop[v] + d / bt.SPEED for v, d in attach if v in to_stop)
    # To B (44 s), across B (8 s), to the street (4 s), to the stop (4 s).
    assert near(seconds, 60.0), seconds

    # Levels: two platforms one level apart need at least LEVEL_CHANGE_SECONDS.
    assert bt.level_gap({"level": "1"}, {"level": "-2"}) == 3
    assert bt.level_gap({"level": "1"}, None) == 1
    assert bt.level_gap({}, {"level": "-2"}) == 0
    assert bt.whole_seconds(0.2) == 1 and bt.whole_seconds(60.0) == 60 and bt.whole_seconds(60.01) == 61
    # Without a way at all: nothing found.
    assert graph.search({b.index[(45, 0)]}, {len(graph.points) + 5}, False) == {}
    # Two platforms that cross on two levels are 0 m apart, not the distance of their ends.
    assert bt.segments_meters(at(0, 0), at(100, 0), at(50, -50), at(50, 50)) == 0.0
    assert near(bt.segments_meters(at(0, 0), at(100, 0), at(50, 10), at(50, 50)), 10.0, 0.5)
    # Platforms not in use are no platforms of the asset.
    assert not bt.bsa.platform_in_use({"railway": "construction", "construction": "platform"})
    assert not bt.bsa.platform_in_use({"railway": "platform", "public_transport": "no"})
    assert not bt.bsa.platform_in_use({"disused:railway": "platform"})
    assert bt.bsa.platform_in_use({"railway": "platform", "public_transport": "platform"})
    # The bays of a bus station and a stop area relation are no rail platforms.
    assert not bt.bsa.is_rail_platform({"highway": "platform", "public_transport": "platform"})
    assert not bt.bsa.is_rail_platform({"type": "public_transport", "public_transport": "stop_area", "railway": "platform"})
    assert bt.bsa.is_rail_platform({"highway": "platform", "railway": "platform"})
    assert bt.bsa.is_rail_platform({"public_transport": "platform", "train": "yes"})
    print("ok: A to B %.0f s (step-free %.0f s), A to the stop %.0f s" % (best, best_free, seconds))
    return 0


if __name__ == "__main__":
    sys.exit(main())
