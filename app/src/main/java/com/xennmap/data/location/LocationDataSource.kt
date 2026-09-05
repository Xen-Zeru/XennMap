package com.xennmap.data.location

import android.annotation.SuppressLint
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Provider-independent GPS sample. */
data class RawFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val speedMps: Float,
    val bearingDeg: Float?,
    val timestamp: Long,
    val satelliteCount: Int? = null,
)

/**
 * Abstraction over the location engine so the app works with or without
 * Google Play services. Both implementations use the on-device GPS and need
 * no internet connectivity.
 */
interface LocationDataSource {
    fun fixes(): Flow<RawFix>
}

/** Google Play Services fused provider (preferred when available). */
class FusedLocationDataSource(context: Context) : LocationDataSource {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    override fun fixes(): Flow<RawFix> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
            .setMinUpdateIntervalMillis(500L)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { location -> trySend(location.toRawFix(null)) }
            }
        }
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(callback) }
    }
}

/** Plain AOSP LocationManager fallback (no Play Services required). */
class AospLocationDataSource(private val locationManager: LocationManager) : LocationDataSource {

    @SuppressLint("MissingPermission")
    override fun fixes(): Flow<RawFix> = callbackFlow {
        val mainHandler = Handler(Looper.getMainLooper())
        var satelliteCount: Int? = null

        val gnssCallback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                satelliteCount = (0 until status.satelliteCount).count { status.usedInFix(it) }
            }
        }

        val listener = LocationListener { location ->
            trySend(location.toRawFix(satelliteCount))
        }

        locationManager.registerGnssStatusCallback(gnssCallback, mainHandler)
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1_000L,
                0f,
                listener,
                Looper.getMainLooper(),
            )
        } catch (_: IllegalArgumentException) {
            // GPS provider unavailable on this device
        }
        awaitClose {
            locationManager.removeUpdates(listener)
            runCatching { locationManager.unregisterGnssStatusCallback(gnssCallback) }
        }
    }
}

private fun Location.toRawFix(satelliteCount: Int?): RawFix = RawFix(
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = accuracy,
    speedMps = speed,
    bearingDeg = if (hasBearing()) bearing else null,
    timestamp = time,
    satelliteCount = satelliteCount,
)

fun playServicesAvailable(context: Context): Boolean =
    GoogleApiAvailability.getInstance()
        .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
