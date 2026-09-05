package com.xennmap.utils

import com.xennmap.domain.model.DepthUnit
import com.xennmap.domain.model.DistanceUnit
import com.xennmap.domain.model.SpeedUnit
import java.util.Locale
import kotlin.math.roundToInt

/** Human-readable formatting of marine quantities according to the user's unit preferences. */
object FormatUtils {

    const val METERS_PER_NAUTICAL_MILE = 1852.0
    const val MPS_TO_KNOTS = 1.943844

    fun distance(meters: Double, unit: DistanceUnit): String = when (unit) {
        DistanceUnit.KILOMETERS ->
            if (meters >= 1000) String.format(Locale.US, "%.1f km", meters / 1000)
            else "${meters.roundToInt()} m"
        DistanceUnit.NAUTICAL_MILES -> {
            val nmi = meters / METERS_PER_NAUTICAL_MILE
            if (nmi >= 0.1) String.format(Locale.US, "%.2f nmi", nmi)
            else "${meters.roundToInt()} m"
        }
    }

    fun distanceValue(meters: Double, unit: DistanceUnit): String = when (unit) {
        DistanceUnit.KILOMETERS -> String.format(Locale.US, "%.1f", meters / 1000)
        DistanceUnit.NAUTICAL_MILES -> String.format(Locale.US, "%.2f", meters / METERS_PER_NAUTICAL_MILE)
    }

    fun distanceUnitLabel(unit: DistanceUnit): String = when (unit) {
        DistanceUnit.KILOMETERS -> "km"
        DistanceUnit.NAUTICAL_MILES -> "nmi"
    }

    fun speed(metersPerSecond: Float, unit: SpeedUnit): String = when (unit) {
        SpeedUnit.KNOTS -> String.format(Locale.US, "%.1f kn", metersPerSecond * MPS_TO_KNOTS)
        SpeedUnit.KILOMETERS_PER_HOUR -> String.format(Locale.US, "%.1f km/h", metersPerSecond * 3.6)
    }

    fun depth(meters: Double, unit: DepthUnit): String = when (unit) {
        DepthUnit.METERS -> String.format(Locale.US, "%.0f m", meters)
        DepthUnit.FEET -> String.format(Locale.US, "%.0f ft", meters * 3.28084)
        DepthUnit.FATHOMS -> String.format(Locale.US, "%.1f fm", meters / 1.8288)
    }

    fun duration(millis: Long): String {
        if (millis < 0) return "—"
        val totalMinutes = millis / 60000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            totalMinutes > 0 -> "${totalMinutes} min"
            else -> "<1 min"
        }
    }

    fun etaMinutes(distanceMeters: Double, speedMps: Float): Int {
        val effectiveSpeed = if (speedMps > 0.5f) speedMps.toDouble() else 8.0 / MPS_TO_KNOTS
        return (distanceMeters / effectiveSpeed / 60).roundToInt().coerceAtLeast(0)
    }

    fun bytes(bytes: Long): String = when {
        bytes >= 1_000_000_000 -> String.format(Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> String.format(Locale.US, "%.0f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format(Locale.US, "%.0f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }

    fun clockTime(epochMillis: Long): String {
        if (epochMillis <= 0) return "—"
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = epochMillis }
        val hour = cal.get(java.util.Calendar.HOUR)
        val minute = cal.get(java.util.Calendar.MINUTE)
        val amPm = if (cal.get(java.util.Calendar.AM_PM) == java.util.Calendar.AM) "AM" else "PM"
        val h = if (hour == 0) 12 else hour
        return String.format(Locale.US, "%d:%02d %s", h, minute, amPm)
    }

    fun shortDate(epochMillis: Long): String {
        if (epochMillis <= 0) return "—"
        val fmt = java.text.SimpleDateFormat("MMM d, yyyy", Locale.US)
        return fmt.format(java.util.Date(epochMillis))
    }

    fun greeting(): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 4..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
    }
}
