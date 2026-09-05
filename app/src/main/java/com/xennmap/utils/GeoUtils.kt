package com.xennmap.utils

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geodesy helpers for short-range marine navigation.
 * Distances use a spherical earth (haversine) which is accurate enough
 * for the distances this app deals with (a few hundred kilometres at most).
 */
object GeoUtils {

    private const val EARTH_RADIUS_M = 6_371_008.8

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Initial bearing from point 1 to point 2, degrees clockwise from true north [0..360). */
    fun initialBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val dLonRad = Math.toRadians(lon2 - lon1)
        val y = sin(dLonRad) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLonRad)
        val deg = Math.toDegrees(atan2(y, x))
        return (deg + 360.0) % 360.0
    }

    fun compassLabel(bearingDeg: Double): String {
        val labels = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
        val index = ((bearingDeg % 360) / 22.5).roundToInt() % 16
        return labels[index]
    }

    /** Ground resolution in meters per device-independent pixel at the given latitude/zoom. */
    fun metersPerPixel(latitudeDeg: Double, zoom: Double): Double {
        val lat = latitudeDeg.coerceIn(-85.0, 85.0)
        return 156_543.03392 * cos(Math.toRadians(lat)) / Math.pow(2.0, zoom)
    }

    fun formatLatitude(latitude: Double, decimals: Int = 4): String {
        val hemi = if (latitude >= 0) "N" else "S"
        return String.format("%.${decimals}f° %s", abs(latitude), hemi)
    }

    fun formatLongitude(longitude: Double, decimals: Int = 4): String {
        val hemi = if (longitude >= 0) "E" else "W"
        return String.format("%.${decimals}f° %s", abs(longitude), hemi)
    }

    fun formatBearing(bearingDeg: Double): String = "${bearingDeg.roundToInt().mod(360)}°"
}
