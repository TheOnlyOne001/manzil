# Setup verification after external setup

Read-only verification on 5 November 2026, approximately 21:15-21:20 IST. Earlier environment absence findings are superseded where this document records installed tools. Existing environment documents, Python lockfiles and source scripts were not overwritten. No SDK, APK, model or dependency was installed.

## Confirmed tools and backend

| Check | Observed result |
|---|---|
| SDK location | `C:\Users\shaya\android-sdk` |
| SDK platforms | `android-31`, `android-35`; **android-36 absent** |
| SDK build tools | `34.0.0`, `35.0.0` |
| adb | Android Debug Bridge 1.0.41, platform-tools 37.0.1-15733141 |
| Connected ADB targets | One target in `device` state; serial not printed or recorded |
| Backend environment | Existing `backend\.venv\Scripts\python.exe`, Python 3.13.5 |
| Imports | FastAPI 0.162.0, HTTPX 0.28.1, NetworkX 3.7, Pydantic 2.14.3, Uvicorn 0.54.0 imported successfully |
| Lockfile preservation | SHA-256 before and after checks: `4089b2dbc2d68baf846cb1f344b014cdc8cc8315ef964507ea03213e45a90b8c` |

Imports used the existing venv Python with `-B`; no `uv sync`, dependency resolution or lockfile mutation was performed. The first pass only counted ADB device states. The user subsequently explicitly identified and authorized the connected Redmi Note 10 Pro; the targeted read-only hardware checks below followed that authorization. No app launch, screenshot, hardware inference benchmark or permission change was performed.

## Authorized real-device follow-up

| Property | Measured value |
|---|---|
| Manufacturer/model | Xiaomi `M2101K6P`; user identifies it as Redmi Note 10 Pro |
| Android/API | Android 12 / API31 |
| ABI list | `arm64-v8a,armeabi-v7a,armeabi` |
| Reported SoC | `SM7150` |
| Linux MemTotal | 5,703,548 kB, approximately 5.44 GiB; consistent with a nominal 6GB variant |
| Linux MemAvailable at check | 2,434,128 kB, approximately 2.32 GiB; transient, not an app allocation allowance |
| `/data` storage | 107GB total, 59GB used, 48GB available (device `df -h`) |
| Google Maps | `26.43.00.1002020710` |
| scrcpy | Verified `5.0.1` at `C:\Users\shaya\scrcpy\scrcpy-win64-v5.0.1\scrcpy.exe`; not on current PATH |

scrcpy `--version` completed successfully; no mirror/control session was opened by this verification. Device serials were neither printed nor recorded. This hardware supports trying a real-device loop first, but no local model/voice/capture capability has yet been demonstrated.

The earlier proposed compile/target SDK36 setup is **not installed**. For the first prototype either deliberately choose SDK35-compatible dependencies or install SDK36 when needed; do not claim SDK36 is present. No Android build or APK installation was verified.

## IMD source and fixture verification

The external `scripts/fetch_imd_nowcast.py` uses a different public endpoint from the REST URLs that previously returned 401:

- Host/path: `https://reactjs.imd.gov.in/geoserver/imd/ows`
- WFS version: `1.1.0`
- Request: `GetFeature`, `outputFormat=application/json`, `srsname=EPSG:4326`
- Layers: `imd:NowcastWarningDistrict` and `imd:NowcastWarningStation`
- Additional PDF: `https://mausam.imd.gov.in/chennai/mcdata/nowcast.pdf`

A bounded **one-feature district request independently returned HTTP200 with default TLS verification enabled** at `2026-11-05T15:50:19.376013+00:00` (21:20 IST). Thus the GeoServer availability claim is supported. This does not change the earlier REST API authentication result, establish an SLA or verify unrestricted production redistribution.

Local derived fixtures contain:

| Fixture | Features | Source timing fields |
|---|---:|---|
| `imd_chennai_district_nowcast.geojson` | 1 | Date `2026-11-05`, `toi=1900`, `vupto=2200`, `update_time=2026-11-05T19:04:13Z` |
| `imd_chennai_stations_nowcast.geojson` | 2 | Meenambakkam AP and Nungambakkam; date `2026-11-05`, `toi=1900`, `vupto=2200`, `updated_at=2026-11-05T19:04:48Z` |
| `imd_tamilnadu_district_nowcast.geojson` | 40 | Regional subset selected from upstream `State=TAMILNADU` |

File modification times are around `2026-11-05T15:03:45Z`; these are filesystem timestamps, **not independently recorded retrieval provenance**. Chennai features contain `cat4=4`, `cat7=7`, `Color=2`.

Three adapter issues need resolution before operational use:

1. The external fetch script initially disabled TLS hostname and certificate verification (`check_hostname=False`, `CERT_NONE`). The independent probe succeeded with standard verification. Following review, the parent agent removed those two overrides, preserving the default verified TLS context. The full downloader was not rerun; its remaining source-specific validation issues below are unresolved.
2. Upstream `update_time`/`updated_at` end in `Z`, yet these interpreted-as-UTC timestamps are later than the observed download/verification time. The `toi/vupto` values appear consistent with a local-evening bulletin but their timezone semantics must be checked. Preserve raw values, record fetch time independently and do not silently reinterpret the `Z` field as IST.
3. WFS `Color` mapping must be validated for this particular layer. The script assigns `1=green ... 4=red`; the earlier REST reference used a different ordering. Do not reuse a severity enum across products without a source-specific mapping. The State field also includes Karaikal under `TAMILNADU`; treat it as upstream regional grouping, not an authoritative state-boundary classification.

The script creates a `results` dictionary containing status/hash/retrieval timestamps but does not persist that dictionary. Derived FeatureCollections also omit a fetch/expiry envelope. These should be added when the adapter is implemented. A saved regional nowcast is not present street-level waterlogging, road passability or offline live data.
