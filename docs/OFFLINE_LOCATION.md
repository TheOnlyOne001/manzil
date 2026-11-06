# Offline GPS and walking progress

Implementation package: `app.manzil.offline.location`. Network-free; no Play Services, geocoder or web calls.

## Integration

`OfflineGpsSource(context)` owns the Android GPS provider subscription. Call `start()` and `stop()` on the main thread. Collect `state: StateFlow<GpsState>`.

Declare `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` in the manifest and request both together at runtime. `PERMISSION_REQUIRED` means precise permission is missing. `PROVIDER_DISABLED` means the device location/GPS toggle is off. `WAITING_FOR_FIX` is not a position estimate. Fresh GPS callbacks produce `FIX`; a one-second timer changes a fix older than 15 seconds to `STALE` without fabricating motion. `STOPPED` marks lifecycle stop. Errors are readable and no network fallback is attempted.

`GpsFix` includes position, horizontal accuracy, optional altitude/bearing/speed, wall-clock source time, elapsed-realtime source timestamp and a `cached` flag. `getLastKnownLocation` is exposed only as cached and never treated as a fresh callback. Call `fix.forNavigation()` to produce a pure `NavigationFix`. Do not display cached/stale positions as a moving live blue dot.

The shared `OfflineLocationSession.get(context)` now owns the source. Activity `onStart` calls `acquirePreview()`, `onStop` calls `releasePreview()`, and a runtime permission result calls `refresh()`. Never call the shared `gps.stop()` directly: the foreground service may still own GPS. Ownership is reference-counted across visible Activity transitions with a separate service lease.

`OfflineNavigationLocationService.start(context): Result<Unit>` must be called only from an explicit Start walking action while the Activity is resumed. It declares foreground service type location with `FOREGROUND_SERVICE_LOCATION`, promotes itself before acquiring GPS, uses a persistent low-importance notification, and exposes `running: StateFlow<Boolean>`. Notification Stop guidance and `stop(context)` release the service lease. A visible Activity retains preview location until it stops. No all-the-time location permission, boot receiver or START_STICKY restoration is used.

The ViewModel uses shared GPS and waits for service running confirmation before marking guidance active. Start guidance requires a new noncached <=15s-old fix with <=30m accuracy. Selecting a new destination, changing origin or ending guidance stops the service. A notification stop is reflected back into navigation state. Route progress/rerouting remains in the retained ViewModel coroutine: locking the screen or backgrounding preserves it; explicitly finishing the Activity clears its ViewModel and stops the service. Process death ends active guidance; a saved route reopens paused. This is deliberately not a process-death autonomous navigation restoration mechanism.

Actual screen-lock device verification is still required; the ownership unit tests are not evidence of OEM background behaviour or GPS reception indoors.

## Route matching

Construct a new `RouteProgressTracker(points: List<RoutePoint>)` for each calculated/rerouted route. BRouter's `RouteCoordinate` maps directly by latitude/longitude. Feed each GPS callback using `update(fix.forNavigation(), SystemClock.elapsedRealtime())`. The return includes quality, snapped location, segment index, distance traveled/remaining, cross-track distance and `shouldReroute`.

- Only noncached fixes <=15 seconds old and <=30m accuracy qualify.
- Three distinct accurate fresh fixes beyond `max(30m, 2×accuracy)` trigger off-route. Duplicate callbacks do not count. A long gap or bad fix resets hysteresis.
- Continuity restricts progress jumps near parallel sections/crossings. Suspicious teleports become `UNCERTAIN_MATCH`, not arrival or arbitrary advancement. Backtracking is allowed when plausible; progress is not forcibly monotonic because that would conceal walking the wrong way.
- Arrival requires two independent fixes near the terminal section, <=20m route remaining, and endpoint distance plus accuracy <=25m.
- Invoke `invalidateFix()` when the GPS stream changes to stale/disabled/stopped. It preserves the last progress for context but cannot trigger rerouting.
- `shouldReroute` stays true while off-route. Caller must allow only one routing job, debounce retries and reset tracker after successful route replacement. Do not recalculate every second.

Distances use haversine geometry; projection is local equirectangular per road segment. This is route matching, not full road-network map matching. It cannot detect road level, sidewalk side, safe crossings, entrances or physical passability. Geometry tests do not constitute a walking-road validation.

## Verification

Seven pure JVM tests cover distance/progress, stale/cached/poor-accuracy rejection, off-route hysteresis and duplicate handling, long gaps, parallel route sections, arrival and suspicious jumps. Root should include these in the regular Gradle unit-test gate and test actual GPS fixes in airplane mode on the Samsung.
