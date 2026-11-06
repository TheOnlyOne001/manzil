# Manzil environment audit

Checked 5 November 2026. This is an inventory and setup proposal, not a functioning Android development environment or application. No SDK, emulator, global package, model, Docker image or application was installed by this audit. No ancestor or project AGENTS.md was found in the checked paths.

| Component | Observed state | Consequence |
|---|---|---|
| Android Studio | Not found in normal installation paths or Windows uninstall registry | Install official stable Studio or command-line SDK when setup begins |
| Android SDK / adb / emulator | Android SDK at `C:\Users\shaya\android-sdk` configured with `cmdline-tools` 12.0, `platform-tools` (adb 1.0.41), `platforms;android-35`, `build-tools;35.0.0` and `34.0.0` | CLI build/adb available; emulator remains optional |
| Android virtual device | Default `.android/avd` folder absent | Prefer a physical device first; emulator remains optional |
| Java on PATH | OpenJDK 17.0.2; JAVA_HOME points to Scoop openjdk17 | Correct major version for proposed Android build, but old patch level; use maintained JDK 17 |
| Additional Java | Oracle JDK 24.0.2 registered at `C:\Program Files\Java\jdk-24` | Do not globally switch Java for Android or OTP |
| Gradle / Kotlin CLI | Neither on PATH | Normal: project wrapper and Gradle plugins provide these; no global installation needed |
| Python | 3.13.5 at `C:\Python313\python.exe` | Usable for metadata/data scripts; backend dependency resolution must be tested |
| uv | 0.11.2 | Ready for project-local dependency management |
| Node / npm | Node 22.17.1; npm available | Optional tools only; native Android does not require React Native or Node |
| Git | 2.50.1.windows.1 | Available |
| Docker | 27.4.0 client; Linux-engine named pipe unavailable | Docker engine is not running/reachable; avoid making initial setup depend on it |
| Hardware | 13.9 GB RAM, hypervisor reported present | Start with a small routing area; Android emulator plus a large OTP build may exhaust RAM |
| Storage | ~54 GB free on C: | Ample storage for SDK platforms, build tools and virtual environments |

Absence above means not found by the bounded audit, not an exhaustive disk search. Hypervisor presence does not prove Android emulator acceleration is working. No physical Android device connection was checked because adb is absent.

Recheck without installing anything:

```powershell
Set-Location 'C:\Users\shaya\Desktop\Projects\manzil-research'
.\scripts\check_environment.ps1
```

The script optionally accepts `-OutputPath` for a JSON report. It intentionally inspects only three non-secret environment variables and never prints API keys or connected device serials.

## Proposed tool mapping

| Need | Initial technology | Notes |
|---|---|---|
| Native Android UI/panda/control cards | Kotlin, Jetpack Compose, Material 3, Compose animations | Native Android; simple local animation first |
| Custom map/route icons/offline rendering | MapLibre Native Android in Compose `AndroidView` | Use published AAR, not native source build; tile provider/license is separate |
| Optional Google map mode | Google Maps SDK / Maps Compose and Routes API | Separate integration; preserve Google attribution/display/caching requirements |
| Saved journey and provenance | Room database; files for licensed map assets | Store itinerary, fallback steps, issue/expiry timestamps and manual progress |
| Resumable refresh/download jobs | WorkManager | Not suitable for continuous immediate navigation updates |
| Live foreground journey position | Android location APIs / FusedLocationProvider + location foreground service | Start with an explicit in-app journey action; handle denied/coarse/unavailable location |
| Voice | Android SpeechRecognizer / TextToSpeech behind interfaces | Tamil availability and on-device support must be probed on the target device; text input always works |
| API aggregation | Python + FastAPI + HTTPX + Pydantic in uv environment | A narrow backend adapter layer; no key embedded in APK |
| Organizer supplied graph and custom constraints | Python graph prototype, initially NetworkX plus explicit constraint solver | NetworkX shortest path alone is not multimodal/timetable routing |
| Real GTFS + street routing | OpenTripPlanner 2.6.0, separate JDK 21 process, OSM PBF + validated GTFS | Conditional on usable feed calendar, connected street graph, resource budget |
| Weather/traffic/safety inputs | HTTP adapters with source/observation/expiry metadata | Open-Meteo probe succeeded; parent reports IMD public API probes returned 401 |
| AI agent | Typed tool calls and strict data schemas, provider adapter | No model/provider committed; route feasibility stays in routing code |

Proposed baseline: open data/MapLibre for Manzil's own offline journey. Do not bulk-download OSM standard tile servers. Do not draw Google Routes output on MapLibre or assume it can be cached for offline use. An OSM PBF is routing/source data, not a ready-to-display offline basemap: tiles, style, fonts and sprites are additional assets.

## Primary references

- [Android Studio/AGP compatibility](https://developer.android.com/build/releases/about-agp)
- [Kotlin/Gradle/AGP compatibility](https://kotlinlang.org/docs/gradle-configure-project.html)
- [Compose BOM](https://developer.android.com/develop/ui/compose/bom)
- [MapLibre Android artifacts](https://maplibre.org/maplibre-native/android/examples/data/rendering-engine/)
- [MapLibre Compose offline resources](https://maplibre.org/maplibre-compose/offline/)
- [Room releases](https://developer.android.com/jetpack/androidx/releases/room)
- [WorkManager scope](https://developer.android.com/develop/background-work/background-tasks/persistent)
- [Foreground location service requirements](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [OTP 2.6 tutorial, JDK and input requirements](https://docs.opentripplanner.org/en/v2.6.0/Basic-Tutorial/)
