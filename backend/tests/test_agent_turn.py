"""Unit tests for /api/v1/agent/turn. No network: Vertex is replaced by httpx.MockTransport."""

import asyncio
import json
import unittest
from typing import get_args

import httpx
from fastapi import FastAPI
from starlette.testclient import TestClient

from app import agent_turn as at
from app.gemini_agent import AgentUnavailable, GeminiClient, GeminiSettings

PROJECT = "test-project-123"


class FakeCreds:
    def __init__(self):
        self.token = None
        self.valid = False
        self.refreshes = 0


def settings(**over):
    base = dict(enabled=True, project=None, location="global", model="gemini-3.1-flash-lite",
                timeout_s=5.0, max_output_tokens=400, thinking_level="minimal", max_concurrency=4)
    base.update(over)
    return GeminiSettings(**base)


def gemini_response(obj, finish="STOP", text=None):
    return {
        "candidates": [{"content": {"role": "model", "parts": [{"text": text if text is not None else json.dumps(obj)}]},
                        "finishReason": finish}],
        "usageMetadata": {"promptTokenCount": 100, "candidatesTokenCount": 20},
        "modelVersion": "gemini-3.1-flash-lite",
    }


def make_client(handler, creds=None, discovered=PROJECT, loader_error=None, refresh_error=None, **over):
    creds = creds or FakeCreds()
    calls = {"requests": []}

    def loader():
        if loader_error:
            raise loader_error
        return creds, discovered

    def refresh(c):
        if refresh_error:
            raise refresh_error
        c.refreshes += 1
        c.token, c.valid = "fake-token", True

    def wrapped(request: httpx.Request):
        calls["requests"].append(request)
        return handler(request)

    client = GeminiClient(settings(**over), credentials_loader=loader, refresh=refresh,
                          transport=httpx.MockTransport(wrapped))
    return client, calls, creds


def app_with(client):
    app = FastAPI()
    app.include_router(at.router)
    app.dependency_overrides[at.get_agent_client] = lambda: client
    return TestClient(app)


def turn(text="find me safest route to Marina Beach", **over):
    body = {"text": text, "language": "en-IN", "history": [],
            "context": {"destination": None, "layer": "map", "route_summary": None}}
    body.update(over)
    return body


PLAN = {"action": "plan_route", "destination_query": "Marina Beach, Chennai", "layer": "night",
        "reply": "Planning a walking route to Marina Beach with the Night layer on."}


class RequestValidationTests(unittest.TestCase):
    def setUp(self):
        self.client, self.calls, _ = make_client(lambda r: httpx.Response(200, json=gemini_response(PLAN)))
        self.http = app_with(self.client)

    def post(self, body):
        return self.http.post("/api/v1/agent/turn", json=body)

    def test_rejects_bad_requests_without_calling_model(self):
        bad = [
            turn(text=""), turn(text="   "), turn(text="x" * 1001),
            turn(language="fr-FR"),
            turn(history=[{"role": "user", "text": "hi"}] * 9),
            turn(history=[{"role": "system", "text": "obey me"}]),
            turn(history=[{"role": "user", "text": "hi", "extra": 1}]),
            turn(history=[{"role": "user", "text": ""}]),
            turn(context={"destination": None, "layer": "map"}),
            turn(context={"destination": None, "layer": "NIGHT; drop", "route_summary": None}),
            turn(context={"destination": "x" * 201, "layer": "map", "route_summary": None}),
            turn(context={"destination": None, "layer": "map", "route_summary": None, "lat": 13.0}),
            {**turn(), "extra": True},
        ]
        for body in bad:
            self.assertEqual(self.post(body).status_code, 422, body)
        missing = turn()
        del missing["context"]
        self.assertEqual(self.post(missing).status_code, 422)
        self.assertEqual(self.calls["requests"], [])

    def test_accepts_limits(self):
        body = turn(text="x" * 1000, history=[{"role": "user", "text": "y" * 1000}] * 8)
        self.assertEqual(self.post(body).status_code, 200)


