"""Night Walk visual context: mapped OSM street-lighting tags and mapped support places.

Evidence rules (see docs/NIGHT_MODE_RESEARCH.md):
  * night_lit     = OSM way explicitly tagged lit=yes / 24/7 / sunset-sunrise. Mapped, NOT verified as
                    currently working, bright or maintained.
  * night_unlit   = OSM way explicitly tagged lit=no. Nothing is ever inferred as unlit.
  * night_unknown = no usable lit tag. Unknown is neither safe nor unsafe. Only a bounded selection is
                    emitted (named major roads + SRM/Potheri walkable local roads) to keep the payload small.
  * night_support = real OSM police / hospital / pharmacy / fuel objects (Overpass snapshot). Opening hours
                    are copied as mapped; "open now" is never asserted.
Pedestrian-prohibited, private, motorway/motorroad, construction and likely-flyover ways are excluded.
"""
from __future__ import annotations

import copy
import json
import math
import re
import time
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable

from app.config import DATA_DIR, DERIVED_DATA_DIR

OSM_TRANSPORT = DATA_DIR / "osm" / "chennai_transport.json"
SUPPORT_POIS = DATA_DIR / "osm" / "chennai_night_support_pois.json"
NIGHT_FILE = DERIVED_DATA_DIR / "chennai_night_context.geojson"

SCHEMA_VERSION = 1
BBOX = (12.65, 79.95, 13.3, 80.35)                  # south, west, north, east (Overpass order)
SRM_CENTER = (80.0415, 12.8225)                     # lon, lat: between SRM Kattankulathur campus and Potheri
SRM_RADIUS_M = 1500.0
MAX_FEATURES = 3000
MAX_SRM_UNKNOWN = 250
SIMPLIFY_TOLERANCE_M = 4.0
CHAIN_MAX_M = 1500.0
MAJOR_MIN_M = 300.0

LIT_VALUES = frozenset({"yes", "24/7", "sunset-sunrise"})
UNLIT_VALUES = frozenset({"no"})
KINDS = ("night_lit", "night_unlit", "night_unknown", "night_support")
SUPPORT_KINDS = ("police", "hospital", "pharmacy", "fuel")

WALKABLE_HIGHWAYS = frozenset({
    "trunk", "trunk_link", "primary", "primary_link", "secondary", "secondary_link", "tertiary", "tertiary_link",
    "unclassified", "residential", "living_street", "service", "road", "pedestrian", "footway", "path", "steps",
    "track", "cycleway",
})
MAJOR_HIGHWAYS = frozenset({"trunk", "primary", "secondary"})
LOCAL_HIGHWAYS = frozenset({"tertiary", "tertiary_link", "unclassified", "residential", "living_street", "service",
                            "road", "pedestrian", "footway", "path", "steps"})
FLYOVER_CANDIDATES = frozenset({"trunk", "trunk_link", "primary", "primary_link", "secondary", "secondary_link"})
BLOCKED_ACCESS = frozenset({"private", "no", "customers", "delivery", "agricultural", "forestry", "military", "permit"})
FOOT_ALLOWED = frozenset({"yes", "designated", "permissive"})
FOOT_BLOCKED = frozenset({"no", "use_sidepath", "private"})
UNKNOWN_SKIP_SERVICE = frozenset({"driveway", "parking_aisle", "drive-through", "emergency_access"})

M_PER_DEG_LON = 108450.0   # at ~13 N (same constants as app.road_context)
M_PER_DEG_LAT = 111195.0

LIMITATIONS = [
    "Lit roads are mapped OpenStreetMap lit tags, not current lamp status; outages and brightness are unknown.",
    "Unknown lighting means no lit tag was mapped; it is neither safe nor unsafe. Most roads are not shown.",
    "Explicitly unlit roads are only those tagged lit=no; nothing is inferred as unlit.",
    "Support places are mapped OSM objects or rows of a historical police directory (AWPS); locations are not live-checked, opening hours are as mapped or unknown, open-now, staffing and entrance access are not verified.",
    "Night context is not a safety rating, a crime measure or a route recommendation.",
]

