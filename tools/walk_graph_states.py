#!/usr/bin/env python3
"""
The walking networks around the stations of many regions (the states of Germany) from one
large .osm.pbf file, one tools/walk_graph.py graph per region, within a small machine's
memory and in steps of a few minutes each (every call does what it can within --budget
seconds and keeps its results under <out-dir>/work, so it is simply called again until it
says "all graphs written"):

    python3 tools/walk_graph_states.py --stations-dir tools/osm-pbf --out-dir tools/osm-pbf/walk-graphs \
        tools/osm-pbf/germany-latest.osm.pbf
    python3 tools/build_transfer_walks.py --source tools/osm-pbf/hamburg-stations.json \
        --graph tools/osm-pbf/walk-graphs/hamburg.json \
        --asset app/src/main/assets/osm-regions/osm-hamburg.json \
        --out app/src/main/assets/osm-regions/transfer-walks-hamburg.json

The stations of region R come from <stations-dir>/R-stations.json (the extract its station
pack was built from, so that the platform axes match). tools/walk_graph.py first holds every
walkable way of the file (some 25 million for Germany) and then locates the ways near a
station; this instead cuts the file into chunks of blocks (each read on its own, with the
file's header block in front), keeps the locations of the nodes near any station (a Python
test per node, in parallel over the chunks), sorts them into numpy arrays on disk, and then
takes from the ways of each chunk the runs of nodes near a station of each region. Same
output and rules as tools/walk_graph.py (WALK_RADIUS_METERS around a station's box, cut to
the part near the box with one node more at each end where that node lies within
STORE_MARGIN_METERS more); for Berlin the two give the same graph.

Data (c) OpenStreetMap contributors, ODbL 1.0.
"""

import argparse
import gc
import json
import multiprocessing
import os
import pickle
import struct
import sys
import time
from array import array

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import extract_osm_pbf as ex  # noqa: E402
import walk_graph as wg  # noqa: E402

osmium = ex.osmium

# Nodes this much beyond the cut (radius + CUT_MARGIN) are still located, for the one node
# more at the end of a run.
STORE_MARGIN_METERS = 250.0
GRID = 100.0  # cells of 0.01 degrees
WALK_KEYS = ("highway", "railway", "public_transport", "indoor")
CHUNK_BYTES = 32 << 20
BATCH = 20000  # ways located together
WORK_VERSION = 3


def cell(lat, lon):
    return int(lat * GRID), int(lon * GRID)


def box_cells(box):
    for y in range(int(box[0] * GRID), int(box[2] * GRID) + 1):
        for x in range(int(box[1] * GRID), int(box[3] * GRID) + 1):
            yield y, x


def save_pickle(value, name):
    with open(name + ".part", "wb") as handle:
        pickle.dump(value, handle, protocol=pickle.HIGHEST_PROTOCOL)
    os.replace(name + ".part", name)


def load_pickle(name, default=None):
    if not os.path.exists(name):
        return default
    with open(name, "rb") as handle:
        return pickle.load(handle)


# --- The blocks of the file -------------------------------------------------------------

def varint(data, i):
    value = shift = 0
    while True:
        b = data[i]
        i += 1
        value |= (b & 0x7F) << shift
        shift += 7
        if b < 0x80:
            return value, i


def pbf_blobs(path):
    """(offset, length, type) of every block of a .osm.pbf file, from the block headers."""
    blobs = []
    with open(path, "rb") as handle:
        offset = 0
        while True:
            head = handle.read(4)
            if len(head) < 4:
                break
            n = struct.unpack(">I", head)[0]
            header = handle.read(n)
            i = 0
            kind = None
            size = 0
            while i < len(header):
                key, i = varint(header, i)
                field, wire = key >> 3, key & 7
                if wire == 2:
                    length, i = varint(header, i)
                    if field == 1:
                        kind = header[i:i + length].decode()
                    i += length
                elif wire == 0:
                    value, i = varint(header, i)
                    if field == 3:
                        size = value
                else:
                    raise ValueError("unexpected block header")
            blobs.append((offset, 4 + n + size, kind))
            offset += 4 + n + size
            handle.seek(offset)
    return blobs


