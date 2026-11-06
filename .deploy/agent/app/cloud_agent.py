"""Standalone Cloud Run app for the Manzil conversational agent (pilot).

Serves exactly two routes:
  GET  /health              public, minimal status, never touches Gemini
                            (not /healthz: Cloud Run's frontend reserves that path and returns 404)
  POST /api/v1/agent/turn   the existing agent router (app.agent_turn), guarded below

No hazard/crowdsource routes, no data files, no database. Gemini is reached through the existing
GeminiClient using Application Default Credentials (on Cloud Run: the service's dedicated service
account via the metadata server; no credential files).

Guard order for the agent route, all before the body is parsed or Gemini is called:
  1. X-Manzil-Device-Token must equal MANZIL_AGENT_ACCESS_TOKEN (constant-time)  -> else 401
  2. Content-Length (if sent) <= 32 KB                                             -> else 413
  3. per-instance sliding window: 30 authorized requests / 60 s                     -> else 429
  4. per-instance concurrency: 2 in flight                                         -> else 429
  5. body actually received <= 32 KB (covers chunked uploads)                     -> else 413

Rate/concurrency state is in memory: per instance, reset on restart. Fine for a pilot with
max-instances=1; it is NOT production user auth or durable rate limiting. A single shared device
token identifies the app build, not a user.

Never log the token, headers or request body.
"""

from __future__ import annotations

import collections
import hmac
import json
import logging
import os
import time
from contextlib import asynccontextmanager
from typing import Callable, Mapping, Optional

from fastapi import FastAPI

from app.agent_turn import close_agent_client, router as agent_router

logger = logging.getLogger("manzil-agent")

TOKEN_ENV = "MANZIL_AGENT_ACCESS_TOKEN"
TOKEN_HEADER = b"x-manzil-device-token"
TURN_PATH = "/api/v1/agent/turn"
MIN_TOKEN_LEN = 32
MAX_BODY_BYTES = 32 * 1024
RATE_LIMIT = 30
RATE_WINDOW_S = 60.0
MAX_IN_FLIGHT = 2


def load_access_token(env: Optional[Mapping[str, str]] = None) -> str:
    """Return the configured device token or raise RuntimeError (fail startup)."""
    env = os.environ if env is None else env
    # Strip: Secret Manager values created with `echo` often carry a trailing newline.
    token = (env.get(TOKEN_ENV) or "").strip()
    if len(token) < MIN_TOKEN_LEN:
        raise RuntimeError(f"{TOKEN_ENV} must be set to a non-blank secret of at least {MIN_TOKEN_LEN} characters.")
    if not all(33 <= ord(c) <= 126 for c in token):
        raise RuntimeError(f"{TOKEN_ENV} must contain only printable ASCII without spaces.")
    return token


class SlidingWindowLimiter:
    """In-memory sliding window. Single event loop, so no locking is needed."""

    def __init__(self, limit: int = RATE_LIMIT, window_s: float = RATE_WINDOW_S,
                 clock: Callable[[], float] = time.monotonic):
        self.limit, self.window_s, self.clock = limit, window_s, clock
        self._hits: collections.deque[float] = collections.deque()

    def check(self) -> Optional[int]:
        """Record a hit and return None if allowed, else seconds until a slot frees up."""
        now = self.clock()
        while self._hits and now - self._hits[0] >= self.window_s:
            self._hits.popleft()
        if len(self._hits) >= self.limit:
            return max(1, int(self.window_s - (now - self._hits[0])) + 1)
        self._hits.append(now)
        return None


async def _send_json(send, status: int, payload: dict, extra_headers: Optional[list] = None) -> None:
    body = json.dumps(payload).encode()
    headers = [(b"content-type", b"application/json"), (b"content-length", str(len(body)).encode())]
    await send({"type": "http.response.start", "status": status, "headers": headers + (extra_headers or [])})
    await send({"type": "http.response.body", "body": body})


class AgentGuard:
    """Pure ASGI middleware protecting TURN_PATH. Other paths pass through untouched."""

    def __init__(self, app, *, token: str, limiter: SlidingWindowLimiter,
                 max_in_flight: int = MAX_IN_FLIGHT, max_body: int = MAX_BODY_BYTES):
        self.app = app
        self._token = token.encode("ascii")
        self.limiter = limiter
        self.max_in_flight = max_in_flight
        self.max_body = max_body
        self.in_flight = 0

    def _authorized(self, headers: list) -> bool:
        presented = [v for k, v in headers if k == TOKEN_HEADER]
        if len(presented) != 1:
            return False
        return hmac.compare_digest(presented[0], self._token)

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http" or scope["path"] != TURN_PATH:
            return await self.app(scope, receive, send)
        headers = scope.get("headers") or []

        if not self._authorized(headers):
            return await _send_json(send, 401, {"error": "unauthorized", "message": "Missing or invalid device token."})

        for k, v in headers:
            if k == b"content-length":
                try:
                    declared = int(v)
                except ValueError:
                    return await _send_json(send, 400, {"error": "bad_request", "message": "Invalid Content-Length."})
                if declared > self.max_body:
                    return await _send_json(send, 413, {"error": "payload_too_large", "message": "Request body exceeds 32 KB."})

        retry = self.limiter.check()
        if retry is not None:
            return await _send_json(send, 429, {"error": "rate_limited", "message": "Too many requests. Try again shortly."},
                                    [(b"retry-after", str(retry).encode())])

        if self.in_flight >= self.max_in_flight:
            return await _send_json(send, 429, {"error": "busy", "message": "The agent is handling other requests."},
                                    [(b"retry-after", b"1")])

        self.in_flight += 1
        try:
            chunks, size = [], 0
            while True:
                message = await receive()
                if message["type"] == "http.disconnect":
                    return
                chunk = message.get("body", b"")
                size += len(chunk)
                if size > self.max_body:
                    return await _send_json(send, 413, {"error": "payload_too_large", "message": "Request body exceeds 32 KB."})
                chunks.append(chunk)
                if not message.get("more_body", False):
                    break
            body, replayed = b"".join(chunks), False

            async def replay():
                nonlocal replayed
                if not replayed:
                    replayed = True
                    return {"type": "http.request", "body": body, "more_body": False}
                return await receive()

            await self.app(scope, replay, send)
        finally:
            self.in_flight -= 1


def create_app(env: Optional[Mapping[str, str]] = None,
               clock: Callable[[], float] = time.monotonic) -> FastAPI:
    """App factory (uvicorn --factory). Raises RuntimeError if the access token is not configured."""
    token = load_access_token(env)

    @asynccontextmanager
    async def lifespan(_app: FastAPI):
        yield
        await close_agent_client()

    app = FastAPI(title="Manzil agent (cloud)", docs_url=None, redoc_url=None, openapi_url=None, lifespan=lifespan)

    @app.get("/health")
    async def health() -> dict:
        return {"status": "ok"}

    app.include_router(agent_router)
    app.add_middleware(AgentGuard, token=token, limiter=SlidingWindowLimiter(clock=clock))
    logger.info("cloud agent configured path=%s rate=%d/%ds concurrency=%d max_body=%d",
                TURN_PATH, RATE_LIMIT, int(RATE_WINDOW_S), MAX_IN_FLIGHT, MAX_BODY_BYTES)
    return app
