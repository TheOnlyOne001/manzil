# Installed native-mobile and visual-design skills

Installed 5 November 2026 using the existing Codex `skill-installer` helper, with exact Git commit refs and `--method download`. These skills will be available on the **next user turn**. This installed instructions/reference material only: no Android application, SDK, Figma integration, model, web framework or deployment was created.

## Installed set

| Skill | Installed SKILL.md | Scope for Manzil |
|---|---|---|
| `edge-to-edge` | `C:\Users\shaya\.codex\skills\edge-to-edge\SKILL.md` | Keep map overlays, microphone button, bottom sheets and input fields clear of system bars and keyboard; Compose and SDK35+ prerequisites |
| `jetpack-compose-performance` | `C:\Users\shaya\.codex\skills\jetpack-compose-performance\SKILL.md` | Diagnose measured Compose jank; includes animation, state, drawing and layout references useful for panda animation and map-adjacent UI |
| `frontend-design` | `C:\Users\shaya\.codex\skills\frontend-design\SKILL.md` | Visual direction, colour/type hierarchy, restrained motion and clear interaction copy; apply to native mobile art direction, not as a reason to build a web app |

## Provenance and licence

Android skills source: [android/skills](https://github.com/android/skills/tree/bf68e2d66a992a1e6aeb7e9263293cc23fcbf475), commit `bf68e2d66a992a1e6aeb7e9263293cc23fcbf475`, Google LLC, Apache-2.0.

- [Edge-to-edge source](https://github.com/android/skills/blob/bf68e2d66a992a1e6aeb7e9263293cc23fcbf475/system/edge-to-edge/SKILL.md)
- [Compose performance source](https://github.com/android/skills/blob/bf68e2d66a992a1e6aeb7e9263293cc23fcbf475/jetpack-compose/jetpack-compose-performance/SKILL.md)

The Android repository stores its licence at root rather than inside these skill directories. The unmodified root `LICENSE.txt` from the same commit was copied into each installed directory to preserve the licence reference.

Visual-design source: [anthropics/skills frontend-design](https://github.com/anthropics/skills/tree/ca45fc496a3436251f191354091d557da31c15b5/skills/frontend-design), commit `ca45fc496a3436251f191354091d557da31c15b5`. This specific skill includes Apache-2.0 `LICENSE.txt`; do not infer a licence for all other Anthropic skills from it.

| SKILL.md | SHA-256 |
|---|---|
| edge-to-edge | `4ca04cb6a5330b96e51410bc815f878b4ca642fba46fe5e1ad724c94e04d6eb8` |
| jetpack-compose-performance | `a1680a1ae079faa47fc706bf034b88d4c4c4903dfa4187e98e0af8b056e1848b` |
| frontend-design | `d91970639e9f5c37682ac7ab60094d35f1c7c1f38d731bd56396563aee10c1d3` |

## Inspection and selection

- Inspected candidate SKILL.md content before installation. Installed files contain Markdown/reference material and licences; no executable `.py`, `.ps1`, `.sh`, `.bat`, `.js` or `.exe` files were present. No downloaded repository code was executed.
- Both installer commands returned exit code 0. SKILL.md files, YAML names, source hashes and all local Markdown reference targets were checked; no missing local linked references were found.
- Installed three skills, not the full repositories. Existing `pdf`, `playwright` and system skills were left intact. No names collided.
- Inspected but **did not install** Android `adaptive`: its current instructions require Navigation 3 and introduce experimental MediaQuery/Grid/FlexBox APIs. That would prematurely expand the initial phone-first setup.
- No dedicated accessibility-only Android skill was found in the inspected repository tree. Do not claim one is installed. Use official Android accessibility guidance and actual TalkBack/font-scale/touch-target verification during implementation.
- No custom skill was authored. No AGP-upgrade, billing, camera, Figma or web deployment skill was added.

## Guidance for the next implementation agent

1. Read the applicable installed SKILL.md when beginning the corresponding task; keep the user's native Android direction and project scope authoritative.
2. Apply visual-design guidance to a compact native token system and map interaction hierarchy. Typography must preserve Tamil/English legibility; decorative choices must not reduce navigation clarity.
3. Use edge-to-edge when laying out the Compose app; preserve the established SDK/build baseline unless actual compatibility requires a deliberate update.
4. Apply performance guidance to existing code with evidence. The skill contains newer, version-qualified Compose APIs; check the project's pinned Compose version before adopting them. Do not upgrade dependencies merely to use a recommendation. Read its category reference before proposing a fix, and profile an appropriate release build before claiming performance improvement.
5. Treat animation as a communication tool. Accessibility still requires dedicated checks: screen-reader semantics, readable large text, sufficient touch targets, contrast, reduced-motion behaviour and one-handed reach. Installation does not prove any of these checks passed.

## Reproduction commands

These commands would abort if rerun over the existing skill directories; they document the completed installation, not a request to reinstall.

```powershell
python 'C:\Users\shaya\.codex\skills\.system\skill-installer\scripts\install-skill-from-github.py' --repo android/skills --ref bf68e2d66a992a1e6aeb7e9263293cc23fcbf475 --path system/edge-to-edge jetpack-compose/jetpack-compose-performance --dest 'C:\Users\shaya\.codex\skills' --method download
python 'C:\Users\shaya\.codex\skills\.system\skill-installer\scripts\install-skill-from-github.py' --repo anthropics/skills --ref ca45fc496a3436251f191354091d557da31c15b5 --path skills/frontend-design --dest 'C:\Users\shaya\.codex\skills' --method download
```
