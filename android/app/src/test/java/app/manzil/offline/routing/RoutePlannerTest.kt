package app.manzil.offline.routing

import app.manzil.system.NetworkStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class RoutePlannerTest {
    private val a = RouteCoordinate(13.08, 80.27)
    private val b = RouteCoordinate(13.09, 80.28)
    private fun route(source: RouteSource) = OfflineRoute(listOf(a, b), 1500, 1200, emptyList(), 5, source)
    private val googleRoute = route(RouteSource.GOOGLE_ROUTES)
    private val deviceRoute = route(RouteSource.OFFLINE_BROUTER)

    private class Counter { var calls = 0 }

    private fun planner(online: OnlineRouteSource, offlineCalls: Counter, offline: () -> OfflineRoute = { deviceRoute }) =
        RoutePlanner(online) { _, _ -> offlineCalls.calls++; offline() }

    @Test fun policyUsesGoogleOnlyOnValidatedInternetWithKey() {
        assertEquals(RoutingDecision.Online(offlineFallback = true), RoutingPolicy.decide(NetworkStatus.ONLINE, true, true))
        assertEquals(RoutingDecision.Online(offlineFallback = false), RoutingPolicy.decide(NetworkStatus.ONLINE, true, false))
        for (status in listOf(NetworkStatus.OFFLINE, NetworkStatus.NO_INTERNET)) {
            assertTrue("Captive portal / no network goes straight offline", RoutingPolicy.decide(status, true, true) is RoutingDecision.OfflineOnly)
            assertTrue(RoutingPolicy.decide(status, true, false) is RoutingDecision.Unavailable)
        }
        assertTrue(RoutingPolicy.decide(NetworkStatus.ONLINE, false, true) is RoutingDecision.OfflineOnly)
        assertTrue(RoutingPolicy.decide(NetworkStatus.ONLINE, false, false) is RoutingDecision.Unavailable)
        val outside = RoutingPolicy.decide(NetworkStatus.OFFLINE, true, false) as RoutingDecision.Unavailable
        assertTrue(outside.reason.startsWith("Outside"))
    }

    @Test fun googleSuccessNeverRunsBRouter() = runBlocking {
        val offline = Counter()
        val planned = planner({ _, _ -> googleRoute }, offline).plan(a, b, RoutingDecision.Online(true))
        assertSame(googleRoute, planned.route)
        assertNull(planned.onlineFailure)
        assertEquals(0, offline.calls)
    }

    @Test fun googleFailureInsideCoverageFallsBackWithReason() = runBlocking {
        val offline = Counter()
        for (failure in listOf<Exception>(OnlineRoutingException(OnlineFailureKind.TIMEOUT), IOException("reset"))) {
            val planned = planner({ _, _ -> throw failure }, offline).plan(a, b, RoutingDecision.Online(true))
            assertEquals(RouteSource.OFFLINE_BROUTER, planned.route.source)
            assertEquals(if (failure is OnlineRoutingException) OnlineFailureKind.TIMEOUT else OnlineFailureKind.NETWORK, planned.onlineFailure)
        }
        assertEquals(2, offline.calls)
    }

    @Test fun googleFailureOutsideCoverageDoesNotTouchBRouter() = runBlocking {
        val offline = Counter()
        val e = runCatching {
            planner({ _, _ -> throw OnlineRoutingException(OnlineFailureKind.KEY_REJECTED, 403) }, offline).plan(a, b, RoutingDecision.Online(false))
        }.exceptionOrNull()
        assertTrue(e is RoutePlanningException)
        assertTrue(e!!.message!!.contains("Outside"))
        assertEquals(0, offline.calls)
    }

    @Test fun offlineOnlyNeverCallsGoogle() = runBlocking {
        val offline = Counter()
        val planned = planner({ _, _ -> fail("Google must not be called without validated internet"); googleRoute }, offline)
            .plan(a, b, RoutingDecision.OfflineOnly("No internet"))
        assertEquals(1, offline.calls)
        assertEquals("No internet", planned.offlineReason)
    }

    @Test fun bothProvidersFailingReportsBoth() = runBlocking {
        val offline = Counter()
        val e = runCatching {
            planner({ _, _ -> throw OnlineRoutingException(OnlineFailureKind.NO_ROUTE) }, offline) { throw OfflineRoutingException("No connected walking path") }
                .plan(a, b, RoutingDecision.Online(true))
        }.exceptionOrNull()
        assertTrue(e is RoutePlanningException)
        assertTrue(e!!.message!!.contains(OnlineFailureKind.NO_ROUTE.userText))
        assertTrue(e.message!!.contains("No connected walking path"))
    }

    @Test fun cancellingDuringGoogleRequestDoesNotFallBack() = runBlocking {
        val offline = Counter()
        val entered = CompletableDeferred<Unit>()
        val job = launch {
            planner({ _, _ -> entered.complete(Unit); awaitCancellation() }, offline).plan(a, b, RoutingDecision.Online(true))
            fail("plan must not complete after cancellation")
        }
        entered.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertEquals(0, offline.calls)
    }

    @Test fun socketErrorCausedByCancellationDoesNotFallBack() = runBlocking {
        // Aborting a blocking socket on cancel surfaces as IOException, not CancellationException.
        val offline = Counter()
        val entered = CompletableDeferred<Unit>()
        val job = launch {
            planner({ _, _ ->
                entered.complete(Unit)
                try { awaitCancellation() } finally { throw IOException("Socket closed") }
            }, offline).plan(a, b, RoutingDecision.Online(true))
        }
        entered.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertEquals(0, offline.calls)
    }

    @Test fun cancellingDuringFallbackPropagates() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        var reported: Throwable? = null
        val job = launch {
            try {
                RoutePlanner({ _, _ -> throw OnlineRoutingException(OnlineFailureKind.TIMEOUT) }) { _, _ ->
                    entered.complete(Unit)
                    try { awaitCancellation() } finally { throw OfflineRoutingException("terminated") }
                }.plan(a, b, RoutingDecision.Online(true))
            } catch (e: RoutePlanningException) {
                reported = e
            }
        }
        entered.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertNull("Cancelled fallback must not be reported as a routing failure", reported)
    }
}
