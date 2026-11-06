# Feature 1: offline panda companion

Decision updated 5 November 2026: user prioritised offline behaviour over predictive departures. Earlier departure-planning board is retained as a later-feature study. Current board: `design/feature-1-board.svg` and `.png`. All readiness states in the board are illustrative, not implemented functionality.

## Product boundary

Manzil is a native Android companion for Google Maps. The first dependable deliverable is a prepared journey that remains readable, explainable and controllable locally after network loss. A universal AI that autonomously reads and controls every Google Maps screen without internet is not an established capability.

Distinguish four abilities:

1. Understand a request: bounded local intents, optional local language model.
2. Know the trip: our saved structured journey, permitted map data, observations and timestamps.
3. See the external screen: optional user-started screenshot/share or MediaProjection session, with separate local vision/OCR inference. It sees pixels, not the underlying route graph. No continuous screen scraping in the baseline.
4. Act: change Manzil state, read instructions aloud, advance steps, open Google Maps using documented intents. This is not arbitrary click/gesture control of Google Maps.

Google Maps offline driving requires a previously downloaded area covering the journey. Its documentation excludes offline walking/transit/cycling directions, traffic and alternatives. Manzil's own saved multimodal instructions cover that separate need. We cannot read or validate Google Maps' private offline-area cache using the documented intents. Label it user-checked, never app-verified.

## Precise top-layer flow

| State | What user sees | Action and next state | Failure / exit |
|---|---|---|---|
| S0 First open | Small panda, destination field, Saved journeys; no compulsory signup | Enter destination or choose saved journey | Manual origin if location permission denied |
| S1 Prepare while online | Destination, origin, needs, planned mode; inspectable itinerary | Prepare journey starts independent downloads and validations | Show exact missing item rather than one misleading 100% progress bar |
| S2 Readiness | Separate rows: saved instructions, our map assets, local AI model, speech input, speech output, Google offline area | Open Google Maps for user-managed area download; return and mark user-checked. Run Manzil local checks | Required instructions must exist; optional AI/voice may remain unavailable with text fallback |
| S3 Start | Selected saved trip with date and source age | Start companion; explicitly request overlay/location only when needed; hand off to Maps | No overlay grant: normal app + notification entry point remains usable |
| S4 Companion collapsed | Movable 56dp panda bubble, concise status indicator | Tap opens compact companion sheet | Never cover navigation turn card, Google attribution or system controls; dismiss accessible |
| S5 Network lost | One status change: Offline; saved at time; no-live-updates label | Local intents and saved instructions continue; no mandatory cloud retry | Cloud calls disabled/cancelled; pending requests cannot silently replace offline state |
| S6 Local interaction | Tap-to-talk or text, next saved instruction, source timestamp | Next / repeat / explain saved step / show saved map / open destination in Maps | Unsupported question: explain available saved information; never invent current ETA/closures |
| S7 Location lost | Location unavailable; last fix age; saved step list | User selects current step, next/previous/undo | Do not animate stale location as current; heading/position not guessed |
| S8 Outside saved coverage | Clearly marked end of known area and saved options | Return to saved steps or request online refresh when possible | Do not draw an invented route across unknown network |
| S9 Reconnected | Updates available, what changed | Fetch, compare and apply validated updates | Do not silently replace an active itinerary with a different route |
| S10 Finish | End journey, saved-trip access | Stop capture/location/voice services; collapse or dismiss companion | No always-on microphone |

Local commands for first slice: `show_saved_journey`, `next_step`, `previous_step`, `repeat_instruction`, `explain_saved_step`, `show_data_age`, `open_google_maps_destination`, `end_journey`. Each tool result includes success/failure and state version. The panda reports completion only after success. Unknown commands do not become arbitrary Android actions.

## Visual system

Use `design/tokens.json`. Paper-white decision sheets, deep-blue text and a single strong blue primary action. Teal distinguishes saved/available status; unknown information uses labelled neutral styling. Colour never stands alone. Keep one primary action per sheet and no more than three initial choices.

