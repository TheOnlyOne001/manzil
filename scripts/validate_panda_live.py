"""Real Cloud Run intent checks. Reads pilot token locally; never prints credentials."""
import json
from pathlib import Path
import httpx

BASE = "https://manzil-agent-1029249845485.asia-south1.run.app"
CASES = [
    ("Find a pharmacy near me", "en-IN", "find_nearby"),
    ("Where is the closest metro station?", "en-IN", "find_nearby"),
    ("Brief me before I leave", "en-IN", "journey_brief"),
    ("Will this work without internet?", "en-IN", "journey_brief"),
    ("Stop navigation", "en-IN", "stop_guidance"),
    ("I need emergency help", "en-IN", "emergency_help"),
    ("Plan a night walk to Marina Beach", "en-IN", "plan_route"),
    ("என் அருகில் மருந்தகம் தேடு", "ta-IN", "find_nearby"),
]

if __name__ == "__main__":
    token = (Path.home()/".codex/secrets/manzil-device-token").read_text().strip()
    results = []
    with httpx.Client(timeout=35) as client:
        for text, language, expected in CASES:
            response = client.post(BASE+"/api/v1/agent/turn", headers={"X-Manzil-Device-Token": token}, json={
                "text": text, "language": language, "history": [],
                "context": {"destination": "ECR Beach", "layer": "map", "route_summary": "31.6 km, 7 hr 12 min; preview only"},
            })
            body = response.json()
            passed = response.status_code == 200 and body.get("action") == expected
            results.append({"query": text, "expected": expected, "status": response.status_code, "passed": passed, "response": body})
            print(expected, response.status_code, "PASS" if passed else "FAIL", flush=True)
    Path("docs/panda-live-validation.json").write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding="utf-8")
    raise SystemExit(0 if all(r["passed"] for r in results) else 1)