# ---------------------------------------------------------------- tag rules
def pedestrian_allowed(tags: dict) -> bool:
    """Conservative pedestrian-access filter on OSM way tags (visual layer, not a router)."""
    hw = tags.get("highway")
    if hw not in WALKABLE_HIGHWAYS:
        return False  # motorway(_link), construction, proposed, raceway, platform, corridor, busway, ...
    if "construction" in tags or tags.get("disused") == "yes" or tags.get("indoor") == "yes" or tags.get("area") == "yes":
        return False
    if tags.get("motorroad") == "yes":
        return False
    foot = tags.get("foot")
    if foot in FOOT_BLOCKED:
        return False
    if foot in FOOT_ALLOWED:
        return True
    if tags.get("access") in BLOCKED_ACCESS:
        return False
    if hw == "cycleway":
        return False  # only with explicit foot=yes/designated/permissive
    # Likely flyover / grade separator: elevated major carriageway without explicit pedestrian permission.
    if hw in FLYOVER_CANDIDATES and tags.get("bridge") in ("yes", "viaduct") and tags.get("layer", "0") not in ("0", "-1", "-2"):
        return False
    return True

def lighting_kind(tags: dict) -> str:
    v = tags.get("lit")
    if v in LIT_VALUES:
        return "night_lit"
    if v in UNLIT_VALUES:
        return "night_unlit"
    return "night_unknown"

# ---------------------------------------------------------------- geometry
def in_bbox(c) -> bool:
    s, w, n, e = BBOX
    return (isinstance(c, (list, tuple)) and len(c) >= 2
            and all(isinstance(x, (int, float)) and not isinstance(x, bool) and math.isfinite(x) for x in c[:2])
            and w <= c[0] <= e and s <= c[1] <= n)

def _m(a, b) -> float:
    return math.hypot((b[0] - a[0]) * M_PER_DEG_LON, (b[1] - a[1]) * M_PER_DEG_LAT)

def length_m(coords) -> float:
    return sum(_m(a, b) for a, b in zip(coords, coords[1:]))

def simplify(coords: list, tol_m: float = SIMPLIFY_TOLERANCE_M) -> list:
    """Douglas-Peucker in local metres; keeps endpoints."""
    if len(coords) <= 2:
        return list(coords)
    keep = [False] * len(coords)
    keep[0] = keep[-1] = True
    stack = [(0, len(coords) - 1)]
    while stack:
        i, j = stack.pop()
        a, b = coords[i], coords[j]
        ax, ay = a[0] * M_PER_DEG_LON, a[1] * M_PER_DEG_LAT
        vx, vy = b[0] * M_PER_DEG_LON - ax, b[1] * M_PER_DEG_LAT - ay
        l2 = vx * vx + vy * vy
        best, idx = -1.0, None
        for k in range(i + 1, j):
            px, py = coords[k][0] * M_PER_DEG_LON - ax, coords[k][1] * M_PER_DEG_LAT - ay
            if l2 == 0:
                d = math.hypot(px, py)
            else:
                t = max(0.0, min(1.0, (px * vx + py * vy) / l2))
                d = math.hypot(px - t * vx, py - t * vy)
            if d > best:
                best, idx = d, k
        if idx is not None and best > tol_m:
            keep[idx] = True
            stack += [(i, idx), (idx, j)]
    return [c for c, k in zip(coords, keep) if k]

def midpoint(coords: list) -> list:
    """Point at half the along-line length (lies on the actual geometry)."""
    total = length_m(coords)
    if total == 0:
        return list(coords[0])
    half, run = total / 2, 0.0
    for a, b in zip(coords, coords[1:]):
        seg = _m(a, b)
        if seg and run + seg >= half:
            t = (half - run) / seg
            return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t]
        run += seg
    return list(coords[-1])

def _r(c) -> list:
    return [round(c[0], 6), round(c[1], 6)]

def near_srm(coords) -> bool:
    return any(_m(c, SRM_CENTER) <= SRM_RADIUS_M for c in coords)

# ---------------------------------------------------------------- road features
HIGHWAY_LABEL = {"trunk": "Trunk road", "primary": "Main road", "secondary": "Secondary road", "tertiary": "Local connector",
                 "residential": "Residential street", "living_street": "Living street", "service": "Service road",
                 "unclassified": "Minor road", "footway": "Footpath", "path": "Path", "pedestrian": "Pedestrian street",
                 "steps": "Steps", "track": "Track", "cycleway": "Cycleway (foot allowed)", "road": "Road"}

def _label(tags: dict) -> str:
    hw = tags.get("highway", "road")
    return tags.get("name") or tags.get("ref") or HIGHWAY_LABEL.get(hw.replace("_link", ""), "Road") + (" link" if hw.endswith("_link") else "")

