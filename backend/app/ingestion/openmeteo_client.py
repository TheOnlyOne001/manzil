"""Open-Meteo High Resolution Precipitation Ingestion Client."""
import httpx
import logging
from typing import Dict, Any, Optional
from app.config import OPEN_METEO_BASE, CHENNAI_BOUNDS

logger = logging.getLogger(__name__)

class OpenMeteoClient:
    def __init__(self, timeout: float = 12.0):
        self.timeout = timeout
        self.base_url = OPEN_METEO_BASE

    async def fetch_current_precipitation(
        self,
        lat: float = CHENNAI_BOUNDS["center_lat"],
        lng: float = CHENNAI_BOUNDS["center_lng"]
    ) -> Optional[Dict[str, Any]]:
        """Fetch live rainfall telemetry and next 6-hour forecast."""
        params = {
            "latitude": lat,
            "longitude": lng,
            "current": "temperature_2m,precipitation,rain,weather_code",
            "hourly": "precipitation_probability,precipitation,rain,weather_code",
            "forecast_days": 1,
            "timezone": "Asia/Kolkata"
        }
        headers = {"User-Agent": "ManzilBackend/1.0 (Chennai Flood Ingestion)"}
        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                resp = await client.get(self.base_url, params=params, headers=headers)
                if resp.status_code == 200:
                    data = resp.json()
                    current = data.get("current", {})
                    logger.info(
                        f"Fetched Open-Meteo precipitation: {current.get('precipitation')} mm/hr "
                        f"(rain={current.get('rain')} mm)"
                    )
                    return data
                else:
                    logger.error(f"Open-Meteo HTTP {resp.status_code}: {resp.text[:200]}")
                    return None
        except Exception as e:
            logger.error(f"Failed to fetch Open-Meteo data: {e}")
            return None