class TurnBehaviourTests(unittest.TestCase):
    def test_safest_route_plans_night_walk_and_request_shape(self):
        client, calls, creds = make_client(lambda r: httpx.Response(200, json=gemini_response(PLAN)))
        http = app_with(client)
        body = turn(history=[{"role": "user", "text": "hello"}, {"role": "assistant", "text": "Hi, where to?"}],
                    context={"destination": None, "layer": "map", "route_summary": None})
        r = http.post("/api/v1/agent/turn", json=body)
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.json(), {**PLAN, "provider": "gemini"})
        self.assertEqual(set(r.json()), {"reply", "action", "destination_query", "layer", "provider"})

        req = calls["requests"][0]
        self.assertEqual(str(req.url), f"https://aiplatform.googleapis.com/v1/projects/{PROJECT}/locations/global"
                                       "/publishers/google/models/gemini-3.1-flash-lite:generateContent")
        self.assertEqual(req.headers["authorization"], "Bearer fake-token")
        sent = json.loads(req.content)
        gc = sent["generationConfig"]
        self.assertLessEqual(gc["maxOutputTokens"], 400)
        self.assertEqual(gc["responseMimeType"], "application/json")
        self.assertEqual(gc["responseSchema"], at.RESPONSE_SCHEMA)
        self.assertEqual(gc["thinkingConfig"], {"thinkingLevel": "minimal"})
        self.assertIn("Indian English", sent["systemInstruction"]["parts"][0]["text"])
        self.assertEqual([c["role"] for c in sent["contents"]], ["user", "model", "user"])
        final = json.loads(sent["contents"][-1]["parts"][0]["text"])
        self.assertEqual(final["utterance"], "find me safest route to Marina Beach")
        self.assertEqual(final["app_context"]["layer"], "map")
        self.assertEqual(creds.refreshes, 1)

        # Valid token is reused; no second refresh.
        http.post("/api/v1/agent/turn", json=turn())
        self.assertEqual(creds.refreshes, 1)

    def test_quota_project_header_from_user_credentials(self):
        creds = FakeCreds()
        creds.quota_project_id = "quota-project-9"
        client, calls, _ = make_client(lambda r: httpx.Response(200, json=gemini_response(PLAN)), creds=creds)
        app_with(client).post("/api/v1/agent/turn", json=turn())
        self.assertEqual(calls["requests"][0].headers["x-goog-user-project"], "quota-project-9")

    def test_language_goes_into_trusted_instruction(self):
        client, calls, _ = make_client(lambda r: httpx.Response(200, json=gemini_response(PLAN)))
        app_with(client).post("/api/v1/agent/turn", json=turn(language="ta-IN"))
        sent = json.loads(calls["requests"][0].content)
        self.assertIn("Tamil", sent["systemInstruction"]["parts"][0]["text"])

    def test_env_project_overrides_discovered_and_regional_host(self):
        client, calls, _ = make_client(lambda r: httpx.Response(200, json=gemini_response(PLAN)),
                                       project="env-project-1", location="asia-south1", discovered="other-project")
        app_with(client).post("/api/v1/agent/turn", json=turn())
        self.assertTrue(str(calls["requests"][0].url).startswith(
            "https://asia-south1-aiplatform.googleapis.com/v1/projects/env-project-1/locations/asia-south1/"))

    def test_valid_actions(self):
        cases = [
            {"action": "show_layer", "destination_query": None, "layer": "flood", "reply": "Showing the Flood layer."},
            {"action": "recenter", "destination_query": None, "layer": None, "reply": "Centring on you."},
            {"action": "route_summary", "destination_query": None, "layer": None, "reply": "About 2 km on foot."},
            {"action": "none", "destination_query": None, "layer": None, "reply": "Where would you like to go?"},
        ]
        for obj in cases:
            client, _, _ = make_client(lambda r, o=obj: httpx.Response(200, json=gemini_response(o)))
            r = app_with(client).post("/api/v1/agent/turn", json=turn())
            self.assertEqual(r.status_code, 200, obj)
            self.assertEqual(r.json()["action"], obj["action"])


