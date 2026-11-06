import json
import math
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path
from starlette.testclient import TestClient
from app.map_overlays import active_reports, build_overlays, save_snapshot
from app.road_context import attach_road_context

class OverlayTests(unittest.TestCase):
 def test_current_reports_require_valid_time_and_not_test(self):
  good={'id':'citizen_1','created_epoch':1000,'ttl_seconds':7200,'coordinates':[80.2,13.0],'description':'Water at crossing'}
  records=[good,dict(good,id='future',created_epoch=1002),dict(good,id='expired',created_epoch=-10000),dict(good,id='badtime',created_epoch=float('nan')),dict(good,id='test',device_id='test_redmi_commuter_01'),dict(good,id='mock_1'),dict(good,id='badcoord',coordinates=[0,0])]
  accepted=active_reports(records,1001)
  self.assertEqual([x['id'] for x in accepted],['citizen_1'])
  self.assertEqual(active_reports([good],8200),[])
  self.assertEqual(active_reports([dict(good,ttl_seconds=999999)],8201),[])
 def test_real_dataset_has_no_current_depth_or_safe_routes(self):
  feed=build_overlays(report_records=[])
  self.assertEqual(feed['metadata']['counts'],{'history':1553,'infrastructure':59})
  ids=[f['id'] for f in feed['features']];self.assertEqual(len(ids),len(set(ids)))
  for f in feed['features']:
   p=f['properties'];self.assertTrue(set(['id','title','layer','evidence','detail','source','observed_at','expires_at'])<=p.keys())
   self.assertFalse(set(['severity','is_passable_pedestrian','is_passable_vehicle','water_depth_estimate_cm','recommended_action'])&p.keys())
   self.assertIsNone(p['observed_at']);self.assertIsNone(p['expires_at'])
 def test_historical_depth_conversion_and_invalid_values(self):
  from app.map_overlays import historical_depth_cm
  self.assertEqual(historical_depth_cm(7),17.8);self.assertEqual(historical_depth_cm('60'),152.4);self.assertEqual(historical_depth_cm(0),0.0)
  for bad in (None,'',' ','abc',-1,float('nan'),float('inf'),float('-inf'),True,[7]):
   self.assertIsNone(historical_depth_cm(bad),bad)
 def test_survey_points_carry_historical_depth_hotspots_do_not(self):
  feed=build_overlays(report_records=[])
  survey=[f['properties'] for f in feed['features'] if f['properties'].get('kind')=='survey_point']
  self.assertEqual(len(survey),192)
  for p in survey:
   d=p['historical_depth_cm'];self.assertIsInstance(d,float);self.assertTrue(math.isfinite(d) and d>=0)
   self.assertEqual(p['historical_depth_unit'],'cm');self.assertIn('inches',p['historical_depth_source'])
   self.assertIsNone(p['observed_at']);self.assertIn('survey date not provided',p['detail']);self.assertIn('unknown survey date (not current depth)',p['detail'])
   self.assertIn(f"{d:g} cm",p['detail'])
  first=next(p for p in survey if p['id']=='survey_inundation_40_record_1');self.assertEqual(first['historical_depth_cm'],17.8)
  for f in feed['features']:
   if f['properties'].get('kind')!='survey_point':
    self.assertFalse({'historical_depth_cm','historical_depth_unit','historical_depth_source'}&f['properties'].keys(),f['id'])
 def test_invalid_survey_depth_is_null_not_guessed(self):
  from unittest.mock import patch
  base={'type':'Feature','geometry':{'coordinates':[80.2,13.0]},'properties':{'id':'survey_inundation_x','field_remarks':'r','water_depth_estimate_cm':99.0,'severity':'CRITICAL','is_passable_vehicle':False}}
  rows=[dict(base,properties=dict(base['properties'],water_depth_inches=v)) for v in (float('nan'),-3)]+[base]
  with patch('app.map_overlays.load_surveyed_inundation_points',return_value=rows):
   feed=build_overlays(report_records=[])
  survey=[f['properties'] for f in feed['features'] if f['properties'].get('kind')=='survey_point']
  self.assertEqual(len(survey),3)
  for p in survey:
   self.assertIsNone(p['historical_depth_cm']);self.assertIsNone(p['historical_depth_source']);self.assertIn('depth unavailable or invalid',p['detail'])
   self.assertFalse({'severity','is_passable_vehicle','water_depth_estimate_cm'}&p.keys())
  json.dumps(feed,allow_nan=False)
 def test_saved_road_context_is_bounded(self):
  feed=build_overlays(report_records=[]);count=0
  for f in feed['features']:
   c=f['properties'].get('road_context')
   if c:
    count+=1;self.assertEqual(f['properties']['layer'],'history');self.assertLessEqual(c['association_distance_m'],35);self.assertTrue(c['approximate'])
    a,b=c['coordinates'];self.assertLessEqual(math.hypot((b[0]-a[0])*108450,(b[1]-a[1])*111195),120.001)
  self.assertGreater(count,200)
 def test_far_points_do_not_get_road_and_near_segment_is_clipped(self):
  with tempfile.TemporaryDirectory() as d:
   Path(d,'tile.geojson').write_text(json.dumps({'features':[{'properties':{'class':'road'},'geometry':{'type':'LineString','coordinates':[[80.0,13.0],[80.01,13.0]]}}]}))
   feed={'features':[{'geometry':{'coordinates':xy},'properties':{'layer':'history'}} for xy in ([80.005,13.0001],[80.005,13.01]) ]}
   result=attach_road_context(feed,Path(d));self.assertIn('road_context',result['features'][0]['properties']);self.assertNotIn('road_context',result['features'][1]['properties'])
 def test_snapshot_is_valid_geojson(self):
  with tempfile.TemporaryDirectory() as d:
   dest=Path(d)/'feed.geojson';feed=build_overlays(report_records=[]);save_snapshot(feed,dest);self.assertEqual(json.loads(dest.read_text(encoding='utf-8')),feed)
 def test_endpoint_no_weather_dependency(self):
  from app.main import app
  from unittest.mock import patch
  # No lifespan startup: this tests the endpoint independently of legacy weather startup.
  from unittest.mock import AsyncMock, MagicMock
  # Weather adapter injected (no network); snapshot writes stubbed so tests never clobber the offline bundle.
  with patch('app.main.get_or_refresh_hazard_feed',side_effect=AssertionError('Legacy hazard feed called')), \
       patch('app.modelled_risk.current_weather',AsyncMock(return_value=None)) as weather, \
       patch('app.map_overlays.save_snapshot',MagicMock()):
   with TestClient(app) as client:
    response=client.get('/api/v1/map-overlays/chennai')
  body=response.json();weather.assert_awaited_once()
  # Static mapped night layer is appended by the endpoint; the evidence overlay itself stays 1612 features.
  non_night=[f for f in body['features'] if f['properties']['layer']!='night']
  self.assertEqual(response.status_code,200);self.assertEqual(response.headers['cache-control'],'no-store');self.assertEqual(len(non_night),1612)
  self.assertEqual(body['metadata']['modelled_risk']['status'],'unavailable');self.assertNotIn('risk',body['metadata']['counts'])

