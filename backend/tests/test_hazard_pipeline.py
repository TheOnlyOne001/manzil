"""Automated verification suite for Manzil Chennai Hazard Ingestion Pipeline."""

import os
import sys
from pathlib import Path
from starlette.testclient import TestClient

# Ensure backend root is on sys.path
backend_root = Path(__file__).resolve().parents[1]
if str(backend_root) not in sys.path:
    sys.path.insert(0, str(backend_root))

from app.main import app

def run_tests():
    print("==================================================")
    print("STARTING HAZARD INGESTION PIPELINE VERIFICATION")
    print("==================================================")
    
    with TestClient(app) as client:
        # 1. Test Root
        res_root = client.get("/")
        assert res_root.status_code == 200, f"Root failed: {res_root.status_code}"
        print("[PASS] 1. Root API endpoint reachable")

        # 2. Test Full Hazards Endpoint (Triggers live ingestion if startup did not)
        print("[INFO] Fetching full unified hazards GeoJSON from live sources...")
        res_hazards = client.get("/api/v1/hazards/chennai")
        assert res_hazards.status_code == 200, f"Hazards endpoint failed: {res_hazards.status_code}"
        hazards_data = res_hazards.json()
        assert hazards_data["type"] == "FeatureCollection", "Payload must be a GeoJSON FeatureCollection"
        features = hazards_data["features"]
        assert len(features) >= 65, f"Expected at least 65 features, got {len(features)}"
        print(f"[PASS] 2. Full Hazards GeoJSON valid: {len(features)} total features ingested")

        # Check metadata
        meta = hazards_data.get("metadata", {})
        print(f"       Metadata: City={meta.get('city')}, Time={meta.get('generated_at_utc')}")
        print(f"       Weather: IMD Color Level={meta.get('live_weather', {}).get('imd_color_level')}, Precip={meta.get('live_weather', {}).get('open_meteo_precip_mm')} mm/hr")

        # 3. Test Subways Endpoint
        res_subways = client.get("/api/v1/hazards/chennai/subways")
        assert res_subways.status_code == 200, f"Subways endpoint failed: {res_subways.status_code}"
        subways_data = res_subways.json()
        subways = subways_data["features"]
        assert len(subways) == 12, f"Expected 12 subways, got {len(subways)}"
        # Verify schema of first subway
        first_subway = subways[0]["properties"]
        assert "name_en" in first_subway and "name_ta" in first_subway
        assert "severity" in first_subway and "color" in first_subway
        assert "is_passable_pedestrian" in first_subway and "recommended_action" in first_subway
        print(f"[PASS] 3. Subways endpoint valid: 12 critical subways returned with bilingual labels and passability")

        # 4. Test Hotspots Endpoint
        res_hotspots = client.get("/api/v1/hazards/chennai/hotspots")
        assert res_hotspots.status_code == 200, f"Hotspots endpoint failed: {res_hotspots.status_code}"
        hotspots_data = res_hotspots.json()
        hotspots = hotspots_data["features"]
        assert len(hotspots) == 53, f"Expected 53 hotspots, got {len(hotspots)}"
        print(f"[PASS] 4. Hotspots endpoint valid: 53 historical inundation points returned")

        # 5. Test Weather Nowcast Endpoint
        res_weather = client.get("/api/v1/weather/nowcast")
        assert res_weather.status_code == 200, f"Weather endpoint failed: {res_weather.status_code}"
        weather_data = res_weather.json()
        assert "live_weather" in weather_data
        print(f"[PASS] 5. Weather nowcast endpoint valid: IMD + Open-Meteo telemetry present")

        # 6. Test Cache and Health Endpoint
        res_health = client.get("/api/v1/health")
        assert res_health.status_code == 200, f"Health endpoint failed: {res_health.status_code}"
        health_data = res_health.json()
        assert health_data["status"] == "healthy"
        assert health_data["cache_populated"] is True
        assert health_data["total_hazards_count"] >= 65
        print(f"[PASS] 6. Health & Cache valid: Cache populated, age={health_data['cache_age_seconds']}s")

        # 7. Test Derived Snapshot File on Disk
        snapshot_file = backend_root.parent / "data" / "derived" / "latest_chennai_hazards.geojson"
        assert snapshot_file.exists(), f"Snapshot file not created at {snapshot_file}"
        assert snapshot_file.stat().st_size > 10000, f"Snapshot file too small ({snapshot_file.stat().st_size} bytes)"
        print(f"[PASS] 7. Derived file verified: {snapshot_file.name} saved ({snapshot_file.stat().st_size} bytes)")

    print("==================================================")
    print("ALL HAZARD INGESTION PIPELINE TESTS PASSED!")
    print("==================================================")

if __name__ == "__main__":
    run_tests()
