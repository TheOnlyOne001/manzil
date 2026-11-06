"""Bounded, public Manzil data acquisition and lightweight validation (stdlib only).
Run: python scripts/download_data.py [--skip-osm]
Raw source data is preserved. No private APIs, credentials, or bulk map tiles.
"""
import argparse, collections, csv, datetime, hashlib, io, json, pathlib, urllib.parse, urllib.request, zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
DATA = ROOT / 'data'
NOW = lambda: datetime.datetime.now(datetime.timezone.utc).isoformat()
MANIFEST = DATA / 'provenance.json'
UA = 'ManzilHackathonResearch/0.1 (bounded Chennai research download)'
records = []

def fetch(url, relative, license_note, body=None, timeout=120):
    path = DATA / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    entry = dict(url=url, path=str(path.relative_to(ROOT)), fetched_at_utc=NOW(), license_note=license_note)
    if body is not None: entry['request_body'] = body.decode()
    try:
        if not path.exists():
            req = urllib.request.Request(url, data=body, headers={'User-Agent': UA})
            with urllib.request.urlopen(req, timeout=timeout) as response:
                entry['http_status'] = response.status
                entry['last_modified'] = response.headers.get('Last-Modified')
                entry['final_url'] = response.url
                size = 0
                with path.with_suffix(path.suffix+'.part').open('wb') as out:
                    while chunk := response.read(1024*1024):
                        size += len(chunk)
                        if size > 200*1024*1024: raise ValueError('Per-file 200 MiB cap reached')
                        out.write(chunk)
                path.with_suffix(path.suffix+'.part').replace(path)
        else:
            entry['cache_reused'] = True
        entry.update(status='downloaded', bytes=path.stat().st_size, sha256=hashlib.sha256(path.read_bytes()).hexdigest())
        print(relative, entry['bytes'], flush=True)
    except Exception as error:
        entry.update(status='failed', error=str(error))
        print(relative, 'FAILED', str(error), flush=True)
    records.append(entry)
    MANIFEST.write_text(json.dumps(records, indent=2), encoding='utf-8')
    return path if entry['status']=='downloaded' else None

def inspect_gtfs(path):
    result = {'file': str(path.relative_to(ROOT)), 'inspection_date': '2026-11-05', 'tables': {}}
    with zipfile.ZipFile(path) as archive:
        result['zip_crc_error'] = archive.testzip()
        result['uncompressed_bytes'] = sum(i.file_size for i in archive.infolist())
        for name in archive.namelist():
            if not name.endswith('.txt'): continue
            with archive.open(name) as raw:
                reader = csv.DictReader(io.TextIOWrapper(raw, encoding='utf-8-sig'))
                info = {'columns': reader.fieldnames, 'rows': 0}
                selected = [c for c in ('wheelchair_boarding','wheelchair_accessible','location_type','route_type','pathway_mode','agency_id','start_date','end_date') if c in (reader.fieldnames or [])]
                counts = {c: collections.Counter() for c in selected}
                lats, lons = [], []
                sample = []
                for row in reader:
                    info['rows'] += 1
                    for c in selected: counts[c][row.get(c,'')] += 1
                    if name.endswith('stops.txt'):
                        try: lats.append(float(row['stop_lat'])); lons.append(float(row['stop_lon']))
                        except (ValueError,KeyError): pass
                    if name.endswith(('calendar.txt','feed_info.txt','agency.txt','calendar_dates.txt')): sample.append(row)
                info['field_counts'] = counts
                if lats: info['bbox_wgs84'] = [min(lons), min(lats), max(lons), max(lats)]
                if sample: info['records'] = sample
                result['tables'][name] = info
    return result

def main():
    parser = argparse.ArgumentParser(); parser.add_argument('--skip-osm', action='store_true'); args=parser.parse_args()
    DATA.mkdir(parents=True, exist_ok=True)
    if MANIFEST.exists(): records.extend(json.loads(MANIFEST.read_text(encoding='utf-8')))
    base = 'https://raw.githubusercontent.com/ungalsoththu/ChennaiGTFS/main/'
    note = 'Community compilation; README claims ODbL/PDDL, root LICENSE differs (MIT). Mirrored agency data has no confirmed open license. Research candidate, not cleared for redistribution/commercial use.'
    fetch(base+'README.md', 'sources/ChennaiGTFS_README.md', note)
    fetch(base+'LICENSE', 'sources/ChennaiGTFS_LICENSE', note)
    fetch('https://api.github.com/repos/ungalsoththu/ChennaiGTFS/commits/main', 'sources/ChennaiGTFS_commit.json', 'GitHub repository metadata')
    reports=[]
    for filename in ('chennai-unified-gtfs.zip','cmrl-gtfs.zip','srr-gtfs.zip','mtc-gtfs.zip'):
        path=fetch(base+'data/'+filename, 'transit/'+filename, note)
        if path: reports.append(inspect_gtfs(path))
    (DATA/'gtfs_validation.json').write_text(json.dumps(reports, indent=2), encoding='utf-8')
    fetch('https://test.cumta.org/static_mtc_gtfs_data.zip', 'transit/cumta-static-mtc-gtfs.zip', 'Agency-hosted publicly linked research bundle; no confirmed open reuse license. Nested GTFS and GeoJSON; inspect with inspect_chennai_data.py.', timeout=120)
    if not args.skip_osm:
        # Chennai metropolitan pilot bbox, not official administrative boundary.
        # Select transport network and useful access nodes, not buildings/planet data.
        bbox='12.65,79.95,13.30,80.35'
        query=f'''[out:json][timeout:120][maxsize:268435456];
        (way[highway]({bbox});way[railway]({bbox});
        node[public_transport]({bbox});node[highway=bus_stop]({bbox});
        node[highway=elevator]({bbox});node[entrance]({bbox}););
        (._;>;);out body;'''
        (DATA/'sources'/'chennai_transport.overpassql').write_text(query,encoding='utf-8')
        path=fetch('https://overpass-api.de/api/interpreter', 'osm/chennai_transport.json', 'ODbL 1.0; attribution OpenStreetMap contributors; https://www.openstreetmap.org/copyright', urllib.parse.urlencode({'data':query}).encode(),180)
        if path:
            value=json.loads(path.read_text(encoding='utf-8'))
            elements=value.get('elements',[])
            stats={'bbox_south_west_north_east':[12.65,79.95,13.30,80.35], 'osm_base_timestamp':value.get('osm3s',{}).get('timestamp_osm_base'), 'remark':value.get('remark'), 'types':collections.Counter(e['type'] for e in elements), 'tag_coverage':{k:sum(k in e.get('tags',{}) for e in elements) for k in ('highway','railway','wheelchair','lit','surface','smoothness','incline','sidewalk','tactile_paving','kerb','entrance','public_transport')}}
            (DATA/'osm_validation.json').write_text(json.dumps(stats,indent=2),encoding='utf-8')
            print(json.dumps(stats),flush=True)

if __name__=='__main__': main()
