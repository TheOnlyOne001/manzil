package app.manzil.offline.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class GpsStatus { STOPPED, PERMISSION_REQUIRED, PROVIDER_DISABLED, WAITING_FOR_FIX, FIX, STALE, ERROR }

data class GpsFix(
    val point: RoutePoint,
    val accuracyMeters: Float?,
    val altitudeMeters: Double?,
    val bearingDegrees: Float?,
    val speedMetersPerSecond: Float?,
    val epochMillis: Long,
    val elapsedRealtimeMillis: Long,
    /** Last-known cached locations never become a live fix merely because they look recent. */
    val cached: Boolean,
    val source: FixSource = FixSource.GPS,
) {
    fun ageMillis(nowElapsedRealtimeMillis: Long = SystemClock.elapsedRealtime()): Long =
        (nowElapsedRealtimeMillis - elapsedRealtimeMillis).coerceAtLeast(0)
    fun forNavigation(): NavigationFix = NavigationFix(point, accuracyMeters?.toDouble() ?: Double.POSITIVE_INFINITY, elapsedRealtimeMillis, cached)
}

data class GpsState(
    val status: GpsStatus = GpsStatus.STOPPED,
    val fix: GpsFix? = null,
    val ageMillis: Long? = null,
    val error: String? = null,
)

/**
 * Two independent live streams, merged by [LocationFixPolicy]:
 *  1. Raw GPS_PROVIDER via LocationManager — always registered; works fully offline and on devices
 *     without Google Play Services.
 *  2. Google fused provider at PRIORITY_HIGH_ACCURACY — only when Play Services is available. When
 *     online it can produce a first fix far sooner (Wi-Fi/cell assisted); a coarser fused fix can
 *     never displace a recent, usable GPS fix.
 * Cached last-known values (from either path) are shown as cached only, never as a live FIX.
 * Own an instance in an Activity/ViewModel or location foreground service. Call start/stop on main
 * thread; never keep an Activity-only source running after the Activity becomes stopped.
 */
