/*
 * Next Stop live page: follows a shared journey in the browser of the recipient.
 *
 * The link carries the planned journey in its fragment (#v=1&d=<base64url JSON>), so it never
 * reaches a server log. This page asks the public APIs itself (BVG HAFAS REST and Transitous
 * MOTIS) for the live times of each ride and estimates where the vehicle is from them. It shows
 * the planned trip with live data, not the GPS position of the traveller.
 *
 * The page opens collapsed: a hero card with the arrival, the map with the vehicle moving
 * smoothly along its way, and a timeline where only the current ride is open. "#demo" plays a
 * made-up trip in fast motion without asking any API (see demo.js).
 *
 * No build step, no framework. The pure logic at the top runs in Node as well (see
 * web/test/live.test.js); the DOM part at the bottom only runs in a browser.
 */
'use strict';

// The shared logic of both pages (web/shared/core.js).
const core = typeof module !== 'undefined' && module.exports ? require('../shared/core.js') : window.NextStopCore;
const {
  MAP_STYLE, fromBase64Url, toBase64Url, str, num, coord, ms, parseBvgTrip, decodePolyline,
  parseTransitousTrip, distance, along, cutPath, cutPathBetween, expectedTime, matchStop, tripUrl, guessProduct,
  canonicalLine, lineColor, inkOn, ROUTE_DRAW_MS, textNumber, rollsUp, lineLength, snakeFeatures, easeOutCubic,
  ageText, freshness, freshnessText, clock, delayText, delayLevel, readLinkFragment, pickLanguage,
  counterText, progressSegments, sheetRest, sheetClamp, appIntentUrl, PLAY_URL,
} = core;

// Polling of each running ride: every 20 s while the page is seen, every 60 s while it is hidden.
// BVG allows 100 requests a minute per client; Transitous asks to stay light.
const POLL_MS = { visible: 20000, hidden: 60000 };
const MAX_BACKOFF_MS = 5 * 60000;
// A leg is asked for while it runs, and shortly before it starts.
const LOOKAHEAD_MS = 45 * 60000;
// The link stops following this long after the arrival.
const EXPIRY_MS = 2 * 3600000;
const LIMITS = { legs: 20, text: 120, id: 400 };

/* ---------- Payload ---------- */

/** A place of the payload, checked: name, id, position, time (epoch ms) and delay (minutes). */
function readPlace(raw, timeKey, delayKey) {
  if (!raw || typeof raw !== 'object') return null;
  const time = num(raw[timeKey]);
  return {
    name: str(raw.name) || '?',
    id: str(raw.id, LIMITS.id),
    pos: coord(raw.lat, raw.lon),
    planned: time !== null ? time * 1000 : null,
    delay: num(raw[delayKey]) || 0,
  };
}

/**
 * Reads the fragment of a v1 link ("v=1&d=..."). Returns the checked journey, or throws an
 * Error whose message is one of "version", "data". Links of format v2 need readFragment.
 */
