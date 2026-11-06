# Offline backend handoff

## Implemented and verified
- Official BRouter commit fcc43926cdd1e577f3dcf5ddf577196f363e190a vendored with MIT license. Java engine compiled using JDK17.
- Kotlin OfflineRouter uses mutable waypoints (BRouter clears/rebuilds the input list). Fixed the original immutable-list bug discovered by real graph testing.
- Complete E80_N10.rd5: 3,007,136 bytes, SHA256 3a519a9580f9b06c027c0ba8852f1a13cfc513e2dee1ab26e542bb000f6c400d. Companion .rd5.json required on device.
- JVM route smoke with real official graph and walking profile:
  - Chennai 13.0827,80.2707 -> 13.075,80.270: 1542m, 40 geometry points, 9 engine turns, 938ms.
  - SRM/Potheri 12.823,80.045 -> 12.831,80.042: 2053m, 49 geometry points, 14 engine turns, 308ms.
- Engine instruction codes checked against upstream VoiceHint.java, including roundabouts and left/right exits. Distances and instructions are engine derived; time estimate uses constant walking4.5km/h, not live traffic.
- RoutingPackIntegrity verifies exact expected size and SHA256 from each companion .rd5.json before routing. Unchanged files cached in process. It rejects partial or missing segments. Metadata provides corruption checking, not independently signed publisher authentication.
- OfflinePack.ready also checks every map manifest file/tile exists with exact recorded length; full pack requires both E75 and E80. Partial E80-only routing remains possible inside E80 coverage.
- SavedOfflineRoute atomically persists actual geometry, turns and destination to app-private storage. ViewModel restores it paused, with fresh GPS required to resume. No fabricated saved directions.

## Pending / boundaries
- E75_N10.rd5 (57,594,201 bytes) downloading via resumable8KB TLS ranges. Do not copy its partial chunks to device or claim full coverage until complete manifest appears.
- Map readiness verifies sizes; routing segments additionally verify digests. Profiles are packaged local files; no server call required.
- Device build, physical walk, freshGPS follow, reroute and offline coldstart persistence are root's device validation; JVM tests alone do not establish these.
- Foot profile permits steps; it is walking routing, not verified wheelchair routing. OSM access and path completeness may be incomplete.
- Backend route smoke runner: data/offline-routing/RoutingSmoke.java; compiled classes in jvm-classes.
- Root/location agent owns subsequent ViewModel foreground-location integration. UI files untouched by backend agent.

## Download integrity
scripts/download_brouter_ranges.py accepts only206 with exact Content-Range and expected byte count; If-Range pins originETag. Each resumed chunks directory now records source.json and rejects changed origin versions. Current live process uses12workers; future invocations allow BROUTER_DOWNLOAD_WORKERS. Do not run overlapping writers. Full file is atomically assembled and checksum recorded only after all chunks present.
