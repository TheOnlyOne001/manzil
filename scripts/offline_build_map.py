"""Build an entirely local vector road map + search index from preserved OSM data.

No public raster tile downloads. Standard library only. Coordinates are WGS84 lon/lat.
Each tile is a clipped GeoJSON FeatureCollection, ready for a MapLibre GeoJsonSource.
"""
from __future__ import annotations
import collections, hashlib, json, math, pathlib, re, unicodedata

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / 'data/offline-map'
WEST, SOUTH, EAST, NORTH = 79.95, 12.65, 80.35, 13.30
CELL = .025
MAJOR = {'motorway','trunk','primary','secondary','tertiary','motorway_link','trunk_link','primary_link','secondary_link','tertiary_link'}

def write(path, obj):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(obj,ensure_ascii=False,separators=(',',':')),encoding='utf-8')

def fc(features): return {'type':'FeatureCollection','features':features}
def feature(fid, coords, props, kind='LineString'):
    return {'type':'Feature','id':fid,'properties':props,'geometry':{'type':kind,'coordinates':coords}}
def norm(text):
    return re.sub(r'\s+',' ',unicodedata.normalize('NFKC',text).casefold()).strip()
def clip(a,b,box):
    x0,y0=a;x1,y1=b; dx=x1-x0;dy=y1-y0;lo,hi=0.,1.
    for p,q in ((-dx,x0-box[0]),(dx,box[2]-x0),(-dy,y0-box[1]),(dy,box[3]-y0)):
        if p==0:
            if q<0:return None
        else:
            t=q/p
            if p<0:lo=max(lo,t)
            else:hi=min(hi,t)
            if lo>hi:return None
    return [[round(x0+lo*dx,6),round(y0+lo*dy,6)],[round(x0+hi*dx,6),round(y0+hi*dy,6)]]

def simplify(points,tolerance=.000025):
    # Iterative Douglas-Peucker: display geometry only, never routing data.
    if len(points)<3:return points
    keep={0,len(points)-1};stack=[(0,len(points)-1)]
    while stack:
        lo,hi=stack.pop();ax,ay=points[lo];bx,by=points[hi];dx=bx-ax;dy=by-ay;den=dx*dx+dy*dy
        best,index=tolerance*tolerance,-1
        for i in range(lo+1,hi):
            px,py=points[i];t=max(0.,min(1.,((px-ax)*dx+(py-ay)*dy)/den)) if den else 0.
            d=(px-ax-t*dx)**2+(py-ay-t*dy)**2
            if d>best:best,index=d,i
        if index>=0:keep.add(index);stack.extend([(lo,index),(index,hi)])
    return [points[i] for i in sorted(keep)]

