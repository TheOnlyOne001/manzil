package app.manzil.offline

import app.manzil.system.NetworkStatus

enum class MapProvider { LOCAL, GOOGLE }
object MapProviderPolicy {
 fun choose(network:NetworkStatus,keyConfigured:Boolean,playServicesAvailable:Boolean,failed:Boolean=false):MapProvider =
  if(network==NetworkStatus.ONLINE && keyConfigured && playServicesAvailable && !failed)MapProvider.GOOGLE else MapProvider.LOCAL
}