class Flood2015Tests(unittest.TestCase):
 KML='<kml xmlns="http://www.opengis.net/kml/2.2"><Document><Folder>%s</Folder></Document></kml>'
 def pm(self,name,geom,**d):
  data=''.join('<SimpleData name="%s">%s</SimpleData>'%kv for kv in d.items())
  return '<Placemark><name>%s</name><ExtendedData><SchemaData>%s</SchemaData></ExtendedData>%s</Placemark>'%(name,data,geom)
 def test_parser_takes_only_real_points_never_polygon_centroids(self):
  from app.ingestion.flood2015 import parse_kml_points
  poly='<Polygon><outerBoundaryIs><LinearRing><coordinates>80.0,13.0 80.1,13.0 80.1,13.1 80.0,13.0</coordinates></LinearRing></outerBoundaryIs></Polygon>'
  kml=self.KML%(self.pm('a','<Point><coordinates>80.25,13.05,0</coordinates></Point>')+self.pm('poly',poly)+self.pm('bad','<Point><coordinates>x,y</coordinates></Point>')+self.pm('nan','<Point><coordinates>nan,13</coordinates></Point>'))
  rows=parse_kml_points(kml)
  self.assertEqual([r['coords'] for r in rows],[[80.25,13.05]]);self.assertEqual(rows[0]['ordinal'],1)
 def test_loader_clips_coverage_and_dedups_within_30m(self):
  from app.ingestion.flood2015 import load_flood2015_points
  with tempfile.TemporaryDirectory() as d:
   pt=lambda lon,lat:'<Point><coordinates>%s,%s</coordinates></Point>'%(lon,lat)
   Path(d,'gcc_stagnation_locations_2015.kml').write_text(self.KML%(
    self.pm('Alpha',pt(80.2500,13.0500))+self.pm('AlphaDup',pt(80.2501,13.0500))   # ~11 m: dropped
    +self.pm('Beta',pt(80.2510,13.0500))                                           # ~108 m: kept
    +self.pm('FarSouth',pt(78.5,9.6))+self.pm('Gamma',pt(80.3000,13.1000))),encoding='utf-8')   # outside coverage / near existing
   Path(d,'tiruvallur_flood_hotspots_2015.kml').write_text(self.KML%self.pm('Alpha','<Point><coordinates>80.25005,13.05</coordinates></Point>',details='Alpha again',name_municipality='X'),encoding='utf-8')
   pts,stats=load_flood2015_points(Path(d),exclude_near=[[80.3000,13.1001]])
  self.assertEqual([p['name'] for p in pts],['Alpha','Beta'])
  self.assertEqual((stats['raw'],stats['outside_coverage'],stats['duplicate_2015'],stats['duplicate_existing'],stats['kept']),(6,1,2,1,2))
  self.assertEqual(pts[0]['id'],'flood2015-gcc-stagnation-1');self.assertEqual(pts[1]['id'],'flood2015-gcc-stagnation-3')
 def test_real_flood2015_features_are_labelled_depth_unknown_and_deduplicated(self):
  from app.ingestion.flood2015 import SOURCE_URL,meters_between
  feed=build_overlays(report_records=[])
  new=[f for f in feed['features'] if f['id'].startswith('flood2015-')]
  self.assertEqual(len(new),1308)
  for f in new:
   p=f['properties'];self.assertEqual(f['geometry']['type'],'Point')
   self.assertEqual((p['layer'],p['evidence'],p['kind'],p['source_year']),('history','historical','hotspot',2015))
   self.assertTrue(p['title'].startswith('Actual 2015 flood location: '));self.assertIn('actual 2015',p['source'].lower());self.assertIn(SOURCE_URL,p['source'])
   self.assertIn('Depth unknown',p['detail']);self.assertIsNone(p['observed_at']);self.assertIsNone(p['expires_at'])
   self.assertFalse({'historical_depth_cm','water_depth_estimate_cm','road_depth_cm','severity','is_passable_vehicle','is_passable_pedestrian'}&p.keys())
   self.assertTrue(79.8<=f['geometry']['coordinates'][0]<=80.5 and 12.5<=f['geometry']['coordinates'][1]<=13.5)
  from app.ingestion.flood2015 import NearIndex
  old=NearIndex(30,[f['geometry']['coordinates'] for f in feed['features'] if f['properties']['layer']=='history' and not f['id'].startswith('flood2015-')])
  mine=NearIndex(30)
  for f in new:
   c=f['geometry']['coordinates']
   self.assertFalse(old.near(c),f['id']);self.assertFalse(mine.near(c),f['id']);mine.add(c)
  self.assertEqual(len({f['id'] for f in feed['features']}),len(feed['features']))

if __name__=='__main__':unittest.main()