def road_feature(kind: str, ways: list[tuple[int, dict, list]], osm_base: str | None, reason: str | None = None) -> dict:
    """ways: [(way_id, tags, coords)] forming one contiguous chain (one way for lit/unlit)."""
    coords: list = []
    for _, _, cs in ways:
        coords.extend(cs if not coords else cs[1:])
    tags = ways[0][1]
    way_ids = [w for w, _, _ in ways]
    line = [_r(c) for c in simplify(coords)]
    name = _label(tags)
    lit_raw = tags.get("lit")
    if kind == "night_lit":
        title = "Mapped lit: " + name
        detail = f"OSM lit={lit_raw}: mapped street lighting, not verified as working or bright; outages unknown. Not a safety rating."
        fid = f"night_lit_w{way_ids[0]}"
    elif kind == "night_unlit":
        title = "Mapped unlit: " + name
        detail = "OSM lit=no: mapped as unlit, not field-verified. Not by itself a safety rating."
        fid = f"night_unlit_w{way_ids[0]}"
    else:
        title = "Lighting unknown: " + name
        detail = ("No usable OSM lit tag" + (f" (lit={lit_raw})" if lit_raw else "")
                  + ": lighting unknown, neither safe nor unsafe.")
        fid = f"night_unknown_w{way_ids[0]}"
    props = {
        "id": fid, "title": title, "layer": "night", "evidence": "mapped", "kind": kind, "detail": detail,
        "source": "OSM chennai_transport.json" + (f" ({osm_base})" if osm_base else "") + ", ODbL",
        "observed_at": None, "expires_at": None,
        "road_context": {"type": "LineString", "coordinates": line, "approximate": True,
                         "source": "OpenStreetMap way geometry (Douglas-Peucker simplified, 4 m)"},
        "osm_way_ids": way_ids, "highway": tags.get("highway"),
        "lit_tag": lit_raw, "length_m": round(length_m(coords), 1),
    }
    if reason:
        props["selection"] = reason
    return {"type": "Feature", "id": fid, "geometry": {"type": "Point", "coordinates": _r(midpoint(line))}, "properties": props}

def _chains(ways: list[tuple[int, dict, list, list]], max_m: float = CHAIN_MAX_M) -> list[list[tuple[int, dict, list]]]:
    """Greedy join of ways (way_id, tags, coords, node_ids) sharing endpoint nodes and the same (name, highway),
    capped at max_m. Only geometry already in OSM is used; ways are reversed when needed."""
    groups: dict[tuple, list] = defaultdict(list)
    for w in ways:
        groups[(w[1].get("name"), w[1].get("highway"))].append(w)
    out = []
    for (name, _), group in sorted(groups.items(), key=lambda kv: (str(kv[0][0]), str(kv[0][1]))):
        if name is None:  # never merge unnamed ways
            out.extend([[(w[0], w[1], w[2])] for w in group])
            continue
        by_end: dict[int, list] = defaultdict(list)
        for w in group:
            by_end[w[3][0]].append(w); by_end[w[3][-1]].append(w)
        used: set[int] = set()
        for w in sorted(group, key=lambda w: w[0]):
            if w[0] in used:
                continue
            used.add(w[0])
            chain = [(w[0], w[1], list(w[2]), list(w[3]))]
            total = length_m(w[2])
            for forward in (True, False):
                while total < max_m:
                    end = chain[-1][3][-1] if forward else chain[0][3][0]
                    nxt = next((c for c in by_end[end] if c[0] not in used), None)
                    if nxt is None:
                        break
                    used.add(nxt[0])
                    cs, ns = list(nxt[2]), list(nxt[3])
                    if forward and ns[0] != end or not forward and ns[-1] != end:
                        cs.reverse(); ns.reverse()
                    item = (nxt[0], nxt[1], cs, ns)
                    chain.append(item) if forward else chain.insert(0, item)
                    total += length_m(cs)
            out.append([(a, b, c) for a, b, c, _ in chain])
    return out

