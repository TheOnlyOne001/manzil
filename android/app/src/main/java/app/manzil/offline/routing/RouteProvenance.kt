package app.manzil.offline.routing

/**
 * Which engine produced a route. Persisted by [RouteSource.storageKey]; unknown or missing keys
 * (routes saved before provenance existed) are read as [OFFLINE_BROUTER], which is what every
 * older build produced.
 */
enum class RouteSource(val storageKey: String) {
    /** Google Routes API `computeRoutes`, travelMode WALK, fetched over validated internet. */
    GOOGLE_ROUTES("google_routes"),

    /** On-device BRouter over the bundled, checksummed Chennai graph. */
    OFFLINE_BROUTER("offline_brouter");

    companion object {
        fun fromStorage(key: String?): RouteSource = entries.firstOrNull { it.storageKey == key } ?: OFFLINE_BROUTER
    }
}

/**
 * Google requires this notice on every displayed walking route
 * (RouteTravelMode docs: WALK routes "are in beta and might sometimes be missing clear sidewalks,
 * pedestrian paths"). Attached to every Google route in [OfflineRoute.warnings].
 */
const val GOOGLE_WALKING_BETA_WARNING =
    "Walking directions are in beta and may be missing clear sidewalks or pedestrian paths."
