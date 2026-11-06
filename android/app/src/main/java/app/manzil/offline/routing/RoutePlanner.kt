package app.manzil.offline.routing

import app.manzil.system.NetworkStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** What the planner will attempt for one request. Pure, so it is unit-tested directly. */
sealed interface RoutingDecision {
    /** Google first; BRouter only if Google fails and [offlineFallback] is true. */
    data class Online(val offlineFallback: Boolean) : RoutingDecision
    data class OfflineOnly(val reason: String) : RoutingDecision
    data class Unavailable(val reason: String) : RoutingDecision
}

object RoutingPolicy {
    /**
     * Google is tried only on an Android-validated default network with a configured key (a
     * captive portal / "connected, no internet" never burns the timeout). BRouter is used only
     * when both endpoints are inside the installed pack.
     */
    fun decide(network: NetworkStatus, onlineConfigured: Boolean, bothInOfflineCoverage: Boolean): RoutingDecision = when {
        network == NetworkStatus.ONLINE && onlineConfigured -> RoutingDecision.Online(offlineFallback = bothInOfflineCoverage)
        bothInOfflineCoverage -> RoutingDecision.OfflineOnly(
            if (network != NetworkStatus.ONLINE) "No internet" else "Google routing key is not configured",
        )
        network != NetworkStatus.ONLINE ->
            RoutingDecision.Unavailable("Outside the installed Chennai offline area, and there is no internet for Google routing.")
        else -> RoutingDecision.Unavailable("Outside the installed Chennai offline area, and Google routing is not configured.")
    }
}

/** Result plus the online failure that caused a fallback, so the UI can say why it is offline. */
data class PlannedRoute(val route: OfflineRoute, val onlineFailure: OnlineFailureKind? = null, val offlineReason: String? = null)

class RoutePlanningException(message: String) : Exception(message)

/** Single online attempt; implementations must be cancellable and bounded by their own timeout. */
fun interface OnlineRouteSource {
    suspend fun route(origin: RouteCoordinate, destination: RouteCoordinate): OfflineRoute
}

/**
 * Orchestrates Google → BRouter. Cancellation always propagates and never starts the fallback:
 * every catch re-checks the coroutine before treating an exception as a provider failure, because
 * aborting a blocking socket on cancel surfaces as an IOException rather than a CancellationException.
 */
class RoutePlanner(
    private val online: OnlineRouteSource,
    private val offline: suspend (RouteCoordinate, RouteCoordinate) -> OfflineRoute,
) {
    suspend fun plan(origin: RouteCoordinate, destination: RouteCoordinate, decision: RoutingDecision): PlannedRoute {
        when (decision) {
            is RoutingDecision.Unavailable -> throw RoutePlanningException(decision.reason)
            is RoutingDecision.OfflineOnly -> return PlannedRoute(runOffline(origin, destination, null), offlineReason = decision.reason)
            is RoutingDecision.Online -> {
                val failure = try {
                    return PlannedRoute(online.route(origin, destination))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: OnlineRoutingException) {
                    currentCoroutineContext().ensureActive(); e.kind
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive(); OnlineFailureKind.NETWORK
                }
                if (!decision.offlineFallback) {
                    throw RoutePlanningException("Google route failed: ${failure.userText}. Outside the installed Chennai offline area, so there is no offline fallback.")
                }
                return PlannedRoute(runOffline(origin, destination, failure), onlineFailure = failure)
            }
        }
    }

    private suspend fun runOffline(origin: RouteCoordinate, destination: RouteCoordinate, onlineFailure: OnlineFailureKind?): OfflineRoute {
        try {
            return offline(origin, destination)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            val offlineText = e.message ?: "Offline route calculation failed"
            throw RoutePlanningException(if (onlineFailure == null) offlineText else "Google route failed: ${onlineFailure.userText}. Offline fallback also failed: $offlineText")
        }
    }
}
