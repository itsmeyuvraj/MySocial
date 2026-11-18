package com.mysocial.mesh.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.abs

/**
 * Offline-resilient location utility for attaching and displaying physical location in mesh posts.
 * Utilizes standard Android AOSP LocationManager without Google Play Services dependencies,
 * respecting user privacy with opt-in permission handling and offline coordinate formatting.
 */
object LocationHelper {

    /**
     * Checks if either fine or coarse location permission is granted.
     */
    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * Retrieves the device's last known location using GPS, Network, or Passive providers.
     * Selects the most accurate/recent location available.
     */
    fun getLastKnownLocation(context: Context): Pair<Double, Double>? {
        if (!hasLocationPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

        var bestLocation: Location? = null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

        for (provider in providers) {
            try {
                if (lm.isProviderEnabled(provider)) {
                    val loc = lm.getLastKnownLocation(provider) ?: continue
                    if (bestLocation == null || loc.accuracy < (bestLocation?.accuracy ?: Float.MAX_VALUE)) {
                        bestLocation = loc
                    }
                }
            } catch (_: SecurityException) {
                // Permission revoked or not granted for this specific provider
            } catch (_: Exception) {
                // Provider disabled or unavailable
            }
        }

        return bestLocation?.let { Pair(it.latitude, it.longitude) }
    }

    /**
     * Actively requests a fresh standalone hardware GPS fix from satellite signals.
     * When off-grid in deep backcountry/forest with zero cellular A-GPS, this engages
     * the phone's autonomous GNSS receiver chip directly with satellites overhead.
     * Times out gracefully after [timeoutMs] and falls back to last known fix if available.
     */
    suspend fun getOrAcquireLocation(context: Context, timeoutMs: Long = 8_000L): Pair<Double, Double>? {
        if (!hasLocationPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

        val last = getLastKnownLocation(context)

        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(loc: Location) {
                        try {
                            lm.removeUpdates(this)
                        } catch (_: Exception) {}
                        if (cont.isActive) {
                            cont.resume(Pair(loc.latitude, loc.longitude))
                        }
                    }

                    override fun onProviderDisabled(provider: String) {}
                    override fun onProviderEnabled(provider: String) {}
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                }

                try {
                    val looper = Looper.getMainLooper()
                    if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, looper)
                    } else if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                        lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 0f, listener, looper)
                    } else {
                        if (cont.isActive) cont.resume(last)
                        return@suspendCancellableCoroutine
                    }

                    cont.invokeOnCancellation {
                        try {
                            lm.removeUpdates(listener)
                        } catch (_: Exception) {}
                    }
                } catch (_: SecurityException) {
                    if (cont.isActive) cont.resume(last)
                } catch (_: Exception) {
                    if (cont.isActive) cont.resume(last)
                }
            }
        } ?: last
    }

    /**
     * Formats latitude and longitude coordinates into a human-readable string:
     * e.g. "37.7749° N, 122.4194° W"
     */
    fun formatCoordinates(lat: Double, lon: Double): String {
        val latDir = if (lat >= 0) "N" else "S"
        val lonDir = if (lon >= 0) "E" else "W"
        return String.format(Locale.US, "%.4f° %s, %.4f° %s", abs(lat), latDir, abs(lon), lonDir)
    }

    /**
     * Attempts to resolve coordinates to a descriptive place/locality name.
     * When offline or geocoding is unavailable, falls back gracefully to formatted coordinates.
     */
    fun getPlaceName(context: Context, lat: Double, lon: Double): String {
        try {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    val parts = mutableListOf<String>()
                    val subLocality = address.subLocality ?: address.featureName
                    val locality = address.locality ?: address.subAdminArea
                    val adminArea = address.adminArea

                    if (!subLocality.isNullOrBlank() && subLocality != locality) {
                        parts.add(subLocality)
                    }
                    if (!locality.isNullOrBlank()) {
                        parts.add(locality)
                    }
                    if (parts.isEmpty() && !adminArea.isNullOrBlank()) {
                        parts.add(adminArea)
                    }
                    if (parts.isNotEmpty()) {
                        return parts.joinToString(", ")
                    }
                }
            }
        } catch (_: Exception) {
            // Geocoder unavailable or network error in offline mode
        }
        return formatCoordinates(lat, lon)
    }

    /**
     * Launches an external maps viewer application (Google Maps, OsmAnd, Organic Maps, etc.)
     * via standard Android geo: URI intent.
     */
    fun openLocationInMap(
        context: Context,
        latitude: Double?,
        longitude: Double?,
        locationName: String?
    ) {
        val uri = if (latitude != null && longitude != null) {
            val label = Uri.encode(locationName ?: "Location")
            Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($label)")
        } else if (!locationName.isNullOrBlank()) {
            Uri.parse("geo:0,0?q=" + Uri.encode(locationName))
        } else {
            null
        }

        if (uri == null) return

        try {
            val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(mapIntent)
        } catch (_: Exception) {
            Toast.makeText(context, "No map application available to view location", Toast.LENGTH_SHORT).show()
        }
    }
}
