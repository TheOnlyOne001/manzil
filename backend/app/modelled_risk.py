"""Low-confidence, weather-conditioned susceptibility proxy for the map overlay feed.

This is NOT a flood observation, a water depth, a likelihood or a passability claim. It combines one
city-wide Open-Meteo precipitation forecast (single grid point at the Chennai centre) with historical
GCC survey depths to flag *previously inundated* survey locations worth watching while rain is falling
or forecast. Thresholds below are uncalibrated heuristics.

Open-Meteo semantics (https://open-meteo.com/en/docs, verified 2026-11-06):
  * hourly.precipitation = "sum of the preceding hour" (mm); entry at time T covers (T-1h, T].
  * current.precipitation = backward-looking sum over current.interval seconds (e.g. 900 s = 15 min),
    so it is an interval amount, not mm/hr. It is recorded only, never used for banding.
  * timeformat=unixtime -> seconds since epoch UTC; ISO strings without an offset are local to
    utc_offset_seconds, which we honour when parsing.
"""
from __future__ import annotations

import asyncio
import copy
import json
import math
import os
import tempfile
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Awaitable, Callable

from app.config import CHENNAI_BOUNDS, DERIVED_DATA_DIR, OPEN_METEO_BASE

WEATHER_FILE = DERIVED_DATA_DIR / "chennai_risk_weather.json"
SCHEMA_VERSION = 1
FETCH_TIMEOUT_S = 8.0
REFRESH_CACHE_S = 600
MAX_VALIDITY = timedelta(hours=2)
MAX_WEATHER_FETCH_SKEW = timedelta(hours=1)
MAX_FUTURE_FETCH_SKEW = timedelta(minutes=5)

REQUEST_PARAMS = {
    "latitude": CHENNAI_BOUNDS["center_lat"], "longitude": CHENNAI_BOUNDS["center_lng"],
    "current": "precipitation", "hourly": "precipitation",
    "past_hours": 4, "forecast_hours": 5,
    "timezone": "GMT", "timeformat": "unixtime", "precipitation_unit": "mm",
}

# Uncalibrated heuristic thresholds (documented in feed metadata and feature detail).
RAIN_6H_TIERS_MM = (5.0, 15.0, 35.0)        # past3h+next3h sum -> rain tier 1/2/3 (0 below 5 mm)
BURST_HOURLY_MM = 15.0                      # any single hour >= this forces rain tier >= 2
DEPTH_TIERS_CM = (15.0, 30.0)               # historical depth -> susceptibility 1/2 (0 below / unknown)
BANDS = ((4, "high"), (2, "elevated"), (1, "watch"))  # rain_tier + susceptibility, needs rain_tier >= 1

LIMITATIONS = [
    "City-wide weather proxy from one Open-Meteo forecast grid point; not a street sensor or observation.",
    "Uncalibrated heuristic: risk_band is not a probability, not a water depth and not a passability or dry-route guarantee.",
    "Only historically surveyed GCC inundation locations are scored; unscored places may still flood.",
    "Historical survey depths have no survey date and are not current depths.",
    "Forecast rainfall may be wrong; features expire within 2 hours of the weather timestamp.",
]

class WeatherInvalid(ValueError):
    pass

def iso(epoch: float) -> str:
    return datetime.fromtimestamp(epoch, timezone.utc).isoformat().replace("+00:00", "Z")

def _num(value: Any, what: str) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
        raise WeatherInvalid(f"{what} is not a finite number")
    return float(value)

def _amount(value: Any, what: str) -> float:
    v = _num(value, what)
    if v < 0 or v > 500:
        raise WeatherInvalid(f"{what} outside 0..500 mm")
    return v

def parse_time(value: Any, utc_offset_seconds: int) -> float:
    """Open-Meteo time -> UTC epoch. Accepts unixtime or ISO 8601 (naive = local to utc_offset_seconds)."""
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        return _num(value, "time")
    if not isinstance(value, str) or not value:
        raise WeatherInvalid("time missing")
    try:
        dt = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as e:
        raise WeatherInvalid(f"bad time {value!r}") from e
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone(timedelta(seconds=utc_offset_seconds)))
    return dt.timestamp()