def chunk_buffer(path, blobs, chunk):
    """The blocks of [chunk] (first, last index) with the header block in front, as a file."""
    first, last = chunk
    with open(path, "rb") as handle:
        handle.seek(blobs[0][0])
        data = handle.read(blobs[0][1])
        handle.seek(blobs[first][0])
        data += handle.read(blobs[last][0] + blobs[last][1] - blobs[first][0])
    return osmium.io.FileBuffer(data, "pbf")


def make_chunks(blobs):
    chunks = []
    start = 1
    size = 0
    for i in range(1, len(blobs)):
        size += blobs[i][1]
        if size >= CHUNK_BYTES:
            chunks.append((start, i))
            start = i + 1
            size = 0
    if start < len(blobs):
        chunks.append((start, len(blobs) - 1))
    return chunks


# --- Station boxes ----------------------------------------------------------------------

def region_boxes(regions, stations_dir, radius, cache_file, deadline):
    """region -> (near boxes, store boxes), cached region by region; None until all are read."""
    boxes = load_pickle(cache_file, {})
    for region in regions:
        source = os.path.join(stations_dir, "%s-stations.json" % region)
        key = (region, os.path.getsize(source), int(os.path.getmtime(source)), radius)
        if boxes.get(region, (None,))[0] == key:
            continue
        if time.time() > deadline:
            return None
        clock = ex.Clock()
        with open(source, "r", encoding="utf-8") as handle:
            stations = json.load(handle)["stations"]
        near = wg.station_boxes(stations, radius + wg.CUT_MARGIN_METERS)
        store = [ex.expand(b, STORE_MARGIN_METERS) for b in near]
        boxes[region] = (key, near, store)
        del stations
        gc.collect()
        save_pickle(boxes, cache_file)
        ex.log("  %s  %s: %d station boxes" % (clock, region, len(near)))
    return {region: boxes[region][1:] for region in regions}


# --- The steps over the chunks (run in worker processes) --------------------------------

STATE = {}


def init_worker(state):
    STATE.update(state)


def store_cells(boxes):
    """Cell -> the store boxes that meet it."""
    cells = {}
    for near, store in boxes.values():
        for box in store:
            for c in box_cells(box):
                cells.setdefault(c, []).append(box)
    return cells


def node_step(index):
    """Locations of the nodes near a station in one chunk; elevators and doors among them."""
    path, blobs, chunks, work = STATE["path"], STATE["blobs"], STATE["chunks"], STATE["work"]
    cells = STATE["cells"]
    ids = array("q")
    lats = array("d")
    lons = array("d")
    elevators = array("q")
    doors = array("q")
    count = 0
    other = False
    for obj in osmium.FileProcessor(chunk_buffer(path, blobs, chunks[index]), osmium.osm.NODE | osmium.osm.WAY):
        if not obj.is_node():
            other = True
            break
        count += 1
        location = obj.location
        lat = location.lat
        lon = location.lon
        here = cells.get((int(lat * GRID), int(lon * GRID)))
        if here is None:
            continue
        for box in here:
            if box[0] <= lat <= box[2] and box[1] <= lon <= box[3]:
                break
        else:
            continue
        ids.append(obj.id)
        lats.append(lat)
        lons.append(lon)
        tags = obj.tags
        if len(tags):
            if tags.get("highway") == "elevator":
                elevators.append(obj.id)
            if "entrance" in tags:
                doors.append(obj.id)
    name = os.path.join(work, "nodes-%04d.npz" % index)
    with open(name + ".part", "wb") as handle:
        np.savez(handle, ids=np.frombuffer(ids, np.int64), lats=np.frombuffer(lats, np.float64),
                 lons=np.frombuffer(lons, np.float64), elevators=np.frombuffer(elevators, np.int64),
                 doors=np.frombuffer(doors, np.int64), info=np.array([count, int(other)], np.int64))
    os.replace(name + ".part", name)
    return index, count, len(ids), other


def open_nodes(work):
    return tuple(np.load(os.path.join(work, "%s.npy" % n), mmap_mode="r") for n in ("ids", "lats", "lons"))


