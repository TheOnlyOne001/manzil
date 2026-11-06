import asyncio
import json
import math
import unittest
from pathlib import Path
from unittest.mock import AsyncMock, MagicMock, patch

from starlette.testclient import TestClient

from app import night_context as nc
from app.map_overlays import build_overlays

BASE_PROPS = {"id", "title", "layer", "evidence", "kind", "detail", "source", "observed_at", "expires_at"}

def way(i, nodes, **tags):
    return {"type": "way", "id": i, "nodes": nodes, "tags": {"highway": "residential", **tags}}

def node(i, lon, lat):
    return {"type": "node", "id": i, "lon": lon, "lat": lat}

class TagRuleTests(unittest.TestCase):
    def test_explicit_lighting_only(self):
        for v in ("yes", "24/7", "sunset-sunrise"):
            self.assertEqual(nc.lighting_kind({"lit": v}), "night_lit")
        self.assertEqual(nc.lighting_kind({"lit": "no"}), "night_unlit")
        for tags in ({}, {"lit": "limited"}, {"lit": "automatic"}, {"lit": "Yes "}, {"highway": "primary", "lanes": "6"}):
            self.assertEqual(nc.lighting_kind(tags), "night_unknown", tags)

    def test_access_filter(self):
        blocked = [{"highway": "motorway"}, {"highway": "motorway_link"}, {"highway": "construction"},
                   {"highway": "proposed"}, {"highway": "residential", "construction": "yes"},
                   {"highway": "trunk", "motorroad": "yes"}, {"highway": "primary", "foot": "no"},
                   {"highway": "primary", "foot": "use_sidepath"}, {"highway": "service", "access": "private"},
                   {"highway": "residential", "access": "no"}, {"highway": "cycleway"}, {"highway": "corridor"},
                   {"highway": "trunk", "bridge": "yes", "layer": "1"}, {"highway": "platform"}]
        for t in blocked:
            self.assertFalse(nc.pedestrian_allowed(t), t)
        allowed = [{"highway": "residential"}, {"highway": "footway"}, {"highway": "service", "access": "private", "foot": "yes"},
                   {"highway": "trunk", "bridge": "yes", "layer": "1", "foot": "yes"}, {"highway": "cycleway", "foot": "designated"},
                   {"highway": "footway", "bridge": "yes", "layer": "1"}, {"highway": "trunk", "motorroad": "no"}]
        for t in allowed:
            self.assertTrue(nc.pedestrian_allowed(t), t)

    def test_geometry_helpers(self):
        line = [[80.0, 13.0], [80.0005, 13.00001], [80.001, 13.0]]
        self.assertEqual(len(nc.simplify(line)), 2)  # ~1 m deviation dropped at 4 m tolerance
        self.assertEqual(len(nc.simplify([[80.0, 13.0], [80.0005, 13.001], [80.001, 13.0]])), 3)
        mid = nc.midpoint([[80.0, 13.0], [80.002, 13.0]])
        self.assertAlmostEqual(mid[0], 80.001); self.assertAlmostEqual(mid[1], 13.0)

