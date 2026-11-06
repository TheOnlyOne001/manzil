# Night Walk visual correction

The previous 11.5 zoom cutoff hid all support icons in the city overview. Removed it. The previous per-road glow used fixed 22/13/7/3.5 dp widths and accumulated opacity where segments overlapped. Lit geometry now draws as a single network in three zoom-scaled strokes plus a fine centre line, reducing the merged yellow patches.

Overview and street views show up to 16 spaced support icons: women police first (up to 4), police (5), hospitals (4), route-adjacent petrol (3), pharmacies (2), subject to collision avoidance and available screen area. No icon is an open-now or guaranteed-safety claim. Pink shields identify explicit women police records; other support shields/crosses remain teal. Route chevrons now render below icons.

Added 23 explicitly labelled AWPS point records from Greater Chennai Police station KML via OpenCity: https://data.opencity.in/dataset/chennai-police-stations . Source KML and women-station directory CSV are saved in data/derived. The KML station codes are retained; names and coordinates are not inferred from ordinary stations. Existing OSM women stations remain. Current staffing, hours and locations are not live verified. Bundle directory markers supplement older downloaded feeds so they remain available offline.

Backend unittest: 37 passing. Android build, lint and device visual verification logged in docs/night-awps-final-build.log and android-evidence.

Final visual review: pink AWPS shields rendered in the Chennai overview with teal support icons and narrow gold road strokes. Lighting strokes are clipped away from the start arrow/label and arrival pin, and support icons avoid the expanded legend.
