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
     * Detailed location coordinates and telemetry.
     */
    data class LocationResult(
        val latitude: Double,
        val longitude: Double,
        val altitude: Double? = null,
        val accuracy: Float? = null
    )

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
     * Checks if high-accuracy fine location permission is granted.
     */
    fun hasFineLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks whether location services are enabled system-wide on the device.
     */
    fun isLocationServicesEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
               lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * Launches Android System Location Settings so the user can enable location services.
     */
    fun openLocationSettings(context: Context) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    /**
     * Copies latitude and longitude coordinates to system clipboard with formatted Toast confirmation.
     */
    fun copyCoordinates(context: Context, latitude: Double, longitude: Double) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("GPS Coordinates", "$latitude, $longitude")
            clipboard?.setPrimaryClip(clip)
            val formatted = formatCoordinates(latitude, longitude)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "Coordinates copied: $formatted", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {}
    }

    /**
     * Retrieves the device's last known location with altitude details if available.
     */
    fun getLastKnownLocationDetails(context: Context): LocationResult? {
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

        return bestLocation?.let {
            val alt = if (it.hasAltitude()) it.altitude else null
            val acc = if (it.hasAccuracy()) it.accuracy else null
            LocationResult(it.latitude, it.longitude, alt, acc)
        }
    }

    /**
     * Retrieves the device's last known location using GPS, Network, or Passive providers.
     * Selects the most accurate/recent location available.
     */
    fun getLastKnownLocation(context: Context): Pair<Double, Double>? {
        return getLastKnownLocationDetails(context)?.let { Pair(it.latitude, it.longitude) }
    }

    /**
     * Actively requests a fresh standalone hardware GPS fix from satellite signals with altitude details.
     */
    suspend fun getOrAcquireLocationDetails(context: Context, timeoutMs: Long = 8_000L): LocationResult? {
        if (!hasLocationPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

        val last = getLastKnownLocationDetails(context)
        val hasFine = hasFineLocationPermission(context)

        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(loc: Location) {
                        try {
                            lm.removeUpdates(this)
                        } catch (_: Exception) {}
                        if (cont.isActive) {
                            val alt = if (loc.hasAltitude()) loc.altitude else null
                            val acc = if (loc.hasAccuracy()) loc.accuracy else null
                            cont.resume(LocationResult(loc.latitude, loc.longitude, alt, acc))
                        }
                    }

                    override fun onProviderDisabled(provider: String) {}
                    override fun onProviderEnabled(provider: String) {}
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                }

                try {
                    val looper = Looper.getMainLooper()
                    var registeredAny = false

                    // If fine location is permitted and GPS is enabled, request satellite fixes
                    if (hasFine && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        try {
                            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, looper)
                            registeredAny = true
                        } catch (_: SecurityException) {}
                    }

                    // Concurrently request network provider for fast coarse / indoor location
                    if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                        try {
                            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 0f, listener, looper)
                            registeredAny = true
                        } catch (_: SecurityException) {}
                    }

                    if (!registeredAny) {
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
     * Actively requests a fresh standalone hardware GPS fix from satellite signals.
     * When off-grid in deep backcountry/forest with zero cellular A-GPS, this engages
     * the phone's autonomous GNSS receiver chip directly with satellites overhead.
     * Times out gracefully after [timeoutMs] and falls back to last known fix if available.
     */
    suspend fun getOrAcquireLocation(context: Context, timeoutMs: Long = 8_000L): Pair<Double, Double>? {
        return getOrAcquireLocationDetails(context, timeoutMs)?.let { Pair(it.latitude, it.longitude) }
    }

    /**
     * Formats latitude and longitude coordinates into a human-readable string:
     * e.g. "37.7749° N, 122.4194° W" or "37.7749° N, 122.4194° W (Alt: 240m)"
     */
    fun formatCoordinates(lat: Double, lon: Double, altitudeMeters: Double? = null): String {
        val latDir = if (lat >= 0) "N" else "S"
        val lonDir = if (lon >= 0) "E" else "W"
        val base = String.format(Locale.US, "%.4f° %s, %.4f° %s", abs(lat), latDir, abs(lon), lonDir)
        return if (altitudeMeters != null && !altitudeMeters.isNaN()) {
            "$base (Alt: ${altitudeMeters.toInt()}m)"
        } else {
            base
        }
    }

    /**
     * Attempts to resolve coordinates to a descriptive place/locality name.
     * When offline or geocoding is unavailable, falls back gracefully to formatted coordinates.
     */
    fun getPlaceName(context: Context, lat: Double, lon: Double, altitudeMeters: Double? = null): String {
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
                        val resolvedName = parts.joinToString(", ")
                        return if (altitudeMeters != null && !altitudeMeters.isNaN()) {
                            "$resolvedName (Alt: ${altitudeMeters.toInt()}m)"
                        } else {
                            resolvedName
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Geocoder unavailable or network error in offline mode
        }
        return formatCoordinates(lat, lon, altitudeMeters)
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
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "No map application available to view location", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
