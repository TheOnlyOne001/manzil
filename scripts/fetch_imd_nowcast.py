"""Fetch and validate live IMD Nowcast data for Chennai and Tamil Nadu via public Mausam GIS GeoServer WFS."""
import datetime
import hashlib
import json
import pathlib
import ssl
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT_PROBES = ROOT / 'data' / 'api-probes'
OUT_DERIVED = ROOT / 'data' / 'derived'

ENDPOINTS = {
    'imd_district_nowcast': {
        'url': 'https://reactjs.imd.gov.in/geoserver/imd/ows?service=WFS&version=1.1.0&request=GetFeature&typename=imd:NowcastWarningDistrict&srsname=EPSG:4326&outputFormat=application/json',
        'desc': 'District-wise Nowcast warnings across India from IMD Mausam GeoServer',
        'is_json': True,
    },
    'imd_station_nowcast': {
        'url': 'https://reactjs.imd.gov.in/geoserver/imd/ows?service=WFS&version=1.1.0&request=GetFeature&typename=imd:NowcastWarningStation&srsname=EPSG:4326&outputFormat=application/json',
        'desc': 'Station-wise Nowcast warnings across India from IMD Mausam GeoServer',
        'is_json': True,
    },
    'imd_rmc_chennai_nowcast_pdf': {
        'url': 'https://mausam.imd.gov.in/chennai/mcdata/nowcast.pdf',
        'desc': 'Official Regional Meteorological Centre (RMC) Chennai daily Nowcast Bulletin PDF',
        'is_json': False,
    }
}

def fetch_data():
    OUT_PROBES.mkdir(parents=True, exist_ok=True)
    OUT_DERIVED.mkdir(parents=True, exist_ok=True)
    ctx = ssl.create_default_context()
    headers = {'User-Agent': 'ManzilResearch/0.2 (public weather audit; Chennai)'}

    results = {}
    for name, info in ENDPOINTS.items():
        print(f"Fetching {name} from {info['url'][:60]}...")
        req = urllib.request.Request(info['url'], headers=headers)
        with urllib.request.urlopen(req, timeout=30, context=ctx) as resp:
            data = resp.read()
            status = resp.status
            content_type = resp.headers.get('Content-Type')

        ext = '.json' if info['is_json'] else '.pdf'
        dest_file = OUT_PROBES / f"{name}{ext}"
        dest_file.write_bytes(data)

        sha256 = hashlib.sha256(data).hexdigest()
        print(f"  -> Saved {len(data)} bytes ({sha256[:12]}...) to {dest_file.name}")

        results[name] = {
            'bytes': len(data),
            'sha256': sha256,
            'status': status,
            'content_type': content_type,
            'retrieved_at_utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
        }

        if info['is_json']:
            parsed = json.loads(data)
            results[name]['feature_count'] = len(parsed.get('features', []))

    # Extract Chennai-specific GeoJSON layers for clean, lightweight operational routing use
    print("\nExtracting Chennai-specific nowcast features...")
    district_raw = json.loads((OUT_PROBES / 'imd_district_nowcast.json').read_text(encoding='utf-8'))
    station_raw = json.loads((OUT_PROBES / 'imd_station_nowcast.json').read_text(encoding='utf-8'))

    chennai_districts = [
        f for f in district_raw.get('features', [])
        if (f.get('properties', {}).get('District') or '').upper() == 'CHENNAI'
        or (f.get('properties', {}).get('State_District') or '').upper() == 'CHENNAI'
    ]
    tamil_nadu_districts = [
        f for f in district_raw.get('features', [])
        if (f.get('properties', {}).get('State') or '').upper() == 'TAMILNADU'
    ]

    chennai_stations = [
        f for f in station_raw.get('features', [])
        if any(name in (f.get('properties', {}).get('Station') or '').lower() for name in ['meenambakkam', 'nungambakkam', 'chennai'])
    ]

    # Save Chennai & Tamil Nadu district slices
    chennai_dist_fc = {'type': 'FeatureCollection', 'features': chennai_districts}
    tn_dist_fc = {'type': 'FeatureCollection', 'features': tamil_nadu_districts}
    chennai_stn_fc = {'type': 'FeatureCollection', 'features': chennai_stations}

    (OUT_DERIVED / 'imd_chennai_district_nowcast.geojson').write_text(json.dumps(chennai_dist_fc, indent=2), encoding='utf-8')
    (OUT_DERIVED / 'imd_tamilnadu_district_nowcast.geojson').write_text(json.dumps(tn_dist_fc, indent=2), encoding='utf-8')
    (OUT_DERIVED / 'imd_chennai_stations_nowcast.geojson').write_text(json.dumps(chennai_stn_fc, indent=2), encoding='utf-8')

    print(f"Extracted Chennai District Nowcast: {len(chennai_districts)} features -> imd_chennai_district_nowcast.geojson")
    print(f"Extracted Tamil Nadu District Nowcast: {len(tamil_nadu_districts)} features -> imd_tamilnadu_district_nowcast.geojson")
    print(f"Extracted Chennai Stations Nowcast: {len(chennai_stations)} features -> imd_chennai_stations_nowcast.geojson")

    # Summary report
    print("\n=== Current Chennai Nowcast Snapshot ===")
    if chennai_districts:
        p = chennai_districts[0]['properties']
        print(f"District: {p.get('District')} ({p.get('State')})")
        print(f"Date: {p.get('Date')} | Issued: {p.get('toi')} hrs | Valid up to: {p.get('vupto')} hrs")
        print(f"Warning Color Level: {p.get('Color')} (1=Green/No Warning, 2=Yellow/Watch, 3=Orange/Alert, 4=Red/Warning)")
        print(f"Light Thunderstorm (cat4): {p.get('cat4')}")
        print(f"Moderate Rain (cat7): {p.get('cat7')}")
        print(f"Centre: {p.get('MC_RMC')}")
        print(f"Last updated: {p.get('update_time')}")

    if chennai_stations:
        print("\nStations:")
        for s in chennai_stations:
            sp = s['properties']
            print(f"  - {sp.get('Station')}: Date={sp.get('Date')} TOI={sp.get('toi')} VUPTO={sp.get('vupto')} Color={sp.get('Color')}")

if __name__ == '__main__':
    fetch_data()