def road_features(osm: dict, max_features: int = MAX_FEATURES, reserved: int = 0) -> tuple[list, dict]:
    elements = osm.get("elements", [])
    nodes = {e["id"]: (e["lon"], e["lat"]) for e in elements if e.get("type") == "node" and "lat" in e and "lon" in e}
    osm_base = (osm.get("osm3s") or {}).get("timestamp_osm_base")
    stats = Counter()
    lit, unlit, unknown_tagged, srm, major = [], [], [], [], []
    for e in elements:
        tags = e.get("tags") or {}
        if e.get("type") != "way" or "highway" not in tags:
            continue
        stats["highway_ways"] += 1
        if not pedestrian_allowed(tags):
            stats["excluded_access_or_class"] += 1
            continue
        ids = e.get("nodes") or []
        coords = [nodes.get(n) for n in ids]
        if len(coords) < 2 or any(c is None or not in_bbox(c) for c in coords) or length_m(coords) == 0:
            stats["excluded_geometry_or_outside_bbox"] += 1
            continue
        kind = lighting_kind(tags)
        if kind == "night_lit":
            lit.append(road_feature(kind, [(e["id"], tags, coords)], osm_base))
        elif kind == "night_unlit":
            unlit.append(road_feature(kind, [(e["id"], tags, coords)], osm_base))
        elif "lit" in tags:  # e.g. lit=limited: tag present but not an explicit lit/unlit value
            unknown_tagged.append(road_feature(kind, [(e["id"], tags, coords)], osm_base, "lit_tag_non_explicit"))
        elif near_srm(coords) and tags["highway"] in LOCAL_HIGHWAYS | MAJOR_HIGHWAYS | {"trunk_link", "primary_link", "secondary_link"} \
                and tags.get("service") not in UNKNOWN_SKIP_SERVICE:
            srm.append((e["id"], tags, coords, ids))
        elif tags["highway"] in MAJOR_HIGHWAYS and tags.get("name") and "expressway" not in tags["name"].lower():
            major.append((e["id"], tags, coords, ids))
    feats = lit + unlit + unknown_tagged
    budget = max_features - reserved - len(feats)
    srm_feats = [road_feature("night_unknown", ch, osm_base, "srm_potheri_local")
                 for ch in _chains(srm)]
    srm_feats.sort(key=lambda f: (-f["properties"]["length_m"], f["id"]))
    srm_feats = srm_feats[:max(0, min(MAX_SRM_UNKNOWN, budget))]
    budget -= len(srm_feats)
    major_feats = [road_feature("night_unknown", ch, osm_base, "named_major_road") for ch in _chains(major)]
    major_feats = [f for f in major_feats if f["properties"]["length_m"] >= MAJOR_MIN_M]
    rank = {"trunk": 0, "primary": 1, "secondary": 2}
    major_feats.sort(key=lambda f: (rank[f["properties"]["highway"]], -f["properties"]["length_m"], f["id"]))
    stats["major_unknown_candidates"] = len(major_feats)
    major_feats = major_feats[:max(0, budget)]
    stats.update(lit=len(lit), unlit=len(unlit), unknown_lit_tag_other=len(unknown_tagged),
                 unknown_srm=len(srm_feats), unknown_major=len(major_feats))
    return feats + srm_feats + major_feats, dict(stats)

# ---------------------------------------------------------------- support POIs (real OSM objects only)
SUPPORT_LABEL = {"police": "Police station", "hospital": "Hospital", "pharmacy": "Pharmacy", "fuel": "Fuel station"}
OVERPASS_URL = "https://overpass-api.de/api/interpreter"
OVERPASS_QUERY = ('[out:json][timeout:25][bbox:12.65,79.95,13.3,80.35];'
                  'nwr["amenity"~"^(police|hospital|pharmacy|fuel)$"];out center tags;')

def fetch_support_pois(destination: Path = SUPPORT_POIS, attempts: int = 2) -> dict | None:
    """One bounded Overpass request (server timeout 25 s), retried at most once. Stores raw response + provenance."""
    log = []
    for i in range(min(attempts, 2)):
        started = time.time()
        try:
            req = urllib.request.Request(OVERPASS_URL, data=urllib.parse.urlencode({"data": OVERPASS_QUERY}).encode(),
                                         headers={"User-Agent": "ManzilResearch/1.0 (night context POI snapshot)"})
            with urllib.request.urlopen(req, timeout=35) as resp:
                raw = json.load(resp)
            log.append({"attempt": i + 1, "ok": True, "seconds": round(time.time() - started, 1)})
            doc = {"fetched_at": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"), "endpoint": OVERPASS_URL,
                   "query": OVERPASS_QUERY, "attempts": log, "licence": "OpenStreetMap contributors, ODbL 1.0", "response": raw}
            destination.write_text(json.dumps(doc, ensure_ascii=False), encoding="utf-8")
            return doc
        except Exception as exc:  # network / HTTP / JSON
            log.append({"attempt": i + 1, "ok": False, "error": repr(exc)[:200], "seconds": round(time.time() - started, 1)})
    return None

