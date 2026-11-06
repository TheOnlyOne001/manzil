"""Bi-directional Crowdsourced Hazard Reporting Service.

Allows commuters to report live waterlogging, stalled vehicles, fallen trees,
and snapped wires. Automatically applies a 2-hour TTL freshness decay
so stale puddle reports naturally expire.
"""

import time
import uuid
import json
import logging
from typing import List, Dict, Any, Optional
from pydantic import BaseModel, Field
from app.config import DERIVED_DATA_DIR

logger = logging.getLogger(__name__)

CROWD_STORAGE_PATH = DERIVED_DATA_DIR / "crowdsourced_reports.json"

class HazardReportSubmission(BaseModel):
    latitude: float = Field(..., ge=12.5, le=13.5, description="Latitude in Chennai bounds")
    longitude: float = Field(..., ge=79.8, le=80.5, description="Longitude in Chennai bounds")
    depth_level: str = Field(
        "KNEE_DEEP",
        description="Puddle depth: ANKLE_DEEP (~10cm), KNEE_DEEP (~30cm), WAIST_DEEP (~60cm), IMPASSABLE (>90cm)"
    )
    hazard_type: str = Field(
        "WATERLOGGING",
        description="Hazard type: WATERLOGGING, STALLED_VEHICLE, FALLEN_TREE, LIVE_WIRE"
    )
    description: Optional[str] = Field(None, max_length=250)
    device_id: Optional[str] = Field(None, max_length=64)

class CrowdsourceHazardService:
    def __init__(self, ttl_seconds: float = 7200.0):
        self.ttl_seconds = ttl_seconds
        self._reports: Dict[str, Dict[str, Any]] = {}
        self._load_from_disk()

    def _load_from_disk(self) -> None:
        """Load stored reports on startup and prune expired ones."""
        if not CROWD_STORAGE_PATH.exists():
            return
        try:
            with open(CROWD_STORAGE_PATH, "r", encoding="utf-8") as f:
                data = json.load(f)
                now = time.time()
                for r in data:
                    if (now - r.get("created_epoch", 0)) < self.ttl_seconds:
                        self._reports[r["id"]] = r
            logger.info(f"Loaded {len(self._reports)} active crowdsourced reports from disk.")
        except Exception as e:
            logger.error(f"Error loading crowdsourced reports: {e}")

    def _save_to_disk(self) -> None:
        """Persist active reports to derived folder."""
        try:
            DERIVED_DATA_DIR.mkdir(parents=True, exist_ok=True)
            active = self.get_active_reports()
            with open(CROWD_STORAGE_PATH, "w", encoding="utf-8") as f:
                json.dump(active, f, indent=2)
        except Exception as e:
            logger.error(f"Error persisting crowdsourced reports: {e}")

    def submit_report(self, sub: HazardReportSubmission) -> Dict[str, Any]:
        """Validate, score, and store a citizen hazard report."""
        now_epoch = time.time()
        report_id = f"crowd_{uuid.uuid4().hex[:8]}"
        
        # Calculate depth, passability, severity, and color
        depth_upper = sub.depth_level.upper()
        if depth_upper == "IMPASSABLE" or depth_upper == "WAIST_DEEP":
            depth_cm = 65 if depth_upper == "WAIST_DEEP" else 95
            severity = "CRITICAL"
            color = "#DC2626"  # Red
            passable_ped = False
            passable_veh = False
        elif depth_upper == "KNEE_DEEP":
            depth_cm = 32
            severity = "HIGH"
            color = "#EA580C"  # Dark Orange
            passable_ped = False
            passable_veh = True
        else:  # ANKLE_DEEP
            depth_cm = 12
            severity = "MODERATE"
            color = "#F59E0B"  # Amber
            passable_ped = True
            passable_veh = True

        report_obj = {
            "id": report_id,
            "created_epoch": now_epoch,
            "coordinates": [round(sub.longitude, 6), round(sub.latitude, 6)],
            "depth_level": sub.depth_level,
            "hazard_type": sub.hazard_type,
            "water_depth_estimate_cm": depth_cm,
            "severity": severity,
            "color": color,
            "is_passable_pedestrian": passable_ped,
            "is_passable_vehicle": passable_veh,
            "description": sub.description or f"Citizen reported {sub.depth_level.lower().replace('_', ' ')} hazard",
            "device_id": sub.device_id or "anonymous_commuter",
            "ttl_seconds": self.ttl_seconds
        }

        self._reports[report_id] = report_obj
        self._save_to_disk()
        logger.info(f"Accepted crowd report {report_id} at [{sub.latitude}, {sub.longitude}] ({sub.depth_level})")
        return report_obj

    def get_active_reports(self) -> List[Dict[str, Any]]:
        """Return all reports that have not exceeded the 2-hour TTL."""
        now = time.time()
        valid = []
        for r_id, r in list(self._reports.items()):
            if (now - r["created_epoch"]) < self.ttl_seconds:
                valid.append(r)
            else:
                # Prune expired report
                del self._reports[r_id]
        return valid

    def to_geojson_features(self) -> List[Dict[str, Any]]:
        """Convert all active citizen reports into GeoJSON Feature objects."""
        active = self.get_active_reports()
        now = time.time()
        features = []
        for r in active:
            age_min = round((now - r["created_epoch"]) / 60.0, 1)
            feat = {
                "type": "Feature",
                "geometry": {
                    "type": "Point",
                    "coordinates": r["coordinates"]
                },
                "properties": {
                    "id": r["id"],
                    "name_en": f"Live Citizen Report: {r['hazard_type']}",
                    "name_ta": f"நேரடி பயனர் தகவல்: {r['hazard_type']}",
                    "category": "CROWDSOURCED_HAZARD_REPORT",
                    "hazard_type": r["hazard_type"],
                    "depth_level": r["depth_level"],
                    "water_depth_estimate_cm": r["water_depth_estimate_cm"],
                    "severity": r["severity"],
                    "color": r["color"],
                    "is_passable_pedestrian": r["is_passable_pedestrian"],
                    "is_passable_vehicle": r["is_passable_vehicle"],
                    "field_remarks": r["description"],
                    "reported_age_minutes": age_min,
                    "recommended_action": f"CAUTION: Live report {age_min}m ago. {r['description']}",
                    "source": "MANZIL_COMMUTER_CROWD_REPORTS"
                }
            }
            features.append(feat)
        return features
