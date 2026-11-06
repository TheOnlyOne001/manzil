"""Synthesizes real-time weather telemetry, sensor data, and infrastructure datasets into a unified hazard GeoJSON."""

import datetime
import logging
from typing import Dict, Any, List, Optional
from app.ingestion.imd_client import IMDClient
from app.ingestion.openmeteo_client import OpenMeteoClient
from app.ingestion.hotspots_loader import load_gcc_hotspots
from app.ingestion.subways_data import CHENNAI_CRITICAL_SUBWAYS
from app.ingestion.inundation_survey import load_surveyed_inundation_points
from app.ingestion.flyovers_data import load_elevated_flyovers
from app.ingestion.waterways_data import load_waterway_corridors
from app.ingestion.cmrl_data import load_cmrl_stations
from app.ingestion.relief_centres_data import load_gcc_relief_centres
from app.ingestion.crowdsource_service import CrowdsourceHazardService

logger = logging.getLogger(__name__)

class HazardSynthesizer:
    def __init__(self, crowdsource_service: Optional[CrowdsourceHazardService] = None):
        self.imd_client = IMDClient()
        self.openmeteo_client = OpenMeteoClient()
        self.crowdsource_service = crowdsource_service or CrowdsourceHazardService()
        
        # Load infrastructure & reference layers
        self.subways = CHENNAI_CRITICAL_SUBWAYS
        self.gcc_hotspots = load_gcc_hotspots()
        self.surveyed_inundation = load_surveyed_inundation_points()
        self.flyovers = load_elevated_flyovers()
        self.waterways = load_waterway_corridors()
        self.cmrl_stations = load_cmrl_stations()
        self.relief_centres = load_gcc_relief_centres()

    async def generate_unified_hazard_feed(self) -> Dict[str, Any]:
        """Fetch live feeds and synthesize into a single normalized GeoJSON FeatureCollection."""
        now_utc = datetime.datetime.now(datetime.timezone.utc).isoformat()

        # 1. Fetch live telemetry
        district_features = await self.imd_client.fetch_district_nowcast(state="TAMILNADU")
        station_features = await self.imd_client.fetch_station_nowcast()
        openmeteo_data = await self.openmeteo_client.fetch_current_precipitation()

        # 2. Extract Chennai macro weather parameters
        chennai_dist = next(
            (f["properties"] for f in district_features if str(f.get("properties", {}).get("District", "")).upper() == "CHENNAI"),
            None
        )
        imd_color_code = chennai_dist.get("Color", 1) if chennai_dist else 1
        imd_toi = chennai_dist.get("toi", "N/A") if chennai_dist else "N/A"
        imd_vupto = chennai_dist.get("vupto", "N/A") if chennai_dist else "N/A"

        # Rainfall rate from Open-Meteo
        current_precip = 0.0
        if openmeteo_data and "current" in openmeteo_data:
            current_precip = float(openmeteo_data["current"].get("precipitation", 0.0))

        # Check IMD station moderate/heavy rain flags
        has_imd_rain = any(
            f.get("properties", {}).get("cat7", 0) > 0 or f.get("properties", {}).get("cat2", 0) > 0
            for f in station_features
        )

        logger.info(
            f"Synthesizing Chennai hazards: IMD Warning Color={imd_color_code}, "
            f"Current Precip={current_precip}mm/hr, IMD Rain Flag={has_imd_rain}"
        )

        output_features: List[Dict[str, Any]] = []

        # 3. Process Critical Subways & Underpasses (High Priority Choke Points)
        for s in self.subways:
            if imd_color_code >= 3 or current_precip > 30.0:
                severity = "CRITICAL"
                depth_cm = s["flood_depth_threshold_cm"] + 15
                passable_ped = False
                passable_veh = False
                color = "#DC2626"  # Red
                action = f"AVOID: Submerged. {s['alternative_route']}"
            elif imd_color_code == 2 or current_precip > 8.0 or has_imd_rain:
                severity = "WARNING"
                depth_cm = int(s["flood_depth_threshold_cm"] * 0.6)
                passable_ped = depth_cm < 20
                passable_veh = depth_cm < 30
                color = "#F97316"  # Orange / Amber
                action = f"CAUTION: Water accumulation reported. {s['alternative_route']}"
            else:
                severity = "LOW"
                depth_cm = 0
                passable_ped = True
                passable_veh = True
                color = "#16A34A"  # Green
                action = "CLEAR: Normal traffic flow"

            feat = {
                "type": "Feature",
                "geometry": {
                    "type": "Point",
                    "coordinates": s["coordinates"]
                },
                "properties": {
                    "id": s["id"],
                    "name_en": s["name_en"],
                    "name_ta": s["name_ta"],
                    "category": "CRITICAL_SUBWAY_CHOKEPOINT",
                    "locality": s["locality"],
                    "severity": severity,
                    "color": color,
                    "water_depth_estimate_cm": depth_cm,
                    "is_passable_pedestrian": passable_ped,
                    "is_passable_vehicle": passable_veh,
                    "recommended_action": action,
                    "imd_warning_level": imd_color_code,
                    "valid_until_toi": imd_vupto,
                    "source": "IMD_NOWCAST_WFS + GCC_ELEVATION_PROFILE",
                    "updated_at_utc": now_utc
                }
            }
            output_features.append(feat)

        # 4. Process GCC Historical Hotspots
        for idx, h in enumerate(self.gcc_hotspots):
            coords = h.get("geometry", {}).get("coordinates", [])
            if not coords or len(coords) < 2:
                continue

            if imd_color_code >= 3:
                h_severity = "HIGH"
                h_color = "#EA580C"
                h_status = "PROBABLE_WATERLOGGING"
            elif imd_color_code == 2 or has_imd_rain:
                h_severity = "MODERATE"
                h_color = "#EAB308"
                h_status = "SURFACE_PUDDLING_POSSIBLE"
            else:
                h_severity = "LOW"
                h_color = "#94A3B8"
                h_status = "INACTIVE_HISTORICAL_HOTSPOT"

            feat = {
                "type": "Feature",
                "geometry": {
                    "type": "Point",
                    "coordinates": coords
                },
                "properties": {
                    "id": f"gcc_hotspot_{h.get('properties', {}).get('id', idx+1)}",
                    "name_en": f"GCC Flood Vulnerability Point #{idx+1}",
                    "name_ta": f"வெள்ள அபாய பகுதி #{idx+1}",
                    "category": "HISTORICAL_FLOOD_HOTSPOT",
                    "severity": h_severity,
                    "color": h_color,
                    "status": h_status,
                    "is_passable_pedestrian": h_severity != "HIGH",
                    "source": "GCC_2020_INUNDATION_SURVEY + LIVE_TELEMETRY",
                    "updated_at_utc": now_utc
                }
            }
            output_features.append(feat)

        # 5. Process Surveyed Road Inundation Depths (192 Points)
        for s_feat in self.surveyed_inundation:
            # Add updated timestamp
            s_copy = dict(s_feat)
            s_copy["properties"] = {**s_copy["properties"], "updated_at_utc": now_utc}
            output_features.append(s_copy)

        # 6. Process Elevated Safe Flyovers & Corridors
        for f_feat in self.flyovers:
            output_features.append(f_feat)

        # 7. Process Major Waterway & Canal Breach Buffer Corridors
        for w_feat in self.waterways:
            output_features.append(w_feat)

        # 8. Process CMRL Metro Flood Sanctuary Stations
        for m_feat in self.cmrl_stations:
            output_features.append(m_feat)

        # 9. Process GCC Emergency Relief Centres & Safe Shelters
        for r_feat in self.relief_centres:
            output_features.append(r_feat)

        # 10. Process Active Crowdsourced Citizen Reports
        crowd_feats = self.crowdsource_service.to_geojson_features()
        output_features.extend(crowd_feats)

        # 11. Add Macro Chennai District Warning Polygon (from IMD WFS if present)
        for d in district_features:
            props = d.get("properties", {})
            if str(props.get("District", "")).upper() == "CHENNAI":
                output_features.append({
                    "type": "Feature",
                    "geometry": d.get("geometry"),
                    "properties": {
                        "id": "imd_chennai_district_boundary",
                        "name_en": "Chennai District IMD Nowcast Warning Boundary",
                        "name_ta": "சென்னை மாவட்ட வானிலை எச்சரிக்கை பகுதி",
                        "category": "DISTRICT_WEATHER_BOUNDARY",
                        "color": "#3B82F6",
                        "imd_color_code": props.get("Color"),
                        "toi": props.get("toi"),
                        "vupto": props.get("vupto"),
                        "date": props.get("Date"),
                        "light_thunderstorm_cat4": props.get("cat4", 0),
                        "moderate_rain_cat7": props.get("cat7", 0),
                        "source": "IMD_MAUSAM_GEOSERVER_WFS",
                        "updated_at_utc": now_utc
                    }
                })
                break

        # Calculate category counts
        category_counts: Dict[str, int] = {}
        for f in output_features:
            cat = f.get("properties", {}).get("category", "OTHER")
            category_counts[cat] = category_counts.get(cat, 0) + 1

        return {
            "type": "FeatureCollection",
            "metadata": {
                "city": "Chennai",
                "generated_at_utc": now_utc,
                "total_hazards_count": len(output_features),
                "category_breakdown": category_counts,
                "subways_count": len(self.subways),
                "hotspots_count": len(self.gcc_hotspots),
                "surveyed_inundation_points_count": len(self.surveyed_inundation),
                "elevated_flyovers_count": len(self.flyovers),
                "waterways_count": len(self.waterways),
                "cmrl_stations_count": len(self.cmrl_stations),
                "relief_centres_count": len(self.relief_centres),
                "active_crowdsource_reports_count": len(crowd_feats),
                "live_weather": {
                    "imd_color_level": imd_color_code,
                    "imd_time_of_issue": imd_toi,
                    "imd_valid_upto": imd_vupto,
                    "open_meteo_precip_mm": current_precip,
                    "imd_rain_active": has_imd_rain
                }
            },
            "features": output_features
        }
