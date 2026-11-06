# Manzil agent on Cloud Run (pilot)

Status: packaging and unit tests only. **Nothing has been built, pushed or deployed from this
change.** The Docker daemon was not running on the dev box, so the image itself has not been
built. Instead, the three copied modules were started with uvicorn in an isolated directory to
check `/health` 200 (originally `/healthz`; renamed, see below), turn without token 401, `/docs` 404, and that startup fails with no token.

## What runs

`backend/app/cloud_agent.py` is a standalone FastAPI app (`uvicorn --factory app.cloud_agent:create_app`)
that reuses `app.agent_turn.router` and `close_agent_client` unchanged. It serves only:

| Route | Auth | Notes |
|---|---|---|
| `GET /health` | public | `{"status":"ok"}`. Does not call Gemini. (Was `/healthz`; Cloud Run reserves that path.) |
| `POST /api/v1/agent/turn` | `X-Manzil-Device-Token` | Existing request/response contract (`AgentTurnRequest` / `AgentTurnResponse`). |

Request and response fields are unchanged. The only addition is that `action` can now take four more values
(`find_nearby`, `journey_brief`, `emergency_help`, `stop_guidance`), alongside `plan_route`, `show_layer`,
`recenter`, `route_summary` and `none`. For `find_nearby`, `destination_query` holds a canonical English
category such as `pharmacy`, with no city suffix because the app supplies the location bias, and `layer` is
null. For the other three, both fields are null. See the `app/agent_turn.py` docstring for the details.
These new actions are in code only and have not been deployed.

There are no hazard, crowdsource, overlay or data routes, no database, no CORS, and `/docs` and
`/openapi.json` are turned off.

Guards on the turn route, in this order and **before the body is parsed or Gemini is called**:

1. Token missing, duplicated or wrong (constant-time `hmac.compare_digest`) → `401 {"error":"unauthorized"}`
2. `Content-Length` > 32 KB → `413`
3. More than 30 authorized requests in a sliding 60 s window → `429 rate_limited` + `Retry-After`
4. Already 2 requests in flight → `429 busy` + `Retry-After: 1`
5. Body actually received > 32 KB (catches chunked uploads) → `413`

Then the usual Pydantic rules apply (`422` on bad shape), plus `502`/`503` from the agent as before.
Unauthorized requests don't count toward the rate limit. The app never logs the token, headers or body.
The agent logs metadata only (action, layer, language, token counts).

**Pilot limits (state these plainly):** the rate limit and concurrency counters live in memory and
are per instance, so they reset whenever the instance restarts or scales to zero. One device token is
shared by the app build, so it is not user authentication. Anyone who pulls the APK can extract it.
For the pilot that is acceptable with `max-instances=1`. Production needs real per-user auth
(e.g. Firebase App Check / ID tokens) and a shared rate limiter.

Startup fails (the container exits) if `MANZIL_AGENT_ACCESS_TOKEN` is missing, blank, shorter than
32 characters, or contains spaces or non-ASCII characters. Leading and trailing whitespace is
stripped, so a secret created with `echo` (which adds a trailing newline) still works.

## Image

- `backend/cloud/Dockerfile`: `python:3.13-slim`, non-root uid 10001, port 8080. It copies only
  `cloud/requirements.txt`, `app/cloud_agent.py`, `app/agent_turn.py` and `app/gemini_agent.py`.
- `backend/cloud/Dockerfile.dockerignore`: an allowlist (BuildKit reads it automatically for this
  Dockerfile), so `.env`, `.venv`, `data/` and the rest of `app/` are never sent to the builder.
- `backend/cloud/requirements.txt`: pinned to the versions installed in `backend/.venv`, which the
  tests ran against: fastapi 0.162.0, starlette 1.10.0, pydantic 2.14.3, uvicorn 0.54.0,
  httpx 0.28.1, google-auth[requests] 2.62.1, requests 2.34.2.

No credential files go into the image. On Cloud Run, `google.auth.default()` picks up the service's
dedicated service account from the metadata server.

## Commands for root (run from `backend/`)

Placeholders: `PROJECT`, `REGION` (e.g. `asia-south1`), `REPO` (Artifact Registry Docker repo),
`SA` (dedicated service account, needs `roles/aiplatform.user` on PROJECT and
`roles/secretmanager.secretAccessor` on the secret), `SECRET` (Secret Manager secret holding the token).

```bash
# Generate a token (>=32 chars). Never commit or log it.
python -c "import secrets; print(secrets.token_urlsafe(48))"

# Build and push (Cloud Shell or any machine with Docker + BuildKit)
cd backend
docker build -f cloud/Dockerfile -t REGION-docker.pkg.dev/PROJECT/REPO/manzil-agent:v1 .
docker push REGION-docker.pkg.dev/PROJECT/REPO/manzil-agent:v1

# Deploy
gcloud run deploy manzil-agent \
  --project=PROJECT --region=REGION \
  --image=REGION-docker.pkg.dev/PROJECT/REPO/manzil-agent:v1 \
  --service-account=SA \
  --port=8080 \
  --min-instances=0 --max-instances=1 \
  --concurrency=4 \
  --cpu=1 --memory=512Mi --timeout=30s \
  --allow-unauthenticated \
  --set-secrets=MANZIL_AGENT_ACCESS_TOKEN=SECRET:latest \
  --set-env-vars=MANZIL_GEMINI_PROJECT=PROJECT,MANZIL_GEMINI_LOCATION=global,MANZIL_AGENT_MAX_CONCURRENCY=2
```