def support_features(doc: dict | None) -> tuple[list, dict]:
    if not doc:
        return [], {"support_source": "unavailable"}
    raw = doc.get("response") or {}
    stats = Counter()
    out, seen = [], set()
    for e in raw.get("elements", []):
        tags = e.get("tags") or {}
        kind = tags.get("amenity")
        if kind not in SUPPORT_KINDS:
            continue
        name = (tags.get("name") or tags.get("name:en") or "").strip()
        if not name:
            stats["skipped_unnamed"] += 1  # real but unnamed: skipped to keep the layer small and legible
            continue
        if e.get("type") == "node":
            c = [e.get("lon"), e.get("lat")]
        else:
            c = [(e.get("center") or {}).get("lon"), (e.get("center") or {}).get("lat")]
        if not in_bbox(c):
            stats["skipped_bad_or_outside_coordinates"] += 1
            continue
        fid = f"night_support_{kind}_{e['type'][0]}{e['id']}"
        if fid in seen:
            continue
        seen.add(fid)
        hours = tags.get("opening_hours")
        hours = hours.strip() if isinstance(hours, str) and hours.strip() else "unknown"
        detail = f"{SUPPORT_LABEL[kind]} mapped in OSM. Hours as mapped: {hours}. Open now, staffing and entrance access not verified."
        out.append({"type": "Feature", "id": fid, "geometry": {"type": "Point", "coordinates": _r(c)},
                    "properties": {"id": fid, "title": name, "layer": "night", "evidence": "mapped", "kind": "night_support",
                                   "support_kind": kind, "opening_hours": hours, "detail": detail,
                                   "source": f"OSM Overpass ({doc.get('fetched_at', '')[:10]}), ODbL",
                                   "observed_at": None, "expires_at": None, "osm_id": e["id"]}})
        stats["support_" + kind] += 1
    out.sort(key=lambda f: f["id"])
    stats["support_source"] = (raw.get("osm3s") or {}).get("timestamp_osm_base") or "unknown"
    return out, dict(stats)

# ---------------------------------------------------------------- All Women Police Stations (explicit source rows only)
AWPS_KML = DERIVED_DATA_DIR / "chennai_police_directory.kml"
AWPS_SOURCE_URL = "https://data.opencity.in/dataset/chennai-police-stations"
AWPS_SOURCE = f"Greater Chennai Police station directory (KML via OpenCity), {AWPS_SOURCE_URL}"
AWPS_STATION_RE = re.compile(r"^(?:W(\d{1,3})\s+)?AWPS$", re.IGNORECASE)
AWPS_PSCODE_RE = re.compile(r"^[A-Za-z0-9]{1,6}$")
_KML = "{http://www.opengis.net/kml/2.2}"

