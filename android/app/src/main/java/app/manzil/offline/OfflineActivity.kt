package app.manzil.offline

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import app.manzil.ui.components.PandaMark
import app.manzil.ui.components.MzIcon
import app.manzil.R
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.manzil.offline.location.*
import app.manzil.offline.routing.RouteSource
import app.manzil.offline.routing.GOOGLE_WALKING_BETA_WARNING
import app.manzil.ui.theme.ManzilTheme
import app.manzil.offline.search.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlin.math.roundToInt

class OfflineActivity:ComponentActivity() {
 private val vm:OfflineViewModel by viewModels()
 private var mapView:AdaptiveMapView?=null
 private val layers:MapLayersViewModel by viewModels()
 private val agent:app.manzil.voice.LivingAgentViewModel by viewModels()
 private lateinit var speaker:app.manzil.voice.AgentSpeaker
 private val permission=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){vm.locationSession.refresh()}
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState);enableEdgeToEdge(statusBarStyle=SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT),navigationBarStyle=SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT))
  speaker=app.manzil.voice.AgentSpeaker(this)
  setContent {ManzilTheme{OfflineScreen()}}
 }
 override fun onStart(){super.onStart();mapView?.start();vm.locationSession.acquirePreview()}
 override fun onResume(){super.onResume();mapView?.resume()}
 override fun onPause(){agent.cancel();speaker.stop();mapView?.pause();super.onPause()}
 override fun onStop(){vm.locationSession.releasePreview();mapView?.stop();super.onStop()}
 override fun onDestroy(){speaker.destroy();mapView?.destroy();super.onDestroy()}
 @OptIn(ExperimentalMaterial3Api::class)
 @Composable private fun OfflineScreen() {
  val ui by vm.ui.collectAsStateWithLifecycle();val gps by vm.gps.state.collectAsStateWithLifecycle()
  var welcome by androidx.compose.runtime.saveable.rememberSaveable{mutableStateOf(true)}
  var voice by remember{mutableStateOf(false)}
  val agentUi by agent.state.collectAsStateWithLifecycle()
  var spokenRequest by remember{mutableStateOf(false)}
  var agentPlaceChoice by remember{mutableStateOf(false)}
  var agentRouteRequested by remember{mutableStateOf(false)}
  var startPicker by remember{mutableStateOf(false)}
  var searchingStart by remember{mutableStateOf(false)}
  var startName by remember{mutableStateOf("Map point")}
  var search by remember {mutableStateOf("")};var searching by remember{mutableStateOf(false)}
  val placeSearch=remember{PlaceSearchRepository(this@OfflineActivity)}
  var searchResults by remember{mutableStateOf(PlaceSearchResult())}
  var searchBusy by remember{mutableStateOf(false)}
  var resolvingPlace by remember{mutableStateOf(false)}
  var searchNetwork by remember{mutableStateOf<app.manzil.system.NetworkStatus?>(null)}
  var searchRetry by remember{mutableIntStateOf(0)}
  var completedSearchRetry by remember{mutableIntStateOf(-1)}
  var agentDestinationAddress by remember{mutableStateOf("")}

  var pick by remember{mutableStateOf<Place?>(null)}
  var emergency by remember{mutableStateOf(false)}
  var journeyBrief by remember{mutableStateOf(false)}
  var nearbyOrigin by remember{mutableStateOf<RoutePoint?>(null)}
  var details by remember{mutableStateOf(false)}
  val focus=LocalFocusManager.current
  val network by layers.network.collectAsStateWithLifecycle()
  val snapshot by layers.snapshot.collectAsStateWithLifecycle()
  val layer by layers.layer.collectAsStateWithLifecycle()
  var layerMenu by remember{mutableStateOf(false)}
  var routePanel by remember{mutableStateOf(false)}
  var provider by remember{mutableStateOf(MapProvider.LOCAL)}
  DisposableEffect(layer) {
   val bars=androidx.core.view.WindowCompat.getInsetsController(window,window.decorView)
   bars.isAppearanceLightStatusBars=layer!=HazardLayer.NIGHT
   bars.isAppearanceLightNavigationBars=layer!=HazardLayer.NIGHT
   onDispose { }
  }
  LaunchedEffect(ui.loading){if(!ui.loading)layers.load()}
  LaunchedEffect(searching){if(searching)mapView?.contentBottomInset((32*resources.displayMetrics.density).toInt())}
  LaunchedEffect(search,searching,network,resolvingPlace,searchRetry,ui.loading) {
   if(!searching || resolvingPlace || ui.loading)return@LaunchedEffect
   val query=search.trim()
   if(query.length<2){searchResults=PlaceSearchResult(query);searchBusy=false;return@LaunchedEffect}
   if(searchResults.query==query && searchNetwork==network && completedSearchRetry==searchRetry)return@LaunchedEffect
   searchBusy=true
   try {
    delay(400)
    searchResults=placeSearch.search(query,ui.places,gps.fix?.takeIf{!it.cached && it.ageMillis()<30000}?.point)
    searchNetwork=network;completedSearchRetry=searchRetry
   }finally{searchBusy=false}
  }

  LaunchedEffect(agentUi.pending,ui.loading) {
   val reply=agentUi.pending?:return@LaunchedEffect
   if(ui.loading)return@LaunchedEffect
   var receipt:String?=null
   when(reply.action) {
    "find_nearby"->{
     val fix=gps.fix?.takeIf{!it.cached && it.ageMillis() in 0..29999 && (it.accuracyMeters?:Float.MAX_VALUE) in 0.1f..500f}
     if(fix==null){receipt="I need a recent location to find places near you. Tap Locate me, then ask again.";if(gps.status==GpsStatus.PERMISSION_REQUIRED)permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))}
     else {
      nearbyOrigin=fix.point
      val query=reply.destinationQuery.orEmpty()
      welcome=false;voice=false;startPicker=false;details=false;searchingStart=false;routePanel=false
      search=query;searching=true;agentPlaceChoice=true;resolvingPlace=true;searchBusy=true
      try {
       val result=placeSearch.search(query,ui.places,fix.point)
       currentCoroutineContext().ensureActive()
       searchResults=result.copy(items=result.items.sortedBy{RouteGeometry.distance(fix.point,RoutePoint(it.latitude,it.longitude))}.take(6))
       searchNetwork=network;completedSearchRetry=searchRetry
       receipt=if(result.items.isEmpty())"No matching places returned. Try another category or name." else "Choose a place to walk there. Distances are straight-line estimates; opening hours are not checked."
      }finally{resolvingPlace=false;searchBusy=false}
     }
    }
    "journey_brief"->{voice=false;journeyBrief=true;receipt="Your journey check uses the phone’s map, route and location state."}
    "emergency_help"->{voice=false;emergency=true;receipt="Emergency options opened. No call has been placed."}
    "stop_guidance"->{vm.stop();receipt="Walking guidance stopped. Your route is still available."}
    "plan_route"->{
     nearbyOrigin=null
     val query=app.manzil.voice.AgentDestinationResolver.searchQuery(reply.destinationQuery.orEmpty())
     layers.select(when(reply.layer){"night"->HazardLayer.NIGHT;"flood"->HazardLayer.HISTORY;else->HazardLayer.NONE})
     welcome=false;voice=false;details=false;startPicker=false;routePanel=true
     resolvingPlace=true;searchBusy=true;search=query;searchingStart=false;searching=true;agentPlaceChoice=true
     try {
      val result=placeSearch.search(query,ui.places,gps.fix?.takeIf{!it.cached && it.ageMillis()<30000}?.point)
      currentCoroutineContext().ensureActive()
      searchResults=result;searchNetwork=network;completedSearchRetry=searchRetry
      val destination=if(result.online)result.items.firstOrNull()?.asPlace() else app.manzil.voice.AgentDestinationResolver.resolve(query,result.items.map{it.asPlace()})
      if(destination!=null){
       agentDestinationAddress=if(result.online)result.items.first().address else ""
       vm.select(destination);vm.origin(null);vm.calculate();agentRouteRequested=true;agentPlaceChoice=false;searching=false
       if(gps.status==GpsStatus.PERMISSION_REQUIRED)permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))
       receipt="Using ${destination.name}${if(agentDestinationAddress.isNotBlank())" · $agentDestinationAddress" else ""}. Finding a walking route from your location."
      }else{receipt=if(result.items.isEmpty())"I couldn’t find that destination. Try a nearby landmark or another name." else "Choose the matching place. I’ll route from your current location."}
     }finally{resolvingPlace=false;searchBusy=false}

    }
    "show_layer"->{layers.select(when(reply.layer){"night"->HazardLayer.NIGHT;"flood"->HazardLayer.HISTORY;else->HazardLayer.NONE});welcome=false;voice=false;routePanel=false}
    "recenter"->{welcome=false;voice=false;mapView?.center();if(gps.status==GpsStatus.PERMISSION_REQUIRED)permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))}
   }
   agent.applied(receipt)
   if(spokenRequest && !speaker.speak(reply.reply,agentUi.language))android.widget.Toast.makeText(this@OfflineActivity,"Spoken reply unavailable for this language on this phone",android.widget.Toast.LENGTH_SHORT).show()
  }
  LaunchedEffect(ui.route,ui.busy,ui.waitingForLocation) {
   if(agentRouteRequested && ui.route!=null && !ui.busy && !ui.waitingForLocation){agentRouteRequested=false;routePanel=false;agent.applied("Route ready · ${formatWalkTime(ui.route!!.estimatedSeconds)}. ${if((ui.planningAccuracyMeters?:0f)>50f)"Approximate start ±${ui.planningAccuracyMeters!!.roundToInt()} m. " else ""}${if(layer==HazardLayer.NIGHT)"Night layer shown; safety comparison unavailable." else "Tap the route to start walking."}")}
  }

  Box(Modifier.fillMaxSize().background(Color(0xFFEDF1ED))) {
   AndroidView(factory={context->AdaptiveMapView(context,vm.pack).also {mapView=it;it.onPick={lat,lon->welcome=false;pick=Place("Dropped pin",lat,lon)};it.onProvider={p->provider=p};it.start();it.resume()}},update={it.setPlaces(ui.places);it.update(ui.route,gps.fix,ui.destination,ui.navigating);it.overlays(snapshot.features,layer);it.network(network)},modifier=Modifier.fillMaxSize())
   Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(16.dp)) {
    Surface(shape=RoundedCornerShape(22.dp),shadowElevation=3.dp,color=if(layer==HazardLayer.NIGHT)Color(0xFF18313F) else Color.White) {
     Column(Modifier.padding(horizontal=18.dp,vertical=6.dp)) {
      Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
       IconButton(onClick={agent.cancel();welcome=true;searching=false}){PandaMark(30.dp,ackKey=ui.route?.calculationMillis?.toInt()?:1)};Spacer(Modifier.width(6.dp));Text("manzil",fontWeight=FontWeight.Bold,fontSize=18.sp,color=if(layer==HazardLayer.NIGHT)Color(0xFFF3F4EE) else Color(0xFF142D4E))
       Spacer(Modifier.weight(1f));Text(if(provider==MapProvider.GOOGLE)"Online map" else "Offline map",fontSize=12.sp,color=if(layer==HazardLayer.NIGHT)Color(0xFF8BD4C8) else Color(0xFF087E83))
       IconButton(onClick={voice=true}){MzIcon(R.drawable.ic_mic,"Talk to Panda",tint=if(layer==HazardLayer.NIGHT)Color(0xFF9CC6CA) else Color(0xFF142D4E))}
      }
      if(searching) {
       TextField(search,{search=it},enabled=!resolvingPlace,shape=RoundedCornerShape(16.dp),colors=TextFieldDefaults.colors(focusedContainerColor=Color(0xFFF0F5F3),unfocusedContainerColor=Color(0xFFF0F5F3),disabledContainerColor=Color(0xFFF0F5F3),focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent,disabledIndicatorColor=Color.Transparent),placeholder={Text(if(searchingStart)"Search starting place" else "Find a place or street")},modifier=Modifier.fillMaxWidth().padding(top=8.dp),singleLine=true)
       if(searchBusy || resolvingPlace) BambooLoading(if(resolvingPlace)"Finding your destination" else "Searching places",Modifier.padding(vertical=8.dp))
       val visibleResults=if(searchResults.query==search.trim() && !searchBusy && !resolvingPlace)searchResults.items else emptyList()
       val searchInk=if(layer==HazardLayer.NIGHT)Color(0xFFF3F4EE) else Color(0xFF142D4E)
       LazyColumn(Modifier.heightIn(max=270.dp)) {items(visibleResults,key={it.id}){candidate->
        val place=candidate.asPlace()
        Row(Modifier.fillMaxWidth().clickable{
         if(searchingStart){startName=place.name;vm.origin(RoutePoint(place.latitude,place.longitude));vm.calculate()}
         else{vm.select(place);if(agentPlaceChoice){agentPlaceChoice=false;agentDestinationAddress=candidate.address;vm.origin(null);vm.calculate();agentRouteRequested=true;if(gps.status==GpsStatus.PERMISSION_REQUIRED)permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))}else{agentDestinationAddress="";startPicker=true}}
         searchingStart=false;searching=false;focus.clearFocus();routePanel=true
        }.padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
         MzIcon(R.drawable.ic_map_pin,null,tint=Color(0xFF087E83),size=22.dp);Spacer(Modifier.width(12.dp))
         Column(Modifier.weight(1f)){Text(place.name,fontSize=15.sp,fontWeight=FontWeight.SemiBold,color=searchInk);Text(candidate.address,fontSize=11.sp,lineHeight=15.sp,maxLines=2,color=if(layer==HazardLayer.NIGHT)Color(0xFFBDD0DC) else Color(0xFF53657A));nearbyOrigin?.let{origin->Text(String.format(java.util.Locale.US,"%.1f km away · straight line",RouteGeometry.distance(origin,RoutePoint(place.latitude,place.longitude))/1000),fontSize=11.sp,color=Color(0xFF087E83))}}
         MzIcon(R.drawable.ic_chevron_right,null,tint=searchInk,size=18.dp)
        }
       }}
       if(!searchBusy && !resolvingPlace && search.trim().length>=2){
        if(searchResults.message!=null)Text(searchResults.message!!,fontSize=12.sp,lineHeight=17.sp,color=searchInk,modifier=Modifier.padding(vertical=8.dp))
        if(visibleResults.isNotEmpty())Text(if(searchResults.online)"Places by Google Maps" else "Saved Chennai places",fontSize=10.sp,color=if(layer==HazardLayer.NIGHT)Color(0xFF8BD4C8) else Color(0xFF087E83))
       }
       Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
        TextButton(onClick={agent.cancel();searching=false;agentPlaceChoice=false;searchBusy=false}){Text(if(resolvingPlace)"Cancel search" else "Close search")}
        if(!searchBusy && !resolvingPlace && search.trim().length>=2)TextButton(onClick={searchRetry++}){Text("Search again")}
       }

      } else if(!welcome || ui.navigating) Text(ui.destination?.name?:"Where are you walking?",fontSize=16.sp,color=if(layer==HazardLayer.NIGHT)Color(0xFFBDD0DC) else Color(0xFF53657A),modifier=Modifier.fillMaxWidth().clickable{welcome=false;searchingStart=false;searching=true}.padding(vertical=8.dp))
     }
    }
   }
   if(!searching && !welcome) Column(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top=120.dp,end=16.dp)) {
    if(layer==HazardLayer.NIGHT) TextButton(onClick={emergency=true},colors=ButtonDefaults.textButtonColors(contentColor=Color(0xFFFF9AA9)),modifier=Modifier.background(Color(0xFF402936),RoundedCornerShape(24.dp))){Text("SOS",fontWeight=FontWeight.Bold)}
    Box {
     FilledTonalButton(onClick={layerMenu=true},colors=ButtonDefaults.filledTonalButtonColors(containerColor=Color.White,contentColor=Color(0xFF142D4E)),contentPadding=PaddingValues(horizontal=14.dp,vertical=8.dp)) {MzIcon(layerIcon(layer),null);Spacer(Modifier.width(8.dp));Text(if(layer==HazardLayer.NONE)"Layers ▾" else "${layer.label} ▾",fontSize=12.sp)}
     DropdownMenu(expanded=layerMenu,onDismissRequest={layerMenu=false}) {
      listOf(HazardLayer.NONE,HazardLayer.NIGHT,HazardLayer.HISTORY).forEach{choice->DropdownMenuItem(leadingIcon={MzIcon(layerIcon(choice),null,tint=if(choice==HazardLayer.NIGHT)Color(0xFFB3822E) else if(choice==HazardLayer.HISTORY)Color(0xFF167FA0) else Color(0xFF2457D6))},text={Text(choice.label)},onClick={layers.select(choice);layerMenu=false;routePanel=false})}
     }
    }
    if(layer!=HazardLayer.NONE && layer!=HazardLayer.NIGHT) Surface(color=Color.White.copy(alpha=.95f),shape=RoundedCornerShape(12.dp)) {
     Column(Modifier.padding(horizontal=12.dp,vertical=7.dp)) {
      Text(when(layer){HazardLayer.HISTORY->"Past flooding · not live";HazardLayer.RISK->if(snapshot.features.none{it.layer==HazardLayer.RISK}){if(snapshot.riskBelowTriggerUntil?.isAfter(java.time.Instant.now())==true)"Rain below model trigger" else "No fresh estimate available"} else "Modelled risk · low confidence";HazardLayer.REPORTS->if(snapshot.features.none{it.layer==HazardLayer.REPORTS})"No fresh reports available" else "Citizen reports · unverified";else->"Mapped places · access unknown"},fontSize=10.sp,lineHeight=14.sp,color=Color(0xFF53657A))
      if(layer==HazardLayer.HISTORY) {
       Row(horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.padding(top=5.dp)) {
        Text("━ <10",color=Color(0xFFDB9D1A),fontSize=11.sp)
        Text("━ 10–30",color=Color(0xFFEB6D21),fontSize=11.sp)
        Text("━ >30 cm",color=Color(0xFFC2304E),fontSize=11.sp)
       }
       Text("◆ Saved hotspots · depth unknown",fontSize=10.sp,lineHeight=14.sp,color=Color(0xFF8154A0))
       Text("Nearby road estimate · historical survey",fontSize=9.sp,lineHeight=12.sp,color=Color(0xFF53657A))
      }
      if(layer==HazardLayer.RISK) {
       Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("━ Watch",color=Color(0xFFDB9D1A),fontSize=10.sp)
        Text("━ Elevated",color=Color(0xFFEB6D21),fontSize=10.sp)
        Text("━ High",color=Color(0xFFC2304E),fontSize=10.sp)
       }
       Text("Rain + flood history · not measured depth",fontSize=9.sp,lineHeight=12.sp,color=Color(0xFF53657A))
      }
      TextButton(onClick={mapView?.showCoverage()},contentPadding=PaddingValues(0.dp),modifier=Modifier.height(32.dp)){Text("View Chennai layer",fontSize=11.sp)}
     }
    }
   }
   if(!searching && !welcome && layer==HazardLayer.NIGHT) {
    Surface(modifier=Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start=16.dp,top=178.dp),color=Color(0xF218313F),shape=RoundedCornerShape(16.dp)) {
     Column(Modifier.padding(horizontal=14.dp,vertical=10.dp)) {
      Row(horizontalArrangement=Arrangement.spacedBy(14.dp)) {
       Text("━ Mapped lit",fontSize=11.sp,lineHeight=15.sp,color=Color(0xFFFFD372))
       Text("┄ Unknown",fontSize=11.sp,lineHeight=15.sp,color=Color(0xFF9CB3C2))
      }
      Text("● Women’s police",fontSize=10.sp,lineHeight=14.sp,color=Color(0xFFFF99C8),modifier=Modifier.padding(top=4.dp))
      Text("Lighting preview · not live verified",fontSize=9.sp,lineHeight=12.sp,color=Color(0xFF9CB3C2),modifier=Modifier.padding(top=4.dp))
      
     }
    }
   }
   if(!searching) Column(Modifier.align(Alignment.BottomCenter).onSizeChanged{mapView?.contentBottomInset(it.height)}.navigationBarsPadding().imePadding().padding(16.dp)) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
     if(provider==MapProvider.GOOGLE) Spacer(Modifier.width(62.dp))
     Text(if(provider==MapProvider.GOOGLE)"Overlay © OpenStreetMap" else "© OpenStreetMap contributors",fontSize=9.sp,lineHeight=12.sp,color=Color(0xFF142D4E),modifier=Modifier.background(Color.White.copy(alpha=.9f),RoundedCornerShape(5.dp)).padding(5.dp))
     FilledIconButton(onClick={mapView?.center()},colors=IconButtonDefaults.filledIconButtonColors(containerColor=Color.White,contentColor=Color(0xFF2457D6))){MzIcon(R.drawable.ic_navigation,"Locate me")}
    }
    if(!welcome && agentUi.answer!=null && !voice) Surface(shape=RoundedCornerShape(16.dp),color=if(layer==HazardLayer.NIGHT)Color(0xFF18313F) else Color(0xFFF0F7F4),modifier=Modifier.fillMaxWidth().padding(top=8.dp)) {
     Row(Modifier.clickable{voice=true}.padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically){PandaMark(24.dp,ackKey=agentUi.messages.size);Spacer(Modifier.width(10.dp));Text(agentUi.answer!!,fontSize=12.sp,lineHeight=17.sp,maxLines=3,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,color=if(layer==HazardLayer.NIGHT)Color.White else Color(0xFF142D4E),modifier=Modifier.weight(1f));MzIcon(R.drawable.ic_chevron_right,"Ask a follow-up",tint=Color(0xFF087E73),size=18.dp)}
    }
    if(!welcome && !ui.navigating && agentDestinationAddress.isNotBlank() && searchResults.online) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
     Text(agentDestinationAddress,fontSize=10.sp,lineHeight=14.sp,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,color=if(layer==HazardLayer.NIGHT)Color(0xFFBDD0DC) else Color(0xFF53657A),modifier=Modifier.weight(1f).background(if(layer==HazardLayer.NIGHT)Color(0xEE18313F) else Color(0xEEFFFFFF),RoundedCornerShape(6.dp)).padding(5.dp))
     TextButton(onClick={agentPlaceChoice=true;searchingStart=false;search=searchResults.query;searching=true}){Text("Change place",fontSize=11.sp)}
    }
    Spacer(Modifier.height(8.dp))
    if(welcome && !ui.navigating) JourneyWelcome(
     loading=ui.loading,savedDestination=ui.destination?.name,
     onSearch={welcome=false;searchingStart=false;searching=true},onVoice={voice=true},
     onResume={welcome=false;routePanel=true},
     onNight={welcome=false;routePanel=false;layers.select(HazardLayer.NIGHT)},
     onFlood={welcome=false;routePanel=false;layers.select(HazardLayer.HISTORY)}
    ) else if(layer!=HazardLayer.NONE && !ui.navigating && !routePanel) Surface(shape=RoundedCornerShape(18.dp),color=Color.White,shadowElevation=2.dp) {
     Row(Modifier.fillMaxWidth().clickable{routePanel=true}.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
      MzIcon(R.drawable.ic_footprints,null,tint=Color(0xFF2457D6));Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
       Text(ui.route?.let{formatWalkTime(it.estimatedSeconds)+" · "+String.format(java.util.Locale.US,"%.1f km",it.distanceMeters/1000.0)}?:"Plan a walk",fontSize=16.sp,fontWeight=FontWeight.SemiBold,color=Color(0xFF142D4E))
       Text(ui.destination?.name?:"Choose a destination",fontSize=12.sp,maxLines=1,color=Color(0xFF53657A))
      }
      MzIcon(R.drawable.ic_chevron_right,"Show route controls")
     }
    } else Surface(shape=RoundedCornerShape(24.dp),shadowElevation=4.dp,color=Color.White) {
     Column(Modifier.padding(horizontal=18.dp,vertical=14.dp)) {
      val progress=ui.progress
      val next=ui.route?.turns?.firstOrNull{it.pointIndex>(progress?.segmentIndex?:0)}
      val guidanceTitle=when {
       progress?.quality==TrackingQuality.ARRIVED -> "You've arrived"
       ui.navigating && ui.busy -> "Finding another way"
       ui.navigating && progress?.quality in listOf(TrackingQuality.STALE_FIX,TrackingQuality.POOR_ACCURACY,TrackingQuality.WAITING) -> "Waiting for accurate GPS"
       ui.navigating && progress?.quality==TrackingQuality.UNCERTAIN_MATCH -> "Checking your position"
       ui.navigating && progress?.quality==TrackingQuality.OFF_ROUTE -> "You're off the route"
       ui.navigating -> next?.instruction?:"Follow the highlighted path"
       ui.waitingForLocation -> "Finding your location"
       ui.route?.source==RouteSource.GOOGLE_ROUTES -> "Google walking route"
       ui.route!=null -> "Offline walking route"
       else -> if(ui.destination==null)"Where to?" else "Plan your walk"
      }
      Row(verticalAlignment=Alignment.CenterVertically) {
       Text(guidanceTitle,fontSize=18.sp,fontWeight=FontWeight.Bold,color=Color(0xFF142D4E),maxLines=2,modifier=Modifier.weight(1f))
       if(layer!=HazardLayer.NONE && !ui.navigating)TextButton(onClick={routePanel=false}){Text("Hide",fontSize=12.sp)}
      }
      if(ui.loading || ui.busy || ui.waitingForLocation) BambooLoading(if(ui.loading)"Preparing your Chennai map" else if(ui.busy)"Finding your route" else "Finding your location",Modifier.padding(top=8.dp))
      val route=ui.route
      if(route!=null) Text("${((progress?.remainingMeters?:route.distanceMeters.toDouble())/1000).let{String.format(java.util.Locale.US,"%.2f",it)}} km  · ${formatWalkTime(route.estimatedSeconds)}",fontSize=16.sp,color=Color(0xFF2457D6),modifier=Modifier.padding(top=8.dp))
      if(details || ui.loading || ui.message.contains("could not") || ui.message.startsWith("Saved") || ui.busy || ui.message.contains("Waiting") || ui.message.contains("failed",true) || ui.message.contains("Outside")) Text(ui.message,fontSize=12.sp,lineHeight=18.sp,color=Color(0xFF53657A),modifier=Modifier.padding(top=6.dp))
      val gpsText=when(gps.status){GpsStatus.FIX->"Location ±${gps.fix?.accuracyMeters?.roundToInt()} m";GpsStatus.PERMISSION_REQUIRED->"Location permission needed";GpsStatus.PROVIDER_DISABLED->"Turn on device location";GpsStatus.STALE->"Location is out of date";else->"Finding your location…"}
      Text(if(ui.manualOrigin!=null)"From $startName · preview" else gpsText,fontSize=12.sp,color=Color(0xFF087E83),modifier=Modifier.padding(top=8.dp))
      if((ui.planningAccuracyMeters?:0f)>50f)Text("Approximate start ±${ui.planningAccuracyMeters!!.roundToInt()} m · preview",fontSize=12.sp,color=Color(0xFF975700),modifier=Modifier.padding(top=4.dp))
      if(progress!=null && progress.quality!=TrackingQuality.TRACKING)Text(progress.quality.name.replace('_',' '),fontSize=12.sp,color=Color(0xFF975700))
      Spacer(Modifier.height(12.dp))
      if(gps.status==GpsStatus.PERMISSION_REQUIRED && ui.manualOrigin==null) Button(onClick={permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))},modifier=Modifier.fillMaxWidth()){Text("Enable location")}
      else if(ui.destination!=null) Button(onClick={if(ui.navigating)vm.stop() else if(ui.manualOrigin!=null && route!=null){startPicker=true} else if(route==null){if(ui.waitingForLocation)vm.calculate() else startPicker=true} else vm.navigate()},enabled=!ui.loading&&!ui.busy&&!ui.waitingForLocation&&ui.destination!=null,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {Text(if(ui.busy)"Finding your route…" else if(ui.navigating)"Stop guidance" else if(ui.waitingForLocation)"Waiting for location…" else if(route==null)"Choose starting point" else if(ui.manualOrigin!=null)"Change starting point" else "Start walking")}
      if(ui.waitingForLocation && gps.status==GpsStatus.PROVIDER_DISABLED)TextButton(onClick={startActivity(android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))}){Text("Turn on location")}
      if(ui.waitingForLocation && gps.fix?.let{!it.cached && it.ageMillis()<15000 && (it.accuracyMeters?:999f)<=500}==true)TextButton(onClick={val f=vm.gps.state.value.fix;if(f!=null && !f.cached && f.ageMillis()<15000 && (f.accuracyMeters?:999f)<=500){startName="Approximate location";vm.origin(f.point);vm.calculate()}}){Text("Preview from approximate location",fontSize=12.sp)}
      Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
       TextButton(onClick={details=!details},enabled=ui.route!=null){Text(if(details)"Less" else "Route details",fontSize=12.sp)}
       TextButton(onClick={startPicker=true},enabled=!ui.busy&&!ui.navigating){Text("Start point",fontSize=12.sp)}
       if(ui.route!=null)TextButton(onClick={vm.calculate(ui.navigating)},enabled=ui.destination!=null&&!ui.busy){Text("Recalculate",fontSize=12.sp)}
      }
      if(ui.destination==null)Text("Search above, or hold a point on the map.",fontSize=12.sp,color=Color(0xFF53657A))
     }
    }
    if(!welcome && ui.route?.source==RouteSource.GOOGLE_ROUTES)Text(GOOGLE_WALKING_BETA_WARNING,fontSize=9.sp,lineHeight=12.sp,color=if(layer==HazardLayer.NIGHT)Color(0xFFBDD0DC) else Color(0xFF53657A),modifier=Modifier.background(if(layer==HazardLayer.NIGHT)Color(0xEE18313F) else Color(0xEEFFFFFF),RoundedCornerShape(6.dp)).padding(horizontal=8.dp,vertical=4.dp))
   }
  }
  if(journeyBrief) JourneyBriefSheet(
   destination=ui.destination?.name,
   summary=ui.route?.let{formatWalkTime(it.estimatedSeconds)+" · "+String.format(java.util.Locale.US,"%.1f km",it.distanceMeters/1000.0)},
   signals=app.manzil.voice.JourneyBrief.signals(ui.packReady,
    ui.route?.points?.all{vm.pack.inCoverage(it.latitude,it.longitude)}?:gps.fix?.let{vm.pack.inCoverage(it.point.latitude,it.point.longitude)}?:false,
    network==app.manzil.system.NetworkStatus.ONLINE,ui.route!=null,gps.fix?.cached?:true,gps.fix?.ageMillis(),gps.fix?.accuracyMeters),
   onDismiss={journeyBrief=false},onReview={journeyBrief=false;welcome=false;routePanel=true;details=true},onNight={journeyBrief=false;welcome=false;routePanel=false;layers.select(HazardLayer.NIGHT)})
  if(voice) PandaVoiceSheet(agent=agentUi,onDismiss={voice=false;agent.cancel();speaker.stop()},onLanguage=agent::language,onBrief={voice=false;journeyBrief=true},onEmergency={voice=false;emergency=true},onSend={request,spoken->
   spokenRequest=spoken;speaker.stop()
   val routeContext=when{ui.busy->"Route calculation in progress";ui.waitingForLocation->"Waiting for a fresh current location, no route yet";ui.route!=null->"${ui.route!!.distanceMeters} metres, ${formatWalkTime(ui.route!!.estimatedSeconds)}, ${if(ui.manualOrigin!=null)"manual start preview" else "route from captured start, initial accuracy ${ui.planningAccuracyMeters?:"unknown"} metres"}; lighting-aware alternatives have NOT been compared; ${if(ui.navigating)"guidance active" else "preview only"}";else->null}
   agent.ask(request,app.manzil.voice.AgentContext(ui.destination?.name,when(layer){HazardLayer.NIGHT->"night";HazardLayer.HISTORY->"flood";else->"map"},routeContext))
  })
  if(startPicker) ModalBottomSheet(onDismissRequest={startPicker=false},containerColor=Color.White) {
   Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=24.dp).padding(bottom=24.dp)) {
    Text("Start your walk",fontSize=24.sp,fontWeight=FontWeight.SemiBold,color=Color(0xFF142D4E))
    Text(ui.destination?.name?:"Choose a destination first",fontSize=14.sp,color=Color(0xFF53657A),modifier=Modifier.padding(top=6.dp,bottom=20.dp))
    TextButton(onClick={startPicker=false;vm.origin(null);vm.calculate();mapView?.center();routePanel=true;if(gps.status==GpsStatus.PERMISSION_REQUIRED)permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) {MzIcon(R.drawable.ic_navigation,null);Spacer(Modifier.width(12.dp));Text("Use current location",modifier=Modifier.weight(1f))}
    HorizontalDivider(color=Color(0xFFE8EDF2))
    TextButton(onClick={startPicker=false;searchingStart=true;search="";searching=true},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)){MzIcon(R.drawable.ic_route,null);Spacer(Modifier.width(12.dp));Text("Search a starting place",modifier=Modifier.weight(1f))}
    TextButton(onClick={startPicker=false;routePanel=false;android.widget.Toast.makeText(this@OfflineActivity,"Touch and hold a road, then choose Start here",android.widget.Toast.LENGTH_LONG).show()},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)){Text("Choose on map",modifier=Modifier.weight(1f))}
   }
  }
  if(emergency) AlertDialog(onDismissRequest={emergency=false},title={Text("Emergency help")},text={Text("112 · Police, ambulance and fire\nOpens your phone dialler. You place the call.")},confirmButton={TextButton(onClick={emergency=false;runCatching{startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL,android.net.Uri.parse("tel:112")))}.onFailure{android.widget.Toast.makeText(this@OfflineActivity,"Open your phone app and dial 112",android.widget.Toast.LENGTH_LONG).show()}}){Text("Open 112 dialler")}},dismissButton={TextButton(onClick={emergency=false}){Text("Close")}})
  if(details) ModalBottomSheet(onDismissRequest={details=false},containerColor=Color.White) {
   Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=24.dp)) {
    Text("Route details",style=MaterialTheme.typography.titleLarge)
    Text(ui.message,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(vertical=6.dp))
    Text(if(ui.route?.source==RouteSource.GOOGLE_ROUTES)"Directions by Google Maps" else "Walking paths from OpenStreetMap. Access and path conditions may have changed.",style=MaterialTheme.typography.bodySmall,color=Color(0xFF53657A))
    ui.route?.warnings.orEmpty().forEach{Text(it,style=MaterialTheme.typography.bodySmall,color=Color(0xFF53657A),modifier=Modifier.padding(top=6.dp))}
    LazyColumn(Modifier.fillMaxWidth().heightIn(max=360.dp).padding(vertical=16.dp)) {
     items(ui.route?.turns.orEmpty()){turn->
      Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
       MzIcon(R.drawable.ic_footprints,null,tint=Color(0xFF087E83));Spacer(Modifier.width(16.dp))
       Column {Text(turn.instruction,style=MaterialTheme.typography.bodyMedium)
        if(turn.distanceToNextMeters>0)Text("Next section: ${turn.distanceToNextMeters.roundToInt()} m",style=MaterialTheme.typography.bodySmall,color=Color(0xFF53657A))}
      }
     }
    }
   }
  }
  pick?.let {point->ModalBottomSheet(onDismissRequest={pick=null},containerColor=Color.White) {
   Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=24.dp).padding(bottom=24.dp)) {
    Text("Use this place",fontSize=24.sp,fontWeight=FontWeight.SemiBold,color=Color(0xFF142D4E))
    Text("Choose how this point fits your walk",fontSize=14.sp,color=Color(0xFF53657A),modifier=Modifier.padding(top=6.dp,bottom=16.dp))
    Button(onClick={startName="Map point";vm.origin(RoutePoint(point.latitude,point.longitude));pick=null;routePanel=true;if(ui.destination!=null)vm.calculate()},modifier=Modifier.fillMaxWidth().heightIn(min=50.dp)){Text("Start here")}
    TextButton(onClick={vm.select(point);agentDestinationAddress="";pick=null;startPicker=true;routePanel=true},modifier=Modifier.fillMaxWidth().heightIn(min=50.dp)){Text("Walk to here")}
   }
  }}
 }
}

private fun formatWalkTime(seconds:Int):String {val m=(seconds/60.0).roundToInt();return if(m<60) "$m min" else "${m/60} hr ${m%60} min"}

private fun layerIcon(layer:HazardLayer):Int=when(layer){HazardLayer.NIGHT->R.drawable.ic_layer_night;HazardLayer.HISTORY,HazardLayer.RISK,HazardLayer.REPORTS->R.drawable.ic_layer_flood;else->R.drawable.ic_layer_map}
