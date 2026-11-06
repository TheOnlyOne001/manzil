# Manzil Chennai Comprehensive Hazard & Ingestion Pipeline

High-precision real-time waterlogging, subway passability, elevated safe corridors, major waterway overflow buffers, CMRL flood haven stations, GCC emergency relief shelters, and crowdsourced hazard reporting for Chennai.

Provides normalized GeoJSON consumable by both **Google Maps SDK** (online) and **MapLibre Native** (offline).

---

## 1. Architecture & Multi-Layer Ingestion

```
+------------------------------------+       +------------------------------------+
|      IMD Mausam GeoServer WFS      |       |      Open-Meteo Precipitation      |
|  - District Warning (EPSG:4326)    |       |  - Live rainfall rate (mm/hr)      |
|  - AWS Stations (Meenambakkam/Nung)|       |  - Short-term forecast telemetry   |
+-----------------+------------------+       +-----------------+------------------+
                  |                                            |
                  +---------------------+----------------------+
                                        |
+---------------------------------------v-----------------------------------------+
|                              HAZARD SYNTHESIZER ENGINE                          |
|  Aggregates live weather + empirical terrain data + emergency relief layers:    |
|                                                                                 |
|  1. 12 Critical Subways (Madley, Duraisamy, Ganesapuram, etc.)                  |
|  2. 192 Surveyed Road Inundation Depths (Measured 5" to 60" / GCC Field Survey) |
|  3. 18 Elevated Safe Flyovers & Corridors (Kathipara, Gemini, Koyambedu, etc.)  |
|  4. 4 Major Waterways & Breach Buffers (Adyar, Cooum, Buckingham, Otteri)       |
|  5. 41 CMRL Metro Sanctuary Stations (Dry pedestrian crossing & transit hops)   |
|  6. 89 GCC Emergency Relief Centres (Shelters & officer contacts by zone)       |
|  7. 53 GCC Historical Flood Hotspots                                            |
|  8. Live Citizen Crowdsourced Hazard Reports (with 2-hour TTL freshness decay)  |
+---------------------------------------+-----------------------------------------+
                                        |
                                        v
                       Unified GeoJSON FeatureCollection
                       (410+ features across Greater Chennai)
                                        |
                    +-------------------+-------------------+
                    |                                       |
                    v                                       v
        [Google Maps SDK (Online)]              [MapLibre Native (Offline)]
          - GeoJsonLayer overlay                  - GeoJsonSource + Layers
          - Subway bypass alerts                  - Bundled into offline pack
```

---

## 2. API Endpoints

Base URL: `http://localhost:8000` (or `http://10.0.2.2:8000` in Android Emulator, or via `adb reverse tcp:8000 tcp:8000` on physical phone).

| Method | Endpoint | Description | Features Count |
|---|---|---|---|
| `GET` | `/` | API service discovery index | - |
| `GET` | `/api/v1/health` | Service health, cache age, category breakdown | - |
| `GET` | `/api/v1/hazards/chennai` | Complete unified GeoJSON across all layers | **410+** |
| `GET` | `/api/v1/hazards/chennai/subways` | 12 critical subways with passability & alternative routes | 12 |
| `GET` | `/api/v1/hazards/chennai/inundation-points` | 192 surveyed road inundation depth points (inches & cm) | 192 |
| `GET` | `/api/v1/hazards/chennai/flyovers` | 18 elevated safe corridors (high-ground bypasses) | 18 |
| `GET` | `/api/v1/hazards/chennai/waterways` | 4 river/canal overflow breach corridors & buffer zones | 4 |
| `GET` | `/api/v1/hazards/chennai/metro-havens` | 41 CMRL flood sanctuary stations & dry road crossings | 41 |
| `GET` | `/api/v1/hazards/chennai/relief-centres` | 89 GCC emergency relief centres (filter by `?zone=1..15`) | 89 |
| `GET` | `/api/v1/hazards/chennai/hotspots` | 53 GCC historical inundation points | 53 |
| `POST`| `/api/v1/hazards/report` | Submit citizen puddle/hazard observation (JSON) | - |
| `GET` | `/api/v1/hazards/reports/live` | Active crowdsourced citizen reports (< 2h old) | Dynamic |
| `GET` | `/api/v1/weather/nowcast` | Live IMD bulletin & Open-Meteo rainfall rate | - |
| `POST`| `/api/v1/hazards/refresh` | Force evict cache and fetch fresh external feeds | - |

---

## 3. Layer Specifications & Color Scheme

| Category | Color | Hex Code | Utility |
|---|---|---|---|
| **ELEVATED_SAFE_CORRIDOR** | Cyan | `#06B6D4` | High-ground safe flyovers bypassing submerged ground roads |
| **METRO_SANCTUARY_STATION** | Purple | `#8B5CF6` | CMRL stations for dry road crossings & resilient transit |
| **EMERGENCY_RELIEF_CENTRE** | Emerald | `#10B981` | GCC community halls & schools with designated officer phones |
| **WATERWAY_OVERFLOW_CORRIDOR**| Blue | `#2563EB` | Adyar, Cooum, Buckingham Canal buffer zones |
| **SURVEYED_ROAD_INUNDATION** | Amber/Red | `#F59E0B` - `#DC2626` | Empirical road depths (from 5" to 60") with field remarks |
| **CRITICAL_SUBWAY_CHOKEPOINT** | Green/Amber/Red | `#16A34A` / `#F97316` / `#DC2626` | Passability & bypass alternatives based on live rainfall |
| **CROWDSOURCED_HAZARD_REPORT** | Amber/Red | `#F59E0B` - `#DC2626` | Live user reports with auto 2-hour freshness decay |

---

## 4. How to Test & Validate

Run the end-to-end automated test suite:
```powershell
$env:PYTHONPATH="C:\Users\shaya\Desktop\Projects\manzil-research\backend"
.\backend\.venv\Scripts\python.exe backend\tests\test_expanded_pipeline.py
```

Start the local server with auto-reload:
```powershell
.\backend\run_server.ps1
```

Interactive Swagger API docs will be live at:
`http://127.0.0.1:8000/docs`
