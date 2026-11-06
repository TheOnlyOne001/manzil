# Flood coverage audit (Chennai)

Audited 2026-11-06 from files in `data/`. Counts and bounding boxes were computed directly from the
geometry in each file; no position was added, moved or inferred. Bounding boxes are S, W, N, E (WGS84).

## What is in the repo now

| Layer | File | Features | Bounding box (S, W, N, E) |
|---|---|---|---|
| GCC flood hotspots 2020 | `data/reference-maps/gcc-flood-hotspots-2020.geojson` (OpenCity dataset `3aef2eef…`) | 53 points | 12.8628, 80.1460, 13.1801, 80.2989 |
| Surveyed inundation points with depth (`DEPTH`, inches) | `data/reference-maps/chennai-inundation-points-depth.kml` | 192 points | 12.9114, 80.1674, 13.1646, 80.2611 |
| Derived hazard layer (all categories) | `data/derived/latest_chennai_hazards.geojson` | 410 | 12.8628, 80.1344, 13.2354, 80.3319 |
| Of which flood-relevant (hotspots 53, surveyed inundation 192, subway chokepoints 12, waterway overflow corridors 4) | same | 261 | — |
| App overlay bundle | `data/derived/chennai_map_overlays.geojson` | 3,304 (192 `survey_point`, 53 `hotspot`; rest are flyover/metro/night layers) | 12.6567, 79.9521, 13.2993, 80.3271 |

The two primary flood point sources (hotspots + surveyed depth) are the same 245 points that appear
in both derived files. The derived files are wider only because of non-flood layers (relief centres,
metro, night lighting).

## Gaps

Flood-relevant features per edge band (bands overlap at corners):

| Band | Features |
|---|---|
| North, lat ≥ 13.12 | 32 |
| South, lat ≤ 12.95 | 31 |
| West, lon ≤ 80.19 | 22 |
| Core (12.95–13.12, lon > 80.19) | 180 |

- **North:** surveyed depth points stop at 13.1646 and hotspots at 13.1801. Nothing covers
  Tiruvottiyur north, Ennore or Manali (roughly 13.18–13.25). The derived layer reaches 13.2354, but
  only because of non-flood categories.
- **West:** depth points start at 80.1674. Ambattur, Avadi, Poonamallee and Porur (west of about
  80.15) have almost no flood points.
- **South:** depth points stop at 12.9114. Pallikaranai marsh fringe, Medavakkam, Sholinganallur,
  Perumbakkam and OMR south have hotspots only, and no depth data.
- **Time:** every point is historical (2020 hotspots, plus an undated depth survey). None of it is a
  live flood observation, and none of it is an extent polygon.

## No new data was vetted in this pass

This pass downloaded and validated nothing new. The resources below are already listed in
`data/reference-maps/opencity-flood-search-metadata.json`, which is a CKAN `package_search` result.
Each one is a georeferenced KML from Greater Chennai Corporation on OpenCity, licensed "Other
(Public Domain)". These are the most readily usable expansion candidates. Before using any of them:
download it, check its CRS and geometry, record its sha256 in `reference_manifest.json`, and confirm
it actually covers the gaps above.

Dataset `chennai-flooding-data` (`022dd080-e927-40d7-897d-adf3ee98ad69`):
- Flooding Points 2015: https://data.opencity.in/dataset/022dd080-e927-40d7-897d-adf3ee98ad69/resource/80ed2fa7-1150-4f55-8125-682ab55282ac/download/93d2905a-a580-482d-96b0-4d9ccf5273ef.kml
- Flood Hazard Zones Map (polygons, useful for west and south): https://data.opencity.in/dataset/022dd080-e927-40d7-897d-adf3ee98ad69/resource/e61afe07-0f52-4be9-bdf3-5eb0f62b2ed6/download/7f29da20-6621-4b49-b0cf-28b9d1de232b.kml
- Flows, 5/10/25/50/100/200-year return periods (modelled, not observed). Example, 100-year: https://data.opencity.in/dataset/022dd080-e927-40d7-897d-adf3ee98ad69/resource/e34a8f9d-251c-401d-8642-bd231625a081/download/994a99a1-6104-40c7-ac10-2b7a7ab74437.kml

