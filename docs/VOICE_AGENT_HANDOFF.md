# Voice agent handoff

Written 6 November 2026. **Status: only native push-to-talk speech input exists. No cloud voice agent is integrated.** Nothing below about Gemini Live is built, tested or benchmarked in Manzil.

## What exists now

`android/app/src/main/java/app/manzil/voice/PandaSpeechInput.kt`

```kotlin
class PandaSpeechInput(context: Context, onState: (String) -> Unit, onResult: (String) -> Unit)
fun start(languageTag: String)   // "en-IN" | "hi-IN" | "ta-IN" | "te-IN"
fun stop()                       // cancel; nothing is delivered for the cancelled session
fun destroy()                    // release; instance cannot be restarted
```

- Uses the phone's currently installed/default `SpeechRecognizer` service. No API keys, no network calls of our own, **no offline guarantee**: the service decides whether it needs a network.
- One `start` = one utterance. No auto-listen, restart or loop.
- Each session ends with exactly one of `onResult(best non-blank transcript)` or a terminal `onState` string (`PandaSpeechInput.TERMINAL_STATES`). Progress strings: `Starting microphone…`, `Listening…`, `Understanding…`. Terminal examples: `No speech heard. Try again.`, `Speech recognition unavailable.`, `Microphone permission needed.`, `Speech service couldn't be reached. Try again.`, `This language isn't available for speech input.`
- `stop()` sets an inactive flag and bumps a session id, so late results/errors are dropped. Recognizer create/release happens on the main thread; calls from other threads are posted to main.
- It does not request permission. The caller requests `RECORD_AUDIO` and calls `start` only once granted (it re-checks and reports `Microphone permission needed.` defensively).
- Manifest: `RECORD_AUDIO` permission and a `<queries>` entry for `android.speech.RecognitionService` (API 30+ package visibility).

**Root UI owns everything visible**: the Compose voice sheet, mic button, permission request and rationale, language picker, showing the state strings, `destroy()` in `onDispose`, and routing the confirmed transcript through `voice/PandaCommand` into destination search or map layer selection. The transcript is user input, not a command to run blindly.

## Later: Gemini Live on Vertex AI (not built)

Goal: optional continuous spoken dialogue with native audio, used only online and only when the user opts in. The deterministic local command path stays the default and the fallback.

### Shape

```
Phone (explicit mic toggle) ──PCM 16 kHz──▶ Manzil backend (WSS proxy) ──▶ Vertex AI Live API
      ◀──PCM 24 kHz audio + tool calls────          │  ADC / service account
Phone validates + executes allowlisted tools locally ◀┘
```

- **Auth: server-side Application Default Credentials (service account) on the Manzil backend.** The phone never holds Google Cloud credentials. The Google Maps API key is for Maps Platform services and must never be used or reused for Vertex. The phone authenticates to our backend with our own short-lived session token.
- Backend proxies the stateful WebSocket session, enforces budgets, logs usage, and can kill a session.
- Model candidates from the Vertex docs' supported-model table: `gemini-3.8-live`, `gemini-live-2.5-flash-native-audio` (both listed GA/recommended). Choose by measured latency and Indian-language quality, not by the table.
- Audio formats per the Vertex overview: input raw 16-bit PCM 16 kHz little-endian; output raw 16-bit PCM 24 kHz little-endian.

### Languages

The Vertex "Configure language and voice" table lists `en-IN` (as an "en-IN & hi-IN bundle" row), `hi-IN`, `ta-IN`, `te-IN`. The page constrains response language through **system instructions** (its tip: "RESPOND IN LANGUAGE. YOU MUST RESPOND UNMISTAKABLY IN LANGUAGE."); its sample also sets `speech_config.language_code` without saying whether native audio models honour it. Pass the user's chosen tag in both and verify on device. Table presence is not evidence of quality for Tamil/Telugu navigation speech; test it.

### Function calling (declared once at session setup)

Tools must be declared in the session config at connect time. All calls are **requests**: the phone validates parameters against local state and executes, then returns a short result. The model never gets a free-form action.

| Tool | Parameters | Phone-side gate |
|---|---|---|
| `set_destination` | `place_id` or saved-destination id from a list we supplied; never raw coordinates from speech alone | Requires location permission; show the destination and require a user tap to confirm before routing |
| `select_layer` | enum of our map layers | Layer must exist in the loaded offline pack |
| `recenter` | none | Requires location permission and a current fix; otherwise return "no location" |
| `route_summary` | none | Only if a route is active; read from our own saved/computed route, never claim Google Maps' route |

**No call execution**: no phone-call, SMS, dialer, `tel:` intent, arbitrary URL, package launch or Accessibility tap tool exists, and none may be added through this channel. Vertex docs note audio input/output can reduce function-calling reliability, so keep tools few and confirm consequential ones.

### Session budgets (to set and measure, not measured yet)

- Explicit mic: session starts only from a visible user toggle, shows a persistent "live" indicator, ends on toggle-off, app background, screen off, or N seconds of silence. No wake word, no background listening.
- Per session: hard wall-clock cap (start at 3–5 minutes) and token cap enforced by the backend; per-user daily cap. Vertex counts audio at ~25 tokens/s, and past turns are re-billed every turn, so long sessions cost superlinearly.
- Vertex documents a 128k-token context limit and sliding-window context compression (which loses history), plus session duration limits that its own pages state differently (15 min audio-only without compression vs a 10 min default). Re-check before relying on either; reconnect with a session handle only if needed.
- On any failure, budget hit or offline: end the session and fall back to `PandaSpeechInput` + local commands. Never pretend the cloud answered.

### Must not

Do not claim cloud integration until it exists and is measured. Do not ship Google Cloud credentials or long-lived keys in the APK. Do not let the model choose arbitrary tools, coordinates, packages or phone actions.

## Sources

Fetched and read 6 Nov 2026:
- Vertex Gemini Live API overview (models, audio formats): https://docs.cloud.google.com/vertex-ai/generative-ai/docs/live-api
- Configure language and voice (en-IN/hi-IN/ta-IN/te-IN table, system-instruction tip): https://docs.cloud.google.com/vertex-ai/generative-ai/docs/live-api/configure-language-voice

From official search results, not re-read in full (verify before implementing):
- Start and manage sessions (duration limits, compression, session resumption): https://docs.cloud.google.com/vertex-ai/generative-ai/docs/live-api/start-manage-session
- Live API reference (10 min default, 128k context, rate limits): https://docs.cloud.google.com/vertex-ai/generative-ai/docs/model-reference/multimodal-live
- Configure capabilities (function calling declared at setup; audio impacts tool use): https://docs.cloud.google.com/vertex-ai/generative-ai/docs/live-api/configure-gemini-capabilities
- Best practices: https://docs.cloud.google.com/vertex-ai/generative-ai/docs/live-api/best-practices
- Pricing: https://docs.cloud.google.com/vertex-ai/generative-ai/pricing

Standard references (not fetched in this pass):
- Application Default Credentials: https://cloud.google.com/docs/authentication/application-default-credentials
- Android SpeechRecognizer: https://developer.android.com/reference/android/speech/SpeechRecognizer
