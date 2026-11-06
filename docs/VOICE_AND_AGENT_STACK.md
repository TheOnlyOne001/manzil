# Manzil voice and agent selection

Updated 5 November 2026 after user made offline behaviour the first feature. No cloud model, local inference engine or speech model has been benchmarked in Manzil yet. Model names/prices below are documented candidates, not measured quality/latency claims.

## Decision

Use the connected real phone for performance work, with scrcpy if its executable is located. An emulator is useful for screen sizes and lifecycle tests but does not establish this phone's local AI speed, microphone language support or OEM background behaviour.

The observed phone is Xiaomi M2101K6P, Android 12 / API 31, approximately 5.44 GiB reported RAM and 2.32 GiB available at inspection. Therefore start with a small local command engine, not an always-running multimodal LLM. Memory available varies and must be measured with Google Maps open.

| Role | First choice | Qualification |
|---|---|---|
| Guaranteed offline command path | Typed local intent dispatcher and visible buttons | Handles next/repeat/previous/show saved data/end journey without any LLM |
| Experimental offline natural-language tool router | FunctionGemma 270M through LiteRT-LM | Specialist function-calling model; benchmark and probably specialise for our few tools. Not a general assistant, vision model or established Tamil interpreter |
| Optional local explanatory text | Gemma 3-1B runtime candidate | Evaluate only if memory/latency budget permits; canned grounded explanations preferred initially |
| Offline speech input | Android on-device SpeechRecognizer when supported for selected language | Query availability and language support, download before trip, test airplane-mode cold start. `EXTRA_PREFER_OFFLINE` alone is not proof |
| Offline speech output | Android TextToSpeech installed voice with networkConnectionRequired=false | Test actual Tamil/English voice offline; downloadable voices are separate assets |
| Online low-cost dialogue | Gemini 3.1 Flash-Lite + typed function calls | Documented stable model; keeps cloud optional, structured calls validated locally |
| Online alternative | GPT-5.4 mini | Function calling/structured output supported; benchmark against same request set |
| Optional online natural voice | Gemini 3.8 Live or GPT-Realtime-2.1 Mini | Later comparison for interruption/streaming and Tamil; continuous sessions have different cost, network and battery profile |

Google's FunctionGemma overview positions it as a 270M tool-calling specialist; no vendor benchmark proves Redmi performance. LiteRT-LM supports local model execution. Gemini Nano through ML Kit is not our overlay baseline: device support is limited and the ML Kit docs restrict inference to the top foreground app, including restrictions on foreground services when another app is on top. Do not assume a floating overlay makes Manzil TOP.

