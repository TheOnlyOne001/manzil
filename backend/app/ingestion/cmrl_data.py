"""Chennai Metro Rail (CMRL) Stations as Flood Sanctuaries and Dry Mobility Hops.

Source: Official CMRL GTFS specification.
In extreme monsoons, Chennai Metro stations function as:
1. Flood-immune mass transit connecting north, central, and south Chennai.
2. Elevated dry pedestrian concourses allowing pedestrians to cross flooded 6-lane
   arterial corridors (Anna Salai, GST Road, Poonamallee High Road) safely.
"""

import zipfile
import csv
import io
import logging
from typing import List, Dict, Any
from app.config import DATA_DIR

logger = logging.getLogger(__name__)

CMRL_GTFS_ZIP = DATA_DIR / "transit" / "cmrl-gtfs.zip"

def load_cmrl_stations() -> List[Dict[str, Any]]:
    """Parse CMRL GTFS stops into Metro Flood Haven features."""
    if not CMRL_GTFS_ZIP.exists():
        logger.warning(f"CMRL GTFS zip not found at {CMRL_GTFS_ZIP}")
        return []

    try:
        with zipfile.ZipFile(CMRL_GTFS_ZIP, "r") as z:
            if "stops.txt" not in z.namelist():
                logger.warning("stops.txt missing from cmrl-gtfs.zip")
                return []

            content = z.read("stops.txt").decode("utf-8")
            reader = csv.DictReader(io.StringIO(content))

            # Deduplicate by canonical station name
            unique_stations: Dict[str, Dict[str, Any]] = {}

            for s in reader:
                raw_name = s.get("stop_name", "").strip()
                if not raw_name:
                    continue

                clean_name = raw_name.title()
                # Normalize Central station title
                if "Puratchi Thalaivar" in clean_name or "M.G.Ramachandran" in clean_name:
                    clean_name = "Puratchi Thalaivar Dr. M.G. Ramachandran Central Metro"

                if clean_name not in unique_stations:
                    try:
                        lat = float(s["stop_lat"])
                        lng = float(s["stop_lon"])
                        unique_stations[clean_name] = {
                            "name_en": clean_name,
                            "stop_id": s.get("stop_id", ""),
                            "lat": lat,
                            "lng": lng
                        }
                    except (ValueError, KeyError):
                        continue

            features = []
            for idx, (name, st) in enumerate(unique_stations.items(), start=1):
                # Classify line and type
                is_interchange = "Central" in name or "Alandur" in name or "Egmore" in name
                
                feat = {
                    "type": "Feature",
                    "geometry": {
                        "type": "Point",
                        "coordinates": [st["lng"], st["lat"]]
                    },
                    "properties": {
                        "id": f"cmrl_station_{idx}",
                        "name_en": st["name_en"],
                        "name_ta": f"{st['name_en']} மெட்ரோ நிலையம்",
                        "category": "METRO_SANCTUARY_STATION",
                        "operator": "Chennai Metro Rail Limited (CMRL)",
                        "severity": "OPERATIONAL_TRANSIT_HAVEN",
                        "color": "#8B5CF6",  # Purple (Metro sanctuary)
                        "is_flood_immune": True,
                        "is_passable_pedestrian": True,
                        "has_elevated_dry_crossing": True,
                        "is_interchange": is_interchange,
                        "recommended_action": "RECOMMENDED: Use station concourse for dry road crossing and resilient mass transit",
                        "source": "OFFICIAL_CMRL_GTFS"
                    }
                }
                features.append(feat)

            logger.info(f"Loaded {len(features)} CMRL flood sanctuary stations.")
            return features

    except Exception as e:
        logger.error(f"Error parsing CMRL GTFS stations: {e}")
        return []
