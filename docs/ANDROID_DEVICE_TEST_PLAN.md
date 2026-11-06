# First Android build: device acceptance

Target: connected Redmi Note 10 Pro, Android 12/API 31. Implementation: `android/`, package `app.manzil`. The separate sibling `../manzil-app` warmup scaffold is preserved.

This is a saved-journey companion proof. A bundled sample is not an operational route recommendation. Google Maps owns its route and downloads; Manzil does not verify either through its destination intent.

## Build gate

Run `scripts/build_android.ps1`: debug assembly, local unit tests and Android lint. Install only a successfully built APK. Wrapper 8.13 JAR is verified against Gradle's published SHA256; the distribution checksum is pinned in wrapper properties.

## Physical device checks

| Check | Evidence required |
|---|---|
| First launch | No crash; readable safe areas; sample clearly identified |
| Save | Explicit save persists a complete sample and source metadata |
| Advance | Next/previous updates visible step; boundaries remain valid |
| Process death | Force-stop then relaunch retains saved step and journey |
| Offline cold start | Airplane mode, Wi-Fi and mobile data off; force-stop/relaunch; saved instructions and commands work |
| Unknown command | No invented answer, route, ETA or external action |
| Keyboard | Command field and action remain visible with IME open |
| Larger text | At 200% font scale, content scrolls and controls stay reachable |
| Maps handoff | Actual Google Maps opens at intended destination; no claim of matched route |
| Overlay denied | Journey still works inside app without overlay permission |
| Overlay enabled | Panda appears above Maps, expands, advances a step, dismisses; app sees same persisted step |
| Offline speech output | Installed engine selects a non-network voice; actual audio still requires listening verification |
| End | Stops companion; saved trip remains accessible |

Restore original airplane, Wi-Fi, data and font-scale settings after tests. Do not clear another application's data, download Maps areas, or inspect unrelated phone content. APK installation and app-scoped state changes are authorized by the user.

## Evidence handling

`scripts/android_ui.py snapshot NAME` writes a phone screenshot and UI hierarchy to `android-evidence/`. `tap LABEL` targets one exact observed accessible label; ambiguity fails rather than tapping a guessed location. Report actual observed results in a separate implementation verification file. A passing build is not proof of device behaviour.

## Beyond this slice

Local learned command model, speech input, screenshot observation, location-aware progress, real journey preparation and arbitrary Google Maps UI control remain distinct capabilities. Do not label them complete because buttons or a panda overlay work.