def normalize_open_meteo(raw: Any, fetched_at: float) -> dict:
    """Validate a raw Open-Meteo response and summarise rain around the current hour. Raises WeatherInvalid."""
    if not isinstance(raw, dict):
        raise WeatherInvalid("response is not an object")
    offset = raw.get("utc_offset_seconds", 0)
    if isinstance(offset, bool) or not isinstance(offset, int) or abs(offset) > 14 * 3600:
        raise WeatherInvalid("bad utc_offset_seconds")
    if (raw.get("hourly_units") or {}).get("precipitation") != "mm":
        raise WeatherInvalid("hourly precipitation unit is not mm")
    current, hourly = raw.get("current"), raw.get("hourly")
    if not isinstance(current, dict) or not isinstance(hourly, dict):
        raise WeatherInvalid("current/hourly block missing")
    weather_time = parse_time(current.get("time"), offset)
    interval = current.get("interval")
    if isinstance(interval, bool) or not isinstance(interval, int) or not 0 < interval <= 3600:
        raise WeatherInvalid("bad current.interval")
    current_mm = _amount(current.get("precipitation"), "current.precipitation")
    fetched_at = _num(fetched_at, "fetched_at")
    if abs(fetched_at - weather_time) > MAX_WEATHER_FETCH_SKEW.total_seconds():
        raise WeatherInvalid("weather time too far from fetch time")
    times, amounts = hourly.get("time"), hourly.get("precipitation")
    if not isinstance(times, list) or not isinstance(amounts, list) or len(times) != len(amounts):
        raise WeatherInvalid("hourly arrays malformed")
    series: dict[float, float] = {}
    for t, v in zip(times, amounts):
        end = parse_time(t, offset)
        if end % 3600:
            raise WeatherInvalid("hourly time not on the hour")
        if v is not None:
            series[end] = _amount(v, "hourly.precipitation")
    h0 = math.floor(weather_time / 3600) * 3600
    past_ends = [h0 - 7200, h0 - 3600, h0]                  # covers (h0-3h, h0]
    next_ends = [h0 + 3600, h0 + 7200, h0 + 10800]          # covers (h0, h0+3h]
    missing = [e for e in past_ends + next_ends if e not in series]
    if missing:
        raise WeatherInvalid("hourly window incomplete around current hour")
    past = [series[e] for e in past_ends]
    nxt = [series[e] for e in next_ends]
    return {
        "source": "Open-Meteo forecast API (" + OPEN_METEO_BASE + "), single city-centre grid point",
        "latitude": REQUEST_PARAMS["latitude"], "longitude": REQUEST_PARAMS["longitude"],
        "fetched_at": iso(fetched_at), "weather_time": iso(weather_time),
        "current_interval_seconds": interval, "current_precipitation_mm_over_interval": current_mm,
        "past_3h_window": [iso(h0 - 10800), iso(h0)], "next_3h_window": [iso(h0), iso(h0 + 10800)],
        "past_3h_mm": round(sum(past), 2), "next_3h_mm": round(sum(nxt), 2),
        "max_hourly_mm": round(max(past + nxt), 2),
        "hourly_mm": [{"end": iso(e), "mm": series[e]} for e in past_ends + next_ends],
    }

async def fetch_open_meteo(timeout: float = FETCH_TIMEOUT_S) -> dict:
    import httpx
    timeout = min(timeout, FETCH_TIMEOUT_S)
    async with httpx.AsyncClient(timeout=httpx.Timeout(timeout)) as client:
        resp = await asyncio.wait_for(client.get(OPEN_METEO_BASE, params=REQUEST_PARAMS,
                                                 headers={"User-Agent": "ManzilBackend/1.0 (map overlay risk proxy)"}), timeout)
    resp.raise_for_status()
    return resp.json()

