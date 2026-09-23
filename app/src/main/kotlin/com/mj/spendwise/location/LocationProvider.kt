package com.mj.spendwise.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

data class LatLng(val lat: Double, val lng: Double)

/** Thin wrapper around FusedLocationProviderClient + Geocoder. Every call is best-effort (returns null on failure). */
class LocationProvider(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * Current position; if a fresh fix isn't available within 6 s, falls back to the last known one.
     * Null when permission is missing or the device has never had a location.
     */
    @SuppressLint("MissingPermission") // guarded by hasPermission()
    suspend fun getCurrentLocation(): LatLng? {
        if (!hasPermission()) return null
        return freshLocation() ?: lastKnownLocation()
    }

    @SuppressLint("MissingPermission")
    private suspend fun lastKnownLocation(): LatLng? = withTimeoutOrNull(3_000) {
        suspendCancellableCoroutine { cont ->
            try {
                LocationServices.getFusedLocationProviderClient(context).lastLocation
                    .addOnSuccessListener { loc -> cont.resume(loc?.let { LatLng(it.latitude, it.longitude) }) }
                    .addOnFailureListener { cont.resume(null) }
            } catch (e: Exception) {
                cont.resume(null)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun freshLocation(): LatLng? {
        return withTimeoutOrNull(6_000) {
            suspendCancellableCoroutine { cont ->
                try {
                    val cts = CancellationTokenSource()
                    cont.invokeOnCancellation { cts.cancel() }
                    LocationServices.getFusedLocationProviderClient(context)
                        .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                        .addOnSuccessListener { loc -> cont.resume(loc?.let { LatLng(it.latitude, it.longitude) }) }
                        .addOnFailureListener { cont.resume(null) }
                } catch (e: Exception) {
                    cont.resume(null)
                }
            }
        }
    }

    /** "Vashi, Navi Mumbai" style name; null when Geocoder is unavailable or offline. */
    @Suppress("DEPRECATION")
    suspend fun reverseGeocode(point: LatLng): String? = withContext(Dispatchers.IO) {
        try {
            if (!Geocoder.isPresent()) return@withContext null
            val a = Geocoder(context, Locale.getDefault()).getFromLocation(point.lat, point.lng, 1)?.firstOrNull()
                ?: return@withContext null
            listOfNotNull(a.subLocality ?: a.featureName, a.locality).distinct().joinToString(", ").ifBlank { null }
        } catch (e: Exception) {
            null
        }
    }
}
