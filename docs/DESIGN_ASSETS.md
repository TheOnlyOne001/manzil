# Manzil design asset handoff

Official source assets acquired 5 November 2026. Assets are local and self-contained; no runtime CDN is required. This package contains interface icons and font candidates, not a generated panda mascot or a finished design system.

## Icons

One consistent family: **Lucide**. Source repository: https://github.com/lucide-icons/lucide . Pinned commit: `ee8e10d746a10907205366cfd984922f0c58939a`.

32 original SVGs in `design/assets/icons/`:

`navigation`, `route`, `map-pin`, `clock`, `wallet`, `mic`, `volume-2`, `accessibility`, `bus`, `train-front`, `footprints`, `cloud-rain`, `triangle-exclamation-point`, `wifi-off`, `arrow-left`, `chevron-right`, `check`, `x`, `sliders-horizontal`, `bookmark`, `locate-fixed`, `users`, `shield`, `info`, `undo-2`, `pause`, `play`, `search`, `menu`, `chevron-down`, `circle-question-mark`, `download`.

Current-source naming matters: the official `triangle-alert` page redirects to `triangle-exclamation-point`, and `circle-help` redirects to `circle-question-mark`. This package uses the canonical filenames present at the pinned source commit.

The `accessibility` icon is a general accessibility symbol, **not a wheelchair pictogram**. Do not label it as a verified wheelchair access indicator or imply a route is accessible merely because the icon appears. No unsupported wheelchair asset has been invented.

Each file is checked for well-formed SVG XML, `viewBox="0 0 24 24"`, outline `fill="none"`, `stroke="currentColor"`, stroke width 2, and absence of scripts, external references, embedded images and event handlers. Downloaded source geometry remains unmodified. Full results and per-file SHA-256 hashes are in `design/assets/manifest.json`.

Keep the complete `design/assets/licenses/LUCIDE-LICENSE` in the project distribution: it contains Lucide's permissive ISC notice and the MIT notice for Feather-derived icons. Official license: https://lucide.dev/license . Do not strip provenance when copying icons into app resources.

## Native Android handoff

Raw SVG files are **not drop-in `painterResource` drawable resources** for Compose. Use Android Studio Vector Asset Studio to import SVG into `res/drawable/ic_<name>.xml`, or generate reviewed Compose `ImageVector` definitions. Preserve the 24-unit viewport, 2-unit stroke, round caps and joins. Replace `currentColor` with an import-compatible explicit color in a temporary conversion copy if the importer requires it; apply theme tint in Compose. Keep original source SVGs unchanged.

Validate every converted icon in light/dark UI at its actual display size; XML validity of the source alone does not establish Android conversion fidelity. Compound geometry, arcs, circles, opacity and rounded rectangles need visual review. Name Android resources with underscores, for example `train-front.svg` becomes `ic_train_front.xml`.

For an actionable button, the icon needs a translated accessible name. Decorative icons adjacent to equivalent text should not repeat the announcement. Directional arrows may need layout-direction mirroring; transport and hazard icons should not be mirrored automatically.

Official references:

- https://developer.android.com/studio/write/vector-asset-studio
- https://developer.android.com/develop/ui/compose/graphics/images/loading

No Android conversion or device rendering is claimed in this asset-download task.

## Fonts

Small optional font candidates from the official Google Fonts repository, pinned commit `eb20e9cf19d97cf579fa3867d729d4222d6c59e1`:

| Family | Local file | Binary size | Purpose |
|---|---|---:|---|
| Manrope | `design/assets/fonts/manrope/Manrope[wght].ttf` | 164,700 bytes | Latin-script interface candidate |
| Noto Sans Tamil | `design/assets/fonts/notosanstamil/NotoSansTamil[wdth,wght].ttf` | 340,668 bytes | Tamil-script companion candidate |

Each family includes its upstream `OFL.txt` and `METADATA.pb`. The SIL Open Font License 1.1 applies; retain notices and observe reserved-name provisions when modifying fonts. Source locations: https://github.com/google/fonts/tree/main/ofl/manrope and https://github.com/google/fonts/tree/main/ofl/notosanstamil . The manifest uses immutable commit URLs rather than these moving browsing links.

The two binaries total 505,368 bytes. These are downloaded files, not fonts installed globally. Rename copies to Android-compatible lowercase resource filenames when packaging in `res/font/`; keep originals for provenance. Configure script-appropriate font families/fallbacks and verify Tamil shaping, mixed Tamil/Latin strings, weights and clipping on the target Android device before finalising typography.

FontTools successfully parsed both font files (`design/assets/font-validation.json`). Manrope's weight axis is 200-800 with a 200 default, so set the intended UI weight explicitly; it contains no Tamil-block codepoints. Noto Sans Tamil provides weight 100-900 and width 62.5-100, with 72 Tamil-block codepoints. This validates file structure and character coverage, not device shaping/rendering.

## Reproduce

```powershell
python scripts/download_design_assets.py
```

The script uses the Python standard library, requests immutable official repository URLs, and caps each response at 5 MiB. It downloads only the named SVGs, the license, and the two font families with metadata. Asset choices and application styling remain the parent design task's responsibility.