class ModelOutputRejectionTests(unittest.TestCase):
    def assert_rejected(self, raw, code="agent_bad_output"):
        client, _, _ = make_client(lambda r: httpx.Response(200, json=raw))
        r = app_with(client).post("/api/v1/agent/turn", json=turn())
        self.assertEqual(r.status_code, 502, raw)
        self.assertEqual(r.json()["detail"]["error"], code)
        self.assertNotIn("reply", r.json())

    def test_rejects_invalid_or_extra_fields(self):
        bad = [
            {**PLAN, "action": "call_phone"},
            {**PLAN, "layer": "satellite"},
            {**PLAN, "extra": "x"},
            {**PLAN, "lat": 13.05, "lng": 80.28},
            {k: v for k, v in PLAN.items() if k != "layer"},
            {**PLAN, "reply": ""},
            {**PLAN, "reply": 5},
            {**PLAN, "destination_query": None},
            {**PLAN, "destination_query": "13.0500, 80.2824"},
            {**PLAN, "destination_query": "https://maps.example/x"},
            {**PLAN, "layer": None},
            {"action": "show_layer", "destination_query": "Marina Beach", "layer": "flood", "reply": "ok"},
            {"action": "show_layer", "destination_query": None, "layer": None, "reply": "ok"},
            {"action": "recenter", "destination_query": None, "layer": "night", "reply": "ok"},
            {"action": "none", "destination_query": "Marina", "layer": None, "reply": "ok"},
        ]
        for obj in bad:
            self.assert_rejected(gemini_response(obj))

    def test_rejects_unverifiable_claims_and_coordinates(self):
        for reply in ["This is the safest route to Marina Beach.", "Your route is flood-free.",
                      "Your route is ready.", "I've found a well-lit route.", "The path is completely safe.",
                      "Head to 13.0500, 80.2824 now.", "Lighting verified along the way."]:
            self.assert_rejected(gemini_response({**PLAN, "reply": reply}))

    def test_allows_negated_qualifications(self):
        for reply in ["Planning a walking route to Marina Beach; lighting is not verified.",
                      "Opening flood history; current road conditions are unknown and safety cannot be guaranteed.",
                      "Planning the route with the Night layer, but it isn't verified and can't be guaranteed.",
                      "Opening flood history. No area is guaranteed flood-free."]:
            client, _, _ = make_client(lambda r, t=reply: httpx.Response(200, json=gemini_response({**PLAN, "reply": t})))
            r = app_with(client).post("/api/v1/agent/turn", json=turn())
            self.assertEqual(r.status_code, 200, reply)
            self.assertEqual(r.json()["reply"], reply)

    def test_negation_does_not_leak_across_clauses(self):
        self.assertTrue(at.has_affirmative_claim("No worries, the route is verified."))
        self.assertTrue(at.has_affirmative_claim("Not far. Lighting is guaranteed."))
        self.assertTrue(at.has_affirmative_claim("Don't worry; this is the safest route."))
        self.assertFalse(at.has_affirmative_claim("Current road conditions are unknown."))

    def test_prompt_requires_english_destination_and_flood_history_wording(self):
        text = at.system_instruction_for("ta-IN")
        self.assertIn("canonical English place name", text)
        self.assertIn("Opening flood history; current road conditions are unknown.", text)
        self.assertIn("Never say or imply that flood", text)

    def test_rejects_malformed_envelopes(self):
        self.assert_rejected(gemini_response(None, text="not json"))
        self.assert_rejected(gemini_response(None, text="[1,2]"))
        self.assert_rejected(gemini_response(PLAN, finish="MAX_TOKENS"))
        self.assert_rejected(gemini_response(PLAN, finish="SAFETY"), code="agent_blocked")
        self.assert_rejected({"promptFeedback": {"blockReason": "SAFETY"}}, code="agent_blocked")
        self.assert_rejected({"candidates": []})

    def test_thought_parts_are_ignored(self):
        raw = gemini_response(PLAN)
        raw["candidates"][0]["content"]["parts"].insert(0, {"text": "thinking...", "thought": True})
        client, _, _ = make_client(lambda r: httpx.Response(200, json=raw))
        self.assertEqual(app_with(client).post("/api/v1/agent/turn", json=turn()).status_code, 200)


NEARBY = {"action": "find_nearby", "destination_query": "pharmacy", "layer": None,
          "reply": "Showing pharmacies near you; I can't tell which is nearest or whether they're open."}
BRIEF = {"action": "journey_brief", "destination_query": None, "layer": None, "reply": "Opening your journey brief."}
SOS = {"action": "emergency_help", "destination_query": None, "layer": None,
       "reply": "If you are in danger, call 112 now; opening SOS options."}
STOP = {"action": "stop_guidance", "destination_query": None, "layer": None, "reply": "Stopping guidance."}


def decide(obj):
    return at.ModelDecision.model_validate(obj)


def post_decision(obj):
    client, _, _ = make_client(lambda r: httpx.Response(200, json=gemini_response(obj)))
    return app_with(client).post("/api/v1/agent/turn", json=turn())