class SyntheticBuildTests(unittest.TestCase):
    def osm(self):
        far = (80.25, 13.05)  # central Chennai, far from SRM
        srm = nc.SRM_CENTER
        els = [node(1, *far), node(2, far[0] + 0.001, far[1]), node(3, far[0] + 0.002, far[1] + 0.0005),
               node(4, srm[0], srm[1]), node(5, srm[0] + 0.001, srm[1]), node(6, 81.5, 13.0), node(7, 81.6, 13.0)]
        els += [way(10, [1, 2, 3], lit="yes", name="Lit Street"), way(11, [1, 2], lit="no"), way(12, [1, 2]),
                way(13, [1, 2], lit="yes", highway="motorway"), way(14, [1, 2], lit="yes", access="private"),
                way(15, [4, 5], name="Campus lane"), way(16, [6, 7], lit="yes"), way(17, [1, 2], lit="limited"),
                way(18, [4, 5], highway="service", service="driveway"), way(19, [1, 2], lit="yes", highway="construction")]
        return {"osm3s": {"timestamp_osm_base": "2026-11-05T14:26:06Z"}, "elements": els}

    def pois(self):
        return {"fetched_at": "2026-11-06T03:00:00Z", "response": {"elements": [
            {"type": "node", "id": 1, "lat": 13.05, "lon": 80.25, "tags": {"amenity": "pharmacy", "name": "Real Pharmacy", "opening_hours": "24/7"}},
            {"type": "way", "id": 1, "center": {"lat": 13.06, "lon": 80.26}, "tags": {"amenity": "police", "name": "Real Police Station"}},
            {"type": "node", "id": 2, "lat": 13.05, "lon": 80.25, "tags": {"amenity": "fuel"}},                 # unnamed: skipped
            {"type": "node", "id": 3, "lat": 13.05, "lon": 80.25, "tags": {"amenity": "atm", "name": "ATM"}},   # not support kind
            {"type": "node", "id": 4, "lat": 14.5, "lon": 80.25, "tags": {"amenity": "hospital", "name": "Far"}}]}}

    def test_contract_and_filters(self):
        doc = nc.build_night_context(self.osm(), self.pois(), generated_at="2026-11-06T03:00:00Z")
        by = {f["id"]: f for f in doc["features"]}
        self.assertEqual(set(by), {"night_lit_w10", "night_unlit_w11", "night_unknown_w17", "night_unknown_w15",
                                   "night_support_pharmacy_n1", "night_support_police_w1"})
        lit = by["night_lit_w10"]["properties"]
        self.assertEqual(lit["road_context"]["coordinates"][0], [80.25, 13.05])
        self.assertEqual(lit["road_context"]["coordinates"][-1], [80.252, 13.0505])
        self.assertTrue(lit["road_context"]["approximate"]); self.assertEqual(lit["lit_tag"], "yes")
        self.assertEqual(by["night_unknown_w15"]["properties"]["selection"], "srm_potheri_local")
        self.assertEqual(by["night_support_pharmacy_n1"]["properties"]["opening_hours"], "24/7")
        police = by["night_support_police_w1"]["properties"]
        self.assertEqual((police["opening_hours"], police["support_kind"], police["title"]), ("unknown", "police", "Real Police Station"))
        for f in doc["features"]:
            p = f["properties"]
            self.assertTrue(BASE_PROPS <= p.keys()); self.assertTrue(nc.valid_night_feature(f), f["id"])
            self.assertNotIn("open now", p["detail"].replace("Open now, staffing", ""))
        self.assertEqual(doc["metadata"]["selection_stats"]["skipped_unnamed"], 1)

    def test_no_pois_means_no_support_and_cap(self):
        doc = nc.build_night_context(self.osm(), None)
        self.assertFalse([f for f in doc["features"] if f["properties"]["kind"] == "night_support"])
        self.assertEqual(len(nc.build_night_context(self.osm(), self.pois(), max_features=3)["features"]), 3)

def kml_placemark(station, pscode, awps, coords, oid="1"):
    return (f'<Placemark><ExtendedData><SchemaData schemaUrl="#p"><SimpleData name="OBJECTID">{oid}</SimpleData>'
            f'<SimpleData name="STATION">{station}</SimpleData><SimpleData name="PSCODE">{pscode}</SimpleData>'
            f'<SimpleData name="AWPS">{awps}</SimpleData></SchemaData></ExtendedData>'
            f'<Point><coordinates>{coords}</coordinates></Point></Placemark>')

