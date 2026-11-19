/*
 * Next Stop web pages: the logic both pages share (web/live and web/route). Plain functions
 * without a DOM: the link codec, the parsers of the two timetable APIs (BVG HAFAS REST and
 * Transitous MOTIS), geometry, line colours, clock and freshness texts, and small motion helpers.
 *
 * Loaded by the pages as a classic script before their own app.js (as window.NextStopCore) and
 * by the Node tests with require(). No build step, no framework.
 */
(function (root) {
'use strict';

const LIMITS = { legs: 20, text: 120, id: 400 };

const BVG_URL = 'https://v6.bvg.transport.rest';
const TRANSITOUS_URL = 'https://api.transitous.org';
const MAP_STYLE = { light: 'https://tiles.openfreemap.org/styles/positron', dark: 'https://tiles.openfreemap.org/styles/dark' };

/** Base64url (no padding) to bytes. */
function base64UrlBytes(text) {
  let b64 = String(text).replace(/-/g, '+').replace(/_/g, '/');
  while (b64.length % 4) b64 += '=';
  if (typeof atob === 'function') {
    const binary = atob(b64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    return bytes;
  }
  return Uint8Array.from(Buffer.from(b64, 'base64'));
}

/** Base64url (no padding) to a UTF-8 string. */
function fromBase64Url(text) {
  return new TextDecoder('utf-8', { fatal: true }).decode(base64UrlBytes(text));
}

// The most a compressed link may unpack to: far beyond any real journey, against made-up links.
const MAX_LINK_BYTES = 256 * 1024;

/**
 * Raw deflate data (RFC 1951, as java.util.zip.Deflater(nowrap = true) writes it) unpacked to a
 * UTF-8 string by the browser itself (DecompressionStream). Rejects with Error("unsupported") in
 * browsers without it and with Error("data") for broken data.
 */
async function inflateRaw(bytes) {
  let stream;
  try {
    stream = new Blob([bytes]).stream().pipeThrough(new DecompressionStream('deflate-raw'));
  } catch (e) {
    throw new Error('unsupported');
  }
  const reader = stream.getReader();
  const parts = [];
  let size = 0;
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.length;
      if (size > MAX_LINK_BYTES) throw new Error('too large');
      parts.push(value);
    }
  } catch (e) {
    reader.cancel().catch(() => {});
    throw new Error('data');
  }
  const all = new Uint8Array(size);
  let at = 0;
  for (const part of parts) {
    all.set(part, at);
    at += part.length;
  }
  try {
    return new TextDecoder('utf-8', { fatal: true }).decode(all);
  } catch (e) {
    throw new Error('data');
  }
}

/**
 * The parts of a link fragment ("#v=2&l=de&d=..."): the format version, the sender's language
 * ("l", null when absent) and the payload as text: v1 base64url JSON, v2 the same JSON
 * raw-deflated before base64url. Rejects with Error("version"), Error("data") or
 * Error("unsupported") (no DecompressionStream in this browser).
 */
async function readLinkFragment(hash) {
  const params = new URLSearchParams(String(hash || '').replace(/^#/, ''));
  const version = params.get('v');
  const lang = /^[a-z]{2,3}$/.test(params.get('l') || '') ? params.get('l') : null;
  const data = params.get('d') || '';
  if (version === '1') {
    try {
      return { version: 1, lang, text: fromBase64Url(data) };
    } catch (e) {
      throw new Error('data');
    }
  }
  if (version !== '2') throw new Error('version');
  let bytes;
  try {
    bytes = base64UrlBytes(data);
  } catch (e) {
    throw new Error('data');
  }
  if (bytes.length === 0) throw new Error('data');
  return { version: 2, lang, text: await inflateRaw(bytes) };
}

/**
 * The texts of the page language: the recipient's browser language when it is German or
 * English, else the sender's language from the link ([sender], "de" or "en"), else English.
 */
function pickLanguage(browser, sender) {
  const own = String(browser || '').toLowerCase();
  if (/^de\b/.test(own)) return 'de';
  if (/^en\b/.test(own)) return 'en';
  return sender === 'de' ? 'de' : 'en';
}

/** A UTF-8 string to base64url without padding (used by the tests; the app encodes in Kotlin). */
function toBase64Url(text) {
  const bytes = new TextEncoder().encode(text);
  let b64;
  if (typeof btoa === 'function') {
    let binary = '';
    for (const b of bytes) binary += String.fromCharCode(b);
    b64 = btoa(binary);
  } else {
    b64 = Buffer.from(bytes).toString('base64');
  }
  return b64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function str(value, max) {
  return typeof value === 'string' && value.length > 0 ? value.slice(0, max || LIMITS.text) : null;
}
function num(value) {
  return typeof value === 'number' && isFinite(value) ? value : null;
}
function coord(lat, lon) {
  const a = num(lat);
  const o = num(lon);
  return a !== null && o !== null && Math.abs(a) <= 90 && Math.abs(o) <= 180 ? [a, o] : null;
}

function ms(iso) {
  if (!iso) return null;
  const t = Date.parse(iso);
  return isNaN(t) ? null : t;
}

/**
 * A trip of the BVG API (hafas-rest-api v6, /trips/:id?polyline=true&stopovers=true) as
 * { stops: [{ id, name, pos, arr, dep, parr, pdep, cancelled, arrPlatform, depPlatform, parrPlatform,
 *   pdepPlatform, realtime }], path: [[lat, lon]], current, cancelled, line, direction }.
 */
function parseBvgTrip(root) {
  const trip = root && root.trip ? root.trip : root;
  if (!trip || !Array.isArray(trip.stopovers)) throw new Error('trip');
  const stops = trip.stopovers.map((s) => {
    const stop = s.stop || {};
    const loc = stop.location || {};
    return {
      id: stop.id ? String(stop.id) : null,
      name: stop.name || '?',
      pos: coord(loc.latitude, loc.longitude),
      arr: ms(s.arrival),
      dep: ms(s.departure),
      parr: ms(s.plannedArrival),
      pdep: ms(s.plannedDeparture),
      cancelled: !!s.cancelled,
      // Platforms as announced now and as planned; whether HAFAS has a prognosis for this stop.
      arrPlatform: s.arrivalPlatform ? String(s.arrivalPlatform) : null,
      depPlatform: s.departurePlatform ? String(s.departurePlatform) : null,
      parrPlatform: s.plannedArrivalPlatform ? String(s.plannedArrivalPlatform) : null,
      pdepPlatform: s.plannedDeparturePlatform ? String(s.plannedDeparturePlatform) : null,
      realtime: typeof s.departureDelay === 'number' || typeof s.arrivalDelay === 'number',
    };
  });
  const path = [];
  const features = trip.polyline && Array.isArray(trip.polyline.features) ? trip.polyline.features : [];
  for (const f of features) {
    const c = f && f.geometry && f.geometry.coordinates;
    const p = Array.isArray(c) ? coord(c[1], c[0]) : null;
    if (p) path.push(p);
  }
  const cl = trip.currentLocation;
  return {
    stops,
    path,
    current: cl ? coord(cl.latitude, cl.longitude) : null,
    cancelled: !!trip.cancelled,
    line: trip.line && trip.line.name ? String(trip.line.name) : null,
    direction: trip.direction ? String(trip.direction) : null,
    product: trip.line && typeof trip.line.product === 'string' ? trip.line.product : null,
    color: null,
  };
}

/** A Google encoded polyline with [precision] decimal places as [[lat, lon]]. */
function decodePolyline(encoded, precision) {
  const factor = Math.pow(10, precision);
  const points = [];
  let index = 0;
  let lat = 0;
  let lon = 0;
  while (index < encoded.length) {
    for (let k = 0; k < 2; k++) {
      let result = 0;
      let shift = 0;
      let b;
      do {
        if (index >= encoded.length) return points;
        b = encoded.charCodeAt(index++) - 63;
        result |= (b & 0x1f) << shift;
        shift += 5;
      } while (b >= 0x20 && shift < 35);
      const delta = result & 1 ? ~(result >> 1) : result >> 1;
      if (k === 0) lat += delta;
      else lon += delta;
    }
    points.push([lat / factor, lon / factor]);
  }
  return points;
}

/** The shape of a MOTIS leg; tries the named precision, then 7, 6, 5 near the start of the leg. */
function decodeGeometry(geometry, from) {
  if (!geometry || typeof geometry.points !== 'string') return [];
  const start = from ? coord(from.lat, from.lon) : null;
  const tried = [geometry.precision, 7, 6, 5].filter((p, i, all) => typeof p === 'number' && all.indexOf(p) === i);
  for (const precision of tried) {
    const shape = decodePolyline(geometry.points, precision);
    const first = shape[0];
    if (!first) continue;
    if (start ? Math.abs(first[0] - start[0]) < 0.05 && Math.abs(first[1] - start[1]) < 0.08
      : Math.abs(first[0]) <= 90 && Math.abs(first[1]) <= 180) return shape;
  }
  return decodePolyline(geometry.points, tried[0] || 7);
}

/** The product of the app (as HAFAS names it) for a MOTIS mode. */
function motisProduct(mode) {
  const m = String(mode || '');
  if (m === 'SUBWAY' || m === 'METRO') return 'subway';
  if (m === 'SUBURBAN') return 'suburban';
  if (m === 'TRAM' || m === 'CABLE_CAR' || m === 'FUNICULAR') return 'tram';
  if (m === 'COACH') return 'coach';
  if (m === 'BUS' || m === 'ODM') return 'bus';
  if (m === 'FERRY') return 'ferry';
  if (m === 'REGIONAL_RAIL' || m === 'REGIONAL_FAST_RAIL' || m === 'RAIL') return 'regional';
  if (m === 'HIGHSPEED_RAIL' || m === 'LONG_DISTANCE' || m === 'NIGHT_RAIL') return 'express';
  return null;
}

/** A trip of Transitous (MOTIS /api/v6/trip?tripId=...) in the same form as parseBvgTrip. */
function parseTransitousTrip(root) {
  const legs = root && Array.isArray(root.legs) ? root.legs : null;
  if (!legs) throw new Error('trip');
  const rides = legs.filter((l) => l && l.mode && !/^(WALK|BIKE|CAR|FLEX|RENTAL|ODM)/.test(l.mode));
  const stops = [];
  const path = [];
  let cancelled = false;
  for (const leg of rides) {
    if (leg.cancelled) cancelled = true;
    const places = [leg.from].concat(leg.intermediateStops || [], [leg.to]).filter(Boolean);
    for (const p of places) {
      const id = p.stopId ? String(p.stopId) : null;
      const prev = stops[stops.length - 1];
      const entry = {
        id,
        name: p.name || '?',
        pos: coord(p.lat, p.lon),
        arr: ms(p.arrival),
        dep: ms(p.departure),
        parr: ms(p.scheduledArrival),
        pdep: ms(p.scheduledDeparture),
        cancelled: !!p.cancelled,
        // MOTIS names one track per stop: the same for arriving and leaving.
        arrPlatform: p.track ? String(p.track) : null,
        depPlatform: p.track ? String(p.track) : null,
        parrPlatform: p.scheduledTrack ? String(p.scheduledTrack) : null,
        pdepPlatform: p.scheduledTrack ? String(p.scheduledTrack) : null,
        realtime: !!leg.realTime,
      };
      if (prev && id && prev.id === id) {
        if (entry.dep !== null) prev.dep = entry.dep;
        if (entry.pdep !== null) prev.pdep = entry.pdep;
        if (entry.depPlatform) prev.depPlatform = entry.depPlatform;
        if (entry.pdepPlatform) prev.pdepPlatform = entry.pdepPlatform;
      } else {
        stops.push(entry);
      }
    }
    for (const point of decodeGeometry(leg.legGeometry, leg.from)) path.push(point);
  }
  const first = rides[0] || {};
  return {
    stops,
    path,
    current: null,
    cancelled,
    line: canonicalLine(first.routeShortName || first.displayName || null, motisProduct(first.mode)),
    direction: first.headsign || null,
    product: motisProduct(first.mode),
    color: /^[0-9a-f]{6}$/i.test(first.routeColor || '') ? '#' + first.routeColor : null,
  };
}

function distance(a, b) {
  const rad = Math.PI / 180;
  const dLat = (b[0] - a[0]) * rad;
  const dLon = (b[1] - a[1]) * rad * Math.cos(((a[0] + b[0]) / 2) * rad);
  return Math.sqrt(dLat * dLat + dLon * dLon) * 6371000;
}

// A way reaches a stop once it comes this close (metres), and has passed it once it runs this much
// farther off than its nearest point there (as TripMatch in the app).
const LOCK_METRES = 150;
const LEAVE_METRES = 2000;

/**
 * The point of [path] from [from] on nearest to [point], on the first pass of the way that comes
 * within LOCK_METRES: a ring line (S41/S42) passes the same place again a lap later, and the
 * nearest point anywhere may lie on that later pass.
 */
function nearestIndex(path, point, from) {
  let best = -1;
  let bestDistance = Infinity;
  for (let i = from || 0; i < path.length; i++) {
    const d = distance(path[i], point);
    if (d < bestDistance) {
      bestDistance = d;
      best = i;
    } else if (bestDistance <= LOCK_METRES && d > bestDistance + LEAVE_METRES) {
      break;
    }
  }
  return best;
}

/** The point at [fraction] (0..1) of the way along [points]. */
function along(points, fraction) {
  if (points.length === 0) return null;
  if (points.length === 1 || fraction <= 0) return points[0];
  if (fraction >= 1) return points[points.length - 1];
  let total = 0;
  for (let i = 1; i < points.length; i++) total += distance(points[i - 1], points[i]);
  let left = total * fraction;
  for (let i = 1; i < points.length; i++) {
    const d = distance(points[i - 1], points[i]);
    if (left <= d && d > 0) {
      const f = left / d;
      return [points[i - 1][0] + (points[i][0] - points[i - 1][0]) * f, points[i - 1][1] + (points[i][1] - points[i - 1][1]) * f];
    }
    left -= d;
  }
  return points[points.length - 1];
}

/** The piece of [path] between two positions, or the straight line when there is no path. */
function cutPath(path, a, b) {
  if (!a || !b) return [];
  if (!path || path.length < 2) return [a, b];
  const i = nearestIndex(path, a, 0);
  const j = nearestIndex(path, b, i);
  if (i < 0 || j <= i) return [a, b];
  return [a].concat(path.slice(i + 1, j), [b]);
}

function expectedTime(stop, key) {
  // The live time, else the planned one.
  if (key === 'arr') return stop.arr !== null ? stop.arr : stop.parr !== null ? stop.parr : stop.dep;
  return stop.dep !== null ? stop.dep : stop.pdep !== null ? stop.pdep : stop.arr;
}

/**
 * The piece of [path] (the way of a whole run) from stop [i] to stop [j] of its [stops], each stop
 * on its own pass of the way (see nearestIndex); null where the way does not tell them apart.
 */
function cutPathBetween(path, stops, i, j) {
  if (!path || path.length < 2 || i < 0 || j <= i || j >= stops.length) return null;
  let from = 0;
  let a = -1;
  for (let k = 0; k <= j; k++) {
    if (stops[k].pos) from = Math.max(from, nearestIndex(path, stops[k].pos, from));
    if (k === i) a = from;
  }
  if (from <= a) return null;
  // From the stop itself to the stop itself, as cutPath.
  return [stops[i].pos || path[a]].concat(path.slice(a + 1, from), [stops[j].pos || path[from]]);
}

/**
 * Index of the stop of [live] that matches a place of the payload: by id, by name, by position.
 * A ring line calls at one stop more than once: with [key] ('dep' or 'arr') the call nearest to
 * the time of the place (planned, and as expected with its delay) wins, else the first.
 */
function matchStop(stops, place, after, key) {
  const start = after == null ? 0 : after + 1;
  const plainId = (id) => (id ? String(id).replace(/^tr:/, '') : null);
  const planned = key && place.planned != null ? place.planned : null;
  const expected = planned !== null ? planned + (place.delay || 0) * 60000 : null;
  const gap = (stop) => {
    const plan = key === 'arr' ? (stop.parr != null ? stop.parr : stop.pdep) : (stop.pdep != null ? stop.pdep : stop.parr);
    const now = expectedTime(stop, key);
    let g = Infinity;
    if (plan != null) g = Math.abs(plan - planned);
    if (now != null) g = Math.min(g, Math.abs(now - expected));
    return g;
  };
  const pick = (same) => {
    let best = -1;
    let bestGap = Infinity;
    for (let i = start; i < stops.length; i++) {
      if (!same(stops[i])) continue;
      if (planned === null) return i;
      if (best < 0) best = i;
      const g = gap(stops[i]);
      if (g < bestGap) {
        bestGap = g;
        best = i;
      }
    }
    return best;
  };
  const id = plainId(place.id);
  if (id) {
    const byId = pick((stop) => plainId(stop.id) === id);
    if (byId >= 0) return byId;
  }
  const name = (place.name || '').toLowerCase();
  const byName = pick((stop) => stop.name.toLowerCase() === name);
  if (byName >= 0) return byName;
  if (place.pos) {
    let best = -1;
    let bestD = 400; // metres
    for (let i = start; i < stops.length; i++) {
      if (!stops[i].pos) continue;
      const d = distance(stops[i].pos, place.pos);
      if (d < bestD) {
        bestD = d;
        best = i;
      }
    }
    return best;
  }
  return -1;
}

/** The URL that gives the live data of a leg. */
function tripUrl(leg) {
  const id = encodeURIComponent(leg.trip.replace(/^tr:/, ''));
  if (leg.provider === 'tr') return TRANSITOUS_URL + '/api/v6/trip?tripId=' + id + '&detailedLegs=true';
  return BVG_URL + '/trips/' + id + '?polyline=true&stopovers=true&remarks=false';
}

// The colours of the app (data/LineColors.kt, ui/Components.kt): S- and U-Bahn of Berlin's
// network map, which belong to a U-Bahn named U... or an S-Bahn named S... in the VBB area only;
// then by kind and product.
// The U-Bahn colours are those of the BVG's current line signs.
const LINE_COLORS = {
  U1: '#419328', U2: '#ED3C12', U3: '#016144', U4: '#FFC101', U5: '#603B2C', U6: '#504F87',
  U7: '#0279BD', U8: '#013F75', U9: '#F96815',
  S1: '#DA6BA2', S2: '#007734', S25: '#007734', S26: '#007734', S3: '#0066AD', S41: '#AD5937',
  S42: '#CB6418', S45: '#CD9C53', S46: '#CD9C53', S47: '#CD9C53', S5: '#EB7405', S7: '#816DA6',
  S75: '#816DA6', S8: '#66AA22', S85: '#66AA22', S9: '#992746',
};
const SUBWAY_SIGN_COLORS = Object.keys(LINE_COLORS).filter((k) => k.charAt(0) === 'U').map((k) => LINE_COLORS[k]);
const PRODUCT_COLORS = {
  subway: '#115D91', suburban: '#2D8B3C', tram: '#BE1414', bus: '#95276E', ferry: '#528DBA',
  regional: '#DA251D', express: '#4A4F57', coach: '#2F6F73',
};
const BERLIN_TRAMS = ['M1', 'M2', 'M4', 'M5', 'M6', 'M8', 'M10', 'M13', 'M17', '12', '16', '18', '21', '27', '37', '50', '60', '61', '62', '63', '67', '68'];
const LONG_DISTANCE = /^(ICE|IC|EC|ECE|EN|NJ|RJ|RJX|TGV|ES|EST|D|FLX)\b/;

// The area of the VBB (Berlin and Brandenburg) as a rough outline, as VBB_OUTLINE in
// data/RoutingSource.kt: [lat, lon].
const VBB_OUTLINE = [
  [53.05, 11.25], [53.34, 11.58], [53.38, 12.30], [53.27, 12.75], [53.22, 13.20],
  [53.42, 13.70], [53.40, 14.25], [53.31, 14.47], [52.95, 14.20], [52.60, 14.67],
  [52.30, 14.64], [51.85, 14.80], [51.53, 14.77], [51.41, 14.20], [51.35, 13.60],
  [51.41, 13.14], [51.75, 13.10], [51.95, 12.78], [52.10, 12.38], [52.25, 12.18],
  [52.45, 12.23], [52.65, 12.18], [52.85, 12.13], [52.95, 11.78],
];

/** True inside the area of the VBB, for a place [lat, lon]. */
function inVbbArea(pos) {
  if (!Array.isArray(pos) || pos.length < 2) return false;
  const lat = pos[0];
  const lon = pos[1];
  let inside = false;
  let prev = VBB_OUTLINE[VBB_OUTLINE.length - 1];
  for (const point of VBB_OUTLINE) {
    if ((point[0] > lat) !== (prev[0] > lat) &&
      lon < (prev[1] - point[1]) * (lat - point[0]) / (prev[0] - point[0]) + point[1]) inside = !inside;
    prev = point;
  }
  return inside;
}

/**
 * True where a ride at [pos] runs in the VBB area, whose names and colours the pages know by
 * themselves. Without a place the pages' home counts, as in the app.
 */
function inHomeArea(pos) {
  return !Array.isArray(pos) || inVbbArea(pos);
}

// Kinds of trains written in capitals in front of a number (data/Products.kt): "rj 171" is RJ 171.
// Kinds some networks write in small letters on purpose (Czech "Os", "Sp", "Ex", "R") are not here.
const CANONICAL_KINDS = ['ICE', 'IC', 'EC', 'ECE', 'EN', 'NJ', 'RJ', 'RJX', 'TGV', 'ES', 'EST', 'FLX', 'THA', 'EUR',
  'IRE', 'RE', 'RB', 'IR', 'MEX', 'FEX'];
const TRAIN_KIND_PRODUCTS = ['express', 'regional', 'coach'];

/**
 * [line] with the kind of a train in front written as everywhere ("rj 171" as "RJ 171"), for
 * trains, coaches and rides of unknown [product]; other names (a bus "Bus 585", "S 1", "Os 6123")
 * stay as they are.
 */
function canonicalLine(line, product) {
  if (typeof line !== 'string') return line;
  if (product && TRAIN_KIND_PRODUCTS.indexOf(product) < 0) return line;
  const m = /^(\p{L}{2,4})(?=$|[\s\d])/u.exec(line);
  if (!m) return line;
  const kind = m[1].toUpperCase();
  if (CANONICAL_KINDS.indexOf(kind) < 0 || kind === m[1]) return line;
  return kind + line.slice(m[1].length);
}

/**
 * The product of a line from its name alone, or null. The names of Berlin's network (U8, S41,
 * M10, N60, 100) count only for a ride at [pos] in the VBB area (or of unknown place): a train
 * "U1" in another country is no U-Bahn.
 */
function guessProduct(line, pos) {
  const name = String(line || '').replace(/\s+/g, '').toUpperCase();
  if (/^(RE|RB)\d/.test(name)) return 'regional';
  if (LONG_DISTANCE.test(String(line || '').toUpperCase())) return 'express';
  if (!inHomeArea(pos)) return null;
  if (/^U\d/.test(name)) return 'subway';
  if (/^S\d/.test(name)) return 'suburban';
  if (BERLIN_TRAMS.indexOf(name) >= 0) return 'tram';
  if (/^F\d/.test(name)) return 'ferry';
  if (/^([MNX]\d+|\d{3})$/.test(name)) return 'bus';
  return null;
}

/**
 * The colour of the badge of a ride of [line] of [product] at [pos] ([lat, lon], where it is
 * boarded), as in the app (LineColors.rideStyle): for a U-Bahn of Berlin the colour of the BVG's
 * line sign, before any colour a feed sends; else the colour its feed sends for this very ride;
 * Berlin's network colours for an S-Bahn named S... in the VBB area; then ICE red, FlixTrain
 * green, and the colour of the product.
 */
function lineColor(line, product, feedColor, pos) {
  const kind = product || guessProduct(line, pos);
  const name = String(line || '').replace(/\s+/g, '').toUpperCase();
  if (kind === 'subway' && name.charAt(0) === 'U' && LINE_COLORS[name] && inHomeArea(pos)) return LINE_COLORS[name];
  if (feedColor && /^#[0-9a-f]{6}$/i.test(feedColor)) return feedColor;
  const letter = kind === 'subway' ? 'U' : kind === 'suburban' ? 'S' : null;
  if (letter && name.charAt(0) === letter && LINE_COLORS[name] && inHomeArea(pos)) return LINE_COLORS[name];
  const upper = String(line || '').toUpperCase();
  if (/^ICE\b/.test(upper)) return '#EC0016';
  if (/^FLX\b/.test(upper) || (kind === 'coach' && /FLIX/.test(upper))) return '#97D700';
  return PRODUCT_COLORS[kind] || '#5A6072';
}

/**
 * Dark or white ink on a badge of [hex]. On the colour of a Berlin U-Bahn line the ink of the
 * BVG's sign: white, black on the U4's yellow.
 */
function inkOn(hex) {
  const m = /^#?([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})$/i.exec(hex || '');
  if (!m) return '#ffffff';
  const upper = '#' + (m[1] + m[2] + m[3]).toUpperCase();
  if (upper === LINE_COLORS.U4) return '#000000';
  if (SUBWAY_SIGN_COLORS.indexOf(upper) >= 0) return '#ffffff';
  const lin = (v) => {
    const c = parseInt(v, 16) / 255;
    return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  };
  const l = 0.2126 * lin(m[1]) + 0.7152 * lin(m[2]) + 0.0722 * lin(m[3]);
  return l > 0.45 ? '#16130A' : '#ffffff';
}

/** How long the route takes to draw itself in when the page opens, in milliseconds. */
const ROUTE_DRAW_MS = 1400;

/** The first number in [text] ("in 23 min" → 23, "14:05" → 845 minutes of the day), else null. */
function textNumber(text) {
  const clockMatch = /(\d{1,2}):(\d{2})/.exec(String(text || ''));
  if (clockMatch) return Number(clockMatch[1]) * 60 + Number(clockMatch[2]);
  const hoursMatch = /(\d+)\s*h\s*(\d+)/.exec(String(text || ''));
  if (hoursMatch) return Number(hoursMatch[1]) * 60 + Number(hoursMatch[2]);
  const match = /-?\d+/.exec(String(text || ''));
  return match ? Number(match[0]) : null;
}

/**
 * Which way a changing text rolls: up (the new one comes from below) when its number got
 * smaller or stayed, as a countdown does; down when it grew, as a later arrival.
 */
function rollsUp(before, after) {
  const a = textNumber(before);
  const b = textNumber(after);
  return a == null || b == null || b <= a;
}

/** The length of a line of [lon, lat] points, in about metres (flat earth, good for shares of a city trip). */
function lineLength(coordinates) {
  let total = 0;
  for (let i = 1; i < coordinates.length; i++) {
    const [x0, y0] = coordinates[i - 1];
    const [x1, y1] = coordinates[i];
    const kx = 111320 * Math.cos(((y0 + y1) / 2) * Math.PI / 180);
    total += Math.hypot((x1 - x0) * kx, (y1 - y0) * 110540);
  }
  return total;
}

/**
 * The route as far as [fraction] (0 to 1) of its whole length, in the order of [features]
 * (GeoJSON line features, from the start of the trip to its end): the snake of the route
 * drawing itself in. Lines beyond the head are left out, the one at the head is cut there.
 */
function snakeFeatures(features, fraction) {
  if (fraction >= 1) return features;
  const lengths = features.map((f) => lineLength(f.geometry.coordinates));
  const total = lengths.reduce((a, b) => a + b, 0);
  let left = Math.max(0, fraction) * total;
  const out = [];
  for (let i = 0; i < features.length && left > 0; i++) {
    const coords = features[i].geometry.coordinates;
    if (lengths[i] <= left) {
      out.push(features[i]);
      left -= lengths[i];
      continue;
    }
    const cut = [coords[0]];
    for (let j = 1; j < coords.length && left > 0; j++) {
      const piece = lineLength([coords[j - 1], coords[j]]);
      if (piece <= left) {
        cut.push(coords[j]);
        left -= piece;
      } else {
        const k = piece > 0 ? left / piece : 0;
        cut.push([coords[j - 1][0] + (coords[j][0] - coords[j - 1][0]) * k, coords[j - 1][1] + (coords[j][1] - coords[j - 1][1]) * k]);
        left = 0;
      }
    }
    if (cut.length > 1) out.push({ type: 'Feature', properties: features[i].properties, geometry: { type: 'LineString', coordinates: cut } });
    break;
  }
  return out;
}

/** An ease out with a soft end, for the snake of the route. */
function easeOutCubic(t) {
  const k = Math.min(1, Math.max(0, t));
  return 1 - Math.pow(1 - k, 3);
}

/** "vor 12 s", "vor 3 min", "gerade eben". */
function ageText(T, ageMs) {
  const s = Math.max(0, Math.round(ageMs / 1000));
  if (s < 10) return T.justNow;
  if (s < 60) return T.agoS(s);
  const m = Math.floor(s / 60);
  if (m < 60) return T.agoM(m);
  return T.agoH(Math.floor(m / 60));
}

/**
 * How fresh the live data is: 'plan' (nothing to ask), 'loading', 'none' (never got any),
 * 'stale' (the last requests failed and the last answer is over a minute old) or 'fresh'.
 */
function freshness(lastOk, lastFail, pollable, now) {
  if (!pollable) return { kind: 'plan', age: null };
  if (lastOk == null) return { kind: lastFail == null ? 'loading' : 'none', age: null };
  const age = now - lastOk;
  if (lastFail != null && lastFail > lastOk && age > 60000) return { kind: 'stale', age };
  return { kind: 'fresh', age };
}

function freshnessText(T, f) {
  switch (f.kind) {
    case 'plan': return T.planOnly;
    case 'loading': return T.loading;
    case 'none': return T.noLive;
    case 'stale': return T.stale(Math.max(1, Math.floor(f.age / 60000)));
    default: return T.updated(ageText(T, f.age));
  }
}

const CLOCK = typeof Intl !== 'undefined'
  ? new Intl.DateTimeFormat('de-DE', { hour: '2-digit', minute: '2-digit', timeZone: 'Europe/Berlin' })
  : null;
function clock(t) {
  return t == null ? '' : CLOCK ? CLOCK.format(new Date(t)) : new Date(t).toISOString().slice(11, 16);
}

function delayText(T, minutes) {
  if (minutes > 0) return T.late(minutes);
  if (minutes < 0) return T.early(-minutes);
  return T.onTime;
}

/** 'ok' (on time or early), 'warn' (1–4 min late) or 'bad' (5 min and more). */
function delayLevel(minutes) {
  return minutes >= 5 ? 'bad' : minutes > 0 ? 'warn' : 'ok';
}

/** [[lat, lon]] as a Google encoded polyline with [precision] decimal places (the inverse of decodePolyline). */
function encodePolyline(points, precision) {
  const factor = Math.pow(10, precision == null ? 5 : precision);
  let out = '';
  let lastLat = 0;
  let lastLon = 0;
  const put = (value) => {
    let v = value < 0 ? ~(value << 1) : value << 1;
    while (v >= 0x20) {
      out += String.fromCharCode((0x20 | (v & 0x1f)) + 63);
      v >>= 5;
    }
    out += String.fromCharCode(v + 63);
  };
  for (const p of points) {
    const lat = Math.round(p[0] * factor);
    const lon = Math.round(p[1] * factor);
    put(lat - lastLat);
    put(lon - lastLon);
    lastLat = lat;
    lastLon = lon;
  }
  return out;
}

/* ---------- Look of both pages: counter, progress bar, swipe sheet, app links ---------- */

/** The app on Google Play: the store page, for a button "Bei Google Play laden". */
const APP_PACKAGE = 'io.github.jnbp.nextstop';
const PLAY_URL = 'https://play.google.com/store/apps/details?id=' + APP_PACKAGE;

/**
 * Minutes as the app writes them: "5′" below an hour, "1:20 h" from an hour on. Null for no
 * value; negative values count as 0.
 */
function minutesMark(minutes) {
  if (minutes == null || !Number.isFinite(minutes)) return null;
  const m = Math.max(0, Math.round(minutes));
  if (m < 60) return m + '′';
  return Math.floor(m / 60) + ':' + String(m % 60).padStart(2, '0') + ' h';
}

/**
 * The counter of the hero card: the minutes until [t] (rounded up, like a departure board), or
 * [nowText] in the last 45 s; empty once [t] has passed.
 */
function counterText(t, now, nowText) {
  if (t == null || now >= t) return '';
  if (t - now < 45000) return nowText;
  return minutesMark(Math.ceil((t - now) / 60000));
}

/**
 * The bar of a whole trip in pieces, one per leg: where each starts and how wide it is (both
 * 0..1 of the time from the first departure to the last arrival), its colour and whether it is a
 * walk. [legs]: [{ dep, arr, color, walk }] in order. A gap between legs (a wait) belongs to no
 * piece; a leg of no length still gets a sliver so it stays visible.
 */
function progressSegments(legs) {
  if (!legs || !legs.length) return [];
  const start = legs[0].dep;
  const end = legs[legs.length - 1].arr;
  const span = end - start;
  const f = (t) => (span > 0 ? Math.max(0, Math.min(1, (t - start) / span)) : 0);
  return legs.map((l) => {
    const left = f(l.dep);
    const width = Math.max(0.004, f(l.arr) - left);
    return { left, width: Math.min(width, 1 - left), color: l.walk ? null : l.color || null, walk: !!l.walk };
  });
}

/**
 * Where a swipe sheet comes to rest after a drag: 0 (open) or [closed] (the offset of its
 * collapsed position, in px from the top of the open sheet). [offset] is where the drag let go,
 * [velocity] its speed in px per ms (down positive): a flick decides, otherwise the nearer end.
 */
function sheetRest(offset, velocity, closed) {
  if (!(closed > 0)) return 0;
  if (velocity > 0.35) return closed;
  if (velocity < -0.35) return 0;
  return offset > closed / 2 ? closed : 0;
}

/**
 * What a drag on the head of a swipe sheet does (#262), decided once it has moved [dy] px (down
 * positive): 'sheet' moves the sheet, 'scroll' scrolls its content. The handle row always moves
 * the sheet. The hero ([handle] 'hero') moves the collapsed sheet either way and the open one
 * down while its content is at the top ([scrollTop] 0), as a native bottom sheet; pushed up on
 * the open sheet, or down while the content is scrolled, it scrolls the content instead.
 */
function sheetDragMode(handle, open, dy, scrollTop) {
  if (handle !== 'hero' || !open) return 'sheet';
  if (dy > 0 && !(scrollTop > 0)) return 'sheet';
  return 'scroll';
}

/** An offset of a sheet held within 0..[closed], with a soft give beyond both ends. */
function sheetClamp(offset, closed) {
  if (offset < 0) return offset / 3;
  if (offset > closed) return closed + (offset - closed) / 3;
  return offset;
}

/**
 * An Android intent link that opens the app at nextstop://open/[path]?[params] and, when the app
 * is not installed, [fallback] in the browser (the store page by default).
 */
function appIntentUrl(path, params, fallback) {
  const query = params && String(params) ? '?' + String(params) : '';
  const back = fallback === undefined ? PLAY_URL : fallback;
  return 'intent://open' + (path ? '/' + path : '') + query + '#Intent;scheme=nextstop;package=' + APP_PACKAGE + ';' +
    (back ? 'S.browser_fallback_url=' + encodeURIComponent(back) + ';' : '') + 'end';
}

/** Which of the three parts of a train to board ('front', 'middle', 'rear'), as three flags front to rear. */
function boardParts(part) {
  return ['front', 'middle', 'rear'].map((p) => p === part);
}

const api = {
  base64UrlBytes,
  inflateRaw,
  readLinkFragment,
  pickLanguage,
  MAX_LINK_BYTES,
  BVG_URL,
  TRANSITOUS_URL,
  MAP_STYLE,
  fromBase64Url,
  toBase64Url,
  str,
  num,
  coord,
  ms,
  parseBvgTrip,
  decodePolyline,
  decodeGeometry,
  motisProduct,
  parseTransitousTrip,
  distance,
  nearestIndex,
  along,
  cutPath,
  cutPathBetween,
  expectedTime,
  matchStop,
  tripUrl,
  LINE_COLORS,
  PRODUCT_COLORS,
  BERLIN_TRAMS,
  LONG_DISTANCE,
  VBB_OUTLINE,
  inVbbArea,
  inHomeArea,
  canonicalLine,
  guessProduct,
  lineColor,
  inkOn,
  ROUTE_DRAW_MS,
  textNumber,
  rollsUp,
  lineLength,
  snakeFeatures,
  easeOutCubic,
  ageText,
  freshness,
  freshnessText,
  clock,
  delayText,
  delayLevel,
  encodePolyline,
  LIMITS,
  APP_PACKAGE,
  PLAY_URL,
  minutesMark,
  counterText,
  progressSegments,
  sheetRest,
  sheetClamp,
  sheetDragMode,
  appIntentUrl,
  boardParts,
};
if (typeof module !== 'undefined' && module.exports) module.exports = api;
else root.NextStopCore = api;
})(typeof globalThis !== 'undefined' ? globalThis : this);