class NewActionContractTests(unittest.TestCase):
    def test_new_actions_return_existing_response_shape(self):
        for obj in (NEARBY, BRIEF, SOS, STOP):
            r = post_decision(obj)
            self.assertEqual(r.status_code, 200, obj)
            self.assertEqual(r.json(), {**obj, "provider": "gemini"})
            self.assertEqual(set(r.json()), {"reply", "action", "destination_query", "layer", "provider"})

    def test_request_and_response_contract_unchanged(self):
        self.assertEqual(set(at.AgentTurnRequest.model_fields), {"text", "language", "history", "context"})
        self.assertEqual(set(at.TurnContext.model_fields), {"destination", "layer", "route_summary"})
        self.assertEqual(set(at.AgentTurnResponse.model_fields),
                         {"reply", "action", "destination_query", "layer", "provider"})

    def test_schema_enum_matches_validator(self):
        enum = at.RESPONSE_SCHEMA["properties"]["action"]["enum"]
        self.assertEqual(set(enum), set(get_args(at.Action)))
        self.assertEqual(len(enum), len(set(enum)))
        for a in ("plan_route", "show_layer", "recenter", "route_summary", "none",
                  "find_nearby", "journey_brief", "emergency_help", "stop_guidance"):
            self.assertIn(a, enum)

    def test_nearby_categories_accepted_and_city_suffix_stripped(self):
        for dq, expected in [("pharmacy", "pharmacy"), ("metro station", "metro station"),
                             ("police station", "police station"), ("Pharmacy, Chennai", "Pharmacy"),
                             ("  bus stop , chennai ", "bus stop"), ("ATM/bank", "ATM/bank")]:
            self.assertEqual(decide({**NEARBY, "destination_query": dq}).destination_query, expected, dq)

    def test_nearby_field_validation(self):
        bad = [
            {**NEARBY, "destination_query": None},
            {**NEARBY, "destination_query": "   "},
            {**NEARBY, "layer": "map"},
            {**NEARBY, "layer": "night"},
            {**NEARBY, "destination_query": "nearest pharmacy"},
            {**NEARBY, "destination_query": "pharmacy near me"},
            {**NEARBY, "destination_query": "open pharmacy"},
            {**NEARBY, "destination_query": "24 hour pharmacy"},
            {**NEARBY, "destination_query": "safest police station"},
            {**NEARBY, "destination_query": "மருந்தகம்"},        # must be canonical English
            {**NEARBY, "destination_query": "12 Anna Salai"},    # an address, not a category
            {**NEARBY, "destination_query": "13.0500, 80.2824"},
            {**NEARBY, "destination_query": "https://maps.example/x"},
            {**NEARBY, "destination_query": "x" * 61},
        ]
        for obj in bad:
            self.assertEqual(post_decision(obj).status_code, 502, obj)

    def test_null_field_actions_reject_fields(self):
        for base in (BRIEF, SOS, STOP):
            for over in ({"destination_query": "Marina Beach"}, {"layer": "map"}, {"layer": "flood"}):
                self.assertEqual(post_decision({**base, **over}).status_code, 502, (base["action"], over))

    def test_plan_route_cannot_auto_route_to_closest(self):
        for dq in ("nearest pharmacy", "closest metro station, Chennai", "police station near me"):
            self.assertEqual(post_decision({**PLAN, "destination_query": dq}).status_code, 502, dq)
        self.assertEqual(post_decision(PLAN).status_code, 200)

    def test_emergency_reply_must_be_short_and_mention_112(self):
        self.assertEqual(post_decision({**SOS, "reply": "Opening SOS options."}).status_code, 502)
        long = "Call 112 now. " + "Stay where people can see you and keep your phone with you. " * 4
        self.assertGreater(len(long), at.EMERGENCY_REPLY_MAX)
        self.assertEqual(post_decision({**SOS, "reply": long}).status_code, 502)
        self.assertEqual(post_decision({**SOS, "reply": "खतरे में हों तो तुरंत 112 पर कॉल करें।"}).status_code, 200)

    def test_new_action_replies_reject_unverifiable_claims(self):
        cases = [
            (NEARBY, "The nearest pharmacy is Apollo on Anna Salai."),
            (NEARBY, "There's a pharmacy that is open now."),
            (NEARBY, "The pharmacies are open."),
            (NEARBY, "Routing you to the closest pharmacy."),
            (NEARBY, "Taking you to the nearest metro station."),
            (SOS, "I'm calling 112 for you."),
            (SOS, "Call 112. I have alerted the police."),
            (SOS, "Call 112; help is on the way."),
            (SOS, "Calling 112 now."),
            (STOP, "Stopping guidance; your trip has been cancelled."),
            (STOP, "Done, route deleted."),
            (BRIEF, "It will work offline."),
            (BRIEF, "You're all set to go."),
            (BRIEF, "Opening your brief; you are ready to go."),
        ]
        for base, reply in cases:
            self.assertEqual(post_decision({**base, "reply": reply}).status_code, 502, reply)

    def test_new_action_replies_allow_hedged_wording(self):
        cases = [
            (NEARBY, "Showing police stations near you. I can't tell whether any are open."),
            (NEARBY, "Showing metro stations nearby; which one is closest is unknown."),
            (NEARBY, "Showing pharmacies near you; opening hours are not known."),
            (SOS, "Call 112 if you are in danger; I can't call for you, but SOS options are opening."),
            (STOP, "Stopping guidance; your trip is kept and nothing is deleted."),
            (BRIEF, "Opening your journey brief; it will show whether the map works offline."),
        ]
        for base, reply in cases:
            r = post_decision({**base, "reply": reply})
            self.assertEqual(r.status_code, 200, reply)
            self.assertEqual(r.json()["reply"], reply)

    def test_existing_actions_keep_previous_reply_rules(self):
        # The new guard is scoped to the new actions; route_summary restates app facts as before.
        reply = "About 2 km; the summary says the nearest metro station is on the way."
        r = post_decision({"action": "route_summary", "destination_query": None, "layer": None, "reply": reply})
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.json()["reply"], reply)
        # The original route/safety claim guard still applies to the new actions.
        self.assertEqual(post_decision({**BRIEF, "reply": "Here's your route."}).status_code, 502)
        self.assertEqual(post_decision({**NEARBY, "reply": "This is the safest pharmacy."}).status_code, 502)