def main():
    source=ROOT/'data/osm/chennai_transport.json'
    doc=json.loads(source.read_text(encoding='utf-8'))
    nodes={e['id']:(e['lon'],e['lat']) for e in doc['elements'] if e['type']=='node'}
    tiles=collections.defaultdict(list); overview=[]; search={}; named_points=[]
    nx=round((EAST-WEST)/CELL);ny=round((NORTH-SOUTH)/CELL)
    def add_search(e,lon,lat,kind):
        tags=e.get('tags',{});name=tags.get('name') or tags.get('name:en') or tags.get('name:ta')
        if not name or not(WEST<=lon<=EAST and SOUTH<=lat<=NORTH):return
        aliases=sorted({v for k,v in tags.items() if k in ('name:en','name:ta','alt_name','short_name','official_name') and v!=name})
        key=(norm(name),kind,int((lon-WEST)/.025),int((lat-SOUTH)/.025))
        entry={'id':f"{e['type'][0]}{e['id']}",'name':name,'aliases':aliases,'normalized':norm(' '.join([name]+aliases)),'lon':round(lon,6),'lat':round(lat,6),'kind':kind}
        if key not in search:search[key]=entry
        if kind!='road':named_points.append(feature(entry['id'],[entry['lon'],entry['lat']],{'name':name,'class':kind},'Point'))
    counts=collections.Counter()
    for e in doc['elements']:
        tags=e.get('tags',{})
        if e['type']=='node':
            kind='station' if tags.get('railway') in ('station','halt') else 'stop' if tags.get('public_transport') or tags.get('highway')=='bus_stop' else 'entrance'
            add_search(e,e['lon'],e['lat'],kind)
            continue
        if e['type']!='way':continue
        highway=tags.get('highway');railway=tags.get('railway')
        if not highway and not railway:continue
        coords=[nodes[n] for n in e['nodes'] if n in nodes]
        if len(coords)<2:continue
        cls='rail' if railway and not highway else 'major' if highway in MAJOR else 'path' if highway in ('footway','path','pedestrian','steps','cycleway','bridleway') else 'road'
        props={'class':cls,'type':highway or railway}
        for key in ('name','name:ta','name:en','bridge','tunnel','layer'):
            if key in tags:props[key]=tags[key]
        mid=coords[len(coords)//2];add_search(e,*mid,'road' if highway else 'station')
        # Each segment is clipped into every grid cell it crosses, then contiguous pieces join.
        chunks=collections.defaultdict(list)
        for a,b in zip(coords,coords[1:]):
            minx=max(0,math.floor((min(a[0],b[0])-WEST)/CELL));maxx=min(nx-1,math.floor((max(a[0],b[0])-WEST)/CELL))
            miny=max(0,math.floor((min(a[1],b[1])-SOUTH)/CELL));maxy=min(ny-1,math.floor((max(a[1],b[1])-SOUTH)/CELL))
            for x in range(minx,maxx+1):
                for y in range(miny,maxy+1):
                    piece=clip(a,b,(WEST+x*CELL,SOUTH+y*CELL,WEST+(x+1)*CELL,SOUTH+(y+1)*CELL))
                    if not piece or piece[0]==piece[1]:continue
                    lines=chunks[x,y]
                    if lines and lines[-1][-1]==piece[0]:lines[-1].append(piece[1])
                    else:lines.append(piece)
        for (x,y),lines in chunks.items():
            lines=[simplify(line) for line in lines]
            fid=f"w{e['id']}_{x}_{y}"
            item=feature(fid,lines,props,'MultiLineString')
            tiles[x,y].append(item);counts[cls]+=1
            if cls in ('major','rail'):overview.append(feature(fid,[simplify(line,.00012) for line in lines],props,'MultiLineString'))
    poi_path=OUT/'poi-source.json'
    if poi_path.exists():
        for e in json.loads(poi_path.read_text(encoding='utf-8')).get('elements',[]):
            pt=e if 'lon' in e else e.get('center',{})
            if 'lon' not in pt:continue
            t=e.get('tags',{});kind=t.get('amenity') or t.get('shop') or t.get('place') or 'place'
            add_search(e,pt['lon'],pt['lat'],kind)
    tile_rows=[]
    for (x,y),features in sorted(tiles.items()):
        name=f'tiles/{x}_{y}.geojson';write(OUT/name,fc(features));raw=(OUT/name).read_bytes()
        tile_rows.append({'x':x,'y':y,'file':name,'features':len(features),'bytes':len(raw),'sha256':hashlib.sha256(raw).hexdigest()})
    write(OUT/'overview.geojson',fc(overview));write(OUT/'places.geojson',fc(named_points))
    write(OUT/'search.json',{'version':1,'entries':sorted(search.values(),key=lambda r:r['normalized'])})
    mainfiles={name:{'bytes':(OUT/name).stat().st_size,'sha256':hashlib.sha256((OUT/name).read_bytes()).hexdigest()} for name in ('overview.geojson','places.geojson','search.json')}
    manifest={'version':1,'name':'Chennai transport map','bounds':[WEST,SOUTH,EAST,NORTH],'grid':{'west':WEST,'south':SOUTH,'cellDegrees':CELL,'columns':nx,'rows':ny},'sourceTimestamp':doc.get('osm3s',{}).get('timestamp_osm_base'),'sourceSha256':hashlib.sha256(source.read_bytes()).hexdigest(),'attribution':'© OpenStreetMap contributors','license':'ODbL-1.0','licenseUrl':'https://www.openstreetmap.org/copyright','tiles':tile_rows,'files':mainfiles,'searchEntries':len(search),'featureCounts':dict(counts),'limitations':['Transport-oriented map, not complete landuse/building basemap','Display geometry is simplified and must not be used as routing graph','Named road centroids are approximate search targets, not verified entrances']}
    write(OUT/'manifest.json',manifest)
    (OUT/'ATTRIBUTION.txt').write_text('© OpenStreetMap contributors. Data licensed under ODbL 1.0.\nhttps://www.openstreetmap.org/copyright\nhttps://opendatacommons.org/licenses/odbl/1-0/\n',encoding='utf-8')
    print(json.dumps({'tiles':len(tile_rows),'searchEntries':len(search),'mainFiles':mainfiles,'tileBytes':sum(r['bytes'] for r in tile_rows),'maxTileBytes':max(r['bytes'] for r in tile_rows),'counts':dict(counts)},indent=2))

if __name__=='__main__':main()
