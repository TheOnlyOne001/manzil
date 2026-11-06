# Samsung live-run verification — 5 November 2026

Device: Samsung F56, ADB SM-E566B / RZGL40EETBZ, Android16/API36, 1080×2340 at450dpi. Native app package app.manzil. Application source: android/. Sibling ../manzil-app is an untouched warmup scaffold.

## Verified on the real phone

- Debug APK installed and launched. Root-authored frontend is the default.
- User selected the existing Google Maps navigation session. Manzil did not launch a replacement destination or choose its route.
- Found Maps notification permission denied; user explicitly approved enabling it for this test. Enabled Maps notifications, Manzil notification access, display-over-other-apps and its foreground notification.
- Actual Maps notification category was navigation, channel1_foreground_1. First observed text: “70 m · Turn left onto Mahatma Gandhi Rd”. Later it updated to “110 m · At the roundabout, continue straight to stay on Mahatma Gandhi Rd”. These were received from Maps; they are not bundled fixture strings.
- Floating56dp panda appeared at the left above the real Maps app. Tap expanded a296dp-wide dark panel with the actual observed instruction, read control, open-app action and close/collapse controls. No next-step button is shown in live mode. Screenshot review confirms top maneuver, right control column and bottom ETA remain visible in this tested layout. Universal obstruction avoidance is not claimed.
- Actual Maps observation persisted in app-private storage. Airplane mode1, Wi-Fi0, mobile data0; Manzil force-stopped and cold-started. Actual saved instruction remained readable and was labelled saved/current unavailable. Original airplane0/Wi-Fi1/data1 restored in finally block.
- Separate earlier sample test retained progress at step2 after an offline cold start. This is storage evidence, not actual route guidance.
-35 JVM tests passed, zero failures: dispatcher13, parser7, journey repository9, live filter/freshness/codec4, URI2.

## Boundaries

Maps displayed Searching for GPS during the live run. This is a real integration and notification-update test, not a moving-road/GPS-accuracy validation. No road journey was completed.

This version observes Maps notification text and displays/caches it. It does not understand map pixels, read a full route graph, forecast traffic, reroute offline, or autonomously control Maps UI. No learned local model or speech recognition is included. TTS engine reports an installed offline voice; actual audio intelligibility has not been listening-verified.

Notification access is a broad Android permission; code rejects non-Maps packages before reading their extras. The verified navigation category is required, so unrelated Maps downloads/promotions are rejected. The app has no INTERNET permission. Last observation is not a complete offline journey pack. Its freshness state is not proof of GPS/current position.

## Evidence

Screenshots and UI snapshots are in android-evidence/:
- samsung-live-check: actual Maps text in Manzil
- samsung-panda-live-map: collapsed panda over Maps
- samsung-live-overlay-expanded: actual instruction in expanded dark panel
- samsung-real-offline-coldstart: persisted actual instruction with saved label offline

Final build evidence: docs/android-live-final-build.log. Tests: android/app/build/test-results/testDebugUnitTest/. Lint: android/app/build/reports/lint-results-debug.html. Build reproducibly with scripts/build_android.ps1; add -Install to install on the Samsung.

Claude Opus produced the core persistence/command/live listener implementation, Sonnet assisted build diagnosis. Root authored final frontend, integrated live mode, fixed build lint configuration, added live-data tests and performed phone QA. Claude usage limit was reached; remaining work was completed directly.
