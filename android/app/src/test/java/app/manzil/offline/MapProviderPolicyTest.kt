package app.manzil.offline
import app.manzil.system.NetworkStatus
import org.junit.Assert.assertEquals
import org.junit.Test
class MapProviderPolicyTest {
 @Test fun googleRequiresValidatedNetworkAndCredentials(){
  assertEquals(MapProvider.GOOGLE,MapProviderPolicy.choose(NetworkStatus.ONLINE,true,true))
  for(status in listOf(NetworkStatus.OFFLINE,NetworkStatus.NO_INTERNET))assertEquals(MapProvider.LOCAL,MapProviderPolicy.choose(status,true,true))
  assertEquals(MapProvider.LOCAL,MapProviderPolicy.choose(NetworkStatus.ONLINE,false,true))
  assertEquals(MapProvider.LOCAL,MapProviderPolicy.choose(NetworkStatus.ONLINE,true,false))
  assertEquals(MapProvider.LOCAL,MapProviderPolicy.choose(NetworkStatus.ONLINE,true,true,true))
 }
}
