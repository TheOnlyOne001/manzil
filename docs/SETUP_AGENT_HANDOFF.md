# Setup-agent handoff: Manzil

## Mission and boundary

Prepare a reproducible local environment for a native Android mobility assistant, one stage at a time. The current request is research/environment preparation; **do not implement the app or choose final feature scope yet**. Do not install multiple alternate stacks. Read `ENVIRONMENT_AUDIT.md`, the API research and `DATA_INVENTORY.md` before work. Use the organizer-supplied network as the authoritative hackathon input when it becomes available; it has not yet been supplied.

Keep all setup artifacts in `C:\Users\shaya\Desktop\Projects\manzil-research` or a deliberately selected sibling app folder. Preserve downloaded original datasets and provenance. Do not silently repair expired calendars or fabricate current GTFS schedules. Stop at an actionable missing credential/data dependency and continue other independent setup work.

## Stage 1: repeat audit and establish pins

```powershell
Set-Location 'C:\Users\shaya\Desktop\Projects\manzil-research'
.\scripts\check_environment.ps1
```

Use a conservative Android baseline rather than arbitrary latest dependencies:

| Component | Proposed pin |
|---|---|
| JDK for Android | Maintained JDK 17 patch release, isolated from system default |
| Android Gradle Plugin | 8.11.1 |
| Gradle wrapper | 8.13 |
| Kotlin Android + Compose compiler plugins | 2.2.21, same version for both |
| compileSdk / targetSdk for local prototype | 36 / 36 |
| minSdk | 26 (design choice; confirm target test device) |
| SDK build-tools | 35.0.0, or AGP-required version after validation |
| Backend Python | Existing Python 3.13 initially; select isolated Python 3.12 only if a required wheel fails |
| OpenTripPlanner, if needed | 2.6.0 shaded JAR + isolated JDK 21 |

This pin set is chosen from documented compatibility ranges: Kotlin 2.2.21 fully supports AGP through 8.11.1 and Gradle through 8.14; AGP 8.11 uses Gradle 8.13. It is **proposed, not build-verified**. Do not upgrade to AGP 9 merely because a newer Studio template suggests it. Resolve and pin a compatible stable Compose BOM, MapLibre AAR, Room/KSP, WorkManager and HTTP client in a version catalog during the first compile smoke test. No dynamic `+` versions. Do not mix Room 2 (`androidx.room`) and Room 3 (`androidx.room3`) APIs from different documentation generations. Consult [Room releases](https://developer.android.com/jetpack/androidx/releases/room) and [Kotlin compatibility](https://kotlinlang.org/docs/gradle-configure-project.html).

## Stage 2: Android tools only

Download official stable [Android Studio](https://developer.android.com/studio) or [command-line tools](https://developer.android.com/tools). Verify official download checksum when provided. The host has ~46 GB free, so install a single platform first. No NDK is required to consume a published MapLibre AAR. Defer emulator/system images until a physical Android device path has been considered.

After the official command-line tools are installed, use the actual SDK path:

```powershell
$manzilSdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$manzilSdkManager = Join-Path $manzilSdk 'cmdline-tools\latest\bin\sdkmanager.bat'
& $manzilSdkManager --sdk_root=$manzilSdk --list
& $manzilSdkManager --sdk_root=$manzilSdk 'platform-tools' 'platforms;android-36' 'build-tools;35.0.0'
& (Join-Path $manzilSdk 'platform-tools\adb.exe') version
```

Use an isolated/session-scoped JDK selection or Gradle toolchain; do not rewrite global PATH/JAVA_HOME. If the installer requires licence acceptance, present the actual terms to the user rather than auto-piping acceptance. After a minimal project is separately authorized, use its Gradle wrapper for `--version`, `assembleDebug` and `lintDebug`. Dependency resolution and APK compilation are the compatibility proof. Do not report emulator/phone success without launching on that device.

## Stage 3: data-adapter Python environment

When adapter scaffolding is authorized, create a separate backend directory and isolated environment:

```powershell
uv init --bare backend
Set-Location backend
uv python pin 3.13
uv add fastapi 'uvicorn[standard]' httpx pydantic networkx
uv sync
uv run python -c "import fastapi,httpx,pydantic,networkx; print('backend imports OK')"
```

Keep `uv.lock`, pin the interpreter, and report resolved versions. These commands do not create a routing API. Add pandas/GeoPandas/Shapely/osmium only if actual source formats require them. Do not add heavy GDAL stacks before a need is established. API responses must carry source, observed-at, fetched-at and expiry fields; saved forecasts are not live observations. Begin with fixture-driven adapters so missing paid keys cannot block setup.

## Stage 4: conditional routing engine

For the supplied hackathon graph, a small deterministic Python solver is the simplest initial path. Implement hard constraints explicitly; a low score must never make a forbidden edge feasible. Time-dependent transit must respect schedules, transfers and walking budgets. A one-weight shortest path is insufficient.

Use OTP only when validated GTFS and routable OSM PBF overlap the intended area and dates. OTP needs PBF, not Overpass JSON or rendered map tiles. A small valid corridor is preferable to building an entire India graph. Obtain OTP 2.6.0 from the [official tutorial's Maven links](https://docs.opentripplanner.org/en/v2.6.0/Basic-Tutorial/) and verify the artifact. Use an explicit JDK21 executable and start with a bounded heap, monitoring memory:

```powershell
# Assign these to the verified downloaded paths; they are placeholders, not existing files.
$manzilJava21 = 'C:\path\to\jdk-21\bin\java.exe'
$manzilOtpJar = 'C:\path\to\otp-2.6.0-shaded.jar'
$manzilOtpInput = 'C:\path\to\validated-small-otp-input'
& $manzilJava21 -version
& $manzilJava21 -Xmx3G -jar $manzilOtpJar --build --save $manzilOtpInput
& $manzilJava21 -Xmx3G -jar $manzilOtpJar --load --port 8801 $manzilOtpInput
```

Input directory must contain only the intended OSM PBF, GTFS ZIP(s) named with `gtfs`, and configuration. Record build logs, graph date range, stop-linking failures and an actual GraphQL journey response for an active service date. Server startup alone does not validate usable routes or accessibility. Keep Docker optional: its engine is currently unreachable.

## Stage 5: map assets and smoke checks

- MapLibre renders the app's own OSM-derived geometry, accessibility markers and weather polygons. Use a tile source explicitly permitting the intended use and offline download. Store style, sprites, glyphs and the selected region together. OSM standard public raster servers are not a bulk/offline source.
- Google map/Routes integration is optional and separate. Obtain user-owned restricted keys through proper configuration; use server-side keys for backend APIs. Do not harvest existing credentials. Do not place Google route content on a non-Google map or persist Google content beyond allowed terms.
- Verify that a small Chennai region renders with attribution, then verify it in airplane mode; a warm on-screen cache is not proof of a saved offline region.
- Persist and reload a sample journey after process termination, with network and location both disabled. Show manual step advancement, source age and expiry.
- Probe device Tamil speech recognition and TTS availability. Never promise offline Tamil solely because the API supports a locale parameter.
- Only after the above, verify a user-initiated location foreground service; WorkManager is for deferred sync, not live navigation. Keep a mock-location source for repeatable tests.

## Completion evidence required

Return a short inventory of installed paths/versions, lockfiles, download checksums, consumed storage, build commands with exit codes, and tested device/SDK version (no serial number needed). Mark unresolved credentials, stale feeds and unavailable APIs separately. Current research is not application implementation. Do not call the environment ready until the promised compile/run and data checks have actually passed.