def save_weather(raw: dict, fetched_at: float, destination: Path = WEATHER_FILE) -> dict:
    """Validate then atomically store raw response + validated summary. Returns summary."""
    summary = normalize_open_meteo(raw, fetched_at)
    doc = {"schema_version": SCHEMA_VERSION, "fetched_at": summary["fetched_at"], "weather_time": summary["weather_time"],
           "request": REQUEST_PARAMS, "summary": summary, "response": raw}
    destination.parent.mkdir(parents=True, exist_ok=True)
    name = None
    try:
        with tempfile.NamedTemporaryFile("w", encoding="utf-8", dir=destination.parent, delete=False) as f:
            name = f.name
            json.dump(doc, f, ensure_ascii=False, indent=1, allow_nan=False)
        os.replace(name, destination)
    finally:
        if name and os.path.exists(name):
            os.unlink(name)
    return summary

def load_weather(path: Path = WEATHER_FILE) -> dict | None:
    """Re-validate the stored raw response; None when missing or malformed."""
    try:
        doc = json.loads(path.read_text(encoding="utf-8"))
        if doc.get("schema_version") != SCHEMA_VERSION:
            return None
        fetched = datetime.fromisoformat(doc["fetched_at"].replace("Z", "+00:00")).timestamp()
        return normalize_open_meteo(doc["response"], fetched)
    except (OSError, ValueError, KeyError, TypeError, AttributeError):
        return None

def _rain_tier(summary: dict) -> int:
    total = summary["past_3h_mm"] + summary["next_3h_mm"]
    tier = sum(total >= t for t in RAIN_6H_TIERS_MM)
    if summary["max_hourly_mm"] >= BURST_HOURLY_MM:
        tier = max(tier, 2)
    return tier

def _susceptibility(depth_cm: Any) -> int:
    if isinstance(depth_cm, bool) or not isinstance(depth_cm, (int, float)) or not math.isfinite(depth_cm) or depth_cm < 0:
        return 0
    return sum(depth_cm >= t for t in DEPTH_TIERS_CM)

def _band(score: int) -> str:
    return next(name for minimum, name in BANDS if score >= minimum)

METHOD = {
    "kind": "rainfall_proxy", "confidence": "low", "calibrated": False,
    "rain_index": "Open-Meteo hourly precipitation summed over the 3 h before and 3 h after the current hour (mm)",
    "rain_tiers_mm_6h": list(RAIN_6H_TIERS_MM), "burst_hourly_mm_forces_tier_2": BURST_HOURLY_MM,
    "historical_depth_tiers_cm": list(DEPTH_TIERS_CM),
    "band_rule": "score = rain_tier + depth_susceptibility; requires rain_tier >= 1; 1 watch, 2-3 elevated, >=4 high",
    "scored_locations": "GCC historical inundation survey points only",
}

