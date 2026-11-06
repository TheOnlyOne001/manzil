# Native maps and evidence overlays

Google Maps SDK 20.1.0 is selected with validated internet, configured Android key and Play Services. MapLibre remains visible until Google calls map-loaded; a 15-second load failure leaves the local map available. Loss of validated internet restores MapLibre. Destination, local route geometry, GPS source, camera and selected overlay are shared. BRouter remains the walking route engine even with Google visible; the Maps SDK is not a Routes API subscription.

Credentials are read from ignored `android/local.properties` (`MAPS_API_KEY`) or `MANZIL_GOOGLE_MAPS_API_KEY`. Never add them to documentation. Restrict the Google key to Maps SDK for Android and Android package `app.manzil`, debug certificate SHA-1 `33:B3:0E:B0:0E:F7:12:82:3E:7A:57:34:70:05:A7:78:6E:82:D8:73`. Release signing needs its own restriction.

## Overlay presentation

One Layers selector: Map only, Flood history, Recent reports, Transit & structures. Amber dots and dashed, bounded nearby OSM road context identify historical observations; they are not a flood-depth heatmap or confirmed flood extent. Coral is reserved for timestamped, unexpired citizen reports. Teal infrastructure never implies safe or open. A 950ms one-shot fade respects system reduced-motion settings. Tap a mark for a short evidence label. The geographic projection comes from the active embedded SDK, never guessed from another app's pixels.

Data comes exclusively from `/api/v1/map-overlays/chennai`, not legacy synthesized hazard endpoints. The offline normalized snapshot is bundled and SHA-256 verified by the pack installer. Reports expire locally even when offline. No current reports is a valid empty state. Unknown flood conditions are not rendered green.

`MANZIL_OVERLAY_API_URL` in ignored local.properties configures a backend base URL. Development uses `http://127.0.0.1:8011` with `adb reverse tcp:8011 tcp:8011`; this requires the connected workstation. Production requires a reachable HTTPS deployment. Cached/bundled layers remain available when the development backend is unreachable.

Mappls assessment and official documentation links: MAP_PROVIDER_COMPARISON.md. Supplied Mappls configs remain untouched in their original directory. They have not been authenticated in this application; SDK/API entitlements cannot be inferred from their opaque binary contents.

## Device verification (6 November 2026)

- Samsung SM-E566B: Google SDK initialized with latest renderer, then returned INVALID_ARGUMENT / Authorization failure for the supplied demo key. Secret value omitted from evidence/docs. App retained MapLibre after failed load. Successful Google tile rendering and a loaded-Google-to-offline switch remain unverified until key authorization is corrected.
- Local development endpoint returned HTTP200/304 features; phone fetched it through ADB reverse, confirmed by backend access log. A fresh endpoint response is not a fresh flood observation.
- Layers menu, historical-point tap tooltip/accessibility description and compact route bar verified on device.
- Airplane1/Wi-Fi0/mobiledata0: map and historical overlay remained visible; reports layer showed No fresh reports available. Original connection settings restored.
- 55 Android tests pass, six backend tests pass. Final polish build log: docs/maps-overlay-polish-build.log.
- Evidence: android-evidence/flood-history-overview.png, hazard-tap-label.xml, hazard-offline-retained.xml, reports-empty-offline.xml. Initial overview roads were too thick; zoom-scaled widths added in polish build.

Mappls `.a.conf`/`.a.olf` remain in supplied folder and were not copied into this APK. Its predictive routing and Nearby Reports are candidates, not verified enabled APIs. See provider assessment for official source links and package/SHA256 setup requirements.

Final visual review: android-evidence/layers-closeup-final.png shows dashed amber road-context segments aligned with T. Nagar streets (including around Doraiswamy Subway), historical point markers and compact route bar. The broad map uses zoom-scaled thinner roads. Road-context geometry is approximate association, not measured flood extent.


## 2026-11-06 integration revision

The replacement project key renders real Google tiles on the Samsung F56. Older authorization-failure notes above refer to the previous key. Secrets remain in local.properties.

The universal action is now **Find walking route**. Both basemaps use the same on-device BRouter walking route, local destination search and saved route state. Google Maps SDK is the online renderer; this build does not call Google Routes or Places for journey planning. Provider changes wait for Google tiles to load, and ignore callbacks from superseded map instances.

Flood history encodes provenance, not invented current severity: amber circles = 192 GCC survey locations (date unknown), purple diamonds = 53 historical GCC hotspots (2020). Matching dashed road accents indicate nearby OSM road context, not measured flood extent. Tap labels retain the historical qualifier. There are 242 approximate road associations. Recent reports and infrastructure remain separate layers; no road passability or current depth is inferred from historical data.

### Device checks

Samsung SM-E566B, 2026-11-06: Google tiles rendered; active-process log contained zero authorization failures or fatal exceptions. A manually selected real map start to GST Food Street produced a 0.34 km walking route. With airplane mode enabled and Wi-Fi/data disabled, MapLibre became active and on-device recalculation succeeded. Restoring connectivity returned to Google with the route state retained. Both colours/shapes and a survey tap label were inspected on Google. Offline historical layers remained available.

Visual inspection found and corrected two fallback issues: the first indoor GPS fix must not recenter an existing route/explicit camera; Google 256px versus MapLibre 512px tile zooms require a one-level conversion. Historical colour indicates dataset provenance, not water depth or current severity.

Evidence: android-evidence/google-history-roads.png, google-history-tap.png, switch-history-restored.png, final-offline-recalculated.png. Builds run assembleDebug, testDebugUnitTest (55 tests), lintDebug and install on the Samsung. Outdoor walking/progress/rerouting remains untested by user choice.

Final scale-conversion build: docs/seamless-scale-build.log passed build, 55 unit tests and lint; installed successfully. Final paired screenshots: verified-google-route.png, verified-local-route.png, verified-google-restored.png. Route retained at matching scale; indoor fix no longer steals camera. Airplane/Wi-Fi/mobile data restored to 0/1/1.
