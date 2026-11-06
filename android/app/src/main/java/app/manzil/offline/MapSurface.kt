package app.manzil.offline

import android.graphics.PointF
import app.manzil.offline.location.GpsFix
import app.manzil.offline.routing.OfflineRoute

data class MapCamera(val latitude:Double,val longitude:Double,val zoom:Double,val bearing:Double=0.0,val tilt:Double=0.0)
interface MapSurface {
 var onPick:(Double,Double)->Unit
 var onTap:(PointF)->Boolean
 var onCameraChanged:()->Unit
 fun camera():MapCamera?
 fun move(camera:MapCamera)
 fun project(latitude:Double,longitude:Double):PointF?
 fun setPlaces(places:List<Place>)
 fun update(route:OfflineRoute?,fix:GpsFix?,destination:Place?,navigating:Boolean=false)
 fun nightMode(enabled:Boolean) {}
 fun center()
 fun start()
 fun resume()
 fun pause()
 fun stop()
 fun destroy()
}
