"""Small reproducible, credential-free public endpoint audit; no paid calls."""
import concurrent.futures, datetime, hashlib, json, pathlib, ssl, urllib.request, urllib.error

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / 'data' / 'api-probes'
ENDPOINTS = {
 'open_meteo_chennai': 'https://api.open-meteo.com/v1/forecast?latitude=13.0827&longitude=80.2707&hourly=temperature_2m,precipitation_probability,precipitation,weather_code&forecast_days=3&timezone=Asia%2FKolkata',
 'imd_district_nowcast': 'https://reactjs.imd.gov.in/geoserver/imd/ows?service=WFS&version=1.1.0&request=GetFeature&typename=imd:NowcastWarningDistrict&srsname=EPSG:4326&outputFormat=application/json&cql_filter=State%3D%27TAMILNADU%27',
 'imd_station_nowcast': 'https://reactjs.imd.gov.in/geoserver/imd/ows?service=WFS&version=1.1.0&request=GetFeature&typename=imd:NowcastWarningStation&srsname=EPSG:4326&outputFormat=application/json&cql_filter=Station%20IN%20(%27Meenambakkam%20AP%27%2C%27Nungambakkam%27)',
 'imd_rmc_chennai_bulletin': 'https://mausam.imd.gov.in/chennai/mcdata/nowcast.pdf',
 'imd_gateway_gated_reference': 'https://api.imd.gov.in/api/v1/districtnowcast',
 'imd_api_reference': 'https://api.imd.gov.in/public/api_reference.html',
 'chennai_flood_about': 'https://chennaifloodmonitor.tn.gov.in/Master/AboutUs',
 'google_routes_policy': 'https://developers.google.com/maps/documentation/routes/policies',
 'osm_tile_policy': 'https://operations.osmfoundation.org/policies/tiles/',
 'open_meteo_terms': 'https://open-meteo.com/en/terms',
 'bhashini_documentation': 'https://bhashini.gitbook.io/bhashini-apis',
}

def probe(item):
 name, url = item
 row = {'id': name, 'url': url, 'retrieved_at_utc': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'authenticated': False}
 req = urllib.request.Request(url, headers={'User-Agent': 'ManzilResearch/0.2 (bounded hackathon source audit)'})
 ctx = ssl.create_default_context()
 ctx.check_hostname = False
 ctx.verify_mode = ssl.CERT_NONE
 try:
  with urllib.request.urlopen(req, timeout=25, context=ctx) as r:
   body = r.read(8_000_001)
   row.update(http_status=r.status, content_type=r.headers.get('Content-Type'), final_url=r.url)
  if len(body) > 8_000_000:
   row['error'] = 'Response exceeded 8MB limit; not saved'
   return row
  suffix = '.json' if 'json' in (row['content_type'] or '') else ('.pdf' if 'pdf' in (row['content_type'] or '') else '.html')
  path = OUT / (name + suffix)
  path.write_bytes(body)
  row.update(path=str(path.relative_to(ROOT)), bytes=len(body), sha256=hashlib.sha256(body).hexdigest())
  try:
   obj = json.loads(body)
   row['json_shape'] = list(obj.keys())[:20] if isinstance(obj, dict) else {'type':type(obj).__name__, 'length':len(obj)}
   if name == 'open_meteo_chennai':
    row['data_check'] = {'latitude': obj.get('latitude'), 'longitude':obj.get('longitude'), 'first_time':obj.get('hourly',{}).get('time',[None])[0], 'hour_count':len(obj.get('hourly',{}).get('time',[]))}
   if 'imd_' in name:
    features = obj.get('features', []) if isinstance(obj, dict) else []
    row['feature_count'] = len(features)
    if features:
     row['sample_feature'] = features[0].get('properties')
  except (ValueError, TypeError):
   row['json_parse'] = False
 except urllib.error.HTTPError as e:
  row.update(http_status=e.code, error=str(e))
 except Exception as e:
  row['error'] = str(e)
 return row

if __name__ == '__main__':
 OUT.mkdir(parents=True, exist_ok=True)
 with concurrent.futures.ThreadPoolExecutor(max_workers=3) as ex:
  rows = list(ex.map(probe, ENDPOINTS.items()))
 (OUT/'manifest.json').write_text(json.dumps(rows,indent=2),encoding='utf-8')
 print(json.dumps(rows,indent=2))
