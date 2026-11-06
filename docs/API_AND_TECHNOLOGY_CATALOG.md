# Manzil: data APIs and feature-to-tool inventory

Research date: 5 November 2026 (Asia/Kolkata). This is preparation, not a final feature specification. No API accounts, subscriptions, billing projects or external messages were created. Credentials were not searched for in unrelated projects.

## Evidence labels

- **TESTED**: a real credential-free request succeeded and its response is saved.
- **AUTH REQUIRED**: public request was rejected; credentials/onboarding required.
- **DOCUMENTED**: primary documentation reviewed, authenticated data call not made.
- **PARTNERSHIP / UNKNOWN**: useful source exists, but no usable public API/access grant established.
- **LOCAL DATA**: downloaded archive; freshness, completeness and rights assessed separately.

Local API evidence: `data/api-probes/manifest.json`; reproduce with `python scripts/probe_public_apis.py`. Map/transit details: `DATA_INVENTORY.md` and its manifests. A successful HTTP response does not establish accuracy or production availability.

## Map display, search and geographic data

| Source/tool | What it provides | Access and current evidence | Fit / limitations |
|---|---|---|---|
| OpenStreetMap / Overpass | Streets, paths, stops, entrances, crossings, some lighting/accessibility tags | LOCAL DATA; bounded query in inventory; ODbL attribution | Best independent geometry source for editable routing and permitted local snapshots. Missing tags mean unknown, not accessible/unlit. Public Overpass is shared research infrastructure, not a production SLA. |
| MapLibre Native Android | Native map rendering, layers, icons, camera, offline regions | DOCUMENTED; renderer library, not a data API | Suggested independent rendering baseline. Need a licensed tile source plus styles, glyphs and sprites. Downloaded OSM JSON is not an offline basemap. |
| Google Maps SDK for Android | Embedded Google basemap, markers, polylines | DOCUMENTED; API key/project setup | Optional Google-backed UI. SDK rendering does not itself provide our accessibility solver or unrestricted offline downloads. |
| Google Places / Geocoding | Place search, place IDs, addresses | DOCUMENTED candidate; project/key/billing; no live call | Optional if using Google stack; apply product-specific attribution and storage terms. Consumer Ask Maps is not a general developer API. |
| Local stop/place index | Search downloaded stop names, aliases, Tamil/English names where available | Build from approved local datasets with SQLite FTS | Credential-free prototype search; coverage limited to dataset. |
| Self-hosted or commercial geocoder | Broader address search | Optional later | Do not default generated app autocomplete to public Nominatim. Its public endpoint forbids autocomplete, caps total app traffic at 1 request/sec and requires identifying User-Agent, attribution and caching. |

