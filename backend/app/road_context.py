"""Associate actual nearby OSM segments; never infer flood extent."""
import json, math
from pathlib import Path

def attach_road_context(feed:dict,tiles:Path)->dict:
 targets=[f for f in feed['features'] if f['properties']['layer']=='history'];buckets={};needed=set()
 def xy(c):return c[0]*108450,c[1]*111195
 def key(p):return int(p[0]//100),int(p[1]//100)
 for f in targets:
  x,y=key(xy(f['geometry']['coordinates']));needed.update((x+dx,y+dy) for dx in (-1,0,1) for dy in (-1,0,1))
 for file in tiles.glob('*.geojson'):
  for f in json.loads(file.read_text(encoding='utf-8'))['features']:
   g=f['geometry'];p=f.get('properties',{})
   if p.get('class') not in ('road','path','major'):continue
   lines=[g['coordinates']] if g['type']=='LineString' else g['coordinates'] if g['type']=='MultiLineString' else []
   for line in lines:
    for a,b in zip(line,line[1:]):
     aa,bb=xy(a),xy(b);ka,kb=key(aa),key(bb)
     for x in range(min(ka[0],kb[0]),max(ka[0],kb[0])+1):
      for y in range(min(ka[1],kb[1]),max(ka[1],kb[1])+1):
       if (x,y) in needed:buckets.setdefault((x,y),[]).append((a,b,aa,bb,p.get('name')))
 for f in targets:
  q=xy(f['geometry']['coordinates']);k=key(q);best=None
  for dx in (-1,0,1):
   for dy in (-1,0,1):
    for a,b,aa,bb,name in buckets.get((k[0]+dx,k[1]+dy),[]):
     vx,vy=bb[0]-aa[0],bb[1]-aa[1];length2=vx*vx+vy*vy
     if length2<1:continue
     t=max(0,min(1,((q[0]-aa[0])*vx+(q[1]-aa[1])*vy)/length2));d=math.hypot(q[0]-aa[0]-t*vx,q[1]-aa[1]-t*vy)
     if d<=35 and (best is None or d<best[0]):best=(d,a,b,t,math.sqrt(length2),name)
  if best:
   d,a,b,t,length,name=best;coords=[[a[0]+(b[0]-a[0])*v,a[1]+(b[1]-a[1])*v] for v in (max(0,t-60/length),min(1,t+60/length))]
   f['properties']['road_context']={'type':'LineString','coordinates':coords,'association_distance_m':round(d,2),'approximate':True,'detail':'Nearby OSM display road segment; approximate association only, not observed flood extent.','source':'OpenStreetMap display geometry (simplified)','road_name':name}
 return feed
