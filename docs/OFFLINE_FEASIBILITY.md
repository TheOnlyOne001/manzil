# Offline panda over the actual Google Maps app: feasibility

Research checked 5 November 2026. The requested first feature is an Android panda agent that observes and assists with **the separate Google Maps app**, including offline operation. This is distinct from an embedded map. No device-specific benchmark or working prototype has yet established feasibility.

## Separate the four capabilities

| Capability | Candidate mechanism | Actual boundary |
|---|---|---|
| Display panda above Maps | Android application overlay | Requires special overlay permission; draws the app's UI, not georeferenced objects inside Google Maps |
| Observe the visible Maps screen | User-consented MediaProjection; accessibility tree where appropriate and exposed | Screen pixels/text are partial and potentially stale. They do not expose Maps' internal routing graph, camera projection or live data feed |
| Reason/speak offline | Bundled local model, device speech recognition/TTS, deterministic command handling | Models/language assets must already be downloaded; hardware, Tamil support, latency and concurrent memory use need testing |
| Perform an action in Maps | Documented Maps URLs/intents; separately evaluated narrow accessibility actions | Capture/overlay permission does not grant control. Deep links initiate documented actions; arbitrary external map camera control is not supplied by them |

Screen-space arrows may help identify a currently visible button. They must disappear or be recomputed after pan/zoom, rotation, sheet movement or window resize. A floating overlay cannot be assumed to remain attached to a real geographic point when Google Maps changes its camera. OCR text and a route-colour screenshot cannot establish current traffic, flood or safety facts that are not actually provided.

## On-device model candidates

