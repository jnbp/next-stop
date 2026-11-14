"""
Regions of the OpenStreetMap tools: bounding boxes (south, west, north, east) and the 16
German states (Bundeslaender) with their official state code, the first two digits of the
municipality key (AGS) that also starts the second part of a German stop id (DHID, tag
ref:IFOPT, e.g. "de:11000:900100003" lies in Berlin, state 11; "de:05315:11201" in
Nordrhein-Westfalen, state 05).

Standard library only; shared by tools/extract_osm_pbf.py and tools/build_station_asset.py.
"""

import re

# The box around Berlin that tools/fetch_osm_stations.py asks in tiles (kept equal to its
# BERLIN_* constants, which tools/test_build_station_asset.py checks).
BERLIN_BOX = (52.33, 13.08, 52.68, 13.77)

# The states: slug -> (state code, bounding box). The boxes are generous and overlap (Berlin
# lies inside the box of Brandenburg, Bremen inside that of Niedersachsen); they are only the
# fallback of state_of() for things without a DHID near them, tried in STATE_ORDER.
STATES = {
    "schleswig-holstein": ("01", (53.36, 7.86, 55.06, 11.32)),
    "hamburg": ("02", (53.39, 9.73, 53.74, 10.33)),
    "niedersachsen": ("03", (51.29, 6.65, 53.89, 11.60)),
    "bremen": ("04", (53.01, 8.48, 53.61, 8.99)),
    "nordrhein-westfalen": ("05", (50.32, 5.86, 52.53, 9.46)),
    "hessen": ("06", (49.39, 7.77, 51.66, 10.24)),
    "rheinland-pfalz": ("07", (48.97, 6.11, 50.94, 8.51)),
    "baden-wuerttemberg": ("08", (47.53, 7.51, 49.79, 10.50)),
    "bayern": ("09", (47.27, 8.97, 50.56, 13.84)),
    "saarland": ("10", (49.11, 6.36, 49.64, 7.40)),
    "berlin": ("11", (52.34, 13.09, 52.68, 13.76)),
    "brandenburg": ("12", (51.36, 11.27, 53.56, 14.77)),
    "mecklenburg-vorpommern": ("13", (53.11, 10.59, 54.69, 14.41)),
    "sachsen": ("14", (50.17, 11.87, 51.69, 15.04)),
    "sachsen-anhalt": ("15", (50.94, 10.56, 53.04, 13.19)),
    "thueringen": ("16", (50.20, 9.88, 51.65, 12.65)),
}
STATE_OF_CODE = {code: slug for slug, (code, _) in STATES.items()}


def _box_area(box):
    return (box[2] - box[0]) * (box[3] - box[1])


# Smallest box first: the city states before the states around them.
STATE_ORDER = sorted(STATES, key=lambda slug: _box_area(STATES[slug][1]))

# The presets of --region. "berlin" is the Overpass box (a little larger than the state, it
# reaches into Brandenburg), so the default build stays what it was; the states are their
# state codes (by DHID, else nearest DHID, else box), the others plain boxes.
BOX_REGIONS = {
    "berlin": BERLIN_BOX,
    "munich": (47.98, 11.36, 48.25, 11.72),
    "germany": (47.27, 5.86, 55.06, 15.05),
}
REGIONS = dict(BOX_REGIONS)
for _slug, (_code, _box) in STATES.items():
    REGIONS.setdefault(_slug, _box)

DHID_RE = re.compile(r"^de:(\d{2})(\d{3}):[^:\s;]+")


def dhid_stop_place(value):
    """The stop place part of a German stop id ("de:11000:900100003" of
    "de:11000:900100003:2:53"), or None when [value] is none."""
    match = DHID_RE.match((value or "").strip())
    return match.group(0) if match else None


def dhid_state(value):
    """The state code ("11") of a DHID, or None."""
    match = DHID_RE.match((value or "").strip())
    return match.group(1) if match else None


def in_box(box, lat, lon):
    return box[0] <= lat <= box[2] and box[1] <= lon <= box[3]


def box_contains(outer, inner):
    return outer[0] <= inner[0] and outer[1] <= inner[1] and outer[2] >= inner[2] and outer[3] >= inner[3]


def expand(box, meters):
    import math
    dlat = meters / 111320.0
    dlon = meters / (111320.0 * max(0.2, math.cos(math.radians((box[0] + box[2]) / 2))))
    return (box[0] - dlat, box[1] - dlon, box[2] + dlat, box[3] + dlon)


class StateLocator(object):
    """The state of a point: of its own DHID; else of the nearest point with a DHID within
    about 15 km whose state box holds the point (a place outside the box of a state is not in
    that state: without this a village of Thuringia next to a Hessian stop went to Hessen,
    whose build does not reach it, and was lost from every pack); else the first state box
    (STATE_ORDER) containing it; else None."""

    CELL = 0.05  # degrees, about 5.5 km north-south

    def __init__(self):
        self.grid = {}

    @staticmethod
    def own(lat, lon, code):
        """[code] where it is a state whose box holds the point, else None: a DHID of a state
        far away is a mapping error (de:05156:... of Mettmann on stops in the Harz)."""
        if code in STATE_OF_CODE and in_box(STATES[STATE_OF_CODE[code]][1], lat, lon):
            return code
        return None

    def add(self, lat, lon, code):
        if self.own(lat, lon, code):
            self.grid.setdefault((int(lat // self.CELL), int(lon // self.CELL)), []).append((lat, lon, code))

    def nearest_by_state(self, lat, lon):
        """{state code: squared distance in degrees (longitude scaled)} of the nearest point
        with a DHID of each state near [lat, lon] whose state box holds it."""
        cy, cx = int(lat // self.CELL), int(lon // self.CELL)
        best = {}
        holds = {}
        for dy in (-2, -1, 0, 1, 2):
            for dx in (-3, -2, -1, 0, 1, 2, 3):
                for plat, plon, pcode in self.grid.get((cy + dy, cx + dx), ()):
                    ok = holds.get(pcode)
                    if ok is None:
                        ok = holds[pcode] = in_box(STATES[STATE_OF_CODE[pcode]][1], lat, lon)
                    if not ok:
                        continue
                    d = (plat - lat) ** 2 + ((plon - lon) * 0.63) ** 2
                    if pcode not in best or d < best[pcode]:
                        best[pcode] = d
        return best

    def locate(self, lat, lon, code=None):
        if self.own(lat, lon, code):
            return code
        best = self.nearest_by_state(lat, lon)
        if best:
            return min(best, key=best.get)
        for slug in STATE_ORDER:
            if in_box(STATES[slug][1], lat, lon):
                return STATES[slug][0]
        return None

    def near_state(self, lat, lon, state, band_meters, code=None):
        """True when a place of no DHID of its own ([code] None) may lie in [state]: its
        nearest DHID of that state is at most [band_meters] farther than its nearest DHID of
        any state. The state builds take such places on both sides of a border (the app
        drops the second copy where it loads both packs), since the extracts of two states
        differ near the border and could each place them in the other."""
        if self.own(lat, lon, code):
            return code == state
        best = self.nearest_by_state(lat, lon)
        if state not in best:
            return self.locate(lat, lon) == state
        to_degrees = band_meters / 111320.0
        nearest = min(best.values()) ** 0.5
        return best[state] ** 0.5 <= nearest + to_degrees
