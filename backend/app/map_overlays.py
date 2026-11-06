"""Evidence-labelled map context. Never derive street flooding from weather."""
from __future__ import annotations
import csv
import io
import json
import math
import re
import zipfile
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from app.config import DATA_DIR, DERIVED_DATA_DIR
from app.ingestion.inundation_survey import load_surveyed_inundation_points
from app.ingestion.hotspots_loader import load_gcc_hotspots
from app.ingestion.flyovers_data import CHENNAI_ELEVATED_FLYOVERS
from app.ingestion.flood2015 import SOURCE_URL as FLOOD2015_SOURCE_URL, load_flood2015_points

SNAPSHOT = DERIVED_DATA_DIR / "chennai_map_overlays.geojson"
ROAD_CONTEXT = DERIVED_DATA_DIR / "chennai_historical_road_context.json"

def stamp(epoch: float) -> str:
    return datetime.fromtimestamp(epoch, timezone.utc).isoformat().replace("+00:00", "Z")

def point(coords: Any) -> bool:
    return (isinstance(coords, (list, tuple)) and len(coords) >= 2
            and all(isinstance(x, (int, float)) and math.isfinite(x) for x in coords[:2])
            and 79.8 <= coords[0] <= 80.5 and 12.5 <= coords[1] <= 13.5)

def feature(identifier, title, coords, layer, evidence, detail, source, observed=None, expires=None, **extra):
    if not point(coords):
        return None
    return {"type": "Feature", "id": str(identifier), "geometry": {"type": "Point", "coordinates": list(coords[:2])},
            "properties": {"id": str(identifier), "title": str(title), "layer": layer, "evidence": evidence,
                           "detail": detail, "source": source, "observed_at": observed, "expires_at": expires, **extra}}

INCHES_TO_CM = 2.54
# Schema "chennai_flood_inundation_inches" in the source KML declares DEPTH in inches.
SURVEY_DEPTH_SOURCE = ("GCC inundation survey DEPTH field (KML schema chennai_flood_inundation_inches, inches), "
                       "converted to cm at 2.54 cm/in")

def historical_depth_cm(raw_inches: Any) -> float | None:
    """Convert the survey's historical DEPTH (inches) to cm; None if missing, non-finite or negative."""
    if isinstance(raw_inches, bool):
        return None
    try:
        inches = float(raw_inches)
    except (TypeError, ValueError, OverflowError):
        return None
    if not math.isfinite(inches) or inches < 0:
        return None
    return round(inches * INCHES_TO_CM, 1)

def survey_point_feature(identifier: str, coords: Any, props: dict):
    depth = historical_depth_cm(props.get("water_depth_inches"))
    if depth is None:
        depth_text = "Historical surveyed depth unavailable or invalid."
    else:
        depth_text = f"Historical surveyed depth: {depth:g} cm at an unknown survey date (not current depth)."
    return feature(identifier, "Historical inundation survey point", coords, "history", "historical",
        "Historical GCC survey location; survey date not provided. This is not a current flood observation. "
        + depth_text + " Field remark: " + str(props.get("field_remarks") or "Not provided"),
        "GCC inundation survey via OpenCity; chennai-inundation-points-depth.kml", kind="survey_point",
        historical_depth_cm=depth, historical_depth_unit="cm",
        historical_depth_source=SURVEY_DEPTH_SOURCE if depth is not None else None)

def active_reports(records: list[dict], now: float) -> list[dict]:
    result = []
    for r in records:
        # Old development records must never become commuter observations.
        provenance = " ".join(str(r.get(k, "")) for k in ("id", "device_id", "description", "source"))
        if r.get("is_test") or r.get("simulated") or re.search(r"(?i)(?:^|[^a-z])(test|demo|mock|synthetic|simulated)(?:[^a-z]|$)", provenance):
            continue
        try:
            created = float(r["created_epoch"])
            ttl = min(float(r.get("ttl_seconds", 7200)), 7200)
            if not all(math.isfinite(x) for x in (created, ttl)) or ttl <= 0 or not created <= now < created + ttl:
                continue
            f = feature(r["id"], "Commuter report: " + str(r.get("hazard_type", "hazard")).replace("_", " ").lower(),
                        r.get("coordinates"), "reports", "reported",
                        "Unverified commuter report. " + str(r.get("description") or "No description supplied.") + " Current passability is unknown.",
                        "Manzil commuter submission (unverified)", stamp(created), stamp(created + ttl), kind="commuter_report")
            if f:
                result.append(f)
        except (KeyError, ValueError, TypeError, OverflowError):
            continue
    return result

