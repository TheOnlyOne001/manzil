# Resume point — 2026-11-06

Goal remains active, not complete.

Latest APK built/tested/linted and installed on Samsung RZGL40EETBZ. OfflineActivity is launcher; Maps companion remains accessible via header icon. Full packaged map + search + E75/E80 routing data verified on-device (371 matching SHA256s). 53 unit/integration tests pass. Actual phone calculated new routes without internet (1.01km/306ms, another0.83km), rendered local map, retained route/preview provenance after cold start, and installed its own pack without ADB provisioning. See OFFLINE_DEVICE_VERIFICATION.md.

Device locked itself with unlock pattern during final western route UI check. User was asked via async to unlock; wait for unlock/inspect actual state, never attempt pattern. Network settings restored to airplane0/wifi1/mobiledata1. No download/build processes need restarting; completed logs authoritative. Latest full build docs/offline-full-pack-build.log.

Pending: final western-area real-device route check; fresh accurate GPS with internet off; active foreground guidance/screen-lock and physical deviation/reroute verification. User chose continue indoors; recent GPS ~80m accuracy/stale. Do not relax accuracy gates or inject mock GPS to claim real movement. Current saved route is manual preview to SRM Potheri Station Stop,0.83km. Other directions were not simulated. Tests with fixtures are separate from live proof.

Root owns frontend. Claude and native agents reported quota limits; user reset may differ by provider. No need retry agents for pending device authentication/GPS.

Update: User unlocked phone; final western device route PASSED offline0.60km/202ms, turns sheet inspected. Phone now on GPS start to SRM Potheri Station Stop, waiting for accurate fix. ActiveGPSrequest confirmed; lastfix stale75m. Connectionsettings restored. Pending async question asks whether user can now do open-sky/shortsafe walk test. Do not ask for unlock again unless currentstate locked.

Latest: user chose Leave the outdoor test pending. No pending question remains. Final installed companion transition checked: Maps companion shows saved original instruction; Back returns to offline destination. Remaining realGPS/movement-dependent checks must wait for user to resume physical test.