Sources: [MapLibre Android](https://maplibre.org/maplibre-native/android/), [offline packs](https://maplibre.org/maplibre-compose/offline/), [Google markers](https://developers.google.com/maps/documentation/android-sdk/marker), [Nominatim policy](https://operations.osmfoundation.org/policies/nominatim/), [OSM copyright](https://www.openstreetmap.org/copyright).

**Map-content boundary:** Google Routes policy restricts caching and requires routes displayed on a map to use a Google map. Keep Google-derived geometry out of the MapLibre offline data path. Standard OSM raster tiles prohibit bulk/offline downloads; use raw extracts to generate owned tiles or a provider explicitly allowing offline packs. [Google policy](https://developers.google.com/maps/documentation/routes/policies), [OSM tile policy](https://operations.osmfoundation.org/policies/tiles/).

## Traffic and departure-time APIs

| Provider | Inputs/outputs worth using | Access | Decision |
|---|---|---|---|
| Google Routes Compute Routes | Origin/destination, `departureTime`, traffic-aware preferences, alternate routes; duration and polyline | DOCUMENTED; billing enabled + key/OAuth. POST `https://routes.googleapis.com/directions/v2:computeRoutes` with explicit field mask | First candidate if retaining Google map UI. Test multiple departures on a few Chennai corridors once a project is provided. This is provider prediction, not our trained model. |
| Google Compute Route Matrix | Travel-time matrix, traffic/transit options subject to request limits | DOCUMENTED; billed per element | Useful for meetup scoring; cap origin x destination x departure combinations. |
| TomTom Routing | `departAt`, `arriveAt`, traffic-aware routes, historic/live travel-time fields | DOCUMENTED; API key/account. `/routing/1/calculateRoute/{locations}/json` | Alternative traffic baseline. India is listed for flow/incidents; exact Chennai coverage must be measured. |
| TomTom Flow / Incidents | Segment speeds, current vs free-flow travel time, incidents | DOCUMENTED; key; latest docs point new integrations toward Orbis APIs | Candidate for segment overlays and bottleneck evidence. Pin API generation before coding; no credentialed validation yet. |
| TomTom Traffic Stats | Historical traffic analysis | DOCUMENTED; separate evaluation/commercial product | Candidate training/evaluation source. Not the same entitlement as routing. India listed, selected-city coverage caveat. |
| Mappls Routing | Traffic-aware routes and ETA, India-oriented routing | DOCUMENTED; developer key/entitlement | India-focused alternative; future departure support and Chennai measurement quality must be checked with a provisioned account. |
| HERE Routing v8 | Time-aware routing with traffic/historical conditions | DOCUMENTED alternative; account/API key | Keep as reserve; no reason to install four vendor SDKs initially. |

Sources: [Google request guide](https://developers.google.com/maps/documentation/routes/compute_route_directions), [traffic](https://developers.google.com/maps/documentation/routes/traffic-opt), [billing](https://developers.google.com/maps/documentation/routes/usage-and-billing), [India price table](https://developers.google.com/maps/billing-and-pricing/pricing-india), [TomTom routing](https://docs.tomtom.com/routing-api/documentation/tomtom-maps/v1/calculate-route), [India traffic coverage](https://docs.tomtom.com/traffic-api/documentation/tomtom-maps/v1/product-information/market-coverage), [Traffic Stats](https://docs.tomtom.com/traffic-stats/documentation/product-information/market-coverage), [Mappls routing](https://developer.mappls.com/documentation/sdk/rest-apis/mappls-routing-api/readme/), [HERE traffic](https://docs.here.com/routing/docs/routing-v8-traffic-in-routing).

Cost controls: use one traffic provider initially; bounded departure grid; request only required fields; per-provider quotas/timeouts. Free caps are not permission to enable billing automatically. Historical observations for ML are a separate acquisition task. Do not retain vendor responses for training without checking the applicable data rights.

## Chennai transit and accessibility

| Source/tool | Purpose | Status / usable boundary |
|---|---|---|
| Organiser network | Official PS routes, costs, modes and edge attributes | NOT PROVIDED. Highest-priority input; build an adapter when its schema is known. |
| ChennaiGTFS community compilation | MTC / CMRL / suburban stop and route structure, schedules | LOCAL DATA. Detailed archive inspection in inventory. Treat schedules and licensing as provisional; repository claims are not agency verification. |
| Chennai One / MTC / CUMTA | Live bus locations, ticketing, multimodal services | Consumer capabilities established; no authorised developer feed obtained. Do not scrape private mobile APIs or promise live GTFS-RT. |
| CMRL published facilities | General accessibility and station reference | DOCUMENTED; does not establish current lift operational status or exact entrance accessibility. |
| OpenTripPlanner | Server-side GTFS+OSM multimodal itinerary generation | DOCUMENTED/self-hosted candidate after clean GTFS and proper OSM topology are available. Not an Android offline engine. |
| openrouteservice wheelchair profile | OSM-based wheelchair routing with surface/incline/kerb restrictions | DOCUMENTED; hosted key or self-hosted backend. Missing OSM attributes still limit conclusions. Optional comparison, not a substitute for audited entrances. |
| Own audited access graph | Entrances, stairs, lifts, crossings, width, surface, walk limits | Needed for the differentiating accessibility behaviour; observations require provenance and expiry. No verified live lift feed found. |

Sources: [ChennaiGTFS](https://github.com/ungalsoththu/ChennaiGTFS), [CUMTA app](https://play.google.com/store/apps/details?id=in.mobility.cumta), [CMRL facilities](https://wcag.chennaimetrorail.org/facilities-for-differently-abled-people-bkp/), [OTP documentation](https://docs.opentripplanner.org/en/latest/), [ORS backend](https://giscience.github.io/openrouteservice/).

## Rain, flood and nighttime-route evidence

| Source | Output / granularity | Probe and practical use |
|---|---|---|
| Open-Meteo Forecast | Gridded hourly temperature, precipitation and probability | TESTED: 72 hourly records for Chennai starting 2026-11-05. Returned model grid point differs from requested coordinate. Weather context, not street inundation. |
| Open-Meteo historical / previous runs | Reanalysis and archived model predictions | DOCUMENTED, not downloaded yet. Historical reanalysis is not what a forecast knew at departure; use archived forecast runs for honest prediction evaluation. |
| IMD APIs / Mausam WFS | District/station nowcasts, observations, warnings and radar products | TESTED & DOWNLOADED: Public IMD Mausam GeoServer WFS endpoints (`reactjs.imd.gov.in/geoserver/imd/ows`) return live GeoJSON without authentication using CQL filters (`District='CHENNAI'`, `State='TAMILNADU'`). Station nowcasts for Meenambakkam and Nungambakkam, plus RMC Chennai nowcast PDF bulletin, successfully downloaded and parsed. The developer portal `api.imd.gov.in` remains 401 token-gated. |
| Chennai Flood Monitoring DSS | Flood modelling and monitoring reference | About page TESTED/downloaded; no public authenticated street-depth API verified. |
| IIT Madras CrowdWater | Crowdsourced water depth/location/photo reports | DOCUMENTED public platform; developer API, reuse rights and current report availability not established. Partnership candidate. No personal reports harvested. |
| NRSC/Bhuvan flood layers | Historical event inundation and regional flood context | DOCUMENTED candidate; event-specific, not live road passability. Do not infer kerb/ramp gradients from coarse elevation. |
| OpenCity / GCC historical flood hotspots | Historical 2020 event point locations | LOCAL DATA: source KML downloaded through public CKAN metadata; 53 points converted to GeoJSON. Historical susceptibility only. See REFERENCE_MAPS.md and reference_manifest.json. |
| Safetipin | Lighting, visibility, people and other audit parameters; Chennai work listed | PARTNERSHIP / UNKNOWN: no accessible current Chennai audit feed or reuse grant established. |
| OSM `lit`, crossings, paths | Sparse mapped physical attributes | LOCAL DATA where present; no tag means unknown. `lit=yes` is not a live lamp-health reading. |
| App observations | Timestamped local closures/access incidents | Future owned data, with source/confidence/expiry and moderation. Simulated demo events must be tagged synthetic. |

Sources: [Open-Meteo docs](https://open-meteo.com/en/docs), [terms](https://open-meteo.com/en/terms), [forecast archives](https://open-meteo.com/en/docs/historical-forecast-api), [IMD portal](https://api.imd.gov.in/public/), [API reference](https://api.imd.gov.in/public/api_reference.html), [Chennai flood DSS](https://chennaifloodmonitor.tn.gov.in/Master/AboutUs), [CrowdWater](https://crowdwater.idf-iitmadras.in/), [Bhuvan](https://bhuvan-app1.nrsc.gov.in/disaster/usrtasks/flood/flood.php?uname=empty), [Safetipin](https://safetipin.com/our-apps/).

Open-Meteo's free endpoint is non-commercial with published limits; attribution applies. Commercial/promotional use needs the appropriate plan. No street-level water-depth, live lighting or live lift API has been verified. These are data acquisition gaps, not missing ML libraries. Historical flood source: [OpenCity CKAN dataset](https://data.opencity.in/dataset/3aef2eef-a97e-4fac-a4a9-aaa42a91fbfa).

## Feature-to-technology handoff (provisional, not feature design)

| Feature | Proposed tools | Required data / verification |
|---|---|---|
| Native Android UI | Kotlin, Jetpack Compose, ViewModel/StateFlow | Pin mutually compatible versions in environment handoff |
| Map icons, highlight, camera | MapLibre Native via Compose interoperability; Google SDK alternative kept separate | Map source licence and offline asset completeness |
| Multi-modal routing | Python FastAPI + Pydantic adapter for supplied graph; OTP later if real GTFS scale warrants it | Time-dependent services, transfers, fares, walk edges |
| Accessibility constraints | Deterministic graph filtering + resource-constrained search; NetworkX useful for baseline checks | Cumulative walking, stairs/lift status, unknown attributes; simple shortest path alone is insufficient |
| Departure optimisation | Traffic provider adapter + bounded time-grid comparison | Actual provider output, timestamp, no unsupported certainty |
| Reliability / local prediction | pandas/NumPy; scikit-learn baseline only once measured outcomes exist | Chronological evaluation, calibration and no future-data leakage |
| Weather/closure overlay | HTTP client + GeoJSON/Shapely; event expiry | Weather != waterlogging; historical risk != current closure |
| Agent interaction | Tool-call interface over typed trip constraints and route results; provider-neutral LLM adapter | Model key later; deterministic text templates as offline fallback |
| Tamil/English voice | Android SpeechRecognizer + TextToSpeech; BHASHINI as optional remote adapter | Query on-device/language availability; test Tamil and code-switching on target device |
| Panda animation | Compose animation for small states; optional Lottie asset later | No asset generation/runtime dependency needed for data audit |
| Saved journey | Room/SQLite + files; WorkManager for refresh; offline renderer assets separately | Airplane-mode test and separate loss-of-location test |
| Active trip location | Android location APIs, appropriate foreground service | Runtime permissions, battery behaviour; WorkManager is not a continuous GPS loop |
| Floating companion | Optional Android application overlay | Separate consent and architecture; not a route-data/control API into Google Maps |
| Pool/meetup matching | Backend constrained candidate matching + geospatial distance; WebSocket later | Opt-in participants, access/vehicle needs; no public ride-booking integration obtained |
| Notifications/check-ins | Android notification APIs and backend events if needed | User-controlled; no messaging service/account setup yet |

Voice sources: [Android recognition](https://developer.android.com/reference/android/speech/SpeechRecognizer), [BHASHINI pipeline docs](https://bhashini.gitbook.io/bhashini-apis). BHASHINI docs describe pipeline search/config/compute and PoC access; production terms need separate confirmation. Do not confuse the government platform with similarly named commercial domains. No speech API key or language pipeline was tested here.

## Setup agent acceptance gates

1. Read inventory and environment audit first; preserve raw files and manifests.
2. Build only the selected native baseline; prove a debug build before adding services.
3. Load a bounded approved dataset; show dataset date, attribution and unknown fields.
4. Demonstrate weather requests via saved fixtures (Open-Meteo 72h forecast and IMD Chennai/TN Nowcast GeoJSON) and live adapters.
5. Google/TomTom/Mappls adapters stay disabled until explicit credentials and quotas are configured.
6. No claim of live transit, flood, lighting or lift data without an actual validated feed.
7. No forecasting model installation/training until the target, historical labels and baseline are specified.
8. Keep the organiser network as a required separate input; do not silently replace it with stale public timetables.
