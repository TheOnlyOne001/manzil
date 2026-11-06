package app.manzil.system

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import app.manzil.model.Destination
import java.util.Locale

/** Pure builder for the documented `geo:` destination URI (unit tested). */
object GeoUri {
    /** geo:lat,lng?q=lat,lng(Label) as documented for Google Maps intents. */
    fun forDestination(destination: Destination): String {
        val lat = format(destination.latitude)
        val lng = format(destination.longitude)
        return "geo:$lat,$lng?q=$lat,$lng(${encode(destination.name)})"
    }

    private fun format(value: Double): String = String.format(Locale.US, "%.6f", value)

    /** RFC 3986 percent-encoding of everything except unreserved characters. */
    fun encode(text: String): String = buildString {
        text.toByteArray(Charsets.UTF_8).forEach { byte ->
            val c = byte.toInt() and 0xFF
            val ch = c.toChar()
            if (c < 0x80 && (ch.isLetterOrDigit() || ch in "-._~")) {
                append(ch)
            } else {
                append('%')
                append("0123456789ABCDEF"[c shr 4])
                append("0123456789ABCDEF"[c and 0x0F])
            }
        }
    }
}

enum class MapsLaunchResult { OPENED_GOOGLE_MAPS, OPENED_OTHER_APP, NOT_AVAILABLE }

/**
 * Hands a destination to Google Maps through its documented intent. Manzil cannot read the route
 * Google Maps chooses, its downloaded offline areas, or its navigation state.
 */
object MapsLauncher {
    const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"

    fun isGoogleMapsInstalled(context: Context): Boolean = try {
        if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(GOOGLE_MAPS_PACKAGE, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(GOOGLE_MAPS_PACKAGE, 0)
        }
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun openDestination(context: Context, destination: Destination): MapsLaunchResult {
        val base = Intent(Intent.ACTION_VIEW, Uri.parse(GeoUri.forDestination(destination)))
        if (context !is Activity) base.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (isGoogleMapsInstalled(context) && tryStart(context, Intent(base).setPackage(GOOGLE_MAPS_PACKAGE))) {
            return MapsLaunchResult.OPENED_GOOGLE_MAPS
        }
        return if (tryStart(context, base)) MapsLaunchResult.OPENED_OTHER_APP else MapsLaunchResult.NOT_AVAILABLE
    }

    /** Opens the Google Maps app itself (e.g. so the user can manage offline areas there). */
    fun openGoogleMapsApp(context: Context): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(GOOGLE_MAPS_PACKAGE) ?: return false
        if (context !is Activity) launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return tryStart(context, launch)
    }

    private fun tryStart(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
