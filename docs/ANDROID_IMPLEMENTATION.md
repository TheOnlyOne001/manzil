# Manzil Android — first runnable slice

Written 5 November 2026. Source lives in `android/`. Status: **sources complete, not yet built or run**. The root agent owns Gradle wrapper/downloads, the build, `adb` installs and device tests; nothing below claims a successful build or device result.

## What this slice does

A native Kotlin + Jetpack Compose app (`app.manzil`) that keeps one saved journey usable with no network:

1. **Home / prepare.** Shows the bundled *sample* journey Guindy → Chennai Central, clearly tagged `SAMPLE · NOT VERIFIED` with a disclaimer that it is not operational transit information. One main button: **Save journey to this phone**. Readiness rows show truthful states: saved steps (not saved / saved at time), Google Maps offline area (*not checked by Manzil*, with an "Open Google Maps" link), read-aloud voice (installed offline voice or "steps stay as text"), typed commands (works offline; no speech input or AI model in this build). Empty state under "On this phone" until something is saved; saved rows show progress and a remove control (with confirmation).
2. **Active journey.** Current step card (step N of M, mode, instruction, caution), Previous / Repeat / Explain controls, optional read-aloud button, a tappable step timeline ("I'm at this step"), and a bottom bar with **Ask** + one primary action (Next step → Finish journey on the last step → Back to home when finished). Shows "Saved <time> · No live updates", the sample notice, and Android's network status (Offline / No internet / Online). More section: open destination in Google Maps, panda bubble toggle, read steps aloud toggle, voice picker, "How old is this?", finish.
3. **Ask Panda bottom sheet.** Suggestion chips issue typed tool calls directly; a text field goes through a deterministic keyword parser. Unsupported, live-data, location and emergency requests get explicit refusals. IME-aware (`navigationBarsPadding().imePadding()` inside a zero-inset sheet).
4. **Durable progress.** Every accepted command is written synchronously (SharedPreferences `commit()` on `Dispatchers.IO`) *before* the UI or overlay reports success. Cold start with a journey in progress opens the journey screen at the persisted step.
5. **Optional panda bubble.** A plain Android View overlay (`TYPE_APPLICATION_OVERLAY`) owned by a `specialUse` foreground service. Draggable 56dp panda with a status dot; snaps to the nearest edge (one 180 ms `ValueAnimator`, honours the system animator scale); tap expands a compact panel with **Next / Repeat / Open Manzil / Close** (each ≥ 56dp tall, ≥ 48dp wide, labelled). It clamps inside system bars and cutouts, never takes focus, and only shows Manzil's own saved step.
6. **Google Maps hand-off.** Documented `geo:lat,lng?q=lat,lng(label)` intent with `setPackage("com.google.android.apps.maps")` when the package is visible, otherwise any `geo:` handler, otherwise an honest failure. Copy always says Google Maps picks its own route and Manzil cannot see it.

## Toolchain (pinned, unverified build)

| Item | Version |
|---|---|
| AGP | 8.11.1 (`com.android.application`) |
| Gradle | 8.13 (wrapper supplied by root; not in this tree) |
| Kotlin | 2.2.21 (`org.jetbrains.kotlin.android` + `org.jetbrains.kotlin.plugin.compose`) |
| JDK | 17 (`jvmTarget` 17; no toolchain auto-download) |
| compileSdk / targetSdk / minSdk | 35 / 35 / 26 |
| Compose BOM | 2025.05.01 (material3, foundation, ui) |
| activity-compose | 1.10.1 |
| lifecycle (runtime-ktx, runtime-compose, viewmodel-compose) | 2.9.0 |
| core-ktx | 1.15.0 (1.17+ needs SDK 36) |
| kotlinx-coroutines-android | 1.10.2 |
| Tests | junit 4.13.2, org.json 20240303 (JVM tests only) |

No dependency in the list requires compileSdk 36. There is **no INTERNET permission** and no network code.

### Bundled design assets

The sandbox used for this task could not copy binaries, so `app/build.gradle.kts` registers a per-variant `bundle<Variant>DesignAssets` task that copies, at build time:

- `design/assets/fonts/manrope/Manrope[wght].ttf` → generated `res/font/manrope.ttf`
- `design/assets/licenses/LUCIDE-LICENSE` and `design/assets/fonts/manrope/OFL.txt` → generated `assets/licenses/`

It is wired with AGP's `variant.sources.res/assets.addGeneratedSourceDirectory`. If someone places `app/src/main/res/font/manrope.ttf` by hand, the font copy is skipped automatically to avoid a duplicate resource. Manrope's variable default weight is 200, so Compose (`FontVariation.Settings`) and the overlay (`setFontVariationSettings("'wght' N")`) set weights explicitly.