def locate_batch(nodes, refs):
    """(found, lats, lons) for an int64 array of refs."""
    ids, lats, lons = nodes
    pos = np.searchsorted(ids, refs)
    pos[pos >= len(ids)] = 0
    found = ids[pos] == refs
    return found, lats[pos], lons[pos]


def runs_of(kind, refs, found, plat, plon, near_cells):
    """(region index, run) of the parts of a way near a station of each region (as walk_graph)."""
    inside = {}
    for i in np.flatnonzero(found).tolist():
        lat = plat[i]
        lon = plon[i]
        for r, box in near_cells.get((int(lat * GRID), int(lon * GRID)), ()):
            if box[0] <= lat <= box[2] and box[1] <= lon <= box[3]:
                flags = inside.get(r)
                if flags is None:
                    flags = inside[r] = [False] * len(refs)
                flags[i] = True
    if not inside:
        return []
    k, level, steps, area = kind
    closed = len(refs) > 3 and refs[0] == refs[-1]
    out = []
    n = len(refs)
    for r, flags in inside.items():
        keep = [False] * n
        for i, flag in enumerate(flags):
            if flag:
                for j in (i - 1, i, i + 1):
                    if 0 <= j < n and found[j]:
                        keep[j] = True
        is_area = 1 if (area and closed and all(keep)) else 0
        run = []
        for i in range(n):
            if keep[i]:
                run.append(int(refs[i]))
            elif run:
                if len(run) >= 2:
                    out.append((r, [k, level, steps, is_area] + run))
                run = []
        if len(run) >= 2:
            out.append((r, [k, level, steps, is_area] + run))
    return out


def flush(batch, nodes, near_cells, handle):
    if not batch:
        return 0
    lengths = [len(refs) for _, refs in batch]
    flat = np.fromiter((ref for _, refs in batch for ref in refs), np.int64, sum(lengths))
    found, plat, plon = locate_batch(nodes, flat)
    kept = 0
    start = 0
    for (kind, refs), n in zip(batch, lengths):
        f = found[start:start + n]
        if f.any():
            for r, record in runs_of(kind, refs, f, plat[start:start + n].tolist(), plon[start:start + n].tolist(), near_cells):
                handle.write("%d\t%s\n" % (r, json.dumps(record, separators=(",", ":"))))
                kept += 1
        start += n
    batch.clear()
    return kept


def way_step(index):
    """The runs near a station of the walkable ways of one chunk; the outline ways of the multipolygons."""
    path, blobs, chunks, work = STATE["path"], STATE["blobs"], STATE["chunks"], STATE["work"]
    near_cells, members = STATE["near_cells"], STATE["members"]
    nodes = open_nodes(work)
    outlines = {}
    count = walkable = kept = 0
    other = False
    batch = []
    name = os.path.join(work, "ways-%04d.txt" % index)
    with open(name + ".part", "w") as handle:
        for obj in osmium.FileProcessor(chunk_buffer(path, blobs, chunks[index]), osmium.osm.WAY | osmium.osm.RELATION):
            if obj.is_relation():
                other = True
                break
            count += 1
            wid = obj.id
            if wid in members:
                outlines[wid] = array("q", [n.ref for n in obj.nodes])
            tags = obj.tags
            if not any(k in tags for k in WALK_KEYS):
                continue
            kind = wg.walk_kind(ex.tag_dict(tags))
            if kind is None or len(obj.nodes) < 2:
                continue
            walkable += 1
            batch.append((kind, [n.ref for n in obj.nodes]))
            if len(batch) >= BATCH:
                kept += flush(batch, nodes, near_cells, handle)
        kept += flush(batch, nodes, near_cells, handle)
    save_pickle(outlines, os.path.join(work, "outlines-%04d.pickle" % index))
    os.replace(name + ".part", name)
    save_pickle([count, walkable, kept, int(other)], os.path.join(work, "ways-%04d.info" % index))
    return index, count, walkable, kept, other


# --- Driver -----------------------------------------------------------------------------