Sources: [FunctionGemma](https://ai.google.dev/gemma/docs/functiongemma), [model card](https://ai.google.dev/gemma/docs/functiongemma/model_card), [LiteRT-LM](https://developers.google.com/edge/litert-lm/overview), [ML Kit limitations](https://developers.google.com/ml-kit/genai), [SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer), [TTS Voice API](https://developer.android.com/reference/android/speech/tts/Voice).

## Smooth interaction is mostly application engineering

Show local tap/listening feedback immediately; do not wait for inference to acknowledge input. Keep partial transcript editable and show an explicit stop control. Only show Working after recognition completes. Read at most one short instruction by default. Barge-in/stop must cancel audio and invalidate outstanding model/tool results using a request ID.

Local navigation controls use no model round-trip. Background I/O and inference must not block Compose rendering. Use bounded queues, short context, one model at a time and an inactivity unload policy. Avoid passing the whole map, full GPS history or screenshot every frame. Provide only trip state and the current relevant saved facts.

Suggested measurement targets (not achieved results): UI acknowledgement under 100 ms; warm simple tool decision under 1.5 s; voice response starts within 2.5 s of final transcript; stable 60 Hz UI where supported. Report p50/p95, cold load time, peakPSS and thermal state with Maps foreground. If the model cannot meet the target, preserve deterministic commands and expose the slower free-form feature separately.

## What the phone agent can control

Allowlist tools with strict parameters: journey ID, step ID, known destination coordinates, locale, boolean speech state. Reject arbitrary package names, shell commands, unknown URLs and screen-coordinate taps. Route/map data is input, never executable instructions.

Supported: our sheet, our map, our saved steps, our voice; Google Maps documented search/navigation intents. Opening a destination does not guarantee Google chooses our saved path. Google does not expose a public API to read its current route or its downloaded-area cache through these intents.

Seeing external screen is optional and separate: user-started screenshot or MediaProjection -> local text extraction/vision -> uncertain observation. Capture does not grant control and the map's pixels do not reveal all road/route semantics. OCR language support must be checked; do not assume Tamil OCR is bundled. No autonomous Google Maps click loop in this first slice. Google Play's AccessibilityService guidance prohibits general autonomous planning/execution through that API and distinguishes narrow deterministic automation/qualified accessibility tools.

Sources: [Maps intents](https://developer.android.com/guide/components/google-maps-intents), [screen capture](https://developer.android.com/media/grow/media-projection), [AccessibilityService policy](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en).

## Cloud cost comparison for later online mode

Prices checked from official docs on 5 November 2026; USD, standard processing, no cache discount. One illustrative exchange totals 2,000 input tokens and 500 output tokens across all calls. Tool results, reasoning tokens and repeated contexts can increase actual usage.

| Text model | Input /1M tokens | Output /1M tokens | Example exchange | 1,000 exchanges |
|---|---:|---:|---:|---:|
| Gemini 3.1 Flash-Lite | $0.25 | $1.50 | $0.00125 | $1.25 |
| Gemini 3.5 Flash-Lite | $0.30 | $2.50 | $0.00185 | $1.85 |
| GPT-5.4 mini | $0.75 | $4.50 | $0.00375 | $3.75 |

These exclude speech, map/traffic calls, hosting, taxes and retries. For optional cloud transcription, OpenAI lists gpt-4o-mini-transcribe at an estimated $0.003/min; 15 seconds per exchange adds $0.00075, or$0.75 per 1,000. Local TTS has no cloud token charge but voice quality/availability must be tested. Offline inference has no per-request API bill, but has model download, device memory, heat and battery costs.

Gemini's current Live price page lists audio input $3/M tokens and audio output $12/M for the relevant Live family; OpenAI lists Realtime2.1 Mini audio $10/M input and$20/M output. Do not infer latency, Tamil quality or session cost directly from token prices. Billing includes modality/context behaviour; benchmark one bounded session using provider usage records. Free-tier data use/limits differ from paid tier. Keep permanent keys on backend; use supported ephemeral credentials for direct live clients.

Sources: [Gemini official pricing](https://ai.google.dev/gemini-api/docs/pricing), [Flash-Lite model](https://ai.google.dev/gemini-api/docs/models/gemini-3.1-flash-lite), [GPT-5.4 mini](https://developers.openai.com/api/docs/models/gpt-5.4-mini), [OpenAI pricing](https://developers.openai.com/api/docs/pricing), [Gemini Live tools](https://ai.google.dev/gemini-api/docs/live-api/tools), [Live capabilities/languages](https://ai.google.dev/gemini-api/docs/live-api/capabilities). Retrieved Google source text saved in `design/research/` because web retrieval rejected the site's Markdown response; actual primary pages were fetched directly.

## First device proof

1. Native shell + saved-trip repository + panda bubble + local tool dispatcher.
2. Kill/relaunch in airplane mode; saved steps and manual progression work.
3. Probe installed speech languages and local TTS; run utterance tests without network.
4. Benchmark a small text-only local model against the same deterministic commands with Maps foreground.
5. Add on-demand screen observation only after measuring memory and demonstrating consent/lifecycle handling.

Do not download a multi-GB vision model simply because desktop setup is available. Test the smallest useful capability first. No new forecasting or pooled-ride implementation belongs in this first offline proof.
