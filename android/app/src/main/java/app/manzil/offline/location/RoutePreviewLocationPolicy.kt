package app.manzil.offline.location

/** Planning can tolerate a bounded uncertain start; guidance keeps its separate <=30m gate. */
object RoutePreviewLocationPolicy {
 fun accepts(cached:Boolean,ageMillis:Long,accuracyMeters:Float?):Boolean =
  !cached && ageMillis in 0 until 15000 && accuracyMeters!=null && accuracyMeters.isFinite() && accuracyMeters>0 && accuracyMeters<=500f
}
