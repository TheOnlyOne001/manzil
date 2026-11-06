# Night Walk visual layer
Implemented 2026-11-06. This is a map visualisation, not night-aware rerouting.

Select Layers > Night Walk. Dark navy basemaps on Google and MapLibre; gold multi-stroke glow for explicitly mapped lit roads, muted dashed blue-grey unknown roads, dusty rose mapped-unlit roads, teal vector support-place symbols. One 1.1-second reveal honours reduced-motion settings. Explore lighting selects the nearest mapped-lit feature; destination and blue route remain unchanged. Tap shows evidence. Support locations are not asserted open/staffed.

Data: 3,000 bounded features, 966 mapped-lit, 53 explicitly unlit, 390 selected unknown and 1,591 support places from OSM. Actual mapped geometries, simplified within 4m; never manufactured light positions. Unknown layer is selected context, not complete city coverage. Backend excludes prohibited/private ways per its tags. The source is still incomplete and cannot establish a route is safe. Source fetch and provenance in chennai_night_context.geojson metadata. Bundled locally and merged into live overlay endpoint.

Validation: 33 backend tests passed; 58 Android tests passed; lint and install successful. Real Samsung screenshots night-google-final.png, night-support-tap.png and night-offline-final.png verify rendered corridors, actual Shell POI label and MapLibre fallback. Connectivity restored afterward. No live walking or night-safety outcome claimed. Google and MapLibre projection differences shift visible screen centre somewhat but layer stays geographically attached.

Files: backend/app/night_context.py, backend/tests/test_night_context.py; Android NightWalkCanvas.kt, MapSurface.kt, AdaptiveMapView.kt, GoogleNativeMapView.kt, OfflineMapView.kt, HazardOverlays.kt, OfflineActivity.kt.
