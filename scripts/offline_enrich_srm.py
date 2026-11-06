"""Enrich offline search and display polygons from a bounded official OSM map extract.

Run after offline_build_map.py. A search coordinate is a feature representative point,
not proof of a routable entrance. No external business contact details are retained.
"""
import datetime,hashlib,json,pathlib,xml.etree.ElementTree as ET
from offline_build_map import OUT,write,fc,feature,norm

source=OUT/'srm-osm.xml'
root=ET.parse(source).getroot()
nodes={e.get('id'):[float(e.get('lon')),float(e.get('lat'))] for e in root.findall('node')}
search=json.loads((OUT/'search.json').read_text(encoding='utf-8'))
ids={e['id'] for e in search['entries']}
places=json.loads((OUT/'places.geojson').read_text(encoding='utf-8'))['features']
context=[];added=0
for e in root:
    tags={t.get('k'):t.get('v') for t in e.findall('tag')}
    name=tags.get('name') or tags.get('name:en') or tags.get('name:ta')
    if e.tag=='node':coords=nodes[e.get('id')];line=None
    elif e.tag=='way':
        line=[nodes[n.get('ref')] for n in e.findall('nd') if n.get('ref') in nodes]
        if not line:continue
        coords=[sum(p[0] for p in line)/len(line),sum(p[1] for p in line)/len(line)]
    else:continue
    fid=e.tag[0]+e.get('id')
    kind=tags.get('amenity') or tags.get('shop') or tags.get('place') or ('building' if tags.get('building') else 'place')
    if name and fid not in ids and not tags.get('highway'):
        aliases=sorted({v for k,v in tags.items() if k in ('name:en','name:ta','alt_name','short_name') and v!=name})
        entry={'id':fid,'name':name,'aliases':aliases,'normalized':norm(' '.join([name]+aliases)),'lon':round(coords[0],6),'lat':round(coords[1],6),'kind':kind}
        search['entries'].append(entry);ids.add(fid);added+=1
        places.append(feature(fid,[entry['lon'],entry['lat']],{'name':name,'class':kind},'Point'))
    if line and len(line)>3 and line[0]==line[-1]:
        cls='water' if tags.get('natural')=='water' or tags.get('water') else 'park' if tags.get('leisure') in ('park','garden','pitch') or tags.get('landuse') in ('grass','forest','recreation_ground') else 'building' if tags.get('building') else None
        if cls:
            props={'class':cls}
            if name:props['name']=name
            context.append(feature(fid,[[[round(x,6),round(y,6)] for x,y in line]],props,'Polygon'))
search['entries'].sort(key=lambda e:e['normalized'])
write(OUT/'search.json',search);write(OUT/'places.geojson',fc(places));write(OUT/'context-srm.geojson',fc(context))
manifest=json.loads((OUT/'manifest.json').read_text(encoding='utf-8'))
for name in ('search.json','places.geojson','context-srm.geojson'):
    raw=(OUT/name).read_bytes();manifest['files'][name]={'bytes':len(raw),'sha256':hashlib.sha256(raw).hexdigest()}
manifest['searchEntries']=len(search['entries'])
manifest['contextBounds']=[80.035,12.815,80.055,12.835]
manifest['contextNote']='SRM/Potheri context only. Buildings/water/parks outside this extract are not included. Closed ways only; multipolygon relations omitted.'
write(OUT/'manifest.json',manifest)
provenance={'url':'https://api.openstreetmap.org/api/0.6/map?bbox=80.035,12.815,80.055,12.835','fetchedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'sourceFile':'srm-osm.xml','bytes':source.stat().st_size,'sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'license':'ODbL-1.0','addedSearchEntries':added,'contextPolygons':len(context)}
write(OUT/'srm-source-manifest.json',provenance)
print(json.dumps(provenance,indent=2))
