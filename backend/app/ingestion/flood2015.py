"""Actual 2015 Chennai flood locations from the OpenCity "Chennai Floods 2015 Data" CKAN package.

Only real KML <Point> coordinates are used (never polygon centroids). The source does not give a
measured depth for these locations, so none is exposed. Depth stays unknown.

    python -m app.ingestion.flood2015 --fetch   # re-download raw KMLs via CKAN package_show
"""
from __future__ import annotations
import json
import math
import re
import sys
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any, Callable

from app.config import DATA_DIR

SOURCE_URL = "https://data.opencity.in/dataset/chennai-floods-2015-data"
CKAN_PACKAGE_SHOW = "https://data.opencity.in/api/3/action/package_show?id=chennai-floods-2015-data"
RAW_DIR = DATA_DIR / "flood-2015-raw"
DEDUP_M = 30.0
M_PER_DEG_LON = 108450.0   # ~13 N, same constants as app.road_context / night_context
M_PER_DEG_LAT = 111195.0
KML_NS = {"k": "http://www.opengis.net/kml/2.2"}


def _clean(value: Any) -> str:
    return re.sub(r"\s+", " ", str(value or "")).strip(" ,;-")


def _name_gcc_stagnation(pm_name: str, d: dict) -> str:
    return _clean(pm_name)


def _name_gcc_area(pm_name: str, d: dict) -> str:
    return _clean(d.get("location"))


def _name_kancheepuram(pm_name: str, d: dict) -> str:
    return ", ".join(x for x in (_clean(d.get("details")), _clean(d.get("taluka"))) if x)


def _name_tiruvallur(pm_name: str, d: dict) -> str:
    return ", ".join(x for x in (_clean(d.get("details")) or _clean(pm_name), _clean(d.get("name_municipality"))) if x)


# slug -> (CKAN resource name, raw file name, human label, name extractor)
DATASETS: dict[str, tuple[str, str, str, Callable[[str, dict], str]]] = {
    "gcc-stagnation": ("GCC Stagnation Locations in 2015", "gcc_stagnation_locations_2015.kml",
                       "GCC water stagnation locations", _name_gcc_stagnation),
    "gcc-hotspots": ("Chennai 2015 GCC Area Flood Hotspots", "gcc_area_flood_hotspots_2015.kml",
                     "GCC area flood hotspots", _name_gcc_area),
    "kancheepuram": ("Chennai 2015 Kancheepuram District Flood Hotspots", "kancheepuram_flood_hotspots_2015.kml",
                     "Kancheepuram district flood hotspots", _name_kancheepuram),
    "tiruvallur": ("Chennai 2015 Tiruvallur District Flood Hotspots", "tiruvallur_flood_hotspots_2015.kml",
                   "Tiruvallur district flood hotspots", _name_tiruvallur),
}


def _in_coverage(c: Any) -> bool:
    # Same geographic coverage as app.map_overlays.point (imported lazily: map_overlays imports this module).
    from app.map_overlays import point
    return point(c)


def meters_between(a, b) -> float:
    return math.hypot((a[0] - b[0]) * M_PER_DEG_LON, (a[1] - b[1]) * M_PER_DEG_LAT)


class NearIndex:
    """Grid index answering 'is any stored coordinate within radius_m'."""
    def __init__(self, radius_m: float = DEDUP_M, coords=()):
        self.radius = radius_m
        self.cell_lon = radius_m / M_PER_DEG_LON
        self.cell_lat = radius_m / M_PER_DEG_LAT
        self.cells: dict[tuple[int, int], list] = {}
        for c in coords:
            self.add(c)

    def _key(self, c):
        return (math.floor(c[0] / self.cell_lon), math.floor(c[1] / self.cell_lat))

    def add(self, c) -> None:
        self.cells.setdefault(self._key(c), []).append((c[0], c[1]))

    def near(self, c) -> bool:
        kx, ky = self._key(c)
        return any(meters_between(c, o) <= self.radius
                   for dx in (-1, 0, 1) for dy in (-1, 0, 1) for o in self.cells.get((kx + dx, ky + dy), ()))