def awps_features(path: Path = AWPS_KML) -> tuple[list, dict]:
    """night_support/police features from placemarks whose STATION field is explicitly '<Wn> AWPS' or 'AWPS'.

    Nothing is inferred: names come only from the STATION / PSCODE fields of the same placemark, never from nearby
    ordinary stations or from the women-police CSV. Rows with a bad/duplicate station id, a STATION/AWPS code
    mismatch or unusable coordinates are skipped and counted. Returns ([], {"awps_source": "unavailable"}) if the
    file is missing or unparsable."""
    try:
        raw = path.read_bytes()
        if b"<!DOCTYPE" in raw or b"<!ENTITY" in raw:  # downloaded file is untrusted: no DTD/entity tricks
            return [], {"awps_source": "rejected_dtd"}
        root = ET.fromstring(raw)
    except (OSError, ET.ParseError):
        return [], {"awps_source": "unavailable"}
    stats: Counter = Counter()
    out, seen_ids, seen_codes = [], set(), set()
    for pm in root.iter(_KML + "Placemark"):
        stats["awps_placemarks_total"] += 1
        data = {s.get("name"): (s.text or "").strip() for s in pm.iter(_KML + "SimpleData")}
        m = AWPS_STATION_RE.match(data.get("STATION", ""))
        if not m:
            continue  # ordinary station (or no STATION text): never promoted to AWPS
        code = f"W{int(m.group(1))}" if m.group(1) else None
        field = data.get("AWPS", "")
        if code and field and field.upper() != code:
            stats["awps_skipped_code_mismatch"] += 1
            continue
        pscode = data.get("PSCODE", "")
        if not AWPS_PSCODE_RE.match(pscode):
            stats["awps_skipped_bad_station_id"] += 1
            continue
        fid = f"night_support_police_awps_{pscode.lower()}"
        if fid in seen_ids or (code and code in seen_codes):
            stats["awps_skipped_duplicate_id"] += 1
            continue
        coord_el = pm.find(f"{_KML}Point/{_KML}coordinates")
        try:
            parts = (coord_el.text or "").strip().split(",")
            c = [float(parts[0]), float(parts[1])]
        except (AttributeError, IndexError, ValueError):
            c = None
        if c is None or not in_bbox(c):
            stats["awps_skipped_bad_or_outside_coordinates"] += 1
            continue
        seen_ids.add(fid)
        if code:
            seen_codes.add(code)
        title = f"{code} All Women Police Station" if code else f"All Women Police Station (station code {pscode})"
        label = f"AWPS {code}, station code {pscode}" if code else f"AWPS, station code {pscode}"
        detail = (f"All Women Police Station ({label}) from the Greater Chennai Police station directory dataset. "
                  "Historical directory location: not live-checked, may have moved or closed. Hours unknown. "
                  "Open now, staffing and entrance access not verified.")
        out.append({"type": "Feature", "id": fid, "geometry": {"type": "Point", "coordinates": _r(c)},
                    "properties": {"id": fid, "title": title, "layer": "night", "evidence": "mapped", "kind": "night_support",
                                   "support_kind": "police", "opening_hours": "unknown", "detail": detail,
                                   "source": AWPS_SOURCE, "source_url": AWPS_SOURCE_URL, "observed_at": None,
                                   "expires_at": None, "awps_code": code, "police_station_code": pscode,
                                   "source_object_id": data.get("OBJECTID") or None}})
        stats["support_police_awps"] += 1
    out.sort(key=lambda f: f["id"])
    stats["awps_source"] = path.name
    return out, dict(stats)

# ---------------------------------------------------------------- build / load / integrate
def build_night_context(osm: dict, pois: dict | None, generated_at: str | None = None, max_features: int = MAX_FEATURES,
                        awps: tuple[list, dict] | None = None) -> dict:
    support, s_stats = support_features(pois)
    if awps:  # source-backed AWPS first so the feature cap never drops them
        support = awps[0] + support
        s_stats.update(awps[1])
    support = support[:max_features]
    roads, r_stats = road_features(osm, max_features=max_features, reserved=len(support))
    roads = roads[:max(0, max_features - len(support))]
    features = roads + support
    return {"type": "FeatureCollection", "metadata": {
        "schema_version": SCHEMA_VERSION, "layer": "night",
        "generated_at": generated_at or datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "osm_transport_base": (osm.get("osm3s") or {}).get("timestamp_osm_base"),
        "support_pois_fetched_at": (pois or {}).get("fetched_at"),
        "bbox_south_west_north_east": list(BBOX), "srm_center_lon_lat": list(SRM_CENTER), "srm_radius_m": SRM_RADIUS_M,
        "kind_counts": dict(Counter(f["properties"]["kind"] for f in features)),
        "support_counts": dict(Counter(f["properties"]["support_kind"] for f in support)),
        "awps_source_url": AWPS_SOURCE_URL if awps else None,
        "selection_stats": {**r_stats, **s_stats}, "lit_values": sorted(LIT_VALUES), "limitations": LIMITATIONS,
        "licence": "OpenStreetMap contributors, ODbL 1.0"}, "features": features}

_cache: dict[str, Any] = {"key": None, "value": None}

def valid_night_feature(f: Any) -> bool:
    try:
        p = f["properties"]
        if f["type"] != "Feature" or f["geometry"]["type"] != "Point" or not in_bbox(f["geometry"]["coordinates"]):
            return False
        if p["layer"] != "night" or p["evidence"] != "mapped" or p["kind"] not in KINDS or f["id"] != p["id"]:
            return False
        if p["observed_at"] is not None or p["expires_at"] is not None:
            return False
        rc = p.get("road_context")
        if p["kind"] == "night_support":
            return p.get("support_kind") in SUPPORT_KINDS and isinstance(p.get("opening_hours"), str) and rc is None
        return (rc["type"] == "LineString" and rc["approximate"] is True and len(rc["coordinates"]) >= 2
                and all(in_bbox(c) for c in rc["coordinates"]))
    except (KeyError, TypeError):
        return False

