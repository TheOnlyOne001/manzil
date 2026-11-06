"""Validate reproducible map-pack hashes, geometry bounds and search records."""
import hashlib,json
from offline_build_map import OUT

m=json.loads((OUT/'manifest.json').read_text(encoding='utf-8'))
features=0;bytes_total=0
for row in m['tiles']:
    raw=(OUT/row['file']).read_bytes()
    assert hashlib.sha256(raw).hexdigest()==row['sha256']
    assert len(raw)==row['bytes'];bytes_total+=len(raw)
    data=json.loads(raw);assert data['type']=='FeatureCollection'
    assert len(data['features'])==row['features'];features+=len(data['features'])
    west=m['grid']['west']+row['x']*m['grid']['cellDegrees']
    south=m['grid']['south']+row['y']*m['grid']['cellDegrees'];cell=m['grid']['cellDegrees']
    for f in data['features']:
        assert f['geometry']['type']=='MultiLineString'
        assert f['properties']['class'] in ('major','road','path','rail')
        for line in f['geometry']['coordinates']:
            assert len(line)>=2
            assert all(west-1e-6<=x<=west+cell+1e-6 and south-1e-6<=y<=south+cell+1e-6 for x,y in line)
for name,row in m['files'].items():
    raw=(OUT/name).read_bytes();assert hashlib.sha256(raw).hexdigest()==row['sha256']
    assert len(raw)==row['bytes'];bytes_total+=len(raw)
entries=json.loads((OUT/'search.json').read_text(encoding='utf-8'))['entries']
assert len(entries)==m['searchEntries']
assert all(m['bounds'][0]<=e['lon']<=m['bounds'][2] and m['bounds'][1]<=e['lat']<=m['bounds'][3] for e in entries)
result={'status':'pass','tiles':len(m['tiles']),'features':features,'searchEntries':len(entries),'displayAndSearchBytes':bytes_total,'checks':['all manifest checksums','tile coordinate bounds','line geometries nonempty','search coordinates inside coverage','declared counts match'],'note':'Does not establish completeness, routing connectivity, phone rendering performance or current road conditions.'}
(OUT/'validation.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
print(json.dumps(result,indent=2))
