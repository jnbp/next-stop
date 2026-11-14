/*
 * Demo of the live page ("#demo"): a made-up trip across Berlin in fast motion, with fake
 * answers in the form of the BVG API, so the design can be looked at without any request to a
 * timetable API. Only loaded by app.js for a "#demo" fragment.
 *
 * Options in the fragment: #demo&speed=12&at=8&name=Ana&stale=1
 *   speed  how many seconds of the trip pass per real second (default 12)
 *   at     minute of the trip to start at (default 10, in the U8; -5 before, 70 arrived, 200 expired)
 *   name   the first name in the headline ("" for none)
 *   stale  the API stops answering one minute (real time) into the demo, to see the stale state
 *   arrive the trip arrives a few seconds into the demo, to see the check and the confetti
 *          (#demo&arrive=1; sets at and speed unless they are given)
 */
'use strict';
(function () {
  // Stops with [name, id, lat, lon, minute of arrival in the plan].
  const U8 = [
    ['U Alexanderplatz', '900100005', 52.52153, 13.41123, 6],
    ['U Jannowitzbrücke', '900100004', 52.5153, 13.4183, 8],
    ['U Heinrich-Heine-Str.', '900100008', 52.5108, 13.4163, 9],
    ['U Moritzplatz', '900013101', 52.5037, 13.4108, 11],
    ['U Kottbusser Tor', '900013102', 52.4991, 13.4181, 13],
    ['U Schönleinstr.', '900016201', 52.4931, 13.4220, 15],
    ['U Hermannplatz', '900078101', 52.48693, 13.42484, 17],
  ];
  const U7 = [
    ['U Hermannplatz', '900078101', 52.48660, 13.42430, 21],
    ['U Rathaus Neukölln', '900078102', 52.4811, 13.4352, 23],
    ['U Karl-Marx-Str.', '900078103', 52.4762, 13.4398, 24],
    ['S+U Neukölln', '900078201', 52.4693, 13.4422, 26],
    ['U Grenzallee', '900079221', 52.4633, 13.4448, 27],
    ['U Blaschkoallee', '900080402', 52.4524, 13.4489, 29],
    ['U Parchimer Allee', '900080401', 52.4459, 13.4500, 30],
    ['U Britz-Süd', '900080406', 52.4373, 13.4475, 32],
    ['U Johannisthaler Chaussee', '900082202', 52.4295, 13.4535, 33],
    ['U Lipschitzallee', '900082201', 52.4247, 13.4630, 35],
    ['U Wutzkyallee', '900083101', 52.4231, 13.4746, 36],
    ['U Zwickauer Damm', '900083102', 52.4235, 13.4838, 38],
    ['U Rudow', '900083201', 52.4157, 13.4964, 40],
  ];
  const X7 = [
    ['U Rudow', '900083201', 52.41540, 13.49690, 44],
    ['Waßmannsdorfer Chaussee', '900260011', 52.3985, 13.5065, 48],
    ['Schönefeld, Bahnhof', '900260001', 52.3910, 13.5126, 51],
    ['Flughafen BER', '900260005', 52.3644, 13.5097, 58],
  ];
  const RIDES = {
    'demo-u8': { stops: U8, line: 'U8', product: 'subway', direction: 'S+U Wittenau → Hermannstr.', delay: () => 2 },
    'demo-u7': { stops: U7, line: 'U7', product: 'subway', direction: 'U Rudow', delay: () => 1 },
    'demo-x7': { stops: X7, line: 'X7', product: 'bus', direction: 'Flughafen BER', delay: (m) => (m < 30 ? 3 : 5) },
  };

  function create(helpers) {
    const opts = new URLSearchParams(String(helpers.hash || '').replace(/^#demo&?/, ''));
    const arrive = opts.get('arrive') === '1';
    const speed = Math.max(1, Math.min(600, Number(opts.get('speed')) || (arrive ? 30 : 12)));
    const at = opts.has('at') ? Number(opts.get('at')) || 0 : arrive ? 61 : 10;
    const name = opts.has('name') ? opts.get('name') : 'Ana';
    const stale = opts.get('stale') === '1';
    const realStart = Date.now();
    // The trip starts [at] minutes before the demo, on a whole minute.
    const t0 = Math.round(realStart / 60000) * 60000 - at * 60000;
    const now = () => t0 + at * 60000 + (Date.now() - realStart) * speed;
    const minute = (m) => t0 + m * 60000;
    const sec = (m) => Math.round(minute(m) / 1000);

    const place = (s, timeKey, delayKey, delay) => {
      const p = { name: s[0], id: s[1], lat: s[2], lon: s[3] };
      // Departures 30 s after the arrival, as in the fake answers below.
      p[timeKey] = sec(s[4]) + (timeKey === 'dep' ? 30 : 0);
      if (delay) p[delayKey] = delay;
      return p;
    };
    const ride = (trip, r) => ({
      p: 'bvg', trip, line: r.line, dir: r.direction.replace(/^.*→ /, ''),
      from: place(r.stops[0], 'dep', 'dd', r.delay(0)),
      to: place(r.stops[r.stops.length - 1], 'arr', 'ad', r.delay(0)),
    });
    const walk = (from, to, m0, m1) => ({
      walk: true,
      from: { name: from[0], lat: from[1], lon: from[2], dep: sec(m0) },
      to: { name: to[0], id: to[3], lat: to[1], lon: to[2], arr: sec(m1) },
    });
    const payload = {
      n: name || undefined,
      t: sec(-3),
      dest: { name: 'Flughafen BER', lat: 52.3644, lon: 13.5097 },
      legs: [
        walk(['Karl-Liebknecht-Str. 13', 52.5235, 13.4086], ['U Alexanderplatz', 52.52153, 13.41123, '900100005'], 0, 5),
        ride('demo-u8', RIDES['demo-u8']),
        walk(['U Hermannplatz', 52.48693, 13.42484], ['U Hermannplatz (U7)', 52.48660, 13.42430, '900078101'], 17, 19),
        ride('demo-u7', RIDES['demo-u7']),
        walk(['U Rudow', 52.4157, 13.4964], ['U Rudow (Bus)', 52.41540, 13.49690, '900083201'], 40, 42),
        ride('demo-x7', RIDES['demo-x7']),
      ],
    };

    /** An answer like v6.bvg.transport.rest/trips/:id?polyline=true&stopovers=true. */
    function trip(id) {
      const r = RIDES[id];
      const tripMinute = (now() - t0) / 60000;
      const delay = r.delay(tripMinute) * 60000;
      const iso = (t) => new Date(t).toISOString();
      const stopovers = r.stops.map((s, i) => {
        const planned = minute(s[4]);
        const last = i === r.stops.length - 1;
        return {
          stop: { type: 'stop', id: s[1], name: s[0], location: { type: 'location', latitude: s[2], longitude: s[3] } },
          plannedArrival: i === 0 ? null : iso(planned),
          arrival: i === 0 ? null : iso(planned + delay),
          plannedDeparture: last ? null : iso(planned + 30000),
          departure: last ? null : iso(planned + 30000 + delay),
        };
      });
      // A slightly bent way between the stops, as a real polyline would be.
      const features = [];
      r.stops.forEach((s, i) => {
        features.push({ type: 'Feature', properties: {}, geometry: { type: 'Point', coordinates: [s[3], s[2]] } });
        const next = r.stops[i + 1];
        if (next) {
          const lat = (s[2] + next[2]) / 2 + (next[3] - s[3]) * 0.12;
          const lon = (s[3] + next[3]) / 2 - (next[2] - s[2]) * 0.12;
          features.push({ type: 'Feature', properties: {}, geometry: { type: 'Point', coordinates: [lon, lat] } });
        }
      });
      return {
        trip: {
          id, direction: r.direction.replace(/^.*→ /, ''), cancelled: false,
          line: { type: 'line', name: r.line, product: r.product },
          stopovers,
          polyline: { type: 'FeatureCollection', features },
        },
      };
    }

    function fakeFetch(url) {
      const m = /\/trips\/(demo-[a-z0-9]+)/.exec(url);
      return new Promise((resolve, reject) => {
        setTimeout(() => {
          if (stale && Date.now() - realStart > 60000) {
            reject(new Error('offline (demo)'));
            return;
          }
          if (!m || !RIDES[m[1]]) {
            resolve({ ok: false, status: 404, json: () => Promise.resolve({}) });
            return;
          }
          const body = trip(m[1]);
          resolve({ ok: true, status: 200, json: () => Promise.resolve(body) });
        }, 250);
      });
    }

    return {
      hash: '#v=1&d=' + helpers.toBase64Url(JSON.stringify(payload)),
      now,
      fetch: fakeFetch,
    };
  }

  window.NextStopDemo = { create };
})();
