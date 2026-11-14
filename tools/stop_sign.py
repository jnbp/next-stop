#!/usr/bin/env python3
"""
Where the sign ("H") of a bus or tram stop stands on the map, from the provider's point and
the positions (platforms, stop positions) of the stop in OpenStreetMap. The same rule as
`stopSignPoint` in app/src/main/java/io/github/jnbp/nextstop/data/StopSignPlace.kt (keep the
two alike); used by tools/audit_stop_signs.py and its test.

- The positions taken are those within CLUSTER_METERS of the position nearest to the
  provider's point (a stop area of one id can hold positions of both ends of a village).
- The provider's point stays where it lies within KEEP_METERS of one of them and inside their
  bounding box grown by BOX_MARGIN_METERS.
- Else the sign goes to their centre, pulled to at most PULL_METERS from the position nearest
  to it (the centre of two positions 150 m apart would stand 75 m from both).

So the sign never stands farther than the bounding box of the positions plus 20 m, nor (moved)
farther than 30 m from a position.
"""

import math

CLUSTER_METERS = 250.0
KEEP_METERS = 30.0
BOX_MARGIN_METERS = 20.0
PULL_METERS = 30.0


def meters(a, b):
    north = (b[0] - a[0]) * 111320.0
    east = (b[1] - a[1]) * 111320.0 * math.cos(math.radians((a[0] + b[0]) / 2))
    return math.hypot(north, east)


def in_box(point, items, margin):
    dlat = margin / 111320.0
    dlon = margin / (111320.0 * max(0.01, math.cos(math.radians(point[0]))))
    return (min(p[0] for p in items) - dlat <= point[0] <= max(p[0] for p in items) + dlat and
            min(p[1] for p in items) - dlon <= point[1] <= max(p[1] for p in items) + dlon)


def sign_point(provider, positions):
    """The point of the sign: [provider] (lat, lon) or None, [positions] a list of (lat, lon)."""
    if not positions:
        return provider
    if provider is None:
        anchor = (sum(p[0] for p in positions) / len(positions), sum(p[1] for p in positions) / len(positions))
    else:
        anchor = provider
    first = min(positions, key=lambda p: meters(anchor, p))
    cluster = [p for p in positions if meters(first, p) <= CLUSTER_METERS]
    if provider is not None and min(meters(provider, p) for p in cluster) <= KEEP_METERS and \
            in_box(provider, cluster, BOX_MARGIN_METERS):
        return provider
    centre = (sum(p[0] for p in cluster) / len(cluster), sum(p[1] for p in cluster) / len(cluster))
    near = min(cluster, key=lambda p: meters(centre, p))
    d = meters(centre, near)
    if d > PULL_METERS:
        share = PULL_METERS / d
        centre = (near[0] + (centre[0] - near[0]) * share, near[1] + (centre[1] - near[1]) * share)
    return centre
