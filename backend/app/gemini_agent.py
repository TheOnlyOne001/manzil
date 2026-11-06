"""Vertex AI Gemini client for the Manzil conversational agent.

Server-side only. Uses Application Default Credentials (google-auth); the phone never holds
Google Cloud credentials. One bounded generateContent call per turn: no retries, hard timeout,
capped output tokens, a small concurrency limit and a short cooldown after configuration-class
failures so a broken setup cannot turn into a retry storm.

Never log prompt text, history, replies, tokens or credential material.
"""

from __future__ import annotations

import asyncio
import functools
import logging
import os
import re
import time
from dataclasses import dataclass
from typing import Any, Callable, Optional

import httpx

logger = logging.getLogger("manzil-agent")

SCOPES = ("https://www.googleapis.com/auth/cloud-platform",)
DEFAULT_MODEL = "gemini-3.1-flash-lite"
DEFAULT_LOCATION = "global"
MAX_TIMEOUT_S = 20.0
MAX_OUTPUT_TOKENS = 400
_PROJECT_RE = re.compile(r"^[a-z][a-z0-9-]{4,28}[a-z0-9]$")
_LOCATION_RE = re.compile(r"^[a-z0-9-]{2,40}$")
_MODEL_RE = re.compile(r"^[a-z0-9][a-z0-9.\-]{1,80}$")


class AgentUnavailable(Exception):
    """Gemini cannot serve this turn (not configured, auth, quota, timeout, upstream down)."""

    def __init__(self, code: str, message: str, cooldown_s: float = 0.0):
        super().__init__(message)
        self.code = code
        self.message = message
        self.cooldown_s = cooldown_s


class AgentUpstreamError(Exception):
    """Gemini answered, but not with something we can use (bad request, blocked, malformed)."""

    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code
        self.message = message


@dataclass(frozen=True)
class GeminiSettings:
    enabled: bool
    project: Optional[str]  # None = discover from ADC
    location: str
    model: str
    timeout_s: float
    max_output_tokens: int
    thinking_level: Optional[str]
    max_concurrency: int

    @classmethod
    def from_env(cls, env: Optional[dict] = None) -> "GeminiSettings":
        env = os.environ if env is None else env

        def _float(name: str, default: float) -> float:
            try:
                return float(env.get(name, default))
            except (TypeError, ValueError):
                return default

        def _int(name: str, default: int) -> int:
            try:
                return int(env.get(name, default))
            except (TypeError, ValueError):
                return default

        thinking = (env.get("MANZIL_GEMINI_THINKING_LEVEL", "minimal") or "").strip().lower()
        return cls(
            enabled=env.get("MANZIL_AGENT_ENABLED", "1").strip().lower() not in ("0", "false", "no", "off"),
            project=(env.get("MANZIL_GEMINI_PROJECT") or "").strip() or None,
            location=(env.get("MANZIL_GEMINI_LOCATION") or DEFAULT_LOCATION).strip(),
            model=(env.get("MANZIL_GEMINI_MODEL") or DEFAULT_MODEL).strip(),
            # Caps are enforced: env may lower them, never raise them.
            timeout_s=min(max(_float("MANZIL_GEMINI_TIMEOUT_S", 15.0), 1.0), MAX_TIMEOUT_S),
            max_output_tokens=min(max(_int("MANZIL_GEMINI_MAX_OUTPUT_TOKENS", MAX_OUTPUT_TOKENS), 64), MAX_OUTPUT_TOKENS),
            thinking_level=None if thinking in ("", "none", "off") else thinking,
            max_concurrency=min(max(_int("MANZIL_AGENT_MAX_CONCURRENCY", 4), 1), 16),
        )


def _default_credentials_loader() -> tuple[Any, Optional[str]]:
    """Application Default Credentials, avoiding google.auth.default()'s slow path on dev machines.

    With no GOOGLE_APPLICATION_CREDENTIALS, google.auth.default() shells out to `gcloud config get project`
    (measured 10-24 s on the Windows dev box), which would eat the whole turn budget. When the well-known
    gcloud ADC file exists we load it directly (<1 s) and use GOOGLE_CLOUD_PROJECT or its quota_project_id as
    the discovered project. Anything else (service-account file, metadata server) goes through
    google.auth.default() unchanged.
    """
    import google.auth
    from google.auth import _cloud_sdk

    if not os.environ.get("GOOGLE_APPLICATION_CREDENTIALS"):
        path = _cloud_sdk.get_application_default_credentials_path()
        if path and os.path.isfile(path):
            creds, project = google.auth.load_credentials_from_file(path, scopes=list(SCOPES))
            project = project or os.environ.get("GOOGLE_CLOUD_PROJECT") or getattr(creds, "quota_project_id", None)
            return creds, project
    return google.auth.default(scopes=list(SCOPES))


