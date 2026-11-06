package app.manzil.offline.location
import org.junit.Assert.*
import org.junit.Test
class RoutePreviewLocationPolicyTest {
 @Test fun approximateFreshFixCanPlan(){assertTrue(RoutePreviewLocationPolicy.accepts(false,1000,150f));assertTrue(RoutePreviewLocationPolicy.accepts(false,1000,500f))}
 @Test fun noStaleOrCachedStarts(){assertFalse(RoutePreviewLocationPolicy.accepts(true,1000,4f));assertFalse(RoutePreviewLocationPolicy.accepts(false,15000,4f));assertFalse(RoutePreviewLocationPolicy.accepts(false,-1,4f))}
 @Test fun rejectsUnknownOrUnboundedAccuracy(){listOf(null,Float.NaN,Float.POSITIVE_INFINITY,0f,-1f,501f).forEach{assertFalse(RoutePreviewLocationPolicy.accepts(false,1000,it))}}
}
