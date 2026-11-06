"""Major Waterways & Canal Overflow Breach Buffer Zones in Chennai.

Covers the 4 critical surface water corridors that overflow during monsoon downpours
and reservoir releases (Chembarambakkam, Poondi, Red Hills):
1. Adyar River (Manapakkam -> Saidapet -> Kotturpuram -> Adyar Estuary)
2. Cooum River (Maduravoyal -> Aminjikarai -> Choolaimedu -> Napier Bridge)
3. Buckingham Canal (North: Basin Bridge; South: Mylapore, Kotturpuram, Sholinganallur)
4. Otteri Nullah (Anna Nagar -> Kilpauk -> Perambur -> Basin Bridge)
"""

from typing import List, Dict, Any

CHENNAI_WATERWAYS: List[Dict[str, Any]] = [
    {
        "id": "waterway_adyar_river",
        "name_en": "Adyar River Overflow Corridor",
        "name_ta": "அடையாறு ஆற்று வெள்ள அபாயப் பகுதி",
        "category": "WATERWAY_OVERFLOW_CORRIDOR",
        "waterway_type": "RIVER",
        "breach_vulnerability": "CRITICAL_ON_RESERVOIR_DISCHARGE",
        "upstream_reservoir": "Chembarambakkam Lake",
        "critical_vulnerable_neighborhoods": [
            "Manapakkam", "Ramapuram", "Jafferkhanpet", "Saidapet Maraimalai Adigal Bridge",
            "Kotturpuram River View Colony", "Adyar"
        ],
        "safe_buffer_distance_meters": 150,
        "coordinates": [
            [80.1702, 13.0185],  # Manapakkam
            [80.1985, 13.0235],  # Jafferkhanpet
            [80.2217, 13.0189],  # Saidapet Maraimalai Adigal Bridge
            [80.2443, 13.0182],  # Kotturpuram Bridge
            [80.2642, 13.0083],  # Thiru-Vi-Ka Bridge (Adyar Estuary)
        ]
    },
    {
        "id": "waterway_cooum_river",
        "name_en": "Cooum River Overflow Corridor",
        "name_ta": "கூவம் ஆற்று வெள்ள அபாயப் பகுதி",
        "category": "WATERWAY_OVERFLOW_CORRIDOR",
        "waterway_type": "RIVER",
        "breach_vulnerability": "HIGH",
        "upstream_reservoir": "Poondi / Kesavaram Anicut",
        "critical_vulnerable_neighborhoods": [
            "Maduravoyal", "Koyambedu River Edge", "Arumbakkam", "Aminjikarai (PH Road)",
            "Choolaimedu / Namasivayapuram", "Chetpet", "Chintadripet", "Napier Bridge"
        ],
        "safe_buffer_distance_meters": 120,
        "coordinates": [
            [80.1610, 13.0635],  # Maduravoyal
            [80.1950, 13.0720],  # Koyambedu
            [80.2185, 13.0760],  # Aminjikarai
            [80.2430, 13.0710],  # Chetpet
            [80.2720, 13.0765],  # Chintadripet
            [80.2880, 13.0680]   # Napier Bridge (Bay of Bengal)
        ]
    },
    {
        "id": "waterway_buckingham_canal_south",
        "name_en": "Buckingham Canal (Central-South Corridor)",
        "name_ta": "பக்கிங்காம் கால்வாய் (மத்திய-தெற்குப் பகுதி)",
        "category": "WATERWAY_OVERFLOW_CORRIDOR",
        "waterway_type": "CANAL",
        "breach_vulnerability": "HIGH_ON_HIGH_TIDE",
        "upstream_reservoir": "Tidal Influx & Urban Storm Runoff",
        "critical_vulnerable_neighborhoods": [
            "Mylapore Canal Bank Road", "Kotturpuram Canal Junction", "Kasturba Nagar MRTS",
            "Indira Nagar", "Thiruvanmiyur Canal Edge", "Perungudi / Sholinganallur"
        ],
        "safe_buffer_distance_meters": 100,
        "coordinates": [
            [80.2680, 13.0350],  # Mylapore Canal Bank
            [80.2520, 13.0150],  # Kotturpuram
            [80.2510, 12.9980],  # Kasturba Nagar MRTS
            [80.2530, 12.9810],  # Thiruvanmiyur
            [80.2350, 12.9450],  # Perungudi Canal Edge
            [80.2280, 12.8950]   # Sholinganallur Canal Junction
        ]
    },
    {
        "id": "waterway_otteri_nullah",
        "name_en": "Otteri Nullah Storm Canal",
        "name_ta": "ஒட்டேரி நல்லா கால்வாய்",
        "category": "WATERWAY_OVERFLOW_CORRIDOR",
        "waterway_type": "STORM_DRAINAGE_CANAL",
        "breach_vulnerability": "VERY_HIGH",
        "upstream_reservoir": "North Chennai Basin Urban Storm Outflow",
        "critical_vulnerable_neighborhoods": [
            "Anna Nagar East Canal Edge", "Kilpauk Garden Road", "Purasawalkam",
            "Otteri / Brick Kiln Road", "Basin Bridge Junction"
        ],
        "safe_buffer_distance_meters": 80,
        "coordinates": [
            [80.2150, 13.0880],  # Anna Nagar East
            [80.2350, 13.0840],  # Kilpauk Garden
            [80.2520, 13.0870],  # Otteri
            [80.2680, 13.0980],  # Basin Bridge Junction
        ]
    }
]

def load_waterway_corridors() -> List[Dict[str, Any]]:
    """Return major Chennai waterways as GeoJSON LineString corridor features."""
    features = []
    for w in CHENNAI_WATERWAYS:
        features.append({
            "type": "Feature",
            "geometry": {
                "type": "LineString",
                "coordinates": w["coordinates"]
            },
            "properties": {
                "id": w["id"],
                "name_en": w["name_en"],
                "name_ta": w["name_ta"],
                "category": "WATERWAY_OVERFLOW_CORRIDOR",
                "waterway_type": w["waterway_type"],
                "breach_vulnerability": w["breach_vulnerability"],
                "upstream_reservoir": w["upstream_reservoir"],
                "color": "#2563EB",  # Blue (Waterway vulnerability)
                "severity": "ELEVATED_VULNERABILITY",
                "safe_buffer_distance_meters": w["safe_buffer_distance_meters"],
                "vulnerable_neighborhoods": ", ".join(w["critical_vulnerable_neighborhoods"]),
                "recommended_action": f"CAUTION: Maintain >{w['safe_buffer_distance_meters']}m clearance during heavy rain or dam release.",
                "source": "GCC_PWD_MAJOR_DRAINAGE_CORRIDORS"
            }
        })
    return features
