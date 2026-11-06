# Road depth colours, weather proxy and route markers

Implemented 2026-11-06 on Samsung F56.

## Visual design
- Flood history: solid, softly glowing nearby road segments; amber below 10 cm, orange 10–30 cm inclusive, crimson above 30 cm. These are historic survey readings projected onto approximate nearby road segments, not live road-depth measurements. GCC KML schema explicitly identifies inches; convert by 2.54, retaining one decimal. 192 valid values, 12.7–152.4 cm. There are no amber depth samples in the present dataset; none are invented.
- Purple saved hotspots have unknown depth. The compact legend omits 2020 as requested; tap details retain source year. Fetch time does not replace observation year.
- Route origin: oriented blue arrow with Start label. White route chevrons indicate travel direction; navy arrival pin is anchored to the snapped route endpoint. These render through the active SDK projection on both basemaps. Start is not a fake GPS position.
- Modelled flood risk: separate layer with watch/elevated/high bands. Rainfall proxy uses a single Chennai forecast grid point and historic susceptibility; thresholds are uncalibrated and confidence low. No claimed current depth, probability, passability or dry guarantee.
- Elevated roads & transit: existing mapped/curated locations; access and conditions unknown. This is context, not a verified alternative-route engine.

## Weather pipeline
Open-Meteo hourly amounts cover the preceding hour. Sum past three and next three hours; rain tiers at 5/15/35 mm, hourly burst 15 mm forces tier >=2; historical susceptibility at 15/30 cm. Only rain tier >=1 emits risk features. Scores 1=watch, 2–3=elevated, >=4=high. Forecast data expires within two hours; server caches fetches for ten minutes. Zero features with valid dry weather displays Rain below model trigger, distinguished from unavailable weather. No synthetic weather is installed on the device.

The development server runs on localhost:8011 through USB adb reverse. A hosted HTTPS deployment is still needed for weather refresh when away from this computer. Saved historic layers remain available without that connection.

## Validation
Backend: 20 tests passed including conversion, invalid inputs, forecast windows/timezones, expiry, cache/concurrency, live-data isolation and no dry-flyover guarantee. Android: 57 tests passed plus lint and successful installation. Real Open-Meteo fetch returned 0.4 mm past 3 h + 0.9 mm next 3 h, below trigger; positive risk scenarios were tested in unit fixtures, not shown as live observations.

Device screenshots: depth-markers-unlocked.png, depth-roads-google-final.png, depth-record-tap.png (33.0 cm historic), risk-live-no-trigger.png, elevated-layer-check.png. Outdoor GPS walking remains pending by user choice.

Final network fix: onLost now emits offline directly instead of synchronously querying possibly stale default-network capabilities. Final depth-offline-verified.png shows MapLibre with matching orange/crimson road colours in airplane mode; depth-online-restored.png records restoration. Connection settings restored. Final build: docs/depth-network-final-build.log, 57 Android tests, lint, install successful.
