"""Unit tests for the standalone Cloud Run agent app. No network, no real Gemini."""

import asyncio
import json
import unittest

import httpx
from starlette.testclient import TestClient

from app import agent_turn as at
from app import cloud_agent as ca

TOKEN = "t" * 20 + "0123456789abcdef"  # 36 chars
ENV = {ca.TOKEN_ENV: TOKEN}
AUTH = {"X-Manzil-Device-Token": TOKEN}

PLAN = {"action": "plan_route", "destination_query": "Marina Beach, Chennai", "layer": "night",
        "reply": "Planning a walking route to Marina Beach with the Night layer on."}


def gemini_response(obj):
    return {"candidates": [{"content": {"role": "model", "parts": [{"text": json.dumps(obj)}]},
                            "finishReason": "STOP"}],
            "usageMetadata": {"promptTokenCount": 100, "candidatesTokenCount": 20}}


class FakeGemini:
    def __init__(self, gate: asyncio.Event | None = None):
        self.calls = 0
        self.gate = gate

    async def generate(self, system_instruction, contents, schema):
        self.calls += 1
        if self.gate is not None:
            await self.gate.wait()
        return gemini_response(PLAN)

    async def aclose(self):
        pass


def turn_body(text="find me safest route to Marina Beach"):
    return {"text": text, "language": "en-IN", "history": [],
            "context": {"destination": None, "layer": "map", "route_summary": None}}


class Clock:
    def __init__(self):
        self.now = 1000.0

    def __call__(self):
        return self.now


def build(fake=None, clock=None):
    app = ca.create_app(ENV, clock=clock or Clock())
    fake = fake or FakeGemini()
    app.dependency_overrides[at.get_agent_client] = lambda: fake
    return app, fake


class StartupTests(unittest.TestCase):
    def test_missing_blank_or_short_token_fails_startup(self):
        for env in ({}, {ca.TOKEN_ENV: ""}, {ca.TOKEN_ENV: "   \n"}, {ca.TOKEN_ENV: "x" * 31},
                    {ca.TOKEN_ENV: "x" * 20 + " " + "y" * 20}):
            with self.assertRaises(RuntimeError):
                ca.create_app(env)

    def test_trailing_newline_is_stripped(self):
        self.assertEqual(ca.load_access_token({ca.TOKEN_ENV: TOKEN + "\n"}), TOKEN)

    def test_main_app_routes_are_not_mounted(self):
        client = TestClient(build()[0])
        for path in ("/", "/api/v1/hazards", "/api/v1/crowdsource/reports", "/api/v1/map/overlays",
                     "/api/v1/night/context", "/docs", "/openapi.json"):
            self.assertEqual(client.get(path).status_code, 404, path)


class HttpTests(unittest.TestCase):
    def setUp(self):
        self.clock = Clock()
        self.app, self.fake = build(clock=self.clock)
        self.client = TestClient(self.app)

    def test_health_is_public_and_minimal(self):
        r = self.client.get("/health")
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.json(), {"status": "ok"})
        # Cloud Run reserves /healthz, so the app no longer serves it.
        self.assertEqual(self.client.get("/healthz").status_code, 404)

    def test_unknown_paths_404_and_docs_disabled(self):
        for path in ("/docs", "/openapi.json", "/api/v1/hazards"):
            self.assertEqual(self.client.get(path).status_code, 404, path)

    def test_missing_and_wrong_token_rejected_before_gemini(self):
        cases = [{}, {"X-Manzil-Device-Token": ""}, {"X-Manzil-Device-Token": TOKEN[:-1] + "X"},
                 {"X-Manzil-Device-Token": TOKEN + "extra"}]
        for headers in cases:
            r = self.client.post(ca.TURN_PATH, json=turn_body(), headers=headers)
            self.assertEqual(r.status_code, 401)
            self.assertEqual(r.json()["error"], "unauthorized")
            self.assertNotIn(TOKEN, r.text)
        # Invalid body without token still 401 (auth before validation).
        r = self.client.post(ca.TURN_PATH, content=b"x" * 50_000, headers={"content-type": "application/json"})
        self.assertEqual(r.status_code, 401)
        self.assertEqual(self.fake.calls, 0)

    def test_authorized_turn_uses_existing_contract(self):
        r = self.client.post(ca.TURN_PATH, json=turn_body(), headers=AUTH)
        self.assertEqual(r.status_code, 200, r.text)
        self.assertEqual(r.json(), {**PLAN, "provider": "gemini"})
        self.assertEqual(self.fake.calls, 1)

    def test_pydantic_boundaries_still_apply(self):
        bad = turn_body()
        bad["extra"] = 1
        r = self.client.post(ca.TURN_PATH, json=bad, headers=AUTH)
        self.assertEqual(r.status_code, 422)
        self.assertEqual(self.fake.calls, 0)

    def test_oversize_body_rejected(self):
        big = json.dumps(turn_body("a" * 40_000)).encode()
        r = self.client.post(ca.TURN_PATH, content=big, headers={**AUTH, "content-type": "application/json"})
        self.assertEqual(r.status_code, 413)
        self.assertEqual(self.fake.calls, 0)

    def test_oversize_chunked_body_rejected(self):
        def chunks():
            for _ in range(5):
                yield b"x" * 8000
        r = self.client.post(ca.TURN_PATH, content=chunks(), headers={**AUTH, "content-type": "application/json"})
        self.assertEqual(r.status_code, 413)
        self.assertEqual(self.fake.calls, 0)

    def test_rate_limit_30_per_minute_then_resets(self):
        for i in range(ca.RATE_LIMIT):
            self.assertEqual(self.client.post(ca.TURN_PATH, json=turn_body(), headers=AUTH).status_code, 200, i)
        r = self.client.post(ca.TURN_PATH, json=turn_body(), headers=AUTH)
        self.assertEqual(r.status_code, 429)
        self.assertEqual(r.json()["error"], "rate_limited")
        self.assertIn("retry-after", r.headers)
        self.assertEqual(self.fake.calls, ca.RATE_LIMIT)
        self.clock.now += ca.RATE_WINDOW_S
        self.assertEqual(self.client.post(ca.TURN_PATH, json=turn_body(), headers=AUTH).status_code, 200)

    def test_unauthorized_requests_do_not_consume_rate_limit(self):
        for _ in range(ca.RATE_LIMIT + 5):
            self.client.post(ca.TURN_PATH, json=turn_body(), headers={"X-Manzil-Device-Token": "nope"})
        self.assertEqual(self.client.post(ca.TURN_PATH, json=turn_body(), headers=AUTH).status_code, 200)


class ConcurrencyTests(unittest.IsolatedAsyncioTestCase):
    async def test_third_concurrent_request_rejected(self):
        gate = asyncio.Event()
        app, fake = build(FakeGemini(gate))
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://t") as c:
            first = [asyncio.create_task(c.post(ca.TURN_PATH, json=turn_body(), headers=AUTH)) for _ in range(2)]
            for _ in range(50):
                if fake.calls == 2:
                    break
                await asyncio.sleep(0.01)
            self.assertEqual(fake.calls, 2)
            third = await c.post(ca.TURN_PATH, json=turn_body(), headers=AUTH)
            self.assertEqual(third.status_code, 429)
            self.assertEqual(third.json()["error"], "busy")
            gate.set()
            results = await asyncio.gather(*first)
            self.assertEqual([r.status_code for r in results], [200, 200])
            after = await c.post(ca.TURN_PATH, json=turn_body(), headers=AUTH)
            self.assertEqual(after.status_code, 200)


if __name__ == "__main__":
    unittest.main()