## Source map

```
android/app/src/main/java/app/manzil/
  ManzilApplication.kt      AppGraph: settings, repository, controller, speech (process singletons)
  MainActivity.kt           edge-to-edge, intent handling, overlay permission + bubble start (OverlayControl)
  model/                    Journey, steps, progress, StoredState; SampleJourneys; TimeText
  data/                     KeyValueStore (+SharedPreferences impl), JourneyCodec (versioned JSON), JourneyRepository
  agent/                    LocalCommand allowlist, CommandParser, CommandDispatcher (pure), JourneyController
  system/                   AppSettings, NetworkStatus, SpeechOutput (TTS), MapsLauncher + GeoUri
  overlay/                  OverlayService (FGS specialUse), CompanionOverlay (Views), PandaBubbleView
  ui/                       ManzilViewModel, ManzilRoot, HomeScreen, JourneyScreen, CommandSheet, components/, theme/
  frontend/                 added separately by the root agent during this task; MainActivity now renders
                            FrontendRoot (which uses ManzilViewModel). ui/ManzilRoot remains as the
                            original composition and is currently not referenced by MainActivity.
android/app/src/main/res/   strings, colors, theme, Lucide vectors, panda vector, adaptive launcher icon
android/app/src/test/       CommandParserTest, CommandDispatcherTest, JourneyRepositoryTest, GeoUriTest
```

## Agent boundary

- `LocalCommand` is a sealed allowlist: `show_saved_journey`, `next_step`, `previous_step`, `repeat_instruction`, `explain_saved_step`, `show_data_age`, `open_google_maps_destination`, `end_journey`, `select_step`, `stop_speaking`, `list_commands`, `live_info_unavailable`, `unsupported`. There is no shell, URL, package-name, coordinate-tap or accessibility action.
- `CommandParser` is a deterministic English keyword matcher (whole words). Non-English text, including Tamil script, returns `Unsupported(NOT_UNDERSTOOD)`. Live questions (traffic, delays, next train, platform, weather, fares...) return `LiveInfoUnavailable`. Location questions say location isn't used. Emergency words say Manzil can't call anyone and point to 112.
- `CommandDispatcher` is pure; each `CommandOutcome` has `tool`, `success`, `message`, `stateVersion` and an optional effect (speak, stop speech, open Maps, show journey). Explanations come only from the saved step's `explanation` text.
- `JourneyController` serialises commands with a `Mutex`, persists progress, then returns. A failed write returns `success=false` ("Couldn't save progress..."). Maps outcomes are reported only after `startActivity` succeeds.

## Persistence and the Room migration

This slice stores one JSON document (`schemaVersion: 1`) under `manzil.journeys.v1` in the `manzil_journeys` SharedPreferences file, written with `commit()`. Behaviour covered by tests:

- save → new repository over the same store (simulated process death) → identical journeys and progress;
- corrupt JSON is copied to `manzil.journeys.v1.corrupt`, then the store resets and the UI says so;
- a newer `schemaVersion` is never overwritten (writes refused, UI explains);
- progress pointing at a missing journey or an out-of-range step is dropped on load.

Planned Room migration (next slice, not done here):

1. Entities `journey` (id, origin, destination name/lat/lng, isSample, source, preparedAt, savedAt, contentChecksum), `journey_step` (journeyId, position, id, title, detail, explanation, mode, caution) and `journey_progress` (journeyId PK, stepIndex, status, startedAt, updatedAt, stateVersion).
2. `JourneyRepository` keeps its public API (`state`, `saveJourney`, `removeJourney`, `writeProgress`); the Room implementation updates progress in a transaction and exposes `Flow` mapped to `StoredState`.
3. One-time import: on first Room open, if `manzil.journeys.v1` exists, decode with `JourneyCodec`, insert in one transaction, then write a `migratedToRoom=true` marker and keep the old key for one release as a fallback.
4. Export the Room schema JSON and add a migration test that seeds the SharedPreferences document and asserts identical `StoredState`.

## Overlay / foreground service details

