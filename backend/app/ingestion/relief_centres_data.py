"""Greater Chennai Corporation Emergency Flood Relief Centres & Safe Havens.

Source: Official Greater Chennai Corporation Floods & Rain Relief Centres Registry (2024).
Provides dry community centers, schools, and safe shelters with designated
officer contact numbers across all 15 Chennai zones.
"""

import re
import logging
from typing import List, Dict, Any
from pypdf import PdfReader
from app.config import DATA_DIR

logger = logging.getLogger(__name__)

PDF_PATH = DATA_DIR / "reference-maps" / "gcc-relief-centres-2024.pdf"

# Chennai 15 Administrative Zone Centroids (EPSG:4326 [lng, lat])
ZONE_CENTROIDS: Dict[int, List[float]] = {
    1: [80.3015, 13.1585],  # Tiruvottiyur
    2: [80.2605, 13.1680],  # Manali
    3: [80.2310, 13.1480],  # Madhavaram
    4: [80.2880, 13.1250],  # Tondiarpet
    5: [80.2920, 13.1050],  # Royapuram
    6: [80.2450, 13.1120],  # Thiru-Vi-Ka Nagar
    7: [80.1600, 13.1150],  # Ambattur
    8: [80.2100, 13.0850],  # Anna Nagar
    9: [80.2450, 13.0450],  # Teynampet
    10: [80.2200, 13.0500], # Kodambakkam
    11: [80.1750, 13.0400], # Valasaravakkam
    12: [80.2000, 12.9980], # Alandur
    13: [80.2550, 13.0050], # Adyar
    14: [80.2350, 12.9600], # Perungudi
    15: [80.2280, 12.9000]  # Sholinganallur
}

ZONE_NAMES: Dict[int, str] = {
    1: "Tiruvottiyur", 2: "Manali", 3: "Madhavaram", 4: "Tondiarpet", 5: "Royapuram",
    6: "Thiru-Vi-Ka Nagar", 7: "Ambattur", 8: "Anna Nagar", 9: "Teynampet", 10: "Kodambakkam",
    11: "Valasaravakkam", 12: "Alandur", 13: "Adyar", 14: "Perungudi", 15: "Sholinganallur"
}

def load_gcc_relief_centres() -> List[Dict[str, Any]]:
    """Parse official GCC relief centre PDF into GeoJSON features."""
    if not PDF_PATH.exists():
        logger.warning(f"GCC Relief Centres PDF not found at {PDF_PATH}")
        return []

    try:
        reader = PdfReader(str(PDF_PATH))
        full_text = "\n".join([p.extract_text() for p in reader.pages])
        lines = [l.strip() for l in full_text.split("\n") if l.strip()]

        phone_pattern = re.compile(r"(\b[6-9]\d{9}\b)")
        records: List[Dict[str, Any]] = []
        current: Dict[str, Any] = {}

        for line in lines:
            start_match = re.match(r"^(\d+)\s+(\d+)\s+(\d+)\b", line)
            if start_match:
                if current and current.get("phone"):
                    records.append(current)
                sl, zone, div = start_match.groups()
                current = {
                    "sl": int(sl),
                    "zone": int(zone),
                    "division": int(div),
                    "address_tokens": [],
                    "phone": None
                }
            elif current:
                phone_match = phone_pattern.search(line)
                if phone_match and not current["phone"]:
                    current["phone"] = phone_match.group(1)
                    after_phone = line[phone_match.end():].strip()
                    if after_phone:
                        current["address_tokens"].append(after_phone)
                elif current.get("phone"):
                    current["address_tokens"].append(line)

        if current and current.get("phone"):
            records.append(current)

        features = []
        for r in records:
            addr = " ".join(r["address_tokens"]).strip()
            if not addr:
                addr = f"GCC Relief Camp Division {r['division']}"

            zone_num = r["zone"]
            base_coords = ZONE_CENTROIDS.get(zone_num, [80.2300, 13.0500])
            
            # Deterministic spread inside the division so pins don't overlap
            offset_x = ((r["division"] % 7) - 3) * 0.0045 + ((r["sl"] % 5) * 0.001)
            offset_y = ((r["division"] % 5) - 2) * 0.0045 - ((r["sl"] % 3) * 0.001)
            coords = [round(base_coords[0] + offset_x, 6), round(base_coords[1] + offset_y, 6)]

            zone_name = ZONE_NAMES.get(zone_num, f"Zone {zone_num}")

            feat = {
                "type": "Feature",
                "geometry": {
                    "type": "Point",
                    "coordinates": coords
                },
                "properties": {
                    "id": f"gcc_relief_centre_{r['sl']}",
                    "name_en": f"GCC Relief Centre: {addr}",
                    "name_ta": f"மாநகராட்சி நிவாரண மையம் (மண்டலம் {zone_num}): {addr}",
                    "category": "EMERGENCY_RELIEF_CENTRE",
                    "zone_number": zone_num,
                    "zone_name": zone_name,
                    "division": r["division"],
                    "officer_phone": r["phone"],
                    "address": addr,
                    "severity": "SAFE_HAVEN",
                    "color": "#10B981",  # Emerald Green (Safe Relief Shelter)
                    "is_dry_shelter": True,
                    "is_passable_pedestrian": True,
                    "recommended_action": f"SAFE HAVEN: Emergency dry shelter. Contact Officer: {r['phone']}",
                    "source": "GCC_OFFICIAL_RELIEF_CENTRES_2024"
                }
            }
            features.append(feat)

        logger.info(f"Loaded {len(features)} GCC Emergency Relief Centres.")
        return features

    except Exception as e:
        logger.error(f"Error parsing GCC Relief Centres PDF: {e}")
        return []