def parse_kml_points(text: str) -> list[dict]:
    """Return one record per Placemark that has a real <Point>; polygons/lines/other geometry are ignored."""
    out = []
    root = ET.fromstring(text)
    for ordinal, pm in enumerate(root.findall(".//k:Placemark", KML_NS), start=1):
        coord = pm.find("./k:Point/k:coordinates", KML_NS)
        if coord is None or not coord.text:
            continue
        try:
            nums = [float(x) for x in coord.text.strip().split(",")[:2]]
        except ValueError:
            continue
        if len(nums) != 2 or not all(math.isfinite(x) for x in nums):
            continue
        name_el = pm.find("k:name", KML_NS)
        data = {(s.get("name") or "").lower(): s.text for s in pm.findall(".//k:SimpleData", KML_NS)}
        out.append({"ordinal": ordinal, "coords": nums, "pm_name": name_el.text if name_el is not None else "", "data": data})
    return out


def load_flood2015_points(raw_dir: Path = RAW_DIR, exclude_near=None, dedup_m: float = DEDUP_M) -> tuple[list[dict], dict]:
    """Load, clip to map coverage and dedup the 2015 points.

    exclude_near: coordinates of already-present features; any 2015 point within dedup_m of one is dropped.
    Datasets are processed in DATASETS order, so the first dataset keeps the point when two 2015 sources coincide.
    """
    index = NearIndex(dedup_m)                              # 2015 points kept so far
    existing_index = NearIndex(dedup_m, exclude_near or ())  # features already on the map
    points: list[dict] = []
    stats = {"raw": 0, "outside_coverage": 0, "duplicate_2015": 0, "duplicate_existing": 0, "kept": 0, "by_dataset": {}}
    for slug, (_, filename, label, namer) in DATASETS.items():
        path = raw_dir / filename
        kept = 0
        if not path.is_file():
            stats["by_dataset"][slug] = 0
            continue
        for rec in parse_kml_points(path.read_text(encoding="utf-8")):
            stats["raw"] += 1
            c = rec["coords"]
            if not _in_coverage(c):
                stats["outside_coverage"] += 1
                continue
            if existing_index.near(c):
                stats["duplicate_existing"] += 1
                continue
            if index.near(c):
                stats["duplicate_2015"] += 1
                continue
            index.add(c)
            kept += 1
            points.append({"id": f"flood2015-{slug}-{rec['ordinal']}", "coords": c, "dataset": slug, "dataset_label": label,
                           "name": namer(rec["pm_name"], rec["data"]) or "Unnamed location"})
        stats["by_dataset"][slug] = kept
    stats["kept"] = len(points)
    return points, stats


def fetch_raw(raw_dir: Path = RAW_DIR) -> list[str]:
    """Download the selected KMLs using CKAN package_show (https://data.opencity.in only)."""
    def get(url: str) -> bytes:
        if urllib.parse.urlparse(url).netloc != "data.opencity.in":
            raise ValueError("unexpected host: " + url)
        with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "manzil-research"}), timeout=90) as r:
            return r.read()
    package = json.loads(get(CKAN_PACKAGE_SHOW))
    if not package.get("success"):
        raise RuntimeError("CKAN package_show failed")
    by_name = {r["name"].strip(): r["url"] for r in package["result"]["resources"]}
    raw_dir.mkdir(parents=True, exist_ok=True)
    (raw_dir / "package_show.json").write_bytes(json.dumps(package, ensure_ascii=False, indent=1).encode("utf-8"))
    saved = []
    for resource_name, filename, _, _ in DATASETS.values():
        (raw_dir / filename).write_bytes(get(by_name[resource_name]))
        saved.append(filename)
    return saved


if __name__ == "__main__":
    if "--fetch" in sys.argv:
        print(fetch_raw())
    pts, st = load_flood2015_points()
    print(json.dumps(st))
