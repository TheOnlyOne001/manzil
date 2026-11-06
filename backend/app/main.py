"""Manzil Chennai Comprehensive Hazard & Ingestion FastAPI Service.

Provides real-time synthesized waterlogging, subway choke points, surveyed road depths,
elevated safe corridors, major waterway overflow buffers, CMRL flood haven stations,
GCC emergency relief centres, and live crowdsourced citizen reports.
"""

import time
import json
import logging
from typing import Dict, Any, Optional
from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.config import DERIVED_DATA_DIR
from app.ingestion.synthesizer import HazardSynthesizer
from app.ingestion.crowdsource_service import CrowdsourceHazardService, HazardReportSubmission
from app.agent_turn import router as agent_router, close_agent_client

# Setup logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger("manzil-backend")

app = FastAPI(
    title="Manzil Chennai Comprehensive Hazard & Navigation Ingestion API",
    description="Live multi-layer waterlogging, subway passability, elevated corridors, and disaster relief telemetry.",
    version="2.0.0"
)

# CORS middleware for mobile & web access
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Conversational agent (Gemini on Vertex AI via server-side ADC). See docs/LIVING_AGENT_BACKEND.md.
app.include_router(agent_router)

crowd_service = CrowdsourceHazardService(ttl_seconds=7200.0)
synthesizer = HazardSynthesizer(crowdsource_service=crowd_service)

# In-memory cache
_cache: Dict[str, Any] = {
    "data": None,
    "last_fetched_epoch": 0.0,
    "ttl_seconds": 300.0  # 5 minutes TTL
}

def _save_snapshot(feed_data: Dict[str, Any]) -> None:
    """Save latest snapshot to derived data folder for offline use."""
    try:
        DERIVED_DATA_DIR.mkdir(parents=True, exist_ok=True)
        snapshot_path = DERIVED_DATA_DIR / "latest_chennai_hazards.geojson"
        with open(snapshot_path, "w", encoding="utf-8") as f:
            json.dump(feed_data, f, indent=2)
        logger.info(f"Updated full hazard snapshot on disk at {snapshot_path}")
    except Exception as e:
        logger.error(f"Failed to write snapshot to disk: {e}")

async def get_or_refresh_hazard_feed(force: bool = False) -> Dict[str, Any]:
    """Retrieve hazard feed from cache or execute live fetch."""
    now = time.time()
    if (
        not force
        and _cache["data"] is not None
        and (now - _cache["last_fetched_epoch"] < _cache["ttl_seconds"])
    ):
        return _cache["data"]

    logger.info("Cache miss or force refresh: synthesizing live hazard data...")
    feed = await synthesizer.generate_unified_hazard_feed()
    _cache["data"] = feed
    _cache["last_fetched_epoch"] = now

    _save_snapshot(feed)
    return feed

@app.on_event("startup")
async def startup_event():
    # Map overlay availability must not depend on weather upstreams.
    # Legacy synthesized feeds remain lazy, only when their own endpoints are requested.
    # Modelled risk uses only the stored, re-validated weather file here (no network at startup).
    from app.map_overlays import build_overlays, save_snapshot
    from app.modelled_risk import add_modelled_risk, load_weather
    from app.night_context import add_night_context, load_night_context
    from datetime import datetime, timezone
    import asyncio
    def _build():
        now = datetime.now(timezone.utc)
        feed = add_modelled_risk(build_overlays(now=now), load_weather(), now)
        save_snapshot(add_night_context(feed, load_night_context()))
    await asyncio.to_thread(_build)

@app.on_event("shutdown")
async def shutdown_event():
    await close_agent_client()

@app.get("/")
async def root():
    return {
        "service": "Manzil Chennai Hazard & Mobility Ingestion API",
        "version": "2.0.0",
        "city": "Chennai",
        "endpoints": [
            "/api/v1/health",
            "/api/v1/map-overlays/chennai",
            "/api/v1/hazards/chennai",
            "/api/v1/hazards/chennai/subways",
            "/api/v1/hazards/chennai/inundation-points",
            "/api/v1/hazards/chennai/flyovers",
            "/api/v1/hazards/chennai/waterways",
            "/api/v1/hazards/chennai/metro-havens",
            "/api/v1/hazards/chennai/relief-centres",
            "/api/v1/hazards/chennai/hotspots",
            "/api/v1/hazards/report (POST)",
            "/api/v1/hazards/reports/live (GET)",
            "/api/v1/weather/nowcast",
            "/api/v1/hazards/refresh",
            "/api/v1/agent/turn (POST)"
        ]
    }

