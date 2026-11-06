"""IMD Mausam GeoServer WFS Ingestion Client."""
import httpx
import logging
from typing import Dict, Any, List
from app.config import IMD_GEOSERVER_WFS_BASE

logger = logging.getLogger(__name__)

class IMDClient:
    def __init__(self, timeout: float = 15.0):
        self.timeout = timeout
        self.base_url = IMD_GEOSERVER_WFS_BASE

    async def fetch_district_nowcast(self, state: str = "TAMILNADU") -> List[Dict[str, Any]]:
        """Fetch District-wise Nowcast warnings for Tamil Nadu / Chennai."""
        params = {
            "service": "WFS",
            "version": "1.1.0",
            "request": "GetFeature",
            "typename": "imd:NowcastWarningDistrict",
            "srsname": "EPSG:4326",
            "outputFormat": "application/json",
            "cql_filter": f"State='{state}'"
        }
        headers = {"User-Agent": "ManzilBackend/1.0 (Chennai Flood Ingestion)"}
        try:
            async with httpx.AsyncClient(verify=False, timeout=self.timeout) as client:
                resp = await client.get(self.base_url, params=params, headers=headers)
                if resp.status_code == 200:
                    data = resp.json()
                    features = data.get("features", [])
                    logger.info(f"Fetched {len(features)} district nowcast records from IMD WFS.")
                    return features
                else:
                    logger.error(f"IMD District Nowcast HTTP {resp.status_code}: {resp.text[:200]}")
                    return []
        except Exception as e:
            logger.error(f"Failed to fetch IMD District Nowcast: {e}")
            return []

    async def fetch_station_nowcast(self) -> List[Dict[str, Any]]:
        """Fetch Station-wise Nowcast for Meenambakkam and Nungambakkam."""
        params = {
            "service": "WFS",
            "version": "1.1.0",
            "request": "GetFeature",
            "typename": "imd:NowcastWarningStation",
            "srsname": "EPSG:4326",
            "outputFormat": "application/json",
            "cql_filter": "Station IN ('Meenambakkam AP','Nungambakkam')"
        }
        headers = {"User-Agent": "ManzilBackend/1.0 (Chennai Flood Ingestion)"}
        try:
            async with httpx.AsyncClient(verify=False, timeout=self.timeout) as client:
                resp = await client.get(self.base_url, params=params, headers=headers)
                if resp.status_code == 200:
                    data = resp.json()
                    features = data.get("features", [])
                    logger.info(f"Fetched {len(features)} station nowcast records from IMD WFS.")
                    return features
                else:
                    logger.error(f"IMD Station Nowcast HTTP {resp.status_code}: {resp.text[:200]}")
                    return []
        except Exception as e:
            logger.error(f"Failed to fetch IMD Station Nowcast: {e}")
            return []
