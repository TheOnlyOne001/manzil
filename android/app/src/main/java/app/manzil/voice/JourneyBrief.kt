package app.manzil.voice

data class BriefSignal(val title:String,val detail:String,val ready:Boolean)

/** Device facts only: never a model-generated safety verdict. */
object JourneyBrief {
 fun signals(packReady:Boolean,inCoverage:Boolean,online:Boolean,hasRoute:Boolean,cached:Boolean,ageMs:Long?,accuracy:Float?):List<BriefSignal> {
  val fixReady=!cached && ageMs!=null && ageMs in 0..14999 && accuracy!=null && accuracy.isFinite() && accuracy>0 && accuracy<=30
  return listOf(
   BriefSignal("Chennai offline pack",if(packReady && inCoverage)"Map and walking router available here" else if(packReady)"Installed · this journey may leave coverage" else "Not ready · keep an internet connection",packReady && inCoverage),
   BriefSignal("Your location",if(fixReady)"Fresh fix · ±${accuracy!!.toInt()} m" else "Waiting for a fresh, accurate walking fix",fixReady),
   BriefSignal("Walking route",if(hasRoute)"Route available · review before starting" else "Choose a destination to plan a route",hasRoute),
   BriefSignal("Panda conversation",if(online)"Connected · needs internet" else "Offline · use map and saved directions",online)
  )
 }
}