**LiteRT-LM is the main runtime candidate to benchmark for a companion while Maps stays foreground.** Its official Android Kotlin SDK loads `.litertlm` models and supports hardware backends. Model initialization can be slow, so it must run off the UI thread. Choose and pin a compatible release and model format before implementation, rather than copying `latest.release` into a project. The runtime does not make arbitrary model weights, NPU binaries or every Android device compatible. [Official Android guide](https://developers.google.com/edge/litert-lm/android), [runtime repository](https://github.com/google-ai-edge/LiteRT-LM)

Gemma 3n is a candidate for multimodal experiments, not a commitment. Its E2B designation represents effective parameters, not the full file size or complete app memory requirement. Vision/audio encoders and caches add cost; Google describes selective loading and PLE caching as ways to reduce memory. A small text-only command model plus OCR can be evaluated before full-screen vision reasoning. Broad multilingual model training does not prove reliable Tamil place-name recognition or tool selection. [Gemma 3n overview](https://ai.google.dev/gemma/docs/gemma-3n)

No universal minimum RAM threshold was established from the reviewed official runtime documentation. The user-selected Redmi Note 10 Pro is now verified as Android12/API31, arm64, SM7150, approximately 5.44GiB Linux-visible RAM and 2.32GiB available at the check. This is a **small text-model experiment target**, not a basis for promising a continuous multimodal model. The app must coexist with Google Maps, speech, screen capture and the OS. Measure model load success, peak memory, response latency, temperature, battery use and OS process kills on this phone. Do not use another flagship phone's benchmark as a performance estimate.

### Concrete first model gate on this Redmi

1. **Baseline:** a deterministic bounded command router plus observed UI/OCR text. It works without model inference and provides the comparison for correctness and latency.
2. **First learned router candidate: FunctionGemma 270M.** Google's official Mobile Actions guide demonstrates specialization for function calling and deployment through LiteRT-LM. This is a small tool-selection model, not a general conversational or vision model. Its guide explicitly recommends task-specific fine-tuning; start by measuring base performance on the exact small tool schema, then decide whether tuning is warranted. Tamil understanding, OCR interpretation, general travel reasoning and arbitrary Maps control are not established by that demo. [Official Mobile Actions recipe](https://ai.google.dev/gemma/docs/mobile-actions?hl=en), [Google DeepMind model page](https://deepmind.google/models/gemma/functiongemma/)
3. **Optional text explanation candidate: Gemma 3 1B IT**, using the official-linked LiteRT community compatible bundle and short context. This is a larger resource step and should be evaluated separately from the router. Do not make it read screenshots directly or infer unobserved map facts. The model card's multilingual family claims and benchmarks do not establish reliable Tamil route-name handling on this device. [Model card](https://ai.google.dev/gemma/docs/core/model_card_3), [LiteRT model repository](https://huggingface.co/litert-community/Gemma3-1B-IT/tree/main)
4. **Defer Gemma 4 E2B / Gemma 3n multimodal initially.** Google's LiteRT Gemma4 page lists an E2B bundle of 2.58GB and benchmarks newer hardware, not this Redmi. Model disk size and reported peak CPU memory do not measure full concurrent GPU/OS/app memory. This may become an experiment after the small-model gate, not the first promised implementation. [Official Gemma4 LiteRT performance](https://developers.google.com/edge/litert-lm/models/gemma-4)

For the first model, verify the chosen exact AAR's Android minSdk/ABI and model/runtime compatibility. Test CPU first for compatibility, then a supported GPU backend; do not assume an SM7150-compatible NPU artifact exists. Cap context/output and run one inference at a time. Proposed acceptance checks: 30 representative commands plus ambiguous/out-of-scope examples, no unknown tool execution, no Maps process death, measured p50/p95 response times, and a 10-minute repeated-use run with network disabled. These are future tests, not passed results. No model weights were downloaded during this audit.

**ML Kit Gemini Nano is not a suitable default for the overlay loop.** Official documentation limits GenAI inference to the app being the top foreground application; even a foreground service can receive `BACKGROUND_USE_BLOCKED`. If Google Maps is foreground, a panda foreground service is not sufficient. Availability also depends on an explicit device/model support list and downloaded AICore assets, with per-app quotas. It may be useful in a Manzil foreground screen on a supported phone, but that is a different interaction. [ML Kit GenAI overview](https://developers.google.com/ml-kit/genai)

An offline deterministic intent router is a useful fallback: recognise a small set such as explain, repeat, open saved destination, pause and resume. It offers no general scene understanding, but keeps essential functions available if the local model is unavailable or too slow. Local inference cannot acquire new traffic observations, new bus positions or new road closures without connectivity.

## Offline Tamil and English speech

- On API31+, check `SpeechRecognizer.isOnDeviceRecognitionAvailable()` and use `createOnDeviceSpeechRecognizer()` where supported. On API33+, use `checkRecognitionSupport()` and inspect installed/supported languages for the exact recognition intent and locale, including `ta-IN`.
- `EXTRA_PREFER_OFFLINE` alone is not a guarantee; Android says implementations may ignore it. Language and on-device recognition availability depend on the installed service/model. No promise of offline Tamil is justified until tested.
- For TTS, enumerate voices, select the intended Tamil/English locale with `isNetworkConnectionRequired()==false`, check installed voice data and play a phrase with networking disabled.
- Keep text input and predefined accessible actions functional. If native offline Tamil recognition is unavailable, evaluate a separately bundled ASR model against Tamil/English mixed speech and Chennai place names; that adds another model-size, memory, licence and latency decision.

Sources: [SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer), [RecognitionSupport](https://developer.android.com/reference/android/speech/RecognitionSupport), [RecognizerIntent](https://developer.android.com/reference/android/speech/RecognizerIntent), [TTS Voice](https://developer.android.com/reference/android/speech/tts/Voice).

## Screen observation and actual control

MediaProjection requires a system consent flow for each session, and Android14+ permits sharing just one app window. It requires the appropriate foreground-service type. Tokens are single-use for creating a capture session; capture may stop on screen lock, user revocation or process death. Handle `onStop` and re-consent. A recording/capture token does not give permission to click controls. [MediaProjection](https://developer.android.com/media/grow/media-projection)

AccessibilityService can retrieve exposed UI nodes and dispatch declared gestures when enabled, but rendered map content may not be represented as useful semantic nodes. UI text, coordinates and control layout can change by Maps version, language, screen size and navigation state. Screen captures and node text should be processed locally, minimally retained and limited to the user-selected task.

Google Play explicitly prohibits Accessibility API use to autonomously initiate, plan and execute actions/decisions. Its policy distinguishes narrow, clearly understood automation from autonomous agents. User consent alone does not remove that distribution restriction. Being a travel app with accessibility preferences does not automatically qualify the entire app as a disability accessibility tool. Do not promise unrestricted autonomous Maps control or Play approval. A supervised research prototype and a distributable app need separate feasibility conclusions. [Accessibility API policy](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en), [Android AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)

Prefer documented Google Maps URLs/intents for supported user-requested actions such as opening a destination or directions. They need no Maps API key, but do not guarantee offline completion or arbitrary map manipulation. The outcome depends on Google Maps' own available data. [Maps URLs](https://developers.google.com/maps/documentation/urls/get-started)

## What “offline” can honestly mean

After initial downloads, the panda may process a visible screen locally, respond using local assets and show Manzil's saved journey information. Google Maps retains its own offline limitations: downloaded maps provide driving support, but offline transit/walking directions and live traffic are not available according to its help documentation. Manzil cannot unlock those missing services through screen control. It may supplement with its independently stored, licensed journey instructions. [Google Maps offline help](https://support.google.com/maps/answer/6291838?co=GENIE.Platform%3DAndroid&hl=en-en)

Saved Google Maps content must not be assumed freely extractable or repackagable. The earlier Google content caching/display constraints still apply. Keep externally observed route facts, own-source journey data and model-generated explanations distinct; do not infer permission to bulk-save map imagery from screen-capture access.

## Bounded first-device verification

1. Device identification is complete for the user-authorized Redmi Note 10 Pro; see `SETUP_VERIFICATION_2026-11-05.md`. Repeat available-memory measurement immediately before inference tests. scrcpy5.0.1 is installed for visible real-device iteration; no emulator is needed for this first gate.
2. Verify a small overlay, explicit capture consent and clean stop/revoke behaviour on actual Maps, without changing a real journey.
3. Test visible text extraction and explain a single current screen; no claims about unseen route conditions.
4. Verify Tamil/English on-device speech and TTS with all networking disabled after asset download.
5. Benchmark one small local model while Maps and capture remain active. Use typed output with an allowlist of actions; invalid or uncertain output must not trigger gestures.
6. Verify one documented, user-requested Maps intent. Evaluate any further control separately against Android behaviour and distribution requirements.
7. Kill/restart the panda, lock/unlock the screen and remove connectivity/location. Record actual degradation and never continue acting from stale screenshots.

Success is a measured offline assistance loop on one known device. It is not yet proof of autonomous citywide navigation, reliable geospatial screen anchoring, universal Tamil support or compatibility with all Android phones.
