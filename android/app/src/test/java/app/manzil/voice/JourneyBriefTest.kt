package app.manzil.voice
import org.junit.Assert.*
import org.junit.Test
class JourneyBriefTest {
 @Test fun cachedOrPoorFixNeverReady(){for(pair in listOf(true to 5f,false to 120f,false to Float.NaN)){assertFalse(JourneyBrief.signals(true,true,true,true,pair.first,100,pair.second)[1].ready)}}
 @Test fun staleFixNeverReady(){assertFalse(JourneyBrief.signals(true,true,true,true,false,15000,5f)[1].ready)}
 @Test fun offlineDoesNotInvalidatePack(){val s=JourneyBrief.signals(true,true,false,true,false,100,5f);assertTrue(s[0].ready);assertTrue(s[1].ready);assertFalse(s[3].ready)}
 @Test fun routeOutsidePackNotCovered(){assertFalse(JourneyBrief.signals(true,false,true,true,false,100,5f)[0].ready)}
 @Test fun missingRouteAndPackAreNotInvented(){val s=JourneyBrief.signals(false,true,true,false,true,null,null);assertFalse(s[0].ready);assertFalse(s[2].ready)}
}
