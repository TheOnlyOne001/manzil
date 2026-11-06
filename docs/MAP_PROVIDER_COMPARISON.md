# Map provider assessment — 6 November 2026

Recommendation: retain provider-neutral overlays and the already implemented MapLibre/BRouter offline pack. Evaluate Mappls as an online India-focused alternative with a real Chennai route/report probe after SDK authentication. Do not replace the working offline engine on the strength of a product brochure or the count of allocated APIs. Google remains a viable online provider; predictive traffic by itself is not unique to Mappls or Manzil.

## Supplied Mappls files: what is actually known
Inspected only metadata of `C:/Users/shaya/Desktop/app1793907268870i1673381300`: one .a.conf file (2080 bytes) and one .a.olf file (256 bytes). Both are opaque binary, not UTF-8/JSON. No contents or credentials were printed, decoded, uploaded, or copied into documentation. No credentialed network request was made.

The names match the current Android configuration mechanism. Their existence does NOT establish successful authentication, package/fingerprint match, validity/expiry, which SDKs or33APIs are enabled, quota balance, commercial offline license, or Chennai data quality. The user-reported2SDK/33API allocation is not yet independently verified; it cannot be inferred from these opaque files. The authoritative checks are the developer dashboard and successful SDK calls with this app/signing certificate.

## Current Android authentication (not legacy client-secret setup)
Mappls official Getting Started requires configuration files tied to package name and signing certificate SHA-256, placed in the app module root; apply `com.mappls.services.android`, using plugin dependency `com.mappls.services:mappls-services:1.0.0` shown in the docs. Their prerequisites include Java17 and compileSdk35. This matches our baseline. Verify the chosen SDK documentation version before pinning artifacts. [Official setup](https://developer.mappls.com/documentation/sdk/android/docs/v2.0.1/Getting-Started/).

The official error guide explicitly distinguishes missing plugin, invalid/corrupt config, package/fingerprint mismatch, missing map configuration and API authorization failures. A valid map display does not prove every API is entitled. [Official errors](https://developer.mappls.com/documentation/sdk/android/docs/v2.0.0/SDK-Error-code/).

## Capability comparison

| Need | Mappls documented capability | Google documented capability | Manzil implication |
|---|---|---|---|
| Leave-time advice | Predictive Directions supports live/predictive traffic and arrival/departure time | Routes supports departureTime and traffic-aware routing/models | Both can compare candidate departure times. Show estimated arrival and travel duration separately; leaving20minutes sooner is not necessarily20minutes less travel. |
| Nearby road incidents | Android Nearby Report API takes bounds and returns report data | No equivalent general Google Maps consumer-report export established in this review | Mappls is a useful candidate; validate actual Chennai density, categories, timestamps and entitlement before presenting live hazards. |
| Actual offline start and reroute | Navigation product advertises hybrid offline/online operation; exact SDK/package/license available to this app unverified | Google Navigation Android FAQ explicitly says no offline mode, only journey pre-cache | Keep our explicit offline graph/search/map pack. Do not mistake cache for arbitrary offline route computation. |
| Offline walking | No verified current Mappls Android integration procedure/entitlement for offline walking found here | Google Maps consumer offline help says walking/transit/cycling directions unavailable offline | Current Manzil offline walking is concrete added value; preserve it. |
| Historical flood/waterlogging overlay | Our own evidence-labelled dataset can be drawn via map SDK layers | Our own evidence-labelled dataset can be drawn via map SDK overlays | Provider choice does not make historical points current flood observations. |
| India/Chennai quality | India-focused routing/report products warrant a local trial | Routes supports multiple modes; already integrated online candidate | No objective comparative Chennai accuracy test performed. Do not claim one wins on data freshness/ETA accuracy without paired measurements. |

Sources: [Mappls predictive routing](https://developer.mappls.com/documentation/sdk/android/docs/v2.0.0/Predictive-Route-APIs/), [Mappls Android Nearby Reports](https://developer.mappls.com/documentation/sdk/android/docs/v2.0.2/Nearby-Report/), [Mappls Navigation product](https://about.mappls.com/api/navigation-sdk/), [Google traffic configuration](https://developers.google.com/maps/documentation/routes/config_trade_offs), [Google traffic options](https://developers.google.com/maps/documentation/routes/traffic-opt), [Google Navigation FAQ](https://developers.google.com/maps/documentation/navigation/android-sdk/faq), [Google consumer offline limitations](https://support.google.com/maps/answer/6291838/).

## Concrete next verification
1. Check `app.manzil` and the currently installed signing certificate SHA-256 against the Mappls console registration; integrate files through the official plugin without exposing their content in logs/source control.
2. Validate online map load and record only success/error codes. Then separately call Predictive Directions and Nearby Reports for a bounded Chennai area. Record elapsed time, response timestamp, report category/age, and quota/authorization failures. Empty reports is a legitimate result, not a broken map.
3. Compare the same origin/destination and departures now,+15,+30,+45minutes. Do not claim a20minute saving unless returned estimates support it. Traffic predictions require connectivity and remain estimates.
4. For offline Mappls, require a documented licensed pack-download/offline-routing workflow and an airplane-mode coldstart/new-destination/reroute test before claiming equivalent offline functionality. The product-level hybrid claim is insufficient implementation evidence.
5. Keep common `MapOverlay` geometry/evidence interface: Google or Mappls online; MapLibre+BRouter offline. Do not transfer proprietary provider tiles/data to our offline pack unless explicitly licensed. No current license determination was made in this review.

## UI and agent value
Panda should explain an actionable difference: compare departure windows, surface a timestamped incident near the selected route, distinguish historical risk from current reports, or preserve functioning offline walking. The map SDK provides coordinate-to-screen projection for accurate road overlays inside Manzil's map. It does not expose the camera projection of the separate Google Maps app; SDK integration is not a way to draw geographically anchored roads over another app's arbitrary camera.

No Android/source integration changes were made as part of this assessment.
