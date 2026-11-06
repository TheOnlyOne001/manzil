# Panda presentation demo

New build and Cloud Run revision `manzil-agent-00003-x76`, 2026-11-06.

## Suggested 90-second sequence
1. Ask/type **Find a pharmacy near me**. Panda resolves the category through Gemini, asks Google Places with current-location bias, and shows up to six returned choices sorted by straight-line distance. Pick one to request its real Google walking route. On-device result: Big save pharmacy, 0.60 km, 9 minutes. Returned choices are not an exhaustive nearest-place ranking; opening hours are not checked.
2. Ask **Brief me before I leave**. The device opens a clean departure sheet with route duration/distance, installed offline pack plus route coverage, GPS freshness/accuracy and internet status. Those facts are computed on device, not invented by Gemini. The **Journey check** shortcut works without a cloud request.
3. Tap **Inspect night layer** to inspect mapped lighting. This does not produce a verified safer alternative.
4. Ask **I need emergency help**. Panda opens SOS choices. The user must choose to open the dialler and place a call; no automatic emergency call or message.
5. Ask **Stop navigation** to stop guidance while keeping the destination/route. Ask **Take me to Marina Beach instead** for the existing contextual replanning flow.

## Validation
- Android: 119 unit tests, zero failures; assemble and lint passed.
- Backend: 92 tests passed (Claude backend run).
- Real deployed Gemini: 8/8 intent cases passed, including Tamil typed pharmacy query, nearby metro, departure brief, offline readiness, emergency help, stop guidance and night route. See `panda-live-validation.json`.
- Samsung: nearby Google Places choices and selected 600 m/9-minute Google route confirmed. Departure sheet visually inspected and device pack/location facts checked.
- Speech recognition in four languages and outdoor movement remain separate, previously deferred tests. These checks use real typed requests, not simulated route data.