@app.get("/api/v1/health")
async def health():
    now = time.time()
    cache_age = now - _cache["last_fetched_epoch"] if _cache["last_fetched_epoch"] > 0 else None
    return {
        "status": "healthy",
        "cache_populated": _cache["data"] is not None,
        "cache_age_seconds": round(cache_age, 1) if cache_age is not None else None,
        "ttl_seconds": _cache["ttl_seconds"],
        "total_hazards_count": len(_cache["data"]["features"]) if _cache["data"] else 0,
        "category_breakdown": _cache["data"].get("metadata", {}).get("category_breakdown", {}) if _cache["data"] else {}
    }

@app.get("/api/v1/hazards/chennai")
async def get_all_hazards(force: bool = False):
    """Return the complete unified GeoJSON FeatureCollection across all 10 layers."""
    feed = await get_or_refresh_hazard_feed(force=force)
    return JSONResponse(content=feed, media_type="application/geo+json")

@app.get("/api/v1/hazards/chennai/subways")
async def get_subways():
    """Return 12 critical subways and underpasses with passability status."""
    feed = await get_or_refresh_hazard_feed()
    features = [
        f for f in feed.get("features", [])
        if f.get("properties", {}).get("category") == "CRITICAL_SUBWAY_CHOKEPOINT"
    ]
    return JSONResponse(
        content={
            "type": "FeatureCollection",
            "metadata": {**feed.get("metadata", {}), "filtered_category": "CRITICAL_SUBWAY_CHOKEPOINT", "count": len(features)},
            "features": features
        },
        media_type="application/geo+json"
    )

@app.get("/api/v1/hazards/chennai/inundation-points")
async def get_surveyed_inundation_points():
    """Return 192 surveyed road inundation depth points with measured inches/cm and field remarks."""
    feed = await get_or_refresh_hazard_feed()
    features = [
        f for f in feed.get("features", [])
        if f.get("properties", {}).get("category") == "SURVEYED_ROAD_INUNDATION"
    ]
    return JSONResponse(
        content={
            "type": "FeatureCollection",
            "metadata": {**feed.get("metadata", {}), "filtered_category": "SURVEYED_ROAD_INUNDATION", "count": len(features)},
            "features": features
        },
        media_type="application/geo+json"
    )

@app.get("/api/v1/hazards/chennai/flyovers")
async def get_flyovers():
    """Return 18 elevated safe flyovers and grade separators (dry high-ground bypasses)."""
    feed = await get_or_refresh_hazard_feed()
    features = [
        f for f in feed.get("features", [])
        if f.get("properties", {}).get("category") == "ELEVATED_SAFE_CORRIDOR"
    ]
    return JSONResponse(
        content={
            "type": "FeatureCollection",
            "metadata": {**feed.get("metadata", {}), "filtered_category": "ELEVATED_SAFE_CORRIDOR", "count": len(features)},
            "features": features
        },
        media_type="application/geo+json"
    )

@app.get("/api/v1/hazards/chennai/waterways")
async def get_waterways():
    """Return 4 major river & canal overflow vulnerability corridors with buffer zones."""
    feed = await get_or_refresh_hazard_feed()
    features = [
        f for f in feed.get("features", [])
        if f.get("properties", {}).get("category") == "WATERWAY_OVERFLOW_CORRIDOR"
    ]
    return JSONResponse(
        content={
            "type": "FeatureCollection",
            "metadata": {**feed.get("metadata", {}), "filtered_category": "WATERWAY_OVERFLOW_CORRIDOR", "count": len(features)},
            "features": features
        },
        media_type="application/geo+json"
    )

@app.get("/api/v1/hazards/chennai/metro-havens")
async def get_metro_havens():
    """Return 41 CMRL flood sanctuary stations with dry pedestrian road-crossing access."""
    feed = await get_or_refresh_hazard_feed()
    features = [
        f for f in feed.get("features", [])
        if f.get("properties", {}).get("category") == "METRO_SANCTUARY_STATION"
    ]
    return JSONResponse(
        content={
            "type": "FeatureCollection",
            "metadata": {**feed.get("metadata", {}), "filtered_category": "METRO_SANCTUARY_STATION", "count": len(features)},
            "features": features
        },
        media_type="application/geo+json"
    )

