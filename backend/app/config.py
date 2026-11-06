"""Configuration for Manzil Chennai Hazard Ingestion Pipeline."""
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parents[2]
DATA_DIR = BASE_DIR / "data"

# Chennai Geographic Bounding Box
CHENNAI_BOUNDS = {
    "min_lat": 12.80,
    "max_lat": 13.30,
    "min_lng": 79.95,
    "max_lng": 80.35,
    "center_lat": 13.0827,
    "center_lng": 80.2707
}

# External API Endpoints
IMD_GEOSERVER_WFS_BASE = "https://reactjs.imd.gov.in/geoserver/imd/ows"
OPEN_METEO_BASE = "https://api.open-meteo.com/v1/forecast"

# Hotspots and Reference Files
GCC_HOTSPOTS_PATH = DATA_DIR / "reference-maps" / "gcc-flood-hotspots-2020.geojson"
DERIVED_DATA_DIR = DATA_DIR / "derived"
