"""Curated Elevated Safe Corridors & Flyovers in Chennai.

During heavy rains and localized flooding, ground-level junctions and subways submerge,
while elevated flyovers, overbridges, and grade separators provide dry, passable
high-ground routes.
"""

from typing import List, Dict, Any

CHENNAI_ELEVATED_FLYOVERS: List[Dict[str, Any]] = [
    {
        "id": "flyover_kathipara_01",
        "name_en": "Kathipara Grade Separator",
        "name_ta": "கத்திப்பாரா சந்திப்பு மேம்பாலம்",
        "locality": "Guindy / Alandur",
        "coordinates": [80.2038, 13.0076],
        "type": "MULTI_TIER_GRADE_SEPARATOR",
        "recommended_action": "RECOMMENDED: Bypass flooded Guindy roundabout & GST ground level via upper decks",
        "connecting_corridors": "Mount Road (Anna Salai) <-> GST Road <-> Poonamallee Rd <-> Inner Ring Rd"
    },
    {
        "id": "flyover_gemini_anna_02",
        "name_en": "Gemini (Anna) Flyover",
        "name_ta": "ஜெமினி (அண்ணா) மேம்பாலம்",
        "locality": "Teynampet / Nungambakkam",
        "coordinates": [80.2520, 13.0515],
        "type": "ARTERIAL_FLYOVER",
        "recommended_action": "RECOMMENDED: Bypass Cathedral Road / Anna Salai ground water stagnation",
        "connecting_corridors": "Anna Salai <-> Cathedral Road"
    },
    {
        "id": "flyover_koyambedu_03",
        "name_en": "Koyambedu Grade Separator",
        "name_ta": "கோயம்பேடு சந்திப்பு மேம்பாலம்",
        "locality": "Koyambedu",
        "coordinates": [80.1945, 13.0694],
        "type": "GRADE_SEPARATOR",
        "recommended_action": "RECOMMENDED: Bypass heavily waterlogged Koyambedu market ground junction",
        "connecting_corridors": "Jawaharlal Nehru Road (100 Feet Rd) <-> Poonamallee High Road"
    },
    {
        "id": "flyover_padi_04",
        "name_en": "Padi Grade Separator",
        "name_ta": "பாடி சந்திப்பு மேம்பாலம்",
        "locality": "Padi / Villivakkam",
        "coordinates": [80.1873, 13.0991],
        "type": "ROTARY_GRADE_SEPARATOR",
        "recommended_action": "RECOMMENDED: Bypass flooded Padi / CTH ground level and rail subway approach",
        "connecting_corridors": "Inner Ring Road <-> Chennai-Tiruvallur High Road"
    },
    {
        "id": "flyover_porur_05",
        "name_en": "Porur Flyover",
        "name_ta": "போரூர் மேம்பாலம்",
        "locality": "Porur",
        "coordinates": [80.1558, 13.0336],
        "type": "ARTERIAL_FLYOVER",
        "recommended_action": "RECOMMENDED: Bypass Porur lake overflow at ground level",
        "connecting_corridors": "Mount-Poonamallee Road <-> Kundrathur Main Road"
    },
    {
        "id": "flyover_usman_road_06",
        "name_en": "South Usman Road Flyover",
        "name_ta": "தெற்கு உஸ்மான் சாலை மேம்பாலம்",
        "locality": "T. Nagar",
        "coordinates": [80.2335, 13.0401],
        "type": "URBAN_FLYOVER",
        "recommended_action": "RECOMMENDED: High-ground bypass over T. Nagar shopping district flooding",
        "connecting_corridors": "CIT Nagar <-> Panagal Park"
    },
    {
        "id": "flyover_north_usman_07",
        "name_en": "North Usman Road Flyover",
        "name_ta": "வடக்கு உஸ்மான் சாலை மேம்பாலம்",
        "locality": "T. Nagar / Kodambakkam",
        "coordinates": [80.2310, 13.0482],
        "type": "URBAN_FLYOVER",
        "recommended_action": "RECOMMENDED: Bypass Madley & Duraisamy submerged subways",
        "connecting_corridors": "Mahalingapuram <-> Panagal Park"
    },
    {
        "id": "flyover_medavakkam_08",
        "name_en": "Medavakkam Flyover",
        "name_ta": "மேடவாக்கம் மேம்பாலம்",
        "locality": "Medavakkam",
        "coordinates": [80.1895, 12.9231],
        "type": "MULTI_TIER_FLYOVER",
        "recommended_action": "RECOMMENDED: Bypass prone water stagnation at Medavakkam junction",
        "connecting_corridors": "Velachery Main Road <-> Tambaram-Velachery Road"
    },
    {
        "id": "flyover_velachery_bypass_09",
        "name_en": "Velachery Bypass Flyover",
        "name_ta": "வேளச்சேரி பைபாஸ் மேம்பாலம்",
        "locality": "Velachery",
        "coordinates": [80.2223, 12.9785],
        "type": "GRADE_SEPARATOR",
        "recommended_action": "RECOMMENDED: High elevated passage across Velachery marsh lowlands",
        "connecting_corridors": "Velachery Bypass <-> Taramani Link Road"
    },
    {
        "id": "flyover_retteri_10",
        "name_en": "Retteri Flyover",
        "name_ta": "ரெட்டேரி மேம்பாலம்",
        "locality": "Kolathur",
        "coordinates": [80.2081, 13.1254],
        "type": "DUAL_FLYOVER",
        "recommended_action": "RECOMMENDED: Bypass Retteri Lake surplus runoff at ground junction",
        "connecting_corridors": "200 Feet Inner Ring Road <-> Kolathur Main Road"
    },
    {
        "id": "flyover_thirumangalam_11",
        "name_en": "Thirumangalam Flyover",
        "name_ta": "திருமங்கலம் மேம்பாலம்",
        "locality": "Anna Nagar West",
        "coordinates": [80.1983, 13.0854],
        "type": "URBAN_FLYOVER",
        "recommended_action": "RECOMMENDED: Bypass Thirumangalam signal junction",
        "connecting_corridors": "Jawaharlal Nehru Road <-> 2nd Avenue"
    },
    {
        "id": "flyover_kodambakkam_rob_12",
        "name_en": "Kodambakkam Railway Overbridge",
        "name_ta": "கோடம்பாக்கம் ரயில்வே மேம்பாலம்",
        "locality": "Kodambakkam",
        "coordinates": [80.2259, 13.0519],
        "type": "RAILWAY_OVERBRIDGE",
        "recommended_action": "RECOMMENDED: Essential high rail crossing avoiding Rangarajapuram subway",
        "connecting_corridors": "Arcot Road <-> Habibullah Road"
    },
    {
        "id": "flyover_saidapet_bridge_13",
        "name_en": "Saidapet Maraimalai Adigal Bridge",
        "name_ta": "சைதாப்பேட்டை மறைமலை அடிகள் பாலம்",
        "locality": "Saidapet",
        "coordinates": [80.2217, 13.0189],
        "type": "RIVER_CROSSING_BRIDGE",
        "recommended_action": "RECOMMENDED: High arterial bridge traversing Adyar River above flood levels",
        "connecting_corridors": "Anna Salai (Saidapet <-> Guindy)"
    },
    {
        "id": "flyover_kotturpuram_14",
        "name_en": "Kotturpuram High Bridge",
        "name_ta": "கோட்டூர்புரம் உயர்மட்டப் பாலம்",
        "locality": "Kotturpuram",
        "coordinates": [80.2443, 13.0182],
        "type": "RIVER_CROSSING_BRIDGE",
        "recommended_action": "RECOMMENDED: High crossing over Adyar River between Nandanam and Adyar",
        "connecting_corridors": "Cenotaph Road <-> Gandhi Mandapam Road"
    },
    {
        "id": "flyover_perambur_rob_15",
        "name_en": "Perambur Railway Flyover",
        "name_ta": "பெரம்பூர் ரயில்வே மேம்பாலம்",
        "locality": "Perambur",
        "coordinates": [80.2458, 13.1077],
        "type": "RAILWAY_OVERBRIDGE",
        "recommended_action": "RECOMMENDED: Bypass completely flooded Ganesapuram & Stanley subways",
        "connecting_corridors": "Perambur Barracks Road <-> Madhavaram High Road"
    },
    {
        "id": "flyover_chromepet_radha_16",
        "name_en": "Chromepet - Radha Nagar Flyover",
        "name_ta": "குரோம்பேட்டை ராதா நகர் மேம்பாலம்",
        "locality": "Chromepet",
        "coordinates": [80.1415, 12.9515],
        "type": "RAILWAY_OVERBRIDGE",
        "recommended_action": "RECOMMENDED: Avoid inundated Chromepet subway under railway tracks",
        "connecting_corridors": "GST Road <-> Radha Nagar / Hasthinapuram"
    },
    {
        "id": "flyover_pallavaram_17",
        "name_en": "Pallavaram Flyover",
        "name_ta": "பல்லாவரம் மேம்பாலம்",
        "locality": "Pallavaram",
        "coordinates": [80.1528, 12.9675],
        "type": "ARTERIAL_FLYOVER",
        "recommended_action": "RECOMMENDED: Elevated high route bypassing Pallavaram market waterlogging",
        "connecting_corridors": "Grand Southern Trunk (GST) Road"
    },
    {
        "id": "flyover_maduravoyal_18",
        "name_en": "Maduravoyal Grade Separator",
        "name_ta": "மதுரவாயல் சந்திப்பு மேம்பாலம்",
        "locality": "Maduravoyal",
        "coordinates": [80.1610, 13.0635],
        "type": "HIGHWAY_INTERCHANGE",
        "recommended_action": "RECOMMENDED: Elevated bypass over Cooum River flood basin edge",
        "connecting_corridors": "Poonamallee High Road <-> Chennai Bypass Expressway"
    }
]

def load_elevated_flyovers() -> List[Dict[str, Any]]:
    """Return curated elevated safe flyovers as GeoJSON features."""
    features = []
    for f in CHENNAI_ELEVATED_FLYOVERS:
        features.append({
            "type": "Feature",
            "geometry": {
                "type": "Point",
                "coordinates": f["coordinates"]
            },
            "properties": {
                "id": f["id"],
                "name_en": f["name_en"],
                "name_ta": f["name_ta"],
                "category": "ELEVATED_SAFE_CORRIDOR",
                "locality": f["locality"],
                "type": f["type"],
                "severity": "SAFE_HIGH_GROUND",
                "color": "#06B6D4",  # Cyan (High ground safe corridor)
                "is_elevated": True,
                "is_passable_pedestrian": True,
                "is_passable_vehicle": True,
                "water_depth_estimate_cm": 0,
                "recommended_action": f["recommended_action"],
                "connecting_corridors": f["connecting_corridors"],
                "source": "MANZIL_ELEVATED_NETWORK_REGISTRY"
            }
        })
    return features
