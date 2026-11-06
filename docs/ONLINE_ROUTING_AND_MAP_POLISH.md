# Online routing and Night Walk polish

Online route requests now use Google Routes computeRoutes (WALK), directly from Android over HTTPS. Google success does not start BRouter. A validated network and configured manifest key are required. A 10-second wall-clock timeout bounds the online attempt; errors fall back to the installed Chennai routing graph only within coverage. Cancellation never starts a fallback. Route source and warnings are preserved with backward-compatible saved-route decoding.

Night Walk preserves geographic gold lighting strokes. Support POIs are filtered to within 400 metres of the route or location (destination when location is absent). Selection runs on Dispatchers.Default, independently of drawing. Below local zoom, POIs are hidden. At local zoom, each viewport shows at most six spaced markers, with no more than two fuel stations and three of any other category. Opening hours and current lighting remain unverified.

The map header and legend are shorter. The collapsed route strip shows duration in hours/minutes and distance. Expanded controls have a Hide action. Google position markers are no longer recreated on unrelated Compose updates. Route details identify the routing provider.

## Verification
Build, lint and 76 JVM tests passed (`docs/online-routing-final-build.log`). Installed on Samsung SM-E566B.

Real current manual-preview journey to Marina Beach Road: online request returned `google_routes`, 39,064 m, 32,089 seconds, 1,247 geometry points and 30 turns in 431 ms. With airplane mode enabled and both Wi-Fi/data off, the map switched to MapLibre; Recalculate returned `offline_brouter`, 38,455 m, 30,764 seconds in 1,691 ms. Restored airplane=0, Wi-Fi=1, mobile-data=1 and recalculated online again (334 ms, Google source). These timings are individual observed runs, not benchmarks. Visual screenshots show the reduced marker clutter and compact route strip. API-error fallback and cancellation are unit-tested; airplane-mode offline routing was exercised on device. Outdoor GPS walking remains deferred at the user's request.

Close-up visual QA confirmed the sparse route-adjacent support icons. Markers avoid the header, legend and bottom journey controls. Tap titles now use road names with an ellipsis instead of duplicating the lighting-status prefix.