@app.get("/api/v1/hazards/chennai/relief-centres")
async def get_relief_centres(zone: Optional[int] = Query(None, ge=1, le=15)):
    """Return 89 GCC Emergency Relief Centres with officer phone numbers (optionally filtered by zone)."""
    feed = await get_or_refresh_hazard_feed()
    features = [
        f for f in feed.get("features", [])
        if f.get("properties", {}).get("category") == "EMERGENCY_RELIEF_CENTRE"
        and (zone is None or f.get("properties", {}).get("zone_number") == zone)
    ]
    return JSONResponse(
        content={
            "type": "FeatureCollection",
            "metadata": {**feed.get("metadata", {}), "filtered_category": "EMERGENCY_RELIEF_CENTRE", "zone_filter": zone, "count": len(features)},
            "features": features
        },
        media_type="application/geo+json"
    )

@app.get("/api/v1/hazards/chennai/hotspots")
async def get_hotspots():
    """Return 53 GCC historical flood inundation points with current risk severity."""
    feed = await get_or_refresh_hazard_feed()
    features = [
        f for f in feed.get("features", [])
        if f.get("properties", {}).get("category") == "HISTORICAL_FLOOD_HOTSPOT"
    ]
    return JSONResponse(
        content={
            "type": "FeatureCollection",
            "metadata": {**feed.get("metadata", {}), "filtered_category": "HISTORICAL_FLOOD_HOTSPOT", "count": len(features)},
            "features": features
        },
        media_type="application/geo+json"
    )

@app.post("/api/v1/hazards/report")
async def submit_hazard_report(report: HazardReportSubmission):
    """Submit a real-time crowdsourced citizen hazard observation."""
    saved_report = crowd_service.submit_report(report)
    # Evict cache so next fetch immediately reflects citizen reports
    _cache["data"] = None
    return {
        "status": "received",
        "report_id": saved_report["id"],
        "severity": saved_report["severity"],
        "water_depth_estimate_cm": saved_report["water_depth_estimate_cm"],
        "color": saved_report["color"],
        "ttl_hours": round(saved_report["ttl_seconds"] / 3600.0, 1)
    }

@app.get("/api/v1/hazards/reports/live")
async def get_live_reports():
    """Return active (non-expired) citizen crowd reports as GeoJSON."""
    features = crowd_service.to_geojson_features()
    return JSONResponse(
        content={
            "type": "FeatureCollection",
            "metadata": {
                "category": "CROWDSOURCED_HAZARD_REPORT",
                "active_reports_count": len(features),
                "ttl_hours": 2.0
            },
            "features": features
        },
        media_type="application/geo+json"
    )

@app.get("/api/v1/weather/nowcast")
async def get_weather_nowcast():
    """Return live weather and nowcast bulletin details."""
    feed = await get_or_refresh_hazard_feed()
    return {
        "metadata": feed.get("metadata", {}),
        "live_weather": feed.get("metadata", {}).get("live_weather", {})
    }

@app.post("/api/v1/hazards/refresh")
async def refresh_hazards():
    """Force evict cache and fetch fresh external feeds."""
    feed = await get_or_refresh_hazard_feed(force=True)
    return {
        "status": "refreshed",
        "generated_at_utc": feed.get("metadata", {}).get("generated_at_utc"),
        "total_hazards": len(feed.get("features", [])),
        "category_breakdown": feed.get("metadata", {}).get("category_breakdown", {})
    }

@app.get("/api/v1/map-overlays/chennai")
async def get_chennai_map_overlays():
    """Evidence-labelled context, without synthesized flood depth or passability.
    Adds the low-confidence 'risk' rainfall proxy from Open-Meteo (cached 10 min, <=8 s fetch) and the
    static mapped 'night' layer (OSM lit tags + support places); never uses the legacy synthesized hazard feed."""
    from app.map_overlays import build_overlays, save_snapshot
    from app.modelled_risk import add_modelled_risk, current_weather
    from app.night_context import add_night_context, load_night_context
    from datetime import datetime, timezone
    import asyncio
    weather = await current_weather()
    def _build():
        now = datetime.now(timezone.utc)
        feed = add_modelled_risk(build_overlays(now=now), weather, now)
        # Static mapped Night Walk context (OSM lit tags + support places); no network.
        feed = add_night_context(feed, load_night_context())
        save_snapshot(feed)
        return feed
    feed = await asyncio.to_thread(_build)
    return JSONResponse(content=feed, media_type="application/geo+json", headers={"Cache-Control": "no-store"})
