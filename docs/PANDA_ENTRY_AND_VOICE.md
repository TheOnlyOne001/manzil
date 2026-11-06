# Panda entry and voice — 6 November 2026

## Entry design
Map stays visible. White surfaces, navy #142D4E, bamboo teal #087E73, quiet sage #F0F5F3. One large question and one search/voice field; Night Walk and Flood context are secondary inline actions. Saved journeys remain a compact continuation row. The panda in the header returns to this home view. Legacy MainActivity entry without a saved-notification intent redirects to the map.

Native vector panda blinks/nods on entry, voice state changes and route arrival. Segmented bamboo animation runs only while loading a pack, requesting a route, acquiring a requested fix, or waiting on native speech recognition. System reduced-motion setting disables animated frames. It is indeterminate, not a fabricated percentage.

## Working scope
Voice sheet selects English, Hindi, Tamil or Telugu and requests Android microphone permission only when the user presses the microphone. Phone speech service returns a transcript for review. Use this request changes a known map layer, recenters, or puts a place query into existing local search; the user chooses the result. These are a small deterministic command set, not an AI conversation. Native-script place search is limited to names in the saved gazetteer; no automatic translation is implemented. Speech availability and online/offline support depend on the installed service and language packs.

Closing, backgrounding or stopping cancels recognition and rejects late callbacks. No background hotword, automatic dialing or hidden recording. The typed field exercises the same command path without pretending to be microphone evidence.

## Next: living agent
See VOICE_AGENT_HANDOFF.md. Recommended cloud path: app audio -> authenticated Manzil WebSocket backend -> Vertex Gemini Live native audio -> spoken response and typed tool requests -> app state validation -> map action/result. Route geometry and hazard facts stay in Manzil's existing data adapters. Use current map layer, destination and route summary as context; continuous screen video is unnecessary for our own SDK map.

Panda should listen with a small ear/halo response, grow bamboo while an actual tool request is pending, blink once on success, and show a compact retry affordance on failure. Voice volume can drive a subtle waveform only after real microphone samples are available. Avoid continuous pulsing that implies listening when idle.

Cloud credentials, enabled service/model/region, project billing eligibility, backend authentication and usage caps are still required. The Maps key is not a Vertex session credential. The $300 trial is subject to account/product eligibility and expiry; no credit balance was inspected. Start with short explicit sessions, close idle streams, log actual billable usage and compare latency/quality for all four languages before enabling persistent dialogue.

## Verification
Final build installed on Samsung SM-E566B / RZGL40EETBZ. assembleDebug, lintDebug and 89 JVM tests passed (0 failures/errors/skips), see panda-entry-final-build.log.

On-device UI checks: legacy MainActivity entry redirects to welcome; Night Walk quick action exposes lighting legend and SOS; Flood context quick action exposes history legend; voice sheet fully expanded with visible input; typed “show night walk” through the voice command path selects Night Walk; typed “Take me to Marina Beach” becomes query “Marina Beach” and offers Marina Beach Road. These typed checks are not speech-recognition evidence.

Evidence: android-evidence/panda-home-final.png, panda-voice-final.png, panda-command-search.xml, legacy-entry-redirect.xml. User explicitly deferred spoken-language testing. No cloud conversation, language accuracy, microphone end-to-end or outdoor navigation verification is claimed.