def run_parallel(function, todo, state, workers, deadline, label):
    """Runs [function] over [todo] until all are done or [deadline] is near; the results."""
    results = []
    if not todo:
        return results
    clock = ex.Clock()
    context = multiprocessing.get_context("fork")
    with context.Pool(workers, initializer=init_worker, initargs=(state,)) as pool:
        pending = []
        todo = list(todo)
        while todo or pending:
            while todo and len(pending) < workers and time.time() < deadline:
                pending.append(pool.apply_async(function, (todo.pop(0),)))
            if not pending:
                break
            pending[0].wait()
            result = pending.pop(0).get()
            results.append(result)
            ex.log("  %s  %s %s" % (clock, label, result))
    return results


def main(argv=None):
    parser = argparse.ArgumentParser(description="Walk graphs of many regions from one .osm.pbf file, in steps.")
    parser.add_argument("file", help="the .osm.pbf file (all of Germany)")
    parser.add_argument("--stations-dir", required=True, help="folder with <region>-stations.json")
    parser.add_argument("--out-dir", required=True, help="writes <region>.json here (its work/ keeps the steps)")
    parser.add_argument("--regions", default=None, help="comma-separated (default: every *-stations.json)")
    parser.add_argument("--radius", type=float, default=wg.WALK_RADIUS_METERS)
    parser.add_argument("--budget", type=float, default=140.0, help="seconds to start new work in")
    parser.add_argument("--workers", type=int, default=3)
    args = parser.parse_args(argv)
    start = time.time()
    deadline = start + args.budget
    if args.regions:
        regions = args.regions.split(",")
    else:
        regions = sorted(n[:-len("-stations.json")] for n in os.listdir(args.stations_dir) if n.endswith("-stations.json"))
    work = os.path.join(args.out_dir, "work")
    os.makedirs(work, exist_ok=True)
    stat = os.stat(args.file)
    key = [os.path.basename(args.file), stat.st_size, stat.st_mtime_ns, regions, args.radius, WORK_VERSION]
    meta_name = os.path.join(work, "meta.json")
    meta = json.load(open(meta_name)) if os.path.exists(meta_name) else None
    if meta is None or meta.get("key") != key:
        ex.log("Blocks of %s" % os.path.basename(args.file))
        for name in os.listdir(work):
            os.remove(os.path.join(work, name))
        blobs = pbf_blobs(args.file)
        meta = {"key": key, "blobs": blobs, "chunks": make_chunks(blobs)}
        with open(meta_name, "w") as handle:
            json.dump(meta, handle)
    blobs = [tuple(b) for b in meta["blobs"]]
    chunks = [tuple(c) for c in meta["chunks"]]
    ex.log("%d blocks in %d chunks" % (len(blobs), len(chunks)))

    boxes = region_boxes(regions, args.stations_dir, args.radius, os.path.join(args.out_dir, "boxes.pickle"), deadline)
    if boxes is None:
        ex.log("Station boxes not all read yet: call again")
        return 3
    state = {"path": args.file, "blobs": blobs, "chunks": chunks, "work": work}

    # Multipolygons: the relations are at the end of the file.
    relations_name = os.path.join(work, "relations.pickle")
    relations = load_pickle(relations_name)
    if relations is None:
        clock = ex.Clock()
        relations = []
        for index in range(len(chunks) - 1, -1, -1):
            buffer = chunk_buffer(args.file, blobs, chunks[index])
            first = next(iter(osmium.FileProcessor(buffer)), None)
            buffer = chunk_buffer(args.file, blobs, chunks[index])
            processor = osmium.FileProcessor(buffer, osmium.osm.RELATION).with_filter(
                osmium.filter.TagFilter(("railway", "platform"), ("public_transport", "platform"), ("highway", "pedestrian"),
                                        ("indoor", "area"), ("indoor", "corridor")))
            for relation in processor:
                tags = ex.tag_dict(relation.tags)
                if tags.get("type") != "multipolygon":
                    continue
                kind = wg.walk_kind(dict(tags, area="yes"))
                if kind is None:
                    continue
                ways = array("q", [m.ref for m in relation.members if m.type == "w" and m.role in ("outer", "")])
                if ways:
                    relations.append((relation.id, kind, ways))
            if first is None or not first.is_relation():
                break
        save_pickle(relations, relations_name)
        ex.log("  %s  %d platforms and areas drawn as multipolygons" % (clock, len(relations)))

    # Step 1: the nodes near a station, chunk by chunk, until a chunk holds more than nodes.
    def node_info(index):
        name = os.path.join(work, "nodes-%04d.npz" % index)
        if not os.path.exists(name):
            return None
        return np.load(name)["info"].tolist()

    infos = [node_info(i) for i in range(len(chunks))]
    last_node_chunk = next((i for i, info in enumerate(infos) if info and info[1]), None)
    if last_node_chunk is None:
        todo = [i for i, info in enumerate(infos) if info is None]
        state["cells"] = store_cells(boxes)
        # Chunks in order; any chunk beyond the first one with ways is of no use, so the
        # chunks are done in small rounds.
        while todo and time.time() < deadline:
            round_ = todo[:args.workers * 4]
            todo = todo[len(round_):]
            results = run_parallel(node_step, round_, state, args.workers, deadline, "nodes")
            if any(r[3] for r in results):
                break
        infos = [node_info(i) for i in range(len(chunks))]
        last_node_chunk = next((i for i, info in enumerate(infos) if info and info[1]), None)
        if last_node_chunk is None or any(info is None for info in infos[:last_node_chunk]):
            ex.log("Nodes: %d of the chunks done: call again" % sum(1 for info in infos if info))
            return 3

    # Step 2: the nodes sorted, on disk.
    if not os.path.exists(os.path.join(work, "lons.npy")):
        if time.time() > deadline - 30:
            return 3
        clock = ex.Clock()
        # The file is sorted by type, then id: the chunks in order are sorted already (checked).
        parts = [np.load(os.path.join(work, "nodes-%04d.npz" % i)) for i in range(last_node_chunk + 1)]
        total = sum(len(p["ids"]) for p in parts)
        last = -1
        for p in parts:
            ids = p["ids"]
            if len(ids) and (ids[0] <= last or np.any(np.diff(ids) <= 0)):
                raise SystemExit("the nodes of the file are not sorted by id")
            if len(ids):
                last = ids[-1]
        for name, dtype in (("ids", np.int64), ("lats", np.float64), ("lons", np.float64)):
            target = np.lib.format.open_memmap(os.path.join(work, "%s.part.npy" % name), mode="w+", dtype=dtype, shape=(total,))
            at = 0
            for p in parts:
                values = p[name]
                target[at:at + len(values)] = values
                at += len(values)
            target.flush()
            del target
        for name in ("ids", "lats", "lons"):
            os.replace(os.path.join(work, "%s.part.npy" % name), os.path.join(work, "%s.npy" % name))
        for name in ("elevators", "doors"):
            np.save(os.path.join(work, "%s.npy" % name), np.unique(np.concatenate([p[name] for p in parts])))
        ex.log("  %s  %d nodes near a station" % (clock, total))
        del parts
        gc.collect()

    # Step 3: the ways, from the chunk with the last nodes on.
    def way_info(index):
        return load_pickle(os.path.join(work, "ways-%04d.info" % index))

    winfos = {i: way_info(i) for i in range(last_node_chunk, len(chunks))}
    last_way_chunk = next((i for i in sorted(winfos) if winfos[i] and winfos[i][3]), None)
    if last_way_chunk is None:
        near_cells = {}
        for r, region in enumerate(regions):
            for box in boxes[region][0]:
                for c in box_cells(box):
                    near_cells.setdefault(c, []).append((r, box))
        state["near_cells"] = near_cells
        state["members"] = {w for _, _, ways in relations for w in ways}
        todo = [i for i in sorted(winfos) if winfos[i] is None]
        while todo and time.time() < deadline:
            round_ = todo[:args.workers * 2]
            todo = todo[len(round_):]
            results = run_parallel(way_step, round_, state, args.workers, deadline, "ways")
            if any(r[4] for r in results):
                break
        winfos = {i: way_info(i) for i in range(last_node_chunk, len(chunks))}
        last_way_chunk = next((i for i in sorted(winfos) if winfos[i] and winfos[i][3]), None)
        if last_way_chunk is None or any(winfos[i] is None for i in range(last_node_chunk, last_way_chunk)):
            ex.log("Ways: %d chunks done: call again" % sum(1 for v in winfos.values() if v))
            return 3
    way_chunks = list(range(last_node_chunk, last_way_chunk + 1))
    ex.log("Ways: %d read, %d walkable, %d runs near a station"
           % tuple(sum(winfos[i][k] for i in way_chunks) for k in range(3)))

    # Step 4: the runs split by region, with the outlines of the multipolygons.
    split_done = os.path.join(work, "split.done")
    if not os.path.exists(split_done):
        clock = ex.Clock()
        nodes = open_nodes(work)
        near_cells = {}
        for r, region in enumerate(regions):
            for box in boxes[region][0]:
                for c in box_cells(box):
                    near_cells.setdefault(c, []).append((r, box))
        outs = [open(os.path.join(work, "%s.ways" % region), "w") for region in regions]
        for i in way_chunks:
            with open(os.path.join(work, "ways-%04d.txt" % i)) as handle:
                for line in handle:
                    r, record = line.split("\t", 1)
                    outs[int(r)].write(record)
        outlines = {}
        for i in way_chunks:
            outlines.update(load_pickle(os.path.join(work, "outlines-%04d.pickle" % i)))
        rings = 0
        for _, kind, ways in relations:
            parts = [list(outlines[w]) for w in ways if w in outlines]
            for ring in wg.join_rings(parts):
                if len(ring) < 2:
                    continue
                refs = np.array(ring, np.int64)
                found, plat, plon = locate_batch(nodes, refs)
                for r, record in runs_of(kind, ring, found, plat.tolist(), plon.tolist(), near_cells):
                    outs[r].write(json.dumps(record, separators=(",", ":")) + "\n")
                    rings += 1
        for out in outs:
            out.close()
        open(split_done, "w").close()
        ex.log("  %s  runs split by region (%d of multipolygon outlines)" % (clock, rings))

    # Step 5: one graph per region.
    nodes = open_nodes(work)
    elevators = set(np.load(os.path.join(work, "elevators.npy")).tolist())
    doors = set(np.load(os.path.join(work, "doors.npy")).tolist())
    for region in regions:
        out = os.path.join(args.out_dir, "%s.json" % region)
        if os.path.exists(out) and os.path.getmtime(out) >= os.path.getmtime(split_done):
            continue
        if time.time() > deadline:
            ex.log("Graphs: call again")
            return 3
        index_of = {}
        order = []
        out_ways = []
        with open(os.path.join(work, "%s.ways" % region)) as handle:
            for line in handle:
                record = json.loads(line)
                run = []
                for ref in record[4:]:
                    i = index_of.get(ref)
                    if i is None:
                        i = index_of[ref] = len(order)
                        order.append(ref)
                    run.append(i)
                out_ways.append(record[:4] + run)
        found, plat, plon = locate_batch(nodes, np.array(order, np.int64))
        flat = []
        for lat, lon in zip(plat.tolist(), plon.tolist()):
            flat.append(round(lat, 6))
            flat.append(round(lon, 6))
        graph = {
            "license": "Data (c) OpenStreetMap contributors, ODbL 1.0, https://www.openstreetmap.org/copyright",
            "generated": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            "version": wg.GRAPH_VERSION,
            "radiusMeters": args.radius,
            "nodes": flat,
            "ways": out_ways,
            "elevators": sorted(index_of[n] for n in elevators if n in index_of),
            "doors": sorted(index_of[n] for n in doors if n in index_of),
        }
        ex.log("  %s: %d nodes, %d ways, %d elevators, %d doors"
               % (region, len(order), len(out_ways), len(graph["elevators"]), len(graph["doors"])))
        wg.write_graph(graph, out)
        del graph, out_ways, index_of, order
        gc.collect()
    ex.log("All graphs written")
    return 0


if __name__ == "__main__":
    sys.exit(main())