- Manrope Latin and Noto Sans Tamil, bundled locally with OFL licences. Body 16sp, secondary14sp; critical directions20–24sp. Respect system text scaling; no fixed-height text cards.
- Lucide is the one UI icon family: 24dp visible size, 2-unit stroke, minimum48dp touch region. Convert approved paths into VectorDrawable/ImageVector, test visual parity. No emoji as interface icons.
- 20dp horizontal padding, 8dp spacing rhythm with 4dp optical adjustments, 56dp primary buttons. Test320dp width and200% font scale, landscape/keyboard/system bars.
- The background map is contextual. It never competes with the next action. Show route highlight only on a map Manzil owns. A floating screen-coordinate highlight over Google Maps is not geographically anchored.
- Contrast targets: normal text at least4.5:1; large text and meaningful graphical controls at least3:1. Validate actual rendered states, including dimmed/disabled labels. Avoid glass/blur over map labels.
- Semantics: readable TalkBack order, state descriptions, text alternatives for map instructions, labels on icon controls; avoid duplicate panda announcements.

## Mascot and motion

An original small vector panda study is a placeholder design asset, not a finished character rig. Keep identical silhouette and proportions across states: idle, listening, working, speaking, caution, offline. Neutral helpful expression; no celebratory animation around hazards.

Use native Compose animation for initial blink/ear/tilt and sheet transitions. State changes180–240ms; brief one-shot acknowledgement. No perpetual jumping or spinning. Respect reduced-motion settings; keep a static fallback. Voice amplitude can drive a subtle speaking indicator only when actual audio is playing. Listening indicator reflects microphone state, not model status.

If later character work needs authored poses and transitions, choose one Rive state-machine runtime OR Lottie for authored clips. Neither library creates the mascot. Do not install both without a demonstrated need; validate the selected release against the Android baseline.

## Stack decision for this first slice

| Layer | Choice | Why |
|---|---|---|
| Native UI | Kotlin + Jetpack Compose + Material3 primitives with Manzil tokens | System integration, accessibility semantics and animation control |
| State | ViewModel + StateFlow, explicit local state machine | Same controls work without network or model |
| Storage | Room + versioned journey/model asset files | Offline source of truth; checksums, completion and expiry |
| External Maps | Documented Android intents; optional application overlay | Supports companion experience without claiming private route access |
| Independent offline map | MapLibre Native with permitted complete asset pack | Optional fallback view; downloaded Overpass JSON alone is insufficient |
| Agent | Local typed command router first; LiteRT-LM candidate after device benchmark | Cloud cannot be the offline dependency |
| Voice | On-device recognition when language pack is verified; local TTS; text always available | Offline availability is device/language specific |
| Refresh | WorkManager for resumable preparation; active trip foreground service where required | Deferred download vs active navigation are separate jobs |
| Backend | Existing FastAPI adapter environment, online preparation only | Journey remains usable when backend is unreachable |

Target phone: user reports connected Redmi Note10 Pro and authorises ADB. Read measured hardware in SETUP_VERIFICATION_2026-11-05.md; never assume a flagship/Nano-capable device. Use installed compileSdk35 until dependency requirements justify36. Proposed Gradle/AGP pins still need an actual build.

## Definition of tested offline behaviour

Must test on the real phone, not just a warm browser/emulator cache:

- Prepare -> kill app -> enable airplane mode with Wi-Fi also off -> cold start saved journey.
- Same test with mobile data enabled but backend unreachable, to test real connectivity logic.
- Location available vs disabled vs stale; manual step advancement works in each appropriate state.
- Model missing/corrupted/too slow: commands and saved instructions still work.
- Microphone denied or Tamil model unavailable: text controls and readable directions remain.
- Google Maps not installed, downloaded area absent/expired, handoff ignored: return to saved journey with clear status.
- Screen locked/app backgrounded/process killed: no fabricated success or stale live position.
- No unbounded retry loop; no main-thread inference; measure frame timing, peak memory, latency and battery/thermal behaviour.
- Do not show current bus arrivals, live flood clearance, or working-lift claims from cached records.

Sources: [Google offline Maps](https://support.google.com/maps/answer/6291838?co=GENIE.Platform%3DAndroid&hl=en), [Maps intents](https://developer.android.com/guide/components/google-maps-intents), [MediaProjection](https://developer.android.com/media/grow/media-projection), [48dp touch targets](https://support.google.com/accessibility/android/answer/7101858), [ML Kit GenAI limitations](https://developers.google.com/ml-kit/genai), [Play accessibility automation](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en).