def prompt_section(action: str) -> str:
    text = at.system_instruction_for("en-IN")
    start = text.index(f"\n- {action}:")
    end = text.find("\n- ", start + 1)
    return text[start:end]


class PromptIntentBoundaryTests(unittest.TestCase):
    def test_every_action_is_described_once(self):
        text = at.system_instruction_for("en-IN")
        for a in at.ACTIONS:
            self.assertEqual(text.count(f"\n- {a}:"), 1, a)

    def test_journey_brief_phrases(self):
        sec = prompt_section("journey_brief")
        for phrase in ("brief me before I leave", "will it work offline", "am I ready to go"):
            self.assertIn(phrase, sec)
        self.assertIn("This is not route_summary", sec)
        for other in ("route_summary", "find_nearby", "stop_guidance", "plan_route", "none"):
            self.assertNotIn("work offline", prompt_section(other), other)

    def test_emergency_owns_immediate_danger(self):
        sec = prompt_section("emergency_help")
        for phrase in ("immediate danger", "call 112", "never calls anyone automatically", "SOS options"):
            self.assertIn(phrase, sec)
        self.assertNotIn("immediate danger", prompt_section("none"))

    def test_stop_guidance_is_not_cancel_or_delete(self):
        sec = prompt_section("stop_guidance")
        self.assertIn('"stop navigation"', sec)
        self.assertIn("trip and destination are kept", sec)
        self.assertIn("use none", sec)
        self.assertIn("cancelling trips, deleting data", prompt_section("none"))

    def test_find_nearby_category_and_no_auto_route(self):
        sec = prompt_section("find_nearby")
        for phrase in ('"pharmacy"', '"metro station"', '"police station"', 'Do not add "Chennai"',
                       "app adds the user's location itself", "never routes to the closest one automatically",
                       "whether any place is open"):
            self.assertIn(phrase, sec)
        self.assertIn("that is find_nearby, not plan_route", prompt_section("plan_route"))

    def test_existing_sections_keep_their_rules(self):
        self.assertIn("restate only facts it contains", prompt_section("route_summary"))
        self.assertIn('Add ", Chennai" when', prompt_section("plan_route"))
        text = at.system_instruction_for("en-IN")
        self.assertIn("destination_query is set only for plan_route (a place name) and find_nearby (a category)", text)

    def test_prompt_example_replies_pass_validation(self):
        text = at.system_instruction_for("en-IN")
        for obj in (NEARBY, BRIEF, SOS, STOP):
            self.assertIn(obj["reply"], text)
            self.assertEqual(decide(obj).reply, obj["reply"])


