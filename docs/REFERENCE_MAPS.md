# Downloaded Chennai reference maps

Fetched 5 November 2026. Files are in `data/reference-maps/`; `reference_manifest.json` records source URL, UTC download time, SHA-256, byte count, date and use limitations. Downloads total approximately 5.05 MB, excluding small metadata and generated previews. No account or private API was used.

| File | Source and date | Verified use |
|---|---|---|
| `cmrl-line-map-2023.pdf` | [CMRL line map](https://chennaimetrorail.org/wp-content/uploads/2023/11/Line-Map.pdf); URL upload folder November 2023, internal date unverified | Valid one-page PDF, 1,889,457 bytes; image-only first page |
| `cmrl-phase-I-II-plan-2024-12-18.pdf` | [CMRL Phase I & II map](https://chennaimetrorail.org/wp-content/uploads/2025/02/Phase-I-II-Map-Updated-Map-18.12.2024-PHASE-1-2-MAP.pdf); filename date 18 December 2024 | Valid one-page PDF, 3,130,214 bytes; title confirms Phase I & II map |
| `gcc-flood-hotspots-2020.kml` | [OpenCity Chennai Floods 2020 Data](https://data.opencity.in/dataset/3aef2eef-a97e-4fac-a4a9-aaa42a91fbfa) | 25,619-byte source KML; historical GCC flood hotspots |
| `gcc-flood-hotspots-2020.geojson` | Derived locally from the KML above | 53 point features; longitude/latitude preserved; only generated ID, source year, classification and dataset provenance retained |
| `opencity-flood-search-metadata.json` | [OpenCity public CKAN search](https://data.opencity.in/api/3/action/package_search?q=chennai%20flood&rows=8) | Catalog metadata and downloadable resource links; includes 2005/2015/2020 history and return-period maps |

CMRL maps are static visual references. A Phase II planned corridor or station is **not evidence of an operational route**. Confirm operational status, schedules and station access from current authority data before routing. No explicit CMRL redistribution licence was verified, so retain these as research references rather than automatically bundling them in the app.

OpenCity's dataset metadata declares **Other (Public Domain)**. Preserve attribution and original source provenance. Its metadata modification date is 25 November 2025, but the underlying flood event is **2020**. These points are suitable for a historical susceptibility demonstration only; they establish neither current flooding nor road closure, water depth or passability. The conversion retains point coordinates without simplification and omits descriptions and non-geometric fields.

First-page previews were generated with PyMuPDF for parent-agent visual inspection:

- `data/reference-maps/cmrl-line-map-2023.pdf.page1.png`
- `data/reference-maps/cmrl-phase-I-II-plan-2024-12-18.pdf.page1.png`

Catalogued but not bulk-downloaded: OpenCity's 5/10/25/50/100/200-year return-period KMLs and 39.9 MB hazard-zone map. These are modelled/static hazard products, not live street-level flood measurements. No crowdsourced personal-contact layer was collected.
