package app.manzil.voice
import app.manzil.offline.Place
import org.junit.Assert.*
import org.junit.Test
class AgentDestinationResolverTest {
 @Test fun groundedMarinaRoad(){val p=Place("Marina Beach Road",13.0,80.0);assertEquals(p,AgentDestinationResolver.resolve("Marina Beach",listOf(p)))}
 @Test fun cityQualifier(){val p=Place("Marina Beach Road",13.0,80.0);assertEquals(p,AgentDestinationResolver.resolve("Marina Beach, Chennai",listOf(p)))}
 @Test fun ambiguousRequiresChoice(){assertNull(AgentDestinationResolver.resolve("Temple",listOf(Place("Temple",13.0,80.0),Place("Temple",13.1,80.0))))}
 @Test fun ambiguousSearchRetainsUsefulName(){assertEquals("Guindy",AgentDestinationResolver.searchQuery("Guindy, Chennai"));assertEquals("Chennai",AgentDestinationResolver.searchQuery("Chennai"))}
 @Test fun unrelatedNameIsNeverGuessed(){assertNull(AgentDestinationResolver.resolve("Safest beach",listOf(Place("Marina Beach",13.0,80.0))))}
}
