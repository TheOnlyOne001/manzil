package app.manzil.offline

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.manzil.BuildConfig
import app.manzil.system.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Instant

class MapLayersViewModel(app:Application):AndroidViewModel(app) {
 val network=networkStatusFlow(app).stateIn(viewModelScope,SharingStarted.Eagerly,currentNetworkStatus(app))
 private val repository=HazardRepository(app,OfflinePack(app).root)
 private val _snapshot=MutableStateFlow(HazardSnapshot());val snapshot=_snapshot.asStateFlow()
 private val _layer=MutableStateFlow(HazardLayer.NONE);val layer=_layer.asStateFlow()
 private var ready=false
 private var lastFetch=0L
 private var loadJob:Job?=null
 init {
  viewModelScope.launch { network.collect{if(ready)refresh()} }
  viewModelScope.launch {while(isActive){delay(60000);if(ready){val now=Instant.now();_snapshot.update{it.copy(features=it.features.filter{f->f.visibleAt(now)})};refresh()}}}
 }
 fun select(layer:HazardLayer){_layer.value=layer}
 fun load(){
  if(ready)return
  ready=true
  viewModelScope.launch {
   _snapshot.value=withContext(Dispatchers.IO){runCatching{repository.saved()}.getOrDefault(HazardSnapshot(message="Layers unavailable"))}
   refresh()
  }
 }
 private fun refresh(){
  if(network.value!=NetworkStatus.ONLINE){_snapshot.update{it.copy(saved=true,message="Saved layers")};return}
  if(BuildConfig.OVERLAY_API_URL.isBlank() || System.currentTimeMillis()-lastFetch<120000 || loadJob?.isActive==true)return
  lastFetch=System.currentTimeMillis()
  loadJob=viewModelScope.launch {
   val result=withContext(Dispatchers.IO){runCatching{repository.fetch(BuildConfig.OVERLAY_API_URL)}}
   result.onSuccess{_snapshot.value=it}.onFailure{_snapshot.update{it.copy(saved=true,message="Saved layers · refresh unavailable")}}
  }
 }
}