def add_modelled_risk(feed: dict, weather: dict | None, now: datetime) -> dict:
    """Return a copy of feed with layer='risk' proxy features, or none plus status 'unavailable'."""
    if now.tzinfo is None:
        raise ValueError("Timezone-aware now required")
    out = copy.deepcopy(feed)
    meta = out.setdefault("metadata", {})
    block: dict[str, Any] = {"status": "unavailable", "method": METHOD, "limitations": LIMITATIONS, "feature_count": 0}
    meta["modelled_risk"] = block
    limits = meta.setdefault("limitations", [])
    note = "Risk features are a low-confidence city-wide rainfall proxy over historical survey points, not observations."
    if note not in limits:
        limits.append(note)
    out["features"] = [f for f in out.get("features", []) if f["properties"].get("layer") != "risk"]
    if weather is None:
        block["reason"] = "weather_missing_or_malformed"
    else:
        fetched = datetime.fromisoformat(weather["fetched_at"].replace("Z", "+00:00"))
        observed = datetime.fromisoformat(weather["weather_time"].replace("Z", "+00:00"))
        expires = min(observed, fetched) + MAX_VALIDITY
        block["weather"] = {k: v for k, v in weather.items() if k != "hourly_mm"}
        block["expires_at"] = iso(expires.timestamp())
        if fetched - now > MAX_FUTURE_FETCH_SKEW or now >= expires:
            block["reason"] = "weather_stale"
        else:
            tier = _rain_tier(weather)
            block.update(status="available", rain_tier=tier)
            if tier == 0:
                block["reason"] = "rain_below_watch_threshold"
            features = []
            for f in out["features"] if tier else []:
                p = f["properties"]
                if p.get("kind") != "survey_point":
                    continue
                depth = p.get("historical_depth_cm")
                susc = _susceptibility(depth)
                band = _band(tier + susc)
                rp = {"id": "risk_" + p["id"], "title": "Modelled rain watch near past inundation (low confidence)",
                      "layer": "risk", "evidence": "modelled", "kind": "rainfall_proxy", "risk_band": band,
                      "confidence": "low",
                      "detail": (f"Low-confidence city-wide rainfall proxy, not a street observation. Forecast rain "
                                 f"{weather['past_3h_mm']:g} mm in the past 3 h and {weather['next_3h_mm']:g} mm in the next 3 h "
                                 f"(Open-Meteo, one city grid point) at a location with past surveyed inundation"
                                 + (f" (historical depth {depth:g} cm, survey date unknown)" if depth is not None else "")
                                 + f". Band '{band}' = rain tier {tier} + historical susceptibility {susc}; uncalibrated, "
                                 "not a probability, depth or passability/dry-route claim. Check local conditions."),
                      "source": "Open-Meteo forecast + GCC historical inundation survey (OpenCity)",
                      "observed_at": weather["weather_time"], "expires_at": iso(expires.timestamp()),
                      "historical_feature_id": p["id"],
                      "basis": {"rain_past_3h_mm": weather["past_3h_mm"], "rain_next_3h_mm": weather["next_3h_mm"],
                                "max_hourly_mm": weather["max_hourly_mm"], "rain_tier": tier,
                                "historical_survey_depth_cm": depth, "depth_susceptibility": susc}}
                if "road_context" in p:
                    rp["road_context"] = copy.deepcopy(p["road_context"])
                features.append({"type": "Feature", "id": rp["id"], "geometry": copy.deepcopy(f["geometry"]), "properties": rp})
            out["features"].extend(features)
            block["feature_count"] = len(features)
    counts: dict[str, int] = {}
    for f in out["features"]:
        counts[f["properties"]["layer"]] = counts.get(f["properties"]["layer"], 0) + 1
    meta["counts"] = counts
    return out

# ---- endpoint adapter: 10 min cache, single in-flight refresh, <= 8 s timeout ----
_state: dict[str, Any] = {"checked": None, "weather": None, "lock": None, "loop": None}

def _lock() -> asyncio.Lock:
    loop = asyncio.get_running_loop()
    if _state["lock"] is None or _state["loop"] is not loop:
        _state.update(lock=asyncio.Lock(), loop=loop)
    return _state["lock"]

async def current_weather(fetcher: Callable[..., Awaitable[dict]] = fetch_open_meteo,
                          clock: Callable[[], float] = time.time, path: Path = WEATHER_FILE) -> dict | None:
    async with _lock():
        now = clock()
        if _state["checked"] is not None and 0 <= now - _state["checked"] < REFRESH_CACHE_S:
            return _state["weather"]
        try:
            raw = await asyncio.wait_for(fetcher(timeout=FETCH_TIMEOUT_S), FETCH_TIMEOUT_S)
            weather = await asyncio.to_thread(save_weather, raw, now, path)
        except Exception:
            weather = await asyncio.to_thread(load_weather, path)  # staleness is judged by add_modelled_risk
        _state.update(checked=now, weather=weather)
        return weather

def reset_cache() -> None:
    _state.update(checked=None, weather=None)

if __name__ == "__main__":  # python -m app.modelled_risk : fetch once, store, refresh overlay snapshot
    from app.map_overlays import build_overlays, save_snapshot
    fetched_at = time.time()
    summary = save_weather(asyncio.run(fetch_open_meteo()), fetched_at)
    now = datetime.now(timezone.utc)
    feed = add_modelled_risk(build_overlays(now=now), load_weather(), now)
    save_snapshot(feed)
    print(json.dumps({"weather": summary, "modelled_risk": {k: v for k, v in feed["metadata"]["modelled_risk"].items()
                                                            if k in ("status", "reason", "rain_tier", "feature_count", "expires_at")},
                      "counts": feed["metadata"]["counts"]}, indent=1))