function parseFragment(hash) {
  const params = new URLSearchParams(String(hash || '').replace(/^#/, ''));
  if (params.get('v') !== '1') throw new Error('version');
  let raw;
  try {
    raw = JSON.parse(fromBase64Url(params.get('d') || ''));
  } catch (e) {
    throw new Error('data');
  }
  return parsePayload(raw);
}

/**
 * Reads the fragment of a link of either format: v1 ("v=1&d=<base64url JSON>") or v2
 * ("v=2&l=<language>&d=<base64url of the raw-deflated compact JSON>", see expandLive). Resolves
 * to the checked journey with the sender's language as [lang]; rejects with an Error whose
 * message is "version", "data" or "unsupported" (a browser that cannot unpack v2).
 */
async function readFragment(hash) {
  const link = await readLinkFragment(hash);
  let raw;
  try {
    raw = JSON.parse(link.text);
    if (link.version === 2) raw = expandLive(raw);
  } catch (e) {
    throw new Error('data');
  }
  const journey = parsePayload(raw);
  journey.lang = link.lang;
  return journey;
}

/** The journey of a v1 payload (or a v2 payload made v1 by expandLive), checked. */
function parsePayload(raw) {
  if (!raw || !Array.isArray(raw.legs) || raw.legs.length === 0 || raw.legs.length > LIMITS.legs) {
    throw new Error('data');
  }
  const legs = raw.legs.map((leg) => {
    const walk = leg && leg.walk === true;
    const from = readPlace(leg && leg.from, 'dep', 'dd');
    const to = readPlace(leg && leg.to, 'arr', 'ad');
    if (!from || !to || from.planned === null || to.planned === null) throw new Error('data');
    const provider = leg.p === 'tr' ? 'tr' : leg.p === 'bvg' ? 'bvg' : null;
    return {
      walk,
      provider: walk ? null : provider,
      trip: walk ? null : str(leg.trip, LIMITS.id),
      line: walk ? null : canonicalLine(str(leg.line, 30), null),
      direction: walk ? null : str(leg.dir),
      from,
      to,
    };
  });
  const last = legs[legs.length - 1];
  const dest = raw.dest && typeof raw.dest === 'object' ? raw.dest : {};
  return {
    name: str(raw.n, 40),
    created: num(raw.t) !== null ? raw.t * 1000 : null,
    dest: {
      name: str(dest.name) || last.to.name,
      pos: coord(dest.lat, dest.lon) || last.to.pos,
      // A saved place of the sender: "h" home, "w" work (the page then says "nach Hause").
      kind: dest.k === 'h' || dest.k === 'w' ? dest.k : null,
    },
    legs,
  };
}

/*
 * Format v2 of the live link (written by liveLinkCompactJson in JourneyShare.kt), before
 * deflate: {n, b, c, s, r, o, e, z, k}
 *   n  first name of the sender (optional)
 *   b  planned departure of the first leg, epoch seconds; every other time is seconds after b
 *   c  when the link was made, seconds after b
 *   s  the stops, each once: [name] | [name, id] | [name, id or null, lat, lon] (4 decimals)
 *   r  the legs: a ride {f, t, d, a, dd, ad, p, i, l, r, w}: from and to as indexes into s,
 *      planned departure and arrival, delays (min), provider "bvg"/"tr", trip id, line,
 *      direction, and w: [dep, arr] of the walk before it (from the stop where the leg before it
 *      ends, or for the first leg from the start); a walk that is no such link {k:1, f, t, d, a}
 *   o  name of the start of the first walk (only a stop; never an address or a position)
 *   e  [dep, arr] of the walk after the last leg, to the destination
 *   z  the destination: [name, lat, lon]
 *   k  "h" when the destination is the sender's home, "w" their work
 */

/** A v2 payload as the v1 payload it stands for; throws on anything malformed. */
function expandLive(c) {
  if (!c || typeof c !== 'object' || !Array.isArray(c.s) || !Array.isArray(c.r)) throw new Error('data');
  const base = num(c.b);
  if (base === null || c.r.length === 0 || c.r.length > LIMITS.legs || c.s.length > 2 * LIMITS.legs + 2) throw new Error('data');
  const at = (offset) => {
    if (num(offset) === null) throw new Error('data');
    return base + offset;
  };
  const stop = (index) => {
    const s = Number.isInteger(index) ? c.s[index] : null;
    if (!Array.isArray(s) || typeof s[0] !== 'string') throw new Error('data');
    return { name: s[0], id: s[1] || undefined, lat: s[2], lon: s[3] };
  };
  const place = (index, timeKey, time, delayKey, delay, name) => {
    const p = stop(index);
    const out = { name: name || p.name, id: p.id, lat: p.lat, lon: p.lon };
    out[timeKey] = at(time);
    if (delay) out[delayKey] = delay;
    return out;
  };
  const span = (pair) => {
    if (!Array.isArray(pair) || pair.length !== 2) throw new Error('data');
    return pair;
  };
  const legs = [];
  let lastTo = null; // the stop index where the leg before ends
  c.r.forEach((r) => {
    if (!r || typeof r !== 'object') throw new Error('data');
    if (r.k === 1) {
      legs.push({ walk: true, from: place(r.f, 'dep', r.d), to: place(r.t, 'arr', r.a) });
      lastTo = r.t;
      return;
    }
    if (r.w != null) {
      const [d, a] = span(r.w);
      const origin = lastTo === null;
      const fromStop = origin ? r.f : lastTo;
      legs.push({
        walk: true,
        from: place(fromStop, 'dep', d, null, 0, origin ? str(c.o) : null),
        to: place(r.f, 'arr', a),
      });
    }
    legs.push({
      p: r.p, trip: r.i, line: r.l, dir: r.r,
      from: place(r.f, 'dep', r.d, 'dd', r.dd),
      to: place(r.t, 'arr', r.a, 'ad', r.ad),
    });
    lastTo = r.t;
  });
  const z = Array.isArray(c.z) ? c.z : [];
  const dest = { name: typeof z[0] === 'string' ? z[0] : null, lat: z[1], lon: z[2], k: c.k };
  if (c.e != null) {
    const [d, a] = span(c.e);
    const from = place(lastTo, 'dep', d);
    const to = { name: dest.name || from.name, arr: at(a) };
    if (num(dest.lat) !== null && num(dest.lon) !== null) {
      to.lat = dest.lat;
      to.lon = dest.lon;
    } else {
      to.lat = from.lat;
      to.lon = from.lon;
    }
    legs.push({ walk: true, from, to });
  }
  return { n: c.n, t: num(c.c) !== null ? base + c.c : null, legs, dest };
}

/** A coordinate as the app writes it in v2: 4 decimals (about 10 m). */
function round4(value) {
  return Math.round(value * 1e4) / 1e4;
}

/**
 * The v2 JSON of a v1 payload, written exactly as the app writes it (liveLinkCompactJson in
 * JourneyShare.kt), for the tests and the demo. [extra]: o (start name of the first walk), k.
 */
function encodeLive(raw, extra) {
  const opts = extra || {};
  const stops = [];
  const keys = new Map();
  const stopIndex = (p) => {
    const key = p.id ? 'i:' + p.id + '|' + p.name : 'n:' + p.name + '|' + (p.lat != null ? round4(p.lat) + ',' + round4(p.lon) : '');
    if (keys.has(key)) return keys.get(key);
    const entry = [p.name];
    if (p.lat != null && p.lon != null) entry.push(p.id || null, round4(p.lat), round4(p.lon));
    else if (p.id) entry.push(p.id);
    stops.push(entry);
    keys.set(key, stops.length - 1);
    return stops.length - 1;
  };
  const legs = raw.legs;
  const base = legs[0].from.dep;
  const out = { };
  if (raw.n) out.n = raw.n;
  out.b = base;
  out.c = raw.t - base;
  const rides = [];
  let walkBefore = null;
  let end = null;
  // The same stop: the same id where both have one, else the same name.
  const same = (a, b) => (a.id && b.id ? a.id === b.id : a.name === b.name);
  legs.forEach((l, i) => {
    if (l.walk) {
      const next = legs[i + 1];
      const prev = legs[i - 1];
      const linksFrom = i === 0 || (prev && !prev.walk && same(prev.to, l.from));
      if (next && !next.walk && linksFrom && same(l.to, next.from)) {
        walkBefore = [l.from.dep - base, l.to.arr - base];
        return;
      }
      if (!next && prev && !prev.walk && same(prev.to, l.from)) {
        end = [l.from.dep - base, l.to.arr - base];
        return;
      }
      // The start of the trip is never in the link: a first walk starts at the stop it leads to.
      rides.push({ k: 1, f: stopIndex(i === 0 ? l.to : l.from), t: stopIndex(l.to), d: l.from.dep - base, a: l.to.arr - base });
      return;
    }
    const r = { f: stopIndex(l.from), t: stopIndex(l.to), d: l.from.dep - base, a: l.to.arr - base };
    if (l.from.dd) r.dd = l.from.dd;
    if (l.to.ad) r.ad = l.to.ad;
    if (l.p) r.p = l.p;
    if (l.trip) r.i = l.trip;
    if (l.line) r.l = l.line;
    if (l.dir) r.r = l.dir;
    if (walkBefore) r.w = walkBefore;
    walkBefore = null;
    rides.push(r);
  });
  out.s = stops;
  out.r = rides;
  if (opts.o) out.o = opts.o;
  if (end) out.e = end;
  const z = [raw.dest.name];
  if (raw.dest.lat != null && raw.dest.lon != null) z.push(round4(raw.dest.lat), round4(raw.dest.lon));
  out.z = z;
  if (opts.k) out.k = opts.k;
  return JSON.stringify(out);
}

/* ---------- Times and state ---------- */

/**
 * The journey with times as expected now: each leg gets dep, arr (epoch ms), its stops from the
 * board stop to the alight stop where live data is known, and its way on the map. Walks follow
 * the ride before them (estimated). [live] maps a leg index to parsed live data.
 */
function computeLegs(journey, live) {
  const out = [];
  let shift = 0; // ms the journey runs late, carried into walks
  journey.legs.forEach((leg, index) => {
    const data = live[index];
    const plannedDuration = leg.to.planned - leg.from.planned;
    let dep = leg.from.planned + leg.from.delay * 60000;
    let arr = leg.to.planned + leg.to.delay * 60000;
    let stops = [];
    let path = null;
    let liveOk = false;
    let cancelled = false;
    let depPlatform = null;
    let arrPlatform = null;
    if (leg.walk) {
      const prev = out[out.length - 1];
      if (prev) {
        dep = Math.max(leg.from.planned, prev.arr);
        arr = dep + plannedDuration;
      } else {
        dep = leg.from.planned + shift;
        arr = dep + plannedDuration;
      }
    } else if (data && data.stops && data.stops.length) {
      // A ring line calls at a stop more than once: the calls at the times of the ride.
      const i = matchStop(data.stops, leg.from, null, 'dep');
      const j = i >= 0 ? matchStop(data.stops, leg.to, i, 'arr') : -1;
      if (i >= 0 && j > i) {
        stops = data.stops.slice(i, j + 1);
        dep = expectedTime(stops[0], 'dep');
        arr = expectedTime(stops[stops.length - 1], 'arr');
        liveOk = true;
        cancelled = data.cancelled || stops[0].cancelled || stops[stops.length - 1].cancelled;
        depPlatform = stops[0].depPlatform || null;
        arrPlatform = stops[stops.length - 1].arrPlatform || null;
        const a = stops[0].pos || leg.from.pos;
        const b = stops[stops.length - 1].pos || leg.to.pos;
        // Cut between the very calls of the ride, each on its own pass of the way (a ring).
        path = data.path && data.path.length > 1
          ? cutPathBetween(data.path, data.stops, i, j) || cutPath(data.path, a, b)
          : stops.map((s) => s.pos).filter(Boolean);
      }
    }
    if (!path || path.length < 2) path = [leg.from.pos, leg.to.pos].filter(Boolean);
    shift = arr - leg.to.planned;
    // The ride's own line first: the trip of the provider may name the line its run began as
    // (a night line that goes on as a day line).
    const product = (data && data.product) || null;
    const line = canonicalLine(leg.line || (data && data.line) || null, product);
    out.push({
      index,
      walk: leg.walk,
      line,
      color: leg.walk ? null : lineColor(line, product, data && data.color, leg.from.pos),
      direction: (data && data.direction) || leg.direction,
      from: leg.from,
      to: leg.to,
      dep,
      arr,
      delayDep: Math.round((dep - leg.from.planned) / 60000),
      delayArr: Math.round((arr - leg.to.planned) / 60000),
      stops,
      path,
      current: data && data.current ? data.current : null,
      live: liveOk,
      cancelled,
      depPlatform,
      arrPlatform,
    });
  });
  return out;
}

/** Where a ride is at [now]: its position, the next stop and whether it stands at a stop. */
function ridePosition(leg, now) {
  const stops = leg.stops;
  if (stops.length >= 2) {
    // The last stop the vehicle has left, and the next one.
    let k = -1;
    for (let i = 0; i < stops.length; i++) {
      const dep = expectedTime(stops[i], 'dep');
      if (dep !== null && dep <= now) k = i;
    }
    if (k < 0) return { pos: stops[0].pos, next: stops[1], at: stops[0] };
    if (k >= stops.length - 1) return { pos: stops[stops.length - 1].pos, next: null, at: stops[stops.length - 1] };
    const next = stops[k + 1];
    const arrNext = expectedTime(next, 'arr');
    // Standing at the next stop already, before it leaves again.
    if (arrNext !== null && now >= arrNext) return { pos: next.pos, next: stops[k + 2] || null, at: next };
    const left = expectedTime(stops[k], 'dep');
    const f = arrNext !== null && arrNext > left ? (now - left) / (arrNext - left) : 0.5;
    let pos = null;
    if (stops[k].pos && next.pos) {
      const piece = cutPath(leg.path, stops[k].pos, next.pos);
      pos = along(piece, f);
    }
    if (leg.current && pos && distance(leg.current, pos) < 3000) pos = leg.current;
    return { pos: pos || leg.current, next, at: null };
  }
  // Without live stops: along the way by the time.
  const f = leg.arr > leg.dep ? (now - leg.dep) / (leg.arr - leg.dep) : 0;
  return { pos: leg.current || along(leg.path, Math.max(0, Math.min(1, f))), next: null, at: null };
}

/**
 * The state of the trip at [now]:
 * { phase: 'before'|'ride'|'walk'|'wait'|'arrived'|'expired', leg, nextLeg, pos, next, dep, arr, delay }.
 */
function tripState(legs, now) {
  const first = legs[0];
  const last = legs[legs.length - 1];
  const arr = last.arr;
  const base = { arr, delay: last.delayArr, firstRide: legs.find((l) => !l.walk) || null };
  if (now >= arr + EXPIRY_MS) return Object.assign(base, { phase: 'expired' });
  if (now >= arr) return Object.assign(base, { phase: 'arrived', pos: last.to.pos });
  if (now < first.dep) return Object.assign(base, { phase: 'before', leg: first, dep: first.dep, pos: first.from.pos });
  for (let i = 0; i < legs.length; i++) {
    const leg = legs[i];
    if (now >= leg.dep && now < leg.arr) {
      if (leg.walk) {
        const f = leg.arr > leg.dep ? (now - leg.dep) / (leg.arr - leg.dep) : 0;
        return Object.assign(base, { phase: 'walk', leg, pos: along(leg.path, f), nextLeg: legs[i + 1] || null });
      }
      const where = ridePosition(leg, now);
      return Object.assign(base, { phase: 'ride', leg, pos: where.pos, next: where.next, at: where.at });
    }
    const following = legs[i + 1];
    if (following && now >= leg.arr && now < following.dep) {
      return Object.assign(base, { phase: 'wait', leg: following, pos: following.from.pos || leg.to.pos });
    }
  }
  return Object.assign(base, { phase: 'wait', leg: last, pos: last.from.pos });
}

/** Which legs to ask for live data at [now]: rides not over yet that start within the look-ahead. */
function legsToPoll(journey, legs, now) {
  return legs
    .filter((l) => !l.walk && journey.legs[l.index].trip && journey.legs[l.index].provider)
    .filter((l) => l.arr > now - 2 * 60000 && l.dep < now + LOOKAHEAD_MS)
    .map((l) => l.index);
}

/* ---------- View model: progress, motion, state line, timeline ---------- */

/** How far the whole trip is at [now] (0..1, by time) and where the changes are on that bar. */
function tripProgress(legs, now) {
  const start = legs[0].dep;
  const end = legs[legs.length - 1].arr;
  const span = end - start;
  const f = (t) => (span > 0 ? Math.max(0, Math.min(1, (t - start) / span)) : t >= end ? 1 : 0);
  return { value: f(now), marks: legs.slice(0, -1).map((l) => f(l.arr)) };
}

/**
 * [path] split at the point nearest to [pos]: the part already travelled and the part ahead,
 * both containing that point. Without a position everything is ahead.
 */
function splitPathAt(path, pos) {
  if (!path || path.length < 2 || !pos) return { passed: [], ahead: path ? path.slice() : [] };
  let best = { d: Infinity, i: 0, point: path[0] };
  for (let i = 1; i < path.length; i++) {
    const a = path[i - 1];
    const b = path[i];
    // Projection in a local flat frame (good enough within a city).
    const k = Math.cos(((a[0] + b[0]) / 2) * Math.PI / 180);
    const ax = a[1] * k;
    const bx = b[1] * k;
    const px = pos[1] * k;
    const dx = bx - ax;
    const dy = b[0] - a[0];
    const len = dx * dx + dy * dy;
    const t = len > 0 ? Math.max(0, Math.min(1, ((px - ax) * dx + (pos[0] - a[0]) * dy) / len)) : 0;
    const point = [a[0] + dy * t, a[1] + (b[1] - a[1]) * t];
    const d = distance(point, pos);
    if (d < best.d) best = { d, i, point };
  }
  return {
    passed: path.slice(0, best.i).concat([best.point]),
    ahead: [best.point].concat(path.slice(best.i)),
  };
}

/**
 * The shown position of the vehicle eased toward [target] over [dtMs]: smooth between updates,
 * with a jump when the target is far away (a new leg, a correction of more than [snapM] metres).
 */
function easeToward(current, target, dtMs, tauMs, snapM) {
  if (!target) return null;
  if (!current) return target.slice();
  if (distance(current, target) > (snapM || 1500)) return target.slice();
  const k = 1 - Math.exp(-Math.max(0, dtMs) / (tauMs || 900));
  return [current[0] + (target[0] - current[0]) * k, current[1] + (target[1] - current[1]) * k];
}

/** "in 23 min", "in 1 h 05 min", "jetzt"; empty once [arr] has passed. */
function countdownText(T, arr, now) {
  if (arr == null || now >= arr) return '';
  const minutes = Math.ceil((arr - now) / 60000);
  if (minutes <= 1 && arr - now < 45000) return T.now;
  if (minutes < 60) return T.inMin(minutes);
  return T.inHours(Math.floor(minutes / 60), minutes % 60);
}

/* ---------- Motion ---------- */

/** How long the confetti of an arrival flies, in seconds, and how many pieces it has. */
const CONFETTI_SECONDS = 1.6;
const CONFETTI_COUNT = 40;

/** [count] pieces of confetti in [colors] colours, the same for the same [seed]: dots, ribbons and strips. */
function confettiPieces(count, colors, seed) {
  let x = (seed >>> 0) || 1;
  const random = () => {
    // xorshift: the same burst for the same seed, also in the tests.
    x ^= x << 13; x >>>= 0;
    x ^= x >>> 17;
    x ^= x << 5; x >>>= 0;
    return x / 4294967296;
  };
  const kinds = ['dot', 'ribbon', 'strip'];
  return Array.from({ length: count }, (_, i) => {
    const direction = -Math.PI / 2 + (random() - 0.5) * Math.PI * 0.85;
    const speed = 280 + random() * 240;
    return {
      vx: Math.cos(direction) * speed,
      vy: Math.sin(direction) * speed,
      spin: (random() - 0.5) * 1080,
      angle: random() * 360,
      kind: kinds[i % kinds.length],
      color: colors.length ? colors[i % colors.length] : '#1B4FD8',
      size: 4 + random() * 4,
    };
  });
}

/** Where [piece] is [seconds] after the burst, in CSS pixels from its start (x right, y down), and how visible. */
function confettiAt(piece, seconds) {
  const drag = 1.6;
  const gravity = 820;
  return {
    x: piece.vx * (1 - Math.exp(-drag * seconds)) / drag,
    y: piece.vy * seconds + 0.5 * gravity * seconds * seconds,
    angle: piece.angle + piece.spin * seconds,
    alpha: Math.min(1, Math.max(0, (CONFETTI_SECONDS - seconds) / 0.5)),
  };
}

/** The wait before the next request of a ride: 20 s seen, 60 s hidden, doubling after failures (max 5 min). */
function pollDelay(hidden, failures) {
  const base = hidden ? POLL_MS.hidden : POLL_MS.visible;
  if (!failures) return base;
  return Math.min(MAX_BACKOFF_MS, Math.max(base, POLL_MS.visible * Math.pow(2, failures)));
}

/** The one line under the arrival: "Im U8 · nächster Halt Hermannplatz", "Steigt um in …", … */
function stateLine(T, state) {
  const leg = state.leg;
  switch (state.phase) {
    case 'before':
      return !leg.walk ? T.before(clock(leg.dep), leg.line || '?', leg.from.name) : T.beforeWalk(clock(leg.dep));
    case 'ride': {
      const head = T.ride(leg.line || '?');
      if (leg.cancelled) return head + ' · ' + T.cancelled;
      if (state.at) return head + ' · ' + T.at(state.at.name);
      if (state.next) return head + ' · ' + T.next(state.next.name);
      return head + ' · ' + T.next(leg.to.name);
    }
    case 'walk':
      return T.walk(state.nextLeg ? state.nextLeg.from.name : leg.to.name);
    case 'wait':
      return leg.walk ? T.walk(leg.to.name) : T.change(leg.from.name, leg.line || '?', clock(leg.dep));
    case 'arrived':
      return T.arrived(clock(state.arr));
    default:
      return T.expired;
  }
}

const RAIL = ['subway', 'suburban', 'regional', 'express'];

/** "Gleis 1" for rail, "Steig 3" for bus and tram (guessed from the line and place), other labels as they are. */
function platformLabel(T, platform, line, pos) {
  if (!platform) return null;
  if (!/^\d/.test(platform)) return platform;
  const p = guessProduct(line, pos);
  return p && RAIL.indexOf(p) < 0 ? T.bay(platform) : T.track(platform);
}

/**
 * The hero of the page, the next thing that happens, as the app's companion shows it:
 * { kind, counter ("19′", "1:05 h", "jetzt" or ''), title ("Aussteigen in Flughafen BER"),
 *   time (epoch ms of that moment), delay (min) and platform ("Gleis 1" or null) }.
 * kind: 'before', 'off' (riding: get off), 'walk', 'board' (waiting for the next ride),
 * 'arrived' or 'expired'.
 */
function heroModel(T, state, now) {
  const leg = state.leg;
  switch (state.phase) {
    case 'before':
      return {
        kind: 'before', counter: counterText(leg.dep, now, T.now), time: leg.dep,
        title: leg.walk ? T.heroWalkStart : T.heroBoard(leg.line || '?', leg.from.name),
        delay: leg.walk ? 0 : leg.delayDep, platform: leg.walk ? null : platformLabel(T, leg.depPlatform, leg.line, leg.from.pos),
      };
    case 'ride':
      return {
        kind: 'off', counter: counterText(leg.arr, now, T.now), time: leg.arr, title: T.heroOff(leg.to.name),
        delay: leg.delayArr, platform: platformLabel(T, leg.arrPlatform, leg.line, leg.to.pos),
      };
    case 'walk':
      return {
        kind: 'walk', counter: counterText(leg.arr, now, T.now), time: leg.arr,
        title: T.heroWalk(state.nextLeg && !state.nextLeg.walk ? state.nextLeg.from.name : leg.to.name), delay: 0, platform: null,
      };
    case 'wait':
      if (leg.walk) {
        return { kind: 'walk', counter: counterText(leg.arr, now, T.now), time: leg.arr, title: T.heroWalk(leg.to.name), delay: 0, platform: null };
      }
      return {
        kind: 'board', counter: counterText(leg.dep, now, T.now), time: leg.dep, title: T.heroBoard(leg.line || '?', leg.from.name),
        delay: leg.delayDep, platform: platformLabel(T, leg.depPlatform, leg.line, leg.from.pos),
      };
    case 'arrived':
      return { kind: 'arrived', counter: '', time: state.arr, title: T.heroArrived, delay: state.delay, platform: null };
    default:
      return { kind: 'expired', counter: '', time: state.arr, title: T.endedHead, delay: 0, platform: null };
  }
}

/**
 * The timeline: one entry per leg with its status ('done', 'current', 'upcoming'), the stops
 * between board and alight stop (each 'passed', 'at', 'next' or 'upcoming') and, for the
 * current ride, a preview of the next [ahead] stops with the count of the rest.
 */
function timelineModel(legs, state, now, ahead) {
  const max = ahead || 3;
  const over = state.phase === 'arrived' || state.phase === 'expired';
  const currentIndex = !over && state.leg ? state.leg.index : -1;
  return legs.map((l) => {
    const current = l.index === currentIndex;
    const status = current ? 'current' : l.arr <= now || over ? 'done' : 'upcoming';
    const riding = current && state.phase === 'ride';
    const between = l.stops.length > 2 ? l.stops.slice(1, -1) : [];
    const stops = between.map((s) => {
      const arr = expectedTime(s, 'arr');
      const dep = expectedTime(s, 'dep');
      let st = status === 'done' || (dep !== null && dep <= now) ? 'passed' : 'upcoming';
      if (riding && state.at === s) st = 'at';
      else if (riding && state.next === s) st = 'next';
      return { name: s.name, time: arr !== null ? arr : dep, status: st, cancelled: !!s.cancelled };
    });
    const passed = stops.filter((s) => s.status === 'passed').length;
    const rest = stops.filter((s) => s.status !== 'passed');
    return {
      index: l.index,
      walk: l.walk,
      line: l.line,
      color: l.color,
      direction: l.direction,
      cancelled: l.cancelled,
      status,
      minutes: Math.max(1, Math.round((l.arr - l.dep) / 60000)),
      from: { name: l.from.name, time: l.dep, delay: l.delayDep, passed: status === 'done' || (riding && now >= l.dep) },
      to: {
        name: l.to.name,
        time: l.arr,
        delay: l.delayArr,
        next: riding && (state.next === l.stops[l.stops.length - 1] || (!state.next && !state.at)),
        at: riding && state.at === l.stops[l.stops.length - 1],
      },
      stops,
      passed: current ? passed : 0,
      preview: current ? rest.slice(0, max) : [],
      more: current ? Math.max(0, rest.length - max) : 0,
    };
  });
}

/* ---------- Texts ---------- */

const TEXTS = {
  de: {
    lang: 'de',
    onWay: (n, d) => (n ? n + ' ist unterwegs nach ' + d : 'Unterwegs nach ' + d),
    arrivedHead: (n, d) => (n ? n + ' ist in ' + d + ' angekommen' : 'Angekommen in ' + d),
    endedHead: 'Fahrt beendet',
    onWayHome: (n) => (n ? n + ' ist auf dem Weg nach Hause' : 'Auf dem Weg nach Hause'),
    arrivedHome: (n) => (n ? n + ' ist zu Hause angekommen' : 'Zu Hause angekommen'),
    onWayWork: (n) => (n ? n + ' ist auf dem Weg zur Arbeit' : 'Auf dem Weg zur Arbeit'),
    arrivedWork: (n) => (n ? n + ' ist bei der Arbeit angekommen' : 'Bei der Arbeit angekommen'),
    eta: 'Ankunft',
    onTime: 'pünktlich',
    late: (m) => '+' + m + ' min',
    early: (m) => '−' + m + ' min',
    now: 'jetzt',
    inMin: (m) => 'in ' + m + ' min',
    inHours: (h, m) => 'in ' + h + ' h ' + (m < 10 ? '0' : '') + m + ' min',
    before: (t, line, place) => 'Startet um ' + t + ' · ' + line + ' ab ' + place,
    beforeWalk: (t) => 'Startet um ' + t + ' zu Fuß',
    ride: (line) => 'Im ' + line,
    next: (s) => 'nächster Halt ' + s,
    at: (s) => 'hält in ' + s,
    walk: (to) => 'Zu Fuß zu ' + to,
    change: (place, line, t) => 'Steigt um in ' + place + ' · ' + line + ' um ' + t,
    arrived: (t) => 'Angekommen um ' + t,
    expired: 'Fahrt beendet · Link abgelaufen',
    expiredNote: 'Dieser Live-Link ist abgelaufen.',
    cancelled: 'fällt aus',
    justNow: 'gerade eben',
    agoS: (s) => 'vor ' + s + ' s',
    agoM: (m) => 'vor ' + m + ' min',
    agoH: (h) => 'vor ' + h + ' h',
    updated: (age) => 'Live · ' + age,
    stale: (m) => 'Keine Live-Daten seit ' + m + ' min',
    noLive: 'Keine Live-Daten – Fahrplanzeiten',
    loading: 'Lädt Live-Daten …',
    planOnly: 'Fahrplanzeiten',
    walkLeg: 'Fußweg',
    minutes: (m) => m + ' min',
    towards: (d) => 'Richtung ' + d,
    stopCount: (n) => (n === 1 ? '1 Halt' : n + ' Halte'),
    stopsPassed: (n) => (n === 1 ? '1 Halt passiert' : n + ' Halte passiert'),
    moreStops: (n) => (n === 1 ? '+ 1 weiterer Halt' : '+ ' + n + ' weitere Halte'),
    follow: 'Folgen',
    expand: 'Fahrtverlauf ein- oder ausklappen',
    disclaimer: 'Keine GPS-Position – berechnet aus Echtzeitdaten.',
    sources: 'Fahrplandaten',
    openSource: 'Open Source (GPL-3.0)',
    sourceCode: 'Quellcode auf GitHub',
    shared: (t) => 'Geteilt um ' + t,
    demo: 'Demo mit einer erfundenen Fahrt im Zeitraffer',
    badLink: 'Dieser Link ist ungültig oder unvollständig.',
    badVersion: 'Dieser Link stammt aus einer neueren App-Version.',
    badBrowser: 'Dieser Browser kann den Link nicht öffnen. Bitte aktualisiere ihn oder nimm einen anderen Browser.',
    vehicle: 'Fahrzeug (geschätzt)',
    destination: 'Ziel',
    heroOff: (s) => 'Aussteigen in ' + s,
    heroWalk: (s) => 'Zu Fuß zu ' + s,
    heroWalkStart: 'Los geht\'s zu Fuß',
    heroBoard: (line, s) => 'Einsteigen in ' + line + ' · ' + s,
    heroArrived: 'Angekommen',
    track: (p) => 'Gleis ' + p,
    bay: (p) => 'Steig ' + p,
    arrivesAt: (t) => 'an ' + t,
    barStart: 'Start',
    barNow: (t) => 'jetzt ' + t,
    barEnd: 'Ziel',
    openApp: 'In Next Stop öffnen',
    getApp: 'Bei Google Play laden',
    appLine: 'Next Stop: Fahrten planen, live verfolgen und teilen. Open Source, ohne Tracking.',
    nextStopBox: (s) => 'Nächster Halt: ' + s,
    linkExpired: 'Link abgelaufen',
  },
  en: {
    lang: 'en',
    onWay: (n, d) => (n ? n + ' is on the way to ' + d : 'On the way to ' + d),
    arrivedHead: (n, d) => (n ? n + ' has arrived at ' + d : 'Arrived at ' + d),
    endedHead: 'Trip over',
    onWayHome: (n) => (n ? n + ' is on the way home' : 'On the way home'),
    arrivedHome: (n) => (n ? n + ' has arrived home' : 'Arrived home'),
    onWayWork: (n) => (n ? n + ' is on the way to work' : 'On the way to work'),
    arrivedWork: (n) => (n ? n + ' has arrived at work' : 'Arrived at work'),
    eta: 'Arrival',
    onTime: 'on time',
    late: (m) => '+' + m + ' min',
    early: (m) => '−' + m + ' min',
    now: 'now',
    inMin: (m) => 'in ' + m + ' min',
    inHours: (h, m) => 'in ' + h + ' h ' + (m < 10 ? '0' : '') + m + ' min',
    before: (t, line, place) => 'Starts at ' + t + ' · ' + line + ' from ' + place,
    beforeWalk: (t) => 'Starts at ' + t + ' on foot',
    ride: (line) => 'On the ' + line,
    next: (s) => 'next stop ' + s,
    at: (s) => 'stopping at ' + s,
    walk: (to) => 'Walking to ' + to,
    change: (place, line, t) => 'Changing at ' + place + ' · ' + line + ' at ' + t,
    arrived: (t) => 'Arrived at ' + t,
    expired: 'Trip over · link expired',
    expiredNote: 'This live link has expired.',
    cancelled: 'cancelled',
    justNow: 'just now',
    agoS: (s) => s + ' s ago',
    agoM: (m) => m + ' min ago',
    agoH: (h) => h + ' h ago',
    updated: (age) => 'Live · ' + age,
    stale: (m) => 'No live data for ' + m + ' min',
    noLive: 'No live data – timetable times',
    loading: 'Loading live data …',
    planOnly: 'Timetable times',
    walkLeg: 'Walk',
    minutes: (m) => m + ' min',
    towards: (d) => 'towards ' + d,
    stopCount: (n) => (n === 1 ? '1 stop' : n + ' stops'),
    stopsPassed: (n) => (n === 1 ? '1 stop passed' : n + ' stops passed'),
    moreStops: (n) => (n === 1 ? '+ 1 more stop' : '+ ' + n + ' more stops'),
    follow: 'Follow',
    expand: 'Show or hide the stops',
    disclaimer: 'No GPS position – estimated from real-time data.',
    sources: 'Timetable data',
    openSource: 'Open source (GPL-3.0)',
    sourceCode: 'Source code on GitHub',
    shared: (t) => 'Shared at ' + t,
    demo: 'Demo with a made-up trip in fast motion',
    badLink: 'This link is invalid or incomplete.',
    badVersion: 'This link comes from a newer app version.',
    badBrowser: 'This browser cannot open the link. Please update it or use another browser.',
    vehicle: 'Vehicle (estimated)',
    destination: 'Destination',
    heroOff: (s) => 'Get off at ' + s,
    heroWalk: (s) => 'Walking to ' + s,
    heroWalkStart: 'Off on foot',
    heroBoard: (line, s) => 'Board the ' + line + ' · ' + s,
    heroArrived: 'Arrived',
    track: (p) => 'Platform ' + p,
    bay: (p) => 'Bay ' + p,
    arrivesAt: (t) => 'arr. ' + t,
    barStart: 'Start',
    barNow: (t) => 'now ' + t,
    barEnd: 'End',
    openApp: 'Open in Next Stop',
    getApp: 'Get it on Google Play',
    appLine: 'Next Stop: plan, follow and share trips. Open source, no tracking.',
    nextStopBox: (s) => 'Next stop: ' + s,
    linkExpired: 'Link expired',
  },
};

/** The texts for the browser [language], or the [sender]'s language for any but German and English. */
function pickTexts(language, sender) {
  return TEXTS[pickLanguage(language, sender)];
}

/** The headline of the hero card: on the way, arrived or over; "nach Hause" for the sender's home. */
function headline(T, phase, name, dest) {
  if (phase === 'expired') return T.endedHead;
  const arrived = phase === 'arrived';
  if (dest.kind === 'h') return arrived ? T.arrivedHome(name) : T.onWayHome(name);
  if (dest.kind === 'w') return arrived ? T.arrivedWork(name) : T.onWayWork(name);
  return arrived ? T.arrivedHead(name, dest.name) : T.onWay(name, dest.name);
}

if (typeof module !== 'undefined' && module.exports) {
  module.exports = {
    fromBase64Url, toBase64Url, parseFragment, readFragment, expandLive, encodeLive, parseBvgTrip, parseTransitousTrip, decodePolyline,
    computeLegs, tripState, ridePosition, legsToPoll, tripUrl, stateLine, delayText, delayLevel, pickTexts, headline,
    cutPath, along, clock, tripProgress, splitPathAt, easeToward, countdownText, ageText, freshness,
    freshnessText, pollDelay, timelineModel, lineColor, inkOn, guessProduct, distance, EXPIRY_MS, POLL_MS,
    textNumber, rollsUp, lineLength, snakeFeatures, easeOutCubic, confettiPieces, confettiAt, CONFETTI_SECONDS,
    CONFETTI_COUNT, ROUTE_DRAW_MS, heroModel, platformLabel, progressSegments, counterText, sheetRest, sheetClamp,
    appIntentUrl, PLAY_URL,
  };
}

/* ---------- Page ---------- */

if (typeof document !== 'undefined') {
  (function page() {
    // The recipient's language; the sender's ("l" in the link) only when it is neither German nor English.
    const T = pickTexts(navigator.language, new URLSearchParams(location.hash.slice(1)).get('l'));
    document.documentElement.lang = T.lang;
    const $ = (id) => document.getElementById(id);
    const reducedMotion = typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;
    // Elements, the walk icon, line badges and rolling numbers (web/shared/ui.js).
    const { el, walkIcon, badge, rollTo } = window.NextStopUi.create({ reducedMotion });

    // Static texts.
    $('eta-label').textContent = T.eta;
    $('disclaimer').textContent = T.disclaimer;
    $('src-label').textContent = T.sources;
    $('oss-label').textContent = T.openSource;
    $('code-link').textContent = T.sourceCode;
    $('follow-label').textContent = T.follow;
    $('follow').title = T.follow;
    $('grabber').setAttribute('aria-label', T.expand);
    $('app-line').textContent = T.appLine;
    $('get-app-label').textContent = T.getApp;
    $('get-app').href = PLAY_URL;
    if (/Android/i.test(navigator.userAgent)) {
      // The app opens if it is installed; otherwise the store page.
      const open = $('open-app');
      open.textContent = T.openApp;
      open.href = appIntentUrl('', null);
      open.hidden = false;
      $('get-app').classList.add('quiet');
    }

    let demo = null;
    const nowMs = () => (demo ? demo.now() : Date.now());
    const darkQuery = typeof matchMedia === 'function' ? matchMedia('(prefers-color-scheme: dark)') : null;

    if (/^#demo\b/.test(location.hash)) {
      // The demo script is only loaded for "#demo": it fakes the clock and the API answers.
      const script = document.createElement('script');
      script.src = 'demo.js';
      script.onload = () => {
        demo = window.NextStopDemo.create({ toBase64Url, hash: location.hash });
        start(demo.hash);
      };
      script.onerror = () => start('');
      document.head.append(script);
    } else {
      start(location.hash);
    }
    window.addEventListener('hashchange', () => location.reload());

    function start(hash) {
      readFragment(hash).then(run, (e) => {
        $('headline').textContent = e.message === 'version' ? T.badVersion : e.message === 'unsupported' ? T.badBrowser : T.badLink;
        document.body.classList.add('broken');
      });
    }

    function run(journey) {

      const live = {};
      const nextPoll = {};
      const failures = {};
      const open = new Map(); // leg index -> opened (true) or closed (false) by the user
      const showAll = new Set(); // legs whose stops are all shown
      const pollable = journey.legs.some((l) => !l.walk && l.trip && l.provider);
      let lastOk = null;
      let lastFail = null;
      let legs = computeLegs(journey, live);
      let state = tripState(legs, nowMs());
      let timelineSig = '';
      let scrolledTo = -2;
      let paddingSig = '';
      let segsSig = '';
      // The swipe sheet of phones: open (all cards) or collapsed (the hero only); its offset in px.
      let sheetOpen = false;
      let sheetClosed = 0;
      let sheetOffset = 0;
      let dragging = null;
      let polling = false;
      let firstPoll = true;

      /* ----- Map ----- */
      let map = null;
      let mapReady = false;
      let fitted = false;
      let following = true;
      let autoMoveUntil = 0; // while a camera animation of ours runs, the vehicle does not move the map
      let vehicleMarker = null;
      let vehicleEl = null;
      let vehicleOnMap = false;
      let rafId = 0;
      let shown = null; // the eased position of the vehicle
      let target = null;
      let targetAt = 0;
      let lastFrame = 0;
      let routeAt = 0;
      let revealStart = null; // when the route started to draw itself in
      let timelineShown = false; // the rows come in one after the other only the first time
      let unfolding = -1; // the leg the user just opened: its stops spring in once
      let celebrated = false;

      const isWide = () => innerWidth >= 900;

      function insets() {
        const top = $('topbar').getBoundingClientRect();
        if (isWide()) {
          const panel = $('panel').getBoundingClientRect();
          return { top: 48, bottom: 48, left: 48, right: Math.round(innerWidth - panel.left) + 32 };
        }
        const sheet = $('sheet').getBoundingClientRect();
        return {
          top: Math.round(top.bottom) + 24,
          bottom: Math.max(24, Math.round(innerHeight - sheet.top) + 24),
          left: 32,
          right: 32,
        };
      }

      /**
       * The sheet of phones: it reaches from below the top bar to the bottom; collapsed, only its
       * hero (counter, what comes next, the bar of the trip) shows above the bottom edge.
       */
      function sheetGeometry() {
        const sheet = $('sheet');
        if (isWide()) {
          sheet.style.removeProperty('--sheet-top');
          sheet.style.removeProperty('--sheet-y');
          sheetClosed = 0;
          sheetOffset = 0;
          return;
        }
        const top = Math.round($('topbar').getBoundingClientRect().bottom) + 10;
        sheet.style.setProperty('--sheet-top', top + 'px');
        const box = sheet.getBoundingClientRect();
        const labels = $('bar-start').parentElement.getBoundingClientRect();
        const body = $('sheet-body');
        // The hero's bottom within the sheet, whatever the sheet's own offset and scroll are.
        const heroBottom = labels.bottom - box.top + (sheetOpen ? 0 : body.scrollTop) + 18;
        const height = innerHeight - top;
        sheetClosed = Math.max(0, Math.round(height - heroBottom));
        if (!dragging) sheetOffset = sheetOpen ? 0 : sheetClosed;
        sheet.style.setProperty('--sheet-y', Math.round(sheetOffset) + 'px');
        sheet.classList.toggle('open', sheetOpen);
        $('grabber').setAttribute('aria-expanded', String(sheetOpen));
      }

      function setSheet(open) {
        sheetOpen = open;
        if (!open) $('sheet-body').scrollTop = 0;
        layoutVars();
        setTimeout(layoutVars, 380);
      }

      // Dragging the grabber or the hero moves the sheet with the finger; it rests open or collapsed.
      function startDrag(e) {
        if (isWide() || (e.button != null && e.button !== 0)) return;
        dragging = { y: e.clientY, from: sheetOffset, t: performance.now(), v: 0, moved: false, id: e.pointerId };
        $('sheet').classList.add('dragging');
      }
      function moveDrag(e) {
        if (!dragging || e.pointerId !== dragging.id) return;
        const dy = e.clientY - dragging.y;
        if (Math.abs(dy) > 6) dragging.moved = true;
        if (!dragging.moved) return;
        const t = performance.now();
        const next = sheetClamp(dragging.from + dy, sheetClosed);
        dragging.v = (next - sheetOffset) / Math.max(1, t - dragging.t);
        dragging.t = t;
        sheetOffset = next;
        $('sheet').style.setProperty('--sheet-y', Math.round(sheetOffset) + 'px');
      }
      function endDrag(e, tap) {
        if (!dragging || e.pointerId !== dragging.id) return;
        const d = dragging;
        dragging = null;
        $('sheet').classList.remove('dragging');
        if (!d.moved) {
          if (tap) setSheet(!sheetOpen);
          else layoutVars();
          return;
        }
        setSheet(sheetRest(sheetOffset, d.v, sheetClosed) === 0);
      }

      function layoutVars() {
        sheetGeometry();
        const sheet = $('sheet').getBoundingClientRect();
        document.documentElement.style.setProperty('--sheet-h', isWide() ? '0px' : Math.max(0, Math.round(innerHeight - sheet.top)) + 'px');
        // setPadding moves the camera at once (and stops a running animation): only on a change.
        const padding = insets();
        const sig = JSON.stringify(padding);
        if (map && sig !== paddingSig) {
          paddingSig = sig;
          map.setPadding(padding);
        }
      }

      function styleUrl() {
        return darkQuery && darkQuery.matches ? MAP_STYLE.dark : MAP_STYLE.light;
      }

      function addLayers() {
        if (map.getSource('route')) return;
        const dark = darkQuery && darkQuery.matches;
        const empty = { type: 'FeatureCollection', features: [] };
        map.addSource('route', { type: 'geojson', data: empty });
        map.addLayer({
          id: 'route-casing', type: 'line', source: 'route',
          filter: ['!=', ['get', 'walk'], true],
          layout: { 'line-cap': 'round', 'line-join': 'round' },
          paint: { 'line-color': dark ? '#0b1020' : '#ffffff', 'line-width': 9, 'line-opacity': 0.85 },
        });
        map.addLayer({
          id: 'route-ride', type: 'line', source: 'route',
          filter: ['!=', ['get', 'walk'], true],
          layout: { 'line-cap': 'round', 'line-join': 'round' },
          paint: {
            'line-color': ['case', ['get', 'done'], dark ? '#4b5468' : '#a7afc0', ['get', 'color']],
            'line-width': ['case', ['get', 'done'], 4, 5.5],
          },
        });
        map.addLayer({
          id: 'route-walk', type: 'line', source: 'route',
          filter: ['==', ['get', 'walk'], true],
          layout: { 'line-cap': 'round' },
          paint: { 'line-color': ['case', ['get', 'done'], dark ? '#4b5468' : '#a7afc0', dark ? '#c9d3ee' : '#5b6680'], 'line-width': 3.5, 'line-dasharray': [0.1, 2] },
        });
        map.addSource('stops', { type: 'geojson', data: empty });
        map.addLayer({
          id: 'stops', type: 'circle', source: 'stops',
          paint: {
            'circle-radius': ['case', ['get', 'change'], 6, 3.5],
            'circle-color': dark ? '#121828' : '#ffffff',
            'circle-stroke-color': ['case', ['get', 'done'], dark ? '#4b5468' : '#a7afc0', ['get', 'color']],
            'circle-stroke-width': ['case', ['get', 'change'], 3, 2],
          },
        });
        mapReady = true;
        drawRoute(true);
      }

      function initMap() {
        if (typeof maplibregl === 'undefined') return;
        const points = [];
        journey.legs.forEach((l) => [l.from.pos, l.to.pos].forEach((p) => p && points.push(p)));
        if (points.length === 0) return;
        map = new maplibregl.Map({
          container: 'map',
          style: styleUrl(),
          center: [points[0][1], points[0][0]],
          zoom: 12,
          attributionControl: { compact: true },
          pitchWithRotate: false,
          dragRotate: false,
        });
        map.touchZoomRotate.disableRotation();
        map.on('style.load', addLayers);
        // A move by the user (drag, pinch, wheel) stops following the vehicle.
        map.on('movestart', (e) => {
          if (e.originalEvent) setFollowing(false);
        });
        if (darkQuery && darkQuery.addEventListener) {
          darkQuery.addEventListener('change', () => {
            mapReady = false;
            map.setStyle(styleUrl(), { diff: false });
          });
        }
        const dest = el('div', 'marker-dest');
        dest.title = T.destination;
        dest.append(el('i'));
        if (journey.dest.pos) new maplibregl.Marker({ element: dest, anchor: 'bottom' }).setLngLat([journey.dest.pos[1], journey.dest.pos[0]]).addTo(map);
        vehicleEl = el('div', 'marker-vehicle');
        vehicleEl.title = T.vehicle;
        vehicleMarker = new maplibregl.Marker({ element: vehicleEl });
        layoutVars();
      }

      function setFollowing(on) {
        following = on;
        $('follow').hidden = on || !vehicleVisible();
        if (on && map && shown) {
          const duration = reducedMotion ? 0 : 900;
          map.easeTo({ center: [shown[1], shown[0]], zoom: Math.max(map.getZoom(), 14), duration });
          autoMoveUntil = performance.now() + duration + 50;
        }
      }

      function vehicleVisible() {
        return state.pos && (state.phase === 'ride' || state.phase === 'walk' || state.phase === 'wait' || state.phase === 'before');
      }

      function drawRoute(force) {
        if (!map || !mapReady) return;
        const now = nowMs();
        const t = typeof performance !== 'undefined' ? performance.now() : Date.now();
        // The first time, the route draws itself in like a snake from its start to its end.
        if (revealStart === null) revealStart = reducedMotion ? t - ROUTE_DRAW_MS - 100 : t;
        const reveal = easeOutCubic((t - revealStart) / ROUTE_DRAW_MS);
        const revealing = t - revealStart < ROUTE_DRAW_MS + 100;
        if (!force && !revealing && t - routeAt < 1000) return;
        routeAt = t;
        let features = [];
        const line = (points, props) => points.length > 1 && features.push({
          type: 'Feature', properties: props, geometry: { type: 'LineString', coordinates: points.map((p) => [p[1], p[0]]) },
        });
        const over = state.phase === 'arrived' || state.phase === 'expired';
        legs.forEach((l) => {
          const color = l.walk ? '#5b6680' : l.color || '#1B4FD8';
          const props = (done) => ({ walk: l.walk, done, color });
          if (over || l.arr <= now) line(l.path, props(true));
          else if (state.leg === l && (state.phase === 'ride' || state.phase === 'walk') && shown) {
            const split = splitPathAt(l.path, shown);
            line(split.passed, props(true));
            line(split.ahead, props(false));
          } else line(l.path, props(false));
        });
        if (reveal < 1) features = snakeFeatures(features, reveal);
        map.getSource('route').setData({ type: 'FeatureCollection', features });
        const stops = [];
        legs.forEach((l, i) => {
          if (l.walk) return;
          const done = over || l.arr <= now;
          const color = l.color || '#1B4FD8';
          const point = (p, change, passed) => p && stops.push({
            type: 'Feature', properties: { change, done: passed, color }, geometry: { type: 'Point', coordinates: [p[1], p[0]] },
          });
          l.stops.slice(1, -1).forEach((s) => {
            const t = expectedTime(s, 'dep');
            point(s.pos, false, done || (t !== null && t <= now));
          });
          point(l.from.pos, true, done || now >= l.dep);
          if (i < legs.length - 1) point(l.to.pos, true, done);
        });
        // The stops follow once the snake has arrived.
        map.getSource('stops').setData({ type: 'FeatureCollection', features: reveal < 1 ? [] : stops });
        if (!fitted) {
          fitted = true;
          const bounds = new maplibregl.LngLatBounds();
          legs.forEach((l) => l.path.forEach((p) => bounds.extend([p[1], p[0]])));
          if (journey.dest.pos) bounds.extend([journey.dest.pos[1], journey.dest.pos[0]]);
          if (!bounds.isEmpty()) map.fitBounds(bounds, { padding: insets(), duration: 0, maxZoom: 15 });
          // A moment on the whole trip, then close to the vehicle.
          setTimeout(() => {
            if (following && vehicleVisible()) setFollowing(true);
          }, 1600);
        }
      }

      function updateVehicle(t) {
        if (!vehicleMarker) return;
        if (!vehicleVisible()) {
          vehicleMarker.remove();
          vehicleOnMap = false;
          shown = null;
          $('follow').hidden = true;
          return;
        }
        const now = nowMs();
        if (!target || now - targetAt > (demo ? 100 : 250)) {
          target = tripState(legs, now).pos || state.pos;
          targetAt = now;
        }
        const dt = lastFrame ? t - lastFrame : 1000;
        lastFrame = t;
        shown = reducedMotion ? target.slice() : easeToward(shown, target, dt, 700, 1500);
        const walking = state.phase !== 'ride';
        const label = walking ? '' : state.leg.line || '';
        if (vehicleEl.dataset.label !== label || vehicleEl.dataset.walking !== String(walking)) {
          vehicleEl.dataset.label = label;
          vehicleEl.dataset.walking = String(walking);
          vehicleEl.replaceChildren();
          const chip = el('span', 'chip');
          if (walking) chip.append(walkIcon());
          else chip.textContent = label;
          const color = walking ? null : state.leg.color;
          chip.style.background = color || '';
          chip.style.color = color ? inkOn(color) : '';
          vehicleEl.classList.toggle('walking', walking);
          vehicleEl.append(el('span', 'ring'), chip);
        }
        vehicleMarker.setLngLat([shown[1], shown[0]]);
        if (!vehicleOnMap) {
          vehicleMarker.addTo(map);
          vehicleOnMap = true;
        }
        if (following && t > autoMoveUntil && mapReady && fitted) map.jumpTo({ center: [shown[1], shown[0]] });
        $('follow').hidden = following;
      }

      // One animation loop at most; it stops while the page is hidden.
      function frame(t) {
        rafId = 0;
        if (document.hidden) return;
        updateVehicle(t || performance.now());
        drawRoute(false);
        rafId = requestAnimationFrame(frame);
      }
      function kick() {
        if (!rafId) rafId = requestAnimationFrame(frame);
      }

      // The arrival: the check pops and draws itself, and once per link a small burst of
      // confetti in the colours of the lines flies up from it. Still with reduced motion.
      function celebrate() {
        const check = $('check');
        check.hidden = false;
        let seen = false;
        try {
          const key = 'ns-arrived:' + location.hash.slice(0, 200);
          seen = sessionStorage.getItem(key) === '1';
          sessionStorage.setItem(key, '1');
        } catch (e) { /* no storage: celebrate anyway */ }
        if (reducedMotion || seen) {
          check.classList.add('still');
          return;
        }
        check.classList.add('pop');
        const canvas = $('confetti');
        const ctx = canvas.getContext && canvas.getContext('2d');
        if (!ctx) return;
        const ratio = window.devicePixelRatio || 1;
        canvas.width = Math.round(innerWidth * ratio);
        canvas.height = Math.round(innerHeight * ratio);
        canvas.hidden = false;
        const colors = [...new Set(legs.filter((l) => !l.walk && l.color).map((l) => l.color).concat(['#C8F16B', '#1B4FD8']))];
        const pieces = confettiPieces(CONFETTI_COUNT, colors, 7);
        const box = check.getBoundingClientRect();
        const origin = { x: box.left + box.width / 2, y: box.top + box.height / 2 };
        const begin = performance.now() + 180;
        const draw = (t) => {
          const seconds = (t - begin) / 1000;
          ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
          ctx.clearRect(0, 0, innerWidth, innerHeight);
          if (seconds >= CONFETTI_SECONDS) {
            canvas.hidden = true;
            return;
          }
          if (seconds > 0) {
            pieces.forEach((p) => {
              const at = confettiAt(p, seconds);
              ctx.save();
              ctx.globalAlpha = at.alpha;
              ctx.fillStyle = p.color;
              ctx.translate(origin.x + at.x, origin.y + at.y);
              ctx.rotate(at.angle * Math.PI / 180);
              if (p.kind === 'dot') {
                ctx.beginPath();
                ctx.arc(0, 0, p.size / 2.4, 0, Math.PI * 2);
                ctx.fill();
              } else if (p.kind === 'ribbon') {
                const w = p.size * 1.8 * (0.25 + 0.75 * Math.abs(Math.cos(at.angle * Math.PI / 90)));
                ctx.fillRect(-w / 2, -p.size * 0.35, w, p.size * 0.7);
              } else {
                ctx.fillRect(-p.size * 0.2, -p.size, p.size * 0.4, p.size * 2);
              }
              ctx.restore();
            });
          }
          requestAnimationFrame(draw);
        };
        requestAnimationFrame(draw);
      }

      /* ----- Top bar and hero ----- */
      function drawHero(now) {
        const phase = state.phase;
        const head = headline(T, phase, journey.name, journey.dest);
        $('headline').textContent = head;
        document.title = head + ' · Next Stop';
        // The top bar: when the trip ends, coloured by the delay.
        rollTo($('eta-time'), clock(state.arr));
        const level = delayLevel(state.delay);
        $('eta').className = 'eta ' + level;

        // The hero: the next thing that happens, with the counter until then.
        const over = phase === 'arrived' || phase === 'expired';
        const hero = heroModel(T, state, now);
        rollTo($('countdown'), hero.counter);
        $('countdown').hidden = over;
        $('counter').classList.toggle('long', hero.counter.length > 4);
        $('counter').classList.toggle('done', over);
        $('hero-title').textContent = hero.title;
        $('hero-time').textContent = phase === 'expired' ? T.linkExpired : clock(hero.time);
        const d = $('eta-delay');
        const showDelay = hero.kind === 'off' || hero.kind === 'board' || hero.kind === 'before' || hero.kind === 'arrived';
        d.hidden = !showDelay;
        d.textContent = delayText(T, hero.delay);
        d.className = 'delay ' + delayLevel(hero.delay);
        $('hero-plat').textContent = hero.platform || '';
        $('hero-plat').hidden = !hero.platform;

        // The bar of the whole trip: a piece per leg in its line colour, the travelled part pale.
        const progress = tripProgress(legs, now);
        const segs = progressSegments(legs);
        const sig = JSON.stringify(segs);
        if (sig !== segsSig) {
          segsSig = sig;
          $('progress-segs').replaceChildren(...segs.map((g) => {
            const piece = el('i', g.walk ? 'walk' : '');
            piece.style.left = (g.left * 100).toFixed(2) + '%';
            piece.style.width = (g.width * 100).toFixed(2) + '%';
            if (!g.walk) piece.style.background = g.color || 'var(--blue)';
            return piece;
          }));
        }
        const fill = $('progress-fill');
        const dot = $('progress-dot');
        if (!fill.dataset.shown && !reducedMotion) {
          // The first fill springs from empty to where the trip is.
          fill.dataset.shown = '1';
          fill.classList.add('first');
          dot.classList.add('first');
          void fill.offsetWidth;
          setTimeout(() => {
            fill.classList.remove('first');
            dot.classList.remove('first');
          }, 1300);
        }
        fill.style.transform = 'scaleX(' + progress.value.toFixed(4) + ')';
        dot.style.left = (progress.value * 100).toFixed(2) + '%';
        dot.hidden = over;
        $('bar-start').textContent = clock(legs[0].dep) + ' ' + T.barStart;
        $('bar-now').textContent = over || phase === 'before' ? '' : T.barNow(clock(now));
        $('bar-end').textContent = clock(legs[legs.length - 1].arr) + ' ' + T.barEnd;

        $('state').textContent = stateLine(T, state);
        const f = over ? { kind: 'over' } : freshness(lastOk, lastFail, pollable, now);
        const live = $('live');
        live.className = 'live chip-live ' + f.kind;
        live.hidden = f.kind === 'over';
        if (f.kind !== 'over') $('fresh').textContent = freshnessText(T, f);
        document.body.dataset.phase = phase;
        if (phase === 'arrived' && !celebrated) {
          celebrated = true;
          celebrate();
        }
        if (phase === 'expired') $('check').hidden = false;
        const notes = [];
        if (demo) notes.push(T.demo);
        if (phase === 'expired') notes.push(T.expiredNote);
        if (journey.created) notes.push(T.shared(clock(journey.created)));
        $('note').textContent = notes.join(' · ');
      }

      /* ----- Timeline ----- */
      function stopRow(cls, time, name, delay) {
        const li = el('li', 'stop ' + cls);
        li.append(el('span', 'time', clock(time)), el('span', 'dot'));
        const label = el('span', 'name', name);
        li.append(label);
        if (delay) li.append(el('span', 'delay small ' + delayLevel(delay), delayText(T, delay)));
        return li;
      }

      function drawTimeline(now) {
        const model = timelineModel(legs, state, now, 3);
        const sig = JSON.stringify(model) + JSON.stringify([...open]) + JSON.stringify([...showAll]);
        if (sig === timelineSig) return;
        timelineSig = sig;
        const list = $('timeline');
        list.replaceChildren();
        model.forEach((m) => {
          const isOpen = open.has(m.index) ? open.get(m.index) : m.status === 'current';
          const item = el('li', 'leg ' + m.status + (isOpen ? ' open' : '') + (m.walk ? ' is-walk' : ''));
          // The first time the rows come in one after the other from below, with a spring.
          if (!timelineShown && !reducedMotion) {
            item.classList.add('enter');
            item.style.setProperty('--i', String(Math.min(list.childElementCount, 8)));
          }
          if (unfolding === m.index && isOpen) item.classList.add('unfolding');
          item.style.setProperty('--line', m.walk ? 'var(--muted)' : m.color || 'var(--blue)');
          const row = el('button', 'leg-row');
          row.type = 'button';
          row.setAttribute('aria-expanded', String(isOpen));
          row.append(badge(m));
          const main = el('span', 'leg-main');
          main.append(el('span', 'leg-route', m.walk ? T.walkLeg + ' · ' + T.minutes(m.minutes) : m.from.name + ' → ' + m.to.name));
          const sub = [];
          if (m.walk) sub.push(m.from.name === m.to.name ? m.to.name : m.from.name + ' → ' + m.to.name);
          else {
            if (m.direction) sub.push(T.towards(m.direction));
            if (m.stops.length) sub.push(T.stopCount(m.stops.length + 1));
            if (m.cancelled) sub.push(T.cancelled);
          }
          main.append(el('span', 'leg-sub' + (m.cancelled ? ' bad' : ''), sub.join(' · ')));
          row.append(main);
          const times = el('span', 'leg-times');
          times.append(el('b', m.from.delay > 0 ? 'late' : '', clock(m.from.time)), el('small', '', clock(m.to.time)));
          row.append(times, el('span', 'chev'));
          item.append(row);

          const detail = el('div', 'leg-detail');
          const inner = el('div', 'leg-detail-inner');
          const stops = el('ol', 'stops');
          stops.append(stopRow('board' + (m.from.passed ? ' passed' : ''), m.from.time, m.from.name, m.walk ? 0 : m.from.delay));
          if (m.status === 'current' && !showAll.has(m.index)) {
            if (m.passed) stops.append(el('li', 'stop summary passed', T.stopsPassed(m.passed)));
            m.preview.forEach((s) => stops.append(stopRow(s.status, s.time, s.name)));
            if (m.more) {
              const more = el('li', 'stop summary more');
              const button = el('button', 'more-btn', T.moreStops(m.more));
              button.type = 'button';
              button.addEventListener('click', (e) => {
                e.stopPropagation();
                showAll.add(m.index);
                render();
              });
              more.append(button);
              stops.append(more);
            }
          } else {
            m.stops.forEach((s) => stops.append(stopRow(s.status, s.time, s.name)));
          }
          stops.append(stopRow('alight' + (m.to.next ? ' next' : '') + (m.to.at ? ' at' : '') + (m.status === 'done' ? ' passed' : ''), m.to.time, m.to.name, m.walk ? 0 : m.to.delay));
          inner.append(stops);
          detail.append(inner);
          item.append(detail);
          row.addEventListener('click', () => {
            const next = !item.classList.contains('open');
            open.set(m.index, next);
            item.classList.toggle('open', next);
            // Opened, its stops spring in once.
            item.classList.toggle('unfolding', next && !reducedMotion);
            unfolding = next ? m.index : -1;
            setTimeout(() => {
              item.classList.remove('unfolding');
              if (unfolding === m.index) unfolding = -1;
            }, 700);
            row.setAttribute('aria-expanded', String(next));
            timelineSig = JSON.stringify(model) + JSON.stringify([...open]) + JSON.stringify([...showAll]);
            setTimeout(layoutVars, 320);
          });
          list.append(item);
        });
        timelineShown = true;
        // When the current leg changes (and at the start), bring it into view where the cards show.
        const current = model.find((m) => m.status === 'current');
        const currentIndex = current ? current.index : -1;
        if (currentIndex !== scrolledTo && (isWide() || sheetOpen)) {
          scrolledTo = currentIndex;
          const item = list.querySelector('.leg.current');
          if (item && item.scrollIntoView) item.scrollIntoView({ block: 'nearest', behavior: reducedMotion ? 'auto' : 'smooth' });
        }
        layoutVars();
      }

      function render() {
        const now = nowMs();
        legs = computeLegs(journey, live);
        state = tripState(legs, now);
        drawHero(now);
        drawTimeline(now);
        target = null;
        if (document.hidden) return;
        if (!vehicleVisible() && vehicleMarker) updateVehicle(performance.now());
      }

      /* ----- Live data ----- */
      async function fetchLeg(index) {
        const leg = journey.legs[index];
        const controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
        const timeout = setTimeout(() => controller && controller.abort(), 15000);
        try {
          const options = {
            signal: controller ? controller.signal : undefined,
            headers: { Accept: 'application/json' },
            referrerPolicy: 'strict-origin-when-cross-origin',
          };
          const response = await (demo ? demo.fetch(tripUrl(leg), options) : fetch(tripUrl(leg), options));
          if (response.status === 429) throw new Error('rate');
          if (!response.ok) throw new Error('http ' + response.status);
          const json = await response.json();
          live[index] = leg.provider === 'tr' ? parseTransitousTrip(json) : parseBvgTrip(json);
          failures[index] = 0;
          lastOk = nowMs();
          return true;
        } catch (e) {
          // Network errors, a blocked request (CORS) or a refusal: wait longer each time.
          failures[index] = (failures[index] || 0) + 1;
          lastFail = nowMs();
          return false;
        } finally {
          nextPoll[index] = nowMs() + pollDelay(document.hidden, failures[index]);
          clearTimeout(timeout);
        }
      }

      async function poll() {
        if (polling) return;
        polling = true;
        try {
          const now = nowMs();
          if (state.phase !== 'expired') {
            // At the first load every ride is asked for once, for its way on the map.
            const due = firstPoll
              ? journey.legs.map((l, i) => i).filter((i) => !journey.legs[i].walk && journey.legs[i].trip && journey.legs[i].provider)
              : legsToPoll(journey, legs, now).filter((i) => !(nextPoll[i] > now));
            for (const i of due) await fetchLeg(i); // one after another, gently
          }
        } finally {
          firstPoll = false;
          polling = false;
          render();
          drawRoute(true);
        }
      }

      document.addEventListener('visibilitychange', () => {
        if (document.hidden) return;
        // Back on screen: ask at once, then every 20 s again.
        Object.keys(nextPoll).forEach((k) => {
          nextPoll[k] = 0;
        });
        lastFrame = 0;
        poll();
        kick();
      });

      $('follow').addEventListener('click', () => setFollowing(true));
      [$('grabber'), $('hero')].forEach((handle) => {
        handle.addEventListener('pointerdown', (e) => {
          startDrag(e);
          if (dragging && handle.setPointerCapture) handle.setPointerCapture(e.pointerId);
        });
        handle.addEventListener('pointermove', moveDrag);
        handle.addEventListener('pointerup', (e) => endDrag(e, true));
        handle.addEventListener('pointercancel', (e) => endDrag(e, false));
      });
      // The keyboard: the grabber is a button that opens and collapses the sheet.
      $('grabber').addEventListener('keydown', (e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          setSheet(!sheetOpen);
        }
      });
      window.addEventListener('resize', layoutVars);

      // The map library comes last: the hero and the timeline work without it.
      window.NextStopUi.loadMapLibrary(initMap);
      render();
      poll();
      setInterval(() => {
        if (!document.hidden) render();
        poll();
      }, demo ? 250 : 1000);
      kick();
    }
  })();
}
