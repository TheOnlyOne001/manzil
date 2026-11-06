# Chennai map overlay backend handoff

## Frontend contract
GET `/api/v1/map-overlays/chennai` responds `application/geo+json`, Cache-Control:no-store. Offline snapshot: `data/derived/chennai_map_overlays.geojson`.

Every point has properties `id`, `title`, `layer`, `evidence`, `detail`, `source`, `observed_at`, `expires_at`. IDs unique/stable within the source files; original survey F_ID repeats in11 records, so record-index suffixes disambiguate them without dropping records.

Layers delivered now:
- history/historical:192 GCC survey points and53 GCC/OpenCity2020 hotspots. Historical, never presented as currently flooded. Survey observation dates unknown:null.
- infrastructure/curated:18 approximate manually curated flyover points and41 unique CMRL station points parsed directly from static GTFS. No claim of dry decks, pedestrian access, flood immunity, operating services or shelter capacity.
- reports/reported:only unexpired valid-timestamp commuter submissions, explicitly unverified. Future timestamps, nonfinite values, invalid coordinates, test/demo/mock/synthetic/simulated records rejected. TTL capped at2hours. Current snapshot contains0 reports; existing test_redmi_commuter_01 was excluded.
- weather/forecast:reserved; no weather feature currently emitted. Rain does not establish current street flooding or water depth.

## Road context visualization
242 of245 history points carry `properties.road_context`:
`{type:"LineString",coordinates:[[lon,lat],[lon,lat]],association_distance_m:0..35,approximate:true,detail,source,road_name:null|string}`.

This is an actual nearby OSM simplified display-road segment. The point-to-segment association is at most35m; the line is clipped to at most120m (60m around closest projection, bounded by segment endpoints). Render a thin amber contextual glow on selection, while retaining the original history point. It is NOT an observed inundation extent and must not imply the road currently floods. Some points may associate to the wrong parallel road; no routing restriction is generated.

Association cache: `data/derived/chennai_historical_road_context.json`; matching implementation:`backend/app/road_context.py`. Source:`data/offline-map/tiles/*.geojson`. Map attribution OpenStreetMap/ODbL applies. The live endpoint reads this cache and small local sources, with no OSM/weather requests.

## Excluded unsafe synthesis
The endpoint does not normalize from latest_chennai_hazards.geojson. It whitelists original evidence from local KML/GeoJSON/GTFS and curatedpoints. Excluded: fabricated rainfall-to-road depth, severity/passability calculations, guessed shelter-centroid/jitter coordinates, invented waterway lines, claimed safe flyover detours, flood-immune metro stations. No `water_depth_estimate_cm`, `is_passable_*`, or `recommended_action` fields are exported.

## Startup and integration
Backend startup now generates only this evidence-labelled local snapshot; legacy synthesized hazard feeds are loaded lazily only if their old endpoints are called. Android should use the new endpoint exclusively. Old hazard endpoints still exist and have NOT been corrected: do not use them for current waterlogging/safety UI. Restart existing uvicorn process to load the new route. No Android files, builds or device actions performed for this feature.

Offline clients must hide expired reports using expires_at even when reading a saved snapshot. History/infrastructure stay visible as history/context, not live conditions. Dataset download time and response generated_at are not observation timestamps.

## Verification
`cd backend; .venv/Scripts/python.exe -m unittest discover -s tests -p test_map_overlays.py -q`
6tests passed: report future/expiry/test/coordinate rejection; realcounts and fieldwhitelist/uniqueIDs; all cachedroad distances/lengths; synthetic near/far association correctness; atomic snapshot roundtrip; real FastAPI startup+endpoint with legacyweather feed mocked to fail ifcalled.

Current result:304 points;245 history;59 infrastructure;242 optional road contexts;0 active reports. Snapshot regeneration is atomic. No physical field verification or live flood detection is claimed.
