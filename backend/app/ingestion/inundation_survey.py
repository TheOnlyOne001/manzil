"""Parser for 192 Surveyed Road Inundation Points with Measured Depths.

Data source: Greater Chennai Corporation Flood Inundation Survey via OpenCity.
Each point contains empirical flood depth measurements in inches/centimeters
and field condition remarks.
"""

import xml.etree.ElementTree as ET
import logging
from pathlib import Path
from typing import List, Dict, Any
from app.config import DATA_DIR

logger = logging.getLogger(__name__)

KML_PATH = DATA_DIR / "reference-maps" / "chennai-inundation-points-depth.kml"

def load_surveyed_inundation_points() -> List[Dict[str, Any]]:
    """Parse surveyed road inundation depth points into GeoJSON features."""
    if not KML_PATH.exists():
        logger.warning(f"Surveyed inundation KML not found at {KML_PATH}")
        return []

    try:
        root = ET.fromstring(KML_PATH.read_text(encoding="utf-8"))
        ns = {"kml": "http://www.opengis.net/kml/2.2"}
        placemarks = root.findall(".//kml:Placemark", ns)

        features = []
        for idx, pm in enumerate(placemarks):
            sd = pm.findall(".//kml:SimpleData", ns)
            props = {d.attrib.get("name"): d.text for d in sd}

            lat_str = props.get("F_LATITUDE")
            lng_str = props.get("F_LONGITUDE")
            depth_str = props.get("DEPTH")
            remarks = (props.get("F_REMARKS") or "Surveyed inundation location").strip()
            fid = props.get("F_ID", str(idx + 1))

            if not lat_str or not lng_str or not depth_str:
                continue

            try:
                lat = float(lat_str)
                lng = float(lng_str)
                depth_in = float(depth_str)
                depth_cm = round(depth_in * 2.54, 1)

                # Classify severity and passability
                if depth_cm >= 50.0:
                    severity = "CRITICAL"
                    color = "#DC2626"  # Red
                    passable_ped = False
                    passable_veh = False
                elif depth_cm >= 20.0:
                    severity = "HIGH"
                    color = "#EA580C"  # Dark Orange
                    passable_ped = False
                    passable_veh = True
                elif depth_cm >= 10.0:
                    severity = "MODERATE"
                    color = "#F59E0B"  # Amber
                    passable_ped = True
                    passable_veh = True
                else:
                    severity = "LOW"
                    color = "#EAB308"  # Yellow
                    passable_ped = True
                    passable_veh = True

                feat = {
                    "type": "Feature",
                    "geometry": {
                        "type": "Point",
                        "coordinates": [lng, lat]
                    },
                    "properties": {
                        "id": f"survey_inundation_{fid}",
                        "name_en": f"Surveyed Waterlogging Point #{fid}",
                        "name_ta": f"கள ஆய்வு வெள்ளப் புள்ளி #{fid}",
                        "category": "SURVEYED_ROAD_INUNDATION",
                        "severity": severity,
                        "color": color,
                        "water_depth_inches": depth_in,
                        "water_depth_estimate_cm": depth_cm,
                        "field_remarks": remarks,
                        "is_passable_pedestrian": passable_ped,
                        "is_passable_vehicle": passable_veh,
                        "source": "GCC_INUNDATION_FIELD_SURVEY"
                    }
                }
                features.append(feat)
            except ValueError:
                continue

        logger.info(f"Loaded {len(features)} surveyed road inundation depth points.")
        return features

    except Exception as e:
        logger.error(f"Error parsing surveyed inundation points: {e}")
        return []
