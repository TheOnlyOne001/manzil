"""Comprehensive validation test suite for Manzil Expanded Hazard & Navigation Ingestion Pipeline."""

import sys
from pathlib import Path
from starlette.testclient import TestClient

# Ensure backend root is on sys.path
backend_root = Path(__file__).resolve().parents[1]
if str(backend_root) not in sys.path:
    sys.path.insert(0, str(backend_root))

from app.main import app

def run_tests():
    print("======================================================================")
    print("STARTING MANZIL COMPREHENSIVE HAZARD INGESTION PIPELINE VERIFICATION")
    print("======================================================================")

    with TestClient(app) as client:
        # 1. Root and Health Check
        res_root = client.get("/")
        assert res_root.status_code == 200, f"Root failed: {res_root.status_code}"
        print("[PASS] 1. Root API service discovery accessible")

        # 2. Ingest Full Unified Multi-Layer GeoJSON
        print("[INFO] Executing multi-source ingestion synthesis...")
        res_all = client.get("/api/v1/hazards/chennai")
        assert res_all.status_code == 200, f"Hazards endpoint failed: {res_all.status_code}"
        data_all = res_all.json()
        assert data_all["type"] == "FeatureCollection"
        features = data_all["features"]
        meta = data_all["metadata"]
        breakdown = meta.get("category_breakdown", {})

        print(f"[PASS] 2. Ingested Unified GeoJSON with {len(features)} total features across Chennai.")
        print(f"       Category Breakdown:")
        for cat, cnt in sorted(breakdown.items()):
            print(f"         - {cat}: {cnt}")

        assert len(features) >= 400, f"Expected >= 400 features, got {len(features)}"

        # 3. Verify Surveyed Road Inundation Depths (192 empirical points)
        res_inundation = client.get("/api/v1/hazards/chennai/inundation-points")
        assert res_inundation.status_code == 200
        inundation_features = res_inundation.json()["features"]
        assert len(inundation_features) == 192, f"Expected 192 surveyed points, got {len(inundation_features)}"
        sample_pt = inundation_features[0]["properties"]
        assert "water_depth_inches" in sample_pt and "water_depth_estimate_cm" in sample_pt
        assert "field_remarks" in sample_pt and "is_passable_pedestrian" in sample_pt
        print(f"[PASS] 3. Surveyed Inundation Layer: 192 points verified with empirical depths & remarks.")

        # 4. Verify Elevated Safe Corridors (18 Flyovers)
        res_flyovers = client.get("/api/v1/hazards/chennai/flyovers")
        assert res_flyovers.status_code == 200
        flyover_features = res_flyovers.json()["features"]
        assert len(flyover_features) == 18, f"Expected 18 flyovers, got {len(flyover_features)}"
        sample_flyover = flyover_features[0]["properties"]
        assert sample_flyover["is_elevated"] is True
        assert sample_flyover["color"] == "#06B6D4"
        assert "Kathipara" in [f["properties"]["name_en"] for f in flyover_features if "Kathipara" in f["properties"]["name_en"]][0]
        print(f"[PASS] 4. Elevated Flyovers Layer: 18 high-ground bypass corridors verified.")

        # 5. Verify Major Waterway & Canal Breach Buffer Corridors (4 Waterways)
        res_waterways = client.get("/api/v1/hazards/chennai/waterways")
        assert res_waterways.status_code == 200
        waterway_features = res_waterways.json()["features"]
        assert len(waterway_features) == 4, f"Expected 4 waterways, got {len(waterway_features)}"
        adyar = next(f for f in waterway_features if f["properties"]["id"] == "waterway_adyar_river")
        assert adyar["geometry"]["type"] == "LineString"
        assert "Chembarambakkam" in adyar["properties"]["upstream_reservoir"]
        assert adyar["properties"]["safe_buffer_distance_meters"] == 150
        print(f"[PASS] 5. Major Waterways Layer: Adyar, Cooum, Buckingham Canal & Otteri Nullah verified.")

        # 6. Verify CMRL Metro Stations (41 Flood Sanctuary Stations)
        res_metro = client.get("/api/v1/hazards/chennai/metro-havens")
        assert res_metro.status_code == 200
        metro_features = res_metro.json()["features"]
        assert len(metro_features) == 41, f"Expected 41 CMRL stations, got {len(metro_features)}"
        sample_metro = metro_features[0]["properties"]
        assert sample_metro["has_elevated_dry_crossing"] is True
        assert sample_metro["color"] == "#8B5CF6"
        print(f"[PASS] 6. CMRL Metro Havens Layer: 41 stations verified from official CMRL GTFS.")

        # 7. Verify GCC Emergency Relief Centres (89 Centres across 15 Zones)
        res_relief = client.get("/api/v1/hazards/chennai/relief-centres")
        assert res_relief.status_code == 200
        relief_features = res_relief.json()["features"]
        assert len(relief_features) == 89, f"Expected 89 relief centres, got {len(relief_features)}"
        sample_rc = relief_features[0]["properties"]
        assert "officer_phone" in sample_rc and len(sample_rc["officer_phone"]) == 10
        assert "zone_number" in sample_rc and sample_rc["is_dry_shelter"] is True

        # Test zone filtering
        res_zone1 = client.get("/api/v1/hazards/chennai/relief-centres?zone=1")
        assert res_zone1.status_code == 200
        zone1_features = res_zone1.json()["features"]
        assert len(zone1_features) > 0
        assert all(f["properties"]["zone_number"] == 1 for f in zone1_features)
        print(f"[PASS] 7. GCC Relief Centres Layer: 89 verified centres with officer contacts + zone filter.")

        # 8. Test Bi-directional Crowdsourced Hazard Reporting
        print("[INFO] Testing citizen hazard submission endpoint...")
        sample_report = {
            "latitude": 12.9815,
            "longitude": 80.2215,
            "depth_level": "KNEE_DEEP",
            "hazard_type": "WATERLOGGING",
            "description": "Velachery 100 Feet Road knee-deep water near Vijaya Nagar bus stop",
            "device_id": "test_redmi_commuter_01"
        }
        res_sub = client.post("/api/v1/hazards/report", json=sample_report)
        assert res_sub.status_code == 200, f"Submit report failed: {res_sub.text}"
        sub_body = res_sub.json()
        assert sub_body["status"] == "received"
        assert sub_body["severity"] == "HIGH"
        assert sub_body["color"] == "#EA580C"
        assert sub_body["water_depth_estimate_cm"] == 32
        report_id = sub_body["report_id"]

        # Verify live reports list
        res_live = client.get("/api/v1/hazards/reports/live")
        assert res_live.status_code == 200
        live_reports = res_live.json()["features"]
        assert any(f["properties"]["id"] == report_id for f in live_reports)
        print(f"[PASS] 8. Crowdsourced Reporting: Citizen report submitted and verified live ({report_id}).")

        # 9. Verify Disk Snapshot File Generation
        snapshot_path = backend_root.parent / "data" / "derived" / "latest_chennai_hazards.geojson"
        assert snapshot_path.exists(), f"Snapshot not found at {snapshot_path}"
        file_size = snapshot_path.stat().st_size
        print(f"[PASS] 9. Disk snapshot verified: {snapshot_path.name} saved ({file_size:,} bytes).")
        assert file_size > 100000, f"Snapshot file size ({file_size} bytes) smaller than expected."

    print("======================================================================")
    print("ALL COMPREHENSIVE HAZARD INGESTION TESTS PASSED SUCCESSFULLY!")
    print("======================================================================")

if __name__ == "__main__":
    run_tests()