class AwpsExtractionTests(unittest.TestCase):
    def extract(self, *placemarks, prefix=""):
        import tempfile
        xml = ('<?xml version="1.0"?>' + prefix + '<kml xmlns="http://www.opengis.net/kml/2.2"><Document><Folder>'
               + "".join(placemarks) + "</Folder></Document></kml>")
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / "dir.kml"
            p.write_text(xml, encoding="utf-8")
            return nc.awps_features(p)

    def test_only_explicit_awps_stations_with_valid_unique_ids(self):
        feats, stats = self.extract(
            kml_placemark("W26 AWPS", "R3", "W26", "80.2172,13.0439", "102"),
            kml_placemark("AWPS", "G1", "AWPS", "80.2604,13.0871,0"),                 # bare AWPS: no number invented
            kml_placemark("ADYAR", "R1", "", "80.25,13.0"),                           # ordinary station near AWPS: ignored
            kml_placemark("W26 AWPS", "R3", "W26", "80.21,13.04"),                   # duplicate station id
            kml_placemark("W9 AWPS", "V1", "W9", "81.9,13.1"),                       # outside bbox
            kml_placemark("W8 AWPS", "V5", "W8", "nan,13.1"),                        # unusable coordinate
            kml_placemark("W7 AWPS", "K7", "W3", "80.21,13.09"),                     # STATION/AWPS code mismatch
            kml_placemark("W6 AWPS", "", "W6", "80.23,13.09"))                       # no station id
        self.assertEqual([f["id"] for f in feats], ["night_support_police_awps_g1", "night_support_police_awps_r3"])
        r3 = next(f for f in feats if f["id"].endswith("_r3"))
        p = r3["properties"]
        self.assertEqual((p["title"], p["kind"], p["support_kind"], p["opening_hours"]),
                         ("W26 All Women Police Station", "night_support", "police", "unknown"))
        self.assertEqual(r3["geometry"]["coordinates"], [80.2172, 13.0439])
        self.assertEqual(p["source_url"], nc.AWPS_SOURCE_URL); self.assertIn(nc.AWPS_SOURCE_URL, p["source"])
        self.assertIn("not live-checked", p["detail"]); self.assertIn("Hours unknown", p["detail"])
        bare = next(f for f in feats if f["id"].endswith("_g1"))["properties"]
        self.assertEqual((bare["title"], bare["awps_code"]), ("All Women Police Station (station code G1)", None))
        for f in feats:
            self.assertTrue(nc.valid_night_feature(f), f["id"])
        self.assertEqual((stats["support_police_awps"], stats["awps_skipped_duplicate_id"], stats["awps_skipped_code_mismatch"],
                          stats["awps_skipped_bad_station_id"], stats["awps_skipped_bad_or_outside_coordinates"]), (2, 1, 1, 1, 2))
        self.assertEqual(stats["awps_placemarks_total"], 8)

    def test_dtd_and_missing_file_yield_nothing(self):
        feats, stats = self.extract(kml_placemark("W1 AWPS", "F4", "W1", "80.25,13.06"), prefix='<!DOCTYPE kml [<!ENTITY x "y">]>')
        self.assertEqual((feats, stats["awps_source"]), ([], "rejected_dtd"))
        self.assertEqual(nc.awps_features(Path("does-not-exist.kml")), ([], {"awps_source": "unavailable"}))

    def test_build_keeps_awps_under_cap_and_is_cache_visible(self):
        import tempfile
        awps = self.extract(kml_placemark("W1 AWPS", "F4", "W1", "80.25,13.06"))
        osm = SyntheticBuildTests().osm()
        doc = nc.build_night_context(osm, SyntheticBuildTests().pois(), max_features=2, awps=awps)
        self.assertIn("night_support_police_awps_f4", {f["id"] for f in doc["features"]})
        self.assertEqual(doc["metadata"]["support_counts"]["police"], 1)
        with tempfile.TemporaryDirectory() as d:  # regenerated file replaces the mtime/size-keyed cache entry
            path = Path(d) / "night.geojson"
            nc.write_geojson(nc.build_night_context(osm, None), path)
            before = {f["id"] for f in nc.load_night_context(path)["features"]}
            nc.write_geojson(doc, path)
            after = {f["id"] for f in nc.load_night_context(path)["features"]}
        self.assertNotIn("night_support_police_awps_f4", before); self.assertIn("night_support_police_awps_f4", after)

    def test_generated_file_has_every_explicit_awps_row_of_real_kml(self):
        if not nc.AWPS_KML.is_file():
            self.skipTest("KML not present")
        expected, stats = nc.awps_features()
        self.assertEqual(len(expected), stats["support_police_awps"])
        doc = json.loads(nc.NIGHT_FILE.read_text(encoding="utf-8"))
        got = [f for f in doc["features"] if "awps_code" in f["properties"]]
        self.assertEqual(sorted(f["id"] for f in got), [f["id"] for f in expected])
        self.assertTrue(all("women" in f["properties"]["title"].lower() for f in got))
        self.assertEqual(len({f["id"] for f in doc["features"]}), len(doc["features"]))

class GeneratedFileTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.path = nc.NIGHT_FILE
        cls.doc = json.loads(cls.path.read_text(encoding="utf-8"))
        osm = json.loads(nc.OSM_TRANSPORT.read_text(encoding="utf-8"))
        cls.nodes = {e["id"]: (e["lon"], e["lat"]) for e in osm["elements"] if e["type"] == "node"}
        cls.ways = {e["id"]: e for e in osm["elements"] if e["type"] == "way"}
        cls.pois = json.loads(nc.SUPPORT_POIS.read_text(encoding="utf-8")) if nc.SUPPORT_POIS.is_file() else None

    def test_ids_coords_size_and_counts(self):
        feats = self.doc["features"]
        self.assertLessEqual(len(feats), nc.MAX_FEATURES)
        self.assertLess(self.path.stat().st_size, 2_000_000)
        ids = [f["id"] for f in feats]; self.assertEqual(len(ids), len(set(ids)))
        kinds = {f["properties"]["kind"] for f in feats}
        self.assertTrue({"night_lit", "night_unlit", "night_unknown"} <= kinds)
        for f in feats:
            self.assertTrue(nc.valid_night_feature(f), f["id"])
            self.assertTrue(BASE_PROPS <= f["properties"].keys())
        self.assertEqual(self.doc["metadata"]["kind_counts"], dict(__import__("collections").Counter(f["properties"]["kind"] for f in feats)))
        self.assertIsNotNone(nc.load_night_context(self.path))

    def test_roads_use_actual_osm_geometry_and_explicit_tags(self):
        for f in self.doc["features"]:
            p = f["properties"]
            if p["kind"] == "night_support":
                continue
            ways = [self.ways[i] for i in p["osm_way_ids"]]
            for w in ways:
                self.assertTrue(nc.pedestrian_allowed(w["tags"]), w["id"])
                self.assertEqual(w["tags"].get("lit"), p["lit_tag"] if len(ways) == 1 else None)
            lit = ways[0]["tags"].get("lit")
            if p["kind"] == "night_lit":
                self.assertIn(lit, nc.LIT_VALUES)
            elif p["kind"] == "night_unlit":
                self.assertEqual(lit, "no")
            else:
                self.assertNotIn(lit, nc.LIT_VALUES | nc.UNLIT_VALUES)
                self.assertIn(p["selection"], ("srm_potheri_local", "named_major_road", "lit_tag_non_explicit"))
            actual = {(round(self.nodes[n][0], 6), round(self.nodes[n][1], 6)) for w in ways for n in w["nodes"]}
            line = p["road_context"]["coordinates"]
            for c in line:  # simplified line keeps only real way vertices
                self.assertIn(tuple(c), actual, f["id"])
            # point lies on (within 1 m of) the simplified-from geometry
            pt = f["geometry"]["coordinates"]
            def segment_distance(a,b):
                dx=(b[0]-a[0])*nc.M_PER_DEG_LON;dy=(b[1]-a[1])*nc.M_PER_DEG_LAT
                px=(pt[0]-a[0])*nc.M_PER_DEG_LON;py=(pt[1]-a[1])*nc.M_PER_DEG_LAT
                t=max(0,min(1,(px*dx+py*dy)/(dx*dx+dy*dy))) if dx*dx+dy*dy else 0
                return math.hypot(px-t*dx,py-t*dy)
            d=min(segment_distance(a,b) for a,b in zip(line,line[1:]))
            self.assertLess(d, 1.0, f["id"])

    def _dense(self, ways):
        for w in ways:
            cs = [self.nodes[n] for n in w["nodes"]]
            for a, b in zip(cs, cs[1:]):
                for k in range(21):
                    yield (a[0] + (b[0] - a[0]) * k / 20, a[1] + (b[1] - a[1]) * k / 20)

    def test_support_pois_are_real(self):
        support = [f for f in self.doc["features"] if f["properties"]["kind"] == "night_support"]
        if self.pois is None:
            self.assertEqual(support, []); return
        real = {(e["type"][0], e["id"]): e for e in self.pois["response"]["elements"]}
        for f in support:
            p = f["properties"]
            if "awps_code" in p:  # KML-sourced AWPS rows are covered by AwpsExtractionTests
                continue
            e = real[(f["id"].rsplit("_", 1)[1][0], p["osm_id"])]
            self.assertEqual(e["tags"]["amenity"], p["support_kind"])
            self.assertEqual(p["title"], (e["tags"].get("name") or e["tags"].get("name:en")).strip())
            self.assertEqual(p["opening_hours"], e["tags"].get("opening_hours", "unknown").strip() or "unknown")

    def test_representative_srm_and_chennai_features(self):
        feats = self.doc["features"]
        srm = [f for f in feats if f["properties"].get("selection") == "srm_potheri_local"]
        self.assertTrue(srm)
        for f in srm:
            self.assertTrue(nc.near_srm(f["properties"]["road_context"]["coordinates"]))
        central = [f for f in feats if f["properties"]["kind"] == "night_lit" and 13.0 <= f["geometry"]["coordinates"][1] <= 13.1]
        self.assertTrue(central)

class IntegrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.base = build_overlays(report_records=[])
        cls.night = nc.load_night_context()

    def test_add_night_context_is_pure_and_idempotent(self):
        before = json.dumps(self.base, sort_keys=True)
        out = nc.add_night_context(self.base, self.night)
        self.assertEqual(json.dumps(self.base, sort_keys=True), before)
        self.assertEqual(out["metadata"]["counts"], {"history": 1553, "infrastructure": 59, "night": len(self.night["features"])})
        self.assertEqual(out["metadata"]["night_context"]["status"], "available")
        again = nc.add_night_context(out, self.night)
        self.assertEqual(len(again["features"]), len(out["features"]))
        none = nc.add_night_context(out, None)
        self.assertEqual(none["metadata"]["counts"], {"history": 1553, "infrastructure": 59})
        self.assertEqual(none["metadata"]["night_context"]["reason"], "night_context_missing_or_malformed")
        json.dumps(out, allow_nan=False)

    def test_invalid_features_dropped_and_missing_file(self):
        bad = {"metadata": {}, "features": [
            {"type": "Feature", "id": "x", "geometry": {"type": "Point", "coordinates": [0, 0]},
             "properties": {"id": "x", "title": "t", "layer": "night", "evidence": "mapped", "kind": "night_lit",
                            "detail": "", "source": "", "observed_at": None, "expires_at": None}}]}
        self.assertEqual(nc.add_night_context(self.base, bad)["metadata"]["night_context"]["feature_count"], 0)
        self.assertIsNone(nc.load_night_context(Path("does-not-exist.geojson")))

    def test_endpoint_includes_night_layer(self):
        from app.main import app
        with patch("app.main.get_or_refresh_hazard_feed", side_effect=AssertionError("Legacy hazard feed called")), \
             patch("app.modelled_risk.current_weather", AsyncMock(return_value=None)), \
             patch("app.map_overlays.save_snapshot", MagicMock()) as save:
            with TestClient(app) as client:  # runs startup too (snapshot write is stubbed)
                body = client.get("/api/v1/map-overlays/chennai").json()
        night = [f for f in body["features"] if f["properties"]["layer"] == "night"]
        self.assertEqual(len(night), len(self.night["features"]))
        self.assertEqual(body["metadata"]["counts"]["night"], len(night))
        self.assertEqual(body["metadata"]["night_context"]["status"], "available")
        self.assertEqual(len(body["features"]) - len(night), 1612)
        self.assertLess(len(json.dumps(body, separators=(",", ":"))), 5_000_000)
        startup_feed = save.call_args_list[0].args[0]  # startup write happens before the request
        self.assertEqual(startup_feed["metadata"]["counts"]["night"], len(night))

    def test_combined_snapshot_contains_all_layers(self):
        snap = json.loads((nc.DERIVED_DATA_DIR / "chennai_map_overlays.geojson").read_text(encoding="utf-8"))
        counts = snap["metadata"]["counts"]
        self.assertEqual((counts["history"], counts["infrastructure"], counts["night"]), (1553, 59, len(self.night["features"])))
        self.assertIn("night_context", snap["metadata"]); self.assertIn("modelled_risk", snap["metadata"])

if __name__ == "__main__":
    unittest.main()