class OfflineGpsSource(context: Context, private val staleAfterMillis: Long = 15_000L) : LocationListener {
    private val app = context.applicationContext
    private val manager = app.getSystemService(LocationManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(GpsState())
    val state: StateFlow<GpsState> = _state.asStateFlow()
    private var running = false
    private var gpsRegistered = false
    /** Bumped on every start/stop so late Play Services task results from a previous run are ignored. */
    private var generation = 0
    private var fusedClient: FusedLocationProviderClient? = null
    private var fusedCallback: LocationCallback? = null
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            refreshAge()
            handler.postDelayed(this, 1_000L)
        }
    }

    @SuppressLint("MissingPermission") // Explicit precise-location check immediately below.
    fun start() {
        mainThread()
        if (running && _state.value.status == GpsStatus.PERMISSION_REQUIRED) stop()
        if (running) { refreshAge(); return }
        permissionError()?.let { _state.value = GpsState(GpsStatus.PERMISSION_REQUIRED, error = it); return }
        val gpsManager = manager?.takeIf { it.allProviders.contains(LocationManager.GPS_PROVIDER) }
        val hasFused = playServicesAvailable()
        if (gpsManager == null && !hasFused) {
            _state.value = GpsState(GpsStatus.ERROR, error = "This device has no GPS provider.")
            return
        }
        generation++
        try {
            val lastKnown = gpsManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { convert(it, cached = true, FixSource.GPS) }
            _state.value = GpsState(waitingStatus(hasFused), lastKnown, lastKnown?.ageMillis())
            if (gpsManager != null) {
                // Register even if disabled so enabling location resumes without recreating the screen.
                gpsManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, this, Looper.getMainLooper())
                gpsRegistered = true
            }
            running = true
            if (hasFused) startFused(generation)
            handler.post(tick)
        } catch (e: SecurityException) {
            stop()
            _state.value = GpsState(GpsStatus.PERMISSION_REQUIRED, error = "Location permission was revoked.")
        } catch (e: RuntimeException) {
            stop()
            _state.value = GpsState(GpsStatus.ERROR, error = "GPS could not start: ${e.javaClass.simpleName}")
        }
    }

    fun stop() {
        mainThread()
        running = false
        generation++
        handler.removeCallbacks(tick)
        if (gpsRegistered) {
            try { manager?.removeUpdates(this) } catch (_: RuntimeException) { /* Already revoked/stopped. */ }
            gpsRegistered = false
        }
        stopFused()
        _state.value = _state.value.copy(status = GpsStatus.STOPPED, ageMillis = _state.value.fix?.ageMillis())
    }

    override fun onLocationChanged(location: Location) {
        if (!running || location.provider != LocationManager.GPS_PROVIDER) return
        offerLive(location, FixSource.GPS)
    }

    override fun onProviderEnabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER && running && !hasFreshLiveFix()) _state.value = _state.value.copy(status = GpsStatus.WAITING_FOR_FIX, error = null)
    }

    override fun onProviderDisabled(provider: String) {
        // Fused may still deliver real (network) fixes; keep showing them while they stay fresh.
        if (provider == LocationManager.GPS_PROVIDER && running && !(fusedCallback != null && hasFreshLiveFix())) _state.value = _state.value.copy(status = GpsStatus.PROVIDER_DISABLED)
    }

    @SuppressLint("MissingPermission") // start() verified precise permission; SecurityException handled.
    private fun startFused(token: Int) {
        val client = try { LocationServices.getFusedLocationProviderClient(app) } catch (_: RuntimeException) { return }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
            .setMinUpdateIntervalMillis(500L)
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(false) // Deliver the first real estimate now; GPS refines it.
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (!running || token != generation) return
                result.locations.forEach { offerLive(it, FixSource.FUSED) }
            }
        }
        fusedClient = client
        fusedCallback = callback
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnFailureListener { if (token == generation) stopFused() } // Raw GPS keeps running.
            client.lastLocation.addOnSuccessListener { location: Location? ->
                if (running && token == generation && location != null) offerCached(location, FixSource.FUSED)
            }
        } catch (_: SecurityException) {
            stopFused()
        } catch (_: RuntimeException) {
            stopFused()
        }
    }

    private fun stopFused() {
        val callback = fusedCallback
        val client = fusedClient
        fusedCallback = null
        fusedClient = null
        if (callback != null && client != null) {
            try { client.removeLocationUpdates(callback) } catch (_: RuntimeException) { /* Play Services gone. */ }
        }
    }

    private fun offerLive(location: Location, source: FixSource) {
        val fix = convert(location, cached = false, source) ?: return
        if (!LocationFixPolicy.shouldReplaceLive(_state.value.fix, fix)) return
        val now = SystemClock.elapsedRealtime()
        _state.value = GpsState(LocationFixPolicy.statusFor(fix, now, staleAfterMillis, GpsStatus.WAITING_FOR_FIX), fix, fix.ageMillis(now))
    }

    private fun offerCached(location: Location, source: FixSource) {
        val fix = convert(location, cached = true, source) ?: return
        val old = _state.value
        if (!LocationFixPolicy.shouldReplaceCached(old.fix, fix)) return
        _state.value = old.copy(fix = fix, ageMillis = fix.ageMillis())
    }

    private fun hasFreshLiveFix(): Boolean {
        val fix = _state.value.fix ?: return false
        return !fix.cached && fix.ageMillis() <= staleAfterMillis
    }

    /** WAITING if any real source can deliver: GPS on, or (with fused) the network provider on. */
    private fun waitingStatus(hasFused: Boolean): GpsStatus {
        val m = manager ?: return if (hasFused) GpsStatus.WAITING_FOR_FIX else GpsStatus.PROVIDER_DISABLED
        fun on(provider: String) = try { m.isProviderEnabled(provider) } catch (_: RuntimeException) { false }
        return if (on(LocationManager.GPS_PROVIDER) || (hasFused && on(LocationManager.NETWORK_PROVIDER))) GpsStatus.WAITING_FOR_FIX
        else GpsStatus.PROVIDER_DISABLED
    }

    private fun permissionError(): String? = LocationFixPolicy.permissionError(
        granted(Manifest.permission.ACCESS_FINE_LOCATION), granted(Manifest.permission.ACCESS_COARSE_LOCATION))

    private fun granted(permission: String) = ContextCompat.checkSelfPermission(app, permission) == PackageManager.PERMISSION_GRANTED

    private fun playServicesAvailable(): Boolean = try {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(app) == ConnectionResult.SUCCESS
    } catch (_: RuntimeException) { false }

    private fun refreshAge() {
        permissionError()?.let {
            // Permission downgraded/revoked while running: release both registrations, then report.
            stop()
            _state.value = _state.value.copy(status = GpsStatus.PERMISSION_REQUIRED, error = it)
            return
        }
        val old = _state.value
        val age = old.fix?.ageMillis()
        val status = if (old.status == GpsStatus.FIX && (age == null || age > staleAfterMillis)) GpsStatus.STALE else old.status
        _state.value = old.copy(status = status, ageMillis = age)
    }

    private fun convert(location: Location, cached: Boolean, source: FixSource): GpsFix? {
        if (!location.latitude.isFinite() || !location.longitude.isFinite() || location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) return null
        val elapsed = location.elapsedRealtimeNanos / 1_000_000L
        if (elapsed <= 0 || elapsed > SystemClock.elapsedRealtime() + 1_000L) return null
        return GpsFix(RoutePoint(location.latitude, location.longitude),
            location.accuracy.takeIf { location.hasAccuracy() && it.isFinite() && it > 0 },
            location.altitude.takeIf { location.hasAltitude() && it.isFinite() },
            location.bearing.takeIf { location.hasBearing() && it.isFinite() },
            location.speed.takeIf { location.hasSpeed() && it.isFinite() && it >= 0 },
            location.time, elapsed, cached, source)
    }

    private fun mainThread() { check(Looper.myLooper() == Looper.getMainLooper()) { "GPS lifecycle calls must run on the main thread" } }
}
