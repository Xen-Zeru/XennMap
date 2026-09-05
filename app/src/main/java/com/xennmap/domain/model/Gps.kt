package com.xennmap.domain.model

/** One GPS sample from the device location engine. */
data class GpsFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    /** Speed over ground in m/s (0 if unavailable). */
    val speedMps: Float,
    /** Course over ground in degrees clockwise from true north, or null while stationary. */
    val bearingDeg: Float?,
    val timestamp: Long,
)

enum class GpsSignalState(val label: String) {
    NO_FIX("No Signal"),
    ACQUIRING("Searching"),
    GOOD("Strong"),
    FAIR("Fair"),
    POOR("Weak"),
}

/** Everything the UI needs to know about the current GPS situation. */
data class GpsState(
    val fix: GpsFix? = null,
    val signal: GpsSignalState = GpsSignalState.NO_FIX,
    val satelliteCount: Int? = null,
    val isStarted: Boolean = false,
) {
    val hasFix: Boolean get() = fix != null && signal != GpsSignalState.NO_FIX && signal != GpsSignalState.ACQUIRING
}