def build_overlays(now: datetime | None = None, report_records: list[dict] | None = None) -> dict:
    now = now or datetime.now(timezone.utc)
    if now.tzinfo is None:
        raise ValueError("Timezone-aware now required")
    features = []
    for survey_index, f in enumerate(load_surveyed_inundation_points(), start=1):
        p = f["properties"]
        # Only the raw historical inches are read; legacy severity/passability/estimate fields are not exposed.
        features.append(survey_point_feature(p["id"]+"_record_"+str(survey_index), f["geometry"]["coordinates"], p))
    for f in load_gcc_hotspots():
        p = f["properties"]
        features.append(feature("hotspot_"+str(p["id"]), "Historical flood hotspot (2020)", f["geometry"]["coordinates"], "history", "historical",
            "GCC historical flood hotspot dataset, 2020. Current water level and passability are unknown.",
            "GCC/OpenCity dataset " + p.get("source_dataset", "gcc-flood-hotspots-2020"), kind="hotspot", source_year=2020))
    # Actual 2015 flood locations (real KML Points only). Skip any within 30 m of a history point already present.
    existing = [f["geometry"]["coordinates"] for f in features if f is not None and f["properties"]["layer"] == "history"]
    flood2015, _ = load_flood2015_points(exclude_near=existing)
    for p in flood2015:
        features.append(feature(p["id"], "Actual 2015 flood location: " + p["name"], p["coords"], "history", "historical",
            "Location recorded as flooded or water-stagnated in the actual December 2015 Chennai floods (" + p["dataset_label"]
            + "). Depth unknown: this dataset gives no measured water depth. Not a current flood observation; current water level and passability are unknown.",
            "Actual 2015 flood record, OpenCity 'Chennai Floods 2015 Data' (" + p["dataset_label"] + "), " + FLOOD2015_SOURCE_URL,
            kind="hotspot", source_year=2015))
    for p in CHENNAI_ELEVATED_FLYOVERS:
        features.append(feature(p["id"], p["name_en"], p["coordinates"], "infrastructure", "curated",
            "Approximate curated flyover location. Pedestrian access, closure status and flood conditions are unverified; not a recommended detour.",
            "Manzil manually curated flyover list (not field verified)", kind="flyover", location_accuracy="approximate"))
    gtfs = DATA_DIR / "transit" / "cmrl-gtfs.zip"
    seen = set()
    if gtfs.is_file():
        with zipfile.ZipFile(gtfs) as z:
            rows = csv.DictReader(io.StringIO(z.read("stops.txt").decode("utf-8-sig")))
            for p in rows:
                name = p.get("stop_name", "").strip()
                if not name or name.casefold() in seen:
                    continue
                try:
                    coords = [float(p["stop_lon"]), float(p["stop_lat"])]
                except (ValueError, KeyError):
                    continue
                f = feature("metro_"+p["stop_id"], name, coords, "infrastructure", "curated",
                    "CMRL static GTFS station location. Service operation, accessible entrance and flood conditions are not verified. Not a shelter or flood-safe crossing.",
                    "CMRL static GTFS; cmrl-gtfs.zip/stops.txt", kind="metro_station")
                if f:
                    features.append(f)
                    seen.add(name.casefold())
    if report_records is None:
        path = DERIVED_DATA_DIR / "crowdsourced_reports.json"
        try:
            report_records = json.loads(path.read_text(encoding="utf-8")) if path.is_file() else []
        except (OSError, ValueError):
            report_records = []
    features.extend(active_reports(report_records, now.timestamp()))
    features = [f for f in features if f is not None]
    if ROAD_CONTEXT.is_file():
        associations = json.loads(ROAD_CONTEXT.read_text(encoding="utf-8"))
        for f in features:
            if f["properties"]["layer"] == "history" and f["id"] in associations:
                f["properties"]["road_context"] = associations[f["id"]]
    return {"type": "FeatureCollection", "metadata": {
        "schema_version": 1, "generated_at": now.isoformat(), "city": "Chennai",
        "counts": dict(Counter(f["properties"]["layer"] for f in features)),
        "current_flood_observations_verified": False,
        "limitations": ["History points are not current flood extents.", "Infrastructure points are not safety recommendations.",
                        "Weather is omitted: rainfall does not establish street water depth.", "Expired reports must also be hidden by offline clients."]}, "features": features}

def save_snapshot(feed: dict, destination: Path = SNAPSHOT):
    destination.parent.mkdir(parents=True, exist_ok=True)
    # Atomic replacement preserves the previous usable snapshot if serialization fails.
    import tempfile
    import os
    name = None
    try:
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=destination.parent, delete=False) as f:
            name = f.name
            json.dump(feed, f, ensure_ascii=False, separators=(",", ":"), allow_nan=False)
        os.replace(name, destination)
    finally:
        if name and os.path.exists(name):
            os.unlink(name)