def load_night_context(path: Path = NIGHT_FILE) -> dict | None:
    """Cached (by mtime/size) load of the derived night file; None when missing or malformed."""
    try:
        st = path.stat()
        key = (str(path), st.st_mtime_ns, st.st_size)
        if _cache["key"] == key:
            return _cache["value"]
        doc = json.loads(path.read_text(encoding="utf-8"))
        if doc.get("type") != "FeatureCollection" or (doc.get("metadata") or {}).get("schema_version") != SCHEMA_VERSION:
            value = None
        else:
            feats = doc.get("features")
            ok = isinstance(feats, list) and all(valid_night_feature(f) for f in feats) and len({f["id"] for f in feats}) == len(feats)
            value = doc if ok else None
        _cache.update(key=key, value=value)
        return value
    except (OSError, ValueError):
        return None

NIGHT_NOTE = "Night features are mapped OSM lighting tags and support places, not current lamp status or a safety rating."

def add_night_context(feed: dict, night: dict | None) -> dict:
    """Pure: return a copy of feed with layer='night' features replaced by those in `night` (none if None)."""
    out = copy.deepcopy(feed)
    meta = out.setdefault("metadata", {})
    out["features"] = [f for f in out.get("features", []) if f["properties"].get("layer") != "night"]
    block: dict[str, Any] = {"status": "unavailable", "feature_count": 0, "limitations": LIMITATIONS}
    if night is not None:
        feats = [copy.deepcopy(f) for f in night.get("features", []) if valid_night_feature(f)]
        taken = {f["id"] for f in out["features"]}
        feats = [f for f in feats if f["id"] not in taken]
        out["features"].extend(feats)
        nm = night.get("metadata") or {}
        block.update(status="available", feature_count=len(feats), kind_counts=dict(Counter(f["properties"]["kind"] for f in feats)),
                     generated_at=nm.get("generated_at"), osm_transport_base=nm.get("osm_transport_base"),
                     support_pois_fetched_at=nm.get("support_pois_fetched_at"))
    else:
        block["reason"] = "night_context_missing_or_malformed"
    meta["night_context"] = block
    limits = meta.setdefault("limitations", [])
    if NIGHT_NOTE not in limits:
        limits.append(NIGHT_NOTE)
    meta["counts"] = dict(Counter(f["properties"]["layer"] for f in out["features"]))
    return out

def write_geojson(doc: dict, destination: Path) -> None:
    import os, tempfile
    destination.parent.mkdir(parents=True, exist_ok=True)
    name = None
    try:
        with tempfile.NamedTemporaryFile("w", encoding="utf-8", dir=destination.parent, delete=False) as f:
            name = f.name
            json.dump(doc, f, ensure_ascii=False, separators=(",", ":"), allow_nan=False)
        os.replace(name, destination)
    finally:
        if name and os.path.exists(name):
            os.unlink(name)

if __name__ == "__main__":  # python -m app.night_context [--fetch]
    import sys
    pois = fetch_support_pois() if "--fetch" in sys.argv else None
    if pois is None and SUPPORT_POIS.is_file():
        pois = json.loads(SUPPORT_POIS.read_text(encoding="utf-8"))
    osm = json.loads(OSM_TRANSPORT.read_text(encoding="utf-8"))
    night = build_night_context(osm, pois, awps=awps_features())
    write_geojson(night, NIGHT_FILE)
    from app.map_overlays import build_overlays, save_snapshot
    from app.modelled_risk import add_modelled_risk, load_weather
    now = datetime.now(timezone.utc)
    combined = add_night_context(add_modelled_risk(build_overlays(now=now), load_weather(), now), load_night_context())
    save_snapshot(combined)
    print(json.dumps({"night": night["metadata"]["kind_counts"], "support": night["metadata"]["support_counts"],
                      "stats": night["metadata"]["selection_stats"], "night_bytes": NIGHT_FILE.stat().st_size,
                      "combined_counts": combined["metadata"]["counts"]}, indent=1))
