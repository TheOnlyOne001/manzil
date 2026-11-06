# Online destination resolution — 6 November 2026

## Fixed cause
The agent interpreted ECR Beach correctly but could only match an exact name in the saved Chennai gazetteer. A live Places API (New) Text Search call returned ECR Beach with coordinates; that lookup was missing from Android's execution path.

## Implemented
Online agent route requests now use Google Places Text Search directly on the phone, choose Google's top ranked result, show the actual name/address and request a Google walking route from the phone's current location. Change place opens the result list; choosing another result retains the current-location routing intent. Normal destination and starting-point search use the same lookup, debounced 400ms, with stale results hidden and canceled requests discarded. Search again retries explicitly. Offline or lookup failure falls back to saved places and labels that source. No-result and error states offer further search instead of an empty silent result list.

Google Places client reuses existing manifest Maps credentials with Android package/certificate headers, requests only used fields, has a 12-second total deadline and never prints keys/raw error bodies. Permanently closed, malformed, duplicate and invalid-coordinate results are filtered. Places result lists remain in memory and are not added to the offline gazetteer.

## Route preview vs guidance
A second blocker appeared on the Samsung: current indoor location accuracy was approximately 150m, exceeding the old 50m planning gate. Planning now accepts noncached fixes under 15 seconds old with finite accuracy from >0 to 500m. Starts worse than 50m are labelled approximate. Guidance retains its fresh <=30m gate; automatic reroutes retain <=50m. No simulated GPS or invented origin is used.

## Verified
- Real funded-key Places API HTTP200: ECR Beach, Tamil Nadu 600119.
- Real Gemini bare request “ECR beach”: plan_route / ECR Beach, Chennai.
- Actual Samsung app: typed ECR beach -> Gemini -> direct Google Places -> ECR Beach selected -> Google walking route 31.61km / 7h12. Phone fix had improved to ±6m on the successful run; this does not constitute a live 150m preview test. The coarse-preview bounds are covered by JVM tests.
- Change place: ECR Beach plus its address and Places by Google Maps attribution visible.
- 114 Android JVM tests, zero failures/errors. Final lint/build validation in online-search-final-build.log.
- Evidence: android-evidence/ecr-agent-preview.png, ecr-agent-preview.xml, ecr-google-results.xml.

## Remaining boundaries
Google relevance ranking is a default selection, not a guarantee of the user's intended beach; Change place exposes alternatives. Route arrival geometry comes from Google Routes, not an LLM. Cloud conversation still depends on the local backend/ADB bridge; Google Places and Routes calls themselves run directly from Android. Spoken recognition and physical walking remain pending from prior user instructions.

Final fallback check: Wi-Fi and mobile data disabled on Samsung; manual Marina search showed Saved Chennai places and Marina Beach Road. Both settings restored to their prior enabled values in a finally block. Returned to ECR route, online Google map. Evidence: android-evidence/search-offline-fallback.xml. Final build/lint successful; 114 tests passed.