- Manifest: `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, optional `POST_NOTIFICATIONS`; service `android:foregroundServiceType="specialUse"` with a `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` explanation.
- Start path: Journey screen switch → if `Settings.canDrawOverlays` is false, an explainer dialog → `ACTION_MANAGE_OVERLAY_PERMISSION` for this package → on return (`onResume`) the bubble starts automatically only if the user had asked for it. `MainActivity.startBubble()` refuses unless the activity is `RESUMED`. On Android 13+ the notification permission is requested once; declining still starts the bubble.
- `startForeground(id, notification, FOREGROUND_SERVICE_TYPE_SPECIAL_USE)` on API 34+, plain `startForeground` below. `START_NOT_STICKY`; the service never restarts itself. Stop via the bubble's Close, the notification's "Hide bubble", or the in-app switch.
- No accessibility service, MediaProjection, screenshots, GPS, microphone or cloud.

## Voice

`SpeechOutput` binds the default `TextToSpeech` engine and lists only voices with `isNetworkConnectionRequired == false` and without the `notInstalled` feature, ordered en-IN, Tamil, other English, others. That is the engine's own claim and must be checked on the device in airplane mode. With no engine or no such voice the UI says "No installed offline voice found. Steps stay as text." Reading aloud is off by default; the speaker button on the step card always reads on explicit tap. There is **no speech recognition and no LLM** in this build, and the UI says so.

## Accessibility and layout

- Edge-to-edge with dark system-bar icons (light-only palette in this slice); `Scaffold` content uses `systemBars ∪ displayCutout`; bottom bars pad for navigation bars + horizontal cutouts; the sheet handles IME. All screens scroll vertically, so 200% font scale and landscape do not clip. Content width is capped at 600dp.
- Touch targets ≥ 48dp (most controls 56dp). Icon-only buttons have labels; decorative icons and the panda are silent; the route line is one sentence; step rows merge semantics with state descriptions ("current step", "done") and a "Set as current step" click label; replies use a polite live region; headings are marked.
- Colour is never the only signal: every status pairs an icon and words. Text colours were chosen for ≥ 4.5:1 on their backgrounds (deep teal `#066A6E` on the teal tint).
- Motion: one short panda nod after a successful command (skipped when the animator scale is 0), a 240 ms screen crossfade, the bubble edge snap. Nothing loops.

## Unit tests (JVM, `./gradlew :app:testDebugUnitTest`)

- `CommandParserTest`: command phrases, step numbers in digits/words/ordinals, precedence, live/location/emergency refusals, arbitrary/Tamil/symbol input stays unsupported.
- `CommandDispatcherTest`: next/previous/select bounds, version increments, finished-journey rules, caution text, grounded explanations, exact data-age wording, Maps hand-off message.
- `JourneyRepositoryTest`: round trip, simulated process death, durable finish, failed write reports failure, corrupt reset with backup, newer schema not overwritten, removal drops progress, out-of-range progress dropped.
- `GeoUriTest`: documented `geo:` form and RFC 3986 label encoding.

These have not been run yet.

## Device test checklist for the root agent

1. Fresh install → Home shows sample tag, empty "On this phone", CTA "Save journey to this phone". Save → row appears, readiness row "Saved today at …".
2. Start → step 1; Next twice → step 3; force-stop (`adb shell am force-stop app.manzil`) with airplane mode and Wi-Fi off → relaunch opens the journey at step 3.
3. Mobile data on but no validated internet → pill shows "No internet"; everything still works.
4. Ask sheet: "step 2", "explain", "next train?", "where am I", "delete files", Tamil text, keyboard open at 200% font scale.
5. Read aloud with and without an installed offline voice; airplane-mode cold start.
6. Maps: with Google Maps installed (opens destination), disabled/uninstalled (fallback or honest failure).
7. Bubble: deny then grant overlay permission; drag, snap, tap expand near each corner and in landscape; Next/Repeat persist; Open Manzil; Close; notification "Hide bubble"; process kill while bubble shown (service is not restarted).
8. Redmi/MIUI: confirm overlay permission screen and any extra "display pop-up windows while running in background" toggle.

## Known limits

- Build and tests not executed in this task. Lucide `wifi` and `rotate-ccw` paths were transcribed (not in `design/assets/icons`) and `chevron-left` is a mirror of `chevron-right`; all need visual review against the source.
- One bundled sample journey only. No journey preparation, download, refresh or validation pipeline; no backend calls.
- Parser is English keywords only. Tamil UI text, Noto Sans Tamil and Tamil command handling are not implemented.
- Tool reply text is English Kotlin strings (UI chrome is in `strings.xml`); reply localisation is pending.
- No dark theme yet (tokens define only a light palette).
- SharedPreferences, not Room (see migration plan). No WorkManager, no MapLibre map.
- The overlay's pre-API-30 safe-area math approximates the navigation bar at 48dp.
- Manzil cannot read Google Maps' route, navigation state or offline-area cache; the readiness row says "Not checked by Manzil" permanently.
- TTS "offline" relies on the engine's voice flags; actual offline behaviour must be measured on the phone.

## Licences

Lucide icons: ISC (with MIT notice for Feather-derived icons), bundled as `assets/licenses/LUCIDE-LICENSE.txt`. Manrope: SIL OFL 1.1, bundled as `assets/licenses/MANROPE-OFL.txt`. The panda is Manzil's own vector, converted from `design/assets/panda-study.svg`.
