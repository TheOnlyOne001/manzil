# Chennai map and transit data inventory

Inspected and downloaded 5 November 2026. This is a research acquisition, not validation of current transport operations. The organiser-supplied network has not been provided and should become the authoritative hackathon fixture when available.

## Local assets

| Asset | Download size | Actual contents / purpose |
|---|---:|---|
| `data/osm/chennai_transport.json` | 92,773,983 bytes | OSM transport-network subset: 678,805 nodes and 150,691 ways. Snapshot timestamp `2026-11-05T14:26:06Z`. |
| `data/transit/chennai-unified-gtfs.zip` | 9,822,908 bytes | Community unified MTC/CMRL/SRR: 6,542 stop records, 5,402 route records, 48,435 trips, 1,389,081 stop-time rows. |
| `data/transit/mtc-gtfs.zip` | 9,088,141 bytes | 5,580 stops, 4,611 routes, 47,047 trips. |
| `data/transit/cmrl-gtfs.zip` | 147,537 bytes | 254 stop/platform records, 28 route records, 625 trips. Calendar expired in 2021. |
| `data/transit/srr-gtfs.zip` | 154,152 bytes | 708 stop/platform/entrance records, 763 routes/trips, 2,076 pathways. |
| `data/transit/cumta-static-mtc-gtfs.zip` | 30,796,159 bytes | Directly downloadable agency-hosted bundle; inner GTFS plus four GeoJSON files. HTTP Last-Modified: 27 July 2026. |
| `data/derived/chennai.bus.gtfs.zip` | See derived manifest | Inner CUMTA GTFS: 7,128 stops, 4,030 routes, 68,320 trips, 1,672,859 stop times. |
| `data/derived/all_mtc_routes.geojson` | See derived manifest | 637 features, as supplied; not equivalent to GTFS route count. |
| `data/derived/routes_polyline.geojson` | See derived manifest | 1,851 features, as supplied; geometry candidate. |
| `data/derived/mtc_stops.geojson` | See derived manifest | 5,519 features, as supplied; differs from inner GTFS's 7,128 records. |
| `data/derived/bus_terminus.geojson` | See derived manifest | 75 terminal features. |
| `data/derived/unified_stops.geojson` | See derived manifest | Locally converted 6,542 community-feed stop points. |
| `data/derived/chennai_data_preview.png` | See derived manifest | Visually inspected, tile-free static map preview of OSM and transit extent. |

Raw downloads total approximately 143 MB; data plus extracted/derived artifacts approximately 265 MB at inspection. The raw archives have been preserved. ZIP CRC checks passed. These checks are basic structural checks, not a full MobilityData GTFS validator run.

## Sources and provenance

- OSM query endpoint: https://overpass-api.de/api/interpreter . Exact request: `data/sources/chennai_transport.overpassql`.
- OpenStreetMap attribution/license: https://www.openstreetmap.org/copyright . ODbL 1.0, attribute OpenStreetMap contributors.
- Community transit source: https://github.com/ungalsoththu/ChennaiGTFS . Observed commit `af0742dba777f9657ed6fda2046d6c2a721d630b`. README, LICENSE and commit metadata saved under `data/sources/`.
- Direct agency-hosted archive: https://test.cumta.org/static_mtc_gtfs_data.zip . This is a public static link; no encrypted/private API access was used.
- `data/provenance.json` records request URL, HTTP status, retrieval timestamp, source last-modified header where supplied, file size and SHA-256 for original assets.
- `data/derived_provenance.json` records archive member origins and SHA-256 for extracted and generated artifacts.
- `data/gtfs_validation.json`, `data/cumta_nested_gtfs_validation.json`, `data/cumta_departure_counts.json` and `data/osm_validation.json` contain actual counts and field coverage.

## Geographic scope and limits

OSM query box: south 12.65, west 79.95, north 13.30, east 80.35 (WGS84). It is a rectangular pilot area, not the Chennai administrative boundary. The query selected highway and railway ways, transit/access nodes and the nodes required for those ways. It did **not** retrieve all OSM features, buildings or turn-restriction relations. Ways crossing the box can extend outside it.

The community transit stop bounds are wider: west 79.66410927, south 12.6148, east 80.33584, north 13.53316845. Some transit endpoints lie beyond the downloaded OSM area. Therefore this is **not a complete end-to-end routing graph for every transit journey**. It is raw Overpass JSON, not an OTP-ready PBF, navigation engine or offline tile pack. Before building a production routing graph, obtain a properly licensed complete regional extract including relations, cover the chosen service area and convert using Osmium/pyosmium. The preview clearly marks the difference in coverage.

## Material data findings

1. **Accessibility is largely missing.** No `wheelchair_boarding` or `wheelchair_accessible` columns were present in the community stop/trip tables. All 2,076 SRR pathways have `pathway_mode=1` (walkway), so this feed does not differentiate stairs and lifts. Do not infer a step-free journey from presence of pathways.
2. **OSM tag coverage is sparse.** Of 148,961 highway ways, only 28 have a wheelchair tag, 1,127 lighting, 9,303 surface, 144 incline and 334 sidewalk. Missing tags mean unknown, not accessible or unlit. Counts across all nodes/ways are in `osm_validation.json`; the highway-only denominator above is more useful for routing.
3. **CMRL schedule is expired.** Its calendar ends 2021-12-31. Keep it as a structural candidate; do not silently extend dates and call it current. The source's fare URL is a 2021 chart.
4. **Long date ranges do not prove currency.** Community MTC calendar ends 2030 and SRR calendar ends 2170. The direct agency bundle calendar ends 2099.
5. **Agency download differs from repository summary.** The actual July-2026 agency bundle contains 68,320 trips. Its stop-time frequency analysis still finds 4,721 occurrences each of `00:00:15`, `00:02:30` and `00:04:45`, with repeated subsequent increments. This supports concerns about synthetic/placeholder times but does not justify declaring every trip invalid. Validate per trip against current published timetables before using arrival predictions. Repository claims are not substituted for observed counts.
6. **Treat fields as untrusted data.** Community MTC calendars include two unused test-like service IDs, one containing script markup. Neither has associated trips in this snapshot. Do not render raw strings as HTML; quarantine irrelevant test records in a documented derived feed, preserving originals.
7. **Unified feed needs full validation.** The merged file has three agencies but 28 CMRL route records with blank agency IDs. Suburban services use route type 1. Validate agency references and intended mode classification before import; do not assume a successful ZIP check means GTFS compliance.
8. **Rights are unresolved for transit.** Repository README claims ODbL/PDDL for compiled data while root LICENSE contains MIT. Agency mirrors explicitly lack a confirmed open source-data license. Keep local research assets separate; resolve source rights before redistribution/commercial use. OSM's license is explicit and independent.
9. **Not live data.** None of these downloaded assets is a verified live bus, lift, flood, streetlight or traffic feed. Fetch timestamps are not observation timestamps. Road passability, vehicle accessibility and current fares remain unverified.

## Reproduce and hand off

```powershell
python scripts/download_data.py
python scripts/inspect_chennai_data.py
```

Download script uses Python standard library; preview uses the installed Matplotlib. Existing files are reused and hashed. Do not repeatedly rerun large Overpass queries to test UI changes. `--skip-osm` avoids the OSM request. Raw filenames are immutable within this snapshot; use a fresh dated data directory or deliberately version a new snapshot when refreshing.

Recommended logistics next: import and full-validate GTFS, produce a separately labelled cleaned research feed, select a demonstration corridor within both datasets, acquire a complete OSM PBF for that corridor, and build the graph. Keep unknown accessibility explicit. Obtain the organiser network before selecting the final routing adapter.