class UnavailableTests(unittest.TestCase):
    def assert_503(self, client, code):
        r = app_with(client).post("/api/v1/agent/turn", json=turn())
        self.assertEqual(r.status_code, 503)
        self.assertEqual(r.json()["detail"]["error"], code)
        self.assertEqual(r.json()["detail"]["provider"], "gemini")
        return r

    def test_disabled_and_missing_configuration(self):
        ok = lambda r: httpx.Response(200, json=gemini_response(PLAN))
        c, calls, _ = make_client(ok, enabled=False)
        self.assert_503(c, "agent_disabled")
        c, _, _ = make_client(ok, loader_error=RuntimeError("no adc"))
        self.assert_503(c, "agent_not_configured")
        c, _, _ = make_client(ok, discovered=None)
        self.assert_503(c, "agent_not_configured")
        c, _, _ = make_client(ok, discovered="Bad Project!")
        self.assert_503(c, "agent_not_configured")
        c, _, _ = make_client(ok, refresh_error=RuntimeError("expired"))
        self.assert_503(c, "agent_auth_failed")
        c, calls, _ = make_client(ok, project="BAD")
        self.assert_503(c, "agent_not_configured")
        self.assertEqual(calls["requests"], [])

    def test_upstream_status_mapping_and_no_retry(self):
        for status, code in [(401, "agent_auth_failed"), (403, "agent_auth_failed"), (404, "agent_model_unavailable"),
                             (429, "agent_rate_limited"), (500, "agent_upstream_unavailable"),
                             (503, "agent_upstream_unavailable")]:
            c, calls, _ = make_client(lambda r, s=status: httpx.Response(s, json={"error": {"message": "x"}}))
            self.assert_503(c, code)
            self.assertEqual(len(calls["requests"]), 1, status)  # exactly one attempt
        c, _, _ = make_client(lambda r: httpx.Response(400, json={"error": {}}))
        r = app_with(c).post("/api/v1/agent/turn", json=turn())
        self.assertEqual((r.status_code, r.json()["detail"]["error"]), (502, "agent_upstream_rejected"))

    def test_cooldown_prevents_hammering(self):
        c, calls, _ = make_client(lambda r: httpx.Response(404, json={}))
        http = app_with(c)
        for _ in range(3):
            self.assertEqual(http.post("/api/v1/agent/turn", json=turn()).status_code, 503)
        self.assertEqual(len(calls["requests"]), 1)

    def test_timeout_and_network_errors(self):
        def slow(r):
            raise httpx.ReadTimeout("slow", request=r)

        def down(r):
            raise httpx.ConnectError("down", request=r)

        c, _, _ = make_client(slow)
        self.assert_503(c, "agent_timeout")
        c, _, _ = make_client(down)
        self.assert_503(c, "agent_unreachable")

    def test_overall_deadline(self):
        async def run():
            async def hang(request):
                await asyncio.sleep(5)
                return httpx.Response(200, json=gemini_response(PLAN))

            class Hang(httpx.AsyncBaseTransport):
                async def handle_async_request(self, request):
                    return await hang(request)

            creds = FakeCreds()

            def refresh(c):
                c.token, c.valid = "t", True

            client = GeminiClient(settings(timeout_s=1.0), credentials_loader=lambda: (creds, PROJECT),
                                  refresh=refresh, transport=Hang())
            with self.assertRaises(AgentUnavailable) as ctx:
                await client.generate("s", [], {})
            await client.aclose()
            return ctx.exception.code

        self.assertEqual(asyncio.run(run()), "agent_timeout")


class SettingsTests(unittest.TestCase):
    def test_caps_cannot_be_raised(self):
        s = GeminiSettings.from_env({"MANZIL_GEMINI_TIMEOUT_S": "90", "MANZIL_GEMINI_MAX_OUTPUT_TOKENS": "4000"})
        self.assertEqual((s.timeout_s, s.max_output_tokens), (20.0, 400))
        s = GeminiSettings.from_env({})
        self.assertEqual((s.model, s.location, s.project, s.enabled), ("gemini-3.1-flash-lite", "global", None, True))
        self.assertFalse(GeminiSettings.from_env({"MANZIL_AGENT_ENABLED": "0"}).enabled)
        self.assertIsNone(GeminiSettings.from_env({"MANZIL_GEMINI_THINKING_LEVEL": "off"}).thinking_level)

    def test_router_registered_in_main(self):
        from app.main import app
        # FastAPI 0.162 includes routers lazily, so check the generated OpenAPI paths.
        self.assertIn("post", app.openapi()["paths"].get("/api/v1/agent/turn", {}))


if __name__ == "__main__":
    unittest.main()
