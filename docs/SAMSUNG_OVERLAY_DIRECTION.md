# Samsung test target and overlay direction

Updated 5 November 2026. User replaced the Redmi with Samsung F56. ADB identifies the authorized device as SM-E566B, serial RZGL40EETBZ, Android 16/API 36. Reported RAM is 7,606,636 kB (nominal 8 GB). Available memory is transient, not an inference budget. Build and UI helper scripts now target this serial. Earlier Redmi measurements remain historical only.

The user's two Maps screenshots show navigation in day and night modes. Preserve the top turn banner and secondary turn hint, the right-hand control column, the bottom ETA panel, and the user's route position. These images inform placement; they do not establish a safe position for every Maps layout.

Collapsed: a movable 56dp panda with no text card. Prefer a left-edge starting position around mid-screen, below turn guidance and above the lower recenter/speed controls. Allow the user to move it; never claim automatic awareness of Maps controls.

Expanded: one instruction, a subdued sample/saved step label, read and open controls, and one next action. No four-button grid. Collapse from the panda and dismiss from an accessible close control. Avoid constant motion, glass effects, or an opaque full-width bottom sheet above Maps.

Day and night surfaces need explicit legibility checks. Follow system appearance initially; Google Maps can independently use a different mode, so system theme does not prove map-theme matching. Do not claim screen reading or automatic geographic anchoring.

Root owns frontend design and visual review. Claude Opus handles core state, persistence and agent logic; Sonnet handles build logistics. Root-authored presentation components are under android/app/src/main/java/app/manzil/frontend; integration and phone visual acceptance must be verified before describing them as shipped.