def _default_refresh(credentials: Any) -> None:
    from google.auth.transport.requests import Request

    # Bound the token refresh itself; google-auth's default request timeout is 120 s.
    credentials.refresh(functools.partial(Request(), timeout=10))


class GeminiClient:
    def __init__(
        self,
        settings: GeminiSettings,
        *,
        credentials_loader: Callable[[], tuple[Any, Optional[str]]] = _default_credentials_loader,
        refresh: Callable[[Any], None] = _default_refresh,
        transport: Optional[httpx.AsyncBaseTransport] = None,
        clock: Callable[[], float] = time.monotonic,
    ):
        self.settings = settings
        self._credentials_loader = credentials_loader
        self._refresh = refresh
        self._transport = transport
        self._clock = clock
        self._credentials: Any = None
        self._project: Optional[str] = None
        self._auth_lock = asyncio.Lock()
        self._semaphore = asyncio.Semaphore(settings.max_concurrency)
        self._http: Optional[httpx.AsyncClient] = None
        self._unavailable_until = 0.0
        self._unavailable: Optional[AgentUnavailable] = None

    # ---- configuration -------------------------------------------------------------------
    @property
    def project(self) -> Optional[str]:
        return self._project

    def _validate_static(self) -> None:
        s = self.settings
        if not s.enabled:
            raise AgentUnavailable("agent_disabled", "The conversational agent is disabled on this server (MANZIL_AGENT_ENABLED=0).")
        if not _LOCATION_RE.match(s.location):
            raise AgentUnavailable("agent_not_configured", "MANZIL_GEMINI_LOCATION is not a valid Vertex AI location.")
        if not _MODEL_RE.match(s.model):
            raise AgentUnavailable("agent_not_configured", "MANZIL_GEMINI_MODEL is not a valid model id.")
        if s.project and not _PROJECT_RE.match(s.project):
            raise AgentUnavailable("agent_not_configured", "MANZIL_GEMINI_PROJECT is not a valid Google Cloud project id.")

    async def _ensure_credentials(self) -> dict[str, str]:
        """Load ADC once and refresh off the event loop when expired. Returns auth headers."""
        async with self._auth_lock:
            if self._credentials is None:
                try:
                    creds, discovered = await asyncio.to_thread(self._credentials_loader)
                except Exception as exc:  # DefaultCredentialsError and friends; never echo details
                    raise AgentUnavailable(
                        "agent_not_configured",
                        "Google Cloud Application Default Credentials were not found on the server.",
                        cooldown_s=60,
                    ) from exc
                project = self.settings.project
                if project is None and discovered and _PROJECT_RE.match(discovered):
                    project = discovered
                if project is None:
                    raise AgentUnavailable(
                        "agent_not_configured",
                        "No Google Cloud project configured. Set MANZIL_GEMINI_PROJECT.",
                        cooldown_s=60,
                    )
                self._credentials, self._project = creds, project
            creds = self._credentials
            if not getattr(creds, "valid", False) or not getattr(creds, "token", None):
                try:
                    await asyncio.to_thread(self._refresh, creds)
                except Exception as exc:
                    raise AgentUnavailable(
                        "agent_auth_failed",
                        "Could not refresh Google Cloud credentials. Re-run `gcloud auth application-default login`.",
                        cooldown_s=30,
                    ) from exc
            token = getattr(creds, "token", None)
            if not token:
                raise AgentUnavailable("agent_auth_failed", "Google Cloud credentials produced no access token.", cooldown_s=30)
            headers = {"Authorization": f"Bearer {token}"}
            quota_project = getattr(creds, "quota_project_id", None)
            if isinstance(quota_project, str) and _PROJECT_RE.match(quota_project):
                # Same header google-auth's apply() adds for user credentials with a quota project.
                headers["x-goog-user-project"] = quota_project
            return headers

    def endpoint(self) -> str:
        loc = self.settings.location
        host = "aiplatform.googleapis.com" if loc == "global" else f"{loc}-aiplatform.googleapis.com"
        return (
            f"https://{host}/v1/projects/{self._project}/locations/{loc}"
            f"/publishers/google/models/{self.settings.model}:generateContent"
        )

    def _client(self) -> httpx.AsyncClient:
        if self._http is None:
            t = self.settings.timeout_s
            self._http = httpx.AsyncClient(
                transport=self._transport,
                timeout=httpx.Timeout(t, connect=min(5.0, t)),
                follow_redirects=False,
            )
        return self._http

    async def aclose(self) -> None:
        if self._http is not None:
            await self._http.aclose()
            self._http = None

    # ---- call ------------------------------------------------------------------------------
    def build_body(self, system_instruction: str, contents: list[dict], response_schema: dict) -> dict:
        generation_config: dict[str, Any] = {
            "temperature": 0.2,
            "candidateCount": 1,
            "maxOutputTokens": self.settings.max_output_tokens,
            "responseMimeType": "application/json",
            "responseSchema": response_schema,
        }
        if self.settings.thinking_level:
            generation_config["thinkingConfig"] = {"thinkingLevel": self.settings.thinking_level}
        return {
            "systemInstruction": {"parts": [{"text": system_instruction}]},
            "contents": contents,
            "generationConfig": generation_config,
        }

    async def generate(self, system_instruction: str, contents: list[dict], response_schema: dict) -> dict:
        """One generateContent call. Returns the raw JSON response or raises AgentUnavailable/AgentUpstreamError."""
        self._validate_static()
        now = self._clock()
        if self._unavailable is not None and now < self._unavailable_until:
            raise self._unavailable
        if self._semaphore.locked():
            raise AgentUnavailable("agent_busy", "The agent is handling other requests. Try again in a moment.")
        async with self._semaphore:
            started = self._clock()
            try:
                async with asyncio.timeout(self.settings.timeout_s):
                    auth_headers = await self._ensure_credentials()
                    response = await self._client().post(
                        self.endpoint(),
                        json=self.build_body(system_instruction, contents, response_schema),
                        headers={**auth_headers, "Content-Type": "application/json; charset=utf-8"},
                    )
            except AgentUnavailable as exc:
                self._trip(exc)
                raise
            except (TimeoutError, httpx.TimeoutException) as exc:
                raise AgentUnavailable("agent_timeout", "Gemini did not answer in time.") from exc
            except httpx.HTTPError as exc:
                raise AgentUnavailable("agent_unreachable", "Could not reach Vertex AI.") from exc
            elapsed_ms = round((self._clock() - started) * 1000)
            logger.info("gemini generateContent model=%s status=%s ms=%s", self.settings.model, response.status_code, elapsed_ms)
            return self._handle_status(response)

    def _trip(self, exc: AgentUnavailable) -> None:
        if exc.cooldown_s > 0:
            self._unavailable = exc
            self._unavailable_until = self._clock() + exc.cooldown_s

    def _handle_status(self, response: httpx.Response) -> dict:
        status = response.status_code
        if status == 200:
            try:
                data = response.json()
            except ValueError as exc:
                raise AgentUpstreamError("agent_bad_output", "Gemini returned a non-JSON response.") from exc
            if not isinstance(data, dict):
                raise AgentUpstreamError("agent_bad_output", "Gemini returned an unexpected response.")
            self._unavailable = None
            return data
        if status in (401, 403):
            if status == 401:
                self._credentials_force_refresh()
            exc = AgentUnavailable(
                "agent_auth_failed",
                "Vertex AI rejected the server's credentials or the project lacks Vertex AI access.",
                cooldown_s=60,
            )
        elif status == 404:
            exc = AgentUnavailable(
                "agent_model_unavailable",
                f"Model {self.settings.model} is not available in {self.settings.location} for this project.",
                cooldown_s=60,
            )
        elif status == 429:
            exc = AgentUnavailable("agent_rate_limited", "Vertex AI quota or rate limit reached. Try again shortly.", cooldown_s=10)
        elif status >= 500:
            exc = AgentUnavailable("agent_upstream_unavailable", "Vertex AI is temporarily unavailable.", cooldown_s=5)
        else:
            raise AgentUpstreamError("agent_upstream_rejected", f"Vertex AI rejected the request (HTTP {status}).")
        self._trip(exc)
        raise exc

    def _credentials_force_refresh(self) -> None:
        # Drop the cached token so the next turn (after cooldown) refreshes instead of reusing it.
        creds = self._credentials
        if creds is not None and hasattr(creds, "token"):
            try:
                creds.token = None
            except Exception:
                self._credentials = None