Notes:
- `--allow-unauthenticated` only opens IAM. The app still requires the device token on the turn route.
- `--concurrency=4` lets Cloud Run hand a third request to the instance so the app returns a clean
  `429 busy` instead of queueing it. The app's own cap of 2 is the real limit.
  `MANZIL_AGENT_MAX_CONCURRENCY=2` makes the Gemini client cap match.
- `--timeout=30s` sits above the Gemini client's 20 s hard cap.
- Optional env vars, same as the local backend: `MANZIL_GEMINI_MODEL`, `MANZIL_GEMINI_TIMEOUT_S`,
  `MANZIL_GEMINI_THINKING_LEVEL`. Their caps are enforced in `gemini_agent.py`.

## Calling it

```bash
URL=$(gcloud run services describe manzil-agent --region=REGION --format='value(status.url)')
curl -s "$URL/health"
curl -s -X POST "$URL/api/v1/agent/turn" \
  -H "Content-Type: application/json" \
  -H "X-Manzil-Device-Token: $TOKEN" \
  -d '{"text":"take me to Marina Beach","language":"en-IN","history":[],
       "context":{"destination":null,"layer":"map","route_summary":null}}'
```

Header: `X-Manzil-Device-Token: <token>` on every turn request. The body is the existing
`AgentTurnRequest`: `text` 1–1000 chars, `language` one of `en-IN|hi-IN|ta-IN|te-IN`, `history` up to
8 items, and `context` with explicit `destination`/`layer`/`route_summary`. Unknown fields are rejected.

**Health path:** Cloud Run reserves some URL paths ending in `z` (including `/healthz`), and its
frontend answers them with a Google-branded 404 before the container sees the request. The app
therefore serves `GET /health`; `/healthz` is no longer routed. Cloud Run's default TCP startup probe
on 8080 does not use either path. The rename is in code only and takes effect on the next deploy;
the revision recorded below still has the old `/healthz` route.

## Tests

```powershell
cd backend
.venv\Scripts\python.exe -m unittest tests.test_cloud_agent tests.test_agent_turn
```

`tests/test_cloud_agent.py` covers: startup fails with a missing, blank, short or space-containing
token; trailing newline is stripped; health is public and minimal; main-app routes and docs return
404; missing or wrong token returns 401 with zero model calls (even for a 50 KB body); an authorized
turn with mocked Gemini returns the existing contract; Pydantic 422s; 413 for both Content-Length and
chunked bodies; 30/min then 429 with reset after the window; unauthorized requests don't use up the
rate limit; and the third concurrent request gets 429 `busy`.

## Deployment verified 2026-11-06

Deployed Cloud Run service `manzil-agent` in `asia-south1`, project `project-69f64060-ab6d-43fd-b01`.
HTTPS endpoint: https://manzil-agent-1029249845485.asia-south1.run.app
Revision: manzil-agent-00002-42g. Dedicated runtime service account uses Vertex AI; device token is in Secret Manager. Scale 0–1 instances, 512 MiB, 1 CPU, 30-second timeout. This is pilot device authentication, not multi-user account authentication.

Android now uses separate `MANZIL_AGENT_API_URL` configuration (HTTPS only), with device token in private `files/agent-access-token`, provisioned to this Samsung. No secret is compiled into the APK. Reinstall after uninstall or clearing app data requires reprovisioning. Overlay local development URL was cleared; saved/bundled historical layers remain available, but live local overlay ingestion is not hosted by this agent-only service.

Verified authenticated real Gemini turn returns HTTP 200 and a structured ECR Beach route intent; missing token returns 401. Cloud Run intercepts `/healthz` with a 404, so that path is not a usable external health check (the code now serves `/health` instead; not yet deployed); authenticated turn is the end-to-end check.

On Samsung RZGL40EETBZ, stopped laptop backend, removed all ADB reverse mappings, disabled Wi-Fi with mobile data enabled. Panda's actual “Show flood history around my route” request opened the Flood context map and displayed the actual result. Evidence: android-evidence/cloud-mobile-result.xml. USB was retained for UI inspection only; it carried no backend forwarding. Restored Wi-Fi afterward. Spoken recognition and outdoor guidance remain previously deferred tests.

Validation: 74 backend tests passed; Android assembleDebug, unit tests and lintDebug passed (docs/standalone-android-build.log). Build image succeeded (docs/cloud-build-mtls.log). Standard cloudbuild.googleapis.com was connection-reset on this network; the official cloudbuild.mtls.googleapis.com endpoint worked using a per-process gcloud endpoint override, not a global configuration change.
