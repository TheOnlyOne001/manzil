"""Loader for GCC Historical Flood Hotspots."""
import json
import logging
from typing import List, Dict, Any
from app.config import GCC_HOTSPOTS_PATH

logger = logging.getLogger(__name__)

def load_gcc_hotspots() -> List[Dict[str, Any]]:
    """Load the 53 historical GCC flood hotspots GeoJSON."""
    if not GCC_HOTSPOTS_PATH.exists():
        logger.warning(f"GCC Hotspots file not found at {GCC_HOTSPOTS_PATH}")
        return []
    try:
        with open(GCC_HOTSPOTS_PATH, "r", encoding="utf-8") as f:
            data = json.load(f)
            features = data.get("features", [])
            logger.info(f"Loaded {len(features)} GCC flood hotspot records.")
            return features
    except Exception as e:
        logger.error(f"Error loading GCC hotspots: {e}")
        return []
