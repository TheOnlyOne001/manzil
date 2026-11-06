import asyncio
import json
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

from app import modelled_risk as mr
from app.map_overlays import build_overlays

NOW = datetime(2026, 11, 6, 10, 7, tzinfo=timezone.utc)
H0 = int(datetime(2026, 11, 6, 10, 0, tzinfo=timezone.utc).timestamp())
HEAVY = (0, 0, 10, 10, 10, 10, 10, 10, 0)

def raw(hourly_mm=(0,) * 9, current_time=H0, **over):
    # hourly entries end at H0-4h .. H0+4h; each value is Open-Meteo's preceding-hour sum
    r = {"utc_offset_seconds": 0, "hourly_units": {"precipitation": "mm"},
         "current": {"time": current_time, "interval": 900, "precipitation": 0.4},
         "hourly": {"time": [H0 + 3600 * k for k in range(-4, 5)], "precipitation": list(hourly_mm)}}
    r.update(over)
    return r

def summary(**kw):
    return mr.normalize_open_meteo(raw(**kw), NOW.timestamp())

class NormalizeTests(unittest.TestCase):
    def test_windows_use_preceding_hour_sums(self):
        # ends -4h..+4h; past = ends -2h,-1h,0 ; next = ends +1h,+2h,+3h
        s = summary(hourly_mm=(100, 50, 1, 2, 3, 4, 5, 6, 100))
        self.assertEqual((s["past_3h_mm"], s["next_3h_mm"], s["max_hourly_mm"]), (6.0, 15.0, 6.0))
        self.assertEqual((s["weather_time"], s["fetched_at"]), ("2026-11-06T10:00:00Z", "2026-11-06T10:07:00Z"))
        self.assertEqual(s["current_precipitation_mm_over_interval"], 0.4)
        self.assertEqual(s["current_interval_seconds"], 900)
        self.assertEqual(s["next_3h_window"], ["2026-11-06T10:00:00Z", "2026-11-06T13:00:00Z"])

    def test_iso_times_honour_utc_offset(self):
        ist = timezone(timedelta(hours=5, minutes=30))
        loc = lambda e: datetime.fromtimestamp(e, ist).strftime("%Y-%m-%dT%H:%M")
        r = raw(hourly_mm=(0, 0, 1, 1, 1, 2, 2, 2, 0))
        r["utc_offset_seconds"] = 19800
        r["current"]["time"] = loc(H0 + 900)
        r["hourly"]["time"] = [loc(t) for t in r["hourly"]["time"]]
        s = mr.normalize_open_meteo(r, NOW.timestamp())
        self.assertEqual(s["weather_time"], "2026-11-06T10:15:00Z")
        self.assertEqual((s["past_3h_mm"], s["next_3h_mm"]), (3.0, 6.0))

    def test_malformed_inputs_rejected(self):
        bad = [None, [], raw(hourly_units={"precipitation": "inch"}), raw(current=None),
               raw(hourly_mm=(0, 0, float("nan"), 0, 0, 0, 0, 0, 0)), raw(hourly_mm=(0, 0, -1, 0, 0, 0, 0, 0, 0)),
               raw(hourly_mm=(0, 0, 0, None, 0, 0, 0, 0, 0)), raw(hourly_mm=(0, 0, 0)),
               raw(current_time=H0 - 3 * 3600), raw(current_time="not-a-time"), raw(utc_offset_seconds="x"),
               raw(hourly={"time": [H0 + 60], "precipitation": [0]})]
        for key, value in (("interval", 0), ("precipitation", True)):
            r = raw(); r["current"][key] = value; bad.append(r)
        r = raw(); r["hourly"]["precipitation"][5] = "3"; bad.append(r)
        for b in bad:
            with self.assertRaises(mr.WeatherInvalid):
                mr.normalize_open_meteo(b, NOW.timestamp())

    def test_store_reload_and_corrupt_file(self):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "w.json"
            s = mr.save_weather(raw(hourly_mm=(0, 0, 1, 1, 1, 1, 1, 1, 0)), NOW.timestamp(), path)
            doc = json.loads(path.read_text(encoding="utf-8"))
            self.assertEqual((doc["fetched_at"], doc["weather_time"]), (s["fetched_at"], s["weather_time"]))
            self.assertEqual(mr.load_weather(path), s)
            with self.assertRaises(mr.WeatherInvalid):
                mr.save_weather(raw(hourly_units={}), NOW.timestamp(), Path(d) / "bad.json")
            self.assertFalse((Path(d) / "bad.json").exists())
            path.write_text("{not json", encoding="utf-8")
            self.assertIsNone(mr.load_weather(path))
            self.assertIsNone(mr.load_weather(Path(d) / "missing.json"))

class RiskLayerTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.base = build_overlays(now=NOW, report_records=[])

    def risk(self, feed):
        return [f for f in feed["features"] if f["properties"]["layer"] == "risk"]

    def test_heavy_rain_contract(self):
        feed = mr.add_modelled_risk(self.base, summary(hourly_mm=HEAVY), NOW)
        risk = self.risk(feed)
        self.assertEqual(len(risk), 192)
        self.assertEqual(feed["metadata"]["counts"], {"history": 1553, "infrastructure": 59, "risk": 192})
        block = feed["metadata"]["modelled_risk"]
        self.assertEqual((block["status"], block["rain_tier"], block["feature_count"]), ("available", 3, 192))
        self.assertFalse(block["method"]["calibrated"]); self.assertTrue(block["limitations"])
        hist = {f["id"]: f for f in self.base["features"]}
        with_road = 0
        for f in risk:
            p = f["properties"]
            self.assertEqual((p["layer"], p["evidence"], p["kind"], p["confidence"]), ("risk", "modelled", "rainfall_proxy", "low"))
            self.assertIn(p["risk_band"], ("elevated", "high"))
            self.assertEqual(p["observed_at"], "2026-11-06T10:00:00Z")
            exp = datetime.fromisoformat(p["expires_at"].replace("Z", "+00:00"))
            self.assertTrue(NOW < exp <= datetime(2026, 11, 6, 12, 0, tzinfo=timezone.utc))
            h = hist[p["historical_feature_id"]]
            self.assertEqual(f["geometry"], h["geometry"])
            self.assertEqual(p.get("road_context"), h["properties"].get("road_context"))
            with_road += "road_context" in p
            self.assertFalse({"severity", "is_passable_pedestrian", "is_passable_vehicle", "water_depth_estimate_cm",
                              "historical_depth_cm", "water_depth_cm", "probability", "recommended_action"} & p.keys())
            self.assertIn("not a probability, depth or passability", p["detail"])
        self.assertGreater(with_road, 150)
        self.assertEqual({f["properties"]["risk_band"] for f in risk if f["properties"]["basis"]["depth_susceptibility"] >= 1}, {"high"})
        self.assertNotIn("risk", self.base["metadata"]["counts"])  # input not mutated
        json.dumps(feed, allow_nan=False)

    def test_band_rule(self):
        self.assertEqual([mr._band(s) for s in (1, 2, 3, 4, 5)], ["watch", "elevated", "elevated", "high", "high"])
        self.assertEqual([mr._susceptibility(v) for v in (None, float("nan"), -1, 0, 14.9, 15, 29.9, 30, 152.4)],
                         [0, 0, 0, 0, 0, 1, 1, 2, 2])
        self.assertEqual(mr._rain_tier(summary(hourly_mm=(0, 0, 0, 0, 0, 16, 0, 0, 0))), 2)  # burst rule
        light = mr.add_modelled_risk(self.base, summary(hourly_mm=(0, 0, 1, 1, 1, 1, 1, 0, 0)), NOW)
        bands = {f["properties"]["risk_band"] for f in self.risk(light)}
        self.assertTrue(bands <= {"watch", "elevated"} and "watch" in bands)

    def test_dry_weather_available_but_no_features(self):
        feed = mr.add_modelled_risk(self.base, summary(hourly_mm=(0, 0, 1, 1, 0, 1, 1, 0, 0)), NOW)
        self.assertEqual(self.risk(feed), [])
        self.assertEqual(feed["metadata"]["modelled_risk"]["status"], "available")
        self.assertEqual(feed["metadata"]["modelled_risk"]["reason"], "rain_below_watch_threshold")

    def test_missing_or_stale_weather_gives_no_features(self):
        heavy = summary(hourly_mm=HEAVY)
        cases = ((None, NOW, "weather_missing_or_malformed"),
                 (heavy, datetime(2026, 11, 6, 12, 0, tzinfo=timezone.utc), "weather_stale"),
                 (heavy, datetime(2026, 11, 6, 9, 50, tzinfo=timezone.utc), "weather_stale"))
        for weather, now, reason in cases:
            feed = mr.add_modelled_risk(self.base, weather, now)
            self.assertEqual(self.risk(feed), [])
            block = feed["metadata"]["modelled_risk"]
            self.assertEqual((block["status"], block["reason"]), ("unavailable", reason))
            self.assertEqual(feed["metadata"]["counts"], {"history": 1553, "infrastructure": 59})
        with self.assertRaises(ValueError):
            mr.add_modelled_risk(self.base, heavy, NOW.replace(tzinfo=None))

    def test_reapplying_replaces_risk_layer(self):
        once = mr.add_modelled_risk(self.base, summary(hourly_mm=HEAVY), NOW)
        self.assertEqual(self.risk(mr.add_modelled_risk(once, None, NOW)), [])
        self.assertEqual(len(self.risk(mr.add_modelled_risk(once, summary(hourly_mm=HEAVY), NOW))), 192)

    def test_flyovers_stay_unverified(self):
        flyovers = [f for f in self.base["features"] if f["properties"]["kind"] == "flyover"]
        self.assertTrue(flyovers)
        for f in flyovers:
            self.assertIn("unverified", f["properties"]["detail"])

class AdapterTests(unittest.TestCase):
    def setUp(self):
        mr.reset_cache()

    def tearDown(self):
        mr.reset_cache()

    def test_cache_lock_and_fallback(self):
        calls, clock = [], [NOW.timestamp()]
        async def fetcher(timeout):
            calls.append(timeout); await asyncio.sleep(0.01); return raw(hourly_mm=(0, 0, 5, 5, 5, 5, 5, 5, 0))
        async def failing(timeout):
            calls.append(timeout); raise OSError("offline")
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "w.json"
            tick = lambda: clock[0]
            async def run():
                a, b = await asyncio.gather(mr.current_weather(fetcher, tick, path), mr.current_weather(fetcher, tick, path))
                self.assertEqual(a, b); self.assertEqual(len(calls), 1); self.assertLessEqual(calls[0], 8)
                self.assertTrue(path.exists())
                clock[0] += 599; await mr.current_weather(failing, tick, path); self.assertEqual(len(calls), 1)  # cached
                clock[0] += 2; c = await mr.current_weather(failing, tick, path)
                self.assertEqual(len(calls), 2); self.assertEqual(c, a)  # fell back to stored validated file
                path.unlink(); mr.reset_cache()
                self.assertIsNone(await mr.current_weather(failing, tick, path))
            asyncio.run(run())

if __name__ == "__main__":
    unittest.main()
