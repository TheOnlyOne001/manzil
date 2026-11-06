# Chennai offline map and local search pack

Prepared 5 November 2026. Actual OSM data, not sample streets or mocked places. The routing engine is separate; none of this display geometry should be used as the routing graph.

## Ready artifacts

Directory: `data/offline-map/`.

- `manifest.json`: coverage, grid definition, per-file SHA256 and byte counts.
- `tiles/*.geojson`: 356 clipped grid tiles, 161,198 total road/path/rail feature fragments; 35,848,212 bytes total. Largest tile405,115 bytes.
- `overview.geojson`: major roads and rail, more simplified for city overview;3,729,395 bytes.
- `search.json`:13,535 actual named road/stop/place records. SRM/Potheri enrichment includes122 additional named amenities/buildings/places.
- `places.geojson`: optional point layer corresponding to named stops and contextual POIs.
- `context-srm.geojson`:263 actual closed-way water/park/building polygons around SRM/Potheri; not citywide landcover.
- `ATTRIBUTION.txt`: required OpenStreetMap attribution and ODbL links.
- `validation.json`: actual integrity results; does not claim phone rendering performance or routing connectivity.

Raw `srm-osm.xml`, its source manifest and any source-only snapshots need not be bundled. Ship the manifest, listed display/search files, tiles and attribution only.

## Exact Android integration schema

Manifest bounds use `[west,south,east,north]` = `[79.95,12.65,80.35,13.30]`. Grid has `west`, `south`, `cellDegrees:0.025`, `columns:16`, `rows:26`. Calculate tile `x=floor((longitude-west)/cellDegrees)`, `y=floor((latitude-south)/cellDegrees)` and find a matching manifest row. Missing cells are empty; do not imply coverage outside bounds. Each tile row has `x,y,file,features,bytes,sha256`.

Tile geometry is GeoJSON `MultiLineString`, coordinates `[longitude,latitude]`. Feature properties:

| Property | Values |
|---|---|
| `class` | `major`, `road`, `path`, `rail` |
| `type` | Original OSM highway/railway tag |
| `name`, `name:en`, `name:ta` | Present only when supplied by OSM |
| `bridge`, `tunnel`, `layer` | Optional original OSM tags |

Use overview at city zoom. At detailed zoom load only the viewport's intersecting tiles plus a small ring, merge their FeatureCollections off the UI thread, and update a MapLibre GeoJsonSource. Avoid loading all356tiles as one source. Unique feature IDs combine OSMway and cell so adjacent tile fragments do not collide.

SRM context polygons have `class:water|park|building`, optional `name`; render FillLayers below roads. Their coverage is `[80.035,12.815,80.055,12.835]` and they intentionally omit unassembled multipolygon relations.

`search.json` is `{version:1,entries:[...]}`. Each entry contains `id,name,aliases:[...],normalized,lon,lat,kind`. Names and Tamil aliases are preserved. `normalized` is NFKC/casefold with collapsed whitespace. `kind` includes road, stop, entrance and actual amenity/place tags. Search coordinates are approximate representative points: named ways may use a midpoint/centroid, not a verified accessible entrance. Keep a map-pin destination fallback. “Anu's Punjabi Dhaba” was not found in these acquired sources and must not be fabricated.

MapLibre line/fill/circle layers work without remote styles, sprites or glyphs. If text is rendered using SymbolLayers, supply local glyphs or verify Android local font rendering; never leave a remote glyph URL in an offline style. Root is initially rendering selected labels with Android native text and map projection.

## Data sources and rights

The road source is preserved `data/osm/chennai_transport.json`, OSM timestamp2026-11-05T14:26:06Z, selected road/rail ways plus transit/access nodes. `manifest.json` records its SHA256. It excludes turn-restriction relations, many POIs, landuse and buildings; these are display limitations, and the route engine uses its own complete route segments.

Local enrichment came from one bounded official OSM API call:
`https://api.openstreetmap.org/api/0.6/map?bbox=80.035,12.815,80.055,12.835`.
Raw source1,214,988bytes, hash/provenance in `srm-source-manifest.json`. Overpass attempts for broader POIs timed out or returned502; no unverified response was incorporated. No public raster map tiles were bulk downloaded.

Data license: ODbL1.0, attribution “© OpenStreetMap contributors”. Display attribution on the map and include the license link. Sources: [OSM copyright](https://www.openstreetmap.org/copyright), [MapLibre GeoJsonSource API](https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.style.sources/-geo-json-source/index.html).

## Reproduce and validate

1. `backend/.venv/Scripts/python.exe scripts/offline_build_map.py`
2. `backend/.venv/Scripts/python.exe scripts/offline_enrich_srm.py`
3. `backend/.venv/Scripts/python.exe scripts/offline_validate_map.py`

Scripts read preserved sources; they do not repeat network requests. All tile geometry is clipped to its cell and simplified only for display. Validation checks every manifest hash, count, coordinate bound and nonempty line geometry. OSM completeness and walkability remain separate concerns.
