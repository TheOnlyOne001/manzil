# Offline navigation verification — Samsung F56

Device: Samsung SM-E566B, ADB RZGL40EETBZ, Android 16. Testing began 2026-11-05, continued 2026-11-06 (India).

## Observed on the actual phone

- Native road map and local labels render after moving the pack to app-owned internal storage. Log: 2,246 road features in the SRM viewport. External ADB-created storage was visible to shell but returned no files in the running app; internal storage resolved the symptom.
- Local search found SRM Potheri Station Stop from the downloaded index.
- Set airplane mode=1, Wi-Fi=0, mobile data=0; selected a real map point (12.823743910617083, 80.04393188488643) as a **manual preview start**.
- Phone calculated a **new 1.01 km walking route** to SRM Potheri Station Stop in **306 ms**, drawing real BRouter geometry on the native map. No remote route service or prerecorded route supplied this result.
- Force-stopped and relaunched the application while still offline. Saved geometry/destination reopened, labelled “Saved offline route / waiting for fresh GPS”.
- Restored original connection settings: airplane mode=0, Wi-Fi=1, mobile data=1.
- Evidence: android-evidence/offline-route-calculated.png, offline-route-evidence.xml, offline-cold-start-route.png, offline-cold-start-details.xml, offline-network-before.json.

## Other bounded evidence

- JVM real-graph smoke: Chennai 1,542m /40 points/9 turns; Potheri 2,053m/49 points/14 turns.
- Final Android suite: **53 tests passed**, including real routes in eastern Potheri, western Chengalpattu, and across the E75/E80 boundary at Veppampattu. Integrity tests reject truncated and same-length corrupted graphs. Includes route progress/GPS ownership; these are not a physical walking test. Full build and lint passed (docs/offline-full-pack-build.log).
- Segment integrity: size and SHA-256 checked against downloaded manifests.

## Still pending; goal is not complete

- Full pack is COMPLETE and installed: 371 files / 102,903,450 bytes. All 371 on-device SHA-256 hashes match the build index (android-evidence/full-pack-device-hashes.json). E75 hash c1882bb73306f8a661b427a21ad60ede4cd54107f3cc8753475b2f45bd4a8ee2. Native map bounds: west79.95/south12.65/east80.35/north13.30. Final APK: android/app/build/outputs/apk/debug/app-debug.apk (121,756,418 bytes).
- Bundled pack self-installation PASSED: moved prior internal pack aside, disabled Wi-Fi/data with airplane mode, launched APK; it recreated app-owned map/search/graph and calculated a new 0.83km route. E80 on-device SHA-256 matched source. Preview-start provenance survived another offline cold start. Evidence: offline-self-installed.png, offline-bundled-new-route.xml, offline-preview-cold-start.xml. Connectivity restored afterward.
- Fresh accurate GPS without internet. User chose to continue indoors; recent GNSS fixes are stale or inaccurate. No mock GPS has been substituted.
- Physical route following and automatic off-route recalculation. Geometry/unit tests do not prove a real walk.
- Foreground service and screen-lock lifecycle on actual active GPS guidance.
- After user unlocked Samsung, western-route device test PASSED with airplane1/Wi-Fi0/data0: new 0.60km route from map-picked (12.695017304502016,79.97868723831454) to Old Bus stand in Chengalpattu, computed in202ms. Real saved turns displayed in details sheet. Evidence offline-west-route.png and offline-west-turns.xml.
- GPS-start attempt to SRM Potheri Station Stop correctly withheld routing: active permitted GPS provider request at1s interval confirmed by Android, last fix approximately75m accuracy and stale. No actual walk or injected location. User asked whether open-sky testing is now possible. Network settings restored afterward.
- Current device connectivity verified restored: airplane_mode_on=0, wifi_on=1, mobile_data=1.
- Previous pack retained as files/manzil.prebundle-20261106 inside app storage for reversible self-install test; current pack is files/manzil. Saved journey/Maps companion state was preserved.

## Final companion check and pending physical evidence

On the final installed APK, Maps companion header action opened the preserved notification companion with its original Intra College Rd observation correctly labelled saved/not current. System Back returned to the offline screen with SRM Potheri Station Stop selected. Evidence companion-preserved.xml, companion-return-offline.xml.

User explicitly chose “Leave the outdoor test pending”. Do not repeatedly request an outdoor walk. Fresh accurate satellite fix, actual progress, active-guidance screen-lock and automatic rerouting on a real deviation remain unverified. All other completed evidence above remains bounded; goal is not achieved.