Dataset `chennai-floods-2015-data` (`866141ab-3a3f-4dc0-8092-421d97ba29a2`):
- 2015 Floods Inundation Zone (extent): https://data.opencity.in/dataset/866141ab-3a3f-4dc0-8092-421d97ba29a2/resource/2056abd6-26d7-413b-9dfa-e63cbbf41ee7/download/7cb3cecf-a95a-4786-8032-9c7417655d24.kml
- GCC Stagnation Locations 2015: https://data.opencity.in/dataset/866141ab-3a3f-4dc0-8092-421d97ba29a2/resource/b5f39598-2e2d-40c5-82c3-806a1ad65c91/download/db3840ff-9f33-43a3-b826-2f9dae1bcb78.kml
- 2015 GCC Area Flood Hotspots: https://data.opencity.in/dataset/866141ab-3a3f-4dc0-8092-421d97ba29a2/resource/8e1c5b2d-322c-4dbd-bd64-6da2d1a8681d/download/31523c86-0ad1-42f5-b4d1-297daa4bbcd6.kml
- 2015 Tiruvallur District Flood Hotspots (north and west gap): https://data.opencity.in/dataset/866141ab-3a3f-4dc0-8092-421d97ba29a2/resource/ec58c238-b760-474a-93f5-181abc8d45e9/download/d8d3ac1d-b486-4446-bc76-3dff0af8cbdb.kml
- 2015 Kancheepuram District Flood Hotspots (south and west gap): https://data.opencity.in/dataset/866141ab-3a3f-4dc0-8092-421d97ba29a2/resource/e180e7f3-a2bc-4cd5-9883-008c94a24bed/download/3746a11e-620f-40e4-94a1-21ea8fe2a35e.kml
- 2015 Crowd-sourced Flooding Locations (lower trust; label as crowd-sourced): https://data.opencity.in/dataset/866141ab-3a3f-4dc0-8092-421d97ba29a2/resource/782a2e52-78ac-4f89-b469-28a6f7d365eb/download/46d6c279-ae09-43a8-8691-7a5386f69e3a.kml

Dataset `chennai-floods-2005-data` (`bdae9914-f0e6-46e1-ad26-ed4ad4f5251a`):
- GCC Flood Extent 2005: https://data.opencity.in/dataset/bdae9914-f0e6-46e1-ad26-ed4ad4f5251a/resource/5a5a41fd-d7f5-442a-a523-d58e3b6ef53a/download/c49407c8-1c03-4646-a3ba-0ddb16da0d9f.kml
- GCC Flood Hotspots 2005: https://data.opencity.in/dataset/bdae9914-f0e6-46e1-ad26-ed4ad4f5251a/resource/eeb91a74-eeab-4398-bc87-cc1423c83785/download/7cbcb0b1-7561-4e03-acab-b08ec68a838a.kml

Satellite-derived extents, for events and areas the GCC data does not cover. Rasters and vectors
need clipping; check each licence:
- NRSC/Bhuvan disaster services (flood inundation maps per event): https://bhuvan-app1.nrsc.gov.in/disaster/disaster.php
- Copernicus EMS rapid mapping activations: https://rapidmapping.emergency.copernicus.eu/
- UNOSAT / HDX flood products: https://data.humdata.org/

Rule for any additions: keep source URL, fetch time, licence and hash. Keep the observed, modelled
and crowd-sourced categories distinct. Never snap or synthesise points to fill a gap.

## Implemented expansion, 6 November 2026
Imported and deduplicated 1,308 actual KML point records from the OpenCity 2015 GCC stagnation/GCC hotspots/Tiruvallur/Kancheepuram datasets. Combined history now has 1,553 points. Of the additions, 430 are north of latitude13.1 and160 west oflongitude80.15. New points have unknown depth and remain explicitly historical. Original source coordinates retained; no polygon centroids or invented extension of road depth. Bundled records supplement older cache feeds.

## Verification
40 backend tests and 87 Android tests passed; build/lint/install successful (docs/fused-expanded-final-build.log). The Samsung displayed location accuracy 6 m then4 m and Use current location produced Google walking route39.07km with Start walking available. No walking movement was performed. Layer icons and expanded historical point coverage visually reviewed in android-evidence/layer-icons-final.png and flood-expanded-final.png. Installed offline pack contains all1,308 added2015records. Panda has one reduced-motion-aware blink/nod on route completion and contextual tap assistance.
