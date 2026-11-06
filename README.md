# Manzil Android companion and research

The native app is in `android/` (package `app.manzil`). Its current default is a real Google Maps notification companion, with a minimal panda overlay and locally saved observations. See [Samsung live-run verification](docs/SAMSUNG_LIVE_VERIFICATION.md) for tested behaviour and limitations. The sample journey remains test scaffolding, not the live experience.

Build: `./scripts/build_android.ps1`; install to the connected Samsung F56: `./scripts/build_android.ps1 -Install`.

# Research history

Prepared 5 November 2026. Scope: inspect and download useful Chennai map/transit/reference data, check API access, and prepare a small setup agent's environment brief. The original research scope below predates the Android implementation; the datasets are not production-ready navigation inputs.

## Read in this order

1. [Data inventory](docs/DATA_INVENTORY.md): actual downloads, bounds, counts, source quality and licences.
2. [API and technology catalogue](docs/API_AND_TECHNOLOGY_CATALOG.md): feature-to-tool mapping, provider alternatives, tested vs documented access and data gaps.
3. [Latest setup verification](docs/SETUP_VERIFICATION_2026-11-05.md): installed SDK/backend and connected Redmi; supersedes the initial absence findings.
4. [Setup-agent handoff](docs/SETUP_AGENT_HANDOFF.md): staged commands and acceptance checks.
5. [Reference maps](docs/REFERENCE_MAPS.md): official map documents and historical reference layers, separate from operational routing.

## Verified starting points

- Bounded OSM transport geometry downloaded for 12.65–13.30 N, 79.95–80.35 E; not a complete all-layer city extract or ready offline tile pack.
- Community MTC/CMRL/suburban GTFS and a publicly linked CUMTA-hosted MTC archive downloaded and inspected. Raw sources preserved; schedules/access fields need validation.
- Two official CMRL reference map PDFs downloaded and visually inspected; Phase II planning is not operational status. A historical GCC/OpenCity 2020 flood-hotspot layer was downloaded and converted into 53 GeoJSON points.
- Open-Meteo Chennai forecast returned valid JSON with 72 hourly records.
- IMD district/station nowcasts (Chennai/TN) and official RMC Chennai bulletins were successfully downloaded and extracted via public Mausam GeoServer WFS feeds (HTTP200); the internal developer gateway `api.imd.gov.in` remains token-gated (HTTP401).
- Android CLI SDK at `C:\Users\shaya\android-sdk` now has API31/35 and adb37.0.1; backend imports pass. Redmi Note10 Pro (Android12, nominal6GB RAM) is connected and scrcpy5.0.1 is installed. API36 and Android app compilation remain unverified/absent as detailed in the latest verification.

## Evidence and reproduction

`data/provenance.json` records raw map/transit URL, time, bytes, SHA256 and licence notes. `data/*validation.json` records inspection results. `data/api-probes/manifest.json` records live HTTP probe outcomes. `docs/environment-audit.json` records host audit results.

The collected data, extracted files and previews occupy approximately 272 MB. [Map inspection preview](data/derived/chennai_data_preview.png) shows the road/rail extract alongside the wider transit-stop extent. This visual check does not validate route connectivity.

```powershell
Set-Location 'C:\Users\shaya\Desktop\Projects\manzil-research'
python scripts/probe_public_apis.py
.\scripts\check_environment.ps1
```

Downloader scripts are retained under `scripts/`; read their scope before rerunning large network fetches. Preserve original snapshots instead of overwriting them to make old reports appear current.

## Inputs still required

- Organiser-supplied network, schema and additional round requirements.
- A selected map/traffic provider account if paid or key-gated integrations are desired; none was created.
- Verified current bus/rail service dates, entrance and lift observations, lighting evidence and waterlogging reports for the chosen demo area.
- Final app folder when implementation starts; the Redmi Note10 Pro is the authorised initial test device.

Keep data status explicit: observed, forecast, historical, unknown, synthetic. Do not upgrade absent accessibility tags into a positive claim. Keep provider-derived map content out of incompatible map/cache paths. Do not infer a full navigation-ready environment from these research artifacts.

## Offline-first design pack

The user selected offline companion behaviour as feature1. [Precise flow and design](docs/FEATURE_1_OFFLINE_DESIGN.md), [visual board](design/feature-1-board.png), [voice/model choice](docs/VOICE_AND_AGENT_STACK.md), and [offline feasibility](docs/OFFLINE_FEASIBILITY.md) define the current direction. The departure-planning board is retained as a later-feature study.

[Installed skills](docs/INSTALLED_DESIGN_SKILLS.md): frontend-design, edge-to-edge and jetpack-compose-performance. [Assets](docs/DESIGN_ASSETS.md):32 original Lucide SVGs, Manrope/Noto Sans Tamil fonts with licences, plus an original static panda study. Native asset conversion and real-device rendering still need testing.

## Copyable setup-agent prompt (current)

> Work from `C:\Users\shaya\Desktop\Projects\manzil-research`. Read README.md, docs/ENVIRONMENT_AUDIT.md, docs/DATA_INVENTORY.md, docs/API_AND_TECHNOLOGY_CATALOG.md and docs/SETUP_AGENT_HANDOFF.md. Carry out only the setup stage currently requested by the user. Preserve raw data and provenance; use isolated dependencies and the documented version baseline. Report commands, exit codes, versions and concrete smoke-test results. Keep key-gated adapters disabled until configured; do not create billing/accounts, scrape private transit APIs, invent schedules or claim missing accessibility observations. Do not redesign features. Return only changes, validation results and specific unresolved inputs.
